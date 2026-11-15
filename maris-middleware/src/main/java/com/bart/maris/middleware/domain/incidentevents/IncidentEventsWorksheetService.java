package com.bart.maris.middleware.domain.incidentevents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vehicle desk work sheet (old screen VHIE03I, "Revenue Event Log"): the events logged for a revenue date, adding an
 * event, updating or deleting one. Rules from MARIS_RUI/EGLSource/MRSVehicleIncdEvent/RUIFunctions.egl: VHIE03IS-REVTIME,
 * -INITIAL (the code lists), VHIE03I-CHK_ACCESS, -DATE-CK, -GET-DATA, -MOVE, -GET-CA, -CAR-NO, -CON, -ADD-DATA, -UPDATE.
 * Every text comes from the screen's own statements.
 */
public class IncidentEventsWorksheetService {
    static final String PKG = "MRSVehicleIncdEvent";

    private final Programs programs;
    private final String futureTo, futureFrom, fromAfterTo, noData, exceeded, notValidSymptom, deptCd, accessGui, createPart, accessDenied;
    private final String invalidCarAdd, invalidCon, carOrCon, added, updated, problem;
    private final String badDirection, badTracking, badAction, badTech;
    private final List<String> carTypes, directions;
    private final int messageWidth;
    private final IncidentEventsIncidentFiling filing;

