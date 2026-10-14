package com.demo.inventory.repository;

import com.demo.inventory.model.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store so the demo needs no database. */
@Repository
public class ProductRepository {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        save(new Product("LAP-001", "Laptop", 15, new BigDecimal("899.99")));
        save(new Product("MOU-002", "Wireless Mouse", 120, new BigDecimal("24.50")));
        save(new Product("KEY-003", "Mechanical Keyboard", 4, new BigDecimal("79.00")));
        save(new Product("MON-004", "27\" Monitor", 8, new BigDecimal("249.00")));
        save(new Product("HDP-005", "Headphones", 2, new BigDecimal("59.90")));
    }

    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(products.get(sku));
    }

    public Product save(Product product) {
        products.put(product.sku(), product);
        return product;
    }
}
