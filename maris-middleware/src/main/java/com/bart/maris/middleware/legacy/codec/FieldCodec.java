package com.bart.maris.middleware.legacy.codec;

import com.bart.maris.middleware.legacy.layout.FieldLayout;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Reads and writes single field values exactly like the gt2 browser layer does
 * (ITFNonNumericDataItem, ITFNumericDataItem, ITFPackDataItem, ITFPacfDataItem, ITFBinDataItem).
 *
 * <ul>
 *   <li>char: ISO-8859-1 bytes, right-padded with spaces; trailing spaces are removed on read.</li>
 *   <li>num: ASCII zoned decimal ("123"); a negative value carries 0x70+digit in the last byte.</li>
 *   <li>decimal / pacf: packed decimal, sign nibble C (or F for pacf) for positive, D for negative.</li>
 *   <li>smallint / int / bigint / bin: big-endian two's complement.</li>
 * </ul>
 *
 * <p>Unlike the browser, values that do not fit are rejected instead of being silently truncated.
 */
public final class FieldCodec {
    private static final byte SPACE = 0x20;

    private FieldCodec() {}

    /** Writes the field's empty value (what the browser's setEmpty produces) into one element. */
    public static void writeDefault(FieldLayout f, byte[] buf, int pos) {
        switch (f.type()) {
            case NUM, NUMC -> Arrays.fill(buf, pos, pos + f.bytes(), (byte) '0');
            case DECIMAL -> { Arrays.fill(buf, pos, pos + f.bytes(), (byte) 0); buf[pos + f.bytes() - 1] = 0x0C; }
            case PACF -> { Arrays.fill(buf, pos, pos + f.bytes(), (byte) 0); buf[pos + f.bytes() - 1] = 0x0F; }
            case SMALLINT, INT, BIGINT, BIN -> Arrays.fill(buf, pos, pos + f.bytes(), (byte) 0);
            default -> Arrays.fill(buf, pos, pos + f.bytes(), SPACE);   // CHAR, MBCHAR, HEX, GROUP
        }
    }

    public static void write(FieldLayout f, Object value, byte[] buf, int pos, String path) {
        switch (f.type()) {
            case CHAR, MBCHAR -> writeText(f, value, buf, pos, path);
            case NUM, NUMC -> writeZoned(f, unscaled(f, value, path), buf, pos, path);
            case DECIMAL -> writePacked(f, unscaled(f, value, path), 0x0C, buf, pos, path);
            case PACF -> writePacked(f, unscaled(f, value, path), 0x0F, buf, pos, path);
            case SMALLINT, INT, BIGINT, BIN -> writeBinary(f, unscaled(f, value, path), buf, pos, path);
            case HEX -> writeHex(f, value, buf, pos, path);
            case GROUP -> throw new CodecException(path, "is a group; send an object with its fields");
        }
    }

    public static Object read(FieldLayout f, byte[] buf, int pos) {
        return switch (f.type()) {
            case CHAR, MBCHAR, GROUP -> readText(buf, pos, f.bytes());
            case NUM, NUMC -> scaled(readZoned(buf, pos, f.bytes()), f.decimals());
            case DECIMAL, PACF -> scaled(readPacked(buf, pos, f.bytes()), f.decimals());
            case SMALLINT, INT, BIGINT, BIN -> scaled(new BigInteger(Arrays.copyOfRange(buf, pos, pos + f.bytes())), f.decimals());
            case HEX -> HexFormat.of().withUpperCase().formatHex(buf, pos, pos + f.bytes());
        };
    }

    // ---- text --------------------------------------------------------------------------------------------

    private static void writeText(FieldLayout f, Object value, byte[] buf, int pos, String path) {
        String s = value instanceof BigDecimal bd ? bd.toPlainString() : String.valueOf(value);
        byte[] b = encodeLatin1(s, path);
        if (b.length > f.bytes()) {
            throw new CodecException(path, "is " + b.length + " characters; the field holds " + f.bytes());
        }
        System.arraycopy(b, 0, buf, pos, b.length);
        Arrays.fill(buf, pos + b.length, pos + f.bytes(), SPACE);
    }

    private static String readText(byte[] buf, int pos, int len) {
        int end = pos + len;
        while (end > pos && buf[end - 1] == SPACE) end--;
        return new String(buf, pos, end - pos, StandardCharsets.ISO_8859_1);
    }

