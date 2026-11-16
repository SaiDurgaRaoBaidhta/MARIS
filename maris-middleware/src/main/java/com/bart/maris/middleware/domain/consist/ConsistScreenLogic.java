package com.bart.maris.middleware.domain.consist;

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
 * of checks and record moves written out in the EGL (old VHLT01I's lay-up and VHTR01I's reconfigure, some 1,700 lines
 * each). The rules are the screen's; the programs it calls go through the legacy layer like any other clean API call.
 * Nothing is drawn and no widget is involved. The screen's own record-to-statement-group connections are kept as the
 * engine keeps them (ScreenInstance.execute): the fields a statement group writes are noted, and when it returns each
 * written field the screen listens to ({@code VHLT01IW-TRIGGER.CON-TRBL data -> VHLT01IS-CKCO-IND}) runs its groups, in the
 * order first written; {@code A hasExecuted -> B} runs B after A.
 */
final class ConsistScreenLogic implements Interp.Host {
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
    /** "RECORD|FIELD" -> the statement groups its "FIELD data" event runs (the screen's connections) */
    private final Map<String, List<String>> onData = new HashMap<>();
    /** statement group -> the groups its hasExecuted runs */
    private final Map<String, List<String>> after = new HashMap<>();
    private int depth;
    /** the fields written by each running statement group, in first-written order (null path = the whole record) */
    private final java.util.ArrayDeque<List<Map.Entry<Rec, Rec.Path>>> updates = new java.util.ArrayDeque<>();
    /** "RECORD|FIELD" -> when it was last written (a counter), for "the last one written wins" (bound texts, enable/disable) */
    private final Map<String, Long> writes = new HashMap<>();
    private long clock2;

    ConsistScreenLogic(ScreenBundle bundle, String screen, LegacyProgramService legacy, Clock clock) {
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
        for (JsonNode c : def.path("connections")) connection(c);
        this.interp = new Interp(this);
    }

    /** keeps the screen's record-event and hasExecuted connections to statement groups */
    private void connection(JsonNode c) {
        if (!c.path("m").asText().equals("connectEventToAction")) return;
        JsonNode a = c.path("a");
        String source = a.path(0).path("id").asText(), event = a.path(1).asText(), target = a.path(2).path("id").asText();
        if (!target.endsWith("__reference") || !a.path(3).asText().equals("execute")) return;
        String group = target.substring(0, target.length() - "__reference".length());
        if (event.equals("hasExecuted") && source.endsWith("__reference")) {
            after.computeIfAbsent(source.substring(0, source.length() - "__reference".length()).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(group);
        } else if (event.endsWith(" data") && !source.startsWith("screen.")) {
            String field = event.substring(0, event.length() - " data".length());
            onData.computeIfAbsent((source + "|" + field).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(group);
        }
    }

    private Rec add(Rec r) {
        records.add(r);
        byName.put(r.var.toUpperCase(Locale.ROOT), r);
        return r;
    }

    /** runs one statement group, then the groups connected to its hasExecuted */
    void run(String function) {
        run(function, null);
    }

    /**
     * runs a statement group (triggered by: the group whose write fired it, or null). A group that fires itself without
     * changing anything (VHTR01IS-CON-MSG writes CON-NOT-INSRV, which runs CON-MSG again, while CON-TRBL stays "Y") is
     * not run around again: the old window would spin there; here it stops once nothing changes.
     */
    private void run(String function, String triggeredBy) {
        if (depth > 40) throw new IllegalStateException("statement groups call each other without end at " + function);
        depth++;
        List<Map.Entry<Rec, Rec.Path>> mine;
        int before = function.equalsIgnoreCase(triggeredBy) ? state() : 0;
        updates.push(new ArrayList<>());
        try {
            (depth == 1 ? interp : new Interp(this)).run(function);
        } finally {
            mine = updates.pop();
        }
        try {
            if (function.equalsIgnoreCase(triggeredBy) && state() == before) return;
            for (Map.Entry<Rec, Rec.Path> w : mine) fire(w.getKey(), w.getValue(), function);
            for (String next : after.getOrDefault(function.toUpperCase(Locale.ROOT), List.of())) run(next);
        } finally {
            depth--;
        }
    }

    /** a fingerprint of every record's bytes */
    private int state() {
        int h = 1;
        for (Rec r : records) h = 31 * h + java.util.Arrays.hashCode(r.data);
        return h;
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
                    : out.get(out.size() - 1).values().stream().allMatch(ConsistScreenLogic::empty))) out.remove(out.size() - 1);
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

    /** a write to a field the screen listens to runs its statement groups at once (the field, or a group around it) */
    @Override
    public void written(Rec rec, Rec.Path path) {
        String r = rec.var.toUpperCase(Locale.ROOT);
        if (path == null) {
            for (com.bart.maris.middleware.legacy.layout.FieldLayout f : rec.layout.fields()) mark(r, f);
        } else {
            for (com.bart.maris.middleware.legacy.layout.FieldLayout f : path.chain()) writes.put(r + "|" + f.name().toUpperCase(Locale.ROOT), ++clock2);
        }
        if (onData.isEmpty()) return;
        if (updates.isEmpty()) { fire(rec, path, null); return; }               // written from outside a statement group: at once
        List<Map.Entry<Rec, Rec.Path>> l = updates.peek();
        for (Map.Entry<Rec, Rec.Path> e : l) if (e.getKey() == rec && java.util.Objects.equals(e.getValue(), path)) return;
        l.add(new java.util.AbstractMap.SimpleEntry<>(rec, path));
    }

    /** the statement groups listening to a written field (or to any field of a whole record) */
    private void fire(Rec rec, Rec.Path path, String by) {
        String r = rec.var.toUpperCase(Locale.ROOT);
        List<String> run = new ArrayList<>();
        if (path == null) {                                               // the whole record: every field it has
            for (Map.Entry<String, List<String>> e : onData.entrySet()) if (e.getKey().startsWith(r + "|")) run.addAll(e.getValue());
        } else {
            for (com.bart.maris.middleware.legacy.layout.FieldLayout f : path.chain()) {
                run.addAll(onData.getOrDefault(r + "|" + f.name().toUpperCase(Locale.ROOT), List.of()));
            }
        }
        for (String g : run) run(g, by);
    }

    private void mark(String r, com.bart.maris.middleware.legacy.layout.FieldLayout f) {
        writes.put(r + "|" + f.name().toUpperCase(Locale.ROOT), ++clock2);
        for (com.bart.maris.middleware.legacy.layout.FieldLayout c : f.children()) mark(r, c);
    }

    /** when a field was last written by the screen's statement groups (0: never), to pick the last of several */
    long lastWritten(String var, String field) {
        return writes.getOrDefault((var + "|" + field).toUpperCase(Locale.ROOT), 0L);
    }

    @Override
    public Supplier<LocalDateTime> clock() {
        return () -> LocalDateTime.now(clock);
    }
}
