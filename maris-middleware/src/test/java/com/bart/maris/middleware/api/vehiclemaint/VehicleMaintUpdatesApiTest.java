package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The vehicle maintenance update screens over HTTP: car hours (VHVM08I), consist status (VHVM10I), consist history (VHVM19I). */
class VehicleMaintUpdatesApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String VM = "/api/v1/vehicle-maintenance";
    private final AtomicReference<Map<String, Map<String, Object>>> sent = new AtomicReference<>();

    private void car(int urc) {
        backend().on("VHVM1A5", in -> reply(urc, 1, Map.of("VHVM1A5_DATA", Map.of("carNo", "1101", "currLoc", "HY01", "cumHrs", 51234, "hrsToNextPm", 40,
                "hrsFromLastPm", 210, "whereIsCar", "YRD"))));
    }

    @Test
    void carHoursShowTheCar() throws Exception {
        car(0);
        Answer a = get(VM + "/car-hours/1101", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("location").asText()).isEqualTo("HY01");
        assertThat(a.body().get("hoursToNextPm").asLong()).isEqualTo(40);
        car(100);
        Answer missing = get(VM + "/car-hours/9999", null);
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM08I", "BAD-CAR"));
    }

    @Test
    void testHoursMoveThePmHours() throws Exception {
        car(0);
        backend().on("VHVM8A1", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Answer a = post(VM + "/car-hours/1101/test-hours", Map.of("testHours", "25"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM08I", "UPDATED"));
        assertThat(sent.get().get("VHVM8A1_CRIT")).containsEntry("carNo", "1101");
        assertThat(sent.get().get("VHVM8A1_DATA")).containsEntry("cumHrs", 51234L).containsEntry("hrsToNextPm", 15L).containsEntry("hrsFromLastPm", 235L)
                .containsEntry("termLastUpd", "MARIS-UI");
        backend().reset();
        Answer bad = post(VM + "/car-hours/1101/test-hours", Map.of("testHours", "1000"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHVM8A1");
    }

    private static List<Map<String, Object>> consists(int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("conNo", String.valueOf(201 + i % 700));
            r.put("conStat", "SRV");
            r.put("dashNo", 1);
            r.put("dateInsrv", "2026-11-10");
            r.put("dateInsrvRev", "2026-11-09");
            r.put("timeInsrv", "05.00.00");
            out.add(r);
        }
        return out;
    }

    @Test
    void consistStatusListsAndUpdates() throws Exception {
        Answer o = get(VM + "/consist-status/options", null);
        assertThat(o.body().get("statuses").toString()).isEqualTo(json.writeValueAsString(TEXTS.items("VHVM10I", "Drop-down_List2")));
        assertThat(o.body().get("columns").get(0).get("field").asText()).isEqualTo("conNo");

        backend().on("VHVM10A", in -> { sent.set(in); return reply(0, 2, Map.of("VHVM10A_DATA", rows("dataRow", consists(2)))); });
        Answer list = get(VM + "/consist-status", Map.of("from", "201"));
        assertThat(sent.get().get("VHVM10A_CRIT")).containsEntry("conNoBeg", "201").containsEntry("conNoEnd", "201");
        assertThat(list.body().get("rows").size()).isEqualTo(2);
        assertThat(list.body().get("message").asText()).isEmpty();
        backend().on("VHVM10A", in -> reply(0, 182, Map.of("VHVM10A_DATA", rows("dataRow", consists(182)))));
        assertThat(get(VM + "/consist-status", Map.of("from", "201", "to", "999")).body().get("message").asText()).isEqualTo(TEXTS.label("VHVM10I", "Label3"));

        backend().on("VHVM10C", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Map<String, Object> change = new HashMap<>(Map.of("conNo", "201", "status", "OUT", "dateOutsrv", "", "dateInsrvRev", "2026-11-09", "noCarsInd", "N"));
        Answer u = post(VM + "/consist-status/update", change);
        assertThat(u.status()).isEqualTo(200);
        assertThat(u.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM10I", "UPDATED"));
        assertThat(sent.get().get("VHVM10C_DATA")).containsEntry("conStat", "OUT").containsEntry("dateOutsrv", "2026-11-09").containsEntry("termMaintUpd", "MARIS-UI");
        assertThat(sent.get().get("XDCONTROL")).containsEntry("ufunction", "UPDATE");

        backend().reset();
        Answer missing = post(VM + "/consist-status/update", Map.of("conNo", "201", "status", ""));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM10I", "DATA-ERROR"));
        assertThat(called()).doesNotContain("VHVM10C");
        backend().on("VHVM10C", in -> reply(8, 0, Map.of()));
        assertThat(post(VM + "/consist-status/update", change).status()).isEqualTo(422);
    }

    @Test
    void consistHistoryListsAndUpdates() throws Exception {
        backend().on("VHYT34A", in -> reply(0, 1, Map.of("VHYT34A_DATA", Map.of("timeRevEnd", "02.30.00", "currentDate", "2026-11-14", "currentTime", "01.15.00",
                "yesterday", "2026-11-13"))));
        Answer o = get(VM + "/consist-history/options", null);
        assertThat(o.body().get("revDate").asText()).isEqualTo("11-13-2026");             // before the revenue day's end: yesterday

        backend().on("VHVM19A", in -> { sent.set(in); return reply(0, 2, Map.of("VHVM19A_DATA", rows("dataRow", consists(2)))); });
        Answer list = get(VM + "/consist-history", Map.of("from", "201", "to", "205", "revDate", "11-13-2026"));
        assertThat(sent.get().get("VHVM19A_CRIT")).containsEntry("conNoBeg", "201").containsEntry("conNoEnd", "205").containsEntry("revDate", "2026-11-13");
        assertThat(list.body().get("rows").size()).isEqualTo(2);
        backend().on("VHVM19A", in -> reply(100, 0, Map.of()));
        Answer none = get(VM + "/consist-history", Map.of("from", "999"));
        assertThat(none.body().get("rows").size()).isZero();
        assertThat(none.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHVM19IS-RET-DATA", "MRSVehicleMaintenance", "MSG").get(0));

        backend().on("VHVM19B", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Map<String, Object> change = new HashMap<>(Map.of("conNo", "201", "dashNo", 1, "dateInsrv", "11-10-2026", "timeInsrv", "05.00.00",
                "dateInsrvRev", "2026-11-09", "status", "CON", "dateInsrvIcs", "11-12-2026", "timeInsrvIcs", "05.10.00", "noCarsInd", "N"));
        Answer u = post(VM + "/consist-history/update", change);
        assertThat(u.status()).isEqualTo(200);
        assertThat(u.body().get("message").asText()).isEqualTo(TEXTS.assignedAll("VHVM19IS-MOD-DATA", "MRSVehicleMaintenance", "MSG").get(0));
        assertThat(sent.get().get("VHVM19B_CRIT")).containsEntry("dateInsrv", "2026-11-10").containsEntry("dateInsrvRev", "2026-11-09")
                .containsEntry("termMaintUpd", "MARIS-UI").containsEntry("dashNo", 1L);
        assertThat(sent.get().get("VHVM19B_DATA")).containsEntry("conStat", "CON").containsEntry("dateInsrvIcs", "2026-11-12");
        backend().reset();
        change.put("status", "");
        assertThat(post(VM + "/consist-history/update", change).status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHVM19B");
    }
}
