package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the track windows' texts from the screen bundle (EGL) when a service starts, so nothing is copied into Java.
 * Module-local helper for the track feature (old VHYT08I, VHYT02I, VHYT06I, VHYT14I).
 */
public final class YardTrackingTrackEgl {
    private YardTrackingTrackEgl() {}

    /** the text literals a statement group assigns to a field ({@code X.field = "..."}), in code order, at any depth */
    public static List<String> assigned(ScreenBundle screens, String function, String pkg, String field) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new ArrayList<>();
        walk(f.body(), field, out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no text to " + field);
        return out;
    }

    /** the first text a statement group assigns to a field */
    public static String first(ScreenBundle screens, String function, String pkg, String field) {
        return assigned(screens, function, pkg, field).get(0);
    }

    private static void walk(List<Ast.Stmt> body, String field, List<String> out) {
        for (Ast.Stmt s : body) {
            if (s instanceof Ast.Assign a && a.value() instanceof Ast.Str v && last(a.target()).equalsIgnoreCase(field)) out.add(v.value());
            if (s instanceof Ast.If i) {
                walk(i.then(), field, out);
                walk(i.otherwise(), field, out);
            }
            if (s instanceof Ast.While w) walk(w.body(), field, out);
        }
    }

    /** a widget's caption on an old screen (a button's text), e.g. VHYT08I Push_Button1 = "Change Reason" */
    public static String caption(ScreenBundle screens, String screen, String widget) {
        JsonNode p = screens.screen(screen).path("widgets").path(widget).path("props");
        for (String k : List.of("object", "text", "label")) if (p.hasNonNull(k) && !p.get(k).asText().isBlank()) return p.get(k).asText().strip();
        throw new IllegalStateException(screen + " has no caption for " + widget);
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }
}
