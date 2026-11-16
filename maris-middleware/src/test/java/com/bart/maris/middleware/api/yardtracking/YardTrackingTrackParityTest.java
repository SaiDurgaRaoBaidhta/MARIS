package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrack;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackService;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The track window (old VHYT08I) against the old screen run by the engine: opened from the system-wide window (VHSW01I) on a
 * track, as in the old application, then positions clicked and a button pressed.
 */
class YardTrackingTrackParityTest {
    static final String GROUP = "BRIGHTCO";
    /** the engine's terminal id (maris.engine.terminal-id default), which the old windows record on updates */
    static final String TERMINAL = "MARIS-UI";

    @SuppressWarnings("unchecked")
    static String win(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return (String) w.get("id");
        return null;
    }

    /** VHSW01I opened from the menu, a track clicked: VHYT08I opens on yard "SW" and that track */
    static ScreenSession old(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSW01I", p.user);
        p.clear();
        s.window(win(s, "VHSW01I")).recordPart("VHSW01IW-PARM").set("TRACK-LOC data", YardTrackingTrackBackend.TRACK);
        assertThat(win(s, "VHYT08I")).as("VHYT08I opened").isNotNull();
        return s;
    }

    static YardTrackingTrackService api(Parity p) {
        return new YardTrackingTrackService(p.legacy(), p.messages(), Parity.bundle(), p.clock, TERMINAL);
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : calls) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    static void sameCalls(List<Map.Entry<String, Map<String, Map<String, Object>>>> old, List<Map.Entry<String, Map<String, Map<String, Object>>>> now) {
        assertThat(now.stream().map(Map.Entry::getKey).distinct().toList()).isEqualTo(old.stream().map(Map.Entry::getKey).distinct().toList());
        for (String[] pr : new String[][] {{"VHYT08D", "VHYT08D_CRIT"}, {"VHYT08B", "VHYT08B_CRIT"}, {"VHYT08F", "VHYT08F_CRIT"}, {"VHYT08H", "VHYT08H_CRIT"},
                {"VHYT08C", "VHYT08C_CRIT"}, {"VHYT08A", "VHYT08A_CRIT"}, {"VH01A02", "VH01A02_CRIT"}, {"VHYT08G", "VHYT08G_CRIT"}, {"VHYT08G", "VHYT08G_DATA"}}) {
            assertThat(sent(now, pr[0], pr[1]).stream().distinct().toList()).as(pr[0] + " " + pr[1]).isEqualTo(sent(old, pr[0], pr[1]).stream().distinct().toList());
        }
    }

    static void samePositions(ScreenSession s, YardTrackingTrack.Track t) {
        RecordPart one = s.window(win(s, "VHYT08I")).recordPart("VHYT08IW-1"), three = s.window(win(s, "VHYT08I")).recordPart("VHYT08IW-3");
        for (YardTrackingTrack.Position x : t.positions()) {
            int n = x.slot();
            assertThat(x.carShown()).as("car " + n).isEqualTo(str(one.get("CAR-NO-" + n + " data")));
            assertThat(x.status()).as("status " + n).isEqualTo(str(one.get("VEH-STAT-" + n + " data")));
            assertThat(x.homeYard()).as("home " + n).isEqualTo(str(one.get("HOME-YRD-" + n + " data")));
            assertThat(x.pseudoTrain()).as("pseudo " + n).isEqualTo(str(one.get("CON-" + n + " data")).strip());
            assertThat(x.position()).as("position " + n).isEqualTo(Integer.parseInt(str(three.get("LABEL-" + n + "-CENTER data")).strip()));
        }
    }

    static String str(Object o) {
        return o == null ? "" : o.toString().stripTrailing();
    }

