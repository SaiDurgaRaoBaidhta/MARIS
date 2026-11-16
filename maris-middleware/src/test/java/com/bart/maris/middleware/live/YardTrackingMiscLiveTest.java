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
 * Read-only, against the real backend: the Concord and Richmond shop maps (VHYT60A, VHYT95A) against the old screens run by
 * the engine, the shop activity yards (VHYT31A, VHYT31H) and the in-service trains (VHYT52A).
 * Never called here: VHYT31B / VHYT31E (shop activity List writes), VHYT22A / VHYT23A (prints), VHYT52B, VHYT54C, and
 * VHYT34I at all (its opening calls VHYT34B, which writes).
 */
class YardTrackingMiscLiveTest extends LiveTestBase {
    private void oldCarsAreInTheApi(String map, String screen) throws Exception {
        JsonNode clean = send("GET", "/api/v1/yard-tracking/maps/" + map, null);
        assertThat(clean.get("_status").asInt()).as(map).isEqualTo(200);
        List<String> api = new ArrayList<>();
        for (JsonNode c : clean.get("cars")) api.add(c.get("label").asText());
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        JsonNode widgets = null;
        for (JsonNode w : open.get("windows")) if (w.get("screen").asText().equalsIgnoreCase(screen)) widgets = w.get("widgets");
        assertThat(widgets).as(screen + " opened").isNotNull();
        List<String> old = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = widgets.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String v = e.getValue().path("value").asText("");
            if (e.getKey().matches("[A-Z0-9_]+TXT\\d*") && v.matches(".*\\d.*")) old.add(v.strip());
        }
        for (String x : api) old.remove(x);
        // VHYT96I's S2TXT01 shows S-2's spot 2 car a second time (see YardTrackingShopMapsParityTest)
        old.removeIf(api::contains);
        assertThat(old).as(screen + ": cars the old map shows that the API does not").isEmpty();
    }

    @Test
    void concordShop() throws Exception {
        oldCarsAreInTheApi("concord-shop", "VHYT60I");
    }

    @Test
    void richmondShop() throws Exception {
        oldCarsAreInTheApi("richmond-shop", "VHYT96I");
    }

    @Test
    void shopActivityOptionsAndPrintersAreRead() throws Exception {
        JsonNode o = send("GET", "/api/v1/yard-tracking/shop-activity/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("yards").size()).as("VHYT31A yards").isPositive();
        String first = o.at("/yards/0/code").asText();
        JsonNode named = send("GET", "/api/v1/yard-tracking/shop-activity/options?yard=" + first, null);
        assertThat(named.get("yard").asText()).isEqualTo(first);
    }

    @Test
    void inServiceTrainsAreRead() throws Exception {
        JsonNode t = send("GET", "/api/v1/yard-tracking/pseudo-correction?yard=CY", null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        assertThat(t.get("trains").isArray()).isTrue();
        for (JsonNode train : t.get("trains")) assertThat(train.get("consist").asText()).matches("\\d{3}-\\d{2}");
    }
}
