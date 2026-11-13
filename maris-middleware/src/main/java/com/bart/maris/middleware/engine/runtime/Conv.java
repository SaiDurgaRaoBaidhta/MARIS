package com.bart.maris.middleware.engine.runtime;

import com.bart.maris.middleware.legacy.codec.FieldCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.FieldType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * EGL value semantics on record bytes: what an assignment stores and what a reference reads.
 * Text is ISO-8859-1, padded with blanks and cut to the field length; numbers lose excess decimals and
 * high-order digits (as VisualAge Generator truncates); text moved into a number is read as a number.
 */
public final class Conv {
    private Conv() {}

    // ---- reading -------------------------------------------------------------------------------------------

    /** the value of one element: String for text/groups/hex, BigDecimal for numbers */
    public static Object read(FieldLayout f, byte[] buf, int pos) {
        if (f.type() == FieldType.CHAR || f.type() == FieldType.MBCHAR || f.type() == FieldType.GROUP || f.isGroup() && !f.type().isPacked()
                && !f.type().isZoned() && !f.type().isBinary()) {
            return text(buf, pos, f.bytes());
        }
        if (f.type() == FieldType.HEX) return FieldCodec.read(f, buf, pos);
        try {
            Object v = FieldCodec.read(f, buf, pos);
            return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }

    /** the raw characters of an element (a group or a zoned number read as text), not trimmed */
    public static String text(byte[] buf, int pos, int len) {
        return new String(buf, pos, Math.max(0, Math.min(len, buf.length - pos)), StandardCharsets.ISO_8859_1);
    }

    // ---- writing -------------------------------------------------------------------------------------------

    public static void write(FieldLayout f, Object value, byte[] buf, int pos) {
        if (value instanceof Boolean b) value = b ? BigDecimal.ONE : BigDecimal.ZERO;
        FieldType t = f.type();
        if (t == FieldType.CHAR || t == FieldType.MBCHAR || t == FieldType.GROUP) {
            writeText(value instanceof BigDecimal bd ? plain(bd) : String.valueOf(value), buf, pos, f.bytes());
            return;
        }
        if (t == FieldType.HEX) {
            try { FieldCodec.write(f, value, buf, pos, f.name()); } catch (RuntimeException e) { Arrays.fill(buf, pos, pos + f.bytes(), (byte) 0); }
            return;
        }
        BigDecimal n = number(value);
        n = n.setScale(f.decimals(), RoundingMode.DOWN);
        int digits = digitsOf(f);
        BigInteger unscaled = n.unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(digits);
        BigInteger kept = unscaled.abs().mod(mod);
        if (unscaled.signum() < 0) kept = kept.negate();
        if (t.isZoned() && kept.signum() < 0) kept = kept.negate();   // num fields in MARIS are unsigned in the browser layer
        if (t.isBinary()) {
            long max = switch (f.bytes()) { case 2 -> Short.MAX_VALUE; case 4 -> Integer.MAX_VALUE; default -> Long.MAX_VALUE; };
            long v = kept.longValue();
            if (v > max || v < -max - 1) kept = BigInteger.valueOf(f.bytes() == 2 ? (short) v : f.bytes() == 4 ? (int) v : v);
        }
        FieldCodec.write(f, new BigDecimal(kept, f.decimals()), buf, pos, f.name());
    }

    private static int digitsOf(FieldLayout f) {
        return switch (f.type()) {
            case SMALLINT -> 5;
            case INT -> 10;
            case BIGINT -> 19;
            case BIN -> f.length() <= 4 ? 5 : f.length() <= 9 ? 10 : 19;
            default -> f.length();
        };
    }

    public static void writeText(String s, byte[] buf, int pos, int len) {
        int n = Math.min(len, buf.length - pos);
        for (int i = 0; i < n; i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            buf[pos + i] = (byte) (c <= 0xFF ? c : '?');
        }
    }

    // ---- value conversions ---------------------------------------------------------------------------------

    /** a value as a number; text that is not a number counts as 0 (blank fields are common) */
    public static BigDecimal number(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        if (v instanceof Boolean b) return b ? BigDecimal.ONE : BigDecimal.ZERO;
        String s = v.toString().strip();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.startsWith("+") ? s.substring(1) : s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static boolean isNumberText(String s) {
        String t = s.strip();
        if (t.isEmpty()) return false;
        try { new BigDecimal(t); return true; } catch (NumberFormatException e) { return false; }
    }

    public static String string(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal bd) return plain(bd);
        if (v instanceof Boolean b) return b ? "1" : "0";
        return v.toString();
    }

    public static String plain(BigDecimal bd) {
        return bd.signum() == 0 && bd.scale() <= 0 ? "0" : bd.toPlainString();
    }

    public static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\0')) e--;
        return s.substring(0, e);
    }

    public static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof BigDecimal bd) return bd.signum() != 0;
        return v != null && !rtrim(v.toString()).isEmpty();
    }

    /** EGL comparison: numbers numerically; text blank-padded; text against a number numerically when it is one */
    public static int compare(Object a, Object b) {
        if (a instanceof Boolean || b instanceof Boolean) return Boolean.compare(truthy(a), truthy(b));
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y);
        if (a instanceof BigDecimal x) {
            String s = string(b);
            return isNumberText(s) ? x.compareTo(number(s)) : plain(x).compareTo(rtrim(s));
        }
        if (b instanceof BigDecimal y) {
            String s = string(a);
            return isNumberText(s) ? number(s).compareTo(y) : rtrim(s).compareTo(plain(y));
        }
        return rtrim(string(a)).compareTo(rtrim(string(b)));
    }
}
