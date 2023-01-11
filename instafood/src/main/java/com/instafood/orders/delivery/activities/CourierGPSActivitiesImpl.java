package com.instafood.orders.delivery.activities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CourierGPSActivitiesImpl implements CourierGPSActivities{

    private static Logger logger = LoggerFactory.getLogger(CourierGPSActivitiesImpl.class);

    public CourierGPSActivitiesImpl() {
        
    }

    @Override
    public boolean registerDeliveryGPSTracking(String pickupLocation, String deliveryLocation) {
        // register a delivery trip
        logger.info("GPS tracking enabled. Pickup: {}, delivery to {}", pickupLocation, deliveryLocation);
        return true;
    }
}
