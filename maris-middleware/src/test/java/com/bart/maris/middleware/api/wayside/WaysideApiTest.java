package com.bart.maris.middleware.api.wayside;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Wayside: stock number search (old screen WS17I). Rules from MRSWaysideMaintenance/RUIFunctions.egl:
 * WS17IS-REFORMAT builds the LIKE pattern, WS17IS-SEARCH calls WS17A05.
 */
class WaysideApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private void stock(int urc, List<Map<String, Object>> rows) {
        backend().on("WS17A05", in -> {
            crit.set(in.get("WS17A05_CRIT"));
            return reply(urc, rows.size(), Map.of("WS17A05_DATA", rows("dataRow", rows)));
        });
    }

    @Test
    void searchSendsTheOldLikePatternAndListsTheStockNumbers() throws Exception {
        stock(0, List.of(Map.of("deptCd", "E", "stockNo", "123456789", "stockDesc", "BRAKE SHOE"),
                Map.of("deptCd", "E", "stockNo", "223456789", "stockDesc", "BRAKE PAD")));
        Answer a = get("/api/v1/wayside/stock-numbers", Map.of("description", "BRAKE", "dept", "E"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get().get("stockDesc")).isEqualTo("%BRAKE%%%%%");      // "%" + text, trailing blanks as "%"
        assertThat(crit.get().get("deptCd")).isEqualTo("E");
        assertThat(a.body().get("count").asInt()).isEqualTo(2);
        assertThat(a.body().at("/stockNumbers/0").toString())
                .isEqualTo("{\"dept\":\"E\",\"stockNo\":\"123456789\",\"description\":\"BRAKE SHOE\"}");
    }

    @Test
    void anEmptyDescriptionSearchesEverything() throws Exception {
        stock(0, List.of());
        get("/api/v1/wayside/stock-numbers", Map.of());
        assertThat(crit.get().get("stockDesc")).isEqualTo("%%%%%%%%%%%");
        assertThat(crit.get().get("deptCd")).isEqualTo("");
    }

    @Test
    void innerSpacesStayAndOnlyTrailingOnesBecomeWildcards() throws Exception {
        stock(0, List.of());
        get("/api/v1/wayside/stock-numbers", Map.of("description", "AB C"));
        assertThat(crit.get().get("stockDesc")).isEqualTo("%AB C%%%%%%");
    }

    @Test
    void theDescriptionHoldsTenCharactersAndTheDeptOne() throws Exception {
        stock(0, List.of());
        assertThat(get("/api/v1/wayside/stock-numbers", Map.of("description", "ABCDEFGHIJK")).status()).isEqualTo(400);
        assertThat(get("/api/v1/wayside/stock-numbers", Map.of("dept", "EE")).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("WS17A05");
    }

    @Test
    void noRowsGivesAnEmptyListWithTheMarisMessage() throws Exception {
        backend().on("WS17A05", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL",
                Map.of("urc", 100, "umsgcode", "REC NOT FND", "umsgins", List.of("STOCK NO", "", ""))), "00000000"));
        Answer a = get("/api/v1/wayside/stock-numbers", Map.of("description", "ZZZ"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("count").asInt()).isZero();
        assertThat(a.body().get("message").asText()).isEqualTo("STOCK NO Is Not Found");
    }

    @Test
    void aBackendErrorIsReportedWithItsMessage() throws Exception {
        backend().on("WS17A05", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL",
                Map.of("urc", -805, "umsgcode", "REC NOT FND", "umsgins", List.of("STOCK", "", ""))), "00000000"));
        Answer a = get("/api/v1/wayside/stock-numbers", Map.of("description", "BRAKE"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("STOCK Is Not Found");
    }
}
