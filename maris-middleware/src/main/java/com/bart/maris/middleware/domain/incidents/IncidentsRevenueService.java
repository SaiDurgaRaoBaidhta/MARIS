package com.bart.maris.middleware.domain.incidents;

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
 * Revenue incidents of one car (old VHINC06I): every check of the old screen (car VHINC6A, symptom VHINC1A, date
 * AFCXVLD against the revenue date VHXREVC, time), then VHINVC, VHINCX and the incident form VHINC3C.
 */
public final class IncidentsRevenueService {
    static final String PKG = IncidentsTrainService.PKG;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy"), TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int[] MONTH_DAYS = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

    private final Programs programs;
    private final IncidentScreens screens;
    private final IncidentCalls calls;
    private final IncidentsTrainService train;
    private final Clock clock;
    private final String terminal;
    private final String reptId, accessGui, accessPart, accessDenied, all, inService, errFoundOk, discovery, prefix, type, name, reptType, marisFunc;
    private final List<String> directions, carTypes;
    private final int maxDays, window, finalLength, termLength;
    private final Map<String, String> m = new LinkedHashMap<>();
    private final String part1, part3, part5;

    public IncidentsRevenueService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, IncidentScreens screens, IncidentsTrainService train,
                                   Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.screens = screens;
        this.calls = new IncidentCalls(programs);
        this.train = train;
        this.clock = clock;
        this.terminal = terminal;
        accessGui = t.assigned("VH001IS-INIT-Y", "MRSVehicleMenu", "GUI-NAME", null);
        accessPart = screens.inText("VH001IS-ACCE-RIX", PKG);
        accessDenied = t.assigned("VH001IS-MESSAGEX", PKG, "ERROR-MESSAGEX", null);
        directions = t.compared("VHINC06IS-CHK-DATA", PKG, "CON-DIRECTION");
        carTypes = t.compared("VHINC06IS-CHK-DATA", PKG, "CAR-TYPE-OCCR");
        inService = t.compared("VHINC06IS-CHK-DATA", PKG, "WHERE-IS-CAR").get(0);
        all = t.compared("VHINC06IS-CHK-DATA", PKG, "SPEED").get(0);
        maxDays = screens.comparedNumber("VHINC06IS-DATE-CHK", PKG, "JULIAN-DAYS-DIFF");
        errFoundOk = t.compared("VHINC06IS-STORE-DB", PKG, "ERR-FOUND").get(0);
        discovery = t.assigned("VHINC06IS-MOVE-RE2", PKG, "DISCV-DUR", null);
        prefix = t.assigned("VHINC06IS-STORE-DB", PKG, "INCD-PFX", null);
        type = t.assigned("VHINC06IS-STORE-DB", PKG, "INCD-TYPE", null);
        name = t.assigned("VHINC06IS-STORE-DB", PKG, "INCIDENT-NAME", null);
        reptType = t.assigned("VHINC06IS-CA-CIC2", PKG, "REPT-TYPE", null);
        reptId = t.assigned("VHINC06IS-CA-CIC2", PKG, "REPT-ID", null);
        marisFunc = t.assigned("VHINC06IS-STORE-DB", PKG, "MARIS-FUNC", null);
        window = screens.field("VHINC06IW", PKG, "TEMP-MSG").occurs();
        finalLength = screens.field("VHINC06IW-TRIGGER", PKG, "SUCCESS-MESSAGE").length();
        termLength = 8;                                                 // VHINC3C_CRIT.TERM-ID is CHAR(8)
        for (String[] k : new String[][] {{"car", "Invalid car"}, {"notInUse", "not in use"}, {"symp", "Invalid symp"}, {"sympMissing", "does not exist"},
                {"date", "Invalid date of"}, {"old", "older than"}, {"future", "Future date"}, {"timeMissing", "Invalid time of"}, {"time", "Invalid time."},
                {"futureTime", "Future time"}, {"speed", "Invalid speed"}, {"speedValue", "valid value"}, {"direction", "Invalid direction"},
                {"location", "Invalid location"}, {"narr", "Invalid incident narr"}}) {
            m.put(k[0], t.assigned("VHINC06IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", k[1]));
        }
        part1 = t.assignedRaw("VHINC06IS-STORE-DB", PKG, "SUCCESS-MESSAGE-1").get(0);
        part3 = t.assignedRaw("VHINC06IS-STORE-DB", PKG, "SUCCESS-MESSAGE-3").get(0);
        part5 = t.assignedRaw("VHINC06IS-STORE-DB", PKG, "SUCCESS-MESSAGE-5").get(0);
    }

    /** what the screen opens with (VHINC06IS-ENTRY): the revenue date; the time is left for the user */
    public IncidentsTrainService.Defaults defaults() {
        return new IncidentsTrainService.Defaults(train.defaults().date(), "");
    }

    public record Create(String carNo, String symptom, String date, String time, String speed, String direction, String location, String narrative) {}

    public record Result(String message, String incidentNo) {}

    /** Create (VH01A02 access, VHINC06IS-CHK-DATA, VHINC06IS-STORE-DB) */
    public Result create(Create c, String userGroup) {
        calls.access(userGroup, accessGui, accessPart, accessDenied);
        String carIn = s(c.carNo());
        String car = !carIn.isEmpty() && carIn.length() <= 4 && carIn.chars().allMatch(Character::isDigit) ? String.format("%04d", Integer.parseInt(carIn)) : "0000";
        String symp = s(c.symptom()), date = s(c.date()), speed = s(c.speed()), dir = s(c.direction()), loc = s(c.location());
        String narr = c.narrative() == null ? "" : c.narrative();
        String hhmm = IncidentScreens.fit(s(c.time()), 4);                 // DISP-TIME-INCD-OCCR is CHAR(4): "HHMM" as typed
        LocalDateTime now = LocalDateTime.now(clock);
        boolean badCar = car.equals("0000"), carMissing = false, notInUse = false;
        boolean badDate = false, old = false, future = false, badTime = false, futureTime = false;
        long daysDiff = 0;
        if (date.isEmpty()) badDate = true;
        else {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("dateError", "N");
            v.put("formDate", IncidentsTrainService.ymd(date));
            v.put("otherSqlError", "N");
            Map<String, Object> r = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", v)).result().record("AFCXVLD_CRIT");
            String de = text(r, "dateError"), se = text(r, "otherSqlError");
            if ("Y".equals(de) && "N".equals(se)) badDate = true;
            else if (!("N".equals(de) && "Y".equals(se))) {
                Map<String, Object> rc = new LinkedHashMap<>();
                rc.put("selIdx", 3);
                String revenue = screens.text(programs.attempt("VHXREVC", Map.of("VHXREVC_CRIT", rc)).result().record("VHXREVC_DATA"), "VHXREVC_DATA", PKG, "REVENUE-DATE");
                int[] j1 = julian(revenue), j2 = julian(date);
                if (j1[0] == j2[0]) {
                    if (j2[1] > j1[1]) future = true;
                    else { daysDiff = (j1[0] * 1000L + j1[1]) - (j2[0] * 1000L + j2[1]); if (daysDiff > maxDays) old = true; }
                } else if (Math.abs(j1[0] - j2[0]) > 1) old = true;
                else if (j1[0] > j2[0]) {
                    int d1 = j1[1] + (j2[0] % 4 > 0 ? 365 : 366);
                    daysDiff = Math.abs(d1 - j2[1]);
                    if (daysDiff > maxDays) old = true;
                } else future = true;
            }
        }
        boolean noTime = hhmm.isBlank(), noSpeed = speed.isEmpty(), noLoc = loc.isEmpty(), noNarr = narr.isBlank();
        boolean badDir = dir.isEmpty() || !directions.contains(dir);
        boolean badSpeed = !noSpeed && IncidentCalls.badSpeed(speed, all);
        Map<String, Object> symptomCrit = new LinkedHashMap<>();
        if (!noTime) {
            if (!hhmm.chars().allMatch(Character::isDigit)) badTime = true;
            else {
                int h = Integer.parseInt(hhmm.substring(0, 2)), mi = Integer.parseInt(hhmm.substring(2));
                if (h > 24 || (h == 24 && mi > 0) || (h == 0 && mi == 0) || mi > 59) badTime = true;
                else if (daysDiff == 0) {
                    symptomCrit.put("revenueTime", hhmm.substring(0, 2) + " " + hhmm.substring(2) + " ");
                    symptomCrit.put("allowTime", now.format(TIME));
                    long diff = Math.abs((h * 60L + mi) - (now.getHour() * 60L + now.getMinute()));   // TOTAL-MIN-DIFF is unsigned
                    if (!(h <= 23 && now.getHour() <= 3) && diff > 60) futureTime = true;
                }
            }
        }
        boolean noSymp = symp.isEmpty(), sympMissing = !noSymp && !calls.symptomExists(symp, symptomCrit);
        if (!badCar) {
            ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", car))).result();
            int urc = r.control() == null ? 0 : r.control().returnCode();
            if (urc == 0 || urc == 4) {
                Map<String, Object> d = r.record("VHINC6A_DATA");
                String first = IncidentScreens.firstCarType(d);
                if (r.control() == null || r.control().rowCount() == 0 || !carTypes.contains(first)) carMissing = true;
                else if (!inService.equals(text(d, "whereIsCar"))) notInUse = true;
            } else if (urc == 100) carMissing = true;
        }
        List<String> errors = new ArrayList<>();
        // a blank / zero car is refused without a message: CHK-DATA blanks CAR-NO before BLD-MSG looks for "0000" (old quirk, kept)
        if (!badCar && carMissing) errors.add(m.get("car"));
        if (!badCar && notInUse) errors.add(m.get("notInUse"));
        if (noSymp) errors.add(m.get("symp"));
        else if (sympMissing) errors.add(m.get("sympMissing"));
        if (badDate) errors.add(m.get("date"));
        else if (old) errors.add(m.get("old"));
        else if (future) errors.add(m.get("future"));
        if (noTime) errors.add(m.get("timeMissing"));
        else if (badTime) errors.add(m.get("time"));
        else if (futureTime) errors.add(m.get("futureTime"));
        if (noSpeed) errors.add(m.get("speed"));
        else if (badSpeed) errors.add(m.get("speedValue"));
        if (badDir) errors.add(m.get("direction"));
        if (errors.size() < 6 && noLoc) errors.add(m.get("location"));
        if (errors.size() < 6 && noNarr) errors.add(m.get("narr"));
        if (!errors.isEmpty() || badCar) throw new InvalidRequestException(IncidentScreens.join(errors, window, finalLength));

        String time = hhmm.substring(0, 2) + ":" + hhmm.substring(2) + ":00";
        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("userTermId", terminal);
        valid.put("inputDate", IncidentsTrainService.ymd(date));
        valid.put("inputTime", time);
        programs.run("VHINVC", Map.of("VHINVC_CRIT", valid));
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        crit.put("incdType", type);
        crit.put("incdPfx", prefix);
        crit.put("incidentName", name);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", car);
        data.put("incdType", type);
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
        data.put("marisFunc", marisFunc);
        ProgramResult r = programs.run("VHINCX", Map.of("VHINCX_CRIT", crit, "VHINCX_DATA", data));
        if (!errFoundOk.equals(text(r.record("VHINCX_CRIT"), "errFound"))) throw programs.error("VHINCX", r.control());
        String no = screens.text(r.record("VHINCX_DATA"), "VHINCX_DATA", PKG, "INCD-NO");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("reptId", reptId);
        form.put("reptType", reptType);
        form.put("conCar1", car);
        form.put("incdNo", no);
        form.put("termId", IncidentScreens.fit(terminal, termLength).strip());
        form.put("userGrp", userGroup);
        programs.attempt("VHINC3C", Map.of("VHINC3C_CRIT", form));
        return new Result(screens.parts("VHINC06IW-TRIGGER", PKG, "SUCCESS-MESSAGE", part1, no, part3, car, part5), no);
    }

    /** XDATECON-GREG2JUL of an MM-DD-YYYY date: {year, day of year} (a year divisible by 4 is a leap year) */
    static int[] julian(String mdy) {
        String d = IncidentScreens.fit(mdy, 10);
        int mo = n(d.substring(0, 2)), dy = n(d.substring(3, 5)), yr = n(d.substring(6, 10));
        int ddd = (mo >= 1 && mo <= 12 ? MONTH_DAYS[mo - 1] : 0) + dy;
        if (yr % 4 == 0 && mo > 2) ddd++;
        return new int[] {yr, ddd};
    }

    private static int n(String s) {
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return 0; }
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
