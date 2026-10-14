package com.demo.order.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CouponApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsActiveCoupons() throws Exception {
        mvc.perform(get("/api/orders/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void quotesACoupon() throws Exception {
        mvc.perform(get("/api/orders/coupons/WELCOME10").param("total", "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(5.00))
                .andExpect(jsonPath("$.totalToPay").value(45.00));
    }

    @Test
    void redeemsACoupon() throws Exception {
        mvc.perform(post("/api/orders/coupons/BIG20/redeem").param("total", "600.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(120.00));
    }

    @Test
    void expiredCouponReturns409() throws Exception {
        mvc.perform(get("/api/orders/coupons/SUMMER15").param("total", "200"))
                .andExpect(status().isConflict());
    }
}
