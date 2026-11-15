package com.bart.maris.middleware.api.wayside;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Wayside narratives (old WS13I) over HTTP: latest, Prv / Nxt, and adding the next narrative. */
class WaysideNarrativeApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> added = new AtomicReference<>();

    private static Map<String, Object> narr(int no) {
        return Map.of("narr", "LINE " + no, "dateNarr", "2026-11-01", "timeNarr", "10.00.00", "userGrp", "WAYSIDE", "maxRows", 3, "narrNo", no);
    }

    @BeforeEach
    void backendAnswers() {
        added.set(null);
        backend().on("WSX2A01", in -> reply(0, 1, Map.of("WSX2A01_DATA", narr(3))));
        backend().on("WS13A02", in -> reply(0, 1, Map.of("WS13A02_DATA", narr(((Number) in.get("WS13A02_CRIT").get("narrNo")).intValue()))));
        backend().on("WS13A04", in -> { added.set(in.get("WS13A04_DATA")); return reply(0, 1, Map.of()); });
    }

    @Test
    void latestOfAnIncident() throws Exception {
        Answer a = get("/api/v1/wayside/narratives/latest", Map.of("incident", "29990010"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("text").asText()).isEqualTo("LINE 3");
        assertThat(a.body().get("row").asInt()).isEqualTo(3);
        assertThat(a.body().get("hasPrev").asBoolean()).isTrue();
        assertThat(a.body().get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void prvGoesBackOneAndNeverBelowTheFirst() throws Exception {
        Answer a = get("/api/v1/wayside/narratives/step", Map.of("incident", "29990010", "direction", "prev", "row", 1, "maxRows", 3));
        assertThat(a.body().get("row").asInt()).isEqualTo(1);
        assertThat(a.body().get("hasPrev").asBoolean()).isFalse();
    }

    @Test
    void addSendsTheNextNumberWithUserGroupAndTerminal() throws Exception {
        Answer a = post("/api/v1/wayside/narratives", Map.of("incident", "29990010", "text", "CHECKED THE SWITCH"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(added.get()).containsEntry("incdNo", "29990010").containsEntry("narrNo", 4L).containsEntry("narr", "CHECKED THE SWITCH")
                .containsEntry("userGrp", "BRIGHTCO").containsEntry("termNarr", "MARIS-UI");
    }

    @Test
    void aNarrativeNeedsAContextAndText() throws Exception {
        assertThat(get("/api/v1/wayside/narratives/latest", Map.of()).status()).isEqualTo(400);
        assertThat(post("/api/v1/wayside/narratives", Map.of("incident", "29990010", "text", " ")).status()).isEqualTo(400);
        assertThat(added.get()).isNull();
    }
}
