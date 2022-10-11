package com.instafood.orders.dispatcher;

import com.instafood.orders.delivery.CourierDeliveryJob;
import com.instafood.orders.delivery.CourierDeliveryWorkflow;
import com.instafood.orders.dispatcher.domain.FoodOrder;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.instafood.orders.dispatcher.domain.Restaurant;
import com.instafood.orders.megaburger.MegaBurgerOrderWorkflow;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Workflow;

import java.time.Duration;

public class OrderWorkflowImpl implements OrderWorkflow {
    private OrderStatus currentStatus = OrderStatus.CREATED;
    private int etaInMinutes = -1;

    @Override
    public void orderFood(FoodOrder order) {
        if (Restaurant.MEGABURGER.equals(order.getRestaurant())) {
            MegaBurgerOrderWorkflow megaBurgerOrderWorkflow = Workflow
                    .newChildWorkflowStub(MegaBurgerOrderWorkflow.class);
            Async.procedure(megaBurgerOrderWorkflow::orderFood, order);
        } else {
            throw new RuntimeException(
                    String.format("%s invalid, Restaurant option not available", order.getRestaurant()));
        }
        // Wait for an ETA or abort if restaurant rejected order
        Workflow.await(() -> etaInMinutes != -1 || OrderStatus.REJECTED.equals(currentStatus));
        if (OrderStatus.REJECTED.equals(currentStatus)) {
            throw new RuntimeException("Order was rejected by restaurant");
        }

        if (!order.isPickup()) {
            // Wait for predicted ETA or until order marks as ready
            Workflow.await(Duration.ofMinutes(getTimeToSendCourier()), () -> OrderStatus.READY.equals(currentStatus));

            CourierDeliveryWorkflow courierDeliveryWorkflow = Workflow
                    .newChildWorkflowStub(CourierDeliveryWorkflow.class);
            Async.procedure(courierDeliveryWorkflow::deliverOrder,
                    new CourierDeliveryJob(order.getRestaurant(), order.getAddress(), order.getTelephone()));

            Workflow.await(() -> OrderStatus.COURIER_DELIVERED.equals(currentStatus));
        } else {
            Workflow.await(() -> OrderStatus.RESTAURANT_DELIVERED.equals(currentStatus));
        }
    }

    // TODO: this should be an activity
    private int getTimeToSendCourier() {
        return etaInMinutes;
    }

    @Override
    public OrderStatus getStatus() {
        return currentStatus;
    }

    @Override
    public int getEtaInMinutes() {
        return etaInMinutes;
    }

    @Override
    public void updateStatus(OrderStatus orderStatus) {
        this.currentStatus = orderStatus;
    }

    @Override
    public void updateEta(int etaInMinutes) {
        this.etaInMinutes = etaInMinutes;
    }
}
