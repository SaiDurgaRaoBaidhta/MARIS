package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the pseudo train windows' texts and numbers from the screen bundle (EGL) when a service starts, so nothing is
 * copied into Java (old VHYT09I, VHYT01I, VHYT07I, VHYT12I). Module-local helper of the pseudo train feature.
 */
public final class YardTrackingPseudoEgl {
    private YardTrackingPseudoEgl() {}

    /** the text literals a statement group assigns to a field ({@code X.field = "..."}), in code order, spaces kept */
    public static List<String> assigned(ScreenBundle screens, String function, String pkg, String field) {
        List<String> out = new ArrayList<>();
        walk(body(screens, function, pkg), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no text to " + field);
        return out;
    }

    /** the first text a statement group assigns to a field, spaces kept */
    public static String first(ScreenBundle screens, String function, String pkg, String field) {
        return assigned(screens, function, pkg, field).get(0);
    }

    /** the divisor of the first division a statement group makes, e.g. VHYT09IS-PAGE-CNT's {@code CON-ROW-CNT / 4.00} */
    public static int divisor(ScreenBundle screens, String function, String pkg) {
        for (Ast.Stmt s : body(screens, function, pkg)) {
            if (s instanceof Ast.Assign a && a.value() instanceof Ast.Binary b && b.op().equals("/") && b.right() instanceof Ast.Num n) {
                return n.value().intValue();
            }
        }
        throw new IllegalStateException(function + " divides by no number");
    }

    /** the text literals the first {@code if} of a statement group compares with, e.g. VHYT09IS-COLOR's "GO    ", "USABLE" */
    public static List<String> compared(ScreenBundle screens, String function, String pkg) {
        for (Ast.Stmt s : body(screens, function, pkg)) {
            if (s instanceof Ast.If i) {
                List<String> out = new ArrayList<>();
                literals(i.cond(), out);
                if (!out.isEmpty()) return out;
            }
        }
        throw new IllegalStateException(function + " compares with no text");
    }

    private static void literals(Ast.Expr e, List<String> out) {
        switch (e) {
            case Ast.Str s -> out.add(s.value());
            case Ast.Binary b -> { literals(b.left(), out); literals(b.right(), out); }
            default -> {}
        }
    }

    /** a message prompter's text (MessagePrompter messageString) */
    public static String prompt(ScreenBundle screens, String screen, String prompter) {
        String t = screens.promptText(screen, prompter);
        if (t == null) throw new IllegalStateException(screen + " prompter " + prompter + " has no text");
        return t;
    }

    private static List<Ast.Stmt> body(ScreenBundle screens, String function, String pkg) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        return f.body();
    }

    private static void walk(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str v
                        && a.target().segments().get(a.target().segments().size() - 1).name().equalsIgnoreCase(field) -> out.add(v.value());
                case Ast.If i -> { walk(i.then(), field, out); walk(i.otherwise(), field, out); }
                case Ast.While w -> walk(w.body(), field, out);
                case Ast.Try t -> walk(t.body(), field, out);
                default -> {}
            }
        }
    }
}
