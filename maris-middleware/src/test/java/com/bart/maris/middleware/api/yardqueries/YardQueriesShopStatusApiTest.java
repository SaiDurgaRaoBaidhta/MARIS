package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesEgl;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Shop status report (old screen VHYT25I): the clean API. Rules from MrsYardTrackingQueries/RUIFunctions.egl
 * VHYT25IS-INITIAL / -GET-DATA / -REFORMAT (VHYT25A), VHYT30IS-GET-DATA / -REFORMAT (VHYT30A), VHYT25IS-FINALCT.
 */
class YardQueriesShopStatusApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> crit25 = new AtomicReference<>(), crit30 = new AtomicReference<>();

    /** a VHYT25A row: elapsed time in the shop as years / months / days / hours / minutes */
    private static Map<String, Object> shopCar(String car, String track, int yr, int mo, int dd, int hh, int mm) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", 1, "carNo", car, "vagenOrientation", "<", "lftOrientCd", "<",
                "rgtOrientCd", ">", "vehStat", "NO GO", "dateInShop", "11-10-2026", "timeInShop", Map.of("timeInHhmm", "08:15"),
                "moveReasonDsc", "BRAKES"));
        m.put("currentTime", "14:30:00");
        m.putAll(Map.of("tsYr", yr, "tsMo", mo, "tsDd", dd, "tsHh", hh, "tsMm", mm, "trainIncdInd", "Y", "carType", Map.of("carLetter", "A")));
        return m;
    }

    /** a VHYT30A row; yardFirst: its yard time stamp is earlier than its shop one, so it came from the yard */
    private static Map<String, Object> outCar(String car, String track, boolean yardFirst) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", 2, "carNo", car, "vagenOrientation", ">", "lftOrientCd", "<",
                "rgtOrientCd", ">", "vehStat", "GO", "dateOutShop", "11-11-2026", "timeOutShop", Map.of("timeOutHhmm", "09:00"),
                "dateOutYrd", "11-09-2026"));
        m.put("timeOutYrd", Map.of("yTimeOutHhmm", "07:00"));
        m.putAll(Map.of("moveReasonDsc", "TEST", "tsDt", 20261111L, "tsyDt", yardFirst ? 20261109L : 20261112L,
                "tsDd", 1, "tsHh", 2, "tsMm", 3, "tsyDd", 4, "tsyHh", 5, "tsyMm", 6, "carType", Map.of("carLetter", "B")));
        return m;
    }

    private void backend(List<Map<String, Object>> shop, List<Map<String, Object>> out) {
        backend().on("VHYT25A", in -> { crit25.set(in.get("VHYT25A_CRIT")); return reply(shop.isEmpty() ? 100 : 0, shop.size(), Map.of("VHYT25A_DATA", rows("dataRow", shop))); });
        backend().on("VHYT30A", in -> { crit30.set(in.get("VHYT30A_CRIT")); return reply(out.isEmpty() ? 100 : 0, out.size(), Map.of("VHYT30A_DATA", rows("dataRow", out))); });
    }

    @Test
    void theShopsCarsWithTheirTimeInTheShopAndTheAverage() throws Exception {
        backend(List.of(shopCar("1001", "S-1", 0, 0, 2, 5, 40),       // 2 days 5:40 -> 2 days 6 hours (over 30 minutes)
                        shopCar("1002", "S-2", 0, 1, 0, 23, 45),       // 30 days 23:45 -> 31 days 0 hours
                        shopCar("1003", "T-24", 0, 0, 9, 9, 9)),       // T-24 is counted, not timed
                List.of(outCar("2001", "SHOP", true), outCar("2002", "T-24", false)));
        Answer a = get("/api/v1/yard-queries/shop-status/HY", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT25A", "VHYT30A");
        assertThat(crit25.get().get("yrdTrkgYrd")).isEqualTo("HY");
        assertThat(crit30.get()).containsEntry("yrdTrkgYrd", "HY").containsEntry("yrdSubLoc", "T-24");    // HY: its transfer track
        assertThat(a.body().get("shop").asText())
                .isEqualTo(YardQueriesEgl.pairs(Parity.bundle(), "VHYT25IS-INITIAL", "MrsYardTrackingQueries", "YRD-TRKG-YRD", "SHOP").get("HY"));
        assertThat(a.body().get("runTime").asText()).isEqualTo("14:30");
        assertThat(a.body().at("/cars/0/car").asText()).isEqualTo("1001");
        assertThat(a.body().at("/cars/0/leftEnd").asText()).isEqualTo("<");                 // the car's orientation is its left end
        assertThat(a.body().at("/cars/0/incident").asBoolean()).isTrue();
        assertThat(a.body().at("/cars/0/elapsedDays").asInt()).isEqualTo(2);
        assertThat(a.body().at("/cars/0/elapsedHours").asInt()).isEqualTo(6);
        assertThat(a.body().at("/cars/1/elapsedDays").asInt()).isEqualTo(31);
        assertThat(a.body().at("/cars/1/elapsedHours").asInt()).isEqualTo(0);
        assertThat(a.body().at("/cars/2").has("elapsedDays")).isFalse();
        // (54 + 744) hours / 2 timed cars = 399 hours = 16 days 15 hours
        assertThat(a.body().get("averageDays").asInt()).isEqualTo(16);
        assertThat(a.body().get("averageHours").asInt()).isEqualTo(15);
        assertThat(a.body().get("totalCars").asInt()).isEqualTo(3);

        assertThat(a.body().at("/outOfYard/cars/0/origin").asText()).isEqualTo("YARD");
        assertThat(a.body().at("/outOfYard/cars/0/dateIn").asText()).isEqualTo("11-09-2026");
        assertThat(a.body().at("/outOfYard/cars/0/elapsedDays").asInt()).isEqualTo(4);
        assertThat(a.body().at("/outOfYard/cars/0/elapsedMinutes").asInt()).isEqualTo(6);
        assertThat(a.body().at("/outOfYard/cars/1/origin").asText()).isEqualTo("SHOP");
        assertThat(a.body().at("/outOfYard/cars/1/rightEnd").asText()).isEqualTo(">");
        assertThat(a.body().get("grandTotal").asInt()).isEqualTo(4);                          // 3 + the one car on SHOP
    }

    @Test
    void aYardWithNoCarsIsEmpty() throws Exception {
        backend(List.of(), List.of());
        Answer a = get("/api/v1/yard-queries/shop-status/CY", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit30.get()).containsEntry("yrdSubLoc", "");
        assertThat(a.body().get("cars").size()).isZero();
        assertThat(a.body().has("averageDays")).isFalse();
        assertThat(a.body().get("grandTotal").asInt()).isZero();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHYT25A", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("SHOP", "", ""))), "00000000"));
        Answer a = get("/api/v1/yard-queries/shop-status/HY", null);
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("SHOP Is Not Found");
    }

    @Test
    void aBadYardIsRefusedAndCallsNothing() throws Exception {
        Answer a = get("/api/v1/yard-queries/shop-status/TOOLONG", null);
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void theApiNeedsASignedOnUser() throws Exception {
        send("POST", "/api/v1/auth/signoff", null);
        assertThat(get("/api/v1/yard-queries/shop-status/HY", null).status()).isEqualTo(401);
    }
}
