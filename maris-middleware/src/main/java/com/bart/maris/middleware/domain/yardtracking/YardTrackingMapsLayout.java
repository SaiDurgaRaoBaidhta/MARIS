package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A yard tracking map's tracks and spots, read from the old screen's LOAD when the service starts. The yard reports
 * module's reader (YardReportsLayout.of) covers {@code if (DATA.YRD-SUB-LOC[CNT] == "S-1") PLACER();} with placers that
 * test POSN-NO. The Hayward shop (VHYT04IS-LOAD) also has
 * <ul>
 *   <li>tracks tested together: {@code if (YRD-SUB-LOC == "3N-1" || YRD-SUB-LOC == "3N-2" ...) VHYT04IS-3-N();}</li>
 *   <li>placers that test no POSN-NO (VHYT04IS-2-N1, -3-N ...): the track has one spot and shows its last row, whatever
 *       the row's position. Such a track has an EMPTY spot set.</li>
 * </ul>
 */
public final class YardTrackingMapsLayout {
    private YardTrackingMapsLayout() {}

    /** track -> its spots (empty: one spot, any position), in the LOAD's order */
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
                    List<String> names = new ArrayList<>();
                    trackNames(i.cond(), names);
                    String placer = names.isEmpty() ? null : firstCall(i.then());
                    if (placer != null) {
                        Ast.Function p = screens.function(placer, pkg);
                        if (p == null) throw new IllegalStateException("function " + placer + " not found");
                        Set<Integer> spots = new TreeSet<>();
                        spots(p.body(), spots);
                        for (String n : names) out.put(n, spots);
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

    /** the tracks a condition tests: YRD-SUB-LOC == "x", or several joined with || */
    private static void trackNames(Ast.Expr e, List<String> out) {
        if (!(e instanceof Ast.Binary b)) return;
        if (b.op().equals("||")) {
            List<String> l = new ArrayList<>(), r = new ArrayList<>();
            trackNames(b.left(), l);
            trackNames(b.right(), r);
            if (!l.isEmpty() && !r.isEmpty()) { out.addAll(l); out.addAll(r); }
            return;
        }
        if (b.op().equals("==") && b.left() instanceof Ast.Name n && b.right() instanceof Ast.Str v
                && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase("YRD-SUB-LOC")) out.add(v.value());
    }

    private static void spots(List<Ast.Stmt> body, Set<Integer> out) {
        for (Ast.Stmt s : body) {
            if (s instanceof Ast.If i) {
                if (i.cond() instanceof Ast.Binary b && b.op().equals("==") && b.left() instanceof Ast.Name n
                        && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase("POSN-NO") && b.right() instanceof Ast.Num num) {
                    out.add(num.value().intValue());
                }
                spots(i.then(), out);
                spots(i.otherwise(), out);
            }
        }
    }

    private static String firstCall(List<Ast.Stmt> body) {
        return !body.isEmpty() && body.get(0) instanceof Ast.ExprStmt es && es.expr() instanceof Ast.Call c ? c.name() : null;
    }
}
