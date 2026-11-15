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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Home yard fleet summary (old VHHF01I) and its print (printer window VHHF02I). For each home yard the old screen
 * reads the yard's cars (VHHF01A) and the cars required (VHHF01B) and counts them by car type into 18 rows; this
 * service counts the same way (VHHF01IS-TYPE-A .. TYPE-K, REQD-CHK, MOVE-CY .. MOVE-RY). The row names, yards, car
 * types and headings are read from the old screen.
 */
public final class VehicleQueriesHomeYardFleetService {
    private static final String PKG = "MRSVehicleQueries";
    /** VEH-STAT values the old screen counts as held (not in the fleet) */
    private static final Set<String> HELD = Set.of("STOR", "SAFETY", "ACIDNT", "TEST");
    /** car types whose fleet count the old screen writes into type D's count (see {@link #count}) */
    private static final Set<String> FLEET_INTO_D = Set.of("L", "M");
    /** VEH-STAT values of a car that is not in maintenance and still in the fleet */
    private static final Set<String> AVAILABLE = Set.of("GO", "USABLE", "ERROR");

    private final Programs programs;
    private final SortedMap<Integer, String> rowNames;
    private final int rowCount;
    private final List<String> yards;
    /** car type letters in the old columns' order, e.g. A B C D E L M K */
    private final List<String> types = new ArrayList<>();
    /** the old headings: per car type, then the total's */
    private final List<String> headings = new ArrayList<>();
    private final String submitted;

