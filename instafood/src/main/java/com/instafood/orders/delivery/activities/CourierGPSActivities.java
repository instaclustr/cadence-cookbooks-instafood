package com.instafood.orders.delivery.activities;

import com.uber.cadence.activity.ActivityMethod;

public interface CourierGPSActivities {
    @ActivityMethod
    boolean registerDeliveryGPSTracking(String pickupLocation, String deliveryLocation);
}
