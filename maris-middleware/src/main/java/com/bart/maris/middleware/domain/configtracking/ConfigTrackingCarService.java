package com.bart.maris.middleware.domain.configtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesEgl;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleRunner;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens.View;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A car's configuration (old screen VHCF01I): its trucks and their axles, traction motors, brake discs, bearings and
 * gear boxes with the wheel and brake-disc measurements, run with the screen's own rules by the configuration modules'
 * {@link ConfigQueriesRuleRunner}:
 * <ul>
 * <li>List / Refresh (VHCF01IS-LIST: the car checked, VHCF01A; its trucks and parts, VHCF01B..);</li>
 * <li>an action chosen (VHCF01IS-INIT3: what may be edited);</li>
 * <li>Update (VHCF01IS-UPDATE): a car with no trucks gets the typed ones (VHCF01IS-ADD); otherwise the action decides:
 * the highlighted serials come off (SERIAL OFF), the typed serials go on (SERIAL ON), or the geometry readings are
 * written (GEOMETRY DATA).</li>
 * </ul>
 * Values are given and returned by the screen's working record fields (VHCF01IW, e.g. AXLE-SERIAL-01, FLANGE-1,
 * DC-READING-TIR-1A, EMPL-NO, LOC); a part is highlighted by its old serial button (TRUCK1, AXLE1, DISC1, TM1 ...).
 * Print opens the car configuration report's printer window (VFCF05I, {@link ConfigTrackingReportService}).
 */
public final class ConfigTrackingCarService {
    static final String SCREEN = "VHCF01I", ACTION = "Drop-down_List1", LOCATION = "Drop-down_List3";

    /** the serial buttons: the part each highlights -> the old button */
    static final Map<String, String> SERIAL_BUTTONS = ordered("TRUCK1", "TruckSerX", "TRUCK2", "TruckSerY", "AXLE1", "AxleSer1", "AXLE2", "AxleSer2",
            "AXLE3", "AxleSer3", "AXLE4", "AxleSer4", "DISC1", "discSer31", "DISC2", "discSer2", "DISC3", "discSer3", "DISC4", "discSer4",
            "TM1", "TmSer1", "TM2", "TmSer2", "TM3", "TmSer3", "TM4", "TmSer4");

    public record Options(List<String> actions, List<String> trucks, List<String> locations) {}

    /**
     * @param values the working record (VHCF01IW) by old field name; @param highlighted the parts highlighted (VHCF01IW-COLOR)
     * @param updatable the Update button is enabled; @param homeYard the car's home yard (VHCF01A)
     */
    public record Car(String message, String homeYard, Map<String, String> values, Map<String, String> highlighted, boolean updatable, List<String> notices) {}

    /** an Update: the action, the truck (X or Y), the values by VHCF01IW field, the parts highlighted, employee and location */
    public record Change(String car, String action, String truck, Map<String, String> values, List<String> highlight, String employee, String location) {}

    private final ConfigQueriesRuleScreens screens;
    private final List<String> actions, trucks, locations;

    public ConfigTrackingCarService(ConfigQueriesRuleScreens screens) {
        this.screens = screens;
        this.actions = ConfigQueriesEgl.listItems(screens.bundle(), SCREEN, ACTION);
        this.trucks = ConfigQueriesEgl.listItems(screens.bundle(), SCREEN, "Radio_Button_Set1");
        this.locations = ConfigQueriesEgl.listItems(screens.bundle(), SCREEN, LOCATION);
    }

    public Options options() {
        return new Options(actions, trucks, locations);
    }

    /** List / Refresh with an action chosen first (as the user picks it before listing) */
    public Car list(String group, String car, String action, String truck) {
        return view(listed(group, car, action, truck));
    }

    /** List, the user's changes, then Update */
    public Car update(String group, Change c) {
        ConfigQueriesRuleRunner logic = listed(group, c.car(), c.action(), c.truck());
        Map<String, String> values = c.values() == null ? Map.of() : c.values();
        for (Map.Entry<String, String> e : values.entrySet()) {
            String f = e.getKey().toUpperCase(Locale.ROOT);
            if (f.startsWith("LOC") || f.equals("EMPL-NO")) continue;          // given by employee / location
            logic.typeField("VHCF01IW", f, e.getValue() == null ? "" : e.getValue());
            if (f.equals("X-TRUCK-SERIAL")) logic.fire("Text211", "losingFocus");   // VHCF01IS-TRUCK for truck X
            if (f.equals("Y-TRUCK-SERIAL")) logic.fire("Text21", "losingFocus");
        }
        if (c.employee() != null) logic.type("Text34", c.employee().strip());
        if (c.location() != null) {
            String loc = c.location().strip().toUpperCase(Locale.ROOT);
            logic.store("VHCF01IW", "LOC", loc);
            logic.store("VHCF01IW", "LOC-IDX", locations.indexOf(loc) + 1);
        }
        for (String part : c.highlight() == null ? List.<String>of() : c.highlight()) {
            String b = SERIAL_BUTTONS.get(part.toUpperCase(Locale.ROOT));
            if (b == null) throw new InvalidRequestException("highlight: one of " + SERIAL_BUTTONS.keySet());
            logic.fire(b, "clicked");
        }
        logic.fire("Push_Button321", "clicked");
        return view(logic);
    }

    private ConfigQueriesRuleRunner listed(String group, String car, String action, String truck) {
        ConfigQueriesRuleRunner logic = screens.open(SCREEN, group);
        // VHCF01IS-INIT selects item 1 of the action list (ACTION-IDX = 1); the list then shows that item, and its
        // selectedItem binding writes it into ACTION-DATA, as the old runtime's list did
        long shownIdx = logic.number("VHCF01IW-01", "ACTION-IDX");
        if (shownIdx > 0 && shownIdx <= actions.size()) logic.store("VHCF01IW-01", "ACTION-DATA", actions.get((int) shownIdx - 1));
        logic.type("Text1", car == null ? "" : car.strip());
        String a = action == null || action.isBlank() ? actions.get(0) : action.strip();
        int idx = actions.indexOf(a);
        if (idx < 0) throw new InvalidRequestException("action: one of " + actions);
        if (idx > 0) {                                                       // the list opens on NO ACTION SELECTED
            logic.store("VHCF01IW-01", "ACTION-DATA", a);
            logic.store("VHCF01IW-01", "ACTION-IDX", idx + 1);
            logic.fire(ACTION, "selectedItem");                               // -> VHCF01IS-INIT3
        }
        if ("Y".equalsIgnoreCase(truck == null ? "" : truck.strip()) || (truck != null && trucks.indexOf(truck.strip()) == 1)) {
            logic.type("Radio_Button_Set1", 2);
        }
        logic.fire("Push_Button1", "clicked");
        return logic;
    }

    private Car view(ConfigQueriesRuleRunner logic) {
        View v = screens.view(logic, List.of());
        Map<String, String> values = new LinkedHashMap<>(), colors = new LinkedHashMap<>();
        logic.get("VHCF01IW");                                                // fails fast if the record is missing
        for (String f : logic.fieldNames("VHCF01IW")) values.put(f, logic.text("VHCF01IW", f).strip());
        for (String f : logic.fieldNames("VHCF01IW-COLOR")) {
            String c = logic.text("VHCF01IW-COLOR", f).strip();
            if (!c.isEmpty()) colors.put(f, c);
        }
        return new Car(logic.text("VHCF01IW-MSG", "FINAL-MESSAGE").strip(), logic.text("VHCF01A_DATA", "HOME-YRD").strip(), values, colors,
                logic.enabled("Push_Button321"), v.notices());
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }
}
