package com.demo.inventory.service;

import com.demo.inventory.model.Product;
import com.demo.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(new ProductRepository());
    }

    @Test
    void listsAllProductsSortedBySku() {
        assertThat(service.findAll())
                .extracting(Product::sku)
                .containsExactly("HDP-005", "KEY-003", "LAP-001", "MON-004", "MOU-002");
    }

    @Test
    void findsProductBySku() {
        assertThat(service.findBySku("LAP-001").name()).isEqualTo("Laptop");
    }

    @Test
    void unknownSkuThrowsNotFound() {
        assertThatThrownBy(() -> service.findBySku("NOPE"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void calculatesTotalInventoryValue() {
        // 15*899.99 + 120*24.50 + 4*79.00 + 8*249.00 + 2*59.90
        assertThat(service.totalInventoryValue()).isEqualByComparingTo(new BigDecimal("18867.65"));
    }

    @Test
    void reservingReducesStock() {
        Product updated = service.reserve("MOU-002", 20);

        assertThat(updated.quantity()).isEqualTo(100);
        assertThat(service.findBySku("MOU-002").quantity()).isEqualTo(100);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        assertThatThrownBy(() -> service.reserve("HDP-005", 3))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void cannotReserveZeroOrNegative() {
        assertThatThrownBy(() -> service.reserve("LAP-001", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findsLowStockProducts() {
        assertThat(service.lowStock(5))
                .extracting(Product::sku)
                .containsExactly("HDP-005", "KEY-003");
    }
}
