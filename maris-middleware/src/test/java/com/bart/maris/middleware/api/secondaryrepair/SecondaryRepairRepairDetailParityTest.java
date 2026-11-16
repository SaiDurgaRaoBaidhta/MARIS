package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairRepairDetailService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairRepairDetailService.RepairLine;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Repair detail (VHSR09I, opened from the menu VHMSRI) against the old screen run by the engine: what is written
 * (VHSR09D) for typed lines, a kit and "No Repair", and the messages.
 */
class SecondaryRepairRepairDetailParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Parity backend(String status) {
        Parity p = new Parity();
        p.on("VHSR09B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR09B_DATA",
                Map.of("docCount", status == null ? 0 : 1, "subJobStat", status == null ? "" : status)));
        p.on("VHSR09A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR09A_DATA_01", Map.of("dataRow", List.of(
                Map.of("stockNo", "111111111", "serNo", "S0", "cmpntCd", "BRK", "cmpntFaultCd", "F1", "cntCmpntsRepr", 1)))));
        p.on("VHSR09F", in -> "ZZZ".equals(in.get("VHSR09F_CRIT").get("cmpntCd")) ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0), "VHSR09F_DATA", Map.of("cmpntCdDesc", "BRAKE")));
        p.on("VHSR09G", in -> "ZZZZ".equals(in.get("VHSR09G_CRIT").get("cmpntFaultCd")) ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0), "VHSR09G_DATA", Map.of("cmpntFaultDesc", "WORN")));
        p.on("VHSR09C", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR09C_DATA", Map.of("kitDesc", "BRAKE KIT", "kitFound", "Y", "kitDetlFound", "Y")));
        p.on("VHSR09E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR09E_DATA", Map.of("dataRow", List.of(
                Map.of("cntParts", 2, "partFaultCd", "F2", "partNo", "PAD", "partStockNo", "222222222", "serDesc", "KS1"),
                Map.of("cntParts", 1, "partFaultCd", "F3", "partNo", "PIN", "partStockNo", "333333333", "serDesc", "KS2")))));
        p.on("VHSR09D", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHSR09H", in -> Map.of("XDCONTROL", Map.of("urc", 100)));
        return p;
    }

    static SecondaryRepairRepairDetailService api(Parity p) {
        return new SecondaryRepairRepairDetailService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    static final String[][] W = {{"txtStockNo1", "txtSerNo", "txtPartCd", "txtFaultCd", "txtQty"}, {"txtStockNo11", "txtSerNo1", "txtPartCd1", "txtFaultCd1", "txtQty1"},
            {"txtStockNo12", "txtSerNo2", "txtPartCd2", "txtFaultCd2", "txtQty2"}};

    /** the menu opens VHSR09I; the sub job is listed first, as the old screen needs */
    static ScreenSession repair(Parity p, String job, String doc) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMSRI", p.user);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHMSRI"), List.of(), "Push_Button1", "clicked", null);
        String w = SecondaryRepairCommentsParityTest.window(s, "VHSR09I");
        s.event(w, List.of(new ScreenSession.Change("txtJobNo", "object", job), new ScreenSession.Change("txtDocNo", "object", doc)), "pbtList", "clicked", null);
        return s;
    }

    /** rows "stock|serial|part|fault|qty" */
    static List<ScreenSession.Change> typed(String kit, String... rows) {
        List<ScreenSession.Change> out = new ArrayList<>();
        if (kit != null) out.add(new ScreenSession.Change("txtKit", "object", kit));
        for (int i = 0; i < rows.length; i++) {
            String[] f = rows[i].split("\\|", -1);
            for (int k = 0; k < 5; k++) out.add(new ScreenSession.Change(W[i][k], "object", k == 4 && f[k].isEmpty() ? "0" : f[k]));
        }
        return out;
    }

    static List<RepairLine> lines(String... rows) {
        List<RepairLine> out = new ArrayList<>();
        for (String r : rows) {
            String[] f = r.split("\\|", -1);
            out.add(new RepairLine(f[0], f[1], f[2], f[3], f[4].isEmpty() ? null : Integer.valueOf(f[4])));
        }
        return out;
    }

    static String[] rows(String spec) {
        return spec == null || spec.isEmpty() ? new String[0] : spec.split(";");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '#', nullValues = "NULL", value = {"NULL#444444444|S4|BRK|F1|2;555555555|S5|||1#false", "BRAKEKIT#NULL#false",
            "BRAKEKIT#444444444|S4|BRK|F1|2#false", "NULL#NULL#true"})
    void addingRepairsWritesWhatTheOldScreenWrites(String kit, String spec, boolean noRepair) {
        String[] r = rows(spec);
        Parity p = backend("REP");
        ScreenSession s = repair(p, "A12345", "1");
        String w = SecondaryRepairCommentsParityTest.window(s, "VHSR09I");
        if (kit != null) s.event(w, typed(kit), "txtKit", "losingFocus", null);           // the kit is checked on leaving its field
        s.event(w, typed(null, r), noRepair ? "Push_Button2" : "pbtAddRepair", "clicked", null);
        var old = p.lastSent("VHSR09D");
        assertThat(old).as("old VHSR09D, message " + SecondaryRepairCommentsParityTest.value(s, "VHSR09I", "txtMsgArea")).isNotNull();
        String oldMessage = SecondaryRepairCommentsParityTest.value(s, "VHSR09I", "txtMsgArea");

        p.clear();
        var added = api(p).add("A12345", 1, kit, noRepair, lines(r), TERMINAL);
        assertThat(p.lastSent("VHSR09D").get("VHSR09D_DATA_01")).isEqualTo(old.get("VHSR09D_DATA_01"));
        assertThat(p.lastSent("VHSR09D").get("VHSR09D_DATA_02")).isEqualTo(old.get("VHSR09D_DATA_02"));
        assertThat(added.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '#', value = {"||||", "|S1|BRK||1", "12AB|S1|ZZZ|ZZZZ|0", "444|S|BRK|F1|1;||||;|S9|||0"})
    void repairChecksGiveTheOldMessages(String spec) {
        String[] r = rows(spec);
        Parity p = backend("REP");
        ScreenSession s = repair(p, "A12345", "1");
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR09I"), typed(null, r), "pbtAddRepair", "clicked", null);
        assertThat(p.lastSent("VHSR09D")).isNull();
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR09I", "txtMsgArea");
        String msg;
        try { api(p).add("A12345", 1, null, false, lines(r), TERMINAL); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"A1,2,CLO", "A1,2,OPN", "A1,2,NULL", "'',2,REP", "A1,0,REP", "'',0,REP"})
    void subJobStateGivesTheOldMessages(String job, int doc, String status) {
        Parity p = backend(status);
        ScreenSession s = repair(p, job, String.valueOf(doc));
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR09I", "txtMsgArea");
        String msg;
        try { api(p).details(job, doc); msg = ""; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @ParameterizedTest
    @CsvSource({"N,N", "Y,N"})
    void kitChecksGiveTheOldMessages(String found, String details) {
        Parity p = backend("REP");
        p.on("VHSR09C", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR09C_DATA", Map.of("kitFound", found, "kitDetlFound", details)));
        ScreenSession s = repair(p, "A12345", "1");
        String w = SecondaryRepairCommentsParityTest.window(s, "VHSR09I");
        s.event(w, typed("NOKIT"), "txtKit", "losingFocus", null);
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR09I", "txtMsgArea");
        String msg;
        try { api(p).kit("NOKIT"); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }
}
