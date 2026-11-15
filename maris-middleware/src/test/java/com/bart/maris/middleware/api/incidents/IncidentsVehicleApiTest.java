package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The vehicle incident screens over HTTP: filing (VHINC08I), changing (VHINC09I) and maintaining (VHINC10I). */
class IncidentsVehicleApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Map<String, Object>>> written = new AtomicReference<>();

    private void createBackend() {
        backend().on("VHCI02G", in -> reply(0, 1, Map.of("VHCI02G_CRIT", Map.of("currentDate", "11-14-2026"))));
        backend().on("VHINCXA", in -> reply(0, 2, Map.of("VHINCXA_DATA", Map.of("discvDurCount", 2, "dataRow", List.of(Map.of("discvDur", "2", "discvDurDesc", "IN YARD"),
                Map.of("discvDur", "3", "discvDurDesc", "IN SHOP"))))));
        backend().on("VH01A02", in -> reply(100, 0, Map.of()));
        backend().on("VHINC8E", in -> reply(0, 1, Map.of("VHINC8E_DATA", Map.of("daysDiff", 1, "timeDiff", 0))));
        backend().on("VHINC1A", in -> reply(0, 1, Map.of("VHINC1A_CRIT", Map.of("vagenCount", 1))));
        backend().on("VHINC6A", in -> reply(0, 1, Map.of("VHINC6A_DATA", Map.of("whereIsCar", "YRD", "carType", Map.of("carTypeOccr", List.of("A", "", "", ""))))));
        backend().on("VHINXA", in -> reply(100, 0, Map.of()));
        backend().on("VHINVB", in -> reply(0, 1, Map.of("VHINVB_DATA", Map.of("revenueDate", "2026-11-13"))));
        backend().on("VHINCY", in -> { written.set(in); return reply(0, 1, Map.of("VHINCY_DATA", Map.of("incdNo", "A0012345"))); });
        backend().on("VHINXC", in -> reply(0, 1, Map.of()));
        backend().on("VHINC3A", in -> reply(0, 1, Map.of()));
    }

    @Test
    void optionsGiveTheTitleDateAndDurations() throws Exception {
        createBackend();
        Answer a = get("/api/v1/incidents/vehicle-incident/options", Map.of("type", "YRD"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("title").asText()).isEqualTo(TEXTS.assignedAll("VHINC08IS-ENTRY", "MRSVehicleIncident", "GUI-TITLE").get(0));
        assertThat(a.body().get("date").asText()).isEqualTo("11-14-2026");
        assertThat(a.body().at("/durations/1/description").asText()).isEqualTo("IN SHOP");
        assertThat(get("/api/v1/incidents/vehicle-incident/options", Map.of("type", "XYZ")).status()).isEqualTo(400);
    }

    @Test
    void filingWritesTheIncidentUnderTheSignedOnGroup() throws Exception {
        createBackend();
        Answer a = post("/api/v1/incidents/vehicle-incident", Map.of("type", "YRD", "car", "1101", "symptom", "0120", "duration", "2", "date", "11-14-2026",
                "time", "0915", "location", "CY01", "narrative", "DOOR STUCK"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("incident").asText()).isEqualTo("A0012345");
        assertThat(a.body().get("message").asText()).contains("A0012345").contains("1101");
        Map<String, Object> d = written.get().get("VHINCY_DATA");
        assertThat(d.get("userGrp")).isEqualTo("BRIGHTCO");
        assertThat(d.get("dateIncdOccr")).isEqualTo("2026-11-14");
        assertThat(d.get("timeIncdOccr")).isEqualTo("09:15:00");
        assertThat(d.get("narr")).isEqualTo("DOOR STUCK");
        assertThat(called()).containsSubsequence("VHINC8E", "VHINC1A", "VHINC6A", "VHINXA", "VHINVB", "VHINCY", "VHINXC", "VHINC3A");
    }

    @Test
    void aFailedCheckWritesNothing() throws Exception {
        createBackend();
        Answer a = post("/api/v1/incidents/vehicle-incident", Map.of("type", "YRD", "car", "", "symptom", "", "duration", "", "date", "11-14-2026", "time", "",
                "location", "", "narrative", ""));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).startsWith(TEXTS.assignedAll("VHINC08IS-BLD-MSG2", "MRSVehicleIncident", "ERROR-MESSAGE-OCCR").get(0).replace(".", ""));
        assertThat(called()).doesNotContain("VHINCY", "VHINXC", "VHINC3A", "VHINXB");
    }

    @Test
    void aGroupWithoutTheRevenueFixPartMayNotFile() throws Exception {
        createBackend();
        backend().on("VH01A02", in -> reply(0, 1, Map.of("VH01A02_DATA", Map.of("userGroupAccess", List.of(Map.of("partName", "pbtRevenueFixIncident", "accessAllow", "N"))))));
        Answer a = post("/api/v1/incidents/vehicle-incident", Map.of("type", "REV", "car", "1101", "symptom", "0120", "duration", "1", "date", "11-14-2026",
                "time", "0915", "location", "CY01", "narrative", "X"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VH001IS-MESSAGEX", "MRSVehicleIncident", "ERROR-MESSAGEX", null));
        assertThat(called()).doesNotContain("VHINCY");
    }

    private void incidentBackend(String type, String status) {
        backend().on("VHINC1D", in -> reply(0, 1, Map.of("VHINC1D_DATA", Map.of("carNo", "1101", "conNo", "201", "dashNo", 3, "incdType", type, "vehIncdStat", status,
                "sympCd", "0120", "incdLoc", "CY01", "dateIncdOccr", "11-10-2026", "timeIncdOccr", "09.15.00",
                "dataNarrRow", List.of(Map.of("narrNo", 1, "userGrp", "SHOPCY", "dateNarr", "11-10-2026", "timeNarr", "09.20.00", "narr", "DOOR STUCK"))))));
        backend().on("VHINC9A", in -> reply(0, 1, Map.of("VHINC9A_DATA", Map.of("dispDataRow", "1101 1102"))));
        backend().on("VHINC1A", in -> reply(0, 1, Map.of("VHINC1A_CRIT", Map.of("vagenCount", 1))));
        backend().on("VHINC1C", in -> { written.set(in); return reply(0, 1, Map.of()); });
        backend().on("VHINCXC", in -> { written.set(in); return reply(0, 1, Map.of()); });
    }

    @Test
    void anIncidentIsListedAndChanged() throws Exception {
        incidentBackend("YRD", "OPN");
        Answer a = get("/api/v1/incidents/vehicle-update/A0012345", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/incident/consist").asText()).isEqualTo("201-03");
        assertThat(a.body().at("/incident/carsInConsist").asText()).isEqualTo("1101 1102");
        assertThat(a.body().at("/incident/narratives/0/text").asText()).isEqualTo("DOOR STUCK");
        assertThat(a.body().at("/editing/details").asBoolean()).isTrue();
        Answer u = post("/api/v1/incidents/vehicle-update/A0012345", Map.of("symptom", "0130", "location", "CY02", "narrative", "DOOR STUCK", "narrativeIndex", 0));
        assertThat(u.status()).isEqualTo(200);
        assertThat(u.body().get("message").asText()).contains("A0012345");
        assertThat(written.get().get("VHINC1C_DATA")).containsEntry("sympCd", "0130").containsEntry("incdLoc", "CY02").containsEntry("userGrp", "SHOPCY");
        assertThat(written.get().get("VHINC1C_CRIT")).containsEntry("narrChg", "N");
    }

    @Test
    void anotherIncidentTypeCannotBeChanged() throws Exception {
        incidentBackend("TRN", "OPN");
        Answer a = get("/api/v1/incidents/vehicle-update/B0012345", null);
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).contains("TRN");
        assertThat(post("/api/v1/incidents/vehicle-update/B0012345", Map.of("symptom", "0130", "location", "CY02")).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHINC1C");
    }

    @Test
    void maintenanceListsStatusesAndRewritesTheIncident() throws Exception {
        incidentBackend("SHP", "DEF");
        backend().on("VH01A01", in -> reply(0, 1, Map.of("VH01A01_DATA", Map.of("numVehIncdStat", 2, "vehIncdStatRow", List.of(
                Map.of("vehIncdStat", "OPN", "vehIncdStatDesc", "OPEN"), Map.of("vehIncdStat", "CLO", "vehIncdStatDesc", "CLOSED"))))));
        backend().on("VHXREVA", in -> reply(0, 1, Map.of("VHXREVA_DATA", Map.of("revenueDate", "11-13-2026"))));
        Answer o = get("/api/v1/incidents/vehicle-maintenance/options", null);
        assertThat(o.body().at("/statuses/1/code").asText()).isEqualTo("CLO");
        assertThat(o.body().get("revenueDate").asText()).isEqualTo("11-13-2026");
        Answer l = get("/api/v1/incidents/vehicle-maintenance/A0012345", null);
        assertThat(l.body().get("status").asText()).isEqualTo("DEF");
        Answer u = post("/api/v1/incidents/vehicle-maintenance/A0012345", Map.of("status", "CLO", "type", "SHP", "symptom", "0120", "duration", "2", "location", "CY01",
                "cumulativeHours", 12, "cumulativeMinutes", 5, "narrative", "DOOR FIXED", "narrativeIndex", 0));
        assertThat(u.status()).isEqualTo(200);
        assertThat(u.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHINC10I", "UPDATE-MSG"));
        assertThat(written.get().get("VHINCXC_DATA")).containsEntry("vehIncdStat", "CLO").containsEntry("cumMin", 5L);
        assertThat(written.get().get("VHINCXC_CRIT")).containsEntry("narrChg", "Y");
        Answer bad = post("/api/v1/incidents/vehicle-maintenance/A0012345", Map.of("status", "", "type", "SHP"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHINC10I", "STATUS-ERR"));
    }

    @Test
    void narrativesAreReadAgain() throws Exception {
        incidentBackend("YRD", "OPN");
        Answer a = get("/api/v1/incidents/vehicle-incident/A0012345/narratives", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get(0).get("userGroup").asText()).isEqualTo("SHOPCY");
        assertThat(a.body().get(0).get("time").asText()).isEqualTo("09:20:00");
    }
}
