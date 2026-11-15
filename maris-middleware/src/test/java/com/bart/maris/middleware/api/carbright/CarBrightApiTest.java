package com.bart.maris.middleware.api.carbright;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Car thorough cleaning report (old screens VHCBR1I list, VHCBR2I printer selection). Rules from
 * MRSVehicleCarBright/RUIFunctions.egl: VHCBR1IS-LIST, VHCBR01IS-PRINT, VHCBR2IS-SUBMIT.
 */
class CarBrightApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleCarBright";
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private void dates() {
        backend().on("VHCBR1X", in -> reply(0, 1, Map.of("VHCBR1X_DATA", Map.of("dateCarBrit", "2026-08-15", "overDueDate", "2026-05-15"))));
    }

    private void cars(int urc, List<Map<String, Object>> rows) {
        backend().on("VHCBR1A", in -> {
            crit.set(in.get("VHCBR1A_CRIT"));
            return reply(urc, rows.size(), Map.of("VHCBR1A_DATA", rows("dataRow", rows)));
        });
    }

    @Test
    void listSendsTheDatesYardAndOptionAndListsEveryCar() throws Exception {
        dates();
        cars(0, List.of(Map.of("carNo", "1101", "currLoc", "CY01", "vehStat", "GO", "dateCarBrit", "2026-07-01",
                        "carNo1", "1102", "currLoc1", "CY02", "vehStat1", "NO GO", "dateCarBrit1", "2026-06-01"),
                Map.of("carNo", "1103", "currLoc", "CY03", "vehStat", "GO", "dateCarBrit", "2026-05-01")));
        Answer a = get("/api/v1/car-bright/cleaning", Map.of("homeYard", "CY", "option", "overdue"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("homeYrd", "CY").containsEntry("optionIndex", 2L)
                .containsEntry("dateCrit", "2026-08-15").containsEntry("overDueDate", "2026-05-15");
        // the old list shows two cars per row; the API lists every car once, in the same order
        assertThat(a.body().get("count").asInt()).isEqualTo(3);
        assertThat(a.body().at("/cars/1").toString())
                .isEqualTo("{\"car\":\"1102\",\"location\":\"CY02\",\"status\":\"NO GO\",\"trainIncident\":\"\",\"lastThoroughClean\":\"2026-06-01\"}");
        assertThat(a.body().get("cleaningDate").asText()).isEqualTo("2026-08-15");
        assertThat(a.body().get("overdueDate").asText()).isEqualTo("2026-05-15");
    }

    @Test
    void theDefaultOptionIsLastCleaning() throws Exception {
        dates();
        cars(0, List.of());
        get("/api/v1/car-bright/cleaning", Map.of("homeYard", "ALL"));
        assertThat(crit.get()).containsEntry("optionIndex", 1L).containsEntry("homeYrd", "ALL");
    }

    @Test
    void onlyTheOldHomeYardsAreAccepted() throws Exception {
        dates();
        cars(0, List.of());
        for (String y : List.of("", "ZZ", "SVB")) {
            Answer a = get("/api/v1/car-bright/cleaning", Map.of("homeYard", y));
            assertThat(a.status()).as(y).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "home yard"));
        }
        assertThat(called()).doesNotContain("VHCBR1X", "VHCBR1A");
        assertThat(get("/api/v1/car-bright/home-yards", null).body().toString()).isEqualTo("[\"CY\",\"DY\",\"HY\",\"RY\",\"ALL\"]");
    }

    @Test
    void noCarsGivesTheOldMessage() throws Exception {
        dates();
        cars(100, List.of());
        Answer a = get("/api/v1/car-bright/cleaning", Map.of("homeYard", "HY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("count").asInt()).isZero();
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "No records"));
    }

    @Test
    void withoutTheCleaningDatesNothingIsListed() throws Exception {
        backend().on("VHCBR1X", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        cars(0, List.of());
        Answer a = get("/api/v1/car-bright/cleaning", Map.of("homeYard", "HY"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "not retrieved"));
        assertThat(called()).doesNotContain("VHCBR1A");
    }

    @Test
    void printSendsTheOldReportRequest() throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        backend().on("VHCBR02", in -> { sent.set(in.get("VHCBR02_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/car-bright/cleaning/print", Map.of("homeYard", "DY", "option", "last", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.prompt("VHCBR2I", "Message_Prompter1"));
        assertThat(sent.get()).containsEntry("reptType", "DUBLNHP1").containsEntry("homeYrd", "DY")
                .containsEntry("brightenSw", "").containsEntry("dateReptFrom", "");
        post("/api/v1/car-bright/cleaning/print", Map.of("homeYard", "DY", "option", "overdue", "printer", "DUBLNHP1"));
        assertThat(sent.get()).containsEntry("brightenSw", "N");              // as the old VHCBR2IS-SUBMIT sends it
    }

    @Test
    void printNeedsAPrinterAndAValidYard() throws Exception {
        assertThat(post("/api/v1/car-bright/cleaning/print", Map.of("homeYard", "DY")).status()).isEqualTo(400);
        assertThat(post("/api/v1/car-bright/cleaning/print", Map.of("homeYard", "XX", "printer", "P1")).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHCBR02");
    }

    @Test
    void printersComeFromWs70a01() throws Exception {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"))))));
        Answer a = get("/api/v1/car-bright/printers", null);
        assertThat(a.body().toString()).isEqualTo("[{\"description\":\"DUBLIN TMZ\",\"vtam\":\"DUBLNHP1\"}]");
    }
}
