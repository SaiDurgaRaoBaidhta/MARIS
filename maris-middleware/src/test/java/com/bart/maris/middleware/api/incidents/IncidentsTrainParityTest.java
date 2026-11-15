package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidents.IncidentScreens;
import com.bart.maris.middleware.domain.incidents.IncidentsTrainService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Train incidents (VHINC01I) against the old screen run by the engine, opened the old way (menu VHMOTDI), both sides
 * on the same scripted backend: the records sent to every program and the messages shown.
 */
class IncidentsTrainParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Parity backend(int consistRows, String inUse) {
        Parity p = new Parity();
        p.on("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINCD", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINCD_DATA", Map.of("revenueDate", "2026-11-13")));
        LocalDateTime now = LocalDateTime.now();
        p.on("VHCI02G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI02G_CRIT", Map.of("currentDate",
                now.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")), "currentTime", now.format(DateTimeFormatter.ofPattern("HH.mm.ss")))));
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVA_DATA", Map.of("revenueDate", "11-13-2026")));
        p.on("VHINC1A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC1A_CRIT",
                Map.of("sympCd", in.get("VHINC1A_CRIT").get("sympCd"), "vagenCount", "9999".equals(in.get("VHINC1A_CRIT").get("sympCd")) ? 0 : 1)));
        p.on("VHINC1B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", consistRows), "VHINC1B_DATA", Map.of("conNo", "201", "inUseInd", inUse, "noCarsInd", "N")));
        p.on("VHINVC", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINC1F", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINC1F_DATA", Map.of("incdNo", "T0001234")));
        p.on("VHINC1D", in -> "40000001".equals(in.get("VHINC1D_CRIT").get("incdNo"))
                ? Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINC1D_DATA", incident(List.of(Map.of("narrNo", 1, "userGrp", "VEHSUPR", "dateNarr", "11-13-2026", "timeNarr", "10.16.00", "narr", "TRAIN STOPPED"),
                                Map.of("narrNo", 2, "userGrp", "BRIGHTCO", "narr", "MOVED"))))
                : Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0)));
        p.on("VHINC1C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static Map<String, Object> incident(List<Map<String, Object>> narratives) {
        Map<String, Object> d = new java.util.LinkedHashMap<>(Map.of("conNo", "201", "dashNo", 3, "carNo", "0095", "sympCd", "0120", "discvDur", "1",
                "dateIncdOccr", "11-13-2026", "timeIncdOccr", "10:15:00", "speed", "35", "conDirection", "N"));
        d.put("incdLoc", "A10");
        d.put("dataNarrRow", narratives);
        return d;
    }

    static IncidentsTrainService api(Parity p) {
        EglTexts t = new EglTexts(Parity.bundle());
        return new IncidentsTrainService(p.legacy(), p.messages(), t, new IncidentScreens(Parity.bundle()), Clock.systemDefaultZone(), TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s) {
        return (String) window(s, "VHINC01I").get("id");
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s, "VHINC01I").get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static ScreenSession opened(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMOTDI", p.user);
        s.event((String) window(s, "VHMOTDI").get("id"), List.of(), "Push_Button11", "clicked", null);
        assertThat(window(s, "VHINC01I")).as("VHINC01I opened").isNotNull();
        // VH001I passes its VH01A01_DATA (signed-on group) to VHINC01I; VHMOTDI does not, so its access check would ask for a blank group
        s.window(id(s)).recordPart("VH01A01_DATA").set("USER-GROUP data", p.user.groupId());
        return s;
    }

    static ScreenSession.Change c(String widget, Object v) {
        return new ScreenSession.Change(widget, "object", v);
    }

    /** what each side sent to a program; times of day (now) left out, they are checked for their form */
    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Object> records = new TreeMap<>();
            for (var r : e.getValue().entrySet()) {
                if (r.getKey().equals("XDCONTROL")) continue;
                Map<String, Object> f = new TreeMap<>(r.getValue());
                f.remove("allowTime");                                           // the time now (seconds differ between the two runs)
                for (String k : Set.of("timeNarr", "allowTime", "allowTimeHr", "allowTimeMin", "allowTimeSec", "allowTimeFiller1", "allowTimeFiller2")) {
                    if (f.containsKey(k)) f.put(k, String.valueOf(f.get(k)).matches("\\d\\d[:.]\\d\\d[:.]\\d\\d|\\d\\d|[:.]") ? "(time)" : f.get(k));
                }
                if (r.getKey().endsWith("_DATA") && !program.matches("VHINC1F|VHINC1C|VHINVC")) continue;   // a read program's answer record
                records.put(r.getKey(), f);
            }
            out.add(records);
        }
        return out;
    }

    static String nowHhmm() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
    }

    static String message(Supplier<Object> call) {
        try { call.get(); return null; } catch (InvalidRequestException e) { return e.getMessage(); }
    }

    @Test
    void createSendsTheSameIncident() {
        Parity p = backend(1, "Y");
        ScreenSession s = opened(p);
        String date = value(s, "txtDateincd"), time = nowHhmm();
        int mark = p.sent.size();
        s.event(id(s), List.of(c("txtConsistno", "201"), c("txtSympcd", "0120"), c("txtTimeincd", time), c("txtSpeed", "35"), c("txtDirection", "N"),
                c("txtLocation", "A10"), c("mleNarrative", "TRAIN STOPPED")), "pbtCreate", "clicked", null);
        var old = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
        String oldMessage = value(s, "txtFooter");
        p.clear();
        var svc = api(p);
        assertThat(svc.defaults().date()).isEqualTo(date);
        p.clear();
        var r = svc.create(new IncidentsTrainService.Create("201", "0120", date, time, "35", "N", "A10", "TRAIN STOPPED"), p.user.groupId());
        for (String program : List.of("VH01A02", "VHINC1A", "VHINC1B", "VHINVC", "VHINC1F")) {
            assertThat(sent(p.sent, program)).as(program).isEqualTo(sent(old, program));
        }
        assertThat(r.message()).isEqualTo(oldMessage);
        assertThat(r.incidentNo()).isEqualTo("T0001234");
    }

    @Test
    void createChecksMatchTheOldScreen() {
        String[][] cases = {
                {"", "", "", "", "", "", ""},                           // everything missing
                {"201", "9999", "2599", "4A", "X", "A10", "N1"},        // unknown symptom, bad time, bad speed, bad direction
                {"201", "0120", "NOW", "35", "N", "", "N1"},            // no location
        };
        for (String[] k : cases) {
            for (int[] con : new int[][] {{1, 1}, {0, 1}, {1, 0}}) {
                Parity p = backend(con[0], con[1] == 1 ? "Y" : "N");
                ScreenSession s = opened(p);
                String date = value(s, "txtDateincd"), time = k[2].equals("NOW") ? nowHhmm() : k[2];
                int mark = p.sent.size();
                s.event(id(s), List.of(c("txtConsistno", k[0]), c("txtSympcd", k[1]), c("txtTimeincd", time), c("txtSpeed", k[3]), c("txtDirection", k[4]),
                        c("txtLocation", k[5]), c("mleNarrative", k[6])), "pbtCreate", "clicked", null);
                var old = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
                String oldMessage = value(s, "txtFooter");
                p.clear();
                String msg = message(() -> api(p).create(new IncidentsTrainService.Create(k[0], k[1], date, time, k[3], k[4], k[5], k[6]), p.user.groupId()));
                assertThat(msg).as(String.join("|", k) + " consist " + con[0] + con[1]).isEqualTo(oldMessage);
                assertThat(sent(p.sent, "VHINC1F")).isEmpty();
                assertThat(sent(old, "VHINC1F")).isEmpty();
                for (String program : List.of("VHINC1A", "VHINC1B")) assertThat(sent(p.sent, program)).as(program).isEqualTo(sent(old, program));
            }
        }
    }

    @Test
    void updateListsAndChangesTheIncident() {
        Parity p = backend(1, "Y");
        ScreenSession s = opened(p);
        s.event(id(s), List.of(new ScreenSession.Change("rbtEntrymode", "selectionIndex", 2)), "rbtEntrymode", "selectionIsValid", null);
        s.event(id(s), List.of(c("txtIncdno", "40000001")), "pbtList", "clicked", null);
        assertThat(value(s, "txtSympcd")).isEqualTo("0120");
        int mark = p.sent.size();
        s.event(id(s), List.of(c("txtSympcd", "0130"), c("txtSpeed", "40"), c("txtLocation", "A12")), "pbtUpdate", "clicked", null);
        var old = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
        String oldMessage = value(s, "txtFooter");
        p.clear();
        var svc = api(p);
        var inc = svc.incident("40000001");
        assertThat(inc.speed()).isEqualTo("35");
        assertThat(inc.narratives()).hasSize(2);
        p.clear();
        var r = svc.change("40000001", new IncidentsTrainService.Change("0130", "40", "N", "A12", 1), p.user.groupId());
        for (String program : List.of("VH01A02", "VHINC1A", "VHINC1C")) assertThat(sent(p.sent, program)).as(program).isEqualTo(sent(old, program));
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @Test
    void listOfAnUnknownIncidentGivesTheOldMessage() {
        Parity p = backend(1, "Y");
        ScreenSession s = opened(p);
        s.event(id(s), List.of(new ScreenSession.Change("rbtEntrymode", "selectionIndex", 2)), "rbtEntrymode", "selectionIsValid", null);
        s.event(id(s), List.of(c("txtIncdno", "40000009")), "pbtList", "clicked", null);
        String old = value(s, "txtFooter");
        assertThat(old).isNotBlank();
        assertThat(message(() -> api(p).incident("40000009"))).isEqualTo(old);
    }
}
