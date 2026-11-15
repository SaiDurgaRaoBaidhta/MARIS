package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Repair, read-only, against the real backend: the codes, an incident's repairs (VHRV04I lists open and closed
 * incidents alike), one repair and its components equal what the old screens show. Nothing is added, changed or
 * closed. The incident is one with several repairs and components in the local copy (-Dmaris.live.repair-incident=...).
 */
class RepairLiveTest extends LiveTestBase {
    static final String INCD = System.getProperty("maris.live.repair-incident", "U288178C");

    JsonNode oldScreen(String screen, List<Map<String, Object>> steps) throws Exception {
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        String id = open.at("/windows/0/id").asText();
        JsonNode s = open;
        for (Map<String, Object> step : steps) s = send("POST", "/api/v1/ui/windows/" + id + "/events", step);
        return s;
    }

    static Map<String, Object> list(String incd) {
        return Map.of("changes", List.of(Map.of("widget", "txtIncidentNo", "attr", "object", "value", incd)), "widget", "pbtSearch", "event", "clicked");
    }

    static String widget(JsonNode state, String w) {
        return state.at("/windows/0/widgets/" + w + "/value").asText("").strip();
    }

    @Test
    void codesComeFromTheBackend() throws Exception {
        JsonNode o = send("GET", "/api/v1/repair/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("causes").size()).isPositive();
        assertThat(o.get("dispositions").size()).isPositive();
    }

    @Test
    void incidentAndRepairMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/repair/incidents/" + INCD + "?screen=VHRV04I", null);
        assertThat(clean.get("_status").asInt()).as(clean.toString()).isEqualTo(200);
        JsonNode old = oldScreen("VHRV04I", List.of(list(INCD)));
        assertThat(clean.get("car").asText()).isEqualTo(widget(old, "txtCarno"));
        assertThat(clean.get("status").asText()).isEqualTo(widget(old, "txtVehincdstat"));
        assertThat(clean.get("consist").asText()).isEqualTo(widget(old, "txtCondashno"));
        assertThat(clean.get("repairs").size()).isEqualTo(old.at("/windows/0/widgets/cntPreviousRepairs/rows").size());

        int doc = clean.at("/repairs/0/document").asInt();
        JsonNode repair = send("GET", "/api/v1/repair/incidents/" + INCD + "/repairs/" + doc, null);
        JsonNode sel = oldScreen("VHRV04I", List.of(list(INCD),
                Map.of("changes", List.of(Map.of("widget", "cntPreviousRepairs", "attr", "selectionIndices", "value", List.of(1))))));
        assertThat(repair.get("system").asText()).isEqualTo(widget(sel, "txtSys"));
        assertThat(repair.get("cause").asText()).isEqualTo(widget(sel, "txtRepairCauseCode"));
        assertThat(repair.get("disposition").asText()).isEqualTo(widget(sel, "txtReprDisposition"));
        assertThat(repair.get("narratives").size()).isPositive();

        JsonNode comps = send("GET", "/api/v1/repair/incidents/" + INCD + "/repairs/" + doc + "/components", null);
        assertThat(comps.get("_status").asInt()).isEqualTo(200);
        assertThat(comps.get("nextDocument").asInt()).isEqualTo(comps.get("components").size() + 1);
    }
}
