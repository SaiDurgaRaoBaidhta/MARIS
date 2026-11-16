package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Revenue incidents (VHINC06I) and event / shop / miscellaneous incidents (VHINC03I) of a car, over HTTP. */
class IncidentsCarApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleIncident";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
    private static final String TODAY = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    private static final String NOW = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));

    private void checks() {
        backend().on("VH01A02", in -> reply(0, 0, Map.of()));
        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHXREVC", in -> reply(0, 1, Map.of("VHXREVC_DATA", Map.of("revenueDate", TODAY))));
        backend().on("VHINC1A", in -> reply(0, 1, Map.of("VHINC1A_CRIT", Map.of("vagenCount", 1))));
        backend().on("VHINC6A", in -> reply(0, 1, Map.of("VHINC6A_DATA", Map.of("whereIsCar", "CON", "carType", "C2"))));
        backend().on("VHINVC", in -> reply(0, 0, Map.of()));
        backend().on("VHINVB", in -> reply(0, 0, Map.of()));
        backend().on("VHINCXA", in -> reply(0, 2, Map.of("VHINCXA_DATA", Map.of("discvDurCount", 2,
                "dataRow", List.of(Map.of("discvDur", "1", "discvDurDesc", "REVENUE"), Map.of("discvDur", "4", "discvDurDesc", "SHOP"))))));
    }

    private static Map<String, Object> body(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void revenueIncidentIsFiledWithItsFormPrinted() throws Exception {
        checks();
        backend().on("VHINCX", in -> { sent.set(in.get("VHINCX_CRIT")); return reply(0, 0, Map.of("VHINCX_CRIT", Map.of("errFound", "N"), "VHINCX_DATA", Map.of("incdNo", "G0004321"))); });
        backend().on("VHINC3C", in -> reply(0, 0, Map.of()));
        Answer a = post("/api/v1/incidents/revenue", body("carNo", "1101", "symptom", "0120", "date", TODAY, "time", NOW, "speed", "35", "direction", "N",
                "location", "A10", "narrative", "DOOR"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("incidentNo").asText()).isEqualTo("G0004321");
        assertThat(sent.get()).containsEntry("incdPfx", TEXTS.assigned("VHINC06IS-STORE-DB", PKG, "INCD-PFX", null)).containsEntry("carNo", "1101");
        assertThat(called()).containsSubsequence("VH01A02", "AFCXVLD", "VHXREVC", "VHINC1A", "VHINC6A", "VHINVC", "VHINCX", "VHINC3C");
    }

    @Test
    void revenueIncidentOfACarNotInUseIsRefused() throws Exception {
        checks();
        backend().on("VHINC6A", in -> reply(0, 1, Map.of("VHINC6A_DATA", Map.of("whereIsCar", "SHP", "carType", "C2"))));
        Answer a = post("/api/v1/incidents/revenue", body("carNo", "1101", "symptom", "0120", "date", TODAY, "time", NOW, "speed", "35", "direction", "N",
                "location", "A10", "narrative", "DOOR"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHINC06IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", "not in use").replace(".", ""));
        assertThat(called()).doesNotContain("VHINCX");
    }

    @Test
    void carIncidentOptionsAndTheShopTypeFixedDuration() throws Exception {
        checks();
        backend().on("VHCI02G", in -> reply(0, 1, Map.of("VHCI02G_CRIT", Map.of("currentDate", TODAY, "currentTime", "10.00.00"))));
        Answer a = get("/api/v1/incidents/car-incident/options", Map.of("type", "SHN"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("date").asText()).isEqualTo(TODAY);
        assertThat(a.body().get("discoveries")).hasSize(2);
        assertThat(a.body().get("fixedDiscovery").asText()).isEqualTo("4");
        assertThat(a.body().get("title").asText()).contains("Shop");
        assertThat(get("/api/v1/incidents/car-incident/options", Map.of("type", "XXX")).status()).isEqualTo(400);
    }

    @Test
    void carIncidentIsFiledWithThePrefixOfItsType() throws Exception {
        checks();
        backend().on("VHINCZ", in -> { sent.set(in.get("VHINCZ_CRIT")); return reply(0, 0, Map.of("VHINCZ_DATA", Map.of("incdNo", "D0000009"))); });
        backend().on("VHINC3A", in -> reply(0, 0, Map.of()));
        Answer a = post("/api/v1/incidents/car-incident", body("type", "MSC", "carNo", "1101", "symptom", "0120", "discovery", "1", "date", TODAY, "time", NOW,
                "location", "A10", "narrative", "DOOR"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).contains("D0000009");
        assertThat(sent.get()).containsEntry("incdType", "MSC").containsEntry("typeInd", "MSC");
        assertThat(called()).containsSubsequence("VHINVB", "VHINCZ", "VHINC3A");
    }

    @Test
    void symptomNarrative() throws Exception {
        backend().on("VHINC3B", in -> reply(0, 1, Map.of("VHINC3B_CRIT", Map.of("sympCd", "0120", "sympCdNarr", "DOORS DO NOT CLOSE"))));
        Answer a = get("/api/v1/incidents/car-incident/symptoms/0120/narrative", null);
        assertThat(a.body().get("narrative").asText()).isEqualTo("DOORS DO NOT CLOSE");
    }
}
