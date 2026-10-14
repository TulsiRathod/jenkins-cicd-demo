package com.demo.inventory.service;

import com.demo.inventory.model.Product;
import com.demo.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class InventoryService {

    private final ProductRepository repository;

    public InventoryService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> findAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::sku))
                .toList();
    }

    public Product findBySku(String sku) {
        return repository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    /** Sum of quantity x price across all products. */
    public BigDecimal totalInventoryValue() {
        return repository.findAll().stream()
                .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Takes stock out of inventory for an order. */
    public Product reserve(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Product product = findBySku(sku);
        if (product.quantity() < quantity) {
            throw new InsufficientStockException(sku, quantity, product.quantity());
        }
        return repository.save(product.withQuantity(product.quantity() - quantity));
    }
}
