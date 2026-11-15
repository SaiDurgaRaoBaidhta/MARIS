package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The narrative window (VHNAR01I) against the real backend: reading only (VHXNR1A). Adding and rewriting narratives
 * (VHXNR2A/3A/7A/8A) change data, so they are covered by the API and parity tests only.
 */
class VehicleMaintNarrativeLiveTest extends LiveTestBase {
    @Test
    void stepsBackToTheFirstNarrativeOfARecentIncident() throws Exception {
        LocalDate to = LocalDate.now();
        JsonNode list = send("GET", "/api/v1/incident-events/incidents?from=" + to.minusDays(30) + "&to=" + to, null);
        String incident = list.path("incidents").path(0).path("incident").asText("");
        if (incident.isEmpty()) return;                                 // no incident in the local data: nothing to read
        JsonNode n = send("GET", "/api/v1/vehicle-maintenance/narratives/step?incdNo=" + incident + "&currentRow=2&maxRows=2&direction=prev", null);
        assertThat(n.get("_status").asInt()).isEqualTo(200);
        assertThat(n.get("currentRow").asInt()).isEqualTo(1);
        assertThat(n.get("incdNo").asText()).isEqualTo(incident);
        assertThat(n.get("canPrev").asBoolean()).isFalse();
    }
}
