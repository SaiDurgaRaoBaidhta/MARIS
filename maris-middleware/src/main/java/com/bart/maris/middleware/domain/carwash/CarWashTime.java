package com.bart.maris.middleware.domain.carwash;

/**
 * The car wash screens' time rule (VHCWAIS-VAL-TIME of VHCWA2I, VHCWA3IS-A-TIME of VHCWA3I): the typed text, 8
 * characters read right to left as POS8..POS1, becomes HH:MM:SS. A "." or ":" in the third place separates hours from
 * minutes, otherwise the first four characters are HHMM; blank seconds and a blank first hour digit become "0".
 */
final class CarWashTime {
    enum Error { NONE, HOURS, MINUTES, NOT_NUMERIC }

    /** @param time the HH:MM:SS the old screen stores, null when nothing was typed */
    record Parsed(String time, Error error) {}

    private CarWashTime() {}

    static Parsed parse(String typed) {
        String in = typed == null ? "" : typed;
        in = (in + "        ").substring(0, 8);
        if (in.isBlank()) return new Parsed(null, Error.NONE);
        char p8 = in.charAt(0), p7 = in.charAt(1), p6 = in.charAt(2), p5 = in.charAt(3), p4 = in.charAt(4), p2 = in.charAt(6), p1 = in.charAt(7);
        boolean separated = p6 == '.' || p6 == ':';
        char h1 = p8, h2 = p7, m1 = separated ? p5 : p6, m2 = separated ? p4 : p5, s1 = p2, s2 = p1;
        if (s1 == ' ') s1 = '0';
        if (s2 == ' ') s2 = '0';
        if (h1 == ' ') h1 = '0';
        String out = "" + h1 + h2 + ':' + m1 + m2 + ':' + s1 + s2;
        if (!digit(h1) || !digit(h2)) return new Parsed(out, Error.NOT_NUMERIC);
        if ((h1 - '0') * 10 + (h2 - '0') > 23) return new Parsed(out, Error.HOURS);
        if (!digit(m1) || !digit(m2)) return new Parsed(out, Error.NOT_NUMERIC);
        if ((m1 - '0') * 10 + (m2 - '0') > 59) return new Parsed(out, Error.MINUTES);
        return new Parsed(out, Error.NONE);
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
