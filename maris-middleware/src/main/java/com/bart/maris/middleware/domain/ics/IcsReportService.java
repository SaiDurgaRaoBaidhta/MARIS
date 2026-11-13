package com.bart.maris.middleware.domain.ics;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ICS reports. The rules are the old screens' (MARIS_RUI/EGLSource/MRSVehicleIcsReports/RUIFunctions.egl):
 * VHIC30IS-BEGIN (the lists), VHIC30IS-SELECT and -RPT-TYPE (the criteria), VHIC31IS-GET-DATA (the report),
 * VHIC30IS-SUBMIT (hard copy). Read-only except printing, which asks MARIS to print.
 */
public class IcsReportService {
    private static final String PKG = "MRSVehicleIcsReports";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final Programs programs;
    private final Clock clock;
    /* the old screens' own texts, read from the screen bundle (EGL source) when the service starts */
    private final String badDate, submitted, allYards, allLocations, allMessages;

    public IcsReportService(LegacyProgramService legacy, MessageResolver messages, Clock clock, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.badDate = screens.promptText("VHIC30I", "Bad-Date-Msg");
        this.submitted = screens.promptText("VHIC30I", "PickUpPrinterMsg");
        this.allYards = screens.assignedText("VHIC30IS-BEGIN", PKG, "YRD-DESC");
        this.allLocations = screens.assignedText("VHIC30IS-BEGIN", PKG, "LOC-DESC");
        this.allMessages = screens.assignedText("VHIC30IS-BEGIN", PKG, "MSG-TRAN-CD-DESC");
    }

    // ---- VHIC30IS-BEGIN: printers, revenue date, yards, locations, message codes ---------------------------

