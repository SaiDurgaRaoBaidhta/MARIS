package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Move a pseudo train to another yard (old screen VHIC03I): the clean API. It writes (program VHIC03A).
 * Rules come from MRSYardTrackingReport/RUIFunctions.egl: VHIC03IS-INIT (the yards), VHIC03IS-MOVE (the checks), VHIC03IS-MOVEX.
 */
class YardReportsMoveApiTest extends ApiTestBase {
    private static final String PKG = "MRSYardTrackingReport";
    /* the old screen's own texts (VHIC03IS-MOVE / -MOVEX), not copies */
    private static final List<String> MOVE_MSGS = YardReportsLayout.assignedTexts(Parity.bundle(), "VHIC03IS-MOVE", PKG, "MSG");
    private static final List<String> MOVEX_MSGS = YardReportsLayout.assignedTexts(Parity.bundle(), "VHIC03IS-MOVEX", PKG, "MSG");

    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private void moveBackend(int urc, String errorSw) {
        backend().on("VHIC03A", in -> {
            sent.set(in.get("VHIC03A_CRIT"));
            Map<String, Object> crit = new java.util.HashMap<>(in.get("VHIC03A_CRIT"));
            crit.put("errorSw", errorSw);
            return reply(urc, 0, Map.of("VHIC03A_CRIT", crit));
        });
    }

    private Answer move(String fromYard, String toYard) throws Exception {
        return post("/api/v1/yard-reports/pseudo-train-move", Map.of("fromYard", fromYard, "fromPosition", 3, "toPosition", 5, "toYard", toYard));
    }

    @Test
    void theYardsComeFromVhyt09a() throws Exception {
        backend().on("VHYT09A", in -> reply(0, 2, Map.of("VHYT09A_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "RY"))))));
        Answer a = get("/api/v1/yard-reports/pseudo-train-move/yards", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT09A");
        assertThat(a.body().toString()).isEqualTo("[\"CY\",\"RY\"]");
    }

    @Test
    void movingSendsTheCarsTheYardsAndWhoMovedThem() throws Exception {
        moveBackend(0, "");
        Answer a = move("CY", "RY");
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHIC03A");
        Map<String, Object> c = sent.get();
        assertThat(c.get("fromYrdTrkgYrd")).isEqualTo("CY");
        assertThat(c.get("toYrdTrkgYrd")).isEqualTo("RY");
        assertThat(((Number) c.get("fromPosnNo")).intValue()).isEqualTo(3);
        assertThat(((Number) c.get("toPosnNo")).intValue()).isEqualTo(5);
        assertThat(c.get("userGroup")).isEqualTo("BRIGHTCO");                 // the signed-on group
        assertThat(c.get("termId")).isEqualTo("MARIS-UI");                    // maris.engine.terminal-id
        assertThat(a.body().get("moved").asBoolean()).isTrue();
        // VHIC03IS-MOVEX: MSGY-1 + from + MSGY-3 + to
        String expected = (YardReportsLayout.assignedTexts(Parity.bundle(), "VHIC03IS-MOVEX", PKG, "MSGY-1").get(0) + "CY"
                + YardReportsLayout.assignedTexts(Parity.bundle(), "VHIC03IS-MOVEX", PKG, "MSGY-3").get(0) + "RY").replaceAll("\\s+", " ").strip();
        assertThat(a.body().get("message").asText()).isEqualTo(expected);
    }

    @Test
    void noRoomInTheNewYardIsNotAMove() throws Exception {
        moveBackend(0, "Y");
        Answer a = move("CY", "RY");
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("moved").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).isEqualTo(MOVEX_MSGS.get(0).strip());
    }

    @Test
    void theSameYardIsRefusedWithTheOldMessageAndCallsNothing() throws Exception {
        moveBackend(0, "");
        Answer a = move("CY", "CY");
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(MOVE_MSGS.get(0).strip());
        assertThat(called()).isEmpty();
    }

    @Test
    void noYardChosenIsRefusedWithTheOldMessageAndCallsNothing() throws Exception {
        moveBackend(0, "");
        Answer a = move("CY", "");
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(MOVE_MSGS.get(1).strip());
        assertThat(called()).isEmpty();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHIC03A", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("PSEUDO TRAIN", "", ""))), "00000000"));
        Answer a = move("CY", "RY");
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("PSEUDO TRAIN Is Not Found");
    }
}
