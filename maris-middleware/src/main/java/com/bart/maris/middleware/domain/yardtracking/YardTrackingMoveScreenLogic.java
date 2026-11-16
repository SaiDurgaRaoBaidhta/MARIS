package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.runtime.Interp;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs an old screen's own statement groups on its records, without a window: for screens whose rules are long chains
 * of checks and record moves written out in the EGL (old VHYT11I car move on a track; copied from the track feature so the two can change apart). The rules are
 * the screen's; the programs it calls go through the legacy layer like any other clean API call. Nothing is drawn and
 * no widget is involved. Like the old runtime (and the engine's ExecutablePart), a statement group's field events fire
 * after it returns: a field another group watches ("TRIGGER.OPEN-MOVECAR data" -&gt; VHYT08IS-MOVE-CAR) runs that group, and
 * "hasExecuted" chains (VHYT08IS-CK-USER -&gt; VHYT08IS-PSEUDO-L -&gt; ...) run in the screen's connection order. Windows the
 * screen would open from such a field are recorded in {@link #opened}. While "replaying" (rebuilding a list the user
 * already saw), programs that write are not called again: they answer with their own input and a zero return code.
 */
final class YardTrackingMoveScreenLogic implements Interp.Host {
    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String pkg;
    private final List<String> pkgs = new ArrayList<>();
    private final Map<String, Rec> byName = new LinkedHashMap<>();
    private final List<Rec> records = new ArrayList<>();
    private final Interp interp;
    /** the records each program call sent and got back, in call order: program -> parameter -> values */
    final List<Map.Entry<String, Map<String, Object>>> calls = new ArrayList<>();
    /** programs not called while replaying (they write) */
    private java.util.Set<String> skipped = java.util.Set.of();
    /** "REC.FIELD" -&gt; statement groups / windows to open when the field is written; "FN" -&gt; groups run after it */
    private final Map<String, List<String>> onField = new HashMap<>(), onWindow = new HashMap<>(), afterGroup = new HashMap<>();
    private final java.util.ArrayDeque<List<String>> writes = new java.util.ArrayDeque<>();
    /** the windows the screen opened, in order (e.g. VHYT13I after Orientation) */
    final List<String> opened = new ArrayList<>();
    /** every "Y" written to an ENABLE-/DISABLE- field, in write order ("REC.FIELD"): the last one per button is its state */
    final List<String> switches = new ArrayList<>();

    YardTrackingMoveScreenLogic(ScreenBundle bundle, String screen, LegacyProgramService legacy, Clock clock) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.clock = clock;
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
            RecordLayout l = bundle.recordType(type, pkgs);
            if (l != null) add(new Rec(n, l));
            else {
                Rec t = bundle.table(type);
                if (t != null) { Rec r = new Rec(n, t.layout); r.data = t.data; add(r); }
            }
        }
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (!"connectEventToAction".equals(c.path("m").asText()) || a.size() < 4) continue;
            String from = a.get(0).path("id").asText(), event = a.get(1).asText(), to = a.get(2).path("id").asText(), action = a.get(3).asText();
            String fn = to.endsWith("__reference") && action.equals("execute") ? to.substring(0, to.length() - 11).toUpperCase(Locale.ROOT) : null;
            if (event.equals("hasExecuted") && from.endsWith("__reference") && fn != null) {
                afterGroup.computeIfAbsent(from.substring(0, from.length() - 11).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(fn);
            } else if (event.endsWith(" data") && !from.startsWith("screen.")) {
                String key = (from + "." + event.substring(0, event.length() - 5)).toUpperCase(Locale.ROOT);
                if (fn != null) onField.computeIfAbsent(key, k -> new ArrayList<>()).add(fn);
                else if (action.equals("openWidget") && !to.startsWith("screen.")) onWindow.computeIfAbsent(key, k -> new ArrayList<>()).add(to);
            }
        }
        this.interp = new Interp(this);
    }

    /** the sign-on values the old menus hand every record (user group, terminal id), as the engine seeds an opened screen */
    void signOn(String group, String terminal) {
        for (Rec r : List.copyOf(records)) {
            if (r.var.equalsIgnoreCase("XDCONTROL")) continue;
            for (String f : List.of("USER-GRP", "USER-GROUP", "USER_GRP", "USERGRP", "STORE-USER-GRP")) {
                Rec.Path p = r.find(f);
                if (p != null && p.dims().isEmpty()) interp.store(new Interp.FieldRef(r, p, new int[0], null, null), group);
            }
            for (String f : List.of("TERM-ID", "TERM_ID")) {
                Rec.Path p = r.find(f);
                if (p != null && p.dims().isEmpty()) interp.store(new Interp.FieldRef(r, p, new int[0], null, null), terminal);
            }
        }
    }

    private Rec add(Rec r) {
        records.add(r);
        byName.put(r.var.toUpperCase(Locale.ROOT), r);
        return r;
    }

    /** runs one statement group, then the field events it caused and the groups chained after it (like an ExecutablePart) */
    void run(String function) {
        writes.push(new ArrayList<>());
        List<String> mine;
        try {
            interp.run(function);
        } finally {
            mine = writes.pop();
        }
        for (String key : mine) {
            for (String w : onWindow.getOrDefault(key, List.of())) opened.add(w.toUpperCase(Locale.ROOT));
            for (String fn : onField.getOrDefault(key, List.of())) run(fn);
        }
        for (String fn : afterGroup.getOrDefault(function.toUpperCase(Locale.ROOT), List.of())) run(fn);
    }

    /** while set, these programs are not called (a list the user already saw is rebuilt without writing twice) */
    void skip(java.util.Set<String> programs) {
        skipped = programs;
    }

    private Rec rec(String var) {
        Rec r = byName.get(var.toUpperCase(Locale.ROOT));
        if (r == null) throw new IllegalArgumentException("no record " + var);
        return r;
    }

    /** a field's value as text (right-trimmed), by its EGL name; index &gt; 0 for an array field's element */
    String text(String var, String field, int... index) {
        Interp.FieldRef f = ref(var, field, index);
        Object v = interp.read(f);
        return v == null ? "" : com.bart.maris.middleware.engine.runtime.Conv.rtrim(com.bart.maris.middleware.engine.runtime.Conv.string(v));
    }

    /** a numeric field's value */
    long number(String var, String field, int... index) {
        Object v = interp.read(ref(var, field, index));
        return v instanceof Number n ? n.longValue() : v == null || v.toString().isBlank() ? 0 : Long.parseLong(v.toString().strip());
    }

    /** stores a value with EGL assignment rules, by the field's EGL name */
    void store(String var, String field, Object value, int... index) {
        interp.store(ref(var, field, index), value);
    }

    private Interp.FieldRef ref(String var, String field, int... index) {
        Rec r = rec(var);
        Rec.Path p = r.find(field);
        if (p == null) throw new IllegalArgumentException("no field " + field + " in " + var);
        return new Interp.FieldRef(r, p, index, null, null);
    }

    /** a record's values (json field names) */
    Map<String, Object> get(String var) {
        Rec r = byName.get(var.toUpperCase(Locale.ROOT));
        if (r == null) throw new IllegalArgumentException("no record " + var);
        return RecordCodec.decode(r.layout, r.data, false);
    }

    /**
     * the rows of a record's table field (its first array field, e.g. DATA-ROW), each by the old field names (DESC, CY-A ..)
     * as the screens' table columns name them
     */
    List<Map<String, Object>> rows(String var) {
        Rec r = byName.get(var.toUpperCase(Locale.ROOT));
        if (r == null) throw new IllegalArgumentException("no record " + var);
        Map<String, Object> values = get(var);
        for (com.bart.maris.middleware.legacy.layout.FieldLayout f : r.layout.fields()) {
            if (!f.isArray() || !(values.get(f.json()) instanceof List<?> list)) continue;
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object row : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (row instanceof Map<?, ?> x) {
                    for (com.bart.maris.middleware.legacy.layout.FieldLayout c : f.children()) {
                        Object v = x.get(c.json());
                        String name = c.name().startsWith("VAGen_") ? c.name().substring(6) : c.name();
                        m.put(name.toUpperCase(Locale.ROOT), v instanceof String str ? str.strip() : v);   // the screens match field names in any case (DY-m)
                    }
                }
                out.add(m);
            }
            // the old tables end at the last row with a description (rows below it keep counts the screen copies into every row)
            while (!out.isEmpty() && (out.get(out.size() - 1).containsKey("DESC") ? empty(out.get(out.size() - 1).get("DESC"))
                    : out.get(out.size() - 1).values().stream().allMatch(YardTrackingMoveScreenLogic::empty))) out.remove(out.size() - 1);
            return out;
        }
        return List.of();
    }

    private static boolean empty(Object v) {
        return v == null || v instanceof String s && s.isBlank() || v instanceof Number n && n.doubleValue() == 0;
    }

    /** replaces a record's values */
    void put(String var, Map<String, ?> values) {
        Rec r = byName.get(var.toUpperCase(Locale.ROOT));
        if (r == null) throw new IllegalArgumentException("no record " + var);
        r.data = RecordCodec.encode(r.layout, values);
    }

    /** sets one top-level field of a record */
    void set(String var, String field, Object value) {
        Map<String, Object> v = new LinkedHashMap<>(get(var));
        v.put(field, value);
        put(var, v);
    }

    @Override
    public Rec record(String n) {
        String up = n.startsWith("type:") ? null : n.toUpperCase(Locale.ROOT);
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
        Map<String, Object> trace = new LinkedHashMap<>(inputs);
        if (skipped.contains(p.toUpperCase(Locale.ROOT))) {             // replaying: the write already happened
            in.errorCode = "00000000";
            return;
        }
        calls.add(Map.entry(def.function(), trace));
        try {
            ProgramResult res = legacy.execute(def.function(), inputs, false);
            for (Map.Entry<String, Rec> e : recordArgs) {
                Map<String, Object> back = res.record(e.getKey());
                if (back != null) e.getValue().data = RecordCodec.encode(e.getValue().layout, back);
            }
            in.errorCode = "00000000";
        } catch (RuntimeException e) {
            in.errorCode = "SVCFAIL";
        }
    }

    @Override
    public void written(Rec rec, Rec.Path path) {
        if (path != null && path.dims().isEmpty()) {
            String n = path.field().name().toUpperCase(Locale.ROOT);
            if (n.contains("ENABLE") || n.contains("DISABLE")) {
                Object v = interp.read(new Interp.FieldRef(rec, path, new int[0], null, null));
                if (v != null && "Y".equals(v.toString().strip())) switches.add((rec.var + "." + n).toUpperCase(Locale.ROOT));
            }
        }
        if (writes.isEmpty() || path == null) return;
        List<String> l = writes.peek();
        for (com.bart.maris.middleware.legacy.layout.FieldLayout f : path.chain()) {          // the field and the groups around it
            String key = (rec.var + "." + f.name()).toUpperCase(Locale.ROOT);
            if ((onField.containsKey(key) || onWindow.containsKey(key)) && !l.contains(key)) l.add(key);
        }
    }

    @Override
    public Supplier<LocalDateTime> clock() {
        return () -> LocalDateTime.now(clock);
    }
}
