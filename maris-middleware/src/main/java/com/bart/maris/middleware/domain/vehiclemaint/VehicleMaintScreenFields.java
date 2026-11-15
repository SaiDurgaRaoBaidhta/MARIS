package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What an old screen's wiring says about a record: which widget shows which field (connections "screen.Widget object /
 * selectedItem" to "RECORD FIELD data") and which widgets a trigger disables. Read from the screen bundle so the clean
 * services follow the old screen instead of a typed field list.
 */
final class VehicleMaintScreenFields {
    private final ScreenBundle bundle;
    private final String pkg;

    VehicleMaintScreenFields(ScreenBundle bundle, String pkg) {
        this.bundle = bundle;
        this.pkg = pkg;
    }

    /** widget -> JSON name of the record field it shows, in wiring order */
    Map<String, String> bound(String screen, String record) {
        RecordLayout layout = bundle.recordType(record, List.of(pkg));
        if (layout == null) throw new IllegalStateException("record " + record + " not found");
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode c : def(screen).path("connections")) {
            JsonNode a = c.path("a");
            if (c.path("m").asText().startsWith("connectEvent") || a.size() < 4) continue;
            String l = a.path(0).path("id").asText(""), r = a.path(2).path("id").asText("");
            String widget = null, field = null;
            if (l.startsWith("screen.") && r.equalsIgnoreCase(record)) { widget = l.substring(7); field = a.path(3).asText(); }
            else if (r.startsWith("screen.") && l.equalsIgnoreCase(record)) { widget = r.substring(7); field = a.path(1).asText(); }
            if (widget == null || !field.endsWith(" data")) continue;
            String json = json(layout, field.substring(0, field.length() - 5));
            if (json != null) out.putIfAbsent(widget, json);
        }
        if (out.isEmpty()) throw new IllegalStateException(screen + " shows no field of " + record);
        return out;
    }

    /** the widgets a trigger field disables, e.g. VHVM03IW-TRIGGERS DISABLE */
    Set<String> disabledBy(String screen, String trigger) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode c : def(screen).path("connections")) {
            JsonNode a = c.path("a");
            if (c.path("m").asText().startsWith("connectEventToAction") && a.path(1).asText().equalsIgnoreCase(trigger + " data")
                    && a.path(3).asText().equals("disable") && a.path(2).path("id").asText().startsWith("screen.")) {
                out.add(a.path(2).path("id").asText().substring(7));
            }
        }
        if (out.isEmpty()) throw new IllegalStateException(screen + ": " + trigger + " disables nothing");
        return out;
    }

    /** the JSON name of a record field (EGL name; VAGen_ prefixed names answer to their short name too) */
    static String json(RecordLayout layout, String egl) {
        for (FieldLayout f : flat(layout.fields())) {
            String n = f.name().toUpperCase(Locale.ROOT);
            if (n.equals(egl.toUpperCase(Locale.ROOT)) || n.equals("VAGEN_" + egl.toUpperCase(Locale.ROOT))) return f.json();
        }
        return null;
    }

    static List<FieldLayout> flat(List<FieldLayout> fields) {
        List<FieldLayout> out = new java.util.ArrayList<>();
        for (FieldLayout f : fields) {
            out.add(f);
            if (f.children() != null) out.addAll(flat(f.children()));
        }
        return out;
    }

    private JsonNode def(String screen) {
        JsonNode d = bundle.screen(screen);
        if (d == null) throw new IllegalStateException("screen " + screen + " not found");
        return d;
    }
}
