package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens.Step;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Add a component serial to stock (old screen VHCF21I), run with the screen's own rules by {@link ConfigQueriesRuleRunner}:
 * VHCF21I-INITIAL (component types, VHCF21A; today's date), VHCF21I-GET-STOCK (the type's stock numbers, VHCF21B),
 * Update: VHCF21I-VALID-DATE (date and missing-data checks), VHCF21I-UPDATE (serial already there? VHCF21C) and
 * VHCF21I-ADD (VHCF21E tracking code, VHCF21D insert as STO / INV).
 */
public final class ConfigQueriesComponentAddService {
    static final String SCREEN = "VHCF21I";
    /** the old widgets: type list, stock list, serial, date, action list, message */
    static final String TYPE = "Drop-down_List1", STOCK = "Drop-down_List2", SERIAL = "Text3", DATE = "Text2", ACTION = "Drop-down_List3", MESSAGE = "Text1";

    public record Options(List<String> componentTypes, List<String> actions, String date, List<String> notices) {}

    public record Stocks(List<String> stocks, List<String> notices) {}

    /** @param added the old screen's success text was shown */
    public record Result(boolean added, String message, List<String> notices) {}

    private final ConfigQueriesRuleScreens screens;
    private final List<String> actions;
    private final String success;

    public ConfigQueriesComponentAddService(ConfigQueriesRuleScreens screens) {
        this.screens = screens;
        this.actions = ConfigQueriesEgl.listItems(screens.bundle(), SCREEN, ACTION);
        this.success = ConfigQueriesEgl.assignedTexts(screens.bundle(), "VHCF21I-ADD", "MRSConfigurTrackQuery", "MSG").get(0).strip();
    }

    /** the screen opens: its component types (VHCF21A) and the date it proposes */
    public Options options(String group) {
        ConfigQueriesRuleRunner logic = screens.open(SCREEN, group);
        View v = screens.view(logic, List.of(DATE));
        return new Options(column(logic, "VHCF21A_DATA", "CMPNT-EQUIP-TYPE"), actions, v.field(DATE), v.notices());
    }

    /** a component type chosen: its stock numbers (VHCF21B) */
    public Stocks stocks(String group, String type) {
        ConfigQueriesRuleRunner logic = screens.open(SCREEN, group);
        choose(logic, type);
        return new Stocks(column(logic, "VHCF21B_DATA", "STOCK-NO-L"), screens.view(logic, List.of()).notices());
    }

    /** Update: the checks, then the serial added to stock */
    public Result add(String group, String type, String stock, String serial, String date) {
        ConfigQueriesRuleRunner logic = screens.open(SCREEN, group);
        choose(logic, type);
        for (Step s : List.of(Step.type(STOCK, nz(stock)), Step.type(SERIAL, nz(serial)), Step.type(DATE, nz(date)))) logic.type(s.widget(), s.value());
        logic.fire("Push_Button1", "clicked");
        View v = screens.view(logic, List.of(MESSAGE));
        String msg = v.field(MESSAGE).strip();
        return new Result(msg.equals(success), msg, v.notices());
    }

    /** the type list's selected item feeds VHCF21IW and runs VHCF21I-GET-STOCK, as choosing it on the old screen did */
    private static void choose(ConfigQueriesRuleRunner logic, String type) {
        if (nz(type).isEmpty()) return;                                      // nothing chosen: the list never fired
        logic.type(TYPE, nz(type));
        logic.fire(TYPE, "selectedItem");
    }

    /** a list's items: one column of a program's rows, blanks left out */
    static List<String> column(ConfigQueriesRuleRunner logic, String record, String field) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : logic.rows(record, "DATA_ROW")) {
            String v = row.getOrDefault(field, "");
            if (!v.isBlank()) out.add(v);
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
