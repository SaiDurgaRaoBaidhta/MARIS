package com.bart.maris.middleware.domain.common;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The old screens' own fixed texts and choices, read from the screen bundle (the EGL source) so the clean services
 * never carry a typed copy. Everything is looked up when a service starts: a text that is no longer in the EGL stops
 * the middleware from starting instead of showing an outdated copy.
 */
public final class EglTexts {
    private final ScreenBundle bundle;

    public EglTexts(ScreenBundle bundle) {
        this.bundle = bundle;
    }

    /** the message of a screen's message box (MessagePrompter messageString) */
    public String prompt(String screen, String prompter) {
        return bundle.promptText(screen, prompter);
    }

    /**
     * the text a statement group assigns to a field, e.g. FINAL-MESSAGE = "Please enter a valid home yard.".
     * When the function assigns several texts to that field, {@code containing} picks the one meant (case-insensitive).
     */
    public String assigned(String function, String pkg, String field, String containing) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> all = new ArrayList<>();
        collect(f.body(), field.toUpperCase(Locale.ROOT), all);
        for (String s : all) {
            if (containing == null || s.toLowerCase(Locale.ROOT).contains(containing.toLowerCase(Locale.ROOT))) return s.strip();
        }
        throw new IllegalStateException(function + " assigns no text" + (containing == null ? "" : " containing '" + containing + "'") + " to " + field);
    }

    private static void collect(List<Ast.Stmt> stmts, String field, List<String> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str str
                        && a.target().segments().get(a.target().segments().size() - 1).name().equalsIgnoreCase(field) -> out.add(str.value());
                case Ast.If i -> { collect(i.then(), field, out); collect(i.otherwise(), field, out); }
                case Ast.While w -> collect(w.body(), field, out);
                case Ast.Try t -> { collect(t.body(), field, out); collect(t.onException(), field, out); }
                case Ast.For fo -> collect(fo.body(), field, out);
                default -> { }
            }
        }
    }

    /**
     * the message of the message box a trigger field opens, e.g. VHCWR1I's INVALID-YARD opens msgInvalidYard
     * (the connection "TRIGGERS.FIELD data" -> prompter "prompt")
     */
    public String triggeredPrompt(String screen, String triggerField) {
        JsonNode def = bundle.screen(screen);
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (c.path("m").asText().startsWith("connectEventToAction") && a.path(1).asText().equalsIgnoreCase(triggerField + " data")
                    && a.path(3).asText().equals("prompt")) {
                return prompt(screen, a.path(2).path("id").asText());
            }
        }
        throw new IllegalStateException(screen + ": " + triggerField + " opens no message box");
    }

    /** the values a statement group compares a field with, in order, e.g. YARD != "CY" && YARD != "DY" ... -> CY, DY */
    public List<String> compared(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new ArrayList<>();
        compare(f.body(), field.toUpperCase(Locale.ROOT), out);
        if (out.isEmpty()) throw new IllegalStateException(function + " compares " + field + " with no value");
        return out;
    }

    private static void compare(List<Ast.Stmt> stmts, String field, List<String> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.If i -> { compareExpr(i.cond(), field, out); compare(i.then(), field, out); compare(i.otherwise(), field, out); }
                case Ast.While w -> { compareExpr(w.cond(), field, out); compare(w.body(), field, out); }
                case Ast.Try t -> compare(t.body(), field, out);
                default -> { }
            }
        }
    }

    private static void compareExpr(Ast.Expr e, String field, List<String> out) {
        if (e instanceof Ast.Binary b) {
            if (List.of("==", "!=", ">=", "<=", ">", "<").contains(b.op()) && b.left() instanceof Ast.Name n
                    && n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase(field)) {
                String v = b.right() instanceof Ast.Str str ? str.value().strip() : b.right() instanceof Ast.Num num ? num.value().toPlainString() : null;
                if (v != null && !v.isEmpty() && !out.contains(v)) out.add(v);
            }
            compareExpr(b.left(), field, out);
            compareExpr(b.right(), field, out);
        } else if (e instanceof Ast.Unary u) {
            compareExpr(u.operand(), field, out);
        }
    }

    /**
     * for every if whose condition reads {@code conditionField}, in source order: the first text its "then" assigns to
     * the field, or "" when it assigns none. E.g. the MESG1 texts under {@code if (AFCXVLDW.SQL-ERROR == "Y")}, one
     * per date checked. Empty when there is no such if.
     */
    public List<String> assignedUnder(String function, String pkg, String field, String conditionField) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> out = new ArrayList<>();
        under(f.body(), field.toUpperCase(Locale.ROOT), conditionField.toUpperCase(Locale.ROOT), out);
        return out.stream().map(String::strip).toList();
    }

    private static void under(List<Ast.Stmt> stmts, String field, String cond, List<String> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.If i -> {
                    if (reads(i.cond(), cond)) {
                        List<String> texts = new ArrayList<>();
                        collect(i.then(), field, texts);
                        out.add(texts.isEmpty() ? "" : texts.get(0));
                    } else under(i.then(), field, cond, out);
                    under(i.otherwise(), field, cond, out);
                }
                case Ast.While w -> under(w.body(), field, cond, out);
                case Ast.Try t -> { under(t.body(), field, cond, out); under(t.onException(), field, cond, out); }
                case Ast.For fo -> under(fo.body(), field, cond, out);
                default -> { }
            }
        }
    }

    private static boolean reads(Ast.Expr e, String field) {
        return switch (e) {
            case Ast.Name n -> n.segments().get(n.segments().size() - 1).name().equalsIgnoreCase(field);
            case Ast.Binary b -> reads(b.left(), field) || reads(b.right(), field);
            case Ast.Unary u -> reads(u.operand(), field);
            default -> false;
        };
    }

    /** the first number a statement group assigns to a field, e.g. VHNG01IW.MINUTES = 10 */
    public java.math.BigDecimal assignedNumber(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        java.math.BigDecimal n = number(f.body(), field.toUpperCase(Locale.ROOT));
        if (n == null) throw new IllegalStateException(function + " assigns no number to " + field);
        return n;
    }

    private static java.math.BigDecimal number(List<Ast.Stmt> stmts, String field) {
        for (Ast.Stmt s : stmts) {
            java.math.BigDecimal n = switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Num num
                        && a.target().segments().get(a.target().segments().size() - 1).name().equalsIgnoreCase(field) -> num.value();
                case Ast.If i -> { java.math.BigDecimal x = number(i.then(), field); yield x != null ? x : number(i.otherwise(), field); }
                case Ast.Try t -> number(t.body(), field);
                default -> null;
            };
            if (n != null) return n;
        }
        return null;
    }

    /** one assignment {@code RECORD.TARGET = SOURCE-RECORD.SOURCE-FIELD} */
    public record Copy(String target, String sourceRecord, String sourceField) {}

    /** the fields a statement group copies into a record (anywhere in it), e.g. VHMC01D_CRIT.RPT = VHMC01IW.RPT */
    public List<Copy> copiedInto(String function, String pkg, String record) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<Copy> out = new ArrayList<>();
        copies(f.body(), record, out);
        return out;
    }

    private static void copies(List<Ast.Stmt> stmts, String record, List<Copy> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Name src && a.target().segments().size() == 2
                        && a.target().segments().get(0).name().equalsIgnoreCase(record) && src.segments().size() == 2 ->
                        out.add(new Copy(a.target().segments().get(1).name(), src.segments().get(0).name(), src.segments().get(1).name()));
                case Ast.If i -> { copies(i.then(), record, out); copies(i.otherwise(), record, out); }
                case Ast.Try t -> copies(t.body(), record, out);
                case Ast.While w -> copies(w.body(), record, out);
                default -> { }
            }
        }
    }

    /**
     * a working field's value before any statement sets it, as text moved into a character field: a number field
     * gives its zeros ("0" for NUM(1)), a character field nothing
     */
    public String startText(String record, String pkg, String field) {
        com.bart.maris.middleware.legacy.layout.RecordLayout r = bundle.recordType(record, List.of(pkg));
        if (r == null) throw new IllegalStateException("record " + record + " not found");
        for (com.bart.maris.middleware.legacy.layout.FieldLayout f : r.fields()) {
            if (!f.name().equalsIgnoreCase(field)) continue;
            return switch (f.type()) {
                case NUM, NUMC, DECIMAL, PACF, SMALLINT, INT, BIGINT, BIN -> "0".repeat(Math.max(1, f.length() - f.decimals()));
                default -> "";
            };
        }
        throw new IllegalStateException(record + " has no field " + field);
    }

    /** the text of the button whose click runs a statement group, e.g. VHMC01I's "Make Train - Print" for VHMC01IS-SUBMIT-M */
    public String buttonFor(String screen, String function) {
        JsonNode def = bundle.screen(screen);
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(1).asText().equals("clicked") && a.path(2).path("id").asText().equalsIgnoreCase(function + "__reference")
                    && a.path(0).path("id").asText().startsWith("screen.")) {
                return label(screen, a.path(0).path("id").asText().substring(7));
            }
        }
        throw new IllegalStateException(screen + ": no button runs " + function);
    }

    /** the title of a screen's window, e.g. "VHMR01I - Reason Used by Shop Report Dec. 2002 V. 1.0" */
    public String windowTitle(String screen) {
        for (JsonNode w : bundle.screen(screen).path("widgets")) {
            if (w.path("type").asText().equals("ShellView")) {
                String t = w.path("props").path("title").asText("").strip();
                if (!t.isEmpty()) return t;
            }
        }
        throw new IllegalStateException(screen + " has no window title");
    }

    /** one table column of an old screen: its heading and the record field it shows */
    public record Column(String widget, String heading, String field) {}

    /** a screen's table columns in their order, e.g. VHHF01I: ("Car Type", CAR-TYPE-DATA), ("A", CAR-CYA-DATA), ... */
    public List<Column> columns(String screen) {
        List<Column> out = new ArrayList<>();
        var it = bundle.screen(screen).path("widgets").fields();
        while (it.hasNext()) {
            var e = it.next();
            JsonNode w = e.getValue();
            if (!w.path("type").asText().equals("ContainerDetailsColumn")) continue;
            out.add(new Column(e.getKey(), w.path("props").path("heading").asText("").strip(), w.path("props").path("attributeName").asText("")));
        }
        return out;
    }

    /** a table's columns in the order the table lists them (a column it lists twice comes twice, as the old screen shows it) */
    public List<Column> tableColumns(String screen, String table) {
        JsonNode widgets = bundle.screen(screen).path("widgets");
        List<Column> out = new ArrayList<>();
        for (JsonNode child : widgets.path(table).path("props").path("children")) {
            String id = child.path("id").asText();
            JsonNode w = widgets.path(id);
            if (!w.path("type").asText().equals("ContainerDetailsColumn")) continue;
            out.add(new Column(id, w.path("props").path("heading").asText("").strip(), w.path("props").path("attributeName").asText("")));
        }
        if (out.isEmpty()) throw new IllegalStateException(screen + "." + table + " has no columns");
        return out;
    }

    /** the texts a statement group assigns to the occurrences of an array field, by index, e.g. CAR-TYPE-DATA[1] = "Cars Assigned" */
    public java.util.SortedMap<Integer, String> indexed(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        java.util.SortedMap<Integer, String> out = new java.util.TreeMap<>();
        indexed(f.body(), field.toUpperCase(Locale.ROOT), out);
        if (out.isEmpty()) throw new IllegalStateException(function + " assigns no " + field + "[n]");
        return out;
    }

    private static void indexed(List<Ast.Stmt> stmts, String field, java.util.SortedMap<Integer, String> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str str -> {
                    Ast.Segment last = a.target().segments().get(a.target().segments().size() - 1);
                    if (last.name().equalsIgnoreCase(field) && last.subscripts().size() == 1 && last.subscripts().get(0) instanceof Ast.Num n) {
                        out.putIfAbsent(n.value().intValue(), str.value().strip());
                    }
                }
                case Ast.If i -> { indexed(i.then(), field, out); indexed(i.otherwise(), field, out); }
                case Ast.Try t -> indexed(t.body(), field, out);
                default -> { }
            }
        }
    }

    /**
     * every non-blank text a statement group assigns to a field, in source order (all branches). For screens whose
     * messages differ only slightly per branch, e.g. VHYS01IS-CHK-SEL: "You Have Entered an Invalid Yard" and "...Yard."
     */
    public List<String> assignedAll(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> all = new ArrayList<>();
        collect(f.body(), field.toUpperCase(Locale.ROOT), all);
        return all.stream().map(String::strip).filter(x -> !x.isEmpty()).toList();
    }

    /** like {@link #assignedAll} but every text exactly as written (spaces kept), e.g. SQL pieces " WHERE T1.CAR_NO = '" */
    public List<String> assignedRaw(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<String> all = new ArrayList<>();
        collect(f.body(), field.toUpperCase(Locale.ROOT), all);
        return all.stream().filter(x -> !x.isBlank()).toList();
    }

    /** the first text argument of a call in a statement group, e.g. "<O({vblm" in strLib.indexOf("<O({vblm", ...) */
    public String callText(String function, String pkg, String call) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String found = callText(f.body(), call.toLowerCase(Locale.ROOT));
        if (found == null) throw new IllegalStateException(function + " makes no " + call + " call with a text");
        return found;
    }

    private static String callText(List<Ast.Stmt> stmts, String call) {
        for (Ast.Stmt s : stmts) {
            String r = switch (s) {
                case Ast.Assign a -> callText(a.value(), call);
                case Ast.If i -> { String x = callText(i.cond(), call); if (x == null) x = callText(i.then(), call); yield x != null ? x : callText(i.otherwise(), call); }
                case Ast.While w -> { String x = callText(w.cond(), call); yield x != null ? x : callText(w.body(), call); }
                case Ast.For fo -> callText(fo.body(), call);
                case Ast.Try t -> callText(t.body(), call);
                default -> null;
            };
            if (r != null) return r;
        }
        return null;
    }

    private static String callText(Ast.Expr e, String call) {
        return switch (e) {
            case Ast.Call c -> {
                if (c.name().toLowerCase(Locale.ROOT).endsWith(call) && !c.args().isEmpty() && c.args().get(0) instanceof Ast.Str str) yield str.value();
                String x = null;
                for (Ast.Expr a : c.args()) { x = callText(a, call); if (x != null) break; }
                yield x;
            }
            case Ast.Binary b -> { String x = callText(b.left(), call); yield x != null ? x : callText(b.right(), call); }
            case Ast.Unary u -> callText(u.operand(), call);
            default -> null;
        };
    }

    /** one "if (F[r] == "c" || ...)  TARGET = SOURCE[n];" of a statement group: the tests (row, character), the target, the source row */
    public record OrRule(String target, int sourceRow, List<Map.Entry<Integer, String>> tests) {}

    /**
     * the if-statements of a statement group that copy one array element when any of their "field[row] == text"
     * tests holds, e.g. VHSV06IS-MOVE-CON: if (CAR-ORIENTATION[1] == "(" || ...) CAR-1-LFT-OR = CAR-ORIENTATION[1]
     */
    public List<OrRule> orRules(String function, String pkg, String field) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<OrRule> out = new ArrayList<>();
        orRules(f.body(), field.toUpperCase(Locale.ROOT), out);
        return out;
    }

    private static void orRules(List<Ast.Stmt> stmts, String field, List<OrRule> out) {
        for (Ast.Stmt s : stmts) {
            if (s instanceof Ast.If i) {
                List<Map.Entry<Integer, String>> tests = new ArrayList<>();
                if (orTests(i.cond(), field, tests) && i.then().size() == 1 && i.then().get(0) instanceof Ast.Assign a && a.value() instanceof Ast.Name src) {
                    Ast.Segment last = src.segments().get(src.segments().size() - 1);
                    if (last.name().equalsIgnoreCase(field) && last.subscripts().size() == 1 && last.subscripts().get(0) instanceof Ast.Num n) {
                        String target = a.target().segments().get(a.target().segments().size() - 1).name().toUpperCase(Locale.ROOT);
                        out.add(new OrRule(target, n.value().intValue(), tests));
                        continue;
                    }
                }
                orRules(i.then(), field, out);
                orRules(i.otherwise(), field, out);
            }
        }
    }

    /** a condition made only of "field[row] == text" tests joined by || */
    private static boolean orTests(Ast.Expr e, String field, List<Map.Entry<Integer, String>> out) {
        if (e instanceof Ast.Binary b && b.op().equals("||")) return orTests(b.left(), field, out) && orTests(b.right(), field, out);
        if (e instanceof Ast.Binary b && b.op().equals("==") && b.left() instanceof Ast.Name n && b.right() instanceof Ast.Str str) {
            Ast.Segment last = n.segments().get(n.segments().size() - 1);
            if (last.name().equalsIgnoreCase(field) && last.subscripts().size() == 1 && last.subscripts().get(0) instanceof Ast.Num row) {
                out.add(Map.entry(row.value().intValue(), str.value()));
                return true;
            }
        }
        return false;
    }

    /** the value a screen puts into a widget when it opens (openedWidget -> widget, constant parameter), e.g. Narr "N" */
    public String openingValue(String screen, String widget) {
        for (JsonNode c : bundle.screen(screen).path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(1).asText().equals("openedWidget") && a.path(2).path("id").asText().equals("screen." + widget)) {
                for (JsonNode p : a.path(4)) {
                    JsonNode args = p.path("args");
                    if (p.path("call").asText().equals("cm.createConstantParameter") && args.path(0).asText().equals("value")) return args.path(1).asText();
                }
            }
        }
        throw new IllegalStateException(screen + " sets nothing into " + widget + " when it opens");
    }

    /** one cell assignment {@code FIELD[row] = expression} of a statement group, e.g. CAR-L-DATA[1] = TOTAL-YARD-M */
    public record Cell(String field, int row, Ast.Expr value) {}

    /** a statement group's cell assignments to array fields starting with {@code prefix}, in source order (texts left out) */
    public List<Cell> cells(String function, String pkg, String prefix) {
        Ast.Function f = bundle.function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        List<Cell> out = new ArrayList<>();
        cells(f.body(), prefix.toUpperCase(Locale.ROOT), out);
        if (out.isEmpty()) throw new IllegalStateException(function + " sets no " + prefix + "...[n]");
        return out;
    }

    private static void cells(List<Ast.Stmt> stmts, String prefix, List<Cell> out) {
        for (Ast.Stmt s : stmts) {
            switch (s) {
                case Ast.Assign a when !(a.value() instanceof Ast.Str) -> {
                    Ast.Segment last = a.target().segments().get(a.target().segments().size() - 1);
                    if (last.name().toUpperCase(Locale.ROOT).startsWith(prefix) && last.subscripts().size() == 1 && last.subscripts().get(0) instanceof Ast.Num n) {
                        out.add(new Cell(last.name().toUpperCase(Locale.ROOT), n.value().intValue(), a.value()));
                    }
                }
                case Ast.If i -> { cells(i.then(), prefix, out); cells(i.otherwise(), prefix, out); }
                case Ast.Try t -> cells(t.body(), prefix, out);
                default -> { }
            }
        }
    }

    /** a label's text, e.g. VHCWR1I lblFooter "(First 500 rows displayed)" */
    public String label(String screen, String widget) {
        String t = bundle.screen(screen).path("widgets").path(widget).path("props").path("object").asText(null);
        if (t == null) throw new IllegalStateException(screen + "." + widget + " has no text");
        return t.strip();
    }

    /** the fixed items of a screen's list widget (drop-down / radio set), e.g. the home yards CY, DY, HY, RY, ALL */
    public List<String> items(String screen, String widget) {
        JsonNode w = bundle.screen(screen).path("widgets").path(widget);
        List<String> out = new ArrayList<>();
        w.path("props").path("items").forEach(i -> { if (i.isTextual() && !i.asText().isBlank()) out.add(i.asText().strip()); });
        if (out.isEmpty()) throw new IllegalStateException(screen + "." + widget + " has no fixed items");
        return out;
    }
}
