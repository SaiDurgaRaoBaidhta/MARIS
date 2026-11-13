package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ICS module, read-only, against the real backend. */
class IcsLiveTest extends LiveTestBase {
    /** the clean ICS API and the old ICS screens (run by the engine) give the same report from the real backend */
    @Test
    void icsCleanApiMatchesTheOldScreenOnTheRealBackend() throws Exception {
        JsonNode o = send("GET", "/api/v1/ics/options", null);
        String iso = o.get("revenueDate").asText();                                   // the date MARIS itself proposes
        assertThat(iso).matches("\\d{4}-\\d{2}-\\d{2}");
        String mdy = iso.substring(5, 7) + "-" + iso.substring(8) + "-" + iso.substring(0, 4);

        JsonNode clean = send("GET", "/api/v1/ics/messages?revenueDate=" + iso, null);

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHIC30I"));
        String win = open.at("/windows/0/id").asText();
        JsonNode old = send("POST", "/api/v1/ui/windows/" + win + "/events", Map.of(
                "changes", java.util.List.of(Map.of("widget", "Text3", "attr", "object", "value", mdy)),
                "widget", "Push_Button3", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode w : old.get("windows")) if (w.get("screen").asText().equals("VHIC31I")) report = w.get("widgets");
        assertThat(report).as("the old screen opened its report").isNotNull();
        JsonNode rows = report.at("/VAGen_Container_Details_DATA_ROW/rows");

        assertThat(clean.get("count").asInt()).isEqualTo(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(clean.at("/messages/" + i + "/consist").asText()).as("row " + i).isEqualTo(rows.get(i).get(3).asText());
            assertThat(clean.at("/messages/" + i + "/type").asText()).as("row " + i).isEqualTo(rows.get(i).get(2).asText());
        }
        assertThat(send("GET", "/api/v1/ics/messages?revenueDate=2099-01-01", null).get("_status").asInt()).isEqualTo(400);
    }
}
