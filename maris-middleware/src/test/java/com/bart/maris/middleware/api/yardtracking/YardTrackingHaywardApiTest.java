package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Hayward yard update map (old VHYT05I) over HTTP against the fake backend. */
class YardTrackingHaywardApiTest extends ApiTestBase {
    @Test
    void theMapShowsTheCarsWithTheirClickCodes() throws Exception {
        backend().on("VHYT07A", in -> reply(0, 1, Map.of("VHYT07A_DATA", rows("dataRow", List.of(
                Map.of("carNo", "1101", "vagenOrientation", "<", "yrdSubLoc", "T-10", "posnNo", 1, "vehStat", "GO", "homeYrd", "HY"))))));
        backend().on("VH01A02", in -> reply(0, 1, Map.of("VH01A02_DATA", Map.of("userGroupAccess", List.of(
                Map.of("partName", "pbtMaint", "accessAllow", "N"))))));
        Answer a = get("/api/v1/yard-tracking/hayward-yard", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("yard").asText()).isEqualTo("HY");
        assertThat(a.body().get("maintenance").asBoolean()).isFalse();
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("<1101");
        assertThat(a.body().at("/cars/0/code").asText()).isEqualTo("T-1001");
        assertThat(a.body().at("/cars/0/condition").asText()).isEqualTo("go");
    }

    @Test
    void threeClicksOpenTheMoveDetail() throws Exception {
        Answer a = post("/api/v1/yard-tracking/hayward-yard/selection", Map.of("clicks", List.of(
                Map.of("spot", "T-1001"), Map.of("spot", "T-1003"), Map.of("spot", "T-1101"))));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("open").asText()).isEqualTo("VHYT11I");
        assertThat(a.body().at("/params/fromYrdSubLoc").asText()).isEqualTo("T-10");
        assertThat(a.body().at("/params/positionNoTo").asInt()).isEqualTo(3);
        assertThat(a.body().at("/params/toYrdSubLoc").asText()).isEqualTo("T-11");
    }

    @Test
    void anUnknownSpotIsRefused() throws Exception {
        Answer a = post("/api/v1/yard-tracking/hayward-yard/selection", Map.of("clicks", List.of(Map.of("spot", "ZZ"))));
        assertThat(a.status()).isEqualTo(400);
    }
}
