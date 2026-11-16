package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Configuration tracking query screens that write (VHCF21I, VHCF37I / VHCF35I, VHCF06I), against the real backend, read
 * paths only: their lists, a truck retrieved, an axle listed. Add, build and axle Update are never called here.
 */
class ConfigQueriesUpdatesLiveTest extends LiveTestBase {
    private static String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** VHCF21I: component types (VHCF21A), then the first type's stock numbers (VHCF21B) */
    @Test
    void serialAddLists() throws Exception {
        JsonNode o = send("GET", "/api/v1/config-queries/serial-add/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("actions").toString()).isEqualTo("[\"ADD\"]");
        if (o.get("componentTypes").isEmpty()) return;
        JsonNode s = send("GET", "/api/v1/config-queries/serial-add/stocks?componentType=" + q(o.at("/componentTypes/0").asText()), null);
        assertThat(s.get("_status").asInt()).isEqualTo(200);
        assertThat(s.get("stocks").isArray()).isTrue();
    }

    /** VHCF37I: the truck stocks (VHCF35C), a real truck of the first stock (found through the VHCF16I inquiry), retrieved */
    @Test
    void truckInquiry() throws Exception {
        JsonNode o = send("GET", "/api/v1/config-queries/trucks/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("statuses")).isNotEmpty();
        if (o.get("stocks").isEmpty()) return;
        String stock = o.at("/stocks/0/stock").asText();
        JsonNode c = send("GET", "/api/v1/config-queries/components?sort=1&stock=" + q(stock), null);
        if (c.get("rows") == null || c.get("rows").isEmpty()) return;
        String serial = c.at("/rows/0/serial").asText();
        JsonNode t = send("GET", "/api/v1/config-queries/trucks?stock=" + q(stock) + "&serial=" + q(serial), null);
        assertThat(t.get("_status").asInt()).isEqualTo(200);
        assertThat(t.get("components")).hasSize(6);
        assertThat(t.get("updatable").asBoolean()).isFalse();                // the inquiry screen has no Update
    }

    /** VHCF06I: a serial that cannot exist lists nothing (only VHCF06A is called on that path) */
    @Test
    void unknownAxle() throws Exception {
        JsonNode a = send("GET", "/api/v1/config-queries/axles?kind=axle&serial=ZZ9999999999", null);
        assertThat(a.get("_status").asInt()).isEqualTo(200);
        assertThat(a.get("message").asText()).isNotBlank();
        assertThat(a.get("updatable").asBoolean()).isFalse();
    }
}
