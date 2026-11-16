package com.bart.maris.middleware.api.yardtracking;

import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.old;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.sent;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.str;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.win;
import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackReasonService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A car's move reason (old VHYT06I), opened by Change Reason on the track window, against the old screens run by the engine. */
class YardTrackingTrackReasonParityTest {
    static YardTrackingTrackReasonService api(Parity p) {
        return new YardTrackingTrackReasonService(p.legacy(), p.messages(), Parity.bundle(), YardTrackingTrackParityTest.TERMINAL);
    }

    /** the track window opened, position 3 clicked, Change Reason: the reason window */
    static String openReason(ScreenSession s) {
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button32", "clicked", null);
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        return win(s, "VHYT06I");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> widgets(ScreenSession s, String id) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (id.equals(w.get("id"))) return (Map<String, Map<String, Object>>) w.get("widgets");
        return Map.of();
    }

    @Test
    void openingReadsTheSameAndShowsTheSame() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        p.clear();
        String r = openReason(s);
        var oldCalls = new ArrayList<>(p.sent);
        Map<String, Map<String, Object>> w = widgets(s, r);
        p.clear();
        var v = api(p).view("SW", YardTrackingTrackBackend.TRACK, "1103", 3);
        for (String[] pr : new String[][] {{"VHYT06A", "VHYT06A_CRIT"}, {"VHYT06B", "VHYT06B_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]));
        }
        assertThat(v.car().car()).isEqualTo(str(w.get("Text1").get("value")).strip());
        assertThat(v.car().status()).isEqualTo(str(w.get("Text7").get("value")).strip());
        @SuppressWarnings("unchecked") List<String> items = (List<String>) w.get("Drop-down_List1").get("items");
        // the engine draws these items as the MOVE-REASON group objects (a drop-down fed from a DATA_ROW group column, an engine
        // gap); how many there are is what can be compared
        assertThat(items).hasSize(v.reasons().size());
    }

    @Test
    void updateWritesTheSameRecordsAndSaysTheSame() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String r = openReason(s);
        s.event(r, List.of(), "Drop-down_List1", "select", 2);                      // M2
        s.event(r, List.of(), "Drop-down_List1", "listDroppedDown", null);
        p.clear();
        s.event(r, List.of(), "Push_Button1", "clicked", null);
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = str(widgets(s, r).get("Text6").get("value")).strip();
        assertThat(sent(oldCalls, "VHYT06D", "VHYT06D_CRIT")).as("the old window updated: " + oldCalls.stream().map(Map.Entry::getKey).toList()).isNotEmpty();
        p.clear();
        var c = api(p).change(new YardTrackingTrackReasonService.Change("SW", YardTrackingTrackBackend.TRACK, "1103", 3, "M2"), YardTrackingTrackParityTest.GROUP);
        for (String[] pr : new String[][] {{"VHYT06C", "VHYT06C_CRIT"}, {"VHYT06D", "VHYT06D_CRIT"}, {"VHYT06D", "VHYT06D_DATA"}, {"VHYT06E", "VHYT06E_CRIT"},
                {"VHYT06E", "VHYT06E_DATA"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0] + " " + pr[1]).isEqualTo(sent(oldCalls, pr[0], pr[1]));
        }
        // the old window builds "Reason code M1 for car 1103 has been changed to M2", then reloads the car; the reload re-selects
        // the drop-down, which runs VHYT06IS-VALID and replaces the message with its "select a new reason" text. The API keeps the
        // update's own message (a deliberate difference); the history it is built from is checked here.
        assertThat(oldMessage).isEqualTo(com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackEgl.first(Parity.bundle(), "VHYT06IS-VALID", "MRSYardTracking", "FINAL-MESSAGE"));
        assertThat(c.message()).startsWith(com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackEgl.first(Parity.bundle(), "VHYT06IS-CHANGE", "MRSYardTracking", "FINAL-MSG-1"))
                .contains("M1").contains("1103").endsWith("M2");
        assertThat(c.updated()).isTrue();
    }

    @Test
    void theSameReasonIsNotWritten() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String r = openReason(s);
        s.event(r, List.of(), "Drop-down_List1", "select", 1);                      // M1, the car's own
        s.event(r, List.of(), "Drop-down_List1", "listDroppedDown", null);
        p.clear();
        s.event(r, List.of(), "Push_Button1", "clicked", null);
        String oldMessage = str(widgets(s, r).get("Text6").get("value")).strip();
        assertThat(sent(p.sent, "VHYT06D", "VHYT06D_CRIT")).isEmpty();
        var c = api(p).change(new YardTrackingTrackReasonService.Change("SW", YardTrackingTrackBackend.TRACK, "1103", 3, "M1"), YardTrackingTrackParityTest.GROUP);
        assertThat(c.message()).isEqualTo(oldMessage);
        assertThat(c.updated()).isFalse();
    }
}
