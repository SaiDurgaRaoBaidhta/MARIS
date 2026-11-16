package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The reports criteria window (old screen VHYT27I): its options (VHYT27IS-BEGIN: WS70A01, VHYT27B, VHRT04A) and Hard Copy
 * (VHYT27IS-TEST-DATE, VHYT27IS-SUBMIT: VHYT27A / F / C / D / E / G by report).
 */
class YardQueriesReportsApiTest extends ApiTestBase {
    private static final String BAD_RANGE = Parity.bundle().promptText("VHYT27I", "Message_Prompter1");
    private static final String SUBMITTED = Parity.bundle().promptText("VHYT27I", "Message_Prompter2");
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    @Test
    void theOptionsAreThePrintersYardsAndProposedDates() throws Exception {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(
                Map.of("prtrLocDesc", "DUBLIN", "vtamName", "DUBLNHP1", "prtrName", "DUBLIN TMZ", "serverName", "S1"))))));
        backend().on("VHYT27B", in -> reply(0, 0, Map.of("VHYT27B_DATA", Map.of("startDate", "11-01-2026", "endDate", "11-12-2026",
                "startDatedb2", "2026-11-01", "endDatedb2", "2026-11-12"))));
        backend().on("VHRT04A", in -> reply(0, 2, Map.of("VHRT04A_DATA", rows("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "HY", "yrdDesc", "HAYWARD YARD"))))));
        Answer a = get("/api/v1/yard-queries/report-options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("WS70A01", "VHYT27B", "VHRT04A");
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
        assertThat(a.body().at("/yards/1/code").asText()).isEqualTo("HY");
        assertThat(a.body().get("from").asText()).isEqualTo("2026-11-01");
        assertThat(a.body().get("to").asText()).isEqualTo("2026-11-12");
    }

    @Test
    void aListThatFailsIsAWarning() throws Exception {
        backend().on("WS70A01", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("PRINTERS", "", ""))), "00000000"));
        backend().on("VHYT27B", in -> reply(0, 0, Map.of()));
        backend().on("VHRT04A", in -> reply(0, 0, Map.of()));
        Answer a = get("/api/v1/yard-queries/report-options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/warnings/0").asText()).isEqualTo("PRINTERS Is Not Found");
    }

    private Answer print(Map<String, Object> body) throws Exception {
        for (String p : List.of("VHYT27A", "VHYT27C", "VHYT27D", "VHYT27E", "VHYT27F", "VHYT27G")) {
            backend().on(p, in -> { sent.set(new HashMap<>(in.get(p + "_CRIT"))); sent.get().put("_program", p); return reply(0, 0, Map.of()); });
        }
        return post("/api/v1/yard-queries/reports/print", body);
    }

    @Test
    void eachReportIsPrintedByItsProgramWithItsRecord() throws Exception {
        Answer a = print(Map.of("report", "SHOPSTAT", "yard", "CY", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(SUBMITTED);
        assertThat(sent.get()).containsEntry("_program", "VHYT27A").containsEntry("yrdTrkgYrd", "CY").containsEntry("reptType", "SHOPSTAT")
                .containsEntry("reptCd", "DUBLNHP1");

        print(Map.of("report", "YARD-LOC", "yard", "", "printer", "DUBLNHP1"));
        assertThat(sent.get()).containsEntry("_program", "VHYT27F").containsEntry("yrdTrkgYrd", "HY").containsEntry("reptType", "");

        print(Map.of("report", "CAR-HIST", "yard", "RY", "printer", "P1", "from", "2026-11-01", "to", "2026-11-10", "fromCar", "1200"));
        assertThat(sent.get()).containsEntry("_program", "VHYT27C").containsEntry("dateReptFrom", "11-01-2026").containsEntry("dateReptTo", "11-10-2026")
                .containsEntry("fromCar", "1200").containsEntry("toCar", "1200");

        print(Map.of("report", "YARDHIST", "yard", "RY", "printer", "P1", "from", "2026-11-01", "to", "2026-11-10"));
        assertThat(sent.get()).containsEntry("_program", "VHYT27D").containsEntry("dateReptFrom", "11-01-2026");

        print(Map.of("report", "0400-RPT", "yard", "RY", "printer", "P1", "from", "2026-11-10", "to", "2026-11-01"));   // the old screen does not check this one's dates
        assertThat(sent.get()).containsEntry("_program", "VHYT27E").containsEntry("dateReptFrom", "11-10-2026");

        print(Map.of("report", "04XX-RPT", "yard", "", "printer", "P1", "from", "2026-11-01", "to", "2026-11-10"));
        assertThat(sent.get()).containsEntry("_program", "VHYT27G").containsEntry("yrdTrkgYrd", "")                  // no HY for this one
                .containsEntry("dateReptFrom", "2026-11-01").containsEntry("dateReptTo", "2026-11-10");
    }

    @Test
    void theOldChecksRefuseAndNothingIsPrinted() throws Exception {
        Answer badRange = print(Map.of("report", "CAR-HIST", "yard", "HY", "printer", "P1", "from", "2026-11-10", "to", "2026-11-01"));
        assertThat(badRange.status()).isEqualTo(400);
        assertThat(badRange.body().get("detail").asText()).isEqualTo(BAD_RANGE);
        assertThat(print(Map.of("report", "SHOPSTAT", "yard", "HY", "printer", "")).status()).isEqualTo(400);      // Hard Copy needs a printer
        assertThat(print(Map.of("report", "OSDETAIL", "yard", "HY", "printer", "P1")).status()).isEqualTo(400);    // no hard copy
        assertThat(print(Map.of("report", "NOSUCH", "yard", "HY", "printer", "P1")).status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHYT27A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("PRINTER", "", ""))), "00000000"));
        Answer a = post("/api/v1/yard-queries/reports/print", Map.of("report", "SHOPSTAT", "yard", "HY", "printer", "P1"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("PRINTER Is Not Found");
    }
}
