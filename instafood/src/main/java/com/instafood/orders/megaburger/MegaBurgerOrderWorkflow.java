package com.instafood.orders.megaburger;

import com.instafood.orders.dispatcher.domain.FoodOrder;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.WorkflowMethod;

public interface MegaBurgerOrderWorkflow {

    @WorkflowMethod
    void orderFood(FoodOrder order);

    @QueryMethod
    OrderStatus getStatus();
}
