package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Truck build (old VHCF35I) and truck inquiry (VHCF37I), both run with the screens' own rules by
 * {@link ConfigQueriesRuleRunner}. The two screens share their statement groups:
 * <ul>
 * <li>opening: VHCF35IS-INITIAL (the truck stock numbers, VHCF35C);</li>
 * <li>a stock chosen: VHCF35IS-TRUCK-SEL (the component names for that truck type);</li>
 * <li>Retrieve: VHCF35IS-VALID-TR / VHCF37IS-VALID-TR (the truck, VHCF35D; its components, VHCF35B; each axle's
 * wheels, bearings and gear boxes, VHCF37F);</li>
 * <li>Update (VHCF35I only): VHCF35IS-PRE-VALID: highlighted serials taken off the truck (VHCF35H), or the typed
 * serials checked (VHCF35D per new part) and added to the truck (VHCF35G).</li>
 * </ul>
 * The inquiry screen's Print has no logic in the old code, so it is not offered.
 */
public final class ConfigQueriesTruckService {
    public static final String BUILD = "VHCF35I", INQUIRY = "VHCF37I";
    static final String STOCK = "Drop-down_List1", STATUS = "Drop-down_List2", LOCATION = "Drop-down_List3", SERIAL = "Text1", RETRIEVE = "Push_Button4",
            UPDATE = "Push_Button1";

    /** a truck position: its key, the old name field, serial field, colour field, and VHCF35I's serial box and highlight button */
    private record Part(String key, String nameField, String serialField, String colorField, String box, String toggle) {}

    private static final List<Part> PARTS = List.of(
            new Part("axle1", "AXLE-1-NAME", "AXLE1", "AXLE1-COLOR", "Text2", "Push_Button6"),
            new Part("axle2", "AXLE-2-NAME", "AXLE2", "AXLE2-COLOR", "Text3", "Push_Button61"),
            new Part("tractionMotor1", "TM-1-NAME", "TM-1", "TM1-COLOR", "Text4", "Push_Button611"),
            new Part("tractionMotor2", "TM-2-NAME", "TM-2", "TM2-COLOR", "Text5", "Push_Button6111"),
            new Part("brakeDisc1", "BD-1-NAME", "BRAKE-DISC-1", "DISC1-COLOR", "Text6", "Push_Button61111"),
            new Part("brakeDisc2", "BD-2-NAME", "BRAKE-DISC-2", "DISC2-COLOR", "Text7", "Push_Button611111"));

    /** an axle's parts (VHCF35IW-HOLD-AXLE): key, field, the old name field ("" = no name on the screen) */
    private static final String[][] AXLE_PARTS = {
            {"wheel1", "WHEEL1", "WHEEL-1-NAME"}, {"wheel2", "WHEEL2", "WHEEL-2-NAME"}, {"wheel3", "WHEEL3", "WHEEL-3-NAME"}, {"wheel4", "WHEEL4", "WHEEL-4-NAME"},
            {"bBearing1", "B-BEARING1", ""}, {"bBearing2", "B-BEARING2", ""}, {"nBearing1", "N-BEARING1", ""}, {"nBearing2", "N-BEARING2", ""},
            {"gearBox1", "GEAR-BOX1", ""}, {"gearBox2", "GEAR-BOX2", ""}};
    /** VHCF35I's N-bearing boxes the user types into */
    private static final Map<String, String> N_BEARING_BOXES = Map.of("nBearing1", "Text11", "nBearing2", "Text16");

    public record Stock(String stock, String description) {}

    public record Options(List<Stock> stocks, List<String> statuses, List<String> locations, List<String> notices) {}

    /** @param highlighted the part is highlighted (yellow) to be taken off the truck */
    public record Component(String key, String name, String serial, boolean highlighted) {}

    public record AxlePart(String key, String name, String serial) {}

    /** @param updatable VHCF35I's Update is enabled */
    public record Truck(String message, String car, String dateRebuilt, String status, String location, String comments, List<Component> components,
                        List<AxlePart> axleParts, boolean updatable, List<String> notices) {}

    /**
     * a VHCF35I Update: the serials typed per part key (axle1, tractionMotor1, ...), the parts highlighted to come off,
     * the N-bearing serials, status, location and comments
     */
    public record Build(String stock, String serial, Map<String, String> components, List<String> takeOff, Map<String, String> axleParts, String status,
                        String location, String comments) {}

    private final ConfigQueriesRuleScreens screens;
    private final List<String> statuses, locations;

    public ConfigQueriesTruckService(ConfigQueriesRuleScreens screens) {
        this.screens = screens;
        this.statuses = ConfigQueriesEgl.listItems(screens.bundle(), BUILD, STATUS);
        this.locations = ConfigQueriesEgl.listItems(screens.bundle(), BUILD, LOCATION);
    }

