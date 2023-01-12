import com.instafood.orders.delivery.CourierDeliveryStatus;
import com.instafood.orders.delivery.CourierDeliveryWorkflow;
import com.instafood.orders.delivery.CourierDeliveryWorkflowImpl;
import com.instafood.orders.dispatcher.OrderWorkflow;
import com.instafood.orders.dispatcher.domain.FoodOrder;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.instafood.orders.dispatcher.domain.Restaurant;
import com.instafood.orders.megaburger.MegaBurgerFoodOrder;
import com.instafood.orders.megaburger.service.MegaBurgerOrdersApiClient;
import com.uber.cadence.EventType;
import com.uber.cadence.GetWorkflowExecutionHistoryRequest;
import com.uber.cadence.History;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.ListOpenWorkflowExecutionsRequest;
import com.uber.cadence.StartTimeFilter;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowExecutionInfo;
import com.uber.cadence.WorkflowTypeFilter;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.testing.WorkflowReplayer;
import org.apache.thrift.TException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstafoodApplicationTest {

        private WorkflowClient workflowClient;
        private MegaBurgerOrdersApiClient megaBurgerOrdersApiClient;
        private OrderWorkflow orderWorkflow;

        @BeforeEach
        public void setUp() {
                Awaitility.setDefaultTimeout(Duration.ofMinutes(1));

                WorkflowOptions workflowOptions = new WorkflowOptions.Builder()
                                .setExecutionStartToCloseTimeout(Duration.ofMinutes(5))
                                .setTaskList(InstafoodApplication.TASK_LIST)
                                .build();
                workflowClient = WorkflowClient.newInstance(
                                new WorkflowServiceTChannel(ClientOptions.newBuilder()
                                                .setHost(InstafoodApplication.getCadenceHostProperty())
                                                .setPort(7933)
                                                .build()),
                                WorkflowClientOptions.newBuilder().setDomain(InstafoodApplication.DOMAIN).build());
                orderWorkflow = workflowClient.newWorkflowStub(OrderWorkflow.class, workflowOptions);

                megaBurgerOrdersApiClient = new MegaBurgerOrdersApiClient();
        }

        @Test
        public void givenAnOrderItShouldBeSentToMegaBurgerAndBeDeliveredAccordingly() {
                FoodOrder order = new FoodOrder(Restaurant.MEGABURGER, "vegan burger", 2, "+54 112343-2324",
                                "Díaz velez 433, La lucila", true);

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

                await().until(
                                () -> workflowHistoryHasEvent(workflowClient, workflowExecution,
                                                EventType.WorkflowExecutionCompleted));
        }

        private Integer getLastOrderId() {
                List<MegaBurgerFoodOrder> orders = megaBurgerOrdersApiClient.getAll();
                return orders.get(orders.size() - 1).getId();
        }

        private boolean workflowHistoryHasEvent(WorkflowClient workflowClient, WorkflowExecution workflowExecution,
                        EventType eventType) throws TException {
                GetWorkflowExecutionHistoryRequest request = new GetWorkflowExecutionHistoryRequest()
                                .setExecution(workflowExecution)
                                .setDomain(InstafoodApplication.DOMAIN);

                List<HistoryEvent> events = workflowClient.getService()
                                .GetWorkflowExecutionHistory(request).getHistory().getEvents();

                return events.stream()
                                .map(HistoryEvent::getEventType)
                                .anyMatch(eventType::equals);
        }

        @Test
        public void givenAnOrderWhenMegaBurgerRejectsItShouldEndWorkflow() {
                FoodOrder order = new FoodOrder(Restaurant.MEGABURGER, "vegan burger", 2, "+54 112343-2324",
                                "Díaz velez 433, La lucila", true);

                // Client orders food
                WorkflowExecution workflowExecution = WorkflowClient.start(orderWorkflow::orderFood, order);

                // Wait until order is pending Megaburger's acceptance
                await().until(() -> OrderStatus.PENDING.equals(orderWorkflow.getStatus()));

                // Megaburger rejects order
                megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "REJECTED");
                await().until(() -> OrderStatus.REJECTED.equals(orderWorkflow.getStatus()));

                await().until(
                                () -> workflowHistoryHasEvent(workflowClient, workflowExecution,
                                                EventType.WorkflowExecutionFailed));
        }

        @Test
        public void givenAnOrderWithDeliveryItShoulBeSentToMegaBurgerAndDeliveredByACourierAccordingly() {
                FoodOrder order = new FoodOrder(Restaurant.MEGABURGER, "vegan burger", 2, "+54 112343-2324",
                                "Díaz velez 433, La lucila", false);

                // Client orders food
                WorkflowExecution workflowExecution = WorkflowClient.start(orderWorkflow::orderFood, order);

                // Wait until order is pending Megaburger's acceptance
                await().until(() -> OrderStatus.PENDING.equals(orderWorkflow.getStatus()));

                // Megaburger accepts order and sends ETA
                megaBurgerOrdersApiClient.updateStatusAndEta(getLastOrderId(), "ACCEPTED", 15);

                // Wait until order is accepted and we have an ETA
                await().until(() -> OrderStatus.ACCEPTED.equals(orderWorkflow.getStatus()));
                await().until(() -> orderWorkflow.getEtaInMinutes() != -1);

                // Megaburger marks order as ready
                megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "READY");

                await().until(() -> getOpenCourierDeliveryWorkflowsWithParentId(workflowExecution.getWorkflowId())
                                .size() != 0);
                String courierDeliveryWorkflowId = getOpenCourierDeliveryWorkflowsWithParentId(
                                workflowExecution.getWorkflowId()).get(0)
                                .getExecution().getWorkflowId();
                CourierDeliveryWorkflow courierDeliveryWorkflow = workflowClient.newWorkflowStub(
                                CourierDeliveryWorkflow.class,
                                courierDeliveryWorkflowId);

                // Courier accepts order
                courierDeliveryWorkflow.updateStatus(CourierDeliveryStatus.ACCEPTED);
                await().until(() -> OrderStatus.COURIER_ACCEPTED.equals(orderWorkflow.getStatus()));

                // Courier picked up order
                courierDeliveryWorkflow.updateStatus(CourierDeliveryStatus.PICKED_UP);
                // Megaburger marks order as delivered
                megaBurgerOrdersApiClient.updateStatus(getLastOrderId(), "RESTAURANT_DELIVERED");

                // Courier delivered order
                courierDeliveryWorkflow.updateStatus(CourierDeliveryStatus.DELIVERED);
                await().until(() -> OrderStatus.COURIER_DELIVERED.equals(orderWorkflow.getStatus()));

                await().until(
                                () -> workflowHistoryHasEvent(workflowClient, workflowExecution,
                                                EventType.WorkflowExecutionCompleted));

                // All new courier workflows should support GPS tracking, since this is a new
                // job it will return true
                assertTrue(courierDeliveryWorkflow.courierSupportsGPSTracking());
        }

        @Test
        public void givenCourierWorkflowWhenGpsNotSupportedThenHistoryReplaysCorrectly() throws Exception {
                // We have stored the history for a workflow that was executed before GPS
                // support was added into a file - "resources/history-gps-not-supported.json"

                // We use the workflow replayer to ensure that our legacy workflow can still
                // execute correctly.

                WorkflowReplayer.replayWorkflowExecutionFromResource("history-gps-not-supported.json",
                                CourierDeliveryWorkflowImpl.class);

                // If we did not implement our version check, this method would throw an
                // exception -- try it yourself by editing the CourierDeliveryWorkflowImpl
                // class!
        }

        private List<WorkflowExecutionInfo> getOpenCourierDeliveryWorkflowsWithParentId(String parentWorkflowId) {
                try {
                        return workflowClient.getService()
                                        .ListOpenWorkflowExecutions(
                                                        new ListOpenWorkflowExecutionsRequest()
                                                                        .setDomain(InstafoodApplication.DOMAIN)
                                                                        .setStartTimeFilter(new StartTimeFilter()
                                                                                        .setEarliestTime(0)
                                                                                        .setLatestTime(Instant.now()
                                                                                                        .toEpochMilli()
                                                                                                        * 1000000))
                                                                        .setTypeFilter(
                                                                                        new WorkflowTypeFilter()
                                                                                                        .setName("CourierDeliveryWorkflow::deliverOrder")))
                                        .getExecutions().stream()
                                        .filter(workflowExecutionInfo -> parentWorkflowId
                                                        .equals(getParentWorkflowId(workflowExecutionInfo)))
                                        .collect(Collectors.toList());
                } catch (TException e) {
                        throw new RuntimeException(e);
                }
        }

        private String getParentWorkflowId(WorkflowExecutionInfo workflowExecutionInfo) {
                return getWorkflowExecutionHistory(workflowExecutionInfo)
                                .getEvents().get(0)
                                .getWorkflowExecutionStartedEventAttributes()
                                .getParentWorkflowExecution().getWorkflowId();
        }

        private History getWorkflowExecutionHistory(WorkflowExecutionInfo workflowExecutionInfo) {
                try {
                        return workflowClient.getService()
                                        .GetWorkflowExecutionHistory(new GetWorkflowExecutionHistoryRequest()
                                                        .setDomain(InstafoodApplication.DOMAIN)
                                                        .setExecution(workflowExecutionInfo.getExecution()))
                                        .getHistory();
                } catch (TException e) {
                        throw new RuntimeException(e);
                }
        }
}