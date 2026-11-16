package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistLogService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Consist log (VHCL01I criteria, VHCL02I display) against the old screens run by the engine. */
class VehicleQueriesConsistLogParityTest {
    /** consist, dash, cars (MAX-POSN-NO), split or swap partner */
    static final int[][] CONSISTS = {{201, 1, 2}, {202, 3, 3}, {203, 1, 1}, {204, 1, 10}, {205, 2, 2}, {206, 1, 4}};

    static Map<String, Object> consist(int con, int dash, int posn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("consist", Map.of("conNoX", String.valueOf(con), "dash", "-", "dashNoX", dash));
        m.put("conNo", String.valueOf(con));
        m.put("dashNo", dash);
        m.put("originLoc", "CY0" + (con % 10));
        m.put("outsrvLoc", "DY0" + (con % 10));
        m.put("timeInsrv", "05:1" + (con % 10) + ":00");
        m.put("timeOutsrv", "09:3" + (con % 10) + ":00");
        m.put("dateInsrvRev", "2026-11-12");
        m.put("runTimeHrs", con % 10);
        m.put("runTimeMin", 20 + con % 10);
        if (con == 201) m.put("consistSwap", Map.of("conNoSwap", "305", "dashSwap", "-", "dashNoSwap", 1));
        if (con == 202) m.put("consistSplit", Map.of("conNoSplit", "306", "dashSplit", "-", "dashNoSplit", 2));
        m.put("washInd", con % 2 == 0 ? "Y" : "N");
        m.put("maxPosnNo", posn);
        m.put("exactRowCnt", posn);
        return m;
    }

    static List<Map<String, Object>> cars(int[][] consists) {
        List<Map<String, Object>> out = new ArrayList<>();
        int car = 1100;
        for (int[] c : consists) {
            for (int k = 0; k < c[2]; k++) {
                car++;
                out.add(Map.of("consist", Map.of("conNoX", String.valueOf(c[0]), "dash", "-", "dashNoX", c[1]),
                        "carNoX", Map.of("lftOr", k == 0 ? "<" : " ", "carNo", String.valueOf(car), "rgtOr", k == c[2] - 1 ? ">" : " "),
                        "softVer", k == 0 || k == 3 ? "$1.2" : "1.0", "homeYrd", k % 2 == 0 ? "CY" : "HY", "carWnInd", k == 1 ? "Y" : "N",
                        "conWnInd", c[0] == 202 ? "Y" : "N"));
            }
        }
        return out;
    }

