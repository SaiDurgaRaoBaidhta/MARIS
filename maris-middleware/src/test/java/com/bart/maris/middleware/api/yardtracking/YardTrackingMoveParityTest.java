package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingMove;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMoveService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingOrientationService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingYardPrintService;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The car move window (old VHYT11I), the orientation window it opens (VHYT13I) and the yard car list hard copy (VHYT24I),
 * against the old screens run by the engine with the same scripted backend.
 */
class YardTrackingMoveParityTest {
    static final String GROUP = "BRIGHTCO";
    /** the engine's terminal id (maris.engine.terminal-id default), which the old windows record on updates */
    static final String TERMINAL = "MARIS-UI";
    static final String YARD = YardTrackingMoveBackend.YARD, TRACK = YardTrackingMoveBackend.TRACK, TO = YardTrackingMoveBackend.TO_TRACK;
    /** the old position buttons -1- .. -5- */
    static final String[] POS = {"Push_Button1", "Push_Button11", "Push_Button12", "Push_Button4", "Push_Button14"};

    @SuppressWarnings("unchecked")
    static String win(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return (String) w.get("id");
        return null;
    }

    /** VHYT11I with the values a yard map connects in (yard, track, move-to track), refreshed as after a move */
    static ScreenSession old(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT11I", p.user);
        String w = win(s, "VHYT11I");
        s.window(w).recordPart("VH01A01_DATA").set("TERM-ID data", TERMINAL);
        s.window(w).recordPart("VHYT08IW-PASSVALUE").set("YRD_TRKG_YRD data", YARD);
        s.window(w).recordPart("VHYT08IW-PASSVALUE").set("YRD-SUB-LOC data", TRACK);
        s.window(w).recordPart("VHYT11IW-PASSVALUE").set("FROM-YRD-SUB-LOC data", TRACK);
        s.window(w).recordPart("VHYT11IW-PASSVALUE").set("TO-YRD-SUB-LOC data", TO);
        p.clear();
        s.window(w).recordPart("VHYT11IW-TRIGGER").set("REFRESH-SW data", "Y");       // runs the opening groups again with them
        return s;
    }

    static YardTrackingMoveService api(Parity p) {
        return new YardTrackingMoveService(p.legacy(), p.messages(), Parity.bundle(), p.clock, TERMINAL);
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : calls) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    static String str(Object o) {
        return o == null ? "" : o.toString().stripTrailing();
    }

    static void samePositions(ScreenSession s, YardTrackingMove.Track t) {
        samePositions(s, t, true);
    }

    static void samePositions(ScreenSession s, YardTrackingMove.Track t, boolean colors) {
        RecordPart one = s.window(win(s, "VHYT11I")).recordPart("VHYT08IW-1"), color = s.window(win(s, "VHYT11I")).recordPart("VHYT11IW-COLOR");
        for (YardTrackingMove.Position x : t.positions()) {
            int n = x.slot();
            assertThat(x.carShown()).as("car " + n).isEqualTo(str(one.get("CAR-NO-" + n + " data")));
            assertThat(x.status()).as("status " + n).isEqualTo(str(one.get("VEH-STAT-" + n + " data")));
            assertThat(x.homeYard()).as("home " + n).isEqualTo(str(one.get("HOME-YRD-" + n + " data")));
            assertThat(x.pseudoTrain()).as("pseudo " + n).isEqualTo(str(one.get("CON-" + n + " data")).strip());
            if (colors) assertThat(x.color()).as("color " + n).isEqualTo(str(color.get("CAR-" + n + "-COLOR data")).strip());
        }
    }

