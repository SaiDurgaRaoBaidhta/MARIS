package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Yard reports module, read-only, against the real backend: every car the old inquiry map (run by the engine) shows is
 * in the clean API's answer. The API can show a few more, where an old map has a spot wired to no widget (see
 * YardReportsParityTest), so this checks old within API, with no fixed counts.
 */
class YardReportsLiveTest extends LiveTestBase {
    private void oldCarsAreInTheApi(String map, String screen, boolean orientationOnly) throws Exception {
        JsonNode clean = send("GET", "/api/v1/yard-reports/maps/" + map, null);
        assertThat(clean.get("_status").asInt()).as(map).isEqualTo(200);
        List<String> api = new ArrayList<>();
        for (JsonNode c : clean.get("cars")) api.add(c.get(orientationOnly ? "orientation" : "label").asText());

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        JsonNode widgets = null;
        for (JsonNode w : open.get("windows")) if (w.get("screen").asText().equals(screen)) widgets = w.get("widgets");
        assertThat(widgets).as(screen + " opened").isNotNull();
        List<String> old = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = widgets.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String v = e.getValue().path("value").asText("");
            if (e.getKey().matches("[A-Z0-9]+TXT\\d+") && !v.isBlank()) old.add(v);
        }
        for (String x : api) old.remove(x);
        assertThat(old).as(screen + ": cars the old map shows that the API does not").isEmpty();
    }

    @Test
    void berryessa() throws Exception {
        oldCarsAreInTheApi("berryessa", "VHIC01", false);
    }

    @Test
    void millbrae() throws Exception {
        oldCarsAreInTheApi("millbrae", "VHYT80I", true);
    }

    @Test
    void dalyCityYard() throws Exception {
        oldCarsAreInTheApi("daly-city-yard", "VHYT105I", false);
    }

    @Test
    void concordYard() throws Exception {
        oldCarsAreInTheApi("concord-yard", "VHYT70I", false);
    }

    @Test
    void dalyCityShop() throws Exception {
        oldCarsAreInTheApi("daly-city-shop", "VHYT100I", false);
    }

    @Test
    void concordShop() throws Exception {
        oldCarsAreInTheApi("concord-shop", "VHYT61I", false);
    }

    @Test
    void richmondYard() throws Exception {
        oldCarsAreInTheApi("richmond-yard", "VHYT90I", false);
    }

    /** every place the old system-wide map marks has cars in the API, and the other places have none */
    @Test
    void systemWide() throws Exception {
        JsonNode clean = send("GET", "/api/v1/yard-reports/system-wide", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSW01I"));
        JsonNode widgets = open.at("/windows/0/widgets");
        Map<String, String> marker = Map.of("MACART", "Push_Button414", "OKS", "Push_Button4141", "BAYFAIR", "Push_Button4142", "M90", "Push_Button4143");
        for (JsonNode loc : clean.get("locations")) {
            boolean marked = !widgets.path(marker.get(loc.get("location").asText())).path("value").asText("").isBlank();
            assertThat(marked).as(loc.get("location").asText()).isEqualTo(!loc.get("cars").isEmpty());
        }
    }

    /** VHIC03I's yard list (read-only VHYT09A); the move itself writes, so it is never run here */
    @Test
    void pseudoTrainMoveYards() throws Exception {
        JsonNode yards = send("GET", "/api/v1/yard-reports/pseudo-train-move/yards", null);
        assertThat(yards.get("_status").asInt()).isEqualTo(200);
        assertThat(yards.get("_body")).isNotEmpty();
        for (JsonNode y : yards.get("_body")) assertThat(y.asText()).isNotBlank();
    }
}
