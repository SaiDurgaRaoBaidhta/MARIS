package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The consist maintenance API (old VHMT01I, VHCM01I, VHCT01I, VHCC01I, VHTS01I, VHSC01I, VHCY01I) over HTTP. */
class ConsistMaintenanceApiTest extends ApiTestBase {
    /** a program that answers its own records back */
    private static void echo(String... programs) {
        for (String p : programs) {
            backend().on(p, in -> {
                Map<String, Map<String, Object>> out = new HashMap<>();
                for (var e : in.entrySet()) if (!e.getKey().equals("XDCONTROL")) out.put(e.getKey(), e.getValue());
                out.put("XDCONTROL", Map.of("urc", 0, "urowcnt", 1));
                return new FakeLegacyBackend.Reply(out, "00000000");
            });
        }
    }

    @Test
    void aCarIsLookedUpAndItsLocationShown() throws Exception {
        backend().on("VHCY1A1", in -> reply(0, 1, Map.of("VHCY1A1_DATA", Map.of("vagenIndex", 1, "dataRow", List.of(Map.of("loc", "RY", "vagenTable", "YRD"))))));
        backend().on("VHCY1A4", in -> reply(0, 1, Map.of("VHCY1A4_DATA", Map.of("carNo", "1101", "carType", "A2", "currLoc", "CY", "whereIsCar", "YRD",
                "shopLocInd", "N", "yrdLocInd", "N", "transferTrkInd", "N"))));
        Answer a = get("/api/v1/consist/car-location/1101", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCY1A1", "VHCY1A4");
        assertThat(a.body().get("currentLocation").asText()).isEqualTo("CY");
        assertThat(a.body().get("message").asText()).isEqualTo("Enter the Move To Yard or Location");
    }

    @Test
    void aMoveToTheSameLocationIsRefusedWithTheOldMessage() throws Exception {
        backend().on("VHCY1A1", in -> reply(0, 1, Map.of("VHCY1A1_DATA", Map.of("vagenIndex", 1, "dataRow", List.of(Map.of("loc", "CY", "vagenTable", "YRD"))))));
        backend().on("VHCY1A4", in -> reply(0, 1, Map.of("VHCY1A4_DATA", Map.of("carNo", "1101", "carType", "A2", "currLoc", "CY", "whereIsCar", "YRD",
                "shopLocInd", "N", "yrdLocInd", "N", "transferTrkInd", "N"))));
        Answer a = post("/api/v1/consist/car-location/1101/move", Map.of("location", "CY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("Cannot move to same location");
        assertThat(a.body().get("moved").asBoolean()).isFalse();
        assertThat(called()).doesNotContain("VHCY1A3");
    }

    @Test
    void makeTrainChecksTheConsistAsTheScreenDoes() throws Exception {
        echo("VHMT016", "VHMT015");
        Answer a = post("/api/v1/consist/make-train/check", Map.of("consist", "201"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHMT016", "VHMT015");
        assertThat(a.body().at("/fields/consist").asText()).isEqualTo("201");
    }

    @Test
    void aSwapWithAnInvalidFromConsistSaysSo() throws Exception {
        echo("VHMT016", "VHXCONA");
        backend().on("VHXCONB", in -> reply(100, 0, Map.of()));
        backend().on("VHTS1A9", in -> reply(100, 0, Map.of()));
        Answer a = post("/api/v1/consist/swap", Map.of("pairs", List.of(List.of("201", "305")), "update", false));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("From Consist is not valid");
        assertThat(a.body().get("swaps").isArray()).isTrue();
    }

    @Test
    void missingInputIsA400() throws Exception {
        assertThat(post("/api/v1/consist/swap", Map.of("pairs", List.of(), "update", true)).status()).isEqualTo(400);
        assertThat(post("/api/v1/consist/split", Map.of("consist", "201", "atCar", 2)).status()).isEqualTo(400);
        assertThat(post("/api/v1/consist/change/list", Map.of("consist", " ")).status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }
}
