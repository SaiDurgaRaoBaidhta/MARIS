package com.bart.maris.middleware.api.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Car wash reports (old screens VHCWR1I washes by date range, VHCWR2I wash over due). Rules from
 * MRSVehicleCarWash/RUIFunctions.egl: VHCWR1IS-RET-DATA (VHCWR1A), VHCWR2IS-RET-DATA (VHCWR2A).
 */
class CarWashApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private void washes(int urc, List<Map<String, Object>> rows) {
        backend().on("VHCWR1A", in -> { crit.set(in.get("VHCWR1A_CRIT")); return reply(urc, rows.size(), Map.of("VHCWR1A_DATA", rows("dataRow", rows))); });
    }

    private void overdue(int urc, List<Map<String, Object>> rows) {
        backend().on("VHCWR2A", in -> { crit.set(in.get("VHCWR2A_CRIT")); return reply(urc, rows.size(), Map.of("VHCWR2A_DATA", rows("dataRow", rows))); });
    }

    @Test
    void washesSendTheYardAndTheOldDatesAndListEveryCar() throws Exception {
        washes(0, List.of(Map.of("carNo", "1101", "washedDate", "11-02-2026", "washedTime", "07:15", "carNo1", "1102", "washedDate1", "11-03-2026", "washedTime1", "08:00")));
        Answer a = get("/api/v1/car-wash/washes", Map.of("yard", "cy", "from", "2026-11-01", "to", "2026-11-12"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("yard", "CY").containsEntry("fromDate", "11-01-2026").containsEntry("toDate", "11-12-2026");
        assertThat(a.body().get("count").asInt()).isEqualTo(2);
        assertThat(a.body().at("/washes/1").toString()).isEqualTo("{\"car\":\"1102\",\"date\":\"11-03-2026\",\"time\":\"08:00\"}");
        assertThat(a.body().get("maxRowsReached").asBoolean()).isFalse();
    }

    @Test
    void theChecksRunInTheOldOrderWithTheOldMessages() throws Exception {
        washes(0, List.of());
        String today = LocalDate.now().toString(), tomorrow = LocalDate.now().plusDays(1).toString();
        Object[][] cases = {
                {"", "2026-11-01", "2026-11-02", "INVALID-YARD"},
                {"SVB", "2026-11-01", "2026-11-02", "INVALID-YARD"},
                {"CY", "", "2026-11-02", "INVALID-FROM-DATE"},
                {"CY", "2026-02-30", "2026-11-02", "INVALID-FROM-DATE"},
                {"CY", tomorrow, today, "FROM-GR-SYS"},
                {"CY", "2026-11-01", "x", "INVALID-TO-DATE"},
                {"CY", "2026-11-01", tomorrow, "TO-GR-SYS"},
                {"CY", "2026-11-05", "2026-11-01", "FROM-LESS-TO"},
        };
        for (Object[] c : cases) {
            Answer a = get("/api/v1/car-wash/washes", Map.of("yard", c[0], "from", c[1], "to", c[2]));
            assertThat(a.status()).as(c[3].toString()).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCWR1I", (String) c[3]));
        }
        assertThat(called()).doesNotContain("VHCWR1A");
    }

    @Test
    void fiveHundredRowsShowTheOldFooter() throws Exception {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) many.add(Map.of("carNo", String.valueOf(1000 + i)));
        washes(0, many);
        Answer a = get("/api/v1/car-wash/washes", Map.of("yard", "HY", "from", "2026-11-01", "to", "2026-11-02"));
        assertThat(a.body().get("maxRowsReached").asBoolean()).isTrue();
        assertThat(a.body().get("note").asText()).isEqualTo(TEXTS.label("VHCWR1I", "lblFooter"));
    }

    @Test
    void noCarsGivesTheOldMessage() throws Exception {
        washes(100, List.of());
        Answer a = get("/api/v1/car-wash/washes", Map.of("yard", "DY", "from", "2026-11-01", "to", "2026-11-02"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("count").asInt()).isZero();
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCWR1I", "NO-CARS-FOUND"));
    }

    @Test
    void overdueSendsTheHomeYardAndListsTheCars() throws Exception {
        overdue(0, List.of(Map.of("carNo", "1101", "dateLastWash", "10-01-2026", "timeLastWash", "06:00", "currLoc", "HY03", "daysOverdue", 12, "totalWashes", 40)));
        Answer a = get("/api/v1/car-wash/overdue", Map.of("homeYard", "HY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("homeYrd", "HY");
        assertThat(a.body().at("/cars/0").toString()).isEqualTo(
                "{\"car\":\"1101\",\"lastWashDate\":\"10-01-2026\",\"lastWashTime\":\"06:00\",\"location\":\"HY03\",\"daysOverdue\":12,\"totalWashes\":40}");
    }

    @Test
    void overdueChecksTheHomeYard() throws Exception {
        overdue(0, List.of());
        Answer a = get("/api/v1/car-wash/overdue", Map.of("homeYard", "ALL"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCWR2I", "INVALID-YARD"));
        assertThat(called()).doesNotContain("VHCWR2A");
    }
}
