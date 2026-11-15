package com.bart.maris.middleware.api.incidentevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The event and incident log over HTTP. */
class IncidentEventsApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleIncdEvent";
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    @Test
    void logListsTheIncidents() throws Exception {
        backend().on("VHIE01A", in -> { crit.set(in.get("VHIE01A_CRIT")); return reply(0, 1, Map.of("VHIE01A_DATA", rows("dataRow", List.of(Map.of(
                "dateIncdOccrRev", "2026-11-10", "incdNo", "29990010", "carNo", "4377", "vehIncdStat", "OPN", "timeIncdOccr", Map.of("timeHhmm", "07:15"),
                "conNo2", Map.of("con", "197", "dash", "-", "conDashNo", 3), "narr", "BRAKES DRAGGING"))))); });
        Answer a = get("/api/v1/incident-events/incidents", Map.of("from", "2026-11-01", "to", "2026-11-12"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get()).containsEntry("fromDate", "2026-11-01").containsEntry("toDate", "2026-11-12");
        assertThat(a.body().at("/incidents/0").toString()).isEqualTo("{\"revenueDate\":\"2026-11-10\",\"incident\":\"29990010\",\"car\":\"4377\","
                + "\"status\":\"OPN\",\"symptom\":\"\",\"time\":\"07:15\",\"speed\":\"\",\"direction\":\"\",\"location\":\"\",\"consist\":\"197-03\",\"narrative\":\"BRAKES DRAGGING\"}");
    }

    @Test
    void theDateChecksKeepTheOldOrderAndMessages() throws Exception {
        String future = LocalDate.now().plusDays(3).toString();
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", "2026-11-01", "to", future)).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Future TO"));
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", future, "to", future)).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Future FROM"));
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", "2026-11-05", "to", "2026-11-01")).body().get("detail").asText())
                .isEqualTo(TEXTS.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Less Than"));
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", "13/45/2026", "to", "2026-11-01")).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHIE01A");
    }

    @Test
    void noIncidentsAndTooManyKeepTheOldMessages() throws Exception {
        backend().on("VHIE01A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", "2020-01-01", "to", "2020-01-02")).body().get("message").asText())
                .isEqualTo(TEXTS.assigned("VHIE01IS-GET-DATA", PKG, "MESSAGE", "No Data"));
        backend().on("VHIE01A", in -> reply(0, 0, Map.of("VHIE01A_CRIT", Map.of("exceedFlag", "Y"))));
        assertThat(get("/api/v1/incident-events/incidents", Map.of("from", "2020-01-01", "to", "2020-12-31")).body().get("message").asText())
                .isEqualTo(TEXTS.assigned("VHIE01IS-GET-DATA", PKG, "MESSAGE", "Exceeded"));
    }

    @Test
    void printSendsTheLogReportToThePrinter() throws Exception {
        backend().on("VHIE02A", in -> { crit.set(in.get("VHIE02A_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/incident-events/incidents/print", Map.of("from", "2026-11-01", "to", "2026-11-12", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHIE02I", "SUBMIT"));
        assertThat(crit.get()).containsEntry("reptType", "INCLOG").containsEntry("reptCd", "DUBLNHP1")
                .containsEntry("dateReptFrom", "11-01-2026").containsEntry("dateReptTo", "11-12-2026");
        assertThat(post("/api/v1/incident-events/incidents/print", Map.of("from", "2026-11-01", "to", "2026-11-12")).status()).isEqualTo(400);
    }

    @Test
    void consistLookUpPadsShortCarNumbersAndReportsNotFound() throws Exception {
        backend().on("VHIE03H", in -> { crit.set(in.get("VHIE03H_DATA")); return reply(0, 1, Map.of("VHIE03H_DATA", Map.of("conNo", "532", "dashNo", Map.of("dashNo1", "00", "dashNo2", "01")))); });
        Answer a = get("/api/v1/incident-events/cars/123/consist", null);
        assertThat(crit.get()).containsEntry("carNo", "0123");
        assertThat(a.body().get("consist").asText()).isEqualTo("532-01");
        backend().on("VHIE03H", in -> reply(100, 0, Map.of()));
        assertThat(get("/api/v1/incident-events/cars/9999/consist", null).body().get("message").asText())
                .isEqualTo(TEXTS.assigned("VHIE06-CAR-NO", PKG, "MSG", "No Consit"));
    }

    @Test
    void consistCarsListTheCarsInOrder() throws Exception {
        backend().on("VHIE06A", in -> { crit.set(in.get("VHIE06A_CRIT")); return reply(0, 2, Map.of("VHIE06A_DATA", rows("dataRow", List.of(
                Map.of("carFormat", " 3198}", "dashNo", Map.of("dashNo1", "00", "dashNo2", "01")), Map.of("carFormat", "{4074 ", "dashNo", Map.of("dashNo1", "00", "dashNo2", "01")))))); });
        Answer a = get("/api/v1/incident-events/consists/532-01/cars", null);
        assertThat(crit.get()).containsEntry("conNo", "532").containsEntry("flag", "1");
        assertThat(a.body().get("cars").toString()).isEqualTo("[\" 3198}\",\"{4074\"]");
        backend().on("VHIE06A", in -> reply(100, 0, Map.of()));
        assertThat(get("/api/v1/incident-events/consists/999/cars", null).body().get("message").asText())
                .isEqualTo(TEXTS.assigned("VHIE06IS-CON", PKG, "MSG", "No Cars"));
    }
}
