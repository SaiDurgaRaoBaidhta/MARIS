package com.bart.maris.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the basicRecord definitions of one EGL source file. Reads only. */
final class RecordParser {
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern RECORD = Pattern.compile("\\bRecord\\s+(\\S+)\\s+type\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile(
            "^(\\d+)\\s+(\\*|[^\\s(\\[{]+)(?:\\s+([A-Za-z]+)\\s*(?:\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?)?\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*(\\{.*\\})?$",
            Pattern.DOTALL);
    private static final Set<String> PRIMITIVES = Set.of("CHAR", "MBCHAR", "DBCHAR", "UNICODE", "HEX", "NUM", "NUMC",
            "DECIMAL", "PACF", "SMALLINT", "INT", "BIGINT", "BIN");

    static String packageOf(String source) {
        Matcher m = PACKAGE.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    static List<RecordDef> parse(String rawSource, String file) {
        String src = EglText.stripComments(rawSource);
        String pkg = packageOf(src);
        List<RecordDef> out = new ArrayList<>();
        Matcher m = RECORD.matcher(src);
        int searchFrom = 0;
        while (m.find(searchFrom)) {
            int bodyStart = m.end();
            int end = EglText.indexOfKeyword(src, "end", bodyStart);
            if (end < 0) break;
            searchFrom = end + 3;
            if (!m.group(2).equalsIgnoreCase("basicRecord")) continue;
            RecordDef r = new RecordDef();
            r.name = m.group(1);
            r.pkg = pkg;
            r.file = file;
            build(r, src.substring(bodyStart, end));
            out.add(r);
        }
        return out;
    }

    private static void build(RecordDef r, String body) {
        String b = body.strip();
        if (b.startsWith("{")) {                       // record-level properties block
            int depth = 0, i = 0;
            for (; i < b.length(); i++) {
                if (b.charAt(i) == '{') depth++;
                if (b.charAt(i) == '}' && --depth == 0) break;
            }
            b = b.substring(Math.min(i + 1, b.length()));
        }
        Field root = new Field();
        root.level = 0;
        Deque<Field> stack = new ArrayDeque<>();
        stack.push(root);
        for (String st : EglText.statements(b)) {
            Matcher f = FIELD.matcher(st);
            if (!f.matches()) {
                r.fixed = false;                       // e.g. "errorCode string" - not a fixed-layout record
                r.problems.add("not a fixed-layout field: " + st);
                continue;
            }
            Field fd = new Field();
            fd.level = Integer.parseInt(f.group(1));
            fd.name = f.group(2);
            fd.filler = "*".equals(fd.name);
            if (f.group(3) != null) {
                fd.type = f.group(3).toUpperCase(Locale.ROOT);
                if (!PRIMITIVES.contains(fd.type)) {
                    r.fixed = false;
                    r.problems.add("unsupported type " + fd.type + " in: " + st);
                    continue;
                }
                fd.length = f.group(4) != null ? Integer.parseInt(f.group(4)) : Field.defaultLength(fd.type);
                fd.decimals = f.group(5) != null ? Integer.parseInt(f.group(5)) : 0;
                fd.bytes = Field.bytesFor(fd.type, fd.length);
                if (fd.bytes <= 0) {
                    r.fixed = false;
                    r.problems.add("cannot size: " + st);
                }
            }
            fd.occurs = f.group(6) != null ? Integer.parseInt(f.group(6)) : 1;
            while (stack.peek() != root && stack.peek().level >= fd.level) stack.pop();
            stack.peek().children.add(fd);
            stack.push(fd);
        }
        r.length = layout(root, r, "");
        r.fields = root.children;
        assignJsonNames(r.fields);
    }

    /** Assigns offsets depth-first and returns the bytes of one element of {@code node}. */
    private static int layout(Field node, RecordDef r, String path) {
        int cursor = 0;
        for (Field c : node.children) {
            c.offset = cursor;
            int elem = layout(c, r, path.isEmpty() ? c.name : path + "." + c.name);
            cursor += elem * c.occurs;
        }
        if (node.type == null) {                       // untyped group: size is its children
            node.bytes = cursor;
            if (node.level > 0) node.type = "GROUP";
        } else if (cursor > node.bytes) {
            r.problems.add("children of " + path + " use " + cursor + " bytes but the field has " + node.bytes);
        }
        return node.bytes;
    }

    /** camelCase JSON names, unique among siblings. */
    static void assignJsonNames(List<Field> fields) {
        Set<String> used = new HashSet<>();
        for (Field f : fields) {
            if (f.filler) continue;
            String base = camel(f.name);
            String n = base;
            for (int i = 2; !used.add(n); i++) n = base + i;
            f.json = n;
            assignJsonNames(f.children);
        }
    }

    static String camel(String eglName) {
        String[] parts = eglName.split("[-_#@$]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            sb.append(sb.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        String s = sb.toString();
        return s.isEmpty() || !Character.isLetter(s.charAt(0)) ? "f" + s : s;
    }
}
