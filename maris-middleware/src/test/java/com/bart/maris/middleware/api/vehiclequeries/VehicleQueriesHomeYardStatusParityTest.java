package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesHomeYardStatusService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Criteria;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Home yard status (VHYS01I) and its Base / Status report (VHYS02I) against the old screens run by the engine. */
class VehicleQueriesHomeYardStatusParityTest {
    /** the toggle, yard box, narrative list and (status option) status box of each option on VHYS01I */
    static final Map<String, String[]> WIDGETS = Map.of(
            "base", new String[] {"tbnHomeYardBase", "cboYard1", "ddlNarrBase", null},
            "status", new String[] {"tbnHomeYardStatus", "cboYard2", "ddlNarrStatus", "cboVeh-Status"},
            "nogo", new String[] {"tbnHomeYardNogo", "cboYard3", null, null},
            "summary", new String[] {"tbnHomeYardSummary", "cboYard4", null, null});

    static Map<String, Object> car(String no, String stat) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", no); m.put("dispVehStat", Map.of("vehStat", stat, "vehStatFiller", " ", "incdInd", "T")); m.put("currLoc", "CY01");
        m.put("atoPresentInd", "Y"); m.put("cumHrs", 1234); m.put("cumMin", 5); m.put("mtbi", 77); m.put("homeYrd", "CY");
        return m;
    }

    static Parity backend(int carsUrc) {
        Parity p = new Parity();
        p.on("VHYS01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS01A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY")))));
        p.on("VHYS01B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS01B_DATA", Map.of("dataRow", List.of(
                Map.of("vehStat", "GO", "vehStatDesc", "GO CAR"), Map.of("vehStat", "NO GO", "vehStatDesc", "NO GO CAR")))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHYS02A", in -> {
            String ret = String.valueOf(in.get("VHYS02A_CRIT").get("retInd")).strip();
            if (ret.equals("CAR")) return carsUrc != 0 ? Map.of("XDCONTROL", Map.of("urc", carsUrc))
                    : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS02A_CAR_DATA", Map.of("carCount", 2, "dataRow", List.of(car("1101", "NO GO"), car("1102", "GO"))));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYS02A_INCD_DATA", Map.of("carIncdCount", 1, "dataRow", List.of(
                    Map.of("carNo", "1101", "incdNo", "29990010", "sympCd", "B1", "incdLoc", "CY01", "dateIncdOccr", "2026-10-02", "timeIncdOccr", "10:15:00",
                            "conDirection", "N", "dispVehIncdStat", Map.of("vehIncdStat", "OPN", "vehIncdFiller", " ", "vehIncdStatDesc", "OPEN")))));
        });
        p.on("VHYS02B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS02B_NARR_DATA", Map.of("narrCount", 2, "dataRow", List.of(
                Map.of("narrNo", 1, "narr", "DOOR STUCK"), Map.of("narrNo", 2, "narr", "FIXED")))));
        p.on("VHYS01C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesHomeYardStatusService api(Parity p) {
        return new VehicleQueriesHomeYardStatusService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
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

    /** VHYS01I with one option chosen; button pbtDisplay or pbtHardcopy */
    @SuppressWarnings("unchecked")
    static ScreenSession old(Parity p, Criteria c, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYS01I", p.user);
        String w = (String) window(s, "VHYS01I").get("id");
        List<ScreenSession.Change> changes = new ArrayList<>();
        String[] ws = c.option() == null ? null : WIDGETS.get(c.option());
        if (ws != null) {
            s.event(w, List.of(new ScreenSession.Change(ws[0], "selection", true)), null, null, null);
            changes.add(new ScreenSession.Change(ws[1], "entryObject", c.yard()));
            if (ws[3] != null) changes.add(new ScreenSession.Change(ws[3], "entryObject", c.status()));
            if (ws[2] != null && c.narr() != null) {
                List<String> items = (List<String>) widget(s, "VHYS01I", ws[2]).get("items");
                s.event(w, List.of(), ws[2], "select", items.indexOf(c.narr()) + 1);
            }
        }
        changes.add(new ScreenSession.Change("txtCarFrom", "object", c.carFrom()));
        changes.add(new ScreenSession.Change("txtCarTo", "object", c.carTo()));
        if (button.equals("pbtHardcopy")) s.event(w, List.of(), "cntPrinterdet", "select", 1);
        s.event(w, changes, button, "clicked", null);
        return s;
    }

    static String oldMessage(ScreenSession s) {
        Object v = widget(s, "VHYS01I", "msgFooter").get("value");
        return v == null ? "" : v.toString().strip();
    }

    @Test
    @SuppressWarnings("unchecked")
    void baseReportMatchesTheOldScreens() {
        Criteria c = new Criteria("base", "CY", "", "Y", "1000", "2000");
        Parity p = backend(0);
        ScreenSession s = old(p, c, "pbtDisplay");
        assertThat(window(s, "VHYS02I")).as("VHYS02I opened").isNotNull();
        List<Map.Entry<String, Map<String, Map<String, Object>>>> oldCalls = new ArrayList<>(p.sent);
        Object oldTitle = window(s, "VHYS02I").get("title");
        Object oldCar = widget(s, "VHYS02I", "txtCarNo").get("value"), oldStat = widget(s, "VHYS02I", "txtVehStatus").get("value");
        List<List<String>> oldIncidents = (List<List<String>>) widget(s, "VHYS02I", "cntIncdDetails").get("rows");
        Object oldNarr = widget(s, "VHYS02I", "mleIncdNarr").get("value");

        p.clear();
        var svc = api(p);
        var cars = svc.cars(c);
        var incidents = svc.incidents(c, cars.cars().get(0).car());
        var narr = svc.narrative(cars.cars().get(0).car(), incidents.incidents().get(0).incident(), cars.cars().get(0).homeYard());
        assertThat(crit(p.sent, "VHYS02A")).isEqualTo(crit(oldCalls, "VHYS02A"));
        assertThat(crit(p.sent, "VHYS02B")).isEqualTo(crit(oldCalls, "VHYS02B"));
        assertThat(cars.criteria().title()).isEqualTo(String.valueOf(oldTitle).strip());
        assertThat(cars.cars().get(0).car()).isEqualTo(oldCar);
        assertThat(cars.cars().get(0).status()).isEqualTo(String.valueOf(oldStat).stripTrailing());
        assertThat(incidents.incidents().get(0).incident()).isEqualTo(oldIncidents.get(0).get(0));
        assertThat(incidents.incidents().get(0).status()).isEqualTo(oldIncidents.get(0).get(5).stripTrailing());
        assertThat(String.valueOf(oldNarr)).contains(narr.get(0));
    }

    /** each call of a program with its criteria, in order */
    static List<Object> crit(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(program + "_CRIT"));
        return out;
    }

    @Test
    void statusReportAndNoCarsMessage() {
        Criteria c = new Criteria("status", "HY", "NO GO", "N", "", "");
        Parity p = backend(100);
        ScreenSession s = old(p, c, "pbtDisplay");
        Object oldCrit = p.lastSent("VHYS02A").get("VHYS02A_CRIT");
        Object oldMsg = widget(s, "VHYS02I", "Text1").get("value");
        p.clear();
        var cars = api(p).cars(c);
        assertThat(p.lastSent("VHYS02A").get("VHYS02A_CRIT")).isEqualTo(oldCrit);
        assertThat(cars.message()).isEqualTo(String.valueOf(oldMsg).strip());
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        same(new Criteria("base", "ZZ", "", "Y", "", ""));             // yard not in the list
        same(new Criteria("base", "", "", "Y", "", ""));               // no yard
        same(new Criteria("status", "CY", "ZZZ", "Y", "", ""));        // status not in the list
        same(new Criteria("status", "CY", "", "Y", "", ""));           // no status
        same(new Criteria("nogo", "ZZ", "", null, "", ""));            // NOGO yard not in the list
        same(new Criteria("nogo", "", "", null, "", ""));              // NOGO no yard
        same(new Criteria("summary", "ZZ", "", null, "", ""));         // summary yard not in the list
        same(new Criteria("summary", "", "", null, "", ""));           // summary no yard
        same(new Criteria(null, "", "", null, "", ""));                // no option
        same(new Criteria("base", "CY", "", "Y", "3000", "1000"));     // to car below from car
        same(new Criteria("base", "CY", "", "Y", "10AB", "2000"));     // from car not a number
    }

    private void same(Criteria c) {
        Parity p = backend(0);
        ScreenSession s = old(p, c, "pbtDisplay");
        String oldMessage = oldMessage(s);
        assertThat(oldMessage).as("old message for " + c).isNotBlank();
        assertThat(window(s, "VHYS02I")).isNull();
        String message;
        try { api(p).check(c); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).as(c.toString()).isEqualTo(oldMessage);
    }

    @Test
    void printSendsTheSameRecord() {
        for (Criteria c : List.of(new Criteria("base", "CY", "", "Y", "", ""), new Criteria("status", "HY", "GO", "N", "1000", "2000"),
                new Criteria("nogo", "CY", "", null, "", ""), new Criteria("summary", "HY", "", null, "0100", "0200"))) {
            Parity p = backend(0);
            ScreenSession s = old(p, c, "pbtHardcopy");
            Object sent = p.lastSent("VHYS01C").get("VHYS01C_CRIT");
            String oldMessage = oldMessage(s);
            p.clear();
            assertThat(api(p).print(c, "DUBLNHP1")).as(c.toString()).isEqualTo(oldMessage);
            assertThat(p.lastSent("VHYS01C").get("VHYS01C_CRIT")).as(c.toString()).isEqualTo(sent);
        }
    }

    /** NOGO backend: VHYS03A gives the cars, VHYS03B adds each car's incidents to the data record at ROW-CNT */
    @SuppressWarnings("unchecked")
    static Parity nogoBackend(int cars, int incidentsPerCar) {
        Parity p = backend(0);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < cars; i++) rows.add(Map.of("carNo", String.valueOf(1101 + i), "vehStat", "NO GO", "vehStatFiller", " ", "incdInd", "T", "currLoc", "CY0" + i));
        p.on("VHYS03A", in -> cars == 0 ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars), "VHYS03A_DATA", Map.of("carRow", rows)));
        p.on("VHYS03B", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHYS03B_CRIT"));
            int at = ((Number) crit.get("rowCnt")).intValue();
            List<Map<String, Object>> data = new ArrayList<>((List<Map<String, Object>>) in.get("VHYS03B_DATA").get("dataRow"));
            while (data.size() < at) data.add(Map.of());
            data = new ArrayList<>(data.subList(0, at));
            String car = String.valueOf(crit.get("carNo")).strip();
            for (int j = 0; j < incidentsPerCar && at < 600; j++, at++) {
                data.add(Map.of("carNo", j == 0 ? car : "", "storeCarNo", car, "dispVehStat", Map.of("vehStat", "NO GO", "vehStatFiller", " ", "incdInd", "T"),
                        "currLoc", "CY01", "incdNo", "2999" + car, "incdLoc", "CY02", "dateIncdOccr", "2026-10-02", "timeIncdOccr", "10:15:00", "conDirection", "S"));
            }
            crit.put("rowCnt", at);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", incidentsPerCar), "VHYS03B_CRIT", crit, "VHYS03B_DATA", Map.of("dataRow", data));
        });
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void nogoReportMatchesTheOldScreens() {
        Criteria c = new Criteria("nogo", "CY", "", null, "", "");
        Parity p = nogoBackend(2, 2);
        ScreenSession s = old(p, c, "pbtDisplay");
        assertThat(window(s, "VHYS03I")).as("VHYS03I opened").isNotNull();
        List<Map.Entry<String, Map<String, Map<String, Object>>>> oldCalls = new ArrayList<>(p.sent);
        List<List<String>> oldRows = (List<List<String>>) widget(s, "VHYS03I", "cntIncdDetails").get("rows");
        p.clear();
        var nogo = api(p).nogo(c);
        assertThat(crit(p.sent, "VHYS03A")).isEqualTo(crit(oldCalls, "VHYS03A"));
        assertThat(crit(p.sent, "VHYS03B")).isEqualTo(crit(oldCalls, "VHYS03B"));
        assertThat(nogo.incidents()).hasSize(oldRows.size());
        for (int i = 0; i < oldRows.size(); i++) {
            var r = nogo.incidents().get(i);
            assertThat(List.of(r.car(), r.status(), r.location(), r.date(), r.time(), r.incidentLocation(), r.direction(), r.incident()))
                    .isEqualTo(oldRows.get(i).stream().map(String::stripTrailing).toList());
        }
        p.clear();
        api(p).narrative(nogo.incidents().get(0).incidentCar(), nogo.incidents().get(0).incident(), c.yard());
        // the old screen asks for the first narrative twice (SELECT-NARR and the row selection), with the same criteria
        assertThat(crit(p.sent, "VHYS02B")).isEqualTo(crit(oldCalls, "VHYS02B").stream().distinct().toList());
    }

    @Test
    void nogoMessagesMatchTheOldScreen() {
        for (int[] k : new int[][] {{0, 0}, {3, 250}}) {                  // no cars; 600 incident rows reached
            Criteria c = new Criteria("nogo", "CY", "", null, "", "");
            Parity p = nogoBackend(k[0], k[1]);
            ScreenSession s = old(p, c, "pbtDisplay");
            Object oldMsg = widget(s, "VHYS03I", "tstMsg2").get("value");
            p.clear();
            assertThat(api(p).nogo(c).message()).as(k[0] + " cars").isEqualTo(String.valueOf(oldMsg).strip());
        }
    }

    static Map<String, Object> pair(String a, String star, String b) {
        Map<String, Object> m = new HashMap<>();
        m.put("dispCarNo", Map.of("carNo", a, "carNoFiller", " ", "carNoStar", star));
        m.put("dispVehStat", Map.of("vehStat", "GO", "vehStatFiller", " ", "incdInd", "")); m.put("currLoc", "CY01"); m.put("homeYrd", "CY");
        m.put("dispCarNo1", Map.of("carNo1", b, "carNo1Filler", " ", "carNo1Star", ""));
        m.put("dispVehStat1", Map.of("vehStat1", "NO GO", "vehStat1Filler", " ", "incdInd1", "T")); m.put("currLoc1", "HY02"); m.put("homeYrd1", "CY");
        return m;
    }

    static Map<String, Object> typed(String type, String stat, String where, String war) {
        return Map.of("carType", Map.of("carType1", type), "vehStat", stat, "whereIsCar", where, "warInd", war);
    }

    static final List<Map<String, Object>> TYPED = List.of(typed("A", "STOR", "YRD", "N"), typed("A", "GO", "CON", "N"), typed("B", "NO GO", "YRD", "Y"),
            typed("C", "NO GO", "YRD", "N"), typed("D", "AWPART", "YRD", "N"), typed("E", "PM", "YRD", "N"), typed("L", "ENGHLD", "YRD", "N"),
            typed("M", "MODHLD", "YRD", "N"), typed("K", "MISC", "YRD", "N"), typed("K", "GO", "YRD", "N"), typed("C", "INSP", "YRD", "N"), typed("X", "GO", "YRD", "N"));

    @Test
    @SuppressWarnings("unchecked")
    void summaryAndTypeSummaryMatchTheOldScreens() {
        Criteria c = new Criteria("summary", "CY", "", null, "", "");
        Parity p = backend(0);
        p.on("VHYS04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS04A_DATA", Map.of("dataRow", List.of(pair("1101", "*", "1102"), pair("1103", "", "")))));
        p.on("VHYS05A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", TYPED.size()), "VHYS05A_DATA", Map.of("dataRow", TYPED)));
        ScreenSession s = old(p, c, "pbtDisplay");
        assertThat(window(s, "VHYS04I")).as("VHYS04I opened").isNotNull();
        Object oldHeader = widget(s, "VHYS04I", "lblHeader").get("value");
        List<List<String>> oldCars = (List<List<String>>) widget(s, "VHYS04I", "cntIncdDetails").get("rows");
        Object oldCrit = p.lastSent("VHYS04A").get("VHYS04A_CRIT");
        s.event((String) window(s, "VHYS04I").get("id"), List.of(), "pbtHomeYrdSummary", "clicked", null);
        assertThat(window(s, "VHYS05I")).as("VHYS05I opened").isNotNull();
        List<List<String>> oldTypes = (List<List<String>>) widget(s, "VHYS05I", "cntCarsSummary").get("rows");
        Object oldTypeCrit = p.lastSent("VHYS05A").get("VHYS05A_CRIT");

        p.clear();
        var sum = api(p).summary(c);
        assertThat(p.lastSent("VHYS04A").get("VHYS04A_CRIT")).isEqualTo(oldCrit);
        assertThat(sum.header()).isEqualTo(String.valueOf(oldHeader).strip());
        List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
        sum.cars().forEach(x -> mine.add((x.car() + (x.atoMissing() ? " *" : "")) + "|" + x.status() + "|" + x.location()));
        for (List<String> r : oldCars) for (int k = 0; k < 8; k += 4) if (!r.get(k).isBlank()) theirs.add(r.get(k).strip() + "|" + r.get(k + 1).stripTrailing() + "|" + r.get(k + 2).strip());
        assertThat(mine).isEqualTo(theirs);
        var types = api(p).typeSummary(c.yard());
        assertThat(p.lastSent("VHYS05A").get("VHYS05A_CRIT")).isEqualTo(oldTypeCrit);
        assertThat(types.rows()).hasSize(oldTypes.size());
        for (int i = 0; i < oldTypes.size(); i++) {
            List<String> row = new ArrayList<>();
            row.add(types.rows().get(i).name());
            types.rows().get(i).byType().forEach(n -> row.add(String.valueOf(n)));
            row.add(String.valueOf(types.rows().get(i).total()));
            assertThat(row).as("row " + (i + 1)).isEqualTo(oldTypes.get(i).stream().map(String::strip).toList());
        }
    }

    @Test
    void summaryWithoutCars() {
        Criteria c = new Criteria("summary", "HY", "", null, "", "");
        Parity p = backend(0);
        p.on("VHYS04A", in -> Map.of("XDCONTROL", Map.of("urc", 100)));
        ScreenSession s = old(p, c, "pbtDisplay");
        Object d = s.state().get("dialog");
        assertThat(d).isNotNull();
        p.clear();
        assertThat(api(p).summary(c).message()).isEqualTo(String.valueOf(((Map<?, ?>) d).get("message")).strip());
    }
}
