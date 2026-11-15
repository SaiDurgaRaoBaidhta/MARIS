package com.bart.maris.middleware.api.incidentevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsWorksheetService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The vehicle desk work sheet (old VHIE03I) and its event detail (VHIE04I) against the old screens run by the engine. */
class IncidentEventsWorksheetParityTest {
    static Parity backend() {
        Parity p = new Parity();
        p.on("VHIE01B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentTime", "10:00:00",
                "currentDate", "2026-11-12", "yesterday", "2026-11-11")));
        p.on("VHIE04D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE04D_DATA", Map.of("dataRow", List.of(
                Map.of("sympCd", "0120", "sympCdNarr", "DOORS WILL NOT CLOSE"), Map.of("sympCd", "0300", "sympCdNarr", "BRAKES DRAGGING")))));
        p.on("VHIE03B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE03B_DATA", Map.of("dataRow", List.of(
                Map.of("vehTechId", "T1", "vehTechIdDesc", "TECH ONE", "actvInd", "Y"), Map.of("vehTechId", "T2", "vehTechIdDesc", "TECH TWO", "actvInd", "Y")))));
        p.on("VHIE03D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE03D_DATA", Map.of("dataRow", List.of(
                Map.of("actionCd", "A1", "actionCdDesc", "ADJUSTED"), Map.of("actionCd", "A2", "actionCdDesc", "REPLACED")))));
        p.on("VHIE03C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE03C_DATA", Map.of("dataRow", List.of(
                Map.of("trackingCd", "K1", "trackingDesc", "TRACK ONE"), Map.of("trackingCd", "K2", "trackingDesc", "TRACK TWO")))));
        p.on("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VH01A02_DATA", Map.of("userGroupAccess", List.of(
                Map.of("guiName", "VH001I", "partName", "pbtEventLogCreateIncident", "accessAllow", "Y")))));
        return p;
    }

    /** VHIE03I as the vehicle menu opens it */
    static ScreenSession open(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHIE03I", p.user);
        return s;
    }

    /** the records the last call of a program sent, without XDCONTROL (its row count is left over from the call before) */
    static Map<String, Map<String, Object>> records(Parity p, String program) {
        Map<String, Map<String, Object>> sent = p.lastSent(program);
        if (sent == null) return null;
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>(sent);
        out.remove("XDCONTROL");
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        Map<String, Object> found = null;
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) found = w;
        return found;
    }

    static String id(ScreenSession s, String screen) {
        Map<String, Object> w = window(s, screen);
        return w == null ? null : (String) w.get("id");
    }

    @SuppressWarnings("unchecked")
    static Object attr(ScreenSession s, String screen, String widget, String attr) {
        Map<String, Object> w = window(s, screen);
        if (w == null) return null;
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        return x == null ? null : x.get(attr);
    }

    static String value(ScreenSession s, String screen, String widget) {
        Object v = attr(s, screen, widget, "value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static List<List<String>> rows(ScreenSession s, String screen, String table) {
        Object v = attr(s, screen, table, "rows");
        List<List<String>> out = new ArrayList<>();
        if (v instanceof List<?> l) for (Object r : l) out.add(((List<Object>) r).stream().map(x -> x == null ? "" : x.toString().strip()).toList());
        return out;
    }

    static final List<Map<String, Object>> EVENTS = List.of(
            row(1, "4377", "197", "03", "CY01", "N", "35", "10:15:00", "0120", "T1", "DOORS WILL NOT CLOSE", "K1", "A1", "ADJUSTED", "", "2026-11-12", "2026-11-12", "05:01:00", 7),
            row(2, "1101", "", "", "MC01", "S", "ALL", "11:20:00", "0300", "", "BRAKES", "", "", "", "29990012", "", "", "", 0),
            row(3, "2202", "", "", "DC01", "W", "20", "12:30:00", "0120", "", "DOOR", "", "", "", "", "", "", "", 0));

    static Map<String, Object> row(int n, String car, String con, String dash, String loc, String dir, String speed, String time, String symp, String tech,
                                   String narr, String trk, String act, String actDesc, String incd, String insrvRev, String insrv, String tInsrv, int bin) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("cntEvents", n); m.put("carNo", car); m.put("conNo", con); m.put("dashNo", Map.of("no1", "00", "no2", dash.isEmpty() ? "00" : dash));
        m.put("eventLoc", loc); m.put("conDirection", dir); m.put("speed", speed); m.put("timeEventOccr", time); m.put("sympCd", symp);
        m.put("vehTechId", tech); m.put("narr", narr); m.put("trackingCd", trk); m.put("actionCd", act); m.put("actionCdDesc", actDesc);
        m.put("incdNo", incd); m.put("dateInsrvRev", insrvRev); m.put("dateInsrv", insrv); m.put("timeInsrv", tInsrv); m.put("binDashNo", bin);
        m.put("dateEventOccr", "2026-11-12"); m.put("userGrp", n == 1 ? "BRIGHTCO" : "");
        return m;
    }

    static Parity full() {
        Parity p = backend();
        p.on("VHIE03A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", EVENTS.size()), "VHIE03A_CRIT", Map.of("dateEventOccr", "2026-11-12"),
                "VHIE03A_DATA", Map.of("dataRow", EVENTS)));
        p.on("VHIE04C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHIE04C_CRIT", Map.of("hasCaNarr", "Y"),
                "VHIE04C_DATA", Map.of("dataRow", List.of(Map.of("caNarr", "RESET BREAKER", "narr", "DOORS WILL NOT CLOSE ON CAR 4377")))));
        p.on("VHIE03H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHIE03H_DATA", Map.of("conNo", "532", "carNo", in.get("VHIE03H_DATA").get("carNo"),
                "dashNo", Map.of("dashNo1", "00", "dashNo2", "01"), "binDashNo", 1, "timeInsrv", "04:10:00", "dateInsrvRev", "2026-11-12", "dateInsrv", "2026-11-12")));
        p.on("VHIE03G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE03G_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "4377", "carFormat", Map.of("orient1", "<", "car", "4377", "orient2", ""), "dashNo", Map.of("dashNo1", "00", "dashNo2", "02"),
                        "binDashNo", 2, "timeInsrv", "06:00:00", "dateInsrvRev", "2026-11-12", "dateInsrv", "2026-11-12", "whereIsCar", "CON"),
                Map.of("carNo", "1101", "carFormat", Map.of("orient1", "", "car", "1101", "orient2", ">"), "whereIsCar", "CON")))));
        p.on("VHIE03E", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE03E_DATA", Map.of("conNo", in.get("VHIE03E_DATA").get("conNo"),
                "itemCount", "999".equals(in.get("VHIE03E_DATA").get("conNo")) ? 0 : 1, "conStat", "CON")));
        p.on("VHINC6A", in -> "9999".equals(in.get("VHINC6A_CRIT").get("carNo")) ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("whereIsCar", "CON", "conNo", "532", "carType", "A")));
        p.on("VHIE04A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHIE04B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static final String USER = "BRIGHTCO", TERMINAL = "MARIS-UI";

    static IncidentEventsWorksheetService api(Parity p) {
        return new IncidentEventsWorksheetService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    static List<String> items(ScreenSession s, String screen, String widget) {
        Object v = attr(s, screen, widget, "items");
        return v instanceof List<?> l ? ((List<Object>) l).stream().map(x -> String.valueOf(x).strip()).filter(x -> !x.isEmpty()).toList() : List.of();
    }

    @Test
    void optionsMatchTheOldScreen() {
        Parity p = full();
        ScreenSession s = open(p);
        var old = new java.util.LinkedHashMap<String, Object>();
        for (String pr : List.of("VHIE04D", "VHIE03B", "VHIE03C", "VHIE03D", "VH01A02")) old.put(pr, records(p, pr));
        List<List<String>> oldLists = new ArrayList<>();
        for (String dd : List.of("Drop-down_List1", "Drop-down_List2", "Drop-down_List3")) if (!items(s, "VHIE03I", dd).isEmpty()) oldLists.add(items(s, "VHIE03I", dd));
        p.clear();
        var o = api(p).options(USER, TERMINAL);
        for (String pr : List.of("VHIE04D", "VHIE03B", "VHIE03C", "VHIE03D", "VH01A02")) assertThat(records(p, pr)).as(pr).isEqualTo(old.get(pr));
        assertThat(o.revenueDate()).isEqualTo("2026-11-12");
        assertThat(value(s, "VHIE03I", "Text1")).isEqualTo("11-12-2026");
        List<List<String>> mine = new ArrayList<>();                         // the code lists the old drop-downs show
        for (var l : List.of(o.technicians(), o.actions(), o.trackingCodes())) mine.add(l.stream().map(c -> c.code()).toList());
        assertThat(mine).containsAll(oldLists);
        assertThat(o.createAllowed()).isTrue();
        assertThat(o.message()).isNull();
    }

    /** the table's columns, as the API gives them */
    static List<String> cells(IncidentEventsWorksheetService.Event e) {
        return List.of(e.hasIncident() ? "X" : "", e.eventId(), e.consist(), e.location(), e.direction(), e.speed(), e.time(), e.symptom(), e.car(),
                e.technician(), e.narrative(), e.trackingCode(), e.actionText(), e.incident(), e.dateInServiceRev(), e.dateInService(), e.timeInService());
    }

    static ScreenSession listed(Parity p, String date) {
        ScreenSession s = open(p);
        s.event(id(s, "VHIE03I"), List.of(new ScreenSession.Change("Text1", "object", date)), "Push_Button3", "clicked", null);
        return s;
    }

    @Test
    void eventsMatchTheOldScreen() {
        for (String[] c : new String[][] {{"11-12-2026", "2026-11-12"}, {"11-10-2026", "2026-11-10"}, {"12-01-2026", "2026-12-01"}}) {
            Parity p = full();
            ScreenSession s = listed(p, c[0]);
            var oldCrit = records(p, "VHIE03A");
            long oldCalls = p.sent.stream().filter(e -> e.getKey().equals("VHIE03A")).count();
            List<List<String>> oldRows = rows(s, "VHIE03I", "VAGen_Container_Details_DATA_ROW");
            String oldMsg = value(s, "VHIE03I", "Text3");
            p.clear();
            var log = api(p).events(c[1]);
            assertThat(records(p, "VHIE03A")).as(c[0]).isEqualTo(oldCrit);
            assertThat(oldCalls).as("the old List reads the events even when the date check fails").isGreaterThanOrEqualTo(1);
            assertThat(log.events().stream().map(IncidentEventsWorksheetParityTest::cells).toList()).as(c[0]).isEqualTo(oldRows);
            assertThat(log.message() == null ? "" : log.message()).as(c[0]).isEqualTo(oldMsg);
        }
    }

    /** the add cases: {car, consist, location, direction, speed, symptom, narrative, tech, tracking, action, focus field} */
    static final String[][] ADDS = {
            {"4377", "", "CY01", "N", "35", "0120", "DOOR STUCK", "T1", "K1", "A1", "Text2"},
            {"", "532", "MC01", "S", "ALL", "0300", "BRAKES", "", "", "", "Text7"},
            {"4377", "", "CY01", "X", "35", "0120", "DOOR STUCK", "T9", "K9", "A9", "Text2"},
            {"9999", "", "CY01", "N", "35", "0120", "BAD CAR", "", "", "", "Text2"},
            {"", "999", "CY01", "N", "35", "0120", "BAD CON", "", "", "", "Text7"},
            {"", "", "CY01", "N", "35", "0120", "NOTHING", "", "", "", "Text5"},
    };

    @Test
    void addMatchesTheOldScreen() {
        for (String[] c : ADDS) {
            Parity p = full();
            ScreenSession s = open(p);
            String w = id(s, "VHIE03I");
            List<ScreenSession.Change> typed = List.of(new ScreenSession.Change("Text2", "object", c[0]), new ScreenSession.Change("Text7", "object", c[1]),
                    new ScreenSession.Change("Text5", "object", c[2]), new ScreenSession.Change("Text6", "object", c[3]), new ScreenSession.Change("Text721", "object", c[4]),
                    new ScreenSession.Change("Text9", "object", c[5]), new ScreenSession.Change("Text8", "object", c[6]), new ScreenSession.Change("Text13", "object", c[7]),
                    new ScreenSession.Change("Text10", "object", c[8]), new ScreenSession.Change("Text12", "object", c[9]));
            s.event(w, typed, c[10], "losingFocus", null);
            p.clear();
            s.event(w, List.of(), "pb1Add", "clicked", null);
            var old = records(p, "VHIE04A");
            String oldMsg = value(s, "VHIE03I", "Text3");
            p.clear();
            var in = new IncidentEventsWorksheetService.EventInput(c[0], c[1], c[2], c[3], c[4], "", c[5], c[6], c[7], c[8], c[9], "");
            String msg;
            try { msg = api(p).add(in, USER, TERMINAL); } catch (InvalidRequestException e) { msg = e.getMessage(); }
            assertThat(records(p, "VHIE04A")).as(String.join("/", c) + " old message " + oldMsg).isEqualTo(old);
            assertThat(msg).as(String.join("/", c)).isEqualTo(oldMsg);
        }
    }

    @SuppressWarnings("unchecked")
    static void ok(ScreenSession s) {
        Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
        if (d != null) s.answer(((Number) d.get("id")).intValue(), "OK");
    }

    /** the old screen after a row is picked: the fields it shows (MOVE, GET-CA, CON) */
    static ScreenSession picked(Parity p, int index) {
        ScreenSession s = listed(p, "11-12-2026");
        s.event(id(s, "VHIE03I"), List.of(new ScreenSession.Change("VAGen_Container_Details_DATA_ROW", "selectionIndices", List.of(index + 1))), null, null, null);
        return s;
    }

    /** update cases: {row index, location, direction, tech, tracking, action, narrative, delete} */
    static final String[][] UPDATES = {
            {"0", "CY02", "S", "T2", "K2", "A2", "", "N"},
            {"1", "MC02", "E", "", "", "", "BRAKES FIXED", "N"},
            {"0", "", "", "", "", "", "", "Y"},
            {"1", "MC02", "Q", "T9", "", "", "", "N"},
    };

    @Test
    void updateMatchesTheOldScreen() {
        for (String[] c : UPDATES) {
            Parity p = full();
            int index = Integer.parseInt(c[0]);
            ScreenSession s = picked(p, index);
            String w = id(s, "VHIE03I");
            Map<String, String> shown = new java.util.LinkedHashMap<>();
            for (String f : List.of("Text2", "Text7", "Text5", "Text6", "Text721", "Text4", "Text9", "Text8", "Text13", "Text10", "Text12", "Text121")) shown.put(f, value(s, "VHIE03I", f));
            List<ScreenSession.Change> typed = new ArrayList<>();
            String[][] edits = {{"Text5", c[1]}, {"Text6", c[2]}, {"Text13", c[3]}, {"Text10", c[4]}, {"Text12", c[5]}, {"Text8", c[6]}};
            for (String[] e : edits) if (!e[1].isEmpty() || c[7].equals("N") && !e[0].equals("Text8")) { typed.add(new ScreenSession.Change(e[0], "object", e[1])); shown.put(e[0], e[1]); }
            p.clear();
            if (c[7].equals("Y")) {
                s.event(w, typed, "Push_Button61", "clicked", null);
                ok(s);
            } else {
                s.event(w, typed, "Push_Button7", "clicked", null);
            }
            var old = records(p, "VHIE04B");
            String oldMsg = value(s, "VHIE03I", "Text3");
            p.clear();
            var in = new IncidentEventsWorksheetService.EventInput(shown.get("Text2"), shown.get("Text7"), shown.get("Text5"), shown.get("Text6"), shown.get("Text721"),
                    shown.get("Text4"), shown.get("Text9"), shown.get("Text8"), shown.get("Text13"), shown.get("Text10"), shown.get("Text12"), shown.get("Text121"));
            String msg;
            try { msg = api(p).update("2026-11-12", (Integer) EVENTS.get(index).get("cntEvents"), in, c[7].equals("Y"), USER, TERMINAL); }
            catch (InvalidRequestException e) { msg = e.getMessage(); }
            assertThat(records(p, "VHIE04B")).as(String.join("/", c) + " shown " + shown + " old message " + oldMsg).isEqualTo(old);
            assertThat(msg).as(String.join("/", c)).isEqualTo(oldMsg);
        }
    }

    @Test
    void pickedRowMatchesTheOldScreen() {
        for (int index : new int[] {0, 1}) {
            Parity p = full();
            ScreenSession s = picked(p, index);
            var oldCa = records(p, "VHIE04C");
            var oldG = records(p, "VHIE03G");
            String narr = value(s, "VHIE03I", "Text8"), ca = value(s, "VHIE03I", "Text11");
            List<String> cars = new ArrayList<>();
            for (int k = 14; k <= 23; k++) if (!value(s, "VHIE03I", "Text" + k).isEmpty()) cars.add(value(s, "VHIE03I", "Text" + k));
            p.clear();
            var api = api(p);
            var e = api.events("2026-11-12").events().get(index);
            var c = api.correctiveAction("2026-11-12", e.number());
            assertThat(records(p, "VHIE04C")).isEqualTo(oldCa);
            assertThat(c.narrative()).isEqualTo(narr);
            assertThat(c.correctiveAction()).isEqualTo(ca);
            if (!e.consist().isEmpty()) {
                var con = api.consistCars(e.consist(), "2026-11-12", new IncidentEventsWorksheetService.InService(e.binDash(), e.timeInService(), e.dateInServiceRev(), e.dateInService()));
                assertThat(records(p, "VHIE03G")).isEqualTo(oldG);
                assertThat(con.cars().stream().map(String::strip).toList()).isEqualTo(cars);
            }
        }
    }

    static Parity filing() {
        Parity p = full();
        p.on("VHINC6A", in -> {
            String car = String.valueOf(in.get("VHINC6A_CRIT").get("carNo")).strip();
            if (car.equals("9999")) return Map.of("XDCONTROL", Map.of("urc", 100));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("whereIsCar", car.equals("4377") ? "CON" : "SRV", "conNo", "197",
                    "carType", "A"));
        });
        p.on("VHEVLX", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHEVLX_CRIT", Map.of("errFound", "N"), "VHEVLX_DATA", Map.of("incdNo", Map.of("incdPfx", "G", "incdNoBase", 101, "incdSuffix", ""))));
        p.on("VHEVLY", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHEVLY_DATA", Map.of("incdNo", "G0000102")));
        p.on("VHEVL1F", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHEVL1F_DATA", Map.of("incdNo", "G0000103")));
        p.on("VHINC8E", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINC8E_DATA", Map.of("daysDiff", 2, "timeDiff", 30)));
        p.on("VHINC1A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINC1A_CRIT", Map.of("vagenCount", 1)));
        p.on("VHINXC", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINC3A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINC1B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC1B_DATA", Map.of("conNo", "197", "inUseInd", "Y", "noCarsInd", "N")));
        p.on("VHINCE", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINCE_DATA", Map.of("systemDate", "11-12-2026")));
        return p;
    }

    static final List<String> WRITES = List.of("VHEVLX", "VHEVLY", "VHEVL1F", "VHINC1X", "VHIE04B", "VHINC3A", "VHINXB", "VHINXC");

    /** filing cases: {row index, car (null = as shown), time, speed, direction} */
    static final String[][] FILINGS = {
            {"0", null, null, null, null},
            {"2", null, null, null, null},
            {"0", "", null, null, null},
            {"0", null, "25:10:00", "1X", "Q"},
            {"0", "", "", "", ""},
    };

    @Test
    void createIncidentMatchesTheOldScreen() {
        List<String> compared = new ArrayList<>();
        for (String[] c : java.util.stream.Stream.concat(java.util.Arrays.stream(FILINGS), java.util.stream.Stream.<String[]>of(new String[] {"1", null, null, null, null})).toList()) {
            Parity p = filing();
            int index = Integer.parseInt(c[0]);
            ScreenSession s = picked(p, index);
            String w = id(s, "VHIE03I");
            Map<String, String> shown = new java.util.LinkedHashMap<>();
            for (String f : List.of("Text2", "Text7", "Text5", "Text6", "Text721", "Text4", "Text9", "Text8", "Text13", "Text10", "Text12", "Text121")) shown.put(f, value(s, "VHIE03I", f));
            List<ScreenSession.Change> typed = new ArrayList<>();
            String[][] edits = {{"Text2", c[1]}, {"Text4", c[2]}, {"Text721", c[3]}, {"Text6", c[4]}};
            for (String[] e : edits) if (e[1] != null) { typed.add(new ScreenSession.Change(e[0], "object", e[1])); shown.put(e[0], e[1]); }
            p.clear();
            s.event(w, typed, "pbtCreReqMaint", "clicked", null);
            List<String> oldOrder = p.sent.stream().map(e -> e.getKey()).filter(WRITES::contains).toList();
            List<String> oldAll = p.sent.stream().map(e -> e.getKey()).toList();
            Map<String, Object> old = new java.util.LinkedHashMap<>();
            for (String pr : WRITES) old.put(pr, records(p, pr));
            String oldMsg = value(s, "VHIE03I", "Text3");
            p.clear();
            var in = new IncidentEventsWorksheetService.EventInput(shown.get("Text2"), shown.get("Text7"), shown.get("Text5"), shown.get("Text6"), shown.get("Text721"),
                    shown.get("Text4"), shown.get("Text9"), shown.get("Text8"), shown.get("Text13"), shown.get("Text10"), shown.get("Text12"), shown.get("Text121"));
            String msg;
            try { msg = api(p).createIncident("2026-11-12", (Integer) EVENTS.get(index).get("cntEvents"), in, USER, TERMINAL); }
            catch (InvalidRequestException e) { msg = e.getMessage(); }
            if (oldAll.isEmpty()) { assertThat(p.sent.stream().map(e -> e.getKey()).filter(WRITES::contains).toList()).isEmpty(); continue; }
            String label = java.util.Arrays.toString(c) + " shown " + shown + " old message " + oldMsg + " old calls " + oldAll + " dialog " + s.state().get("dialog");
            assertThat(p.sent.stream().map(e -> e.getKey()).filter(WRITES::contains).toList()).as(label).isEqualTo(oldOrder);
            for (String pr : WRITES) assertThat(records(p, pr)).as(pr + " " + label).isEqualTo(old.get(pr));
            assertThat(msg == null ? "" : msg).as(label).isEqualTo(oldMsg);
            compared.add(c[0] + "/" + c[1] + ": " + oldOrder + " " + oldMsg);
        }
        assertThat(compared).hasSize(FILINGS.length);
    }
}