    public IcsOptions options() {
        List<String> warnings = new ArrayList<>();
        List<IcsOptions.Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) {
            for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) {
                printers.add(new IcsOptions.Printer(text(r, "prtrLocDesc"), text(r, "vtamName"), text(r, "prtrName"), text(r, "serverName")));
            }
        } else warnings.add(p.error());

        String revenueDate = "";
        Programs.Outcome d = programs.attempt("VHIC30A", Map.of());
        if (d.ok()) {
            Map<String, Object> rec = d.result().record("VHIC30A_DATA");
            revenueDate = text(rec, "revDatedb2");
            if (revenueDate.isEmpty() && !text(rec, "revDate").isEmpty()) revenueDate = toIso(text(rec, "revDate"));
        } else warnings.add(d.error());

        List<IcsOptions.Code> yards = codes("VHRT04A", "VHRT04A_DATA", "yrd", "yrdDesc", allYards, warnings);
        List<IcsOptions.Code> locations = codes("VHIC30B", "VHIC30B_DATA", "locIcs", "locIcsDesc", allLocations, warnings);
        List<IcsOptions.Code> messageCodes = codes("VHIC30C", "VHIC30C_DATA", "msgTranCd", "msgTranCdDesc", allMessages, warnings);
        return new IcsOptions(revenueDate, printers, yards, locations, messageCodes, warnings);
    }

    /** a code list; on success ALL is added last, on error the list stays empty and the message is a warning */
    private List<IcsOptions.Code> codes(String program, String record, String code, String desc, String all, List<String> warnings) {
        List<IcsOptions.Code> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt(program, Map.of());
        if (!o.ok()) {
            warnings.add(o.error());
            return out;
        }
        for (Map<String, Object> r : rows(o.result(), record, "dataRow")) out.add(new IcsOptions.Code(text(r, code), text(r, desc)));
        out.add(new IcsOptions.Code("ALL", all));
        return out;
    }

    // ---- VHIC30IS-SELECT + VHIC31IS-GET-DATA: the report -----------------------------------------------------

    public IcsReport messages(IcsCriteria c) {
        LocalDate date = checkDate(c.revenueDate());
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("revDate", ISO.format(date));
        crit.put("reportType", reportType(c));
        crit.put("reportOrder", c.groupBy().equals("location") ? "L" : "Y");
        Range msg = range(c.messageCode(), "", "99"), loc = range(c.location(), "", "999"), yrd = range(c.yard(), "", "999");
        crit.put("begMsgTranCd", msg.from);
        crit.put("endMsgTranCd", msg.to);
        crit.put("begConCurrLoc", loc.from);
        crit.put("endConCurrLoc", loc.to);
        crit.put("begYrdTrkg", yrd.from);
        crit.put("endYrdTrkg", yrd.to);

        ProgramResult r = programs.run("VHIC31A", Map.of("VHIC31A_CRIT", crit));
        List<IcsReport.Message> out = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHIC31A_DATA", "dataRow")) out.add(message(row));
        }
        return new IcsReport(ISO.format(date), out.size(), out.size() >= IcsReport.MAX_ROWS, out);
    }

    private static String reportType(IcsCriteria c) {
        return c.order().equals("consist") ? "C" : "D";
    }

    private record Range(String from, String to) {}

    /** ALL = the whole range the old screen sends; one value = from and to that value */
    private static Range range(String value, String low, String high) {
        return value.equals("ALL") ? new Range(low, high) : new Range(value, value);
    }

    /** one row as VHIC31I shows it; the consist dash number is rebuilt from DASH-NO and "00" shown blank */
    private static IcsReport.Message message(Map<String, Object> row) {
        @SuppressWarnings("unchecked") Map<String, Object> con = row.get("conNo") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked") Map<String, Object> to = row.get("conDashTo") instanceof Map<?, ?> m2 ? (Map<String, Object>) m2 : Map.of();
        String consist = consist(text(con, "conNoNo"), text(con, "dash"), number(row, "dashNo"));
        String consistTo = consist(text(to, "conNoTo"), text(to, "dashTo"), number(row, "dashNoTo"));
        List<String> cars = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Object car = row.get("car" + i);
            String s = car instanceof Map<?, ?> cm ? String.join("", text(cast(cm), "left" + i), text(cast(cm), "no" + i), text(cast(cm), "right" + i))
                    : car == null ? "" : car.toString().strip();
            if (!s.isEmpty()) cars.add(s);
        }
        return new IcsReport.Message(join(text(row, "tsDate"), text(row, "tsTime")), text(row, "msgTranCd"), consist, consistTo,
                number(row, "conSize"), text(row, "conCurrLoc"), text(row, "conCurrLocIcs"), text(row, "trainDir"),
                text(row, "inZoneFlag"), join(text(row, "dateCreate"), text(row, "timeCreate")),
                join(text(row, "dateTrans"), text(row, "timeTrans")), number(row, "msgSeqNo"), text(row, "msgProcessedCd"),
                text(row, "funcMsgCmplt"), join(text(row, "dateMsgCmplt"), text(row, "timeMsgCmplt")), text(row, "dateInsrvRev"), cars);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String consist(String no, String dash, long dashNo) {
        if (no.isEmpty() && dash.isEmpty()) return "";
        return (no + dash + (dashNo == 0 ? "" : String.format("%02d", dashNo))).strip();
    }

    private static String join(String a, String b) {
        return (a + " " + b).strip();
    }

    // ---- VHIC30IS-SUBMIT: hard copy ------------------------------------------------------------------------

    /** asks MARIS to print the report on a printer (its VTAM name); returns the old confirmation */
    public String print(IcsCriteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer for the hard copy.");
        LocalDate date = checkDate(c.revenueDate());
        Range msg = range(c.messageCode(), "", "99"), loc = range(c.location(), "", "999"), yrd = range(c.yard(), "", "999");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("vtamName", printer.strip());
        crit.put("reportType", reportType(c));
        crit.put("functionCode", reportType(c));
        crit.put("dateFrom", MDY.format(date));
        crit.put("dateTo", MDY.format(date));
        crit.put("yrdTrkgYrd", yrd.from.equals(yrd.to) ? yrd.from : "");
        crit.put("currLoc", loc.from.equals(loc.to) ? loc.from : "");
        crit.put("tranCd", msg.from.equals(msg.to) ? msg.from : "");
        programs.run("VHIC30E", Map.of("VHIC30E_CRIT", crit));
        return submitted;
    }

    // ---- dates ---------------------------------------------------------------------------------------------

    /** a real date, from 2001 on, not in the future (VHIC30IS-SELECT) */
    private LocalDate checkDate(String iso) {
        LocalDate d;
        try {
            d = LocalDate.parse(iso == null ? "" : iso.strip(), ISO);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(badDate);
        }
        if (d.getYear() < 2001 || d.isAfter(LocalDate.now(clock))) throw new InvalidRequestException(badDate);
        return d;
    }

    private static String toIso(String mdy) {
        try {
            return ISO.format(LocalDate.parse(mdy, MDY));
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
