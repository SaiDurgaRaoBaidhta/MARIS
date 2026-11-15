package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.List;
import java.util.Locale;

/** The fixed texts a statement group passes to a program, e.g. call "printDnC"(VTAM-NAME, "CT", ...) -> CT. */
final class VehicleQueriesProgramArguments {
    private VehicleQueriesProgramArguments() {}

    /** the first text literal among the arguments of the function's call of {@code program} */
    static String text(ScreenBundle bundle, String function, String pkg, String program) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String found = find(f.body(), program.toLowerCase(Locale.ROOT));
        if (found == null) throw new IllegalStateException(function + " passes no text to " + program);
        return found;
    }

    private static String find(List<Ast.Stmt> stmts, String program) {
        for (Ast.Stmt s : stmts) {
            String r = switch (s) {
                case Ast.CallProgram c when c.program().toLowerCase(Locale.ROOT).endsWith(program) ->
                        c.args().stream().filter(a -> a instanceof Ast.Str).map(a -> ((Ast.Str) a).value()).findFirst().orElse(null);
                case Ast.If i -> { String x = find(i.then(), program); yield x != null ? x : find(i.otherwise(), program); }
                case Ast.Try t -> find(t.body(), program);
                case Ast.While w -> find(w.body(), program);
                case Ast.For fo -> find(fo.body(), program);
                default -> null;
            };
            if (r != null) return r;
        }
        return null;
    }
}
