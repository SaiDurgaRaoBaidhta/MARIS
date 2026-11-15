package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairCommentsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Job / repair comments (VHSR02I, opened from the secondary repair menu VHMSRI) against the old screen run by the engine:
 * the records sent to the writing programs VHSR02A and VHSR02B and the messages.
 */
class SecondaryRepairCommentsParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Parity backend(int existing) {
        Parity p = new Parity();
        p.on("VHSR02E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR02E_DATA", Map.of("subJobStat", "OPN")));
        p.on("VHSR02F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR02F_DATA", existing == 0 ? Map.of("maxRows", 0)
                : Map.of("maxRows", existing, "narrNo", 1, "narr", "FIRST COMMENT", "userGrp", "BRIGHTCO", "dateNarr", "11-01-2026", "timeNarr", "10:00:00")));
        p.on("VHSR02C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR02C_DATA", Map.of("narr", "SECOND COMMENT", "userGrp", "BRIGHTCO",
                "dateNarr", "11-02-2026", "timeNarr", "11:00:00")));
        p.on("VHSR02A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHSR02B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHSR02D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR02D_DATA", Map.of("narrNo", existing + 1, "narr", "NEW COMMENT",
                "userGrp", "BRIGHTCO", "currentRow", existing + 1, "dateNarr", "11-14-2026", "timeNarr", "12:00:00")));
        return p;
    }

    static SecondaryRepairCommentsService api(Parity p) {
        return new SecondaryRepairCommentsService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static String window(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> w.get("screen").equals(screen))
                .map(w -> (String) w.get("id")).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!screen.equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            Object v = x == null ? null : x.get("value");
            return v == null ? "" : v.toString().strip();
        }
        return null;
    }

    /** the menu VHMSRI opens the comments screen, as the users did */
    static ScreenSession comments(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMSRI", p.user);
        s.event(window(s, "VHMSRI"), List.of(), "Push_Button38", "clicked", null);
        assertThat(window(s, "VHSR02I")).isNotNull();
        return s;
    }

    static void list(ScreenSession s, String job, String doc, int selection) {
        s.event(window(s, "VHSR02I"), List.of(new ScreenSession.Change("txtJobNo", "object", job), new ScreenSession.Change("txtSubJobDocNo", "object", doc),
                new ScreenSession.Change("rbtSel", "selectionIndex", selection)), "pbtList", "clicked", null);
    }

    @ParameterizedTest
    @CsvSource({"1,JOB,0", "2,REPAIR,2"})
    void addingAndChangingACommentSendWhatTheOldScreenSends(int selection, String kind, int existing) {
        Parity p = backend(existing);
        ScreenSession s = comments(p);
        list(s, "A12345", "1", selection);
        String w = window(s, "VHSR02I");
        s.event(w, List.of(new ScreenSession.Change("mleNewNarr", "object", "NEW COMMENT")), "pbtAdd", "clicked", null);
        var oldAdd = p.lastSent("VHSR02A").get("VHSR02A_DATA");
        String oldAddMessage = value(s, "VHSR02I", "txtMsgArea");
        s.event(w, List.of(new ScreenSession.Change("mleCurrentNarr", "object", "CHANGED")), "pbtUpdate", "clicked", null);
        var oldUpd = p.lastSent("VHSR02B");
        String oldUpdMessage = value(s, "VHSR02I", "txtMsgArea");
        int narrNo = ((Number) oldUpd.get("VHSR02B_CRIT").get("narrNo")).intValue();

        p.clear();
        var svc = api(p);
        var added = svc.add("a12345", 1, kind, "NEW COMMENT", p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR02A").get("VHSR02A_DATA")).isEqualTo(oldAdd);
        assertThat(added.message()).isEqualTo(oldAddMessage);
        String updated = svc.update("A12345", 1, kind, narrNo, "CHANGED", p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR02B").get("VHSR02B_CRIT")).isEqualTo(oldUpd.get("VHSR02B_CRIT"));
        assertThat(p.lastSent("VHSR02B").get("VHSR02B_DATA")).isEqualTo(oldUpd.get("VHSR02B_DATA"));
        assertThat(updated).isEqualTo(oldUpdMessage);
    }

    @ParameterizedTest
    @CsvSource({"A12345,2", "A12345,3"})
    void pagingReadsTheCommentTheOldScreenReads(String job, int existing) {
        Parity p = backend(existing);
        ScreenSession s = comments(p);
        list(s, job, "1", 1);
        s.event(window(s, "VHSR02I"), List.of(), "pbtNxt", "clicked", null);
        var oldCrit = p.lastSent("VHSR02C").get("VHSR02C_CRIT");
        String oldText = value(s, "VHSR02I", "mleCurrentNarr");
        p.clear();
        var c = api(p).comment(job, 1, "JOB", ((Number) oldCrit.get("narrNo")).intValue());
        assertThat(p.lastSent("VHSR02C").get("VHSR02C_CRIT")).isEqualTo(oldCrit);
        assertThat(c.text()).isEqualTo(oldText);
    }

    @ParameterizedTest
    @CsvSource(value = {"'',0", "A12345,0", "'',1"}, nullValues = "NULL")
    void listChecksGiveTheOldMessages(String job, String doc) {
        Parity p = backend(1);
        ScreenSession s = comments(p);
        list(s, job, doc, 1);
        String old = value(s, "VHSR02I", "txtMsgArea");
        assertThat(old).isNotBlank();
        String msg;
        try { api(p).list(job, Integer.parseInt(doc), "JOB"); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @ParameterizedTest
    @CsvSource({"1"})
    void unknownSubJobAndBlankCommentGiveTheOldMessages(int selection) {
        Parity p = backend(1);
        p.on("VHSR02E", in -> Map.of("XDCONTROL", Map.of("urc", 100)));
        ScreenSession s = comments(p);
        list(s, "ZZZZZZ", "9", selection);
        String old = value(s, "VHSR02I", "txtMsgArea");
        String msg;
        try { api(p).list("ZZZZZZ", 9, "JOB"); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);

        Parity q = backend(1);
        ScreenSession t = comments(q);
        list(t, "A12345", "1", selection);
        t.event(window(t, "VHSR02I"), List.of(new ScreenSession.Change("mleNewNarr", "object", "")), "pbtAdd", "clicked", null);
        String oldBlank = value(t, "VHSR02I", "txtMsgArea");
        assertThat(q.lastSent("VHSR02A")).isNull();
        try { api(q).add("A12345", 1, "JOB", " ", "BRIGHTCO", TERMINAL); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(oldBlank);
    }
}
