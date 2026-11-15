package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Vehicle parts status / mod tracking (VHVM11I) over HTTP. */
class VehicleMaintTrackingApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String BASE = "/api/v1/vehicle-maintenance/tracking-events";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private void dates() {
        backend().on("VHVMDTA", in -> reply(0, 1, Map.of("VHVMDTA_CRIT", Map.of("currentDate", "11-14-2026"))));
    }

    private static Map<String, Object> event(String type, String incident) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("car", "1101");
        e.put("eventType", type);
        e.put("eventSubType", "1");
        e.put("status", "OPEN");
        e.put("dateInitialized", "10-01-2026");
        e.put("timeInitialized", "10.00.00");
        e.put("trackingRefNo", "123456789");
        e.put("dateClosed", "");
        e.put("incident", incident);
        e.put("comment", "OLD");
        e.put("location", "HY-T12");
        e.put("locationCompleted", "HY");
        e.put("homeYard", "HY");
        e.put("dateLastUpdated", "11-01-2026");
        return e;
    }

    @Test
    void optionsListTheBackendChoices() throws Exception {
        dates();
        backend().on("VHVM11E", in -> reply(0, 3, Map.of("VHVM11E_DATA", rows("dataRow", List.of(Map.of("eventType", "ALL"), Map.of("eventType", "AWP"),
                Map.of("eventType", "MOD", "eventSubType", "A", "eventSubTypeDsc", "MODIFICATION"))))));
        backend().on("VHVM11F", in -> reply(0, 2, Map.of("VHVM11F_CRIT", Map.of("beginDate", ""), "VHVM11F_DATA", rows("dataRow",
                List.of(Map.of("eventStat", "ALL"), Map.of("eventStat", "OPEN"))))));
        backend().on("VHVM11G", in -> reply(0, 1, Map.of("VHVM11G_DATA", rows("dataRow", List.of(Map.of("yrd", "HY"))))));
        Answer a = get(BASE + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("entryEvents").get(0).get("label").asText()).isEqualTo("MOD-A MODIFICATION");
        assertThat(a.body().get("entryStatuses").toString()).isEqualTo("[\"OPEN\"]");
        assertThat(a.body().get("dateFrom").asText()).isEqualTo(TEXTS.assignedAll("VHVM11I-BEGIN", "MRSVehicleMaintenance", "DATE-FROM").get(0));
        assertThat(a.body().get("dateTo").asText()).isEqualTo("11-14-2026");
        assertThat(a.body().get("questions").get("remove").asText()).isEqualTo(TEXTS.prompt("VHVM11I", "Message_Prompter1"));
    }

    @Test
    void searchConvertsTheCriteria() throws Exception {
        backend().on("VHVM11A", in -> { sent.set(in.get("VHVM11A_CRIT")); return reply(100, 0, Map.of()); });
        Map<String, Object> c = new HashMap<>();
        c.put("dateFrom", "01-01-2025");
        c.put("dateTo", "11-14-2026");
        c.put("car", "ALL");
        c.put("carType", "ALL");
        c.put("trackingRefNo", "12-34-56789");
        Answer a = post(BASE + "/search", c);
        assertThat(sent.get()).containsEntry("dateFrom", "2025-01-01").containsEntry("carNo", "").containsEntry("begCarType", "A1")
                .containsEntry("endCarType", "C2").containsEntry("trkgRefNo", "123456789");
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHVM11I-GET-DATA", "MRSVehicleMaintenance", "MSG", "No data"));
    }

    @Test
    void updateAnOpenEventWithAClosedDateIsRefused() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("event", event("AWP", ""));
        body.put("dateClosed", "11-02-2026");
        Answer a = post(BASE + "/update", body);
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHVM11C");
    }

    @Test
    void closeAsksThenClosesWithTheTerminal() throws Exception {
        dates();
        backend().on("VHVM11C", in -> { sent.set(in.get("VHVM11C_DATA")); return reply(0, 0, Map.of()); });
        Map<String, Object> body = new HashMap<>();
        body.put("event", event("AWP", ""));
        Answer ask = post(BASE + "/close", body);
        assertThat(ask.body().get("question").asText()).isEqualTo(TEXTS.prompt("VHVM11I", "Message_Prompter2"));
        assertThat(called()).doesNotContain("VHVM11C");
        body.put("answer", true);
        Answer done = post(BASE + "/close", body);
        assertThat(done.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("dateClosed", "2026-11-14").containsEntry("actvInd", "N").containsEntry("locCmpltEvent", "HY-T");
    }

    @Test
    void removeAndAddReportProgramErrors() throws Exception {
        backend().on("VHVM11C", in -> reply(-803, 0, Map.of()));
        Map<String, Object> body = new HashMap<>();
        body.put("event", event("AWP", ""));
        assertThat(post(BASE + "/remove", body).status()).isEqualTo(422);
        Answer missing = post(BASE, Map.of("car", "1101"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHVM11I-ADD", "MRSVehicleMaintenance", "MSG", "Missing"));
    }

    @Test
    void workAssignIsRefusedForAnIIncident() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("event", event("AWP", "I0000001"));
        assertThat(post(BASE + "/work-assign", body).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHVM11K");
    }

    @Test
    void carCheck() throws Exception {
        backend().on("VHVM1A5", in -> reply(100, 0, Map.of()));
        Answer a = get(BASE + "/cars/9999/check", null);
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHVM11I-VALID-CAR", "MRSVehicleMaintenance", "MSG", "Not Exist"));
    }
}
