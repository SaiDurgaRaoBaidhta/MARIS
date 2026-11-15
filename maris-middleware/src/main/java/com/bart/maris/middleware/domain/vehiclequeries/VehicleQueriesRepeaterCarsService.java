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
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repeater cars (old VHRC01I criteria, VHRC02I incidents list): the home yards, the checks before Display, the
 * incidents of a car or a yard (VHRC02A), and Hard Copy (VHRC011). Messages, the "all" choice and the row limit come
 * from the old screens.
 */
public final class VehicleQueriesRepeaterCarsService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final String all, invalidYard, carNotNumeric, carNotFound, useHardCopy, submitted, footer;
    private final int footerAbove;
    /** DISP-VEH-STAT's fields, to show the status the way the old column does (status, filler, incident indicator) */
    private final List<FieldLayout> statusParts;

    public VehicleQueriesRepeaterCarsService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        all = t.assigned("VHRC01IS-INIT", PKG, "YRD", null);
        invalidYard = t.triggeredPrompt("VHRC01I", "INVALID-HOMEYARD");
        carNotNumeric = t.triggeredPrompt("VHRC01I", "CAR-NOT-NUMERIC");
        carNotFound = t.triggeredPrompt("VHRC01I", "CAR-NOT-FOUND");
        useHardCopy = t.triggeredPrompt("VHRC01I", "HARD-COPY-VALID");
        submitted = t.triggeredPrompt("VHRC01I", "SUBMIT").strip();
        footer = t.label("VHRC02I", "lblFooter");
        footerAbove = Integer.parseInt(t.compared("VHRC02IS-RET-DATA", PKG, "TOTAL-INCDS").get(0));
        statusParts = statusLayout(legacy, catalog);
    }

    private static List<FieldLayout> statusLayout(LegacyProgramService legacy, LegacyCatalog catalog) {
        for (ServiceDefinition.Param p : legacy.definition("VHRC02A").params()) {
            if (!p.name().equals("VHRC02A_DATA")) continue;
            for (FieldLayout row : catalog.record(p.record()).fields()) {
                for (FieldLayout f : row.children()) if (f.json().equals("dispVehStat")) return f.children();
            }
        }
        throw new IllegalStateException("VHRC02A_DATA has no DATA_ROW.DISP-VEH-STAT");
    }

    public record Printer(String description, String vtam) {}

    /** {@code homeYard}: the yard the old screen selects when it opens (and on Clear) */
    public record Options(List<String> homeYards, String homeYard, List<Printer> printers) {}

    /** VHRC01IS-INIT (program VHYS01A, then the "all" choice) and the printers (WS70A01) */
    public Options options() {
        List<String> yards = new ArrayList<>();
        Programs.Outcome o = programs.attempt("VHYS01A", Map.of());
        if (o.ok()) {
            for (Map<String, Object> r : rows(o.result(), "VHYS01A_DATA", "dataRow")) yards.add(text(r, "yrd"));
            yards.add(all);
        }
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return new Options(yards, all, printers);
    }

    public record Incident(String car, String location, String status, String vehicleStatus, String incidentIndicator, String incident,
                           String date, String time, long hoursBetween, long hoursFromLast, String symptom) {}

    /** {@code footer}: the old note when only the first rows are shown */
    public record Incidents(String homeYard, String car, int total, List<Incident> incidents, String footer) {}

    /** Display (VHRC01IS-DISPLAY), then VHRC02I's list (VHRC02IS-RET-DATA, program VHRC02A) */
    public Incidents list(String homeYard, String car) {
        String yard = homeYard == null ? "" : homeYard.strip();
        String c = car == null ? "" : car.strip();
        if (yard.isEmpty()) throw new InvalidRequestException(invalidYard);
        if (yard.equals(all)) throw new InvalidRequestException(useHardCopy);
        if (!c.isEmpty() && !c.equals(all)) {
            if (!c.chars().allMatch(Character::isDigit)) throw new InvalidRequestException(carNotNumeric);
            ProgramResult r = programs.attempt("VHCI01B", Map.of("VHCI01B_DATA", Map.of("carNo", c))).result();
            int urc = r.control() == null ? 0 : r.control().returnCode();
            if (urc == 100) throw new InvalidRequestException(carNotFound);
            if (urc != 0 && urc != 4) throw programs.error("VHCI01B", r.control());
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", c);
        crit.put("homeYrd", yard);
        ProgramResult r = programs.attempt("VHRC02A", Map.of("VHRC02A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new Incidents(yard, c, 0, List.of(), null);
        if (urc != 0 && urc != 4) throw programs.error("VHRC02A", r.control());
        List<Incident> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHRC02A_DATA", "dataRow")) {
            @SuppressWarnings("unchecked") Map<String, Object> st = row.get("dispVehStat") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            StringBuilder shown = new StringBuilder();
            for (FieldLayout f : statusParts) shown.append(String.format("%-" + f.length() + "s", text(st, f.json())));
            out.add(new Incident(text(row, "carNo"), text(row, "currLoc"), shown.toString().stripTrailing(), text(st, "vehStat"),
                    text(st, "vehIncdInd"), text(row, "incdNo"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"),
                    number(row, "hrsBetIncds"), number(row, "hrsFromLastIncd"), text(row, "sympCd")));
        }
        int total = (int) number(r.record("VHRC02A_DATA"), "totalIncds");
        return new Incidents(yard, c, total, out, total > footerAbove ? footer : null);
    }

    /** Hard Copy (VHRC01IS-SUBMIT, program VHRC011): the old screen sends the criteria as they are */
    public String print(String homeYard, String car, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", homeYard == null ? "" : homeYard.strip());
        crit.put("carNo", car == null ? "" : car.strip());
        crit.put("reptType", printer.strip());
        programs.run("VHRC011", Map.of("VHRC011_CRIT", crit));
        return submitted;
    }
}
