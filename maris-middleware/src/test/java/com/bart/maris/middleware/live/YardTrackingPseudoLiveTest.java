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
 * Pseudo trains, read-only, against the real backend: the yards and pseudo trains (VHYT09A, VHYT09B, VHYT09C), the
 * printers (WS70A01), the Hayward yard map (VHYT07A) and the verify window's opening (VHYT12A, VHYT12C). Print (VHYT01A),
 * Verify (VHYT12D) and Reset (VHYT12E) write and are never called here.
 */
class YardTrackingPseudoLiveTest extends LiveTestBase {
    @Test
    void yardsAndTheirPseudoTrains() throws Exception {
        JsonNode o = send("GET", "/api/v1/yard-tracking/pseudo-trains/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("yards").size()).isGreaterThan(1);
        String yard = o.get("yards").get(0).asText();
        JsonNode r = send("GET", "/api/v1/yard-tracking/pseudo-trains?yard=" + yard, null);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("message").asText()).startsWith(yard.length() >= 3 ? yard.substring(0, 3) : yard);
        for (JsonNode c : r.get("consists")) assertThat(c.get("consist").asText()).isNotBlank();
        JsonNode all = send("GET", "/api/v1/yard-tracking/pseudo-trains?yard=ALL", null);
        assertThat(all.get("_status").asInt()).isEqualTo(200);
        assertThat(all.get("consists").size()).isGreaterThanOrEqualTo(r.get("consists").size());
    }

    @Test
    void printers() throws Exception {
        JsonNode p = send("GET", "/api/v1/yard-tracking/pseudo-trains/printers", null);
        assertThat(p.get("_status").asInt()).isEqualTo(200);
    }

    @Test
    void everyCarTheOldHaywardMapShowsIsInTheApi() throws Exception {
        JsonNode clean = send("GET", "/api/v1/yard-tracking/hayward-yard-map", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        List<String> api = new ArrayList<>();
        for (JsonNode c : clean.get("cars")) api.add(c.get("label").asText());
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "Vhyt07i"));
        JsonNode widgets = null;
        for (JsonNode w : open.get("windows")) if (w.get("screen").asText().equalsIgnoreCase("Vhyt07i")) widgets = w.get("widgets");
        assertThat(widgets).as("Vhyt07i opened").isNotNull();
        List<String> old = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = widgets.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String v = e.getValue().path("value").asText("");
            if (e.getKey().matches("[A-Z0-9]+TXT\\d+") && v.matches(".*\\d.*")) old.add(v.strip());
        }
        for (String x : api) old.remove(x);
        assertThat(old).as("cars the old map shows that the API does not").isEmpty();
    }

    @Test
    void verifyWindowOpensWithoutWriting() throws Exception {
        JsonNode s = send("POST", "/api/v1/yard-tracking/consist-verify/open", Map.of("yard", "HY", "track", "T-10", "fromPosition", 1, "toPosition", 4));
        assertThat(s.get("_status").asInt()).isEqualTo(200);
        assertThat(s.has("cars")).isTrue();
    }
}
