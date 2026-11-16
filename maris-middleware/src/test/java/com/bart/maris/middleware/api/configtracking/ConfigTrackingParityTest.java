package com.bart.maris.middleware.api.configtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.domain.configtracking.ConfigTrackingCarService;
import com.bart.maris.middleware.domain.configtracking.ConfigTrackingReportService;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Car configuration (VHCF01I) and its report printer window (VFCF05I): the clean API against the old screens run by the
 * engine, same scripted backend. Compares every record sent (the reads and the writers) and the message / values shown.
 */
class ConfigTrackingParityTest {
    static final String TERMINAL = "MARIS-UI";                       // the terminal id the engine signs the old screens on with
    /** the programs VHCF01I's Update may call */
    static final List<String> WRITERS = List.of("VHCF01C", "VHCF01D", "VHCF01F", "VHCF01G", "VHCF01H", "VHCF01J", "VHCF01K", "VHCF01L", "VHCF01M", "VHCF01N",
            "VHCF01P", "VHCF01Q", "VHCF01S", "VHCF01T", "VHCF01U", "VHCF01X", "VHCF01Y");
    private Parity p;

    private void backend(boolean carValid, boolean trucks) {
        p = new Parity();
        p.on("VHCF01A", in -> carValid ? Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCF01A_DATA", Map.of("carNo", "1101", "carType", "A2", "homeYrd", "HY",
                "acDcCd", "DC")) : Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0)));
        p.on("VHCF01B", in -> {
            if (!trucks) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(part("TRUCK", "X", "TRKX1", "", ""));
            rows.add(part("TRUCK", "Y", "TRKY1", "", ""));
            rows.add(part("AXLE", "1", "AX1", "TRKX1", "X"));
            rows.add(part("AXLE", "2", "AX2", "TRKX1", "X"));
            rows.add(part("TRACTION MOTOR", "1", "TM1", "TRKX1", "X"));
            rows.add(part("BRAKE DISC", "1", "BD1", "TRKX1", "X"));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHCF01B_DATA", Map.of("dataRow", rows, "finalEmplNo", "12345", "finalShopLoc", "HY"));
        });
        for (String w : WRITERS) p.on(w, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VHCF01W", in -> {                                              // a typed truck serial: its stock (X: 17-35-88520, Y: 17-35-88515)
            Map<String, Object> crit = new HashMap<>(in.get("VHCF01W_CRIT"));
            if (String.valueOf(crit.get("cmpntSerialNo")).startsWith("UNK")) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            crit.put("truckStockNo", String.valueOf(crit.get("cmpntSerialNo")).startsWith("NEWX") ? "173588520" : "173588515");
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHCF01W_CRIT", crit);
        });
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrLocDesc", "SHOP PRINTER", "vtamName", "PRT01"), Map.of("prtrLocDesc", "YARD PRINTER", "vtamName", "PRT02")))));
        p.on("VHCF05A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
    }

    private static Map<String, Object> part(String type, String posn, String serial, String parent, String parentPosn) {
        Map<String, Object> m = new LinkedHashMap<>(Map.of("carNo", "1101", "stockNo", "17355" + posn + "001", "posnCd", posn, "cmpntSerialNo", serial,
                "cmpntEquipType", type, "serialStat", "CAR", "parentSerialNo", parent, "parentPosnCd", parentPosn, "dateConfigUpd", "2025-10-10",
                "timeConfigUpd", "10.11.12"));
        m.put("termConfigUpd", "OLDTERM");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    private static String id(ScreenSession s) {
        return (String) window(s).get("id");
    }

    /** the old boxes of the working record fields the tests type */
    static final Map<String, String> WIDGETS = Map.ofEntries(Map.entry("X-TRUCK-SERIAL", "Text211"), Map.entry("Y-TRUCK-SERIAL", "Text21"),
            Map.entry("AXLE-SERIAL-01", "Text321"), Map.entry("AXLE-SERIAL-02", "Text3111"), Map.entry("AXLE-SERIAL-03", "Text32"), Map.entry("AXLE-SERIAL-04", "Text311"),
            Map.entry("DISC-SERIAL-01", "Text2"), Map.entry("DISC-SERIAL-02", "Text33211"), Map.entry("DISC-SERIAL-03", "Text331"), Map.entry("DISC-SERIAL-04", "Text3321"),
            Map.entry("TM-SERIAL-01", "Text3332111"), Map.entry("TM-SERIAL-02", "Text33222111"), Map.entry("TM-SERIAL-03", "Text333211"),
            Map.entry("TM-SERIAL-04", "Text3322211"), Map.entry("FLANGE-1", "Text551"), Map.entry("DIAMETER-1", "Text5411"));

    private static final List<String> ACTIONS = List.of("NO ACTION SELECTED", "LIST DATA", "TRUCK,AXLE, MOTOR, DISC - SERIAL OFF", "TRUCK,AXLE, MOTOR, DISC - SERIAL ON",
            "GEOMETRY DATA");

    /** the old screen: opened with the terminal id, the action chosen, the car typed, List */
    private ScreenSession old(String car, String action) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF01I", p.user);
        RecordPart sign = s.window(id(s)).recordPart("VH01A01_DATA");
        if (sign != null) sign.set("TERM-ID data", TERMINAL);
        int idx = ACTIONS.indexOf(action);
        if (idx > 0) s.event(id(s), List.of(), "Drop-down_List1", "select", idx + 1);
        s.event(id(s), List.of(new ScreenSession.Change("Text1", "object", car)), "Push_Button1", "clicked", null);
        return s;
    }

    private ConfigTrackingCarService api() {
        return new ConfigTrackingCarService(new ConfigQueriesRuleScreens(Parity.bundle(), p.legacy(), p.messages(), p.clock, TERMINAL));
    }

    private Map<String, List<Map<String, Map<String, Object>>>> byProgram() {
        Map<String, List<Map<String, Map<String, Object>>>> m = new LinkedHashMap<>();
        for (var e : p.sent) {
            Map<String, Map<String, Object>> r = new HashMap<>(e.getValue());
            r.remove("XDCONTROL");                                            // the control block carries over between calls
            m.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(r);
        }
        return m;
    }

    private static String field(ScreenSession s, String record, String field) {
        Object v = s.window(id(s)).recordPart(record).get(field + " data");
        return v == null ? "" : v.toString().strip();
    }

    private void sameList(boolean valid, boolean trucks, String action) {
        backend(valid, trucks);
        ScreenSession s = old("1101", action);
        var oldSent = byProgram();
        p.clear();
        var c = api().list("BRIGHTCO", "1101", action, "X");
        assertThat(byProgram()).isEqualTo(oldSent);
        assertThat(c.message()).isEqualTo(field(s, "VHCF01IW-MSG", "FINAL-MESSAGE"));
        for (String f : List.of("X-TRUCK-SERIAL", "Y-TRUCK-SERIAL", "AXLE-SERIAL-01", "AXLE-SERIAL-02", "TM-SERIAL-01", "DISC-SERIAL-01", "EMPL-NOX", "LOCX")) {
            assertThat(c.values().get(f)).as(f).isEqualTo(field(s, "VHCF01IW", f));
        }
    }

    @Test
    void listingMatchesTheOldScreen() {
        sameList(true, true, "NO ACTION SELECTED");
        sameList(true, true, "LIST DATA");
        sameList(true, true, "GEOMETRY DATA");
        sameList(true, false, "NO ACTION SELECTED");                         // no trucks yet: enter them and Update
        sameList(false, false, "NO ACTION SELECTED");                        // not a car
    }

    @Test
    void aListedCarShowsItsTrucks() {
        backend(true, true);
        var c = api().list("BRIGHTCO", "1101", "LIST DATA", "X");
        assertThat(c.values()).containsEntry("X-TRUCK-SERIAL", "TRKX1").containsEntry("AXLE-SERIAL-01", "AX1");
        assertThat(c.homeYard()).isEqualTo("HY");
    }

    /** List, then values, highlights, employee and location, then Update, on both sides */
    private void sameUpdate(boolean trucks, String action, Map<String, String> values, List<String> highlight, String employee, String location, boolean writes) {
        backend(true, trucks);
        ScreenSession s = old("1101", action);
        int listCalls = p.sent.size();                                       // the API repeats the opening and List before its Update
        String w = id(s);
        for (var e : values.entrySet()) {
            String widget = WIDGETS.get(e.getKey());
            s.event(w, List.of(new ScreenSession.Change(widget, "object", e.getValue())), widget, "losingFocus", null);
        }
        if (employee != null) s.event(w, List.of(new ScreenSession.Change("Text34", "object", employee)), null, null, null);
        if (location != null) {                                              // the location list's selected item and index, as it writes them
            s.window(w).recordPart("VHCF01IW").set("LOC data", location);
            s.window(w).recordPart("VHCF01IW").set("LOC-IDX data", List.of("CY", "DY", "HY", "RY").indexOf(location) + 1);
        }
        Map<String, String> buttons = Map.of("TRUCK1", "TruckSerX", "AXLE1", "AxleSer1", "TM1", "TmSer1", "DISC1", "discSer31");
        for (String h : highlight) s.event(w, List.of(), buttons.get(h), "clicked", null);
        s.event(w, List.of(), "Push_Button321", "clicked", null);
        p.sent.subList(0, listCalls).clear();                                // the user's steps after List, Update included
        var oldSent = byProgram();
        if (writes) assertThat(oldSent.keySet()).as("the old Update reached a writer").containsAnyElementsOf(WRITERS);
        else assertThat(oldSent.keySet()).as("the old Update wrote nothing").doesNotContainAnyElementsOf(WRITERS);

        p.clear();
        var c = api().update("BRIGHTCO", new ConfigTrackingCarService.Change("1101", action, "X", values, highlight, employee, location));
        p.sent.subList(0, listCalls).clear();
        assertThat(byProgram()).isEqualTo(oldSent);
        assertThat(c.message()).isEqualTo(field(s, "VHCF01IW-MSG", "FINAL-MESSAGE"));
    }

    @Test
    void updateWithoutEmployeeOrLocationMatchesTheOldScreen() {
        sameUpdate(true, "GEOMETRY DATA", Map.of("FLANGE-1", "1.25"), List.of(), null, null, false);
    }

    @Test
    void updateWithNoActionOnACarWithTrucksMatchesTheOldScreen() {
        sameUpdate(true, "NO ACTION SELECTED", Map.of(), List.of(), "12345", "HY", false);
    }

    @Test
    void geometryDataMatchesTheOldScreen() {
        sameUpdate(true, "GEOMETRY DATA", Map.of("FLANGE-1", "1.25", "DIAMETER-1", "28.50"), List.of(), "12345", "HY", true);
    }

    @Test
    void serialOffMatchesTheOldScreen() {
        sameUpdate(true, "TRUCK,AXLE, MOTOR, DISC - SERIAL OFF", Map.of(), List.of("AXLE1"), "12345", "HY", true);
    }

    @Test
    void typingKnownTrucksOnACarWithoutTrucksMatchesTheOldScreen() {
        // old quirk: a truck serial VHCF01W knows sets TRUCK-IS-FOUND, so Update then says the car already has its components
        sameUpdate(false, "NO ACTION SELECTED", Map.of("X-TRUCK-SERIAL", "NEWX1", "Y-TRUCK-SERIAL", "NEWY1"), List.of(), "12345", "HY", false);
    }

    @Test
    void addingTrucksToACarMatchesTheOldScreen() {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("X-TRUCK-SERIAL", "UNKX1");
        parts.put("Y-TRUCK-SERIAL", "UNKY1");
        for (String k : List.of("AXLE", "DISC", "TM")) for (int i = 1; i <= 4; i++) parts.put(k + "-SERIAL-0" + i, k + "N" + i);
        sameUpdate(false, "NO ACTION SELECTED", parts, List.of(), "12345", "HY", true);
    }

    @Test
    void printMatchesTheOldScreen() {
        backend(true, true);
        ScreenSession s = p.oldScreens();
        s.openRoot("VFCF05I", p.user);
        s.window(id(s)).recordPart("VHCF05IW-PASSVALUE").set("CAR-NO data", "1101");
        s.event(id(s), List.of(), "cntPrinterdet", "select", 2);
        p.clear();
        s.event(id(s), List.of(), "Push_Button2", "clicked", null);
        var oldSent = byProgram();
        assertThat(oldSent).containsKey("VHCF05A");

        p.clear();
        var r = new ConfigTrackingReportService(p.legacy(), p.messages());
        assertThat(r.printers().printers()).extracting(ConfigTrackingReportService.Printer::vtamName).containsExactly("PRT01", "PRT02");
        p.clear();
        r.print("1101", "PRT02");
        assertThat(byProgram()).isEqualTo(oldSent);
    }
}
