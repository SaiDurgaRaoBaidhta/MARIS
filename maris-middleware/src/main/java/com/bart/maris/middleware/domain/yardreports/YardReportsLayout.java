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
        return colours(screens, function, pkg, "HOME-YRD", "CAR-COLOR", "CAR-COLOR-B");
    }

    /**
     * the same for any colour statement group or function: {@code if (yardField == "HY") textField = "GREEN"; ...}
     * (e.g. dy100Color: HOME_YARD, theColor); backgroundField null = the group sets no background ([text colour] only)
     */
    public static Map<String, List<String>> colours(ScreenBundle screens, String function, String pkg, String yardField,
                                                    String textField, String backgroundField) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        Map<String, List<String>> out = new LinkedHashMap<>();
        colours(f.body(), out, yardField, textField, backgroundField);
        if (!out.containsKey(OTHER_YARD)) throw new IllegalStateException(function + " has no colours for other home yards");
        return out;
    }

    private static void colours(List<Ast.Stmt> body, Map<String, List<String>> out, String yardField, String textField, String backgroundField) {
        for (Ast.Stmt s : body) {
            if (!(s instanceof Ast.If i)) continue;
            if (compared(i.cond(), yardField) instanceof Ast.Str yard) out.put(yard.value(), pair(i.then(), textField, backgroundField));
            if (i.otherwise().size() == 1 && i.otherwise().get(0) instanceof Ast.If) colours(i.otherwise(), out, yardField, textField, backgroundField);
            else if (!i.otherwise().isEmpty()) out.put(OTHER_YARD, pair(i.otherwise(), textField, backgroundField));
        }
    }

    /** the text colour (and background) a branch assigns */
    private static List<String> pair(List<Ast.Stmt> branch, String textField, String backgroundField) {
        String text = null, background = null;
        for (Ast.Stmt s : branch) {
            if (s instanceof Ast.Assign a && a.value() instanceof Ast.Str v) {
                String field = a.target().segments().get(a.target().segments().size() - 1).name();
                if (field.equalsIgnoreCase(textField)) text = v.value();
                if (field.equalsIgnoreCase(backgroundField == null ? "" : backgroundField)) background = v.value();
            }
        }
        if (text == null || (backgroundField != null && background == null)) {
            throw new IllegalStateException("a colour branch without " + textField + (backgroundField == null ? "" : " / " + backgroundField));
        }
        return backgroundField == null ? List.of(text) : List.of(text, background);
    }

    /**
     * every text a statement group assigns to a field, in the order the code has them (then before else), e.g. the two
     * VHIC03IS-MOVE messages; ScreenBundle.assignedText gives only the first. Fails at start-up if there is none.
     */
    public static List<String> assignedTexts(ScreenBundle screens, String function, String pkg, String field) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new java.util.ArrayList<>();
        texts(f.body(), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no text to " + field);
        return out;
    }

    private static void texts(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str v
                        && a.target().segments().get(a.target().segments().size() - 1).name().equalsIgnoreCase(field) -> out.add(v.value());
                case Ast.If i -> { texts(i.then(), field, out); texts(i.otherwise(), field, out); }
                case Ast.While w -> texts(w.body(), field, out);
                case Ast.Try t -> texts(t.body(), field, out);
                default -> {}
            }
        }
    }

    /**
     * every text a statement group compares a field with, with the given operator ("==" or "!="), in code order, e.g. the
     * car types VHYT53IS-VALID-MID accepts ({@code CAR-TYPE != "B1" && ...}). Fails at start-up if there is none.
     */
    public static List<String> comparedTexts(ScreenBundle screens, String function, String pkg, String field, String op) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new java.util.ArrayList<>();
        compared(f.body(), field, op, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares " + field + " with nothing (" + op + ")");
        return out;
    }

    private static void compared(List<Ast.Stmt> body, String field, String op, List<String> out) {
        for (Ast.Stmt s : body) {
            if (s instanceof Ast.If i) {
                compared(i.cond(), field, op, out);
                compared(i.then(), field, op, out);
                compared(i.otherwise(), field, op, out);
            } else if (s instanceof Ast.While w) compared(w.body(), field, op, out);
        }
    }

    private static void compared(Ast.Expr e, String field, String op, List<String> out) {
        if (!(e instanceof Ast.Binary b)) return;
        if (b.op().equals(op) && b.left() instanceof Ast.Name n && b.right() instanceof Ast.Str v
                && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase(field)) {
            if (!out.contains(v.value().strip())) out.add(v.value().strip());
        }
        compared(b.left(), field, op, out);
        compared(b.right(), field, op, out);
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
