package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Hayward yard update map (VHYT05I) on real data, read-only (VHYT07A, VH01A02). */
class YardTrackingHaywardLiveTest extends LiveTestBase {
    @Test
    void theMapShowsHaywardsCarsOnKnownSpots() throws Exception {
        JsonNode m = send("GET", "/api/v1/yard-tracking/hayward-yard", null);
        assertThat(m.get("_status").asInt()).isEqualTo(200);
        assertThat(m.get("yard").asText()).isEqualTo("HY");
        assertThat(m.get("cars")).isNotEmpty();
        for (JsonNode c : m.get("cars")) {
            assertThat(c.get("code").asText()).as(c.get("track") + "/" + c.get("position")).isNotBlank();
            assertThat(c.get("label").asText()).contains(c.get("car").asText());
        }
        JsonNode first = m.get("cars").get(0);
        JsonNode sel = send("POST", "/api/v1/yard-tracking/hayward-yard/selection", Map.of("clicks", List.of(Map.of("spot", first.get("code").asText()))));
        assertThat(sel.get("_status").asInt()).isEqualTo(200);
        assertThat(sel.path("open").isMissingNode() || sel.path("open").isNull()).as("one click does not open the move yet").isTrue();
    }
}
