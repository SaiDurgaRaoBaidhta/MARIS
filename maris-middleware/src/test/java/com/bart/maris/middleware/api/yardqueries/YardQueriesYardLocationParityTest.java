package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocation;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocationService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The yard location status report: the clean API against the old VHYT26I run by the engine, reached as users reach it:
 * VHYT27I for report 2 (the yard location report), a yard chosen, Display. Compared: every record sent (VHYT26A, VHYT30A and
 * the writing VHYT26C with the reason table it sorts) and every table and total the window shows.
 */
class YardQueriesYardLocationParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Map<String, Object> car(int i, String track, String reason, String home, String letter, int dd, int hh, int mm) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", i, "carNo", String.valueOf(1100 + i), "vagenOrientation", i % 2 == 0 ? ">" : "<",
                "vehStat", i % 3 == 0 ? "GO" : "NO GO", "dateInYrd", "11-0" + (i % 9 + 1) + "-2026", "timeInYrd", Map.of("timeInHhmm", "08:1" + i % 10),
                "moveReasonCd", reason, "moveReasonDsc", "REASON " + reason.strip()));
        m.putAll(Map.of("currentTime", "14:30:00", "rgtOrientCd", ">", "lftOrientCd", "<", "homeYrd", home, "trainIncdInd", i == 1 ? "Y" : "N",
                "carType", Map.of("carLetter", letter), "tsYr", 0, "tsMo", i == 5 ? 1 : 0, "tsDd", dd, "tsHh", hh));
        m.put("tsMm", mm);
        if (track.startsWith("T-24")) m.put("timeInYrd", Map.of("timeInHhmm", ""));
        return m;
    }

    static Map<String, Object> outCar(int i, String track, boolean yardFirst) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", i, "carNo", String.valueOf(2000 + i), "vagenOrientation", ">",
                "lftOrientCd", "<", "rgtOrientCd", ">", "vehStat", "GO", "dateOutShop", "11-11-2026", "timeOutShop", Map.of("timeOutHhmm", "09:00"),
                "dateOutYrd", "11-09-2026"));
        m.put("timeOutYrd", Map.of("yTimeOutHhmm", "07:00"));
        m.putAll(Map.of("moveReasonDsc", "TEST", "tsDt", 20261111L, "tsyDt", yardFirst ? 20261109L : 20261112L, "currentTime", "14:31:00",
                "tsDd", i, "tsHh", 2, "tsMm", 3));
        m.putAll(Map.of("tsyDd", 4 + i, "tsyHh", 5, "tsyMm", 6, "carType", Map.of("carLetter", "B")));
        return m;
    }

    /** cars of every letter and home yard, GO / 50 cars (left out of the reason summary), a T-24 car (not timed) */
    static final List<Map<String, Object>> CARS = List.of(car(1, "T-1", "BR  ", "CY", "A", 1, 4, 40), car(2, "T-24", "DR  ", "HY", "B", 0, 0, 0),
            car(3, "T-2", "BR  ", "RY", "C", 3, 23, 31), car(4, "T-3", "GO  ", "DY", "D", 0, 5, 10), car(5, "T-4", "50  ", "CY", "E", 2, 0, 0),
            car(6, "T-5", "PM  ", "CY", "M", 0, 7, 30), car(7, "T-6", "PM  ", "HY", "L", 10, 1, 2), car(8, "T-7", "DR  ", "RY", "K", 0, 0, 45),
            car(9, "T-8", "BR  ", "DY", "A", 4, 12, 0));

    static Parity backend(List<Map<String, Object>> cars, List<Map<String, Object>> out) {
        Parity p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        p.on("VHYT27B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHYT27B_DATA", Map.of("startDatedb2", "2026-11-01", "endDatedb2", "2026-11-10")));
        p.on("VHRT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHRT04A_DATA", Map.of("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "HY", "yrdDesc", "HAYWARD YARD")))));
        p.on("VHYT26A", in -> cars.isEmpty() ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars.size()), "VHYT26A_DATA", Map.of("dataRow", cars)));
        p.on("VHYT30A", in -> out.isEmpty() ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", out.size()), "VHYT30A_DATA", Map.of("dataRow", out)));
        // VHYT26C sorts the reason table by count (it stores it in MARIS's report work table and reads it back)
        p.on("VHYT26C", in -> {
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) in.get("VHYT26IW-REASON").get("dataRow"));
            int n = ((Number) in.get("VHYT26C_CRIT").get("numOfRows")).intValue();
            List<Map<String, Object>> sorted = new ArrayList<>(rows.subList(0, Math.min(n, rows.size())));
            sorted.sort((a, b) -> Integer.compare(((Number) b.get("totalCars")).intValue(), ((Number) a.get("totalCars")).intValue()));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", sorted.size()), "VHYT26C_DATA", Map.of("dataRow", sorted));
        });
        return p;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> widgets(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (w.get("screen").toString().equalsIgnoreCase(screen)) return (Map<String, Map<String, Object>>) w.get("widgets");
        }
        throw new AssertionError(screen + " is not open: " + s.state().get("windows"));
    }

    /** a table as the old window shows it, trailing blank rows dropped (the API leaves them out) */
    @SuppressWarnings("unchecked")
    static List<List<String>> rows(Map<String, Map<String, Object>> w, String container) {
        List<List<String>> out = new ArrayList<>();
        for (Object r : (List<Object>) w.get(container).getOrDefault("rows", List.of())) {
            out.add(((List<Object>) r).stream().map(x -> x == null ? "" : x.toString().strip()).toList());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).stream().allMatch(c -> c.isEmpty() || c.equals("0"))) out.remove(out.size() - 1);
        return out;
    }

    static String value(Map<String, Map<String, Object>> w, String widget) {
        Object v = w.get(widget).get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** VHYT27I opened by the menu for a report (VHYT27IS-BEGIN sets REPORT-INDEX and turns Display on), a yard, dates, Display */
    @SuppressWarnings("unchecked")
    static ScreenSession openOld(Parity p, int report, String yard, String from, String to) {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT27I", p.user);
        String w = ((List<Map<String, Object>>) old.state().get("windows")).get(0).get("id").toString();
        ScreenInstance s = old.window(w);
        s.recordPart("VHYT27IW").set("REPORT-INDEX data", report);
        s.recordPart("VHYT27IW-TRIGGERS").set("ENABLE-DISPLAY data", "Y");
        if (from != null) s.recordPart("VHYT27IW").set("FROM-DATE data", from);
        if (to != null) s.recordPart("VHYT27IW").set("TO-DATE data", to);
        List<String> yards = (List<String>) widgets(old, "VHYT27I").get("Drop-down_List1").get("items");
        for (int i = 0; i < yards.size(); i++) {
            if (yards.get(i).startsWith(yard + " ")) {
                old.event(w, List.of(), "Drop-down_List1", "select", i + 1);
                // the list's selectedItem reaches YARD-SELECTION through an event-to-attribute connection the engine does not apply
                s.recordPart("VHYT27IW-PASSVALUE").set("YARD-SELECTION data", yards.get(i));
            }
        }
        p.clear();
        old.event(w, List.of(), "Push_Button3", "clicked", null);
        return old;
    }

    static YardQueriesYardLocationService api(Parity p) {
        return new YardQueriesYardLocationService(p.legacy(), Parity.bundle(), p.clock, TERMINAL);
    }

    /** the same report from both sides; the yard is the one the old window actually sent (see the class comment of the shop status test) */
    static void same(List<Map<String, Object>> cars, List<Map<String, Object>> out, String yard) {
        Parity p = backend(cars, out);
        ScreenSession old = openOld(p, 2, yard, null, null);
        Map<String, Map<String, Object>> w = widgets(old, "VHYT26I");
        var oldSent = new ArrayList<>(p.sent);
        Map<String, Map<String, Object>> sent26 = p.lastSent("VHYT26A");
        assertThat(sent26).as("the old report asked for the yard's cars").isNotNull();
        String oldYard = String.valueOf(sent26.get("VHYT26A_CRIT").get("yrdTrkgYrd"));
        assertThat(oldYard).as("the engine passes the chosen yard, or (without the YARD-SELECTION fix) a blank one").isIn(yard, "");

        p.clear();
        YardQueriesYardLocation r = api(p).report(oldYard);
        for (String program : List.of("VHYT26A", "VHYT30A", "VHYT26C")) {
            List<Object> o = new ArrayList<>(), n = new ArrayList<>();
            for (var e : oldSent) if (e.getKey().equals(program)) o.add(withoutControl(e.getValue()));
            for (var e : p.sent) if (e.getKey().equals(program)) n.add(withoutControl(e.getValue()));
            assertThat(n).as(program + " records sent").isEqualTo(o);
        }
        assertThat(r.yardName()).isEqualTo(value(w, "Text1"));
        assertThat(r.runDate()).isEqualTo(value(w, "Text2"));
        assertThat(r.runTime()).isEqualTo(value(w, "Text3"));
        assertThat(r.totalCars()).isEqualTo(value(w, "Text4"));
        assertThat(r.title()).isEqualTo(value(w, "Label1"));
        assertThat(r.cars().rows()).as("cars").isEqualTo(rows(w, "VAGen_Container_Details_DATA"));
        assertThat(r.reasons().rows()).as("reason summary").isEqualTo(rows(w, "VAGen_Container_Details_DATA_ROW"));
        assertThat(r.homeYards().rows()).as("home yard summary").isEqualTo(rows(w, "VAGen_Container_Details_DATA_ROW1"));
        assertThat(r.exchange().rows()).as("shop / yard exchange").isEqualTo(rows(w, "VAGen_Container_Details_DATA1"));
        // The test track note follows VHYT26IW-TRIGGERS.INC-TEST-TRACK ("data" -> Label5 show). The window hides Label5 first
        // and VHYT26IS-INITIAL then sets the trigger for HY, but the engine applies the hide after the show, so its Label5
        // stays hidden; the trigger itself is compared.
        @SuppressWarnings("unchecked") String id = ((List<Map<String, Object>>) old.state().get("windows")).stream()
                .filter(x -> x.get("screen").toString().equalsIgnoreCase("VHYT26I")).map(x -> x.get("id").toString()).findFirst().orElseThrow();
        assertThat(r.testTracksNote() != null).as("the test track note")
                .isEqualTo("Y".equals(String.valueOf(old.window(id).recordPart("VHYT26IW-TRIGGERS").get("INC-TEST-TRACK data")).strip()));
        if (r.testTracksNote() != null) assertThat(r.testTracksNote()).isEqualTo(value(w, "Label5"));
    }

    /** XDCONTROL carries the previous call's control block on the old side; the business records are compared */
    static Map<String, Map<String, Object>> withoutControl(Map<String, Map<String, Object>> in) {
        Map<String, Map<String, Object>> m = new HashMap<>(in);
        m.remove("XDCONTROL");
        return m;
    }

    @Test
    void hayward() {
        same(CARS, List.of(outCar(1, "SHOP", true), outCar(2, "T-24", false), outCar(3, "YARD", false)), "HY");
    }

    @Test
    void concordWithNoExchange() {
        same(CARS, List.of(), "CY");
    }

    @Test
    void anEmptyYard() {
        same(List.of(), List.of(), "HY");
    }
}
