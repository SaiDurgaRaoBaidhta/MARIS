package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The vehicle queries' batch print reports: one old screen each, all built from the same template (a from / to date
 * that two programs fill in, an optional yard list, a printer, and Hard Copy that checks the dates and submits the
 * report program). Each screen's own statement groups give the rules' texts, so nothing is copied here.
 *
 * <p>Hard Copy (statement group SUBMIT-X, then SUBMIT) checks in this order, the last message set wins:
 * each date through AFCXVLD; the yard (when the screen asks for one); the minutes (when the screen has them);
 * an invalid from date; a to date after today; an invalid to date; a from date after the to date.
 */
public final class VehicleQueriesBatchReportService {
    private static final String PKG = "MRSVehicleQueries";
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * One report (a screen with two report buttons is two reports). {@code fromProgram} / {@code toProgram} are null
     * when the screen has no dates, {@code yardProgram} when it has no yard; {@code minutesFunction} is the statement
     * group that sets the minutes' start value (null when the screen has no minutes).
     */
    public record Report(String key, String screen, String fromProgram, String toProgram, String yardProgram, String submitProgram,
                         String validateFunction, String submitFunction, String minutesFunction) {}

    /** the old screens, by the key the API uses */
    public static final List<Report> REPORTS = List.of(
            new Report("cars-pulled", "VHCP01I", "VHCP01A", "VHCP01B", "VHCP01C", "VHCP01D", "VHCP01IS-SUBMIT-X", "VHCP01IS-SUBMIT", null),
            new Report("trains-cleared-elsewhere", "VHDP01I", "VHDP01A", "VHDP01B", null, "VHDP01C", "VHDP01IS-SUBMIT-X", "VHDP01IS-SUBMIT", null),
            new Report("fixed-consist-orientation", "VHFO01I", "VHFO01A", "VHFO01B", null, "VHFO01E", "VHFO01IS-SUBMIT-X", "VHFP01IS-SUBMIT", null),
            new Report("reason-used-by-shop", "VHMR01I", "VHMR01A", "VHMR01B", "VHMR01C", "VHMR01D", "VHMR01IS-SUBMIT-X", "VHMR01IS-SUBMIT", null),
            new Report("shop-status-reason-50", "VHMR02I", "VHMR02A", "VHMR02B", "VHMR02C", "VHMR02D", "VHMR02IW-SUBMIT-X", "VHMR02IS-SUBMIT", null),
            new Report("shop-status-reason-099", "VHMR03I", "VHMR03A", "VHMR03B", "VHMR03C", "VHMR03D", "VHMR03IS-SUBMIT-X", "VHMR03IS-SUBMIT", null),
            new Report("no-go-cars-moved", "VHNG01I", "VHNG01A", "VHNG01B", "VHNG01C", "VHNG01D", "VHNG01IS-SUBMIT-X", "VHNG01IS-SUBMIT", "VHNG01IS-INIT"),
            new Report("combined-trains", "VHOT01I", "VHOT01A", "VHOT01B", null, "VHOT01C", "VHOT01IS-SUBMIT-X", "VHOT01IS-SUBMIT", null),
            new Report("pseudo-train-consists", "VHPS01I", "VHPS01A", "VHPS01B", "VHPS01C", "VHPS01E", "VHPS01IS-SUBMIT-X", "VHPS01IS-SUBMIT", null),
            new Report("revenue-car-run-time", "VHRC03I", "VHRC03A", "VHRC03B", null, "VHRC03C", "VHRC03IS-SUBMIT-X", "VHRC03IS-SUBMIT", null),
            new Report("yard-pseudo-trains", "VHPT01I", "VHPT01A", "VHPT01B", "VHPT01C", "VHPT01E", "VHPT01IS-SUBMIT-X", "VHPT01IS-SUBMIT", null),
            new Report("clear-train-moves", "VHMC01I", "VHMC01A", "VHMC01B", "VHMC01C", "VHMC01D", "VHMC01IS-SUBMIT-X", "VHMC01IS-SUB-CLEAR", "VHMC01IS-INIT"),
            new Report("make-train-moves", "VHMC01I", "VHMC01A", "VHMC01B", "VHMC01C", "VHMC01E", "VHMC01IS-SUBMIT-M", "VHMC01IS-SUB-MAKE", "VHMC01IS-INIT"),
            new Report("out-of-town-no-go", "VHYTOTI", null, null, "VHYTOTA", "VHYTOTB", "VHYTOTIS-SUBMIT-X", "VHYTOTIS-SUBMIT", null));

