package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintCarHoursService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintConsistHistoryService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintConsistStatusService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.engine.screen.ScreenSession.Change;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The vehicle maintenance update screens (VHVM08I car hours, VHVM10I consist status, VHVM19I consist history) against
 * the old screens run by the engine: the records each side sends to the writing programs, and the messages.
 */
class VehicleMaintUpdatesParityTest {
    /** the terminal id the engine records (maris.engine.terminal-id default) */
    static final String TERMINAL = "MARIS-UI";
    static final EglTexts TEXTS = new EglTexts(Parity.bundle());

    // ---- helpers ---------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s, String screen) {
        return (String) window(s, screen).get("id");
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static String dialog(ScreenSession s) {
        Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
        return d == null ? null : String.valueOf(d.get("message"));
    }

    @SuppressWarnings("unchecked")
    static void ok(ScreenSession s) {
        Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
        if (d != null) s.answer(((Number) d.get("id")).intValue(), "OK");
    }

    static Map<String, Object> reply(int urc, int rows, Object... records) {
        Map<String, Object> m = new HashMap<>();
        m.put("XDCONTROL", Map.of("urc", urc, "urowcnt", rows));
        for (int i = 0; i < records.length; i += 2) m.put((String) records[i], records[i + 1]);
        return m;
    }

    /**
     * the records last sent to a program; XDCONTROL's row count is left out: the old screens send whatever their
     * previous read left there, the backend's own answer field
     */
    static Map<String, Map<String, Object>> sent(Parity p, String program) {
        Map<String, Map<String, Object>> m = p.lastSent(program);
        if (m == null) return null;
        Map<String, Map<String, Object>> out = new HashMap<>(m);
        Map<String, Object> x = new HashMap<>(m.get("XDCONTROL"));
        x.remove("urowcnt");
        out.put("XDCONTROL", x);
        return out;
    }

    // ---- VHVM08I ---------------------------------------------------------------------------------------------

    static Parity hoursBackend(int urc) {
        Parity p = new Parity();
        p.on("VHVM1A5", in -> reply(urc, 1, "VHVM1A5_DATA", Map.of("carNo", in.get("VHVM1A5_CRIT").get("carNo"), "currLoc", "HY01", "cumHrs", 51234,
                "hrsToNextPm", 40, "hrsFromLastPm", 210, "whereIsCar", "YRD")));
        p.on("VHVM8A1", in -> reply(0, 0));
        return p;
    }

