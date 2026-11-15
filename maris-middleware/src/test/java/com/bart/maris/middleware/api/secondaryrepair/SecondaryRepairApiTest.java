package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Secondary repair read-only screens over HTTP: the old checks and messages, the lists, the reprint. */
class SecondaryRepairApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSSecondaryRepair";
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private static String msg(String fn, String containing) {
        return TEXTS.assigned(fn, PKG, "FINAL-MESSAGE", containing);
    }

    @BeforeEach
    void lists() {
        backend().on("VHSR05C", in -> reply(0, 2, Map.of("VHSR05C_DATA", rows("dataRow", List.of(
                Map.of("subJobStat", "OPN", "subJobStatDesc", "OPEN"), Map.of("subJobStat", "OUT", "subJobStatDesc", "OUT THE DOOR"))))));
        backend().on("VHSR05B", in -> reply(0, 1, Map.of("VHSR05B_DATA", Map.of("stockDesc", "BRAKE CALIPER"))));
    }

    @Test
    void stockInquiryListsTheJobsOfAStock() throws Exception {
        backend().on("VHSR05A", in -> { crit.set(in.get("VHSR05A_CRIT")); return reply(0, 1, Map.of("VHSR05A_DATA", Map.of("totCntCmpntsIn", 2,
                "dataRow", List.of(Map.of("jobNo", "A12345", "subJobDocNo", 1, "serNo", "S1", "cntCmpntsIn", 2, "subJobStat", "OPN"))))); });
        Answer a = get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "stk123", "status", "OUT", "longTermStorage", "y"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("stockNo", "STK123").containsEntry("subJobStat", "OUT").containsEntry("longTermStorInd", "Y");
        assertThat(a.body().get("description").asText()).isEqualTo("BRAKE CALIPER");
        assertThat(a.body().get("totalQuantityIn").asInt()).isEqualTo(2);
        assertThat(a.body().at("/jobs/0/job").asText()).isEqualTo("A12345");
        assertThat(get("/api/v1/secondary-repair/stock-inquiry/options", null).body().at("/statuses/1/code").asText()).isEqualTo("OUT");
    }

    @Test
    void stockInquiryChecksLikeTheOldScreen() throws Exception {
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of()).body().get("detail").asText()).isEqualTo(msg("VHSR05IS-VAL-STOCK", "Enter"));
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "X", "status", "ZZZ")).body().get("detail").asText())
                .isEqualTo(msg("VHSR05IS-VAL-OTHER", "STATUS"));
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "X", "longTermStorage", "Q")).body().get("detail").asText())
                .isEqualTo(msg("VHSR05IS-VAL-OTHER", "STORAGE"));
        backend().on("VHSR05B", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "NOPE")).body().get("detail").asText())
                .isEqualTo(msg("VHSR05IS-VAL-STOCK", "does not exist"));
        assertThat(called()).doesNotContain("VHSR05A");
    }

    @Test
    void stockInquiryNotesNoRowsAndTheRowLimit() throws Exception {
        backend().on("VHSR05A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "X")).body().get("message").asText()).isEqualTo(msg("VHSR05IS-LIST", "No information"));
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) many.add(Map.of("jobNo", "J" + i));
        backend().on("VHSR05A", in -> reply(0, 200, Map.of("VHSR05A_DATA", rows("dataRow", many))));
        assertThat(get("/api/v1/secondary-repair/stock-inquiry", Map.of("stockNo", "X")).body().get("note").asText()).isEqualTo(msg("VHSR05IS-LIST", "ROWS"));
    }

    @Test
    void openJobsOnlyForTheStatusAndAnyJobForTheHistory() throws Exception {
        backend().on("VHSR11A", in -> reply(0, 1, Map.of("VHSR11A_DATA", Map.of("recordFound", "Y", "jobStat", "CLO",
                "dataRow", List.of(Map.of("subJobDocNo", 1, "serNo", "S1"))))));
        assertThat(get("/api/v1/secondary-repair/jobs/A1", null).body().get("detail").asText()).isEqualTo(msg("VHSR11IS-LIST", "Closed"));
        Answer h = get("/api/v1/secondary-repair/jobs/A1/history", null);
        assertThat(h.status()).isEqualTo(200);
        assertThat(h.body().at("/subJobs/0/serialNo").asText()).isEqualTo("S1");
        backend().on("VHSR11A", in -> reply(100, 0, Map.of("VHSR11A_DATA", Map.of("recordFound", "N"))));
        assertThat(get("/api/v1/secondary-repair/jobs/A2", null).body().get("detail").asText()).isEqualTo(msg("VHSR11IS-LIST", "does not exist"));
    }

    @Test
    void subJobGivesItsNarrativesRepairsAndLabor() throws Exception {
        backend().on("VHSRJNX", in -> { crit.set(in.get("VHSRJNX_DATA")); return reply(0, 1, Map.of("VHSRJNX_DATA", rows("dataRow", List.of(Map.of("narr", "CHECKED"))))); });
        backend().on("VHSRJRN", in -> reply(100, 0, Map.of()));
        backend().on("VHSR09A", in -> reply(0, 1, Map.of("VHSR09A_DATA_01", rows("dataRow", List.of(Map.of("cmpntCd", "BRK", "cntCmpntsRepr", 2))))));
        backend().on("VHSR10A", in -> reply(0, 1, Map.of("VHSR10A_DATA", rows("dataRow", List.of(Map.of("emplNo", "12345", "reprLaborHrs", 1.5))))));
        Answer a = get("/api/v1/secondary-repair/jobs/A12345/sub-jobs/2", null);
        assertThat(crit.get()).containsEntry("jobNo", "A12345").containsEntry("subJobDocNo", 2L);
        assertThat(a.body().get("narrative").toString()).isEqualTo("[\"CHECKED\"]");
        assertThat(a.body().get("repairNarrative").size()).isZero();
        assertThat(a.body().at("/repairs/0/component").asText()).isEqualTo("BRK");
        assertThat(a.body().at("/labor/0/hours").asDouble()).isEqualTo(1.5);
    }

    @Test
    void reprintChecksTheJobThenSubmits() throws Exception {
        backend().on("VHSR15C", in -> { crit.set(in.get("VHSR15C_CRIT")); return reply(0, 1, Map.of("VHSR15C_DATA", Map.of("defaultPrtIdx", 1,
                "dataRow", List.of(Map.of("prtrLocDesc", "SHOP", "vtamName", "SHOPHP1"))))); });
        Answer o = get("/api/v1/secondary-repair/reprint/options", null);
        assertThat(crit.get()).containsEntry("userGroup", "BRIGHTCO");                         // the signed-on user group
        assertThat(o.body().get("defaultPrinter").asText()).isEqualTo("SHOPHP1");

        backend().on("VHSR15A", in -> reply(0, 1, Map.of("VHSR15A_DATA", Map.of("recordExist", "Y", "subJobStat", "CLO"))));
        assertThat(post("/api/v1/secondary-repair/jobs/A1/reprint", Map.of("doc", 1, "printer", "SHOPHP1")).body().get("detail").asText())
                .isEqualTo(msg("VHSR15IS-VALIDATE", "Closed"));
        assertThat(called()).doesNotContain("VHSR15B");

        backend().on("VHSR15A", in -> reply(0, 1, Map.of("VHSR15A_DATA", Map.of("recordExist", "Y", "subJobStat", "OPN"))));
        backend().on("VHSR15B", in -> { crit.set(in.get("VHSR15B_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/secondary-repair/jobs/A1/reprint", Map.of("doc", 2, "printer", "SHOPHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHSR15IS-MESSAGE", PKG, "FINAL-MESSAGE", "SUBMITTED"));
        assertThat(crit.get()).containsEntry("jobNo", "A1").containsEntry("docNo", 2L).containsEntry("reptType", "SHOPHP1");
    }
}
