package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopActivityService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrainsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Old VHYT54I (force clear), VHYT52I (pseudo correction), VHYT31I (shop activity), VHYT23I and VHYT22I (prints) run by
 * the engine against the clean API, on the same scripted backend: the records every program gets (writers included) and
 * the messages must be the same.
 */
class YardTrackingMiscParityTest {

    @SuppressWarnings("unchecked")
    public static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> w = window(s, screen);
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** the old table's rows, cells stripped, blank cells after the last car left out */
    @SuppressWarnings("unchecked")
    static List<List<String>> oldRows(ScreenSession s, String screen, String table) {
        Map<String, Object> t = (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(table);
        List<List<String>> out = new ArrayList<>();
        for (Object row : (List<Object>) t.get("rows")) {
            List<String> cells = new ArrayList<>();
            for (Object c : (List<Object>) row) if (c != null && !c.toString().isBlank()) cells.add(c.toString().strip());
            if (!cells.isEmpty()) out.add(cells);
        }
        return out;
    }

    public static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Object> m = new HashMap<>(e.getValue());
            m.remove("XDCONTROL");                                         // the old screens pass the previous call's control block on
            out.add(m);
        }
        return out;
    }

    // ---- VHYT54I force clear ----------------------------------------------------------------------------------

    static Parity forceBackend(int trainUrc, int yardUrc) {
        Parity p = new Parity();
        p.on("VHYT54A", in -> Map.of("XDCONTROL", Map.of("urc", trainUrc, "urowcnt", 1), "VHYT54A_DATA",
                Map.of("dataRow", List.of(Map.of("conNo", "101", "conStat", "INS", "dashNo", 3, "dateInsrvRev", "2026-11-15")))));
        p.on("VHYT54B", in -> Map.of("XDCONTROL", Map.of("urc", yardUrc, "urowcnt", 1), "VHYT54B_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY")))));
        p.on("VHYT54C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        return p;
    }

    @ParameterizedTest
    @CsvSource({"05,CY,0,0", "101,CY,8,0", "101,ZZ,0,8", "101,CY,0,0", "999,DY,0,0"})
    void forceClearMatchesTheOldScreen(String train, String location, int trainUrc, int yardUrc) {
        Parity p = forceBackend(trainUrc, yardUrc);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT54I", p.user);
        String id = (String) window(s, "VHYT54I").get("id");
        s.event(id, List.of(new ScreenSession.Change("Text1", "object", train), new ScreenSession.Change("Text2", "object", location)), "Text2", "losingFocus", null);
        s.event(id, List.of(), "Push_Button1", "clicked", null);
        String oldMessage = value(s, "VHYT54I", "Text3");
        var oldSent = new ArrayList<>(p.sent);
        // the terminal the engine's sign-on gave the old screen
        String terminal = oldSent.stream().filter(e -> e.getKey().equals("VHYT54C")).map(e -> String.valueOf(e.getValue().get("VHYT54C_DATA").get("termLastUpd")))
                .findFirst().orElse("");
        p.clear();
        String message = new YardTrackingTrainsService(p.legacy(), Parity.bundle(), p.clock, terminal).forceClear(train, location, p.user.groupId());
        assertThat(oldMessage).as("old message").isNotBlank();
        assertThat(message.strip()).isEqualTo(oldMessage);
        for (String pgm : List.of("VHYT54A", "VHYT54B", "VHYT54C")) assertThat(sent(p.sent, pgm)).as(pgm).isEqualTo(sent(oldSent, pgm));
    }

    // ---- VHYT52I pseudo correction ----------------------------------------------------------------------------

    static Map<String, Object> car(String car, String con, int dash, String stat, String vehStat, String noCars, String orient) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", car, "consist", Map.of("conNo", con, "conHyphen", "-", "dashNo", dash), "conStat", stat,
                "vehStat", vehStat, "noCarsInd", noCars, "vagenOrientation", orient, "lftOrientationCd", "<", "rgtOrientationCd", ">"));
        m.put("dateInsrvRev", "2026-11-15");
        m.put("dateInsrv", "2026-11-15");
        m.put("timeInsrv", "08:00:00");
        return m;
    }

    static Parity pseudoBackend(String yesRequest, int writeUrc) {
        Parity p = new Parity();
        List<Map<String, Object>> rows = List.of(car("1101", "201", 1, "INS", "GO", "N", "<"), car("1102", "201", 1, "INS", "GO", "N", ">"),
                car("1201", "305", 2, "CON", "GO", "N", "<"),
                car("1301", "410", 3, "CAR", "NO GO", "N", ">"), car("1302", "410", 3, "CAR", "GO", "N", "<"),
                car("1401", "512", 4, "INS", "GO", "Y", "<"));
        p.on("VHYT52A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHYT52A_DATA", Map.of("dataRow", rows)));
        p.on("VHYT52B", in -> Map.of("XDCONTROL", Map.of("urc", writeUrc, "urowcnt", 1), "VHYT52B_CRIT", Map.of("yesRequest", yesRequest)));
        return p;
    }

    @ParameterizedTest
    @CsvSource({"1,N,0", "1,Y,0", "1,N,8", "2,N,0", "3,N,0", "4,N,0"})
    void retrieveMatchesTheOldScreen(int row, String yesRequest, int writeUrc) {
        Parity p = pseudoBackend(yesRequest, writeUrc);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT52I", p.user);
        String id = (String) window(s, "VHYT52I").get("id");
        s.window(id).recordPart("VHYT52A-PASSVALUE").set("YRD-TRKG-YRD data", "CY");
        s.event(id, List.of(), "Push_Button1", "clicked", null);                   // Refresh: VHYT52A_INIT for the yard
        s.event(id, List.of(), "VAGen_Container_Details1", "select", row);
        s.event(id, List.of(), "Push_Button21", "clicked", null);
        // Text1 has three read-only bindings (MESG1, MESG5, MESG4); the engine shows the last one, the old runtime the one
        // last written. The screen's message is MESG1, which every branch of VHYT52A-RETRIEVE sets.
        String oldMessage = String.valueOf(s.window(id).recordPart("VHYT52A_MESSAGE").get("MESG1 data")).strip();
        List<List<String>> oldRows = oldRows(s, "VHYT52I", "VAGen_Container_Details1");
        var oldSent = new ArrayList<>(p.sent);
        p.clear();
        var svc = new YardTrackingTrainsService(p.legacy(), Parity.bundle(), p.clock, "");
        String whichGui = String.valueOf(s.window(id).recordPart("VHYT52A-PASSVALUE").get("WHICH-GUI data")).strip();
        var r = svc.retrieve("CY", whichGui, null, row, p.user.groupId());
        assertThat(r.message().strip()).as("message").isEqualTo(oldMessage);
        assertThat(sent(p.sent, "VHYT52B")).as("VHYT52B").isEqualTo(sent(oldSent, "VHYT52B"));
        assertThat(p.lastSent("VHYT52A").get("VHYT52A_CRIT")).as("VHYT52A").isEqualTo(oldSent.get(1).getValue().get("VHYT52A_CRIT"));
        assertThat(oldMessage).as("old message").isNotBlank();
        List<List<String>> api = new ArrayList<>();
        for (var t : r.trains()) { List<String> cells = new ArrayList<>(List.of(t.consist())); cells.addAll(t.cars()); api.add(cells); }
        assertThat(api).isEqualTo(oldRows);
    }

    // ---- VHYT31I shop activity --------------------------------------------------------------------------------

    static Parity activityBackend(int listUrc) {
        Parity p = new Parity();
        p.on("VHYT31A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHYT31A_DATA", Map.of("dataRow", List.of(
                Map.of("yard", Map.of("yrd", "HY", "yrdDesc", "HAYWARD")), Map.of("yard", Map.of("yrd", "CY", "yrdDesc", "CONCORD")),
                Map.of("yard", Map.of("yrd", "ALL", "yrdDesc", "ALL YARDS"))))));
        p.on("VHYT31H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT31H_DATA", Map.of("yrdDesc", "CONCORD")));
        p.on("VHYT31E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT31E_DATA", Map.of("dateWork", "2026-11-14")));
        p.on("VHYT31B", in -> Map.of("XDCONTROL", Map.of("urc", listUrc, "urowcnt", 2), "VHYT31B_DATA", Map.ofEntries(Map.entry("rowCnt", 2), Map.entry("rowCnt3", 1),
                Map.entry("arrivalCnt", 4), Map.entry("departCnt", 3), Map.entry("releaseCnt", 2), Map.entry("productCnt", 5), Map.entry("prodGainLossCnt", -1),
                Map.entry("availGainLossCnt", 2), Map.entry("a400Cnt", 7), Map.entry("carInCnt", 1), Map.entry("carInCnt3", 2),
                Map.entry("dataRow", List.of(Map.of("carInfo", Map.of("lftOr", "<", "carNo", "1101", "rgtOr", " "), "vehStat", "GO", "yrdSubLoc", "S-1", "posnNo", 1,
                                "moveReason", Map.of("moveReasonCd", "PM", "moveReasonDesc", "PM INSPECTION")),
                        Map.of("carInfo", Map.of("lftOr", " ", "carNo", "1102", "rgtOr", ">"), "vehStat", "NO GO", "yrdSubLoc", "S-2", "posnNo", 2))))));
        return p;
    }

    @ParameterizedTest
    @CsvSource({"CY,11-14-2026,0", "HY,2026-11-14,0", "CY,11-14-2026,100", "ALL,11-14-2026,0"})
    void activityMatchesTheOldScreen(String yard, String date, int listUrc) {
        Parity p = activityBackend(listUrc);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT31I", p.user);
        String id = (String) window(s, "VHYT31I").get("id");
        int idx = List.of("HY", "CY", "ALL").indexOf(yard) + 1;
        s.event(id, List.of(), "Drop-down_List1", "select", idx);
        s.event(id, List.of(new ScreenSession.Change("Text6", "object", date)), "pbList", "clicked", null);
        var oldSent = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHYT31I", "Text1");
        List<String> oldTotals = new ArrayList<>();
        for (String t : List.of("Text4", "Text5", "Text2", "Text31", "Text41", "Text51", "Text21", "Text7", "Text3")) oldTotals.add(value(s, "VHYT31I", t));
        List<List<String>> oldTable = oldRows(s, "VHYT31I", "VAGen_Container_Details_DATA-ROW");
        p.clear();
        var a = new YardTrackingShopActivityService(p.legacy(), Parity.bundle(), p.clock).list(yard, date);
        assertThat(a.message().strip()).isEqualTo(oldMessage);
        for (String pgm : List.of("VHYT31E", "VHYT31B")) assertThat(sent(p.sent, pgm)).as(pgm).isEqualTo(sent(oldSent, pgm));
        var t = a.totals();
        List<String> api = List.of(t.arrivals(), t.departures(), t.releases(), t.remaining(), t.production(), t.productionGainLoss(),
                t.availabilityGainLoss(), t.available0400(), t.remaining3()).stream().map(String::valueOf).toList();
        for (int i = 0; i < api.size(); i++) {
            String o = oldTotals.get(i).isEmpty() ? "0" : oldTotals.get(i).replaceFirst("^(-?)0+(?=\\d)", "$1");
            assertThat(api.get(i)).as("total " + i).isEqualTo(o);
        }
        if (listUrc == 0 && !yard.equals("ALL")) {
            assertThat(a.cars()).extracting(YardTrackingShopActivityService.Car::car).containsExactly("<1101", "1102>");
            List<List<String>> table = new ArrayList<>();
            for (var c : a.cars()) {
                List<String> cells = new ArrayList<>();
                for (String v : List.of(c.car(), c.availabilityDate(), c.availabilityTime(), c.status(), c.track(), String.valueOf(c.position()),
                        c.inShopDate(), c.inShopTime(), c.outShopDate(), c.outShopTime(), c.moveReason())) if (!v.isBlank()) cells.add(v);
                table.add(cells);
            }
            assertThat(table).as("the table").isEqualTo(oldTable);
        }
    }

    @Test
    void activityOpensLikeTheOldScreen() {
        for (String passed : List.of("", "CY")) {
            Parity p = activityBackend(0);
            ScreenSession s = p.oldScreens();
            if (passed.isEmpty()) s.openRoot("VHYT31I", p.user);
            var oldSent = new ArrayList<>(p.sent);
            p.clear();
            var o = new YardTrackingShopActivityService(p.legacy(), Parity.bundle(), p.clock).options(passed);
            assertThat(o.yards()).extracting(YardTrackingShopActivityService.Yard::code).containsExactly("HY", "CY", "ALL");
            if (passed.isEmpty()) {
                assertThat(o.yard()).isEqualTo("HY");
                assertThat(sent(p.sent, "VHYT31A")).isEqualTo(sent(oldSent, "VHYT31A"));
                assertThat(o.date()).isEqualTo(value(s, "VHYT31I", "Text6"));
            } else {
                assertThat(o.yard()).isEqualTo("CY");
                assertThat(o.yardName()).isEqualTo("CONCORD");
                assertThat(p.lastSent("VHYT31H").get("VHYT31H_CRIT")).isEqualTo(Map.of("yrd", "CY"));
            }
        }
    }

    // ---- VHYT23I / VHYT22I prints -----------------------------------------------------------------------------

    static Parity printBackend() {
        Parity p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("serverName", "S1", "prtrName", "SHOP PRINTER", "vtamName", "PRT01", "prtrLocDesc", "CONCORD"),
                Map.of("serverName", "S1", "prtrName", "OFFICE", "vtamName", "PRT02", "prtrLocDesc", "HAYWARD")))));
        p.on("VHYT23A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VHYT22A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        return p;
    }

    @Test
    void activityPrintMatchesTheOldScreen() {
        Parity p = printBackend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT23I", p.user);
        String id = (String) window(s, "VHYT23I").get("id");
        s.window(id).recordPart("VHYT23IW-PASSVALUE").set("YRD-TRKG-YRD data", "CY");
        s.window(id).recordPart("VHYT23IW-PASSVALUE").set("REPORT-DATE data", "11-14-2026");
        s.event(id, List.of(), "cntPrinterdet", "select", 2);
        s.event(id, List.of(), "Push_Button21", "clicked", null);
        var old = p.lastSent("VHYT23A");
        p.clear();
        var svc = new YardTrackingShopActivityService(p.legacy(), Parity.bundle(), p.clock);
        assertThat(svc.printers()).extracting(YardTrackingShopActivityService.Printer::name).containsExactly("PRT01", "PRT02");
        p.clear();
        svc.printActivity("CY", "11-14-2026", 2);
        assertThat(old).as("old VHYT23A").isNotNull();
        assertThat(p.lastSent("VHYT23A").get("VHYT23A_CRIT")).isEqualTo(old.get("VHYT23A_CRIT"));
    }

    @Test
    void carHistoryPrintMatchesTheOldScreen() {
        Parity p = printBackend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT22I", p.user);
        String id = (String) window(s, "VHYT22I").get("id");
        s.window(id).recordPart("VHYT22IW-PASSVALUE").set("CAR-NO data", "1101");
        s.event(id, List.of(), "cntPrinterdet", "select", 1);
        s.event(id, List.of(), "Push_Button211", "clicked", null);
        var old = p.lastSent("VHYT22A");
        p.clear();
        new YardTrackingShopActivityService(p.legacy(), Parity.bundle(), p.clock).printCarHistory("1101", 1);
        assertThat(old).as("old VHYT22A").isNotNull();
        assertThat(p.lastSent("VHYT22A").get("VHYT22A_CRIT")).isEqualTo(old.get("VHYT22A_CRIT"));
        try {
            new YardTrackingShopActivityService(p.legacy(), Parity.bundle(), p.clock).printCarHistory("1101", 9);
            throw new AssertionError("a printer outside the list is refused");
        } catch (InvalidRequestException expected) { /* refused */ }
    }
}
