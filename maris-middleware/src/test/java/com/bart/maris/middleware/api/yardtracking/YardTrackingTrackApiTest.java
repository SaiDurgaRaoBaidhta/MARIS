package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackEgl;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The track windows' endpoints (old VHYT08I, VHYT02I, VHYT06I, VHYT14I) over HTTP, against a fake backend. */
class YardTrackingTrackApiTest extends ApiTestBase {
    @BeforeEach
    @SuppressWarnings("unchecked")
    void scripted() {
        YardTrackingTrackBackend.answers(YardTrackingTrackBackend.CARS.length).forEach((program, answer) -> backend().on(program, in -> {
            Map<String, Map<String, Object>> m = new LinkedHashMap<>();
            answer.apply(in).forEach((k, v) -> m.put(k, (Map<String, Object>) v));
            return new FakeLegacyBackend.Reply(m, "00000000");
        }));
    }

    @Test
    void theTrackShowsItsCarsAndLocations() throws Exception {
        Answer a = get("/api/v1/yard-tracking/track", Map.of("yard", "SW", "subLocation", "T-12"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsSubsequence("VHYT08D", "VHYT08B", "VHYT08F", "VHYT08H", "VHYT08C", "VHYT08A", "VH01A02");
        assertThat(a.body().at("/positions/0/car").asText()).isEqualTo("1101");
        assertThat(a.body().at("/positions/1/status").asText()).isEqualTo("GO    TI");
        assertThat(a.body().at("/positions/2/position").asInt()).isEqualTo(3);
        assertThat(a.body().at("/locations/0/code").asText()).isEqualTo("T-13");
        assertThat(a.body().get("userMayChange").asBoolean()).isTrue();
        assertThat(a.body().get("positions").size()).isEqualTo(30);
    }

    @Test
    void aTrackIsRequired() throws Exception {
        assertThat(get("/api/v1/yard-tracking/track", Map.of("yard", "SW")).status()).isEqualTo(400);
    }

    @Test
    void changeReasonOpensTheReasonWindowForTheClickedCar() throws Exception {
        Answer a = post("/api/v1/yard-tracking/track/actions", Map.of("yard", "SW", "subLocation", "T-12", "slots", List.of(3), "action", "change-reason"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("opens").asText()).isEqualTo("VHYT06I");
        assertThat(a.body().at("/with/carNo").asText()).isEqualTo("1103");
    }

    @Test
    void anUnknownActionIsRefused() throws Exception {
        assertThat(post("/api/v1/yard-tracking/track/actions", Map.of("yard", "SW", "subLocation", "T-12", "action", "jump")).status()).isEqualTo(400);
    }

    @Test
    void aReasonChangeIsWrittenWithItsHistory() throws Exception {
        Answer v = get("/api/v1/yard-tracking/move-reasons/car", Map.of("yard", "SW", "subLocation", "T-12", "car", "1103", "position", 3));
        assertThat(v.body().at("/reasons/1/code").asText()).isEqualTo("M2");
        assertThat(v.body().at("/car/reasonCode").asText()).isEqualTo("M1");
        backend().reset();
        scripted();
        Answer c = post("/api/v1/yard-tracking/move-reasons/change", Map.of("yard", "SW", "subLocation", "T-12", "car", "1103", "position", 3, "reasonCode", "M2"));
        assertThat(c.status()).isEqualTo(200);
        assertThat(called()).containsSubsequence("VHYT06B", "VHYT06C", "VHYT06D", "VHYT06E", "VHYT06B");
        assertThat(c.body().get("updated").asBoolean()).isTrue();
    }

    @Test
    void aBlankReasonIsRefusedWithTheOldText() throws Exception {
        Answer c = post("/api/v1/yard-tracking/move-reasons/change", Map.of("yard", "SW", "subLocation", "T-12", "car", "1103", "position", 3, "reasonCode", ""));
        assertThat(c.status()).isEqualTo(400);
        assertThat(c.body().get("detail").asText()).isEqualTo(YardTrackingTrackEgl.first(Parity.bundle(), "VHYT06IS-VALID", "MRSYardTracking", "FINAL-MESSAGE"));
    }

    @Test
    void fixOrderAsksBeforeUnMakingAPseudoTrainThenMoves() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("yard", "SW", "subLocation", "T-12", "position", 4, "carIndex", 1, "relativeIndex", 4, "after", false));
        Answer q = post("/api/v1/yard-tracking/fix-order", body);
        assertThat(q.status()).isEqualTo(200);
        assertThat(q.body().get("question").asText()).contains("201");
        assertThat(called()).doesNotContain("VHYT14D");
        body.put("confirm", true);
        Answer y = post("/api/v1/yard-tracking/fix-order", body);
        assertThat(y.body().get("moved").asBoolean()).isTrue();
        assertThat(called()).contains("VHYT14F", "VHYT14D", "VHYT14C", "VHYT14E");
    }

    @Test
    void theReportWindowListsAndPrints() throws Exception {
        Answer o = get("/api/v1/yard-tracking/track-report/options", Map.of("yard", "SW"));
        assertThat(o.body().at("/printers/1/vtam").asText()).isEqualTo("LKS11HP1");
        assertThat(o.body().at("/subLocations/2").asText()).isEqualTo("ALL");
        Answer p = post("/api/v1/yard-tracking/track-report/print", Map.of("yard", "SW", "subLocation", "", "printer", "LKS11HP1"));
        assertThat(p.status()).isEqualTo(200);
        assertThat(backend().calls().stream().filter(c -> c.service().startsWith("VHYT02A")).findFirst().orElseThrow().rawBody()).contains("ALL");
        assertThat(post("/api/v1/yard-tracking/track-report/print", Map.of("yard", "", "printer", "LKS11HP1")).status()).isEqualTo(400);
    }
}