    static Parity backend(int consistUrc, List<Map<String, Object>> consistRows, List<Map<String, Object>> carRows, String exceed, int dateUrc) {
        Parity p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHCL01E", in -> Map.of("XDCONTROL", Map.of("urc", dateUrc), "VHCL01E_DATA", Map.of("dateWork", in.get("VHCL01E_CRIT").get("dateWork"))));
        p.on("VHCL02B", in -> consistUrc != 0 ? Map.of("XDCONTROL", Map.of("urc", consistUrc)) : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", consistRows.size()),
                "VHCL02B_DATA", Map.of("exceedLimit", exceed, "dataRow", consistRows)));
        p.on("VHCL02C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", carRows.size()), "VHCL02C_DATA", Map.of("dataRow", carRows)));
        p.on("VHCL011", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static Parity standard() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int[] c : CONSISTS) rows.add(consist(c[0], c[1], c[2]));
        return backend(0, rows, cars(CONSISTS), "N", 0);
    }

    static VehicleQueriesConsistLogService api(Parity p) {
        return new VehicleQueriesConsistLogService(p.legacy(), p.messages(), com.bart.maris.middleware.support.TestCatalog.get(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> w = window(s, screen);
        if (w == null) return "(no " + screen + ")";
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        if (x == null) return "(no " + widget + ")";
        Object v = x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static String dialog(ScreenSession s) {
        Object d = s.state().get("dialog");
        return d == null ? null : String.valueOf(((Map<?, ?>) d).get("message")).strip();
    }

    static ScreenSession old(Parity p, String button, String from, String to, String date, boolean printer) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCL01I", p.user);
        String w = (String) window(s, "VHCL01I").get("id");
        if (printer) s.event(w, List.of(), "Container_Details1", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtFromdate", "object", from), new ScreenSession.Change("txtTodate", "object", to),
                new ScreenSession.Change("Text2", "object", date)), button, "clicked", null);
        return s;
    }

    static String mdyToIso(String mdy) {
        return mdy.substring(6, 10) + "-" + mdy.substring(0, 2) + "-" + mdy.substring(3, 5);
    }

    /** VHCL02I's widgets of row r (1..4) */
    static final String[][] ROW = {
            {"Consist1", "Origin1", "OutSrv_loc1", "TimeInServ1", "TimeOutServ1", "RunTime1", "Swap1", "Split1", "Washed1", "CarCnt1"},
            {"Consist2", "OriginLoc2", "OutSrvLoc2", "TimeInSrv2", "TimeOutSrv2", "RunTime2", "ConsistSwap2", "ConsistSplit2", "WashInd2", "CarCnt2"},
            {"Consist3", "OriginLoc3", "OutSrvLoc3", "TimeInSrv3", "TimeOutSrv3", "RunTime3", "ConsisSwap3", "ConsistSplit3", "WashInd3", "CarCnt3"},
            {"Consist4", null, "OutSrvLoc4", "TimeInsrv4", "TimeOutSrv4", "RunTime4", "ConsistSwap4", "ConsistSplit4", "WashInt4", "CarCnt4"}};

    static void samePage(ScreenSession s, VehicleQueriesConsistLogService.ConsistLog log, int page) {
        for (int r = 0; r < VehicleQueriesConsistLogService.PAGE; r++) {
            int i = (page - 1) * VehicleQueriesConsistLogService.PAGE + r;
            if (i >= log.consists().size()) {
                assertThat(value(s, "VHCL02I", ROW[r][0])).as("row " + (r + 1) + " empty").isEmpty();
                continue;
            }
            var c = log.consists().get(i);
            String[] got = {c.consist(), c.origin(), c.outOfServiceLocation(), c.timeIn(), c.timeOut(), c.runTime(), c.swap(), c.split(), c.washed(),
                    String.valueOf(c.carCount())};
            for (int k = 0; k < got.length; k++) {
                if (ROW[r][k] == null) continue;                                // VHCL02I shows no origin on its 4th row
                assertThat(got[k]).as("page " + page + " row " + (r + 1) + " " + ROW[r][k]).isEqualTo(value(s, "VHCL02I", ROW[r][k]));
            }
            for (int k = 1; k <= 10; k++) {
                String car = k <= c.cars().size() ? c.cars().get(k - 1).shown() : "";
                String yard = k <= c.cars().size() ? c.cars().get(k - 1).homeYard() : "";
                assertThat(car).as("page " + page + " row " + (r + 1) + " car " + k).isEqualTo(value(s, "VHCL02I", "CarNo" + (r + 1) + "_" + k));
                assertThat(yard).as("page " + page + " row " + (r + 1) + " yard " + k).isEqualTo(value(s, "VHCL02I", "HY" + (r + 1) + "_" + k));
            }
        }
    }

    @Test
    void displaySendsTheSameCriteriaAndShowsTheSameConsists() {
        Parity p = standard();
        ScreenSession s = old(p, "pbtDisplay", "201", "206", "11/12/2026", false);
        assertThat(window(s, "VHCL02I")).as("VHCL02I opened").isNotNull();
        var oldB = p.lastSent("VHCL02B").get("VHCL02B_CRIT");
        var oldC = p.lastSent("VHCL02C").get("VHCL02C_CRIT");
        var oldE = p.lastSent("VHCL01E").get("VHCL01E_CRIT");
        p.clear();
        var log = api(p).log("201", "206", "2026-11-12");
        assertThat(p.lastSent("VHCL01E").get("VHCL01E_CRIT")).isEqualTo(oldE);
        assertThat(p.lastSent("VHCL02B").get("VHCL02B_CRIT")).isEqualTo(oldB);
        assertThat(p.lastSent("VHCL02C").get("VHCL02C_CRIT")).isEqualTo(oldC);
        assertThat(log.message()).isEqualTo(value(s, "VHCL02I", "FinalMsg"));
        assertThat(String.valueOf(log.consistCount())).isEqualTo(value(s, "VHCL02I", "txt_countOfConsists"));
        assertThat(String.valueOf(log.pages())).isEqualTo(value(s, "VHCL02I", "txt_TotalPage"));
        assertThat(log.criteria().revenueDate()).isEqualTo(value(s, "VHCL02I", "txtRevdat"));
        samePage(s, log, 1);
        s.event((String) window(s, "VHCL02I").get("id"), List.of(), "Next_btn", "clicked", null);
        assertThat(value(s, "VHCL02I", "txt_pageCount")).isEqualTo("2");
        samePage(s, log, 2);
        assertThat(log.consists().get(1).warning()).isTrue();
        assertThat(log.consists().get(0).cars().get(1).warning()).isTrue();
    }

    @Test
    void allAndAnEmptyToSendTheSameRange() {
        for (String[] range : new String[][] {{"ALL", ""}, {"202", ""}, {"5", "7"}}) {
            Parity p = standard();
            old(p, "pbtDisplay", range[0], range[1], "11/12/2026", false);
            var oldB = p.lastSent("VHCL02B").get("VHCL02B_CRIT");
            p.clear();
            api(p).log(range[0], range[1], "2026-11-12");
            assertThat(p.lastSent("VHCL02B").get("VHCL02B_CRIT")).as(range[0] + ".." + range[1]).isEqualTo(oldB);
        }
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        same(standard(), "", "", "11/12/2026");
        same(standard(), "0", "", "11/12/2026");
        same(standard(), "ABC", "", "11/12/2026");
        same(standard(), "205", "201", "11/12/2026");
        List<Map<String, Object>> rows = List.of(consist(201, 1, 2));
        same(backend(0, rows, cars(new int[][] {{201, 1, 2}}), "N", 8), "201", "", "13/45/2026");     // VHCL01E: not a date
        same(backend(0, rows, cars(new int[][] {{201, 1, 2}}), "N", 8), "0", "", "13/45/2026");       // both: the date's message wins
    }

    private void same(Parity p, String from, String to, String date) {
        ScreenSession s = old(p, "pbtDisplay", from, to, date, false);
        String oldMessage = value(s, "VHCL01I", "Text1");
        assertThat(oldMessage).as("old message for " + from + ".." + to + " " + date).isNotBlank();
        assertThat(window(s, "VHCL02I")).isNull();
        p.clear();
        String iso = date.startsWith("13") ? date : mdyToIso(date);
        String message;
        try { api(p).log(from, to, iso); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).as(from + ".." + to + " " + date).isEqualTo(oldMessage);
    }

    @Test
    void closingMessagesMatch() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int[] c : CONSISTS) rows.add(consist(c[0], c[1], c[2]));
        List<Map<String, Object>> shuffled = new ArrayList<>(cars(CONSISTS));
        java.util.Collections.swap(shuffled, 2, 6);                            // consist 202's cars out of step: "Database has been changed"
        for (Parity p : List.of(backend(100, rows, cars(CONSISTS), "N", 0), backend(0, rows, cars(CONSISTS), "Y", 0), backend(0, rows, shuffled, "N", 0),
                backend(0, rows, List.of(), "N", 0))) {
            ScreenSession s = old(p, "pbtDisplay", "201", "206", "11/12/2026", false);
            p.clear();
            var log = api(p).log("201", "206", "2026-11-12");
            assertThat(log.message()).isEqualTo(value(s, "VHCL02I", "FinalMsg"));
            assertThat(String.valueOf(log.consistCount())).isEqualTo(value(s, "VHCL02I", "txt_countOfConsists"));
            samePage(s, log, 1);
        }
    }

    @Test
    void printSendsTheSameRecord() {
        Parity p = standard();
        ScreenSession s = old(p, "pbtHardcopy", "ALL", "", "11/12/2026", true);
        var sent = p.lastSent("VHCL011").get("VHCL011_CRIT");
        String oldMessage = dialog(s);
        p.clear();
        String message = api(p).print("ALL", "", "2026-11-12", "DUBLNHP1");
        assertThat(p.lastSent("VHCL011").get("VHCL011_CRIT")).isEqualTo(sent);
        assertThat(message).isEqualTo(oldMessage);
    }
}
