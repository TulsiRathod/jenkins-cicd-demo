package com.demo.frontend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Backends point to a port where nothing listens, so we see how the frontend behaves when they are down
@SpringBootTest(properties = {
        "store.inventory-url=http://localhost:1",
        "store.order-url=http://localhost:1"
})
@AutoConfigureMockMvc
class FrontendIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void servesTheStorePage() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Store App")));
    }

    @Test
    void exposesVersion() throws Exception {
        mvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("frontend"))
                .andExpect(jsonPath("$.environment").value("local"));
    }

    @Test
    void statusShowsUnavailableBackends() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("local"))
                .andExpect(jsonPath("$.inventory").value("unavailable"))
                .andExpect(jsonPath("$.order").value("unavailable"));
    }

    @Test
    void productsReturn503WhenInventoryIsDown() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("inventory service is not reachable"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
