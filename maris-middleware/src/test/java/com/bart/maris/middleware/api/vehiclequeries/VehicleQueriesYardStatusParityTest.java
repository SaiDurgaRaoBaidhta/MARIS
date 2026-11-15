package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Criteria;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesYardStatusService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Yard status (VHYS06I) and its reports (VHYS07I .. VHYS10I) against the old screens run by the engine. */
class VehicleQueriesYardStatusParityTest {
    static final Map<String, String[]> WIDGETS = Map.of(
            "base", new String[] {"tbnOptYardBase", "cboYardBase", "ddlNarrBase", null},
            "status", new String[] {"tbnOptYardStatus", "cboYardStatus", "ddlNarrStatus", "cboStatus"},
            "nogo", new String[] {"tbnOptYardNogo", "cboYardNoGo", null, null},
            "summary", new String[] {"tbnOptYardSummary", "cboYardSummary", null, null});

    static Map<String, Object> car(String no, String stat, String loc) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", no); m.put("dispVehStat", Map.of("vehStat", stat, "vehStatFiller", " ", "incdInd", "T")); m.put("currLoc", loc);
        m.put("atoPresentInd", "Y"); m.put("cumHrs", 1234); m.put("cumMin", 5); m.put("mtbi", 77); m.put("homeYrd", "CY");
        return m;
    }

    static Map<String, Object> typed(String type, String stat, String where, String war) {
        return Map.of("carType", Map.of("carType1", type), "vehStat", stat, "whereIsCar", where, "warInd", war);
    }

    static final List<Map<String, Object>> TYPED = List.of(typed("A", "STOR", "YRD", "N"), typed("A", "GO", "CON", "N"), typed("B", "NO GO", "YRD", "Y"),
            typed("C", "NO GO", "YRD", "N"), typed("D", "AWPART", "YRD", "N"), typed("E", "PM", "YRD", "N"), typed("L", "ENGHLD", "YRD", "N"),
            typed("L", "AWPART", "YRD", "N"), typed("M", "AWPART", "YRD", "N"), typed("M", "MODHLD", "YRD", "N"), typed("K", "MISC", "YRD", "N"),
            typed("K", "GO", "YRD", "N"), typed("C", "INSP", "YRD", "N"), typed("L", "GO", "YRD", "N"));

    @SuppressWarnings("unchecked")
    static Parity backend(int carsUrc, int nogoCars, int incidentsPerCar) {
        Parity p = new Parity();
        p.on("VHYS06A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS06A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY")))));
        p.on("VHYS06B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS06B_DATA", Map.of("dataRow", List.of(
                Map.of("vehStat", "GO", "vehStatDesc", "GO CAR"), Map.of("vehStat", "NO GO", "vehStatDesc", "NO GO CAR")))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHYS07A", in -> {
            String ret = String.valueOf(in.get("VHYS07A_CRIT").get("retInd")).strip();
            if (ret.equals("CAR")) return carsUrc != 0 ? Map.of("XDCONTROL", Map.of("urc", carsUrc))
                    : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS07A_CAR_DATA", Map.of("carCount", 2, "dataRow", List.of(car("1101", "NO GO", "HY01"), car("1102", "GO", "CY02"))));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYS07A_INCD_DATA", Map.of("carIncdCount", 1, "dataRow", List.of(
                    Map.of("carNo", "1101", "incdNo", "29990010", "sympCd", "B1", "incdLoc", "CY01", "dateIncdOccr", "2026-10-02", "timeIncdOccr", "10:15:00",
                            "conDirection", "N", "dispVehIncdStat", Map.of("vehIncdStat", "OPN", "vehIncdStatFiller", " ", "vehIncdStatDesc", "OPEN")))));
        });
        p.on("VHYS07B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYS07B_NARR_DATA", Map.of("narrCount", 1, "dataRow", List.of(Map.of("narrNo", 1, "narr", "DOOR STUCK")))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < nogoCars; i++) rows.add(Map.of("carNo", String.valueOf(1101 + i), "vehStat", "NO GO", "vehStatFiller", " ", "incdInd", "T", "homeYrd", "CY"));
        p.on("VHYS08A", in -> nogoCars == 0 ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", nogoCars), "VHYS08A_DATA", Map.of("carRow", rows)));
        p.on("VHYS08B", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHYS08B_CRIT"));
            int at = ((Number) crit.get("rowCnt")).intValue();
            List<Map<String, Object>> data = new ArrayList<>((List<Map<String, Object>>) in.get("VHYS08B_DATA").get("dataRow"));
            while (data.size() < at) data.add(Map.of());
            data = new ArrayList<>(data.subList(0, at));
            String car = String.valueOf(crit.get("carNo")).strip();
            for (int j = 0; j < incidentsPerCar; j++, at++) data.add(Map.of("carNo", j == 0 ? car : "", "storeCarNo", car,
                    "dispVehStat", Map.of("vehStat", "NO GO", "vehStatFiller", " ", "incdInd", "T"), "homeYrd", "CY", "incdNo", "2999" + car, "incdLoc", "CY02",
                    "dateIncdOccr", "2026-10-02", "timeIncdOccr", "10:15:00", "conDirection", "S"));
            crit.put("rowCnt", at);
            return Map.of("XDCONTROL", Map.of("urc", incidentsPerCar == 0 ? 100 : 0, "urowcnt", incidentsPerCar), "VHYS08B_CRIT", crit, "VHYS08B_DATA", Map.of("dataRow", data));
        });
        p.on("VHYS09A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYS09A_DATA", Map.of("dataRow", List.of(Map.of(
                "dispCarNo", Map.of("carNo", "1101", "carNoFiller", " ", "carNoStar", "*"), "dispVehStat", Map.of("vehStat", "GO", "vehStatFiller", " ", "incdInd", ""),
                "mtbi", 12, "homeYrd", "CY", "dispCarNo1", Map.of("carNo1", "1102", "carNo1Filler", " ", "carNo1Star", ""),
                "dispVehStat1", Map.of("vehStat1", "NO GO", "vehStat1Filler", " ", "incdInd1", "T"), "mtbi1", 3, "homeYrd1", "HY")))));
        p.on("VHYS10A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", TYPED.size()), "VHYS10A_DATA", Map.of("dataRow", TYPED)));
        p.on("VHYS06C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesYardStatusService api(Parity p) {
        return new VehicleQueriesYardStatusService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String widget) {
        return (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
    }

    static String value(ScreenSession s, String screen, String widget) {
        Object v = widget(s, screen, widget).get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static ScreenSession old(Parity p, Criteria c, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYS06I", p.user);
        String w = (String) window(s, "VHYS06I").get("id");
        List<ScreenSession.Change> changes = new ArrayList<>();
        String[] ws = c.option() == null ? null : WIDGETS.get(c.option());
        if (ws != null) {
            s.event(w, List.of(new ScreenSession.Change(ws[0], "selection", true)), null, null, null);
            changes.add(new ScreenSession.Change(ws[1], "entryObject", c.yard()));
            if (ws[3] != null) changes.add(new ScreenSession.Change(ws[3], "entryObject", c.status()));
            if (ws[2] != null && c.narr() != null) {
                List<String> items = (List<String>) widget(s, "VHYS06I", ws[2]).get("items");
                s.event(w, List.of(), ws[2], "select", items.indexOf(c.narr()) + 1);
            }
        }
        changes.add(new ScreenSession.Change("txtCarFrom", "object", c.carFrom()));
        changes.add(new ScreenSession.Change("txtCarTo", "object", c.carTo()));
        if (button.equals("pbtHardcopy")) s.event(w, List.of(), "cntPrinterdet", "select", 1);
        s.event(w, changes, button, "clicked", null);
        return s;
    }

    static List<Object> crit(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(program + "_CRIT"));
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void baseReportMatchesTheOldScreens() {
        Criteria c = new Criteria("base", "CY", "", "Y", "1000", "2000");
        Parity p = backend(0, 0, 0);
        ScreenSession s = old(p, c, "pbtDisplay");
        assertThat(window(s, "VHYS07I")).as("VHYS07I opened; message " + value(s, "VHYS06I", "Text1")).isNotNull();
        var oldCalls = new ArrayList<>(p.sent);
        List<List<String>> oldIncidents = (List<List<String>>) widget(s, "VHYS07I", "cntIncdDetails").get("rows");
        p.clear();
        var svc = api(p);
        var cars = svc.cars(c);
        var incidents = svc.incidents(c, cars.cars().get(0).car());
        svc.narrative(cars.cars().get(0).car(), incidents.incidents().get(0).incident(), cars.cars().get(0).narrativeYard());
        assertThat(crit(p.sent, "VHYS07A")).isEqualTo(crit(oldCalls, "VHYS07A"));
        assertThat(crit(p.sent, "VHYS07B")).isEqualTo(crit(oldCalls, "VHYS07B").stream().distinct().toList());
        assertThat(cars.criteria().title()).isEqualTo(String.valueOf(window(s, "VHYS07I").get("title")).strip());
        assertThat(cars.cars().get(0).car()).isEqualTo(value(s, "VHYS07I", "txtCarNo"));
        assertThat(incidents.incidents().get(0).status()).isEqualTo(oldIncidents.get(0).get(5).stripTrailing());
    }

    @Test
    void carsMessagesMatchTheOldScreen() {
        for (int urc : new int[] {100, 8}) {
            Criteria c = new Criteria("status", "HY", "NO GO", "N", "", "");
            Parity p = backend(urc, 0, 0);
            ScreenSession s = old(p, c, "pbtDisplay");
            String oldMsg = value(s, "VHYS07I", "Text1");
            p.clear();
            assertThat(api(p).cars(c).message()).as("urc " + urc).isEqualTo(oldMsg);
        }
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        for (Criteria c : List.of(new Criteria("base", "ZZ", "", "Y", "", ""), new Criteria("base", "", "", "Y", "", ""),
                new Criteria("status", "CY", "ZZZ", "Y", "", ""), new Criteria("status", "CY", "", "Y", "", ""), new Criteria("nogo", "", "", null, "", ""),
                new Criteria("summary", "ZZ", "", null, "", ""), new Criteria(null, "", "", null, "", ""), new Criteria("base", "CY", "", "Y", "3000", "1000"))) {
            Parity p = backend(0, 0, 0);
            ScreenSession s = old(p, c, "pbtDisplay");
            String oldMessage = value(s, "VHYS06I", "Text1");
            assertThat(oldMessage).as("old message for " + c).isNotBlank();
            String message;
            try { api(p).check(c); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
            assertThat(message).as(c.toString()).isEqualTo(oldMessage);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void nogoMatchesTheOldScreens() {
        for (int[] k : new int[][] {{2, 2}, {0, 0}, {2, 0}}) {
            Criteria c = new Criteria("nogo", "CY", "", null, "", "");
            Parity p = backend(0, k[0], k[1]);
            ScreenSession s = old(p, c, "pbtDisplay");
            assertThat(window(s, "VHYS08I")).as("VHYS08I opened").isNotNull();
            var oldCalls = new ArrayList<>(p.sent);
            List<List<String>> oldRows = (List<List<String>>) widget(s, "VHYS08I", "cntIncdDetails").get("rows");
            String oldMsg = value(s, "VHYS08I", "Text1");
            p.clear();
            var nogo = api(p).nogo(c);
            assertThat(crit(p.sent, "VHYS08A")).isEqualTo(crit(oldCalls, "VHYS08A"));
            assertThat(crit(p.sent, "VHYS08B")).isEqualTo(crit(oldCalls, "VHYS08B"));
            assertThat(nogo.incidents()).hasSize(oldRows == null ? 0 : oldRows.size());
            assertThat(nogo.message() == null ? "" : nogo.message()).as(k[0] + " cars " + k[1] + " incidents").isEqualTo(oldMsg);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void summaryAndTypeSummaryMatchTheOldScreens() {
        Criteria c = new Criteria("summary", "CY", "", null, "", "");
        Parity p = backend(0, 0, 0);
        ScreenSession s = old(p, c, "pbtDisplay");
        assertThat(window(s, "VHYS09I")).as("VHYS09I opened").isNotNull();
        String oldHeader = value(s, "VHYS09I", "lblHeader");
        List<List<String>> oldCars = (List<List<String>>) widget(s, "VHYS09I", "cntIncdDetails").get("rows");
        s.event((String) window(s, "VHYS09I").get("id"), List.of(), "pbtHomeYrdSummary", "clicked", null);
        List<List<String>> oldTypes = (List<List<String>>) widget(s, "VHYS10I", "cntCarsSummary").get("rows");
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        var sum = api(p).summary(c);
        var types = api(p).typeSummary(c.yard());
        assertThat(crit(p.sent, "VHYS09A")).isEqualTo(crit(oldCalls, "VHYS09A"));
        assertThat(crit(p.sent, "VHYS10A")).isEqualTo(crit(oldCalls, "VHYS10A"));
        assertThat(sum.header()).isEqualTo(oldHeader);
        List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
        sum.cars().forEach(x -> mine.add(x.car() + (x.atoMissing() ? " *" : "") + "|" + x.status() + "|" + x.homeYard() + "|" + x.mtbi()));
        for (List<String> r : oldCars) for (int k = 0; k < 8; k += 4) {
            if (!r.get(k).isBlank()) theirs.add(r.get(k).strip() + "|" + r.get(k + 1).stripTrailing() + "|" + r.get(k + 2).strip() + "|" + r.get(k + 3).strip());
        }
        assertThat(mine).isEqualTo(theirs);
        for (int i = 0; i < oldTypes.size(); i++) {
            List<String> row = new ArrayList<>();
            row.add(types.rows().get(i).name());
            types.rows().get(i).byType().forEach(n -> row.add(String.valueOf(n)));
            row.add(String.valueOf(types.rows().get(i).total()));
            assertThat(row).as("row " + (i + 1)).isEqualTo(oldTypes.get(i).stream().map(String::strip).toList());
        }
    }

    @Test
    void printSendsTheSameRecord() {
        for (Criteria c : List.of(new Criteria("base", "CY", "", "Y", "", ""), new Criteria("status", "HY", "GO", "N", "1000", "2000"),
                new Criteria("nogo", "CY", "", null, "", ""), new Criteria("summary", "HY", "", null, "0000", "0200"))) {
            Parity p = backend(0, 0, 0);
            ScreenSession s = old(p, c, "pbtHardcopy");
            Object sent = p.lastSent("VHYS06C") == null ? null : p.lastSent("VHYS06C").get("VHYS06C_CRIT");
            String oldMessage = value(s, "VHYS06I", "Text1");
            p.clear();
            String message;
            try { message = api(p).print(c, "DUBLNHP1"); } catch (InvalidRequestException e) { message = e.getMessage(); }
            assertThat(message).as(c.toString()).isEqualTo(oldMessage);
            assertThat(p.lastSent("VHYS06C") == null ? null : p.lastSent("VHYS06C").get("VHYS06C_CRIT")).as(c.toString()).isEqualTo(sent);
        }
    }
}
