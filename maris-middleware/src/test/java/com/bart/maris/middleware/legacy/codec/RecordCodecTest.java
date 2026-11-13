package com.bart.maris.middleware.legacy.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.FieldType;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.support.TestCatalog;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordCodecTest {
    private final LegacyCatalog catalog = TestCatalog.get();

    @Test
    void emptyXdcontrolIsExactlyWhatTheBrowserSends() {
        byte[] b = RecordCodec.blank(catalog.record("MRSCommonCode.XDCONTROL"));
        assertThat(b).hasSize(122);
        // 95 spaces (message code + 3 inserts), URC "000", UROWCNT 0x0000, 22 spaces
        String expected = "20".repeat(95) + "303030" + "0000" + "20".repeat(22);
        assertThat(HexFormat.of().formatHex(b)).isEqualTo(expected);
    }

    @Test
    void signonCriteriaRecord() {
        RecordLayout crit = catalog.record("MRSMainMenu.MRS02A_CRIT");
        byte[] b = RecordCodec.encode(crit, Map.of("userGrp", "BRIGHTCO", "userPassword", "AB12"));
        assertThat(new String(b, StandardCharsets.ISO_8859_1)).isEqualTo("BRIGHTCOAB12  ");
        // EGL names work as keys too
        assertThat(RecordCodec.encode(crit, Map.of("USER_GRP", "X"))).startsWith("X       ".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void roundTripXdcontrolWithInsertsAndRowCount() {
        RecordLayout xd = catalog.record("MRSCommonCode.XDCONTROL");
        Map<String, Object> in = Map.of("umsgcode", "SQL OTHER ERR", "umsgins", List.of("-904", "VHXCONA", "MRS_CON"),
                "urc", -904, "urowcnt", 37, "udown", "Y", "ufunction", "RETRIEVE");
        Map<String, Object> out = RecordCodec.decode(xd, RecordCodec.encode(xd, in), false);
        assertThat(out.get("umsgcode")).isEqualTo("SQL OTHER ERR");
        assertThat(out.get("umsgins")).isEqualTo(List.of("-904", "VHXCONA", "MRS_CON"));
        assertThat(out.get("urc")).isEqualTo(-904L);
        assertThat(out.get("urowcnt")).isEqualTo(37L);
        assertThat(out.get("udown")).isEqualTo("Y");
    }

    @Test
    void tableRowsAreNestedAndTrailingEmptyRowsDropped() {
        RecordLayout data = catalog.record("MRSCommonCode.WS70A01_DATA");
        assertThat(data.length()).isEqualTo(19600);
        byte[] b = RecordCodec.encode(data, Map.of("dataRow", List.of(
                Map.of("serverName", "DPX", "prtrName", "DUBLIN TMZ", "vtamName", "DUBLNHP1"),
                Map.of("vtamName", "LKS11HP1"))));
        Map<String, Object> trimmed = RecordCodec.decode(data, b, true);
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) trimmed.get("dataRow");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("vtamName", "DUBLNHP1").containsEntry("prtrLocDesc", "");
        assertThat((List<?>) RecordCodec.decode(data, b, false).get("dataRow")).hasSize(200);
    }

    @Test
    void groupWithItsOwnTypeCanBeSetWhole() {
        FieldLayout cc = new FieldLayout("CC", "cc", FieldType.NUM, 2, 0, 2, 1, 0, false, null);
        FieldLayout yr = new FieldLayout("YR", "yr", FieldType.NUM, 2, 0, 2, 1, 2, false, null);
        FieldLayout cy = new FieldLayout("CY", "cy", FieldType.NUM, 4, 0, 4, 1, 0, false, List.of(cc, yr));
        RecordLayout r = new RecordLayout("R", "P", 4, List.of(cy));
        assertThat(new String(RecordCodec.encode(r, Map.of("cy", 2026)), StandardCharsets.ISO_8859_1)).isEqualTo("2026");
        assertThat(RecordCodec.decode(r, "2026".getBytes(StandardCharsets.ISO_8859_1), false))
                .isEqualTo(Map.of("cy", Map.of("cc", 20L, "yr", 26L)));
    }

    @Test
    void decimalsComeBackAsBigDecimal() {
        FieldLayout amt = new FieldLayout("AMT", "amt", FieldType.DECIMAL, 7, 2, 4, 1, 0, false, null);
        RecordLayout r = new RecordLayout("R", "P", 4, List.of(amt));
        assertThat(RecordCodec.decode(r, RecordCodec.encode(r, Map.of("amt", "-12.5")), false).get("amt"))
                .isEqualTo(new BigDecimal("-12.50"));
    }

    @Test
    void valuesThatDoNotFitAreRefused() {
        RecordLayout crit = catalog.record("MRSMainMenu.MRS02A_CRIT");
        assertThatThrownBy(() -> RecordCodec.encode(crit, Map.of("userGrp", "TOOLONGGROUP")))
                .isInstanceOf(CodecException.class).hasMessageContaining("MRS02A_CRIT.userGrp");
        assertThatThrownBy(() -> RecordCodec.encode(crit, Map.of("nope", "x")))
                .isInstanceOf(CodecException.class).hasMessageContaining("not a field");
        assertThatThrownBy(() -> RecordCodec.encode(crit, Map.of("userGrp", "caf€")))
                .isInstanceOf(CodecException.class).hasMessageContaining("cannot store");
        RecordLayout xd = catalog.record("MRSCommonCode.XDCONTROL");
        assertThatThrownBy(() -> RecordCodec.encode(xd, Map.of("urc", 1000))).hasMessageContaining("more than 3 digits");
        assertThatThrownBy(() -> RecordCodec.encode(xd, Map.of("urc", "1.5"))).hasMessageContaining("decimal places");
        assertThatThrownBy(() -> RecordCodec.encode(xd, Map.of("urowcnt", 40000))).hasMessageContaining("out of range");
        assertThatThrownBy(() -> RecordCodec.encode(xd, Map.of("umsgins", List.of("a", "b", "c", "d")))).hasMessageContaining("holds 3");
    }
}
