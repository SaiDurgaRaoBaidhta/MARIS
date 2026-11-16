package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Consist tracking over HTTP (old VHST01I .. VHST05I). */
class VehicleQueriesConsistTrackingApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String BASE = "/api/v1/vehicle-queries/consist-tracking";
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private static Map<String, Object> car(String car, String con, int dash, String vehStat) {
        return Map.ofEntries(Map.entry("carNo", car), Map.entry("consist", Map.of("conNo", con, "conHyphen", "-", "dashNo", dash)), Map.entry("homeYrd", "CY"),
                Map.entry("originLoc", "CY"), Map.entry("dateInsrvRev", "2026-11-10"), Map.entry("conStat", "CON"), Map.entry("vehStat", vehStat),
                Map.entry("timeInsrv", "05:10:00"), Map.entry("trainIncdInd", "N"), Map.entry("vagenOrientation", "L"), Map.entry("lftOrientationCd", "L"),
                Map.entry("rgtOrientationCd", "R"), Map.entry("msgLoc", "M1"), Map.entry("scannerDir", "N"), Map.entry("orientOfCars", "A"),
                Map.entry("jaIncdInd", "N"), Map.entry("carWnInd", "N"), Map.entry("conWnInd", "N"));
    }

    @Test
    void optionsGiveTheReportsAndPrinters() throws Exception {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
        Answer a = get(BASE + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("reports").size()).isEqualTo(TEXTS.items("VHST05I", "Radio_Button_Set1").size());
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void statusTrainsGroupTheCarsByConsist() throws Exception {
        backend().on("VHST2A1", in -> {
            crit.set(in.get("VHST2A1_CRIT"));
            return reply(0, 3, Map.of("VHST2A1_DATA", rows("dataRow", List.of(car("1101", "201", 1, "GO"), car("1102", "201", 1, "USABLE"), car("1201", "305", 2, "NO GO")))));
        });
        Answer a = get(BASE + "/status-trains", Map.of("from", "ALL"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("conNoLow", 100L).containsEntry("conNoHigh", 999L);
        assertThat(a.body().get("trains").size()).isEqualTo(2);
        assertThat(a.body().at("/trains/0/consist").asText()).isEqualTo("201-01");
        assertThat(a.body().at("/trains/0/origin").asText()).isEqualTo("Concord");
        assertThat(a.body().at("/trains/0/dateInService").asText()).isEqualTo("11-10-2026");
        assertThat(a.body().at("/trains/0/cars").size()).isEqualTo(2);
        assertThat(a.body().at("/trains/0/cars/0/leftMark").asText()).isEqualTo("L");
        assertThat(a.body().at("/trains/0/cars/1/usable").asBoolean()).isTrue();
        assertThat(a.body().at("/trains/1/cars/0/alert").asBoolean()).isTrue();
    }

    @Test
    void aBadRangeIsRefusedWithTheOldMessage() throws Exception {
        Answer a = get(BASE + "/status-trains", Map.of("from", "300", "to", "200"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().toString()).contains(TEXTS.assignedAll("VHST01IS-MSG", "MRSVehicleQueries", "WS-MSG").get(1));
        Answer b = get(BASE + "/car-details", Map.of("from", ""));
        assertThat(b.status()).isEqualTo(400);
        assertThat(b.body().toString()).contains(TEXTS.assignedAll("VHST01IS-MSG", "MRSVehicleQueries", "WS-MSG").get(0));
    }

    @Test
    void carDetailsAskEachCarsCleaning() throws Exception {
        backend().on("VHST4A2", in -> reply(0, 1, Map.of("VHST4A2_DATA", rows("dataRow", List.of(Map.of("carType", "A", "cntDaysBiwkOd", 14, "cntDaysShampoOd", 30,
                "cntDaysThoroOd", 60))))));
        backend().on("VHST04A", in -> reply(0, 1, Map.of("VHST04A_DATA", rows("dataRow", List.of(Map.of("consist", Map.of("conNo", "201", "conHyphen", "-", "dashNo", 1),
                "carNo", "1101", "originLoc", "HY", "dateInsrvRev", "2026-11-10", "timeInsrv", "06:00:00"))))));
        backend().on("VHST04B", in -> reply(0, 1, Map.of("VHST04B_CRIT", Map.of("carNo", "1101", "vagenCount", 1, "totClnCount", 3),
                "VHST04B_DATA", rows("dataRow", List.of(Map.of("currSchdPmNo", "", "hrsFromLastPm", 42, "homeYrd", "HY", "dateNxtCarWash", "10-01-2026",
                        "dateCarCleanBwk", "11-01-2026", "dateCarCleanShm", "11-01-2026", "dateCarCleanThr", "11-01-2026", "washDays", 0))))));
        Answer a = get(BASE + "/car-details", Map.of("from", "201"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/trains/0/origin").asText()).isEqualTo("Hayward");
        assertThat(a.body().at("/trains/0/cars/0/pm").asText()).isEqualTo("00042");
        assertThat(a.body().at("/trains/0/cars/0/homeYard").asText()).isEqualTo("HY");
    }

    @Test
    void symptomsNeedASymptom() throws Exception {
        Answer a = get(BASE + "/symptoms", Map.of("consist", "201"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().toString()).contains(TEXTS.assignedAll("VHST01IS-DISPLAY-X", "MRSVehicleQueries", "WS-MSG").get(0));
    }

    @Test
    void symptomsReadTheDatesThenTheConsist() throws Exception {
        backend().on("VHST3A3", in -> reply(0, 1, Map.of("VHST3A3_DATA", Map.of("fromDate", "2026-01-01"))));
        backend().on("VHST3A4", in -> reply(0, 1, Map.of("VHST3A4_DATA", Map.of("toDate", "2026-11-13"))));
        backend().on("VHST3A1", in -> {
            crit.set(in.get("VHST3A1_CRIT"));
            return reply(0, 1, Map.of("VHST3A1_DATA", Map.of("rowCount", 1, "dataRow", List.of(Map.of("carNo", "1101", "consist",
                    Map.of("conNo", "201", "conHyphen", "-", "dashNo", 1), "sympCount", 4, "vehStat", "GO", "softVer", "A$", "homeYrd", "DY", "originLoc", "DY")))));
        });
        Answer a = get(BASE + "/symptoms", Map.of("consist", "201", "symptom", "0120"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("conNo", "201").containsEntry("sympCd", "0120").containsEntry("dateLow", "2026-01-01").containsEntry("dateRev", "2026-11-13");
        assertThat(a.body().at("/trains/0/cars/0/symptomCount").asLong()).isEqualTo(4);
        assertThat(a.body().at("/trains/0/cars/0/software").asBoolean()).isTrue();
    }

    @Test
    void printSubmitsTheReport() throws Exception {
        backend().on("VHST011", in -> { crit.set(in.get("VHST011_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post(BASE + "/print", Map.of("report", 2, "from", "201", "to", "305", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHST05I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("reptId", "2").containsEntry("reptMisc", "201").containsEntry("conNo", "305").containsEntry("reptType", "DUBLNHP1");
        Answer b = post(BASE + "/symptoms/print", Map.of("consist", "201", "symptom", "0120", "printer", "DUBLNHP1"));
        assertThat(b.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("reptId", "3").containsEntry("sympCd", "0120");
        Answer c = post(BASE + "/print", Map.of("report", 1, "from", "201", "printer", ""));
        assertThat(c.status()).isEqualTo(400);
    }
}
