package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Fleet report (old VHFR01I): every car (program VHFR01A) counted by car type into 17 rows, and the available GO cars
 * by the yard they are in; Hard Copy prints it (VHFR01C). Counts the way VHFR01IS-TYPE-A .. TYPE-K do; row names,
 * yards, car types and headings are read from the old screen.
 */
public final class VehicleQueriesFleetReportService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String INIT = "VHFR01IS-INIT";
    private static final Set<String> HELD = Set.of("STOR", "SAFETY", "ACIDNT", "TEST");
    private static final Set<String> AVAILABLE = Set.of("GO", "USABLE", "ERROR");
    /** VHFR01IS-TYPE-L / TYPE-M write their fleet count into type E's ("TOTAL-FLEET-E = TOTAL-FLEET-L + 1") */
    private static final Set<String> FLEET_INTO_E = Set.of("L", "M");

    private final Programs programs;
    private final SortedMap<Integer, String> rowNames;
    private final int rowCount;
    /** GO-CARS-IN-YARDS: the yards of the second table, the last one is "any other place" */
    private final List<String> goYards;
    private final List<String> types = new ArrayList<>();
    private final List<String> headings = new ArrayList<>();
    private final String submitted;

    public VehicleQueriesFleetReportService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        rowNames = t.indexed(INIT, PKG, "CAR-TYPE-DATA");
        rowCount = t.assignedNumber(INIT, PKG, "ROW-COUNT").intValue();
        goYards = List.copyOf(t.indexed(INIT, PKG, "GO-CARS-IN-YARDS").values());
        for (EglTexts.Column c : t.columns("VHFR01I")) {
            String f = c.field().toUpperCase(Locale.ROOT);
            if (!f.startsWith("CAR-") || !f.endsWith("-DATA") || f.equals("CAR-TYPE-DATA")) continue;
            String type = f.substring(4, f.length() - 5);
            if (!type.equals("TOTAL")) types.add(type);
            headings.add(c.heading());
        }
        submitted = t.triggeredPrompt("VHFR01I", "SUBMIT").strip();
    }

    public record Cells(List<Integer> byType, int total) {}

    public record Row(int row, String name, Cells cells) {}

    public record YardRow(String yard, Cells cells) {}

    /** both tables are empty when VHFR01A finds no car (the old tables stay empty) */
    public record FleetReport(List<String> types, List<String> headings, List<Row> rows, List<YardRow> goCarsByYard, String message) {}

    /** counters per car type with the old screen's own total counter last */
    private final class Counts {
        final Map<String, int[]> by = new HashMap<>();

        int[] of(String what) {
            return by.computeIfAbsent(what, k -> new int[types.size() + 1]);
        }

        void add(String what, int type) {
            of(what)[type]++;
            of(what)[types.size()]++;
        }
    }

    /** the car type column of a car: its type letter, C split by ATO equipment (CW with, CO without) */
    private int typeOf(Map<String, Object> car) {
        @SuppressWarnings("unchecked") Map<String, Object> t = car.get("carType") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        String letter = t == null ? "" : text(t, "carType1");
        if (letter.equals("C")) letter = text(car, "atoPresentInd").equals("Y") ? "CW" : "CO";
        return types.indexOf(letter);
    }

    public FleetReport report() {
        ProgramResult r = programs.attempt("VHFR01A", Map.of()).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new FleetReport(types, headings, List.of(), List.of(), null);
        if (urc != 0 && urc != 4) return new FleetReport(types, headings, List.of(), List.of(), programs.error("VHFR01A", r.control()).getMessage());

        Counts c = new Counts();
        Map<String, Counts> go = new HashMap<>();
        for (String y : goYards) go.put(y, new Counts());
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<Map<String, Object>> cars = rows(r, "VHFR01A_DATA", "dataRow");
        for (int i = 0; i < Math.min(n, cars.size()); i++) {
            Map<String, Object> car = cars.get(i);
            int t = typeOf(car);
            if (t < 0) continue;
            String stat = text(car, "vehStat");
            int[] fleet = c.of("fleet");
            if (FLEET_INTO_E.contains(types.get(t)) && types.contains("E")) {
                fleet[types.indexOf("E")] = fleet[t] + 1;                        // kept as the old screen counts (see FLEET_INTO_E)
                fleet[types.size()]++;
            } else c.add("fleet", t);
            if (HELD.contains(stat)) { c.add("held", t); continue; }
            if (text(car, "whereIsCar").equals("CON")) { c.add("revenue", t); continue; }
            c.add("maint", t);
            String war = text(car, "warInd");
            if (war.equals("Y") && stat.equals("NO GO")) c.add("warranty", t);
            else if (war.equals("N") && stat.equals("NO GO")) c.add("unscheduled", t);
            else if (stat.equals("AWPART")) c.add("awaitingParts", t);
            else if (stat.equals("PM")) c.add("preventive", t);
            else if (stat.equals("ENGHLD")) c.add("engineering", t);
            else if (stat.equals("MODHLD")) c.add("modification", t);
            else if (stat.equals("MISC")) c.add("misc", t);
            else {
                c.of("maint")[t]--;
                c.of("maint")[types.size()]--;
                if (!AVAILABLE.contains(stat)) { fleet[t]--; fleet[types.size()]--; }
                else {
                    c.add("goAvailable", t);
                    String loc = text(car, "currLoc");
                    int y = goYards.subList(0, goYards.size() - 1).indexOf(loc);
                    go.get(goYards.get(y < 0 ? goYards.size() - 1 : y)).add("go", t);
                }
            }
        }

        int[][] col = new int[rowCount + 1][];
        for (int i = 1; i <= rowCount; i++) col[i] = new int[types.size() + 1];
        for (int k = 0; k <= types.size(); k++) {
            int inMaint = c.of("unscheduled")[k] + c.of("awaitingParts")[k] + c.of("preventive")[k] + c.of("warranty")[k]
                    + c.of("engineering")[k] + c.of("modification")[k] + c.of("misc")[k];
            col[1][k] = c.of("fleet")[k];
            col[2][k] = c.of("held")[k];
            col[3][k] = c.of("fleet")[k] - c.of("held")[k];
            col[5][k] = c.of("unscheduled")[k];
            col[6][k] = c.of("awaitingParts")[k];
            col[7][k] = c.of("preventive")[k];
            col[8][k] = c.of("warranty")[k];
            col[9][k] = c.of("engineering")[k];
            col[10][k] = c.of("modification")[k];
            col[11][k] = c.of("misc")[k];
            col[13][k] = inMaint;
            col[15][k] = stored(col[3][k]) - stored(col[13][k]);
            col[16][k] = c.of("revenue")[k];
            col[17][k] = c.of("goAvailable")[k];
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= rowCount; i++) rows.add(new Row(i, rowNames.getOrDefault(i, ""), cells(col[i])));
        List<YardRow> byYard = new ArrayList<>();
        for (String y : goYards) byYard.add(new YardRow(y, cells(go.get(y).of("go"))));
        return new FleetReport(types, headings, rows, byYard, null);
    }

    private Cells cells(int[] v) {
        List<Integer> byType = new ArrayList<>();
        for (int k = 0; k < types.size(); k++) byType.add(stored(v[k]));
        return new Cells(byType, stored(v[types.size()]));
    }

    /** the old table cells are unsigned 4-digit numbers: a negative count loses its sign */
    private static int stored(int v) {
        return Math.abs(v) % 10_000;
    }

    /** Hard Copy (VHFR01IS-SUBMIT, program VHFR01C) */
    public String print(String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        programs.run("VHFR01C", Map.of("VHFR01C_CRIT", Map.of("reptType", printer.strip())));
        return submitted;
    }
}
