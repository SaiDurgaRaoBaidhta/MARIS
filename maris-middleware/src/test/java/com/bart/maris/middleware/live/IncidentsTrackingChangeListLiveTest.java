package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle tracking change incident (VHCI02I) against the real backend, read-only: its choices and the car-status and
 * move lists (VHINC6A, VHCI01B, VHCI01D, VHCI01A only). The DI / UD / DF / FC lists can reset a pseudo consist
 * (VHINXB) and Change writes, so those are covered by the API and parity tests only.
 */
class IncidentsTrackingChangeListLiveTest extends LiveTestBase {
    @Test
    void choicesMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/incidents/tracking-change/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCI02I"));
        JsonNode w = old.at("/windows/0/widgets");
        List<String> items = new ArrayList<>(), statuses = new ArrayList<>();
        w.at("/ddlStatus/items").forEach(i -> items.add(i.asText().strip()));
        clean.get("statuses").forEach(i -> statuses.add(i.asText()));
        assertThat(statuses).isEqualTo(items);
        assertThat(clean.get("date").asText()).isEqualTo(w.at("/txtStatChgDate/value").asText().strip());
    }

    @Test
    void carStatusAndMoveListsAnswer() throws Exception {
        for (String car : List.of("1101", "1102", "1201", "3142")) {
            for (int action : new int[] {1, 7}) {
                JsonNode r = send("POST", "/api/v1/incidents/tracking-change/incidents", Map.of("action", action, "status", "GO", "car", car));
                assertThat(r.get("_status").asInt()).as("car " + car + " action " + action).isEqualTo(200);
                assertThat(r.has("rows")).isTrue();
                r.get("rows").forEach(row -> assertThat(row.get("incident").asText()).isNotBlank());
            }
        }
    }
}
