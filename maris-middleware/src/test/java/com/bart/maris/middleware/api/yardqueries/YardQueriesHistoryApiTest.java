package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Car location history (old VHYT29I, VHYT29A) and yard vehicle status history (old VHYT33I, VHYT33A): the clean API.
 * Rules from MrsYardTrackingQueries/RUIFunctions.egl VHYT29IS-GET-DATA / -REFORMAT / -CHECK, VHYT33IS-GET-DATA / -REFORMAT
 * / -CHECK; the dates are checked as VHYT27IS-TEST-DATE checks them, with its message.
 */
class YardQueriesHistoryApiTest extends ApiTestBase {
    private static final String BAD_RANGE = Parity.bundle().promptText("VHYT27I", "Message_Prompter1");
    private final AtomicReference<Map<String, Object>> crit = new AtomicReference<>();

    private static Map<String, Object> move(String car, String prevTrack, String prevReason) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", car, "yrdSubLoc", "S-1", "posnNo", 3, "vehStat", "GO", "vagenOrientation", "<",
                "dateUserMoved", "11-05-2026", "timeUserMoved", "10:20:00", "yrdSubLocPrev", prevTrack, "moveReasonCdPrv", prevReason, "posnNoPrev", 2));
        m.put("moveReasonDesc", "BRAKES");
        m.put("moveReasonCdNew", "B01");
        return m;
    }

    private void moves(int urc, List<Map<String, Object>> rows) {
        backend().on("VHYT29A", in -> { crit.set(in.get("VHYT29A_CRIT")); return reply(urc, rows.size(), Map.of("VHYT29A_DATA", rows("dataRow", rows))); });
    }

    @Test
    void carHistorySendsTheYardDatesAndCarRange() throws Exception {
        moves(0, List.of(move("1001", "T-24", ""), move("1001", "", "R99")));
        Answer a = get("/api/v1/yard-queries/car-history", Map.of("yard", "CY", "from", "2026-11-01", "to", "2026-11-10", "fromCar", "1001", "toCar", "1200"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT29A");
        assertThat(crit.get()).containsEntry("yrdTrkgYrd", "CY").containsEntry("startDate", "2026-11-01").containsEntry("endDate", "2026-11-10")
                .containsEntry("fromCar", "1001").containsEntry("toCar", "1200");
        assertThat(a.body().at("/rows/0/car").asText()).isEqualTo("1001");
        assertThat(a.body().at("/rows/0/previousTrack").asText()).isEqualTo("T-24");
        assertThat(a.body().at("/rows/1/previousTrack").asText()).isEqualTo("R99");        // no previous track: the previous reason
        assertThat(a.body().at("/rows/0/reasonCode").asText()).isEqualTo("B01");
        assertThat(a.body().get("maxRowsReached").asBoolean()).isFalse();
    }

    @Test
    void carHistoryCarRangeDefaultsAsTheOldScreen() throws Exception {
        moves(100, List.of());
        get("/api/v1/yard-queries/car-history", Map.of("from", "2026-11-01", "to", "2026-11-10"));
        assertThat(crit.get()).containsEntry("fromCar", "0300").containsEntry("toCar", "2999").containsEntry("yrdTrkgYrd", "HY");
        get("/api/v1/yard-queries/car-history", Map.of("yard", "RY", "from", "2026-11-01", "to", "2026-11-10", "fromCar", "1500"));
        assertThat(crit.get()).containsEntry("fromCar", "1500").containsEntry("toCar", "1500");   // one car
    }

    @Test
    void reaching324RowsIsFlagged() throws Exception {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 324; i++) many.add(move("1001", "T-24", ""));
        moves(0, many);
        Answer a = get("/api/v1/yard-queries/car-history", Map.of("from", "2026-11-01", "to", "2026-11-10"));
        assertThat(a.body().get("maxRowsReached").asBoolean()).isTrue();
    }

    @Test
    void aBadDateRangeIsRefusedWithTheOldMessageAndCallsNothing() throws Exception {
        moves(0, List.of());
        for (String[] r : new String[][]{{"2026-11-10", "2026-11-01"}, {"2000-01-01", "2026-11-01"}, {"2026-13-01", "2026-11-01"}, {"", "2026-11-01"}, {"11/01/2026", "2026-11-10"}}) {
            Answer a = get("/api/v1/yard-queries/car-history", Map.of("from", r[0], "to", r[1]));
            assertThat(a.status()).as(r[0] + ".." + r[1]).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).isEqualTo(BAD_RANGE);
        }
        assertThat(called()).isEmpty();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHYT29A", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("HISTORY", "", ""))), "00000000"));
        Answer a = get("/api/v1/yard-queries/car-history", Map.of("from", "2026-11-01", "to", "2026-11-10"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("HISTORY Is Not Found");
    }

    @Test
    void statusHistorySendsTheYardAndDatesAndListsTheChanges() throws Exception {
        backend().on("VHYT33A", in -> {
            crit.set(in.get("VHYT33A_CRIT"));
            return reply(0, 1, Map.of("VHYT33A_DATA", rows("dataRow", List.of(Map.of("carNo", "1001", "dateStatChg", "11-05-2026", "timeStatChg", "10:20:00",
                    "vehStatNew", "NO GO", "vehStatPrev", "GO", "yrdSubLoc", "S-1", "moveReasonCd", "B01", "moveReasonDesc", "BRAKES")))));
        });
        Answer a = get("/api/v1/yard-queries/status-history", Map.of("yard", "DY", "from", "2026-11-01", "to", "2026-11-10"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT33A");
        assertThat(crit.get()).containsEntry("yrdTrkgYrd", "DY").containsEntry("startDate", "2026-11-01").containsEntry("endDate", "2026-11-10")
                .containsEntry("fromCar", "").containsEntry("toCar", "");
        assertThat(a.body().at("/rows/0/newStatus").asText()).isEqualTo("NO GO");
        assertThat(a.body().at("/rows/0/previousStatus").asText()).isEqualTo("GO");
        assertThat(a.body().at("/rows/0/reason").asText()).isEqualTo("BRAKES");
    }

    @Test
    void statusHistoryNoRowsIsEmpty() throws Exception {
        backend().on("VHYT33A", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/yard-queries/status-history", Map.of("from", "2026-11-01", "to", "2026-11-10"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("rows").size()).isZero();
    }
}
