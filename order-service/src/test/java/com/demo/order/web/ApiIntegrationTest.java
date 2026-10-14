package com.demo.order.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsOrders() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getsSingleOrder() throws Exception {
        mvc.perform(get("/api/orders/ORD-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer").value("alice"));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mvc.perform(get("/api/orders/ORD-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void placesOrder() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customer":"carol","sku":"KEY-003","quantity":2,"unitPrice":79.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ORD-1003"))
                .andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void invalidOrderReturns400() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customer":"carol","sku":"KEY-003","quantity":0,"unitPrice":79.00}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellingTwiceReturns409() throws Exception {
        mvc.perform(post("/api/orders/ORD-1002/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/orders/ORD-1002/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsRevenue() throws Exception {
        mvc.perform(get("/api/orders/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(973.49));
    }

    @Test
    void exposesVersion() throws Exception {
        mvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("order-service"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
