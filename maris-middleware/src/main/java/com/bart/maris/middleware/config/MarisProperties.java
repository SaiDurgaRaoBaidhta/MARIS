package com.bart.maris.middleware.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All settings under {@code maris.*}. Every environment-specific value comes from here. */
@Validated
@ConfigurationProperties(prefix = "maris")
public record MarisProperties(@NotNull Legacy legacy, @NotNull Catalog catalog, DevApi devApi, Security security,
                              Fleet fleet, Engine engine) {

    public MarisProperties {
        devApi = devApi == null ? new DevApi(false) : devApi;
        security = security == null ? new Security(true) : security;
        fleet = fleet == null ? new Fleet(List.of()) : fleet;
        engine = engine == null ? new Engine(null, null, null, null) : engine;
    }

    /**
     * The screen engine that runs the old screens' logic.
     * @param location        the screen bundle generated from the EGL source
     * @param blockedPrograms programs the engine must not call (a guard for test runs; empty in normal use)
     * @param terminalId      the terminal id the screens record on updates (setting maris.engine.terminal-id)
     * @param allowedPrograms when not empty, the only programs the engine may call (read-only test runs)
     */
    public record Engine(String location, List<String> blockedPrograms, List<String> allowedPrograms, String terminalId) {
        public Engine {
            terminalId = terminalId == null || terminalId.isBlank() ? "MARIS-UI" : terminalId;
            allowedPrograms = allowedPrograms == null ? List.of() : List.copyOf(allowedPrograms);
            location = location == null || location.isBlank() ? "classpath:egl-screens/" : location;
            blockedPrograms = blockedPrograms == null ? List.of() : List.copyOf(blockedPrograms);
        }
    }

    /**
     * @param baseUrl      e.g. http://localhost:8080/MARIS_RUI_Services_Web
     * @param servicesPath path under baseUrl where the EGL services live
     * @param circuitBreakerFailureRate percent of failed calls that opens the circuit breaker
     */
    public record Legacy(@NotBlank String baseUrl, @NotBlank String servicesPath, @NotNull Duration connectTimeout,
                         @NotNull Duration readTimeout, int circuitBreakerFailureRate, @NotNull Duration circuitBreakerOpenFor) {}

    public record Catalog(@NotBlank String location) {}

    /** Developer endpoints that run any legacy program by name. Never enable in production. */
    public record DevApi(boolean enabled) {}

    /** @param csrfEnabled CSRF protection for the browser session (keep on; tests may turn it off) */
    public record Security(boolean csrfEnabled) {}

    /** The yards and shops the UI shows, in its order. */
    public record Fleet(List<Yard> yards) {
        public Fleet {
            yards = yards == null ? List.of() : List.copyOf(yards);
        }
    }

    /**
     * @param code       the UI's yard code (maris-ui data: HY, BS, DP …)
     * @param legacyCode MRS_YRD_TRKG.YRD_TRKG_YRD for the yard (HY, SVB, DPX …)
     * @param shop       the yard has a shop map
     */
    public record Yard(@NotBlank String code, @NotBlank String legacyCode, @NotBlank String name, String city, boolean shop) {}
}