    @Test
    void theTrackMatchesTheOldWindow() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        YardTrackingTrack.Track t = api(p).track("SW", YardTrackingTrackBackend.TRACK, 1, GROUP);
        sameCalls(oldCalls, p.sent);
        samePositions(s, t);
        assertThat(t.positions().get(1).status()).isEqualTo("GO    TI");
        assertThat(t.positions().get(1).usable()).isTrue();
        assertThat(t.positions().get(0).alert()).isTrue();
        assertThat(t.subLocationDescription()).isEqualTo("TRACK TWELVE");
        assertThat(t.locations()).extracting(YardTrackingTrack.Location::code).containsExactly("T-13", "SHOP1");
        assertThat(t.canMoveCar()).isTrue();
        assertThat(t.canFixOrder()).isTrue();
        assertThat(t.next()).isFalse();
    }

    @Test
    void theSecondPageMatches() {
        Parity p = YardTrackingTrackBackend.backend(35);
        ScreenSession s = old(p);
        s.event(win(s, "VHYT08I"), List.of(), "Push_Button21", "clicked", null);
        p.clear();
        YardTrackingTrack.Track t = api(p).track("SW", YardTrackingTrackBackend.TRACK, 2, GROUP);
        samePositions(s, t);
        assertThat(t.page()).isEqualTo(2);
        assertThat(t.previous()).isTrue();
    }

    @Test
    void changeReasonOpensTheReasonWindowWithTheSameValues() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button32", "clicked", null);                 // position 3
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        String reason = win(s, "VHYT06I");
        assertThat(reason).as("VHYT06I opened").isNotNull();
        RecordPart pv = s.window(reason).recordPart("VHYT06IW-PASSVALUE");
        p.clear();
        YardTrackingTrack.Result r = api(p).act(new YardTrackingTrack.Action("SW", YardTrackingTrackBackend.TRACK, 1, List.of(3), "change-reason", null), GROUP);
        assertThat(r.opens()).isEqualTo("VHYT06I");
        assertThat(r.with().get("carNo")).isEqualTo(str(pv.get("CAR-NO data")));
        assertThat(String.valueOf(r.with().get("posnNo"))).isEqualTo(str(pv.get("POSN-NO data")).strip());
        assertThat(r.with().get("yrdSubLoc")).isEqualTo(str(pv.get("YRD-SUB-LOC data")));
    }

    @Test
    void fixOrderOpensTheFixWindowWithTheSamePosition() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button33", "clicked", null);                 // position 4
        s.event(w, List.of(), "Push_Button412", "clicked", null);
        String fix = win(s, "VHYT14I");
        assertThat(fix).as("VHYT14I opened").isNotNull();
        // VHYT14IS-LOAD-DATA starts on the position passed in, then clears the pass value: its start index is what was passed
        String started = str(s.window(fix).recordPart("VHYT14IW-X").get("INIT-CAR-INDEX data")).strip();
        p.clear();
        YardTrackingTrack.Result r = api(p).act(new YardTrackingTrack.Action("SW", YardTrackingTrackBackend.TRACK, 1, List.of(4), "fix-order", null), GROUP);
        assertThat(r.opens()).isEqualTo("VHYT14I");
        assertThat(String.valueOf(r.with().get("posnNo"))).isEqualTo(started);
    }

    @Test
    void moveCarSendsTheSameMove() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button31", "clicked", null);                 // position 2
        s.event(w, List.of(), "Push_Button33", "clicked", null);                 // position 4
        s.event(w, List.of(), "Drop-down_List1", "select", 1);
        p.clear();
        s.event(w, List.of(), "Push_Button42", "clicked", null);
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = str(s.window(w).recordPart("VHYT08IW-MESSAGE").get("MESSAGE data"));
        assertThat(sent(oldCalls, "VHYT08G", "VHYT08G_CRIT")).as("the old window moved: " + oldCalls.stream().map(Map.Entry::getKey).toList()).isNotEmpty();
        p.clear();
        YardTrackingTrack.Result r = api(p).act(new YardTrackingTrack.Action("SW", YardTrackingTrackBackend.TRACK, 1, List.of(2, 4), "move-car", "T-13"), GROUP);
        assertThat(sent(p.sent, "VHYT08G", "VHYT08G_CRIT")).isEqualTo(sent(oldCalls, "VHYT08G", "VHYT08G_CRIT"));
        assertThat(sent(p.sent, "VHYT08G", "VHYT08G_DATA")).isEqualTo(sent(oldCalls, "VHYT08G", "VHYT08G_DATA"));
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @Test
    void changeReasonWithNoCarSelectedSaysWhatTheOldWindowSays() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        String oldMessage = str(s.window(w).recordPart("VHYT08IW-MESSAGE").get("MESSAGE data"));
        assertThat(oldMessage).isNotBlank();
        YardTrackingTrack.Result r = api(p).act(new YardTrackingTrack.Action("SW", YardTrackingTrackBackend.TRACK, 1, List.of(), "change-reason", null), GROUP);
        assertThat(r.message()).isEqualTo(oldMessage);
        assertThat(r.opens()).isNull();
    }

    @Test
    void aGroupOfAnotherYardCannotChangeReasons() {
        Parity p = YardTrackingTrackBackend.backend();
        p.on("VHYT08D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08D_DATA", Map.of("yrdTrkgYrd", "RY", "pseudoYrdInd", "N")));
        ScreenSession s = old(p);
        @SuppressWarnings("unchecked") Map<String, Object> button = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) ((List<Map<String, Object>>) s.state()
                .get("windows")).get(1)).get("widgets")).get("Push_Button1");
        assertThat(button.get("enabled")).isEqualTo(false);
        YardTrackingTrack.Track t = api(p).track("SW", YardTrackingTrackBackend.TRACK, 1, GROUP);
        assertThat(t.userMayChange()).isFalse();
        assertThatThrownBy(() -> api(p).act(new YardTrackingTrack.Action("SW", YardTrackingTrackBackend.TRACK, 1, List.of(3), "change-reason", null), GROUP))
                .isInstanceOf(InvalidRequestException.class);
    }
}
