package com.bart.maris.middleware.api.tables2;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** MRS messages received (old VHCCS04I, 05I, 06I, 07I, 18I) through the HTTP API against a fake backend. */
class Tables2ApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    @Test
    void theOptionsAreTheCriteriaLists() throws Exception {
        backend().on("VHCCS4E", in -> reply(0, 1, Map.of("VHCCS4E_DATA", rows("dataRow", List.of(Map.of("yrd", "HY"))))));
        backend().on("VHCCS4F", in -> reply(0, 1, Map.of("VHCCS4F_DATA", rows("dataRow", List.of(Map.of("tranCd", "05"))))));
        backend().on("VHCCS4G", in -> reply(0, 1, Map.of("VHCCS4G_DATA", rows("dataRow", List.of(Map.of("loc", "HY01"))))));
        backend().on("VHCCS4H", in -> reply(0, 1, Map.of("VHCCS4H_DATA", rows("dataRow", List.of(
                Map.of("scanId", Map.of("scannerId", "A", "scFil", "-", "loc", "HY01", "scFil2", "-", "scanDesc", "N")))))));
        backend().on("VHCCS4I", in -> reply(0, 1, Map.of("VHCCS4I_DATA", rows("dataRow", List.of(Map.of("msgProcessedCd", "Y"))))));
        Answer a = get("/api/v1/tables-2/messages/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCCS4E", "VHCCS4F", "VHCCS4G", "VHCCS4H", "VHCCS4I");
        assertThat(a.body().at("/yards/0").asText()).isEqualTo("HY");
        assertThat(a.body().at("/scanners/0/id").asText()).isEqualTo("A-HY01-N");
        assertThat(a.body().at("/scanners/0/direction").asText()).isEqualTo("N");
        assertThat(a.body().get("fromDate").asText()).matches("\\d{2}-\\d{2}-\\d{4}");
    }

    @Test
    void listSendsTheRangesAndTheTimeStampWindow() throws Exception {
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHYTIME", in -> reply(0, 0, Map.of()));
        backend().on("VHCCS4H", in -> reply(0, 0, Map.of()));
        backend().on("VHCCS4A", in -> { sent.set(new HashMap<>(in.get("VHCCS4A_CRIT")));
            return reply(0, 1, Map.of("VHCCS4A_DATA", Map.of("dataRow", List.of(Map.of("tsMsgRcvd", "2026-11-14-10.15.00.000000", "conNo", "201")),
                    "msgtobeprocd", 4))); });
        Answer a = post("/api/v1/tables-2/messages/list", Map.of("fromDate", "11-14-2026", "fromTime", "09:00:00", "toTime", "12:00:00", "yard", "HY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("conCurrYrdFr", "HY").containsEntry("conCurrYrdTo", "HY").containsEntry("tranCdTo", "99")
                .containsEntry("compareDtF", "2026-11-14-09.00.00.000000").containsEntry("compareDtT", "2026-11-14-12.00.00.000000");
        assertThat(a.body().at("/messages/0/conNo").asText()).isEqualTo("201");
        assertThat(a.body().get("toBeProcessed").asInt()).isEqualTo(4);
        assertThat(a.body().get("compareFrom").asText()).isEqualTo("2026-11-14-09.00.00.000000");
    }

    @Test
    void aBadDateIsRefusedWithTheOldMessage() throws Exception {
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "Y", "otherSqlError", "N"))));
        Answer a = post("/api/v1/tables-2/messages/list", Map.of("fromDate", "13-45-2026", "fromTime", "09:00:00", "toTime", "12:00:00"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo("INVALID FROM DATE");
        assertThat(called()).doesNotContain("VHCCS4A");
    }

    @Test
    void aDetailSaveChecksTheDatesThenUpdates() throws Exception {
        backend().on("VHCCS5N", in -> reply(0, 0, Map.of()));
        backend().on("VHCCS5M", in -> reply(0, 0, Map.of()));
        backend().on("VHCCS5C", in -> { sent.set(new HashMap<>(in.get("VHCCS5C_DATA"))); return reply(0, 0, Map.of()); });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tsMsgRcvd", "2026-11-14-10.15.00.000000");
        m.put("dateTrans", "11-14-2026");
        m.put("msgTranCd", "05");
        m.put("msgLength", 42);
        m.put("iCars", "1101 1102");
        Answer a = post("/api/v1/tables-2/messages/update", m);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("UPDATE SUCCESSFUL");
        assertThat(sent.get()).containsEntry("dateTrans", "2026-11-14");
        assertThat(called()).containsExactly("VHCCS5N", "VHCCS5C");
    }

    @Test
    void carAndErrorUpdatesGoToTheirPrograms() throws Exception {
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHCCS6C", in -> { sent.set(new HashMap<>(in.get("VHCCS6C_DATA"))); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/tables-2/messages/cars/update", Map.of("timestamp", "TS1", "carNo", "1101", "carOrder", 1, "conNo", "201", "dashNo", 2,
                "dateInService", "11-14-2026", "orientation", "<", "orientationDirection", "N"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("dateInsrvRev", "2026-11-14").containsEntry("vagenOrientation", "<");
        backend().on("VHCCS7C", in -> reply(0, 0, Map.of()));
        Answer e = post("/api/v1/tables-2/messages/errors/update", Map.of("timestamp", "TS1", "count", 0, "function", "MOVE", "description", "X", "type", "E"));
        assertThat(e.status()).isEqualTo(400);
        assertThat(e.body().get("detail").asText()).isEqualTo("Message Error Type required");   // the old screen's inverted type test wins
    }

    @Test
    void theHardCopyNeedsAPrinterAndSendsTheCriteria() throws Exception {
        assertThat(post("/api/v1/tables-2/messages/print", Map.of("fromDate", "11-14-2026")).status()).isEqualTo(400);
        backend().on("VHCCSHA", in -> { sent.set(new HashMap<>(in.get("VHCCSHA_CRIT"))); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/tables-2/messages/print", Map.of("printer", "DUBLNHP1", "fromDate", "11-14-2026", "scanner", "A-HY01", "sort", 2));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("reptId", "DUBLNHP1").containsEntry("scannerId", "A");
        assertThat(String.valueOf(sent.get().get("sortCode"))).isEqualTo("2");
    }
}
