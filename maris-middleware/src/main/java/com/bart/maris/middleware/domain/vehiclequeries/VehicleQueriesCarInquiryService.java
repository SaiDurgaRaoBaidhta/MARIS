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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status / history inquiry of one car (old VHSV07I asks for the car, VHSV06I shows it): the car, its incidents in the
 * date range VHSV06F gives, an incident's narrative and consist (VHSV06N), its repairs (VHSV06C) with their narrative
 * and components (VHNARY, VHSV06D), and the car's last move reason (VHSV06B). The consist cars' orientation marks
 * follow VHSV06IS-MOVE-CON's own tests, read from the screen.
 */
public final class VehicleQueriesCarInquiryService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final String invalidCar, titleName, titleCar, titleFrom, titleTo;
    private final List<EglTexts.OrRule> orientation;

    public VehicleQueriesCarInquiryService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        invalidCar = t.assignedAll("VHSV07IS-DISPLAY", PKG, "MESG1").get(0);
        titleName = t.assigned("VHSV06IS-RETDATA", PKG, "TITLE-1", null);
        titleCar = t.assignedRaw("VHSV06IS-RETDATA", PKG, "TITLE-2").get(0);
        titleFrom = t.assignedRaw("VHSV06IS-RETDATA", PKG, "TITLE-4").get(0);
        titleTo = t.assignedRaw("VHSV06IS-RETDATA", PKG, "TITLE-6").get(0);
        orientation = t.orRules("VHSV06IS-MOVE-CON", PKG, "CAR-ORIENTATION");
        if (orientation.isEmpty()) throw new IllegalStateException("VHSV06IS-MOVE-CON has no orientation tests");
    }

    /** VHSV07IS-DISPLAY: a car number is required and numeric */
    public String check(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || !c.chars().allMatch(Character::isDigit) || c.length() > 4) throw new InvalidRequestException(invalidCar);
        return c;
    }

    public record CarDetails(String car, String carShown, String status, String location, String homeYard, String ato, long mtbi, String warranty,
                             long hoursFromLastPm, long cumulativeHours) {}

    public record Incident(String incident, String date, String time, String symptom, String location, String status, String direction,
                           long hours, String deferredPriority) {}

    public record MoveReason(String subLocation, String code, String description) {}

    public record Inquiry(String title, CarDetails car, List<Incident> incidents, MoveReason moveReason) {}

    /** VHSV07IS-DISPLAY, then VHSV06IS-RETDATA (VHSV06F, VHSV06A) and VHSV06IS-M-REASON (VHSV06B) */
    public Inquiry inquiry(String car) {
        String c = check(car);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", c);
        crit.put("incdNo", "");
        crit.put("whichGui", "");
        Programs.Outcome f = programs.attempt("VHSV06F", Map.of());
        String from = "", to = "";
        if (f.ok() && f.result().record("VHSV06F_DATA") != null) {
            from = text(f.result().record("VHSV06F_DATA"), "fromDate");
            to = text(f.result().record("VHSV06F_DATA"), "toDate");
        }
        crit.put("incdDateFrom", from);
        crit.put("incdDateTo", to);
        String title = titleName + titleCar + c + titleFrom + from + titleTo + to;
        ProgramResult r = programs.attempt("VHSV06A", Map.of("VHSV06A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) return new Inquiry(title, null, List.of(), null);
        Map<String, Object> d = r.record("VHSV06A_DATA_01"), d2 = r.record("VHSV06A_DATA_02");
        CarDetails details = null;
        if (d != null) {
            @SuppressWarnings("unchecked") Map<String, Object> st = d.get("dispVehStat") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
            Object filler = st.get("vehStatFiller");
            details = new CarDetails(c, d2 == null ? c : text(d2, "carnoExt"), String.format("%-6s%s", text(st, "vehStat"), filler == null ? "" : filler).stripTrailing(),
                    text(d, "currLoc"), text(d, "homeYrd"), text(d, "atoPresentInd"), number(d, "mtbi"), text(d, "warInd"), number(d, "hrsFromLastPm"),
                    number(d, "cumHrs"));
        }
        int count = (int) number(r.record("VHSV06A_CRIT"), "rowCount");
        List<Incident> out = new ArrayList<>();
        List<Map<String, Object>> list = rows(r, "VHSV06A_DATA_03", "dataRow");
        for (int i = 0; i < Math.min(count, list.size()); i++) {
            Map<String, Object> row = list.get(i);
            out.add(new Incident(text(row, "incdNo"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"), text(row, "sympCd"), text(row, "incdLoc"),
                    text(row, "vehIncdStat"), text(row, "conDirection"), number(row, "cumHrs"), text(row, "deferredPriority")));
        }
        MoveReason reason = null;
        Programs.Outcome b = programs.attempt("VHSV06B", Map.of("VHSV06B_DATA", Map.of("carNo", c)));
        Map<String, Object> bd = b.result().record("VHSV06B_DATA");
        if (bd != null) reason = new MoveReason(text(bd, "yrdSubLoc"), text(bd, "moveReasonCd"), text(bd, "moveReasonDesc"));
        return new Inquiry(title, details, out, reason);
    }

    public record IncidentDetails(List<String> narrative, String consist, List<String> cars) {}

    /** VHSV06IS-I-PRVNXT (VHSV06N) and VHSV06IS-MOVE-CON: an incident's narrative and the consist the car ran in */
    public IncidentDetails incident(String incident) {
        String i = incident == null ? "" : incident.strip();
        if (i.isEmpty() || !i.chars().allMatch(Character::isDigit) || i.length() > 8) throw new InvalidRequestException("An incident number is up to 8 digits.");
        ProgramResult r = programs.attempt("VHSV06N", Map.of("VHSV06N_CRIT", Map.of("critIncdNo", i))).result();
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<String> narr = new ArrayList<>();
        List<Map<String, Object>> lines = rows(r, "VHSV06N_DATA", "dataRow");
        for (int k = 0; k < Math.min(n, lines.size()); k++) narr.add(text(lines.get(k), "narr"));
        if (n == 0) return new IncidentDetails(narr, "", List.of());
        Map<String, Object> data = r.record("VHSV06N_DATA");
        String con = data == null ? "" : text(data, "conNo");
        long dash = data == null ? 0 : number(data, "dashNo");
        @SuppressWarnings("unchecked")                                        // all 10 car rows: the row count is the narrative's
        List<Map<String, Object>> cars = data != null && data.get("carRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        Map<String, String> marks = new LinkedHashMap<>();                       // CAR-n-LFT-OR / RGT-OR -> mark
        for (EglTexts.OrRule rule : orientation) {
            boolean hit = false;
            for (var test : rule.tests()) {
                int row = test.getKey();
                String o = row <= cars.size() ? text(cars.get(row - 1), "carOrientation") : "";
                if (o.equals(test.getValue())) { hit = true; break; }
            }
            if (hit) marks.put(rule.target(), rule.sourceRow() <= cars.size() ? text(cars.get(rule.sourceRow() - 1), "carOrientation") : "");
        }
        List<String> shown = new ArrayList<>();
        for (int k = 1; k <= Math.min(10, cars.size()); k++) {
            String car = text(cars.get(k - 1), "carNo");
            String text = marks.getOrDefault("CAR-" + k + "-LFT-OR", "") + car + marks.getOrDefault("CAR-" + k + "-RGT-OR", "");
            shown.add(text.strip());
        }
        String consist = con.isBlank() ? "" : String.format("%-3s-%02d", con, dash);
        return new IncidentDetails(narr, consist, shown);
    }

    public record Repair(long document, String system, String subSystem, String disposition, String serialOn, String serialOff, String partControl) {}

    /** VHSV06IS-REPR-DATA (VHSV06C): an incident's repairs */
    public List<Repair> repairs(String incident) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incident == null ? "" : incident.strip());
        crit.put("reprRowCount", 0);
        ProgramResult r = programs.attempt("VHSV06C", Map.of("VHSV06C_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) return List.of();
        int n = (int) number(r.record("VHSV06C_CRIT"), "reprRowCount");
        List<Repair> out = new ArrayList<>();
        List<Map<String, Object>> list = rows(r, "VHSV06C_DATA", "dataRow");
        for (int k = 0; k < Math.min(n, list.size()); k++) {
            Map<String, Object> row = list.get(k);
            out.add(new Repair(number(row, "reprDocNo"), text(row, "vehSystem"), text(row, "vehSubSystem"), text(row, "reprDispCd"), text(row, "serNoOn"),
                    text(row, "serNoOff"), text(row, "vehPrtCtlNo")));
        }
        return out;
    }

    public record Component(long document, String component, String serialOff, String disposition, String fault) {}

    public record RepairDetails(List<String> narrative, List<Component> components) {}

    /** VHSV06IS-R-PRVNXT: a repair's narrative (VHNARY) and components (VHSV06D) */
    public RepairDetails repair(String incident, long document) {
        String i = incident == null ? "" : incident.strip();
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
        ProgramResult dr = programs.attempt("VHSV06D", Map.of("VHSV06D_CRIT", critD)).result();
        List<Component> comps = new ArrayList<>();
        int dn = dr.control() == null ? 0 : dr.control().rowCount();
        List<Map<String, Object>> drows = rows(dr, "VHSV06D_DATA", "dataRow");
        for (int k = 0; k < Math.min(dn, drows.size()); k++) {
            Map<String, Object> row = drows.get(k);
            comps.add(new Component(number(row, "cmpntDocNo"), text(row, "cmpntCd"), text(row, "serNoOff"), text(row, "reprDispCd"), text(row, "cmpntFaultCd")));
        }
        return new RepairDetails(narr, comps);
    }
}
