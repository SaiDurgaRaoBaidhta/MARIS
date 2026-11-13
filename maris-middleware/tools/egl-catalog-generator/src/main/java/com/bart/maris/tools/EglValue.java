package com.bart.maris.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the small literal language of EGL part declarations and connection calls into plain JSON values:
 * strings stay strings, numbers become Long/Double, booleans stay booleans, identifiers become {"id": "a.b"},
 * arrays become lists, {@code x = v} property blocks become maps, and calls become {"call": name, "args": [...]}.
 * {@code new Type{...}} values are skipped (layout data the middleware does not need).
 */
final class EglValue {
    private final String s;
    private int i;

    private EglValue(String s) { this.s = s; }

    static Object parse(String text) {
        EglValue p = new EglValue(text);
        Object v = p.value();
        p.ws();
        return v;
    }

    /** Parses "a, b, c" (the inside of a call's parentheses). */
    static List<Object> args(String text) {
        EglValue p = new EglValue(text);
        List<Object> out = new ArrayList<>();
        p.ws();
        while (p.i < p.s.length()) {
            out.add(p.value());
            p.ws();
            if (p.i < p.s.length() && p.s.charAt(p.i) == ',') { p.i++; p.ws(); }
            else break;
        }
        return out;
    }

    /** Parses "{ a = 1, b = "x" }" into a map. */
    static Map<String, Object> props(String text) {
        EglValue p = new EglValue(text);
        p.ws();
        return p.i < p.s.length() && p.s.charAt(p.i) == '{' ? p.block() : Map.of();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '"') return string();
        if (c == '[') return array();
        if (c == '{') return block();
        if (c == '-' || c == '+' || Character.isDigit(c)) {
            int st = i;
            i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            String num = s.substring(st, i);
            if (num.equals("-") || num.equals("+")) return null;
            return num.contains(".") ? (Object) Double.parseDouble(num) : (Object) Long.parseLong(num);
        }
        String id = ident();
        if (id.isEmpty()) { i++; return null; }
        if (id.equals("true") || id.equals("false")) return Boolean.parseBoolean(id);
        if (id.equals("new")) {                      // new Type{...}: skipped
            ident();
            ws();
            if (i < s.length() && s.charAt(i) == '{') block();
            return null;
        }
        ws();
        if (i < s.length() && s.charAt(i) == '(') {
            int close = matching(i, '(', ')');
            List<Object> a = args(s.substring(i + 1, close));
            i = close + 1;
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("call", id);
            call.put("args", a);
            return call;
        }
        return Map.of("id", id);
    }

    private String ident() {
        int st = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '#' || c == '@' || c == '$') i++;
            else break;
        }
        return s.substring(st, i);
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                sb.append(switch (n) { case 'n' -> '\n'; case 't' -> '\t'; default -> n; });
            } else sb.append(c);
        }
        i++;
        return sb.toString();
    }

    private List<Object> array() {
        int close = matching(i, '[', ']');
        List<Object> out = args(s.substring(i + 1, close));
        i = close + 1;
        return out;
    }

    private Map<String, Object> block() {
        int close = matching(i, '{', '}');
        String inner = s.substring(i + 1, close);
        i = close + 1;
        Map<String, Object> out = new LinkedHashMap<>();
        for (String part : splitTop(inner)) {
            int eq = topEquals(part);
            if (eq < 0) continue;
            String k = part.substring(0, eq).strip();
            Object v = parse(part.substring(eq + 1));
            if (v != null) out.put(k, v);
        }
        return out;
    }

    private static int topEquals(String part) {
        boolean str = false;
        for (int k = 0; k < part.length(); k++) {
            char c = part.charAt(k);
            if (c == '"') str = !str;
            if (!str && c == '=') return k;
            if (!str && (c == '{' || c == '[' || c == '(')) return -1;
        }
        return -1;
    }

    /** splits on commas outside brackets and strings */
    static List<String> splitTop(String t) {
        List<String> out = new ArrayList<>();
        int depth = 0, st = 0;
        boolean str = false;
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            if (c == '"' && (k == 0 || t.charAt(k - 1) != '\\')) str = !str;
            if (str) continue;
            if (c == '{' || c == '[' || c == '(') depth++;
            if (c == '}' || c == ']' || c == ')') depth--;
            if (c == ',' && depth == 0) { out.add(t.substring(st, k)); st = k + 1; }
        }
        if (st < t.length()) out.add(t.substring(st));
        return out;
    }

    private int matching(int open, char o, char cl) {
        int depth = 0;
        boolean str = false;
        for (int k = open; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '"' && s.charAt(k - 1) != '\\') str = !str;
            if (str) continue;
            if (c == '{' || c == '[' || c == '(') depth++;
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                if (depth == 0) return k;
            }
        }
        return s.length() - 1;
    }
}
