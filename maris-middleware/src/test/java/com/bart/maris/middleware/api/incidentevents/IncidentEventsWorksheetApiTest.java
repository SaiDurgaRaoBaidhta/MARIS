package com.bart.maris.middleware.api.incidentevents;

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

/** The vehicle desk work sheet (old VHIE03I / VHIE04I) and the symptom history (VHIE07I) over HTTP. */
class IncidentEventsWorksheetApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleIncdEvent";
    private static final String BASE = "/api/v1/incident-events/worksheet";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private void lists() {
        backend().on("VHIE01B", in -> reply(0, 0, Map.of("VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentTime", "10:00:00", "currentDate", "2026-11-12",
                "yesterday", "2026-11-11"))));
        backend().on("VHIE04D", in -> reply(0, 1, Map.of("VHIE04D_DATA", rows("dataRow", List.of(Map.of("sympCd", "0120", "sympCdNarr", "DOORS WILL NOT CLOSE"))))));
        backend().on("VHIE03B", in -> reply(0, 1, Map.of("VHIE03B_DATA", rows("dataRow", List.of(Map.of("vehTechId", "T1", "vehTechIdDesc", "TECH ONE"))))));
        backend().on("VHIE03C", in -> reply(0, 1, Map.of("VHIE03C_DATA", rows("dataRow", List.of(Map.of("trackingCd", "K1", "trackingDesc", "TRACK ONE"))))));
        backend().on("VHIE03D", in -> reply(0, 1, Map.of("VHIE03D_DATA", rows("dataRow", List.of(Map.of("actionCd", "A1", "actionCdDesc", "ADJUSTED"))))));
        backend().on("VH01A02", in -> reply(0, 1, Map.of("VH01A02_DATA", Map.of("userGroupAccess", List.of(
                Map.of("guiName", "VH001I", "partName", "pbtEventLogCreateIncident", "accessAllow", "N"))))));
        backend().on("VHIE03A", in -> reply(0, 1, Map.of("VHIE03A_DATA", rows("dataRow", List.of(row())))));
        backend().on("VHIE03G", in -> reply(0, 1, Map.of("VHIE03G_DATA", rows("dataRow", List.of(Map.of("carNo", "4377", "whereIsCar", "CON"))))));
        backend().on("VHIE04C", in -> reply(0, 1, Map.of("VHIE04C_CRIT", Map.of("hasCaNarr", "Y"), "VHIE04C_DATA", rows("dataRow", List.of(Map.of("caNarr", "RESET", "narr", "DOOR"))))));
    }

    private static Map<String, Object> row() {
        Map<String, Object> m = new HashMap<>();
        m.put("cntEvents", 1); m.put("carNo", "4377"); m.put("conNo", "197"); m.put("dashNo", Map.of("no1", "00", "no2", "03")); m.put("eventLoc", "CY01");
        m.put("conDirection", "N"); m.put("speed", "35"); m.put("timeEventOccr", "10:15:00"); m.put("sympCd", "0120"); m.put("narr", "DOOR");
        m.put("actionCdDesc", Map.of("actionCdDesc4", "ADJU", "actionCdDesc26", "STED")); m.put("dateEventOccr", "2026-11-12"); m.put("incdNo", "");
        m.put("dateInsrvRev", "2026-11-12"); m.put("dateInsrv", "2026-11-12"); m.put("timeInsrv", "05:00:00"); m.put("binDashNo", 7);
        return m;
    }

    private static Map<String, Object> input(String direction, String tech) {
        Map<String, Object> m = new HashMap<>();
        m.put("car", "4377"); m.put("consist", "197-03"); m.put("location", "CY01"); m.put("direction", direction); m.put("speed", "35");
        m.put("time", "10:15:00"); m.put("symptom", "0120"); m.put("narrative", "DOOR"); m.put("technician", tech);
        return m;
    }

    @Test
    void optionsGiveTheListsTheRevenueDateAndTheCreateAccess() throws Exception {
        lists();
        Answer a = get(BASE + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("revenueDate").asText()).isEqualTo("2026-11-12");
        assertThat(a.body().at("/symptoms/0/code").asText()).isEqualTo("0120");
        assertThat(a.body().at("/technicians/0/description").asText()).isEqualTo("TECH ONE");
        assertThat(a.body().get("createAllowed").asBoolean()).isFalse();
        assertThat(called()).contains("VHIE04D", "VHIE03B", "VHIE03C", "VHIE03D", "VH01A02");
    }

    @Test
    void eventsListTheRevenueDateAndKeepTheOldMessages() throws Exception {
        lists();
        backend().on("VHIE03A", in -> { sent.set(in.get("VHIE03A_CRIT")); return reply(0, 1, Map.of("VHIE03A_DATA", rows("dataRow", List.of(row())))); });
        Answer a = get(BASE + "/events", Map.of("date", "2026-11-12"));
        assertThat(sent.get()).containsEntry("dateEventOccr", "2026-11-12");
        assertThat(a.body().at("/events/0/eventId").asText()).isEqualTo("2026-11-12-01");
        assertThat(a.body().at("/events/0/consist").asText()).isEqualTo("197-03");
        assertThat(a.body().at("/events/0/actionText").asText()).isEqualTo("ADJU");
        backend().on("VHIE03A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        assertThat(get(BASE + "/events", Map.of("date", "2026-11-10")).body().get("message").asText())
                .isEqualTo(TEXTS.assigned("VHIE03IS-GET-DATA", PKG, "MESSAGE", "No Data"));
        assertThat(get(BASE + "/events", Map.of("date", "11/10/2026")).status()).isEqualTo(400);
    }

    @Test
    void updateSendsTheEventAndRejectsBadCodes() throws Exception {
        lists();
        backend().on("VHIE04B", in -> { sent.set(in.get("VHIE04B_DATA")); return reply(0, 0, Map.of()); });
        Answer a = send("PUT", BASE + "/events/2026-11-12/1", input("S", "T1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHIE03IS-UPDATE", PKG, "MESSAGE").stream().filter(s -> s.contains("Updated")).findFirst().orElseThrow());
        assertThat(sent.get()).containsEntry("conDirection", "S").containsEntry("vehTechId", "T1").containsEntry("deleteInd", "N").containsEntry("caNarr", "RESET");
        Answer bad = send("PUT", BASE + "/events/2026-11-12/1", input("Q", "T9"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).contains("Direction").contains("Tech");
        Answer del = send("DELETE", BASE + "/events/2026-11-12/1", input("N", "T1"));
        assertThat(del.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("deleteInd", "Y");
    }

    @Test
    void addChecksTheCarAndConsistAndAddsTheEvent() throws Exception {
        lists();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of("VHINC6A_DATA", Map.of("whereIsCar", "CON", "carType", "A"))));
        backend().on("VHIE03G", in -> reply(0, 1, Map.of("VHIE03G_DATA", rows("dataRow", List.of(Map.of("carNo", "4377", "dashNo", Map.of("dashNo1", "00", "dashNo2", "03"),
                "binDashNo", 3, "timeInsrv", "05:00:00", "dateInsrvRev", "2026-11-12", "dateInsrv", "2026-11-12"))))));
        backend().on("VHIE03E", in -> reply(0, 0, Map.of("VHIE03E_DATA", Map.of("itemCount", 1))));
        backend().on("VHIE04A", in -> { sent.set(in.get("VHIE04A_DATA")); return reply(0, 0, Map.of()); });
        Answer a = post(BASE + "/events", input("N", "T1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHIE03IS-ADD-DATA", PKG, "MESSAGE", "Added"));
        assertThat(sent.get()).containsEntry("dateRevenue", "2026-11-12").containsEntry("userGrp", "BRIGHTCO").containsEntry("binDashNo", 3L);
        Map<String, Object> none = input("N", "");
        none.put("car", ""); none.put("consist", "");
        assertThat(post(BASE + "/events", none).body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHIE03IS-ADD-DATA", PKG, "MESSAGE", "or VALID"));
    }

    @Test
    void creatingAnIncidentNeedsTheGroupsAccess() throws Exception {
        lists();
        Answer a = post(BASE + "/events/2026-11-12/1/incident", input("N", "T1"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHEVLX", "VHEVLY", "VHEVL1F", "VHIE04B");
    }

    @Test
    void symptomHistoryMergesEventsAndIncidents() throws Exception {
        backend().on("VHIE07C", in -> { sent.set(in.get("VHIE07C_CRIT")); return reply(0, 0, Map.of("VHIE07C_DATA", Map.of("currentDate", "2026-11-14", "beginDate", "2026-10-15"))); });
        backend().on("VHIE07A", in -> reply(0, 1, Map.of("VHIE07A_CRIT", Map.of("recCount", 1), "VHIE07A_DATA", rows("dataRow", List.of(Map.of("carNo", "4377", "conNo", "197",
                "dashNo", 3, "dateInsrv", "2026-11-01"))))));
        backend().on("VHIE07B", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        Answer a = get(BASE + "/symptom-history/0120", Map.of("days", "30"));
        assertThat(sent.get()).containsEntry("numDays", 30L);
        assertThat(a.body().at("/rows/0/type").asText()).isEqualTo("E");
        assertThat(a.body().at("/rows/0/dateInService").asText()).isEqualTo("11-01-2026");
        assertThat(a.body().at("/rows/0/consist").asText()).isEqualTo("197-03");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHIE07IS-LOOKUP", PKG, "MSG", "INCIDENTS"));
        assertThat(get(BASE + "/symptom-history/0120", null).body().get("days").asInt()).isEqualTo(90);
    }
}
