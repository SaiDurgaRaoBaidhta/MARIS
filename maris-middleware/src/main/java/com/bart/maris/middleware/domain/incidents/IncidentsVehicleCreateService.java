package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;
import static com.bart.maris.middleware.domain.incidents.IncidentsVehicleSupport.PKG;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filing a vehicle incident for one car (old VHINC08I, opened from the vehicle menus as Incident Yard, Incident Shop -
 * NOGO or the revenue problem fix). The type (YRD, SHP, REV) gives the window title, the incident prefix and report
 * type; the checks, messages and the calls VHINC8E, VHINC1A, VHINC6A, VHINXA/VHINXB, VHINVB, VHINCY, VHINXC and
 * VHINC3A follow VHINC08IS-CK-DATA2 and what it runs.
 */
public final class IncidentsVehicleCreateService {
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy"), HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Programs programs;
    private final IncidentsVehicleSupport support;
    private final Clock clock;
    private final String terminal;
    private final Map<String, String> titles, prefixes, types, names, reportTypes;
    private final String revType, defaultRevDuration, marisFunc, reportId, retNarr, partName, notAvailable, guiName, dur1;
    private final List<String> messages, carTypes, inUseTypes, yardShopTypes;
    private final int oldDays, width, tempLength, finalLength, maxMessages;
    private final String car0, dateFiller;

