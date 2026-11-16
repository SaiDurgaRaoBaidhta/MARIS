package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesEgl;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Configuration tracking query screens that write, through HTTP with a fake backend: VHCF21I add a serial to stock. */
class ConfigQueriesUpdatesApiTest extends ApiTestBase {
    private static final String PKG = "MRSConfigurTrackQuery";
    private final AtomicReference<Map<String, Object>> inserted = new AtomicReference<>();

    private void serialBackend(int existsUrc) {
        backend().on("VHCF21A", in -> reply(0, 2, Map.of("VHCF21A_DATA", rows("dataRow", List.of(Map.of("cmpntEquipType", "AXLE"), Map.of("cmpntEquipType", "BEARING"))))));
        backend().on("VHCF21B", in -> reply(0, 1, Map.of("VHCF21B_DATA", rows("dataRow", List.of(Map.of("stockNo", "123456789", "stockNoL", "12-34-56789", "cmpntEquipType", "AXLE"))))));
        backend().on("VHCF21C", in -> reply(existsUrc, existsUrc == 0 ? 1 : 0, Map.of()));
        backend().on("VHCF21E", in -> reply(0, 1, Map.of("VHCF21E_DATA", Map.of("cmpntTrackingCd", "W"))));
        backend().on("VHCF21D", in -> { inserted.set(in.get("VHCF21D_DATA")); return reply(0, 1, Map.of()); });
    }

    @Test
    void serialAddOptionsAndStocks() throws Exception {
        serialBackend(100);
        Answer o = get("/api/v1/config-queries/serial-add/options", null);
        assertThat(o.status()).isEqualTo(200);
        assertThat(o.body().get("componentTypes").toString()).isEqualTo("[\"AXLE\",\"BEARING\"]");
        assertThat(o.body().get("actions").toString()).isEqualTo("[\"ADD\"]");
        assertThat(o.body().get("date").asText()).matches("\\d\\d-\\d\\d-\\d{4}");
        Answer s = get("/api/v1/config-queries/serial-add/stocks", Map.of("componentType", "AXLE"));
        assertThat(s.body().get("stocks").toString()).isEqualTo("[\"12-34-56789\"]");
    }

    @Test
    void serialAddInsertsAsTheOldScreen() throws Exception {
        serialBackend(100);
        Answer a = post("/api/v1/config-queries/serial-add", Map.of("componentType", "AXLE", "stock", "12-34-56789", "serial", "SN9", "date", "11-01-2025"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("added").asBoolean()).isTrue();
        assertThat(a.body().get("message").asText()).isEqualTo(ConfigQueriesEgl.assignedTexts(Parity.bundle(), "VHCF21I-ADD", PKG, "MSG").get(0).strip());
        assertThat(called()).containsSubsequence("VHCF21C", "VHCF21E", "VHCF21D");
        @SuppressWarnings("unchecked") Map<String, Object> row = ((List<Map<String, Object>>) inserted.get().get("dataRow")).get(0);
        assertThat(row).containsEntry("stockNo", "123456789").containsEntry("cmpntSerialNo", "SN9").containsEntry("serialStat", "STO")
                .containsEntry("histActionCd", "INV").containsEntry("cmpntTrackingCd", "WAR");
        assertThat(row.get("dateBuyRebuilt").toString()).contains("dateBuyRest=-11-01").contains("dateBuyYearNnnn=2025");   // 2025-11-01
    }

    @Test
    void serialAddRefusesAnExistingSerial() throws Exception {
        serialBackend(0);
        Answer a = post("/api/v1/config-queries/serial-add", Map.of("componentType", "AXLE", "stock", "12-34-56789", "serial", "SN9", "date", "11-01-2025"));
        assertThat(a.body().get("added").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).isEqualTo(ConfigQueriesEgl.assignedTexts(Parity.bundle(), "VHCF21I-UPDATE", PKG, "MSG").get(0).strip());
        assertThat(called()).doesNotContain("VHCF21D");
    }

    @Test
    void truckInquiryAndBuildRefusal() throws Exception {
        backend().on("VHCF35C", in -> reply(0, 1, Map.of("VHCF35C_DATA", rows("dataRow", List.of(
                Map.of("stockNo", "173588520", "stockDisplay", Map.of("stockNoL", "17-35-88520", "vagenSpace", " ", "shortStockDesc", "TRUCK X")))))));
        backend().on("VHCF35D", in -> reply(0, 1, Map.of("VHCF35D_DATA", Map.of("carNo", "1101", "cmpntSerialNo", "TRK1", "truckStat", "REBUILT", "shopLoc", "HY"))));
        backend().on("VHCF35B", in -> reply(0, 2, Map.of("VHCF35B_DATA", rows("dataRow", List.of(
                Map.of("cmpntEquipType", "AXLE", "posnCd", "1", "cmpntSerialNo", "AX1"), Map.of("cmpntEquipType", "AXLE", "posnCd", "2", "cmpntSerialNo", "AX2"))))));
        backend().on("VHCF37F", in -> reply(100, 0, Map.of()));
        Answer o = get("/api/v1/config-queries/trucks/options", null);
        assertThat(o.body().at("/stocks/0/stock").asText()).isEqualTo("17-35-88520");
        assertThat(o.body().get("statuses").toString()).isEqualTo("[\"REPAIR\",\"REBUILT\",\"WHEELSCUT\"]");
        Answer t = get("/api/v1/config-queries/trucks", Map.of("stock", "17-35-88520", "serial", "TRK1"));
        assertThat(t.status()).isEqualTo(200);
        assertThat(t.body().at("/components/0/serial").asText()).isEqualTo("AX1");
        assertThat(t.body().at("/components/0/name").asText()).isEqualTo("AXLE1");          // the X-end names for 17-35-88520
        assertThat(t.body().get("car").asText()).isEqualTo("1101");
        // the build screen refuses a truck that is on a car: nothing is written
        Answer b = post("/api/v1/config-queries/trucks/build", Map.of("stock", "17-35-88520", "serial", "TRK1", "components", Map.of("axle1", "NEW1")));
        assertThat(b.status()).isEqualTo(200);
        assertThat(b.body().get("updatable").asBoolean()).isFalse();
        assertThat(called()).doesNotContain("VHCF35G", "VHCF35H");
    }

    @Test
    void axleListAndUpdateChecks() throws Exception {
        backend().on("VHCF06A", in -> reply(0, 0, Map.of("VHCF06A_CRIT", Map.of("axleNotFound", "Y"))));
        Answer a = get("/api/v1/config-queries/axles", Map.of("kind", "axle", "serial", "NOPE"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("updatable").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).startsWith("Component does not exist");
        Answer bad = post("/api/v1/config-queries/axles/update", Map.of("kind", "axle", "serial", "NOPE", "checks", List.of("notABox")));
        assertThat(bad.status()).isEqualTo(400);
    }
}
