package com.demo.order.model;

import java.math.BigDecimal;

public record Coupon(String code, int percentOff, BigDecimal minimumOrder, int usesLeft, boolean active) {

    public Coupon withUsesLeft(int remaining) {
        return new Coupon(code, percentOff, minimumOrder, remaining, active);
    }
}
