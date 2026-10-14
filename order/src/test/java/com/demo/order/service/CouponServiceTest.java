package com.demo.order.service;

import com.demo.order.model.Coupon;
import com.demo.order.model.DiscountQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponServiceTest {

    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService();
    }

    @Test
    void listsOnlyActiveCoupons() {
        assertThat(service.activeCoupons()).extracting(Coupon::code).containsExactly("BIG20", "WELCOME10");
    }

    @Test
    void quotesPercentageDiscount() {
        DiscountQuote quote = service.quote("welcome10", new BigDecimal("80.00"));

        assertThat(quote.code()).isEqualTo("WELCOME10");
        assertThat(quote.discount()).isEqualByComparingTo("8.00");
        assertThat(quote.totalToPay()).isEqualByComparingTo("72.00");
    }

    @Test
    void capsTheDiscount() {
        DiscountQuote quote = service.quote("BIG20", new BigDecimal("2000.00"));

        assertThat(quote.discount()).isEqualByComparingTo("250.00");
        assertThat(quote.totalToPay()).isEqualByComparingTo("1750.00");
    }

    @Test
    void enforcesMinimumOrder() {
        assertThatThrownBy(() -> service.quote("BIG20", new BigDecimal("499.99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum order");
    }

    @Test
    void rejectsExpiredAndUnknownCoupons() {
        assertThatThrownBy(() -> service.quote("SUMMER15", new BigDecimal("200")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> service.quote("FREE100", new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown coupon");
    }

    @Test
    void rejectsMissingInput() {
        assertThatThrownBy(() -> service.quote(" ", new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.quote("WELCOME10", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void couponCanBeUsedUp() {
        for (int i = 0; i < 10; i++) {
            service.redeem("BIG20", new BigDecimal("600.00"));
        }

        assertThat(service.activeCoupons()).extracting(Coupon::code).containsExactly("WELCOME10");
        assertThatThrownBy(() -> service.redeem("BIG20", new BigDecimal("600.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("used up");
    }
}
