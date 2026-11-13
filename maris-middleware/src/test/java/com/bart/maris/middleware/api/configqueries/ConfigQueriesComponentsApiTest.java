package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesEgl;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Config component inquiry (old screen VHCF16I): the clean API. Rules from MRSConfigurTrackQuery/RUIFunctions.egl
 * VHCF16IS-INITIAL (lists: VHCF16B, VHCF16C, VHCF16D), VHCF16I-GET-DATA (VHCF16A), CommonFunctions VHCF14IS-STK-DASH.
 */
class ConfigQueriesComponentsApiTest extends ApiTestBase {
    private static final List<String> MSG = ConfigQueriesEgl.assignedTexts(Parity.bundle(), "VHCF16I-GET-DATA", "MRSConfigurTrackQuery", "MSG");
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    @Test
    void theOptionsAreTheOldLists() throws Exception {
        backend().on("VHCF16B", in -> reply(0, 2, Map.of("VHCF16B_DATA", rows("dataRow", List.of(Map.of("cmpntEquipType", "TRUCK"), Map.of("cmpntEquipType", "AXLE"))))));
        backend().on("VHCF16C", in -> reply(0, 1, Map.of("VHCF16C_DATA", rows("dataRow", List.of(
                Map.of("stockNo", "123456789", "stockDisplay", Map.of("stockNoL", "12-34-56789", "shortStockDesc", "TRUCK ASSY")))))));
        backend().on("VHCF16D", in -> reply(0, 2, Map.of("VHCF16D_DATA", rows("dataRow", List.of(Map.of("serialStat", "ACT"), Map.of("serialStat", "SCR"))))));
        Answer a = get("/api/v1/config-queries/component-options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCF16B", "VHCF16C", "VHCF16D");
        assertThat(a.body().get("equipmentTypes").toString()).isEqualTo("[\"TRUCK\",\"AXLE\"]");
        assertThat(a.body().at("/stocks/0/stock").asText()).isEqualTo("12-34-56789");
        assertThat(a.body().at("/stocks/0/description").asText()).isEqualTo("TRUCK ASSY");
        assertThat(a.body().get("serialStatuses").toString()).isEqualTo("[\"ACT\",\"SCR\"]");
        assertThat(a.body().get("homeYards").toString()).isEqualTo("[\"CY\",\"DY\",\"HY\",\"RY\"]");   // the old list's items
    }

    private void components(int urc, String limitFlag, List<Map<String, Object>> rows) {
        backend().on("VHCF16A", in -> {
            crit.set(in.get("VHCF16A_CRIT"));
            Map<String, Object> c = new HashMap<>(in.get("VHCF16A_CRIT"));
            c.put("dataLimitFlag", limitFlag);
            return reply(urc, rows.size(), Map.of("VHCF16A_CRIT", c, "VHCF16A_DATA", rows("dataRow", rows)));
        });
    }

    private static Map<String, Object> row(String car) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", car, "homeYrd", "CY", "cmpntEquipType", "TRUCK", "cmpntSerialNo", "SN1", "cmpntTrackingCd", "T1",
                "dateBuyRebuilt", "01-02-2020", "serialStat", "ACT", "stockNo", "12-34-56789", "posnCd", "A", "cmpntCumHrs", 1234));
        m.put("parentSerialNo", "PSN");
        return m;
    }

    @Test
    void searchingSendsTheCriteriaAsTheOldScreen() throws Exception {
        components(0, "N", List.of(row("1001"), row("1002")));
        Answer a = get("/api/v1/config-queries/components", Map.of("stock", "12-34-56789", "serial", "SN1", "status", "ACT", "homeYard", "CY", "sort", "2"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCF16A");
        assertThat(crit.get()).containsEntry("stockNo", "123456789")                 // VHCF14IS-STK-DASH: the dashes out
                .containsEntry("cmpntSerialNo", "SN1").containsEntry("serialStat", "ACT").containsEntry("homeYrd", "CY")
                .containsEntry("sortIndex", "2").containsEntry("dataLimitFlag", "N").containsEntry("cmpntEquipType", "");
        assertThat(a.body().at("/rows/1/car").asText()).isEqualTo("1002");
        assertThat(a.body().at("/rows/0/hours").asLong()).isEqualTo(1234);
        assertThat(a.body().at("/rows/0/parentSerial").asText()).isEqualTo("PSN");
        assertThat(a.body().get("limitReached").asBoolean()).isFalse();
        assertThat(a.body().has("message")).isFalse();
    }

    @Test
    void theDataLimitIsTheOldMessage() throws Exception {
        components(0, "Y", List.of(row("1001")));
        Answer a = get("/api/v1/config-queries/components", Map.of("sort", "1"));
        assertThat(crit.get()).containsEntry("sortIndex", "1").containsEntry("stockNo", "");
        assertThat(a.body().get("limitReached").asBoolean()).isTrue();
        assertThat(a.body().get("message").asText()).isEqualTo(MSG.get(1).strip());
    }

    @Test
    void noDataIsTheOldMessage() throws Exception {
        components(100, "N", List.of());
        Answer a = get("/api/v1/config-queries/components", Map.of());
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("rows").size()).isZero();
        assertThat(a.body().get("message").asText()).isEqualTo(MSG.get(2).strip());
    }

    @Test
    void aHomeYardNeedsAStockAndNothingIsCalled() throws Exception {
        components(0, "N", List.of());
        Answer a = get("/api/v1/config-queries/components", Map.of("homeYard", "CY"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(MSG.get(3).strip());
        assertThat(get("/api/v1/config-queries/components", Map.of("homeYard", "XX", "stock", "12-34-56789")).status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHCF16A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("COMPONENT", "", ""))), "00000000"));
        Answer a = get("/api/v1/config-queries/components", Map.of());
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("COMPONENT Is Not Found");
    }

    @Test
    void theApiNeedsASignedOnUser() throws Exception {
        send("POST", "/api/v1/auth/signoff", null);
        assertThat(get("/api/v1/config-queries/components", Map.of()).status()).isEqualTo(401);
    }
}
