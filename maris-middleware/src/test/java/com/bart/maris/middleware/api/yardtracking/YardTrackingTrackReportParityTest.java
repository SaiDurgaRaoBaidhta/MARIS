package com.bart.maris.middleware.api.yardtracking;

import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.old;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.sent;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.str;
import static com.bart.maris.middleware.api.yardtracking.YardTrackingTrackParityTest.win;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackReportService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The report printer window (old VHYT02I), opened by Print on the track window, against the old screens run by the engine. */
class YardTrackingTrackReportParityTest {
    static YardTrackingTrackReportService api(Parity p) {
        return new YardTrackingTrackReportService(p.legacy(), p.messages(), Parity.bundle());
    }

    @Test
    void openingReadsTheSameListsAndHardCopyPrintsTheSame() {
        Parity p = YardTrackingTrackBackend.backend();
        ScreenSession s = old(p);
        p.clear();
        s.event(win(s, "VHYT08I"), List.of(), "Push_Button411", "clicked", null);
        String w = win(s, "VHYT02I");
        assertThat(w).as("VHYT02I opened").isNotNull();
        var oldOpen = new ArrayList<>(p.sent);
        // the engine draws the yard / track drop-downs (fed from DATA_ROW columns) as row objects, so their choice is set here
        s.window(w).recordPart("VHYT02IW-WORK").set("YRD-TRKG-YRD data", "SW");
        s.window(w).recordPart("VHYT02IW-WORK").set("YRD-SUB-LOC data", YardTrackingTrackBackend.TRACK);
        s.event(w, List.of(), "cntPrinterdet", "select", 2);
        p.clear();
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        var oldPrint = new ArrayList<>(p.sent);
        assertThat(sent(oldPrint, "VHYT02A", "VHYT02A_CRIT")).as("old printed: " + str(s.window(w).recordPart("VHYT02IW-MESSAGE").get("MESSAGE data"))).isNotEmpty();
        p.clear();
        var o = api(p).options("SW");
        assertThat(sent(p.sent, "VHYT02B", "VHYT02B_CRIT")).isEqualTo(sent(oldOpen, "VHYT02B", "VHYT02B_CRIT").subList(0, 1));
        assertThat(o.printers()).extracting(YardTrackingTrackReportService.Printer::vtam).containsExactly("DUBLNHP1", "LKS11HP1");
        assertThat(o.yards()).containsExactly("SW", "CY");
        assertThat(o.subLocations()).containsExactly("T-12", "T-13", "ALL");
        p.clear();
        api(p).print(new YardTrackingTrackReportService.Print("SW", YardTrackingTrackBackend.TRACK, "LKS11HP1"));
        assertThat(sent(p.sent, "VHYT02A", "VHYT02A_CRIT")).isEqualTo(sent(oldPrint, "VHYT02A", "VHYT02A_CRIT"));
    }

    @Test
    void aYardIsRequired() {
        Parity p = YardTrackingTrackBackend.backend();
        assertThatThrownBy(() -> api(p).print(new YardTrackingTrackReportService.Print("", "", "LKS11HP1"))).isInstanceOf(InvalidRequestException.class)
                .hasMessage(com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackEgl.first(Parity.bundle(), "VHYT02IS-SUBMIT", "MRSYardTracking", "MESSAGE"));
        assertThat(p.sent).isEmpty();
    }
}
