package com.demo.order.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Is the payment provider key configured? The key itself comes from Jenkins Credentials at deploy time
 * (env var PAYMENT_API_KEY) and is never returned - only its last 4 characters, so you can see a rotation.
 */
@RestController
public class PaymentStatusController {

    private final String apiKey;

    public PaymentStatusController(@Value("${PAYMENT_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @GetMapping("/api/orders/payment-status")
    public Map<String, Object> status() {
        if (apiKey.isBlank()) {
            return Map.of("configured", false);
        }
        String ending = apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : "";
        return Map.of("configured", true, "keyEndsWith", ending);
    }
}
