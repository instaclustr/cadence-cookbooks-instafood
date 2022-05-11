package com.instafood.orders.dispatcher.domain;

public class FoodOrder {
    private final Restaurant restaurant;
    private final String meal;
    private final int quantity;
    private final String telephone;
    private final String address;
    private final boolean pickup;

    public FoodOrder(Restaurant restaurant, String meal, int quantity, String telephone, String address, boolean pickup) {
        this.restaurant = restaurant;
        this.meal = meal;
        this.quantity = quantity;
        this.telephone = telephone;
        this.address = address;
        this.pickup = pickup;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public String getMeal() {
        return meal;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getAddress() {
        return address;
    }

    public boolean isPickup() {
        return pickup;
    }
}
