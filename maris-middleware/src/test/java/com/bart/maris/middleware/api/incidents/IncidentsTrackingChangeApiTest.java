package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Vehicle tracking change incident (VHCI02I) over HTTP: options, List Incds and Change. */
class IncidentsTrackingChangeApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String BASE = "/api/v1/incidents/tracking-change";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private static Map<String, Object> row(String incd) {
        return Map.of("incdNo", incd, "eventTypeX", Map.of("eventType", "AWP", "eventSubType", "1"), "vehIncdStat", "OPN", "incdType", "SHP", "sympCd", "0120",
                "change", "", "dateIncdOccr", "10-02-2026");
    }

    private void car(String vehStat) {
        backend().on("VHCI02G", in -> reply(0, 1, Map.of("VHCI02G_CRIT", Map.of("currentDate", LocalDate.now().format(MDY), "currentTime", "23:59:00"))));
        backend().on("VHCI02B", in -> reply(0, 1, Map.of()));
        backend().on("VHINC6A", in -> reply(0, 1, Map.of("VHINC6A_DATA", Map.of("whereIsCar", "YRD"))));
        backend().on("VHCI01B", in -> reply(0, 1, Map.of("VHCI01B_DATA", Map.of("whereIsCar", "YRD", "dispVehStat", Map.of("vehStat", vehStat, "vehStatDesc", "STATUS"),
                "carNo", "1101", "carType", Map.of("carType1", "C", "carTypeRem", "2"), "worstCaseRank", 10, "trainIncdInd", "N"))));
        backend().on("VHCI01D", in -> reply(0, 2, Map.of("VHCI01D_DATA", rows("dataRow", List.of(row("S0000101"), row("S0000102"))))));
    }

    private static Map<String, Object> list(int action, String status, String car) {
        Map<String, Object> r = new HashMap<>();
        r.put("action", action);
        r.put("status", status);
        r.put("car", car);
        return r;
    }

    @Test
    void optionsGiveTheScreensChoices() throws Exception {
        car("NO GO");
        Answer a = get(BASE + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("actions").size()).isEqualTo(TEXTS.items("VHCI02I", "rbtActions").size());
        assertThat(a.body().get("statuses").get(0).asText()).isEqualTo(TEXTS.items("VHCI02I", "ddlStatus").get(0));
        assertThat(a.body().get("time").asText()).isEqualTo("2359");
        assertThat(a.body().get("canChangeLocation").asBoolean()).isTrue();
    }

    @Test
    void listGivesTheIncidentsToMark() throws Exception {
        car("NO GO");
        Answer a = post(BASE + "/incidents", list(1, "GO", "1101"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("rows").size()).isEqualTo(2);
        assertThat(a.body().get("rows").get(1).get("incident").asText()).isEqualTo("S0000102");
        assertThat(a.body().get("changeEnabled").asBoolean()).isTrue();
        assertThat(a.body().get("currentStatus").asText()).startsWith("NO GO");
    }

    @Test
    void aZeroCarGivesTheOldMessage() throws Exception {
        car("NO GO");
        Answer a = post(BASE + "/incidents", list(1, "GO", "0000"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHCI01IS-LIST", "MRSVehicleIncident", "FINAL-ERROR-MESSAGE").get(0));
        assertThat(a.body().get("changeEnabled").asBoolean()).isFalse();
        assertThat(called()).doesNotContain("VHINC6A");
    }

    @Test
    void changeOnADisabledListIsRefused() throws Exception {
        car("GO");                                                             // GO on a GO car: the old screen disables Change
        Map<String, Object> r = new LinkedHashMap<>(list(1, "GO", "1101"));
        r.put("changes", Map.of("S0000101", "Y"));
        r.put("date", LocalDate.now().minusDays(1).format(MDY));
        r.put("time", "0930");
        Answer a = post(BASE + "/changes", r);
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHCIY", "AFCXVLD");
    }

    @SuppressWarnings("unchecked")
    @Test
    void changeAppliesTheActionToTheMarkedIncidents() throws Exception {
        car("NO GO");
        backend().on("VHCI01F", in -> reply(0, 0, Map.of("VHCI01F_CRIT", Map.of("validSw", "Y"))));
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHINVB", in -> reply(0, 0, Map.of()));
        backend().on("VHCI01E", in -> reply(0, 0, Map.of()));
        backend().on("VHCI02D", in -> reply(0, 0, Map.of("VHCI02D_CRIT", Map.of("updTrk", "N"))));
        backend().on("VHINXD", in -> reply(0, 0, Map.of()));
        backend().on("VHCIY", in -> {
            Map<String, Object> t = new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) in.get("VHCI01IW-TEMP-MI"));
            assertThat(((List<Map<String, Object>>) t.get("dataRow")).get(0).get("incdNo").toString()).contains("0000102");   // only the marked one
            t.put("errFound", "N");
            t.put("statusUpd", "Y");
            t.put("newVehStat", "GO");
            t.put("finalIncdCount", 0);
            return reply(0, 0, Map.of("VHCI01IW-TEMP-MI", t));
        });
        Map<String, Object> r = new LinkedHashMap<>(list(1, "GO", "1101"));
        r.put("changes", Map.of("S0000102", "y"));
        r.put("date", LocalDate.now().minusDays(1).format(MDY));
        r.put("time", "0930");
        r.put("narrative", "FIXED");
        Answer a = post(BASE + "/changes", r);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).contains("VHCIY", "VHCI01E", "VHCI02D");
        assertThat(a.body().get("message").asText()).isNotBlank();
    }
}
