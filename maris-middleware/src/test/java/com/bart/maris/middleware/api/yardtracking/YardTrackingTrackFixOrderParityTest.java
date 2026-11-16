package com.bart.maris.middleware.api.yardtracking;

import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.old;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.sent;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.str;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.win;
import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackFixOrderService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fix the order of cars (old VHYT14I), opened by Fix Order on the track window, against the old screens run by the engine. */
class YardTrackingTrackFixOrderParityTest {
    static YardTrackingTrackFixOrderService api(Parity p) {
        return new YardTrackingTrackFixOrderService(p.legacy(), Parity.bundle(), p.clock, YardTrackingTrackParityTest.TERMINAL);
    }

    /** the track window, position 4 clicked, Fix Order: the fix window */
    static String openFix(ScreenSession s) {
        String w = win(s, "VHYT08I");
        s.event(w, List.of(), "Push_Button33", "clicked", null);
        s.event(w, List.of(), "Push_Button412", "clicked", null);
        return win(s, "VHYT14I");
    }

    static String message(ScreenSession s, String id) {
        return str(s.window(id).recordPart("VHYT14IW-MSG").get("FINAL-MESSAGE data")).strip();
    }

    static final String[][] WRITES = {{"VHYT14B", "VHYT14B_CRIT"}, {"VHYT14F", "VHYT14F_CRIT"}, {"VHYT14F", "VHYT14F_DATA"}, {"VHYT14D", "VHYT14D_CRIT"},
            {"VHYT14D", "VHYT14D_DATA"}, {"VHYT14C", "VHYT14C_CRIT"}, {"VHYT14C", "VHYT14C_DATA"}, {"VHYT14E", "VHYT14E_CRIT"}, {"VHYT14E", "VHYT14E_DATA"},
            {"VHYT14A", "VHYT14A_CRIT"}};

    /** the old window's calls after Fix Order, and the API's after the track read it starts with (the window was already open) */
    static void sameWrites(List<Map.Entry<String, Map<String, Map<String, Object>>>> old, List<Map.Entry<String, Map<String, Map<String, Object>>>> api) {
        List<Map.Entry<String, Map<String, Map<String, Object>>>> now = !api.isEmpty() && api.get(0).getKey().equals("VHYT14A") ? api.subList(1, api.size()) : api;
        assertThat(now.stream().map(Map.Entry::getKey).toList()).isEqualTo(old.stream().map(Map.Entry::getKey).toList());
        for (String[] pr : WRITES) assertThat(sent(now, pr[0], pr[1])).as(pr[0] + " " + pr[1]).isEqualTo(sent(old, pr[0], pr[1]));
    }

    @Test
    void openingListsTheSameCars() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        p.clear();
        String f = openFix(s);
        assertThat(f).as("VHYT14I opened").isNotNull();
        var oldCalls = new ArrayList<>(p.sent);
        long selected = Long.parseLong(str(s.window(f).recordPart("VHYT14IW-X").get("INIT-CAR-INDEX data")).strip());
        p.clear();
        var v = api(p).view("SW", YardTrackingTrackBackend.TRACK, 4);
        assertThat(sent(p.sent, "VHYT14A", "VHYT14A_CRIT")).isEqualTo(sent(oldCalls, "VHYT14A", "VHYT14A_CRIT"));
        assertThat(v.selected()).isEqualTo((int) selected);
        assertThat(v.cars()).extracting(YardTrackingTrackFixOrderService.Car::car).containsExactly("1101", "1102", "1103", "1104", "1105");
    }

    @Test
    void movingACarBeforeAnotherWritesTheSame() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String f = openFix(s);
        s.event(f, List.of(), "Drop-down_List2", "select", 4);                    // car to move: 1104
        s.event(f, List.of(), "Drop-down_List1", "select", 3);                    // before 1103
        p.clear();
        s.event(f, List.of(), "pbChangeOrder", "clicked", null);
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = message(s, f);
        assertThat(sent(oldCalls, "VHYT14D", "VHYT14D_CRIT")).as("old moved: " + oldCalls.stream().map(Map.Entry::getKey).toList() + " " + oldMessage).isNotEmpty();
        p.clear();
        var r = api(p).fix(new YardTrackingTrackFixOrderService.Fix("SW", YardTrackingTrackBackend.TRACK, 4, 4, 3, false, false));
        sameWrites(oldCalls, p.sent);
        assertThat(r.message()).isEqualTo(oldMessage);
        assertThat(r.moved()).isTrue();
    }

    @Test
    void aCarAlreadyInPlaceIsNotMoved() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String f = openFix(s);
        s.event(f, List.of(), "Drop-down_List2", "select", 3);                    // 1103 before 1104: already there
        s.event(f, List.of(), "Drop-down_List1", "select", 4);
        p.clear();
        s.event(f, List.of(), "pbChangeOrder", "clicked", null);
        String oldMessage = message(s, f);
        assertThat(p.sent).isEmpty();
        var r = api(p).fix(new YardTrackingTrackFixOrderService.Fix("SW", YardTrackingTrackBackend.TRACK, 4, 3, 4, false, false));
        assertThat(r.message()).isEqualTo(oldMessage).isNotBlank();
        assertThat(r.moved()).isFalse();
    }

    @Test
    void movingACarOutOfAPseudoTrainAsksFirstThenResetsIt() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        String f = openFix(s);
        s.event(f, List.of(), "Drop-down_List2", "select", 1);                    // 1101 (pseudo train 201) before 1104
        s.event(f, List.of(), "Drop-down_List1", "select", 4);
        p.clear();
        s.event(f, List.of(), "pbChangeOrder", "clicked", null);
        @SuppressWarnings("unchecked") Map<String, Object> dialog = (Map<String, Object>) s.state().get("dialog");
        assertThat(dialog).as("the old window asks").isNotNull();
        String question = String.valueOf(dialog.get("message"));
        var oldClick = new ArrayList<>(p.sent);
        p.clear();
        var r = api(p).fix(new YardTrackingTrackFixOrderService.Fix("SW", YardTrackingTrackBackend.TRACK, 4, 1, 4, false, false));
        assertThat(r.question()).isNotNull();
        assertThat(r.question().replaceAll("\\s+", " ").strip()).isEqualTo(question.replaceAll("\\s+", " ").strip());
        p.clear();
        s.answer(((Number) dialog.get("id")).intValue(), String.valueOf(((List<?>) dialog.get("buttons")).get(0)));
        var oldCalls = new ArrayList<>(oldClick);
        oldCalls.addAll(p.sent);
        p.clear();
        var yes = api(p).fix(new YardTrackingTrackFixOrderService.Fix("SW", YardTrackingTrackBackend.TRACK, 4, 1, 4, false, true));
        assertThat(sent(oldCalls, "VHYT14F", "VHYT14F_CRIT")).isNotEmpty();
        sameWrites(oldCalls, p.sent);
        assertThat(yes.message()).isEqualTo(message(s, win(s, "VHYT14I")));
    }
}
