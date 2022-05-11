package com.instafood.orders.megaburger;

import com.instafood.orders.dispatcher.OrderWorkflow;
import com.instafood.orders.dispatcher.domain.FoodOrder;
import com.instafood.orders.dispatcher.domain.OrderStatus;
import com.instafood.orders.megaburger.activities.MegaBurgerOrderActivities;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class MegaBurgerOrderWorkflowImpl implements MegaBurgerOrderWorkflow {

    private static final Logger logger = Workflow.getLogger(MegaBurgerOrderWorkflowImpl.class);

    private final MegaBurgerOrderActivities megaBurgerOrderActivities =
            Workflow.newActivityStub(MegaBurgerOrderActivities.class,
                    new ActivityOptions.Builder()
                            .setRetryOptions(new RetryOptions.Builder()
                                    .setInitialInterval(Duration.ofSeconds(10))
                                    .setMaximumAttempts(3)
                                    .build())
                            .setScheduleToCloseTimeout(Duration.ofMinutes(5)).build());

    private OrderStatus currentStatus = OrderStatus.CREATED;

    @Override
    public void orderFood(FoodOrder order) {
        OrderWorkflow parentOrderWorkflow = getParentOrderWorkflow();

        Integer orderId = megaBurgerOrderActivities.createOrder(mapMegaBurgerFoodOrder(order));
        logger.info("Placed order with id: " + orderId);
        updateOrderStatus(parentOrderWorkflow, OrderStatus.PENDING);

        // Poll until Order is accepted/rejected
        updateOrderStatus(parentOrderWorkflow, pollOrderStatusTransition(orderId, OrderStatus.PENDING));

        if (OrderStatus.REJECTED.equals(currentStatus)) {
            throw new RuntimeException("Order with id " + orderId + " was rejected");
        }
        // Send ETA to parent workflow
        parentOrderWorkflow.updateEta(getOrderEta(orderId));

        // Poll until Order is cooking
        updateOrderStatus(parentOrderWorkflow, pollOrderStatusTransition(orderId, OrderStatus.ACCEPTED));
        // Poll until Order is ready
        updateOrderStatus(parentOrderWorkflow, pollOrderStatusTransition(orderId, OrderStatus.COOKING));
        // Poll until Order is delivered
        updateOrderStatus(parentOrderWorkflow, pollOrderStatusTransition(orderId, OrderStatus.READY));
    }

    private Integer getOrderEta(Integer orderId) {
        return megaBurgerOrderActivities.getOrderById(orderId).getEtaMinutes();
    }

    private OrderWorkflow getParentOrderWorkflow() {
        String parentOrderWorkflowId = Workflow.getWorkflowInfo().getParentWorkflowId();
        return Workflow.newExternalWorkflowStub(OrderWorkflow.class, parentOrderWorkflowId);
    }

    private void updateOrderStatus(OrderWorkflow parentOrderWorkflow, OrderStatus latestStatus) {
        currentStatus = latestStatus;
        parentOrderWorkflow.updateStatus(currentStatus);
    }

    private MegaBurgerFoodOrder mapMegaBurgerFoodOrder(FoodOrder order) {
        MegaBurgerFoodOrder megaBurgerOrder = new MegaBurgerFoodOrder();
        megaBurgerOrder.setMeal(order.getMeal());
        megaBurgerOrder.setQuantity(order.getQuantity());
        return megaBurgerOrder;
    }

    private OrderStatus pollOrderStatusTransition(Integer orderId, OrderStatus orderStatus) {
        OrderStatus polledStatus = megaBurgerOrderActivities.getOrderById(orderId).getStatus();
        while (orderStatus.equals(polledStatus)) {
            Workflow.sleep(Duration.ofSeconds(10));
            polledStatus = megaBurgerOrderActivities.getOrderById(orderId).getStatus();
            logger.debug("[Polling] order: " + orderId + ", current status: " + polledStatus);
        }
        return polledStatus;
    }

    @Override
    public OrderStatus getStatus() {
        return currentStatus;
    }

}
