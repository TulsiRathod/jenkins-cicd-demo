package com.demo.inventory.web;

import com.demo.inventory.model.Product;
import com.demo.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final InventoryService service;

    public ProductController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public List<Product> all() {
        return service.findAll();
    }

    @GetMapping("/products/{sku}")
    public Product one(@PathVariable String sku) {
        return service.findBySku(sku);
    }

    @PostMapping("/products/{sku}/reserve")
    public Product reserve(@PathVariable String sku, @RequestParam int quantity) {
        return service.reserve(sku, quantity);
    }

    @GetMapping("/inventory/value")
    public Map<String, BigDecimal> inventoryValue() {
        return Map.of("totalValue", service.totalInventoryValue());
    }

    @GetMapping("/products/low-stock")
    public List<Product> lowStock(@RequestParam(defaultValue = "5") int threshold) {
        return service.lowStock(threshold);
    }
}
