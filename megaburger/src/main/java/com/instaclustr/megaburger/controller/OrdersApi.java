package com.instaclustr.megaburger.controller;

import com.instaclustr.megaburger.domain.Order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class OrdersApi {

    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(OrdersApi.class);

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getAll() {
        logger.info("New request: Route: /orders; Method: GET");
        return ResponseEntity.ok(orders.values().stream()
                .sorted(Comparator.comparing(Order::getId))
                .collect(Collectors.toList()));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getById(@PathVariable Integer orderId) {
        logger.info("New request: Route: /orders/{}; Method: GET", orderId);
        return ResponseEntity.ok(orders.get(orderId));
    }

    @PatchMapping("/orders/{orderId}")
    public ResponseEntity<Order> update(@PathVariable Integer orderId, @RequestBody Order newOrder) {
        logger.info("New request: Route: /orders/{}; Method: PATCH;", orderId);
        Order order = orders.get(orderId);

        Optional.ofNullable(newOrder.getStatus()).ifPresent(order::updateStatus);
        Optional.ofNullable(newOrder.getEtaMinutes()).ifPresent(order::updateEta);

        orders.put(orderId, order);

        return ResponseEntity.ok(order);
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody Order order) {
        logger.info("New request: Route: /orders; Method: POST");
        order.setId(orders.size());

        orders.put(order.getId(), order);

        return ResponseEntity.status(201).body(order);
    }

    public void deleteAll() {
        orders.clear();
    }
}
