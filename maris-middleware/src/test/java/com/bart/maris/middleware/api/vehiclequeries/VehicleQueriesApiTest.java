package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Vehicle queries over HTTP: the batch print reports. */
class VehicleQueriesApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private void dates() {
        backend().on("VHMR01A", in -> reply(0, 1, Map.of("VHMR01A_DATA", Map.of("fromDate", "2026-10-01"))));
        backend().on("VHMR01B", in -> reply(0, 1, Map.of("VHMR01B_DATA", Map.of("toDate", "2026-10-31"))));
        backend().on("VHMR01C", in -> reply(0, 1, Map.of("VHMR01C_DATA", rows("dataRow", List.of(Map.of("yrdDrop", Map.of("yrd", "CY", "yrdDash", "-", "yrdDesc", "CONCORD")))))));
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
        backend().on("AFCXVLD", in -> {
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            c.put("dateError", "N");
            c.put("otherSqlError", "N");
            return reply(0, 0, Map.of("AFCXVLD_CRIT", c));
        });
    }

    @Test
    void listsTheReports() throws Exception {
        Answer a = get("/api/v1/vehicle-queries/batch-reports", null);
        assertThat(a.body().size()).isEqualTo(com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService.REPORTS.size());
        assertThat(a.body().findValuesAsText("screen")).contains("VHMR01I", "VHPT01I");
    }

    @Test
    void optionsGiveTheStartDatesYardsAndPrinters() throws Exception {
        dates();
        Answer a = get("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("fromDate").asText()).isEqualTo("2026-10-01");
        assertThat(a.body().get("toDate").asText()).isEqualTo("2026-10-31");
        assertThat(a.body().at("/yards/0/code").asText()).isEqualTo("CY");
        assertThat(a.body().at("/yards/0/label").asText()).isEqualTo("CY -CONCORD");
        assertThat(a.body().get("yardRequired").asBoolean()).isTrue();
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void hardCopySubmitsTheReport() throws Exception {
        dates();
        backend().on("VHMR01D", in -> { crit.set(in.get("VHMR01D_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/print",
                Map.of("fromDate", "2026-10-01", "toDate", "2026-10-31", "yard", "CY", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHMR01I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("fromDate", "10/01/2026").containsEntry("toDate", "10/31/2026").containsEntry("yrd", "CY")
                .containsEntry("reptId", "DUBLNHP1");
    }

    @Test
    void badCriteriaGetTheOldMessagesAndSubmitNothing() throws Exception {
        dates();
        String later = LocalDate.now().plusDays(5).toString();
        Answer future = post("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/print",
                Map.of("fromDate", "2026-10-01", "toDate", later, "yard", "CY", "printer", "DUBLNHP1"));
        assertThat(future.status()).isEqualTo(400);
        // the old screen sets "to date after today", then INVALID-TO-DATE replaces it with its own text
        assertThat(future.body().get("detail").asText()).isEqualTo(TEXTS.assignedUnder("VHMR01IS-SUBMIT-X", "MRSVehicleQueries", "MESG1", "INVALID-TO-DATE").get(0));
        Answer noYard = post("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/print",
                Map.of("fromDate", "2026-10-01", "toDate", "2026-10-02", "yard", "", "printer", "DUBLNHP1"));
        assertThat(noYard.body().get("detail").asText()).isEqualTo(TEXTS.assignedUnder("VHMR01IS-SUBMIT-X", "MRSVehicleQueries", "MESG1", "YRD").get(0));
        assertThat(called()).doesNotContain("VHMR01D");
    }

    @Test
    void requestsTheApiCannotTake() throws Exception {
        assertThat(post("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/print", Map.of("fromDate", "2026-10-01")).status()).isEqualTo(400);
        assertThat(post("/api/v1/vehicle-queries/batch-reports/reason-used-by-shop/print", Map.of("fromDate", "10-01-2026", "printer", "P")).status()).isEqualTo(400);
        assertThat(get("/api/v1/vehicle-queries/batch-reports/nothing/options", null).status()).isEqualTo(400);
    }

    @Test
    void carStatusHistoryListsTheChanges() throws Exception {
        dates();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        backend().on("VHCSH1A", in -> { crit.set(in.get("VHCSH1A_CRIT")); return reply(0, 1, Map.of("VHCSH1A_DATA", Map.of("homeYrd", "HY",
                "dataRow", List.of(Map.of("carLoc", "HY01", "dateStatChg", "2026-10-02", "timeStatChg", "10:15:00", "vehStatNew", "NO GO", "cumHrs", 12))))); });
        Answer a = get("/api/v1/vehicle-queries/car-status-history", Map.of("car", "1101", "from", "2026-10-01", "to", "2026-10-31"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("homeYard").asText()).isEqualTo("HY");
        assertThat(a.body().at("/changes/0/statusNew").asText()).isEqualTo("NO GO");
        assertThat(a.body().at("/changes/0/hours").asLong()).isEqualTo(12);
        assertThat(crit.get()).containsEntry("carNo", "1101").containsEntry("fromDate", "10-01-2026").containsEntry("toDate", "10-31-2026");
    }

    @Test
    void carStatusHistoryNeedsANumericCar() throws Exception {
        Answer a = get("/api/v1/vehicle-queries/car-status-history", Map.of("car", "AB", "from", "2026-10-01", "to", "2026-10-31"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCSH1I", "INVALID-CARNO"));
        assertThat(called()).doesNotContain("VHINC6A", "VHCSH1A");
    }

    @Test
    void carStatusHistoryPrints() throws Exception {
        dates();
        backend().on("VHCSH2A", in -> { crit.set(in.get("VHCSH2A_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/car-status-history/print", Map.of("car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCSH2I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("reptType", "DUBLNHP1").containsEntry("dateFrom", "10/01/2026").containsEntry("carNo", "1101");
        assertThat(get("/api/v1/vehicle-queries/printers", null).body().at("/0/vtam").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void homeYardFleetCountsEveryYard() throws Exception {
        backend().on("VHHF01A", in -> reply(0, 1, Map.of("VHHF01A_DATA", rows("dataRow", List.of(Map.of("carType", Map.of("carType1", "A"), "vehStat", "GO", "whereIsCar", "CON"))))));
        backend().on("VHHF01B", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/vehicle-queries/home-yard-fleet", null);
        assertThat(a.status()).isEqualTo(200);
        List<String> yards = new java.util.ArrayList<>();
        a.body().get("yards").forEach(y -> yards.add(y.asText()));
        assertThat(yards).isEqualTo(List.copyOf(TEXTS.indexed("VHHF01IS-INIT", "MRSVehicleQueries", "HOME-YRD-OCCR").values()));
        assertThat(a.body().at("/rows/0/name").asText()).isEqualTo(TEXTS.indexed("VHHF01IS-INIT", "MRSVehicleQueries", "CAR-TYPE-DATA").get(1));
        String yard = yards.get(0);
        assertThat(a.body().at("/rows/0/yards/" + yard + "/byType/0").asInt()).isEqualTo(1);       // one A car in the fleet
        assertThat(a.body().at("/rows/16/yards/" + yard + "/total").asInt()).isEqualTo(1);         // in revenue service
        assertThat(called().stream().filter("VHHF01A"::equals).count()).isEqualTo(4);
    }

    @Test
    void homeYardFleetPrints() throws Exception {
        backend().on("VHHF02A", in -> { crit.set(in.get("VHHF02A_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/home-yard-fleet/print", Map.of("printer", "DUBLNHP1"));
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHHF02I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("reptType", "DUBLNHP1");
        assertThat(post("/api/v1/vehicle-queries/home-yard-fleet/print", Map.of()).status()).isEqualTo(400);
    }

    @Test
    void carHoursGivesTheTotals() throws Exception {
        dates();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        backend().on("VHCHR1A", in -> { crit.set(in.get("VHCHR1A_CRIT")); return reply(0, 1, Map.of("VHCHR1A_DATA", Map.of("totalHrs", 321, "totalMin", 9,
                "dateFrom", "10-01-2026", "dateTo", "10-31-2026"))); });
        Answer a = get("/api/v1/vehicle-queries/car-hours", Map.of("car", "1101", "from", "2026-10-01", "to", "2026-10-31"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("hours").asLong()).isEqualTo(321);
        assertThat(a.body().get("minutes").asLong()).isEqualTo(9);
        assertThat(crit.get()).containsEntry("dateRevFrom", "2026-10-01").containsEntry("carNo", "1101");
        Answer bad = get("/api/v1/vehicle-queries/car-hours", Map.of("car", "0", "from", "2026-10-01", "to", "2026-10-31"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCHR1IS-LIST", "MRSVehicleQueries", "CAR-MESSAGE", "invalid"));
    }

    @Test
    void carHoursPrints() throws Exception {
        dates();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        backend().on("VHCHR01", in -> { crit.set(in.get("VHCHR01_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/car-hours/print", Map.of("car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31", "printer", "DUBLNHP1"));
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCHR1I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("dateRevFrom", "10/01/2026").containsEntry("reptType", "DUBLNHP1");
    }

    @Test
    void fleetReportCountsTheCars() throws Exception {
        backend().on("VHFR01A", in -> reply(0, 2, Map.of("VHFR01A_DATA", rows("dataRow", List.of(
                Map.of("carType", Map.of("carType1", "C"), "atoPresentInd", "Y", "vehStat", "GO", "whereIsCar", "YRD", "currLoc", "HY"),
                Map.of("carType", Map.of("carType1", "A"), "vehStat", "STOR", "whereIsCar", "YRD"))))));
        Answer a = get("/api/v1/vehicle-queries/fleet-report", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/rows/0/cells/total").asInt()).isEqualTo(2);                  // both cars are in the fleet
        assertThat(a.body().at("/rows/1/cells/total").asInt()).isEqualTo(1);                  // the stored one is in hold
        assertThat(a.body().get("types").toString()).contains("CW", "CO");
        int cw = 0;
        for (int i = 0; i < a.body().get("types").size(); i++) if (a.body().get("types").get(i).asText().equals("CW")) cw = i;
        JsonNode hy = null;
        for (JsonNode y : a.body().get("goCarsByYard")) if (y.get("yard").asText().equals("HY")) hy = y;
        assertThat(hy.at("/cells/byType/" + cw).asInt()).isEqualTo(1);
    }

    @Test
    void fleetReportPrints() throws Exception {
        backend().on("VHFR01C", in -> { crit.set(in.get("VHFR01C_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/fleet-report/print", Map.of("printer", "DUBLNHP1"));
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHFR01I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("reptType", "DUBLNHP1");
    }

    @Test
    void repeaterCarsListsTheIncidents() throws Exception {
        dates();
        backend().on("VHYS01A", in -> reply(0, 1, Map.of("VHYS01A_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"))))));
        backend().on("VHCI01B", in -> reply(0, 1, Map.of()));
        backend().on("VHRC02A", in -> { crit.set(in.get("VHRC02A_CRIT")); return reply(0, 1, Map.of("VHRC02A_DATA", Map.of("totalIncds", 1,
                "dataRow", List.of(Map.of("carNo", "1101", "dispVehStat", Map.of("vehStat", "NO GO", "vehIncdInd", "T"), "incdNo", "29990010"))))); });
        Answer o = get("/api/v1/vehicle-queries/repeater-cars/options", null);
        String all = TEXTS.assigned("VHRC01IS-INIT", "MRSVehicleQueries", "YRD", null);
        assertThat(o.body().get("homeYards").toString()).isEqualTo("[\"CY\",\"" + all + "\"]");
        assertThat(o.body().get("homeYard").asText()).isEqualTo(all);
        Answer a = get("/api/v1/vehicle-queries/repeater-cars", Map.of("homeYard", "CY", "car", "1101"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/incidents/0/status").asText()).isEqualTo("NO GO  T");
        assertThat(crit.get()).containsEntry("carNo", "1101").containsEntry("homeYrd", "CY");
        Answer none = get("/api/v1/vehicle-queries/repeater-cars", Map.of("homeYard", ""));
        assertThat(none.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHRC01I", "INVALID-HOMEYARD"));
    }

    @Test
    void repeaterCarsPrints() throws Exception {
        backend().on("VHRC011", in -> { crit.set(in.get("VHRC011_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/repeater-cars/print", Map.of("homeYard", "ALL", "car", "", "printer", "DUBLNHP1"));
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHRC01I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("homeYrd", "ALL").containsEntry("reptType", "DUBLNHP1");
    }

    private void homeYards() {
        dates();
        backend().on("VHYS01A", in -> reply(0, 2, Map.of("VHYS01A_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"))))));
        backend().on("VHYS01B", in -> reply(0, 1, Map.of("VHYS01B_DATA", rows("dataRow", List.of(Map.of("vehStat", "GO", "vehStatDesc", "GO CAR"))))));
    }

    @Test
    void homeYardStatusListsTheCars() throws Exception {
        homeYards();
        backend().on("VHYS02A", in -> { crit.set(in.get("VHYS02A_CRIT")); return reply(0, 1, Map.of("VHYS02A_CAR_DATA", Map.of("carCount", 1,
                "dataRow", List.of(Map.of("carNo", "1101", "dispVehStat", Map.of("vehStat", "GO"), "homeYrd", "CY"))))); });
        Answer o = get("/api/v1/vehicle-queries/home-yard-status/options", null);
        assertThat(o.body().get("yards").toString()).isEqualTo("[\"CY\",\"HY\"]");
        assertThat(o.body().get("narr").asText()).isEqualTo(TEXTS.openingValue("VHYS01I", "ddlNarrBase"));
        Answer a = get("/api/v1/vehicle-queries/home-yard-status/cars", Map.of("option", "base", "yard", "CY", "narr", "Y"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/cars/0/car").asText()).isEqualTo("1101");
        assertThat(crit.get()).containsEntry("homeYrd", "CY").containsEntry("retInd", "CAR")
                .containsEntry("carNoFrom", TEXTS.assigned("VHYS01IS-CHK-SEL", "MRSVehicleQueries", "CAR-NO-FROM", null));
    }

    @Test
    void homeYardStatusChecksLikeTheOldScreen() throws Exception {
        homeYards();
        Answer none = get("/api/v1/vehicle-queries/home-yard-status/check", Map.of("yard", "CY"));
        assertThat(none.status()).isEqualTo(400);
        assertThat(none.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHYS01IS-CHK-SEL", "MRSVehicleQueries", "FINAL-ERROR-MESSAGE").get(12));
        assertThat(called()).doesNotContain("VHYS02A");
    }

    @Test
    void homeYardStatusPrints() throws Exception {
        backend().on("VHYS01C", in -> { crit.set(in.get("VHYS01C_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/home-yard-status/print", Map.of("option", "nogo", "yard", "HY", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("reptId", "3").containsEntry("homeYrd", "HY").containsEntry("reptType", "DUBLNHP1");
    }

    @Test
    void homeYardStatusNogoSummaryAndTypes() throws Exception {
        homeYards();
        backend().on("VHYS03A", in -> reply(100, 0, Map.of()));
        Answer nogo = get("/api/v1/vehicle-queries/home-yard-status/nogo", Map.of("option", "nogo", "yard", "CY"));
        assertThat(nogo.status()).isEqualTo(200);
        assertThat(nogo.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHYS03IS-SELECT", "MRSVehicleQueries", "FINAL-ERROR-MESSAGE").get(0));
        backend().on("VHYS04A", in -> reply(0, 1, Map.of("VHYS04A_DATA", rows("dataRow", List.of(Map.of("dispCarNo", Map.of("carNo", "1101", "carNoStar", "*")))))));
        Answer sum = get("/api/v1/vehicle-queries/home-yard-status/summary", Map.of("option", "summary", "yard", "CY"));
        assertThat(sum.body().at("/cars/0/car").asText()).isEqualTo("1101");
        assertThat(sum.body().at("/cars/0/atoMissing").asBoolean()).isTrue();
        assertThat(sum.body().get("header").asText()).isNotBlank();
        backend().on("VHYS05A", in -> reply(0, 1, Map.of("VHYS05A_DATA", rows("dataRow", List.of(Map.of("carType", Map.of("carType1", "A"), "vehStat", "GO", "whereIsCar", "CON"))))));
        Answer types = get("/api/v1/vehicle-queries/home-yard-status/type-summary", Map.of("yard", "CY"));
        assertThat(types.body().at("/rows/0/total").asInt()).isEqualTo(1);
        assertThat(types.body().at("/rows/15/total").asInt()).isEqualTo(1);                // in revenue service
    }

    @Test
    void yardStatusReports() throws Exception {
        dates();
        backend().on("VHYS06A", in -> reply(0, 2, Map.of("VHYS06A_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"))))));
        backend().on("VHYS06B", in -> reply(0, 1, Map.of("VHYS06B_DATA", rows("dataRow", List.of(Map.of("vehStat", "GO"))))));
        backend().on("VHYS07A", in -> { crit.set(in.get("VHYS07A_CRIT")); return reply(0, 1, Map.of("VHYS07A_CAR_DATA", Map.of("carCount", 1,
                "dataRow", List.of(Map.of("carNo", "1101", "currLoc", "HY01", "homeYrd", "CY"))))); });
        Answer cars = get("/api/v1/vehicle-queries/yard-status/cars", Map.of("option", "base", "yard", "HY", "narr", "N"));
        assertThat(cars.status()).isEqualTo(200);
        assertThat(cars.body().at("/cars/0/narrativeYard").asText()).isEqualTo("HY01");
        assertThat(crit.get()).containsEntry("yard", "HY").containsEntry("retInd", "CAR");
        backend().on("VHYS09A", in -> reply(100, 0, Map.of()));
        Answer sum = get("/api/v1/vehicle-queries/yard-status/summary", Map.of("option", "summary", "yard", "HY"));
        assertThat(sum.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHYS09IS-NOCARS", "MRSVehicleQueries", "MSG").get(0));
        Answer bad = get("/api/v1/vehicle-queries/yard-status/check", Map.of("option", "nogo", "yard", ""));
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHYS06IS-CHK-SEL", "MRSVehicleQueries", "FINAL-ERROR-MESSAGE").get(9));
        backend().on("VHYS06C", in -> { crit.set(in.get("VHYS06C_CRIT")); return reply(0, 0, Map.of()); });
        assertThat(post("/api/v1/vehicle-queries/yard-status/print", Map.of("option", "summary", "yard", "CY", "printer", "DUBLNHP1")).status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("reptId", "4").containsEntry("homeYrd", "CY");
    }

    @Test
    void vehicleStatusReport() throws Exception {
        dates();
        backend().on("VHSV011", in -> reply(0, 1, Map.of("VHSV011_DATA", Map.of("fromDate", "2026-10-01"))));
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        AtomicReference<Map<String, Object>> where = new AtomicReference<>();
        backend().on("VHSV02A", in -> { crit.set(in.get("VHSV02A_CRIT")); where.set(in.get("VHSV02AW1"));
            return reply(0, 1, Map.of("VHSV02A_CRIT", Map.of("rowCount", 1), "VHSV02A_DATA_01", Map.of("currLoc", "CY01"),
                    "VHSV02A_DATA_03", rows("dataRow", List.of(Map.of("incdNo", "29990010", "sympCd", "0120"))))); });
        Answer o = get("/api/v1/vehicle-queries/vehicle-status/options", null);
        assertThat(o.body().get("fromDate").asText()).isEqualTo("2026-10-01");
        assertThat(o.body().get("reports").size()).isEqualTo(2);
        Answer a = get("/api/v1/vehicle-queries/vehicle-status", Map.of("report", "1", "car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31",
                "sympFrom", "0100", "sympTo", "0200"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/incidents/0/incident").asText()).isEqualTo("29990010");
        assertThat(a.body().at("/car/location").asText()).isEqualTo("CY01");
        assertThat(crit.get()).containsEntry("carNo", "1101").containsEntry("incdDateFrom", "2026-10-01").containsEntry("sympCdTo", "0200");
        String w = String.valueOf(where.get().get("whereClause"));
        assertThat(w).contains("1101").contains("0100").contains("2026-10-31").doesNotContain(";");
        Answer bad = get("/api/v1/vehicle-queries/vehicle-status", Map.of("report", "1", "car", "1101", "sympFrom", "01';--"));
        assertThat(bad.status()).isEqualTo(400);                       // never reaches the WHERE clause
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHSV01IS-DISPLAY", "MRSVehicleQueries", "CAR-MESSAGE").get(8));
    }

    @Test
    void vehicleStatusPrints() throws Exception {
        dates();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        backend().on("VHSV012", in -> { crit.set(in.get("VHSV012_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-queries/vehicle-status/print", Map.of("report", 2, "car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31",
                "deferredItems", "B", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHSV01I", "SUBMIT").strip());
        assertThat(crit.get()).containsEntry("reptId", "2").containsEntry("deferredItem", "B").containsEntry("reptType", "DUBLNHP1");
    }

    @Test
    void vehicleHistoryWithRepairs() throws Exception {
        dates();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of()));
        backend().on("VHSV05A", in -> { crit.set(in.get("VHSV05A_CRIT")); return reply(0, 1, Map.of("VHSV05A_CRIT", Map.of("rowCount", 1),
                "VHSV05A_DATA_03", rows("dataRow", List.of(Map.of("incdNo", "29990020"))))); });
        Answer a = get("/api/v1/vehicle-queries/vehicle-history", Map.of("car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/incidents/0/incident").asText()).isEqualTo("29990020");
        assertThat(crit.get()).containsEntry("incdDateFrom", "10-01-2026");                  // as the old screen passes it
        backend().on("VHSV05C", in -> reply(0, 1, Map.of("VHSV05C_CRIT", Map.of("reprRowCount", 1), "VHSV05C_DATA", rows("dataRow", List.of(Map.of("reprDocNo", 2, "serNoOn", "ON1"))))));
        Answer r = get("/api/v1/vehicle-queries/vehicle-history/incidents/29990020/repairs", null);
        assertThat(r.body().at("/0/serialOn").asText()).isEqualTo("ON1");
        backend().on("VHNARY", in -> reply(0, 1, Map.of("VHNARY_DATA", rows("dataRow", List.of(Map.of("narr", "FIXED"))))));
        backend().on("VHSV05D", in -> reply(0, 1, Map.of("VHSV05D_DATA", rows("dataRow", List.of(Map.of("cmpntCd", "BRK"))))));
        Answer d = get("/api/v1/vehicle-queries/vehicle-history/incidents/29990020/repairs/2", null);
        assertThat(d.body().at("/narrative/0").asText()).isEqualTo("FIXED");
        assertThat(d.body().at("/components/0/component").asText()).isEqualTo("BRK");
        backend().on("VHSV012", in -> { crit.set(in.get("VHSV012_CRIT")); return reply(0, 0, Map.of()); });
        post("/api/v1/vehicle-queries/vehicle-history/print", Map.of("car", "1101", "fromDate", "2026-10-01", "toDate", "2026-10-31", "printer", "P1"));
        assertThat(crit.get()).containsEntry("reptId", "3").containsEntry("sympCd", "").containsEntry("sympCdTo", "9999");
    }

    @Test
    void carInquiry() throws Exception {
        backend().on("VHSV06F", in -> reply(0, 1, Map.of("VHSV06F_DATA", Map.of("fromDate", "2026-08-01", "toDate", "2026-11-12"))));
        backend().on("VHSV06A", in -> { crit.set(in.get("VHSV06A_CRIT")); return reply(0, 1, Map.of("VHSV06A_CRIT", Map.of("rowCount", 1),
                "VHSV06A_DATA_01", Map.of("currLoc", "CY01"), "VHSV06A_DATA_03", rows("dataRow", List.of(Map.of("incdNo", "29990010"))))); });
        backend().on("VHSV06B", in -> reply(0, 1, Map.of("VHSV06B_DATA", Map.of("moveReasonDesc", "MOVED FOR PM"))));
        Answer a = get("/api/v1/vehicle-queries/car-inquiry/1101", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/incidents/0/incident").asText()).isEqualTo("29990010");
        assertThat(a.body().at("/moveReason/description").asText()).isEqualTo("MOVED FOR PM");
        assertThat(crit.get()).containsEntry("carNo", "1101").containsEntry("incdDateFrom", "2026-08-01");
        backend().on("VHSV06N", in -> reply(0, 1, Map.of("VHSV06N_DATA", Map.of("dataRow", List.of(Map.of("narr", "STUCK")), "conNo", "201", "dashNo", 3,
                "carRow", List.of(Map.of("carNo", "1101", "carOrientation", "<"), Map.of("carNo", "1102", "carOrientation", ">"))))));
        Answer inc = get("/api/v1/vehicle-queries/car-inquiry/incidents/29990010", null);
        assertThat(inc.body().get("cars").toString()).isEqualTo("[\"<1101\",\"1102>\"]");
        assertThat(inc.body().get("consist").asText()).isEqualTo("201-03");
        Answer bad = get("/api/v1/vehicle-queries/car-inquiry/11A1", null);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHSV07IS-DISPLAY", "MRSVehicleQueries", "MESG1").get(0));
    }
}
