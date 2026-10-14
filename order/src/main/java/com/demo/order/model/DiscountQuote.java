package com.demo.order.model;

import java.math.BigDecimal;

public record DiscountQuote(String code, BigDecimal orderTotal, BigDecimal discount, BigDecimal totalToPay) {
}
