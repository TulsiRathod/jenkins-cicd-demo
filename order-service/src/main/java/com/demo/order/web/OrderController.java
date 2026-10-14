package com.demo.order.web;

import com.demo.order.model.NewOrder;
import com.demo.order.model.Order;
import com.demo.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<Order> all() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Order one(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order place(@RequestBody NewOrder request) {
        return service.place(request);
    }

    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable String id) {
        return service.cancel(id);
    }

    @GetMapping("/revenue")
    public Map<String, BigDecimal> revenue() {
        return Map.of("totalRevenue", service.totalRevenue());
    }
}
