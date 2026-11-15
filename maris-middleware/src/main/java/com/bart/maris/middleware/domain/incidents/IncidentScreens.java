package com.bart.maris.middleware.domain.incidents;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the incident screens share, read from their EGL: field lengths of their working records (messages are built
 * by moving texts into fixed-length parts), the access check part names, and the way they join error texts.
 */
public final class IncidentScreens {
    private final ScreenBundle bundle;

    public IncidentScreens(ScreenBundle bundle) {
        this.bundle = bundle;
    }

    /** a field of a working record, found at any depth */
    public FieldLayout field(String record, String pkg, String name) {
        RecordLayout r = bundle.recordType(record, List.of(pkg, "MRSCommonCode"));
        if (r == null) throw new IllegalStateException("record " + record + " not found");
        FieldLayout f = find(r.fields(), name);
        if (f == null) throw new IllegalStateException(record + " has no field " + name);
        return f;
    }

    private static FieldLayout find(List<FieldLayout> fields, String name) {
        for (FieldLayout f : fields) {
            if (f.name().equalsIgnoreCase(name)) return f;
            FieldLayout c = find(f.children(), name);
            if (c != null) return c;
        }
        return null;
    }

    /**
     * a group field filled part by part, as the screens do (SUCCESS-MESSAGE-1 = "Incd No "; SUCCESS-MESSAGE-2 = no ...):
     * every part padded or cut to its own length, the whole cut to the group's length
     */
    public String parts(String record, String pkg, String group, String... values) {
        FieldLayout g = field(record, pkg, group);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < g.children().size(); i++) b.append(fit(i < values.length ? values[i] : "", g.children().get(i).length()));
        return fit(b.toString(), g.length()).stripTrailing();
    }

    /**
     * *-MOVE-MSG: the error texts joined, each read character by character through a window of {@code window}
     * characters up to its first "."; the "." becomes ", ", and the last character written is blanked. A text with
     * no "." inside the window is copied whole (blanks too) and runs into the next one, as on the old screens.
     */
    public static String join(List<String> texts, int window, int length) {
        if (texts.isEmpty()) return "";
        char[] out = new char[length + 2];
        java.util.Arrays.fill(out, ' ');
        int n = 0;
        for (String t : texts) {
            if (t.isBlank()) break;
            String w = fit(t, window);
            for (int i = 0; i < window; i++) {
                char c = w.charAt(i);
                if (c != '.') { if (n < out.length) out[n] = c; n++; }
                else { if (n < out.length) out[n] = ','; n++; if (n < out.length) out[n] = ' '; n++; break; }
            }
        }
        if (n >= 2 && n - 2 < out.length) out[n - 2] = ' ';           // FINAL-INDEX - 1, 1-based: the last "," goes
        return new String(out, 0, Math.min(length, out.length)).strip();     // the screens show it in a text field, which trims
    }

    /** the text an "in" test looks for in an array, e.g. "pbtTrainIncident" in VH01A02_DATA.PART-NAME */
    public String inText(String function, String pkg) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String s = inText(f.body());
        if (s == null) throw new IllegalStateException(function + " has no in test");
        return s;
    }

    private static String inText(List<Ast.Stmt> stmts) {
        for (Ast.Stmt s : stmts) {
            if (s instanceof Ast.If i) {
                if (i.cond() instanceof Ast.InTest t && t.value() instanceof Ast.Str str) return str.value();
                String x = inText(i.then());
                if (x == null) x = inText(i.otherwise());
                if (x != null) return x;
            }
        }
        return null;
    }

    /** the number a statement group compares a field with, e.g. SYS-MINUTES < 180 */
    public int comparedNumber(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<Integer> out = new ArrayList<>();
        numbers(f.body(), field.toUpperCase(Locale.ROOT), out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares " + field + " with no number");
        return out.get(0);
    }

    private static void numbers(List<Ast.Stmt> stmts, String field, List<Integer> out) {
        for (Ast.Stmt s : stmts) {
            if (s instanceof Ast.If i) {
                numbers(i.cond(), field, out);
                numbers(i.then(), field, out);
                numbers(i.otherwise(), field, out);
            }
        }
    }

    private static void numbers(Ast.Expr e, String field, List<Integer> out) {
        if (e instanceof Ast.Binary b) {
            if (b.left() instanceof Ast.Name n && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase(field) && b.right() instanceof Ast.Num num) {
                out.add(num.value().intValue());
            }
            numbers(b.left(), field, out);
            numbers(b.right(), field, out);
        }
    }

    /** a field of a program's answer as text; a group field (e.g. INCD-NO = prefix, number, suffix) put back together by its layout */
    public String text(java.util.Map<String, Object> rec, String record, String pkg, String name) {
        FieldLayout f = field(record, pkg, name);
        Object v = rec == null ? null : rec.get(f.json());
        if (!(v instanceof java.util.Map<?, ?>)) return v == null ? "" : v.toString().strip();
        return flatten(f, v).strip();
    }

    private static String flatten(FieldLayout f, Object v) {
        if (v instanceof java.util.Map<?, ?> parts && f.isGroup()) {
            StringBuilder b = new StringBuilder();
            for (FieldLayout c : f.children()) b.append(flatten(c, parts.get(c.json())));
            return b.toString();
        }
        if (v instanceof Number n) return String.format("%0" + f.length() + "d", n.longValue());
        return fit(v == null ? "" : v.toString(), f.length());
    }

    /** the first character of a car type answered as CAR-TYPE with CAR-TYPE-OCCR[4] under it (VHINC6A_DATA) */
    public static String firstCarType(java.util.Map<String, Object> rec) {
        Object v = rec == null ? null : rec.get("carType");
        if (v instanceof java.util.Map<?, ?> g && g.get("carTypeOccr") instanceof List<?> l && !l.isEmpty()) return fit(String.valueOf(l.get(0)), 1);
        return fit(v == null ? "" : v.toString(), 1).substring(0, 1);
    }

    public static String fit(String s, int n) {
        String v = s == null ? "" : s;
        return v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length());
    }
}
