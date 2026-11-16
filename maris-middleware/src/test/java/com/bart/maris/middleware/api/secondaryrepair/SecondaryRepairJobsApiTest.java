package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Secondary repair job updates over HTTP (comments VHSR02I): the old checks, what is written, the messages. */
class SecondaryRepairJobsApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSSecondaryRepair";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private void subJobExists(int comments) {
        backend().on("VHSR02E", in -> reply(0, 1, Map.of("VHSR02E_DATA", Map.of("subJobStat", "OPN"))));
        backend().on("VHSR02F", in -> reply(0, 1, Map.of("VHSR02F_DATA", Map.of("maxRows", comments, "narrNo", 1, "narr", "FIRST", "userGrp", "G1"))));
    }

    @Test
    void commentsListTheFirstAndTheCount() throws Exception {
        subJobExists(2);
        Answer a = get("/api/v1/secondary-repair/comments", Map.of("jobNo", "a1", "doc", 1, "kind", "repair"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("count").asInt()).isEqualTo(2);
        assertThat(a.body().at("/comment/text").asText()).isEqualTo("FIRST");
        assertThat(a.body().get("kind").asText()).isEqualTo("REPAIR");
        assertThat(get("/api/v1/secondary-repair/comments/kinds", null).body().toString()).isEqualTo("[\"JOB\",\"REPAIR\"]");
    }

    @Test
    void commentsCheckLikeTheOldScreen() throws Exception {
        Answer a = get("/api/v1/secondary-repair/comments", Map.of("doc", 0, "kind", "JOB"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).startsWith(TEXTS.assigned("VHSR02IS-LIST", PKG, "ERROR-MESSAGE-OCCR", "Job no").replace(".", ","));
        backend().on("VHSR02E", in -> reply(100, 0, Map.of()));
        assertThat(get("/api/v1/secondary-repair/comments", Map.of("jobNo", "Z", "doc", 1, "kind", "JOB")).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHSR02IS-LIST", PKG, "FINAL-MESSAGE", "does not exist"));
        assertThat(post("/api/v1/secondary-repair/comments", Map.of("jobNo", "A1", "doc", 1, "kind", "JOB", "text", " ")).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHSR02IS-ADDNARR", PKG, "FINAL-MESSAGE", "comment"));
        assertThat(called()).doesNotContain("VHSR02A");
    }

    @Test
    void addingACommentWritesTheNextNumberWithTheGroupAndTerminal() throws Exception {
        subJobExists(2);
        backend().on("VHSR02A", in -> { sent.set(in.get("VHSR02A_DATA")); return reply(0, 0, Map.of()); });
        backend().on("VHSR02D", in -> reply(0, 1, Map.of("VHSR02D_DATA", Map.of("narrNo", 3, "narr", "NEW", "currentRow", 3))));
        Answer a = post("/api/v1/secondary-repair/comments", Map.of("jobNo", "A1", "doc", 1, "kind", "JOB", "text", "NEW"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("narrNo", 3L).containsEntry("narr", "NEW").containsEntry("userGrp", "BRIGHTCO")
                .containsEntry("termNarr", "MARIS-UI").containsEntry("jobIndicator", "JOB");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHSR02IS-JOB-NARR", PKG, "FINAL-MESSAGE", "Job comment"));
        assertThat(a.body().at("/comments/count").asInt()).isEqualTo(3);
    }

    @Test
    void changingACommentAndAFailedWriteGiveTheMarisMessage() throws Exception {
        backend().on("VHSR02B", in -> { sent.set(in.get("VHSR02B_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = send("PUT", "/api/v1/secondary-repair/comments/2", Map.of("jobNo", "A1", "doc", 1, "kind", "REPAIR", "text", "FIXED"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("narrNo", 2L).containsEntry("jobIndicator", "REPAIR");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHSR02IS-UPDNARR", PKG, "FINAL-MESSAGE", "repr comment updated successfully"));
        backend().on("VHSR02B", in -> reply(8, 0, Map.of()));
        assertThat(send("PUT", "/api/v1/secondary-repair/comments/2", Map.of("jobNo", "A1", "doc", 1, "kind", "JOB", "text", "X")).status()).isEqualTo(422);
    }

    @Test
    void pagingReadsOneComment() throws Exception {
        backend().on("VHSR02C", in -> { sent.set(in.get("VHSR02C_CRIT")); return reply(0, 1, Map.of("VHSR02C_DATA", Map.of("narr", "SECOND"))); });
        Answer a = get("/api/v1/secondary-repair/comments/2", Map.of("jobNo", "A1", "doc", 1, "kind", "JOB"));
        assertThat(a.body().get("text").asText()).isEqualTo("SECOND");
        assertThat(sent.get()).containsEntry("narrNo", 2L);
        assertThat(List.of("VHSR02C")).isSubsetOf(called());
    }

    // ---- VHSR01I / VHSR08I: new jobs and sub jobs ---------------------------------------------------------------

    private void lookups() {
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHSR01D", in -> reply(0, 0, Map.of("VHSR01D_CRIT", Map.of("rowCount", 0))));
        backend().on("VHSRWT", in -> reply(0, 1, Map.of("VHSRWT_DATA", rows("dataRow", List.of(Map.of("workType", "REPAIR", "workTypeDesc", "REPAIR"))))));
        backend().on("VHSRRA", in -> reply(0, 1, Map.of("VHSRRA_DATA", rows("dataRow", List.of(Map.of("reprGroup", Map.of("reprArea", "R1", "reprAreaDesc", "E")))))));
        backend().on("VHSRVS", in -> reply(0, 1, Map.of("VHSRVS_DATA", Map.of("recordFound", "Y"))));
        backend().on("VHSR01A", in -> reply(0, 1, Map.of("VHSR01A_DATA", Map.of("sumCntCmpntsIn", 0, "cntThreshold", 0))));
        backend().on("VHSR01B", in -> reply(0, 1, Map.of("VHSR01B_DATA", rows("dataRow", List.of(Map.of("equipType", "SWITCH"))))));
        backend().on("VHSR01C", in -> reply(0, 1, Map.of("VHSR01C_DATA", Map.of("equipType", "FAREGATE", "equipLoc", "M16"))));
    }

    private static Map<String, Object> newJob(String dept, String type, String id, String workType) {
        Map<String, Object> m = new java.util.HashMap<>(Map.of("workType", workType, "repairArea", "R1", "stockNo", "123456789", "quantity", 1));
        if (dept != null) m.put("dept", dept);
        if (type != null) m.put("equipmentType", type);
        if (id != null) m.put("equipmentId", id);
        return m;
    }

    @Test
    void waysideJobsTakeTheEquipmentOfTheirDepartment() throws Exception {
        lookups();
        backend().on("VHSR01E", in -> { sent.set(in.get("VHSR01E_DATA")); return reply(0, 0, Map.of("VHSR01E_DATA", Map.of("jobNo", "B00001"))); });
        Answer a = post("/api/v1/secondary-repair/jobs", newJob("A", null, "tvm01", "REPAIR"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("job").asText()).isEqualTo("B00001");
        assertThat(sent.get()).containsEntry("deptCd", "A").containsEntry("equipId", "TVM01").containsEntry("equipType", "FAREGATE").containsEntry("equipLoc", "M16")
                .containsEntry("carNo", "").containsEntry("userGrp", "BRIGHTCO").containsEntry("termLastUpd", "MARIS-UI");
        assertThat(post("/api/v1/secondary-repair/jobs", newJob("W", "SWITCH", null, "REPAIR")).status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("deptCd", "W").containsEntry("equipType", "SWITCH");
    }

    @Test
    void newJobChecksTheDepartmentAndWorkTypeLikeTheOldScreen() throws Exception {
        lookups();
        String c = "VHSR01IS-CREATE";
        assertThat(post("/api/v1/secondary-repair/jobs", newJob("W", "NOPE", null, "BOGUS")).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Work Type").replace(".", "") + ", "
                        + TEXTS.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equipment does not exist").replace(".", ""));
        assertThat(post("/api/v1/secondary-repair/jobs", newJob("A", null, null, "REPAIR")).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equip Id required").replace(".", ""));
        assertThat(called()).doesNotContain("VHSR01E");
        assertThat(get("/api/v1/secondary-repair/departments/W/equipment-types", null).body().at("/0/code").asText()).isEqualTo("SWITCH");
    }

    @Test
    void jobSplitListsTheSubJobsAndCreatesTheNextOne() throws Exception {
        lookups();
        backend().on("VHSR08A", in -> reply(0, 1, Map.of("VHSR08A_DATA", Map.of("recordFound", "Y", "jobStat", "OPN",
                "dataRow", List.of(Map.of("reprArea", "R1", "subJobDocNo", 1, "narr", "FIRST"))))));
        backend().on("VHSR08C", in -> { sent.set(in.get("VHSR08C_DATA")); return reply(0, 0, Map.of()); });
        assertThat(get("/api/v1/secondary-repair/job-split/a1", null).body().get("nextDoc").asInt()).isEqualTo(2);
        Answer a = post("/api/v1/secondary-repair/job-split/A1/sub-jobs", Map.of("workType", "REPAIR", "repairArea", "R1", "stockNo", "123456789", "quantity", 1));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("subJobDocNo", 2L).containsEntry("jobNo", "A1");
        assertThat(a.body().get("message").asText()).contains("A1");
    }

    // ---- VHSR09I / VHSR03I: repairs, labor, close --------------------------------------------------------------

    @Test
    void repairsAndLaborOfASubJobOutTheDoor() throws Exception {
        backend().on("VHSR09B", in -> reply(0, 1, Map.of("VHSR09B_DATA", Map.of("docCount", 1, "subJobStat", "REP"))));
        backend().on("VHSR09A", in -> reply(0, 1, Map.of("VHSR09A_DATA_01", rows("dataRow", List.of(Map.of("stockNo", "111", "cmpntCd", "BRK", "cntCmpntsRepr", 1))))));
        backend().on("VHSR09D", in -> { sent.set(in.get("VHSR09D_DATA_02")); return reply(0, 0, Map.of()); });
        Answer r = post("/api/v1/secondary-repair/repair-detail", Map.of("jobNo", "A1", "doc", 1, "lines", List.of(Map.of("stockNo", "444", "quantity", 2))));
        assertThat(r.status()).as(r.body().toString()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("jobNo", "A1").containsEntry("termLastUpd", "MARIS-UI");
        assertThat(r.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHSR09IS-DB-STORE", PKG, "FINAL-MESSAGE", "Repair Details added"));

        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHSR03F", in -> reply(0, 1, Map.of("VHSR03F_DATA", Map.of("docCount", 1, "subJobStat", "REP", "dateIn", "2026-11-01", "dateOut", "2026-11-10"))));
        backend().on("VHSR03A", in -> reply(100, 0, Map.of()));
        backend().on("VHSR03B", in -> reply(100, 0, Map.of()));
        backend().on("VHSR03D", in -> { sent.set(in.get("VHSR03D_DATA")); return reply(0, 0, Map.of()); });
        Answer l = post("/api/v1/secondary-repair/labor", Map.of("jobNo", "A1", "doc", 1, "comment", "DONE",
                "lines", List.of(Map.of("date", "2026-11-05", "employee", "12345", "hours", 2.5))));
        assertThat(l.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("addNarrInd", "Y").containsEntry("narr", "DONE");
        assertThat(sent.get().get("dataRow").toString()).contains("11-05-2026").contains("12345");
        assertThat(post("/api/v1/secondary-repair/labor/close", Map.of("jobNo", "A1", "doc", 1)).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHSR03IS-RET-DATA", PKG, "FINAL-MESSAGE", "Repair Details required"));
        assertThat(get("/api/v1/secondary-repair/labor", Map.of("jobNo", "A1", "doc", 0)).body().get("detail").asText()).startsWith("Doc No");
        assertThat(called()).doesNotContain("VHSR03E");
    }
}
