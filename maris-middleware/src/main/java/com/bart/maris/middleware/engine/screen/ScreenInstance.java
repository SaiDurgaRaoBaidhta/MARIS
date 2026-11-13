package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.runtime.EngineException;
import com.bart.maris.middleware.engine.runtime.Interp;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One running old screen: its records, function parts, widgets, child screens and connections, built from the
 * bundle's description of its Connections handler, and an interpreter for its statement groups.
 */
public final class ScreenInstance implements Interp.Host {
    public final String id;
    public final String name;
    final String pkg;
    final ScreenSession session;
    final ScreenInstance parent;
    private final ScreenBundle bundle;
    private final JsonNode def;
    private final List<String> pkgs = new ArrayList<>();
    final Map<String, Part> parts = new LinkedHashMap<>();          // upper-case name -> part
    final Map<String, WidgetPart> widgets = new LinkedHashMap<>();  // upper-case widget name -> widget
    private final List<Rec> records = new ArrayList<>();
    private final Map<Rec, RecordPart> recordParts = new IdentityHashMap<>();
    private final Map<String, ConnPart> connParts = new LinkedHashMap<>();
    private final List<A2A> a2a = new ArrayList<>();
    final ScreenPart screenPart;
    final Interp interp;
    private final Deque<Map<RecordPart, List<Rec.Path>>> updates = new ArrayDeque<>();
    boolean open;
    WidgetPart focused;

    ScreenInstance(String id, String name, JsonNode def, ScreenBundle bundle, ScreenSession session, ScreenInstance parent) {
        this.id = id;
        this.name = name;
        this.def = def;
        this.bundle = bundle;
        this.session = session;
        this.parent = parent;
        this.pkg = def.path("pkg").asText();
        pkgs.add(pkg);
        for (JsonNode i : def.path("imports")) {
            String q = i.asText();
            int dot = q.lastIndexOf('.');
            if (dot > 0 && !pkgs.contains(q.substring(0, dot))) pkgs.add(q.substring(0, dot));
        }
        if (!pkgs.contains("MRSCommonCode")) pkgs.add("MRSCommonCode");
        this.screenPart = new ScreenPart(this);
        this.interp = new Interp(this);
        build();
    }

    // ---- building ------------------------------------------------------------------------------------------

    private void build() {
        for (Iterator<Map.Entry<String, JsonNode>> it = def.path("widgets").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            widgets.put(key(e.getKey()), new WidgetPart(e.getKey(), e.getValue().path("type").asText(), e.getValue().get("props"), this));
        }
        for (JsonNode v : def.path("vars")) {
            String n = v.path("name").asText(), type = v.path("type").asText();
            JsonNode props = v.path("props");
            switch (type) {
                case "ExecutablePart" -> parts.put(key(n), new ExecPart(n, props.path("callFunction").path("id").asText(), this));
                case "VariablePart" -> parts.put(key(n), new VariablePart(n, session));
                case "ViewWrapper" -> parts.put(key(n), new WrapperPart(n, props.path("viewPartClass").asText(), this));
                case "DataTableReference", "PublicInterface" -> { }
                case "MessagePrompter" -> {
                    WidgetPart w = new WidgetPart(n, "MessagePrompter", v.get("props"), this);
                    parts.put(key(n), w);
                }
                default -> {
                    if (bundle.screen(type) != null) {                 // an embedded part such as XXM0I (message display)
                        WrapperPart w = new WrapperPart(n, type, this);
                        parts.put(key(n), w);
                        embedded.add(w);
                        continue;
                    }
                    RecordLayout l = bundle.recordType(type, pkgs);
                    if (l == null) {
                        Rec t = bundle.table(type);
                        if (t != null) { Rec r = new Rec(n, t.layout); r.data = t.data; addRecord(r); }
                        continue;
                    }
                    addRecord(new Rec(n, l));
                }
            }
        }
        wire();
    }

    private final List<WrapperPart> embedded = new ArrayList<>();

    private RecordPart addRecord(Rec r) {
        RecordPart p = new RecordPart(r, session);
        records.add(r);
        recordParts.put(r, p);
        parts.put(key(r.var), p);
        return p;
    }

