package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
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
 * The pseudo status train (MARIS_RUI/EGLSource/MRSYardTracking, old VHYT09I) and its printer window (VHYT01I). The rules
 * are the old screen's: VHYT09IS-INIT (yards from VHYT09A plus "ALL"), VHYT09IS-GET-DATA (the yard's consists from VHYT09B,
 * their cars from VHYT09C, merged by VHYT09IS-MOVE-DATA in order, coloured by VHYT09IS-COLOR), VHYT01IS-INIT-X (printers,
 * WS70A01) and VHYT01IS-SUBMIT (VHYT01A). All texts are read from the EGL.
 */
public class YardTrackingPseudoTrainService {
    static final String PKG = "MRSYardTracking";

    private final Programs programs;
    private final String all, allFrom, allTo, opening, selected, noConsist, changed, red, black;
    private final List<String> serviceable;
    private final int pageSize;

    public YardTrackingPseudoTrainService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        all = YardTrackingPseudoEgl.first(screens, "VHYT09IS-INIT", PKG, "YRD");
        // VHYT09IS-INIT first blanks FINAL-MESSAGE, then sets the prompt
        opening = YardTrackingPseudoEgl.assigned(screens, "VHYT09IS-INIT", PKG, "FINAL-MESSAGE").stream().filter(t -> !t.isBlank()).findFirst().orElseThrow();
        allFrom = YardTrackingPseudoEgl.first(screens, "VHYT09IS-GET-DATA", PKG, "FR-YRD-TRKG-YRD");
        allTo = YardTrackingPseudoEgl.first(screens, "VHYT09IS-GET-DATA", PKG, "TO-YRD-TRKG-YRD");
        List<String> tails = YardTrackingPseudoEgl.assigned(screens, "VHYT09IS-GET-DATA", PKG, "FINAL-MSG-2");
        selected = tails.get(0);
        noConsist = tails.get(1);
        changed = YardTrackingPseudoEgl.first(screens, "VHYT09IS-MOVE-DATA", PKG, "FINAL-MESSAGE");
        List<String> colours = YardTrackingPseudoEgl.assigned(screens, "VHYT09IS-COLOR", PKG, "COLOR-1");
        red = colours.get(0);
        black = colours.get(1);
        serviceable = YardTrackingPseudoEgl.compared(screens, "VHYT09IS-COLOR", PKG).stream().map(String::strip).toList();
        pageSize = YardTrackingPseudoEgl.divisor(screens, "VHYT09IS-PAGE-CNT", PKG);
    }

    /** VHYT09IS-INIT: the yards (VHYT09A, then "ALL") and the yard shown first (the one passed in, else the first yard) */
    public YardTrackingPseudoTrain.Options options(String passedYard) {
        ProgramResult r = programs.run("VHYT09A", Map.of("VHYT09A_CRIT", Map.of()));
        List<String> yards = new ArrayList<>();
        if (r.control() == null || r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHYT09A_DATA", "dataRow")) yards.add(text(row, "yrd"));
        }
        yards.add(all);
        String p = passedYard == null ? "" : passedYard.strip();
        return new YardTrackingPseudoTrain.Options(yards, !p.isEmpty() ? p : yards.get(0), opening, pageSize);
    }

    /** VHYT09IS-GET-DATA: the yard's consists ("ALL" = every yard) with their cars, and the old screen's message */
    public YardTrackingPseudoTrain.Report consists(String yard) {
        String y = yard == null ? "" : yard.strip();
        if (y.isEmpty()) throw new InvalidRequestException(opening.strip());
        String from = y.equals(all) ? allFrom : y, to = y.equals(all) ? allTo : y;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("frYrdTrkgYrd", from);
        crit.put("toYrdTrkgYrd", to);
        ProgramResult b = programs.run("VHYT09B", Map.of("VHYT09B_CRIT", crit));
        if (b.control() != null && b.control().returnCode() == 100) {
            return new YardTrackingPseudoTrain.Report(y, List.of(), message(from, noConsist), false);
        }
        List<Map<String, Object>> consists = rows(b, "VHYT09B_DATA", "dataRow");
        // VHYT09IS-GET-CARS: a program error is a message box in the old screen; here it fails the request
        ProgramResult c = programs.run("VHYT09C", Map.of("VHYT09C_CRIT", crit));
        String msg = "";
        List<Map<String, Object>> cars = List.of();
        if (c.control() != null && c.control().returnCode() == 100) msg = message(from, noConsist);
        else cars = rows(c, "VHYT09C_DATA", "dataRow");
        boolean moved = false;
        List<YardTrackingPseudoTrain.Consist> out = new ArrayList<>();
        Map<Integer, Map<Integer, YardTrackingPseudoTrain.Car>> placed = new LinkedHashMap<>();
        int y1 = 1;
        for (int x = 1; x <= consists.size(); x++) {                          // VHYT09IS-MOVE-DATA
            Map<String, Object> con = consists.get(x - 1);
            if (!text(con, "conNo").equals(text(row(cars, y1), "conNo"))) {
                msg = changed;
                break;
            }
            int max = (int) number(con, "maxPosnNo");
            Map<Integer, YardTrackingPseudoTrain.Car> slots = new LinkedHashMap<>();
            for (int k = 1; k <= 10; k++) {
                Map<String, Object> car = row(cars, y1 + k - 1);
                String colour = serviceable.contains(text(car, "vehStat")) ? black : red;   // VHYT09IS-COLOR, all ten positions
                boolean mine = max >= 1 && max <= 10 && k <= max;
                String number = mine ? carText(car) : text(con, "carNo" + k);
                String home = mine ? text(car, "homeYrd") : text(con, "homeYrd" + k);
                slots.put(k, new YardTrackingPseudoTrain.Car(k, number, home, colour));
            }
            placed.put(x, slots);
            moved = true;
            y1 += max;
        }
        for (int x = 1; x <= consists.size(); x++) {
            Map<String, Object> con = consists.get(x - 1);
            List<YardTrackingPseudoTrain.Car> list = new ArrayList<>();
            Map<Integer, YardTrackingPseudoTrain.Car> slots = placed.get(x);
            for (int k = 1; k <= 10; k++) {
                // consists after a mismatch keep VHYT09B's own car fields and colours
                YardTrackingPseudoTrain.Car car = slots != null ? slots.get(k)
                        : new YardTrackingPseudoTrain.Car(k, text(con, "carNo" + k), text(con, "homeYrd" + k), text(con, "color" + k));
                if (!car.car().isEmpty()) list.add(car);
            }
            out.add(new YardTrackingPseudoTrain.Consist(text(con, "conNo"), text(con, "yrdSubLoc"), text(con, "yrdTrkgYrd"),
                    text(con, "dateLastUpd"), text(con, "timeLastUpd"), list));
        }
        if (moved) msg = message(y, selected);                              // the old screen then shows the yard selected
        return new YardTrackingPseudoTrain.Report(y, moved ? out : List.of(), msg, moved);
    }

    /** VHYT01IS-INIT-X: the printers (WS70A01) */
    public List<YardTrackingPseudoTrain.Printer> printers() {
        ProgramResult r = programs.run("WS70A01", Map.of());
        List<YardTrackingPseudoTrain.Printer> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "WS70A01_DATA", "dataRow")) out.add(new YardTrackingPseudoTrain.Printer(text(row, "prtrLocDesc"), text(row, "vtamName")));
        return out;
    }

    /** VHYT01IS-SUBMIT: prints the yard's pseudo status trains on the printer (VHYT01A); URC 0, 4 and 100 are success */
    public void print(String yard, String printer) {
        String y = yard == null ? "" : yard.strip(), p = printer == null ? "" : printer.strip();
        // the old Hard Copy button stays disabled until a yard is listed and a printer chosen
        if (y.isEmpty() || p.isEmpty()) throw new InvalidRequestException("Choose a yard and a printer to print the pseudo status trains.");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yrdTrkgYrd", y);
        crit.put("reptType", p);
        programs.run("VHYT01A", Map.of("VHYT01A_CRIT", crit));
    }

    public int pageSize() {
        return pageSize;
    }

    /** FINAL-MSG-1 (3 characters: the yard) + FINAL-MSG-2, as the screen shows it */
    private static String message(String yard, String tail) {
        String head = yard.length() > 3 ? yard.substring(0, 3) : String.format("%-3s", yard);
        return (head + tail).stripTrailing();
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, int index) {
        return index >= 1 && index <= rows.size() ? rows.get(index - 1) : Map.of();
    }

    /** CAR-NO-X: the left mark, the car and the right mark (6 characters) */
    @SuppressWarnings("unchecked")
    private static String carText(Map<String, Object> car) {
        Object v = car.get("carNoX");
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> x = (Map<String, Object>) m;
            return (pad(x.get("lftOr"), 1) + pad(x.get("carNo"), 4) + pad(x.get("rgtOr"), 1)).strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    private static String pad(Object v, int n) {
        String s = v == null ? "" : v.toString();
        return s.length() >= n ? s.substring(0, n) : String.format("%-" + n + "s", s);
    }
}
