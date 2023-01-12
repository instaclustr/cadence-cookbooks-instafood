package com.instafood.orders.delivery;

import java.time.Duration;

import com.instafood.orders.delivery.activities.CourierGPSActivities;
import com.instafood.orders.dispatcher.OrderWorkflow;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.workflow.Workflow;

public class CourierDeliveryWorkflowImpl implements CourierDeliveryWorkflow {

    private CourierDeliveryStatus currentStatus = CourierDeliveryStatus.CREATED;
    private boolean supportsGpsTracking = false;

    private final CourierGPSActivities courierGPSActivities = Workflow.newActivityStub(CourierGPSActivities.class,
            new ActivityOptions.Builder()
                    .setRetryOptions(new RetryOptions.Builder()
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setMaximumAttempts(3)
                            .build())
                    .setScheduleToCloseTimeout(Duration.ofMinutes(5)).build());

    @Override
    public void deliverOrder(CourierDeliveryJob courierDeliveryJob) {
        OrderWorkflow parentOrderWorkflow = getParentOrderWorkflow();

        Workflow.await(() -> !CourierDeliveryStatus.CREATED.equals(currentStatus));

        if (CourierDeliveryStatus.REJECTED.equals(currentStatus)) {
            parentOrderWorkflow.updateStatus(OrderStatus.COURIER_REJECTED);
            throw new RuntimeException("Courier rejected job");
        }
        parentOrderWorkflow.updateStatus(OrderStatus.COURIER_ACCEPTED);

        // Added new GPS tracking functionality
        int workflowVersion = Workflow.getVersion("GPSTrackingSupported", Workflow.DEFAULT_VERSION, 1);
        if (workflowVersion >= 1) {
            supportsGpsTracking = courierGPSActivities.registerDeliveryGPSTracking(
                    courierDeliveryJob.getRestaurant().toString(),
                    courierDeliveryJob.getAddress());
        }

        Workflow.await(() -> CourierDeliveryStatus.PICKED_UP.equals(currentStatus));
        parentOrderWorkflow.updateStatus(OrderStatus.PICKED_UP);

        Workflow.await(() -> CourierDeliveryStatus.DELIVERED.equals(currentStatus));
        parentOrderWorkflow.updateStatus(OrderStatus.COURIER_DELIVERED);
    }

    private OrderWorkflow getParentOrderWorkflow() {
        String parentOrderWorkflowId = Workflow.getWorkflowInfo().getParentWorkflowId();
        return Workflow.newExternalWorkflowStub(OrderWorkflow.class, parentOrderWorkflowId);
    }

    @Override
    public void updateStatus(CourierDeliveryStatus status) {
        this.currentStatus = status;
    }

    @Override
    public boolean courierSupportsGPSTracking() {
        // TODO Auto-generated method stub
        return supportsGpsTracking;
    }
}
