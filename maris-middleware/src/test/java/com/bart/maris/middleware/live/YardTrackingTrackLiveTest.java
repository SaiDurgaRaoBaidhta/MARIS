package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The track windows (old VHYT08I, VHYT06I, VHYT14I, VHYT02I), read-only, against the real backend. Only reads run here:
 * Move Car (VHYT08G), the reason change (VHYT06C/D/E), Fix Order (VHYT14x) and Hard Copy (VHYT02A) write or print.
 */
class YardTrackingTrackLiveTest extends LiveTestBase {
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
    void aRealTrackShowsItsCarsAndTheirReasonsAndOrder() throws Exception {
        JsonNode car = someCar();
        assertThat(car).as("a car on a track in some yard").isNotNull();
        String yard = car.get("yard").asText(), track = car.get("track").asText();
        JsonNode t = send("GET", "/api/v1/yard-tracking/track?yard=" + yard + "&subLocation=" + q(track), null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        assertThat(t.get("positions").size()).isEqualTo(30);
        assertThat(t.get("subLocation").asText()).isEqualTo(track);
        int position = 0;
        for (int page = 1; position == 0; page++) {
            JsonNode p = page == 1 ? t : send("GET", "/api/v1/yard-tracking/track?yard=" + yard + "&subLocation=" + q(track) + "&page=" + page, null);
            for (JsonNode x : p.get("positions")) if (x.get("car").asText().equals(car.get("car").asText())) position = x.get("position").asInt();
            if (!p.get("next").asBoolean()) break;
        }
        assertThat(position).as("car " + car.get("car").asText() + " on " + yard + " " + track).isPositive();

        JsonNode r = send("GET", "/api/v1/yard-tracking/move-reasons/car?yard=" + yard + "&subLocation=" + q(track) + "&car=" + car.get("car").asText()
                + "&position=" + position, null);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("reasons")).isNotEmpty();
        assertThat(r.at("/car/car").asText()).isEqualTo(car.get("car").asText());

        JsonNode f = send("GET", "/api/v1/yard-tracking/fix-order?yard=" + yard + "&subLocation=" + q(track) + "&position=" + position, null);
        assertThat(f.get("_status").asInt()).isEqualTo(200);
        assertThat(f.get("cars").size()).isPositive();
        assertThat(f.get("cars").get(f.get("selected").asInt() - 1).get("car").asText()).isEqualTo(car.get("car").asText());
    }

    /** VHYT02I's lists; Hard Copy asks MARIS to print, so it is never run here */
    @Test
    void theReportWindowsLists() throws Exception {
        JsonNode o = send("GET", "/api/v1/yard-tracking/track-report/options?yard=HY", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("printers")).isNotEmpty();
        assertThat(o.get("yards")).isNotEmpty();
    }
}
