package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.Interp;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Runs an old configuration tracking screen's own rules on its records, without drawing it (a copy of the consist
 * module's rule runner, ConsistRuleRunner, kept in this module per docs/API_WORK_SPLIT.md "helpers start in the module").
 * The screens' statement groups are wired together by their connections: a button runs a group, a group sets a trigger
 * field, the trigger field runs the next group, a question box answers into a field. This class reads those same
 * connections from the screen bundle and follows them on the records (field events fire after the group that caused
 * them returns, buttons the rules disabled do nothing), so the rules stay the screen's own; the programs it calls go
 * through the legacy layer like any other clean API call.
 *
 * <p>Inputs are given by the old widget names (the screen's bindings say which record fields a widget feeds), and
 * results are read the same way. Message boxes are collected; a question is answered only when the caller passes the
 * answer, otherwise the chain stops there and the question is returned.
 */
public final class ConfigQueriesRuleRunner implements Interp.Host {
    /** the widget attributes that carry its value (a text box's object, a list's selected item) */
    private static final Set<String> VALUE = Set.of("object", "selectedItem", "selection", "selectionIndex");

    private record Bind(String widget, String var, String field, boolean toRecord, boolean toWidget) {}
    private record Link(String fromVar, String fromField, String toVar, String toField) {}
    private record Action(String target, String action, Object constant, String field) {}

    /** a message or question box the rules opened */
    public record Prompt(String name, String text, boolean question) {}

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String screen;
    private final String pkg;
    private final List<String> pkgs = new ArrayList<>();
    private final Map<String, Rec> byName = new LinkedHashMap<>();
    private final List<Rec> records = new ArrayList<>();
    private final Map<String, String> functionOf = new HashMap<>();            // ExecutablePart var -> function
    private final Map<String, JsonNode> prompters = new HashMap<>();
    private final List<String> shells = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final List<Bind> binds = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final Map<String, List<Action>> events = new LinkedHashMap<>();    // "SOURCE.EVENT" -> actions
    private final Map<String, Object> widgetValues = new HashMap<>();
    private final Deque<List<Object[]>> frames = new ArrayDeque<>();
    private final Map<String, Boolean> answers = new HashMap<>();
    private final Set<String> writers;
    private final Interp interp;
    private boolean replaying;

    /** every program call made: program -> the records sent */
    public final List<Map.Entry<String, Map<String, Object>>> calls = new ArrayList<>();
    /** the message / question boxes opened, in order */
    public final List<Prompt> prompts = new ArrayList<>();
    /** a question no answer was given for (the chain stopped there) */
    public Prompt unanswered;

    public ConfigQueriesRuleRunner(ScreenBundle bundle, String screen, LegacyProgramService legacy, Clock clock, Set<String> writers) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.clock = clock;
        this.writers = writers;
        this.screen = screen;
        JsonNode def = bundle.screen(screen);
        if (def == null) throw new IllegalStateException("screen " + screen + " not found");
        this.pkg = def.path("pkg").asText();
        pkgs.add(pkg);
        for (JsonNode i : def.path("imports")) {
            String q = i.asText();
            int dot = q.lastIndexOf('.');
            if (dot > 0 && !pkgs.contains(q.substring(0, dot))) pkgs.add(q.substring(0, dot));
        }
        if (!pkgs.contains("MRSCommonCode")) pkgs.add("MRSCommonCode");
        for (JsonNode v : def.path("vars")) {
            String n = v.path("name").asText(), type = v.path("type").asText();
            if (type.equals("ExecutablePart")) { functionOf.put(up(n), v.path("props").path("callFunction").path("id").asText()); continue; }
            if (type.equals("MessagePrompter")) { prompters.put(up(n), v.path("props")); continue; }
            RecordLayout l = bundle.recordType(type, pkgs);
            if (l != null) add(new Rec(n, l));
            else {
                Rec t = bundle.table(type);
                if (t != null) { Rec r = new Rec(n, t.layout); r.data = t.data.clone(); add(r); }
            }
        }
        def.path("widgets").fields().forEachRemaining(w -> {
            if (w.getValue().path("type").asText().equals("ShellView")) shells.add(w.getKey());
            JsonNode e = w.getValue().path("props").path("enabled");
            enabled.put(up(w.getKey()), !e.isBoolean() || e.asBoolean());
        });
        for (JsonNode c : def.path("connections")) wire(c);
        this.interp = new Interp(this);
    }

    private static String up(String s) { return s.toUpperCase(Locale.ROOT); }

    private Rec add(Rec r) {
        records.add(r);
        byName.put(up(r.var), r);
        return r;
    }

    private static String id(JsonNode n) { return n.path("id").asText(""); }

    /** "F data" -> F, anything else -> null */
    private static String dataField(String attr) {
        return attr.endsWith(" data") ? attr.substring(0, attr.length() - 5) : null;
    }

    private void wire(JsonNode c) {
        String m = c.path("m").asText();
        JsonNode a = c.path("a");
        switch (m) {
            case "connectAttributes", "connectReadOnlyAttributeToAttribute" -> {
                boolean both = m.equals("connectAttributes");
                String s = id(a.get(0)), sa = a.get(1).asText(), t = id(a.get(2)), ta = a.get(3).asText();
                String sf = dataField(sa), tf = dataField(ta);
                boolean sRec = byName.containsKey(up(s)) && sf != null, tRec = byName.containsKey(up(t)) && tf != null;
                if (s.startsWith("screen.") && VALUE.contains(sa) && tRec) binds.add(new Bind(s.substring(7), t, tf, true, both));
                else if (t.startsWith("screen.") && VALUE.contains(ta) && sRec) binds.add(new Bind(t.substring(7), s, sf, both, true));
                else if (sRec && tRec) {
                    links.add(new Link(s, sf, t, tf));
                    if (both) links.add(new Link(t, tf, s, sf));
                }
            }
            case "connectEventToAction" -> {
                String s = id(a.get(0)), ev = a.get(1).asText(), t = a.get(2).isObject() ? id(a.get(2)) : a.get(2).asText(), act = a.get(3).asText();
                events.computeIfAbsent(up(s + "." + ev), k -> new ArrayList<>()).add(new Action(t, act, null, null));
            }
            case "connectEventToActionWithParameters" -> {
                String s = id(a.get(0)), ev = a.get(1).asText(), t = id(a.get(2)), ta = a.get(3).asText();
                String f = dataField(ta);
                JsonNode args = a.get(4) != null && a.get(4).isArray() && a.get(4).size() > 0 ? a.get(4).get(0).path("args") : null;
                Object constant = args != null && args.size() > 1 ? (args.get(1).isNumber() ? args.get(1).numberValue() : args.get(1).asText()) : null;
                if (f != null && byName.containsKey(up(t))) events.computeIfAbsent(up(s + "." + ev), k -> new ArrayList<>()).add(new Action(t, "set", constant, f));
            }
            default -> {}
        }
    }

    // ---- what a caller does ----------------------------------------------------------------------------------------

    /** answers a question box the next time it opens: true = Yes / OK, false = No */
    public void answer(String prompter, boolean yes) {
        answers.put(up(prompter), yes);
    }

    /** while set, the writing programs are not called again (a step the user already took is replayed) */
    public void replaying(boolean on) {
        replaying = on;
    }

    /** what the old menus pass down on sign-on: the user group and terminal id, in every record field the screens use */
    public void signOn(String group, String terminal) {
        for (Rec r : List.copyOf(records)) {
            if (r.var.equalsIgnoreCase("XDCONTROL")) continue;
            for (String f : List.of("USER-GRP", "USER-GROUP", "USER_GRP", "USERGRP", "STORE-USER-GRP")) {
                if (r.find(f) != null && r.find(f).dims().isEmpty()) store(r.var, f, group);
            }
            for (String f : List.of("TERM-ID", "TERM_ID")) {
                if (r.find(f) != null && r.find(f).dims().isEmpty()) store(r.var, f, terminal);
            }
        }
    }

    /**
     * the vehicle menu (old VH001I) loaded its tables with VH01A01 (car types, yards, stations, storage tracks) into
     * VH01A01_DATA and handed them to the screens it opened; a screen that checks against them gets the same load
     */
    public void menuTables() {
        Rec r = byName.get("VH01A01_DATA");
        if (r == null) return;
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("VH01A01_DATA", RecordCodec.decode(r.layout, r.data, true));
        calls.add(Map.entry("VH01A01", new LinkedHashMap<>(in)));
        try {
            Map<String, Object> back = legacy.execute("VH01A01", in, false).record("VH01A01_DATA");
            if (back != null) r.data = RecordCodec.encode(r.layout, back);
        } catch (RuntimeException e) {
            // the menu showed the failure and carried on with what it had
        }
    }

    /** the screen opens: its aboutToOpenWidget / openedWidget actions (window first, then the screen) */
    public void open() {
        for (String ev : List.of("aboutToOpenWidget", "openedWidget")) {
            java.util.Set<String> sources = new LinkedHashSet<>();          // the window (shell), then the screen part
            for (String sh : shells) sources.add(up("screen." + sh));
            sources.add(up("screen." + screen));
            sources.add("SCREEN");
            for (String src : sources) act(src, ev);
        }
    }

    /** types a value into a widget: every record field the widget feeds gets it */
    public void type(String widget, Object value) {
        widgetValues.put(up(widget), value);
        for (Bind b : binds) if (b.toRecord() && b.widget().equalsIgnoreCase(widget)) store(b.var(), b.field(), value == null ? "" : value);
    }

    /**
     * a value typed into the box bound to a record field (the box's other bound fields get it too, as typing did); a
     * field no box feeds is stored as it is
     */
    public void typeField(String var, String field, Object value) {
        for (Bind b : binds) {
            if (b.toRecord() && b.var().equalsIgnoreCase(var) && b.field().equalsIgnoreCase(field)) { type(b.widget(), value); return; }
        }
        store(var, field, value == null ? "" : value);
    }

    /** a widget event, e.g. ("Update_btn", "clicked") or ("CarText", "losingFocus"); a disabled button does nothing */
    public void fire(String widget, String event) {
        if (event.equals("clicked") && !enabled(widget)) return;
        act("screen." + widget, event);
    }

    /** whether a widget is enabled now (the rules enable and disable buttons as the user goes) */
    public boolean enabled(String widget) {
        return enabled.getOrDefault(up(widget), true);
    }

    /** what a widget shows: the last value written to a record field bound to it, or typed into it */
    public String shown(String widget) {
        Object v = widgetValues.get(up(widget));
        if (v == null) for (Bind b : binds) if (b.toWidget() && b.widget().equalsIgnoreCase(widget)) return text(b.var(), b.field());
        return v == null ? "" : Conv.rtrim(v.toString());
    }

    // ---- the connections ---------------------------------------------------------------------------------------------

    private void act(String source, String event) {
        List<Action> list = events.get(up(source + "." + event));
        if (list == null) return;
        for (Action a : List.copyOf(list)) {
            if (unanswered != null) return;
            String t = a.target();
            if (a.action().equals("set")) { store(t, a.field(), a.constant()); continue; }
            String fn = functionOf.get(up(t));
            if (fn != null && a.action().equals("execute")) {
                run(fn);
                if (unanswered == null) act(t, "hasExecuted");                 // an ExecutablePart tells it ran: the next group
                continue;
            }
            if (t.startsWith("screen.") && a.action().equals("click")) { if (enabled(t.substring(7))) act(t, "clicked"); continue; }
            if (t.startsWith("screen.") && (a.action().equals("enable") || a.action().equals("disable"))) {
                enabled.put(up(t.substring(7)), a.action().equals("enable"));
                continue;
            }
            JsonNode p = prompters.get(up(t));
            if (p != null && a.action().equals("prompt")) { prompt(t, p); continue; }
            if (t.equalsIgnoreCase("XXM0I") && a.action().equals("execute")) prompts.add(new Prompt("XXM0I", text("XXMESSAGE", "UMSGCODE"), false));
        }
    }

    private void prompt(String name, JsonNode p) {
        int buttons = p.path("buttonType").asInt(0);
        boolean question = buttons == 3 || buttons == 4 || buttons == 5;
        Prompt pr = new Prompt(name, p.path("messageString").asText("").strip(), question);
        prompts.add(pr);
        if (!question) { act(name, "okYesRetry"); return; }
        Boolean yes = answers.remove(up(name));
        if (yes == null) { unanswered = pr; return; }
        act(name, yes ? "okYesRetry" : "noAbort");
    }

    /** opens a message box the rules name in an EGL statement (some screens open them in code, not by connection) */
    public void prompt(String name) {
        JsonNode p = prompters.get(up(name));
        if (p != null) prompt(name, p);
    }

    /** runs one statement group; field events it caused fire after it returns, as the screens' runtime does */
    public void run(String function) {
        if (unanswered != null) return;
        frames.push(new ArrayList<>());
        List<Object[]> mine;
        try {
            interp.run(function);
        } finally {
            mine = frames.pop();
        }
        signal(mine);
    }

    private void signal(List<Object[]> written) {
        Set<String> seen = new LinkedHashSet<>();
        for (Object[] w : written) {
            Rec r = (Rec) w[0];
            Rec.Path p = (Rec.Path) w[1];
            List<String> fields = new ArrayList<>();
            if (p == null) names(r.layout.fields(), fields);
            else {
                fields.add(p.field().name());
                names(p.field().children(), fields);
                for (FieldLayout g : p.chain()) if (g != p.field()) refresh(r.var, g.name());   // a part changed: the box showing the whole field
            }
            for (String f : fields) {
                String key = up(r.var + "." + f);
                if (!seen.add(key)) continue;
                refresh(r.var, f);
                for (Link l : links) if (l.fromVar().equalsIgnoreCase(r.var) && l.fromField().equalsIgnoreCase(f)) copy(l);
                act(r.var, f + " data");
                if (unanswered != null) return;
            }
        }
    }

    /** a text box bound to a record field shows the field's new value (a box bound to several fields shows the last written) */
    private void refresh(String var, String field) {
        for (Bind b : binds) if (b.toWidget() && b.var().equalsIgnoreCase(var) && b.field().equalsIgnoreCase(field)) widgetValues.put(up(b.widget()), text(var, field));
    }

    private static void names(List<FieldLayout> fields, List<String> out) {
        for (FieldLayout f : fields) {
            if (!f.filler()) out.add(f.name());
            names(f.children(), out);
        }
    }

    private void copy(Link l) {
        if (!text(l.fromVar(), l.fromField()).equals(text(l.toVar(), l.toField()))) store(l.toVar(), l.toField(), interp.read(ref(l.fromVar(), l.fromField())));
    }

    // ---- record access ---------------------------------------------------------------------------------------------

    private Rec rec(String var) {
        Rec r = byName.get(up(var));
        if (r == null) throw new IllegalArgumentException("no record " + var);
        return r;
    }

    private Interp.FieldRef ref(String var, String field, int... index) {
        Rec r = rec(var);
        Rec.Path p = r.find(field);
        if (p == null) throw new IllegalArgumentException("no field " + field + " in " + var);
        return new Interp.FieldRef(r, p, index, null, null);
    }

    /** a field's value as text (right-trimmed), by its EGL name; an index for an array element */
    public String text(String var, String field, int... index) {
        Object v = interp.read(ref(var, field, index));
        return v == null ? "" : Conv.rtrim(Conv.string(v));
    }

    /** a numeric field's value */
    public long number(String var, String field, int... index) {
        Object v = interp.read(ref(var, field, index));
        if (v instanceof Number n) return n.longValue();
        String s = v == null ? "" : v.toString().strip();
        return s.isEmpty() || !s.matches("-?\\d+") ? 0 : Long.parseLong(s);
    }

    /** stores a value with EGL assignment rules (its field events fire) */
    public void store(String var, String field, Object value, int... index) {
        interp.store(ref(var, field, index), value);
    }

    /** the rows of an array field (e.g. a list's DATA-RECORD), each by its old sub-field names; empty rows at the end left out */
    public List<Map<String, String>> rows(String var, String field) {
        Rec r = rec(var);
        Rec.Path p = r.find(field);
        if (p == null) throw new IllegalArgumentException("no field " + field + " in " + var);
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 1; i <= p.field().occurs(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            cells(r, p, p.field().children(), i, row);
            out.add(row);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).values().stream().allMatch(v -> v.isEmpty() || v.matches("0+"))) out.remove(out.size() - 1);
        return out;
    }

    /** a row's sub-fields and the fields inside its groups (e.g. STOCK-DISPLAY and its STOCK-NO-L), by old name */
    private void cells(Rec r, Rec.Path p, List<FieldLayout> fields, int i, Map<String, String> row) {
        for (FieldLayout c : fields) {
            if (c.filler()) continue;
            Object v = interp.read(new Interp.FieldRef(r, Rec.findBelow(p, c.name()), new int[]{i}, null, null));
            row.put(c.name(), v == null ? "" : Conv.rtrim(Conv.string(v)).strip());
            cells(r, p, c.children(), i, row);
        }
    }

    /** a record's single-value fields (no arrays, no fillers), by old name, in layout order */
    public List<String> fieldNames(String var) {
        List<String> out = new ArrayList<>();
        leaves(rec(var).layout.fields(), false, out);
        return out;
    }

    private static void leaves(List<FieldLayout> fields, boolean inArray, List<String> out) {
        for (FieldLayout f : fields) {
            if (f.filler()) continue;
            boolean array = inArray || f.occurs() > 1;
            if (f.children().isEmpty()) { if (!array) out.add(f.name()); }
            else leaves(f.children(), array, out);
        }
    }

    /** a record's values (json field names) */
    public Map<String, Object> get(String var) {
        Rec r = rec(var);
        return RecordCodec.decode(r.layout, r.data, false);
    }

    // ---- Interp.Host -----------------------------------------------------------------------------------------------

    @Override
    public Rec record(String n) {
        String up = n.startsWith("type:") ? null : up(n);
        if (up != null && byName.containsKey(up)) return byName.get(up);
        String name = n.startsWith("type:") ? n.substring(5) : n;
        Rec t = bundle.table(name);
        if (t != null) return n.startsWith("type:") ? t : add(t);
        RecordLayout l = bundle.recordType(name, pkgs);
        if (l == null) return null;
        Rec r = new Rec(name.substring(name.lastIndexOf('.') + 1), l);
        return n.startsWith("type:") ? r : add(r);
    }

    @Override
    public List<Rec> records() {
        return records;
    }

    @Override
    public Ast.Function function(String name) {
        return bundle.function(name, pkg);
    }

    @Override
    public void callProgram(String program, List<Rec> args, Interp in) {
        String p = program.contains(".") ? program.substring(program.lastIndexOf('.') + 1) : program;
        ServiceDefinition def = legacy.definition(p);
        Map<String, Object> inputs = new HashMap<>();
        List<Map.Entry<String, Rec>> recordArgs = new ArrayList<>();
        int next = 0;
        for (ServiceDefinition.Param param : def.params()) {
            if (param.kind() != ServiceDefinition.ParamKind.RECORD) continue;
            if (next < args.size()) {
                Rec r = args.get(next++);
                inputs.put(param.name(), RecordCodec.decode(r.layout, r.data, true));
                recordArgs.add(Map.entry(param.name(), r));
            }
        }
        if (replaying && writers.contains(up(p))) { in.errorCode = "00000000"; return; }
        calls.add(Map.entry(def.function(), new LinkedHashMap<>(inputs)));
        try {
            ProgramResult res = legacy.execute(def.function(), inputs, false);
            for (Map.Entry<String, Rec> e : recordArgs) {
                Map<String, Object> back = res.record(e.getKey());
                if (back != null) e.getValue().data = RecordCodec.encode(e.getValue().layout, back);   // no field events, as the runtime
            }
            in.errorCode = "00000000";
        } catch (RuntimeException e) {
            in.errorCode = "SVCFAIL";
        }
    }

    @Override
    public void written(Rec rec, Rec.Path path) {
        if (!byName.containsValue(rec)) return;
        if (frames.isEmpty()) signal(List.<Object[]>of(new Object[]{rec, path}));
        else frames.peek().add(new Object[]{rec, path});
    }

    @Override
    public Supplier<LocalDateTime> clock() {
        return () -> LocalDateTime.now(clock);
    }
}
