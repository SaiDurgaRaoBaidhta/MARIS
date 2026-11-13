package com.bart.maris.middleware.legacy.codec;

/**
 * The %xx escaping the gt2 browser layer applies to record bytes ({@code syncEncodeString_} /
 * {@code syncDecodeString_} in sync-gt2-vast-support.layer.js): bytes below 32, above 126, and the characters
 * {@code % } " \} are written as % plus two lower-case hex digits; everything else is the character itself.
 */
public final class WireEscaping {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private WireEscaping() {}

    public static String escape(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length + 16);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c < 32 || c > 126 || c == '%' || c == '}' || c == '"' || c == '\\') {
                sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    public static byte[] unescape(String s) {
        byte[] out = new byte[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) throw new CodecException(null, "truncated %xx escape in backend data");
                out[n++] = (byte) Integer.parseInt(s.substring(i + 1, i + 3), 16);
                i += 2;
            } else {
                if (c > 0xFF) throw new CodecException(null, "unexpected character U+" + Integer.toHexString(c) + " in backend data");
                out[n++] = (byte) c;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }
}
