package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Vehicle queries over HTTP: the consist log (VHCL01I, VHCL02I) and the daily vehicle status (VHDVS1I .. VHDVS4I). */
class VehicleQueriesConsistLogApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private void consists() {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
        backend().on("VHCL01E", in -> reply(0, 1, Map.of("VHCL01E_DATA", Map.of("dateWork", in.get("VHCL01E_CRIT").get("dateWork")))));
        backend().on("VHCL02B", in -> reply(0, 1, Map.of("VHCL02B_DATA", Map.of("exceedLimit", "N", "dataRow", List.of(Map.of(
                "consist", Map.of("conNoX", "201", "dash", "-", "dashNoX", 1), "originLoc", "CY01", "outsrvLoc", "DY02", "timeInsrv", "05:10:00",
                "timeOutsrv", "09:30:00", "runTimeHrs", 4, "runTimeMin", 20, "washInd", "Y", "maxPosnNo", 2, "exactRowCnt", 2))))));
        backend().on("VHCL02C", in -> reply(0, 2, Map.of("VHCL02C_DATA", rows("dataRow", List.of(
                Map.of("consist", Map.of("conNoX", "201", "dash", "-", "dashNoX", 1), "carNoX", Map.of("lftOr", "<", "carNo", "1101", "rgtOr", " "),
                        "softVer", "$1.2", "homeYrd", "CY", "carWnInd", "N"),
                Map.of("consist", Map.of("conNoX", "201", "dash", "-", "dashNoX", 1), "carNoX", Map.of("lftOr", " ", "carNo", "1102", "rgtOr", ">"),
                        "softVer", "1.0", "homeYrd", "HY", "carWnInd", "Y"))))));
    }

    @Test
    void optionsGiveTodayAndThePrinters() throws Exception {
        consists();
        Answer a = get("/api/v1/vehicle-queries/consist-log/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("revenueDate").asText()).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(a.body().at("/printers/0/vtamName").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void consistLogListsTheConsistsAndTheirCars() throws Exception {
        consists();
        Answer a = get("/api/v1/vehicle-queries/consist-log", Map.of("from", "201", "to", "", "revenueDate", "2026-11-12"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/criteria/to").asText()).isEqualTo("201");
        assertThat(a.body().at("/consists/0/consist").asText()).isEqualTo("201-01");
        assertThat(a.body().at("/consists/0/runTime").asText()).isEqualTo("04:20");
        assertThat(a.body().at("/consists/0/cars/0/shown").asText()).isEqualTo("$<1101");
        assertThat(a.body().at("/consists/0/cars/1/shown").asText()).as("2-car consists take car 1's version: old VHCL02I").isEqualTo("$ 1102");
        assertThat(a.body().at("/consists/0/cars/1/warning").asBoolean()).isTrue();
        assertThat(a.body().get("pages").asInt()).isEqualTo(1);
        assertThat(a.body().get("message").asText()).contains(TEXTS.assignedAll("VHCL02IS-GET-DATA2", "MRSVehicleQueries", "FINAL-MSG-2").get(0).strip());
    }

    @Test
    void badCriteriaGetTheOldMessage() throws Exception {
        consists();
        Answer a = get("/api/v1/vehicle-queries/consist-log", Map.of("from", "0", "revenueDate", "2026-11-12"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().toString()).contains(TEXTS.assigned("VHCL01IS-VALIDX", "MRSVehicleQueries", "MESSAGE", "FROM CONSIST IS"));
        assertThat(called()).doesNotContain("VHCL02B");
    }

    @Test
    void printSubmitsTheReport() throws Exception {
        consists();
        backend().on("VHCL011", in -> { crit.set(in.get("VHCL011_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/consist-log/print", Map.of("from", "ALL", "to", "", "revenueDate", "2026-11-12", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCL01I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("conNoFrom", "100").containsEntry("conNoTo", "999").containsEntry("dateInsrvRev", "2026-11-12")
                .containsEntry("reptType", "DUBLNHP1");
    }

    private void daily() {
        backend().on("VHDVS11", in -> reply(0, 1, Map.of("VHDVS11_DATA", Map.of("fromDate", "2026-11-13"))));
        backend().on("VHDVS03", in -> reply(0, 1, Map.of("VHDVS03_DATA", rows("dataRow", List.of(Map.of("yrd", "CY", "carType", "A2", "cntCarsReqd", 9, "actvInd", "Y"))))));
        backend().on("VHDVS01", in -> reply(0, 1, Map.of("VHDVS01_DATA", rows("dataRow", List.of(Map.of("yrd", "CY", "carType", "A2", "cntCarsAvailAm", 7,
                "cntCarsShopWar", 2, "cntCarsMlFail", 1))))));
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
    }

    @Test
    void dailyStatusGivesTheOldTables() throws Exception {
        daily();
        Answer a = get("/api/v1/vehicle-queries/daily-status", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("tables").size()).isEqualTo(7);
        assertThat(a.body().at("/tables/1/screen").asText()).isEqualTo("VHDVS1I");
        assertThat(a.body().at("/tables/1/rows/0/CY-A").asInt()).isEqualTo(2);
        assertThat(a.body().at("/tables/1/rows/1/CY-A").asInt()).isEqualTo(1);
        assertThat(a.body().at("/tables/1/columns/0/field").asText()).isEqualTo("DESC");
        assertThat(called()).contains("VHDVS11", "VHDVS03", "VHDVS01");
    }

    @Test
    void dailyStatusRejectsABadDate() throws Exception {
        daily();
        assertThat(get("/api/v1/vehicle-queries/daily-status", Map.of("revenueDate", "11/02/2026")).status()).isEqualTo(400);
    }

    @Test
    void dailyStatusPrintSubmitsTheReport() throws Exception {
        daily();
        backend().on("VHDVS04", in -> { crit.set(in.get("VHDVS04_CRIT")); return reply(0, 0, Map.of()); });
        assertThat(get("/api/v1/vehicle-queries/daily-status/printers", null).body().at("/0/vtamName").asText()).isEqualTo("DUBLNHP1");
        Answer a = post("/api/v1/vehicle-queries/daily-status/print", Map.of("revenueDate", "2026-11-02", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("dateReptFrom", "11-02-2026").containsEntry("reptType", "DUBLNHP1");
    }
}
