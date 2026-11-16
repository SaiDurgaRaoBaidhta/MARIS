package com.bart.maris.middleware.api.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Car wash updates: a consist (old VHCWA2I) and single cars (old VHCWA3I), against a fake backend. */
class CarWashUpdateApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleCarWash";
    private final AtomicReference<Map<String, Map<String, Object>>> written = new AtomicReference<>();

    private void revenueDay(String now) {
        backend().on("VHIE01B", in -> reply(0, 1, Map.of("VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentDate", "2026-11-14", "currentTime", now,
                "yesterday", "2026-11-13"))));
    }

    private void consist(String washInd) {
        backend().on("VHCWA5", in -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String car : List.of("1101", "1802")) rows.add(Map.of("carNo", car, "washInd", "N"));
            return reply(0, 2, Map.of("VHCWA5_CRIT", Map.of("conNo", "201", "dashNo", 3), "VHCWA5_DATA", rows("dataRow", rows)));
        });
        backend().on("VHCWA3", in -> reply(0, 0, Map.of("VHCWA5_DATA", rows("dataRow", List.of(Map.of("carNo", "1101", "washInd", washInd))))));
        backend().on("VHCWA6", in -> { written.set(in); return reply(0, 0, Map.of()); });
    }

    @Test
    void startsOnTheRevenueDate() throws Exception {
        revenueDay("02:00:00");
        Answer a = get("/api/v1/car-wash/consist-washes/start", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("washDate").asText()).isEqualTo("2026-11-13");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCWA2IS-BEGIN", PKG, "MSG", null));
        revenueDay("09:00:00");
        Answer b = get("/api/v1/car-wash/car-washes/start", null);
        assertThat(b.body().get("washDate").asText()).isEqualTo("2026-11-14");
        assertThat(b.body().get("consist").asText()).isEqualTo(TEXTS.assigned("VHCWA3IS-CLEAR", PKG, "CON-NO", null));
    }

    @Test
    void consistListsItsCarsAndDash() throws Exception {
        consist("N");
        Answer a = get("/api/v1/car-wash/consist-washes/consist", Map.of("consist", "201", "date", "2026-11-14"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("cars").toString()).isEqualTo("[\"1101\",\"1802\"]");
        assertThat(a.body().get("dashNo").asInt()).isEqualTo(3);
        assertThat(a.body().get("alreadyWashed").asBoolean()).isFalse();
    }

    @Test
    void washesTheConsistWithTheNormalisedTime() throws Exception {
        consist("N");
        Answer a = post("/api/v1/car-wash/consist-washes", Map.of("consist", "201", "date", "2026-11-14", "time", "0715", "yard", "hy"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCWA2IS-UPDATE", PKG, "MSG", "Updated"));
        Map<String, Object> d = written.get().get("VHCWA6_DATA");
        assertThat(d).containsEntry("timeCarWash", "07:15:00").containsEntry("carWashLoc", "HY").containsEntry("dateCarWash", "2026-11-14")
                .containsEntry("totalCars", 2L).containsEntry("dashNo", 3L);
    }

    @Test
    void anAlreadyWashedConsistIsNotWashedAgain() throws Exception {
        consist("Y");
        Answer a = post("/api/v1/car-wash/consist-washes", Map.of("consist", "201", "date", "2026-11-14", "time", "0715", "yard", "HY"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assignedUnder("VHCWA2I-GET-CARS", PKG, "MSG", "WASH-IND").get(0));
        assertThat(called()).doesNotContain("VHCWA6");
    }

    @Test
    void aWriteErrorIsA422() throws Exception {
        consist("N");
        backend().on("VHCWA6", in -> reply(-803, 0, Map.of()));
        Answer a = post("/api/v1/car-wash/consist-washes", Map.of("consist", "201", "date", "2026-11-14", "time", "0715", "yard", "HY"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).startsWith(TEXTS.assigned("VHCWA2IS-UPDATE", PKG, "MSG", "problem"));
    }

    private void cars(String invalidCar) {
        backend().on("VHINVB", in -> reply(0, 0, Map.of()));
        backend().on("VHCWA3A", in -> reply(0, 0, Map.of("VHCWA3A_DATA", Map.of("carIsValid", in.get("VHCWA3A_CRIT").get("carNo").equals(invalidCar) ? "N" : "Y"))));
        backend().on("VHCWA3B", in -> reply(0, 1, Map.of("VHCWA3B_DATA", Map.of("dateCarWash", "2026-11-14", "dateNxtCarWash", "2026-11-21", "cntTimesWash", 4))));
        backend().on("VHCWA3D", in -> { written.set(in); return reply(0, 0, Map.of()); });
    }

    @Test
    void washesSingleCars() throws Exception {
        cars("");
        Answer a = post("/api/v1/car-wash/car-washes", Map.of("yard", "CY", "date", "2026-11-14", "time", "07.15", "consist", "088",
                "cars", Arrays.asList("1101", "1102", "", "1104")));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("cars").toString()).isEqualTo("[\"1101\",\"1102\"]");
        assertThat(written.get().get("VHCWA3IW-CARS")).containsEntry("numOfCars", 2L).containsEntry("carNo4", "1104");
        assertThat(written.get().get("VHCWA3D_DATA")).containsEntry("conNo", "088").containsEntry("timeCarWash", "07:15:00");
        assertThat(called()).containsSubsequence("VHINVB", "VHCWA3A", "VHCWA3A", "VHCWA3B", "VHCWA3B", "VHCWA3D");
    }

    @Test
    void invalidCarsComeBackWithTheirBoxes() throws Exception {
        cars("1102");
        Answer a = post("/api/v1/car-wash/car-washes", Map.of("yard", "CY", "date", "2026-11-14", "time", "0715", "consist", "088", "cars", List.of("1101", "1102")));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCWA3IS-VAL-CARS", PKG, "FINAL-MSG", "invalid"));
        assertThat(a.body().get("invalidBoxes").toString()).isEqualTo("[2]");
        assertThat(called()).doesNotContain("VHCWA3D");
    }

    @Test
    void timeAndYardChecksComeFirst() throws Exception {
        cars("");
        Answer t = post("/api/v1/car-wash/car-washes", Map.of("yard", "CY", "date", "2026-11-14", "time", "", "cars", List.of("1101")));
        assertThat(t.status()).isEqualTo(400);
        assertThat(t.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "Time Field"));
        Answer y = post("/api/v1/car-wash/car-washes", Map.of("yard", "SF", "date", "2026-11-14", "time", "0700", "cars", List.of("1101")));
        assertThat(y.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHCWA3IS-VAL-YRD", PKG, "FINAL-MSG").get(0));
        Answer c = get("/api/v1/car-wash/car-washes/time", Map.of("time", "2501"));
        assertThat(c.body().get("valid").asBoolean()).isFalse();
        assertThat(c.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "HOURS"));
        assertThat(called()).doesNotContain("VHINVB");
    }
}