    private static byte[] encodeLatin1(String s, String path) {
        CharsetEncoder enc = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer bb = enc.encode(CharBuffer.wrap(s));
            byte[] out = new byte[bb.remaining()];
            bb.get(out);
            return out;
        } catch (CharacterCodingException e) {
            throw new CodecException(path, "contains characters the MARIS backend cannot store");
        }
    }

    // ---- numbers -----------------------------------------------------------------------------------------

    /** Converts a JSON value to the integer the field stores (value x 10^decimals). */
    static BigInteger unscaled(FieldLayout f, Object value, String path) {
        BigDecimal v;
        try {
            if (value instanceof BigDecimal bd) v = bd;
            else if (value instanceof Number n) v = new BigDecimal(n.toString());
            else if (value instanceof String s) v = s.isBlank() ? BigDecimal.ZERO : new BigDecimal(s.strip());
            else throw new CodecException(path, "must be a number");
        } catch (NumberFormatException e) {
            throw new CodecException(path, "is not a valid number");
        }
        try {
            return v.setScale(f.decimals(), RoundingMode.UNNECESSARY).movePointRight(f.decimals()).toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new CodecException(path, "allows at most " + f.decimals() + " decimal places");
        }
    }

    private static Object scaled(BigInteger unscaled, int decimals) {
        if (decimals > 0) return new BigDecimal(unscaled, decimals);
        return unscaled.bitLength() < 64 ? (Object) unscaled.longValueExact() : unscaled;
    }

    private static void checkDigits(FieldLayout f, BigInteger v, String path) {
        if (v.abs().toString().length() > f.length()) {
            throw new CodecException(path, "has more than " + f.length() + " digits");
        }
    }

    private static void writeZoned(FieldLayout f, BigInteger v, byte[] buf, int pos, String path) {
        checkDigits(f, v, path);
        String digits = v.abs().toString();
        int n = f.bytes();
        for (int i = 0; i < n; i++) {
            int src = digits.length() - n + i;
            buf[pos + i] = (byte) (src >= 0 ? digits.charAt(src) : '0');
        }
        if (v.signum() < 0) buf[pos + n - 1] = (byte) (0x70 + (buf[pos + n - 1] - '0'));
    }

    /** Same arithmetic as the browser: each byte contributes (byte mod 16); sign from the last byte. */
    static BigInteger readZoned(byte[] buf, int pos, int len) {
        BigInteger v = BigInteger.ZERO;
        for (int i = 0; i < len; i++) v = v.multiply(BigInteger.TEN).add(BigInteger.valueOf((buf[pos + i] & 0xFF) % 16));
        int last = buf[pos + len - 1] & 0xFF;
        return last < 48 || last > 63 ? v.negate() : v;
    }

    private static void writePacked(FieldLayout f, BigInteger v, int positiveSign, byte[] buf, int pos, String path) {
        checkDigits(f, v, path);
        int nibbles = f.bytes() * 2;
        String digits = v.abs().toString();
        int[] nib = new int[nibbles];
        for (int i = 0; i < digits.length(); i++) nib[nibbles - 1 - digits.length() + i] = digits.charAt(i) - '0';
        nib[nibbles - 1] = v.signum() < 0 ? 0x0D : positiveSign;
        for (int i = 0; i < f.bytes(); i++) buf[pos + i] = (byte) ((nib[2 * i] << 4) | nib[2 * i + 1]);
    }

    /** Standard packed decimal: sign nibble B or D is negative, anything else (C, F, A, E) positive. */
    static BigInteger readPacked(byte[] buf, int pos, int len) {
        BigInteger v = BigInteger.ZERO;
        for (int i = 0; i < len; i++) {
            int b = buf[pos + i] & 0xFF;
            v = v.multiply(BigInteger.TEN).add(BigInteger.valueOf(b >> 4));
            if (i < len - 1) v = v.multiply(BigInteger.TEN).add(BigInteger.valueOf(b & 0x0F));
        }
        int sign = buf[pos + len - 1] & 0x0F;
        return sign == 0x0D || sign == 0x0B ? v.negate() : v;
    }

    private static void writeBinary(FieldLayout f, BigInteger v, byte[] buf, int pos, String path) {
        int n = f.bytes();
        if (v.bitLength() > n * 8 - 1) throw new CodecException(path, "is out of range for a " + (n * 8) + "-bit field");
        byte[] two = v.toByteArray();                 // minimal big-endian two's complement
        byte fill = (byte) (v.signum() < 0 ? 0xFF : 0x00);
        for (int i = 0; i < n; i++) {
            int src = two.length - n + i;
            buf[pos + i] = src >= 0 ? two[src] : fill;
        }
    }

    private static void writeHex(FieldLayout f, Object value, byte[] buf, int pos, String path) {
        byte[] b;
        try {
            String s = String.valueOf(value).strip();
            b = HexFormat.of().parseHex(s.length() % 2 == 0 ? s : s + "0");
        } catch (IllegalArgumentException e) {
            throw new CodecException(path, "must be a hexadecimal string");
        }
        if (b.length > f.bytes()) throw new CodecException(path, "has more than " + f.bytes() + " bytes");
        System.arraycopy(b, 0, buf, pos, b.length);
        Arrays.fill(buf, pos + b.length, pos + f.bytes(), (byte) 0);
    }
}
