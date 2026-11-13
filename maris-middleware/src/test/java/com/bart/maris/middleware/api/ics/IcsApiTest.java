package com.bart.maris.middleware.api.ics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * ICS reports (old screens VHIC30I criteria, VHIC31I report): the clean API.
 * Rules come from MRSVehicleIcsReports/RUIFunctions.egl: VHIC30IS-BEGIN, -SELECT, -RPT-TYPE, -SUBMIT, VHIC31IS-GET-DATA.
 */
class IcsApiTest extends ApiTestBase {
    /* the old screen's own texts (VHIC30I message prompters and VHIC30IS-BEGIN), not copies */
    private static final String BAD_DATE = Parity.bundle().promptText("VHIC30I", "Bad-Date-Msg");
    private static final String SUBMITTED = Parity.bundle().promptText("VHIC30I", "PickUpPrinterMsg");
    private static final String ALL_YARDS = Parity.bundle().assignedText("VHIC30IS-BEGIN", "MRSVehicleIcsReports", "YRD-DESC");

    private void optionsBackend() {
        backend().on("WS70A01", in -> reply(0, 2, Map.of("WS70A01_DATA", rows("dataRow", List.of(
                Map.of("serverName", "LKSPRT01", "prtrName", "DUBLIN TMZ", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN"),
                Map.of("serverName", "LKSPRT01", "prtrName", "IT", "vtamName", "LKS11HP1", "prtrLocDesc", "LAKESIDE"))))));
        backend().on("VHIC30A", in -> reply(0, 1, Map.of("VHIC30A_DATA", Map.of("revDate", "11-12-2026", "revDatedb2", "2026-11-12"))));
        backend().on("VHRT04A", in -> reply(0, 2, Map.of("VHRT04A_DATA", rows("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "HY", "yrdDesc", "HAYWARD YARD"))))));
        backend().on("VHIC30B", in -> reply(0, 1, Map.of("VHIC30B_DATA", rows("dataRow", List.of(
                Map.of("locIcs", "A07", "locIcsDesc", "A05, A07 AND A09"))))));
        backend().on("VHIC30C", in -> reply(0, 2, Map.of("VHIC30C_DATA", rows("dataRow", List.of(
                Map.of("msgTranCd", "1A", "msgTranCdDesc", "IDENTIFY TRAIN"), Map.of("msgTranCd", "1F", "msgTranCdDesc", "TRAIN DISPATCH"))))));
    }

    @Test
    void optionsListEverythingTheCriteriaScreenOffers() throws Exception {
        optionsBackend();
        Answer a = get("/api/v1/ics/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("WS70A01", "VHIC30A", "VHRT04A", "VHIC30B", "VHIC30C");
        assertThat(a.body().at("/printers/0/description").asText()).isEqualTo("DUBLIN");     // PRTR-LOC-DESC, as the old list
        assertThat(a.body().at("/printers/0/vtam").asText()).isEqualTo("DUBLNHP1");
        assertThat(a.body().at("/revenueDate").asText()).isEqualTo("2026-11-12");
        // every list ends with ALL, as the old drop-downs do
        assertThat(a.body().at("/yards").toString()).isEqualTo(
                "[{\"code\":\"CY\",\"description\":\"CONCORD YARD\"},{\"code\":\"HY\",\"description\":\"HAYWARD YARD\"},{\"code\":\"ALL\",\"description\":\"" + ALL_YARDS + "\"}]");
        assertThat(a.body().at("/locations/1/code").asText()).isEqualTo("ALL");
        assertThat(a.body().at("/locations/1/description").asText())
                .isEqualTo(Parity.bundle().assignedText("VHIC30IS-BEGIN", "MRSVehicleIcsReports", "LOC-DESC"));
        assertThat(a.body().at("/messageCodes/2/description").asText())
                .isEqualTo(Parity.bundle().assignedText("VHIC30IS-BEGIN", "MRSVehicleIcsReports", "MSG-TRAN-CD-DESC"));
    }

    @Test
    void aFailedListIsAWarningAndTheOthersStillLoad() throws Exception {
        optionsBackend();
        backend().on("VHIC30B", in -> new com.bart.maris.middleware.support.FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("ICS LOCATIONS", "", ""))), "00000000"));
        Answer a = get("/api/v1/ics/options", null);
        // like the old screen: the message is shown, the other lists still load, the failed list stays empty
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/warnings/0").asText()).isEqualTo("ICS LOCATIONS Is Not Found");
        assertThat(a.body().get("locations").size()).isZero();
        assertThat(a.body().get("yards").size()).isEqualTo(3);
    }

    private final AtomicReference<Map<String, Object>> sentCrit = new AtomicReference<>();

    private void reportBackend(int urc, List<Map<String, Object>> dataRows) {
        backend().on("VHIC31A", in -> {
            sentCrit.set(in.get("VHIC31A_CRIT"));
            return reply(urc, dataRows.size(), Map.of("VHIC31A_DATA", rows("dataRow", dataRows)));
        });
    }

    private static Map<String, Object> row(String time, String conNo, int dashNo) {
        return Map.ofEntries(Map.entry("tsDate", "11-10-2026"), Map.entry("tsTime", time), Map.entry("msgTranCd", "1F"),
                Map.entry("conNo", conNo), Map.entry("dashNo", dashNo), Map.entry("conSize", 4), Map.entry("conCurrLoc", "C55"),
                Map.entry("conCurrLocIcs", "045"), Map.entry("trainDir", "S"), Map.entry("msgSeqNo", 948143),
                Map.entry("msgProcessedCd", "Y"), Map.entry("funcMsgCmplt", "AUTO MT"), Map.entry("car1", " 3198}"));
    }

    @Test
    void messagesForAllLocationsSendTheOldRanges() throws Exception {
        reportBackend(0, List.of(row("00:58:14", "532-01", 1), row("01:04:32", "446-00", 0)));
        Answer a = get("/api/v1/ics/messages", Map.of("revenueDate", "2026-11-10"));
        assertThat(a.status()).isEqualTo(200);
        Map<String, Object> c = sentCrit.get();
        assertThat(c.get("revDate")).isEqualTo("2026-11-10");
        assertThat(c.get("reportType")).isEqualTo("D");                    // date / time order
        assertThat(c.get("reportOrder")).isEqualTo("Y");                   // by yard tracking yard
        assertThat(c.get("begMsgTranCd")).isEqualTo("");
        assertThat(c.get("endMsgTranCd")).isEqualTo("99");
        assertThat(c.get("begConCurrLoc")).isEqualTo("");
        assertThat(c.get("endConCurrLoc")).isEqualTo("999");
        assertThat(c.get("begYrdTrkg")).isEqualTo("");
        assertThat(c.get("endYrdTrkg")).isEqualTo("999");

        assertThat(a.body().get("count").asInt()).isEqualTo(2);
        assertThat(a.body().get("maxRowsReached").asBoolean()).isFalse();
        assertThat(a.body().at("/messages/0/consist").asText()).isEqualTo("532-01");
        assertThat(a.body().at("/messages/1/consist").asText()).isEqualTo("446-");   // "00" dash numbers are blanked
        assertThat(a.body().at("/messages/0/received").asText()).isEqualTo("11-10-2026 00:58:14");
        assertThat(a.body().at("/messages/0/sequence").asLong()).isEqualTo(948143);
        assertThat(a.body().at("/messages/0/cars/0").asText()).isEqualTo("3198}");
    }

    @Test
    void messagesForOneYardLocationAndCodeSendThoseValues() throws Exception {
        reportBackend(0, List.of(row("00:58:14", "532-01", 1)));
        Answer a = get("/api/v1/ics/messages", Map.of("revenueDate", "2026-11-10", "order", "consist", "groupBy", "location",
                "yard", "CY", "location", "A07", "messageCode", "1F"));
        assertThat(a.status()).isEqualTo(200);
        Map<String, Object> c = sentCrit.get();
        assertThat(c.get("reportType")).isEqualTo("C");
        assertThat(c.get("reportOrder")).isEqualTo("L");
        assertThat(c.get("begMsgTranCd")).isEqualTo("1F");
        assertThat(c.get("endMsgTranCd")).isEqualTo("1F");
        assertThat(c.get("begConCurrLoc")).isEqualTo("A07");
        assertThat(c.get("endConCurrLoc")).isEqualTo("A07");
        assertThat(c.get("begYrdTrkg")).isEqualTo("CY");
        assertThat(c.get("endYrdTrkg")).isEqualTo("CY");
    }

    @Test
    void aFutureOrInvalidDateIsRefusedWithTheOldMessageAndCallsNothing() throws Exception {
        reportBackend(0, List.of());
        for (String d : List.of(LocalDate.now().plusDays(2).toString(), "2000-12-31", "2026-13-01", "not-a-date", "")) {
            Answer a = get("/api/v1/ics/messages", Map.of("revenueDate", d));
            assertThat(a.status()).as(d).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).isEqualTo(BAD_DATE);
        }
        assertThat(called()).doesNotContain("VHIC31A");
    }

    @Test
    void noRowsIsAnEmptyList() throws Exception {
        reportBackend(100, List.of());
        Answer a = get("/api/v1/ics/messages", Map.of("revenueDate", "2026-11-10"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("count").asInt()).isZero();
        assertThat(a.body().get("messages").size()).isZero();
    }

    @Test
    void reachingTheMaximumOf150RowsIsFlagged() throws Exception {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 150; i++) many.add(row("01:00:00", "100-01", 1));
        reportBackend(0, many);
        Answer a = get("/api/v1/ics/messages", Map.of("revenueDate", "2026-11-10"));
        assertThat(a.body().get("count").asInt()).isEqualTo(150);
        assertThat(a.body().get("maxRowsReached").asBoolean()).isTrue();
    }

    @Test
    void printingSubmitsTheReportToThePrinter() throws Exception {
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>();
        backend().on("VHIC30E", in -> { crit.set(in.get("VHIC30E_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/ics/messages/print", Map.of("revenueDate", "2026-11-10", "printer", "DUBLNHP1",
                "yard", "CY", "messageCode", "ALL"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(SUBMITTED);
        Map<String, Object> c = crit.get();
        assertThat(c.get("vtamName")).isEqualTo("DUBLNHP1");
        assertThat(c.get("reportType")).isEqualTo("D");
        assertThat(c.get("functionCode")).isEqualTo("D");
        assertThat(c.get("dateFrom")).isEqualTo("11-10-2026");               // the old screen sends the MM-DD-YYYY date
        assertThat(c.get("dateTo")).isEqualTo("11-10-2026");
        assertThat(c.get("yrdTrkgYrd")).isEqualTo("CY");                     // one yard: that yard
        assertThat(c.get("currLoc")).isEqualTo("");                          // ALL: blank
        assertThat(c.get("tranCd")).isEqualTo("");
    }

    @Test
    void printingNeedsAPrinter() throws Exception {
        Answer a = post("/api/v1/ics/messages/print", Map.of("revenueDate", "2026-11-10"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHIC30E");
    }

    @Test
    void theApiNeedsASignedOnUser() throws Exception {
        send("POST", "/api/v1/auth/signoff", null);
        assertThat(get("/api/v1/ics/messages", Map.of("revenueDate", "2026-11-10")).status()).isEqualTo(401);
    }
}
