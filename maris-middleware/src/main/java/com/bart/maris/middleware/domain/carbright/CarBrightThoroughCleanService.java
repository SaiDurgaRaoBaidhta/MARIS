package com.bart.maris.middleware.domain.carbright;

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

/**
 * Add car thorough cleanings (old VHCBP1I). Rules from MRSVehicleCarBright RUIFunctions: VHCBP1I-REV-DATE2 (the
 * opening date and time, VHIE01B), VHCBP1IS-ADD (checks, VHCBR2C per car), VHCBP1IS-MOVE-MSG (how the messages are
 * joined), VHCBP1IS-MOVE-VAL (what the update VHCBP1A gets). Every text and list is read from the screen.
 *
 * <p>Kept as the old screen does it: the "older than 30 days" check never fires (the screen sets DATE-IS-VALID to "Y"
 * itself and never calls VHCBP1B); only the first wrong car is reported.
 */
public final class CarBrightThoroughCleanService {
    private static final String PKG = "MRSVehicleCarBright";
    private static final String ADD = "VHCBP1IS-ADD";
    private static final int CARS = 20, MESSAGES = 4;

    private final Programs programs;
    private final Clock clock;
    private final String terminal;
    private final List<String> yards, validYards, badTimes, carErr3;
    private final String defaultYard, yardBlank, yardInvalid, dateBlank, dateFuture, timeBlank, timeInvalid, firstCarBlank, duplicateCars,
            carErr1, added, duplicate, notAdded, separator, seconds;

