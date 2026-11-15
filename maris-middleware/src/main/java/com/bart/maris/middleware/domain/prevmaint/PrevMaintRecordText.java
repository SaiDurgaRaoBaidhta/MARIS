package com.bart.maris.middleware.domain.prevmaint;

import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A program field read back as the text the old screen sees. A field the record splits into parts (REVENUE-DATE =
 * month, "-", day ...; INCD-NO = prefix, base number, suffix) comes back from the codec as its parts, the numbers without
 * their leading zeros; this puts the parts back into the field's own bytes, e.g. "2026-01-05", "29990099".
 */
public final class PrevMaintRecordText {
    private final LegacyCatalog catalog;

    public PrevMaintRecordText(LegacyCatalog catalog) {
        this.catalog = catalog;
    }

    /** @param param the program's parameter (e.g. VHXREVA_DATA); @param field the JSON name of a top-level field */
    public String text(String program, String param, Map<String, Object> record, String field) {
        Object v = record == null ? null : record.get(field);
        if (!(v instanceof Map<?, ?>)) return v == null ? "" : v.toString().strip();
        ServiceDefinition def = catalog.function(program).orElseThrow(() -> new IllegalStateException("no program " + program));
        String name = def.params().stream().filter(p -> p.name().equals(param)).findFirst()
                .orElseThrow(() -> new IllegalStateException(program + " has no " + param)).record();
        RecordLayout layout = catalog.record(name);
        FieldLayout f = layout.fields().stream().filter(x -> field.equals(x.json())).findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " has no " + field));
        byte[] b = RecordCodec.encode(layout, Map.of(field, v));
        return new String(b, f.offset(), f.bytes(), StandardCharsets.ISO_8859_1).strip();
    }
}
