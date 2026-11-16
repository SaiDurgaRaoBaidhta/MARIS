package com.bart.maris.middleware.api.configtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Configuration tracking through HTTP with a fake backend: VHCF01I car configuration, VFCF05I its report printers. */
class ConfigTrackingApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> printed = new AtomicReference<>();

    @Test
    void optionsAreTheOldLists() throws Exception {
        Answer o = get("/api/v1/config-tracking/cars/options", null);
        assertThat(o.status()).isEqualTo(200);
        assertThat(o.body().at("/actions/0").asText()).isEqualTo("NO ACTION SELECTED");
        assertThat(o.body().get("actions")).hasSize(5);
        assertThat(o.body().get("trucks").toString()).isEqualTo("[\"Truck X\",\"Truck Y\"]");
        assertThat(o.body().get("locations").toString()).isEqualTo("[\"CY\",\"DY\",\"HY\",\"RY\"]");
    }

    @Test
    void anUnknownCarIsRefusedAsTheOldScreen() throws Exception {
        backend().on("VHCF01G", in -> reply(0, 0, Map.of()));
        backend().on("VH01A02", in -> reply(0, 0, Map.of()));
        backend().on("VHCF01A", in -> reply(100, 0, Map.of()));
        Answer c = get("/api/v1/config-tracking/cars/9999", null);
        assertThat(c.status()).isEqualTo(200);
        assertThat(c.body().get("message").asText()).contains("9999").contains("is not a valid Car No");
        assertThat(c.body().get("updatable").asBoolean()).isFalse();
    }

    @Test
    void printersAndHardCopy() throws Exception {
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("prtrLocDesc", "SHOP", "vtamName", "PRT01"))))));
        backend().on("VHCF05A", in -> { printed.set(in.get("VHCF05A_CRIT")); return reply(0, 0, Map.of()); });
        Answer p = get("/api/v1/config-tracking/printers", null);
        assertThat(p.body().at("/printers/0/vtamName").asText()).isEqualTo("PRT01");
        assertThat(post("/api/v1/config-tracking/cars/1101/print", Map.of("printer", "")).status()).isEqualTo(400);
        assertThat(post("/api/v1/config-tracking/cars/1101/print", Map.of("printer", "PRT01")).status()).isEqualTo(202);
        assertThat(printed.get()).containsEntry("reptType", "PRT01").containsEntry("carNo", "1101");
    }
}
