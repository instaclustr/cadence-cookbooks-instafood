# Instafood: Cadence Workflow Versioning Cookbook

![Instaclustr Managed Cadence](images/Instaclustr_Product_Managed_Cadence.png)

## Introduction

### Who is this cookbook for?

This cookbook is for developers and engineers of all levels looking to understand how to change workflows in Cadence using the versioning API. 
The recipe in this book provides *"Hello World!"* type examples based on simple scenarios and use cases.

### What you will learn

How to setup a simple Cadence application which implements workflow versioning on Instaclustr's Managed Service Platform.

### What you will need

- An account on Instaclustr’s managed service platform (sign up for a free trial using the
  following [signup link](https://console2.instaclustr.com/signup))
- Basic Java 11 and Gradle installation
- IntelliJ Community Edition, Visual Studio Code or any other IDE with Gradle support
- Docker (optional: only needed to run Cadence command line client)

### What is Cadence?

A large number of use cases span beyond a single request-reply, require tracking of a complex state, respond to asynchronous events, and communicate to external unreliable dependencies. The usual approach to building such applications is a hodgepodge of stateless services, databases, cron jobs, and queuing systems. This negatively impacts developer productivity as most of the code is dedicated to plumbing, obscuring the actual business logic behind a myriad of low-level details.

Cadence is an orchestration framework that helps developers write fault-tolerant, long-running applications, also known as workflows. In essence, it provides a durable virtual memory that is not linked to a specific process or host, and is able to rebuild application state by replaying individual steps. This includes function stacks, with local variables across all sorts of host and software failures. This allows you to write code using the full power of a programming language while Cadence takes care of durability, availability, and scalability of the application.

## What is workflow versioning?

Cadence's core abstraction is a fault-oblivious stateful **workflow**. Workflow definitions are built by combining fully deterministic code, and calls to external services using the **activities** interface, to define a replayable, fault tolerant workflow.

Over time, it will become neccesary to modify the workflow, or activity code, when requirements change or interfaces are updated. This can introduce problems for workflows that are currently running.

How do we introduce changes that both satisfy our new requirements and do not introduce non-deterministic changes for workflows that are currently executing?

Cadence supports this with the **versioning api**.

### Cadence's state recovery and determinisim requirement

Before we can understand how versioning is implemented, we must first understand the most powerful feature that Cadence offers: **fault tolerant workflow execution**.

In order to deliver that functionality, Cadence must be able to recover a workflow process that has failed mid-execution, and continue execution as if no problem has occured.

So how does it do this? With a combination of re-executing workflow code and persisting the result of activity calls.

Lets consider a simple example workflow with 3 activity calls.

```java
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

    Result2 r2 = activity.step2(r1);
    r2.field = r2.field - 1;

    // possible crash here!

    Result3 r3 = activity.step3(r2);

    return r3;
```

Now lets imagine our workflow code is being executed, and the worker process crashes after step 2 completes but before step 3 executes.

When a new Cadence worker comes online, it will execute the workflow from the start. When encountering a call to an activity, Cadence first checks to see if there is an event and result in the history table, and if there is, will instantly return that result to the workflow worker.

If we return to our example, the workflow will immediately progress to step 3, having restored the history in the workflow, and continue execution.

### Why do we need deterministic code?

In our previous example, imagine what would happen if our recovering workflow code did not execute the activities in the same order as they did the first time.

```java
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

    Result2 r2 = null;
    boolean skipStep2 = random.nextInt(10) < 5;
   
    if (!skipStep2) {
      r2 = activity.step2(r1);
      r2.field = r2.field - 1;
    }

    // possible crash here!

    Result3 r3 = activity.step3(r2);

    return r3;
```

If our workflow code was not deterministic, instead of executing activities *1-2-3*, we might have some code that calls activity 3 without calling activity 2, *1-3*, and this would change the value of the parameter being passed.

How can Cadence recover a workflow which may execute activities in different order or calculate different values every iteration? Short answer, it can't.

If our non-deterministic workflow were to fail and a worker started to recover it, when Cadence encounters a call to an activity that isn't in the history it would throw an exception.

## Workflow versioning explained

So now we understand how Cadence persists activity results, and how it can use the event history to recover a failed worker process.
We also understand that our workflow code must be deterministic, otherwise the workflow is not reliably recoverable.

This leaves us with a problem, what do we do when we **need** to update our workflow code with changes that will make *existing* workflows behave non-deterministically?

Thankfully, Cadence has support for this.

### Updated example

Lets update our example workflow in response to a new requirement:

```diff
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

-   Result2 r2 = activity.step2(r1);
+   Result2 r2 = activity.updatedStep2(r1);
    r2.field = r2.field - 1;

    Result3 r3 = activity.step3(r2);

    return r3;
```

We have replaced step 2 with an updated activity call and we want to deploy it, but active workflows will not be able replay history with the newly updated workflow. When Cadence encounters the new activity call, it will throw an exception.

With the Cadence SDK, we can introduce branching logic using the *workflow.getVersion* procedure call.

```java
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

    Result r2 = null;
    int version = Workflow.getVersion("step2Updated", Workflow.DEFAULT_VERSION, 1);

    if (version == Workflow.DEFAULT_VERSION) {
      // previous code path
      r2 = activity.step2(r1);
      r2.field = r2.field - 1;
    }
    else {
      // new code path
      r2 = activity.alternateStep2(r1);
    }
    
    Result3 r3 = activity.step3(r2);

    return r3;
```

Our new workflow code uses *workflow.getVersion* to determine the version of the "step2Updated" feature. Then we can decide if it should execute the old or new code paths.

How does this work for workflows that started executing before the version check was implemented?

First, lets break down the 3 parameters in this call:

1. the *changeId*, a unique identifier that represents the change made.
2. *minSupported* the lowest version supported by this workflow. In our example, *Workflow.DEFAULT_VERSION*.
3. *maxSupported* the highest version supported. In our example, 1.

When a workflow encounters this call, it will check the version history for the *changeId* and then evaluates the following scenarios:

1. If we are replaying history, due to a worker recovery, and we encounter *workflow.getVersion* for the first time - Record the *minSupported* value and return it.

    - Cadence has correctly identified that a new version has been introduced, and our inflight workflow execution didn't originally support it, so it returns the minimal value.

2. If we are executing this new workflow for the first time - Record the *maxSupported* value and return it.

    - New instances of a workflow will always return the highest available value for *workflow.getVersion* calls.

3. If there is an existing entry in the history, return the recorded value.

    - Additional version updates will not impact in-flight workflows, the version they recorded the first time will always be the same.

#### Making additional changes

*workflow.getVersion* can support multiple updates. We can increase the *maxSupported* value when we add additional changes and introduce even more branching paths to our workflow code.

```java
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

    Result r2 = null;
    int version = Workflow.getVersion("step2Updated", Workflow.DEFAULT_VERSION, 2);

    if (version == Workflow.DEFAULT_VERSION) {
      // initial code path
      r2 = activity.step2(r1);
      r2.field = r2.field - 1;
    }
    else if (version == 1) {
      // second version code path
      r2 = activity.alternateStep2(r1);
    }
    else {
      // newest code path
      r2 = activity.secondAlternateStep2(r1);
    }
    
    Result3 r3 = activity.step3(r2);

    return r3;
```

Eventually, all the workflows running the old logic will complete, and we can consider them no long supported. In this case we can update the *minSupported* value, and then we can remove the workflow branch that supported that version.

```java
    Result1 r1 = activity.step1();
    r1.field = r1.field + 1;

    Result r2 = null;
    int version = Workflow.getVersion("step2Updated", 1, 2);

    if (version == 1) {
      // second version code path
      r2 = activity.alternateStep2(r1);
    }
    else {
      // newest code path
      r2 = activity.secondAlternateStep2(r1);
    }
    
    Result3 r3 = activity.step3(r2);

    return r3;
```

#### Conclusion

Phew! Our workflow has been updated, and it remains deterministic. The first time we encounter any version check, the returned value is persisted.
If we ever replay the history, the value will always remain the same and we can build our workflow code around that guarantee.

## Use Case Example: Instafood meets MegaBurgers

In order to see workflow versioning in action, we'll be updating our Instafood workflow with new functionality.

### Instafood Brief

Instafood is an online app-based meal delivery service. Customers can place an order for food from their favorite local restaurants via Instafood’s mobile app. Orders can be for pickup or delivery. If delivery is chosen, Instafood will organize to have one of their many delivery drivers pickup the order from the restaurant and deliver it to the customer. Instafood provides each restaurant a kiosk/tablet which is used for communication between Instafood and the restaurant. Instafood notifies the restaurant when an order is placed, and then the restaurant can accept the order, provide an ETA, mark it as ready, etc. For delivery orders, Instafood will coordinate to have a delivery driver pick up based on the ETA.

### Ordering from "MegaBurgers"

MegaBurgers is a large multinational fast food hamburger chain. They have an existing mobile app and website that uses a back-end REST API for customers to place orders. Instafood’s backend will directly integrate with MegaBurgers’s existing REST-based ordering system to place orders and receive updates.

![MegaBurger's order status](images/Diagram_3.png)

*Fig 2. MegaBurger's order state machine*

We are designing Instafood to offer meals from any restaurant that signs up to our service. Each company will have its own method to take orders and monitor progress, which makes it a perfect candidate to implement via a child workflow.

In the case of MegaBurger this is done via the following **if** statment, which is made at the time of ordering.

``` java
  if (Restaurant.MEGABURGER.equals(order.getRestaurant())) {
      MegaBurgerOrderWorkflow megaBurgerOrderWorkflow = Workflow.newChildWorkflowStub(MegaBurgerOrderWorkflow.class);
      Async.procedure(megaBurgerOrderWorkflow::orderFood, order);
  }
```
The above example shows how simple this makes the parent workflow, it doesn't have to know about the various peculiarities of each company's order process. That's the job of the child workflow to implement.

## Setting up Instafood Project

In order to run the sample project yourself you’ll need to set up a Cadence cluster. We’ll be using Instaclustr’s Managed Service platform to do so.

### Step 1 - Creating Instaclustr Managed Clusters

A Cadence cluster requires an Apache Cassandra® cluster to connect to for its persistence layer. In order to set up both Cadence and Cassandra clusters we’ll follow ["Creating a Cadence Cluster" documentation.](https://www.instaclustr.com/support/documentation/cadence/getting-started-with-cadence/creating-a-cadence-cluster/)

By using Instaclustr platform, the following operations are handled automatically for you:

- Firewall rules will automatically get configured on the Cassandra cluster for Cadence nodes.
- Authentication between Cadence and Cassandra will get configured, including client encryption settings.
- The Cadence default and visibility keyspaces will be created automatically in Cassandra.
- A link will be created between the two clusters, ensuring you don’t accidentally delete the Cassandra cluster before
  Cadence.
- A Load Balancer will be created. It is recommended to use the load balancer address to connect to your cluster.

### Step 2 - Setting up Cadence Domain

Cadence is backed by a multi-tenant service where the unit of isolation is called a domain. In order
to get our Instafood application running we first need to register a domain for it.

1. In order to interact with our Cadence cluster, we need to install its command line interface client.

   #### macOS
   If using a macOS client the Cadence CLI can be installed with Homebrew as follows:
    ```bash
    brew install cadence-workflow
    # run command line client
    cadence <command> <arguments>
    ```

   #### Other Systems
   If not, the CLI can be used via Docker Hub image `ubercadence/cli`:
    ```bash
    # run command line client
    docker run --network=host --rm ubercadence/cli:master <command> <arguments>
    ```

   For the rest of the steps we'll use `cadence` to refer to the client.

2. In order to connect, it is recommended to use the load balancer address to connect to your cluster. This can be found at the top of the
   *Connection Info* tab, and will look like this: "ab-cd12ef23-45gh-4baf-ad99-df4xy-azba45bc0c8da111.elb.us-east-1.amazonaws.com". We'll call this the <cadence_host>.



3. We can now test our connection by listing current domains:

   ```bash
   cadence --ad <cadence_host>:7933 admin domain list
   ```

4. Add `instafood` domain:

   ```bash
   cadence --ad <cadence_host>:7933 --do instafood domain register
   ```

5. Check it was registered accordingly:

   ```bash
   cadence --ad <cadence_host>:7933 --do instafood domain describe
   ```

### Step 3 - Run Instafood Sample Project

1. Clone Gradle project
   from [Instafood project git repository](https://github.com/instaclustr/cadence-cookbooks-instafood).

2. Open property file at `instafood/src/main/resources/instafood.properties` and replace `cadenceHost` value with your
   load balancer address:

   ```properties
   cadenceHost=<cadence_host>
   ```

3. You can now run the app by  
   ```bash
   cadence-cookbooks-instafood/instafood$ ./gradlew run
   ```
   or executing *InstafoodApplication* main class from your IDE:

   ![Running Instafood app](images/run_instafood.png)

4. Check it is running by looking into its terminal output:

   ![Instafood running terminal output](images/instafood_app_running.png)

## Instafood order workflow review

Now that we have everything set up, lets look at the actual integration between Instafood and Megaburger, and how child workflows are used.

First, lets look at the Instafood workflow. The main function is **orderFood**, which gets started when an order is placed:

**Instafood workflow**
```java
  public void orderFood(FoodOrder order) {
        if (Restaurant.MEGABURGER.equals(order.getRestaurant())) {
            MegaBurgerOrderWorkflow megaBurgerOrderWorkflow = Workflow.newChildWorkflowStub(MegaBurgerOrderWorkflow.class);
            Async.procedure(megaBurgerOrderWorkflow::orderFood, order);
        } else {
            throw new RuntimeException("Restaurant option not available");
        }
        // Wait for an ETA or abort if restaurant rejected order
        Workflow.await(() -> etaInMinutes != -1 || OrderStatus.REJECTED.equals(currentStatus));
        if (OrderStatus.REJECTED.equals(currentStatus)) {
            throw new RuntimeException("Order was rejected by restaurant");
        }

        if (!order.isPickup()) {
            // Wait for predicted ETA or until order marks as ready
            Workflow.await(Duration.ofMinutes(getTimeToSendCourier()), () -> OrderStatus.READY.equals(currentStatus));

            CourierDeliveryWorkflow courierDeliveryWorkflow = Workflow.newChildWorkflowStub(CourierDeliveryWorkflow.class);
            Async.procedure(courierDeliveryWorkflow::deliverOrder, new CourierDeliveryJob(order.getRestaurant(), order.getAddress(), order.getTelephone()));

            Workflow.await(() -> OrderStatus.COURIER_DELIVERED.equals(currentStatus));
        } else {
            Workflow.await(() -> OrderStatus.RESTAURANT_DELIVERED.equals(currentStatus));
        }
    }
```

We can see here, we currently only support the Megaburger restaurant, but there is scope to add more later!

As we mentioned earlier, we invoke the child workflow by creating the **child workflow stub** and starting the **orderFood** workflow on it.

This particular workflow continues executing while the child workflow is progressing, and then it will block while it waits for a signal from the child workflow. Once this message is received, it can progress to the next stage.

Later in the workflow definition, we can see that we call another child workflow. This one is responsible for dispatching a courier to pickup the order from the restaurant and then deliver the order to the customer.

As we have explained, communication between the parent and child workflow is possible via asynchronous messages. 

Let's look at how that is implemented in this workflow.

```java
    // ...

    // Wait for an ETA or abort if restaurant rejected order
    Workflow.await(() -> etaInMinutes != -1 || OrderStatus.REJECTED.equals(currentStatus));

    // ... 
```
Here our workflow is **polling** until the order ETA is updated, so how is that happening? 

(For more details on polling in cadence workflows, have a read of our [polling cookbook](https://github.com/instaclustr/cadence-cookbooks-instafood/blob/main/cookbooks/polling/polling-megafood.md))

Lets look at our parent workflow and its **interface definition**, where it has defined the following method:

**Instafood workflow - interface**
```java
  @SignalMethod
  void updateEta(int estimationInMinutes);
```
The **@SignalMethod** annotation decorates the method and informs Cadence that this method is used to receive signals from an external process, in this case it's coming from the child workflow, which we can see here:

**Megaburgers workflow - Child workflow**
```java
  private OrderWorkflow getParentOrderWorkflow() {
      String parentOrderWorkflowId = Workflow.getWorkflowInfo().getParentWorkflowId();
      return Workflow.newExternalWorkflowStub(OrderWorkflow.class, parentOrderWorkflowId);
  }
    
  OrderWorkflow parentOrderWorkflow = getParentOrderWorkflow();

  // Send ETA to parent workflow
  parentOrderWorkflow.updateEta(getOrderEta(orderId));

  // ...
```
Let's break this down a bit:
1. First, we create a stub to our parent workflow. We do this by calling the Cadence SDK to get the ID of the parent workflow, the create the stub for it.
2. Now that we have the stub, we gain access to the methods on it. We call **updateEta** and provide the ETA, which gets sent asynchronously.
3. This child workflow continues as the order is being prepared.
4. Our parent workflow receives the signal, and then it can also progress.

### Reflection - Instafood and child workflows

Above is a good example of how to use child workflows, and it's also a great example of **why** you want to use them.

Here we are calling a child workflow asynchronously and letting it signal to the parent workflow when the important information is ready. 
The parent workflow can keep working until this information is ready.
This is cruicial for a workflow such as this, where we want to inform the customer of an ETA or dispatch a courier, but it doesn't make sense for the restaurant preparation workflow to have this responsibility.

Similarly, the primary workflow doesn't concern itself with how each restaurant implements the various requirements for updating ETA and status. This makes the workflow simple to implement and easy to understand.
## Running a Happy-Path Scenario

To wrap-up, let’s run a whole order scenario. This scenario is part of the test suite included with our sample project. The only requirement is running both Instafood and MegaBurger server as described in the previous steps. This test case describes a client ordering through Instafood MegaBurger’s new *Vegan Burger* for pick-up:

Let's start by running the server. This can be accomplished by running
  ```bash
  cadence-cookbooks-instafood/instafood$ ./gradlew test
  ```

or *InstafoodApplicationTest* from your IDE

```java
class InstafoodApplicationTest {

    // ...

    @Test
    public void givenAnOrderItShouldBeSentToMegaBurgerAndBeDeliveredAccordingly() {
        FoodOrder order = new FoodOrder(Restaurant.MEGABURGER, "Vegan Burger", 2, "+54 11 2343-2324", "Díaz velez 433, La lucila", true);

        // Client orders food
        WorkflowExecution workflowExecution = WorkflowClient.start(orderWorkflow::orderFood, order);

        // Wait until order is pending Megaburger's acceptance
        await().until(() -> OrderStatus.PENDING.equals(orderWorkflow.getStatus()));

        // Megaburger accepts order and sends ETA
        megaBurgerOrdersApiClient.updateStatusAndEta(getLastOrderId(), "ACCEPTED", 15);
        await().until(() -> OrderStatus.ACCEPTED.equals(orderWorkflow.getStatus()));

        // Megaburger starts cooking order
        megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "COOKING");
        await().until(() -> OrderStatus.COOKING.equals(orderWorkflow.getStatus()));

        // Megaburger signals order is ready
        megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "READY");
        await().until(() -> OrderStatus.READY.equals(orderWorkflow.getStatus()));

        // Megaburger signals order has been picked-up
        megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "RESTAURANT_DELIVERED");
        await().until(() -> OrderStatus.RESTAURANT_DELIVERED.equals(orderWorkflow.getStatus()));

        await().until(() -> workflowHistoryHasEvent(workflowClient, workflowExecution, EventType.WorkflowExecutionCompleted));
    }
}
```

We have 3 actors in this scenario: Instafood, MegaBurger and the Client.


1. The Client sends order to Instafood.
2. Once the order reaches MegaBurger (order status is `PENDING`), MegaBurgers marks it as `ACCEPTED` and sends an ETA.
3. We then have the whole sequence of status updates:
   1. MegaBurger marks order as `COOKING`.
   2. MegaBurger marks order as `READY` (this means it's ready for delivery/pickup).
   3. MegaBurger marks order as `RESTAURANT_DELIVERD`.
4. Since this was an order created as pickup, once the Client has done so the workflow is complete.

## Wrapping Up

In this article we got first-hand experience with Cadence and how to use child workflows. We also showed you how to get a Cadence cluster running with our Instaclustr platform and how easy it is to get an application connect to it. If you’re interested in Cadence and want to learn more about it, you may read about other use cases and documentation at [Cadence workflow - Use cases](https://cadenceworkflow.io/docs/use-cases/).
