package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.consist.ConsistLayUpService;
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
 * Laying up a train: the clean API against the old VHLT01I run by the engine, both against the same scripted backend.
 * The engine does not fill VHLT01I's drop-downs (items from DATA_ROW_of_VHLT010_DATA / _VHLT011_DATA stay empty), so the
 * old side's yard and track are set on the fields their selectedItem is bound to (YARD-LOC, YRD-TRKG-YRD, YARD-SUB-LOC).
 */
class ConsistLayUpParityTest {
    static final String WRITERS = "VHLT012 VHLT017 VHLT018";
    /** the terminal id the engine signs the old screens on with (maris.engine.terminal-id) */
    static final String TERMINAL = "MARIS-UI";

    /** consist status, vehicle status of its cars, pseudo train already at a track ("" = none) */
    static Parity backend(String conStat, String vehStat, String pseudoAt) {
        Parity p = new Parity();
        p.on("VHLT010", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3),
                "VHLT010_CRIT", in.get("VHLT010_CRIT"),
                "VHLT010_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"), Map.of("yrd", "RY")))));
        p.on("VHLT020", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHLT020_DATA", Map.of("yrdTrkgYrd", "HY")));
        p.on("VHXREVC", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVC_DATA", Map.of("revenueDate", "11-14-2026", "timeRevStart", "03:00:00")));
        p.on("VHTR012", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHTR012_DATA", Map.of("currentTime", "23:50:00")));
        p.on("VHLT011", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2),
                "VHLT011_DATA", Map.of("dataRow", List.of(Map.of("yrdSubLoc", "T-10"), Map.of("yrdSubLoc", "T-11")))));
        p.on("VHLT009", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHLT009_CRIT", Map.of("conNo", "201", "noRecordFound", conStat.isEmpty() ? "Y" : "N"),
                "VHLT009_DATA", Map.of("conStat", conStat, "conSize", 2, "dashNo", 3, "dateInsrv", "11-14-2026", "timeInsrv", "05:10:00", "dateInsrvRev", "2026-11-14")));
        p.on("VHLT021", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2),
                "VHLT021_DATA", Map.of("dataRow", List.of(car("1101", 1, vehStat), car("1102", 2, "GO")))));
        p.on("VHLT022", in -> Map.of("XDCONTROL", Map.of("urc", pseudoAt.isEmpty() ? 100 : 0),
                "VHLT022_DATA", pseudoAt.isEmpty() ? Map.of() : Map.of("conNo", "201", "yrdTrkgYrd", "CY", "yrdSubLoc", pseudoAt)));
        p.on("VHLT014", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));             // no train incident
        p.on("VHLT013", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));             // no car incident
        for (String r : List.of("VHLT016", "VHLT019", "VHLT01A")) p.on(r, in -> {
            Map<String, Object> out = new LinkedHashMap<>(in);
            out.put("XDCONTROL", Map.of("urc", 0, "urowcnt", 0));
            return out;
        });
        p.on("VHLT015", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHLT015_CRIT", in.get("VHLT015_CRIT"),
                "VHLT015_DATA", Map.of("dataRow", List.of(car("1101", 1, vehStat), car("1102", 2, "GO")))));
        for (String w : WRITERS.split(" ")) p.on(w, in -> {
            Map<String, Object> out = new LinkedHashMap<>(in);
            out.put("XDCONTROL", Map.of("urc", 0));
            return out;
        });
        return p;
    }

    static Map<String, Object> car(String car, int order, String vehStat) {
        return Map.of("carNo", car, "carOrder", order, "conNo", "201", "dashNo", 3, "vehStat", vehStat, "conStat", "SRV", "carType", "A2",
                "dateInsrv", "11-14-2026", "timeInsrv", "05:10:00", "dateInsrvRev", "2026-11-14");
    }

    static ConsistLayUpService api(Parity p) {
        return new ConsistLayUpService(Parity.bundle(), p.legacy(), p.messages(), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> "VHLT01I".equals(w.get("screen")))
                .map(w -> (String) w.get("id")).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!"VHLT01I".equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            Object v = x == null ? null : x.get("value");
            return v == null ? "" : v.toString().strip();
        }
        return "(no window)";
    }

    @SuppressWarnings("unchecked")
    static boolean enabled(ScreenSession s, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!"VHLT01I".equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            return x != null && !Boolean.FALSE.equals(x.get("enabled"));
        }
        return false;
    }

    /**
     * what Text3 shows; when the old screen writes only a part of MSG-1 (MSG-1-X / MSG-1-XX, the "-LOC INVALID" messages) the
     * engine does not refresh Text3, so the message is read from the record the old screen filled
     */
    static String message(ScreenSession s) {
        String shown = value(s, "Text3");
        if (!shown.isEmpty()) return shown;
        ScreenInstance w = s.window(windowId(s));
        String t = String.valueOf(w.recordPart("VHLT01IW-TIME-MSG").get("MSG-1 data")).strip();
        return !t.isEmpty() && !t.equals("null") ? t : String.valueOf(w.recordPart("VHLT01IW-CON-MSG").get("CON-MSG-1 data")).strip();
    }

    /** opens VHLT01I, chooses a yard and track, types the train (and the time); lays up when asked */
    static ScreenSession old(Parity p, String yard, String track, String consist, String time, boolean layUp) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHLT01I", p.user);
        String id = windowId(s);
        ScreenInstance w = s.window(id);
        w.recordPart("VHLT01IW").set("YARD-LOC data", yard);
        w.recordPart("VHLT01IW").set("YRD-TRKG-YRD data", yard);
        s.event(id, List.of(), "Push_Button3", "clicked", null);
        w.recordPart("VHLT01IW").set("YARD-SUB-LOC data", track);
        s.event(id, List.of(new ScreenSession.Change("Text1", "object", consist)), "Text1", "losingFocus", null);
        if (layUp) {
            List<ScreenSession.Change> ch = new ArrayList<>();
            if (time != null) ch.add(new ScreenSession.Change("Text2", "object", time));
            s.event(id, ch, "Push_Button1", "clicked", null);
        }
        return s;
    }

    /** the business records a program got, call by call (XDCONTROL left out: the old screen passes the last one back) */
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SRV|GO|''",          // may be laid up
            "OUT|GO|''",          // not in service
            "CAR|GO|''",          // car incident
            "CON|GO|''",          // train incident
            "SRV|NO GO|''",       // a NO GO car
            "SRV|GO|T-12",        // pseudo train already somewhere
            "''|GO|''"})          // no such consist
    void theTrainCheckMatchesTheOldScreen(String conStat, String vehStat, String pseudoAt) {
        Parity p = backend(conStat, vehStat, pseudoAt);
        ScreenSession s = old(p, "HY", "T-10", "201", null, false);
        String oldMessage = value(s, "Text3");
        boolean oldEnabled = enabled(s, "Push_Button1");
        var oldChecks = List.of(sent(p, "VHLT009"), sent(p, "VHLT021"), sent(p, "VHLT022"));

        p.clear();
        var t = api(p).train(p.user.groupId(), "201");
        assertThat(t.message()).as("message").isEqualTo(oldMessage);
        assertThat(t.layUp()).as("Lay-up enabled").isEqualTo(oldEnabled);
        assertThat(List.of(sent(p, "VHLT009"), sent(p, "VHLT021"), sent(p, "VHLT022"))).isEqualTo(oldChecks);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "HY|T-10|2359|Y",     // laid up (Text2 takes HHMM)
            "HY|T-10|23.59|Y",    // HH.MM is read too
            "HY|T-10|23:59|N",    // HH:MM is not: the old conversion only knows "." (refused as not numeric)
            "HY|T-10|2500|N",     // hours out of range
            "HY|T-10|AB12|N",     // not numeric
            "HY|T-10|0400|N",     // before the train went in service (05:10)
            "HY|''|2359|N"})      // no storage track
    void layingUpWritesWhatTheOldScreenWrites(String yard, String track, String time, String written) {
        Parity p = backend("SRV", "GO", "");
        ScreenSession s = old(p, yard, track, "201", time, true);
        String oldMessage = message(s);
        Map<String, Object> oldWrites = new LinkedHashMap<>();
        for (String w : WRITERS.split(" ")) oldWrites.put(w, sent(p, w));
        assertThat(oldWrites.values().stream().anyMatch(l -> !((List<?>) l).isEmpty())).as("the old screen wrote").isEqualTo(written.equals("Y"));

        p.clear();
        String message;
        try {
            message = api(p).layUp(p.user.groupId(), yard, track, "201", time).message();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(message).as("message").isEqualTo(oldMessage);
        for (String w : WRITERS.split(" ")) assertThat(sent(p, w)).as(w).isEqualTo(oldWrites.get(w));
    }

    @Test
    void theYardsAndTracksAreTheOldOnes() {
        Parity p = backend("SRV", "GO", "");
        old(p, "HY", "T-10", "201", null, false);
        var oldInit = List.of(sent(p, "VHLT010"), sent(p, "VHLT020"), sent(p, "VHLT011"));
        p.clear();
        var o = api(p).options(p.user.groupId());
        var tracks = api(p).tracks(p.user.groupId(), "HY");
        assertThat(o.yards()).containsExactly("CY", "HY", "RY");
        assertThat(o.yard()).isEqualTo("HY");
        assertThat(tracks).containsExactly("T-10", "T-11");
        assertThat(sent(p, "VHLT010").get(0)).isEqualTo(oldInit.get(0).get(0));
        // the criteria match; the old screen passes its previous VHLT011 answer back in the data rows (the API has none to pass)
        assertThat(((java.util.Map<?, ?>) sent(p, "VHLT011").get(sent(p, "VHLT011").size() - 1)).get("VHLT011_CRIT"))
                .isEqualTo(((java.util.Map<?, ?>) oldInit.get(2).get(oldInit.get(2).size() - 1)).get("VHLT011_CRIT"));
    }

    @Test
    void aLayUpTheOldScreenWouldNotAllowIsRefused() {
        Parity p = backend("OUT", "GO", "");
        var e = catchThrowableOfType(InvalidRequestException.class, () -> api(p).layUp("BRIGHTCO", "HY", "T-10", "201", "23:59"));
        assertThat(e).isNotNull();
        for (String w : WRITERS.split(" ")) assertThat(sent(p, w)).as(w).isEmpty();
    }
}
