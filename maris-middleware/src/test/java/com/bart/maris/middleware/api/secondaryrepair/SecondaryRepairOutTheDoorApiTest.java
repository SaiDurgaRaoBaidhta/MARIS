package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** A secondary repair sub job logged out the door (old VHSR04I), against a fake backend. */
class SecondaryRepairOutTheDoorApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSSecondaryRepair", URL = "/api/v1/secondary-repair/out-the-door";
    private final AtomicReference<Map<String, Map<String, Object>>> written = new AtomicReference<>();

    private void subJob(String status) {
        backend().on("VHSRWT", in -> reply(0, 1, Map.of("VHSRWT_DATA", rows("dataRow", List.of(Map.of("workType", "BENCH", "workTypeDesc", "BENCH TEST"))))));
        backend().on("VHSR04A", in -> reply(0, 2, Map.of("VHSR04A_DATA", rows("dataRow", List.of(Map.of("cmpntFaultCd", "F01"), Map.of("cmpntFaultCd", "F02"))))));
        backend().on("VHSR02E", in -> reply(0, 1, Map.of("VHSR02E_DATA", Map.of("subJobStat", status, "cntCmpntsIn", 2, "dateIn", "11-01-2026",
                "vendorId", "V1", "longTermStorInd", "N"))));
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHSR04B", in -> { written.set(in); return reply(0, 0, Map.of()); });
    }

    private Map<String, Object> request(String date, int out, int scrapped, String fault, String type, String disposition) {
        Map<String, Object> m = new HashMap<>(Map.of("jobNo", "J12345", "doc", 2, "dateOut", date, "qtyOut", out, "qtyScrapped", scrapped, "faultCode", fault,
                "workType", type, "inspector", "QA1"));
        if (disposition != null) m.put("disposition", disposition);
        return m;
    }

    @Test
    void optionsListTheDropDowns() throws Exception {
        subJob("OPN");
        Answer a = get(URL + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("faultCodes").toString()).isEqualTo("[\"F01\",\"F02\"]");
        assertThat(a.body().at("/workTypes/0/description").asText()).isEqualTo("BENCH TEST");
        assertThat(a.body().get("doc").asInt()).isEqualTo(1);
    }

    @Test
    void anOpenSubJobCanGoOut() throws Exception {
        subJob("OPN");
        Answer a = get(URL + "/sub-job", Map.of("jobNo", "J12345", "doc", 2));
        assertThat(a.body().get("canGoOut").asBoolean()).isTrue();
        assertThat(a.body().get("dateOut").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(a.body().get("qtyIn").asInt()).isEqualTo(2);
    }

    @Test
    void aClosedSubJobCannot() throws Exception {
        subJob("CLO");
        Answer a = get(URL + "/sub-job", Map.of("jobNo", "J12345", "doc", 2));
        assertThat(a.body().get("canGoOut").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assignedUnder("VHSR04IS-RET-DATA", PKG, "FINAL-MESSAGE", "SUB-JOB-STAT").get(0));
        Answer u = post(URL, request(LocalDate.now().toString(), 2, 0, "F01", "BENCH", null));
        assertThat(u.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHSR04B");
    }

    @Test
    void goesOutDeferred() throws Exception {
        subJob("OPN");
        Answer a = post(URL, request(LocalDate.now().toString(), 1, 1, "f02", "bench", "DEFERRED"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("status").asText()).isEqualTo("DEF");
        assertThat(a.body().get("message").asText()).contains("J12345").contains(TEXTS.assignedRaw("VHSR04IS-DB-STORE", PKG, "FINAL-MESSAGE-6").get(0).strip());
        Map<String, Object> d = written.get().get("VHSR04B_DATA");
        assertThat(d).containsEntry("subJobStat", "DEF").containsEntry("cmpntFaultCd", "F02").containsEntry("userGrp", "BRIGHTCO").containsEntry("vendorId", "V1");
        assertThat(String.valueOf(d.get("dateOut"))).matches("\\d\\d-\\d\\d-\\d{4}");
    }

    @Test
    void everyFailedCheckIsListed() throws Exception {
        subJob("OPN");
        Answer a = post(URL, request(LocalDate.now().plusDays(3).toString(), 1, 0, "ZZ", "", "OUT_TO_VENDOR"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("errors")).hasSize(6);
        assertThat(a.body().get("detail").asText()).startsWith("Future Date not allowed, ");
        assertThat(called()).doesNotContain("VHSR04B");
    }

    @Test
    void jobAndDocAreRequired() throws Exception {
        subJob("OPN");
        Answer a = get(URL + "/sub-job", Map.of("jobNo", "", "doc", 0));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assignedUnder("VHSR04IS-RET-DATA", PKG, "FINAL-MESSAGE", "JOB-NO").get(2));
    }
}
