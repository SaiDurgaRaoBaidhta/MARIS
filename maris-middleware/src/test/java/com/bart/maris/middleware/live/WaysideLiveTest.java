package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Wayside, read-only, against the real backend: the stock number search equals the old WS17I's. */
class WaysideLiveTest extends LiveTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"BRAKE", "MOTOR", ""})
    void stockSearchMatchesTheOldScreen(String description) throws Exception {
        JsonNode clean = send("GET", "/api/v1/wayside/stock-numbers?description=" + description, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "WS17I"));
        String win = open.at("/windows/0/id").asText();
        JsonNode old = send("POST", "/api/v1/ui/windows/" + win + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", description)), "widget", "Search", "event", "clicked"));
        JsonNode rows = old.at("/windows/0/widgets/Hpt_Container_Details_View1/rows");

        assertThat(clean.get("count").asInt()).isEqualTo(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(clean.at("/stockNumbers/" + i + "/stockNo").asText()).isEqualTo(rows.get(i).get(1).asText());
            assertThat(clean.at("/stockNumbers/" + i + "/description").asText()).isEqualTo(rows.get(i).get(2).asText());
        }
    }
}
