package com.instafood.orders.delivery;

import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.WorkflowMethod;

public interface CourierDeliveryWorkflow {
    @WorkflowMethod
    void deliverOrder(CourierDeliveryJob courierDeliveryJob);

    @SignalMethod
    void updateStatus(CourierDeliveryStatus status);
}
