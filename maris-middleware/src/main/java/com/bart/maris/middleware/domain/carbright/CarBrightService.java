package com.bart.maris.middleware.domain.carbright;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
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
 * Car thorough cleaning report (old screens VHCBR1I and its printer selection VHCBR2I). Rules from
 * MARIS_RUI/EGLSource/MRSVehicleCarBright/RUIFunctions.egl: VHCBR1IS-LIST (programs VHCBR1X, VHCBR1A),
 * VHCBR01IS-PRINT and VHCBR2IS-SUBMIT (program VHCBR02). Read-only except the print request.
 */
public class CarBrightService {
    private static final String PKG = "MRSVehicleCarBright";

    private final Programs programs;
    private final List<String> homeYards;
    private final String badYard, noCars, noDates, submitted;

    public CarBrightService(LegacyProgramService legacy, MessageResolver messages, EglTexts texts) {
        this.programs = new Programs(legacy, messages);
        this.homeYards = texts.items("VHCBR1I", "Drop-down_List1");
        this.badYard = texts.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "home yard");
        this.noCars = texts.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "No records");
        this.noDates = texts.assigned("VHCBR1IS-LIST", PKG, "FINAL-MESSAGE", "not retrieved");
        this.submitted = texts.prompt("VHCBR2I", "Message_Prompter1");
    }

    /** @param status the vehicle status (GO, NO GO ...); @param trainIncident the train incident code, when there is one */
    public record Car(String car, String location, String status, String trainIncident, String lastThoroughClean) {}

    /** @param message the old screen's message when no car is found, otherwise null */
    public record Cleaning(String homeYard, String option, String cleaningDate, String overdueDate, int count, List<Car> cars, String message) {}

    public record Printer(String description, String vtam) {}

    public List<String> homeYards() {
        return homeYards;
    }

    /** VHCBR1IS-LIST: option "last" (last cleaning, the default) or "overdue" (cleaning over due) */
    public Cleaning list(String homeYard, String option) {
        String yard = checkYard(homeYard);
        int opt = optionIndex(option);
        ProgramResult d = programs.attempt("VHCBR1X", Map.of()).result();
        if (d.control().returnCode() != 0) {
            throw new LegacyBusinessException("VHCBR1X", d.control(), new MessageResolver.Message("", "E", noDates));
        }
        Map<String, Object> dates = d.record("VHCBR1X_DATA");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", yard);
        crit.put("optionIndex", opt);
        crit.put("dateCrit", text(dates, "dateCarBrit"));
        crit.put("overDueDate", text(dates, "overDueDate"));
        ProgramResult r = programs.run("VHCBR1A", Map.of("VHCBR1A_CRIT", crit));
        List<Car> cars = new ArrayList<>();
        String message = null;
        if (r.control().rowCount() > 0) {
            for (Map<String, Object> row : rows(r, "VHCBR1A_DATA", "dataRow")) {        // two cars per row in the old list
                cars.add(car(row, ""));
                if (!text(row, "carNo1").isEmpty()) cars.add(car(row, "1"));
            }
        } else if (r.control().returnCode() == 100) {
            message = noCars;
        }
        return new Cleaning(yard, opt == 2 ? "overdue" : "last", text(dates, "dateCarBrit"), text(dates, "overDueDate"), cars.size(), cars, message);
    }

    /** one car of a row: the left one (suffix "") or the right one (suffix "1") */
    @SuppressWarnings("unchecked")
    private static Car car(Map<String, Object> row, String n) {
        Object st = row.get("vehStat" + n), date = row.get("dateCarBrit" + n);
        Map<String, Object> status = st instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of("vehStatDb" + n, st == null ? "" : st);
        return new Car(text(row, "carNo" + n), text(row, "currLoc" + n), text(status, "vehStatDb" + n), text(status, "trainIncd" + n), joined(date));
    }

    /** a field the record splits into parts (DATE-CAR-BRIT = REST + YEAR) read back as one text */
    private static String joined(Object v) {
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder();
            for (Object part : m.values()) sb.append(part == null ? "" : part.toString());
            return sb.toString().strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    /** the printers a report can go to (WS70A01) */
    public List<Printer> printers() {
        List<Printer> out = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("WS70A01", Map.of()), "WS70A01_DATA", "dataRow")) {
            out.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        }
        return out;
    }

    /**
     * VHCBR01IS-PRINT + VHCBR2IS-SUBMIT. As the old screens send it: the report date is not passed down (blank);
     * option 1 sends no BRIGHTEN-SW, option 2 sends "N".
     */
    public String print(String homeYard, String option, String printer) {
        String yard = checkYard(homeYard);
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        int opt = optionIndex(option);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", printer.strip());
        crit.put("dateReptFrom", "");
        crit.put("homeYrd", yard);
        crit.put("brightenSw", opt == 2 ? "N" : "");
        programs.run("VHCBR02", Map.of("VHCBR02_CRIT", crit));
        return submitted;
    }

    private String checkYard(String homeYard) {
        String y = homeYard == null ? "" : homeYard.strip().toUpperCase(java.util.Locale.ROOT);
        if (!homeYards.contains(y)) throw new InvalidRequestException(badYard);
        return y;
    }

    private static int optionIndex(String option) {
        return option != null && option.strip().equalsIgnoreCase("overdue") ? 2 : 1;
    }
}
