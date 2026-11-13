package com.bart.maris.middleware.engine;

import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.egl.Parser;
import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.FieldType;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The old screens, as generated from the EGL source by tools/egl-catalog-generator (ScreenBundleGenerator):
 * record layouts, data tables, statement groups and per-screen connections and widgets. Loaded once.
 */
public final class ScreenBundle {
    private static final Logger log = LoggerFactory.getLogger(ScreenBundle.class);

    private final Map<String, RecordLayout> records = new HashMap<>();               // qualified name -> layout
    private final Map<String, List<String>> recordsByName = new HashMap<>();         // simple name (upper) -> qualified names
    private final Map<String, Rec> tables = new HashMap<>();                          // simple name (upper) -> filled table
    private final Map<String, Map<String, Ast.Function>> functions = new HashMap<>(); // pkg (upper) -> name (upper) -> fn
    private final Map<String, Ast.Function> functionsByName = new HashMap<>();
    private final Map<String, JsonNode> screens = new HashMap<>();                    // screen (upper) -> definition

    public static ScreenBundle load(String location) {
        ScreenBundle b = new ScreenBundle();
        PathMatchingResourcePatternResolver res = new PathMatchingResourcePatternResolver();
        ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String base = location.endsWith("/") ? location : location + "/";
        long t0 = System.currentTimeMillis();
        try {
            Map<String, RecordLayout> recs = read(om, res.getResource(base + "records.json"), new TypeReference<>() {});
            recs.forEach((qn, l) -> {
                b.records.put(qn.toUpperCase(Locale.ROOT), l);
                b.recordsByName.computeIfAbsent(l.name().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(qn);
            });
            JsonNode tables = read(om, res.getResource(base + "tables.json"), new TypeReference<>() {});
            for (Iterator<Map.Entry<String, JsonNode>> it = tables.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                b.tables.put(simple(e.getKey()).toUpperCase(Locale.ROOT), table(e.getKey(), e.getValue()));
            }
            for (Resource r : res.getResources(base + "functions/*.egl")) {
                String src;
                try (InputStream in = r.getInputStream()) { src = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1); }
                for (Ast.Function f : Parser.parseFile(src, r.getFilename())) {
                    b.functions.computeIfAbsent(f.pkg().toUpperCase(Locale.ROOT), k -> new HashMap<>()).putIfAbsent(f.name().toUpperCase(Locale.ROOT), f);
                    b.functionsByName.putIfAbsent(f.name().toUpperCase(Locale.ROOT), f);
                }
            }
            JsonNode screens = read(om, res.getResource(base + "screens.json"), new TypeReference<>() {});
            for (Iterator<Map.Entry<String, JsonNode>> it = screens.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                b.screens.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the screen bundle at " + location, e);
        }
        log.info("Screen bundle loaded in {} ms: {} screens, {} records, {} functions, {} tables", System.currentTimeMillis() - t0,
                b.screens.size(), b.records.size(), b.functionsByName.size(), b.tables.size());
        return b;
    }

    private static <T> T read(ObjectMapper om, Resource r, TypeReference<T> type) throws IOException {
        try (InputStream in = r.getInputStream()) {
            return om.readValue(in, type);
        }
    }

    private static String simple(String qn) {
        int i = qn.lastIndexOf('.');
        return i < 0 ? qn : qn.substring(i + 1);
    }

    /** a data table as a record: one array per column, filled with the table's rows */
    private static Rec table(String qn, JsonNode def) {
        JsonNode rows = def.get("rows");
        int n = Math.max(1, rows.size());
        List<FieldLayout> cols = new ArrayList<>();
        int off = 0;
        for (JsonNode c : def.get("columns")) {
            FieldType t = FieldType.valueOf(c.get("type").asText());
            int len = c.get("length").asInt();
            int bytes = t.isPacked() ? (len + 2) / 2 : len;
            cols.add(new FieldLayout(c.get("name").asText(), c.get("name").asText(), t, len, c.get("decimals").asInt(), bytes, n, off, false, List.of()));
            off += bytes * n;
        }
        Rec r = new Rec(simple(qn), new RecordLayout(simple(qn), "table", off, cols));
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size() && j < rows.get(i).size(); j++) {
                FieldLayout f = cols.get(j);
                Conv.write(f, rows.get(i).get(j).asText(), r.data, f.offset() + i * f.bytes());
            }
        }
        return r;
    }

    // ---- lookups -------------------------------------------------------------------------------------------

    public JsonNode screen(String name) {
        return screens.get(name.toUpperCase(Locale.ROOT));
    }

    public java.util.Set<String> screenNames() {
        return screens.keySet();
    }

    /** a record type by simple or qualified name; the preferred packages are tried first */
    public RecordLayout recordType(String name, List<String> preferPkgs) {
        String up = name.toUpperCase(Locale.ROOT);
        RecordLayout direct = records.get(up);
        if (direct != null) return direct;
        List<String> qns = recordsByName.get(up);
        if (qns == null || qns.isEmpty()) return null;
        for (String pkg : preferPkgs) {
            for (String qn : qns) if (qn.toUpperCase(Locale.ROOT).equals((pkg + "." + name).toUpperCase(Locale.ROOT))) return records.get(qn.toUpperCase(Locale.ROOT));
        }
        return records.get(qns.get(0).toUpperCase(Locale.ROOT));
    }

    /** a fresh copy of a data table, or null */
    public Rec table(String name) {
        Rec t = tables.get(simple(name).toUpperCase(Locale.ROOT));
        return t == null ? null : t.copyOf(t.var);
    }

    /** a function: qualified (pkg.NAME) or by name, preferring the given package */
    public Ast.Function function(String name, String pkg) {
        String up = name.toUpperCase(Locale.ROOT);
        int dot = up.lastIndexOf('.');
        if (dot > 0) {
            Map<String, Ast.Function> m = functions.get(up.substring(0, dot));
            if (m != null && m.containsKey(up.substring(dot + 1))) return m.get(up.substring(dot + 1));
            up = up.substring(dot + 1);
        }
        Map<String, Ast.Function> own = functions.get(pkg.toUpperCase(Locale.ROOT));
        if (own != null && own.containsKey(up)) return own.get(up);
        Map<String, Ast.Function> common = functions.get("MRSCOMMONCODE");
        if (common != null && common.containsKey(up)) return common.get(up);
        return functionsByName.get(up);
    }

    // ---- the old screens' own texts, for the clean services (nothing copied into Java) --------------------

    /** the text of a screen's message prompter (MessagePrompter messageString), e.g. VHIC30I Bad-Date-Msg */
    public String promptText(String screen, String prompter) {
        JsonNode def = screen(screen);
        if (def != null) {
            for (JsonNode v : def.path("vars")) {
                if (v.path("name").asText().equalsIgnoreCase(prompter)) return v.path("props").path("messageString").asText(null);
            }
            JsonNode w = def.path("widgets").path(prompter);
            if (w.has("props")) return w.path("props").path("messageString").asText(null);
        }
        throw new IllegalStateException("screen " + screen + " has no message prompter " + prompter);
    }

    /**
     * the text literal a statement group assigns to a field, e.g. VHIC30IS-BEGIN sets YRD-DESC[ICT] = "ALL YARDS".
     * Fails at start-up when the EGL no longer has it, instead of silently showing an old copy.
     */
    public String assignedText(String function, String pkg, String field) {
        Ast.Function f = function(function, pkg);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String v = findAssigned(f.body(), field.toUpperCase(Locale.ROOT));
        if (v == null) throw new IllegalStateException(function + " assigns no text to " + field);
        return v;
    }

    private static String findAssigned(List<Ast.Stmt> stmts, String field) {
        for (Ast.Stmt s : stmts) {
            String v = switch (s) {
                case Ast.Assign a when a.value() instanceof Ast.Str str
                        && a.target().segments().get(a.target().segments().size() - 1).name().equalsIgnoreCase(field) -> str.value();
                case Ast.If i -> { String x = findAssigned(i.then(), field); yield x != null ? x : findAssigned(i.otherwise(), field); }
                case Ast.While w -> findAssigned(w.body(), field);
                case Ast.Try t -> findAssigned(t.body(), field);
                case Ast.For fo -> findAssigned(fo.body(), field);
                default -> null;
            };
            if (v != null) return v;
        }
        return null;
    }

    public Map<String, Integer> stats() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("screens", screens.size());
        m.put("records", records.size());
        m.put("functions", functionsByName.size());
        return m;
    }
}
