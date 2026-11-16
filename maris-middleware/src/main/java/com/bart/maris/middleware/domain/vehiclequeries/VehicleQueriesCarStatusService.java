package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Car status history (old VHCSH1I) and its print (VHCSH1I Print, then the printer window VHCSH2I). The checks, their
 * messages (the screen's message boxes), the earliest date and the row limit all come from the old screens.
 */
public final class VehicleQueriesCarStatusService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String LIST = "VHCSH1IS-RET-DATA";
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final Programs programs;
    private final Clock clock;
    private final String invalidCar, carNotFound, invalidFrom, invalidTo, fromAfterToday, toAfterToday, toBeforeFrom, noRows, submitted;
    /** the earliest status date (yyyy-MM-dd): an earlier from date becomes this one */
    private final String earliest;
    private final int rowLimit;
    private final String footer;

    public VehicleQueriesCarStatusService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        invalidCar = t.triggeredPrompt("VHCSH1I", "INVALID-CARNO");
        carNotFound = t.triggeredPrompt("VHCSH1I", "CAR-NOT-FOUND");
        invalidFrom = t.triggeredPrompt("VHCSH1I", "INVALID-FROM-DATE");
        invalidTo = t.triggeredPrompt("VHCSH1I", "INVALID-TO-DATE");
        fromAfterToday = t.triggeredPrompt("VHCSH1I", "FROM-GR-SYS");
        toAfterToday = t.triggeredPrompt("VHCSH1I", "TO-GR-SYS");
        toBeforeFrom = t.triggeredPrompt("VHCSH1I", "FROM-LESS-TO");
        noRows = t.triggeredPrompt("VHCSH1I", "NO-REC-FOUND");
        submitted = t.triggeredPrompt("VHCSH2I", "SUBMIT").strip();
        String mdy = t.assigned(LIST, PKG, "FROM-DATE", "-");                       // VHCSH1IW.FROM-DATE = "01-14-2000"
        earliest = mdy.substring(6, 10) + "-" + mdy.substring(0, 2) + "-" + mdy.substring(3, 5);
        rowLimit = Integer.parseInt(t.compared(LIST, PKG, "CAR-COUNT").get(0));
        footer = t.label("VHCSH1I", "lblFooter");
    }

    public record StatusChange(String location, String date, String time, long hours, long minutes, String statusNew, String statusPrevious,
                               String incident, String symptom, String action, String dvsImpact, String lastUpdateDate, String lastUpdateTime,
                               String lastUpdateTerminal, String maintUpdateDate, String maintUpdateTime, String maintUpdateTerminal,
                               String function) {}

    /** {@code fromDate}: the date used (an early date becomes the earliest one); {@code footer}: shown when the list is cut */
    public record History(String car, String homeYard, String fromDate, String toDate, List<StatusChange> changes, String footer, String message) {}

    /** List (VHCSH1IS-RET-DATA) */
    public History list(String car, String fromDate, String toDate) {
        String c = car(car);
        Programs.Outcome found = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", c)));
        if (found.result().control() == null || found.result().control().rowCount() != 1) {
            if (found.result().control() != null && found.result().control().returnCode() == 100) throw new InvalidRequestException(carNotFound);
            throw programs.error("VHINC6A", found.result().control());
        }
        String from = date(fromDate, invalidFrom);
        if (from.compareTo(earliest) < 0) from = earliest;
        else if (from.compareTo(today()) > 0) throw new InvalidRequestException(fromAfterToday);
        String to = date(toDate, invalidTo);
        if (to.compareTo(today()) > 0) throw new InvalidRequestException(toAfterToday);
        if (to.compareTo(from) < 0) throw new InvalidRequestException(toBeforeFrom);

        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", c);
        crit.put("fromDate", dashed(from));
        crit.put("toDate", dashed(to));
        ProgramResult r = programs.run("VHCSH1A", Map.of("VHCSH1A_CRIT", crit));
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new History(c, "", from, to, List.of(), null, noRows);
        List<StatusChange> changes = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHCSH1A_DATA", "dataRow")) {
            changes.add(new StatusChange(text(row, "carLoc"), text(row, "dateStatChg"), text(row, "timeStatChg"), number(row, "cumHrs"),
                    number(row, "cumMin"), text(row, "vehStatNew"), text(row, "vehStatPrev"), text(row, "incdNo"), text(row, "sympCd"),
                    text(row, "statChgActn"), text(row, "dvsImpactInd"), text(row, "dateLastUpd"), text(row, "timeLastUpd"),
                    text(row, "termLastUpd"), text(row, "dateMaintUpd"), text(row, "timeMaintUpd"), text(row, "termMaintUpd"), text(row, "marisFunc")));
        }
        int count = r.control() == null ? changes.size() : r.control().rowCount();
        Map<String, Object> data = r.record("VHCSH1A_DATA");
        return new History(c, data == null ? "" : text(data, "homeYrd"), from, to, changes, count == rowLimit ? footer : null, null);
    }

    /** Print (VHCSH1IS-PRINT) and the printer window's Hard Copy (VHCSH2IS-SUBMIT, program VHCSH2A) */
    public String print(String car, String fromDate, String toDate, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String c = car(car);
        String from = date(fromDate, invalidFrom);
        if (from.compareTo(today()) > 0) throw new InvalidRequestException(fromAfterToday);
        String to = date(toDate, invalidTo);
        if (to.compareTo(today()) > 0) throw new InvalidRequestException(toAfterToday);
        if (to.compareTo(from) < 0) throw new InvalidRequestException(toBeforeFrom);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", printer.strip());
        crit.put("dateFrom", slashed(from));
        crit.put("dateTo", slashed(to));
        crit.put("carNo", c);
        programs.run("VHCSH2A", Map.of("VHCSH2A_CRIT", crit));
        return submitted;
    }

    private String car(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || !c.chars().allMatch(Character::isDigit)) throw new InvalidRequestException(invalidCar);
        return c;
    }

    /** a date the old screen accepts: given, and correct for AFCXVLD; an SQL error there gives the backend's message */
    private String date(String d, String invalid) {
        String iso = d == null ? "" : d.strip();
        if (iso.isEmpty() || !ISO.matcher(iso).matches()) throw new InvalidRequestException(invalid);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", iso);
        crit.put("otherSqlError", "N");
        ProgramResult r = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result();
        Map<String, Object> back = r.record("AFCXVLD_CRIT");
        String dateError = back == null ? "N" : text(back, "dateError"), sql = back == null ? "N" : text(back, "otherSqlError");
        if (dateError.equals("Y") && sql.equals("N")) throw new InvalidRequestException(invalid);
        if (dateError.equals("N") && sql.equals("Y")) throw programs.error("AFCXVLD", r.control());
        return iso;
    }

    private String today() {
        return LocalDate.now(clock).toString();
    }

    private static String dashed(String iso) {
        return iso.substring(5, 7) + "-" + iso.substring(8, 10) + "-" + iso.substring(0, 4);
    }

    private static String slashed(String iso) {
        return iso.substring(5, 7) + "/" + iso.substring(8, 10) + "/" + iso.substring(0, 4);
    }
}
