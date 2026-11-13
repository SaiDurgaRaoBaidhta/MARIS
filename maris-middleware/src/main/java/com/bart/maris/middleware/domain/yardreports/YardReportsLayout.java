package com.bart.maris.middleware.domain.yardreports;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A yard map's tracks and spots, read from the old screen's LOAD statement group when the service starts:
 * {@code if (DATA.YRD-SUB-LOC[COUNTER] == "S-1") VHIC01IS-S1();} names a track and the function placing its cars,
 * whose {@code if (DATA.POSN-NO[COUNTER] == n)} branches are the spots. Fails at start-up if the EGL no longer has them.
 */
public final class YardReportsLayout {
    /** the key of the colours used for any other home yard (the last else) */
    public static final String OTHER_YARD = "*";

    private YardReportsLayout() {}

    /**
     * a map's home-yard colours, read from its colour statement group, e.g. VHIC01IS-COLOR:
     * {@code if (DATA.HOME-YRD[COUNTER] == "HY") ...CAR-COLOR = "GREEN"; ...CAR-COLOR-B = "GREY55"; else ...}
     * -> home yard (or {@link #OTHER_YARD}) -> [text colour, background]
     */
    public static Map<String, List<String>> colours(ScreenBundle screens, String function, String pkg) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        Map<String, List<String>> out = new LinkedHashMap<>();
        colours(f.body(), out);
        if (!out.containsKey(OTHER_YARD)) throw new IllegalStateException(function + " has no colours for other home yards");
        return out;
    }

    private static void colours(List<Ast.Stmt> body, Map<String, List<String>> out) {
        for (Ast.Stmt s : body) {
            if (!(s instanceof Ast.If i)) continue;
            if (compared(i.cond(), "HOME-YRD") instanceof Ast.Str yard) out.put(yard.value(), pair(i.then()));
            if (i.otherwise().size() == 1 && i.otherwise().get(0) instanceof Ast.If) colours(i.otherwise(), out);
            else if (!i.otherwise().isEmpty()) out.put(OTHER_YARD, pair(i.otherwise()));
        }
    }

    /** the CAR-COLOR and CAR-COLOR-B texts a branch assigns */
    private static List<String> pair(List<Ast.Stmt> branch) {
        String text = null, background = null;
        for (Ast.Stmt s : branch) {
            if (s instanceof Ast.Assign a && a.value() instanceof Ast.Str v) {
                String field = a.target().segments().get(a.target().segments().size() - 1).name().toUpperCase(java.util.Locale.ROOT);
                if (field.equals("CAR-COLOR")) text = v.value();
                if (field.equals("CAR-COLOR-B")) background = v.value();
            }
        }
        if (text == null || background == null) throw new IllegalStateException("a colour branch without CAR-COLOR / CAR-COLOR-B");
        return List.of(text, background);
    }

    /** track -> its spots, in the LOAD's order */
    public static Map<String, Set<Integer>> of(ScreenBundle screens, String load, String pkg) {
        Ast.Function f = screens.function(load, pkg);
        if (f == null) throw new IllegalStateException("function " + load + " not found");
        Map<String, Set<Integer>> out = new LinkedHashMap<>();
        tracks(f.body(), screens, pkg, out);
        if (out.isEmpty()) throw new IllegalStateException(load + " places no cars on any track");
        return out;
    }

    private static void tracks(List<Ast.Stmt> body, ScreenBundle screens, String pkg, Map<String, Set<Integer>> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.If i -> {
                    String track = compared(i.cond(), "YRD-SUB-LOC") instanceof Ast.Str str ? str.value() : null;
                    String placer = track == null ? null : firstCall(i.then());
                    if (placer != null) {
                        Ast.Function p = screens.function(placer, pkg);
                        if (p == null) throw new IllegalStateException("function " + placer + " not found");
                        Set<Integer> spots = new TreeSet<>();
                        spots(p.body(), spots);
                        out.put(track, spots);
                    } else tracks(i.then(), screens, pkg, out);
                    tracks(i.otherwise(), screens, pkg, out);
                }
                case Ast.While w -> tracks(w.body(), screens, pkg, out);
                case Ast.For fo -> tracks(fo.body(), screens, pkg, out);
                case Ast.Try t -> tracks(t.body(), screens, pkg, out);
                default -> {}
            }
        }
    }

    private static void spots(List<Ast.Stmt> body, Set<Integer> out) {
        for (Ast.Stmt s : body) {
            if (s instanceof Ast.If i) {
                if (compared(i.cond(), "POSN-NO") instanceof Ast.Num n) out.add(n.value().intValue());
                spots(i.then(), out);
                spots(i.otherwise(), out);
            }
        }
    }

    /** the literal a field (last name segment) is compared with in {@code field == literal}, else null */
    private static Ast.Expr compared(Ast.Expr e, String field) {
        if (e instanceof Ast.Binary b && b.op().equals("==") && b.left() instanceof Ast.Name n
                && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase(field)) return b.right();
        return null;
    }

    private static String firstCall(List<Ast.Stmt> body) {
        return !body.isEmpty() && body.get(0) instanceof Ast.ExprStmt es && es.expr() instanceof Ast.Call c ? c.name() : null;
    }
}
