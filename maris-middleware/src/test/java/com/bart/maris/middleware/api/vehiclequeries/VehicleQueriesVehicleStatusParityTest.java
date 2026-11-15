package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesVehicleStatusService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesVehicleStatusService.Criteria;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Vehicle status (VHSV01I criteria, VHSV02I report) against the old screens run by the engine. */
class VehicleQueriesVehicleStatusParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final LocalDate TODAY = LocalDate.now();

    static Map<String, Object> incident(String no, String con, int dash) {
        Map<String, Object> m = new HashMap<>();
        m.put("incdNo", no); m.put("incdConNo", con); m.put("incdDashNo", dash); m.put("dateIncdOccr", "2026-10-02"); m.put("timeIncdOccr", "10:15:00");
        m.put("sympCd", "0120"); m.put("incdLoc", "CY01"); m.put("vehIncdStat", "DEF"); m.put("conDirection", "N"); m.put("dateInsrvRev", "2026-10-02");
        m.put("dateInsrv", "2026-10-02"); m.put("timeInsrv", "05:00:00"); m.put("cumHrs", 321); m.put("deferredPriority", "B");
        return m;
    }

    static Parity backend(int carUrc, Set<String> bad) {
        Parity p = new Parity();
        p.on("VHSV011", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV011_DATA", Map.of("fromDate", TODAY.minusDays(30).toString())));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHINC6A", in -> Map.of("XDCONTROL", Map.of("urc", carUrc, "urowcnt", carUrc == 0 ? 1 : 0)));
        p.on("AFCXVLD", in -> {
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            c.put("dateError", bad.contains(String.valueOf(c.get("formDate")).strip()) ? "Y" : "N");
            c.put("otherSqlError", "N");
            return Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", c);
        });
        p.on("VHSV02A", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHSV02A_CRIT"));
            crit.put("rowCount", 2);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSV02A_CRIT", crit,
                    "VHSV02A_DATA_01", Map.of("currLoc", "CY01", "dispVehStat", Map.of("vehStat", "NO GO", "vehStatFiller", " T"), "homeYrd", "CY",
                            "atoPresentInd", "Y", "mtbi", 77, "warInd", "N", "nextSchdPmNo", "05", "hrsFromLastPm", 12, "cntTimesWash", 3),
                    "VHSV02A_DATA_03", Map.of("dataRow", List.of(incident("29990010", "201", 3), incident("29990011", "", 0))));
        });
        p.on("VHNARX", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHNARX_DATA", Map.of("dataRow", List.of(Map.of("narr", "DOOR STUCK")))));
        p.on("VHSV02C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSV02C_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "orientation", "<"), Map.of("carNo", "1102", "orientation", ">")))));
        p.on("VHSV012", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHSV05A", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHSV05A_CRIT"));
            crit.put("rowCount", 1);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV05A_CRIT", crit,
                    "VHSV05A_DATA_01", Map.of("currLoc", "HY02", "dispVehStat", Map.of("vehStat", "GO", "vehStatFiller", ""), "homeYrd", "HY", "mtbi", 9),
                    "VHSV05A_DATA_03", Map.of("dataRow", List.of(Map.of("incdNo", "29990020", "dateIncdOccr", "2026-10-05", "timeIncdOccr", "08:00:00",
                            "sympCd", "0300", "incdLoc", "HY01", "vehIncdStat", "CLO", "conDirection", "S", "cumHrs", 55, "pmTeam", "A"))));
        });
        p.on("VHSV05F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV05F_DATA", Map.of("dataRow", List.of(Map.of("narr", "BRAKES")))));
        p.on("VHSV05C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV05C_CRIT", Map.of("incdNo", "29990020", "reprRowCount", 1),
                "VHSV05C_DATA", Map.of("dataRow", List.of(Map.of("reprDocNo", 2, "vehSystem", "BR", "vehSubSystem", "01", "reprDispCd", "R",
                        "serNoOn", "S-ON", "serNoOff", "S-OFF", "vehPrtCtlNo", "PCT1")))));
        p.on("VHNARY", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHNARY_DATA", Map.of("dataRow", List.of(Map.of("narr", "PADS REPLACED")))));
        p.on("VHSV05D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV05D_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntFaultCd", "F1", "cmpntDocNo", 1, "cmpntCd", "BRK", "reprDispCd", "R", "serNoOff", "SER9")))));
        return p;
    }

    static VehicleQueriesVehicleStatusService api(Parity p) {
        return new VehicleQueriesVehicleStatusService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Object v = ((Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget)).get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static Object rows(ScreenSession s, String screen, String widget) {
        return ((Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget)).get("rows");
    }

    static String mdy(String iso) {
        return iso.isEmpty() ? "" : MDY.format(LocalDate.parse(iso));
    }

    static ScreenSession old(Parity p, Criteria c, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSV01I", p.user);
        String w = (String) window(s, "VHSV01I").get("id");
        if (c.report() == 2) s.event(w, List.of(), "rbtReportSel", "select", 2);
        if (button.equals("pbtHardcopy")) s.event(w, List.of(), "cntPrinter", "select", 1);
        List<ScreenSession.Change> ch = new ArrayList<>(List.of(new ScreenSession.Change("txtCarno", "object", c.car()),
                new ScreenSession.Change("txtFromdate", "object", mdy(c.fromDate())), new ScreenSession.Change("txtTodate", "object", mdy(c.toDate())),
                new ScreenSession.Change("txtSympcdfrom", "object", c.sympFrom()), new ScreenSession.Change("txtSympcdto", "object", c.sympTo())));
        if (c.report() == 2) ch.add(new ScreenSession.Change("cmbDefitems", "object", c.deferredItems()));
        s.event(w, ch, button, "clicked", null);
        return s;
    }

    static final String FROM = TODAY.minusDays(20).toString(), TO = TODAY.minusDays(1).toString();

    @Test
    @SuppressWarnings("unchecked")
    void reportMatchesTheOldScreens() {
        for (Criteria c : List.of(new Criteria(1, "1101", FROM, TO, "0100", "0200", ""), new Criteria(2, "1101", FROM, TO, "", "", "ALL"),
                new Criteria(2, "1101", FROM, "", "0100", "", ""))) {
            Parity p = backend(0, Set.of());
            ScreenSession s = old(p, c, "pbtDisplay");
            assertThat(window(s, "VHSV02I")).as("VHSV02I opened for " + c + ": " + value(s, "VHSV01I", "msgFooter")).isNotNull();
            var oldCalls = new ArrayList<>(p.sent);
            List<List<String>> oldRows = (List<List<String>>) rows(s, "VHSV02I", "cntIncdreprdet");
            String oldStatus = value(s, "VHSV02I", "txtVehstat"), oldLoc = value(s, "VHSV02I", "txtLoc"), oldTitle = String.valueOf(window(s, "VHSV02I").get("title"));
            p.clear();
            var svc = api(p);
            var r = svc.report(c);
            for (String prog : List.of("VHSV02A")) {
                assertThat(sent(p.sent, prog, "VHSV02A_CRIT")).as(c + " crit").isEqualTo(sent(oldCalls, prog, "VHSV02A_CRIT").stream().map(VehicleQueriesVehicleStatusParityTest::noCount).toList());
                assertThat(sent(p.sent, prog, "VHSV02AW1")).as(c + " where").isEqualTo(sent(oldCalls, prog, "VHSV02AW1"));
            }
            assertThat(r.car().status()).isEqualTo(oldStatus);
            assertThat(r.car().location()).isEqualTo(oldLoc);
            // the old title is fixed-width fields (spacing differs) followed by the screen version (TITLE-7)
            String version = new EglTexts(Parity.bundle()).assignedAll("VHSV02IS-RETDATA", "MRSVehicleQueries", "TITLE-7").get(0).replaceAll("\\s", "");
            assertThat(r.title().replaceAll("\\s", "")).isEqualTo(oldTitle.replaceAll("\\s", "").replace(version, ""));
            assertThat(oldRows.get(0).get(0)).isEqualTo(r.incidents().get(0).incident());
            svc.narrative(r.incidents().get(0).incident());
            var i = r.incidents().get(0);
            var con = svc.consist(i.consist(), i.dash(), i.dateInServiceRev(), i.dateInService(), i.timeInService());
            assertThat(sent(p.sent, "VHNARX", "VHNARX_DATA").get(0)).isEqualTo(sent(oldCalls, "VHNARX", "VHNARX_DATA").get(0));
            assertThat(sent(p.sent, "VHSV02C", "VHSV02C_CRIT")).isEqualTo(sent(oldCalls, "VHSV02C", "VHSV02C_CRIT"));
            assertThat(con.cars().get(0)).isEqualTo(value(s, "VHSV02I", "txtCar1"));
            assertThat(con.cars().get(1)).isEqualTo(value(s, "VHSV02I", "txtCar2"));
            // the consist label (strLib.formatNumber(dash, "(##)")) is not compared: the engine formats it unlike EGL
        }
    }

    /** the row count VHSV02A returns into its criteria record is not part of what the screen sends the first time */
    static Object noCount(Object crit) {
        Map<String, Object> m = new HashMap<>(Map.class.cast(crit));
        m.put("rowCount", 0L);
        return m;
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        same(backend(0, Set.of()), new Criteria(1, "", FROM, TO, "", "", ""));                 // no car
        same(backend(100, Set.of()), new Criteria(1, "9999", FROM, TO, "", "", ""));           // car not found
        same(backend(0, Set.of(FROM)), new Criteria(1, "1101", FROM, TO, "", "", ""));         // from date incorrect
        same(backend(0, Set.of(TO)), new Criteria(1, "1101", FROM, TO, "", "", ""));           // to date incorrect
        same(backend(0, Set.of()), new Criteria(1, "1101", FROM, TODAY.plusDays(2).toString(), "", "", ""));   // to after today
        same(backend(0, Set.of()), new Criteria(1, "1101", TO, FROM, "", "", ""));             // from after to
        same(backend(0, Set.of()), new Criteria(1, "1101", FROM, TO, "0200", "0100", ""));     // symptoms reversed
        same(backend(0, Set.of()), new Criteria(1, "1101", FROM, TO, "01A0", "", ""));         // from symptom not a number
        same(backend(0, Set.of()), new Criteria(1, "1101", FROM, TO, "0100", "02X0", ""));     // to symptom not a number
        same(backend(0, Set.of()), new Criteria(2, "1101", FROM, TO, "", "", "Z"));            // deferred item not listed
    }

    private void same(Parity p, Criteria c) {
        ScreenSession s = old(p, c, "pbtDisplay");
        String oldMessage = value(s, "VHSV01I", "msgFooter");
        assertThat(oldMessage).as("old message for " + c).isNotBlank();
        assertThat(window(s, "VHSV02I")).isNull();
        String message;
        try { api(p).check(c); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).as(c.toString()).isEqualTo(oldMessage);
    }

    @Test
    void printSendsTheSameRecord() {
        for (Criteria c : List.of(new Criteria(1, "1101", FROM, TO, "0100", "0200", ""), new Criteria(2, "1101", FROM, TO, "", "", "A"))) {
            Parity p = backend(0, Set.of());
            ScreenSession s = old(p, c, "pbtHardcopy");
            Object sent = p.lastSent("VHSV012").get("VHSV012_CRIT");
            Object d = s.state().get("dialog");
            p.clear();
            String message = api(p).print(c, "DUBLNHP1");
            assertThat(p.lastSent("VHSV012").get("VHSV012_CRIT")).as(c.toString()).isEqualTo(sent);
            assertThat(message).isEqualTo(String.valueOf(((Map<?, ?>) d).get("message")).strip());
        }
    }

    static ScreenSession oldHistory(Parity p, Criteria c, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSV04I", p.user);
        String w = (String) window(s, "VHSV04I").get("id");
        if (button.equals("pbtPrint")) s.event(w, List.of(), "cntPrinter", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtCarno", "object", c.car()), new ScreenSession.Change("txtFromdate", "object", mdy(c.fromDate())),
                new ScreenSession.Change("txtTodate", "object", mdy(c.toDate())), new ScreenSession.Change("txtSympcdfrom", "object", c.sympFrom()),
                new ScreenSession.Change("txtSympcdto", "object", c.sympTo())), button, "clicked", null);
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyMatchesTheOldScreens() {
        for (Criteria c : List.of(new Criteria(0, "1101", FROM, TO, "0100", "0400", ""), new Criteria(0, "1101", "", TO, "", "", ""))) {
            Parity p = backend(0, Set.of());
            ScreenSession s = oldHistory(p, c, "pbtDisplay");
            assertThat(window(s, "VHSV05I")).as("VHSV05I opened: " + value(s, "VHSV04I", "msgFooter")).isNotNull();
            var oldCalls = new ArrayList<>(p.sent);
            String oldTitle = String.valueOf(window(s, "VHSV05I").get("title"));
            p.clear();
            var svc = api(p);
            var hist = svc.history(c);
            String incident = hist.incidents().get(0).incident();
            svc.historyNarrative(incident);
            var repairs = svc.repairs(incident);
            svc.repair(incident, repairs.get(0).document());
            for (String[] pr : new String[][] {{"VHSV05A", "VHSV05A_CRIT"}, {"VHSV05F", "VHSV05F_CRIT"}, {"VHNARY", "VHNARY_CRIT"}, {"VHSV05D", "VHSV05D_CRIT"}}) {
                assertThat(sent(p.sent, pr[0], pr[1])).as(c + " " + pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]).stream().distinct().toList());
            }
            assertThat(sent(p.sent, "VHSV05C", "VHSV05C_CRIT").get(0)).isEqualTo(sent(oldCalls, "VHSV05C", "VHSV05C_CRIT").get(0));
            assertThat(hist.car().location()).isEqualTo(value(s, "VHSV05I", "txtLoc"));
            assertThat(incident).isEqualTo(value(s, "VHSV05I", "txtIncdNo"));
            assertThat(repairs.get(0).serialOn()).isEqualTo(value(s, "VHSV05I", "txtSernoon"));
            String version = new EglTexts(Parity.bundle()).assignedAll("VHSV05IS-RETDATA", "MRSVehicleQueries", "TITLE-6").get(0).replaceAll("\\s", "");
            assertThat(hist.title().replaceAll("\\s", "")).isEqualTo(oldTitle.replaceAll("\\s", "").replace(version, ""));
        }
    }

    @Test
    void historyMessagesAndPrintMatchTheOldScreen() {
        for (Criteria c : List.of(new Criteria(0, "", FROM, TO, "", "", ""), new Criteria(0, "1101", TO, FROM, "", "", ""),
                new Criteria(0, "1101", FROM, TO, "0400", "0100", ""))) {
            Parity p = backend(0, Set.of());
            ScreenSession s = oldHistory(p, c, "pbtDisplay");
            String oldMessage = value(s, "VHSV04I", "msgFooter");
            assertThat(oldMessage).as("old message for " + c).isNotBlank();
            String message;
            try { api(p).history(c); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
            assertThat(message).as(c.toString()).isEqualTo(oldMessage);
        }
        for (Criteria c : List.of(new Criteria(0, "1101", FROM, TO, "", "", ""), new Criteria(0, "1101", FROM, TO, "0100", "0300", ""))) {
            Parity p = backend(0, Set.of());
            ScreenSession s = oldHistory(p, c, "pbtPrint");
            Object sent = p.lastSent("VHSV012").get("VHSV012_CRIT");
            Object d = s.state().get("dialog");
            p.clear();
            String message = api(p).printHistory(c, "DUBLNHP1");
            assertThat(p.lastSent("VHSV012").get("VHSV012_CRIT")).as(c.toString()).isEqualTo(sent);
            assertThat(message).isEqualTo(String.valueOf(((Map<?, ?>) d).get("message")).strip());
        }
    }
}