    public CarBrightThoroughCleanService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.terminal = terminal;
        yards = t.items("VHCBP1I", "Drop-down_List1");
        validYards = t.compared(ADD, PKG, "BRIT-LOC");
        badTimes = t.compared(ADD, PKG, "SCR-TIME-CAR-BRIT");
        defaultYard = t.assignedAll("VHCBP1IS-INIT", PKG, "BRIT-LOC").get(0);
        yardBlank = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Yard cannot");
        yardInvalid = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Invalid Yard");
        dateBlank = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Date cannot be blank");
        dateFuture = t.assigned(ADD, PKG, "ERROR-MESSAGE", "greater than");
        timeBlank = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Time cannot");
        timeInvalid = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Invalid Time");
        firstCarBlank = t.assigned(ADD, PKG, "ERROR-MESSAGE", "First car");
        duplicateCars = t.assigned(ADD, PKG, "ERROR-MESSAGE", "Duplicate car");
        carErr1 = t.assignedRaw(ADD, PKG, "CAR-ERR-1").get(0);
        carErr3 = t.assignedRaw(ADD, PKG, "CAR-ERR-3");
        added = t.assigned(ADD, PKG, "FINAL-ERROR-MSG", "added successfully");
        duplicate = t.assigned(ADD, PKG, "FINAL-ERROR-MSG", "Duplicate");
        notAdded = t.assigned(ADD, PKG, "FINAL-ERROR-MSG", "not added");
        separator = t.assignedAll("VHCBP1IS-MOVE-VAL", PKG, "SEPERATOR").get(0);
        seconds = t.assignedRaw("VHCBP1IS-MOVE-VAL", PKG, "TIME-CAR-BRIT-FILLER2").get(0);
    }

    /** @param date the revenue date MM-DD-YYYY; @param time HH.MM, the backend's current time */
    public record Defaults(List<String> yards, String yard, String date, String time, int maxCars) {}

    /** VHCBP1IS-INIT + VHCBP1I-REV-DATE2: today's revenue date (yesterday before the revenue day starts) and the time */
    public Defaults defaults() {
        Programs.Outcome o = programs.attempt("VHIE01B", Map.of());
        Map<String, Object> d = o.result().record("VHIE01B_DATA");
        String time = text(d, "currentTime");
        String rev = time.compareTo(text(d, "timeRevStart")) >= 0 ? text(d, "currentDate") : text(d, "yesterday");
        return new Defaults(yards, defaultYard, mdy(rev), time.length() > 5 ? time.substring(0, 5) : time, CARS);
    }

    public record Added(String message, int cars) {}

    /** VHCBP1IS-ADD. @param date MM-DD-YYYY; @param time HH:MM (or HH.MM); @param cars up to 20, blanks skipped */
    public Added add(String yard, String date, String time, List<String> cars) {
        String y = s(yard), dt = s(date), tm = s(time);
        List<String> boxes = new ArrayList<>();
        for (int i = 0; i < CARS; i++) boxes.add(cars != null && i < cars.size() ? fit(s(cars.get(i)), 4) : "");
        List<String> errors = new ArrayList<>();
        if (y.isEmpty()) errors.add(yardBlank);
        else if (!validYards.contains(y)) throw new InvalidRequestException(yardInvalid);       // the old screen stops here without a message
        if (dt.isEmpty()) errors.add(dateBlank);
        if (future(dt)) errors.add(dateFuture);
        String screenTime = String.format("%-8s", fit(tm, 8));
        if (tm.isEmpty()) errors.add(timeBlank);
        else if (screenTime.equals(String.format("%-8s", badTimes.get(0))) || screenTime.compareTo(String.format("%-8s", badTimes.get(1))) > 0) errors.add(timeInvalid);
        if (boxes.get(0).isEmpty()) errors.add(firstCarBlank);
        else if (duplicates(boxes)) errors.add(duplicateCars);
        if (!errors.isEmpty()) throw new InvalidRequestException(joined(errors));

        Map<String, Object> crit = new LinkedHashMap<>();       // the old screen reuses the record: the last answer's car type goes along
        for (String car : boxes) {
            if (car.isEmpty()) continue;
            if (car.length() != 4 || !car.chars().allMatch(Character::isDigit)) throw new InvalidRequestException(carError(car, 0));
            crit.put("carNo", car);
            ProgramResult r = programs.attempt("VHCBR2C", Map.of("VHCBR2C_CRIT", new LinkedHashMap<>(crit))).result();
            Map<String, Object> back = r.record("VHCBR2C_CRIT");
            if (back != null) crit.put("carType", text(back, "carType"));
            if (r.control() != null && r.control().rowCount() == 1) continue;
            if (r.control() != null && r.control().returnCode() == 100) throw new InvalidRequestException(carError(car, 1));
            throw programs.error("VHCBR2C", r.control());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("britLoc", y);
        data.put("dateCarBrit", ymd(dt));
        String hhmm = String.format("%-5s", fit(tm, 5));
        data.put("timeCarBrit", Map.of("timeCarHhmm", Map.of("hh", hhmm.substring(0, 2), "seperator", separator, "mm", hhmm.substring(3, 5)),
                "timeCarBritFiller2", seconds));
        data.put("termLastUpd", terminal);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String car : boxes) if (!car.isEmpty()) rows.add(Map.of("carNo", car));
        data.put("dataRow", rows);
        data.put("numOfCars", rows.size());
        ProgramResult r = programs.attempt("VHCBP1A", Map.of("VHCBP1A_DATA", data)).result();
        String found = text(r.record("VHCBP1A_DATA"), "recordFound");
        if (found.equals("N")) return new Added(added, rows.size());
        if (found.equals("Y")) throw new InvalidRequestException(duplicate);
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHCBP1A", r.control());
        throw new InvalidRequestException(notAdded);
    }

    /** VHCBP1IS-MOVE-MSG: each message up to its period, then ", ", up to four; the last comma dropped */
    static String joined(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(MESSAGES, errors.size()); i++) {
            String m = errors.get(i);
            int dot = m.indexOf('.');
            sb.append(dot < 0 ? String.format("%-50s", m) : m.substring(0, dot) + ", ");
        }
        String out = sb.toString().stripTrailing();
        return out.endsWith(",") ? out.substring(0, out.length() - 1) : out;
    }

    /** CAR-ERROR-MESSAGE = CAR-ERR-1 (16) + CAR-ERR-2 (4) + CAR-ERR-3 (35): a longer text is cut, as on the old screen */
    private String carError(String car, int which) {
        return (fit(String.format("%-16s", carErr1), 16) + String.format("%-4s", car) + fit(carErr3.get(which), 35)).strip();
    }

    private boolean future(String date) {
        String d = String.format("%-10s", date);
        int mon = num(d.substring(0, 2)), day = num(d.substring(3, 5)), year = num(d.substring(6, 10));
        LocalDate today = LocalDate.now(clock);
        if (year != today.getYear()) return year > today.getYear();
        if (mon != today.getMonthValue()) return mon > today.getMonthValue();
        return day > today.getDayOfMonth();
    }

    private static boolean duplicates(List<String> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = 0; j < boxes.size(); j++) if (i != j && !boxes.get(j).isEmpty() && boxes.get(j).equals(boxes.get(i))) return true;
        }
        return false;
    }

    /** XXDATES-CONVERT "YMD": MM-DD-YYYY to YYYY-MM-DD when the year is numeric, otherwise unchanged */
    static String ymd(String mdy) {
        String d = String.format("%-10s", mdy);
        String year = d.substring(6, 10);
        return year.chars().allMatch(Character::isDigit) ? year + "-" + d.substring(0, 2) + "-" + d.substring(3, 5) : mdy;
    }

    /** XXDATES-CONVERT "MDY": YYYY-MM-DD to MM-DD-YYYY when the year is numeric, otherwise unchanged */
    static String mdy(String ymd) {
        String d = String.format("%-10s", ymd);
        String year = d.substring(0, 4);
        return year.chars().allMatch(Character::isDigit) ? d.substring(5, 7) + "-" + d.substring(8, 10) + "-" + year : ymd;
    }

    private static int num(String s) {
        String t = s.strip();
        return !t.isEmpty() && t.chars().allMatch(Character::isDigit) ? Integer.parseInt(t) : 0;
    }

    private static String fit(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
