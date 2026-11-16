package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the old screens' texts from the screen bundle (EGL) when a service starts, so nothing is copied into Java.
 * Module-local (docs/API_WORK_SPLIT.md: helpers start in the module).
 */
public final class ConfigQueriesEgl {
    private ConfigQueriesEgl() {}

    /** every text a statement group assigns to a field, in code order (then before else); fails at start-up if none */
    public static List<String> assignedTexts(ScreenBundle screens, String function, String pkg, String field) {
        Ast.Function f = screens.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new ArrayList<>();
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

    /** the fixed items of an old screen's list widget (its layout's "items"), blanks left out */
    public static List<String> listItems(ScreenBundle screens, String screen, String widget) {
        List<String> out = new ArrayList<>();
        for (var item : screens.screen(screen).path("widgets").path(widget).path("props").path("items")) {
            if (!item.asText().isBlank()) out.add(item.asText().strip());
        }
        if (out.isEmpty()) throw new IllegalStateException(screen + "." + widget + " has no items");
        return out;
    }
}
