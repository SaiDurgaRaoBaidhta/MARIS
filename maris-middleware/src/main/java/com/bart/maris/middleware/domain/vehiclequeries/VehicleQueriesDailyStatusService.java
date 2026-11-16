package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily vehicle status (old VHDVS1I, with its Next pages VHDVS2I and VHDVS3I and its printer window VHDVS4I). The
 * report is the old screen's own arithmetic: its statement groups run on the program results exactly as the screen
 * runs them when it opens (VHDVS11 the dates, VHDVS03 the cars required, VHDVS01 the counts) or when Retrieve Data is
 * clicked for another day. The tables are the old windows' tables, with their column headings read from the screens.
 */
public final class VehicleQueriesDailyStatusService {
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    /** the old windows' tables: screen, table widget, record shown */
    private static final String[][] TABLES = {
            {"VHDVS1I", "Container_Details1", "VHDVS1IW_VALUES"},
            {"VHDVS1I", "Container_Details3", "VHDVS1I_FAIL"},
            {"VHDVS2I", "Container_Details1", "VHDVS2IW_VALUES"},
            {"VHDVS2I", "Container_Details3", "VHDVS2I_RETURN"},
            {"VHDVS3I", "Container_Details1", "VHDVS3IW_VALUES"},
            {"VHDVS3I", "Container_Details3", "VHDVS3I_FLEET"},
            {"VHDVS3I", "Container_Details2", "VHDVS3IW_OPSFLEET2"}};
    // VHDVS3I's fourth table (Container_Details4) repeats VHDVS3I_FLEET under unnamed columns and shows only blank rows: left out
    /** the opening of VHDVS1I, then what each statement group's end starts (hasExecuted connections) */
    private static final List<String> OPEN = List.of("VHDVS1IS-CLEAR", "VHDVS1IS-LOAD", "VHDVS1IS-CONVERT", "VHDVS1IS-HOME-YRD", "VHDVS1IS-GET-DATA");
    private static final List<String> RETRIEVE = List.of("VHDVS1IS-CLEAR", "VHDVSCLICK", "VHDVS1IS-CONVERT", "VHDVS1IS-GET-DATA");
    private static final List<String> AFTER_DATA = List.of("VHDVS1IS-CALCULATE", "VHDVS1IS-CAL-FAIL", "VHDVS1IS-CAL-RET", "VHDVS1IS-OOS",
            "VHDVS1IS-AT-0400", "VHDVS1IS-AVAIL");

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Programs programs;
    private final EglTexts texts;
    private final Clock clock;
    private final String printed;

    public VehicleQueriesDailyStatusService(ScreenBundle bundle, LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.programs = new Programs(legacy, messages);
        this.texts = t;
        this.clock = clock;
        this.printed = t.triggeredPrompt("VHDVS4I", "SUBMIT").strip();
    }

    public record Column(String field, String heading) {}

    /** one table of the old windows: {@code screen} is the window it was on; rows by field name (DESC and the counts) */
    public record Table(String screen, String title, List<Column> columns, List<Map<String, Object>> rows) {}

    /** the gain or loss per yard (VHDVS2I) */
    public record Gains(long concord, long dalyCity, long hayward, long richmond, long total) {}

    public record DailyStatus(String revenueDate, String validAsOf, List<Table> tables, Gains gains) {}

    /**
     * the report: with no date, as VHDVS1I opens (today); with a date (yyyy-MM-dd), as Retrieve Data shows it after the
     * window opened
     */
    public DailyStatus report(String revenueDate) {
        VehicleQueriesScreenLogic s = new VehicleQueriesScreenLogic(bundle, "VHDVS1I", legacy, clock);
        open(s);
        if (revenueDate != null && !revenueDate.isBlank()) {
            s.set("VHDVS2IW-DATETIME", "currentDate", typed(revenueDate));
            reportDate(s);
            for (String f : RETRIEVE) run(s, f);
        }
        Map<String, VehicleQueriesScreenLogic> screens = new LinkedHashMap<>();
        screens.put("VHDVS1I", s);
        VehicleQueriesScreenLogic s3 = new VehicleQueriesScreenLogic(bundle, "VHDVS3I", legacy, clock);   // VHDVS3IS-MOVE
        s3.put("VHDVS3IW_OPSFLEET", s.get("VHDVS3IW_OPSFLEET"));
        s3.run("VHDVS3IS-MOVE");
        List<Table> tables = new ArrayList<>();
        for (String[] t : TABLES) {
            VehicleQueriesScreenLogic from = t[2].equals("VHDVS3IW_OPSFLEET2") ? s3 : s;
            List<Column> columns = new ArrayList<>();
            for (EglTexts.Column c : texts.tableColumns(t[0], t[1])) columns.add(new Column(c.field().toUpperCase(java.util.Locale.ROOT), c.heading()));
            String title = columns.isEmpty() ? "" : columns.get(0).heading();
            tables.add(new Table(t[0], title, columns, from.rows(t[2])));
        }
        Map<String, Object> dt = s.get("VHDVS2IW-DATETIME"), avail = s.get("VHDVS2IW_AVAIL");
        Gains gains = new Gains(n(avail, "cyGain"), n(avail, "dyGain"), n(avail, "hyGain"), n(avail, "ryGain"), n(avail, "totalGain"));
        return new DailyStatus(text(dt, "currentDate"), text(dt, "currentTime"), tables, gains);
    }

    private void open(VehicleQueriesScreenLogic s) {
        for (String f : OPEN) {
            run(s, f);
            if (f.equals("VHDVS1IS-LOAD")) reportDate(s);
        }
    }

    /** VHDVS1I's connection VHDVS2IW-DATETIME.CURRENT-DATE -> VHDVS01_CRIT.REPORT-DATE: the shown date is the report date */
    private static void reportDate(VehicleQueriesScreenLogic s) {
        s.set("VHDVS01_CRIT", "reportDate", s.get("VHDVS2IW-DATETIME").get("currentDate"));
    }

    private static void run(VehicleQueriesScreenLogic s, String function) {
        s.run(function);
        if (function.equals("VHDVS1IS-GET-DATA")) for (String f : AFTER_DATA) s.run(f);
    }

    private static long n(Map<String, Object> m, String f) {
        Object v = m.get(f);
        return v instanceof Number x ? x.longValue() : 0;
    }

    /** the date as the old window holds it (MM-dd-yyyy); the API takes yyyy-MM-dd */
    private static String typed(String iso) {
        try {
            return MDY.format(LocalDate.parse(iso.strip()));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("The revenue date must be yyyy-MM-dd.");
        }
    }

    public record Printer(String name, String vtamName) {}

    /** VHDVS4I's printers (VHYS01IS-INIT-X: WS70A01) */
    public List<Printer> printers() {
        List<Printer> out = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) out.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return out;
    }

    /** Hard Copy (VHDVS4IS-SUBMIT: program VHDVS04) for the report date VHDVS1I shows (yyyy-MM-dd; none = today) */
    public String print(String revenueDate, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String date = revenueDate == null || revenueDate.isBlank() ? MDY.format(LocalDate.now(clock)) : typed(revenueDate);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", printer.strip());
        crit.put("dateReptFrom", date);
        programs.run("VHDVS04", Map.of("VHDVS04_CRIT", crit));
        return printed;
    }
}
