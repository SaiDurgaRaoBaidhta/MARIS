package com.bart.maris.middleware.api.yardtracking;

import static com.bart.maris.middleware.api.yardtracking.YardTrackingMiscParityTest.sent;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingMiscParityTest.value;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingMiscParityTest.window;
import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingOverridesService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Old VHYT34I (make / break overrides) run by the engine against the clean API on the same scripted backend: the window
 * as it opens (programs called, records sent, time lists, trains, statuses), Make/Break Overrides (VHYT34F) and Single
 * Train Override (VHYT34G): the records every program gets and the message.
 */
class YardTrackingOverridesParityTest {
    static final List<String> READS = List.of("VHYT34A", "VHYT34C", "VHYT34D", "VHYT34H", "VHYT34N");

    static List<Map<String, Object>> times(String... t) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String x : t) out.add(Map.of("timeOfArrival", x));
        return out;
    }

    static Parity backend() {
        Parity p = new Parity();
        p.on("VHYT34A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT34A_DATA", Map.of("currentDate", "2026-11-15",
                "currentTime", "10:00:00", "timeRevEnd", "03:00:00", "yesterday", "2026-11-14")));
        p.on("VHYT34B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT34B_DATA", Map.of("cntDayOfWeek", 2)));
        p.on("VHYT34C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT34C_DATA", Map.of("schedCd", "WD", "dateSchedRev", "2026-11-15")));
        p.on("VHYT34D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT34D_CRIT", Map.of("maCnt", 2, "baCnt", 2, "mpCnt", 2, "bpCnt", 1),
                "VHYT34D_DATA", Map.of("dataRow", times("05:00:00", "06:00:00")), "VHYT34D_BA_DATA", Map.of("dataRow", times("07:00:00", "08:00:00")),
                "VHYT34D_MP_DATA", Map.of("dataRow", times("15:00:00", "16:00:00")), "VHYT34D_BP_DATA", Map.of("dataRow", times("18:00:00", "19:00:00"))));
        p.on("VHYT34H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT34H_DATA", Map.of("dataRow", List.of(Map.of("conNo", "201"), Map.of("conNo", "305")))));
        p.on("VHYT34J", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT34J_DATA", Map.of("dataRow", times("09:10:00", "17:20:00"))));
        p.on("VHYT34K", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT34K_DATA", Map.of("bldUpReductnCd", "MA")));
        p.on("VHYT34N", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT34N_DATA", Map.of("dataRow", List.of(Map.of("conNoFrom", "201",
                "timeOfArrivalFr", "09:10:00", "breakSched", "Y", "breakCancel", "N", "makeSched", "N", "makeCancel", "Y")))));
        for (String pgm : List.of("VHYT34E", "VHYT34F", "VHYT34G", "VHYT34L", "VHYT34M", "VHYT34P")) p.on(pgm, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        return p;
    }

    /** the terminal the engine's sign-on gives every old window */
    static final String TERMINAL = "MARIS-UI";

    static YardTrackingOverridesService api(Parity p) {
        return new YardTrackingOverridesService(p.legacy(), Parity.bundle(), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static List<Object> withoutSchedFor(List<Object> sent) {
        List<Object> out = new ArrayList<>();
        for (Object o : sent) {
            Map<String, Object> m = new java.util.HashMap<>((Map<String, Object>) o);
            Map<String, Object> d = new java.util.HashMap<>((Map<String, Object>) m.get("VHYT34G_DATA"));
            d.remove("schedFor");
            m.put("VHYT34G_DATA", d);
            out.add(m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<String> items(ScreenSession s, String widget) {
        Map<String, Object> w = (Map<String, Object>) ((Map<String, Object>) window(s, "VHYT34I").get("widgets")).get(widget);
        List<String> out = new ArrayList<>();
        for (Object o : (List<Object>) w.getOrDefault("items", List.of())) if (o != null && !o.toString().isBlank()) out.add(o.toString().strip());
        return out;
    }

    @Test
    void windowOpensLikeTheOldScreen() {
        Parity p = backend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT34I", p.user);
        var oldSent = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHYT34I", "Text1");
        p.clear();
        var st = api(p).window("", null, p.user.groupId());
        for (String pgm : READS) assertThat(sent(p.sent, pgm)).as(pgm).isEqualTo(sent(oldSent, pgm));
        assertThat(sent(p.sent, "VHYT34B")).as("VHYT34B").isEqualTo(sent(oldSent, "VHYT34B"));
        assertThat(st.message()).isEqualTo(oldMessage);
        assertThat(st.revenueDate()).isEqualTo(value(s, "VHYT34I", "Text5"));
        assertThat(st.times().get("amMakeFrom")).isEqualTo(items(s, "Drop-down_List31"));
        assertThat(st.times().get("amBreakFrom")).isEqualTo(items(s, "Drop-down_List3"));
        assertThat(st.times().get("pmMakeFrom")).isEqualTo(items(s, "Drop-down_List4"));
        assertThat(st.times().get("pmBreakFrom")).isEqualTo(items(s, "Drop-down_List5"));
        assertThat(st.trains()).isEqualTo(items(s, "Drop-down_List211"));
        assertThat(st.statuses().get("AM-MAKE-STAT")).isEqualTo(value(s, "VHYT34I", "Text311"));
        assertThat(st.singleTrains()).hasSize(1);
        assertThat(st.times().get("amMakeTo")).containsExactly("05:00:00", "06:00:00");
        assertThat(st.times().get("pmBreakTo")).containsExactly("18:00:00");
    }

    /** Make/Break Overrides: AM makes cancelled 05:00-06:00 and PM breaks re-activated */
    @Test
    void overridesMatchTheOldScreen() {
        Parity p = backend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT34I", p.user);
        String id = (String) window(s, "VHYT34I").get("id");
        // what the two drop-downs and the check box write (their bindings: selectedItem -> the time, selectionIndex ->
        // TIME-INDEX, selection -> AM-MAKE-CANCEL), written straight into the records: the engine's drop-down "select" and
        // toggle changes do not reach these bindings
        s.event(id, List.of(), "Toggle_Button12", "selection", null);              // a check box's selection enables the button
        var w = s.window(id).recordPart("VHYT34IW");
        w.set("AM-MAKE-FROM-TIME data", "05:00:00");
        w.set("AM-MAKE-TO-TIME data", "06:00:00");
        w.set("AM-MAKE-CANCEL data", "1");
        s.window(id).recordPart("VHYT34I-TIME-INDEX").set("AM-MAKE-FROM-IDX data", 1);
        s.window(id).recordPart("VHYT34I-TIME-INDEX").set("AM-MAKE-TO-IDX data", 2);
        s.event(id, List.of(), "Push_Button1", "clicked", null);
        var oldSent = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHYT34I", "Text1");
        p.clear();
        var st = api(p).overrides("", null, new YardTrackingOverridesService.Overrides(Map.of("amMakeFrom", "05:00:00", "amMakeTo", "06:00:00"),
                Map.of("amMakeCancel", true)), p.user.groupId());
        // a cancel adds an override row (VHYT34I-ADD-ROW: VHYT34G); re-activates go through VHYT34F
        assertThat(sent(oldSent, "VHYT34G")).as("the old screen saved").isNotEmpty();
        for (String pgm : List.of("VHYT34E", "VHYT34F", "VHYT34G")) assertThat(sent(p.sent, pgm)).as(pgm).isEqualTo(sent(oldSent, pgm));
        assertThat(st.message()).isEqualTo(oldMessage);
    }

    @Test
    void singleTrainOverrideMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT34I", p.user);
        String id = (String) window(s, "VHYT34I").get("id");
        s.event(id, List.of(), "Drop-down_List211", "select", 1);
        s.event(id, List.of(), "Drop-down_List111", "select", 1);
        s.event(id, List.of(new ScreenSession.Change("Toggle_Button12112", "selection", true)), "Toggle_Button12112", "selection", null);
        s.event(id, List.of(), "Push_Button3", "clicked", null);
        var oldSent = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHYT34I", "Text1");
        String oldScheduled = value(s, "VHYT34I", "Text2");
        p.clear();
        var t = api(p).train("", null, "201", "09:10:00", p.user.groupId());
        assertThat(t.arrivals()).containsExactly("09:10:00", "17:20:00");
        // the engine's drop-down "select" does not raise selectedItem, so the old side never ran VHYT34I-SCHED-FOR (its Text2
        // stays blank); the real window runs it when an arrival is picked, and the API does: VHYT34K's MA means a make
        assertThat(oldScheduled).isEmpty();
        assertThat(t.scheduledFor()).isEqualTo("MAKE");
        p.clear();
        var st = api(p).singleOverride("", null, new YardTrackingOverridesService.SingleOverride("201", "09:10:00", false, true, false, false), p.user.groupId());
        // SCHED-FOR (VHYT34G's SCHED-FOR "MAKE") never ran on the old side, see above; everything else must match
        assertThat(withoutSchedFor(sent(p.sent, "VHYT34G"))).as("VHYT34G").isEqualTo(withoutSchedFor(sent(oldSent, "VHYT34G")));
        assertThat(sent(oldSent, "VHYT34G")).as("the old screen saved").isNotEmpty();
        assertThat(st.message()).isEqualTo(oldMessage);
    }
}
