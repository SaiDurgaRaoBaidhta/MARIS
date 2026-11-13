package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.EngineException;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A record field as an object (gt2 HptArrayField / HptField), e.g. the DATA_ROW column held by a VariablePart.
 * Its rows are live views into the record's bytes (HptSubscriptedField).
 */
public final class ArrayFieldPart extends Part {
    final RecordPart owner;
    final Rec.Path path;

    ArrayFieldPart(RecordPart owner, Rec.Path path, ScreenSession session) {
        super(owner.name + "." + path.field().name(), session);
        this.owner = owner;
        this.path = path;
    }

    int occurs() {
        List<FieldLayout> d = path.dims();
        return d.isEmpty() ? 1 : d.get(d.size() - 1).occurs();
    }

    /** one row of an array field: a live view */
    public record Row(ArrayFieldPart field, int index) {
        int offset() {
            return field.path.dims().isEmpty() ? Rec.offset(field.path, new int[0]) : Rec.offset(field.path, new int[]{index});
        }

        /** the value of a sub-field of the row, by its EGL name (a column's attributeName) */
        public Object valueOf(String attribute) {
            FieldLayout f = field.path.field();
            String up = attribute.toUpperCase(Locale.ROOT);
            if (f.name().equalsIgnoreCase(attribute) || f.name().equalsIgnoreCase("VAGen_" + attribute)) return value();
            Rec.Path p = Rec.findBelow(new Rec.Path(List.of(f)), attribute);
            if (p == null) p = Rec.findBelow(new Rec.Path(List.of(f)), "VAGen_" + up);
            if (p == null) return null;
            int off = offset();
            List<FieldLayout> chain = p.chain();
            for (int i = 1; i < chain.size(); i++) off += chain.get(i).offset();   // nested sub-arrays: first element
            return RecordPart.display(Conv.read(p.field(), field.owner.rec.data, off));
        }

        public Object value() {
            return RecordPart.display(Conv.read(field.path.field(), field.owner.rec.data, offset()));
        }

        public String text() {
            return Conv.rtrim(Conv.text(field.owner.rec.data, offset(), field.path.field().bytes()));
        }
    }

    static List<Object> values(RecordPart owner, Rec.Path p) {
        ArrayFieldPart a = owner.fieldObject(p);
        int size = a.size();
        List<Object> out = new ArrayList<>();
        for (int i = 1; i <= size; i++) out.add(new Row(a, i).value());
        return out;
    }

    /** highest row that is not empty */
    int size() {
        for (int i = occurs(); i >= 1; i--) {
            int off = new Row(this, i).offset();
            if (!owner.isDefault(path.field(), off)) return i;
        }
        return 0;
    }

    @Override
    public Object get(String attr) {
        return switch (attr) {
            case "self", "value" -> this;
            case "size" -> BigDecimal.valueOf(size());
            default -> null;
        };
    }

    @Override
    public void set(String attr, Object value) {
        if (attr.equals("self") || attr.equals("value")) {
            if (value instanceof List<?> l) owner.write(path, l);
            signal("self", this);
        }
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        switch (action) {
            case "getFieldsStartingAt_to_", "getValuesStartingAt_to_" -> {
                Object f0 = args instanceof Args a ? a.named("firstIndex", 0) : args.isEmpty() ? null : args.get(0);
                Object l0 = args instanceof Args a ? a.named("lastIndex", 1) : args.size() < 2 ? null : args.get(1);
                int first = f0 == null ? 0 : Conv.number(f0).intValue();
                int last = l0 == null ? 0 : Conv.number(l0).intValue();
                List<Object> out = new ArrayList<>();
                if (first > last) return out;
                if (first < 1 || last > occurs()) throw new EngineException(action + "(" + first + ", " + last + ") outside 1.." + occurs() + " of " + name);
                for (int i = first; i <= last; i++) out.add(action.startsWith("getFields") ? new Row(this, i) : new Row(this, i).value());
                return out;
            }
            case "setValueAtIndex_using_" -> {
                int i = Conv.number(args.get(0)).intValue();
                Object v = args.size() > 1 ? args.get(1) : null;
                if (i < 1 || i > occurs()) return null;                     // outside the column: nothing to write
                Conv.write(path.field(), v == null ? "" : v instanceof Row r ? r.text() : v, owner.rec.data, new Row(this, i).offset());
                signal("self", this);
                return null;
            }
            case "size" -> { return BigDecimal.valueOf(size()); }
            default -> {
                if (invokedFromEvent) set(action, args.isEmpty() ? null : args.get(0));
                return null;
            }
        }
    }
}
