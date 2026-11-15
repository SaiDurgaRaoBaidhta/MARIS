package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleCreateService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Filing a vehicle incident (old VHINC08I, opened from the VHMVSI menu) against the old screen run by the engine:
 * the same typed values must send the same records to every program, and give the same message line.
 */
class IncidentsVehicleCreateParityTest {
    static final String TERMINAL = "MARIS-UI";

    /** a backend where every check passes; {@code change} adjusts one answer */
    static Parity backend(Consumer<Map<String, Map<String, Object>>> change) {
        Map<String, Map<String, Object>> a = new HashMap<>();
        a.put("VHCI02G", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI02G_CRIT", Map.of("currentDate", "11/14/2026", "currentTime", "10:20:30")));
        a.put("VHINCXA", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHINCXA_DATA", Map.of("discvDurCount", 3, "dataRow", List.of(
                Map.of("discvDur", "2", "discvDurDesc", "IN YARD"), Map.of("discvDur", "3", "discvDurDesc", "IN SHOP"), Map.of("discvDur", "4", "discvDurDesc", "PM")))));
        a.put("VHINC8E", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC8E_DATA", Map.of("daysDiff", 1, "timeDiff", 100)));
        a.put("VHINC1A", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC1A_CRIT", Map.of("vagenCount", 1, "sympCd", "0120")));
        a.put("VHINC6A", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("whereIsCar", "YRD", "carType", Map.of("carTypeOccr", List.of("A", "1", "2", "3")))));
        a.put("VHINXA", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINXA_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "conNo", "P01", "yrdSubLoc", "CY-T12", "posnNo", 1), Map.of("carNo", "1102", "conNo", "P01", "yrdSubLoc", "CY-T12", "posnNo", 2)))));
        a.put("VHINXB", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        a.put("VHINVB", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINVB_DATA", Map.of("revenueDate", "2026-11-13")));
        a.put("VHINCY", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINCY_DATA", Map.of("incdNo", "A0012345")));
        a.put("VHINXC", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        a.put("VHINC3A", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        change.accept(a);
        Parity p = new Parity();
        a.forEach((program, answer) -> p.on(program, in -> answer));
        return p;
    }

    static IncidentsVehicleCreateService api(Parity p) {
        return new IncidentsVehicleCreateService(p.legacy(), p.messages(), Parity.bundle(), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> w = window(s, screen);
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** the old menu's button for each incident type */
    static final Map<String, String> MENU_BUTTON = Map.of("YRD", "Push_Button10", "SHP", "Push_Button11");

    record Typed(String type, String car, String symptom, String duration, String time, String location, String narrative) {}

    static ScreenSession old(Parity p, Typed t) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMVSI", p.user);
        s.event((String) window(s, "VHMVSI").get("id"), List.of(), MENU_BUTTON.get(t.type()), "clicked", null);
        assertThat(window(s, "VHINC08I")).as("VHINC08I opened for " + t.type()).isNotNull();
        s.event((String) window(s, "VHINC08I").get("id"), List.of(new ScreenSession.Change("txtCarno", "object", t.car()),
                new ScreenSession.Change("txtSympcd", "object", t.symptom()), new ScreenSession.Change("txtDiscvdur", "object", t.duration()),
                new ScreenSession.Change("txtTimeincd", "object", t.time()), new ScreenSession.Change("txtLocation", "object", t.location()),
                new ScreenSession.Change("mleNarrative", "object", t.narrative())), "pbtCreate", "clicked", null);
        return s;
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Map<String, Object>> recs = new HashMap<>(e.getValue());
            recs.remove("XDCONTROL");
            if (recs.containsKey("VHINCY_DATA")) {                                  // the narrative time's seconds are the clock's
                Map<String, Object> d = new HashMap<>(recs.get("VHINCY_DATA"));
                d.put("timeNarr", String.valueOf(d.get("timeNarr")).substring(0, 5));
                recs.put("VHINCY_DATA", d);
            }
            out.add(recs);
        }
        return out;
    }

    static final List<String> PROGRAMS = List.of("VHINC8E", "VHINC1A", "VHINC6A", "VHINXA", "VHINXB", "VHINVB", "VHINCY", "VHINXC", "VHINC3A");

    /** runs the old screen and the API with the same input; returns the API's outcome (message or error) */
    static String compare(Parity p, Typed t) {
        ScreenSession s = old(p, t);
        String oldMessage = value(s, "VHINC08I", "txtStatusmsg");
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        var svc = api(p);
        var opt = svc.options(t.type(), p.user.groupId());
        String message;
        try {
            message = svc.create(new IncidentsVehicleCreateService.Request(t.type(), t.car(), t.symptom(), t.duration(), opt.date(), t.time(), t.location(),
                    t.narrative(), opt.narrativeDate(), opt.narrativeTime()), p.user.groupId()).message();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        for (String program : PROGRAMS) assertThat(sent(p.sent, program)).as(program).isEqualTo(sent(oldCalls, program));
        assertThat(message).isEqualTo(oldMessage);
        return message;
    }

    @Test
    void optionsMatchTheOpenedWindow() {
        for (String type : MENU_BUTTON.keySet()) {
            Parity p = backend(a -> { });
            ScreenSession s = p.oldScreens();
            s.openRoot("VHMVSI", p.user);
            s.event((String) window(s, "VHMVSI").get("id"), List.of(), MENU_BUTTON.get(type), "clicked", null);
            var opt = api(p).options(type, p.user.groupId());
            assertThat(opt.title()).isEqualTo(window(s, "VHINC08I").get("title"));
            assertThat(opt.date()).isEqualTo(value(s, "VHINC08I", "txtDateincd"));
            assertThat(opt.narrativeDate()).isEqualTo(value(s, "VHINC08I", "txtDispdateincd"));
            assertThat(opt.duration()).isEqualTo(value(s, "VHINC08I", "txtDiscvdur"));
            assertThat(opt.durations()).extracting(IncidentsVehicleCreateService.Duration::code).containsExactly("2", "3", "4");
        }
    }

    @Test
    void createSendsWhatTheOldScreenSends() {
        for (String type : MENU_BUTTON.keySet()) {
            String m = compare(backend(a -> { }), new Typed(type, "1101", "0120", "2", "0915", "CY01", "DOOR STUCK"));
            assertThat(m).contains("A0012345").contains("1101");
        }
    }

    @Test
    void aCarNotInAPseudoConsistIsNotReset() {
        compare(backend(a -> a.put("VHINXA", Map.of("XDCONTROL", Map.of("urc", 100)))), new Typed("YRD", "1101", "0120", "3", "0915", "CY01", "DOOR STUCK"));
    }

    @Test
    void checkMessagesMatch() {
        compare(backend(a -> { }), new Typed("YRD", "", "", "", "", "", ""));
        compare(backend(a -> { }), new Typed("YRD", "1101", "0120", "1", "0915", "CY01", "X"));
        compare(backend(a -> a.put("VHINC6A", Map.of("XDCONTROL", Map.of("urc", 100)))), new Typed("SHP", "1101", "0120", "9", "0915", "", "X"));
        compare(backend(a -> a.put("VHINC6A", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("whereIsCar", "CON",
                "carType", Map.of("carTypeOccr", List.of("B", "1", "2", "3")))))), new Typed("YRD", "1101", "0120", "2", "0915", "CY01", "X"));
        compare(backend(a -> a.put("VHINC8E", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC8E_DATA", Map.of("daysDiff", -1, "timeDiff", 0)))),
                new Typed("YRD", "1101", "0120", "2", "0915", "CY01", "X"));
        compare(backend(a -> a.put("VHINC8E", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC8E_DATA", Map.of("daysDiff", 0, "timeDiff", -5)))),
                new Typed("YRD", "1101", "0120", "2", "0915", "CY01", "X"));
        compare(backend(a -> a.put("VHINC8E", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC8E_DATA", Map.of("daysDiff", 2000, "timeDiff", 0)))),
                new Typed("SHP", "1101", "0120", "2", "0915", "CY01", "X"));
        compare(backend(a -> {
            a.put("VHINC8E", Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "X")));
            a.put("VHINC1A", Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC1A_CRIT", Map.of("vagenCount", 0)));
        }), new Typed("YRD", "11", "9999", "", "0915", "", ""));
    }
}
