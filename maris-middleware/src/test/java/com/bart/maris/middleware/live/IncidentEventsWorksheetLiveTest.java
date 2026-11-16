package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The vehicle desk work sheet (VHIE03I) and the symptom history (VHIE07I), read-only, against the real backend: the
 * clean API equals the old screens run by the engine. Nothing is added, saved or filed here.
 */
class IncidentEventsWorksheetLiveTest extends LiveTestBase {
    private static final String BASE = "/api/v1/incident-events/worksheet";

    private JsonNode window(JsonNode state, String screen) {
        for (JsonNode w : state.get("windows")) if (screen.equalsIgnoreCase(w.get("screen").asText())) return w;
        return null;
    }

    @Test
    void eventsOfTheRevenueDateMatchTheOldScreen() throws Exception {
        JsonNode options = send("GET", BASE + "/options", null);
        assertThat(options.get("_status").asInt()).isEqualTo(200);
        String rev = options.get("revenueDate").asText();
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHIE03I"));
        String mdy = rev.substring(5, 7) + "-" + rev.substring(8) + "-" + rev.substring(0, 4);
        assertThat(window(open, "VHIE03I").at("/widgets/Text1/value").asText().strip()).isEqualTo(mdy);
        String id = window(open, "VHIE03I").get("id").asText();
        JsonNode old = send("POST", "/api/v1/ui/windows/" + id + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", mdy)), "widget", "Push_Button3", "event", "clicked"));
        List<String> oldIds = new ArrayList<>(), ids = new ArrayList<>();
        window(old, "VHIE03I").at("/widgets/VAGen_Container_Details_DATA_ROW/rows").forEach(r -> oldIds.add(r.get(1).asText().strip()));
        JsonNode clean = send("GET", BASE + "/events?date=" + rev, null);
        clean.get("events").forEach(e -> ids.add(e.get("eventId").asText()));
        assertThat(ids).isEqualTo(oldIds);
    }

    @Test
    void symptomHistoryMatchesTheOldScreen() throws Exception {
        JsonNode options = send("GET", BASE + "/options", null);
        String symptom = options.at("/symptoms/0/code").asText("0120");
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHIE03I"));
        String id = window(open, "VHIE03I").get("id").asText();
        JsonNode searched = send("POST", "/api/v1/ui/windows/" + id + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text9", "attr", "object", "value", symptom)), "widget", "Push_Button9", "event", "clicked"));
        JsonNode w7 = window(searched, "VHIE07I");
        assertThat(w7).as("VHIE07I opened").isNotNull();
        JsonNode old = send("POST", "/api/v1/ui/windows/" + w7.get("id").asText() + "/events", Map.of("changes", List.of(), "widget", "Push_Button2", "event", "clicked"));
        List<String> oldRows = new ArrayList<>(), rows = new ArrayList<>();
        window(old, "VHIE07I").at("/widgets/VAGen_Container_Details_DATA_ROW/rows").forEach(r -> oldRows.add(r.get(0).asText().strip() + r.get(1).asText().strip() + r.get(6).asText().strip()));
        JsonNode clean = send("GET", BASE + "/symptom-history/" + symptom, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        clean.get("rows").forEach(r -> rows.add(r.get("type").asText() + r.get("car").asText() + r.get("incident").asText()));
        assertThat(rows).isEqualTo(oldRows);
    }
}