    public VehicleQueriesHomeYardFleetService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        rowNames = t.indexed("VHHF01IS-INIT", PKG, "CAR-TYPE-DATA");
        rowCount = t.assignedNumber("VHHF01IS-INIT", PKG, "ROW-COUNT").intValue();
        yards = List.copyOf(t.indexed("VHHF01IS-INIT", PKG, "HOME-YRD-OCCR").values());
        String first = "CAR-" + yards.get(0);
        for (EglTexts.Column c : t.columns("VHHF01I")) {
            String f = c.field().toUpperCase(Locale.ROOT);
            if (!f.startsWith(first) || !f.endsWith("-DATA")) continue;
            String type = f.substring(first.length(), f.length() - 5);
            if (!type.equals("TOTAL")) types.add(type);
            headings.add(c.heading());
        }
        submitted = t.triggeredPrompt("VHHF02I", "SUBMIT").strip();
    }

    /** one yard's counts: per car type (in {@link Fleet#types()} order) and the total */
    public record Cells(List<Integer> byType, int total) {}

    public record Row(int row, String name, Map<String, Cells> yards) {}

    /**
     * {@code rows} is empty when no yard has cars (the old table stays empty); {@code message}: the backend's message
     * when a yard could not be read (the other yards still show)
     */
    public record Fleet(List<String> yards, List<String> types, List<String> headings, List<Row> rows, String message) {}

    /** the counters of one yard: per car type, and the old screen's own total counter (the -T fields) last */
    private final class Counts {
        final Map<String, int[]> by = new HashMap<>();

        int[] of(String what) {
            return by.computeIfAbsent(what, k -> new int[types.size() + 1]);
        }

        void add(String what, int type, int n) {
            of(what)[type] += n;
            of(what)[types.size()] += n;
        }
    }

    public Fleet summary() {
        // the old screen's table: row -> yard -> cells (all zero until a yard's MOVE fills its column)
        Map<String, int[][]> columns = new LinkedHashMap<>();
        for (String y : yards) columns.put(y, new int[rowCount + 1][types.size() + 1]);
        Map<String, Object> crit = new LinkedHashMap<>();
        int[] required = new int[types.size()];                // TOTAL-REQD-*: kept when VHHF01B fails
        String message = null;
        boolean loaded = false;                                 // LOAD-DATA: the old table fills only after a yard was counted
        for (String yard : yards) {
            crit.put("homeYrdOccr", yards);
            crit.put("homeYrd", yard);
            for (String type : types) crit.put("awp" + type + "Count", 0);
            Programs.Outcome a = programs.attempt("VHHF01A", Map.of("VHHF01A_CRIT", crit));
            ProgramResult r = a.result();
            Map<String, Object> back = r.record("VHHF01A_CRIT");
            if (back != null) { crit.clear(); crit.putAll(back); }               // the record keeps what VHHF01A returned
            int urc = r.control() == null ? 0 : r.control().returnCode();
            if (urc == 100) continue;
            if (urc != 0 && urc != 4) { message = programs.error("VHHF01A", r.control()).getMessage(); continue; }

            Counts c = count(r);
            loaded = true;
            Programs.Outcome b = programs.attempt("VHHF01B", Map.of("VHHF01B_CRIT", Map.of("yrd", yard)));
            if (b.ok()) {
                required = new int[types.size()];
                int n = b.result().control() == null ? 0 : b.result().control().rowCount();
                List<Map<String, Object>> reqd = rows(b.result(), "VHHF01B_DATA", "dataRow");
                for (int i = 0; i < Math.min(n, reqd.size()); i++) {
                    int type = types.indexOf(typeOf(reqd.get(i)));
                    if (type >= 0) required[type] += (int) number(reqd.get(i), "cntCarsReqd");
                }
            }
            int[] awp = new int[types.size()];
            for (int i = 0; i < types.size(); i++) awp[i] = back == null ? 0 : (int) number(back, "awp" + types.get(i) + "Count");
            move(columns.get(yard), c, awp, required);
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; loaded && i <= rowCount; i++) {
            Map<String, Cells> byYard = new LinkedHashMap<>();
            for (String y : yards) {
                int[] cells = columns.get(y)[i];
                List<Integer> byType = new ArrayList<>();
                for (int k = 0; k < types.size(); k++) byType.add(stored(cells[k]));
                byYard.put(y, new Cells(byType, stored(cells[types.size()])));
            }
            rows.add(new Row(i, rowNames.getOrDefault(i, ""), byYard));
        }
        return new Fleet(yards, types, headings, rows, message);
    }

    private static String typeOf(Map<String, Object> row) {
        @SuppressWarnings("unchecked") Map<String, Object> t = row.get("carType") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        return t == null ? "" : text(t, "carType1");
    }

    /** VHHF01IS-TYPE-CHK with TYPE-A .. TYPE-K: count every car the program returned */
    private Counts count(ProgramResult r) {
        Counts c = new Counts();
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<Map<String, Object>> cars = rows(r, "VHHF01A_DATA", "dataRow");
        for (int i = 0; i < Math.min(n, cars.size()); i++) {
            Map<String, Object> car = cars.get(i);
            int t = types.indexOf(typeOf(car));
            if (t < 0) continue;
            String stat = text(car, "vehStat");
            int[] fleet = c.of("fleet");
            if (FLEET_INTO_D.contains(types.get(t)) && types.contains("D")) {
                // VHHF01IS-TYPE-L / TYPE-M: "TOTAL-FLEET-D = TOTAL-FLEET-L + 1" (copied from TYPE-D): the car is not
                // counted in its own column and replaces the D count; the total still counts it. Kept as the old screen shows it.
                fleet[types.indexOf("D")] = fleet[t] + 1;
                fleet[types.size()] += 1;
            } else c.add("fleet", t, 1);
            if (HELD.contains(stat)) { c.add("held", t, 1); continue; }
            if (text(car, "whereIsCar").equals("CON")) { c.add("revenue", t, 1); continue; }
            c.add("maint", t, 1);
            String war = text(car, "warInd");
            if (war.equals("Y") && stat.equals("NO GO")) c.add("warranty", t, 1);
            else if (war.equals("N") && stat.equals("NO GO")) c.add("unscheduled", t, 1);
            else if (stat.equals("AWPART")) { /* counted by the program (AWP-*-COUNT) */ }
            else if (stat.equals("PM")) c.add("preventive", t, 1);
            else if (stat.equals("ENGHLD")) c.add("engineering", t, 1);
            else if (stat.equals("MODHLD")) c.add("modification", t, 1);
            else if (stat.equals("MISC")) c.add("misc", t, 1);
            else {
                c.add("maint", t, -1);
                if (!AVAILABLE.contains(stat)) c.add("fleet", t, -1);
                else c.add("goAvailable", t, 1);
            }
        }
        return c;
    }

    /** VHHF01IS-MOVE-CY .. MOVE-RY: one yard's column */
    private void move(int[][] col, Counts c, int[] awp, int[] required) {
        for (int k = 0; k <= types.size(); k++) {
            boolean total = k == types.size();
            int fleet = sum(c.of("fleet"), k, total), held = sum(c.of("held"), k, total);
            int inMaint = sum(c.of("unscheduled"), k, total) + sum(c.of("preventive"), k, total) + sum(c.of("warranty"), k, total)
                    + sum(c.of("engineering"), k, total) + sum(c.of("modification"), k, total) + sum(c.of("misc"), k, total);
            col[1][k] = fleet;
            col[2][k] = held;
            col[3][k] = fleet - held;
            col[5][k] = sum(c.of("unscheduled"), k, total);
            col[6][k] = sum(c.of("preventive"), k, total);
            col[7][k] = sum(c.of("warranty"), k, total);
            col[8][k] = sum(c.of("engineering"), k, total);
            col[9][k] = sum(c.of("modification"), k, total);
            col[10][k] = sum(c.of("misc"), k, total);
            col[11][k] = inMaint;
            col[13][k] = sum(awp, k, total);
            col[15][k] = sum(required, k, total);
            col[16][k] = col[3][k] - col[11][k];
            col[17][k] = sum(c.of("revenue"), k, total);
            col[18][k] = sum(c.of("goAvailable"), k, total);
        }
    }

    /**
     * a count as the old table's cell keeps it: the cells are unsigned NUM(4) fields (VHHF01IW DATA-ROW), so a negative
     * count loses its sign and only the last 4 digits stay
     */
    private static int stored(int v) {
        return Math.abs(v) % 10_000;
    }

    /** a counter's value: per type, or for the total its own -T counter (counters) or the sum (awp, required) */
    private int sum(int[] v, int k, boolean total) {
        if (!total || v.length > types.size()) return v[k];
        int s = 0;
        for (int x : v) s += x;
        return s;
    }

    /** Print, then the printer window's Hard Copy (VHHF02IS-SUBMIT, program VHHF02A) */
    public String print(String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        programs.run("VHHF02A", Map.of("VHHF02A_CRIT", Map.of("reptType", printer.strip())));
        return submitted;
    }
}
