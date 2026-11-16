package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The narrative window (VHNAR01I) over HTTP. */
class VehicleMaintNarrativeApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleMaintenance";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> sent2 = new AtomicReference<>();

    @Test
    void openingSplitsTheCiPartWithoutCallingTheBackend() throws Exception {
        String marker = TEXTS.compared("VHNARIS-CHK-NARR", PKG, "NARR-1").get(0);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("incdNo", "29990010");
        q.put("currentRow", 2);
        q.put("maxRows", 3);
        q.put("timeNarr", "10.15.00");
        q.put("narr", marker + " ".repeat(25) + "MOVED");
        Answer a = get("/api/v1/vehicle-maintenance/narratives/opening", q);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("ciPart").asText()).isEqualTo(marker);
        assertThat(a.body().get("narr").asText()).isEqualTo("MOVED");
        assertThat(a.body().get("timeNarr").asText()).isEqualTo("10:15");
        assertThat(a.body().get("canPrev").asBoolean()).isTrue();
        assertThat(a.body().get("canNext").asBoolean()).isTrue();
        assertThat(called()).isEmpty();
    }

    @Test
    void stepReadsTheNextIncidentNarrative() throws Exception {
        backend().on("VHXNR1A", in -> { sent.set(in.get("VHXNR1A_CRIT")); return reply(0, 1, Map.of("VHXNR1A_DATA", Map.of("narr", "SECOND", "narrNo", 2, "userGrp", "VEHSUPR"))); });
        Answer a = get("/api/v1/vehicle-maintenance/narratives/step", Map.of("incdNo", "29990010", "currentRow", 1, "maxRows", 3, "direction", "next"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("currentRow", 2L).containsEntry("narrNo", 2L)
                .containsEntry("vagenFunction", TEXTS.compared("VHNARIS-GET-NARR", PKG, "VAGen_FUNCTION").get(1));
        assertThat(a.body().get("narr").asText()).isEqualTo("SECOND");
        assertThat(a.body().get("currentRow").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/vehicle-maintenance/narratives/step", Map.of("incdNo", "29990010", "direction", "sideways")).status()).isEqualTo(400);
    }

    @Test
    void stepOfARepairDocumentUsesVhxnr5a() throws Exception {
        backend().on("VHXNR5A", in -> { sent.set(in.get("VHXNR5A_CRIT")); return reply(0, 1, Map.of("VHXNR5A_DATA", Map.of("narr", "R1", "narrNo", 1))); });
        Answer a = get("/api/v1/vehicle-maintenance/narratives/step", Map.of("incdNo", "29990010", "reprDocNo", 7, "currentRow", 2, "maxRows", 3, "direction", "prev"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("reprDocNo", 7L).containsEntry("currentRow", 1L);
        assertThat(called()).containsExactly("VHXNR5A");
    }

    @Test
    void addWritesUnderTheSignedOnGroupAndReadsBackTheLast() throws Exception {
        backend().on("VHXNR2A", in -> { sent.set(in.get("VHXNR2A_DATA")); return reply(0, 0, Map.of("VHXNR2A_DATA", Map.of("narrNo", 4))); });
        backend().on("VHXNR6A", in -> { sent2.set(in.get("VHXNR6A_CRIT")); return reply(0, 1, Map.of("VHXNR6A_DATA", Map.of("narr", "NEW", "maxRows", 4, "narrNo", 4))); });
        Answer a = post("/api/v1/vehicle-maintenance/narratives", Map.of("incdNo", "29990010", "maxRows", 3, "narrative", "NEW"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("narrNo", 4L).containsEntry("narr", "NEW").containsEntry("userGrp", "BRIGHTCO").containsEntry("termNarr", "MARIS-UI");
        assertThat(sent2.get()).containsEntry("vagenFunction", TEXTS.assigned("VHNARIS-RETR-INCD", PKG, "VAGen_FUNCTION", null));
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHNARIS-RETR-INCD", PKG, "FINAL-ERROR-MESSAGE", null));
        assertThat(a.body().get("narrative").get("currentRow").asInt()).isEqualTo(4);
        assertThat(a.body().get("narrative").get("canNext").asBoolean()).isFalse();
    }

    @Test
    void blankNarrativeIsRefusedAndNothingIsWritten() throws Exception {
        Answer a = post("/api/v1/vehicle-maintenance/narratives", Map.of("incdNo", "29990010", "maxRows", 3, "narrative", " "));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VHNARIS-ADDNARR", PKG, "FINAL-ERROR-MESSAGE", "Invalid"));
        assertThat(post("/api/v1/vehicle-maintenance/narratives/update", Map.of("incdNo", "29990010", "currentRow", 1, "narrative", "")).status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void updateOfARepairNarrativeUsesVhxnr8aAndAProgramErrorIs422() throws Exception {
        backend().on("VHXNR8A", in -> { sent.set(in.get("VHXNR8A_CRIT")); sent2.set(in.get("VHXNR8A_DATA")); return reply(0, 0, Map.of()); });
        Answer a = post("/api/v1/vehicle-maintenance/narratives/update", Map.of("incdNo", "29990010", "reprDocNo", 7, "currentRow", 2, "narrative", "FIXED"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHNARIS-UPDNARR", PKG, "FINAL-ERROR-MESSAGE", "Repr Narrative updated"));
        assertThat(sent.get()).containsEntry("narrNo", 2L).containsEntry("reprDocNo", 7L);
        assertThat(sent2.get()).containsEntry("narr", "FIXED").containsEntry("userGrp", "BRIGHTCO");
        backend().on("VHXNR7A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "AFCR001")), "00000000"));
        assertThat(post("/api/v1/vehicle-maintenance/narratives/update", Map.of("incdNo", "29990010", "currentRow", 2, "narrative", "X")).status()).isEqualTo(422);
    }
}
