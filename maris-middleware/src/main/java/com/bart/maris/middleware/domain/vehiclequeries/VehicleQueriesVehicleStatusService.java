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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Vehicle status (old VHSV01I criteria, VHSV02I incident report). The checks follow VHSV01IS-DISPLAY / SUBMIT-X, the
 * report VHSV02IS-RETDATA, PRVNXT and MOVE-CARS. VHSV02A takes a WHERE clause built by the screen (VHSV02AS-BUILD-SQL);
 * this service builds the same record from the screen's own SQL texts and only from values its checks accepted.
 */
public final class VehicleQueriesVehicleStatusService {
    private static final String PKG = "MRSVehicleQueries";
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private final Programs programs;
    private final Clock clock;
    /** VHSV01IS-DISPLAY's CAR-MESSAGE texts in order (see {@link #check}); VHSV04IS-DISPLAY's (history) */
    private final List<String> m, h;
    private final List<String> historyTitles;
    private final String historyName, historySubmitted;
    private final List<String> reportNames, defChoices, reports, titles, sql8;
    private final Map<String, String> sql = new LinkedHashMap<>();
    private final String orientLeft, submitted;

    public VehicleQueriesVehicleStatusService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        m = t.assignedAll("VHSV01IS-DISPLAY", PKG, "CAR-MESSAGE");
        if (m.size() != 11) throw new IllegalStateException("VHSV01IS-DISPLAY checks changed: " + m);
        h = t.assignedAll("VHSV04IS-DISPLAY", PKG, "CAR-MESSAGE");
        if (h.size() != 10) throw new IllegalStateException("VHSV04IS-DISPLAY checks changed: " + h);
        historyName = t.assigned("VHSV05IS-RETDATA", PKG, "TITLE-1", null);
        // " for date range from ", " to ", "Current ", "   for date >= ", ", symp cd ", " to "
        historyTitles = List.of(t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-2").get(1), t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-4").get(0),
                t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-5").get(0), t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-2").get(0),
                t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-5A-1").get(0), t.assignedRaw("VHSV05IS-RETDATA", PKG, "TITLE-5B-1").get(0));
        historySubmitted = t.triggeredPrompt("VHSV04I", "SUBMIT").strip();
        reports = t.items("VHSV01I", "rbtReportSel");
        defChoices = t.items("VHSV01I", "cmbDefitems");
        reportNames = t.assignedAll("VHSV02IS-RETDATA", PKG, "TITLE-1");
        titles = List.of(t.assignedRaw("VHSV02IS-RETDATA", PKG, "TITLE-2-1").get(0), t.assignedRaw("VHSV02IS-RETDATA", PKG, "TITLE-3-1").get(0),
                t.assignedRaw("VHSV02IS-RETDATA", PKG, "TITLE-4-1").get(0), t.assignedRaw("VHSV02IS-RETDATA", PKG, "TITLE-5-1").get(0),
                t.assignedRaw("VHSV02IS-RETDATA", PKG, "TITLE-6-1").get(0));
        for (String f : List.of("WHERE-1", "WHERE-3", "WHERE-4-1", "WHERE-4-3", "WHERE-5-1", "WHERE-5-3", "WHERE-6-1", "WHERE-6-3", "WHERE-7-1",
                "WHERE-7-3", "WHERE-9-1", "WHERE-9-3", "WHERE-10")) {
            sql.put(f, t.assignedRaw("VHSV02AS-BUILD-SQL", PKG, f).get(0));
        }
        sql8 = t.assignedRaw("VHSV02AS-BUILD-SQL", PKG, "WHERE-8");     // DEF, not closed (report 1), DEF, open (report 2)
        orientLeft = orientations(t);
        submitted = t.triggeredPrompt("VHSV01I", "SUBMIT").strip();
    }

    /** VHSV02IS-MOVE-CARS: the orientation marks shown before the car number (strLib.indexOf("<O({vblm", ...)) */
    private static String orientations(EglTexts t) {
        return t.callText("VHSV02IS-MOVE-CARS", PKG, "indexOf");
    }

    public record Printer(String description, String vtam) {}

    public record Options(List<String> reports, List<String> deferredItems, String fromDate, String toDate, List<Printer> printers) {}

    /** VHSV01IS-INIT (VHSV011: the start from date), VHSV01IS-DATE (today), the printers */
    public Options options() {
        String from = "";
        Programs.Outcome o = programs.attempt("VHSV011", Map.of());
        if (o.ok() && o.result().record("VHSV011_DATA") != null) from = text(o.result().record("VHSV011_DATA"), "fromDate");
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return new Options(reports, defChoices.stream().filter(x -> !x.isBlank()).toList(), from, LocalDate.now(clock).toString(), printers);
    }

    /** report: 1 open / repaired (also when not given), 2 open / deferred; dates yyyy-MM-dd or empty */
    public record Criteria(Integer report, String car, String fromDate, String toDate, String sympFrom, String sympTo, String deferredItems) {
        public Criteria {
            if (report == null) report = 1;
        }
    }

    public record Checked(int report, String car, String fromDate, String toDate, String sympFrom, String sympTo, String deferredItems) {}

    /** VHSV01IS-DISPLAY (and SUBMIT-X): the first failed check's message, or the checked values */
    public Checked check(Criteria c) {
        return check(c, m);
    }

    /** the checks VHSV01I and VHSV04I share, with the screen's own texts; only VHSV01I has the deferred items */
    private Checked check(Criteria c, List<String> m) {
        String car = c.car() == null ? "" : c.car().strip();
        if (car.isEmpty() || car.chars().allMatch(ch -> ch == '0')) throw new InvalidRequestException(m.get(0));
        if (!car.chars().allMatch(Character::isDigit) || car.length() > 4) throw new InvalidRequestException(m.get(1));
        car = String.format("%04d", Integer.parseInt(car));
        ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", car))).result();
        if (r.control() == null || r.control().rowCount() != 1) {
            if (r.control() != null && r.control().returnCode() == 100) throw new InvalidRequestException(m.get(2));
            throw programs.error("VHINC6A", r.control());
        }
        String from = blankOr(c.fromDate()), to = blankOr(c.toDate());
        if (!from.isEmpty() && !validDate(from)) throw new InvalidRequestException(m.get(3));
        if (!to.isEmpty() && !validDate(to)) throw new InvalidRequestException(m.get(4));
        if (!to.isEmpty() && to.compareTo(LocalDate.now(clock).toString()) > 0) throw new InvalidRequestException(m.get(5));
        if (!from.isEmpty() && !to.isEmpty() && from.compareTo(to) > 0) throw new InvalidRequestException(m.get(6));
        String sf = blankOr(c.sympFrom()), st = blankOr(c.sympTo());
        if (!sf.isEmpty() && !st.isEmpty() && digits(sf) && digits(st) && Integer.parseInt(st) < Integer.parseInt(sf)) throw new InvalidRequestException(m.get(7));
        if (!sf.isEmpty() && !digits(sf)) throw new InvalidRequestException(m.get(8));
        if (st.isEmpty()) st = sf;
        if (!st.isEmpty() && !digits(st)) throw new InvalidRequestException(m.get(9));
        String given = blankOr(c.deferredItems());
        if (m.size() > 10 && c.report() == 2 && !given.isEmpty() && defChoices.stream().noneMatch(x -> !x.isBlank() && x.equalsIgnoreCase(given))) throw new InvalidRequestException(m.get(10));
        String def = c.report() == 2 ? given : "";
        return new Checked(c.report() == 2 ? 2 : 1, car, from, to, sf, st, def);
    }

    private static String blankOr(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean digits(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /** AFCXVLD: is the date a real date */
    private boolean validDate(String iso) {
        if (!ISO.matcher(iso).matches()) return false;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", iso);
        crit.put("otherSqlError", "N");
        ProgramResult r = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result();
        Map<String, Object> back = r.record("AFCXVLD_CRIT");
        String dateError = back == null ? "N" : text(back, "dateError"), other = back == null ? "N" : text(back, "otherSqlError");
        if (dateError.equals("N") && other.equals("Y")) throw programs.error("AFCXVLD", r.control());
        return !(dateError.equals("Y") && other.equals("N"));
    }

    // ---- VHSV02I -------------------------------------------------------------------------------------------

    public record CarDetails(String car, String status, String location, String homeYard, String ato, long mtbi, String warranty,
                             String currentPm, String nextPm, long hoursFromLastPm, long timesWashed, long washOverdueDays, String camera,
                             long cumulativeHours, String lastWash, boolean softwareMarked) {}

    public record Incident(String incident, String date, String time, String symptom, String location, String direction, String status,
                           long hours, String deferredPriority, String consist, int dash, String dateInServiceRev, String dateInService,
                           String timeInService) {}

    public record Report(Checked criteria, String title, CarDetails car, List<Incident> incidents) {}

    /** VHSV02IS-RETDATA (program VHSV02A) */
    public Report report(Criteria c) {
        Checked k = check(c);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", k.car());
        crit.put("reportIndex", k.report());
        crit.put("sympCdFrom", k.sympFrom());
        crit.put("sympCdTo", k.sympTo());
        crit.put("incdDateFrom", k.fromDate());
        crit.put("incdDateTo", k.toDate());
        crit.put("defItems", k.deferredItems());
        ProgramResult r = programs.attempt("VHSV02A", Map.of("VHSV02A_CRIT", crit, "VHSV02AW1", Map.of("whereClause", where(k)))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String title = title(k);
        if (urc != 0 && urc != 4) return new Report(k, title, null, List.of());
        CarDetails car = details(k.car(), r.record("VHSV02A_DATA_01"));
        int count = (int) number(r.record("VHSV02A_CRIT"), "rowCount");
        List<Incident> out = new ArrayList<>();
        List<Map<String, Object>> rows = rows(r, "VHSV02A_DATA_03", "dataRow");
        for (int i = 0; i < Math.min(count, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            out.add(new Incident(text(row, "incdNo"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"), text(row, "sympCd"), text(row, "incdLoc"),
                    text(row, "conDirection"), text(row, "vehIncdStat"), number(row, "cumHrs"), text(row, "deferredPriority"), text(row, "incdConNo"),
                    (int) number(row, "incdDashNo"), text(row, "dateInsrvRev"), text(row, "dateInsrv"), text(row, "timeInsrv")));
        }
        return new Report(k, title, car, out);
    }

    /** VHSV02IS-DATE-DIFF: days the next wash is overdue (0 when not due yet or unknown) */
    private long washOverdue(String next) {
        if (next.isBlank()) return 0;
        LocalDate due;
        try {
            due = next.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(next) : LocalDate.parse(next.replace('/', '-'), MDY);
        } catch (RuntimeException e) {
            return 0;
        }
        long diff = ChronoUnit.DAYS.between(LocalDate.now(clock), due);
        return diff >= 0 ? 0 : -diff;
    }

    /** VHSV02IS-RETDATA's title: the report's name, then the criteria given */
    private String title(Checked k) {
        StringBuilder s = new StringBuilder(reportNames.get(k.report() - 1));
        if (!k.fromDate().isEmpty()) s.append(titles.get(0)).append(MDY.format(LocalDate.parse(k.fromDate())));
        if (!k.toDate().isEmpty()) s.append(titles.get(1)).append(MDY.format(LocalDate.parse(k.toDate())));
        if (!k.sympFrom().isEmpty()) s.append(titles.get(2)).append(k.sympFrom());
        if (!k.sympTo().isEmpty()) s.append(titles.get(3)).append(k.sympTo());
        if (!k.deferredItems().isEmpty()) s.append(titles.get(4)).append(k.deferredItems());
        return s.toString();
    }

    /**
     * VHSV02AS-BUILD-SQL: the WHERE clause record, field by field at their lengths (VHSV02AW1). Every value in it
     * passed {@link #check}: a 4-digit car, numeric symptom codes, yyyy-MM-dd dates, a listed deferred item.
     */
    private String where(Checked k) {
        StringBuilder w = new StringBuilder();
        w.append(pad(sql.get("WHERE-1"), 20)).append(pad(k.car(), 4)).append(pad(sql.get("WHERE-3"), 28));
        w.append(k.sympFrom().isEmpty() ? pad("", 25) : pad(sql.get("WHERE-4-1"), 20) + pad(k.sympFrom(), 4) + pad(sql.get("WHERE-4-3"), 1));
        w.append(k.sympTo().isEmpty() ? pad("", 25) : pad(sql.get("WHERE-5-1"), 20) + pad(k.sympTo(), 4) + pad(sql.get("WHERE-5-3"), 1));
        w.append(k.fromDate().isEmpty() ? pad("", 64) : pad(sql.get("WHERE-6-1"), 53) + pad(k.fromDate(), 10) + pad(sql.get("WHERE-6-3"), 1));
        w.append(k.toDate().isEmpty() ? pad("", 64) : pad(sql.get("WHERE-7-1"), 53) + pad(k.toDate(), 10) + pad(sql.get("WHERE-7-3"), 1));
        boolean def = !k.deferredItems().isEmpty();
        w.append(pad(def ? sql8.get(0) : k.report() == 1 ? sql8.get(1) : sql8.get(3), 55));
        String all = defChoices.stream().filter(x -> x.equalsIgnoreCase("ALL")).findFirst().orElse("ALL");
        if (def && !k.deferredItems().equals(all)) w.append(pad(sql.get("WHERE-9-1"), 28)).append(pad(k.deferredItems(), 1)).append(pad(sql.get("WHERE-9-3"), 1));
        else w.append(pad("", 30));
        w.append(pad(sql.get("WHERE-10"), 25)).append(pad("", 29 + 24));
        return w.toString();
    }

    private static String pad(String s, int n) {
        String v = s == null ? "" : s;
        return v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length());
    }

    /** VHSV02IS-PRVNXT (program VHNARX): an incident's narrative lines */
    public List<String> narrative(String incident) {
        String i = blankOr(incident);
        if (!digits(i) || i.length() > 8) throw new InvalidRequestException("An incident number is up to 8 digits.");
        ProgramResult r = programs.attempt("VHNARX", Map.of("VHNARX_DATA", Map.of("critIncdNo", i))).result();
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHNARX_DATA", "dataRow")) lines.add(text(row, "narr"));
        return lines;
    }

    public record Consist(String consist, List<String> cars) {}

    /** VHSV02IS-PRVNXT / MOVE-CARS (program VHSV02C): the consist the incident's car ran in, cars shown with orientation */
    public Consist consist(String conNo, int dash, String dateInServiceRev, String dateInService, String timeInService) {
        String con = blankOr(conNo);
        if (con.isEmpty() || dash <= 0) return new Consist("", List.of());
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", con);
        crit.put("dashNo", dash);
        crit.put("dateInsrv", blankOr(dateInService));
        crit.put("dateInsrvRev", blankOr(dateInServiceRev));
        crit.put("timeInsrv", blankOr(timeInService));
        ProgramResult r = programs.attempt("VHSV02C", Map.of("VHSV02C_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        List<String> cars = new ArrayList<>();
        if (urc == 0 || urc == 4) {
            List<Map<String, Object>> rows = rows(r, "VHSV02C_DATA", "dataRow");
            for (int i = 0; i < Math.min(10, rows.size()); i++) {
                String car = text(rows.get(i), "carNo"), o = text(rows.get(i), "orientation");
                boolean left = !o.isEmpty() && orientLeft.contains(o);          // strLib.indexOf counts from 1: > 0 means found
                cars.add(left ? o + car : car + o);
            }
        }
        return new Consist(con + "-" + String.format("%02d", dash), cars);
    }

    // ---- VHSV04I / VHSV05I: history ---------------------------------------------------------------------

    public record HistoryIncident(String incident, String date, String time, String symptom, String location, String status, String direction,
                                  String deferredPriority, long hours, String pmTeam) {}

    public record History(Checked criteria, String title, CarDetails car, List<HistoryIncident> incidents) {}

    /** VHSV04IS-DISPLAY, then VHSV05IS-RETDATA (program VHSV05A; the dates go as the screen shows them, MM-dd-yyyy) */
    public History history(Criteria c) {
        Checked k = check(new Criteria(0, c.car(), c.fromDate(), c.toDate(), c.sympFrom(), c.sympTo(), ""), h);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", k.car());
        crit.put("sympCdFrom", k.sympFrom());
        crit.put("sympCdTo", k.sympTo());
        crit.put("incdDateFrom", k.fromDate().isEmpty() ? "" : MDY.format(LocalDate.parse(k.fromDate())));
        crit.put("incdDateTo", k.toDate().isEmpty() ? "" : MDY.format(LocalDate.parse(k.toDate())));
        ProgramResult r = programs.attempt("VHSV05A", Map.of("VHSV05A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String title = historyTitle(k);
        if (urc != 0 && urc != 4) return new History(k, title, null, List.of());
        CarDetails car = details(k.car(), r.record("VHSV05A_DATA_01"));
        int count = (int) number(r.record("VHSV05A_CRIT"), "rowCount");
        List<HistoryIncident> out = new ArrayList<>();
        List<Map<String, Object>> rows = rows(r, "VHSV05A_DATA_03", "dataRow");
        for (int i = 0; i < Math.min(count, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            out.add(new HistoryIncident(text(row, "incdNo"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"), text(row, "sympCd"), text(row, "incdLoc"),
                    text(row, "vehIncdStat"), text(row, "conDirection"), text(row, "deferredPriority"), number(row, "cumHrs"), text(row, "pmTeam")));
        }
        return new History(k, title, car, out);
    }

    private CarDetails details(String car, Map<String, Object> d) {
        if (d == null) return null;
        @SuppressWarnings("unchecked") Map<String, Object> st = d.get("dispVehStat") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
        Object filler = st.get("vehStatFiller");
        String status = String.format("%-6s%s", text(st, "vehStat"), filler == null ? "" : filler.toString()).stripTrailing();
        return new CarDetails(car, status, text(d, "currLoc"), text(d, "homeYrd"), text(d, "atoPresentInd"), number(d, "mtbi"), text(d, "warInd"),
                text(d, "currSchdPmNo"), text(d, "nextSchdPmNo"), number(d, "hrsFromLastPm"), number(d, "cntTimesWash"), washOverdue(text(d, "dateNxtCarWash")),
                text(d, "cameraCd"), number(d, "cumHrs"), text(d, "dateCarWash"), text(d, "softVer").indexOf('$') > 0);
    }

    /** VHSV05IS-RETDATA's title */
    private String historyTitle(Checked k) {
        StringBuilder s = new StringBuilder(historyName);
        String from = k.fromDate().isEmpty() ? "" : MDY.format(LocalDate.parse(k.fromDate())), to = k.toDate().isEmpty() ? "" : MDY.format(LocalDate.parse(k.toDate()));
        if (from.isEmpty()) { if (!to.isEmpty()) s.append(historyTitles.get(3)).append(to); }
        else s.append(historyTitles.get(0)).append(from).append(historyTitles.get(1)).append(to.isEmpty() ? historyTitles.get(2) : to);
        if (!k.sympFrom().isEmpty()) s.append(historyTitles.get(4)).append(k.sympFrom());
        if (!k.sympTo().isEmpty()) s.append(historyTitles.get(5)).append(k.sympTo());
        return s.toString().stripTrailing();
    }

    /** VHSV05IS-I-PRVNXT (program VHSV05F): an incident's narrative lines */
    public List<String> historyNarrative(String incident) {
        String i = incidentNo(incident);
        ProgramResult r = programs.attempt("VHSV05F", Map.of("VHSV05F_CRIT", Map.of("critIncdNo", i))).result();
        List<String> lines = new ArrayList<>();
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<Map<String, Object>> rows = rows(r, "VHSV05F_DATA", "dataRow");
        for (int k = 0; k < Math.min(n, rows.size()); k++) lines.add(text(rows.get(k), "narr"));
        return lines;
    }

    private static String incidentNo(String incident) {
        String i = blankOr(incident);
        if (!digits(i) || i.length() > 8) throw new InvalidRequestException("An incident number is up to 8 digits.");
        return i;
    }

    public record Repair(long document, String system, String subSystem, String disposition, String serialOn, String serialOff, String partControl) {}

    /** VHSV05IS-REPR-DATA (program VHSV05C): an incident's repairs */
    public List<Repair> repairs(String incident) {
        String i = incidentNo(incident);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", i);
        crit.put("reprRowCount", 0);
        ProgramResult r = programs.attempt("VHSV05C", Map.of("VHSV05C_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) return List.of();
        int n = (int) number(r.record("VHSV05C_CRIT"), "reprRowCount");
        List<Repair> out = new ArrayList<>();
        List<Map<String, Object>> rows = rows(r, "VHSV05C_DATA", "dataRow");
        for (int k = 0; k < Math.min(n, rows.size()); k++) {
            Map<String, Object> row = rows.get(k);
            out.add(new Repair(number(row, "reprDocNo"), text(row, "vehSystem"), text(row, "vehSubSystem"), text(row, "reprDispCd"), text(row, "serNoOn"),
                    text(row, "serNoOff"), text(row, "vehPrtCtlNo")));
        }
        return out;
    }

    public record Component(long document, String component, String serialOff, String disposition, String fault) {}

    public record RepairDetails(List<String> narrative, List<Component> components) {}

    /** VHSV05IS-R-PRVNXT: a repair's narrative (VHNARY) and components (VHSV05D) */
    public RepairDetails repair(String incident, long document) {
        String i = incidentNo(incident);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", i);
        crit.put("reprDocNo", document);
        ProgramResult nr = programs.attempt("VHNARY", Map.of("VHNARY_CRIT", crit)).result();
        List<String> narr = new ArrayList<>();
        int nn = nr.control() == null ? 0 : nr.control().rowCount();
        List<Map<String, Object>> nrows = rows(nr, "VHNARY_DATA", "dataRow");
        for (int k = 0; k < Math.min(nn, nrows.size()); k++) narr.add(text(nrows.get(k), "narr"));
        Map<String, Object> critD = new LinkedHashMap<>();
        critD.put("reprDocNo", document);
        critD.put("incdNo", i);
        ProgramResult dr = programs.attempt("VHSV05D", Map.of("VHSV05D_CRIT", critD)).result();
        List<Component> comps = new ArrayList<>();
        int dn = dr.control() == null ? 0 : dr.control().rowCount();
        List<Map<String, Object>> drows = rows(dr, "VHSV05D_DATA", "dataRow");
        for (int k = 0; k < Math.min(dn, drows.size()); k++) {
            Map<String, Object> row = drows.get(k);
            comps.add(new Component(number(row, "cmpntDocNo"), text(row, "cmpntCd"), text(row, "serNoOff"), text(row, "reprDispCd"), text(row, "cmpntFaultCd")));
        }
        return new RepairDetails(narr, comps);
    }

    /** VHSV04IS-SUBMIT with SUBMIT-X (program VHSV012, report 3): no symptoms given means all (0000 .. 9999) */
    public String printHistory(Criteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Checked k = check(new Criteria(0, c.car(), c.fromDate(), c.toDate(), c.sympFrom(), c.sympTo(), ""), h);
        String sf = k.sympFrom(), st = k.sympTo();
        if ((sf.isEmpty() && st.isEmpty()) || (digits(sf) && digits(st) && Integer.parseInt(sf) == 0 && Integer.parseInt(st) == 0)) { sf = "0000"; st = "9999"; }
        if (sf.equals("0000") && st.equals("9999")) sf = "";
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", "3");
        crit.put("reptType", printer.strip());
        crit.put("conCar1", k.car());
        crit.put("dateReptFrom", k.fromDate());
        crit.put("dateReptTo", k.toDate());
        crit.put("sympCd", sf);
        crit.put("sympCdTo", st);
        programs.run("VHSV012", Map.of("VHSV012_CRIT", crit));
        return historySubmitted;
    }

    // ---- Print -------------------------------------------------------------------------------------------

    /** VHSV01IS-SUBMIT with SUBMIT-X (program VHSV012) */
    public String print(Criteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Checked k = check(c);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", String.valueOf(k.report()));
        crit.put("reptType", printer.strip());
        crit.put("conCar1", k.car());
        crit.put("dateReptFrom", k.fromDate());
        crit.put("dateReptTo", k.toDate());
        crit.put("sympCd", k.sympFrom());
        crit.put("sympCdTo", k.sympTo());
        crit.put("deferredItem", k.deferredItems());
        programs.run("VHSV012", Map.of("VHSV012_CRIT", crit));
        return submitted;
    }
}
