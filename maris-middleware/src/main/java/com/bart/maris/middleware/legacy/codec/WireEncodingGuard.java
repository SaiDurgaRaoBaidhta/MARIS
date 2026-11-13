package com.bart.maris.middleware.legacy.codec;

import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.nio.charset.Charset;
import java.util.List;

/**
 * The EGL backend answers in the encoding it received. The middleware always sends full ASCII records (like the
 * browser), so replies are ASCII. If a reply still arrives in EBCDIC (seen when a record is sent empty), this guard
 * detects it from the text and zoned-decimal bytes and converts those bytes to the ASCII form before decoding.
 */
public final class WireEncodingGuard {
    private static final Charset EBCDIC = Charset.forName("IBM037");

    private WireEncodingGuard() {}

    public static boolean looksEbcdic(RecordLayout layout, byte[] data) {
        int[] counts = new int[2];   // [ascii-ish, ebcdic-ish]
        scan(layout.fields(), data, 0, counts);
        return counts[1] > counts[0];
    }

    private static void scan(List<FieldLayout> fields, byte[] data, int base, int[] counts) {
        for (FieldLayout f : fields) {
            for (int i = 0; i < f.occurs(); i++) {
                int pos = base + f.offset() + i * f.bytes();
                if (f.isGroup()) {
                    scan(f.children(), data, pos, counts);
                    continue;
                }
                for (int k = pos; k < Math.min(pos + f.bytes(), data.length); k++) {
                    int b = data[k] & 0xFF;
                    if (f.type().isText()) {
                        if (b == 0x20) counts[0]++;
                        else if (b == 0x40) counts[1]++;
                    } else if (f.type().isZoned()) {
                        if (b >= 0x30 && b <= 0x39) counts[0]++;
                        else if (b >= 0xF0 && b <= 0xF9) counts[1]++;
                    }
                }
            }
        }
    }

    /** Returns a copy with text bytes converted from EBCDIC (cp037) to ISO-8859-1 and zoned digits to ASCII form. */
    public static byte[] toAscii(RecordLayout layout, byte[] data) {
        byte[] out = data.clone();
        convert(layout.fields(), out, 0);
        return out;
    }

    private static void convert(List<FieldLayout> fields, byte[] d, int base) {
        for (FieldLayout f : fields) {
            for (int i = 0; i < f.occurs(); i++) {
                int pos = base + f.offset() + i * f.bytes();
                int end = Math.min(pos + f.bytes(), d.length);
                if (f.isGroup()) {
                    convert(f.children(), d, pos);
                } else if (f.type().isText() && pos < end) {
                    byte[] latin1 = new String(d, pos, end - pos, EBCDIC).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                    System.arraycopy(latin1, 0, d, pos, end - pos);
                } else if (f.type().isZoned()) {
                    for (int k = pos; k < end; k++) {
                        int b = d[k] & 0xFF;
                        boolean negative = k == end - 1 && (b >> 4) == 0x0D;
                        d[k] = (byte) ((negative ? 0x70 : 0x30) + (b & 0x0F));
                    }
                }
            }
        }
    }
}
