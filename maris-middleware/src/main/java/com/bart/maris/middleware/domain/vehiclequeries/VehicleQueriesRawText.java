package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.legacy.codec.FieldCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A field of a program record as the old screens see it: its characters at full width. Groups (for example a consist
 * "201-01" made of CON-NO-X, DASH and DASH-NO-X) and numbers moved into text fields ("04") read this way.
 */
final class VehicleQueriesRawText {
    private static final String PKG = "MRSVehicleQueries";
    private final LegacyCatalog catalog;

    VehicleQueriesRawText(LegacyCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * @param record the record type (e.g. VHCL02B_DATA)
     * @param value  the decoded values holding the field (for a field inside an array row: that row)
     * @param path   the field's path from the record (e.g. dataRow, consist); the last name is read from {@code value}
     */
    String text(String record, Map<String, Object> value, String... path) {
        RecordLayout layout = catalog.record(PKG + "." + record);
        List<FieldLayout> fields = layout.fields();
        FieldLayout f = null;
        for (String name : path) {
            f = find(fields, name);
            fields = f.children() == null ? List.of() : f.children();
        }
        if (f == null) throw new IllegalArgumentException("no field path");
        byte[] buf = new byte[f.bytes()];
        write(f, value == null ? null : value.get(f.json()), buf, 0);
        return new String(buf, StandardCharsets.ISO_8859_1);
    }

    private static FieldLayout find(List<FieldLayout> fields, String name) {
        for (FieldLayout f : fields) if (name.equals(f.json()) || name.equalsIgnoreCase(f.name())) return f;
        throw new IllegalArgumentException("no field " + name);
    }

    @SuppressWarnings("unchecked")
    private static void write(FieldLayout f, Object v, byte[] buf, int pos) {
        if (f.isGroup()) {
            Arrays.fill(buf, pos, pos + f.bytes(), (byte) ' ');
            if (v instanceof Map<?, ?> m) {
                for (FieldLayout c : f.children()) if (!c.filler()) write(c, ((Map<String, Object>) m).get(c.json()), buf, pos + c.offset());
            } else if (v != null) {
                FieldCodec.write(f, v, buf, pos, f.json());
            }
            return;
        }
        FieldCodec.writeDefault(f, buf, pos);
        if (v != null) FieldCodec.write(f, v, buf, pos, f.json());
    }
}
