package com.bart.maris.middleware.legacy.client;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.client.RestClient;

/**
 * UP when the EGL web application answers HTTP at all (any status: its root page is a 404 by design).
 * Does not run a program, so it never touches the database.
 */
public class LegacyBackendHealthIndicator implements HealthIndicator {
    private final RestClient http;
    private final LegacyServiceClient client;

    public LegacyBackendHealthIndicator(RestClient http, LegacyServiceClient client) {
        this.http = http;
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            int status = http.get().uri("/").exchange((req, res) -> res.getStatusCode().value());
            return Health.up().withDetail("httpStatus", status).withDetail("circuitBreaker", client.breakerState().name()).build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName())
                    .withDetail("circuitBreaker", client.breakerState().name()).build();
        }
    }
}
