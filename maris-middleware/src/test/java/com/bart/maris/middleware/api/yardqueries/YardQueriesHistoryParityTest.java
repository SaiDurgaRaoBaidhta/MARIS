package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesHistory;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesHistoryService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The history reports: the clean API against the old VHYT29I / VHYT33I run by the engine, reached through VHYT27I (report 3 or
 * 4, the dates and car range typed, Display). The engine does not pass VHYT27I's yard (see YardQueriesShopStatusParityTest),
 * so both old reports fall back to HY; the API is asked for HY.
 */
class YardQueriesHistoryParityTest {
    private Parity p;

    private void backend() {
        p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        p.on("VHYT27B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHRT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHRT04A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "HY", "yrdDesc", "HAYWARD")))));
        List<Map<String, Object>> moves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> m = new HashMap<>(Map.of("carNo", i < 2 ? "1001" : "1002", "yrdSubLoc", "S-" + i, "posnNo", i + 1, "vehStat", "GO",
                    "vagenOrientation", "<", "dateUserMoved", "11-0" + (i + 1) + "-2026", "timeUserMoved", "10:2" + i + ":00",
                    "yrdSubLocPrev", i == 1 ? "" : "T-24", "moveReasonCdPrv", "R9" + i, "posnNoPrev", i));
            m.put("moveReasonDesc", "REASON " + i);
            m.put("moveReasonCdNew", "B0" + i);
            moves.add(m);
        }
        p.on("VHYT29A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", moves.size()), "VHYT29A_DATA", Map.of("dataRow", moves)));
        List<Map<String, Object>> changes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            changes.add(Map.of("carNo", i < 2 ? "1001" : "1002", "dateStatChg", i == 0 ? "11-01-2026" : "11-02-2026", "timeStatChg", "09:0" + i + ":00",
                    "vehStatNew", i % 2 == 0 ? "NO GO" : "GO", "vehStatPrev", "USABLE", "yrdSubLoc", "S-" + i, "moveReasonCd", "C0" + i, "moveReasonDesc", "WHY " + i));
        }
        p.on("VHYT33A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", changes.size()), "VHYT33A_DATA", Map.of("dataRow", changes)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (w.get("screen").toString().equalsIgnoreCase(screen)) return w;
        }
        throw new AssertionError(screen + " is not open");
    }

    /** the rows of the report's one container, each as its cells joined with "|" */
    @SuppressWarnings("unchecked")
    private static List<String> rows(ScreenSession s, String screen) {
        for (Map<String, Object> w : ((Map<String, Map<String, Object>>) window(s, screen).get("widgets")).values()) {
            if (w.get("rows") instanceof List<?> rows) {
                List<String> out = new ArrayList<>();
                for (Object r : rows) out.add(String.join("|", ((List<Object>) r).stream().map(x -> x == null ? "" : x.toString().strip()).toList()));
                return out;
            }
        }
        throw new AssertionError(screen + " has no rows");
    }

    /** VHYT27I for report 3 (car history) or 4 (yard history), dates and car range typed, Display */
    private ScreenSession display(int report, String from, String to, String fromCar, String toCar) {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT27I", p.user);
        String w = window(old, "VHYT27I").get("id").toString();
        ScreenInstance s = old.window(w);
        s.recordPart("VHYT27IW").set("REPORT-INDEX data", report);               // the menu's WHICH-REPORT (CAR-HIST / YARDHIST)
        s.recordPart("VHYT27IW-TRIGGERS").set("ENABLE-DISPLAY data", "Y");
        p.clear();
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", from), new ScreenSession.Change("Text2", "object", to),
                new ScreenSession.Change("Text11", "object", fromCar), new ScreenSession.Change("Text21", "object", toCar)), "Push_Button3", "clicked", null);
        return old;
    }

    @Test
    void carHistoryAsksForTheSameAndShowsTheSameMoves() {
        backend();
        ScreenSession old = display(3, "11-01-2026", "11-10-2026", "1001", "1200");
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHYT29A");
        assertThat(oldSent).as("the old report asked for the moves").isNotNull();
        List<String> oldRows = rows(old, "VHYT29I");

        p.clear();
        YardQueriesHistory.CarHistory r = new YardQueriesHistoryService(p.legacy(), p.messages(), Parity.bundle())
                .carHistory("HY", "2026-11-01", "2026-11-10", "1001", "1200");
        assertThat(p.lastSent("VHYT29A").get("VHYT29A_CRIT")).isEqualTo(oldSent.get("VHYT29A_CRIT"));
        assertThat(oldRows).hasSize(r.rows().size());
        for (int i = 0; i < oldRows.size(); i++) {
            YardQueriesHistory.Move m = r.rows().get(i);
            assertThat(oldRows.get(i)).as("row " + i).contains(m.track()).contains(m.previousTrack()).contains(m.dateMoved()).contains(m.reason())
                    .contains(m.reasonCode());
        }
    }

    @Test
    void aBlankCarRangeIsTheSameAsTheOldDefault() {
        backend();
        display(3, "11-01-2026", "11-10-2026", "", "");
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHYT29A");
        p.clear();
        new YardQueriesHistoryService(p.legacy(), p.messages(), Parity.bundle()).carHistory("HY", "2026-11-01", "2026-11-10", "", "");
        assertThat(p.lastSent("VHYT29A").get("VHYT29A_CRIT")).isEqualTo(oldSent.get("VHYT29A_CRIT"));
    }

    @Test
    void statusHistoryAsksForTheSameAndShowsTheSameChanges() {
        backend();
        ScreenSession old = display(4, "11-01-2026", "11-10-2026", "", "");
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHYT33A");
        assertThat(oldSent).as("the old report asked for the changes").isNotNull();
        List<String> oldRows = rows(old, "VHYT33I");

        p.clear();
        YardQueriesHistory.StatusHistory r = new YardQueriesHistoryService(p.legacy(), p.messages(), Parity.bundle())
                .statusHistory("HY", "2026-11-01", "2026-11-10");
        assertThat(p.lastSent("VHYT33A").get("VHYT33A_CRIT")).isEqualTo(oldSent.get("VHYT33A_CRIT"));
        assertThat(oldRows).hasSize(r.rows().size());
        for (int i = 0; i < oldRows.size(); i++) {
            YardQueriesHistory.StatusChange c = r.rows().get(i);
            assertThat(oldRows.get(i)).as("row " + i).contains(c.track()).contains(c.newStatus()).contains(c.previousStatus()).contains(c.reason())
                    .contains(c.timeChanged());
        }
    }
}
