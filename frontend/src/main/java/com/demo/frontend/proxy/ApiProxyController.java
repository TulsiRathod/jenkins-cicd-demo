package com.demo.frontend.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Forwards the browser's /api calls to the backend services of the same environment,
 * so the page only ever talks to its own server (no CORS, no hard-coded ports in the browser).
 */
@RestController
public class ApiProxyController {

    private final RestClient client;
    private final String inventoryUrl;
    private final String orderUrl;

    public ApiProxyController(RestClient backendClient,
                              @Value("${store.inventory-url}") String inventoryUrl,
                              @Value("${store.order-url}") String orderUrl) {
        this.client = backendClient;
        this.inventoryUrl = inventoryUrl;
        this.orderUrl = orderUrl;
    }

    @RequestMapping({"/api/products/**", "/api/inventory/**"})
    public ResponseEntity<String> inventory(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forward("inventory", inventoryUrl, request, body);
    }

    @RequestMapping("/api/orders/**")
    public ResponseEntity<String> orders(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forward("order", orderUrl, request, body);
    }

    private ResponseEntity<String> forward(String service, String baseUrl, HttpServletRequest request, String body) {
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        URI target = URI.create(baseUrl + request.getRequestURI() + query);

        RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(request.getMethod())).uri(target);
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return spec.exchange((req, res) -> {
                MediaType type = res.getHeaders().getContentType();
                return ResponseEntity.status(res.getStatusCode())
                        .contentType(type != null ? type : MediaType.APPLICATION_JSON)
                        .body(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
            });
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"" + service + " service is not reachable\"}");
        }
    }
}
