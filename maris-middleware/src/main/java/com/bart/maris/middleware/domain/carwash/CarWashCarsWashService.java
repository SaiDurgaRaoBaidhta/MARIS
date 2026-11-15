package com.bart.maris.middleware.domain.carwash;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wash of single cars outside a consist (old VHCWA3I, MRSVehicleCarWash RUIFunctions): up to 10 cars washed in a yard
 * at a date and time, recorded under the dummy consist the screen offers. Checks in the old order: the time
 * (VHCWA3IS-A-TIME), the yard (VHCWA3IS-VAL-YRD), the date and time against the revenue day (VHCWA3IS-NEW-DT: VHINVB),
 * each car (VHCWA3IS-VAL-CARS: VHCWA3A); then each car's next wash (VHCWA3IS-GET-DTE: VHCWA3B) and the write
 * (VHCWA3IS-UPDATE1: VHCWA3D).
 */
public final class CarWashCarsWashService {
    private static final String PKG = "MRSVehicleCarWash";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    /** VHCWA3IW-CARS: CAR-NO1..CAR-NO10, and the DATA_ROW array one longer (its last car is always blank) */
    private static final int CARS = 10;

    private final Programs programs;
    private final String terminal;
    private final String enterData, consist, timeHours, timeMinutes, timeNotNumeric, timeBlank, timeOk, badYard, noCar, badCars, checkDb, updated, failed,
            subsystem, group;
    private final List<String> yards;
    private final int dashNo;

