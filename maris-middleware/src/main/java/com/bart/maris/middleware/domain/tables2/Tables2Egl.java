package com.bart.maris.middleware.domain.tables2;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the table maintenance 2 screens' texts from the screen bundle (EGL) when a service starts, so nothing is copied into
 * Java. Module-local (docs/API_WORK_SPLIT.md: helpers start in the module).
 */
public final class Tables2Egl {
    static final String PKG = "MRSTableMaintenanceApp2";

    private Tables2Egl() {}

    /** every text a statement group assigns to a field ({@code X.field = "..."}), in code order, as written (blanks kept) */
    public static List<String> assigned(ScreenBundle screens, String function, String field) {
        Ast.Function f = screens.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new ArrayList<>();
        walk(f.body(), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no text to " + field);
        return out;
    }

    /** the distinct texts that are not blank, trimmed, in code order (the messages a statement group can show) */
    public static List<String> messages(ScreenBundle screens, String function, String field, int expected) {
        List<String> out = new ArrayList<>();
        for (String t : assigned(screens, function, field)) if (!t.isBlank() && !out.contains(t.strip())) out.add(t.strip());
        if (out.size() != expected) throw new IllegalStateException(function + " has " + out.size() + " " + field + " texts, expected " + expected + ": " + out);
        return out;
    }

    /** the first text a statement group assigns to a field */
    public static String first(ScreenBundle screens, String function, String field) {
        return assigned(screens, function, field).get(0);
    }

    private static void walk(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str v && last(a.target()).equalsIgnoreCase(field) -> out.add(v.value());
                case Ast.If i -> { walk(i.then(), field, out); walk(i.otherwise(), field, out); }
                case Ast.While w -> walk(w.body(), field, out);
                case Ast.For f -> walk(f.body(), field, out);
                case Ast.Try t -> { walk(t.body(), field, out); walk(t.onException(), field, out); }
                default -> { }
            }
        }
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }
}
