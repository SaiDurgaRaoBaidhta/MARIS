package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens.View;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Axle / gear box maintenance (old screen VHCF06I, opened by the truck build's "Axle Gui"), run with the screen's own
 * rules by {@link ConfigQueriesRuleRunner}: List (VHCF06IS-LIST: the axle or gear box, VHCF06A, and its parts) and
 * Update (VHCF06IS-UPDATE: the checked boxes decide what is written: parts taken off or put on, dates and wheel
 * measurements updated; VHCF06C..VHCF06M, VHCF01J, VHCF01P). The Axle / Gear Box / Bearing buttons only fill a pass
 * record for a screen that is not part of the old web UI, so they are not offered.
 */
public final class ConfigQueriesAxleService {
    static final String SCREEN = "VHCF06I", MESSAGE_RECORD = "VHCF06IW-MESSAGE";

    /** the values a user types, by API name -> old widget */
    static final Map<String, String> INPUTS = ordered(
            "axleRebuiltDate", "Text22", "hubCouplingReplacedDate", "Text3", "gearBoxSerial", "Text21", "gearBoxRebuiltDate", "Text31",
            "bBearingSerial", "Text211", "bBearingRebuiltDate", "Text2111", "nBearingSerial", "Text2112", "nBearingRebuiltDate", "Text21111",
            "wheel1Serial", "Text211111", "wheel2Serial", "Text211112", "flange1", "Text2", "flange2", "Text23", "diameter1", "Text24", "diameter2", "Text25");

    /** the boxes a user checks, by API name -> old toggle button */
    static final Map<String, String> CHECKS = ordered(
            "gearBoxOff", "Toggle_Button1", "gearBoxOn", "Toggle_Button11", "bBearingOff", "Toggle_Button12", "bBearingOn", "Toggle_Button111",
            "nBearingOff", "Toggle_Button13", "nBearingOn", "Toggle_Button112", "wheel1Off", "Toggle_Button14", "wheel1On", "Toggle_Button113",
            "wheel2Off", "Toggle_Button15", "wheel2On", "Toggle_Button114", "wheel1Update", "Toggle_Button2", "wheel2Update", "Toggle_Button4",
            "gearBoxRebuiltDateUpdate", "Toggle_Button3", "hubDateUpdate", "Toggle_Button5", "axleDateUpdate", "Toggle_Button6");

    /** what the screen shows, by API name -> VHCF06IW field */
    static final Map<String, String> SHOWN = ordered(
            "axleSerial", "AXLE-SERIAL-NO", "axleStatus", "AXLE-SERIAL-STAT", "axleStock", "AXLE-STOCK-NO", "axleRebuiltDate", "AXLE-REBUILT-DATE",
            "hubCouplingReplacedDate", "AXLE-HUB-COUP-REPL-DATE", "gearBoxSerial", "GEAR-BOX-SERIAL-NO", "gearBoxStock", "GEAR-BOX-STOCK-NO",
            "gearBoxRebuiltDate", "GEAR-DATE-REBUILT", "bBearingSerial", "B-SERIAL-NO", "bBearingRebuiltDate", "BEAR-B-DATE-REBUILT",
            "nBearingSerial", "N-SERIAL-NO", "nBearingRebuiltDate", "BEAR-N-DATE-REBUILT", "wheel1Serial", "WHEEL-01-SERIAL-NO",
            "wheel2Serial", "WHEEL-02-SERIAL-NO", "flange1", "FLANGE-01-MEASUREMENT", "flange2", "FLANGE-02-MEASUREMENT",
            "diameter1", "DIAMETER-01-MEASUREMENT", "diameter2", "DIAMETER-02-MEASUREMENT", "car", "CAR-NO", "homeYard", "HOME-YRD",
            "dateRemoved", "DATE-REMOVED",
            "axleLastUpdated", "AXLE-DATE-LAST-UPD", "axleLastUpdatedTime", "AXLE-TIME-LAST-UPD", "axleLastUpdatedBy", "AXLE-TERM-LAST-UPD",
            "gearBoxLastUpdated", "GEAR-DATE-LAST-UPD", "gearBoxLastUpdatedTime", "GEAR-TIME-LAST-UPD", "gearBoxLastUpdatedBy", "GEAR-TERM-LAST-UPD",
            "bBearingLastUpdated", "BEAR-B-DATE-LAST-UPD", "bBearingLastUpdatedTime", "BEAR-B-TIME-LAST-UPD", "bBearingLastUpdatedBy", "BEAR-B-TERM-LAST-UPD",
            "nBearingLastUpdated", "BEAR-N-DATE-LAST-UPD", "nBearingLastUpdatedTime", "BEAR-N-TIME-LAST-UPD", "nBearingLastUpdatedBy", "BEAR-N-TERM-LAST-UPD",
            "wheel1LastUpdated", "WHEEL-01-DATE-LAST-UPD", "wheel1LastUpdatedTime", "WHEEL-01-TIME-LAST-UPD", "wheel1LastUpdatedBy", "WHEEL-01-TERM-LAST-UPD",
            "wheel2LastUpdated", "WHEEL-02-DATE-LAST-UPD", "wheel2LastUpdatedTime", "WHEEL-02-TIME-LAST-UPD", "wheel2LastUpdatedBy", "WHEEL-02-TERM-LAST-UPD");

    /** @param checkable the boxes the screen leaves enabled after the step; @param updatable Update is enabled */
    public record Axle(String message, Map<String, String> values, List<String> checkable, boolean updatable, List<String> notices) {}

    private final ConfigQueriesRuleScreens screens;

    public ConfigQueriesAxleService(ConfigQueriesRuleScreens screens) {
        this.screens = screens;
    }

    /** List: kind "axle" (the default) or "gearbox" */
    public Axle list(String group, String kind, String serial) {
        return view(listed(group, kind, serial));
    }

    /** List, the values typed and boxes checked, then Update */
    public Axle update(String group, String kind, String serial, Map<String, String> values, Set<String> checks) {
        ConfigQueriesRuleRunner logic = listed(group, kind, serial);
        Map<String, String> v = values == null ? Map.of() : values;
        for (String k : v.keySet()) {
            if (!INPUTS.containsKey(k)) throw new InvalidRequestException("values: one of " + INPUTS.keySet());
            logic.type(INPUTS.get(k), v.get(k) == null ? "" : v.get(k));
        }
        for (String c : checks == null ? Set.<String>of() : checks) {
            if (!CHECKS.containsKey(c)) throw new InvalidRequestException("checks: one of " + CHECKS.keySet());
            logic.type(CHECKS.get(c), "1");                                  // a checked toggle button writes "1" into its field
        }
        logic.fire("Push_Button2", "clicked");
        return view(logic);
    }

    private ConfigQueriesRuleRunner listed(String group, String kind, String serial) {
        boolean gear = "gearbox".equalsIgnoreCase(kind == null ? "" : kind.strip());
        ConfigQueriesRuleRunner logic = screens.open(SCREEN, group);
        // the screen opens on "Axle Serial No:" but its Clear empties AXLE-SELECTION after the radio set it, and choosing
        // the item already selected fires nothing: an axle is listed with AXLE-SELECTION 0 (so a blank axle serial is
        // listed rather than refused, as on the old screen); "Gearbox Serial No:" is a real change (2)
        if (gear) {
            logic.type("Radio_Button_Set2", 2);
            logic.fire("Radio_Button_Set2", "selectionIndex");
        }
        logic.type(gear ? "Text11" : "Text1", serial == null ? "" : serial.strip());
        logic.fire("Push_Button1", "clicked");
        return logic;
    }

    private Axle view(ConfigQueriesRuleRunner logic) {
        View v = screens.view(logic, List.of());
        Map<String, String> values = new LinkedHashMap<>();
        SHOWN.forEach((k, f) -> values.put(k, logic.text("VHCF06IW", f).strip()));
        List<String> checkable = CHECKS.entrySet().stream().filter(e -> logic.enabled(e.getValue())).map(Map.Entry::getKey).toList();
        return new Axle(logic.text(MESSAGE_RECORD, "FINAL-MESSAGE").strip(), values, checkable, logic.enabled("Push_Button2"), v.notices());
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }
}
