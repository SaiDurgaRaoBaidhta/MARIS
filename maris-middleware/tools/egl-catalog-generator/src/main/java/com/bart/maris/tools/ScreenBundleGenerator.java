package com.bart.maris.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the old screens' EGL source (read-only) and writes the bundle the middleware's screen engine runs:
 * <ul>
 *   <li>records.json - every basicRecord (working storage, triggers, pass values, program records) with its byte layout</li>
 *   <li>screens.json - per screen: its handler variables, its connections (initConnections) and its widgets</li>
 *   <li>functions/*.egl - the statement groups (RUIFunctions.egl, CommonFunctions.egl), comments removed</li>
 * </ul>
 * Usage: ScreenBundleGenerator &lt;MarisProd-1 root&gt; &lt;output dir&gt;
 */
public final class ScreenBundleGenerator {
    private static final List<String> RECORD_PROJECTS = List.of("MARIS_Common", "MARIS_RUI", "MARIS_RUI_Services",
            "com.sync.gt2.egl.rui");
    private static final Pattern HANDLER = Pattern.compile("\\bHandler\\s+(\\S+)\\s+type\\s+(\\w+)");
    private static final Pattern DECL = Pattern.compile("^([A-Za-z0-9_#@$-]+)\\s+([A-Za-z0-9_.#@$-]+)\\s*(\\{.*\\})?$", Pattern.DOTALL);
    private static final Pattern CM_CALL = Pattern.compile("^cm\\.(\\w+)\\s*\\((.*)\\)$", Pattern.DOTALL);
    private static final Pattern DATATABLE = Pattern.compile("\\bDataTable\\s+(\\S+)\\s+type\\s+\\w+");
    private static final Pattern TABLE_FIELD = Pattern.compile("^\\d+\\s+(\\S+)\\s+(\\w+)\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)$");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.#@$-]+)\\s*;");
    private static final Pattern TOP_FUNCTION = Pattern.compile("(?m)^\\s*Function\\s+", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]), out = Path.of(args[1]);
        Files.createDirectories(out.resolve("functions"));
        ObjectMapper om = new ObjectMapper();
        StringBuilder rep = new StringBuilder("# Screen bundle report\n\nGenerated " + OffsetDateTime.now() + " from the EGL source (read-only).\n\n");

        // 1. records
        Map<String, RecordDef> records = new TreeMap<>();
        int notFixed = 0;
        for (String project : RECORD_PROJECTS) {
            for (Path f : eglFiles(root.resolve(project).resolve("EGLSource"))) {
                for (RecordDef rd : RecordParser.parse(read(f), root.relativize(f).toString())) {
                    if (records.putIfAbsent(rd.qualifiedName(), rd) == null && !rd.fixed) notFixed++;
                }
            }
        }
        om.writeValue(out.resolve("records.json").toFile(), records);
        rep.append("- records: ").append(records.size()).append(" (not fixed-layout: ").append(notFixed).append(")\n");

        // 1b. data tables (message texts etc.): columns and rows
        Map<String, Object> tables = new TreeMap<>();
        for (String project : List.of("MARIS_Common", "MARIS_RUI")) {
            for (Path f : eglFiles(root.resolve(project).resolve("EGLSource"))) {
                String src = EglText.stripComments(read(f));
                Matcher t = DATATABLE.matcher(src);
                while (t.find()) {
                    int end = EglText.indexOfKeyword(src, "end", t.end());
                    String body = src.substring(t.end(), end < 0 ? src.length() : end);
                    int c = body.indexOf("contents");
                    if (c < 0) continue;
                    int open = body.lastIndexOf('{', c);
                    String fieldsPart = body.substring(0, open);
                    int propsEnd = fieldsPart.indexOf('}');
                    if (fieldsPart.strip().startsWith("{") && propsEnd >= 0) fieldsPart = fieldsPart.substring(propsEnd + 1);
                    List<Map<String, Object>> cols = new ArrayList<>();
                    for (String st : EglText.statements(fieldsPart)) {
                        Matcher fm = TABLE_FIELD.matcher(st);
                        if (fm.matches()) cols.add(Map.of("name", fm.group(1), "type", fm.group(2).toUpperCase(java.util.Locale.ROOT),
                                "length", Integer.parseInt(fm.group(3)), "decimals", fm.group(4) == null ? 0 : Integer.parseInt(fm.group(4))));
                    }
                    Object props = EglValue.props(body.substring(open));
                    Object rows = props instanceof Map<?, ?> m ? m.get("contents") : null;
                    tables.put(RecordParser.packageOf(src) + "." + t.group(1), Map.of("columns", cols, "rows", rows == null ? List.of() : rows));
                }
            }
        }
        om.writeValue(out.resolve("tables.json").toFile(), tables);
        rep.append("- data tables: ").append(tables.size()).append("\n");

        // 2. functions
        int fnFiles = 0;
        for (String project : List.of("MARIS_Common", "MARIS_RUI")) {
            for (Path f : eglFiles(root.resolve(project).resolve("EGLSource"))) {
                String name = f.getFileName().toString();
                if (!name.equals("RUIFunctions.egl") && !name.equals("CommonFunctions.egl")) continue;
                String src = EglText.stripComments(read(f));
                if (!TOP_FUNCTION.matcher(src).find()) continue;
                String pkg = RecordParser.packageOf(src);
                Files.writeString(out.resolve("functions").resolve(pkg + "." + name), src, StandardCharsets.ISO_8859_1);
                fnFiles++;
            }
        }
        rep.append("- function files: ").append(fnFiles).append("\n");

        // 3. screens
        Map<String, Object> screens = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        Path ruiSrc = root.resolve("MARIS_RUI/EGLSource");
        for (Path f : eglFiles(ruiSrc)) {
            if (!f.getFileName().toString().endsWith("Connections.egl")) continue;
            String src = EglText.stripComments(read(f));
            Map<String, Object> screen = parseConnections(src, problems, f.getFileName().toString());
            if (screen == null) continue;
            String viewType = (String) screen.get("view");
            if (viewType != null) {
                Path view = f.getParent().resolve(viewType + ".egl");
                if (Files.exists(view)) screen.put("widgets", parseView(EglText.stripComments(read(view))));
                else problems.add(f.getFileName() + ": view file " + viewType + ".egl not found");
            }
            screens.put((String) screen.get("name"), screen);
        }
        om.writeValue(out.resolve("screens.json").toFile(), screens);
        rep.append("- screens: ").append(screens.size()).append("\n\n");
        if (!problems.isEmpty()) rep.append("## Problems\n\n").append(String.join("\n", problems.stream().map(p -> "- " + p).toList())).append("\n");
        Files.writeString(out.resolve("REPORT.md"), rep.toString());
        System.out.println(rep);
    }

    static Map<String, Object> parseConnections(String src, List<String> problems, String file) {
        Matcher h = HANDLER.matcher(src);
        if (!h.find()) return null;
        Map<String, Object> screen = new LinkedHashMap<>();
        String handler = h.group(1);
        screen.put("handler", handler);
        screen.put("pkg", RecordParser.packageOf(src));
        List<String> imports = new ArrayList<>();
        Matcher im = IMPORT.matcher(src);
        while (im.find()) if (!im.group(1).startsWith("com.sync")) imports.add(im.group(1));
        screen.put("imports", imports);
        int bodyStart = src.indexOf('}', h.end()) + 1;                        // after the handler's property block
        int firstFn = EglText.indexOfKeyword(src, "Function", bodyStart);
        List<Map<String, Object>> vars = new ArrayList<>();
        for (String st : EglText.statements(src.substring(bodyStart, firstFn < 0 ? src.length() : firstFn))) {
            if (st.startsWith("use ")) continue;
            Matcher d = DECL.matcher(st);
            if (!d.matches()) { problems.add(file + ": declaration not understood: " + st); continue; }
            if (d.group(1).equals("screen")) { screen.put("view", d.group(2)); continue; }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", d.group(1));
            v.put("type", d.group(2));
            if (d.group(3) != null) {
                Map<String, Object> props = EglValue.props(d.group(3));
                if (!props.isEmpty()) v.put("props", props);
            }
            vars.add(v);
        }
        screen.put("vars", vars);
        String name = handler.endsWith("Connections") ? handler.substring(0, handler.length() - "Connections".length()) : handler;
        screen.put("name", screen.getOrDefault("view", name));

        int ic = EglText.indexOfKeyword(src, "initConnections", bodyStart);
        List<Map<String, Object>> conns = new ArrayList<>();
        if (ic > 0) {
            int open = src.indexOf(')', ic) + 1;
            int end = lastEnd(src, open);
            for (String st : EglText.statements(src.substring(open, end))) {
                Matcher m = CM_CALL.matcher(st);
                if (!m.matches()) continue;                                  // ReflectionHelper etc.
                String method = m.group(1);
                if (method.equals("setup")) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("m", method);
                c.put("a", EglValue.args(m.group(2)));
                conns.add(c);
            }
        }
        screen.put("connections", conns);
        return screen;
    }

    /** The end of initConnections: the "end" that closes the function (the last but one "end" in the file). */
    private static int lastEnd(String src, int from) {
        int handlerEnd = src.lastIndexOf("end");
        int fnEnd = src.lastIndexOf("end", handlerEnd - 1);
        return fnEnd > from ? fnEnd : src.length();
    }

    /** widgets of an RUIHandler view: name -> {type, props} (layout data skipped) */
    static Map<String, Object> parseView(String src) {
        Map<String, Object> widgets = new LinkedHashMap<>();
        Matcher h = HANDLER.matcher(src);
        if (!h.find()) return widgets;
        int bodyStart = src.indexOf('}', h.end()) + 1;
        int firstFn = EglText.indexOfKeyword(src, "Function", bodyStart);
        for (String st : EglText.statements(src.substring(bodyStart, firstFn < 0 ? src.length() : firstFn))) {
            Matcher d = DECL.matcher(st);
            if (!d.matches()) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", d.group(2));
            if (d.group(3) != null) {
                Map<String, Object> props = new LinkedHashMap<>(EglValue.props(d.group(3)));
                props.remove("position");
                props.remove("layoutData");
                if (!props.isEmpty()) w.put("props", props);
            }
            widgets.put(d.group(1), w);
        }
        return widgets;
    }

    private static List<Path> eglFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".egl")).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
    }
}
