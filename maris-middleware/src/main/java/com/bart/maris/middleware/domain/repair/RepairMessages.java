package com.bart.maris.middleware.domain.repair;

import java.util.List;

/** Small rules the repair screens share, written once here. */
final class RepairMessages {
    private RepairMessages() {}

    /**
     * VHRV01IS-MOVE-MSG / VHRV02IS-MOVE-MSG / VHRV03IS-MOVE-MSG: the messages of up to 6 checks become one line. Each
     * message (a 40-character slot) is copied up to its first ".", which becomes ", "; the last "," is then blanked.
     */
    static String join(List<String> messages) {
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < Math.min(6, messages.size()); k++) {
            String m = String.format("%-40s", messages.get(k));
            if (m.isBlank()) break;
            for (int i = 0; i < 40; i++) {
                char c = m.charAt(i);
                if (c != '.') out.append(c);
                else { out.append(", "); break; }
            }
        }
        if (out.length() >= 2) out.setCharAt(out.length() - 2, ' ');
        String s = out.length() > 240 ? out.substring(0, 240) : out.toString();
        return s.strip();
    }

    /**
     * the value of one character read as a one-digit zoned number (NUMC(1)), the way the old screens read it: the
     * character code mod 16, negative when the code is outside '0'..'?' (e.g. "A" is -1, a blank or "P" is 0).
     */
    static int zonedDigit(char c) {
        int v = c % 16;
        return c < 48 || c > 63 ? -v : v;
    }

    /** a zoned number read from text the same way (blank counts 0), e.g. the "MM" of a typed date */
    static int zoned(String s) {
        int v = 0;
        for (int i = 0; i < s.length(); i++) v = v * 10 + s.charAt(i) % 16;
        char last = s.isEmpty() ? '0' : s.charAt(s.length() - 1);
        return last < 48 || last > 63 ? -v : v;
    }

    static String fit(String s, int len) {
        String v = s == null ? "" : s;
        return v.length() > len ? v.substring(0, len) : v;
    }

    static String pad(String s, int len) {
        return String.format("%-" + len + "s", fit(s, len));
    }
}
