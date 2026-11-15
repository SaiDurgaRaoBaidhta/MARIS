package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The 17-row car summary by car type that VHYS05I and VHYS10I build. Cars are counted the way their TYPE-A .. TYPE-K
 * statement groups count them (every type alike); the table is then filled by evaluating the screen's own cell
 * assignments (TYPE-CHK: CAR-A-DATA[1] = TOTAL-FLEET-A, ...), so a screen that takes a cell from the wrong counter
 * shows the same here. Cells are unsigned 4-digit numbers, as on the old screens.
 */
final class VehicleQueriesTypeSummary {
    private static final Set<String> HELD = Set.of("STOR", "SAFETY", "ACIDNT", "TEST");
    private static final Set<String> AVAILABLE = Set.of("GO", "USABLE", "ERROR");
    /** the old counter names (before "-<type>") of each count */
    private static final Map<String, String> COUNTERS = Map.ofEntries(
            Map.entry("TOTAL-FLEET", "fleet"), Map.entry("TOTAL-YARD", "fleet"), Map.entry("HOLD-CARS", "held"), Map.entry("REV-SERVICE", "revenue"),
            Map.entry("UNSCHD-MAIN", "unscheduled"), Map.entry("WAITPART-UM", "awaitingParts"), Map.entry("PREVEN-MAIN", "preventive"),
            Map.entry("WARRANTY", "warranty"), Map.entry("ENGRNG-HOLD", "engineering"), Map.entry("MODIFY-CARS", "modification"),
            Map.entry("MISCEL-HOLD", "misc"), Map.entry("GO-AVAIL", "goAvailable"));

    record Cells(List<Integer> byType, int total) {}

    private final List<String> types;
    private final List<EglTexts.Cell> formulas;
    private final Map<String, int[]> by = new HashMap<>();

    /** formulas: the screen's CAR-...-DATA[n] assignments; types: its car types in column order */
    VehicleQueriesTypeSummary(List<String> types, List<EglTexts.Cell> formulas) {
        this.types = types;
        this.formulas = formulas;
    }

    private int[] of(String what) {
        return by.computeIfAbsent(what, k -> new int[types.size() + 1]);
    }

    private void add(String what, int t, int n) {
        of(what)[t] += n;
        of(what)[types.size()] += n;
    }

    /** one car of the program's rows (CAR-TYPE.CAR-TYPE-1, VEH-STAT, WHERE-IS-CAR, WAR-IND) */
    void count(Map<String, Object> car) {
        @SuppressWarnings("unchecked") Map<String, Object> ct = car.get("carType") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        int t = types.indexOf(text(ct, "carType1"));
        if (t < 0) return;
        String stat = text(car, "vehStat");
        add("fleet", t, 1);
        if (HELD.contains(stat)) { add("held", t, 1); return; }
        if (text(car, "whereIsCar").equals("CON")) { add("revenue", t, 1); return; }
        String war = text(car, "warInd");
        if (war.equals("Y") && stat.equals("NO GO")) add("warranty", t, 1);
        else if (war.equals("N") && stat.equals("NO GO")) add("unscheduled", t, 1);
        else if (stat.equals("AWPART")) add("awaitingParts", t, 1);
        else if (stat.equals("PM")) add("preventive", t, 1);
        else if (stat.equals("ENGHLD")) add("engineering", t, 1);
        else if (stat.equals("MODHLD")) add("modification", t, 1);
        else if (stat.equals("MISC")) add("misc", t, 1);
        else if (!AVAILABLE.contains(stat)) add("fleet", t, -1);
        else add("goAvailable", t, 1);
    }

    /** the rows 1..rowCount, filled by the screen's cell assignments in their order */
    List<Cells> rows(int rowCount) {
        Map<String, int[]> table = new HashMap<>();                    // "CAR-A-DATA" -> cells by row
        for (EglTexts.Cell c : formulas) {
            table.computeIfAbsent(c.field(), k -> new int[rowCount + 1]);
            if (c.row() >= 1 && c.row() <= rowCount) table.get(c.field())[c.row()] = stored(eval(c.value(), table, rowCount));
        }
        List<Cells> out = new ArrayList<>();
        for (int i = 1; i <= rowCount; i++) {
            List<Integer> byType = new ArrayList<>();
            for (String t : types) byType.add(table.getOrDefault("CAR-" + t + "-DATA", new int[rowCount + 1])[i]);
            out.add(new Cells(byType, table.getOrDefault("CAR-TOTAL-DATA", new int[rowCount + 1])[i]));
        }
        return out;
    }

    private int eval(Ast.Expr e, Map<String, int[]> table, int rowCount) {
        return switch (e) {
            case Ast.Binary b when b.op().equals("+") -> eval(b.left(), table, rowCount) + eval(b.right(), table, rowCount);
            case Ast.Binary b when b.op().equals("-") -> eval(b.left(), table, rowCount) - eval(b.right(), table, rowCount);
            case Ast.Num n -> n.value().intValue();
            case Ast.Name n -> {
                Ast.Segment last = n.segments().get(n.segments().size() - 1);
                String name = last.name().toUpperCase(Locale.ROOT);
                if (last.subscripts().size() == 1 && last.subscripts().get(0) instanceof Ast.Num row) {           // another cell
                    int[] col = table.get(name);
                    int r = row.value().intValue();
                    yield col == null || r < 1 || r > rowCount ? 0 : col[r];
                }
                int dash = name.lastIndexOf('-');
                String counter = dash < 0 ? null : COUNTERS.get(name.substring(0, dash));
                if (counter == null) yield 0;
                String type = name.substring(dash + 1);
                int k = type.equals("T") ? types.size() : types.indexOf(type);
                yield k < 0 ? 0 : of(counter)[k];
            }
            default -> 0;
        };
    }

    static int stored(int v) {
        return Math.abs(v) % 10_000;
    }
}
