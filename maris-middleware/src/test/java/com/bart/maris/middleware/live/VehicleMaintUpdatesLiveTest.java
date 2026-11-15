package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The vehicle maintenance update screens' READ side against the real backend: a car's hours (VHVM1A5), the consist list
 * (VHVM10A), the revenue date (VHYT34A) and the consist history (VHVM19A) equal the old screens'. The writing programs
 * (VHVM8A1, VHVM10C, VHVM19B) are never called here; the API and parity tests cover them.
 */
class VehicleMaintUpdatesLiveTest extends LiveTestBase {
    private static final String VM = "/api/v1/vehicle-maintenance";

    private JsonNode old(String screen, List<Map<String, Object>> changes, String button) throws Exception {
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        if (button == null) return open;
        return send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events",
                Map.of("changes", changes, "widget", button, "event", "clicked"));
    }

    private static Map<String, Object> change(String widget, String value) {
        return Map.of("widget", widget, "attr", "object", "value", value);
    }

    private static String value(JsonNode state, String widget) {
        return state.at("/windows/0/widgets/" + widget + "/value").asText("").strip();
    }

    private static List<String> oldConsists(JsonNode state) {
        List<String> out = new ArrayList<>();
        state.at("/windows/0/widgets/VAGen_Container_Details_DATA_ROW1/rows").forEach(r -> out.add(r.get(0).asText().strip()));
        return out;
    }

    private static List<String> consists(JsonNode clean) {
        List<String> out = new ArrayList<>();
        clean.get("rows").forEach(r -> out.add(r.path("conNo").asText()));
        return out;
    }

    @Test
    void carHoursMatchTheOldScreen() throws Exception {
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;                                         // no car in the local data
        JsonNode clean = send("GET", VM + "/car-hours/" + car, null);
        JsonNode old = old("VHVM08I", List.of(change("Text1", car)), "Push_Button1");  // Search only: VHVM1A5
        assertThat(clean.get("_status").asInt()).as("car " + car).isEqualTo(200);
        assertThat(clean.get("location").asText()).isEqualTo(value(old, "Text3"));
        assertThat(clean.get("cumulativeHours").asText()).isEqualTo(value(old, "Text4"));
        assertThat(clean.get("hoursToNextPm").asText()).isEqualTo(value(old, "Text5"));
        assertThat(clean.get("hoursFromLastPm").asText()).isEqualTo(value(old, "Text6"));
    }

    @Test
    void consistListMatchesTheOldScreen() throws Exception {
        JsonNode clean = send("GET", VM + "/consist-status?from=100&to=999", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = old("VHVM10I", List.of(change("Text5", "100"), change("Text6", "999")), "Push_Button1");
        assertThat(consists(clean)).isEqualTo(oldConsists(old));
    }

    @Test
    void consistHistoryMatchesTheOldScreen() throws Exception {
        JsonNode options = send("GET", VM + "/consist-history/options", null);
        assertThat(options.get("_status").asInt()).isEqualTo(200);
        String rev = options.get("revDate").asText();
        assertThat(rev).isEqualTo(value(old("VHVM19I", List.of(), null), "Text11"));
        // one consist's history: the whole range takes the local backend longer than the middleware waits
        List<String> all = consists(send("GET", VM + "/consist-status?from=100&to=999", null));
        if (all.isEmpty()) return;                                       // no consist in the local data
        String con = all.get(0);
        JsonNode clean = send("GET", VM + "/consist-history?from=" + con + "&revDate=" + rev, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = old("VHVM19I", List.of(change("Text5", con), change("Text11", rev)), "Push_Button1");
        assertThat(consists(clean)).isEqualTo(oldConsists(old));
        assertThat(clean.path("message").asText("")).isEqualTo(value(old, "Text12"));
    }
}
