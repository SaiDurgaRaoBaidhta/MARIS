package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Incident events, read-only, against the real backend: the log and the consist look-up equal the old screens'. */
class IncidentEventsLiveTest extends LiveTestBase {
    @Test
    void logMatchesTheOldScreen() throws Exception {
        LocalDate to = LocalDate.now(), from = to.minusDays(12);
        DateTimeFormatter mdy = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        JsonNode clean = send("GET", "/api/v1/incident-events/incidents?from=" + from + "&to=" + to, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHIE01I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", mdy.format(from)), Map.of("widget", "Text2", "attr", "object", "value", mdy.format(to))),
                "widget", "Push_Button3", "event", "clicked"));
        List<String> oldIds = new ArrayList<>(), ids = new ArrayList<>();
        old.at("/windows/0/widgets/VAGen_Container_Details_DATA_ROW/rows").forEach(r -> oldIds.add(r.get(1).asText()));
        clean.get("incidents").forEach(i -> ids.add(i.get("incident").asText()));
        assertThat(ids).isEqualTo(oldIds);
    }

    @Test
    void consistLookUpMatchesTheOldScreen() throws Exception {
        // a car that is in a consist today, from the yard / fleet data
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        String car = null;
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && !c.path("consist").asText().isBlank()) { car = c.get("car").asText(); break; }
            if (car != null) break;
        }
        if (car == null) car = "4377";
        JsonNode clean = send("GET", "/api/v1/incident-events/cars/" + car + "/consist", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHIE06I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", car)), "widget", "Push_Button3", "event", "clicked"));
        assertThat(clean.get("consist").asText()).isEqualTo(old.at("/windows/0/widgets/Text2/value").asText());
    }
}
