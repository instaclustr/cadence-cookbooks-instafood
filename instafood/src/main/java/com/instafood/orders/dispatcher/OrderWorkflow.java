package com.instafood.orders.dispatcher;

import com.instafood.orders.dispatcher.domain.FoodOrder;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.WorkflowMethod;

public interface OrderWorkflow {
    @WorkflowMethod
    void orderFood(FoodOrder order);

    @QueryMethod
    OrderStatus getStatus();

    @SignalMethod
    void updateStatus(OrderStatus orderStatus);

    @QueryMethod
    int getEtaInMinutes();

    @SignalMethod
    void updateEta(int estimationInMinutes);
}
