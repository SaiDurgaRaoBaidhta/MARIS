package com.bart.maris.middleware.domain.tables3;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the old table maintenance screens' texts and rules from the screen bundle (EGL) when a service starts, so
 * nothing is copied into Java. Module-local (docs/API_WORK_SPLIT.md: helpers start in the module).
 */
public final class Tables3Egl {
    static final String PKG = "MRSTableMaintenanceApp3";

    private Tables3Egl() {}

    /** every text a statement group assigns to a field, in code order (then before else), blanks included */
    public static List<String> assigned(ScreenBundle screens, String function, String field) {
        List<String> out = new ArrayList<>();
        texts(body(screens, function), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no text to " + field);
        return out;
    }

    /** the same, blank texts left out, each stripped */
    public static List<String> messages(ScreenBundle screens, String function, String field) {
        List<String> out = assigned(screens, function, field).stream().filter(s -> !s.isBlank()).map(String::strip).toList();
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns only blanks to " + field);
        return out;
    }

    /** the texts a statement group compares a field with ("if (X.F == 'ALL' || ...)"), in code order */
    public static List<String> compared(ScreenBundle screens, String function, String field) {
        List<String> out = new ArrayList<>();
        compares(body(screens, function), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares " + field + " with no text");
        return out;
    }

    /**
     * One check per top-level if of a statement group like VHCF50IS_DT_CHK: "if (REC.FIELD == blank) else { ... MSG =
     * 'text' }" gives (FIELD, text).
     */
    public static List<Map.Entry<String, String>> fieldChecks(ScreenBundle screens, String function, String messageField) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        for (Ast.Stmt s : body(screens, function)) {
            if (!(s instanceof Ast.If i) || !(i.cond() instanceof Ast.Binary b) || !(b.left() instanceof Ast.Name n)) continue;
            List<String> msg = new ArrayList<>();
            texts(i.otherwise(), messageField, msg);
            if (msg.size() != 1) continue;
            out.add(Map.entry(last(n), msg.get(0).strip()));
        }
        if (out.isEmpty()) throw new IllegalStateException(function + " has no field checks");
        return out;
    }

    private static List<Ast.Stmt> body(ScreenBundle screens, String function) {
        Ast.Function f = screens.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        return f.body();
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }

    private static void texts(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str v && last(a.target()).equalsIgnoreCase(field) -> out.add(v.value());
                case Ast.If i -> { texts(i.then(), field, out); texts(i.otherwise(), field, out); }
                case Ast.While w -> texts(w.body(), field, out);
                case Ast.Try t -> texts(t.body(), field, out);
                default -> {}
            }
        }
    }

    private static void compares(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.If i -> { compares(i.cond(), field, out); compares(i.then(), field, out); compares(i.otherwise(), field, out); }
                case Ast.While w -> compares(w.body(), field, out);
                case Ast.Try t -> compares(t.body(), field, out);
                default -> {}
            }
        }
    }

    private static void compares(Ast.Expr e, String field, List<String> out) {
        if (!(e instanceof Ast.Binary b)) return;
        if (b.left() instanceof Ast.Name n && last(n).equalsIgnoreCase(field) && b.right() instanceof Ast.Str v) out.add(v.value());
        compares(b.left(), field, out);
        compares(b.right(), field, out);
    }
}
