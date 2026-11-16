package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Consist orientation report (old VHCO01I): a consist range and a revenue date range printed by program VHCO011. The
 * start dates come from VHSV011 (from) and VHHPMA3 (to: today once the revenue day started, else yesterday); the
 * consist checks, their messages, the "ALL" range and the submitted message come from the old screen.
 */
public final class VehicleQueriesConsistOrientationService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String VALID = "VHCO01IS-VALID";
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final Programs programs;
    private final String invalidFrom, invalidTo, all, allFrom, allTo, submitted, toDateNull, title;

    public VehicleQueriesConsistOrientationService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        invalidFrom = t.assigned("VHCO01ID-MSG", PKG, "MESSAGE", "FROM CONSIST IS");
        invalidTo = t.assigned("VHCO01ID-MSG", PKG, "MESSAGE", "LESS THAN");
        all = t.compared(VALID, PKG, "CON-FROM").get(0);                          // CON-FROM != "ALL"
        allFrom = t.assignedAll(VALID, PKG, "CON-FROM").get(0);                    // "ALL" becomes 100 .. 999
        allTo = t.assignedAll(VALID, PKG, "CON-TO").get(0);
        submitted = t.triggeredPrompt("VHCO01I", "SUBMIT").strip();
        toDateNull = t.prompt("VHCO01I", "msgNullToError").strip();
        title = t.windowTitle("VHCO01I");
    }

    public record Printer(String description, String vtam) {}

    /** {@code all}: the word that asks for every consist */
    public record Options(String title, String fromDate, String toDate, String all, List<Printer> printers) {}

    /** what the screen shows when it opens (VHCO01IS-PRINTER, VHCO1I-REV-DATE2); dates yyyy-MM-dd */
    public Options options() {
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> row : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(row, "prtrLocDesc"), text(row, "vtamName")));
        String to = "";
        Programs.Outcome rev = programs.attempt("VHHPMA3", Map.of());
        Map<String, Object> d = rev.result().record("VHHPMA3_DATA");
        if (d != null) to = text(d, "currentTime").compareTo(text(d, "timeRevStart")) >= 0 ? text(d, "currentDate") : text(d, "yesterday");
        String from = "";
        Programs.Outcome f = programs.attempt("VHSV011", Map.of());
        if (f.ok() && f.result().record("VHSV011_DATA") != null) from = text(f.result().record("VHSV011_DATA"), "fromDate");
        return new Options(title, from, to, all, printers);
    }

    /** the consist range sent: the old checks (VHCO01IS-VALID), a blank "to" is the "from", "ALL" is every consist */
    public record Range(String from, String to) {}

    public Range range(String from, String to) {
        String f = from == null ? "" : from.strip(), x = to == null ? "" : to.strip();
        if (!f.equalsIgnoreCase(all)) {
            BigInteger fn = number(f);
            if (fn == null || fn.signum() <= 0) throw new InvalidRequestException(invalidFrom);
            BigInteger tn = x.isEmpty() ? BigInteger.ZERO : number(x);
            if (tn == null) throw new InvalidRequestException(invalidTo);
            if (tn.signum() != 0 && tn.compareTo(fn) < 0) throw new InvalidRequestException(invalidTo);
        }
        if (x.isEmpty()) x = f;
        if (f.equalsIgnoreCase(all)) { f = allFrom; x = allTo; }
        return new Range(f, x);
    }

    /** Print (VHCO01I-SUBMIT: program VHCO011); dates yyyy-MM-dd */
    public String print(String from, String to, String fromDate, String toDate, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Range r = range(from, to);
        if (toDate == null || toDate.isBlank()) throw new InvalidRequestException(toDateNull);
        String fd = date(fromDate), td = date(toDate);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", printer.strip());
        crit.put("conNoFrom", r.from());
        crit.put("conNoTo", r.to());
        crit.put("dateInsrvRev", fd);
        crit.put("dateInsrvRevTo", td);
        programs.run("VHCO011", Map.of("VHCO011_CRIT", crit));
        return submitted;
    }

    private static String date(String d) {
        String s = d == null ? "" : d.strip();
        if (!ISO.matcher(s).matches()) throw new InvalidRequestException("Enter the dates as yyyy-MM-dd.");
        return s;
    }

    private static BigInteger number(String s) {
        return s.chars().allMatch(Character::isDigit) && !s.isEmpty() ? new BigInteger(s) : null;
    }
}
