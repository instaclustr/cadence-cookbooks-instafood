package com.instaclustr.megaburger.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Order {
    private Integer id;
    private final String meal;
    private final Integer quantity;
    private OrderStatus status;
    @JsonProperty("eta_minutes")
    private Integer etaMinutes;

    public Order(String meal, Integer quantity) {
        this.meal = meal;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
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

    public void setId(Integer nextInt) {
        this.id = nextInt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public void updateEta(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }
}
