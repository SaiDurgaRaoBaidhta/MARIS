package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
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
 * Train incidents (old VHINC01I): an incident of a whole consist, recorded on the train's pseudo car. Add creates one
 * (VHINC1F, after VHINVC), Update lists one (VHINC1D) and changes its symptom, speed, direction and location (VHINC1C).
 * Both buttons first check the user group may use train incidents (VH01A02), like the old screen.
 */
public final class IncidentsTrainService {
    static final String PKG = "MRSVehicleIncident";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy"), TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Programs programs;
    private final IncidentScreens screens;
    private final IncidentCalls calls;
    private final Clock clock;
    private final String terminal;
    private final String trainCar, discovery, retCheck, accessGui, accessPart, accessDenied, all, notFound, invalidIncident;
    private final List<String> directions;
    private final int revenueStartMinutes, window, finalLength;
    private final Map<String, String> m = new LinkedHashMap<>();
    private final String created1, created3, created5, updated5;

    public IncidentsTrainService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, IncidentScreens screens, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.screens = screens;
        this.calls = new IncidentCalls(programs);
        this.clock = clock;
        this.terminal = terminal;
        trainCar = t.assigned("VHINC01IS-MOVE-RE2", PKG, "CAR-NO", null);
        discovery = t.assigned("VHINC01IS-MOVE-RE2", PKG, "DISCV-DUR", null);
        retCheck = t.assigned("VHINC01IS-RET-INCD", PKG, "RET-CHECK", null);
        notFound = t.assigned("VHINC01IS-RET-INCD", PKG, "SUCCESS-MESSAGE", "not found");
        invalidIncident = t.assigned("VHINC01IS-RET-INCD", PKG, "SUCCESS-MESSAGE", "Invalid");
        accessGui = t.assigned("VH001IS-INIT-Y", "MRSVehicleMenu", "GUI-NAME", null);
        accessPart = screens.inText("VH001IS-ACCE-TIX", PKG);
        accessDenied = t.assigned("VH001IS-MESSAGEX", PKG, "ERROR-MESSAGEX", null);
        directions = t.compared("VHINC01IS-CHK-DATA", PKG, "CON-DIRECTION");
        all = t.compared("VHINC01IS-CHK-DATA", PKG, "SPEED").get(0);
        revenueStartMinutes = screens.comparedNumber("VHINC01IS-REV-TIME", PKG, "SYS-MINUTES");
        window = screens.field("VHINC01IW", PKG, "TEMP-MSG").occurs();
        finalLength = screens.field("VHINC01IW-TRIGGER", PKG, "SUCCESS-MESSAGE").length();
        for (String[] k : new String[][] {{"conNo", "Invalid consist"}, {"conNotFound", "not found"}, {"conNotInService", "not in service"},
                {"conNoCar", "has no car"}, {"symp", "Invalid symp"}, {"sympMissing", "does not exist"}, {"timeMissing", "Invalid time of"},
                {"time", "Invalid time."}, {"futureTime", "Future time"}, {"speed", "Invalid speed"}, {"speedValue", "valid value"},
                {"direction", "Invalid direction"}, {"location", "Invalid location"}, {"narr", "Invalid incident narr"}, {"date", "Invalid date of"}}) {
            m.put(k[0], t.assigned("VHINC01IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", k[1]));
        }
        List<String> p1 = t.assignedRaw("VHINC01IS-STORE-DB", PKG, "SUCCESS-MESSAGE-1");
        List<String> p3 = t.assignedRaw("VHINC01IS-STORE-DB", PKG, "SUCCESS-MESSAGE-3");
        List<String> p5 = t.assignedRaw("VHINC01IS-STORE-DB", PKG, "SUCCESS-MESSAGE-5");
        created1 = p1.get(0);
        created3 = p3.get(0);
        created5 = p5.get(0);
        updated5 = p5.get(1);
    }

    /** what Add starts with (VHINC01IS-ADD / REV-TIME): the revenue date (VHINCD before 3 am, else the database date) and the time now */
    public record Defaults(String date, String time) {}

    public Defaults defaults() {
        String db = text(programs.run("VHINCD", Map.of()).record("VHINCD_DATA"), "revenueDate");
        ProgramResult g = programs.run("VHCI02G", Map.of());
        Map<String, Object> crit = g.record("VHCI02G_CRIT");
        String date = text(crit, "currentDate"), time = text(crit, "currentTime");
        int minutes = num(time, 0, 2) * 60 + num(time, 3, 5);
        String revenue = minutes < revenueStartMinutes ? mdy(db) : date;
        LocalDateTime now = LocalDateTime.now(clock);
        return new Defaults(revenue, now.format(TIME).substring(0, 2) + now.format(TIME).substring(3, 5));
    }

    public record Create(String consistNo, String symptom, String date, String time, String speed, String direction, String location, String narrative) {}

    public record Result(String message, String incidentNo) {}

    /** Add (VHINC01IS-CHK-DATA, CHECK-ENTRY 1): every check of the old screen, then VHINVC and VHINC1F */
    public Result create(Create c, String userGroup) {
        access(userGroup);
        String con = s(c.consistNo()), symp = s(c.symptom()), date = s(c.date()), speed = s(c.speed()), dir = s(c.direction()), loc = s(c.location());
        String narr = c.narrative() == null ? "" : c.narrative();
        String hhmm = IncidentScreens.fit(s(c.time()), 4);                 // DISP-TIME-INCD-OCCR is CHAR(4): "HHMM" as typed
        LocalDateTime now = LocalDateTime.now(clock);
        boolean noCon = con.isEmpty(), noSymp = symp.isEmpty(), noDate = date.isEmpty(), noSpeed = speed.isEmpty(), noLoc = loc.isEmpty(), noNarr = narr.isBlank();
        boolean badDir = dir.isEmpty() || !directions.contains(dir);
        boolean badSpeed = !noSpeed && badSpeed(speed);
        boolean badTime = false, futureTime = false;
        Map<String, Object> symptomCrit = new LinkedHashMap<>();
        if (!hhmm.isBlank()) {
            if (hhmm.chars().allMatch(Character::isDigit)) {
                int h = Integer.parseInt(hhmm.substring(0, 2)), mi = Integer.parseInt(hhmm.substring(2));
                if (h > 24 || (h == 24 && mi > 0) || mi > 59 || (h == 0 && mi == 0)) { badTime = true; futureTime = true; }
            } else {
                badTime = true;
                futureTime = true;
            }
            if (!futureTime) {
                String allow = now.format(TIME);
                symptomCrit.put("revenueTime", hhmm.substring(0, 2) + " " + hhmm.substring(2) + " ");
                symptomCrit.put("allowTime", allow);
                int h = Integer.parseInt(hhmm.substring(0, 2)), allowH = now.getHour();
                long diff = Math.abs((h * 60L + Integer.parseInt(hhmm.substring(2))) - (allowH * 60L + now.getMinute()));   // TOTAL-MIN-DIFF is unsigned
                if (!(h <= 23 && allowH >= 0 && allowH <= 3) && diff > 60) futureTime = true;
            }
        }
        boolean sympMissing = !noSymp && !symptomExists(symp, symptomCrit);
        boolean conNotFound = false, conNotInService = false, conNoCar = false;
        if (!noCon) {
            ProgramResult b = programs.run("VHINC1B", Map.of("VHINC1B_DATA", Map.of("conNo", con)));
            Map<String, Object> d = b.record("VHINC1B_DATA");
            if (b.control() == null || b.control().rowCount() == 0) conNotFound = true;
            else if (!"Y".equals(text(d, "inUseInd"))) conNotInService = true;
            else if ("Y".equals(text(d, "noCarsInd"))) conNoCar = true;
        }
        List<String> errors = new ArrayList<>();
        if (noCon) errors.add(m.get("conNo"));
        else {
            if (conNotFound) errors.add(m.get("conNotFound"));
            if (conNotInService) errors.add(m.get("conNotInService"));
            if (conNoCar) errors.add(m.get("conNoCar"));
        }
        if (noSymp) errors.add(m.get("symp"));
        else if (sympMissing) errors.add(m.get("sympMissing"));
        if (noDate) errors.add(m.get("date"));
        if (badTime) errors.add(m.get("time"));
        else if (futureTime) errors.add(m.get("futureTime"));
        if (noSpeed) errors.add(m.get("speed"));
        else if (badSpeed) errors.add(m.get("speedValue"));
        if (badDir) errors.add(m.get("direction"));
        if (errors.size() < 6 && noLoc) errors.add(m.get("location"));
        if (errors.size() < 6 && noNarr) errors.add(m.get("narr"));
        if (!errors.isEmpty()) throw new InvalidRequestException(IncidentScreens.join(errors, window, finalLength));

        String time = hhmm.substring(0, 2) + ":" + hhmm.substring(2) + ":00";
        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("userTermId", terminal);
        valid.put("inputDate", ymd(date));
        valid.put("inputTime", time);
        programs.run("VHINVC", Map.of("VHINVC_CRIT", valid));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", trainCar);
        data.put("conNo", con);
        data.put("dateIncdOccrRev", date);
        data.put("timeIncdOccr", time);
        data.put("incdLoc", loc);
        data.put("sympCd", symp);
        data.put("speed", speed);
        data.put("discvDur", discovery);
        data.put("conDirection", dir);
        data.put("userGrp", userGroup);
        data.put("termLastUpd", terminal);
        data.put("dateNarr", now.format(DATE));
        data.put("timeNarr", now.format(TIME));
        data.put("narr", narr);
        ProgramResult r = programs.run("VHINC1F", Map.of("VHINC1F_CRIT", Map.of("conNo", con), "VHINC1F_DATA", data));
        String no = screens.text(r.record("VHINC1F_DATA"), "VHINC1F_DATA", PKG, "INCD-NO");
        return new Result(screens.parts("VHINC01IW-TRIGGER", PKG, "SUCCESS-MESSAGE", created1, no, created3, con, created5), no);
    }

    public record NarrativeRow(int narrNo, String userGrp, String dateNarr, String timeNarr, String narr) {}

    public record Incident(String incidentNo, String consistNo, String dashNo, String symptom, String discovery, String date, String time, String speed,
                           String direction, String location, List<NarrativeRow> narratives) {}

    /** Update's List (VHINC01IS-RET-INCD, VHINC1D "BOTH") */
    public Incident incident(String incidentNo) {
        String no = s(incidentNo);
        if (no.isEmpty()) throw new InvalidRequestException(invalidIncident);
        ProgramResult r = programs.run("VHINC1D", Map.of("VHINC1D_CRIT", Map.of("incdNo", no, "retCheck", retCheck)));
        if (r.control() != null && r.control().returnCode() == 100) throw new InvalidRequestException(notFound);
        Map<String, Object> d = r.record("VHINC1D_DATA");
        int count = r.control() == null ? 0 : r.control().rowCount();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d.get("dataNarrRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<NarrativeRow> narr = new ArrayList<>();
        for (int i = 0; i < Math.min(count, rows.size()); i++) {
            Map<String, Object> x = rows.get(i);
            narr.add(new NarrativeRow((int) number(x, "narrNo"), text(x, "userGrp"), text(x, "dateNarr"), text(x, "timeNarr"), raw(x, "narr").stripTrailing()));
        }
        String time = screens.text(d, "VHINC1D_DATA", PKG, "TIME-INCD-OCCR");
        String hhmm = IncidentScreens.fit(time, 5).substring(0, 2) + IncidentScreens.fit(time, 5).substring(3, 5);
        return new Incident(no, text(d, "conNo"), String.format("%02d", number(d, "dashNo") % 100), text(d, "sympCd"), text(d, "discvDur"),
                screens.text(d, "VHINC1D_DATA", PKG, "DATE-INCD-OCCR"), hhmm.strip(), text(d, "speed"), text(d, "conDirection"), text(d, "incdLoc"), narr);
    }

    public record Change(String symptom, String speed, String direction, String location, Integer narrativeIndex) {}

    /** Update's Change (VHINC01IS-CHK-DATA, CHECK-ENTRY 2, then VHINC1C) */
    public Result change(String incidentNo, Change c, String userGroup) {
        access(userGroup);
        Incident inc = incident(incidentNo);
        String symp = s(c.symptom()), speed = s(c.speed()), dir = s(c.direction()), loc = s(c.location());
        boolean badSpeed = !speed.isEmpty() && badSpeed(speed);
        boolean sympMissing = !symp.isEmpty() && !symptomExists(symp, new LinkedHashMap<>());
        List<String> errors = new ArrayList<>();
        if (symp.isEmpty()) errors.add(m.get("symp"));
        else if (sympMissing) errors.add(m.get("sympMissing"));
        if (speed.isEmpty()) errors.add(m.get("speed"));
        else if (badSpeed) errors.add(m.get("speedValue"));
        if (dir.isEmpty()) errors.add(m.get("direction"));
        if (loc.isEmpty()) errors.add(m.get("location"));
        if (!errors.isEmpty()) throw new InvalidRequestException(IncidentScreens.join(errors, window, finalLength));
        int index = c.narrativeIndex() == null ? 1 : c.narrativeIndex();
        String shownGroup = index >= 1 && index <= inc.narratives().size() ? inc.narratives().get(index - 1).userGrp() : "";   // the narrative's group, as the old screen sends
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("speed", speed);
        data.put("sympCd", symp);
        data.put("conDirection", dir);
        data.put("incdLoc", loc);
        data.put("userGrp", shownGroup);
        data.put("termLastUpd", terminal);
        programs.run("VHINC1C", Map.of("VHINC1C_CRIT", Map.of("incdNo", inc.incidentNo()), "VHINC1C_DATA", data));
        return new Result(screens.parts("VHINC01IW-TRIGGER", PKG, "SUCCESS-MESSAGE", created1, inc.incidentNo(), created3, inc.consistNo(), updated5),
                inc.incidentNo());
    }

    private void access(String userGroup) {
        calls.access(userGroup, accessGui, accessPart, accessDenied);
    }

    private boolean symptomExists(String symptom, Map<String, Object> crit) {
        return calls.symptomExists(symptom, crit);
    }

    private boolean badSpeed(String speed) {
        return IncidentCalls.badSpeed(speed, all);
    }

    /** XXDATES-CONVERT "YMD": MM-DD-YYYY to YYYY-MM-DD when its year is numeric */
    static String ymd(String mdy) {
        String d = IncidentScreens.fit(mdy, 10);
        return d.substring(6, 10).chars().allMatch(Character::isDigit) ? d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5) : mdy;
    }

    /** XXDATES-CONVERT "MDY": YYYY-MM-DD to MM-DD-YYYY when its year is numeric */
    static String mdy(String ymd) {
        String d = IncidentScreens.fit(ymd, 10);
        return d.substring(0, 4).chars().allMatch(Character::isDigit) ? d.substring(5, 7) + "-" + d.substring(8, 10) + "-" + d.substring(0, 4) : ymd;
    }

    private static int num(String s, int from, int to) {
        String p = IncidentScreens.fit(s, to).substring(from, to).strip();
        try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 0; }
    }

    private static String raw(Map<String, Object> rec, String field) {
        Object v = rec == null ? null : rec.get(field);
        return v == null ? "" : v.toString();
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
