package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintNarrativeService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The narrative window VHNAR01I against the old screen run by the engine, opened the old way: menu VHMMMI, incident
 * maintenance VHINC10I, List, Add Narrative. Both sides run against the same scripted backend.
 */
class VehicleMaintNarrativeParityTest {
    static final String TERMINAL = "MARIS-UI";
    static final String CI = "CI / ACTION" + " ".repeat(25);

    static Parity backend() {
        Parity p = new Parity();
        p.on("VH01A01", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VH01A01_DATA", Map.of("userGroup", "BRIGHTCO", "termId", TERMINAL, "numVehIncdStat", 1,
                "vehIncdStatRow", List.of(Map.of("vehIncdStat", "OPN", "vehIncdStatDesc", "OPEN")))));
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVA_DATA", Map.of("revenueDate", "11-13-2026")));
        p.on("VHINC1D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHINC1D_DATA", Map.of("carNo", "1101", "incdType", "REV", "sympCd", "0120",
                "vehIncdStat", "OPN", "dataNarrRow", List.of(
                        Map.of("narrNo", 1, "userGrp", "VEHSUPR", "dateNarr", "11-01-2026", "timeNarr", "10.15.00", "narr", "FIRST"),
                        Map.of("narrNo", 2, "userGrp", "BRIGHTCO", "dateNarr", "11-02-2026", "timeNarr", "11.20.00", "narr", "SECOND"),
                        Map.of("narrNo", 3, "userGrp", "BRIGHTCO", "dateNarr", "11-03-2026", "timeNarr", "12.25.00", "narr", "THIRD")))));
        Map<String, Object> second = Map.of("narr", CI + "MOVED TO SHOP", "dateNarr", "11-02-2026", "timeNarr", "11.20.00", "userGrp", "BRIGHTCO", "maxRows", 3,
                "currentRow", 2, "narrNo", 2);
        p.on("VHXNR1A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHXNR1A_DATA", second));
        p.on("VHXNR5A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHXNR5A_DATA", second));
        p.on("VHXNR2A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXNR2A_DATA", Map.of("narrNo", 4)));
        p.on("VHXNR3A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXNR3A_DATA", Map.of("narrNo", 4)));
        Map<String, Object> added = Map.of("narr", "NEW ONE", "dateNarr", "11-13-2026", "timeNarr", "13.30.00", "userGrp", "BRIGHTCO", "maxRows", 4, "narrNo", 4);
        p.on("VHXNR6A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHXNR6A_DATA", added));
        p.on("VHXNR4A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHXNR4A_DATA", added));
        p.on("VHXNR7A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHXNR8A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleMaintNarrativeService api(Parity p) {
        return new VehicleMaintNarrativeService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s, String screen) {
        return (String) window(s, screen).get("id");
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> w = window(s, screen);
        if (w == null) return "(no " + screen + ")";
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        if (x == null) return "(no " + widget + ")";
        Object v = x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** menu -> VHINC10I -> List incident -> Add Narrative: VHNAR01I open with the first narrative */
    static ScreenSession opened(Parity p, Integer reprDocNo) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMMMI", p.user);
        s.event(id(s, "VHMMMI"), List.of(), "Push_Button20", "clicked", null);
        s.event(id(s, "VHINC10I"), List.of(new ScreenSession.Change("txtIncdno", "object", "29990010")), "pbtList", "clicked", null);
        s.event(id(s, "VHINC10I"), List.of(), "pbtNarr", "clicked", null);
        assertThat(window(s, "VHNAR01I")).as("VHNAR01I opened").isNotNull();
        if (reprDocNo != null) s.window(id(s, "VHNAR01I")).recordPart("VHNARIW-PASSVALUE").set("REPR-DOC-NO data", reprDocNo);
        return s;
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Map<String, Object>> records = new java.util.TreeMap<>(e.getValue());
            records.remove("XDCONTROL");                                     // the old screen reuses one XDCONTROL across calls
            if (records.containsKey(program + "_CRIT") && !program.matches("VHXNR[78]A")) records.keySet().retainAll(java.util.Set.of(program + "_CRIT"));
            // a read program's DATA record is its answer: the old screen sends back whatever the last call left in it
            out.add(records);
        }
        return out;
    }

    static List<Object> after(Parity p, int from, String program) {
        return sent(p.sent.subList(from, p.sent.size()), program);
    }

    @Test
    void openingShowsWhatTheParentPassed() {
        Parity p = backend();
        ScreenSession s = opened(p, null);
        var n = api(p).opening("29990010", 0, 1, 3, p.user.groupId(), "11-01-2026", "10.15.00", "FIRST");
        assertThat(n.narr()).isEqualTo(value(s, "VHNAR01I", "mleCurrentNarrative"));
        assertThat(n.userGrp()).isEqualTo(value(s, "VHNAR01I", "txtusergroup"));
        assertThat(n.dateNarr()).isEqualTo(value(s, "VHNAR01I", "txtdatenarrative"));
        assertThat(n.timeNarr()).isEqualTo(value(s, "VHNAR01I", "txttimenarrative"));
        assertThat(String.valueOf(n.currentRow())).isEqualTo(value(s, "VHNAR01I", "txtnarrno"));
        assertThat(n.incdNo()).isEqualTo(value(s, "VHNAR01I", "txtIncdNo"));
    }

    @Test
    void nextAndPrevReadTheSameNarrativeAndSplitTheCiPart() {
        for (Integer repr : new Integer[] {null, 7}) {
            String program = repr == null ? "VHXNR1A" : "VHXNR5A";
            for (String dir : List.of("next", "prev")) {
                Parity p = backend();
                ScreenSession s = opened(p, repr);
                if (dir.equals("prev")) s.event(id(s, "VHNAR01I"), List.of(), "pbtdownnarr", "clicked", null);     // Prv is off on row 1
                int mark = p.sent.size();
                s.event(id(s, "VHNAR01I"), List.of(), dir.equals("next") ? "pbtdownnarr" : "pbtupnarr", "clicked", null);
                List<Object> old = after(p, mark, program);
                p.clear();
                var n = api(p).step("29990010", repr == null ? 0 : repr, dir.equals("next") ? 1 : 2, 3, dir);
                assertThat(sent(p.sent, program)).as(program + " " + dir).isEqualTo(old);
                assertThat(n.narr()).isEqualTo(value(s, "VHNAR01I", "mleCurrentNarrative"));
                assertThat(n.ciPart()).isEqualTo(value(s, "VHNAR01I", "txtCIMsg"));
                assertThat(n.timeNarr()).isEqualTo(value(s, "VHNAR01I", "txttimenarrative"));
                assertThat(String.valueOf(n.currentRow())).isEqualTo(value(s, "VHNAR01I", "txtnarrno"));
            }
        }
    }

    @Test
    void addSendsTheSameNarrativeAndReadsBackTheLastOne() {
        for (Integer repr : new Integer[] {null, 7}) {
            Parity p = backend();
            ScreenSession s = opened(p, repr);
            int mark = p.sent.size();
            s.event(id(s, "VHNAR01I"), List.of(new ScreenSession.Change("mleNewNarrative", "object", "NEW ONE")), "pbtAddNarrative", "clicked", null);
            List<String> programs = repr == null ? List.of("VHXNR2A", "VHXNR6A") : List.of("VHXNR3A", "VHXNR4A");
            List<List<Object>> old = programs.stream().map(x -> after(p, mark, x)).toList();
            p.clear();
            var a = api(p).add("29990010", repr == null ? 0 : repr, 3, "NEW ONE", p.user.groupId());
            for (int i = 0; i < programs.size(); i++) assertThat(sent(p.sent, programs.get(i))).as(programs.get(i)).isEqualTo(old.get(i));
            assertThat(a.message()).isEqualTo(value(s, "VHNAR01I", "txtMsgArea"));
            assertThat(a.narrative().narr()).isEqualTo(value(s, "VHNAR01I", "mleCurrentNarrative"));
            assertThat(String.valueOf(a.narrative().currentRow())).isEqualTo(value(s, "VHNAR01I", "txtnarrno"));
        }
    }

    @Test
    void updateKeepsTheCiPartAndSendsTheSameRecords() {
        for (Integer repr : new Integer[] {null, 7}) {
            String program = repr == null ? "VHXNR7A" : "VHXNR8A";
            Parity p = backend();
            ScreenSession s = opened(p, repr);
            s.event(id(s, "VHNAR01I"), List.of(), "pbtdownnarr", "clicked", null);                // narrative 2 has a CI / ACTION part
            String ci = value(s, "VHNAR01I", "txtCIMsg");
            int mark = p.sent.size();
            s.event(id(s, "VHNAR01I"), List.of(new ScreenSession.Change("mleCurrentNarrative", "object", "MOVED TO SHOP 2")), "pbtUpdateNarrative", "clicked", null);
            List<Object> old = after(p, mark, program);
            p.clear();
            String msg = api(p).update("29990010", repr == null ? 0 : repr, 2, "MOVED TO SHOP 2", ci, p.user.groupId());
            assertThat(sent(p.sent, program)).as(program).isEqualTo(old);
            assertThat(msg).isEqualTo(value(s, "VHNAR01I", "txtMsgArea"));
        }
    }

    @Test
    void blankNarrativeIsRefusedWithTheOldMessage() {
        Parity p = backend();
        ScreenSession s = opened(p, null);
        s.event(id(s, "VHNAR01I"), List.of(), "pbtAddNarrative", "clicked", null);
        String old = value(s, "VHNAR01I", "txtMsgArea");
        assertThat(old).isNotBlank();
        String message;
        try { api(p).add("29990010", 0, 3, " ", "BRIGHTCO"); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(old);
    }
}
