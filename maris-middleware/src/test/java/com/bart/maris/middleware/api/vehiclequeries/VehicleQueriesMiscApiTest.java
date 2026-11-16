package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Consist orientation (VHCO01I), D and C sheet (VHDC01I), SCRAM (VHSCM1I) and car status change (VHCHG1I) over HTTP. */
class VehicleQueriesMiscApiTest extends ApiTestBase {
    private static final String PKG = "MRSVehicleQueries";
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    // ---- VHCO01I

    private void orientation() {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
        backend().on("VHHPMA3", in -> reply(0, 0, Map.of("VHHPMA3_DATA", Map.of("timeRevStart", "03.00.00", "currentDate", "2026-11-14", "currentTime", "10.00.00",
                "yesterday", "2026-11-13"))));
        backend().on("VHSV011", in -> reply(0, 0, Map.of("VHSV011_DATA", Map.of("fromDate", "2026-08-16", "toDate", "2026-11-14"))));
        backend().on("VHCO011", in -> { sent.set(in.get("VHCO011_CRIT")); return reply(0, 0, Map.of()); });
    }

    @Test
    void consistOrientationOptionsGiveTheStartDatesAndPrinters() throws Exception {
        orientation();
        Answer a = get("/api/v1/vehicle-queries/consist-orientation/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("fromDate").asText()).isEqualTo("2026-08-16");
        assertThat(a.body().get("toDate").asText()).isEqualTo("2026-11-14");               // the revenue day has started
        assertThat(a.body().get("all").asText()).isEqualTo("ALL");
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
    }

    @Test
    void consistOrientationPrintsAndChecks() throws Exception {
        orientation();
        Answer a = post("/api/v1/vehicle-queries/consist-orientation/print", Map.of("from", "ALL", "to", "", "fromDate", "2026-08-16", "toDate", "2026-11-14",
                "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHCO01I", "SUBMIT").strip());
        assertThat(sent.get()).containsEntry("conNoFrom", "100").containsEntry("conNoTo", "999").containsEntry("reptType", "DUBLNHP1")
                .containsEntry("dateInsrvRev", "2026-08-16").containsEntry("dateInsrvRevTo", "2026-11-14");
        sent.set(null);
        Answer bad = post("/api/v1/vehicle-queries/consist-orientation/print", Map.of("from", "300", "to", "200", "fromDate", "2026-08-16", "toDate", "2026-11-14",
                "printer", "DUBLNHP1"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCO01ID-MSG", PKG, "MESSAGE", "LESS THAN"));
        assertThat(sent.get()).isNull();
    }

    // ---- VHDC01I

    private void dnc(int carRows) {
        backend().on("VHDC011", in -> reply(0, 1, Map.of("VHDC011_DATA", rows("dataRow", List.of(Map.of("prtrLoc", "DUBLIN TMZ", "cicsVtamName", "DUBLNHP1"))))));
        backend().on("VHDC013", in -> reply(0, carRows, carRows > 0 ? Map.of("VHDC013_DATA", Map.of("carNo", "1101", "incdNo", "29990010")) : Map.of()));
        backend().on("VHDC012", in -> { sent.set(in.get("VHDC012_CRIT")); return reply(0, 0, Map.of()); });
        backend().on("printDnC", in -> new FakeLegacyBackend.Reply(Map.of(), "00000000"));
        backend().on("printBlankDnC", in -> new FakeLegacyBackend.Reply(Map.of(), "00000000"));
    }

    @Test
    void dncSheetListsPrintersAndReprintsACarsSheet() throws Exception {
        dnc(1);
        Answer o = get("/api/v1/vehicle-queries/dnc-sheet/options", null);
        assertThat(o.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
        Answer a = post("/api/v1/vehicle-queries/dnc-sheet/print", Map.of("blankForm", "N", "car", "1101", "incident", "29990010", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHDC01I", "OK").strip());
        assertThat(called()).containsSubsequence("VHDC013", "VHDC01I", "VHDC012");         // printDnC is a service of VHDC01IService
        String printCall = backend().calls().stream().filter(c -> c.body().path("method").asText().equals("printDnC")).findFirst().orElseThrow().body().toString();
        assertThat(printCall).contains("\"CT\"", "\"1101\"", "\"29990010\"", "\"DUBLNHP1\"");
        assertThat(sent.get()).containsEntry("reptId", "REPRINT").containsEntry("conCar1", "1101").containsEntry("userGrp", "BRIGHTCO");
    }

    @Test
    void dncSheetChecksGiveTheOldMessages() throws Exception {
        dnc(0);
        Answer both = post("/api/v1/vehicle-queries/dnc-sheet/print", Map.of("blankForm", "Y", "car", "1101", "incident", "", "printer", "DUBLNHP1"));
        assertThat(both.status()).isEqualTo(400);
        assertThat(both.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHDC01I", "NO-BLANK-ERROR").strip());
        Answer unknown = post("/api/v1/vehicle-queries/dnc-sheet/print", Map.of("blankForm", "N", "car", "9999", "incident", "", "printer", "DUBLNHP1"));
        assertThat(unknown.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHDC01I", "ERROR-CAR").strip());
        assertThat(sent.get()).isNull();
    }

    // ---- VHSCM1I

    @Test
    void scramGivesEveryHomeYard() throws Exception {
        backend().on("VHXREVA", in -> reply(0, 0, Map.of("VHXREVA_DATA", Map.of("revenueDate", "11-14-2026"))));
        backend().on("VHSCM1A", in -> reply(0, 0, Map.of("VHSCM1A_DATA", Map.of("revDate", "2026-11-14", "revDay", 7))));
        backend().on("VHSCM1B", in -> {
            Map<String, Object> b = new HashMap<>(in.get("VHSCM1B_DATA"));
            b.put("recFound", "Y");
            b.put("totSchd1", 4);
            b.put("dataRow", List.of(Map.of("homeYrd", b.get("critHomeYrd") + " AM", "c1", 4)));
            return reply(0, 1, Map.of("VHSCM1B_DATA", b));
        });
        Answer o = get("/api/v1/vehicle-queries/scram/options", null);
        assertThat(o.body().get("day").asText()).isEqualTo("SA");
        assertThat(o.body().get("time").asText()).isEqualTo("DA");
        assertThat(o.body().get("groups").size()).isEqualTo(4);
        Answer a = get("/api/v1/vehicle-queries/scram", Map.of("day", "SA", "time", "DA"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("yards").size()).isEqualTo(4);
        assertThat(a.body().at("/yards/1/homeYard").asText()).isEqualTo("DY");
        assertThat(a.body().at("/yards/1/rows/0/homeYard").asText()).isEqualTo("DY AM");
        assertThat(a.body().at("/yards/1/totals/0/scheduled").asInt()).isEqualTo(4);
        Answer bad = get("/api/v1/vehicle-queries/scram", Map.of("day", "XX", "time", "AM"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHSCM1I", "INVALID-DAY").strip());
    }

    // ---- VHCHG1I

    @Test
    void carStatusChangeReadsAndUpdates() throws Exception {
        AtomicReference<Map<String, Object>> data = new AtomicReference<>();
        backend().on("VHVM1A2", in -> reply(0, 1, Map.of("VHVM1A2_DATA", Map.of("carNo", "1101", "vehStat", "GO", "vehStatPrev", "PM", "termLastUpd", "T1"))));
        backend().on("VHVM1A1", in -> { sent.set(in.get("VHVM1A1_CRIT")); data.set(in.get("VHVM1A1_DATA")); return reply(0, 0, Map.of()); });
        Answer o = get("/api/v1/vehicle-queries/car-status-change/options", null);
        assertThat(o.body().get("statuses").toString()).contains("NO GO", "STOR");
        Answer car = get("/api/v1/vehicle-queries/car-status-change/cars/1101", null);
        assertThat(car.body().get("status").asText()).isEqualTo("GO");
        assertThat(car.body().get("previousStatus").asText()).isEqualTo("PM");
        Answer a = post("/api/v1/vehicle-queries/car-status-change/cars/1101", Map.of("status", "STOR"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCHG1IS-UPDATE", PKG, "MSG", null));
        assertThat(sent.get()).containsEntry("gui", "VHCHG");
        assertThat(data.get()).containsEntry("carNo", "1101").containsEntry("vehStat", "STOR").containsEntry("vehStatPrev", "GO").containsEntry("termLastUpd", "T1");
    }

    @Test
    void carStatusChangeRefusesAnUnknownCarOrStatus() throws Exception {
        backend().on("VHVM1A2", in -> reply(8, 0, Map.of()));
        Answer car = get("/api/v1/vehicle-queries/car-status-change/cars/9999", null);
        assertThat(car.status()).isEqualTo(400);
        assertThat(car.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCHG1IS-GET-STAT", PKG, "MSG", "could not"));
        Answer status = post("/api/v1/vehicle-queries/car-status-change/cars/1101", Map.of("status", "FLYING"));
        assertThat(status.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHVM1A1");
    }
}
