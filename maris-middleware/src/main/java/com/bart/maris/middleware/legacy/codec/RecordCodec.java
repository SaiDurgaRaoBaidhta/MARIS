package com.bart.maris.middleware.legacy.codec;

import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts between JSON-style maps and a record's fixed byte layout. */
public final class RecordCodec {
    private RecordCodec() {}

    /** The record with every field at its empty value. */
    public static byte[] blank(RecordLayout layout) {
        byte[] buf = new byte[layout.length()];
        Arrays.fill(buf, (byte) 0x20);
        writeDefaults(layout.fields(), buf, 0);
        return buf;
    }

    private static void writeDefaults(List<FieldLayout> fields, byte[] buf, int base) {
        for (FieldLayout f : fields) {
            for (int i = 0; i < f.occurs(); i++) {
                int pos = base + f.offset() + i * f.bytes();
                FieldCodec.writeDefault(f, buf, pos);
                writeDefaults(f.children(), buf, pos);
            }
        }
    }

    /**
     * Encodes values onto an empty record. Keys may be the JSON name ({@code userGrp}) or the EGL name
     * ({@code USER_GRP}); fields that are missing or null keep their empty value.
     */
    public static byte[] encode(RecordLayout layout, Map<String, ?> values) {
        byte[] buf = blank(layout);
        if (values != null) writeFields(layout.fields(), values, buf, 0, layout.name());
        return buf;
    }

    private static void writeFields(List<FieldLayout> fields, Map<String, ?> values, byte[] buf, int base, String path) {
        for (String key : values.keySet()) {
            if (fields.stream().noneMatch(f -> !f.filler() && (key.equals(f.json()) || key.equals(f.name())))) {
                throw new CodecException(path + "." + key, "is not a field of this record");
            }
        }
        for (FieldLayout f : fields) {
            if (f.filler()) continue;
            Object v = values.containsKey(f.json()) ? values.get(f.json()) : values.get(f.name());
            if (v == null) continue;
            String p = path + "." + f.json();
            if (f.isArray()) {
                if (!(v instanceof List<?> list)) throw new CodecException(p, "must be a list of up to " + f.occurs() + " items");
                if (list.size() > f.occurs()) throw new CodecException(p, "has " + list.size() + " items; the record holds " + f.occurs());
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) != null) writeElement(f, list.get(i), buf, base + f.offset() + i * f.bytes(), p + "[" + i + "]");
                }
            } else {
                writeElement(f, v, buf, base + f.offset(), p);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeElement(FieldLayout f, Object v, byte[] buf, int pos, String path) {
        if (f.isGroup() && v instanceof Map<?, ?> m) {
            writeFields(f.children(), (Map<String, ?>) m, buf, pos, path);
        } else if (v instanceof Map<?, ?> || v instanceof List<?>) {
            throw new CodecException(path, "must be a single value");
        } else {
            FieldCodec.write(f, v, buf, pos, path);   // a group's own type (e.g. char(24)) can still be set as a whole
        }
    }

    /** Decodes a record. Short input is padded with the record's empty value. */
    public static Map<String, Object> decode(RecordLayout layout, byte[] data, boolean trimEmptyRows) {
        byte[] buf = blank(layout);
        System.arraycopy(data, 0, buf, 0, Math.min(data.length, buf.length));
        return readFields(layout.fields(), buf, 0, trimEmptyRows);
    }

    private static Map<String, Object> readFields(List<FieldLayout> fields, byte[] buf, int base, boolean trim) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldLayout f : fields) {
            if (f.filler()) continue;
            if (f.isArray()) {
                List<Object> items = new ArrayList<>(f.occurs());
                for (int i = 0; i < f.occurs(); i++) items.add(readElement(f, buf, base + f.offset() + i * f.bytes(), trim));
                if (trim) {
                    while (!items.isEmpty() && isEmpty(items.get(items.size() - 1))) items.remove(items.size() - 1);
                }
                out.put(f.json(), items);
            } else {
                out.put(f.json(), readElement(f, buf, base + f.offset(), trim));
            }
        }
        return out;
    }

    private static Object readElement(FieldLayout f, byte[] buf, int pos, boolean trim) {
        return f.isGroup() ? readFields(f.children(), buf, pos, trim) : FieldCodec.read(f, buf, pos);
    }

    /** Blank text, zero numbers, and objects/lists made only of those. Used to drop unused trailing table rows. */
    static boolean isEmpty(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof Long l) return l == 0L;
        if (v instanceof BigInteger b) return b.signum() == 0;
        if (v instanceof BigDecimal d) return d.signum() == 0;
        if (v instanceof Map<?, ?> m) return m.values().stream().allMatch(RecordCodec::isEmpty);
        if (v instanceof List<?> l) return l.stream().allMatch(RecordCodec::isEmpty);
        return false;
    }
}
