package com.instafood.orders.delivery;

import com.instafood.orders.dispatcher.OrderWorkflow;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.uber.cadence.workflow.Workflow;

public class CourierDeliveryWorkflowImpl implements CourierDeliveryWorkflow {

    private CourierDeliveryStatus currentStatus = CourierDeliveryStatus.CREATED;

    @Override
    public void deliverOrder(CourierDeliveryJob courierDeliveryJob) {
        OrderWorkflow parentOrderWorkflow = getParentOrderWorkflow();

        Workflow.await(() -> !CourierDeliveryStatus.CREATED.equals(currentStatus));

        if (CourierDeliveryStatus.REJECTED.equals(currentStatus)) {
            parentOrderWorkflow.updateStatus(OrderStatus.COURIER_REJECTED);
            throw new RuntimeException("Courier rejected job");
        }
        parentOrderWorkflow.updateStatus(OrderStatus.COURIER_ACCEPTED);

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
}
