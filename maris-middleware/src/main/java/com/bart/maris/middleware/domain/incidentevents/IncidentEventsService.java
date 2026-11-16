package com.bart.maris.middleware.domain.incidentevents;

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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event and incident log (old screens VHIE01I log, VHIE02I its printer selection, VHIE06I consist look-up). Rules from
 * MARIS_RUI/EGLSource/MRSVehicleIncdEvent/RUIFunctions.egl: VHIE01IS-REVTIME, -DATE-CK, -GET-DATA, VHIE02IS-SUBMIT,
 * VHIE06-CAR-NO, VHIE06IS-CON. Read-only except the print request.
 */
public class IncidentEventsService {
    private static final String PKG = "MRSVehicleIncdEvent";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    private final Programs programs;
    private final Clock clock;
    private final String toFuture, fromFuture, fromAfterTo, noData, tooMany, submitted, noConsist, noCars;

    public IncidentEventsService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.toFuture = t.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Future TO");
        this.fromFuture = t.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Future FROM");
        this.fromAfterTo = t.assigned("VHIE01IS-DATE-CK", PKG, "MESSAGE", "Less Than");
        this.noData = t.assigned("VHIE01IS-GET-DATA", PKG, "MESSAGE", "No Data");
        this.tooMany = t.assigned("VHIE01IS-GET-DATA", PKG, "MESSAGE", "Exceeded");
        this.submitted = t.triggeredPrompt("VHIE02I", "SUBMIT");
        this.noConsist = t.assigned("VHIE06-CAR-NO", PKG, "MSG", "No Consit");
        this.noCars = t.assigned("VHIE06IS-CON", PKG, "MSG", "No Cars");
    }

    // ---- VHIE01IS-REVTIME: the revenue date the log starts with ---------------------------------------------

    public record RevenueDate(String revenueDate) {}

    public RevenueDate revenueDate() {
        Map<String, Object> d = programs.run("VHIE01B", Map.of()).record("VHIE01B_DATA");
        return new RevenueDate(text(d, "currentTime").compareTo(text(d, "timeRevStart")) >= 0 ? text(d, "currentDate") : text(d, "yesterday"));
    }

    // ---- VHIE01IS-DATE-CK + VHIE01IS-GET-DATA: the log ------------------------------------------------------

    public record Incident(String revenueDate, String incident, String car, String status, String symptom, String time, String speed,
                           String direction, String location, String consist, String narrative) {}

    /** @param message the old screen's message (no data, too many rows), otherwise null */
    public record IncidentLog(String from, String to, int count, List<Incident> incidents, String message) {}

    public IncidentLog incidents(String from, String to) {
        LocalDate[] range = checkRange(from, to);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("fromDate", ISO.format(range[0]));
        crit.put("toDate", ISO.format(range[1]));
        ProgramResult r = programs.run("VHIE01A", Map.of("VHIE01A_CRIT", crit));
        if (r.control().returnCode() == 100) return new IncidentLog(ISO.format(range[0]), ISO.format(range[1]), 0, List.of(), noData);
        List<Incident> out = new ArrayList<>();
        for (Map<String, Object> d : rows(r, "VHIE01A_DATA", "dataRow")) {
            Object time = d.get("timeIncdOccr");
            out.add(new Incident(text(d, "dateIncdOccrRev"), text(d, "incdNo"), text(d, "carNo"), text(d, "vehIncdStat"), text(d, "sympCd"),
                    time instanceof Map<?, ?> tm ? String.valueOf(tm.get("timeHhmm") == null ? "" : tm.get("timeHhmm")).strip() : "",
                    text(d, "speed"), text(d, "conDirection"), text(d, "incdLoc"), consist(d.get("conNo2")), text(d, "narr")));
        }
        String msg = "Y".equals(text(r.record("VHIE01A_CRIT"), "exceedFlag")) ? tooMany : null;
        return new IncidentLog(ISO.format(range[0]), ISO.format(range[1]), out.size(), out, msg);
    }

    /** the checks of VHIE01IS-DATE-CK, in its order; when several fail the old screen shows the last one */
    private LocalDate[] checkRange(String from, String to) {
        LocalDate f = parse(from), t = parse(to), today = LocalDate.now(clock);
        String msg = null;
        if (t.isAfter(today)) msg = toFuture;
        if (f.isAfter(today)) msg = fromFuture;
        if (t.isBefore(f)) msg = fromAfterTo;
        if (msg != null) throw new InvalidRequestException(msg);
        return new LocalDate[]{f, t};
    }

    private static LocalDate parse(String iso) {
        try {
            return LocalDate.parse(iso == null ? "" : iso.strip(), ISO);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Enter the FROM and TO dates as YYYY-MM-DD.");
        }
    }

    @SuppressWarnings("unchecked")
    private static String consist(Object group) {
        if (!(group instanceof Map<?, ?> m)) return group == null ? "" : group.toString().strip();
        Map<String, Object> g = (Map<String, Object>) m;
        String con = text(g, "con");
        if (con.isEmpty()) return "";
        return con + text(g, "dash") + String.format("%02d", number(g, "conDashNo"));
    }

    // ---- VHIE02IS-SUBMIT: print the log --------------------------------------------------------------------

    public record Printer(String description, String vtam) {}

    public List<Printer> printers() {
        List<Printer> out = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("WS70A01", Map.of()), "WS70A01_DATA", "dataRow")) {
            out.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        }
        return out;
    }

    /** the log's printed report (program VHIE02A); the dates go as the old screen shows them, MM-DD-YYYY */
    public String print(String from, String to, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        LocalDate[] range = checkRange(from, to);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", "INCLOG");                                       // VHIE01IS-GET-DATA: WHICH-REPORT = "INCLOG"
        crit.put("reptCd", printer.strip());
        crit.put("dateReptFrom", MDY.format(range[0]));
        crit.put("dateReptTo", MDY.format(range[1]));
        programs.run("VHIE02A", Map.of("VHIE02A_CRIT", crit));
        return submitted;
    }

    // ---- VHIE06I: the consist of a car, the cars of a consist -----------------------------------------------

    /** @param message the old screen's message when nothing is found, otherwise null */
    public record ConsistOfCar(String car, String consist, String con, String message) {}

    public record ConsistCars(String consist, List<String> cars, String message) {}

    /** VHIE06-CAR-NO: a car number under 1000 is zero-padded to 4 digits; program VHIE03H */
    public ConsistOfCar consistOfCar(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || c.length() > 4) throw new InvalidRequestException("Enter a car number of up to 4 characters.");
        if (c.matches("\\d+") && Integer.parseInt(c) < 1000) c = String.format("%04d", Integer.parseInt(c));
        ProgramResult r = programs.attempt("VHIE03H", Map.of("VHIE03H_DATA", Map.of("carNo", c))).result();
        Map<String, Object> d = r.record("VHIE03H_DATA");
        String con = text(d, "conNo");
        if (con.isEmpty()) return new ConsistOfCar(c, "", "", noConsist);
        String dash = dashNo2(d);
        if (dash.length() == 1) dash = "0" + dash;
        return new ConsistOfCar(c, con + "-" + dash, con, null);
    }

    /** DASH-NO-2: the last two characters of DASH-NO (num(4), split into DASH-NO-1 and DASH-NO-2) */
    @SuppressWarnings("unchecked")
    private static String dashNo2(Map<String, Object> rec) {
        Object v = rec.get("dashNo");
        return v instanceof Map<?, ?> m ? text((Map<String, Object>) m, "dashNo2") : "";
    }

    /** VHIE06IS-CON: the cars of a consist ("532" or "532-01"), in train order; program VHIE06A */
    public ConsistCars consistCars(String consist) {
        String c = consist == null ? "" : consist.strip();
        if (c.isEmpty()) throw new InvalidRequestException("Enter a consist number.");
        String con = c.length() > 3 ? c.substring(0, 3) : c;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", con);
        crit.put("flag", "1");
        Programs.Outcome o = programs.attempt("VHIE06A", Map.of("VHIE06A_CRIT", crit));
        if (!o.ok()) return new ConsistCars(con, List.of(), noCars);
        List<Map<String, Object>> rows = rows(o.result(), "VHIE06A_DATA", "dataRow");
        List<String> cars = new ArrayList<>();
        for (Map<String, Object> r : rows.subList(0, Math.min(10, rows.size()))) {                  // the old screen shows 10
            Object f = r.get("carFormat");
            // CAR-FORMAT = left orientation + car + right orientation; a blank orientation stays (" 3198}"), as the old screen shows it
            String car = f instanceof Map<?, ?> fm ? String.join("", fm.values().stream().map(v -> v == null ? " " : v.toString().isEmpty() ? " " : v.toString()).toList())
                    : f == null ? "" : f.toString();
            cars.add(car.stripTrailing());
        }
        String dash = rows.isEmpty() ? "" : dashNo2(rows.get(0));
        if (dash.length() == 1) dash = "0" + dash;
        return new ConsistCars(con + "-" + dash, cars, null);
    }
}
