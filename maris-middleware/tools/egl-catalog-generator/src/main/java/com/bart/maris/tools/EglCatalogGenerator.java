package com.bart.maris.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the MARIS EGL source (read-only) and writes the middleware's legacy catalog:
 * services.json (service -> method -> ordered record parameters) and records.json (fixed byte layouts).
 *
 * <p>Usage: EglCatalogGenerator &lt;MarisProd-1 root&gt; &lt;output dir&gt;
 *
 * <p>Every generated layout is cross-checked against the field signatures that EGL generated for the browser
 * (MARISWeb/WebContent/&lt;package&gt;/&lt;RECORD&gt;.js), so the middleware sees records exactly as the old UI does.
 */
public final class EglCatalogGenerator {
    /** Projects whose records may be service parameters, in order of preference for duplicate names. */
    private static final List<String> RECORD_PROJECTS = List.of(
            "MARIS_Common", "MARIS_RUI_Services", "com.sync.gt2.egl.rui", "com.sync.gt2.egl.server", "MARIS_Server");
    private static final java.util.Set<String> PRIMITIVE_PARAMS = java.util.Set.of("string", "int", "smallint", "bigint", "boolean");
    private static final Pattern JS_SIGNATURE = Pattern.compile("\\{name:\\s*\"([^\"]+)\",\\s*value\\s*:\\s*\"([^\"]+)\"\\}");
    private static final Pattern MESSAGE_ROW = Pattern.compile("\\[\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"\\s*\\]");
    private static final Pattern JS_TYPE = Pattern.compile("([A-Z]+)(?:\\((\\d+)(?:,(\\d+))?\\))?(?:\\[(\\d+)\\])?");

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: EglCatalogGenerator <MarisProd-1 root> <output dir>");
            System.exit(2);
        }
        Result r = generate(Path.of(args[0]), Path.of(args[1]));
        System.out.println(r.summary());
        if (!r.ok()) System.exit(1);
    }

    record Result(int services, int records, int checked, List<String> errors, List<String> warnings) {
        boolean ok() { return errors.isEmpty(); }
        String summary() {
            return "services=" + services + " records=" + records + " cross-checked=" + checked
                    + " errors=" + errors.size() + " warnings=" + warnings.size();
        }
    }

    static Result generate(Path root, Path out) throws IOException {
        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();

        // 1. Index every basicRecord by qualified name.
        Map<String, RecordDef> index = new HashMap<>();
        Map<String, List<String>> byName = new HashMap<>();
        for (String project : RECORD_PROJECTS) {
            Path src = root.resolve(project).resolve("EGLSource");
            if (!Files.isDirectory(src)) continue;
            for (Path f : eglFiles(src)) {
                for (RecordDef rd : RecordParser.parse(read(f), root.relativize(f).toString())) {
                    if (index.putIfAbsent(rd.qualifiedName(), rd) == null) {
                        byName.computeIfAbsent(rd.name, k -> new ArrayList<>()).add(rd.qualifiedName());
                    }
                }
            }
        }

        // 2. Parse the service wrappers and resolve their parameter types.
        List<ServiceDef> services = new ArrayList<>();
        Map<String, RecordDef> used = new TreeMap<>();
        for (Path f : eglFiles(root.resolve("MARIS_RUI_Services/EGLSource"))) {
            if (!f.getFileName().toString().endsWith("Service.egl")) continue;
            ServiceParser.Parsed p = ServiceParser.parse(read(f));
            if (p.service() == null) continue;
            for (ServiceParser.Function fn : p.functions()) {
                ServiceDef sd = new ServiceDef();
                sd.service = p.service();
                sd.function = fn.name();
                sd.program = fn.program();
                sd.pkg = p.pkg();
                boolean ok = true;
                for (String[] nt : fn.params()) {          // EGL parameter syntax is "name Type"
                    String name = nt[0], type = nt[1];
                    if (type.equals("PROGRAM_RETURN_REC")) {
                        sd.params.add(new ServiceDef.Param(name, "PROGRAM_RETURN", null));
                        continue;
                    }
                    if (PRIMITIVE_PARAMS.contains(type.toLowerCase(Locale.ROOT))) {
                        ServiceDef.Param prim = new ServiceDef.Param(name, "PRIMITIVE", null);
                        prim.primitiveType = type.toUpperCase(Locale.ROOT);
                        sd.params.add(prim);
                        continue;
                    }
                    String q = ServiceParser.resolve(type, p, index, byName);
                    RecordDef rd = q == null ? null : index.get(q);
                    if (rd == null) {
                        errors.add(sd.service + "." + sd.function + ": cannot resolve record type " + type);
                        ok = false;
                    } else if (!rd.fixed) {
                        errors.add(sd.service + "." + sd.function + ": record " + q + " is not fixed-layout " + rd.problems);
                        ok = false;
                    } else {
                        sd.params.add(new ServiceDef.Param(name, "RECORD", q));
                        used.put(q, rd);
                    }
                }
                if (sd.program == null) warnings.add(sd.service + "." + sd.function + ": no call statement (service does not run a program)");
                if (ok) services.add(sd);
            }
        }
        services.sort(Comparator.comparing((ServiceDef s) -> s.service).thenComparing(s -> s.function));
        for (RecordDef rd : used.values()) rd.problems.forEach(pr -> warnings.add(rd.qualifiedName() + ": " + pr));

        // 3. Cross-check against the browser's generated record signatures.
        int checked = 0;
        Path web = root.resolve("MARISWeb/WebContent");
        for (RecordDef rd : used.values()) {
            Path js = web.resolve(rd.pkg.toLowerCase(Locale.ROOT).replace('.', '/')).resolve(jsName(rd.name) + ".js");
            if (!Files.exists(js)) {
                warnings.add(rd.qualifiedName() + ": no browser record file to cross-check (" + web.relativize(js) + ")");
                continue;
            }
            checked++;
            Map<String, String> expected = new LinkedHashMap<>();
            Matcher m = JS_SIGNATURE.matcher(read(js));
            while (m.find()) expected.put(m.group(1), normalise(m.group(2)));
            Map<String, String> actual = new LinkedHashMap<>();
            flatten(rd.fields, "", actual);
            if (!expected.equals(actual)) {
                errors.add(rd.qualifiedName() + ": layout differs from browser record " + web.relativize(js)
                        + "\n    browser: " + expected + "\n    ours:    " + actual);
            }
        }

        // 4. Message texts (UMSGCODE -> type + text with ~1..~3 inserts), from the XXM0IT1 data table.
        Map<String, Map<String, String>> messages = new TreeMap<>();
        Path msgTable = root.resolve("MARIS_Common/EGLSource/MRSCommonCode/XXM0IT1.egl");
        if (Files.exists(msgTable)) {
            Matcher mm = MESSAGE_ROW.matcher(read(msgTable));
            while (mm.find()) messages.put(mm.group(1).strip(), Map.of("type", mm.group(2).strip(), "text", mm.group(3).strip()));
        } else {
            warnings.add("message table XXM0IT1.egl not found; messages.json will be empty");
        }

        // 5. Write the catalog.
        Files.createDirectories(out);
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        om.writeValue(out.resolve("services.json").toFile(), services);
        om.writeValue(out.resolve("records.json").toFile(), used);
        om.writeValue(out.resolve("messages.json").toFile(), messages);
        StringBuilder rep = new StringBuilder()
                .append("# Legacy catalog report\n\nGenerated ").append(OffsetDateTime.now()).append(" from the EGL source (read-only).\n\n")
                .append("| Item | Count |\n|---|---|\n")
                .append("| Services (methods) | ").append(services.size()).append(" |\n")
                .append("| Records used by services | ").append(used.size()).append(" |\n")
                .append("| Records cross-checked against browser JS | ").append(checked).append(" |\n")
                .append("| Message texts | ").append(messages.size()).append(" |\n")
                .append("| Errors | ").append(errors.size()).append(" |\n")
                .append("| Warnings | ").append(warnings.size()).append(" |\n\n");
        if (!errors.isEmpty()) rep.append("## Errors\n\n").append(String.join("\n", errors.stream().map(e -> "- " + e).toList())).append("\n\n");
        if (!warnings.isEmpty()) rep.append("## Warnings\n\n").append(String.join("\n", warnings.stream().map(e -> "- " + e).toList())).append("\n");
        Files.writeString(out.resolve("REPORT.md"), rep.toString(), StandardCharsets.UTF_8);
        return new Result(services.size(), used.size(), checked, errors, warnings);
    }

    /** EGL's JavaScript file name for a record: characters other than letters, digits and _ become _00xx. */
    static String jsName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
            else sb.append(String.format("_%04x", (int) c));
        }
        return sb.toString();
    }

    private static void flatten(List<Field> fields, String prefix, Map<String, String> out) {
        for (Field f : fields) {
            String path = prefix + f.name;
            out.put(path, normalise(signature(f)));   // the browser lists fillers too, named "*"
            flatten(f.children, path + "__", out);
        }
    }

    private static String signature(Field f) {
        String t = switch (f.type) {
            case "SMALLINT", "INT", "BIGINT" -> f.type;
            case "CHAR", "MBCHAR", "DBCHAR", "UNICODE", "HEX" -> f.type + "(" + f.length + ")";
            default -> f.type + "(" + f.length + "," + f.decimals + ")";
        };
        return f.occurs > 1 ? t + "[" + f.occurs + "]" : t;
    }

    /** Normalises a type signature, e.g. NUM(3) and NUM(3,0) compare equal. */
    static String normalise(String sig) {
        Matcher m = JS_TYPE.matcher(sig.replace(" ", "").toUpperCase(Locale.ROOT));
        if (!m.matches()) return sig;
        String type = m.group(1);
        StringBuilder sb = new StringBuilder(type);
        if (m.group(2) != null) {
            sb.append('(').append(m.group(2));
            boolean numeric = !(type.equals("CHAR") || type.equals("MBCHAR") || type.equals("DBCHAR")
                    || type.equals("UNICODE") || type.equals("HEX"));
            if (numeric) sb.append(',').append(m.group(3) == null ? "0" : m.group(3));
            sb.append(')');
        }
        if (m.group(4) != null && !m.group(4).equals("1")) sb.append('[').append(m.group(4)).append(']');
        return sb.toString();
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
