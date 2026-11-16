package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Table maintenance 3 (component serials), read-only, against the real backend: the stock list, a listing of every
 * serial, then one listed serial's detail form, hours and geo data. No update: VHCF50D, VHCF50A, VHCF51B, VHCF52B write.
 */
class Tables3LiveTest extends LiveTestBase {
    private static String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void componentSerialsReadOnly() throws Exception {
        JsonNode o = send("GET", "/api/v1/tables-3/component-serials/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("stocks")).isNotEmpty();
        JsonNode l = send("GET", "/api/v1/tables-3/component-serials", null);
        assertThat(l.get("_status").asInt()).isEqualTo(200);
        if (l.get("rows").isEmpty()) return;
        JsonNode row = l.at("/rows/0");
        for (JsonNode r : l.get("rows")) if (!r.get("cmpntSerialNo").asText().isBlank()) { row = r; break; }   // a row with a serial, when there is one
        String stock = row.get("stockNo").asText(), serial = row.get("cmpntSerialNo").asText();
        if (!stock.isBlank()) {
            JsonNode one = send("GET", "/api/v1/tables-3/component-serials?stock=" + q(stock), null);
            for (JsonNode r : one.get("rows")) assertThat(r.get("stockNo").asText()).isEqualTo(stock);
        }
        JsonNode f = send("POST", "/api/v1/tables-3/component-serials/form", row);
        assertThat(f.get("_status").asInt()).isEqualTo(200);
        assertThat(f.at("/form/cmpntSerialNo").asText()).isEqualTo(serial);
        String key = "?stock=" + q(stock) + "&serial=" + q(serial);
        JsonNode h = send("GET", "/api/v1/tables-3/serial-hours" + key, null);
        assertThat(h.get("_status").asInt()).isEqualTo(200);
        for (JsonNode r : h.get("rows")) assertThat(r.get("cmpntSerialNo").asText()).isEqualTo(serial);
        JsonNode g = send("GET", "/api/v1/tables-3/serial-geo-data" + key, null);
        assertThat(g.get("_status").asInt()).isEqualTo(200);
        for (JsonNode r : g.get("rows")) assertThat(r.get("cmpntSerialNo").asText()).isEqualTo(serial);
    }
}
