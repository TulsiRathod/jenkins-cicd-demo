package com.demo.order.service;

import com.demo.order.model.NewOrder;
import com.demo.order.model.Order;
import com.demo.order.model.OrderStatus;
import com.demo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new OrderRepository());
    }

    @Test
    void listsSeededOrders() {
        assertThat(service.findAll()).extracting(Order::id).containsExactly("ORD-1001", "ORD-1002");
    }

    @Test
    void unknownOrderThrowsNotFound() {
        assertThatThrownBy(() -> service.findById("ORD-9999"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void placesOrderWithNewId() {
        Order order = service.place(new NewOrder("carol", "KEY-003", 2, new BigDecimal("79.00")));

        assertThat(order.id()).isEqualTo("ORD-1003");
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.total()).isEqualByComparingTo("158.00");
    }

    @Test
    void rejectsInvalidOrders() {
        assertThatThrownBy(() -> service.place(new NewOrder("", "KEY-003", 1, BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.place(new NewOrder("carol", "KEY-003", 0, BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.place(new NewOrder("carol", "KEY-003", 1, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelsOrderOnlyOnce() {
        assertThat(service.cancel("ORD-1001").status()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel("ORD-1001"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revenueExcludesCancelledOrders() {
        // 1*899.99 + 3*24.50
        assertThat(service.totalRevenue()).isEqualByComparingTo("973.49");

        service.cancel("ORD-1001");

        assertThat(service.totalRevenue()).isEqualByComparingTo("73.50");
    }
}
