package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Car hours (old VHCHR1I): a car's total revenue hours between two dates (List, program VHCHR1A) and its print (Hard
 * Copy, program VHCHR01). The checks and their messages, the earliest date and the not-found message come from the
 * old screen.
 */
public final class VehicleQueriesCarHoursService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String LIST = "VHCHR1IS-LIST";
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final Programs programs;
    private final Clock clock;
    private final String carNotNumeric, carInvalid, carNotFound, invalidFrom, invalidTo, toAfterToday, fromAfterTo, printed;
    /** VHCHR1A finds nothing: the old screen shows this catalog message with the program's inserts */
    private final String notFoundCode;
    /** the earliest date (yyyy-MM-dd): an earlier from date becomes this one */
    private final String earliest;

    public VehicleQueriesCarHoursService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        carNotNumeric = t.assigned(LIST, PKG, "CAR-MESSAGE", "numeric");
        carInvalid = t.assigned(LIST, PKG, "CAR-MESSAGE", "invalid");
        carNotFound = t.assigned(LIST, PKG, "CAR-MESSAGE", "exist");
        invalidFrom = t.triggeredPrompt("VHCHR1I", "INVALID-DATE-REV-FROM");
        invalidTo = t.triggeredPrompt("VHCHR1I", "INVALID-DATE-REV-TO");
        toAfterToday = t.triggeredPrompt("VHCHR1I", "INVALID-TO-GR-SYS");
        fromAfterTo = t.triggeredPrompt("VHCHR1I", "INVALID-FROM-GR-TO");
        printed = t.triggeredPrompt("VHCHR1I", "SUBMIT").strip();
        notFoundCode = t.assigned(LIST, PKG, "UMSGCODE", null);
        String mdy = t.assigned(LIST, PKG, "DATE-REV-FROM", "-");                  // VHCHR1IW.DATE-REV-FROM = "12-29-1999"
        earliest = mdy.substring(6, 10) + "-" + mdy.substring(0, 2) + "-" + mdy.substring(3, 5);
    }

    /** {@code fromDate}: the date used (an early date becomes the earliest one); {@code message}: when nothing was found */
    public record CarHours(String car, String fromDate, String toDate, String actualFrom, String actualTo, long hours, long minutes, String message) {}

    /** List (VHCHR1IS-LIST) */
    public CarHours list(String car, String fromDate, String toDate) {
        String c = car(car);
        String from = date(fromDate, invalidFrom);
        if (from.compareTo(earliest) < 0) from = earliest;
        String to = date(toDate, invalidTo);
        if (to.compareTo(LocalDate.now(clock).toString()) > 0) throw new InvalidRequestException(toAfterToday);
        if (from.compareTo(to) > 0) throw new InvalidRequestException(fromAfterTo);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateRevFrom", from);
        crit.put("dateRevTo", to);
        crit.put("carNo", c);
        ProgramResult r = programs.attempt("VHCHR1A", Map.of("VHCHR1A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new CarHours(c, from, to, "", "", 0, 0, programs.catalogMessage(notFoundCode, r.control()));
        if (urc != 0 && urc != 4) throw programs.error("VHCHR1A", r.control());
        Map<String, Object> d = r.record("VHCHR1A_DATA");
        return new CarHours(c, from, to, text(d, "dateFrom"), text(d, "dateTo"), number(d, "totalHrs"), number(d, "totalMin"), null);
    }

    /** Hard Copy (VHCHR1IS-SUBMIT-X, then VHCHR01IS-SUBMIT: program VHCHR01); no earliest-date change here */
    public String print(String car, String fromDate, String toDate, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String c = car(car);
        String from = date(fromDate, invalidFrom);
        String to = date(toDate, invalidTo);
        if (to.compareTo(LocalDate.now(clock).toString()) > 0) throw new InvalidRequestException(toAfterToday);
        if (from.compareTo(to) > 0) throw new InvalidRequestException(fromAfterTo);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateRevFrom", slashed(from));
        crit.put("dateRevTo", slashed(to));
        crit.put("carNo", c);
        crit.put("reptType", printer.strip());
        programs.run("VHCHR01", Map.of("VHCHR01_CRIT", crit));
        return printed;
    }

    /** the car checks, then VHINC6A: the car must exist */
    private String car(String car) {
        String c = car == null ? "" : car.strip();
        if (!c.chars().allMatch(Character::isDigit)) throw new InvalidRequestException(carNotNumeric);
        if (c.isEmpty() || Integer.parseInt(c) == 0) throw new InvalidRequestException(carInvalid);
        if (c.length() > 4) throw new InvalidRequestException(carInvalid);
        c = String.format("%04d", Integer.parseInt(c));                          // CAR-NO-NUM moved to CAR-NO
        ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", c))).result();
        if (r.control() != null && r.control().rowCount() == 1) return c;
        if (r.control() != null && r.control().returnCode() == 100) throw new InvalidRequestException(carNotFound);
        throw programs.error("VHINC6A", r.control());
    }

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

    private static String slashed(String iso) {
        return iso.substring(5, 7) + "/" + iso.substring(8, 10) + "/" + iso.substring(0, 4);
    }
}
