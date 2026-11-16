package com.bart.maris.middleware.domain.incidentevents;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.List;

/** Texts of the incident event screens that {@code EglTexts} does not reach: the text an {@code "x" in ARRAY} test looks for. */
final class IncidentEventsEgl {
    private IncidentEventsEgl() {}

    /** e.g. VHIE03I-CHK_ACCESS: if ("pbtEventLogCreateIncident" in VH01A02_DATA.PART-NAME) -> pbtEventLogCreateIncident */
    static String inText(ScreenBundle bundle, String function, String pkg, String arrayField) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String s = find(f.body(), arrayField);
        if (s == null) throw new IllegalStateException(function + " looks for no text in " + arrayField);
        return s;
    }

    private static String find(List<Ast.Stmt> stmts, String field) {
        for (Ast.Stmt s : stmts) {
            String r = switch (s) {
                case Ast.If i -> {
                    String x = expr(i.cond(), field);
                    if (x == null) x = find(i.then(), field);
                    if (x == null) x = find(i.otherwise(), field);
                    yield x;
                }
                case Ast.Try t -> find(t.body(), field);
                case Ast.While w -> find(w.body(), field);
                default -> null;
            };
            if (r != null) return r;
        }
        return null;
    }

    private static String expr(Ast.Expr e, String field) {
        return switch (e) {
            case Ast.InTest in when in.value() instanceof Ast.Str str
                    && in.array().segments().get(in.array().segments().size() - 1).name().equalsIgnoreCase(field) -> str.value();
            case Ast.Binary b -> { String x = expr(b.left(), field); yield x != null ? x : expr(b.right(), field); }
            case Ast.Unary u -> expr(u.operand(), field);
            default -> null;
        };
    }
}
