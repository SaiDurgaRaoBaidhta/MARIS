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
 * Vehicle incidents of one car (old VHINC03I), opened for one incident type: event (EVT), shop (SHN) or miscellaneous
 * (MSC). Every check of the old screen (car VHINC6A, symptom VHINC1A, discovered duration VHINCXA, date AFCXVLD, time),
 * then VHINVB, VHINCZ and the incident form VHINC3A. The symptom's standard narrative comes from VHINC3B.
 */
public final class IncidentsCarService {
    static final String PKG = IncidentsTrainService.PKG;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy"), TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Programs programs;
    private final IncidentScreens screens;
    private final Clock clock;
    private final String terminal, reptId, marisFunc, shopType, shopDiscovery;
    private final List<String> types, carTypes;
    private final Map<String, String> titles = new LinkedHashMap<>(), prefixes = new LinkedHashMap<>(), incdTypes = new LinkedHashMap<>(),
            names = new LinkedHashMap<>(), reptTypes = new LinkedHashMap<>();
    private final int maxDays, maxYears, window, finalLength;
    private final Map<String, String> m = new LinkedHashMap<>();
    private final String part1, part3, part5;

    public IncidentsCarService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, IncidentScreens screens, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.screens = screens;
        this.clock = clock;
        this.terminal = terminal;
        types = t.compared("VHINC03IS-STORE-DB", PKG, "TYPE-IND");
        zip(types, t.assignedUnder("VHINC03IS-STORE-DB", PKG, "INCD-PFX", "TYPE-IND"), prefixes);
        zip(types, t.assignedUnder("VHINC03IS-STORE-DB", PKG, "INCD-TYPE", "TYPE-IND"), incdTypes);
        zip(types, t.assignedUnder("VHINC03IS-STORE-DB", PKG, "INCIDENT-NAME", "TYPE-IND"), names);
        zip(t.compared("VHINC03IS-CA-CICS", PKG, "TYPE-IND"), t.assignedUnder("VHINC03IS-CA-CICS", PKG, "REPT-TYPE", "TYPE-IND"), reptTypes);
        zip(t.compared("VHINC03IS-ENTRY", PKG, "TYPE-IND"), t.assignedUnder("VHINC03IS-ENTRY", PKG, "GUI-TITLE", "TYPE-IND"), titles);
        reptId = t.assigned("VHINC03IS-CA-CICS", PKG, "REPT-ID", null);
        marisFunc = t.assigned("VHINC03IS-STORE-DB", PKG, "MARIS-FUNC", null);
        List<String> clr = t.compared("VHINC03IS-CLR-FLDS", PKG, "TYPE-IND");     // EVT, MSC, SHN
        shopType = clr.get(clr.size() - 1);
        shopDiscovery = t.assignedAll("VHINC03IS-CLR-FLDS", PKG, "DISCV-DUR").get(0);       // the non-blank one: SHN's fixed duration
        carTypes = t.compared("VHINC03IS-CHK-DATA", PKG, "CAR-TYPE-OCCR");
        maxDays = screens.comparedNumber("VHINC03IS-DATE-CHK", PKG, "JULIAN-DAYS-DIFF");
        maxYears = screens.comparedNumber("VHINC03IS-DATE-CHK", PKG, "JULIAN-DATE-YR-DIFF");
        window = screens.field("VHINC03IW", PKG, "TEMP-MSG").occurs();
        finalLength = screens.field("VHINC03IW-TRIGGER", PKG, "SUCCESS-MESSAGE").length();
        for (String[] k : new String[][] {{"car", "Invalid car"}, {"carMissing", "Car no does not exist"}, {"symp", "Invalid symp"},
                {"sympMissing", "Symp cd does not exist"}, {"discovery", "discovered dur"}, {"badDate", "Invalid date."}, {"date", "Invalid date of"},
                {"old", "too old"}, {"future", "Future date"}, {"timeMissing", "Invalid time of"}, {"time", "Invalid time."}, {"futureTime", "Future Time"},
                {"location", "Invalid location"}, {"narr", "Invalid incident narr"}}) {
            m.put(k[0], t.assigned("VHINC03IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", k[1]));
        }
        m.put("carMissing", t.assignedRaw("VHINC03IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR").stream().filter(x -> x.contains("does not exist") && x.contains("Car"))
                .findFirst().orElseThrow());                                    // " Car no does not exist." keeps its leading blank
        part1 = t.assignedRaw("VHINC03IS-STORE-DB", PKG, "SUCCESS-MESSAGE-1").get(0);
        part3 = t.assignedRaw("VHINC03IS-STORE-DB", PKG, "SUCCESS-MESSAGE-3").get(0);
        part5 = t.assignedRaw("VHINC03IS-STORE-DB", PKG, "SUCCESS-MESSAGE-5").get(0);
    }

    private static void zip(List<String> keys, List<String> values, Map<String, String> into) {
        for (int i = 0; i < Math.min(keys.size(), values.size()); i++) into.put(keys.get(i), values.get(i));
    }

    public record Discovery(String code, String description) {}

    public record Options(String type, String title, String date, List<Discovery> discoveries, String fixedDiscovery) {}

    /** VHINC03IS-ENTRY / CLR-FLDS: the window's title, today's date (VHCI02G), the discovered durations (VHINCXA); a shop incident's is fixed */
    public Options options(String type) {
        String ty = type(type);
        String date = text(programs.run("VHCI02G", Map.of()).record("VHCI02G_CRIT"), "currentDate");
        return new Options(ty, titles.getOrDefault(ty, ""), date, discoveries(), ty.equals(shopType) ? shopDiscovery : "");
    }

    /** VHINC03IS-SYMP-CD (VHINC3B): the symptom's standard narrative, "" when it has none */
    public String symptomNarrative(String symptom) {
        String s = s(symptom);
        if (s.isEmpty()) return "";
        var o = programs.attempt("VHINC3B", Map.of("VHINC3B_CRIT", Map.of("sympCd", s)));
        return o.ok() ? text(o.result().record("VHINC3B_CRIT"), "sympCdNarr") : "";
    }

    private List<Discovery> discoveries() {
        ProgramResult r = programs.run("VHINCXA", Map.of());
        Map<String, Object> d = r.record("VHINCXA_DATA");
        int n = (int) Programs.number(d, "discvDurCount");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d != null && d.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<Discovery> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, rows.size()); i++) out.add(new Discovery(text(rows.get(i), "discvDur"), text(rows.get(i), "discvDurDesc")));
        return out;
    }

    public record Create(String type, String carNo, String symptom, String discovery, String date, String time, String location, String narrative) {}

    public record Result(String message, String incidentNo) {}

    /** Create (VHINC03IS-CHK-DATA, VHINC03IS-STORE-DB) */
    public Result create(Create c, String userGroup) {
        String ty = type(c.type());
        String carIn = s(c.carNo());
        String car = !carIn.isEmpty() && carIn.length() <= 4 && carIn.chars().allMatch(Character::isDigit) ? String.format("%04d", Integer.parseInt(carIn)) : "0000";
        String symp = s(c.symptom()), disc = ty.equals(shopType) ? shopDiscovery : s(c.discovery()), date = s(c.date()), loc = s(c.location());
        String narr = c.narrative() == null ? "" : c.narrative();
        String hhmm = IncidentScreens.fit(s(c.time()), 4);                 // DISP-TIME-INCD-OCCR is CHAR(4): "HHMM" as typed
        LocalDateTime now = LocalDateTime.now(clock);
        boolean badCar = car.equals("0000"), noSymp = symp.isEmpty(), noDate = date.isEmpty(), noTime = hhmm.isBlank(), noLoc = loc.isEmpty(), noNarr = narr.isBlank();
        boolean badDisc = disc.isEmpty() || discoveries().stream().noneMatch(x -> x.code().equals(disc));
        boolean badDate = false, old = false, future = false, badTime = false, futureTime = false;
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("dateError", "N");
        String d10 = IncidentScreens.fit(date, 10);                          // TEMP-DATE: month, "-", day, "-", year
        v.put("formDate", IncidentsTrainService.ymd(d10.substring(0, 2) + "-" + d10.substring(3, 5) + "-" + d10.substring(6, 10)));
        v.put("otherSqlError", "N");
        {
            Map<String, Object> r = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", v)).result().record("AFCXVLD_CRIT");
            String de = text(r, "dateError"), se = text(r, "otherSqlError");
            if ("Y".equals(de) && "N".equals(se)) badDate = true;
            else if (!("N".equals(de) && "Y".equals(se))) {
                int[] j1 = IncidentsRevenueService.julian(now.format(DATE)), j2 = IncidentsRevenueService.julian(date);
                if (j1[0] == j2[0]) {
                    if (j2[1] > j1[1]) future = true;
                    else if (j1[1] - j2[1] > maxDays) old = true;
                } else if (Math.abs(j1[0] - j2[0]) > maxYears) old = true;
                else if (j1[0] > j2[0]) {
                    if (j1[1] + (j2[0] % 4 > 0 ? 365 : 366) - j2[1] > maxDays) old = true;
                } else future = true;
            }
        }
        if (!hhmm.chars().allMatch(Character::isDigit)) badTime = true;
        else {
            int h = Integer.parseInt(hhmm.substring(0, 2)), mi = Integer.parseInt(hhmm.substring(2));
            if (h > 24 || (h == 24 && mi > 0) || mi > 59) badTime = true;
            else if (IncidentScreens.fit(date, 10).equals(now.format(DATE))) {
                long diff = Math.abs((h * 60L + mi) - (now.getHour() * 60L + now.getMinute()));   // TOTAL-MIN-DIFF is unsigned
                if (diff > 60) futureTime = true;
            }
        }
        Map<String, Object> symptomCrit = new LinkedHashMap<>();
        if (!badTime && IncidentScreens.fit(date, 10).equals(now.format(DATE))) {
            symptomCrit.put("revenueTime", hhmm.substring(0, 2) + ":" + hhmm.substring(2) + " ");
            symptomCrit.put("allowTime", now.format(TIME));
        }
        boolean sympMissing = !noSymp && !new IncidentCalls(programs).symptomExists(symp, symptomCrit);
        boolean carMissing = false;
        ProgramResult cr = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", car))).result();
        int urc = cr.control() == null ? 0 : cr.control().returnCode();
        if (urc == 0 || urc == 4) {
            String first = IncidentScreens.firstCarType(cr.record("VHINC6A_DATA"));
            if (cr.control() == null || cr.control().rowCount() == 0 || !carTypes.contains(first)) carMissing = true;
        } else if (urc == 100) carMissing = true;
        List<String> errors = new ArrayList<>();
        if (badCar) errors.add(m.get("car"));
        else if (carMissing) errors.add(m.get("carMissing"));
        if (noSymp) errors.add(m.get("symp"));
        else if (sympMissing) errors.add(m.get("sympMissing"));
        if (badDisc) errors.add(m.get("discovery"));
        if (badDate) errors.add(m.get("badDate"));
        else if (noDate) errors.add(m.get("date"));
        else {
            if (old) errors.add(m.get("old"));
            if (future) errors.add(m.get("future"));
        }
        if (noTime) errors.add(m.get("timeMissing"));
        else if (badTime) errors.add(m.get("time"));
        else if (futureTime) errors.add(m.get("futureTime"));
        if (errors.size() < 6 && noLoc) errors.add(m.get("location"));
        if (errors.size() < 6 && noNarr) errors.add(m.get("narr"));
        if (!errors.isEmpty()) throw new InvalidRequestException(IncidentScreens.join(errors, window, finalLength));

        String time = hhmm.substring(0, 2) + ":" + hhmm.substring(2) + ":00";
        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("userTermId", terminal);
        valid.put("inputDate", IncidentsTrainService.ymd(date));
        valid.put("inputTime", time);
        programs.run("VHINVB", Map.of("VHINVB_CRIT", valid));
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        crit.put("incdType", incdTypes.get(ty));
        crit.put("incdPfx", prefixes.get(ty));
        crit.put("incidentName", names.get(ty));
        crit.put("typeInd", ty);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", car);
        data.put("incdType", incdTypes.get(ty));
        data.put("dateIncdOccr", IncidentsTrainService.ymd(date));
        data.put("timeIncdOccr", time);
        data.put("incdLoc", loc);
        data.put("sympCd", symp);
        data.put("discvDur", disc);
        data.put("userGrp", userGroup);
        data.put("termLastUpd", terminal);
        data.put("dateNarr", now.format(DATE));
        data.put("timeNarr", now.format(TIME));
        data.put("narr", narr);
        data.put("marisFunc", marisFunc);
        ProgramResult r = programs.run("VHINCZ", Map.of("VHINCZ_CRIT", crit, "VHINCZ_DATA", data));
        String no = screens.text(r.record("VHINCZ_DATA"), "VHINCZ_DATA", PKG, "INCD-NO");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("reptId", reptId);
        form.put("reptType", reptTypes.get(ty));
        form.put("conCar1", car);
        form.put("incdNo", no);
        form.put("termId", IncidentScreens.fit(terminal, 8).strip());
        form.put("userGrp", userGroup);
        programs.attempt("VHINC3A", Map.of("VHINC3A_CRIT", form));
        return new Result(screens.parts("VHINC03IW-TRIGGER", PKG, "SUCCESS-MESSAGE", part1, no, part3, car, part5), no);
    }

    private String type(String type) {
        String t = s(type).toUpperCase(java.util.Locale.ROOT);
        if (!types.contains(t)) throw new InvalidRequestException("type is one of " + String.join(", ", types));
        return t;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
