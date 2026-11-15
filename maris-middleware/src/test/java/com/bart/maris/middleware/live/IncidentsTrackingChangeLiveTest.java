package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle tracking change incident (VHCI03I) against the real backend, read-only: its choices. The change itself
 * writes MARIS data (VHCIV and the programs after it), so it is covered by the API and parity tests only.
 */
class IncidentsTrackingChangeLiveTest extends LiveTestBase {
    @Test
    void neverGoChoicesMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/incidents/tracking-change/never-go/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCI03I"));
        JsonNode w = old.at("/windows/0/widgets");
        List<String> items = new ArrayList<>(), statuses = new ArrayList<>();
        w.at("/cmbCarStatus/items").forEach(i -> { if (!i.asText().isBlank()) items.add(i.asText().strip()); });
        clean.get("statuses").forEach(i -> statuses.add(i.asText()));
        assertThat(statuses).isEqualTo(items);
        assertThat(clean.get("date").asText()).isEqualTo(w.at("/txtStatChgDate/value").asText().strip());
    }
}
