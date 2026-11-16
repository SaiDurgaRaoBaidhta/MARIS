package com.bart.maris.middleware.api.prevmaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Preventive maintenance reports over HTTP: inputs, the old messages, the computed totals, printing. */
class PrevMaintApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    @BeforeEach
    void dates() {
        backend().on("VHHPMA3", in -> reply(0, 1, Map.of("VHHPMA3_DATA", Map.of("timeRevStart", "03:00:00", "currentTime", "02:10:00",
                "currentDate", "2026-11-13", "yesterday", "2026-11-12"))));
        backend().on("VHHPMA2", in -> reply(0, 1, Map.of("VHHPMA2_DATA", Map.of("fromDate", "2026-01-01"))));
    }

    @Test
    void optionsGiveTheDatesListsAndPrinters() throws Exception {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("prtrLocDesc", "DUBLIN TMZ", "vtamName", "DUBLNHP1"))))));
        Answer a = get("/api/v1/preventive-maintenance/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("revenueDate").asText()).isEqualTo("2026-11-12");        // before the revenue day starts: yesterday
        assertThat(a.body().get("fromDate").asText()).isEqualTo("2026-01-01");
        assertThat(a.body().get("reports").size()).isEqualTo(5);
        assertThat(a.body().get("homeYards").toString()).isEqualTo("[\"ALL\",\"CY\",\"DY\",\"HY\",\"RY\"]");
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void hoursByYardSendsTheYardRangeAndDatesAndMarksTrainIncidents() throws Exception {
        backend().on("VHHPRA1", in -> { crit.set(in.get("VHHPRA1_CRIT")); return reply(0, 2, Map.of("VHHPRA1_DATA", rows("dataRow", List.of(
                Map.of("carNo", "1101", "vehStat", "GO", "currLoc", "CY", "homeYrd", "CY", "whereIsCar", "YRD", "trainIncdInd", "Y", "hrsFromLastPm", 120),
                Map.of("carNo", "1102", "vehStat", "NO GO", "currLoc", "M10", "homeYrd", "CY", "whereIsCar", "CON", "conNo", "532", "trainIncdInd", "N"))))); });
        Answer a = get("/api/v1/preventive-maintenance/reports/hours-by-yard", Map.of("homeYard", "ALL"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("homeYrdLow", "CY").containsEntry("homeYrdHigh", "RY")
                .containsEntry("dateFrom", "2026-01-01").containsEntry("dateTo", "2026-11-12");
        assertThat(a.body().at("/cars/0/trainIncident").asText()).isEqualTo("TI");
        assertThat(a.body().at("/cars/1/location").asText()).isEqualTo("532");               // on a train: its consist
        assertThat(a.body().at("/cars/0/hoursSinceLastPm").asInt()).isEqualTo(120);
    }

    @Test
    void mtbiAddsUpPerYardAndForAll() throws Exception {
        backend().on("VHHPMA1", in -> reply(0, 3, Map.of("VHHPMA1_DATA", rows("dataRow", List.of(
                Map.of("carNo", "1101", "homeYrd", "CY", "mtbiHrs", 900, "mtbiIncds", 3),
                Map.of("carNo", "1102", "homeYrd", "CY", "mtbiHrs", 100, "mtbiIncds", 1),
                Map.of("carNo", "2101", "homeYrd", "DY", "mtbiHrs", 500, "mtbiIncds", 0))))));
        Answer a = get("/api/v1/preventive-maintenance/reports/mtbi", Map.of("homeYard", "ALL"));
        assertThat(a.body().at("/mtbi/0").toString()).isEqualTo("{\"shop\":\"CY\",\"hours\":1000,\"incidents\":4,\"cars\":2,\"average\":250.0}");
        assertThat(a.body().at("/mtbi/1/average").asDouble()).isZero();                       // no incidents: 0, not a division error
        assertThat(a.body().at("/mtbi/2").toString()).isEqualTo("{\"shop\":\"ALL\",\"hours\":1500,\"incidents\":4,\"cars\":3,\"average\":375.0}");
    }

    @Test
    void aReportNeedsAYardOrLocation() throws Exception {
        for (String r : List.of("hours-by-yard", "mtbi", "by-pm-type", "hours-descending")) {
            Answer a = get("/api/v1/preventive-maintenance/reports/" + r, Map.of());
            assertThat(a.status()).as(r).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHHPM1I", "NO-HOME-YRD"));
        }
        assertThat(get("/api/v1/preventive-maintenance/reports/hours-to-next", Map.of("homeYard", "CY")).status()).isEqualTo(400);
        assertThat(get("/api/v1/preventive-maintenance/reports/nonsense", Map.of("homeYard", "CY")).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHHPRA1", "VHHPMA1", "VHPMRA1", "VHHPDA1", "VHHPD2A");
    }

    @Test
    void printChecksThePmCriteriaLikeTheOldScreen() throws Exception {
        backend().on("VHHPM01", in -> { crit.set(in.get("VHHPM01_CRIT")); return reply(0, 0, Map.of()); });
        Answer bad = post("/api/v1/preventive-maintenance/reports/by-pm-type/print", Map.of("homeYard", "CY", "pmType", "2", "carFrom", "0500",
                "carTo", "0100", "printer", "DUBLNHP1"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHHPM1I", "NO-VALID-CRITERIA"));
        assertThat(called()).doesNotContain("VHHPM01");

        Answer ok = post("/api/v1/preventive-maintenance/reports/by-pm-type/print", Map.of("homeYard", "CY", "pmType", "2", "carFrom", "0100",
                "carTo", "0500", "hoursFrom", 10, "hoursTo", 200, "printer", "DUBLNHP1"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHHPM1I", "SUBMIT"));
        assertThat(crit.get()).containsEntry("reptId", "3").containsEntry("pmHoursFrom", "0010").containsEntry("reptType", "DUBLNHP1");
    }

    @Test
    void printOfReportFiveNeedsALocation() throws Exception {
        Answer a = post("/api/v1/preventive-maintenance/reports/hours-to-next/print", Map.of("printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHHPM1I", "NO-LOC"));
    }
}
