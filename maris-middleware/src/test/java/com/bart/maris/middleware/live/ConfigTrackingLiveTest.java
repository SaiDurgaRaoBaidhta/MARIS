package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Configuration tracking (VHCF01I car configuration, VFCF05I its printers), against the real backend, read paths only:
 * the printers and a real car listed (VHCF01G, VH01A02, VHCF01A, VHCF01B, VHCF01C). Update and Print are never called.
 */
class ConfigTrackingLiveTest extends LiveTestBase {
    @Test
    void printers() throws Exception {
        JsonNode p = send("GET", "/api/v1/config-tracking/printers", null);
        assertThat(p.get("_status").asInt()).isEqualTo(200);
        assertThat(p.get("printers").isArray()).isTrue();
    }

    /** a car the component inquiry shows (no fixed number), listed with LIST DATA */
    @Test
    void aRealCarIsListed() throws Exception {
        JsonNode o = send("GET", "/api/v1/config-queries/component-options", null);
        if (o.get("stocks") == null || o.get("stocks").isEmpty()) return;
        String car = "";
        for (JsonNode s : o.get("stocks")) {
            JsonNode c = send("GET", "/api/v1/config-queries/components?sort=1&stock=" + s.get("stock").asText(), null);
            for (JsonNode r : c.path("rows")) if (!r.path("car").asText().isBlank()) { car = r.path("car").asText(); break; }
            if (!car.isEmpty()) break;
        }
        if (car.isEmpty()) return;
        JsonNode r = send("GET", "/api/v1/config-tracking/cars/" + car + "?action=LIST%20DATA", null);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("values").get("X-TRUCK-SERIAL").asText() + r.get("values").get("Y-TRUCK-SERIAL").asText()).as("the car's trucks").isNotBlank();
    }
}
