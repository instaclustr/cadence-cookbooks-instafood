package com.instafood.orders.megaburger.activities;

import com.instafood.orders.megaburger.MegaBurgerFoodOrder;
import com.instafood.orders.megaburger.service.MegaBurgerOrdersApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MegaBurgerRestApiOrderActivities implements MegaBurgerOrderActivities {

    private static Logger logger = LoggerFactory.getLogger(MegaBurgerRestApiOrderActivities.class);

    private final MegaBurgerOrdersApiClient megaBurgerApiClient;

    public MegaBurgerRestApiOrderActivities() {
        megaBurgerApiClient = new MegaBurgerOrdersApiClient();
    }

    @Override
    public Integer createOrder(MegaBurgerFoodOrder order) {
        MegaBurgerFoodOrder createdOrder = megaBurgerApiClient.create(order);
        logger.info("response: " + createdOrder);
        return createdOrder.getId();
    }

    @Override
    public MegaBurgerFoodOrder getOrderById(Integer orderId) {
        return megaBurgerApiClient.getById(orderId);
    }
}
