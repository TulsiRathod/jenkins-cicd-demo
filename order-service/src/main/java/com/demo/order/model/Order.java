package com.demo.order.model;

import java.math.BigDecimal;

public record Order(String id, String customer, String sku, int quantity, BigDecimal unitPrice, OrderStatus status) {

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customer, sku, quantity, unitPrice, newStatus);
    }
}
