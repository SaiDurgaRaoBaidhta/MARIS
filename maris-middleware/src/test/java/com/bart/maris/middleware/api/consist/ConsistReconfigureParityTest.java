package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.consist.ConsistReconfigureService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Reconfiguring a train: the clean API against the old VHTR01I (and its confirmation window VHTR02I) run by the engine,
 * both against the same scripted backend. As for VHLT01I, the engine leaves the drop-downs empty, so the old side's yard
 * and track are set on the fields their selectedItem is bound to.
 */
class ConsistReconfigureParityTest {
    static final String WRITERS = "VHTR016 VHLT017 VHLT018";
    static final List<String> CARS = List.of("1101", "1102", "1103", "1104");

    static Parity backend(String conStat) {
        Parity p = new Parity();
        p.on("VHLT010", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHLT010_CRIT", in.get("VHLT010_CRIT"),
                "VHLT010_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY")))));
        p.on("VHXREVC", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVC_DATA", Map.of("revenueDate", "11-14-2026", "timeRevStart", "03:00:00")));
        p.on("VHTR012", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHTR012_DATA", Map.of("currentTime", "23:50:00")));
        p.on("VHLT011", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2),
                "VHLT011_DATA", Map.of("dataRow", List.of(Map.of("yrdSubLoc", "T-10"), Map.of("yrdSubLoc", "T-11")))));
        p.on("VHTR011", in -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < CARS.size(); i++) rows.add(Map.ofEntries(Map.entry("conStat", conStat), Map.entry("dashNo", 3),
                    Map.entry("dateInsrv", "11-14-2026"), Map.entry("timeInsrv", "05:10:00"), Map.entry("dateInsrvRev", "2026-11-14"),
                    Map.entry("conSize", CARS.size()), Map.entry("carNo", CARS.get(i)), Map.entry("carOrder", i + 1), Map.entry("carType", i == 0 || i == 3 ? "A2" : "B2"),
                    Map.entry("whereIsCar", "SRV"), Map.entry("currLoc", "HY"), Map.entry("vagenOrientation", i == 0 ? "<" : i == 3 ? ">" : " "),
                    Map.entry("vehStat", "GO")));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", CARS.size()), "VHTR011_CRIT", Map.of("conNo", "201", "noRecordFound", conStat.isEmpty() ? "Y" : "N"),
                    "VHTR011_DATA", Map.of("dataRow", rows));
        });
        p.on("VHTR014", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHTR014_CRIT", Map.of("conNo", "950", "noRecordFound", "N"),
                "VHTR014_DATA", Map.of("dataRow", List.of(Map.of("conNo", "950", "carNo", "1201", "posnNo", 1, "vagenOrientation", "<", "conSize", 2),
                        Map.of("conNo", "950", "carNo", "1202", "posnNo", 2, "vagenOrientation", ">", "conSize", 2)))));
        p.on("VHSC2A1", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSC2A1_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1102", "carType", "B2", "vehStat", "GO"), Map.of("carNo", "1103", "carType", "B2", "vehStat", "GO")))));
        p.on("VHTS2A3", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHTS2A3_DATA", Map.of("dataRow", List.of(
                Map.of("lftOrientationCd", "<", "rgtOrientationCd", ">", "carType", "B2")))));
        p.on("VHLT014", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        p.on("VHLT013", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        for (String r : List.of("VHTR013", "VHTR015", "VHLT015", "VHLT016", "VHLT019", "VHTR01A")) p.on(r, in -> echo(in));
        for (String w : WRITERS.split(" ")) p.on(w, in -> echo(in));
        return p;
    }

    static Map<String, Object> echo(Map<String, Map<String, Object>> in) {
        Map<String, Object> out = new LinkedHashMap<>(in);
        out.put("XDCONTROL", Map.of("urc", 0, "urowcnt", 0));
        return out;
    }

    static ConsistReconfigureService api(Parity p) {
        return new ConsistReconfigureService(Parity.bundle(), p.legacy(), p.messages(), p.clock, ConsistLayUpParityTest.TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> screen.equals(w.get("screen")))
                .map(w -> (String) w.get("id")).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String name) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if ("VHTR01I".equals(w.get("screen"))) return (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
        }
        return null;
    }

    static String value(ScreenSession s, String name) {
        Map<String, Object> w = widget(s, name);
        Object v = w == null ? null : w.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static boolean enabled(ScreenSession s, String name) {
        Map<String, Object> w = widget(s, name);
        return w != null && !Boolean.FALSE.equals(w.get("enabled"));
    }

    /** the old window after the user's steps */
    static ScreenSession old(Parity p, ConsistReconfigureService.Steps st) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHTR01I", p.user);
        String id = windowId(s, "VHTR01I");
        if (st.action() != 2) s.event(id, List.of(), "Radio_Button_Set1", "select", st.action());
        s.event(id, List.of(new ScreenSession.Change("Text1", "object", st.consist())), "Text1", "losingFocus", null);
        if (st.action() == 1) {
            s.event(id, List.of(new ScreenSession.Change("txtLeftCon", "object", st.leftConsist()),
                    new ScreenSession.Change("txtRightCon", "object", st.rightConsist())), "txtRightCon", "losingFocus", null);
            List<ScreenSession.Change> ch = st.pseudo() == null ? List.of() : List.of(new ScreenSession.Change("txtLeftCon6", "object", st.pseudo()));
            s.event(id, ch, "txtLeftCon6", "losingFocus", null);
        }
        for (int pos : st.splits()) s.event(id, List.of(), "pbtSplitButton" + (pos == 6 ? "61" : String.valueOf(pos)), "clicked", null);
        return s;
    }

    /** then yard, track, time and Reconfigure; answers VHTR02I with Yes when asked to */
    static void oldReconfigure(Parity p, ScreenSession s, String yard, String track, String time, boolean yes) {
        String id = windowId(s, "VHTR01I");
        ScreenInstance w = s.window(id);
        w.recordPart("VHTR01IW").set("YARD-LOC data", yard);
        w.recordPart("VHTR01IW").set("YRD-TRKG-YRD data", yard);
        s.event(id, List.of(), "Push_Button4", "clicked", null);
        w.recordPart("VHTR01IW").set("YARD-SUB-LOC data", track);
        s.event(id, List.of(new ScreenSession.Change("Text2", "object", time)), "Push_Button1", "clicked", null);
        String confirm = windowId(s, "VHTR02I");
        if (confirm != null) s.event(confirm, List.of(), yes ? "Push_Button1" : "Push_Button2", "clicked", null);
    }

    static List<Map<String, Map<String, Object>>> sent(Parity p, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (var e : p.sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Map<String, Object>> m = new LinkedHashMap<>(e.getValue());
            m.remove("XDCONTROL");
            out.add(m);
        }
        return out;
    }

    static ConsistReconfigureService.Steps steps(String kind) {
        return switch (kind) {
            case "split3" -> new ConsistReconfigureService.Steps(2, "201", null, null, null, List.of(3));
            case "split4" -> new ConsistReconfigureService.Steps(2, "201", null, null, null, List.of(4));
            case "buildup" -> new ConsistReconfigureService.Steps(1, "201", "201", "950", null, List.of());
            case "nopseudo" -> new ConsistReconfigureService.Steps(1, "201", "201", "", null, List.of());
            case "neither" -> new ConsistReconfigureService.Steps(1, "201", "300", "950", null, List.of());
            default -> new ConsistReconfigureService.Steps(2, "201", null, null, null, List.of());
        };
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SRV|split3", "SRV|split4", "SRV|buildup", "SRV|nopseudo", "SRV|neither", "SRV|none",
            "OUT|none", "CON|none", "XYZ|none", "''|none"})
    void theWindowMatchesTheOldOne(String conStat, String kind) {
        Parity p = backend(conStat);
        var st = steps(kind);
        ScreenSession s = old(p, st);
        String oldMessage = value(s, "Text4");
        boolean oldReconfigure = enabled(s, "Push_Button1");
        List<String> oldLeft = new ArrayList<>(), oldRight = new ArrayList<>();
        for (String n : List.of("txtLeftCon111", "txtLeftCon211", "txtLeftCon311", "txtLeftCon411", "txtLeftCon511")) oldLeft.add(value(s, n));
        for (String n : List.of("txtLeftCon1111", "txtLeftCon2111", "txtLeftCon3111", "txtLeftCon4111", "txtLeftCon5111")) oldRight.add(value(s, n));
        var oldCalls = p.sent.stream().map(Map.Entry::getKey).filter(k -> !k.equals("VHLT011")).toList();

        p.clear();
        var state = api(p).state(p.user.groupId(), st);
        assertThat(state.message()).as("message").isEqualTo(oldMessage);
        assertThat(state.reconfigure()).as("Reconfigure enabled").isEqualTo(oldReconfigure);
        assertThat(pad(state.left().cars())).as("left cars").isEqualTo(oldLeft);
        assertThat(pad(state.right().cars())).as("right cars").isEqualTo(oldRight);
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).as("programs").isEqualTo(oldCalls);
    }

    static List<String> pad(List<String> cars) {
        List<String> out = new ArrayList<>(cars);
        while (out.size() < 5) out.add("");
        return out.subList(0, 5);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "split3|2359|Y", "split4|2359|Y", "buildup|2359|Y", "split3|2359|N", "split3|2500|Y", "split3|0400|Y"})
    void reconfiguringWritesWhatTheOldScreenWrites(String kind, String time, String yes) {
        Parity p = backend("SRV");
        var st = steps(kind);
        ScreenSession s = old(p, st);
        if (!enabled(s, "Push_Button1")) {                                // nothing to press on the old window
            p.clear();
            var e = org.assertj.core.api.Assertions.catchThrowableOfType(InvalidRequestException.class,
                    () -> api(p).reconfigure(p.user.groupId(), st, "HY", "T-10", time, true));
            assertThat(e).as("refused like the disabled button").isNotNull();
            return;
        }
        oldReconfigure(p, s, "HY", "T-10", time, yes.equals("Y"));
        String oldMessage = value(s, "Text4");
        Map<String, Object> oldWrites = new LinkedHashMap<>();
        for (String w : WRITERS.split(" ")) oldWrites.put(w, sent(p, w));

        p.clear();
        var svc = api(p);
        var first = svc.reconfigure(p.user.groupId(), st, "HY", "T-10", time, false);
        for (String w : WRITERS.split(" ")) assertThat(sent(p, w)).as(w + " before the confirmation").isEmpty();
        if (first.question() != null && yes.equals("Y")) {
            p.clear();
            var done = svc.reconfigure(p.user.groupId(), st, "HY", "T-10", time, true);
            assertThat(done.message()).as("message").isEqualTo(oldMessage);
            // the engine signals VHTR02I's closedWidget twice (its window and its screen, WrapperPart.forward), so the old side
            // runs VHTR01IS-CONT-REC twice and writes the same records twice; the old runtime closed the window once
            for (String w : WRITERS.split(" ")) assertThat(sent(p, w)).as(w).isEqualTo(((List<?>) oldWrites.get(w)).stream().distinct().toList());
        } else {
            assertThat(first.message()).as("message").isEqualTo(oldMessage);
            for (String w : WRITERS.split(" ")) assertThat((List<?>) oldWrites.get(w)).as(w + " (old)").isEmpty();
        }
    }

    @Test
    void theConfirmationIsTheOldWindowsText() {
        Parity p = backend("SRV");
        var svc = api(p);
        var r = svc.reconfigure(p.user.groupId(), steps("buildup"), "HY", "T-10", "2359", false);
        var q = Parity.bundle().screen("VHTR02I").path("widgets");
        assertThat(r.question()).isEqualTo((q.path("Label1").path("props").path("object").asText() + " "
                + q.path("Label2").path("props").path("object").asText()).strip());
    }
}
