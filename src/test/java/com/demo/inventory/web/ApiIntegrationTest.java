package com.demo.inventory.web;

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
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsProducts() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void getsSingleProduct() throws Exception {
        mvc.perform(get("/api/products/LAP-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        mvc.perform(get("/api/products/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product not found: NOPE"));
    }

    @Test
    void reserveReducesStock() throws Exception {
        mvc.perform(post("/api/products/MOU-002/reserve").param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(115));
    }

    @Test
    void overReservingReturns409() throws Exception {
        mvc.perform(post("/api/products/HDP-005/reserve").param("quantity", "10"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidQuantityReturns400() throws Exception {
        mvc.perform(post("/api/products/LAP-001/reserve").param("quantity", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsInventoryValue() throws Exception {
        mvc.perform(get("/api/inventory/value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(18867.65));
    }

    @Test
    void exposesVersion() throws Exception {
        mvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("inventory-service"))
                .andExpect(jsonPath("$.environment").value("local"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