    JsonNode widgetDef(String widget) {
        return def.path("widgets").get(widget);
    }

    WidgetPart shell() {
        for (WidgetPart w : widgets.values()) if (w.isShell()) return w;
        return null;
    }

    static String key(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /** the part an argument of a connection call names: screen, screen.Widget, a variable, cm.getConnection(n) */
    private Part part(JsonNode ref) {
        if (ref.has("call")) {                                   // cm.getConnection(n)
            return conn(ref.path("args").path(0).asText());
        }
        String id = ref.path("id").asText();
        if (id.equals("screen")) return screenPart;
        if (id.startsWith("screen.")) {
            WidgetPart w = widgets.get(key(id.substring(7)));
            if (w == null) {                                     // a widget the view does not declare: keep a stand-in
                w = new WidgetPart(id.substring(7), "Unknown", null, this);
                widgets.put(key(id.substring(7)), w);
            }
            return w;
        }
        Part p = parts.get(key(id));
        if (p != null) return p;
        int dot = id.indexOf('.');
        if (dot > 0 && parts.get(key(id.substring(0, dot))) instanceof WrapperPart w) {   // a widget of a child screen
            WidgetPart cw = w.child().widget(id.substring(dot + 1));
            if (cw != null) return cw;
        }
        Rec r = record(id);                                      // a record type used without a declaration
        if (r != null) return recordParts.get(r);
        throw new EngineException(name + ": connection refers to unknown part " + id);
    }

    private ConnPart conn(String id) {
        return connParts.computeIfAbsent(id, k -> new ConnPart("connection " + k, session));
    }

    /** one attribute-to-attribute connection */
    final class A2A {
        final Part src, tgt;
        final String sa, ta;
        final boolean readOnly;
        boolean aligning;

        A2A(Part src, String sa, Part tgt, String ta, boolean readOnly) {
            this.src = src; this.sa = sa; this.tgt = tgt; this.ta = ta; this.readOnly = readOnly;
        }

        void connect() {
            src.on(sa, (v, has) -> setTarget(has ? v : src.get(sa)));
            if (!readOnly) tgt.on(ta, (v, has) -> setSource(has ? v : tgt.get(ta)));
        }

        void setTarget(Object v) {
            if (aligning) return;
            aligning = true;
            try { tgt.set(ta, v); } finally { aligning = false; }
        }

        void setSource(Object v) {
            if (aligning) return;
            aligning = true;
            try { src.set(sa, v); } finally { aligning = false; }
        }

        void initialSync() {
            Object v = src.get(sa);
            Object tv;
            if ((!readOnly && v == null && (tv = tgt.get(ta)) != null) || !tgt.settable(ta)) {
                if (!readOnly || !tgt.settable(ta)) setSource(tgt.get(ta));
            } else if (v != null) {
                setTarget(v);
            }
        }

        boolean touches(Part p) {
            return src == p || tgt == p;
        }
    }

    private void wire() {
        List<Runnable> promotions = new ArrayList<>();
        for (JsonNode c : def.path("connections")) {
            String m = c.path("m").asText();
            JsonNode a = c.path("a");
            try {
                switch (m) {
                    case "connectAttributes", "connectReadOnlyAttributeToAttribute" -> {
                        A2A x = new A2A(part(a.get(0)), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), m.startsWith("connectReadOnly"));
                        x.connect();
                        a2a.add(x);
                    }
                    case "connectEventToAction" -> e2a(part(a.get(0)), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), null, null);
                    case "connectEventToActionWithParameters" -> e2a(part(a.get(0)), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), a.get(4), null);
                    case "connectEventToActionWithParametersAndId" -> e2a(part(a.get(0)), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), a.get(4), a.get(5).asText());
                    case "connectEventToActionWithId" -> e2a(part(a.get(0)), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), null, a.get(4).asText());
                    case "connectConnectionToAction" -> e2a(conn(a.get(0).asText()), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), a.size() > 4 && a.get(4).isArray() ? a.get(4) : null, null);
                    case "connectConnectionToActionWithId" -> e2a(conn(a.get(0).asText()), a.get(1).asText(), part(a.get(2)), a.get(3).asText(), null, a.get(4).asText());
                    case "promoteAttribute" -> {
                        Part src = part(a.get(0));
                        String sa = a.get(1).asText(), pn = a.get(3).asText();
                        screenPart.attrs.put(pn, new Object[]{src, sa});
                        promotions.add(() -> src.on(sa, (v, has) -> screenPart.signal(pn, has ? v : src.get(sa))));
                    }
                    case "promoteEvent" -> {
                        Part src = part(a.get(0));
                        String ev = a.get(1).asText(), pn = a.get(3).asText();
                        screenPart.promotedEvents.add(pn);
                        promotions.add(() -> src.on(ev, (v, has) -> { if (has) screenPart.signal(pn, v); else screenPart.signal(pn); }));
                    }
                    case "promoteActionAs" -> screenPart.actions.put(a.get(3).asText(), new Object[]{part(a.get(0)), a.get(1).asText()});
                    default -> { }
                }
            } catch (EngineException e) {
                session.warn(name + ": " + m + " " + a + " not wired: " + e.getMessage());
            }
        }
        promotions.forEach(Runnable::run);
    }

    private void e2a(Part src, String event, Part tgt, String action, JsonNode params, String id) {
        ConnPart cp = id == null ? null : conn(id);
        List<Object[]> ps = new ArrayList<>();                       // {name, kind, value...}
        if (params != null) {
            for (JsonNode p : params) {
                String call = p.path("call").asText();
                JsonNode pa = p.path("args");
                if (call.endsWith("createConstantParameter")) ps.add(new Object[]{pa.get(0).asText(), "c", constant(pa.get(1))});
                else if (call.endsWith("createAttributeParameter")) ps.add(new Object[]{pa.get(0).asText(), "a", part(pa.get(1)), pa.get(2).asText()});
                else if (call.endsWith("createConnectionResultParameter")) ps.add(new Object[]{pa.get(0).asText(), "r", conn(pa.get(1).asText())});
            }
        }
        boolean[] busy = {false};
        src.on(event, (v, has) -> {
            if (busy[0]) return;
            busy[0] = true;
            try {
                Args args = new Args();
                if (!ps.isEmpty()) {
                    for (Object[] p : ps) {
                        Object val = switch ((String) p[1]) {
                            case "c" -> p[2];
                            case "a" -> ((Part) p[2]).get((String) p[3]);
                            default -> ((ConnPart) p[2]).result;
                        };
                        args.add((String) p[0], val);
                    }
                } else if (has) {
                    args.add(v);
                }
                Object ret = tgt.perform(action, args, true);
                if (cp != null) cp.returned(ret);
            } finally {
                busy[0] = false;
            }
        });
    }

    private static Object constant(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.decimalValue();
        if (n.isBoolean()) return n.booleanValue();
        return n.asText();
    }

    /** cm.setup: the initial sync of every attribute connection, in declaration order */
    void setup() {
        for (WrapperPart w : embedded) w.child();
        for (A2A x : a2a) {
            try { x.initialSync(); }
            catch (EngineException e) { session.warn(name + ": initial sync " + x.src.name + "." + x.sa + " -> " + x.tgt.name + "." + x.ta + ": " + e.getMessage()); }
        }
    }

    /** a child screen got its view: re-run the initial sync of every connection touching it */
    void align(Part wrapper) {
        for (A2A x : a2a) if (x.touches(wrapper)) x.initialSync();
    }

    // ---- windows -------------------------------------------------------------------------------------------

    void open() {
        if (open) { session.focusWindow(this); return; }
        open = true;
        session.opened(this);
        WidgetPart sh = shell();
        if (sh != null) sh.signal("aboutToOpenWidget");
        screenPart.signal("aboutToOpenWidget");
        if (sh != null) sh.signal("openedWidget");
        screenPart.signal("openedWidget");
    }

    void close() {
        if (!open) return;
        WidgetPart sh = shell();
        if (sh != null) sh.signal("aboutToCloseWidget");
        open = false;
        session.closed(this);
        if (sh != null) sh.signal("closedWidget");
        screenPart.signal("closedWidget");
    }

    void focus(WidgetPart w) {
        if (focused != null) focused.focus = false;
        focused = w;
        w.focus = true;
    }

    // ---- running statement groups --------------------------------------------------------------------------

    /** runs a function like an ExecutablePart: field events are queued and fire after it returns */
    Object execute(String function) {
        updates.push(new LinkedHashMap<>());
        Object ret;
        Map<RecordPart, List<Rec.Path>> mine;
        try {
            ret = interp.run(function);
        } finally {
            mine = updates.pop();
        }
        for (Map.Entry<RecordPart, List<Rec.Path>> e : mine.entrySet()) {
            RecordPart rp = e.getKey();
            List<Rec.Path> paths = e.getValue();
            if (paths.contains(null)) rp.signalWhole();
            else rp.signalFields(paths);
        }
        return ret;
    }

    @Override
    public void written(Rec rec, Rec.Path path) {
        RecordPart rp = recordParts.get(rec);
        if (rp == null) return;                                       // a local variable
        if (updates.isEmpty()) {
            if (path == null) rp.signalWhole(); else rp.signalField(path);
            return;
        }
        List<Rec.Path> l = updates.peek().computeIfAbsent(rp, k -> new ArrayList<>());
        if (path == null) { if (!l.contains(null)) l.add(null); }
        else if (!l.contains(path)) l.add(path);
    }

    @Override
    public Rec record(String n) {
        if (n.startsWith("type:")) {
            RecordLayout l = bundle.recordType(n.substring(5), pkgs);
            return l == null ? null : new Rec(n.substring(5), l);
        }
        Part p = parts.get(key(n));
        if (p instanceof RecordPart rp) return rp.rec;
        if (p != null) return null;
        // a record or data table the handler did not declare: created on first use
        if (n.contains(".")) {
            Rec t = bundle.table(n);
            if (t != null) return addRecord(t).rec;
            RecordLayout l = bundle.recordType(n, pkgs);
            return l == null ? null : addRecord(new Rec(n.substring(n.lastIndexOf('.') + 1), l)).rec;
        }
        Rec t = bundle.table(n);
        if (t != null) return addRecord(t).rec;
        RecordLayout l = bundle.recordType(n, pkgs);
        if (l == null) return null;
        return addRecord(new Rec(n, l)).rec;
    }

    @Override
    public List<Rec> records() {
        return records;
    }

    @Override
    public Ast.Function function(String n) {
        return bundle.function(n, pkg);
    }

    @Override
    public void callProgram(String program, List<Rec> args, Interp in) {
        session.engine.callProgram(this, program, args, in);
    }

    @Override
    public Supplier<LocalDateTime> clock() {
        return session.engine::now;
    }

    /**
     * A screen opened from the new menu gets what the old menus passed down: the signed-on user group and terminal id,
     * in every record field the screens use for them.
     */
    void seedSignon(String group, String terminal) {
        for (RecordPart rp : List.copyOf(recordParts.values())) {
            if (rp.rec.var.equalsIgnoreCase("XDCONTROL")) continue;
            for (String f : List.of("USER-GRP", "USER-GROUP", "USER_GRP", "USERGRP", "STORE-USER-GRP")) {
                if (rp.rec.find(f) != null && rp.rec.find(f).dims().isEmpty()) rp.set(f + " data", group);
            }
            for (String f : List.of("TERM-ID", "TERM_ID")) {
                if (rp.rec.find(f) != null && rp.rec.find(f).dims().isEmpty()) rp.set(f + " data", terminal);
            }
        }
    }

    /** a backend call made while running this screen (shown to the UI and in the log) */
    public void trace(Map<String, Object> call) {
        session.called(call);
    }

    public RecordPart recordPart(String var) {
        return parts.get(key(var)) instanceof RecordPart rp ? rp : null;
    }

    public WidgetPart widget(String n) {
        return widgets.get(key(n));
    }

    /** the widgets' state for the UI */
    public Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (WidgetPart w : widgets.values()) {
            if (w.type.equals("ContainerDetailsColumn") || w.type.endsWith("Converter") || w.type.equals("MessagePrompter")) continue;
            Map<String, Object> v = w.view();
            if (v.size() > 1) m.put(w.name, v);
        }
        return m;
    }
}
