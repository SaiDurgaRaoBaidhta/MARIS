package com.bart.maris.tools;

import java.util.ArrayList;
import java.util.List;

/** Small text helpers for EGL source: comment removal and statement splitting. */
final class EglText {
    private EglText() {}

    /** Removes // and block comments, leaving string literals intact. Line breaks are kept. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        boolean inString = false;
        while (i < n) {
            char c = s.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '"') inString = false;
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
                i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                String skipped = s.substring(i, end < 0 ? n : end + 2);
                skipped.chars().filter(ch -> ch == '\n').forEach(ch -> out.append('\n'));
                i = end < 0 ? n : end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Splits on ';' that are outside braces and string literals. Each result is whitespace-normalised. */
    static List<String> statements(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{') depth++;
                if (c == '}') depth--;
                if (c == ';' && depth == 0) {
                    add(out, cur);
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        add(out, cur);
        return out;
    }

    private static void add(List<String> out, StringBuilder cur) {
        String t = cur.toString().trim().replaceAll("\\s+", " ");
        if (!t.isEmpty()) out.add(t);
    }

    /** Index of the next standalone keyword (not part of a hyphenated or longer identifier), outside strings. */
    static int indexOfKeyword(String s, String keyword, int from) {
        boolean inString = false;
        int k = keyword.length();
        for (int i = from; i <= s.length() - k; i++) {
            char c = s.charAt(i);
            if (c == '"') inString = !inString;
            if (inString) continue;
            if (s.regionMatches(true, i, keyword, 0, k)
                    && (i == 0 || !isIdentChar(s.charAt(i - 1)))
                    && (i + k == s.length() || !isIdentChar(s.charAt(i + k)))) {
                return i;
            }
        }
        return -1;
    }

    static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '#' || c == '@' || c == '$';
    }
}
