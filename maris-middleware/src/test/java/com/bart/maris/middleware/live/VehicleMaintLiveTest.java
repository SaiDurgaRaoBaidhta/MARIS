package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle maintenance, read-only, against the real backend. Printing (VHVM12A / VHVM13A) sends real print jobs, so it
 * is covered by the API and parity tests only.
 */
class VehicleMaintLiveTest extends LiveTestBase {
    @Test
    void reportChoicesMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/vehicle-maintenance/reports/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHVM13i"));
        assertThat(texts(clean.get("eventTypes"))).isEqualTo(texts(old.at("/windows/0/widgets/Drop-down_List2/items")));
        assertThat(texts(clean.get("carTypes"))).isEqualTo(texts(old.at("/windows/0/widgets/Drop-down_List1/items")));
        List<String> printers = new ArrayList<>();
        clean.get("printers").forEach(p -> printers.add(p.get("vtam").asText()));
        assertThat(printers).isNotEmpty();
    }

    @Test
    void narrativeOfARecentIncident() throws Exception {
        LocalDate to = LocalDate.now();
        JsonNode list = send("GET", "/api/v1/incident-events/incidents?from=" + to.minusDays(30) + "&to=" + to, null);
        String incident = list.path("incidents").path(0).path("incident").asText("");
        if (incident.isEmpty()) return;                                 // no incident in the local data: nothing to read
        JsonNode n = send("GET", "/api/v1/vehicle-maintenance/incidents/" + incident + "/narrative", null);
        assertThat(n.get("_status").asInt()).isEqualTo(200);
        assertThat(n.get("lines").isArray()).isTrue();
    }

    private static List<String> texts(JsonNode a) {
        List<String> out = new ArrayList<>();
        a.forEach(i -> { if (!i.asText().isBlank()) out.add(i.asText().strip()); });
        return out;
    }
}
