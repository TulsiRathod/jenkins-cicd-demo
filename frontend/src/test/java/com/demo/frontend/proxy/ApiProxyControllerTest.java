package com.demo.frontend.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiProxyControllerTest {

    private MockRestServiceServer backend;
    private ApiProxyController proxy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        backend = MockRestServiceServer.bindTo(builder).build();
        proxy = new ApiProxyController(builder.build(), "http://inventory-dev:8080", "http://order-dev:8080");
    }

    @Test
    void forwardsInventoryCallsWithQueryString() {
        backend.expect(requestTo("http://inventory-dev:8080/api/products/low-stock?threshold=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"sku\":\"HDP-005\"}]", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/low-stock");
        request.setQueryString("threshold=5");
        ResponseEntity<String> response = proxy.inventory(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("HDP-005");
        backend.verify();
    }

    @Test
    void forwardsOrderPostWithBody() {
        String json = "{\"customer\":\"carol\",\"sku\":\"KEY-003\",\"quantity\":1,\"unitPrice\":79.00}";
        backend.expect(requestTo("http://order-dev:8080/api/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(json))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"id\":\"ORD-1003\"}"));

        ResponseEntity<String> response = proxy.orders(new MockHttpServletRequest("POST", "/api/orders"), json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("ORD-1003");
        backend.verify();
    }

    @Test
    void passesBackendErrorsThrough() {
        backend.expect(requestTo("http://inventory-dev:8080/api/products/NOPE"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"Product not found: NOPE\"}"));

        ResponseEntity<String> response = proxy.inventory(new MockHttpServletRequest("GET", "/api/products/NOPE"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Product not found");
    }

    @Test
    void unreachableBackendReturns503() {
        backend.expect(requestTo("http://order-dev:8080/api/orders"))
                .andRespond(withException(new IOException("Connection refused")));

        ResponseEntity<String> response = proxy.orders(new MockHttpServletRequest("GET", "/api/orders"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("order service is not reachable");
    }
}
