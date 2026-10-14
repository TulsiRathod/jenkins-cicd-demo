package com.demo.inventory.model;

import java.math.BigDecimal;

public record Product(String sku, String name, int quantity, BigDecimal price) {

    public Product withQuantity(int newQuantity) {
        return new Product(sku, name, newQuantity, price);
    }
}
