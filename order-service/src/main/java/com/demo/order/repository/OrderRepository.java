package com.demo.order.repository;

import com.demo.order.model.Order;
import com.demo.order.model.OrderStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory store so the demo needs no database. */
@Repository
public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    public OrderRepository() {
        save(new Order(nextId(), "alice", "LAP-001", 1, new BigDecimal("899.99"), OrderStatus.PLACED));
        save(new Order(nextId(), "bob", "MOU-002", 3, new BigDecimal("24.50"), OrderStatus.PLACED));
    }

    public String nextId() {
        return "ORD-" + sequence.incrementAndGet();
    }

    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }
}