    @Test
    void theTrackMatchesTheOldWindow() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = old(p);
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        YardTrackingMove.Track t = api(p).track(YARD, TRACK, TO, 1, GROUP);
        assertThat(p.sent.stream().map(Map.Entry::getKey).distinct().toList()).isEqualTo(oldCalls.stream().map(Map.Entry::getKey).distinct().toList());
        for (String[] pr : new String[][] {{"VHYT08B", "VHYT08B_CRIT"}, {"VHYT08A", "VHYT08A_CRIT"}, {"VH01A02", "VH01A02_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1]).stream().distinct().toList()).as(pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]).stream().distinct().toList());
        }
        // VHYT11A: the fields the window sets (the test's old window was opened once blank, so VHYT11A got back its first answer)
        @SuppressWarnings("unchecked") Map<String, Object> was = (Map<String, Object>) sent(oldCalls, "VHYT11A", "VHYT11A_CRIT").get(0),
                now = (Map<String, Object>) sent(p.sent, "VHYT11A", "VHYT11A_CRIT").get(0);
        for (String f : List.of("yrdTrkgYrd", "yrdSubLoc")) assertThat(now.get(f)).as("VHYT11A " + f).isEqualTo(was.get(f));
        // the test's refresh ends with VHYT11IS-CLEAR-1 (every car grey); a window opened from a map keeps the colours the load
        // paints: a car whose previous status is USABLE is sky blue (VHYT11I-LOAD-DATA), as the API returns
        samePositions(s, t, false);
        assertThat(t.positions().get(1).color()).isNotEqualTo(t.positions().get(0).color());
        assertThat(t.subLocationDescription()).isEqualTo("TRACK TWELVE");
        assertThat(t.moveTo()).extracting(YardTrackingMove.Location::code).containsExactly(TO, "T-14");
        assertThat(t.toSubLocation()).isEqualTo(TO);
        assertThat(t.positions().get(0).carShown()).contains("1101");
    }

    /** the old window after positions clicked, the move-to track chosen and a button pressed */
    static ScreenSession act(Parity p, List<Integer> slots, String button) {
        ScreenSession s = old(p);
        String w = win(s, "VHYT11I");
        for (int slot : slots) s.event(w, List.of(), POS[slot - 1], "clicked", null);
        p.clear();
        s.event(w, List.of(), button, "clicked", null);
        return s;
    }

    @Test
    void clickingPositionsColoursThemAsTheOldWindow() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = old(p);
        String w = win(s, "VHYT11I");
        s.event(w, List.of(), POS[1], "clicked", null);
        s.event(w, List.of(), POS[3], "clicked", null);
        YardTrackingMoveService svc = api(p);
        YardTrackingMove.Result r = svc.act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(2, 4), "print", null), GROUP);
        samePositions(s, r.track());
        assertThat(r.track().positions().get(1).color()).isEqualTo(svc.selectedColor());
        assertThat(r.opens()).isEqualTo("VHYT02I");
    }

    @Test
    void moveSendsTheSameRecordsAsTheOldWindow() {
        Parity p = YardTrackingMoveBackend.backend("NOT");
        ScreenSession s = act(p, List.of(2, 4), "Push_Button3");
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = str(s.window(win(s, "VHYT11I")).recordPart("VHYT11IW-MESSAGE").get("MESSAGE data")).strip();
        assertThat(sent(oldCalls, "VHYT11B", "VHYT11B_CRIT")).as("the old window moved").isNotEmpty();
        p.clear();
        YardTrackingMove.Result r = api(p).act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(2, 4), "move", null), GROUP);
        for (String[] pr : new String[][] {{"VHYT11C", "VHYT11C_CRIT"}, {"VHYT11B", "VHYT11B_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]));
        }
        assertThat(r.message()).isEqualTo(oldMessage);
        assertThat(r.question()).isNull();
    }

    @Test
    void movingPartOfAPseudoTrainAsksFirstAndMovesOnYes() {
        Parity p = YardTrackingMoveBackend.backend("SPLIT");
        act(p, List.of(2, 4), "Push_Button3");
        assertThat(sent(p.sent, "VHYT11B", "VHYT11B_CRIT")).as("the old window waits for the answer").isEmpty();
        p.clear();
        YardTrackingMoveService svc = api(p);
        YardTrackingMove.Result asked = svc.act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(2, 4), "move", null), GROUP);
        assertThat(asked.question()).contains("201");
        assertThat(sent(p.sent, "VHYT11B", "VHYT11B_CRIT")).isEmpty();
        p.clear();
        svc.act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(2, 4), "move", true), GROUP);
        assertThat(sent(p.sent, "VHYT11B", "VHYT11B_CRIT")).hasSize(1);
    }

    @Test
    void moveChecksMatchTheOldWindow() {
        for (List<Integer> slots : List.of(List.<Integer>of(), List.of(1, 2, 3))) {
            Parity p = YardTrackingMoveBackend.backend("NOT");
            ScreenSession s = act(p, slots, "Push_Button3");
            String oldMessage = str(s.window(win(s, "VHYT11I")).recordPart("VHYT11IW-MESSAGE").get("MESSAGE data")).strip();
            assertThat(oldMessage).as("old message for " + slots).isNotBlank();
            p.clear();
            YardTrackingMove.Result r = api(p).act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, slots, "move", null), GROUP);
            assertThat(r.message()).as("message for " + slots).isEqualTo(oldMessage);
            assertThat(sent(p.sent, "VHYT11B", "VHYT11B_CRIT")).isEmpty();
        }
    }

    @Test
    void reverseSendsTheSameRecordsAsTheOldWindow() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = act(p, List.of(2, 4), "Push_Button31");
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = str(s.window(win(s, "VHYT11I")).recordPart("VHYT11IW-MESSAGE").get("MESSAGE data")).strip();
        assertThat(sent(oldCalls, "VHYT11F", "VHYT11F_CRIT")).as("the old window reversed").isNotEmpty();
        p.clear();
        YardTrackingMove.Result r = api(p).act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(2, 4), "reverse", null), GROUP);
        assertThat(sent(p.sent, "VHYT11F", "VHYT11F_CRIT")).isEqualTo(sent(oldCalls, "VHYT11F", "VHYT11F_CRIT"));
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @Test
    void orientationOpensTheOrientationWindowWithTheSameCar() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = act(p, List.of(3), "Push_Button9");
        String o = win(s, "VHYT13I");
        assertThat(o).as("VHYT13I opened").isNotNull();
        RecordPart pv = s.window(o).recordPart("VHYT13IW-PASSVALUE");
        p.clear();
        YardTrackingMove.Result r = api(p).act(new YardTrackingMove.Action(YARD, TRACK, TO, 1, List.of(3), "orientation", null), GROUP);
        assertThat(r.opens()).isEqualTo("VHYT13I");
        assertThat(r.with().get("carNo")).isEqualTo(str(pv.get("CAR-NO data")).strip());
        assertThat(String.valueOf(r.with().get("posnNo"))).isEqualTo(str(pv.get("POSN-NO data")).strip());
        assertThat(r.with().get("yrdSubLoc")).isEqualTo(str(pv.get("YRD-SUB-LOC data")));
    }

    static YardTrackingOrientationService orientation(Parity p) {
        return new YardTrackingOrientationService(p.legacy(), p.messages(), Parity.bundle(), p.clock, TERMINAL);
    }

    /** VHYT13I as VHYT11I opens it for position 3 (car 1103) */
    static ScreenSession oldOrientation(Parity p) {
        ScreenSession s = act(p, List.of(3), "Push_Button9");
        assertThat(win(s, "VHYT13I")).isNotNull();
        return s;
    }

    @Test
    void theOrientationWindowMatches() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = oldOrientation(p);
        var oldCalls = new ArrayList<>(p.sent);
        String w = win(s, "VHYT13I");
        String oldMessage = str(s.window(w).recordPart("VHYT13IW-MSG").get("FINAL-MESSAGE data")).strip();
        String oldCar = str(s.window(w).recordPart("VHYT13IW").get("CAR-NO data")).strip();
        p.clear();
        YardTrackingMove.Orientation o = orientation(p).orientation(YARD, TRACK, "1103");
        for (String[] pr : new String[][] {{"VHYT13A", "VHYT13A_CRIT"}, {"VHYT13B", "VHYT13B_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1]).stream().distinct().toList()).as(pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]).stream().distinct().toList());
        }
        assertThat(o.car()).isEqualTo(oldCar);
        assertThat(o.message()).isEqualTo(oldMessage);
        assertThat(o.orientations()).containsExactly("<", ">");
        assertThat(o.cars()).hasSize(3);
    }

    @Test
    void changingTheOrientationSendsTheSameRecords() {
        Parity p = YardTrackingMoveBackend.backend();
        ScreenSession s = oldOrientation(p);
        String w = win(s, "VHYT13I");
        p.clear();
        s.event(w, List.of(), "Drop-down_List1", "select", 2);                       // the other mark
        s.event(w, List.of(), "pbChange", "clicked", null);
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = str(s.window(w).recordPart("VHYT13IW-MSG").get("FINAL-MESSAGE data")).strip();
        assertThat(sent(oldCalls, "VHYT13C", "VHYT13C_DATA")).as("the old window wrote").isNotEmpty();
        p.clear();
        YardTrackingMove.Orientation o = orientation(p).change(YARD, TRACK, "1103", ">");
        for (String[] pr : new String[][] {{"VHYT13E", "VHYT13E_CRIT"}, {"VHYT13C", "VHYT13C_CRIT"}, {"VHYT13C", "VHYT13C_DATA"}, {"VHYT13D", "VHYT13D_CRIT"},
                {"VHYT13D", "VHYT13D_DATA"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0] + " " + pr[1]).isEqualTo(sent(oldCalls, pr[0], pr[1]));
        }
        assertThat(o.message()).isEqualTo(oldMessage);
    }

    @Test
    void theYardPrintSendsTheSameRecords() {
        for (String yard : List.of("HY", "DPX", "CY", "MBX")) {
            Parity p = YardTrackingMoveBackend.backend();
            ScreenSession s = p.oldScreens();
            s.openRoot("VHYT24I", p.user);
            String w = win(s, "VHYT24I");
            s.window(w).recordPart("VHYT24IW-PASSVALUE").set("YRD-TRKG-YRD data", yard);
            s.event(w, List.of(), "cntPrinterdet", "select", 2);
            p.clear();
            s.event(w, List.of(), "Push_Button21", "clicked", null);
            var oldCalls = new ArrayList<>(p.sent);
            assertThat(oldCalls).as("old print for " + yard).isNotEmpty();
            p.clear();
            YardTrackingYardPrintService svc = new YardTrackingYardPrintService(p.legacy(), p.messages(), Parity.bundle(), p.clock);
            assertThat(svc.options(yard).printers()).extracting(YardTrackingMove.Printer::name).containsExactly("DUBLNHP1", "LKS11HP1");
            p.clear();
            svc.print(yard, "LKS11HP1");
            assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).as(yard).isEqualTo(oldCalls.stream().map(Map.Entry::getKey).toList());
            String program = oldCalls.get(0).getKey();
            assertThat(p.sent.get(0).getValue().get(program + "_CRIT")).as(yard + " " + program).isEqualTo(oldCalls.get(0).getValue().get(program + "_CRIT"));
        }
    }
}
