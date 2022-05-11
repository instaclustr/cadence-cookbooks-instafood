package com.instafood.orders.dispatcher.domain;

public enum OrderStatus {
    CREATED,
    PENDING,
    ACCEPTED,
    REJECTED,
    COOKING,
    READY,
    COURIER_ACCEPTED,
    COURIER_REJECTED,
    RESTAURANT_DELIVERED,
    PICKED_UP,
    COURIER_DELIVERED;
}
