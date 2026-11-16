package com.bart.maris.middleware.domain.yardqueries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The reports criteria window (old screen VHYT27I): VHYT27IS-BEGIN (printers WS70A01, dates VHYT27B, yards VHRT04A) and Hard
 * Copy (VHYT27IS-TEST-DATE, VHYT27IS-SUBMIT). The report codes are the menu's WHICH-REPORT values, read from VHYT27IS-BEGIN in
 * its order (report 1 = SHOPSTAT ...); Display opens the report pages (shop status, car / status history).
 */
public class YardQueriesReportsService {
    private static final String PKG = "MrsYardTrackingQueries";
    /** report number (VHYT27IW.REPORT-INDEX) -> the program VHYT27IS-SUBMIT prints it with; 6 and 7 have none */
    private static final Map<Integer, String> PRINT = Map.of(1, "VHYT27A", 2, "VHYT27F", 3, "VHYT27C", 4, "VHYT27D", 5, "VHYT27E", 8, "VHYT27G");

    private final Programs programs;
    private final List<String> reports;
    private final String badRange, submitted;

    public YardQueriesReportsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        this.reports = YardQueriesEgl.compared(screens, "VHYT27IS-BEGIN", PKG, "WHICH-REPORT");
        this.badRange = screens.promptText("VHYT27I", "Message_Prompter1");
        this.submitted = screens.promptText("VHYT27I", "Message_Prompter2");
    }

    /** VHYT27IS-BEGIN: a list that fails is a warning and the others still load, as on the old screen */
    public YardQueriesReports.Options options() {
        List<String> warnings = new ArrayList<>();
        List<YardQueriesReports.Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new YardQueriesReports.Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        else warnings.add(p.error());
        String from = "", to = "";
        Programs.Outcome d = programs.attempt("VHYT27B", Map.of());
        if (d.ok()) {
            Map<String, Object> rec = d.result().record("VHYT27B_DATA");
            from = text(rec, "startDatedb2");
            to = text(rec, "endDatedb2");
        } else warnings.add(d.error());
        List<YardQueriesReports.Code> yards = new ArrayList<>();
        Programs.Outcome y = programs.attempt("VHRT04A", Map.of());
        if (y.ok()) for (Map<String, Object> r : rows(y.result(), "VHRT04A_DATA", "dataRow")) yards.add(new YardQueriesReports.Code(text(r, "yrd"), text(r, "yrdDesc")));
        else warnings.add(y.error());
        return new YardQueriesReports.Options(printers, yards, from, to, warnings);
    }

    /** Hard Copy: VHYT27IS-TEST-DATE (reports 3, 4 and 8), then VHYT27IS-SUBMIT; returns the old confirmation */
    public String print(YardQueriesReports.Print r) {
        String code = nz(r.report()).toUpperCase(Locale.ROOT);
        int report = reports.indexOf(code) + 1;
        String program = PRINT.get(report);
        if (program == null) throw new InvalidRequestException(report == 0 ? "Unknown report: " + code : "The " + code + " report has no hard copy.");
        String printer = nz(r.printer());
        if (printer.isEmpty()) throw new InvalidRequestException("Select a printer for the hard copy.");
        String from = nz(r.from()), to = nz(r.to());
        if ((report == 3 || report == 4 || report == 8) && !YardQueriesHistoryService.validRange(from, to)) throw new InvalidRequestException(badRange);

        String yard = nz(r.yard()).toUpperCase(Locale.ROOT);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yrdTrkgYrd", report == 8 || !yard.isEmpty() ? yard : "HY");          // VHYT27G takes the yard as it is
        crit.put("reptCd", printer);
        if (report == 1) crit.put("reptType", code);
        if (report >= 3 && report <= 5) { crit.put("dateReptFrom", mdy(from)); crit.put("dateReptTo", mdy(to)); }
        if (report == 8) { crit.put("dateReptFrom", from); crit.put("dateReptTo", to); }
        if (report == 3) {
            String fc = nz(r.fromCar()), tc = nz(r.toCar());
            crit.put("fromCar", fc.isEmpty() ? "0300" : fc);
            crit.put("toCar", !tc.isEmpty() ? tc : fc.isEmpty() ? "2999" : fc);
        }
        programs.run(program, Map.of(program + "_CRIT", crit));
        return submitted;
    }

    /** the old screen's MM-DD-YYYY date (the field as typed) from yyyy-MM-dd; anything else as it is */
    private static String mdy(String iso) {
        return iso.matches("\\d{4}-\\d{2}-\\d{2}") ? iso.substring(5, 7) + "-" + iso.substring(8) + "-" + iso.substring(0, 4) : iso;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
