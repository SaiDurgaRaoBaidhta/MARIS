package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The car move windows' endpoints (old VHYT11I, VHYT13I, VHYT24I) over HTTP, against a fake backend. */
class YardTrackingMoveApiTest extends ApiTestBase {
    @BeforeEach
    @SuppressWarnings("unchecked")
    void scripted() {
        YardTrackingMoveBackend.answers("NOT").forEach((program, answer) -> backend().on(program, in -> {
            Map<String, Map<String, Object>> m = new LinkedHashMap<>();
            answer.apply(in).forEach((k, v) -> m.put(k, (Map<String, Object>) v));
            return new FakeLegacyBackend.Reply(m, "00000000");
        }));
    }

    @Test
    void theTrackShowsItsCarsAndTheMoveToTracks() throws Exception {
        Answer a = get("/api/v1/yard-tracking/car-moves", Map.of("yard", "CY", "subLocation", "T-12", "toSubLocation", "T-13"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsSubsequence("VHYT08B", "VHYT08C", "VHYT08A", "VHYT11A", "VH01A02");
        assertThat(a.body().at("/positions/0/car").asText()).isEqualTo("1101");
        assertThat(a.body().at("/moveTo/1/code").asText()).isEqualTo("T-14");
        assertThat(a.body().get("toSubLocation").asText()).isEqualTo("T-13");
        assertThat(a.body().get("positions").size()).isEqualTo(30);
    }

    @Test
    void aTrackIsRequired() throws Exception {
        assertThat(get("/api/v1/yard-tracking/car-moves", Map.of("yard", "CY")).status()).isEqualTo(400);
    }

    @Test
    void moveWritesAndAnswersWithTheOldMessage() throws Exception {
        Answer a = post("/api/v1/yard-tracking/car-moves/actions", Map.of("yard", "CY", "subLocation", "T-12", "toSubLocation", "T-13",
                "slots", List.of(2, 4), "action", "move"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).contains("VHYT11C", "VHYT11B");
        assertThat(a.body().get("message").asText()).isNotBlank();
    }

    @Test
    void anUnknownActionOrTrackIsRefused() throws Exception {
        assertThat(post("/api/v1/yard-tracking/car-moves/actions", Map.of("yard", "CY", "subLocation", "T-12", "action", "jump")).status()).isEqualTo(400);
        assertThat(post("/api/v1/yard-tracking/car-moves/actions", Map.of("yard", "CY", "subLocation", "T-12", "toSubLocation", "X-99",
                "slots", List.of(1), "action", "move")).status()).isEqualTo(400);
    }

    @Test
    void theOrientationWindowAndAChange() throws Exception {
        Answer a = get("/api/v1/yard-tracking/orientation", Map.of("yard", "CY", "subLocation", "T-12", "carNo", "1103"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("car").asText()).isEqualTo("1103");
        assertThat(a.body().get("orientations").get(1).asText()).isEqualTo(">");
        Answer c = post("/api/v1/yard-tracking/orientation", Map.of("yard", "CY", "subLocation", "T-12", "carNo", "1103", "orientation", ">"));
        assertThat(c.status()).isEqualTo(200);
        assertThat(called()).contains("VHYT13E", "VHYT13C", "VHYT13D");
        assertThat(post("/api/v1/yard-tracking/orientation", Map.of("yard", "CY", "subLocation", "T-12", "carNo", "1103", "orientation", "x")).status())
                .isEqualTo(400);
    }

    @Test
    void theYardPrint() throws Exception {
        Answer o = get("/api/v1/yard-tracking/yard-print", Map.of("yard", "CY"));
        assertThat(o.status()).isEqualTo(200);
        assertThat(o.body().at("/printers/0/name").asText()).isEqualTo("DUBLNHP1");
        assertThat(post("/api/v1/yard-tracking/yard-print", Map.of("yard", "CY", "printer", "DUBLNHP1")).status()).isEqualTo(200);
        assertThat(called()).contains("VHYT24B");
        assertThat(get("/api/v1/yard-tracking/yard-print", Map.of("yard", "ZZ")).status()).isEqualTo(400);
    }
}