    /** VHCF35IS-INITIAL: the truck stock numbers (VHCF35C), and the status and location lists */
    public Options options(String group) {
        ConfigQueriesRuleRunner logic = screens.open(INQUIRY, group);
        List<Stock> stocks = new ArrayList<>();
        for (Map<String, String> row : logic.rows("VHCF35C_DATA", "DATA_ROW")) {
            String no = row.getOrDefault("STOCK-NO-L", "");
            if (!no.isBlank()) stocks.add(new Stock(no, row.getOrDefault("SHORT-STOCK-DESC", "")));
        }
        return new Options(stocks, statuses, locations, screens.view(logic, List.of()).notices());
    }

    /** Retrieve on the inquiry (VHCF37I) or build (VHCF35I) screen */
    public Truck retrieve(String group, String screen, String stock, String serial) {
        ConfigQueriesRuleRunner logic = retrieved(group, screen, stock, serial);
        return truck(logic, screen);
    }

    /** VHCF35I: Retrieve, the user's changes, then Update */
    public Truck build(String group, Build b) {
        ConfigQueriesRuleRunner logic = retrieved(group, BUILD, b.stock(), b.serial());
        Map<String, String> typed = b.components() == null ? Map.of() : b.components();
        for (Part p : PARTS) if (typed.containsKey(p.key())) logic.type(p.box(), nz(typed.get(p.key())));
        Map<String, String> axle = b.axleParts() == null ? Map.of() : b.axleParts();
        for (Map.Entry<String, String> e : N_BEARING_BOXES.entrySet()) if (axle.containsKey(e.getKey())) logic.type(e.getValue(), nz(axle.get(e.getKey())));
        if (b.status() != null) logic.type(STATUS, nz(b.status()));
        if (b.location() != null) logic.type(LOCATION, nz(b.location()));
        if (b.comments() != null) logic.type("mleNarrative", b.comments());
        for (String key : b.takeOff() == null ? List.<String>of() : b.takeOff()) {
            Part p = PARTS.stream().filter(x -> x.key().equals(key)).findFirst()
                    .orElseThrow(() -> new InvalidRequestException("takeOff: one of " + PARTS.stream().map(Part::key).toList()));
            logic.fire(p.toggle(), "clicked");
        }
        logic.fire(UPDATE, "clicked");
        return truck(logic, BUILD);
    }

    private ConfigQueriesRuleRunner retrieved(String group, String screen, String stock, String serial) {
        ConfigQueriesRuleRunner logic = screens.open(screen, group);
        String display = display(logic, nz(stock));
        if (!display.isEmpty()) {
            logic.type(STOCK, display);                                      // the list's selected item feeds VHCF35IW
            logic.fire(STOCK, "selectionIndex");                             // -> VHCF35IS-TRUCK-SEL (the parts' names)
        }
        logic.type(SERIAL, nz(serial));
        logic.fire(RETRIEVE, "clicked");
        return logic;
    }

    /** the stock list item (VHCF35C's STOCK-DISPLAY, "12-34-56789 DESC") a stock number stands for; as typed when not listed */
    private static String display(ConfigQueriesRuleRunner logic, String stock) {
        if (stock.isEmpty()) return "";
        List<Map<String, String>> rows = logic.rows("VHCF35C_DATA", "DATA_ROW");
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getOrDefault("STOCK-NO-L", "").equals(stock)) return logic.text("VHCF35C_DATA", "STOCK-DISPLAY", i + 1);
        }
        return stock;
    }

    private Truck truck(ConfigQueriesRuleRunner logic, String screen) {
        View v = screens.view(logic, List.of());
        List<Component> parts = new ArrayList<>();
        for (Part p : PARTS) {
            parts.add(new Component(p.key(), logic.text("VHCF35IW-PASSVALUE", p.nameField()).strip(), logic.text("VHCF35IW-HOLD-TRCK", p.serialField()).strip(),
                    logic.text("VHCF35IW-COLOR", p.colorField()).strip().equals("YELLOW")));
        }
        List<AxlePart> axle = new ArrayList<>();
        for (String[] a : AXLE_PARTS) {
            axle.add(new AxlePart(a[0], a[2].isEmpty() ? "" : logic.text("VHCF35IW-PASSVALUE", a[2]).strip(), logic.text("VHCF35IW-HOLD-AXLE", a[1]).strip()));
        }
        return new Truck(logic.text("VHCF35IW-MSG", "MSG").strip(), logic.text("VHCF35D_DATA", "CAR-NO").strip(), logic.text("VHCF35D_DATA", "DATE-BUY-REBUILT").strip(),
                logic.text("VHCF35IW-HOLD-TRCK", "TRUCK-STAT").strip(), logic.text("VHCF35IW-HOLD-TRCK", "LOC").strip(),
                logic.text("VHCF35IW-HOLD-TRCK", "COMMENTS").strip(), parts, axle, screen.equals(BUILD) && logic.enabled(UPDATE), v.notices());
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
