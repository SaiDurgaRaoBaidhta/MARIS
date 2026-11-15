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

/** Vehicle tracking change incident "never go" (VHCI03I) over HTTP. */
class IncidentsNeverGoApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String BASE = "/api/v1/incidents/tracking-change/never-go";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private void now() {
        backend().on("VHCI02G", in -> reply(0, 1, Map.of("VHCI02G_CRIT", Map.of("currentDate", LocalDate.now().format(MDY), "currentTime", "23:59:00"))));
    }

    private Map<String, Object> request(String car, String narrative) {
        Map<String, Object> r = new HashMap<>();
        r.put("car", car);
        r.put("status", "NVGO");
        r.put("date", LocalDate.now().minusDays(1).format(MDY));
        r.put("time", "0930");
        r.put("narrative", narrative);
        return r;
    }

    @Test
    void optionsListTheStatuses() throws Exception {
        now();
        backend().on("VHCI03E", in -> reply(0, 2, Map.of("VHCI03E_DATA", rows("dataRow", List.of(Map.of("vehStat", "NVGO"), Map.of("vehStat", "STOR"))))));
        backend().on("VHCI02B", in -> reply(100, 0, Map.of()));
        Answer a = get(BASE + "/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("statuses").toString()).isEqualTo("[\"NVGO\",\"STOR\"]");
        assertThat(a.body().get("time").asText()).isEqualTo("2359");
        assertThat(a.body().get("canChangeLocation").asBoolean()).isFalse();
    }

    @Test
    void aBlankCarIsRefusedBeforeAnyCall() throws Exception {
        Answer a = post(BASE, request("", "N"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assignedAll("VHCI03IS-VALIDATE2", "MRSVehicleIncident", "FINAL-ERROR-MESSAGE").get(0));
        assertThat(called()).doesNotContain("VHCIV");
    }

    @SuppressWarnings("unchecked")
    @Test
    void changeRecordsTheIncidentAndPassesItToMaintActions() throws Exception {
        now();
        backend().on("VHCI01B", in -> reply(0, 1, Map.of("VHCI01B_DATA", Map.of("whereIsCar", "YRD", "dispVehStat", "GO", "carType", "C2"))));
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHINVB", in -> reply(0, 0, Map.of()));
        backend().on("VHINVA", in -> reply(0, 0, Map.of()));
        backend().on("VHCIV", in -> {
            Map<String, Object> t = new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) in.get("VHCI01IW-TEMP-MI"));
            t.put("errFound", "N");
            t.put("statusUpd", "N");
            t.put("newVehStat", "NVGO");
            t.put("saveIncdNo", "I1234567");
            t.put("saveIncdType", "AWP");
            t.put("dateIncdOccrRev", Map.of("dateIncdYyyy", "2026", "filler1", "-", "dateIncdMm", "11", "filler2", "-", "dateIncdDd", "13"));
            return reply(0, 0, Map.of("VHCI01IW-TEMP-MI", t));
        });
        Answer a = post(BASE, request("1101", "BROKEN DOOR"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("incident").asText()).isEqualTo("I1234567");
        assertThat(a.body().get("maintenance").get("from").asText()).isEqualTo("VHCI03");
        assertThat(a.body().get("maintenance").get("dateInitialized").asText()).isEqualTo("11/13/2026");
        assertThat(called()).doesNotContain("VHINXA", "VHCI01E");
        backend().on("VHCIV", in -> reply(0, 0, Map.of("VHCI01IW-TEMP-MI", Map.of("errFound", "Y"))));
        assertThat(post(BASE, request("1101", "BROKEN DOOR")).status()).isEqualTo(422);
    }
}