    public CarWashCarsWashService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        enterData = t.assignedAll("VHCWA3IS-CLEAR", PKG, "FINAL-MSG").get(0);
        consist = t.assigned("VHCWA3IS-CLEAR", PKG, "CON-NO", null);
        timeHours = t.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "HOURS");
        timeMinutes = t.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "MINUTES");
        timeNotNumeric = t.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "NOT NUMERIC");
        timeBlank = t.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "Time Field");
        timeOk = t.assigned("VHCWA3IS-A-TIME", PKG, "FINAL-MSG", "Update button");
        yards = t.compared("VHCWA3IS-VAL-YRD", PKG, "CAR-WASH-LOC");
        badYard = t.assignedAll("VHCWA3IS-VAL-YRD", PKG, "FINAL-MSG").get(0);
        noCar = t.assigned("VHCWA3IS-VAL-CARS", PKG, "FINAL-MSG", "NO CAR");
        badCars = t.assigned("VHCWA3IS-VAL-CARS", PKG, "FINAL-MSG", "invalid");
        checkDb = t.assigned("VHCWA3IS-VAL-CARS", PKG, "FINAL-MSG", "DATA BASE");
        updated = t.assigned("VHCWA3IS-UPDATE1", PKG, "FINAL-MSG", "updated");
        failed = t.assigned("VHCWA3IS-UPDATE1", PKG, "FINAL-MSG", "Error");
        subsystem = t.assigned("VHCWA3IS-NEW-DT", PKG, "MARIS-SUBSYS", null);
        group = t.assigned("VHCWA3IS-NEW-DT", PKG, "USER-GRP", null);
        dashNo = t.assignedNumber("VHCWA3IS-UPDATE1", PKG, "DASH-NO").intValue();
    }

    /** @param washDate the revenue date the old screen starts with; @param consist the dummy consist it offers */
    public record Start(String washDate, String consist, List<String> yards, String message) {}

    /** VHCWA3IS-INIT, VHCWA3IS-REV-DTE-2, VHCWA3IS-CLEAR */
    public Start start() {
        return new Start(CarWashLegacy.revenueDate(programs), consist, yards, enterData);
    }

    /** the time check alone (VHCWA3IS-A-TIME, run when the old time field is left): the stored HH:MM:SS and the message */
    public record TimeCheck(String time, boolean valid, String message) {}

    public TimeCheck time(String typed) {
        CarWashTime.Parsed p = CarWashTime.parse(typed);
        if (p.time() == null) return new TimeCheck("", false, timeBlank);
        return switch (p.error()) {
            case HOURS -> new TimeCheck(p.time(), false, timeHours);
            case MINUTES -> new TimeCheck(p.time(), false, timeMinutes);
            case NOT_NUMERIC -> new TimeCheck(p.time(), false, timeNotNumeric);
            case NONE -> new TimeCheck(p.time(), true, timeOk);
        };
    }

    /** @param cars the 10 car boxes in order; the cars counted are those before the first blank box */
    public record Wash(String yard, String date, String time, String consist, List<String> cars) {}

    public record Result(String message, List<String> cars) {}

    /** a car the old screen marked "B": its box number (1-10) and number */
    public static final class InvalidCarsException extends InvalidRequestException {
        private final List<Integer> boxes;

        InvalidCarsException(String message, List<Integer> boxes) {
            super(message);
            this.boxes = List.copyOf(boxes);
        }

        public List<Integer> boxes() {
            return boxes;
        }
    }

    /** VHCWA3IS-VALID: checks, then each car's next wash (VHCWA3B) and the wash records (VHCWA3D) */
    public Result wash(Wash w) {
        TimeCheck tc = time(w.time());
        if (!tc.valid()) throw new InvalidRequestException(tc.message());
        String yard = w.yard() == null ? "" : w.yard().strip().toUpperCase(Locale.ROOT);
        if (!yards.contains(yard)) throw new InvalidRequestException(badYard);
        String date = iso(w.date());

        Map<String, Object> inv = new LinkedHashMap<>();                           // VHCWA3IS-NEW-DT
        inv.put("userTermId", terminal);
        inv.put("userGrp", group);
        inv.put("marisSubsys", subsystem);
        inv.put("inputDate", date);
        inv.put("inputTime", tc.time());
        ProgramResult v = programs.attempt("VHINVB", Map.of("VHINVB_CRIT", inv)).result();
        int vurc = v.control() == null ? 0 : v.control().returnCode();
        if (vurc != 0 && vurc != 4) throw CarWashLegacy.failed(programs, "VHINVB", v.control(), "");

        List<String> boxes = new ArrayList<>();
        for (int i = 0; i < CARS; i++) boxes.add(w.cars() != null && i < w.cars().size() && w.cars().get(i) != null ? w.cars().get(i).strip() : "");
        int count = 0;
        while (count < CARS && !boxes.get(count).isEmpty()) count++;
        if (count == 0) throw new InvalidRequestException(noCar);

        List<Integer> bad = new ArrayList<>();                                       // VHCWA3IS-VAL-CARS
        Map<String, Object> answer = null;                                           // each call gets the previous answer, as on the old screen
        for (int i = 0; i < count; i++) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("carNo", boxes.get(i));
            crit.put("dateCarWash", date);
            crit.put("timeCarWash", tc.time());
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("VHCWA3A_CRIT", crit);
            if (answer != null) in.put("VHCWA3A_DATA", answer);
            ProgramResult r = programs.attempt("VHCWA3A", in).result();
            if (r.record("VHCWA3A_DATA") != null) answer = r.record("VHCWA3A_DATA");
            int urc = r.control() == null ? 0 : r.control().returnCode();
            if (urc != 0 && urc != 4 && urc != 100) throw CarWashLegacy.failed(programs, "VHCWA3A", r.control(), checkDb);
            if ("N".equals(text(r.record("VHCWA3A_DATA"), "carIsValid"))) bad.add(i + 1);
        }
        if (!bad.isEmpty()) throw new InvalidCarsException(badCars, bad);

        List<Map<String, Object>> rows = new ArrayList<>();                          // VHCWA3IS-GET-DTE
        Map<String, Object> last = Map.of();
        for (int i = 0; i < count; i++) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("carNo", boxes.get(i));
            crit.put("termId", terminal);
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("VHCWA3B_CRIT", crit);
            if (!last.isEmpty()) in.put("VHCWA3B_DATA", last);
            ProgramResult r = programs.attempt("VHCWA3B", in).result();
            int urc = r.control() == null ? 0 : r.control().returnCode();
            if (urc != 0 && urc != 4) throw CarWashLegacy.failed(programs, "VHCWA3B", r.control(), checkDb);
            if (r.record("VHCWA3B_DATA") != null) last = r.record("VHCWA3B_DATA");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("carNo", boxes.get(i));
            row.put("dateNxtCarWash", text(last, "dateNxtCarWash"));
            row.put("cntTimesWash", number(last, "cntTimesWash"));
            rows.add(row);
        }

        for (int i = count; i < CARS; i++) {                                         // CAR-NO[n] holds every box, counted or not
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("carNo", boxes.get(i));
            row.put("dateNxtCarWash", "");
            row.put("cntTimesWash", 0);
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();                            // VHCWA3IS-UPDATE1
        data.put("dateCarWash", text(last, "dateCarWash"));
        data.put("timeCarWash", tc.time());
        data.put("carWashLoc", yard);
        data.put("termLastUpd", terminal);
        data.put("conNo", w.consist() == null || w.consist().isBlank() ? consist : w.consist().strip());
        data.put("dashNo", dashNo);
        Map<String, Object> cars = new LinkedHashMap<>();
        for (int i = 0; i < CARS; i++) cars.put("carNo" + (i + 1), boxes.get(i));
        cars.put("dataRow", rows);
        cars.put("numOfCars", count);
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("VHCWA3D_DATA", data);
        in.put("VHCWA3IW-CARS", cars);
        ProgramResult r = programs.attempt("VHCWA3D", in).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw CarWashLegacy.failed(programs, "VHCWA3D", r.control(), failed);
        return new Result(updated, boxes.subList(0, count));
    }

    private String iso(String date) {
        try {
            return ISO.format(LocalDate.parse(date == null ? "" : date.strip(), ISO));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(enterData);
        }
    }
}
