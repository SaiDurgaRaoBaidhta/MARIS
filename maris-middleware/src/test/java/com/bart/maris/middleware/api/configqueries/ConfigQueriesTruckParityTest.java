package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesTruckService;
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
 * Truck inquiry (VHCF37I) and truck build (VHCF35I): the clean API against the old screens run by the engine, same
 * scripted backend. Compares every record sent (VHCF35C, VHCF35D, VHCF35B, VHCF37F, and the writers VHCF35G / VHCF35H),
 * the message and the truck the screen holds.
 */
class ConfigQueriesTruckParityTest {
    static final String TERMINAL = "TERM01", STOCK = "17-35-88520", DISPLAY = "17-35-88520 TRUCK ASSY X";
    private Parity p;
    /** the car the truck is on: the build screen refuses a truck that is on a car */
    private String car = "1101";

    /** @param components the truck's parts (type, position, serial); @param validNew a new part's serial check finds it */
    private void backend(boolean truckFound, List<String[]> components, boolean validNew, int addUrc) {
        p = new Parity();
        p.on("VHCF35C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF35C_DATA", Map.of("dataRow", List.of(
                Map.of("stockNo", "173588520", "stockDisplay", Map.of("stockNoL", STOCK, "vagenSpace", " ", "shortStockDesc", "TRUCK ASSY X")),
                Map.of("stockNo", "173588515", "stockDisplay", Map.of("stockNoL", "17-35-88515", "vagenSpace", " ", "shortStockDesc", "TRUCK ASSY Y"))))));
        p.on("VHCF35D", in -> {
            Map<String, Object> crit = in.get("VHCF35D_CRIT");
            boolean part = "Y".equals(crit.get("axle"));
            if (part) return validNew ? Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCF35D_DATA", Map.of("stockNo", "999999999", "cmpntSerialNo", crit.get("cmpntSerialNo")))
                    : Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            if (!truckFound) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCF35D_DATA", Map.of("carNo", car, "dateBuyRebuilt", "2020-05-06", "cmpntSerialNo", "TRK1",
                    "comments", "OLD NOTE", "truckStat", "REBUILT", "shopLoc", "HY", "stockNo", "173588520"));
        });
        p.on("VHCF35B", in -> {
            if (components.isEmpty()) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String[] c : components) rows.add(Map.of("cmpntEquipType", c[0], "posnCd", c[1], "cmpntSerialNo", c[2], "stockNo", "1111111" + c[1] + "1"));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHCF35B_DATA", Map.of("dataRow", rows));
        });
        p.on("VHCF37F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHCF35F_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntEquipType", "WHEEL", "posnCd", "1", "cmpntSerialNo", "W1"), Map.of("cmpntEquipType", "WHEEL", "posnCd", "2", "cmpntSerialNo", "W2"),
                Map.of("cmpntEquipType", "GEAR BOX", "posnCd", "1", "cmpntSerialNo", "G1")))));
        p.on("VHCF35G", in -> Map.of("XDCONTROL", Map.of("urc", addUrc, "urowcnt", 1)));
        p.on("VHCF35H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
    }

    private static final List<String[]> FULL = List.of(new String[]{"AXLE", "1", "AX1"}, new String[]{"AXLE", "2", "AX2"},
            new String[]{"TRACTION MOTOR", "1", "TM1"}, new String[]{"TRACTION MOTOR", "2", "TM2"}, new String[]{"BRAKE DISC", "1", "BD1"},
            new String[]{"BRAKE DISC", "2", "BD2"});

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    /** the old screen: opened by its menu with the terminal id, the stock chosen, the serial typed, Retrieve */
    private ScreenSession old(String screen, String serial) {
        ScreenSession s = p.oldScreens();
        s.openRoot(screen, p.user);
        String id = (String) window(s).get("id");
        RecordPart sign = s.window(id).recordPart("VH01A01_DATA");
        if (sign != null) sign.set("TERM-ID data", TERMINAL);
        // the engine leaves the list fed from DATA_ROW_of_VHCF35C_DATA empty: the item it would show is put in and selected
        s.window(id).widget("Drop-down_List1").set("items", List.of(DISPLAY));
        s.event(id, List.of(), "Drop-down_List1", "select", 1);
        s.event(id, List.of(new ScreenSession.Change("Text1", "object", serial)), "Push_Button4", "clicked", null);
        return s;
    }

    private static String id(ScreenSession s) {
        return (String) window(s).get("id");
    }

    private ConfigQueriesTruckService api() {
        return new ConfigQueriesTruckService(new ConfigQueriesRuleScreens(Parity.bundle(), p.legacy(), p.messages(), p.clock, TERMINAL));
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

    private static void sameTruck(ScreenSession s, ConfigQueriesTruckService.Truck t) {
        assertThat(t.message()).isEqualTo(field(s, "VHCF35IW-MSG", "MSG"));
        assertThat(t.car()).isEqualTo(field(s, "VHCF35D_DATA", "CAR-NO"));
        assertThat(t.status()).isEqualTo(field(s, "VHCF35IW-HOLD-TRCK", "TRUCK-STAT"));
        assertThat(t.location()).isEqualTo(field(s, "VHCF35IW-HOLD-TRCK", "LOC"));
        assertThat(t.comments()).isEqualTo(field(s, "VHCF35IW-HOLD-TRCK", "COMMENTS"));
        Map<String, String> serialField = Map.of("axle1", "AXLE1", "axle2", "AXLE2", "tractionMotor1", "TM-1", "tractionMotor2", "TM-2", "brakeDisc1", "BRAKE-DISC-1",
                "brakeDisc2", "BRAKE-DISC-2");
        for (var c : t.components()) assertThat(c.serial()).as(c.key()).isEqualTo(field(s, "VHCF35IW-HOLD-TRCK", serialField.get(c.key())));
        assertThat(t.components().get(0).name()).isEqualTo(field(s, "VHCF35IW-PASSVALUE", "AXLE-1-NAME"));
        assertThat(t.axleParts().get(0).serial()).isEqualTo(field(s, "VHCF35IW-HOLD-AXLE", "WHEEL1"));
    }

    @Test
    void inquiryMatchesTheOldScreen() {
        for (List<String[]> comps : List.of(FULL, List.<String[]>of())) {
            backend(true, comps, true, 0);
            ScreenSession s = old(ConfigQueriesTruckService.INQUIRY, "TRK1");
            var oldSent = byProgram();
            p.clear();
            var api = api();
            api.options("BRIGHTCO");
            p.clear();
            var t = api.retrieve("BRIGHTCO", ConfigQueriesTruckService.INQUIRY, STOCK, "TRK1");
            var apiSent = byProgram();
            oldSent.remove("VHCF35C");
            apiSent.remove("VHCF35C");
            assertThat(apiSent).isEqualTo(oldSent);
            sameTruck(s, t);
            if (!comps.isEmpty()) assertThat(t.components().get(0).serial()).isEqualTo("AX1");
        }
    }

    @Test
    void anUnknownTruckMatchesTheOldScreen() {
        backend(false, List.of(), true, 0);
        ScreenSession s = old(ConfigQueriesTruckService.INQUIRY, "NOPE");
        String oldMsg = field(s, "VHCF35IW-MSG", "MSG");
        p.clear();
        var t = api().retrieve("BRIGHTCO", ConfigQueriesTruckService.INQUIRY, STOCK, "NOPE");
        assertThat(t.message()).isEqualTo(oldMsg).isNotBlank();
    }

    @Test
    void optionsAreTheOldLists() {
        backend(true, FULL, true, 0);
        var o = api().options("BRIGHTCO");
        assertThat(o.stocks()).extracting(ConfigQueriesTruckService.Stock::stock).containsExactly(STOCK, "17-35-88515");
        assertThat(o.statuses()).containsExactly("REPAIR", "REBUILT", "WHEELSCUT");
        assertThat(o.locations()).containsExactly("CY", "DY", "HY", "RY");
    }

    /** a build: typed new serials, status, location, comments, then Update */
    private void build(List<String[]> comps, boolean validNew, int addUrc, Map<String, String> typed, List<String> takeOff, String writer) {
        build("", comps, validNew, addUrc, typed, takeOff, writer);
    }

    private void build(String onCar, List<String[]> comps, boolean validNew, int addUrc, Map<String, String> typed, List<String> takeOff, String writer) {
        car = onCar;
        backend(true, comps, validNew, addUrc);
        ScreenSession s = old(ConfigQueriesTruckService.BUILD, "TRK1");
        Map<String, String> box = Map.of("axle1", "Text2", "axle2", "Text3", "tractionMotor1", "Text4", "tractionMotor2", "Text5", "brakeDisc1", "Text6", "brakeDisc2", "Text7");
        Map<String, String> toggle = Map.of("axle1", "Push_Button6", "axle2", "Push_Button61", "tractionMotor1", "Push_Button611", "tractionMotor2", "Push_Button6111",
                "brakeDisc1", "Push_Button61111", "brakeDisc2", "Push_Button611111");
        List<ScreenSession.Change> changes = new ArrayList<>();
        typed.forEach((k, v) -> changes.add(new ScreenSession.Change(box.get(k), "object", v)));
        changes.add(new ScreenSession.Change("mleNarrative", "object", "NEW NOTE"));
        s.event(id(s), changes, null, null, null);
        for (String k : takeOff) s.event(id(s), List.of(), toggle.get(k), "clicked", null);
        p.clear();
        s.event(id(s), List.of(), "Push_Button1", "clicked", null);
        var oldSent = byProgram();
        if (writer != null) assertThat(oldSent).as("the old Update reached " + writer).containsKey(writer);
        else assertThat(oldSent).doesNotContainKeys("VHCF35G", "VHCF35H");

        p.clear();
        var t = api().build("BRIGHTCO", new ConfigQueriesTruckService.Build(STOCK, "TRK1", typed, takeOff, Map.of(), null, null, "NEW NOTE"));
        var apiSent = byProgram();
        // only the Update's calls: the API repeats the retrieve, which the old screen had already done
        for (String prog : List.of("VHCF35C", "VHCF35B", "VHCF37F")) { oldSent.remove(prog); apiSent.remove(prog); }
        List<Map<String, Map<String, Object>>> apiD = apiSent.getOrDefault("VHCF35D", List.of());
        if (!apiD.isEmpty()) apiSent.put("VHCF35D", apiD.subList(1, apiD.size()));       // the retrieve's truck read
        if (apiSent.get("VHCF35D") != null && apiSent.get("VHCF35D").isEmpty()) apiSent.remove("VHCF35D");
        assertThat(apiSent).isEqualTo(oldSent);
        assertThat(t.message()).isEqualTo(field(s, "VHCF35IW-MSG", "MSG"));
    }

    @Test
    void buildingANewTruckMatchesTheOldScreen() {
        build(List.of(), true, 0, Map.of("axle1", "NEWAX1", "axle2", "NEWAX2", "tractionMotor1", "NEWTM1", "brakeDisc1", "NEWBD1"), List.of(), "VHCF35G");
    }

    @Test
    void anInvalidNewSerialMatchesTheOldScreen() {
        build(List.of(), false, 0, Map.of("axle1", "BADAX1"), List.of(), null);
    }

    @Test
    void aFailedAddMatchesTheOldScreen() {
        build(List.of(), true, 8, Map.of("tractionMotor2", "NEWTM2"), List.of(), "VHCF35G");
    }

    @Test
    void noSerialsMatchesTheOldScreen() {
        build(List.of(), true, 0, Map.of(), List.of(), null);
    }

    @Test
    void aTruckOnACarIsNotBuiltAsOnTheOldScreen() {
        build("1101", List.of(), true, 0, Map.of("axle1", "NEWAX1"), List.of(), null);
    }

    @Test
    void takingPartsOffMatchesTheOldScreen() {
        build(FULL, true, 0, Map.of(), List.of("axle1", "brakeDisc2"), "VHCF35H");
    }
}
