package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Table maintenance 2 (MRS messages received), read-only, against the real backend. List is never run here: VHCCS04I_LIST
 * calls VHYTIME, which writes; nor are the saves, deletes or the hard copy.
 */
class Tables2LiveTest extends LiveTestBase {
    /** VHCCS04I's criteria lists (VHCCS4E, 4F, 4G, 4H, 4I) and the proposed date */
    @Test
    void options() throws Exception {
        JsonNode o = send("GET", "/api/v1/tables-2/messages/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("warnings")).isEmpty();
        assertThat(o.get("yards").size() + o.get("tranCodes").size() + o.get("locations").size()).isPositive();
        for (JsonNode s : o.get("scanners")) assertThat(s.get("id").asText()).startsWith(s.get("scannerId").asText());
        assertThat(o.get("fromDate").asText()).matches("\\d{2}-\\d{2}-\\d{4}");
    }

    /** VHCCS18I's printers and lists (WS70A01, VHCCSHC / B / D / E / F) */
    @Test
    void printOptions() throws Exception {
        JsonNode o = send("GET", "/api/v1/tables-2/messages/print-options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("warnings")).isEmpty();
        assertThat(o.get("printers")).isNotEmpty();
        assertThat(o.get("sorts").size()).isEqualTo(2);
    }

    /** VHCCS06I / VHCCS07I lists (VHCCS6A, 6E, 7A) for today's time-stamp window */
    @Test
    void carsAndErrorsOfAWindow() throws Exception {
        String day = LocalDate.now().toString();
        String q = "?from=" + URLEncoder.encode(day + "-00.00.00.000000", StandardCharsets.UTF_8) + "&to=" + URLEncoder.encode(day + "-23.59.59.000000", StandardCharsets.UTF_8);
        JsonNode cars = send("GET", "/api/v1/tables-2/messages/cars" + q, null);
        assertThat(cars.get("_status").asInt()).isEqualTo(200);
        for (JsonNode c : cars.get("cars")) assertThat(c.get("timestamp").asText()).startsWith(day);
        JsonNode errors = send("GET", "/api/v1/tables-2/messages/errors" + q, null);
        assertThat(errors.get("_status").asInt()).isEqualTo(200);
    }
}
