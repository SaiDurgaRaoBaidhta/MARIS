package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.List;

/** Field lists read from an old statement group's conditions (the module's own helper, next to EglTexts). */
final class VehicleMaintEgl {
    private VehicleMaintEgl() {}

    /** the fields of a record the first if of a statement group tests, in order, e.g. VHVM02IS-PRE-ADD's required fields */
    static List<String> testedFields(ScreenBundle bundle, String function, String pkg, String record) {
        Ast.If first = firstIf(fn(bundle, function, pkg).body());
        if (first == null) throw new IllegalStateException(function + " has no if");
        List<String> out = new ArrayList<>();
        names(first.cond(), record, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " tests no field of " + record);
        return out;
    }

    /** the fields compared as {@code A.F != B.F} anywhere in a statement group, e.g. VHVM03I-PRE-UP's change check */
    static List<String> comparedFields(ScreenBundle bundle, String function, String pkg, String a, String b) {
        List<String> out = new ArrayList<>();
        walk(fn(bundle, function, pkg).body(), a, b, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares no " + a + " field with " + b);
        return out;
    }

    private static Ast.Function fn(ScreenBundle bundle, String function, String pkg) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        return f;
    }

    private static Ast.If firstIf(List<Ast.Stmt> stmts) {
        for (Ast.Stmt s : stmts) if (s instanceof Ast.If i) return i;
        return null;
    }

    private static void names(Ast.Expr e, String record, List<String> out) {
        switch (e) {
            case Ast.Name n when n.segments().size() == 2 && n.segments().get(0).name().equalsIgnoreCase(record) -> {
                String f = n.segments().get(1).name();
                if (!out.contains(f)) out.add(f);
            }
            case Ast.Binary b -> { names(b.left(), record, out); names(b.right(), record, out); }
            case Ast.Unary u -> names(u.operand(), record, out);
            case Ast.IsTest i -> names(i.operand(), record, out);
            default -> { }
        }
    }

    private static void walk(List<Ast.Stmt> stmts, String a, String b, List<String> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.If i -> { cmp(i.cond(), a, b, out); walk(i.then(), a, b, out); walk(i.otherwise(), a, b, out); }
                case Ast.Try t -> walk(t.body(), a, b, out);
                default -> { }
            }
        }
    }

    private static void cmp(Ast.Expr e, String a, String b, List<String> out) {
        if (!(e instanceof Ast.Binary bin)) return;
        if (bin.op().equals("!=") && bin.left() instanceof Ast.Name l && bin.right() instanceof Ast.Name r && l.segments().size() == 2
                && r.segments().size() == 2 && l.segments().get(0).name().equalsIgnoreCase(a) && r.segments().get(0).name().equalsIgnoreCase(b)
                && l.segments().get(1).name().equalsIgnoreCase(r.segments().get(1).name())) {
            out.add(l.segments().get(1).name());
            return;
        }
        cmp(bin.left(), a, b, out);
        cmp(bin.right(), a, b, out);
    }
}
