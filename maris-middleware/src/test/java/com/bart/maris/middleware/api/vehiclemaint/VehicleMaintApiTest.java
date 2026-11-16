package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Vehicle maintenance read-only screens over HTTP. */
class VehicleMaintApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    @Test
    void narrativeListsTheLines() throws Exception {
        backend().on("VHNARX", in -> { crit.set(in.get("VHNARX_DATA")); return reply(0, 2, Map.of("VHNARX_DATA", rows("dataRow", List.of(Map.of("narr", "LINE 1"), Map.of("narr", "LINE 2"))))); });
        Answer a = get("/api/v1/vehicle-maintenance/incidents/29990010/narrative", null);
        assertThat(crit.get()).containsEntry("critIncdNo", "29990010");
        assertThat(a.body().get("lines").toString()).isEqualTo("[\"LINE 1\",\"LINE 2\"]");
        assertThat(get("/api/v1/vehicle-maintenance/incidents/123456789/narrative", null).status()).isEqualTo(400);
    }

    @Test
    void optionsListTheEventTypesFromVhvm13b() throws Exception {
        backend().on("WS70A01", in -> reply(0, 0, Map.of()));
        backend().on("VHVM13B", in -> reply(0, 2, Map.of("VHVM13B_DATA", rows("dataRow", List.of(Map.of("eventType", "CE"), Map.of("eventType", "BAT"))))));
        Answer a = get("/api/v1/vehicle-maintenance/reports/options", null);
        assertThat(a.body().get("eventTypes").toString()).isEqualTo("[\"CE\",\"BAT\"]");
        assertThat(called()).doesNotContain("VHVM13C");
        assertThat(a.body().get("carTypes").get(0).asText()).isEqualTo("ALL");
        assertThat(a.body().get("homeYards").toString()).isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(TEXTS.items("VHVM13i", "Drop-down_List4")));
        backend().on("VHVM13B", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", -805)), "00000000"));
        assertThat(get("/api/v1/vehicle-maintenance/reports/options", null).body().get("eventTypes").size()).isZero();
    }

    @Test
    void eventReportSendsTheCriteriaAndPrinter() throws Exception {
        backend().on("VHVM12A", in -> { crit.set(in.get("VHVM12A_CRIT")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-maintenance/reports/events/print", Map.of("dateFrom", "2026-11-01", "dateTo", "2026-11-12", "car", "1101",
                "carType", "c2", "eventType", "CE", "printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.prompt("VHVM12I", "Message_Prompter1"));
        assertThat(crit.get()).containsEntry("dateFrom", "2026-11-01").containsEntry("begCarType", "C2").containsEntry("endCarType", "C2")
                .containsEntry("vtamName", "DUBLNHP1");
    }

    @Test
    void carEventReportNeedsCarTypeDateReferenceAndComments() throws Exception {
        Answer a = post("/api/v1/vehicle-maintenance/reports/car-events/print", Map.of("printer", "DUBLNHP1"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(String.join("\n", TEXTS.triggeredPrompt("VHVM13i", "MISSING-CAR-TYPE"),
                TEXTS.triggeredPrompt("VHVM13i", "MISSING-DATE"), TEXTS.triggeredPrompt("VHVM13i", "MISSING-REF-NO"), TEXTS.triggeredPrompt("VHVM13i", "MISSING-COMMENTS")));
        assertThat(called()).doesNotContain("VHVM13A");
        backend().on("VHVM13A", in -> { crit.set(in.get("VHVM13A_CRIT")); return reply(0, 0, Map.of()); });
        Answer ok = post("/api/v1/vehicle-maintenance/reports/car-events/print", Map.of("carType", "C2", "date", "2026-11-10", "trackingRefNo", "R1",
                "comments", "CHECK", "printer", "DUBLNHP1"));
        assertThat(ok.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM13i", "VALID"));
        assertThat(crit.get()).containsEntry("dateFrom", "2026-11-10").containsEntry("comments", "CHECK");
    }
}
