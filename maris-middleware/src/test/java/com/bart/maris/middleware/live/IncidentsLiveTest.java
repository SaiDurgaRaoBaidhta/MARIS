package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Incident entry (VHINC01I, VHINC03I, VHINC06I) against the real backend: the look-ups only (revenue date, today,
 * discovered durations). Filing and changing incidents write to MARIS, so they are covered by the API and parity tests only.
 */
class IncidentsLiveTest extends LiveTestBase {
    @Test
    void trainAndRevenueDefaultsGiveARevenueDate() throws Exception {
        JsonNode t = send("GET", "/api/v1/incidents/train/defaults", null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        assertThat(t.get("date").asText()).matches("\\d\\d[-/]\\d\\d[-/]\\d{4}");
        JsonNode r = send("GET", "/api/v1/incidents/revenue/defaults", null);
        assertThat(r.get("date").asText()).isEqualTo(t.get("date").asText());
    }

    @Test
    void carIncidentOptionsListTheDiscoveredDurations() throws Exception {
        for (String type : new String[] {"EVT", "SHN", "MSC"}) {
            JsonNode o = send("GET", "/api/v1/incidents/car-incident/options?type=" + type, null);
            assertThat(o.get("_status").asInt()).as(type).isEqualTo(200);
            assertThat(o.get("discoveries").size()).as(type).isPositive();
            assertThat(o.get("date").asText()).matches("\\d\\d[-/]\\d\\d[-/]\\d{4}");
        }
    }
}