    static ScreenSession oldHours(Parity p, String car, String test) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHVM08I", p.user);
        String w = id(s, "VHVM08I");
        s.event(w, List.of(new Change("Text1", "object", car)), "Push_Button1", "clicked", null);
        if (dialog(s) != null) return s;
        s.event(w, List.of(new Change("Text2", "object", test)), "Text2", "losingFocus", null);
        s.event(w, List.of(), "Push_Button4", "clicked", null);
        return s;
    }

    @Test
    void testHoursSendTheSameHoursAsTheOldScreen() {
        for (String test : List.of("25", "60", "0")) {
            Parity p = hoursBackend(0);
            ScreenSession s = oldHours(p, "1101", test);
            String oldMessage = dialog(s);
            Map<String, Map<String, Object>> old = sent(p, "VHVM8A1");
            assertThat(old).as("old VHVM8A1 for test " + test).isNotNull();
            p.clear();
            var svc = new VehicleMaintCarHoursService(p.legacy(), p.messages(), TEXTS, TERMINAL);
            var r = svc.addTestHours("1101", test);
            assertThat(sent(p, "VHVM8A1")).as("VHVM8A1 for test " + test).isEqualTo(old);
            assertThat(r.message()).isEqualTo(oldMessage);
        }
    }

    @Test
    void theCarShowsWhatTheOldScreenShows() {
        Parity p = hoursBackend(0);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHVM08I", p.user);
        s.event(id(s, "VHVM08I"), List.of(new Change("Text1", "object", "1101")), "Push_Button1", "clicked", null);
        var oldSent = sent(p, "VHVM1A5");
        p.clear();
        var c = new VehicleMaintCarHoursService(p.legacy(), p.messages(), TEXTS, TERMINAL).car("1101");
        assertThat(sent(p, "VHVM1A5")).isEqualTo(oldSent);
        assertThat(c.location()).isEqualTo(value(s, "VHVM08I", "Text3"));
        assertThat(String.valueOf(c.cumulativeHours())).isEqualTo(value(s, "VHVM08I", "Text4"));
        assertThat(String.valueOf(c.hoursToNextPm())).isEqualTo(value(s, "VHVM08I", "Text5"));
        assertThat(String.valueOf(c.hoursFromLastPm())).isEqualTo(value(s, "VHVM08I", "Text6"));
        assertThat(c.whereIsCar()).isEqualTo(value(s, "VHVM08I", "Text10"));
    }

    @Test
    void anUnknownCarGetsTheOldMessage() {
        Parity p = hoursBackend(100);
        ScreenSession s = oldHours(p, "9999", "5");
        assertThat(dialog(s)).isNotBlank();
        assertThat(p.lastSent("VHVM8A1")).isNull();
        Throwable t = catchThrowable(() -> new VehicleMaintCarHoursService(p.legacy(), p.messages(), TEXTS, TERMINAL).addTestHours("9999", "5"));
        assertThat(t).isInstanceOf(InvalidRequestException.class).hasMessage(dialog(s));
    }

    // ---- VHVM10I ---------------------------------------------------------------------------------------------

    static final List<Map<String, Object>> CONSISTS = List.of(
            row("201", "SRV", 1, 10, "2026-11-10", "2026-11-09", "2026-11-10", "05.10.00", "N", "", "05.00.00"),
            row("202", "CON", 2, 8, "2026-11-10", "2026-11-10", "", "", "", "2026-11-11", "06.00.00"));

    static Map<String, Object> row(String con, String stat, int dash, int size, String insrv, String rev, String ics, String icsTime, String noCars,
                                   String out, String time) {
        Map<String, Object> m = new HashMap<>();
        m.put("conNo", con); m.put("conStat", stat); m.put("dashNo", dash); m.put("conSize", size); m.put("dateInsrv", insrv);
        m.put("dateInsrvRev", rev); m.put("dateInsrvIcs", ics); m.put("timeInsrvIcs", icsTime); m.put("noCarsInd", noCars);
        m.put("dateOutsrv", out); m.put("timeInsrv", time);
        return m;
    }

    static Parity consistBackend(String program, String record) {
        Parity p = new Parity();
        p.on(program, in -> reply(0, CONSISTS.size(), record, Map.of("dataRow", CONSISTS)));
        p.on("VHVM10C", in -> reply(0, 0));
        p.on("VHVM19B", in -> reply(0, 0));
        p.on("VHYT34A", in -> reply(0, 1, "VHYT34A_DATA", Map.of("timeRevStart", "03.00.00", "timeRevEnd", "02.30.00", "currentDate", "2026-11-14",
                "currentTime", "10.15.00", "yesterday", "2026-11-13")));
        return p;
    }

    static int index(List<String> items, String item) {
        return items.indexOf(item) + 1;
    }

    @Test
    void consistStatusUpdatesSendWhatTheOldScreenSends() {
        // row 1 set out of service with no out date (takes its revenue date); row 2 to CON keeps its out date
        String[][] cases = {{"1", "OUT"}, {"2", "CON"}, {"2", "OUT"}};
        for (String[] c : cases) {
            int row = Integer.parseInt(c[0]);
            Parity p = consistBackend("VHVM10A", "VHVM10A_DATA");
            ScreenSession s = p.oldScreens();
            s.openRoot("VHVM10I", p.user);
            String w = id(s, "VHVM10I");
            s.event(w, List.of(new Change("Text5", "object", "201"), new Change("Text6", "object", "")), "Push_Button1", "clicked", null);
            var oldCrit = sent(p, "VHVM10A");
            var statuses = TEXTS.items("VHVM10I", "Drop-down_List2");
            s.event(w, List.of(new Change("VAGen_Container_Details_DATA_ROW1", "selectionIndices", List.of(row))), null, null, null);
            s.event(w, List.of(new Change("Drop-down_List2", "selectionIndex", index(statuses, c[1]))), null, null, null);
            s.event(w, List.of(), "pbtUpdate", "clicked", null);
            String oldMessage = dialog(s);
            Map<String, Map<String, Object>> old = sent(p, "VHVM10C");
            assertThat(old).as("old VHVM10C, case " + String.join("/", c) + ", message " + oldMessage).isNotNull();
            p.clear();
            var svc = new VehicleMaintConsistStatusService(p.legacy(), p.messages(), TEXTS, TERMINAL);
            var list = svc.consists("201", "");
            assertThat(sent(p, "VHVM10A")).isEqualTo(oldCrit);
            Map<String, Object> r = list.rows().get(row - 1);
            String msg = svc.update(new VehicleMaintConsistStatusService.Change((String) r.get("conNo"), c[1], (String) r.get("dateInsrvIcs"),
                    (String) r.get("timeInsrvIcs"), (String) r.get("noCarsInd"), (String) r.get("dateOutsrv"), (String) r.get("dateInsrvRev")));
            assertThat(sent(p, "VHVM10C")).as("case " + String.join("/", c)).isEqualTo(old);
            assertThat(msg).isEqualTo(oldMessage);
        }
    }

    @Test
    void consistStatusNeedsAStatusLikeTheOldScreen() {
        Parity p = consistBackend("VHVM10A", "VHVM10A_DATA");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHVM10I", p.user);
        String w = id(s, "VHVM10I");
        s.event(w, List.of(new Change("Text5", "object", "201")), "Push_Button1", "clicked", null);
        s.event(w, List.of(new Change("VAGen_Container_Details_DATA_ROW1", "selectionIndices", List.of(1))), null, null, null);
        s.event(w, List.of(new Change("Drop-down_List2", "selectionIndex", 0)), null, null, null);
        s.event(w, List.of(), "pbtUpdate", "clicked", null);
        String oldMessage = dialog(s);
        assertThat(p.lastSent("VHVM10C")).isNull();
        Throwable t = catchThrowable(() -> new VehicleMaintConsistStatusService(p.legacy(), p.messages(), TEXTS, TERMINAL)
                .update(new VehicleMaintConsistStatusService.Change("201", "", "", "", "", "", "")));
        assertThat(t).isInstanceOf(InvalidRequestException.class).hasMessage(oldMessage);
    }

    // ---- VHVM19I ---------------------------------------------------------------------------------------------

    @Test
    void consistHistoryMatchesTheOldScreen() {
        for (String[] c : new String[][] {{"1", "CON", "11-12-2026"}, {"2", "OUT", ""}}) {
            int row = Integer.parseInt(c[0]);
            Parity p = consistBackend("VHVM19A", "VHVM19A_DATA");
            ScreenSession s = p.oldScreens();
            s.openRoot("VHVM19I", p.user);
            String w = id(s, "VHVM19I");
            String oldRevDate = value(s, "VHVM19I", "Text11");
            s.event(w, List.of(new Change("Text5", "object", "201"), new Change("Text6", "object", "205")), "Push_Button1", "clicked", null);
            var oldCrit = sent(p, "VHVM19A");
            var statuses = TEXTS.items("VHVM19I", "Drop-down_List2");
            s.event(w, List.of(new Change("VAGen_Container_Details_DATA_ROW1", "selectionIndices", List.of(row))), null, null, null);
            List<Change> edits = new ArrayList<>(List.of(new Change("Drop-down_List2", "selectionIndex", index(statuses, c[1]))));
            if (!c[2].isEmpty()) edits.add(new Change("Text8", "object", c[2]));
            s.event(w, edits, "pbtUpdate", "clicked", null);
            ok(s);
            String oldMessage = value(s, "VHVM19I", "Text12");
            Map<String, Map<String, Object>> old = sent(p, "VHVM19B");
            assertThat(old).as("old VHVM19B, case " + String.join("/", c)).isNotNull();
            p.clear();
            var svc = new VehicleMaintConsistHistoryService(p.legacy(), p.messages(), TEXTS, TERMINAL);
            var o = svc.options();
            assertThat(o.revDate()).isEqualTo(oldRevDate);
            var list = svc.history("201", "205", o.revDate());
            assertThat(sent(p, "VHVM19A")).isEqualTo(oldCrit);
            Map<String, Object> r = list.rows().get(row - 1);
            String msg = svc.update(new VehicleMaintConsistHistoryService.Change((String) r.get("conNo"), ((Number) r.get("dashNo")).longValue(),
                    (String) r.get("dateInsrv"), (String) r.get("timeInsrv"), (String) r.get("dateInsrvRev"), c[1],
                    c[2].isEmpty() ? (String) r.get("dateInsrvIcs") : c[2], (String) r.get("timeInsrvIcs"), (String) r.get("noCarsInd")));
            assertThat(sent(p, "VHVM19B")).as("case " + String.join("/", c)).isEqualTo(old);
            assertThat(msg).isEqualTo(oldMessage);
        }
    }

    @Test
    void consistHistoryNoDataMessageMatches() {
        Parity p = new Parity();
        p.on("VHVM19A", in -> reply(100, 0));
        ScreenSession s = p.oldScreens();
        s.openRoot("VHVM19I", p.user);
        s.event(id(s, "VHVM19I"), List.of(new Change("Text5", "object", "999")), "Push_Button1", "clicked", null);
        ok(s);
        String oldMessage = value(s, "VHVM19I", "Text12");
        var h = new VehicleMaintConsistHistoryService(p.legacy(), p.messages(), TEXTS, TERMINAL).history("999", "", "11-14-2026");
        assertThat(h.rows()).isEmpty();
        assertThat(h.message()).isEqualTo(oldMessage).isNotBlank();
    }
}
