package com.bart.maris.middleware.legacy.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.FieldType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Golden vectors produced by running the browser's own gt2 functions in Node
 * (tools/golden-vectors/extract-gt2-vectors.mjs). The Java codec must produce the same bytes.
 */
class Gt2GoldenVectorsTest {
    private static JsonNode vectors;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = Gt2GoldenVectorsTest.class.getResourceAsStream("/golden/gt2-field-vectors.json")) {
            vectors = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(in);
        }
    }

    static FieldLayout field(String type, int length, int decimals) {
        return switch (type) {
            case "NUM" -> new FieldLayout("F", "f", FieldType.NUM, length, decimals, length, 1, 0, false, null);
            case "DECIMAL" -> new FieldLayout("F", "f", FieldType.DECIMAL, length, decimals, (length + 2) / 2, 1, 0, false, null);
            case "PACF" -> new FieldLayout("F", "f", FieldType.PACF, length, decimals, (length + 2) / 2, 1, 0, false, null);
            case "BIN" -> new FieldLayout("F", "f", FieldType.BIN, length, decimals, length == 4 ? 2 : length == 9 ? 4 : 8, 1, 0, false, null);
            default -> throw new IllegalArgumentException(type);
        };
    }

    @TestFactory
    Stream<DynamicTest> numericFieldsMatchBrowserBytes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors.get("numericVectors")) {
            String name = v.get("type").asText() + "(" + v.get("length").asInt() + "," + v.get("decimals").asInt() + ") " + v.get("value").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> {
                FieldLayout f = field(v.get("type").asText(), v.get("length").asInt(), v.get("decimals").asInt());
                byte[] buf = new byte[f.bytes()];
                FieldCodec.write(f, v.get("value").decimalValue(), buf, 0, "f");
                assertThat(HexFormat.of().formatHex(buf)).isEqualTo(v.get("hex").asText());
                Object back = FieldCodec.read(f, HexFormat.of().parseHex(v.get("hex").asText()), 0);
                assertThat(new BigDecimal(back.toString())).isEqualByComparingTo(v.get("decoded").decimalValue());
            }));
        }
        return tests.stream();
    }

    @Test
    void zonedDecodeOfNonDigitBytesMatchesBrowser() {
        FieldLayout f = field("NUM", 3, 0);
        for (JsonNode v : vectors.get("zonedOddDecodes")) {
            Object back = FieldCodec.read(f, HexFormat.of().parseHex(v.get("hex").asText()), 0);
            assertThat(new BigDecimal(back.toString())).isEqualByComparingTo(v.get("decoded").decimalValue());
        }
    }

    @Test
    void charFieldsPadAndTrimLikeBrowser() {
        FieldLayout f = new FieldLayout("F", "f", FieldType.CHAR, 8, 0, 8, 1, 0, false, null);
        for (JsonNode v : vectors.get("charVectors")) {
            String value = v.get("value").asText();
            byte[] buf = new byte[8];
            if (value.length() > 8) {
                // The browser silently truncates; the middleware refuses instead, so data is never cut without notice.
                assertThatThrownBy(() -> FieldCodec.write(f, value, buf, 0, "f")).isInstanceOf(CodecException.class);
                continue;
            }
            FieldCodec.write(f, value, buf, 0, "f");
            assertThat(new String(buf, StandardCharsets.ISO_8859_1)).isEqualTo(v.get("encoded").asText());
            assertThat(FieldCodec.read(f, buf, 0)).isEqualTo(v.get("gui").asText());
        }
    }

    @Test
    void escapingMatchesSyncEncodeString() {
        for (JsonNode v : vectors.get("escapeVectors")) {
            byte[] raw = HexFormat.of().parseHex(v.get("rawHex").asText());
            assertThat(WireEscaping.escape(raw)).isEqualTo(v.get("escaped").asText());
            assertThat(WireEscaping.unescape(v.get("escaped").asText())).isEqualTo(raw);
        }
    }
}
