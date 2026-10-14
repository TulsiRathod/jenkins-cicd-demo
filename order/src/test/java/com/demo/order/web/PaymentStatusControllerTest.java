package com.demo.order.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusControllerTest {

    @Test
    void notConfiguredWithoutKey() {
        assertThat(new PaymentStatusController("").status())
                .containsEntry("configured", false)
                .doesNotContainKey("keyEndsWith");
    }

    @Test
    void showsOnlyTheLastFourCharacters() {
        var status = new PaymentStatusController("pk_demo_7f3b9a1e2c7e").status();

        assertThat(status).containsEntry("configured", true).containsEntry("keyEndsWith", "2c7e");
        assertThat(status.toString()).doesNotContain("pk_demo");
    }
}
