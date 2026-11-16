package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairLaborService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairLaborService.LaborLine;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Labor detail and job close (VHSR03I, opened from the repair detail VHSR09I as the users did) against the old screens
 * run by the engine: what is written (VHSR03D, VHSR03E) and the messages.
 */
class SecondaryRepairLaborParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Parity backend(String status, int repairs) {
        Parity p = new Parity();
        p.on("VHSR03F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR03F_DATA",
                Map.of("docCount", status == null ? 0 : 1, "subJobStat", status == null ? "" : status, "dateIn", "2026-11-01", "dateOut", "2026-11-10")));
        p.on("VHSR03A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR03A_DATA", Map.of("dataRow", List.of(
                Map.of("dateWork", "11-02-2026", "emplNo", "11111", "reprLaborHrs", new BigDecimal("1.5"))))));
        p.on("VHSR09A", in -> repairs == 0 || !"A12345".equals(in.get("VHSR09A_CRIT").get("jobNo")) && repairs < 0 ? Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", repairs), "VHSR09A_DATA_01", Map.of("dataRow", List.of(Map.of("cmpntCd", "BRK")))));
        p.on("VHSR03B", in -> Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0)));
        p.on("AFCXVLD", in -> {
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            if ("2026-02-30".equals(c.get("formDate"))) c.put("dateError", "Y");
            return Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", c);
        });
        p.on("VHSR03C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR03C_DATA", Map.of("dataRow", List.of(Map.of("shift", "1")))));
        p.on("VHSRWEL", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSRWEL_DATA", Map.of("dataRow", List.of(Map.of("workElementCd", "A")))));
        p.on("VHSR09B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR09B_DATA", Map.of("docCount", 1, "subJobStat", "REP")));
        p.on("VHSR03D", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHSR03E", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static SecondaryRepairLaborService api(Parity p) {
        return new SecondaryRepairLaborService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    /** the menu VHMSRI opens the repair detail VHSR09I; its Labor Detail opens VHSR03I for the sub job */
    static ScreenSession labor(Parity p, String job, String doc) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMSRI", p.user);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHMSRI"), List.of(), "Push_Button1", "clicked", null);
        String w = SecondaryRepairCommentsParityTest.window(s, "VHSR09I");
        assertThat(w).as("VHSR09I").isNotNull();
        s.event(w, List.of(new ScreenSession.Change("txtJobNo", "object", job), new ScreenSession.Change("txtDocNo", "object", doc)), "pbtList", "clicked", null);
        s.event(w, List.of(), "pbtLaborDetail", "clicked", null);
        assertThat(SecondaryRepairCommentsParityTest.window(s, "VHSR03I")).as("VHSR03I").isNotNull();
        return s;
    }

    static final String[][] WIDGETS = {{"txtWorkDate", "txtEmplNo", "txtLaborHrs"}, {"txtWorkDate1", "txtEmplNo1", "txtLaborHrs1"},
            {"txtWorkDate2", "txtEmplNo2", "txtLaborHrs2"}, {"txtWorkDate3", "txtEmplNo31", "txtLaborHrs3"}};

    /** rows as "MMDDYYYY|empl|hours" for the old screen; the API gets the same days as ISO dates */
    static List<ScreenSession.Change> typed(String... rows) {
        List<ScreenSession.Change> out = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            String[] f = rows[i].split("\\|", -1);
            out.add(new ScreenSession.Change(WIDGETS[i][0], "object", f[0]));
            out.add(new ScreenSession.Change(WIDGETS[i][1], "object", f[1]));
            out.add(new ScreenSession.Change(WIDGETS[i][2], "object", f[2].isEmpty() ? "0" : f[2]));
        }
        return out;
    }

    static List<LaborLine> lines(String... rows) {
        List<LaborLine> out = new ArrayList<>();
        for (String r : rows) {
            String[] f = r.split("\\|", -1);
            String iso = f[0].length() == 8 ? f[0].substring(4) + "-" + f[0].substring(0, 2) + "-" + f[0].substring(2, 4) : f[0];
            out.add(new LaborLine(iso, f[1], f[2].isEmpty() ? null : new BigDecimal(f[2])));
        }
        return out;
    }

    @Test
    void addingLaborWritesWhatTheOldScreenWrites() {
        String[] rows = {"11052026|12345|2.5", "11062026|22222|8"};
        Parity p = backend("REP", 1);
        ScreenSession s = labor(p, "A12345", "1");
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR03I"), typed(rows), "pbtAddRepr", "clicked", null);
        Map<String, Object> old = p.lastSent("VHSR03D") == null ? null : p.lastSent("VHSR03D").get("VHSR03D_DATA");
        assertThat(old).as("old VHSR03D, message " + SecondaryRepairCommentsParityTest.value(s, "VHSR03I", "txtMsg")).isNotNull();
        String oldMessage = SecondaryRepairCommentsParityTest.value(s, "VHSR03I", "txtMsg");

        p.clear();
        var added = api(p).add("A12345", 1, lines(rows), null, p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR03D").get("VHSR03D_DATA")).isEqualTo(old);
        assertThat(added.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {"|0|", "10302026|1234A|0", "11052026||100", "02302026|12345|1;11152026|12345|1"})
    void laborChecksGiveTheOldMessages(String rows) {
        String[] r = rows.split(";");
        Parity p = backend("REP", 1);
        ScreenSession s = labor(p, "A12345", "1");
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR03I"), typed(r), "pbtAddRepr", "clicked", null);
        assertThat(p.lastSent("VHSR03D")).isNull();
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR03I", "txtMsg");
        String msg;
        try { api(p).add("A12345", 1, lines(r), null, "BRIGHTCO", TERMINAL); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @Test
    void closingTheJobWritesWhatTheOldScreenWrites() {
        Parity p = backend("REP", 1);
        ScreenSession s = labor(p, "A12345", "1");
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR03I"), List.of(), "pbtCloseJob", "clicked", null);
        var old = p.lastSent("VHSR03E");
        assertThat(old).isNotNull();
        assertThat(p.lastSent("VHSR03D")).isNull();
        p.clear();
        String message = api(p).close("A12345", 1, p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR03E").get("VHSR03E_CRIT")).isEqualTo(old.get("VHSR03E_CRIT"));
        assertThat(p.lastSent("VHSR03E").get("VHSR03E_DATA")).isEqualTo(old.get("VHSR03E_DATA"));
        assertThat(message).isEqualTo(new EglTexts(Parity.bundle()).assigned("VHSR03IS-DB-CLOSE", "MRSSecondaryRepair", "FINAL-MESSAGE", "Job Closed"));
    }

    @ParameterizedTest
    @CsvSource(value = {"CLO,1", "OPN,1", "NULL,1", "REP,0"}, nullValues = "NULL")
    void subJobStateGivesTheOldMessages(String status, int repairs) {
        Parity p = backend(status, repairs);
        ScreenSession s = p.oldScreens();                     // the List of a VHSR03I opened on its own (from VHSR09I it opens loaded)
        s.openRoot("VHSR03I", p.user);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR03I"), List.of(new ScreenSession.Change("txtJobNo", "object", "A1"),
                new ScreenSession.Change("txtDocNo", "object", "2")), "pbtList", "clicked", null);
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR03I", "txtMsg");
        String msg;
        try { var d = api(p).details("A1", 2); msg = d.note(); } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }
}
