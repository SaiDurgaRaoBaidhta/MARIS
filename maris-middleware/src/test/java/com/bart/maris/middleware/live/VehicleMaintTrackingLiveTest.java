package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle parts status / mod tracking (VHVM11I) against the real backend, read-only: the choices, a search and a
 * selected event. Add, update, close, remove and work assign write MARIS data, so they are covered by the API and
 * parity tests only.
 */
class VehicleMaintTrackingLiveTest extends LiveTestBase {
    private static final String BASE = "/api/v1/vehicle-maintenance/tracking-events";

    @Test
    void choicesMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", BASE + "/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHVM11I"));
        JsonNode w = old.at("/windows/0/widgets");
        assertThat(labels(clean.get("searchEvents"))).isEqualTo(texts(w.at("/Drop-down_List1/items")));
        assertThat(labels(clean.get("entryEvents"))).isEqualTo(texts(w.at("/ddlnewloc/items")));
        assertThat(texts(clean.get("statuses"))).isEqualTo(texts(w.at("/Drop-down_List2/items")));
        assertThat(clean.get("dateFrom").asText()).isEqualTo(w.at("/Text12/value").asText().strip());
    }

    @Test
    void searchAndSelectAnEvent() throws Exception {
        JsonNode o = send("GET", BASE + "/options", null);
        Map<String, Object> c = new HashMap<>();
        java.time.format.DateTimeFormatter mdy = java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy");
        c.put("dateFrom", java.time.LocalDate.parse(o.get("dateTo").asText().replace("/", "-"), mdy).minusDays(30).format(mdy));   // a month: the whole range is slow
        c.put("dateTo", o.get("dateTo").asText());
        JsonNode l = send("POST", BASE + "/search", c);
        assertThat(l.get("_status").asInt()).isEqualTo(200);
        assertThat(l.get("events").isArray()).isTrue();
        if (l.get("events").isEmpty()) return;                          // no event in the local data
        JsonNode s = send("POST", BASE + "/selection", l.get("events").get(0));
        assertThat(s.get("_status").asInt()).isEqualTo(200);
        String car = l.get("events").get(0).get("car").asText();
        assertThat(send("GET", BASE + "/cars/" + car + "/check", null).get("_status").asInt()).isEqualTo(200);
    }

    private static List<String> labels(JsonNode a) {
        List<String> out = new ArrayList<>();
        a.forEach(i -> out.add(i.get("label").asText().strip()));
        return out;
    }

    private static List<String> texts(JsonNode a) {
        List<String> out = new ArrayList<>();
        a.forEach(i -> { if (!i.asText().isBlank()) out.add(i.asText().strip()); });
        return out;
    }
}
