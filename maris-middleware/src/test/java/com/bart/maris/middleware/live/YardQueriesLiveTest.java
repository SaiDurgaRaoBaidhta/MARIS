package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Yard tracking queries module, read-only, against the real backend. */
class YardQueriesLiveTest extends LiveTestBase {
    /**
     * the shop status report on real data: every car but T-24's is timed, the totals add up. (The engine cannot run the old
     * VHYT25I with a yard, see YardQueriesShopStatusParityTest, so this checks the report itself, with no fixed numbers.)
     */
    @Test
    void shopStatusReport() throws Exception {
        for (String yard : new String[]{"HY", "CY", "RY", "DY"}) {
            JsonNode r = send("GET", "/api/v1/yard-queries/shop-status/" + yard, null);
            assertThat(r.get("_status").asInt()).as(yard).isEqualTo(200);
            assertThat(r.get("totalCars").asInt()).as(yard).isEqualTo(r.get("cars").size());
            assertThat(r.get("grandTotal").asInt()).as(yard).isGreaterThanOrEqualTo(r.get("totalCars").asInt());
            for (JsonNode c : r.get("cars")) {
                assertThat(c.has("elapsedDays")).as(yard + " " + c.get("car")).isEqualTo(!c.get("track").asText().equals("T-24"));
            }
        }
    }

    /** the history reports on the last ten days of real data: they answer, and a refused range is the old message */
    @Test
    void historyReports() throws Exception {
        String to = java.time.LocalDate.now().toString(), from = java.time.LocalDate.now().minusDays(10).toString();
        JsonNode cars = send("GET", "/api/v1/yard-queries/car-history?yard=HY&from=" + from + "&to=" + to, null);
        assertThat(cars.get("_status").asInt()).isEqualTo(200);
        assertThat(cars.get("maxRowsReached").asBoolean()).isEqualTo(cars.get("rows").size() >= 324);
        JsonNode status = send("GET", "/api/v1/yard-queries/status-history?yard=HY&from=" + from + "&to=" + to, null);
        assertThat(status.get("_status").asInt()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/yard-queries/status-history?from=" + to + "&to=" + from, null).get("_status").asInt()).isEqualTo(400);
    }

    /** VHYT27I's lists (read-only); Hard Copy asks MARIS to print, so it is never run here */
    @Test
    void reportOptions() throws Exception {
        JsonNode o = send("GET", "/api/v1/yard-queries/report-options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("warnings")).isEmpty();
        assertThat(o.get("yards")).isNotEmpty();
        assertThat(o.get("from").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
