package com.bart.maris.middleware.api.repair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Repair over HTTP: what the UI gets and what the middleware sends to MARIS. */
class RepairApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleRepair", INCD = "29990010";
    private final AtomicReference<Map<String, Map<String, Object>>> written = new AtomicReference<>();

    @BeforeEach
    void scripted() {
        backend().on("VHRV01C", in -> reply(0, 2, Map.of("VHRV01C_DATA", Map.of("reprCauseCount", 2, "dataRow", List.of(
                Map.of("reprCauseCd", "A", "reprCauseCdDesc", "ADJUSTED"), Map.of("reprCauseCd", "B", "reprCauseCdDesc", "BROKEN"))))));
        backend().on("VHRV01D", in -> reply(0, 1, Map.of("VHRV01D_DATA", Map.of("reprDispCount", 1, "dataRow", List.of(
                Map.of("reprDispCd", "R", "reprDispCdDesc", "REPLACED"))))));
        backend().on("VHINC2D", in -> reply(0, 1, Map.of("VHINC2D_DATA", Map.of("carNo", "1101", "conNo", "201", "dashNo", 3, "vehIncdStat", "OPN",
                "incdType", "REG"))));
        backend().on("VHRV01A", in -> reply(0, 1, Map.of("VHRV01A_CRIT", Map.of("incdNo", INCD, "reprCount", 1, "reprNarrCount", 1),
                "VHRV01A_DATA", Map.of("dataReprRow", List.of(Map.of("reprDocNo", 1, "reprCauseCd", "A", "vehSystem", "DR", "vehSubSystem", "01",
                        "reprDispCd", "R", "reprLoc", "HY", "dateRepr", "10-02-2026", "timeRepr", "10:15:00", "emplNo", "12345"))),
                "VHRV01A_NARR_DATA", Map.of("dataNarrRow", List.of(Map.of("reprDocNo", 1, "narrNo", 1, "narr", "DOOR ADJUSTED"))))));
        backend().on("VHRV01F", in -> reply(0, 1, Map.of("VHRV01F_DATA", Map.of("dataRow", List.of(
                Map.of("narrNo", 1, "userGrp", "BRIGHTCO", "dateNarr", "10-02-2026", "timeNarr", "10:20:00", "narr", "DOOR ADJUSTED"))))));
        backend().on("VHRV01E", in -> reply(0, 1, Map.of("VHRV01E_CRIT", Map.of("rowCount", 1))));
        backend().on("AFCXVLD", in -> reply(0, 1, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHRV01B", in -> { written.set(in); return reply(0, 1, Map.of("VHRV01B_CRIT", Map.of("incdNo", INCD, "errFound", "N"))); });
        backend().on("VHRV02A", in -> reply(0, 1, Map.of("VHRV02A_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntDocNo", 1, "cmpntCd", "DOR", "cmpntType", "01", "reprDispCd", "R", "dateRepr", "10-02-2026"))))));
        backend().on("VHRV02F", in -> {
            Map<String, Object> d = new HashMap<>(in.get("VHRV02F_DATA"));
            d.put("rowCount", 1);
            return reply(0, 1, Map.of("VHRV02F_DATA", d));
        });
        backend().on("VHRV02B", in -> { written.set(in); return reply(0, 1, Map.of()); });
    }

    @Test
    void optionsComeFromTheCodeTables() throws Exception {
        Answer a = get("/api/v1/repair/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/causes/1/description").asText()).isEqualTo("BROKEN");
        assertThat(a.body().at("/dispositions/0/code").asText()).isEqualTo("R");
        assertThat(a.body().get("yards").toString()).contains("HY");
        assertThat(a.body().get("systemChangePrompt").asText()).isEqualTo(TEXTS.prompt("VHRV04I", "msgSysSubSysChg"));
    }

    @Test
    void anIncidentListsItsRepairs() throws Exception {
        Answer a = get("/api/v1/repair/incidents/" + INCD, Map.of("screen", "VHRV01I"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("car").asText()).isEqualTo("1101");
        assertThat(a.body().get("consist").asText()).isEqualTo("201-03");
        assertThat(a.body().get("nextDocument").asInt()).isEqualTo(2);
        assertThat(a.body().at("/repairs/0/narrative").asText()).isEqualTo("DOOR ADJUSTED");
        assertThat(a.body().at("/defaults/time").asText()).isEqualTo("1015");
        Answer d = get("/api/v1/repair/incidents/" + INCD + "/repairs/1", null);
        assertThat(d.body().get("system").asText()).isEqualTo("DR");
        assertThat(d.body().at("/narratives/0/text").asText()).isEqualTo("DOOR ADJUSTED");
    }

    @Test
    void closedAndUnknownIncidentsKeepTheOldMessages() throws Exception {
        backend().on("VHINC2D", in -> reply(0, 1, Map.of("VHINC2D_DATA", Map.of("carNo", "1101", "vehIncdStat", "CLO", "incdType", "REG"))));
        assertThat(get("/api/v1/repair/incidents/" + INCD, null).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHRV01IS-LIST", PKG, "FINAL-ERROR-MSG", "Closed"));
        backend().on("VHINC2D", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        assertThat(get("/api/v1/repair/incidents/" + INCD, null).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHRV01IS-LIST", PKG, "FINAL-ERROR-MSG", "not found"));
    }

    static Map<String, Object> repair(String loc, String narr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cause", "A");
        m.put("system", "DR");
        m.put("subSystem", "01");
        m.put("disposition", "R");
        m.put("date", "10-05-2026");
        m.put("time", "1415");
        m.put("location", loc);
        m.put("employee", "12345");
        m.put("warranty", "N");
        m.put("laborHours", 2);
        m.put("partControl", "123456");
        m.put("serialOn", "ON1");
        m.put("serialOff", "OFF1");
        m.put("narrative", narr);
        return m;
    }

    @Test
    void addingARepairSendsTheRepairToMaris() throws Exception {
        Answer a = post("/api/v1/repair/incidents/" + INCD + "/repairs", repair("HY", "REPLACED DOOR MOTOR"));
        assertThat(a.status()).as(a.body().toString()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHRV01IS-ADD-REPR", PKG, "FINAL-ERROR-MSG", null));
        Map<String, Object> d = written.get().get("VHRV01B_DATA");
        assertThat(d).containsEntry("reprDocNo", 2L).containsEntry("reprLoc", "HY").containsEntry("timeRepr", "14:15:00")
                .containsEntry("userGrp", "BRIGHTCO").containsEntry("narr", "REPLACED DOOR MOTOR");
        // 123456 gets the P in front and its check letter: 123456 mod 13 = 8, so B..M gives I
        assertThat(String.valueOf(d.get("vehPrtCtlNo")).strip()).isEqualTo("P123456I");
    }

    @Test
    void aRefusedRepairListsEveryProblemLikeTheOldScreen() throws Exception {
        Answer a = post("/api/v1/repair/incidents/" + INCD + "/repairs", repair("XX", ""));
        assertThat(a.status()).isEqualTo(400);
        String yard = TEXTS.assigned("VHRV01IS-VALIDATE", PKG, "ERROR-MESSAGE", "Yard"), narr = TEXTS.assigned("VHRV01IS-VALIDATE", PKG, "ERROR-MESSAGE", "Narr");
        assertThat(a.body().get("detail").asText()).isEqualTo(yard.replace(".", "") + ", " + narr.replace(".", ""));
        assertThat(called()).doesNotContain("VHRV01B");
    }

    @Test
    void addingAComponentSendsItAndListsAgain() throws Exception {
        Answer a = post("/api/v1/repair/incidents/" + INCD + "/repairs/1/components", Map.of("component", "MOT", "type", "02", "fault", "",
                "disposition", "R", "serialOn", "S1", "serialOff", "S2"));
        assertThat(a.status()).as(a.body().toString()).isEqualTo(200);
        assertThat(written.get().get("VHRV02B_DATA")).containsEntry("cmpntDocNo", 2L).containsEntry("cmpntCd", "MOT").containsEntry("dateRepr", "10-02-2026");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHRV02IS-RET-DATA", PKG, "FINAL-MESSAGE").get(0));
        assertThat(a.body().get("components").size()).isEqualTo(1);
    }
}
