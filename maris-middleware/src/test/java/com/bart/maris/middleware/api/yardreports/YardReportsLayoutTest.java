package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The tracks and spots of each yard map, read from the old screens' LOAD statement groups. */
class YardReportsLayoutTest {
    private static final String PKG = "MRSYardTrackingReport";

    private static Set<Integer> spots(int from, int to) {
        return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
    }

    @Test
    void berryessaHasSevenTracksOfTenSpots() {
        Map<String, Set<Integer>> l = YardReportsLayout.of(Parity.bundle(), "VHIC01IS-LOAD", PKG);
        assertThat(l.keySet()).containsExactlyInAnyOrder("S-1", "S-2", "TM3", "TM4", "SSTOR1", "SSTOR2", "SSTOR3");
        assertThat(l.values()).allMatch(s -> s.equals(spots(1, 10)));
    }

    @Test
    void millbraeTracksHaveTheirOwnLengths() {
        Map<String, Set<Integer>> l = YardReportsLayout.of(Parity.bundle(), "VHYT80IS-LOAD", PKG);
        assertThat(l).hasSize(10);
        assertThat(l.get("TM5")).isEqualTo(spots(1, 18));
        assertThat(l.get("TM6")).isEqualTo(spots(1, 17));
        assertThat(l.get("W-1")).isEqualTo(spots(1, 10));
    }

    @Test
    void theInquiryAndUpdateScreenOfAMapPlaceCarsAlike() {
        for (String[] pair : new String[][]{{"VHIC01IS-LOAD", "VHIC02IS-LOAD"}, {"VHYT80IS-LOAD", "VHYT81IS-LOAD"},
                {"VHYT105IS-LOAD", "VHYT106IS-LOAD"}, {"VHYT70IS-LOAD", "VHYT71IS-LOAD"}, {"VHYT90IS-LOAD", "VHYT91IS-LOAD"}}) {
            assertThat(YardReportsLayout.of(Parity.bundle(), pair[1], PKG)).as(pair[1])
                    .isEqualTo(YardReportsLayout.of(Parity.bundle(), pair[0], PKG));
        }
    }

    @Test
    void homeYardColoursComeFromTheColourStatementGroup() {
        Map<String, List<String>> c = YardReportsLayout.colours(Parity.bundle(), "VHIC01IS-COLOR", PKG);
        assertThat(c.get("HY")).containsExactly("GREEN", "GREY55");
        assertThat(c.get("RY")).containsExactly("DARK ORANGE", "LIGHT YELLOW");
        assertThat(c.get("CY")).containsExactly("YELLOW", "GREY55");
        assertThat(c.get("DY")).containsExactly("RED", "LIGHT YELLOW");
        assertThat(c.get(YardReportsLayout.OTHER_YARD)).containsExactly("BLACK", "GREY80");
    }
}
