package com.instafood.orders.megaburger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.instafood.orders.dispatcher.domain.OrderStatus;

public class MegaBurgerFoodOrder {
    private Integer id;
    private String meal;
    private Integer quantity;
    private OrderStatus status;
    @JsonProperty("eta_minutes")
    private Integer etaMinutes;

    public MegaBurgerFoodOrder() {
    }

    public Integer getId() {
        return id;
    }

    public String getMeal() {
        return meal;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setMeal(String meal) {
        this.meal = meal;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }
}
