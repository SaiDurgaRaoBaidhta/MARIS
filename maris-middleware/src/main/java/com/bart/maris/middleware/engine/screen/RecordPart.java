package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.codec.FieldCodec;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A record as a connectable part (gt2 HptRecordPart). Attributes: "data" (the whole storage), "F data" (a field's value),
 * "F" (the field object, e.g. an array column), "self". Field values read as trimmed text or numbers; a field that was
 * never modified and still holds its empty value reads as null (that decides the initial sync of connections).
 */
public final class RecordPart extends Part {
    public final Rec rec;
    private final Set<String> modified = new HashSet<>();
    private boolean allModified;
    private final Map<String, ArrayFieldPart> fieldObjects = new HashMap<>();
    private final boolean level77;

    RecordPart(Rec rec, ScreenSession session) {
        super(rec.var, session);
        this.rec = rec;
        this.level77 = rec.var.toUpperCase(Locale.ROOT).endsWith("_LEVEL77ITEMS");
    }

    static String fieldOf(String attr) {
        return attr.endsWith(" data") ? attr.substring(0, attr.length() - 5) : null;
    }

    private static String strip(String f) {
        return f.startsWith("VAGen_") ? f.substring(6) : f;
    }

    Rec.Path path(String field) {
        Rec.Path p = rec.find(field);
        if (p == null) p = rec.find(strip(field));
        if (p == null) p = rec.find("VAGen_" + field);
        return p;
    }

    @Override
    public Object get(String attr) {
        if (attr.equals("self") || attr.equals("this")) return this;
        if (attr.equals("data")) return rec;
        String f = fieldOf(attr);
        if (f != null) {
            Rec.Path p = path(f);
            if (p == null) return null;
            return value(p);
        }
        Rec.Path p = path(attr);
        if (p == null) return null;
        return fieldObject(p);
    }

    ArrayFieldPart fieldObject(Rec.Path p) {
        return fieldObjects.computeIfAbsent(p.field().name().toUpperCase(Locale.ROOT), k -> new ArrayFieldPart(this, p, session));
    }

    /** the value of a field the way HptField.data() gives it */
    Object value(Rec.Path p) {
        FieldLayout f = p.field();
        boolean mod = allModified || modified.contains(f.name().toUpperCase(Locale.ROOT));
        if (!p.dims().isEmpty()) {
            List<Object> rows = ArrayFieldPart.values(this, p);
            if (rows.isEmpty() && !mod) return null;
            return rows;
        }
        int off = Rec.offset(p, new int[0]);
        if (!mod && isDefault(f, off)) return null;
        return display(Conv.read(f, rec.data, off));
    }

    boolean isDefault(FieldLayout f, int off) {
        byte[] blank = new byte[f.bytes()];
        Arrays.fill(blank, (byte) ' ');
        FieldCodec.writeDefault(f, blank, 0);
        return Arrays.equals(rec.data, off, off + f.bytes(), blank, 0, blank.length);
    }

    static Object display(Object v) {
        return v instanceof String s ? Conv.rtrim(s) : v;
    }

    @Override
    public void set(String attr, Object value) {
        if (attr.equals("data")) {
            if (value instanceof Rec other) {
                if (other.data != rec.data) rec.data = other.data;            // share storage, like setContents_
            } else if (value instanceof RecordPart rp) {
                rec.data = rp.rec.data;
            } else if (value == null) {
                byte[] blank = RecordCodec.blank(rec.layout);
                System.arraycopy(blank, 0, rec.data, 0, Math.min(blank.length, rec.data.length));
            } else {
                Conv.writeText(Conv.string(value), rec.data, 0, rec.data.length);
            }
            signalWhole();
            return;
        }
        String f = fieldOf(attr);
        Rec.Path p = path(f == null ? attr : f);
        if (p == null) return;
        if (f == null) {                                    // "F": the field object; nothing changes
            signalField(p);
            return;
        }
        write(p, value);
        signalField(p);
    }

    void write(Rec.Path p, Object value) {
        FieldLayout f = p.field();
        if (!p.dims().isEmpty()) {
            List<?> rows = value instanceof List<?> l ? l : value == null ? List.of() : List.of(value);
            int n = p.dims().get(p.dims().size() - 1).occurs();
            for (int i = 1; i <= n; i++) {
                int off = Rec.offset(p, new int[]{i});
                Object v = i <= rows.size() ? rows.get(i - 1) : null;
                writeOne(f, v, off);
            }
            return;
        }
        writeOne(f, value, Rec.offset(p, new int[0]));
    }

    private void writeOne(FieldLayout f, Object v, int off) {
        if (v == null) {
            FieldCodec.writeDefault(f, rec.data, off);
            return;
        }
        if (v instanceof ArrayFieldPart.Row row) v = row.text();
        if (v instanceof Boolean b) v = b ? BigDecimal.ONE : BigDecimal.ZERO;
        Conv.write(f, v, rec.data, off);
    }

    // ---- signalling ----------------------------------------------------------------------------------------

    /**
     * "F data" also reaches the field objects F belongs to (gt2 HptField: a group's or array column's object
     * signals its sub-fields' "F data" too), e.g. DATA_ROW_of_VHVM13B_DATA "EVENT-TYPE data" -> a drop-down's items
     */
    @Override
    public void signal(String event) {
        super.signal(event);
        String f = fieldOf(event);
        if (f == null || fieldObjects.isEmpty()) return;
        for (ArrayFieldPart fo : List.copyOf(fieldObjects.values())) {
            if (fo.listens(event) && fo.below(f) != null) fo.signal(event);
        }
    }

    /** someone listens to "F data": the record, or a field object F belongs to (signal forwards it there) */
    private boolean heard(String event) {
        if (listens(event)) return true;
        String f = fieldOf(event);
        if (f == null) return false;
        for (ArrayFieldPart fo : fieldObjects.values()) if (fo.listens(event) && fo.below(f) != null) return true;
        return false;
    }

    /** the whole record changed: "data", then every field's "F data" and "F" in declaration order */
    public void signalWhole() {
        allModified = true;
        signal("data", rec);
        signalAll(rec.layout.fields());
    }

    private void signalAll(List<FieldLayout> fields) {
        for (FieldLayout f : fields) {
            if (f.filler()) continue;
            String n = f.name();
            if (heard(n + " data")) signal(n + " data");
            if (n.startsWith("VAGen_") && listens(strip(n) + " data")) signal(strip(n) + " data");
            if (listens(n)) signal(n, get(n));
            if (n.startsWith("VAGen_") && listens(strip(n))) signal(strip(n), get(n));
            signalAll(f.children());
        }
    }

    /** the queued updates of one function run: per field "F", "F data"; the record's "data" once */
    public void signalFields(List<Rec.Path> paths) {
        boolean first = true;
        for (Rec.Path p : paths) {
            signalOne(p);
            if (first && !level77) { signal("data", rec); first = false; }
        }
    }

    private void signalOne(Rec.Path p) {
        // the field, its array column(s) (value + field object), and the groups it is part of (their value only:
        // a write to CON inside CON-DASH changes "CON-DASH data", but the group's field object stays the same)
        List<FieldLayout> targets = new ArrayList<>(p.chain());
        for (FieldLayout f : targets) {
            modified.add(f.name().toUpperCase(Locale.ROOT));
            String n = f.name();
            boolean valueOnly = f != p.field() && f.occurs() <= 1;
            if (!valueOnly && listens(n)) signal(n, get(n));
            if (!valueOnly && n.startsWith("VAGen_") && listens(strip(n))) signal(strip(n), get(n));
            if (heard(n + " data")) signal(n + " data");
            if (n.startsWith("VAGen_") && listens(strip(n) + " data")) signal(strip(n) + " data");
        }
        signalAll(p.field().children());
    }

    /** one field changed: "F", "F data" (also for its array column and sub-fields), then the record's "data" */
    public void signalField(Rec.Path p) {
        List<FieldLayout> targets = new ArrayList<>(p.chain());           // the field and the groups (and array columns) it is part of
        for (FieldLayout f : targets) {
            modified.add(f.name().toUpperCase(Locale.ROOT));
            String n = f.name();
            boolean valueOnly = f != p.field() && f.occurs() <= 1;          // a group around the field: its value only
            if (!valueOnly && listens(n)) signal(n, get(n));
            if (!valueOnly && n.startsWith("VAGen_") && listens(strip(n))) signal(strip(n), get(n));
            if (heard(n + " data")) signal(n + " data");
            if (n.startsWith("VAGen_") && listens(strip(n) + " data")) signal(strip(n) + " data");
        }
        signalAll(p.field().children());
        if (!level77) signal("data", rec);
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        if (action.equals("setEmpty") || action.equals("clear")) {
            byte[] blank = RecordCodec.blank(rec.layout);
            System.arraycopy(blank, 0, rec.data, 0, blank.length);
            signalWhole();
            return null;
        }
        // invoked from an event: an attribute name sets the attribute to the first argument (or null)
        set(action, args.isEmpty() ? null : args.get(0));
        return null;
    }
}
