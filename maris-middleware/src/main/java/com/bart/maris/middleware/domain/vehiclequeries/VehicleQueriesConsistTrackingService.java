package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consist tracking (status trains). Old VHST05I asks for a consist range and a report: 1 (VHST02I, the summary list:
 * VHST2A1) or 2 (VHST04I, car details: VHST4A2, VHST04A, then VHST04B per car), and prints them (VHST011). Old VHST01I
 * (opened from VHIE03I) asks for one consist and a symptom and shows VHST03I (VHST3A3, VHST3A4, VHST3A1), or prints
 * report 3. The checks, texts, origin names and marks are read from the screens' statement groups.
 */
public final class VehicleQueriesConsistTrackingService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final List<String> reports;
    private final String invalidFrom, invalidTo, symptomBlank, all, submitted, symptomSubmitted, orderFiller, trainMark, symptomMark, pmScheduled;
    private final String allFromDisplay, allToDisplay, allFromPrint, allToPrint, softwareMark;
    private final List<String> goodStatus, symptomGoodStatus;
    private final Map<String, String> origins = new LinkedHashMap<>();
    private final VehicleQueriesOldJulian julian;

    public VehicleQueriesConsistTrackingService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        reports = t.items("VHST05I", "Radio_Button_Set1");
        List<String> msgs = t.assignedAll("VHST01IS-MSG", PKG, "WS-MSG");
        invalidFrom = msgs.get(0);
        invalidTo = msgs.get(1);
        symptomBlank = t.assignedAll("VHST01IS-DISPLAY-X", PKG, "WS-MSG").get(0);
        all = t.compared("VHST01IS-VALID", PKG, "CON-FROM").get(0);
        List<String> display = t.assignedAll("VHST01IS-VALID", PKG, "CON-FROM"), displayTo = t.assignedAll("VHST01IS-VALID", PKG, "CON-TO");
        allFromDisplay = display.get(display.size() - 1);
        allToDisplay = displayTo.get(displayTo.size() - 1);
        List<String> print = t.assignedAll("VHST01IS-SUBMIT", PKG, "CON-FROM"), printTo = t.assignedAll("VHST01IS-SUBMIT", PKG, "CON-TO");
        allFromPrint = print.get(print.size() - 1);
        allToPrint = printTo.get(printTo.size() - 1);
        submitted = t.triggeredPrompt("VHST05I", "SUBMIT").strip();
        symptomSubmitted = t.triggeredPrompt("VHST01I", "SUBMIT").strip();
        orderFiller = t.assignedAll("VHST02IS-LOAD_2", PKG, "ORDER-FILLER").get(0);
        trainMark = t.assignedAll("VHST02IS-LOAD_2", PKG, "TI-1").get(0);
        symptomMark = t.assignedAll("VHST03IS-LOAD", PKG, "MSG").get(0);
        pmScheduled = t.assignedAll("VHST04IS-LOAD-DATA", PKG, "PM1").get(0);
        goodStatus = t.compared("VHST02IS-LOAD_2", PKG, "VEH-STAT");
        symptomGoodStatus = t.compared("VHST03IS-LOAD", PKG, "VEH-STAT");
        softwareMark = searchedText(screens, "CarWithSoftVer");
        List<String> codes = t.compared("VHST02IS-LOAD_2", PKG, "ORIGIN"), names = t.assignedAll("VHST02IS-LOAD_2", PKG, "ORIGIN");
        for (int i = 0; i < codes.size(); i++) origins.put(codes.get(i), names.get(i));
        julian = new VehicleQueriesOldJulian(t);
        if (goodStatus.size() < 2 || symptomGoodStatus.size() < 2 || origins.isEmpty()) throw new IllegalStateException("consist tracking rules not found");
    }

    // ---- criteria ------------------------------------------------------------------------------------------

    public record Printer(String description, String vtam) {}

    public record Options(List<String> reports, List<Printer> printers) {}

    /** VHST05I: the report choices and the printers (WS70A01) */
    public Options options() {
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return new Options(reports, printers);
    }

    /** the consist range after the old checks: the texts the screen holds (CHAR 3) and their number values */
    public record Range(String from, String to, int fromNumber, int toNumber) {}

    /** VHST01IS-VALID (Display) or VHST01IS-SUBMIT (print): ALL is the whole range, a blank "to" is the "from" */
    public Range range(String from, String to, boolean forPrint) {
        String f = VehicleQueriesOldJulian.chars(from, 3), tt = VehicleQueriesOldJulian.chars(to, 3);
        if (!f.equals(VehicleQueriesOldJulian.chars(all, 3))) {
            long fn = VehicleQueriesOldJulian.zoned(f);
            if (fn <= 0) throw new InvalidRequestException(invalidFrom);
            long tn = VehicleQueriesOldJulian.zoned(tt);
            if (tn != 0 && tn < fn) throw new InvalidRequestException(invalidTo);
        }
        if (tt.isBlank()) tt = f;
        if (f.equals(VehicleQueriesOldJulian.chars(all, 3))) {
            f = VehicleQueriesOldJulian.chars(forPrint ? allFromPrint : allFromDisplay, 3);
            tt = VehicleQueriesOldJulian.chars(forPrint ? allToPrint : allToDisplay, 3);
        }
        return new Range(f.strip(), tt.strip(), (int) Math.abs(VehicleQueriesOldJulian.zoned(f)), (int) Math.abs(VehicleQueriesOldJulian.zoned(tt)));
    }

    // ---- VHST02I: summary list -----------------------------------------------------------------------------

    /** {@code alert}: the car shows red (an incident, or a status that is neither GO nor USABLE); {@code usable}: green */
    public record TrainCar(String car, String leftMark, String rightMark, String homeYard, String status, boolean alert, boolean usable, boolean warning) {}

    public record Train(String consist, String origin, String dateInService, String timeInService, String mark, String orderLocation, String orientation,
                        boolean warning, List<TrainCar> cars) {}

    public record Trains(String from, String to, List<Train> trains) {}

    /** VHST01IS-VALID, then VHST02IS-GET-DATA (VHST2A1) and VHST02IS-LOAD_1 .. LOAD_5: each consist with up to 10 cars */
    public Trains statusTrains(String from, String to) {
        Range r = range(from, to, false);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNoLow", r.fromNumber());
        crit.put("conNoHigh", r.toNumber());
        ProgramResult res = programs.attempt("VHST2A1", Map.of("VHST2A1_CRIT", crit)).result();
        int urc = res.control() == null ? 0 : res.control().returnCode();
        if (urc != 0 && urc != 4) return new Trains(r.from(), r.to(), List.of());
        List<Map<String, Object>> list = rows(res, "VHST2A1_DATA", "dataRow");
        List<Train> out = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            Map<String, Object> first = list.get(i);
            String con = consist(first);
            String dir = text(first, "scannerDir");
            String order = text(first, "msgLoc") + (dir.isEmpty() ? "" : orderFiller + dir);
            boolean mark = text(first, "conStat").equals("CON") || text(first, "trainIncdInd").equals("Y");
            boolean conWarn = false;
            List<TrainCar> cars = new ArrayList<>();
            while (i < list.size() && cars.size() < 10 && consist(list.get(i)).equals(con)) {
                Map<String, Object> row = list.get(i);
                if (text(row, "conWnInd").equals("Y")) conWarn = true;
                boolean incident = text(row, "trainIncdInd").equals("Y") || text(row, "jaIncdInd").equals("Y");
                String st = text(row, "vehStat");
                boolean usable = !incident && st.equals(goodStatus.get(1));
                boolean alert = incident || !(st.equals(goodStatus.get(0)) || st.equals(goodStatus.get(1)));
                String[] marks = marks(row);
                cars.add(new TrainCar(text(row, "carNo"), marks[0], marks[1], text(row, "homeYrd"), st, alert, usable, text(row, "carWnInd").equals("Y")));
                i++;
            }
            out.add(new Train(con, origin(text(first, "originLoc")), mdy(text(first, "dateInsrvRev")), text(first, "timeInsrv"), mark ? trainMark : "", order,
                    text(first, "orientOfCars"), conWarn, cars));
        }
        return new Trains(r.from(), r.to(), out);
    }

    // ---- VHST04I: car details ------------------------------------------------------------------------------

    /** {@code pm}: hours from the last PM (5 digits) or the scheduled-PM text; {@code clean}: the overdue cleaning marks */
    public record DetailCar(String car, String homeYard, String pm, long washDays, String clean) {}

    public record DetailTrain(String consist, String origin, String dateInService, String timeInService, List<DetailCar> cars) {}

    public record Details(String from, String to, List<DetailTrain> trains) {}

    /**
     * VHST01IS-VALID, then VHST04IS-OVERDUE (VHST4A2: the overdue days of its first row), VHST04IS-GET-DATA (VHST04A, then
     * VHST04B per car, its records going back with every call), VHST04IS-CL-WASH and VHST04IS-LOAD-DATA. As on the old
     * screen, the consists shown are counted over VHST04B's clean-date count, not over the cars.
     */
    public Details carDetails(String from, String to) {
        Range r = range(from, to, false);
        ProgramResult od = programs.attempt("VHST4A2", Map.of("VHST4A2_CRIT", Map.of("carType", ""))).result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> odRows = od.record("VHST4A2_DATA") != null && od.record("VHST4A2_DATA").get("dataRow") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        Map<String, Object> limits = odRows.isEmpty() ? Map.of() : odRows.get(0);
        Map<String, Object> critA = new LinkedHashMap<>();
        critA.put("conNoLow", r.from());
        critA.put("conNoHigh", r.to());
        ProgramResult a = programs.attempt("VHST04A", Map.of("VHST04A_CRIT", critA)).result();
        int urc = a.control() == null ? 0 : a.control().returnCode();
        if (urc != 0 && urc != 4) return new Details(r.from(), r.to(), List.of());
        List<Map<String, Object>> cars = rows(a, "VHST04A_DATA", "dataRow");
        Map<String, Object> critB = new LinkedHashMap<>();
        critB.put("totClnCount", 0);
        Map<String, Object> data = new LinkedHashMap<>();
        for (int k = 1; k <= cars.size(); k++) {
            critB.put("carNo", text(cars.get(k - 1), "carNo"));
            critB.put("vagenCount", k);
            ProgramResult b = programs.attempt("VHST04B", Map.of("VHST04B_CRIT", critB, "VHST04B_DATA", data)).result();
            if (b.record("VHST04B_CRIT") != null) critB = new LinkedHashMap<>(b.record("VHST04B_CRIT"));
            if (b.record("VHST04B_DATA") != null) data = new LinkedHashMap<>(b.record("VHST04B_DATA"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> found = data.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        int cleanCount = (int) number(critB, "totClnCount");
        List<VehicleQueriesOldJulian.Clean> washed = new ArrayList<>();
        for (int k = 0; k < cars.size(); k++) {
            Map<String, Object> row = k < found.size() ? found.get(k) : Map.of();
            washed.add(julian.clean(row, number(limits, "cntDaysBiwkOd"), number(limits, "cntDaysShampoOd"), number(limits, "cntDaysThoroOd")));
        }
        // VHST04IS-LOAD-DATA: the consists over rows 1 .. clean count, less one; two to a page
        List<String> runs = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        String hold = null;
        for (int i = 1; i <= Math.max(1, cleanCount); i++) {
            String c = i <= cars.size() ? consist(cars.get(i - 1)) : "";
            if (hold == null || (i <= cleanCount && !c.equals(hold))) { runs.add(c); starts.add(i); hold = c; }
        }
        int cnt = runs.size() - 1;
        int shown = cnt <= 0 ? 0 : cnt == 1 ? 1 : Math.min(runs.size(), 2 * ((cnt + 1) / 2));
        List<DetailTrain> out = new ArrayList<>();
        for (int n = 0; n < shown; n++) {
            int i = starts.get(n);
            if (i > cleanCount || runs.get(n).isBlank()) continue;
            Map<String, Object> first = cars.get(i - 1);
            String con = runs.get(n);
            List<DetailCar> list = new ArrayList<>();
            for (int j = i; j <= cars.size() && list.size() < 10 && consist(cars.get(j - 1)).equals(con) && !text(cars.get(j - 1), "carNo").isEmpty(); j++) {
                Map<String, Object> row = j - 1 < found.size() ? found.get(j - 1) : Map.of();
                VehicleQueriesOldJulian.Clean w = washed.get(j - 1);
                String pm = String.valueOf(row.getOrDefault("currSchdPmNo", "")).isBlank()
                        ? String.format("%05d", VehicleQueriesOldJulian.num(number(row, "hrsFromLastPm"), 5)) : pmScheduled;
                list.add(new DetailCar(text(cars.get(j - 1), "carNo"), text(row, "homeYrd"), pm, VehicleQueriesOldJulian.num(w.washDays(), 3), w.clean().strip()));
            }
            out.add(new DetailTrain(con, origin(text(first, "originLoc")), mdy(text(first, "dateInsrvRev")), text(first, "timeInsrv"), list));
        }
        return new Details(r.from(), r.to(), out);
    }

    // ---- VHST01I / VHST03I: symptom ------------------------------------------------------------------------

    public record SymptomCar(String car, String leftMark, String rightMark, boolean software, long symptomCount, String homeYard, boolean alert,
                             boolean usable) {}

    public record SymptomTrain(String consist, String origin, String dateInService, String timeInService, String mark, List<SymptomCar> cars) {}

    public record Symptoms(String consist, String symptom, String dateFrom, String dateTo, List<SymptomTrain> trains) {}

    /** VHST01IS-VALID-X and VHST01IS-DISPLAY-X: a consist number above 0 and a symptom */
    public String[] checkSymptom(String consist, String symptom) {
        String c = VehicleQueriesOldJulian.chars(consist, 3);
        if (VehicleQueriesOldJulian.zoned(c) <= 0) throw new InvalidRequestException(invalidFrom);
        String s = VehicleQueriesOldJulian.chars(symptom, 4);
        if (s.isBlank()) throw new InvalidRequestException(symptomBlank);
        return new String[] {c.strip(), s.strip()};
    }

    /** VHST03IS-GET-DATA (VHST3A3 from date, VHST3A4 to date, VHST3A1) and VHST03IS-LOAD; the old screen shows only the first consist */
    public Symptoms symptoms(String consist, String symptom) {
        String[] k = checkSymptom(consist, symptom);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", k[0]);
        crit.put("sympCd", k[1]);
        crit.put("dateLow", "");
        crit.put("dateRev", "");
        ProgramResult d3 = programs.attempt("VHST3A3", Map.of("VHST3A3_CRIT", Map.of("marisSubsys", ""))).result();
        if (ok(d3) && d3.record("VHST3A3_DATA") != null) crit.put("dateLow", text(d3.record("VHST3A3_DATA"), "fromDate"));
        ProgramResult d4 = programs.attempt("VHST3A4", Map.of("VHST3A4_CRIT", Map.of("marisSubsys", ""))).result();
        if (ok(d4) && d4.record("VHST3A4_DATA") != null) crit.put("dateRev", text(d4.record("VHST3A4_DATA"), "toDate"));
        ProgramResult res = programs.attempt("VHST3A1", Map.of("VHST3A1_CRIT", crit)).result();
        String dFrom = (String) crit.get("dateLow"), dTo = (String) crit.get("dateRev");
        if (!ok(res)) return new Symptoms(k[0], k[1], dFrom, dTo, List.of());
        long rowCount = number(res.record("VHST3A1_DATA"), "rowCount");
        List<Map<String, Object>> list = rows(res, "VHST3A1_DATA", "dataRow");
        List<SymptomTrain> out = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            Map<String, Object> first = list.get(i);
            String con = consist(first);
            List<SymptomCar> cars = new ArrayList<>();
            while (i < list.size() && cars.size() < 10 && consist(list.get(i)).equals(con)) {
                Map<String, Object> row = list.get(i);
                boolean incident = text(row, "trainIncdInd").equals("Y");
                String st = text(row, "vehStat");
                boolean usable = !incident && st.equals(symptomGoodStatus.get(1));
                boolean alert = incident || !(st.equals(symptomGoodStatus.get(0)) || st.equals(symptomGoodStatus.get(1)));
                String[] marks = marks(row);
                boolean soft = String.valueOf(row.getOrDefault("softVer", "")).contains(softwareMark);
                cars.add(new SymptomCar(text(row, "carNo"), marks[0], marks[1], soft, rowCount >= i + 1 ? number(row, "sympCount") : 0, text(row, "homeYrd"),
                        alert, usable));
                i++;
            }
            out.add(new SymptomTrain(con, origin(text(first, "originLoc")), mdy(text(first, "dateInsrvRev")), text(first, "timeInsrv"),
                    text(first, "conStat").equals("CON") ? symptomMark : "", cars));
        }
        return new Symptoms(k[0], k[1], dFrom, dTo, out);
    }

    // ---- printing ------------------------------------------------------------------------------------------

    /** VHST01IS-SUBMIT and SUBMIT-X (VHST011): report 1 summary list, 2 car details */
    public String print(int report, String from, String to, String printer) {
        if (report < 1 || report > reports.size()) throw new InvalidRequestException("Choose a report type.");
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        range(from, to, true);
        Map<String, Object> crit = new LinkedHashMap<>();                 // VHST01IW2: the range as typed (ALL stays ALL)
        crit.put("reptId", String.valueOf(report));
        crit.put("reptMisc", VehicleQueriesOldJulian.chars(from, 3).strip());
        crit.put("reptType", printer.strip());
        crit.put("conNo", VehicleQueriesOldJulian.chars(to, 3).strip());
        crit.put("dateInsrvRev", "");
        crit.put("sympCd", "");
        programs.run("VHST011", Map.of("VHST011_CRIT", crit));
        return submitted;
    }

    /** VHST01IS-SUBMIT-1 and SUBMIT-XX (VHST011, report 3) */
    public String printSymptom(String consist, String symptom, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String[] k = checkSymptom(consist, symptom);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", "3");
        crit.put("reptMisc", k[0]);
        crit.put("reptType", printer.strip());
        crit.put("conNo", "");
        crit.put("dateInsrvRev", "");
        crit.put("sympCd", k[1]);
        programs.run("VHST011", Map.of("VHST011_CRIT", crit));
        return symptomSubmitted;
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** the text a function's first if searches for, e.g. CarWithSoftVer: strLib.indexOf(soft_ver, "$") > 0 */
    private static String searchedText(ScreenBundle screens, String function) {
        Ast.Function f = screens.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        for (Ast.Stmt st : f.body()) {
            if (st instanceof Ast.If i) {
                String x = firstText(i.cond());
                if (x != null) return x;
            }
        }
        throw new IllegalStateException(function + " searches for no text");
    }

    private static String firstText(Ast.Expr e) {
        return switch (e) {
            case Ast.Str s -> s.value();
            case Ast.Call c -> c.args().stream().map(VehicleQueriesConsistTrackingService::firstText).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            case Ast.Binary b -> { String x = firstText(b.left()); yield x != null ? x : firstText(b.right()); }
            default -> null;
        };
    }

    private static boolean ok(ProgramResult r) {
        int urc = r.control() == null ? 0 : r.control().returnCode();
        return urc == 0 || urc == 4;
    }

    /** CONSIST: CON-NO, the hyphen and DASH-NO (2 digits) */
    @SuppressWarnings("unchecked")
    static String consist(Map<String, Object> row) {
        Object c = row.get("consist");
        if (c instanceof Map<?, ?> m) {
            Map<String, Object> x = (Map<String, Object>) m;
            String no = text(x, "conNo");
            if (no.isEmpty() && text(x, "conHyphen").isEmpty()) return "";
            return String.format("%-3s", no) + String.valueOf(x.getOrDefault("conHyphen", " ")) + String.format("%02d", number(x, "dashNo"));
        }
        return c == null ? "" : c.toString().strip();
    }

    /** the orientation mark goes left or right of the car, whichever code matches the car's orientation */
    private static String[] marks(Map<String, Object> row) {
        String o = String.valueOf(row.getOrDefault("vagenOrientation", "")), l = String.valueOf(row.getOrDefault("lftOrientationCd", "")),
                r = String.valueOf(row.getOrDefault("rgtOrientationCd", ""));
        if (l.equals(o)) return new String[] {l.strip(), ""};
        if (r.equals(o)) return new String[] {"", r.strip()};
        return new String[] {"", ""};
    }

    private String origin(String code) {
        return origins.getOrDefault(code, code);
    }

    /** XXDATES-CONVERT "MDY": yyyy-mm-dd to mm-dd-yyyy */
    static String mdy(String date) {
        if (date.length() == 10 && date.charAt(4) == '-' && date.charAt(7) == '-') return date.substring(5, 7) + "-" + date.substring(8) + "-" + date.substring(0, 4);
        return date;
    }
}
