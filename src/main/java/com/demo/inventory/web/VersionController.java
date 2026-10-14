package com.demo.inventory.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Shows which build is running where, so we can prove what got deployed. */
@RestController
public class VersionController {

    private final String version;
    private final String environment;

    public VersionController(ObjectProvider<BuildProperties> buildProperties,
                             @Value("${APP_ENV:local}") String environment) {
        BuildProperties props = buildProperties.getIfAvailable();
        this.version = props != null ? props.getVersion() : "unknown";
        this.environment = environment;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of(
                "app", "inventory-service",
                "version", version,
                "environment", environment);
    }
}