    public IncidentsVehicleCreateService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle bundle, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.support = new IncidentsVehicleSupport(bundle);
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        EglTexts t = support.texts();
        titles = support.byValue("VHINC08IS-ENTRY", "TYPE-IND", "GUI-TITLE");
        prefixes = support.byValue("VHINC08IS-STORE-DB", "TYPE-IND", "INCD-PFX");
        types = support.byValue("VHINC08IS-STORE-DB", "TYPE-IND", "INCD-TYPE");
        names = support.byValue("VHINC08IS-STORE-DB", "TYPE-IND", "INCIDENT-NAME");
        reportTypes = support.byValue("VHINC08IS-CA-CICS", "TYPE-IND", "REPT-TYPE");
        List<String> entryTypes = new ArrayList<>(titles.keySet());
        revType = entryTypes.get(entryTypes.size() - 1);                         // the type that defaults the discovered duration
        defaultRevDuration = t.assignedAll("VHINC08IS-ENTRY", PKG, "DISCV-DUR").get(0);
        marisFunc = t.assigned("VHINC08IS-STORE-DB", PKG, "MARIS-FUNC", null);
        reportId = t.assigned("VHINC08IS-CA-CICS", PKG, "REPT-ID", null);
        retNarr = t.assigned("VHINC08IS-AFT-NAR", PKG, "RET-CHECK", null);
        partName = support.inText("VH001IS-ACCE-RFX");
        notAvailable = t.assigned("VH001IS-MESSAGEX", PKG, "ERROR-MESSAGEX", null);
        guiName = t.assigned("VH001IS-INIT-Y", PKG, "GUI-NAME", null);
        this.messages = t.assignedAll("VHINC08IS-BLD-MSG2", PKG, "ERROR-MESSAGE-OCCR");
        if (this.messages.size() != 13) throw new IllegalStateException("VHINC08IS-BLD-MSG2 has " + this.messages.size() + " messages, expected 13");
        carTypes = t.compared("VHINC08IS-CK-DATA2", PKG, "CAR-TYPE-OCCR");
        inUseTypes = t.compared("VHINC08IS-CK-DATA2", PKG, "WHERE-IS-CAR");
        List<String> typeTests = t.compared("VHINC08IS-CK-DATA2", PKG, "TYPE-IND");
        yardShopTypes = typeTests.subList(0, 2);                                  // YRD, SHP: their own duration list
        dur1 = t.compared("VHINC08IS-CK-DATA2", PKG, "DISCV-DUR").get(0);         // the duration they may not use
        oldDays = support.limit("VHINC08IS-CK-DATA2", "DAYS-DIFF");
        dateFiller = t.assignedAll("VHINC08IS-ENTRY", PKG, "DATE-FILLER-1").get(0);
        car0 = t.compared("VHINC08IS-CK-DATA2", PKG, "CAR-NO").get(0);
        width = support.limit("VHINC08IS-MOVE-MSG", "ERROR-INDEX");
        maxMessages = support.limit("VHINC08IS-MOVE-MSG", "ERROR-LOOP-INDEX");
        tempLength = support.length("VHINC08IW", "TEMP-ERROR-MSG");
        finalLength = support.length("VHINC08IW", "FINAL-ERROR-MSG");
    }

    public record Duration(String code, String description) {}

    public record Options(String type, String title, String date, List<Duration> durations, String duration, String narrativeDate, String narrativeTime,
                          String userGroup) {}

    private String type(String type) {
        String t = type == null ? "" : type.strip().toUpperCase(java.util.Locale.ROOT);
        if (!titles.containsKey(t)) throw new InvalidRequestException("The incident type is one of " + String.join(", ", titles.keySet()) + ".");
        return t;
    }

    /** VHINC08IS-ENTRY: the window as it opens (VHCI02G's date, VHINCXA's discovered durations) */
    public Options options(String type, String userGroup) {
        String t = type(type);
        ProgramResult g = programs.attempt("VHCI02G", Map.of()).result();
        if (g.control() == null || g.control().rowCount() != 1) throw programs.error("VHCI02G", g.control());
        String date = text(g.record("VHCI02G_CRIT"), "currentDate");
        if (date.length() >= 6) date = date.substring(0, 2) + dateFiller + date.substring(3, 5) + dateFiller + date.substring(6);   // DATE-FILLER-1/2
        LocalDateTime now = LocalDateTime.now(clock);
        return new Options(t, titles.get(t), date, durations(), t.equals(revType) ? defaultRevDuration : "", MDY.format(now), HMS.format(now).substring(0, 5),
                userGroup == null ? "" : userGroup);
    }

    private List<Duration> durations() {
        Programs.Outcome o = programs.attempt("VHINCXA", Map.of());
        List<Duration> out = new ArrayList<>();
        Map<String, Object> d = o.result().record("VHINCXA_DATA");
        if (d == null) return out;
        int n = (int) number(d, "discvDurCount");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (int i = 0; i < Math.min(n, rows.size()); i++) out.add(new Duration(text(rows.get(i), "discvDur"), text(rows.get(i), "discvDurDesc")));
        return out;
    }

    /** what the user typed; date as MM-DD-YYYY (or YYYY-MM-DD), time as HHMM, narrative date/time as the window showed them */
    public record Request(String type, String car, String symptom, String duration, String date, String time, String location, String narrative,
                          String narrativeDate, String narrativeTime) {}

    public record Created(String incident, String message, List<String> warnings) {}

    /** Create: VH001IS-INIT-Y / VH001IS-ACCE-RFX, then VHINC08IS-CK-DATA2 and VHINC08IS-STORE-DB */
    public Created create(Request r, String userGroup) {
        String t = type(r.type());
        access(userGroup);
        String car = car(r.car());
        String symptom = blank(r.symptom()), duration = blank(r.duration()), location = blank(r.location()), narrative = r.narrative() == null ? "" : r.narrative();
        boolean invCar = car.equals(car0), invSymp = symptom.isEmpty(), invDur = false, invLoc = location.isEmpty(), invNarr = narrative.isBlank();
        boolean invDate = false, future = false, futureTime = false, old = false, sympMissing = false, carMissing = false, inUse = false;
        if (duration.isEmpty()) invDur = true;
        else if (yardShopTypes.contains(t)) {
            if (duration.equals(dur1)) invDur = true;
            else invDur = durations().stream().noneMatch(d -> d.code().equals(duration));
        }
        String date = r.date() == null ? "" : r.date();
        String hhmm = time4(r.time());
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("marisSubsys", "");
        e.put("dateWork", date.length() > 4 && date.charAt(4) == '-' ? date : ymd(date));
        e.put("timeWork", hhmm.isBlank() ? "" : hhmm.substring(0, 2) + ":" + hhmm.substring(2, 4) + ":00");
        Programs.Outcome eo = programs.attempt("VHINC8E", Map.of("VHINC8E_CRIT", e));
        if (!eo.ok()) invDate = true;
        else {
            long days = number(eo.result().record("VHINC8E_DATA"), "daysDiff"), secs = number(eo.result().record("VHINC8E_DATA"), "timeDiff");
            if (days < 0) future = true;
            else if (days == 0 && secs < 0) futureTime = true;
            if (!future && !futureTime && days > oldDays) old = true;
        }
        if (!invSymp) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("vagenCount", 0);
            c.put("sympCd", symptom);
            Programs.Outcome so = programs.attempt("VHINC1A", Map.of("VHINC1A_CRIT", c));
            if (so.ok() && number(so.result().record("VHINC1A_CRIT"), "vagenCount") == 0) sympMissing = true;
        }
        ProgramResult cr = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", car))).result();
        int urc = cr.control() == null ? 0 : cr.control().returnCode();
        if (urc == 0 || urc == 4) {
            Map<String, Object> d = cr.record("VHINC6A_DATA");
            String type1 = firstCarType(d);
            if (cr.control().rowCount() == 0 || !carTypes.contains(type1)) carMissing = true;
            else if (titles.containsKey(t) && inUseTypes.contains(text(d, "whereIsCar"))) inUse = true;
        } else if (urc == 100) carMissing = true;
        else throw programs.error("VHINC6A", cr.control());
        boolean errors = invCar || invSymp || invDur || invLoc || invNarr || invDate || future || futureTime || old || sympMissing || carMissing || inUse;
        if (errors) {
            List<String> out = new ArrayList<>();
            if (invCar) out.add(messages.get(0));
            else {
                if (carMissing) out.add(messages.get(1));
                if (inUse) out.add(messages.get(2));
            }
            if (invSymp) out.add(messages.get(3));
            else if (sympMissing) out.add(messages.get(4));
            if (invDur) out.add(messages.get(5));
            if (invDate) out.add(messages.get(6));
            else if (old) out.add(t.equals(revType) ? messages.get(8) : messages.get(7));
            else if (future) out.add(messages.get(9));
            else if (futureTime) out.add(messages.get(10));
            if (out.size() < maxMessages && invLoc) out.add(messages.get(11));
            if (out.size() < maxMessages && invNarr) out.add(messages.get(12));
            throw new InvalidRequestException(IncidentsVehicleSupport.join(out, width, tempLength, finalLength, maxMessages));
        }
        return store(t, car, symptom, duration, date, hhmm, location, narrative, r, userGroup);
    }

    private Created store(String t, String car, String symptom, String duration, String date, String hhmm, String location, String narrative, Request r,
                          String userGroup) {
        List<String> warnings = new ArrayList<>();
        String reset = pseudo(car, userGroup, warnings);
        String time = (hhmm.isBlank() ? "00" : hhmm.substring(0, 2)) + ":" + (hhmm.isBlank() ? "00" : hhmm.substring(2, 4)) + ":00";
        Map<String, Object> vb = new LinkedHashMap<>();
        vb.put("userTermId", terminal);
        vb.put("inputTime", time);
        vb.put("inputDate", ymd(date));
        Programs.Outcome bo = programs.attempt("VHINVB", Map.of("VHINVB_CRIT", vb));
        String rev = bo.ok() ? text(bo.result().record("VHINVB_DATA"), "revenueDate") : "";
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        crit.put("typeInd", t);
        crit.put("incdPfx", prefixes.get(t));
        crit.put("incdType", types.get(t));
        crit.put("incidentName", names.get(t));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", car);
        data.put("discvDur", duration);
        data.put("incdLoc", location);
        data.put("sympCd", symptom);
        data.put("termLastUpd", terminal);
        data.put("dateIncdOccrRev", rev);
        data.put("dateIncdOccr", ymd(date));
        data.put("timeIncdOccr", time);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        LocalDateTime now = LocalDateTime.now(clock);
        data.put("dateNarr", r.narrativeDate() == null || r.narrativeDate().isBlank() ? MDY.format(now) : r.narrativeDate());
        String nt = r.narrativeTime() == null || r.narrativeTime().isBlank() ? HMS.format(now) : r.narrativeTime();
        data.put("timeNarr", nt.length() == 5 ? nt + ":" + HMS.format(now).substring(6) : nt);
        data.put("narr", narrative);
        data.put("marisFunc", marisFunc);
        data.put("incdType", types.get(t));
        ProgramResult y = programs.attempt("VHINCY", Map.of("VHINCY_CRIT", crit, "VHINCY_DATA", data)).result();
        int urc = y.control() == null ? 0 : y.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHINCY", y.control());
        String incident = y.record("VHINCY_DATA") == null ? "" : support.group("VHINCY_DATA", "incdNo", y.record("VHINCY_DATA").get("incdNo"));
        String message = support.fixed("VHINC08IW-TRIGGER", List.of(Map.entry("SUCCESS-MESSAGE-1", texts("SUCCESS-MESSAGE-1")), Map.entry("SUCCESS-MESSAGE-2", incident),
                Map.entry("SUCCESS-MESSAGE-3", texts("SUCCESS-MESSAGE-3")), Map.entry("SUCCESS-MESSAGE-4", car), Map.entry("SUCCESS-MESSAGE-5", texts("SUCCESS-MESSAGE-5"))));
        Map<String, Object> xc = new LinkedHashMap<>();
        xc.put("termLastUpd", terminal);
        Programs.Outcome xo = programs.attempt("VHINXC", Map.of("VHINXC_CRIT", Map.of("carNo", car), "VHINXC_DATA", xc));
        if (!xo.ok() && xo.result().control().returnCode() != 100) warnings.add(xo.error());
        Map<String, Object> c3 = new LinkedHashMap<>();
        c3.put("reptId", reportId);
        c3.put("reptType", reportTypes.get(t));
        c3.put("conCar1", car);
        c3.put("incdNo", incident);
        c3.put("termId", terminal);
        c3.put("userGrp", userGroup == null ? "" : userGroup);
        c3.put("sendResetMsg", reset);
        Programs.Outcome co = programs.attempt("VHINC3A", Map.of("VHINC3A_CRIT", c3));
        if (!co.ok() && co.result().control().returnCode() != 100) warnings.add(co.error());
        return new Created(incident, message, warnings);
    }

    private final Map<String, String> successTexts = new LinkedHashMap<>();

    private String texts(String field) {
        return successTexts.computeIfAbsent(field, f -> support.texts().assignedRaw("VHINC08IS-STORE-DB", PKG, f).get(0));
    }

    /** VHINC08IS-PSEUDO: a car in a pseudo consist (VHINXA) is taken out of it (VHINXB); "Y" = VHINC3A sends the reset message */
    private String pseudo(String car, String userGroup, List<String> warnings) {
        Programs.Outcome a = programs.attempt("VHINXA", Map.of("VHINXA_CRIT", Map.of("carNo", car)));
        if (!a.ok()) return "N";
        Map<String, Object> d = a.result().record("VHINXA_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d != null && d.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<Map<String, Object>> cars = new ArrayList<>();
        List<String> cons = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> row = i < rows.size() ? rows.get(i) : Map.of();
            cars.add(Map.of("carNum", text(row, "carNo")));
            cons.add(text(row, "conNo"));
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", rows.isEmpty() ? "" : text(rows.get(0), "conNo"));
        crit.put("yrdSubLoc", rows.isEmpty() ? "" : text(rows.get(0), "yrdSubLoc"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("termLastUpd", terminal);
        data.put("userGroup", userGroup == null ? "" : userGroup);
        data.put("conData", Map.of("conNum", cons));
        data.put("dataRow", cars);
        Programs.Outcome b = programs.attempt("VHINXB", Map.of("VHINXB_CRIT", crit, "VHINXB_DATA", data));
        if (b.ok()) return "Y";
        warnings.add(b.error());
        return "N";
    }

    /** VH001IS-INIT-Y + VH001IS-ACCE-RFX: a group whose access row for the revenue fix part says N may not file */
    private void access(String userGroup) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("userGrp", userGroup == null ? "" : userGroup);
        crit.put("guiName", guiName);
        Programs.Outcome o = programs.attempt("VH01A02", Map.of("VH01A02_CRIT", crit));
        Map<String, Object> d = o.result().record("VH01A02_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d != null && d.get("userGroupAccess") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (Map<String, Object> row : rows) {
            if (text(row, "partName").equals(partName)) {
                if ("N".equals(text(row, "accessAllow"))) throw new InvalidRequestException(notAvailable);
                return;
            }
        }
    }

    /** the car as the screen's NUM(4) field holds it: four digits, "0000" when blank or not a number */
    private String car(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || c.length() > 4 || !c.chars().allMatch(Character::isDigit)) return car0;
        return "0".repeat(4 - c.length()) + c;
    }

    private static String blank(String s) {
        return s == null ? "" : s.strip();
    }

    private static String time4(String s) {
        String t = s == null ? "" : s.replace(":", "").strip();
        if (t.isEmpty()) return "    ";
        return t.length() >= 4 ? t.substring(0, 4) : "0".repeat(4 - t.length()) + t;
    }

    /** XXDATES-CONVERT "YMD": MM-DD-YYYY becomes YYYY-MM-DD; anything whose year part is not a number stays as it is */
    static String ymd(String date) {
        String d = date == null ? "" : date;
        if (d.length() >= 10 && d.substring(6, 10).chars().allMatch(Character::isDigit)) return d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5);
        return d;
    }

    private static String firstCarType(Map<String, Object> d) {
        Object v = d == null ? null : d.get("carType");
        if (v instanceof Map<?, ?> m && m.get("carTypeOccr") instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0)).strip();
        String s = v == null ? "" : v.toString();
        return s.isEmpty() ? "" : s.substring(0, 1);
    }

    /** VHINC08IS-AFT-NAR: the incident's narratives after the narrative window closes (VHINC1D "NARR") */
    public List<IncidentsVehicleSupport.Narrative> narratives(String incident) {
        Programs.Outcome o = IncidentsVehicleSupport.readIncident(programs, incident == null ? "" : incident.strip(), retNarr);
        if (!o.ok()) throw programs.error("VHINC1D", o.result().control());
        return IncidentsVehicleSupport.narratives(o.result());
    }
}
