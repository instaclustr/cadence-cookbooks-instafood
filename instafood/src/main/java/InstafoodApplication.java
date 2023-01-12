import com.google.common.base.Strings;
import com.instafood.orders.delivery.CourierDeliveryWorkflowImpl;
import com.instafood.orders.delivery.activities.CourierGPSActivitiesImpl;
import com.instafood.orders.dispatcher.OrderWorkflowImpl;
import com.instafood.orders.megaburger.MegaBurgerOrderWorkflowImpl;
import com.instafood.orders.megaburger.activities.MegaBurgerRestApiOrderActivities;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;

import java.io.IOException;
import java.util.Properties;

public class InstafoodApplication {

    public static final String DOMAIN = "instafood";
    public static final String TASK_LIST = "test-worker-task-list";

    public static void main(String[] args) {
        WorkflowClient workflowClient = WorkflowClient.newInstance(
                new WorkflowServiceTChannel(ClientOptions.newBuilder()
                        .setHost(getCadenceHostProperty())
                        .setPort(7933)
                        .build()),
                WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
        // Get worker to poll the task list.
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(TASK_LIST);
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class, MegaBurgerOrderWorkflowImpl.class,
                CourierDeliveryWorkflowImpl.class);
        worker.registerActivitiesImplementations(new MegaBurgerRestApiOrderActivities(),
                new CourierGPSActivitiesImpl());
        factory.start();
    }

    public static String getCadenceHostProperty() {
        Properties appProperties = new Properties();
        try {
            appProperties.load(InstafoodApplication.class.getResourceAsStream("/instafood.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String cadenceHost = appProperties.getProperty("cadenceHost");
        if (Strings.isNullOrEmpty(cadenceHost)) {
            throw new RuntimeException(
                    "No cadence hosts are configured, you can set the value in the 'instafood.properties' file, exiting.");
        }
        return cadenceHost;
    }

}
