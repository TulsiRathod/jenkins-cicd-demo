package com.demo.order.service;

import com.demo.order.model.Coupon;
import com.demo.order.model.DiscountQuote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CouponService {

    private static final BigDecimal MAX_DISCOUNT = new BigDecimal("250.00");

    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();

    public CouponService() {
        add(new Coupon("WELCOME10", 10, BigDecimal.ZERO, 100, true));
        add(new Coupon("BIG20", 20, new BigDecimal("500.00"), 10, true));
        add(new Coupon("SUMMER15", 15, new BigDecimal("100.00"), 50, false));
    }

    private void add(Coupon coupon) {
        coupons.put(coupon.code(), coupon);
    }

    /** Coupons customers can use right now. */
    public List<Coupon> activeCoupons() {
        return coupons.values().stream()
                .filter(Coupon::active)
                .filter(c -> c.usesLeft() > 0)
                .sorted(Comparator.comparing(Coupon::code))
                .toList();
    }

    /** What would this coupon take off this order? Does not use the coupon up. */
    public DiscountQuote quote(String code, BigDecimal orderTotal) {
        Coupon coupon = validCoupon(code, orderTotal);
        BigDecimal discount = orderTotal
                .multiply(BigDecimal.valueOf(coupon.percentOff()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (discount.compareTo(MAX_DISCOUNT) > 0) {
            discount = MAX_DISCOUNT;
        }
        return new DiscountQuote(coupon.code(), orderTotal, discount, orderTotal.subtract(discount));
    }

    /** Applies the coupon to an order and counts one use. */
    public DiscountQuote redeem(String code, BigDecimal orderTotal) {
        DiscountQuote quote = quote(code, orderTotal);
        Coupon coupon = coupons.get(quote.code());
        coupons.put(coupon.code(), coupon.withUsesLeft(coupon.usesLeft() - 1));
        return quote;
    }

    private Coupon validCoupon(String code, BigDecimal orderTotal) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code is required");
        }
        if (orderTotal == null || orderTotal.signum() <= 0) {
            throw new IllegalArgumentException("Order total must be positive");
        }
        Coupon coupon = coupons.get(code.trim().toUpperCase());
        if (coupon == null) {
            throw new IllegalArgumentException("Unknown coupon: " + code);
        }
        if (!coupon.active()) {
            throw new IllegalStateException("Coupon " + coupon.code() + " has expired");
        }
        if (coupon.usesLeft() <= 0) {
            throw new IllegalStateException("Coupon " + coupon.code() + " has been used up");
        }
        if (orderTotal.compareTo(coupon.minimumOrder()) < 0) {
            throw new IllegalArgumentException(
                    "Coupon " + coupon.code() + " needs a minimum order of " + coupon.minimumOrder());
        }
        return coupon;
    }
}
