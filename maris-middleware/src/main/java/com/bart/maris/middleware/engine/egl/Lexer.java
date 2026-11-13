package com.bart.maris.middleware.engine.egl;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokens of EGL statement groups (comments are already removed by the bundle generator).
 * Identifiers may contain '-', '#', '@', '$' as in the VisualAge Generator names (VHIC31IW-TRIGGERS);
 * a '-' belongs to an identifier when it joins two identifier characters without spaces.
 */
public final class Lexer {
    public enum Kind { IDENT, STRING, NUMBER, OP, EOF }

    public record Token(Kind kind, String text, int line) {
        public boolean is(String s) { return (kind == Kind.OP || kind == Kind.IDENT) && text.equalsIgnoreCase(s); }
    }

    private Lexer() {}

    public static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0, n = s.length(), line = 1;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && s.charAt(i) != '"') {
                    char d = s.charAt(i++);
                    if (d == '\\' && i < n) {
                        char e = s.charAt(i++);
                        sb.append(switch (e) { case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r'; default -> e; });
                    } else {
                        if (d == '\n') line++;
                        sb.append(d);
                    }
                }
                i++;
                out.add(new Token(Kind.STRING, sb.toString(), line));
                continue;
            }
            boolean dotNumber = c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))
                    && (out.isEmpty() || out.get(out.size() - 1).kind() == Kind.OP && !out.get(out.size() - 1).text().equals("]")
                        && !out.get(out.size() - 1).text().equals(")"));
            if (dotNumber) {                                     // .5
                int st = i++;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                out.add(new Token(Kind.NUMBER, "0" + s.substring(st, i), line));
                continue;
            }
            if (Character.isDigit(c)) {
                int st = i;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                if (i + 1 < n && s.charAt(i) == '.' && Character.isDigit(s.charAt(i + 1))) {
                    i++;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                }
                // a name may start with digits in VAGen (e.g. 1ST-CAR): treat letters following digits as an identifier
                if (i < n && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '_')) {
                    while (i < n && identChar(s, i)) i++;
                    out.add(new Token(Kind.IDENT, s.substring(st, i), line));
                } else {
                    out.add(new Token(Kind.NUMBER, s.substring(st, i), line));
                }
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '#' || c == '@' || c == '$') {
                int st = i;
                while (i < n && identChar(s, i)) i++;
                out.add(new Token(Kind.IDENT, s.substring(st, i), line));
                continue;
            }
            String two = i + 1 < n ? s.substring(i, i + 2) : "";
            if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=") || two.equals("&&")
                    || two.equals("||") || two.equals("::")) {
                out.add(new Token(Kind.OP, two, line));
                i += 2;
                continue;
            }
            out.add(new Token(Kind.OP, String.valueOf(c), line));
            i++;
        }
        out.add(new Token(Kind.EOF, "", line));
        return out;
    }

    private static boolean identChar(String s, int i) {
        char c = s.charAt(i);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '#' || c == '@' || c == '$') return true;
        // hyphen inside a name: previous is an identifier char and next is a letter or digit
        if (c != '-' || i == 0 || !(Character.isLetterOrDigit(s.charAt(i - 1)) || s.charAt(i - 1) == '_')) return false;
        if (i + 1 < s.length() && Character.isLetterOrDigit(s.charAt(i + 1))) return true;
        // a name ending in '-' (DISC-MSG-2- = "."): only when an assignment follows
        int j = i + 1;
        while (j < s.length() && s.charAt(j) == ' ') j++;
        return j + 1 < s.length() && s.charAt(j) == '=' && s.charAt(j + 1) != '=';
    }
}
