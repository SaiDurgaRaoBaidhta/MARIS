package com.bart.maris.middleware.domain.yardqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the old screens' rules and texts from the screen bundle (EGL) when a service starts, so nothing is copied into Java.
 * Module-local (docs/API_WORK_SPLIT.md: helpers start in the module; yardreports has its own, YardReportsLayout).
 */
public final class YardQueriesEgl {
    private YardQueriesEgl() {}

    /**
     * the value -> text pairs of a statement group's {@code if (X.field == "HY") Y.target = "HAYWARD SHOP"; end} branches,
     * in code order, e.g. VHYT25IS-INITIAL's yard -> shop name. Fails at start-up if there is none.
     */
    public static Map<String, String> pairs(ScreenBundle screens, String function, String pkg, String field, String target) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        Map<String, String> out = new LinkedHashMap<>();
        pairs(f.body(), field, target, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " has no " + field + " -> " + target + " branches");
        return out;
    }

    private static void pairs(List<Ast.Stmt> body, String field, String target, Map<String, String> out) {
        for (Ast.Stmt s : body) {
            if (!(s instanceof Ast.If i)) continue;
            if (i.cond() instanceof Ast.Binary b && b.op().equals("==") && b.left() instanceof Ast.Name n && b.right() instanceof Ast.Str v
                    && last(n).equalsIgnoreCase(field)) {
                for (Ast.Stmt t : i.then()) {
                    if (t instanceof Ast.Assign a && a.value() instanceof Ast.Str text && last(a.target()).equalsIgnoreCase(target)) {
                        out.putIfAbsent(v.value().strip(), text.value());
                    }
                }
            }
            pairs(i.then(), field, target, out);
            pairs(i.otherwise(), field, target, out);
        }
    }

    /** the texts a statement group compares a field with ({@code if (X.field == "SHOPSTAT")}), in code order */
    public static List<String> compared(ScreenBundle screens, String function, String pkg, String field) {
        return List.copyOf(pairsOrValues(screens, function, pkg, field));
    }

    private static List<String> pairsOrValues(ScreenBundle screens, String function, String pkg, String field) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new java.util.ArrayList<>();
        values(f.body(), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares " + field + " with nothing");
        return out;
    }

    private static void values(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            if (!(s instanceof Ast.If i)) continue;
            if (i.cond() instanceof Ast.Binary b && b.op().equals("==") && b.left() instanceof Ast.Name n && b.right() instanceof Ast.Str v
                    && last(n).equalsIgnoreCase(field) && !out.contains(v.value().strip())) out.add(v.value().strip());
            values(i.then(), field, out);
            values(i.otherwise(), field, out);
        }
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }
}
