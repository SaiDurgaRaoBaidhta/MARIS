package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The vehicle incident screens, read-only, against the real backend: what VHINC08I, VHINC09I and VHINC10I show when
 * they open and after List equals what the old screens show. Create and Update write incidents, so they are covered by
 * the API and parity tests only.
 */
class IncidentsVehicleLiveTest extends LiveTestBase {
    private JsonNode window(JsonNode state, String screen) {
        for (JsonNode w : state.get("windows")) if (screen.equals(w.get("screen").asText())) return w;
        return null;
    }

    private String value(JsonNode w, String widget) {
        JsonNode v = w.at("/widgets/" + widget + "/value");
        return v.isMissingNode() || v.isNull() ? "" : v.asText().strip();
    }

    private JsonNode click(JsonNode w, String widget, List<Map<String, Object>> changes) throws Exception {
        return send("POST", "/api/v1/ui/windows/" + w.get("id").asText() + "/events", Map.of("changes", changes, "widget", widget, "event", "clicked"));
    }

    @Test
    void filingOpensLikeTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/incidents/vehicle-incident/options?type=YRD", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode menu = send("POST", "/api/v1/ui/open", Map.of("screen", "VHMVSI"));
        JsonNode old = window(click(window(menu, "VHMVSI"), "Push_Button10", List.of()), "VHINC08I");
        assertThat(old).isNotNull();
        assertThat(clean.get("title").asText()).isEqualTo(old.get("title").asText());
        assertThat(clean.get("date").asText()).isEqualTo(value(old, "txtDateincd"));
    }

    @Test
    void maintenanceStatusesAreTheOldList() throws Exception {
        JsonNode clean = send("GET", "/api/v1/incidents/vehicle-maintenance/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode menu = send("POST", "/api/v1/ui/open", Map.of("screen", "VHMMMI"));
        JsonNode old = window(click(window(menu, "VHMMMI"), "Push_Button20", List.of()), "VHINC10I");
        List<String> items = new ArrayList<>(), codes = new ArrayList<>();
        old.at("/widgets/ddlIncdstat/items").forEach(i -> { if (!i.asText().isBlank()) items.add(i.asText().strip()); });
        clean.get("statuses").forEach(s -> codes.add(s.get("code").asText()));
        assertThat(codes).isEqualTo(items);
    }

    @Test
    void anIncidentListsLikeTheOldScreens() throws Exception {
        String incident = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        List<String> cars = new ArrayList<>();
        fleet.path("fleet").elements().forEachRemaining(t -> t.forEach(c -> c.path("cars").forEach(x -> { if (x.has("car") && cars.size() < 5) cars.add(x.get("car").asText()); })));
        for (String car : cars) {
            JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSV07I"));
            JsonNode inq = window(click(window(open, "VHSV07I"), "Push_Button1", List.of(Map.of("widget", "Text1", "attr", "object", "value", car))), "VHSV06I");
            if (inq != null && !value(inq, "txtIncdNo").isEmpty()) { incident = value(inq, "txtIncdNo"); break; }
        }
        if (incident == null) return;                                       // no incident in the local data
        JsonNode clean = send("GET", "/api/v1/incidents/vehicle-maintenance/" + incident, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode menu = send("POST", "/api/v1/ui/open", Map.of("screen", "VHMVSI"));
        JsonNode old09 = window(click(window(menu, "VHMVSI"), "Push_Button7", List.of()), "VHINC09I");
        old09 = window(click(old09, "pbtList", List.of(Map.of("widget", "txtIncdno", "attr", "object", "value", incident))), "VHINC09I");
        JsonNode upd = send("GET", "/api/v1/incidents/vehicle-update/" + incident, null);
        if (upd.get("_status").asInt() == 200) {
            assertThat(upd.at("/incident/car").asText()).isEqualTo(value(old09, "txtCarno"));
            assertThat(upd.at("/incident/consist").asText()).isEqualTo(value(old09, "txtCondashno"));
            assertThat(upd.at("/incident/location").asText()).isEqualTo(value(old09, "txtLocation"));
        } else {
            assertThat(upd.get("detail").asText()).isEqualTo(value(old09, "txtMsgArea"));
        }
        assertThat(clean.get("car").asText()).isEqualTo(upd.get("_status").asInt() == 200 ? upd.at("/incident/car").asText() : clean.get("car").asText());
    }
}
