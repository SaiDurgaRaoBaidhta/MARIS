package com.bart.maris.middleware.api.wayside;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.wayside.WaysideNarrativeService;
import com.bart.maris.middleware.domain.wayside.WaysideNarrativeService.Context;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Wayside narratives (old WS13I, opened with an incident, a repair or a PM) against the old screen run by the engine:
 * the records sent to the adding programs (WS13A07 repair, WS13A04 incident, WS13A05 PM), to the latest-narrative
 * programs (WS13A08, WSX2A01, WSX3A), and to the Prv / Nxt programs (WS13A06, WS13A02, WS13A01).
 */
class WaysideNarrativeParityTest {
    static final String TERMINAL = "MARIS-UI";
    static final int MAX = 3;

    static Map<String, Object> narr(int no) {
        return Map.of("narr", "LINE " + no, "dateNarr", "2026-11-0" + no, "timeNarr", "10.00.0" + no, "userGrp", "WAYSIDE", "maxRows", MAX, "narrNo", no,
                "currentRow", no);
    }

    static Parity backend() {
        Parity p = new Parity();
        for (String w : List.of("WS13A07", "WS13A04", "WS13A05")) p.on(w, in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("WS13A08", in -> Map.of("XDCONTROL", Map.of("urc", 0), "WS13A08_DATA", narr(MAX)));
        p.on("WSX2A01", in -> Map.of("XDCONTROL", Map.of("urc", 0), "WSX2A01_DATA", narr(MAX)));
        p.on("WSX3A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "WSX3A_DATA", narr(MAX)));
        for (String g : List.of("WS13A06", "WS13A02", "WS13A01")) {
            String crit = g + "_CRIT", data = g + "_DATA";
            p.on(g, in -> Map.of("XDCONTROL", Map.of("urc", 0), data, narr(((Number) in.get(crit).get("narrNo")).intValue())));
        }
        return p;
    }

    static WaysideNarrativeService api(Parity p) {
        return new WaysideNarrativeService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), TERMINAL);
    }

    static Context context(String kind) {
        return switch (kind) {
            case "repair" -> new Context("29990010", 2, "", "", "", "", "");
            case "incident" -> new Context("29990010", 0, "", "", "", "", "");
            default -> new Context("", 0, "E", "SW0101", "A10", "M01", "2026-11-01");
        };
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if ("WS13I".equalsIgnoreCase((String) w.get("screen"))) return (String) w.get("id");
        return null;
    }

    /** WS13I as its opener leaves it: the context, the narrative count and the row shown */
    static ScreenSession old(Parity p, Context c, int row) {
        ScreenSession s = p.oldScreens();
        s.openRoot("WS13I", p.user);
        RecordPart pv = s.window(windowId(s)).recordPart("WS13IW-PASSVALUE");
        pv.set("INCD-NO data", c.incident());
        pv.set("REPR-DOC-NO data", c.repairDoc());
        pv.set("PM-DEPT-CD data", c.pmDept());
        pv.set("PM-EQUIP-ID data", c.pmEquipment());
        pv.set("PM-LOC data", c.pmLocation());
        pv.set("PM-CYCLE-CD data", c.pmCycle());
        pv.set("DATE-PM-SCHD data", c.pmScheduled());
        pv.set("MAX-ROWS data", MAX);
        pv.set("CURRENT-ROW data", row);
        return s;
    }

    static final Map<String, String[]> ADD = Map.of("repair", new String[] {"WS13A07", "WS13A08", "WS13A08_CRIT"},
            "incident", new String[] {"WS13A04", "WSX2A01", "WSX2A01_CRIT"}, "pm", new String[] {"WS13A05", "WSX3A", "WSX3A_CRIT"});
    static final Map<String, String> STEP = Map.of("repair", "WS13A06", "incident", "WS13A02", "pm", "WS13A01");

    @ParameterizedTest
    @CsvSource({"repair", "incident", "pm"})
    void addingANarrativeMatchesTheOldScreen(String kind) {
        Context c = context(kind);
        String[] pr = ADD.get(kind);
        Parity p = backend();
        ScreenSession s = old(p, c, MAX);
        s.event(windowId(s), List.of(new ScreenSession.Change("mleNewNarrative", "object", "CHECKED THE SWITCH")), "pbtAddNarrative", "clicked", null);
        var oldAdd = p.lastSent(pr[0]);
        var oldLatest = p.lastSent(pr[1]).get(pr[2]);
        assertThat(oldAdd).as("old " + pr[0]).isNotNull();

        p.clear();
        var n = api(p).add(c, "CHECKED THE SWITCH", p.user.groupId());
        assertThat(p.lastSent(pr[0])).as(pr[0]).isEqualTo(oldAdd);
        assertThat(p.lastSent(pr[1]).get(pr[2])).as(pr[1]).isEqualTo(oldLatest);
        assertThat(n.text()).isEqualTo("LINE " + MAX);
        assertThat(n.row()).isEqualTo(MAX);
    }

    @ParameterizedTest
    @CsvSource({"repair,pbtupnarr,2", "repair,pbtdownnarr,2", "incident,pbtupnarr,1", "incident,pbtdownnarr,3", "pm,pbtupnarr,3", "pm,pbtdownnarr,1",
            "pm,pbtdownnarr,3"})
    void prvAndNxtMatchTheOldScreen(String kind, String button, int row) {
        Context c = context(kind);
        Parity p = backend();
        ScreenSession s = old(p, c, row);
        s.event(windowId(s), List.of(), button, "clicked", null);
        String program = STEP.get(kind);
        var oldCrit = p.lastSent(program).get(program + "_CRIT");

        p.clear();
        var n = api(p).step(c, button.equals("pbtupnarr") ? "prev" : "next", row, MAX);
        assertThat(p.lastSent(program).get(program + "_CRIT")).as(program).isEqualTo(oldCrit);
        assertThat(n.row()).isEqualTo(((Number) oldCrit.get("narrNo")).intValue());
        assertThat(n.text()).isEqualTo("LINE " + n.row());
    }
}
