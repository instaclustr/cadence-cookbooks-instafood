package com.instafood.orders.delivery;

import com.instafood.orders.dispatcher.domain.Restaurant;

public class CourierDeliveryJob {
    private final Restaurant restaurant;
    private final String address;
    private final String telephone;

    public CourierDeliveryJob(Restaurant restaurant, String address, String telephone) {
        this.restaurant = restaurant;
        this.address = address;
        this.telephone = telephone;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public String getAddress() {
        return address;
    }

    public String getTelephone() {
        return telephone;
    }
}
