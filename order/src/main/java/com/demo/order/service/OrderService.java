package com.demo.order.service;

import com.demo.order.model.NewOrder;
import com.demo.order.model.Order;
import com.demo.order.model.OrderStatus;
import com.demo.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public List<Order> findAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Order::id))
                .toList();
    }

    public Order findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order place(NewOrder request) {
        if (request.customer() == null || request.customer().isBlank()) {
            throw new IllegalArgumentException("Customer is required");
        }
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (request.unitPrice() == null || request.unitPrice().signum() <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
        Order order = new Order(repository.nextId(), request.customer(), request.sku(),
                request.quantity(), request.unitPrice(), OrderStatus.PLACED);
        return repository.save(order);
    }

    public Order cancel(String id) {
        Order order = findById(id);
        if (order.status() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order " + id + " is already cancelled");
        }
        return repository.save(order.withStatus(OrderStatus.CANCELLED));
    }

    /** Revenue from all orders that are not cancelled. */
    public BigDecimal totalRevenue() {
        return repository.findAll().stream()
                .filter(o -> o.status() == OrderStatus.PLACED)
                .map(Order::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
