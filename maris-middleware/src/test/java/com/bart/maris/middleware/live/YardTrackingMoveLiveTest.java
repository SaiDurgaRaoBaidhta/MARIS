package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The car move windows (old VHYT11I, VHYT13I, VHYT24I), read-only, against the real backend. Only reads run here: Move
 * (VHYT11B), Reverse (VHYT11F), the orientation change (VHYT13C/D) and Hard Copy (VHYT24x, VHYT02x) write or print.
 */
class YardTrackingMoveLiveTest extends LiveTestBase {
    private static String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** a real car on a real track: the first car of the system-wide fleet (VHSW01A), by yard and track */
    private JsonNode someCar() throws Exception {
        JsonNode f = send("GET", "/api/v1/fleet", null);
        var yards = f.path("fleet").fields();
        while (yards.hasNext()) {
            var y = yards.next();
            for (JsonNode t : y.getValue()) {
                if (t.path("cars").size() > 0 && !t.path("id").asText().isBlank()) {
                    return json.createObjectNode().put("yard", y.getKey()).put("track", t.get("id").asText()).put("car", t.get("cars").get(0).get("car").asText());
                }
            }
        }
        return null;
    }

    @Test
    void aRealTrackShowsItsCarsMoveToTracksAndOrientations() throws Exception {
        JsonNode car = someCar();
        assertThat(car).as("a car on a track in some yard").isNotNull();
        String yard = car.get("yard").asText(), track = car.get("track").asText(), no = car.get("car").asText();
        JsonNode t = send("GET", "/api/v1/yard-tracking/car-moves?yard=" + yard + "&subLocation=" + q(track), null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        assertThat(t.get("positions").size()).isEqualTo(30);
        assertThat(t.get("subLocation").asText()).isEqualTo(track);
        boolean found = false;
        for (int page = 1; !found; page++) {
            JsonNode p = page == 1 ? t : send("GET", "/api/v1/yard-tracking/car-moves?yard=" + yard + "&subLocation=" + q(track) + "&page=" + page, null);
            for (JsonNode x : p.get("positions")) if (x.get("car").asText().equals(no)) found = true;
            if (!p.get("next").asBoolean()) break;
        }
        assertThat(found).as("car " + no + " on " + yard + " " + track).isTrue();

        JsonNode o = send("GET", "/api/v1/yard-tracking/orientation?yard=" + yard + "&subLocation=" + q(track) + "&carNo=" + no, null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("car").asText()).isEqualTo(no);
        assertThat(o.get("cars")).isNotEmpty();
    }

    @Test
    void theYardPrintListsPrinters() throws Exception {
        JsonNode p = send("GET", "/api/v1/yard-tracking/yard-print?yard=CY", null);
        assertThat(p.get("_status").asInt()).isEqualTo(200);
        assertThat(p.get("printers")).isNotEmpty();
    }
}
