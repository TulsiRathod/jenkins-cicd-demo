package com.demo.order.web;

import com.demo.order.model.Coupon;
import com.demo.order.model.DiscountQuote;
import com.demo.order.service.CouponService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders/coupons")
public class CouponController {

    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @GetMapping
    public List<Coupon> active() {
        return service.activeCoupons();
    }

    @GetMapping("/{code}")
    public DiscountQuote quote(@PathVariable String code, @RequestParam BigDecimal total) {
        return service.quote(code, total);
    }

    @PostMapping("/{code}/redeem")
    public DiscountQuote redeem(@PathVariable String code, @RequestParam BigDecimal total) {
        return service.redeem(code, total);
    }
}
