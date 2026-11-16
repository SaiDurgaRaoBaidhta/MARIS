package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Consist maintenance, read-only, against the real backend: only the lookups (a car's location VHCY1A1/VHCY1A4, a
 * consist's cars for make train VHMT016/VHMT015, change VHXREVC/VHCC011, split VHMT016/VHXCONB/VHTS1A9). Create,
 * Combine, Clear Train, Update and swap Update write, so they are never run here.
 */
class ConsistLiveTest extends LiveTestBase {
    @Test
    void aCarsLocationIsLookedUp() throws Exception {
        JsonNode r = send("GET", "/api/v1/consist/car-location/1101", null);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("message").asText()).isNotBlank();
    }

    @Test
    void consistListsAnswer() throws Exception {
        for (String path : new String[]{"/api/v1/consist/make-train/check", "/api/v1/consist/change/list", "/api/v1/consist/split/list"}) {
            JsonNode r = send("POST", path, Map.of("consist", "201"));
            assertThat(r.get("_status").asInt()).as(path).isEqualTo(200);
            assertThat(r.get("cars").isArray()).as(path).isTrue();
        }
        JsonNode start = send("GET", "/api/v1/consist/clear-train", null);
        assertThat(start.get("_status").asInt()).isEqualTo(200);
    }
}
