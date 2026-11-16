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

/**
 * Consist orientation (VHCO01I), D and C sheet (VHDC01I), SCRAM (VHSCM1I) and car status change (VHCHG1I) against the
 * real backend, read-only: start values, printers, the SCRAM table and a car's status. Print and Update write, so they
 * are covered by the API and parity tests only.
 */
class VehicleQueriesMiscLiveTest extends LiveTestBase {
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private JsonNode open(String screen) throws Exception {
        return send("POST", "/api/v1/ui/open", Map.of("screen", screen));
    }

    private static String value(JsonNode state, String widget) {
        return state.at("/windows/0/widgets/" + widget + "/value").asText("").strip();
    }

    private static List<String> rows(JsonNode state, String widget, int column) {
        List<String> out = new ArrayList<>();
        state.at("/windows/0/widgets/" + widget + "/rows").forEach(r -> out.add(r.get(column).asText().strip()));
        return out;
    }

    @Test
    void consistOrientationStartsLikeTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/consist-orientation/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = open("VHCO01I");
        if (!clean.get("fromDate").asText().isEmpty()) assertThat(MDY.format(LocalDate.parse(clean.get("fromDate").asText()))).isEqualTo(value(old, "Text2"));
        if (!clean.get("toDate").asText().isEmpty()) assertThat(MDY.format(LocalDate.parse(clean.get("toDate").asText()))).isEqualTo(value(old, "Text3"));
        List<String> vtams = new ArrayList<>();
        clean.get("printers").forEach(p -> vtams.add(p.get("vtam").asText()));
        assertThat(vtams).containsExactlyElementsOf(rows(old, "Container_Details1", 1));
    }

    @Test
    void dncSheetListsTheOldPrinters() throws Exception {
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/dnc-sheet/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        List<String> vtams = new ArrayList<>();
        clean.get("printers").forEach(p -> vtams.add(p.get("vtam").asText()));
        assertThat(vtams).containsExactlyElementsOf(rows(open("VHDC01I"), "Container_Details1", 1));
    }

    @Test
    void scramMatchesTheOldScreen() throws Exception {
        JsonNode options = send("GET", "/api/v1/vehicle-queries/scram/options", null);
        assertThat(options.get("_status").asInt()).isEqualTo(200);
        JsonNode old = open("VHSCM1I");
        assertThat(options.get("day").asText()).isEqualTo(value(old, "txtDay"));
        assertThat(options.get("time").asText()).isEqualTo(value(old, "txtTime"));
        String day = options.get("day").asText(), time = options.get("time").asText();
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/scram?day=" + day + "&time=" + time, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode listed = send("POST", "/api/v1/ui/windows/" + old.at("/windows/0/id").asText() + "/events", Map.of("changes", List.of(), "widget", "pbtList", "event", "clicked"));
        List<String> shown = new ArrayList<>();
        clean.at("/yards/0/rows").forEach(r -> shown.add(r.get("homeYard").asText()));
        assertThat(shown).isEqualTo(rows(listed, "cntScram", 0));
    }

    @Test
    void carStatusMatchesTheOldScreen() throws Exception {
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;                                                          // no car in the local data
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/car-status-change/cars/" + car, null);
        JsonNode old = open("VHCHG1I");
        JsonNode read = send("POST", "/api/v1/ui/windows/" + old.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", car)), "widget", "Text1", "event", "losingFocus"));
        if (clean.get("_status").asInt() == 200) {
            assertThat(clean.get("status").asText()).isEqualTo(value(read, "Text2"));
            assertThat(clean.get("message").asText()).isEqualTo(value(read, "txtMsg"));
        } else {
            assertThat(clean.get("detail").asText()).isEqualTo(value(read, "txtMsg"));
        }
    }
}
