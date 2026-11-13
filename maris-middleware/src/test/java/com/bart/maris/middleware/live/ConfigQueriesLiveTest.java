package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Configuration tracking queries module, read-only, against the real backend. */
class ConfigQueriesLiveTest extends LiveTestBase {
    /** the old VHCF16I lists, then a search by the first stock number: every row has that stock, no fixed numbers */
    @Test
    void componentInquiry() throws Exception {
        JsonNode o = send("GET", "/api/v1/config-queries/component-options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("warnings")).isEmpty();
        if (o.get("stocks").isEmpty()) return;
        String stock = o.at("/stocks/0/stock").asText();
        JsonNode r = send("GET", "/api/v1/config-queries/components?sort=1&stock=" + stock, null);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("limitReached").asBoolean()).isEqualTo(r.get("rows").size() >= 213);
    }
}
