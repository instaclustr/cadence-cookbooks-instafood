package com.instafood.orders.megaburger.activities;

import com.instafood.orders.megaburger.MegaBurgerFoodOrder;
import com.uber.cadence.activity.ActivityMethod;

public interface MegaBurgerOrderActivities {

    @ActivityMethod
    Integer createOrder(MegaBurgerFoodOrder order);

    @ActivityMethod
    MegaBurgerFoodOrder getOrderById(Integer orderId);
}