    /**
     * a report's rules' texts, read from its screen; "" where the old branch sets no text (the message stays as it
     * was). {@code yard} / {@code minutes} are null when the screen does not check them.
     */
    private record Texts(String incorrectFrom, String incorrectTo, String sqlFrom, String sqlTo, String invalidFrom, String invalidTo,
                         String toAfterToday, String fromAfterTo, String yard, String minutes, BigDecimal minutesStart, String failed,
                         String submitted, String button, String title) {}

    private final Programs programs;
    private final Clock clock;
    /** per report: criteria fields SUBMIT copies from working fields nothing sets (json name -> their start text) */
    private final Map<String, Map<String, String>> fixedCriteria = new LinkedHashMap<>();
    /** per yard program: the DATA_ROW fields, to rebuild the row text the old drop-down shows */
    private final Map<String, List<FieldLayout>> yardParts = new LinkedHashMap<>();
    private final Map<String, Report> byKey = new LinkedHashMap<>();
    private final Map<String, Texts> texts = new LinkedHashMap<>();

    public VehicleQueriesBatchReportService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        for (Report r : REPORTS) {
            byKey.put(r.key(), r);
            if (r.yardProgram() != null) yardParts.put(r.yardProgram(), yardDrop(legacy, catalog, r.yardProgram()));
            Map<String, String> fixed = new LinkedHashMap<>();
            List<FieldLayout> critFields = critLayout(legacy, catalog, r.submitProgram());
            for (EglTexts.Copy c : t.copiedInto(r.submitFunction(), PKG, r.submitProgram() + "_CRIT")) {
                if (INPUTS.contains(c.target().toUpperCase(java.util.Locale.ROOT))) continue;
                String json = critFields.stream().filter(f -> f.name().equalsIgnoreCase(c.target())).map(FieldLayout::json).findFirst()
                        .orElseThrow(() -> new IllegalStateException(r.submitProgram() + "_CRIT has no " + c.target()));
                fixed.put(json, t.startText(c.sourceRecord(), PKG, c.sourceField()));
            }
            fixedCriteria.put(r.key(), fixed);
            String v = r.validateFunction();
            List<String> incorrect = t.assignedUnder(v, PKG, "MESG1", "INCORRECT-DATE");
            List<String> sql = t.assignedUnder(v, PKG, "MESG1", "SQL-ERROR");
            List<String> yard = t.assignedUnder(v, PKG, "MESG1", "YRD");
            List<String> minutes = t.assignedUnder(v, PKG, "MESG1", "MINUTES");
            boolean dates = r.fromProgram() != null;
            texts.put(r.key(), new Texts(at(incorrect, 0, v, dates), at(incorrect, 1, v, dates), at(sql, 0, v, dates), at(sql, 1, v, dates),
                    at(t.assignedUnder(v, PKG, "MESG1", "INVALID-FROM-DATE"), 0, v, dates),
                    at(t.assignedUnder(v, PKG, "MESG1", "INVALID-TO-DATE"), 0, v, dates),
                    at(t.assignedUnder(v, PKG, "MESG1", "WS-CURR-YEAR-NUM"), 0, v, dates),
                    at(t.assignedUnder(v, PKG, "MESG1", "FROM-DATE-YEAR-NUM"), 0, v, dates),
                    yard.isEmpty() ? null : yard.get(0),
                    minutes.isEmpty() ? null : minutes.get(0),
                    r.minutesFunction() == null ? null : t.assignedNumber(r.minutesFunction(), PKG, "MINUTES"),
                    t.assigned(r.submitFunction(), PKG, "MESG1", null),
                    t.triggeredPrompt(r.screen(), "SUBMIT").strip(), t.buttonFor(r.screen(), v), t.windowTitle(r.screen())));
        }
    }

    /** the criteria fields the API fills from the request */
    private static final java.util.Set<String> INPUTS = java.util.Set.of("FROM-DATE", "TO-DATE", "YRD", "MINUTES", "REPT-ID");

    private static List<FieldLayout> critLayout(LegacyProgramService legacy, LegacyCatalog catalog, String program) {
        for (ServiceDefinition.Param p : legacy.definition(program).params()) {
            if (p.name().equals(program + "_CRIT")) return catalog.record(p.record()).fields();
        }
        throw new IllegalStateException(program + " has no " + program + "_CRIT");
    }

    private static List<FieldLayout> yardDrop(LegacyProgramService legacy, LegacyCatalog catalog, String program) {
        for (ServiceDefinition.Param p : legacy.definition(program).params()) {
            if (!p.name().equals(program + "_DATA")) continue;
            for (FieldLayout row : catalog.record(p.record()).fields()) if (row.json().equals("dataRow")) return row.children();
        }
        throw new IllegalStateException(program + "_DATA has no DATA_ROW");
    }

    /** a decoded row as the text of its bytes: every field padded to its length, groups field by field */
    private static void rowText(List<FieldLayout> fields, Map<String, Object> values, StringBuilder out) {
        for (FieldLayout f : fields) {
            Object v = values == null ? null : values.get(f.json());
            if (!f.children().isEmpty()) {
                @SuppressWarnings("unchecked") Map<String, Object> group = v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
                rowText(f.children(), group, out);
            } else {
                out.append(String.format("%-" + f.length() + "s", v == null ? "" : v.toString()));
            }
        }
    }

    /** the i-th check's text; the check must exist when the screen has dates (the template has it), its text may be "" */
    private static String at(List<String> l, int i, String function, boolean required) {
        if (l.size() <= i) {
            if (required) throw new IllegalStateException(function + " lacks check " + (i + 1) + " of the report template");
            return "";
        }
        return l.get(i);
    }

    private static String set(String message, String text) {
        return text.isEmpty() ? message : text;
    }

    private Report report(String key) {
        Report r = byKey.get(key);
        if (r == null) throw new InvalidRequestException("Unknown report " + key + ". Reports: " + String.join(", ", byKey.keySet()));
        return r;
    }

    // ---- what the screen shows when it opens -------------------------------------------------------------

    public record Yard(String code, String label) {}

    public record Printer(String description, String vtam) {}

    public record ReportOptions(String report, String screen, String title, String button, boolean dates, String fromDate, String toDate, List<Yard> yards,
                                boolean yardRequired, BigDecimal minutes, List<Printer> printers) {}

    /** the INIT statement groups: the start dates (programs A and B), the yards (program C) and the printers (WS70A01) */
    public ReportOptions options(String key) {
        Report r = report(key);
        Texts t = texts.get(key);
        String from = r.fromProgram() == null ? "" : startDate(r.fromProgram(), "fromDate");
        String to = r.toProgram() == null ? "" : startDate(r.toProgram(), "toDate");
        List<Yard> yards = new ArrayList<>();
        if (r.yardProgram() != null) {
            Programs.Outcome o = programs.attempt(r.yardProgram(), Map.of());
            if (o.ok()) {
                for (Map<String, Object> row : rows(o.result(), r.yardProgram() + "_DATA", "dataRow")) {
                    @SuppressWarnings("unchecked") Map<String, Object> drop = (Map<String, Object>) row.get("yrdDrop");
                    if (drop == null) continue;
                    String code = text(drop, "yrd");
                    if (code.isEmpty()) continue;
                    StringBuilder label = new StringBuilder();
                    rowText(yardParts.get(r.yardProgram()), row, label);
                    yards.add(new Yard(code, label.toString().strip()));
                    continue;
                }
            }
        }
        List<Printer> printers = printers();
        return new ReportOptions(key, r.screen(), t.title(), t.button(), r.fromProgram() != null, from, to, yards, t.yard() != null, t.minutesStart(), printers);
    }

    /** the printers (WS70A01), as every printer window of this module lists them */
    public List<Printer> printers() {
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> row : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(row, "prtrLocDesc"), text(row, "vtamName")));
        return printers;
    }

    /** a start date program's date (yyyy-MM-dd); blank when the program fails, as the old screen leaves the field empty */
    private String startDate(String program, String field) {
        Programs.Outcome o = programs.attempt(program, Map.of());
        if (!o.ok()) return "";
        Map<String, Object> data = o.result().record(program + "_DATA");
        return data == null ? "" : text(data, field);
    }

    // ---- Hard Copy -----------------------------------------------------------------------------------------

    public record PrintRequest(String fromDate, String toDate, String yard, BigDecimal minutes, String printer) {}

    /** SUBMIT-X (the checks) and SUBMIT (the report program); returns the old screen's confirmation */
    public String print(String key, PrintRequest q) {
        Report r = report(key);
        Texts t = texts.get(key);
        if (q.printer() == null || q.printer().isBlank()) throw new InvalidRequestException("Select a printer.");
        boolean dates = r.fromProgram() != null;
        String from = dates ? date(q.fromDate()) : "", to = dates ? date(q.toDate()) : "";

        String message = null;
        boolean invalidFrom = false, invalidTo = false;
        String noError = " ";                                      // AFCXVLDW.NO-ERROR before any check
        if (!from.isEmpty()) {
            String v = validate(from);
            noError = v.equals("ok") ? "Y" : "N";
            if (v.equals("incorrect")) { invalidFrom = true; message = set(message, t.incorrectFrom()); }
            else if (v.equals("sql")) message = set(message, t.sqlFrom());
        }
        if (!to.isEmpty()) {
            String v = validate(to);
            noError = v.equals("ok") ? "Y" : "N";
            if (v.equals("incorrect")) { invalidTo = true; message = set(message, t.incorrectTo()); }
            else if (v.equals("sql")) message = set(message, t.sqlTo());
        }
        String yard = q.yard() == null ? "" : q.yard().strip();
        if (t.yard() != null && yard.isEmpty()) { message = set(message, t.yard()); noError = "N"; }
        BigDecimal minutes = q.minutes() == null ? BigDecimal.ZERO : q.minutes();
        if (t.minutes() != null && minutes.signum() == 0) { message = set(message, t.minutes()); noError = "N"; }
        boolean ok = true;
        if (invalidFrom) { message = set(message, t.invalidFrom()); ok = false; }
        else if (!to.isEmpty() && after(to, LocalDate.now(clock).toString())) { message = set(message, t.toAfterToday()); invalidTo = true; noError = "N"; }
        if (ok) {
            if (invalidTo) { message = set(message, t.invalidTo()); ok = false; }
            else if (!from.isEmpty() && !to.isEmpty() && after(from, to)) { message = set(message, t.fromAfterTo()); noError = "N"; }
        }
        if (ok && noError.equals("N")) ok = false;
        if (!ok) throw new InvalidRequestException(message == null ? t.failed() : message);

        Map<String, Object> crit = new LinkedHashMap<>();
        if (dates) {
            crit.put("fromDate", slashed(from));
            crit.put("toDate", slashed(to));
        }
        if (r.yardProgram() != null) crit.put("yrd", yard);
        if (t.minutes() != null) crit.put("minutes", minutes);
        crit.putAll(fixedCriteria.get(key));
        crit.put("reptId", q.printer().strip());
        Programs.Outcome o = programs.attempt(r.submitProgram(), Map.of(r.submitProgram() + "_CRIT", crit));
        int urc = o.result().control() == null ? 0 : o.result().control().returnCode();
        if (urc != 0 && urc != 4 && urc != 100) throw new InvalidRequestException(t.failed());
        return t.submitted();
    }

    /** AFCXVLD-VALIDATE: "ok", "incorrect" (DATE-ERROR) or "sql" (OTHER-SQL-ERROR) */
    private String validate(String iso) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", iso);
        crit.put("otherSqlError", "N");
        Map<String, Object> back = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result().record("AFCXVLD_CRIT");
        String dateError = back == null ? "N" : text(back, "dateError"), sql = back == null ? "N" : text(back, "otherSqlError");
        if (dateError.equals("Y") && sql.equals("N")) return "incorrect";
        if (dateError.equals("N") && sql.equals("Y")) return "sql";
        return "ok";
    }

    private static String date(String d) {
        if (d == null || d.isBlank()) return "";
        if (!ISO.matcher(d.strip()).matches()) throw new InvalidRequestException("Dates are yyyy-MM-dd: " + d);
        return d.strip();
    }

    /** the old screens compare year, then month, then day as numbers: the same as comparing yyyy-MM-dd text */
    private static boolean after(String iso, String than) {
        return iso.compareTo(than) > 0;
    }

    /** MM/DD/YYYY, as SUBMIT sends it; an empty date still gets its slashes */
    private static String slashed(String iso) {
        return iso.isEmpty() ? "  /  /    " : iso.substring(5, 7) + "/" + iso.substring(8, 10) + "/" + iso.substring(0, 4);
    }
}
