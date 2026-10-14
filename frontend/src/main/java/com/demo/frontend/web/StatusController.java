package com.demo.frontend.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which build of each service is running in this environment – shown in the page header. */
@RestController
public class StatusController {

    private final RestClient client;
    private final String inventoryUrl;
    private final String orderUrl;
    private final String version;
    private final String environment;

    public StatusController(RestClient backendClient,
                            ObjectProvider<BuildProperties> buildProperties,
                            @Value("${store.inventory-url}") String inventoryUrl,
                            @Value("${store.order-url}") String orderUrl,
                            @Value("${APP_ENV:local}") String environment) {
        BuildProperties props = buildProperties.getIfAvailable();
        this.client = backendClient;
        this.inventoryUrl = inventoryUrl;
        this.orderUrl = orderUrl;
        this.version = props != null ? props.getVersion() : "unknown";
        this.environment = environment;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of(
                "app", "frontend",
                "version", version,
                "environment", environment);
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("environment", environment);
        status.put("frontend", version);
        status.put("inventory", backendVersion(inventoryUrl));
        status.put("order", backendVersion(orderUrl));
        return status;
    }

    private String backendVersion(String baseUrl) {
        try {
            Map<?, ?> body = client.get().uri(baseUrl + "/version").retrieve().body(Map.class);
            return body != null ? String.valueOf(body.get("version")) : "unavailable";
        } catch (RestClientException e) {
            return "unavailable";
        }
    }
}
