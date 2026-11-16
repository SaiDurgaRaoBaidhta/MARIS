package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Consist tracking, read-only, against the real backend: the clean API's first page of consists equals what the old
 * VHST05I / VHST02I / VHST04I and VHST01I / VHST03I show. Print submits a real report, so it is covered by the API and
 * parity tests only.
 */
class VehicleQueriesConsistTrackingLiveTest extends LiveTestBase {
    private static final String BASE = "/api/v1/vehicle-queries/consist-tracking";

    private static JsonNode window(JsonNode state, String screen) {
        for (JsonNode w : state.get("windows")) if (w.get("screen").asText().equalsIgnoreCase(screen)) return w;
        return null;
    }

    private static String value(JsonNode w, String widget) {
        JsonNode x = w.at("/widgets/" + widget + "/value");
        return x.isMissingNode() ? "" : x.asText().strip();
    }

    /** VHST05I with a report and a range, then Display */
    private JsonNode criteria(int report, String from) throws Exception {
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHST05I"));
        String id = open.at("/windows/0/id").asText();
        send("POST", "/api/v1/ui/windows/" + id + "/events", Map.of("changes", List.of(), "widget", "Radio_Button_Set1", "event", "select", "value", report));
        return send("POST", "/api/v1/ui/windows/" + id + "/events", Map.of("changes", List.of(Map.of("widget", "txtFromdate", "attr", "object", "value", from),
                Map.of("widget", "txtTodate", "attr", "object", "value", "")), "widget", "pbtDisplay", "event", "clicked"));
    }

    @Test
    void optionsListThePrinters() throws Exception {
        JsonNode clean = send("GET", BASE + "/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(clean.get("reports").size()).isEqualTo(2);
    }

    @Test
    void summaryListMatchesTheOldScreen() throws Exception {
        JsonNode clean = send("GET", BASE + "/status-trains?from=ALL", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode w = window(criteria(1, "ALL"), "VHST02I");
        assertThat(w).isNotNull();
        String[] consist = {"Text3", "Text31", "Text32", "Text33"}, origin = {"Text4", "Text41", "Text42", "Text43"};
        List<String> old = new ArrayList<>(), api = new ArrayList<>();
        for (int b = 0; b < 4; b++) if (!value(w, consist[b]).isEmpty()) old.add(value(w, consist[b]) + " " + value(w, origin[b]));
        for (int b = 0; b < Math.min(4, clean.get("trains").size()); b++) {
            JsonNode t = clean.get("trains").get(b);
            api.add(t.get("consist").asText() + " " + t.get("origin").asText());
        }
        assertThat(api).isEqualTo(old);
        if (clean.get("trains").size() > 0) {
            JsonNode c = clean.at("/trains/0/cars/0");
            assertThat((c.get("leftMark").asText() + c.get("car").asText() + c.get("rightMark").asText()).strip()).isEqualTo(value(w, "Text7"));
        }
    }

    @Test
    void carDetailsMatchTheOldScreen() throws Exception {
        JsonNode trains = send("GET", BASE + "/status-trains?from=ALL", null).get("trains");
        if (trains == null || trains.isEmpty()) return;                    // no consists in the local data
        String from = trains.get(0).get("consist").asText().substring(0, 3);
        JsonNode clean = send("GET", BASE + "/car-details?from=" + from, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode w = window(criteria(2, from), "VHST04I");
        assertThat(w).isNotNull();
        if (clean.get("trains").isEmpty()) {
            assertThat(value(w, "Text7")).isEmpty();
            return;
        }
        JsonNode t = clean.get("trains").get(0), c = t.at("/cars/0");
        assertThat(t.get("consist").asText()).isEqualTo(value(w, "Text3"));
        assertThat(c.get("car").asText()).isEqualTo(value(w, "Text7"));
        assertThat(c.get("homeYard").asText()).isEqualTo(value(w, "Text17"));
        assertThat(c.get("pm").asText()).isEqualTo(value(w, "Text711"));
        assertThat(String.valueOf(c.get("washDays").asLong())).isEqualTo(value(w, "Text1711"));
        assertThat(c.get("clean").asText()).isEqualTo(value(w, "Text172"));
    }

    @Test
    void symptomViewMatchesTheOldScreen() throws Exception {
        JsonNode trains = send("GET", BASE + "/status-trains?from=ALL", null).get("trains");
        if (trains == null || trains.isEmpty()) return;
        String con = trains.get(0).get("consist").asText().substring(0, 3);
        JsonNode clean = send("GET", BASE + "/symptoms?consist=" + con + "&symptom=0120", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHST01I"));
        JsonNode state = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of("changes", List.of(
                Map.of("widget", "txtFromdate", "attr", "object", "value", con), Map.of("widget", "Text2", "attr", "object", "value", "0120")),
                "widget", "pbtDisplay", "event", "clicked"));
        JsonNode w = window(state, "VHST03I");
        assertThat(w).isNotNull();
        if (clean.get("trains").isEmpty()) {
            assertThat(value(w, "Consist")).isEmpty();
            return;
        }
        assertThat(clean.at("/trains/0/consist").asText()).isEqualTo(value(w, "Consist"));
    }
}
