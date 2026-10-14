package com.demo.order.model;

import java.math.BigDecimal;

/** Request body for placing an order. */
public record NewOrder(String customer, String sku, int quantity, BigDecimal unitPrice) {
}
