package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Lay-up (VHLT01I) and reconfigure (VHTR01I) against the real backend, read-only: the openings, the tracks, the train check
 * and the reconfigure window's train check (VHLT010, VHLT020, VHXREVC, VHTR012, VHLT011, VHLT009, VHLT021, VHLT022, VHTR011
 * and the split checks only). Lay-up Train and Reconfigure write (VHLT012, VHLT017, VHLT018, VHTR016) and are never run here.
 */
class ConsistTrainsLiveTest extends LiveTestBase {
    @Test
    void layUpOpeningTracksAndTrainCheck() throws Exception {
        JsonNode o = send("GET", "/api/v1/consist/lay-up/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("yards")).isNotEmpty();
        assertThat(o.get("time").asText()).isNotBlank();
        String yard = o.get("yards").get(0).asText();
        JsonNode t = send("GET", "/api/v1/consist/lay-up/tracks?yard=" + yard, null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        JsonNode c = send("GET", "/api/v1/consist/lay-up/trains/" + consist(), null);
        assertThat(c.get("_status").asInt()).isEqualTo(200);
        assertThat(c.get("layUp").asBoolean() || !c.get("message").asText().isBlank()).as("a refused train says why").isTrue();
        JsonNode bad = send("GET", "/api/v1/consist/lay-up/trains/AB", null);
        assertThat(bad.get("_status").asInt()).isEqualTo(200);
        assertThat(bad.get("layUp").asBoolean()).isFalse();
    }

    @Test
    void reconfigureOpeningAndTrainCheck() throws Exception {
        JsonNode o = send("GET", "/api/v1/consist/reconfigure/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("yards")).isNotEmpty();
        JsonNode s = send("POST", "/api/v1/consist/reconfigure/state", Map.of("action", 2, "consist", consist(), "splits", List.of()));
        assertThat(s.get("_status").asInt()).isEqualTo(200);
        assertThat(s.get("cars").size() > 0 || !s.get("message").asText().isBlank()).as("cars or the old message").isTrue();
    }

    /** a consist on the Hayward yard map, else one most yards have */
    private String consist() throws Exception {
        JsonNode m = send("GET", "/api/v1/yard-tracking/hayward-yard", null);
        for (JsonNode car : m.path("cars")) if (car.path("consist").asText().matches("\\d{3}")) return car.path("consist").asText();
        return "201";
    }
}
