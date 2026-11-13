package com.bart.maris.middleware.legacy.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.support.TestCatalog;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WireEncodingGuardTest {
    private final RecordLayout xd = TestCatalog.get().record("MRSCommonCode.XDCONTROL");

    /** Real XDCONTROL reply captured from the local backend when the request records were sent empty. */
    private static final String EBCDIC_REPLY = "@".repeat(20) + "N%F1%F0%F0" + "@".repeat(71) + "%F0%F0%F0%00%00%00%00%00@"
            + "%00".repeat(18);

    @Test
    void asciiRepliesAreLeftAlone() {
        byte[] ascii = RecordCodec.encode(xd, Map.of("umsgcode", "OK", "urc", 4));
        assertThat(WireEncodingGuard.looksEbcdic(xd, ascii)).isFalse();
    }

    @Test
    void ebcdicRepliesAreDetectedAndConverted() {
        byte[] raw = WireEscaping.unescape(EBCDIC_REPLY);
        assertThat(WireEncodingGuard.looksEbcdic(xd, raw)).isTrue();
        Map<String, Object> out = RecordCodec.decode(xd, WireEncodingGuard.toAscii(xd, raw), false);
        assertThat(out.get("umsgcode")).isEqualTo("");
        assertThat(out.get("urc")).isEqualTo(0L);
        assertThat(((java.util.List<?>) out.get("umsgins")).get(0)).isEqualTo("+100");
    }
}
