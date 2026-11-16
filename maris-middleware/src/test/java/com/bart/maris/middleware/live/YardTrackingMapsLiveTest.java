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
 * Yard tracking maps, read-only, against the real backend (VHYT40A, VHYT50A, VHYT04A, VH01A02 only read): every car the
 * old map (run by the engine) shows is in the clean API's answer. The API can show a few more where an old map has a spot
 * without a binding (see YardTrackingMapsParityTest), so this checks old within API, with no fixed counts.
 */
class YardTrackingMapsLiveTest extends LiveTestBase {
    private void oldCarsAreInTheApi(String map, String screen) throws Exception {
        JsonNode clean = send("GET", "/api/v1/yard-tracking/maps/" + map, null);
        assertThat(clean.get("_status").asInt()).as(map).isEqualTo(200);
        List<String> api = new ArrayList<>();
        for (JsonNode c : clean.get("cars")) api.add(c.get("label").asText());

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        JsonNode widgets = null;
        for (JsonNode w : open.get("windows")) if (w.get("screen").asText().equals(screen)) widgets = w.get("widgets");
        assertThat(widgets).as(screen + " opened").isNotNull();
        List<String> old = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = widgets.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String v = e.getValue().path("value").asText("");
            if (e.getKey().matches("[A-Z0-9]+TXT\\d+") && v.matches(".*\\d.*")) old.add(v.strip());
        }
        for (String x : api) old.remove(x);
        assertThat(old).as(screen + ": cars the old map shows that the API does not").isEmpty();
    }

    @Test
    void dublin() throws Exception {
        oldCarsAreInTheApi("dublin", "VHYT40I");
    }

    @Test
    void dublinUpdate() throws Exception {
        oldCarsAreInTheApi("dublin-update", "VHYT41I");
    }

    @Test
    void pittsburg() throws Exception {
        oldCarsAreInTheApi("pittsburg", "VHYT50I");
    }

    @Test
    void pittsburgUpdate() throws Exception {
        oldCarsAreInTheApi("pittsburg-update", "VHYT51I");
    }

    @Test
    void haywardShop() throws Exception {
        oldCarsAreInTheApi("hayward-shop", "VHYT04I");
    }

    /** the access list is only read (VH01A02) */
    @Test
    void maintenanceAccessAnswers() throws Exception {
        JsonNode a = send("GET", "/api/v1/yard-tracking/maps/dublin-update/maintenance-access", null);
        assertThat(a.get("_status").asInt()).isEqualTo(200);
        assertThat(a.get("allowed").isBoolean()).isTrue();
    }
}
