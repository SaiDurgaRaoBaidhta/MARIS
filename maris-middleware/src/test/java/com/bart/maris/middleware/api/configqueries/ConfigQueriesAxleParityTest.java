package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesAxleService;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Axle / gear box maintenance (VHCF06I): the clean API against the old screen run by the engine, same scripted backend.
 * Compares every record sent (VHCF06A and the update programs), the message, and the values the screen holds.
 */
class ConfigQueriesAxleParityTest {
    static final String TERMINAL = "TERM01";
    static final List<String> WRITERS = List.of("VHCF06C", "VHCF06D", "VHCF06F", "VHCF06G", "VHCF06H", "VHCF06I", "VHCF06J", "VHCF06K", "VHCF06L", "VHCF06M",
            "VHCF01J", "VHCF01P");
    private Parity p;

    /** @param stat the axle's serial status (STO, CAR, SRP); @param notFound the program says the axle does not exist */
    private void backend(String stat, boolean notFound) {
        p = new Parity();
        p.on("VHCF06A", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHCF06A_CRIT"));
            crit.put("axleNotFound", notFound ? "Y" : "N");
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row("AXLE", "AX1", " ", stat, "173155001"));
            rows.add(row("GEAR BOX", "GB1", "1", "STO", "173155002"));
            rows.add(row("B BEARING", "BB1", "B", "STO", "173155003"));
            rows.add(row("N BEARING", "NB1", "N", "STO", "173155004"));
            rows.add(row("WHEEL", "WH1", "1", "STO", "173155005"));
            rows.add(row("WHEEL", "WH2", "2", "STO", "173155005"));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHCF06A_CRIT", crit, "VHCF06A_DATA", Map.of("stockNo", "173155001", "dataRow", rows));
        });
        for (String w : WRITERS) p.on(w, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
    }

    private static Map<String, Object> row(String type, String serial, String posn, String stat, String stock) {
        Map<String, Object> m = new LinkedHashMap<>(Map.of("cmpntEquipType", type, "cmpntSerialNo", serial, "posnCd", posn, "serialStat", stat, "stockNox", stock,
                "carNo", stat.equals("CAR") ? "1101" : "", "dateBuyRebuilt", "2019-03-04", "dateHubCoupRepl", "2018-01-02", "dateLastUpd", "2025-10-10",
                "homeYrd", "HY"));
        m.put("termLastUpd", "OLDTERM");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    private static String id(ScreenSession s) {
        return (String) window(s).get("id");
    }

    /** the old screen: opened by its menu with the terminal id, the kind chosen, the serial typed, List */
    private ScreenSession old(boolean gear, String serial) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF06I", p.user);
        RecordPart sign = s.window(id(s)).recordPart("VH01A01_DATA");
        if (sign != null) sign.set("TERM-ID data", TERMINAL);
        if (gear) s.event(id(s), List.of(), "Radio_Button_Set2", "select", 2);   // "Axle Serial No:" is already selected
        s.event(id(s), List.of(new ScreenSession.Change(gear ? "Text11" : "Text1", "object", serial)), "Push_Button1", "clicked", null);
        return s;
    }

    private ConfigQueriesAxleService api() {
        return new ConfigQueriesAxleService(new ConfigQueriesRuleScreens(Parity.bundle(), p.legacy(), p.messages(), p.clock, TERMINAL));
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

    private static void sameView(ScreenSession s, ConfigQueriesAxleService.Axle a) {
        assertThat(a.message()).isEqualTo(field(s, "VHCF06IW-MESSAGE", "FINAL-MESSAGE"));
        for (String k : List.of("axleSerial", "gearBoxSerial", "bBearingSerial", "nBearingSerial", "wheel1Serial", "wheel2Serial", "axleRebuiltDate", "homeYard")) {
            assertThat(a.values().get(k)).as(k).isEqualTo(field(s, "VHCF06IW", Map.of("axleSerial", "AXLE-SERIAL-NO", "gearBoxSerial", "GEAR-BOX-SERIAL-NO",
                    "bBearingSerial", "B-SERIAL-NO", "nBearingSerial", "N-SERIAL-NO", "wheel1Serial", "WHEEL-01-SERIAL-NO", "wheel2Serial", "WHEEL-02-SERIAL-NO",
                    "axleRebuiltDate", "AXLE-REBUILT-DATE", "homeYard", "HOME-YRD").get(k)));
        }
    }

    private void sameList(boolean gear, String serial, String stat, boolean notFound) {
        backend(stat, notFound);
        ScreenSession s = old(gear, serial);
        var oldSent = byProgram();
        p.clear();
        var a = api().list("BRIGHTCO", gear ? "gearbox" : "axle", serial);
        assertThat(byProgram()).isEqualTo(oldSent);
        sameView(s, a);
    }

    @Test
    void listingMatchesTheOldScreen() {
        sameList(false, "AX1", "STO", false);
        sameList(true, "GB1", "STO", false);
        sameList(false, "AX1", "CAR", false);                                // still on a car: no update
        sameList(false, "AX1", "SRP", false);                                // scrapped
        sameList(false, "NOPE", "STO", true);                                // not found
        sameList(false, "", "STO", false);                                   // no serial: the screen's own message
    }

    @Test
    void aListedAxleShowsItsParts() {
        backend("STO", false);
        var a = api().list("BRIGHTCO", "axle", "AX1");
        assertThat(a.values().get("axleSerial")).isEqualTo("AX1");
        assertThat(a.updatable()).isTrue();
    }

    /** List, then values and boxes, then Update, on both sides */
    private void sameUpdate(Map<String, String> values, Set<String> checks, String expectWriter) {
        backend("STO", false);
        ScreenSession s = old(false, "AX1");
        List<ScreenSession.Change> changes = new ArrayList<>();
        Map<String, String> inputs = Map.ofEntries(Map.entry("axleRebuiltDate", "Text22"), Map.entry("hubCouplingReplacedDate", "Text3"),
                Map.entry("gearBoxSerial", "Text21"), Map.entry("gearBoxRebuiltDate", "Text31"), Map.entry("bBearingSerial", "Text211"),
                Map.entry("nBearingRebuiltDate", "Text21111"), Map.entry("wheel1Serial", "Text211111"), Map.entry("flange1", "Text2"), Map.entry("diameter1", "Text24"));
        values.forEach((k, v) -> changes.add(new ScreenSession.Change(inputs.get(k), "object", v)));
        s.event(id(s), changes, null, null, null);
        // a checked toggle button writes "1" into its field (what the screen tests); the engine's toggle buttons do not
        Map<String, String> checkField = Map.of("gearBoxOff", "GEAR-CHECK-OFF", "wheel1Off", "WHEEL-01-CHECK-OFF", "wheel1Update", "WHEEL-01-CHECK-UPDATE",
                "axleDateUpdate", "AXLE-DATE-CHECK-UPDATE", "hubDateUpdate", "HUB-DATE-CHECK-UPDATE", "bBearingOff", "B-CHECK-OFF");
        for (String c : checks) s.window(id(s)).recordPart("VHCF06IW").set(checkField.get(c) + " data", "1");
        p.clear();
        s.event(id(s), List.of(), "Push_Button2", "clicked", null);
        var oldSent = byProgram();
        if (expectWriter != null) assertThat(oldSent.keySet()).as("the old Update reached a writer").containsAnyElementsOf(WRITERS);
        else assertThat(oldSent.keySet()).as("the old Update wrote nothing").doesNotContainAnyElementsOf(WRITERS);

        p.clear();
        var a = api().update("BRIGHTCO", "axle", "AX1", values, checks);
        var apiSent = byProgram();
        apiSent.remove("VHCF06A");                                           // the API lists first; the old screen had listed already
        oldSent.remove("VHCF06A");
        assertThat(apiSent).isEqualTo(oldSent);
        assertThat(a.message()).isEqualTo(field(s, "VHCF06IW-MESSAGE", "FINAL-MESSAGE"));
    }

    @Test
    void updateWithNoBoxMatchesTheOldScreen() {
        sameUpdate(Map.of(), Set.of(), null);
    }

    @Test
    void updateWithWheelOffAndUpdateMatchesTheOldScreen() {
        sameUpdate(Map.of(), Set.of("wheel1Off", "wheel1Update"), null);
    }

    @Test
    void axleDateUpdateMatchesTheOldScreen() {
        sameUpdate(Map.of("axleRebuiltDate", "11-01-2025"), Set.of("axleDateUpdate"), firstWriter());
    }

    @Test
    void gearBoxOffMatchesTheOldScreen() {
        sameUpdate(Map.of(), Set.of("gearBoxOff"), null);                     // refused by the screen's own checks, nothing written
    }

    @Test
    void wheelMeasurementsMatchTheOldScreen() {
        sameUpdate(Map.of("flange1", "1.25", "diameter1", "28.5"), Set.of("wheel1Update"), firstWriter());
    }

    /** any writer: which program the old screen picks is what the comparison checks */
    private static String firstWriter() {
        return "";
    }
}
