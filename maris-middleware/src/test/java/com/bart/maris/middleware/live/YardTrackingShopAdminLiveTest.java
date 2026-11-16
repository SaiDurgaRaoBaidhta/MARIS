package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shop administration maps (VHYT10I, VHYT95I) and the shop car move (VHYT03I) on real data, read-only: the maps
 * (VHYT08C, VHYT04A, VHYT95A), a selection (reads only) and the move window opening (VHYT03A/B/D/E/F). Update writes
 * (VHYT03G, VHYT03H) and is never called here.
 */
class YardTrackingShopAdminLiveTest extends LiveTestBase {
    @Test
    void bothShopMapsShowTheirCars() throws Exception {
        for (String map : List.of("hayward", "richmond")) {
            JsonNode m = send("GET", "/api/v1/yard-tracking/shop-admin/" + map, null);
            assertThat(m.get("_status").asInt()).as(map).isEqualTo(200);
            assertThat(m.get("spots")).as(map).isNotEmpty();
            for (JsonNode c : m.get("cars")) assertThat(c.get("label").asText()).contains(c.get("car").asText());
        }
    }

    @Test
    void aCarsMoveWindowOpensWithItsChoices() throws Exception {
        JsonNode m = send("GET", "/api/v1/yard-tracking/shop-admin/hayward", null);
        JsonNode car = null;
        for (JsonNode c : m.get("cars")) if (car == null) car = c;
        if (car == null) return;                                                   // an empty shop: nothing to open
        String spot = car.get("track").asText() + "/" + car.get("position").asText();
        JsonNode sel = send("POST", "/api/v1/yard-tracking/shop-admin/hayward/selection", Map.of("spots", List.of(spot, spot)));
        assertThat(sel.get("_status").asInt()).isEqualTo(200);
        // the same spot twice resets the selection: nothing opens
        assertThat(sel.path("open").isMissingNode() || sel.path("open").isNull()).isTrue();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("carNo", car.get("car").asText()); ctx.put("yrdTrkgYrd", m.get("yard").asText()); ctx.put("yrdSubLoc", car.get("track").asText());
        ctx.put("newYrdLoc", car.get("track").asText()); ctx.put("carNo2", ""); ctx.put("posnNoFrom", car.get("position").asInt());
        ctx.put("posnNoTo", car.get("position").asInt()); ctx.put("whichGui", "VHYT03I");
        JsonNode v = send("POST", "/api/v1/yard-tracking/shop-move", Map.of("context", ctx));
        assertThat(v.get("_status").asInt()).isEqualTo(200);
        assertThat(v.get("carNo").asText()).isEqualTo(car.get("car").asText());
        assertThat(v.get("reasons")).isNotEmpty();
        assertThat(v.get("locations")).isNotEmpty();
    }
}