    public IncidentEventsWorksheetService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle bundle) {
        this.programs = new Programs(legacy, messages);
        futureTo = t.assigned("VHIE03IS-DATE-CK", PKG, "MESSAGE", "Future TO");
        futureFrom = t.assigned("VHIE03IS-DATE-CK", PKG, "MESSAGE", "Future FROM");
        fromAfterTo = t.assigned("VHIE03IS-DATE-CK", PKG, "MESSAGE", "Less Than");
        noData = t.assigned("VHIE03IS-GET-DATA", PKG, "MESSAGE", "No Data");
        exceeded = t.assigned("VHIE03IS-GET-DATA", PKG, "MESSAGE", "Exceeded");
        notValidSymptom = t.assigned("VHIE03IS-SYMP-DESC", PKG, "SYMP-CD-DESC", "Not a Valid");
        deptCd = t.assigned("VHIE03IS-INITIAL", PKG, "DEPT-CD", null);
        accessGui = t.assigned("VHIE03I-CHK_ACCESS", PKG, "GUI-NAME", null);
        createPart = IncidentEventsEgl.inText(bundle, "VHIE03I-CHK_ACCESS", PKG, "PART-NAME");
        accessDenied = t.compared("VHIE03I-CHK_ACCESS", PKG, "ACCESS-ALLOW").get(0);
        invalidCarAdd = t.assignedRaw("VHIE03IS-ADD-DATA", PKG, "MESSAGE").stream().filter(s -> s.contains("Car No, ")).findFirst().orElseThrow().strip();
        invalidCon = t.assigned("VHIE03IS-ADD-DATA", PKG, "MESSAGE", "INVALID Con");
        carOrCon = t.assigned("VHIE03IS-ADD-DATA", PKG, "MESSAGE", "or VALID");
        added = t.assigned("VHIE03IS-ADD-DATA", PKG, "MESSAGE", "Added");
        updated = t.assignedRaw("VHIE03IS-UPDATE", PKG, "MESSAGE").stream().filter(s -> s.contains("Updated")).findFirst().orElseThrow();
        problem = t.assigned("VHIE03IS-UPDATE", PKG, "MESSAGE", "Problem");
        List<String> five = t.assignedRaw("VHIE03IS-UPDATE", PKG, "MESSAGE").stream().filter(s -> s.contains("INVALID")).toList();
        badDirection = pick(five, "Direction");
        badTracking = pick(five, "Tracking");
        badAction = pick(five, "Action");
        badTech = pick(five, "Tech");
        carTypes = t.compared("VHIE03IS-ADD-DATA", PKG, "CAR-TYPE-OCCR");
        directions = t.compared("VHIE03IS-ADD-DATA", PKG, "CON-DIRECTION");
        filing = new IncidentEventsIncidentFiling(t);
        messageWidth = 25;                                                      // VHIE03IS-HOLDMSG.MSG: CHAR(25) x 5
    }

    private static String pick(List<String> texts, String word) {
        return texts.stream().filter(s -> s.contains(word)).findFirst().orElseThrow(() -> new IllegalStateException("no text with " + word));
    }

    // ---- VHIE03IS-REVTIME + VHIE03IS-INITIAL + VHIE03I-CHK_ACCESS: what the screen opens with -----------------------

    public record Code(String code, String description) {}

    /**
     * @param revenueDate   yyyy-MM-dd; before the revenue day's start time it is yesterday
     * @param createAllowed false when the user group's access row for the create-incident button says N
     * @param message       the old screen's message when a list is empty, otherwise null
     */
    public record Options(String revenueDate, String today, List<Code> symptoms, List<Code> technicians, List<Code> actions, List<Code> trackingCodes,
                          boolean createAllowed, String message) {}

    public Options options(String userGroup, String terminal) {
        String[] rev = revenue();
        String message = null;
        Map<String, Object> dc = new LinkedHashMap<>();
        dc.put("deptCd", deptCd);
        dc.put("equipType", "");
        List<Code> symptoms = codes(programs.run("VHIE04D", Map.of("VHIE04D_CRIT", dc)), "VHIE04D_DATA", "sympCd", "sympCdNarr");
        ProgramResult b = programs.run("VHIE03B", Map.of()), d = programs.run("VHIE03D", Map.of()), c = programs.run("VHIE03C", Map.of());
        for (ProgramResult r : List.of(b, d, c)) if (r.control() != null && r.control().returnCode() == 100) message = noData;
        boolean allowed = createAllowed(userGroup, terminal);
        return new Options(rev[0], rev[1], symptoms, codes(b, "VHIE03B_DATA", "vehTechId", "vehTechIdDesc"), codes(d, "VHIE03D_DATA", "actionCd", "actionCdDesc"),
                codes(c, "VHIE03C_DATA", "trackingCd", "trackingDesc"), allowed, message);
    }

    /** VHIE03I-CHK_ACCESS (program VH01A02): false when the group's access row for the create-incident button says N */
    private boolean createAllowed(String userGroup, String terminal) {
        Map<String, Object> ac = new LinkedHashMap<>();
        ac.put("userGrp", userGroup == null ? "" : userGroup);
        ac.put("guiName", accessGui);
        Map<String, Object> ad = new LinkedHashMap<>();                     // the signed-on user's VH01A01 data, as the menu filled it
        ad.put("userGroup", userGroup == null ? "" : userGroup);
        ad.put("termId", terminal == null ? "" : terminal);
        Map<String, Object> access = programs.run("VH01A02", Map.of("VH01A02_CRIT", ac, "VH01A02_DATA", ad)).record("VH01A02_DATA");
        boolean allowed = true;
        if (access != null && access.get("userGroupAccess") instanceof List<?> l) {
            for (Object o : l) {
                @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) o;
                if (createPart.equals(text(row, "partName"))) { allowed = !accessDenied.equals(text(row, "accessAllow")); break; }
            }
        }
        return allowed;
    }

    /** {revenue date, today}, both yyyy-MM-dd (program VHIE01B) */
    private String[] revenue() {
        Map<String, Object> d = programs.run("VHIE01B", Map.of()).record("VHIE01B_DATA");
        String rev = text(d, "currentTime").compareTo(text(d, "timeRevStart")) >= 0 ? text(d, "currentDate") : text(d, "yesterday");
        return new String[]{rev, text(d, "currentDate")};
    }

    private static List<Code> codes(ProgramResult r, String record, String code, String desc) {
        if (r.record(record) == null) return List.of();
        List<Code> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, record, "dataRow")) out.add(new Code(text(row, code), text(row, desc)));
        return out;
    }

    /** VHIE03IS-SYMP-DESC: a symptom code's text, or the old screen's "Not a Valid Symptom Code" */
    public String symptomText(String symptom) {
        String s = symptom == null ? "" : symptom.strip();
        Map<String, Object> dc = new LinkedHashMap<>();
        dc.put("deptCd", deptCd);
        dc.put("equipType", "");
        for (Code c : codes(programs.run("VHIE04D", Map.of("VHIE04D_CRIT", dc)), "VHIE04D_DATA", "sympCd", "sympCdNarr")) {
            if (c.code().equals(s)) return c.description();
        }
        return notValidSymptom;
    }

    // ---- VHIE03IS-DATE-CK + VHIE03IS-GET-DATA: the events of a revenue date ---------------------------------------

    /** one logged event; {@code eventId} is the revenue date and the event's number ("2026-11-12-03") */
    public record Event(int number, String eventId, boolean hasIncident, String consist, String location, String direction, String speed, String time,
                        String symptom, String car, String technician, String narrative, String trackingCode, String actionCode, String actionText,
                        String incident, String dateInServiceRev, String dateInService, String timeInService, int binDash, String userGroup) {}

    /** @param message the old screen's message (a date error, no data, too many rows), otherwise null */
    public record EventLog(String date, List<Event> events, String message) {}

    /**
     * The old screen checks the date (VHIE03IS-DATE-CK) but its List button also runs VHIE03IS-GET-DATA directly, so the
     * events are read even when the check fails; the check's message stays unless the read sets its own.
     * @param date yyyy-MM-dd
     */
    public EventLog events(String date) {
        String d = iso(date);
        String[] rev = revenue();
        String message = null;
        if (rev[0].compareTo(rev[1]) > 0) message = futureTo;                   // TO-DATE is the revenue date the screen opened with
        if (d.compareTo(rev[1]) > 0) message = futureFrom;
        if (rev[0].compareTo(d) < 0) message = fromAfterTo;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateEventOccr", d);
        ProgramResult r = programs.run("VHIE03A", Map.of("VHIE03A_CRIT", crit));
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc == 100) return new EventLog(d, List.of(), noData);
        if ("Y".equals(text(r.record("VHIE03A_CRIT"), "exceedFlag"))) message = exceeded;
        List<Event> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHIE03A_DATA", "dataRow")) out.add(event(row));
        return new EventLog(d, out, message);
    }

    @SuppressWarnings("unchecked")
    private static Event event(Map<String, Object> row) {
        String con = text(row, "conNo");
        String consist = "";
        if (!con.isEmpty()) {
            Object dn = row.get("dashNo");
            String no2 = dn instanceof Map<?, ?> m ? text((Map<String, Object>) m, "no2") : "";
            consist = con + "-" + no2;
        }
        int n = (int) number(row, "cntEvents");
        Object ad = row.get("actionCdDesc");                                 // ACTION-CD-DESC-4 is the column shown
        String desc = ad instanceof Map<?, ?> am ? (pad(((Map<String, Object>) am).get("actionCdDesc4"), 4) + pad(((Map<String, Object>) am).get("actionCdDesc26"), 26)).strip()
                : ad == null ? "" : ad.toString().strip();
        return new Event(n, text(row, "dateEventOccr") + "-" + String.format("%02d", Math.abs(n) % 100), !text(row, "incdNo").isEmpty(), consist,
                text(row, "eventLoc"), text(row, "conDirection"), text(row, "speed"), text(row, "timeEventOccr"), text(row, "sympCd"), text(row, "carNo"),
                text(row, "vehTechId"), text(row, "narr"), text(row, "trackingCd"), text(row, "actionCd"), desc.length() > 4 ? desc.substring(0, 4) : desc,
                text(row, "incdNo"), text(row, "dateInsrvRev"), text(row, "dateInsrv"), text(row, "timeInsrv"), (int) number(row, "binDashNo"), text(row, "userGrp"));
    }

    private static String iso(String date) {
        String d = date == null ? "" : date.strip();
        if (!d.matches("\\d{4}-\\d{2}-\\d{2}")) throw new InvalidRequestException("Enter the date as YYYY-MM-DD.");
        return d;
    }

    // ---- VHIE03IS-GET-CA: an event's narrative and corrective action ----------------------------------------------

    public record CorrectiveAction(boolean hasCorrectiveAction, String correctiveAction, String narrative) {}

    public CorrectiveAction correctiveAction(String revenueDate, int number) {
        ProgramResult r = readCorrectiveAction(iso(revenueDate), number);
        Map<String, Object> first = firstRow(r, "VHIE04C_DATA");
        return new CorrectiveAction("Y".equals(text(r.record("VHIE04C_CRIT"), "hasCaNarr")), text(first, "caNarr"), text(first, "narr"));
    }

    private ProgramResult readCorrectiveAction(String revenueDate, int number) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateRevenue", revenueDate);
        crit.put("cntEvents", number);
        return programs.attempt("VHIE04C", Map.of("VHIE04C_CRIT", crit)).result();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstRow(ProgramResult r, String record) {
        Map<String, Object> d = r.record(record);
        return d != null && d.get("dataRow") instanceof List<?> l && !l.isEmpty() ? (Map<String, Object>) l.get(0) : Map.of();
    }

    // ---- VHIE03IS-CAR-NO + VHIE03IS-CON: the consist of a car, the cars of a consist -------------------------------

    /** when the car was put in service in its consist (the first car row of VHIE03G, or VHIE03H) */
    public record InService(int binDash, String timeInService, String dateInServiceRev, String dateInService) {
        static final InService NONE = new InService(0, "", "", "");
    }

    /** @param consist CCC-DD; @param message the old screen's message when nothing is found */
    public record Consist(String consist, List<String> cars, InService inService, String message) {}

    /** VHIE03IS-CAR-NO (program VHIE03H): the consist a car runs in; a car under 1000 is looked up zero-padded */
    @SuppressWarnings("unchecked")
    public Consist consistOfCar(String car) {
        String c = car == null ? "" : car.strip();
        if (c.matches("\\d{1,4}") && Integer.parseInt(c) < 1000) c = String.format("%04d", Integer.parseInt(c));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", c);
        ProgramResult r = programs.run("VHIE03H", Map.of("VHIE03H_DATA", data));
        if (r.control() != null && r.control().returnCode() == 100) return new Consist("", List.of(), InService.NONE, noData);
        Map<String, Object> d = r.record("VHIE03H_DATA");
        String no2 = d.get("dashNo") instanceof Map<?, ?> m ? String.valueOf(((Map<String, Object>) m).getOrDefault("dashNo2", "")) : "";
        return new Consist(conDash(text(d, "conNo"), no2), List.of(), inService(d), null);
    }

    /**
     * VHIE03IS-CON (program VHIE03G): the cars of a consist. Without the event's in-service data it reads the consist
     * as of the date (flag 1); with it, the consist the event's car was in (flag 2).
     */
    public Consist consistCars(String consist, String date, InService event) {
        String con = consist == null ? "" : consist.strip();
        if (con.length() > 3) con = con.substring(0, 3);
        if (con.isBlank()) throw new InvalidRequestException("Enter a consist number.");
        Map<String, Object> crit = new LinkedHashMap<>();
        boolean byDate = event == null || event.dateInServiceRev().isBlank() || event.timeInService().isBlank();
        if (byDate) {
            crit.put("dateInsrvRev", iso(date));
            crit.put("flag", "1");
        } else {
            crit.put("dateInsrv", IncidentEventsDates.ymd(event.dateInService()));
            crit.put("dateInsrvRev", IncidentEventsDates.ymd(event.dateInServiceRev()));
            crit.put("dashNo", event.binDash());
            crit.put("timeInsrv", event.timeInService());
            crit.put("flag", "2");
        }
        crit.put("conNo", con);
        ProgramResult r = programs.run("VHIE03G", Map.of("VHIE03G_CRIT", crit));
        if (r.control() != null && r.control().returnCode() == 100) return new Consist("", List.of(), InService.NONE, noData);
        List<Map<String, Object>> rows = rows(r, "VHIE03G_DATA", "dataRow");
        List<String> cars = new ArrayList<>();
        for (Map<String, Object> row : rows.subList(0, Math.min(10, rows.size()))) cars.add(carFormat(row));
        Map<String, Object> first = rows.isEmpty() ? Map.of() : rows.get(0);
        String shown = byDate ? conDash(con, dashNo2(first)) : "";
        return new Consist(shown, cars, inService(first), null);
    }

    @SuppressWarnings("unchecked")
    private static String carFormat(Map<String, Object> row) {
        Object f = row.get("carFormat");
        if (!(f instanceof Map<?, ?> m)) return f == null ? "" : f.toString().stripTrailing();
        Map<String, Object> fm = (Map<String, Object>) m;
        return (pad(fm.get("orient1"), 1) + pad(fm.get("car"), 4) + pad(fm.get("orient2"), 1)).stripTrailing();
    }

    private static String pad(Object v, int n) {
        String s = v == null ? "" : v.toString();
        return String.format("%-" + n + "s", s).substring(0, n);
    }

    @SuppressWarnings("unchecked")
    private static String dashNo2(Map<String, Object> row) {
        Object v = row.get("dashNo");
        return v instanceof Map<?, ?> m ? text((Map<String, Object>) m, "dashNo2") : "";
    }

    /** VHIE03W-CARS.CON-DASH: CCC-DD, a blank first dash digit shown as 0 */
    private static String conDash(String con, String no2) {
        String n = String.format("%-2s", no2 == null ? "" : no2).substring(0, 2);
        if (n.charAt(0) == ' ') n = "0" + n.substring(1);
        return String.format("%-3s", con) + "-" + n;
    }

    private static InService inService(Map<String, Object> d) {
        return new InService((int) number(d, "binDashNo"), text(d, "timeInsrv"), text(d, "dateInsrvRev"), text(d, "dateInsrv"));
    }

    // ---- VHIE03IS-ADD-DATA / VHIE03IS-UPDATE: add, update, delete an event ----------------------------------------

    /**
     * What the user types for an event. {@code consist} is CCC or CCC-DD; {@code time} HH:MM:SS (updates only: the old
     * screen's time field is off when adding).
     */
    public record EventInput(String car, String consist, String location, String direction, String speed, String time, String symptom,
                             String narrative, String technician, String trackingCode, String actionCode, String incident) {}

    /** VHIE03IS-ADD-DATA (programs VHINC6A, VHIE03E, VHIE04A): adds an event to today's revenue date */
    public String add(EventInput in, String userGroup, String terminal) {
        String[] rev = revenue();                                             // the Add button runs VHIE03IS-REVTIME first
        String car = s(in.car()), conDash = s(in.consist());
        InService ins = InService.NONE;
        if (conDash.isEmpty() && !car.isEmpty()) {                            // VHIE03IS-CAR-NO when the train is blank
            Consist found = consistOfCar(car);
            if (!found.consist().isEmpty()) { conDash = found.consist(); ins = found.inService(); }
        } else if (!conDash.isEmpty()) {                                      // VHIE03IS-CON
            Consist found = consistCars(conDash, rev[0], null);
            if (!found.consist().isEmpty()) { conDash = found.consist(); ins = found.inService(); }
        }
        String con = conDash.length() > 3 ? conDash.substring(0, 3).strip() : conDash.strip();
        String message = null;
        boolean error = false;
        if (!car.isEmpty() && !validCar(car)) { message = invalidCarAdd; error = true; }
        Codes codes = codes();
        List<String> five = fieldErrors(in, codes);
        if (!five.isEmpty()) error = true;
        if (!con.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("conNo", con);
            ProgramResult er = programs.run("VHIE03E", Map.of("VHIE03E_DATA", e));
            if (number(er.record("VHIE03E_DATA"), "itemCount") == 0) { message = invalidCon; error = true; }
        }
        if (car.isEmpty() && con.isEmpty()) { message = carOrCon; error = true; }
        if (error) {
            if (!five.isEmpty()) message = joined(five);
            throw new InvalidRequestException(message);
        }
        Map<String, Object> d = record(in, car, conDash);
        d.put("dateEventOccr", rev[0]);
        d.put("dateRevenue", rev[0]);
        d.put("userGrp", userGroup == null ? "" : userGroup);
        d.put("termLastUpd", terminal == null ? "" : terminal);
        d.put("binDashNo", ins.binDash());
        d.put("timeInsrv", ins.timeInService());
        d.put("dateInsrvRev", ins.dateInServiceRev());
        d.put("dateInsrv", ins.dateInService());
        ProgramResult r = programs.attempt("VHIE04A", Map.of("VHIE04A_DATA", d)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc == 0 || rc == 4) return added;
        if (rc == 100) return null;
        throw programs.error("VHIE04A", r.control());
    }

    /** the event fields every save sends (VHIE04A_DATA as the screen fills it) */
    private static Map<String, Object> record(EventInput in, String car, String conDash) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("actionCd", s(in.actionCode()));
        d.put("carNo", car);
        d.put("conDirection", s(in.direction()));
        d.put("conDash", conDashMap(conDash));
        d.put("eventLoc", s(in.location()));
        d.put("incdNo", s(in.incident()));
        d.put("speed", s(in.speed()));
        d.put("sympCd", s(in.symptom()));
        d.put("trackingCd", s(in.trackingCode()));
        d.put("vehTechId", s(in.technician()));
        d.put("narrative", s(in.narrative()));
        return d;
    }

    private static Map<String, Object> conDashMap(String conDash) {
        String x = String.format("%-6s", conDash).substring(0, 6);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conNo", x.substring(0, 3));
        m.put("dash", x.substring(3, 4));
        m.put("dashNo", Map.of("dashNo1", x.substring(4, 5), "dashNo2", x.substring(5, 6)));
        return m;
    }

    private record Codes(List<String> tracking, List<String> actions, List<String> techs) {}

    private Codes codes() {
        return new Codes(codes(programs.run("VHIE03C", Map.of()), "VHIE03C_DATA", "trackingCd", "trackingDesc").stream().map(Code::code).toList(),
                codes(programs.run("VHIE03D", Map.of()), "VHIE03D_DATA", "actionCd", "actionCdDesc").stream().map(Code::code).toList(),
                codes(programs.run("VHIE03B", Map.of()), "VHIE03B_DATA", "vehTechId", "vehTechIdDesc").stream().map(Code::code).toList());
    }

    /** the four checks VHIE03IS-ADD-DATA and VHIE03IS-UPDATE share, in their order (VHIE03IS-MESSAGE5) */
    private List<String> fieldErrors(EventInput in, Codes codes) {
        List<String> out = new ArrayList<>();
        String dir = s(in.direction());
        if (!dir.isEmpty() && !directions.contains(dir)) out.add(badDirection);
        String tr = s(in.trackingCode()), ac = s(in.actionCode()), te = s(in.technician());
        if (!tr.isEmpty() && !codes.tracking().contains(tr)) out.add(badTracking);
        if (!ac.isEmpty() && !codes.actions().contains(ac)) out.add(badAction);
        if (!te.isEmpty() && !codes.techs().contains(te)) out.add(badTech);
        return out;
    }

    /** VHIE03IS-HOLDMSG.MESSAGE: the messages side by side, 25 characters each */
    private String joined(List<String> msgs) {
        StringBuilder b = new StringBuilder();
        for (String m : msgs) b.append(String.format("%-" + messageWidth + "s", m).substring(0, messageWidth));
        return b.toString().strip();
    }

    /** VHINC6A: a car of one of the screen's car types */
    private boolean validCar(String car) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", crit)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc != 0 && rc != 4) return false;
        if (r.control() != null && r.control().rowCount() == 0) return false;
        return carTypes.contains(carType(r.record("VHINC6A_DATA")));
    }

    /** VHINC6A_DATA.CAR-TYPE-OCCR[1]: the first letter of the car type */
    @SuppressWarnings("unchecked")
    static String carType(Map<String, Object> d) {
        Object t = d == null ? null : d.get("carType");
        if (t instanceof Map<?, ?> m && m.get("carTypeOccr") instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0)).strip();
        String s = t == null ? "" : t.toString().strip();
        return s.isEmpty() ? "" : s.substring(0, 1);
    }

    /**
     * VHIE03IS-MOVE + VHIE03IS-GET-CA + VHIE03IS-UPDATE (program VHIE04B): saves the changes to an event of the listed
     * revenue date, or deletes it. As in the old screen, a delete answers with the update message.
     */
    public String update(String date, int number, EventInput in, boolean delete, String userGroup, String terminal) {
        Picked p = pick(iso(date), number, in, false);
        return save(p, in, s(in.time()), s(in.incident()), delete, null, userGroup, terminal);
    }

    /** an event as the old screen holds it once its row is picked (VHIE03IS-MOVE, -GET-CA, -CON) */
    private record Picked(String date, Event event, String hasCa, String caNarr, String conDash, InService lookup, List<Map<String, Object>> cars,
                          int conCounter, Map<String, Object> consistCrit) {}

    private Picked pick(String d, int number, EventInput in, boolean always) {
        Event ev = null;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateEventOccr", d);
        for (Map<String, Object> row : rows(programs.run("VHIE03A", Map.of("VHIE03A_CRIT", crit)), "VHIE03A_DATA", "dataRow")) {
            if ((int) number(row, "cntEvents") == number) ev = event(row);
        }
        if (ev == null) throw new InvalidRequestException("Event " + number + " of " + d + " was not found.");
        ProgramResult ca = readCorrectiveAction(d, number);
        String hasCa = text(ca.record("VHIE04C_CRIT"), "hasCaNarr");
        String caNarr = text(firstRow(ca, "VHIE04C_DATA"), "caNarr");
        String conDash = s(in.consist());
        String con = conDash.length() > 3 ? conDash.substring(0, 3).strip() : conDash;
        boolean rowInService = !ev.dateInServiceRev().isEmpty() || !ev.timeInService().isEmpty();
        InService lookup = InService.NONE;
        List<Map<String, Object>> cars = List.of();
        int counter = 0;
        Map<String, Object> gc = new LinkedHashMap<>();
        if (!con.isEmpty() && (!rowInService || always)) {
            if (!rowInService) {
                gc.put("dateInsrvRev", d);
                gc.put("flag", "1");
            } else {
                gc.put("dateInsrv", IncidentEventsDates.ymd(ev.dateInService()));
                gc.put("dateInsrvRev", IncidentEventsDates.ymd(ev.dateInServiceRev()));
                gc.put("dashNo", ev.binDash());
                gc.put("timeInsrv", ev.timeInService());
                gc.put("flag", "2");
            }
            gc.put("conNo", con);
            ProgramResult r = programs.run("VHIE03G", Map.of("VHIE03G_CRIT", gc));
            if (r.control() == null || r.control().returnCode() != 100) {
                cars = rows(r, "VHIE03G_DATA", "dataRow");
                counter = rowInService ? cars.size() : cars.size() - 1;          // flag 1 counts one car less (VHIE03IS-CON)
                Map<String, Object> first = cars.isEmpty() ? Map.of() : cars.get(0);
                if (!rowInService) { conDash = conDash(con, dashNo2(first)); lookup = inService(first); }
            }
        }
        return new Picked(d, ev, hasCa, caNarr, conDash, lookup, cars, counter, gc);
    }

    /** VHIE03IS-UPDATE (program VHIE04B) for a picked event; {@code success} is the message a filed incident leaves */
    @SuppressWarnings("unchecked")
    private String save(Picked p, EventInput in, String time, String incident, boolean delete, String success, String userGroup, String terminal) {
        Event ev = p.event();
        List<String> five = fieldErrors(in, codes());
        if (!five.isEmpty()) throw new InvalidRequestException(joined(five));
        Map<String, Object> bc = new LinkedHashMap<>();
        bc.put("dateRevenue", p.date());
        bc.put("cntEvents", ev.number());
        bc.put("hasCaNarr", p.hasCa());
        Map<String, Object> data = record(in, s(in.car()), p.conDash());
        Map<String, Object> cd = (Map<String, Object>) data.remove("conDash");
        data.put("conNo", cd.get("conNo"));
        data.put("narr", data.remove("narrative"));
        data.put("incdNo", incident);
        data.put("deleteInd", delete ? "Y" : "N");
        String t8 = String.format("%-8s", time).substring(0, 8);
        if (t8.substring(5).isBlank()) t8 = t8.substring(0, 5) + ".00";
        data.put("timeEventOccr", t8.strip().isEmpty() ? "" : t8);
        data.put("narrNo", 1);
        data.put("caNarr", p.caNarr());
        data.put("caNarrNo", 1);
        if (ev.dateInServiceRev().isEmpty() && ev.timeInService().isEmpty()) {
            data.put("binDashNo", p.lookup().binDash());
            data.put("timeInsrv", p.lookup().timeInService());
            data.put("dateInsrvRev", p.lookup().dateInServiceRev());
            data.put("dateInsrv", p.lookup().dateInService());
        } else {
            data.put("binDashNo", ev.binDash());
            data.put("timeInsrv", ev.timeInService());
            data.put("dateInsrvRev", IncidentEventsDates.ymd(ev.dateInServiceRev()));
            data.put("dateInsrv", IncidentEventsDates.ymd(ev.dateInService()));
        }
        data.put("termLastUpd", terminal == null ? "" : terminal);
        data.put("userGrp", rowGroup(ev, userGroup));
        ProgramResult r = programs.attempt("VHIE04B", Map.of("VHIE04B_CRIT", bc, "VHIE04B_DATA", data)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc == 0 || rc == 4) return success != null ? success : updated.strip();   // "Deleted" is set first, then overwritten
        if (rc == 100) return null;
        throw new InvalidRequestException(problem);
    }

    // ---- the CreMaint button: file an incident for a picked event ------------------------------------------------

    /** the error flags of VHIE04IS-CK-DATA* that VHIE03IS-BLD-MSG turns into text */
    private static final class Flags {
        boolean invalidCar, displayInvalidCar, invalidCon, noCarInCon, invalidSymp, invalidSympVal, invalidTimeIncd, dispInvalidTime, invalidSpeed,
                invalidSpeedVal, invalidDirection, invalidLoc, invalidNarr, invalidDateTime, daysGr7, futureDate, futureTime;
        int count;
    }

    /** VHIE03IS-BLD-MSG + VHIE03IS-MOVE-MSG */
    private String buildMessage(Flags f, String car, String symptom, String speed) {
        List<String> e = filing.errors;
        List<String> m = new ArrayList<>();
        if (car.equals(filing.zeroCar)) { if (f.invalidCar) m.add(e.get(0)); }
        else if (f.displayInvalidCar) m.add(e.get(1));
        if (f.invalidCon) m.add(e.get(4));
        if (f.noCarInCon) m.add(e.get(5));
        if (symptom.isEmpty()) { if (f.invalidSymp) m.add(e.get(6)); }
        else if (f.invalidSympVal) m.add(e.get(7));
        if (f.invalidTimeIncd) m.add(e.get(11));
        else if (f.dispInvalidTime) m.add(e.get(12));
        if (speed.isEmpty()) { if (f.invalidSpeed) m.add(e.get(14)); }
        else if (f.invalidSpeedVal) m.add(e.get(15));
        if (f.invalidDirection) m.add(e.get(16));
        if (m.size() < 6) {
            if (f.invalidLoc) m.add(e.get(17));
            if (m.size() < 6) {
                if (f.invalidNarr) m.add(e.get(18));
                if (m.size() < 6) {
                    if (f.invalidDateTime) m.add(e.get(20));
                    else if (f.daysGr7) m.add(e.get(22));
                    else if (f.futureDate) m.add(e.get(23));
                    else if (f.futureTime) m.add(e.get(24));
                }
            }
        }
        return IncidentEventsIncidentFiling.joined(m);
    }

    /** the checks VHIE04IS-CK-DATA and -CK-DATA-T share: time, speed, direction, location, narrative, symptom */
    private void commonChecks(Flags f, String time, String speed, String direction, String location, String narrative, String symptom) {
        if (time.isEmpty()) { f.count++; f.invalidTimeIncd = true; }
        if (speed.isEmpty()) { f.count++; f.invalidSpeed = true; }
        if (direction.isEmpty() || !filing.directions.contains(direction)) { f.count++; f.invalidDirection = true; }
        if (location.isEmpty()) { f.count++; f.invalidLoc = true; }
        if (narrative.isEmpty()) { f.count++; f.invalidNarr = true; }
        if (filing.badSpeed(speed)) { f.count++; f.invalidSpeedVal = true; }
        if (!time.isEmpty() && IncidentEventsIncidentFiling.badTime(time)) { f.count++; f.dispInvalidTime = true; }
        if (symptom.isEmpty()) { f.count++; f.invalidSympVal = true; }
    }

    /** VHINC6A for the checks: {0 = fine, 1 = not a car of the screen's types}; the car's data */
    private Map<String, Object> carInfo(String car, Flags f) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", crit)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc == 0 || rc == 4) {
            if ((r.control() != null && r.control().rowCount() == 0) || !carTypes.contains(carType(r.record("VHINC6A_DATA")))) { if (f != null) { f.count++; f.displayInvalidCar = true; } return null; }
            return r.record("VHINC6A_DATA");
        }
        if (rc == 100) { if (f != null) { f.count++; f.displayInvalidCar = true; } return null; }
        throw programs.error("VHINC6A", r.control());
    }

    /**
     * VHIE03IS-WHICH-ONE and what it triggers: a car in a consist files a revenue incident (VHEVLX), a car out of service
     * a revenue fix incident (VHEVLY), a train with no car a train incident (VHEVL1F, or VHINC1X when the dash is out or
     * swapped). The event is then saved with the new incident number (VHIE04B) and the incident report is queued (VHINC3A).
     * @return the old screen's message: the incident filed, or the reason it was not
     */
    public String createIncident(String date, int number, EventInput in, String userGroup, String terminal) {
        String d = iso(date);
        Picked p = pick(d, number, in, true);
        // the old screen's CreMaint button is off for an event that has an incident, and for a group without access
        if (p.event().hasIncident()) throw new InvalidRequestException("Event " + number + " of " + d + " already has incident " + p.event().incident() + ".");
        if (!createAllowed(userGroup, terminal)) throw new InvalidRequestException("Your user group may not create incidents.");
        String car = s(in.car()), con = p.conDash().length() > 3 ? p.conDash().substring(0, 3).strip() : p.conDash().strip();
        if (car.isEmpty()) {
            if (con.isEmpty()) throw new InvalidRequestException(filing.invalidCarNoConsist);
            return trainIncident(p, in, con, userGroup, terminal);
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", crit)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc != 0 && rc != 4) throw new InvalidRequestException(filing.invalidCar);
        Map<String, Object> info = r.record("VHINC6A_DATA");
        if ((r.control() != null && r.control().rowCount() == 0) || !carTypes.contains(carType(info))) throw new InvalidRequestException(filing.invalidCarNoConsist);
        return filing.inConsist.equals(text(info, "whereIsCar")) ? revenueIncident(p, in, car, con, userGroup, terminal) : fixIncident(p, in, car, con, userGroup, terminal);
    }

    private boolean outOrSwapped(Picked p) {
        return !p.cars().isEmpty() && filing.outStatuses.contains(text(p.cars().get(0), "conStat"));
    }

    /** VHIE04IS-CK-DATA, VHIE03IS-CK-DATA-Y, VHIE04IS-STORE-DB, then VHIE03IS-UPDATE and VHIE04IS-CA-CICS */
    private String revenueIncident(Picked p, EventInput in, String car, String con, String userGroup, String terminal) {
        Flags f = new Flags();
        String time = s(in.time()), speed = s(in.speed()), symptom = s(in.symptom());
        commonChecks(f, time, speed, s(in.direction()), s(in.location()), s(in.narrative()), symptom);
        Map<String, Object> info = carInfo(car, f);
        if (f.count > 0) throw new InvalidRequestException(buildMessage(f, car, symptom, speed));
        if (outOrSwapped(p)) {
            for (int c = 1; c <= Math.min(p.conCounter(), p.cars().size()); c++) {
                if (filing.inConsist.equals(text(p.cars().get(c - 1), "whereIsCar"))) throw new InvalidRequestException(filing.carStillOnCon);
            }
        } else if (info == null || !filing.inConsist.equals(text(info, "whereIsCar"))) {
            return null;
        }
        String ws = IncidentEventsIncidentFiling.workingTime(time);
        String group = rowGroup(p.event(), userGroup);
        Map<String, Object> xc = new LinkedHashMap<>();
        xc.put("carNo", car);
        xc.put("incdType", filing.ivrType);
        xc.put("incdPfx", filing.ivrPfx);
        xc.put("incidentName", filing.ivrName);
        Map<String, Object> xd = incidentData(car, con, in, terminal, p.date(), ws, group);
        xd.put("marisFunc", filing.marisFunc);
        xd.put("incdType", filing.ivrType);
        ProgramResult r = programs.attempt("VHEVLX", Map.of("VHEVLX_CRIT", xc, "VHEVLX_DATA", xd)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (!((rc == 0 || rc == 4) && "N".equals(text(r.record("VHEVLX_CRIT"), "errFound")))) throw programs.error("VHEVLX", r.control());
        String incident = incidentNo(r.record("VHEVLX_DATA"));
        queueReport(group, terminal, incident, car, filing.reportIvr, "N");      // the RUN-UPDATE trigger fires after the group ends
        return save(p, in, ws, incident, false, IncidentEventsIncidentFiling.successMessage(filing.ivrOk, incident, car), userGroup, terminal);
    }

    /** the incident fields VHEVLX / VHEVLY / VHEVL1F / VHINC1X share */
    private Map<String, Object> incidentData(String car, String con, EventInput in, String terminal, String date, String time, String group) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("carNo", car);
        d.put("conNo", con);
        d.put("discvDur", filing.discvDur);
        d.put("incdLoc", s(in.location()));
        d.put("sympCd", s(in.symptom()));
        d.put("conDirection", s(in.direction()));
        d.put("speed", s(in.speed()));
        d.put("termLastUpd", terminal == null ? "" : terminal);
        d.put("dateIncdOccrRev", date);
        d.put("dateIncdOccr", date);
        d.put("timeIncdOccr", time);
        d.put("userGrp", group);
        d.put("dateNarr", date);
        d.put("timeNarr", time);
        d.put("narr", s(in.narrative()));
        return d;
    }

    /** VHIE04IS-CA-CICS (program VHINC3A): the incident report */
    private void queueReport(String group, String terminal, String incident, String car, String type, String reset) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("reptId", filing.reportId);
        c.put("reptType", type);
        c.put("conCar1", car);
        c.put("incdNo", incident);
        c.put("termId", terminal == null ? "" : terminal);
        c.put("userGrp", group);
        c.put("sendResetMsg", reset);
        programs.run("VHINC3A", Map.of("VHINC3A_CRIT", c));
    }

    /** VHIE04IS-CK-DATA-F, -PSEUDO, -STOREDB-F, then VHIE03IS-UPDATE, VHIE04IS-UPD-STAT and -CA-CICS */
    @SuppressWarnings("unchecked")
    private String fixIncident(Picked p, EventInput in, String car, String con, String userGroup, String terminal) {
        Flags f = new Flags();
        String time = s(in.time()), symptom = s(in.symptom());
        if (car.equals(filing.zeroCar)) { f.count++; f.invalidCar = true; }
        if (symptom.isEmpty()) { f.count++; f.invalidSymp = true; }
        if (s(in.location()).isEmpty()) { f.count++; f.invalidLoc = true; }
        if (s(in.narrative()).isEmpty()) { f.count++; f.invalidNarr = true; }
        if (time.isEmpty()) { f.count++; f.invalidTimeIncd = true; }
        String ws = IncidentEventsIncidentFiling.workingTime(time);
        if (IncidentEventsIncidentFiling.badTime(ws)) { f.count++; f.dispInvalidTime = true; }
        Map<String, Object> ec = new LinkedHashMap<>();
        ec.put("dateWork", p.date());
        ec.put("timeWork", time);
        ProgramResult er = programs.attempt("VHINC8E", Map.of("VHINC8E_CRIT", ec)).result();
        int erc = er.control() == null ? 0 : er.control().returnCode();
        if (erc == 0 || erc == 4) {
            long days = number(er.record("VHINC8E_DATA"), "daysDiff"), diff = number(er.record("VHINC8E_DATA"), "timeDiff");
            if (days < 0) { f.count++; f.futureDate = true; }
            else if (days == 0 && diff < 0) { f.count++; f.futureTime = true; }
            if (!f.futureDate && !f.futureTime && days > filing.maxDays) { f.count++; f.daysGr7 = true; }
        } else { f.count++; f.invalidDateTime = true; }
        if (!symptom.isEmpty()) {
            Map<String, Object> ac = new LinkedHashMap<>();
            ac.put("vagenCount", 0);
            ac.put("sympCd", symptom);
            ProgramResult ar = programs.attempt("VHINC1A", Map.of("VHINC1A_CRIT", ac)).result();
            int arc = ar.control() == null ? 0 : ar.control().returnCode();
            if ((arc == 0 || arc == 4) && number(ar.record("VHINC1A_CRIT"), "vagenCount") == 0) { f.count++; f.invalidSympVal = true; }
        }
        Map<String, Object> info = carInfo(car, f);
        if (info != null && filing.inConsist.equals(text(info, "whereIsCar"))) f.count++;      // DISPLAY-IN-USE: counted, no text
        if (f.count > 0) throw new InvalidRequestException(buildMessage(f, car, symptom, s(in.speed())));
        String group = rowGroup(p.event(), userGroup);
        String reset = "N";
        Map<String, Object> xa = new LinkedHashMap<>();
        xa.put("carNo", car);
        ProgramResult ar = programs.attempt("VHINXA", Map.of("VHINXA_CRIT", xa)).result();
        int arc = ar.control() == null ? 0 : ar.control().returnCode();
        if (arc == 0 || arc == 4) {
            List<Map<String, Object>> rows = ar.record("VHINXA_DATA") != null && ar.record("VHINXA_DATA").get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            Map<String, Object> bc = new LinkedHashMap<>();
            bc.put("conNo", rows.isEmpty() ? "" : text(rows.get(0), "conNo"));
            Map<String, Object> bd = new LinkedHashMap<>();
            bd.put("termLastUpd", terminal == null ? "" : terminal);
            bd.put("userGroup", group);
            List<Map<String, Object>> carRows = new ArrayList<>();
            List<String> cons = new ArrayList<>();
            for (int k = 0; k < 10; k++) {
                carRows.add(Map.of("carNum", k < rows.size() ? text(rows.get(k), "carNo") : ""));
                cons.add(k < rows.size() ? text(rows.get(k), "conNo") : "");
            }
            bd.put("dataRow", carRows);
            bd.put("conData", Map.of("conNum", cons));
            ProgramResult br = programs.attempt("VHINXB", Map.of("VHINXB_CRIT", bc, "VHINXB_DATA", bd)).result();
            int brc = br.control() == null ? 0 : br.control().returnCode();
            if (brc == 0 || brc == 4) reset = "Y";
        }
        Event ev = p.event();
        Map<String, Object> yc = new LinkedHashMap<>();
        yc.put("carNo", car);
        yc.put("incdType", filing.fixType);
        yc.put("incdPfx", filing.fixPfx);
        yc.put("incidentName", filing.fixName);
        yc.put("typeInd", filing.typeRev);
        yc.put("dateIncdOccrRev", p.date());
        Map<String, Object> yd = incidentData(car, con, in, terminal, p.date(), time, group);
        yd.put("dashNo", ev.binDash());
        yd.put("dateInsrv", IncidentEventsDates.ymd(ev.dateInService()));
        yd.put("dateInsrvRev", IncidentEventsDates.ymd(ev.dateInServiceRev()));
        yd.put("timeInsrv", ev.timeInService());
        yd.put("marisFunc", filing.marisFunc);
        yd.put("incdType", filing.fixType);
        ProgramResult r = programs.attempt("VHEVLY", Map.of("VHEVLY_CRIT", yc, "VHEVLY_DATA", yd)).result();
        int rc = r.control() == null ? 0 : r.control().returnCode();
        if (rc != 0 && rc != 4) throw new InvalidRequestException(filing.fixProblem);
        String incident = incidentNo(r.record("VHEVLY_DATA"));
        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("carNo", car);
        Map<String, Object> sd = new LinkedHashMap<>();
        sd.put("termLastUpd", terminal == null ? "" : terminal);
        programs.run("VHINXC", Map.of("VHINXC_CRIT", sc, "VHINXC_DATA", sd));
        queueReport(group, terminal, incident, car, filing.reportIvh, reset);
        return save(p, in, time, incident, false, IncidentEventsIncidentFiling.successMessage(filing.fixOk, incident, car), userGroup, terminal);
    }

    /** VHIE04IS-CK-DATA-T, then VHIE03IS-CK-DATA-X + VHIE04IS-STOREDB-X or VHIE04IS-STOREDB-T, then VHIE03IS-UPDATE */
    private String trainIncident(Picked p, EventInput in, String con, String userGroup, String terminal) {
        Flags f = new Flags();
        String time = s(in.time()), speed = s(in.speed()), symptom = s(in.symptom());
        commonChecks(f, time, speed, s(in.direction()), s(in.location()), s(in.narrative()), symptom);
        Map<String, Object> bd = new LinkedHashMap<>();
        bd.put("conNo", con);
        ProgramResult br = programs.attempt("VHINC1B", Map.of("VHINC1B_DATA", bd)).result();
        int brc = br.control() == null ? 0 : br.control().returnCode();
        if (brc == 0 || brc == 4) {
            if (br.control() != null && br.control().rowCount() == 0) { f.count++; f.invalidCon = true; }
            else if ("Y".equals(text(br.record("VHINC1B_DATA"), "noCarsInd"))) { f.count++; f.noCarInCon = true; }
        } else throw programs.error("VHINC1B", br.control());
        if (f.count > 0) throw new InvalidRequestException(buildMessage(f, "", symptom, speed));
        String group = rowGroup(p.event(), userGroup);
        Map<String, Object> d = incidentData(filing.trainCar, con, in, terminal, p.date(), time, group);
        String incident;
        List<String> ok;
        if (outOrSwapped(p)) {
            StringBuilder cars = new StringBuilder();
            for (int c = 1; c <= Math.min(p.conCounter(), p.cars().size()); c++) {
                if (filing.inConsist.equals(text(p.cars().get(c - 1), "whereIsCar"))) cars.append(IncidentEventsIncidentFiling.fit(text(p.cars().get(c - 1), "carNo"), 5));
            }
            if (cars.length() > 0) throw new InvalidRequestException((IncidentEventsIncidentFiling.fit(filing.dashNotInService, 60) + cars).strip());
            Map<String, Object> g = p.consistCrit();
            Map<String, Object> xc = new LinkedHashMap<>();
            xc.put("conNo", con);
            xc.put("dashNo", g.getOrDefault("dashNo", 0));
            xc.put("dateInsrvRev", g.getOrDefault("dateInsrvRev", ""));
            xc.put("dateInsrv", g.getOrDefault("dateInsrv", ""));
            xc.put("timeInsrv", g.getOrDefault("timeInsrv", ""));
            d.put("dashNo", g.getOrDefault("dashNo", 0));
            ProgramResult r = programs.attempt("VHINC1X", Map.of("VHINC1X_CRIT", xc, "VHINC1X_DATA", d)).result();
            int rc = r.control() == null ? 0 : r.control().returnCode();
            if (rc != 0 && rc != 4) throw new InvalidRequestException(filing.trainProblem);
            incident = incidentNo(r.record("VHINC1X_DATA"));
            ok = filing.consistOk;
        } else {
            Map<String, Object> ec = new LinkedHashMap<>();
            ec.put("revenueDate", p.date());
            ec.put("revenueTime", time);
            programs.attempt("VHINCE", Map.of("VHINCE_CRIT", ec));                 // its system date is overwritten by the revenue date
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("conNo", con);
            ProgramResult r = programs.attempt("VHEVL1F", Map.of("VHEVL1F_CRIT", fc, "VHEVL1F_DATA", d)).result();
            int rc = r.control() == null ? 0 : r.control().returnCode();
            if (rc != 0 && rc != 4) throw new InvalidRequestException(filing.trainProblem);
            incident = incidentNo(r.record("VHEVL1F_DATA"));
            ok = filing.trainOk;
        }
        return save(p, in, time, incident, false, IncidentEventsIncidentFiling.successMessage(ok, incident, con), userGroup, terminal);
    }

    /** INCD-NO: CHAR(8), or the group INCD-PFX + INCD-NO-BASE NUM(6) + INCD-SUFFIX */
    @SuppressWarnings("unchecked")
    private static String incidentNo(Map<String, Object> d) {
        Object v = d == null ? null : d.get("incdNo");
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> g = (Map<String, Object>) m;
            return (text(g, "incdPfx") + String.format("%06d", Math.abs(number(g, "incdNoBase")) % 1000000) + text(g, "incdSuffix")).strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    private static String rowGroup(Event ev, String userGroup) {
        return ev.userGroup().isEmpty() ? (userGroup == null ? "" : userGroup) : ev.userGroup();
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
