package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Pseudo trains (old VHYT09I + VHYT01I, Vhyt07i, VHYT12I): the clean API. Rules from MRSYardTracking/RUIFunctions.egl:
 * VHYT09IS-INIT / -GET-DATA / -MOVE-DATA / -COLOR, VHYT01IS-SUBMIT, VHYT07IS-INIT / -LOAD / -COLOR, VHYT12IS-*.
 */
class YardTrackingPseudoApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    @Test
    void optionsListTheYardsThenAll() throws Exception {
        backend().on("VHYT09A", in -> reply(0, 2, Map.of("VHYT09A_DATA", rows("dataRow", List.of(Map.of("yrd", "HY"), Map.of("yrd", "CY"))))));
        Answer a = get("/api/v1/yard-tracking/pseudo-trains/options", Map.of("yard", "CY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("yards").toString()).isEqualTo("[\"HY\",\"CY\",\"ALL\"]");
        assertThat(a.body().get("yard").asText()).isEqualTo("CY");
        assertThat(a.body().get("message").asText()).startsWith("Please select the Yard");
        assertThat(a.body().get("pageSize").asInt()).isEqualTo(4);
    }

    @Test
    void allYardsAskForTheWholeRangeAndColourCars() throws Exception {
        backend().on("VHYT09B", in -> {
            sent.set(in.get("VHYT09B_CRIT"));
            return reply(0, 1, Map.of("VHYT09B_DATA", rows("dataRow", List.of(Map.of("conNo", "201", "maxPosnNo", 2, "yrdSubLoc", "T-10", "yrdTrkgYrd", "HY")))));
        });
        backend().on("VHYT09C", in -> reply(0, 2, Map.of("VHYT09C_DATA", rows("dataRow", List.of(
                Map.of("conNo", "201", "carNoX", Map.of("lftOr", "<", "carNo", "1101", "rgtOr", " "), "homeYrd", "HY", "vehStat", "GO"),
                Map.of("conNo", "201", "carNoX", Map.of("lftOr", " ", "carNo", "1102", "rgtOr", ">"), "homeYrd", "RY", "vehStat", "NO GO"))))));
        Answer a = get("/api/v1/yard-tracking/pseudo-trains", Map.of("yard", "ALL"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().get("frYrdTrkgYrd")).isEqualTo("AAA");
        assertThat(sent.get().get("toYrdTrkgYrd")).isEqualTo("ZZZ");
        assertThat(a.body().get("message").asText()).isEqualTo("ALL Yard is selected.");
        assertThat(a.body().at("/consists/0/cars/0/car").asText()).isEqualTo("<1101");
        assertThat(a.body().at("/consists/0/cars/0/colour").asText()).isEqualTo("BLACK");
        assertThat(a.body().at("/consists/0/cars/1/car").asText()).isEqualTo("1102>");
        assertThat(a.body().at("/consists/0/cars/1/colour").asText()).isEqualTo("RED");
        assertThat(a.body().get("printable").asBoolean()).isTrue();
    }

    @Test
    void anEmptyYardSaysSo() throws Exception {
        backend().on("VHYT09B", in -> reply(100, 0, Map.of("VHYT09B_DATA", Map.of())));
        Answer a = get("/api/v1/yard-tracking/pseudo-trains", Map.of("yard", "RY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("RY  Yard has no consist");
        assertThat(a.body().get("consists").size()).isZero();
        assertThat(called()).containsExactly("VHYT09B");
    }

    @Test
    void printSendsTheYardAndPrinter() throws Exception {
        backend().on("VHYT01A", in -> {
            sent.set(in.get("VHYT01A_CRIT"));
            return reply(0, 0, Map.of("VHYT01A_DATA", Map.of()));
        });
        Answer a = post("/api/v1/yard-tracking/pseudo-trains/print", Map.of("yard", "HY", "printer", "PRT2"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().get("yrdTrkgYrd")).isEqualTo("HY");
        assertThat(sent.get().get("reptType")).isEqualTo("PRT2");
        assertThat(post("/api/v1/yard-tracking/pseudo-trains/print", Map.of("yard", "HY", "printer", "")).status()).isEqualTo(400);
    }

    @Test
    void haywardMapShowsCarsByHomeYardAndTheTrackButtons() throws Exception {
        backend().on("VHYT07A", in -> reply(0, 2, Map.of("VHYT07A_DATA", rows("dataRow", List.of(
                Map.of("carNo", "1234", "vagenOrientation", "<", "yrdSubLoc", "T-10", "posnNo", 1, "homeYrd", "HY", "softver", "V1"),
                Map.of("carNo", "2345", "vagenOrientation", ">", "yrdSubLoc", "NOWHERE", "posnNo", 1, "homeYrd", "RY"))))));
        Answer a = get("/api/v1/yard-tracking/hayward-yard-map", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("yard").asText()).isEqualTo("HY");
        assertThat(a.body().get("cars").size()).isEqualTo(1);
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("$<1234");
        assertThat(a.body().at("/cars/0/colour").asText()).isEqualTo("GREEN");
        assertThat(a.body().get("spots").findValuesAsText("track")).contains("T-10", "T-95", "TT-1", "S-25");
        assertThat(a.body().get("pseudoTrainYard").asText()).isEqualTo("HY");
    }

    private static Map<String, Object> car(String no, String type, String stat, String ato, int pos) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", no, "carType", type, "vehStat", stat, "atoPresentInd", ato, "trainIncdInd", "N",
                "vagenOrientation", pos == 1 ? "<" : ">", "lftOrientationCd", "<", "posnNo", pos, "homeYrd", "HY"));
        return m;
    }

    @Test
    void verifyPassesTheCarsPickedAndWritesTheTrain() throws Exception {
        List<Map<String, Object>> cars = List.of(car("1101", "A2", "GO", "Y", 1), car("1102", "A2", "GO", "Y", 2));
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>(), written = new AtomicReference<>();
        backend().on("VHYT12A", in -> {
            crit.set(in.get("VHYT12A_CRIT"));
            return reply(0, 2, Map.of("VHYT12A_DATA", rows("dataRow", cars)));
        });
        backend().on("VHYT12C", in -> reply(100, 0, Map.of("VHYT12C_DATA", Map.of())));
        backend().on("VHYT12B", in -> reply(100, 0, Map.of("VHYT12B_DATA", Map.of())));
        backend().on("VHYT12D", in -> {
            written.set(in.get("VHYT12D_DATA"));
            return reply(0, 0, Map.of("VHYT12D_DATA", in.get("VHYT12D_DATA")));
        });
        Map<String, Object> body = Map.of("yard", "HY", "track", "T-10", "fromPosition", 1, "toPosition", 2);
        Answer open = post("/api/v1/yard-tracking/consist-verify/open", body);
        assertThat(open.status()).isEqualTo(200);
        assertThat(crit.get().get("yrdTrkgYrd")).isEqualTo("HY");
        assertThat(crit.get().get("yrdSubLoc")).isEqualTo("T-10");
        assertThat(((Number) crit.get().get("toPos")).intValue()).isEqualTo(2);
        assertThat(open.body().get("cars").size()).isEqualTo(2);
        assertThat(open.body().get("verifyEnabled").asBoolean()).isTrue();
        assertThat(called()).doesNotContain("VHYT12D", "VHYT12E");

        Map<String, Object> v = new HashMap<>(body);
        v.put("consist", "345");
        Answer a = post("/api/v1/yard-tracking/consist-verify/verify", v);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("Pseudo train is verified");
        assertThat(written.get().get("conNo")).isEqualTo("345");
    }

    @Test
    void aBadTrainNumberIsRefusedWithTheOldMessage() throws Exception {
        backend().on("VHYT12A", in -> reply(0, 2, Map.of("VHYT12A_DATA", rows("dataRow", List.of(car("1101", "A2", "GO", "Y", 1), car("1102", "A2", "GO", "Y", 2))))));
        backend().on("VHYT12C", in -> reply(100, 0, Map.of("VHYT12C_DATA", Map.of())));
        Answer a = post("/api/v1/yard-tracking/consist-verify/verify", Map.of("yard", "HY", "track", "T-10", "fromPosition", 1, "toPosition", 2, "consist", "AB1"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).contains("Con No is not numeric");
        assertThat(called()).doesNotContain("VHYT12B", "VHYT12D");
    }
}
