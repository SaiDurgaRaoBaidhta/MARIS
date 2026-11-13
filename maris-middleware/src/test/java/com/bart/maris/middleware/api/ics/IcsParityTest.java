package com.bart.maris.middleware.api.ics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.ics.IcsCriteria;
import com.bart.maris.middleware.domain.ics.IcsOptions;
import com.bart.maris.middleware.domain.ics.IcsReport;
import com.bart.maris.middleware.domain.ics.IcsReportService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The ICS clean API against the old VHIC30I / VHIC31I screens run by the engine: same backend, same requests, same rows. */
class IcsParityTest {
    private Parity p;
    private IcsReportService api;

    @BeforeEach
    void backend() {
        p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ", "vtamName", "DUBLNHP1"), Map.of("prtrName", "LKS11HP1", "prtrLocDesc", "IT", "vtamName", "LKS11HP1")))));
        p.on("VHIC30A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIC30A_DATA", Map.of("revDate", "11-12-2026", "revDatedb2", "2026-11-12")));
        p.on("VHRT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHRT04A_DATA", Map.of("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "HY", "yrdDesc", "HAYWARD YARD")))));
        p.on("VHIC30B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIC30B_DATA", Map.of("dataRow", List.of(
                Map.of("locIcs", "A07", "locIcsDesc", "A05, A07 AND A09"), Map.of("locIcs", "C55", "locIcsDesc", "CONCORD")))));
        p.on("VHIC30C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIC30C_DATA", Map.of("dataRow", List.of(
                Map.of("msgTranCd", "1A", "msgTranCdDesc", "IDENTIFY TRAIN"), Map.of("msgTranCd", "1F", "msgTranCdDesc", "TRAIN DISPATCH")))));
        p.on("VHIC31A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIC31A_DATA", Map.of("dataRow", List.of(
                Map.of("tsDate", "11-10-2026", "tsTime", "00:58:14", "conNo", "532-01", "dashNo", 1, "msgTranCd", "1F", "conCurrLoc", "C55"),
                Map.of("tsDate", "11-10-2026", "tsTime", "01:04:32", "conNo", "446-00", "dashNo", 0, "msgTranCd", "1A", "conCurrLoc", "M97")))));
        p.on("VHIC30E", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        api = new IcsReportService(p.legacy(), p.messages(), p.clock, Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> widget(Map<String, Object> state, String screen, String name) {
        for (Map<String, Object> w : (List<Map<String, Object>>) state.get("windows")) {
            if (w.get("screen").equals(screen)) return (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
        }
        throw new AssertionError(screen + " is not open");
    }

    @SuppressWarnings("unchecked")
    private static String windowId(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> w.get("screen").equals(screen))
                .map(w -> (String) w.get("id")).findFirst().orElseThrow();
    }

    private ScreenSession openCriteria() {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIC30I", p.user);
        return old;
    }

    /** an old drop-down item "CY  - CONCORD YARD" as "CY|CONCORD YARD" */
    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> list) {
        return ((List<String>) list.get("items")).stream().map(i -> {
            String[] kv = i.split("\\s+-\\s+", 2);
            return kv[0].strip() + "|" + (kv.length > 1 ? kv[1].strip() : "");
        }).toList();
    }

    private static int indexOf(Map<String, Object> list, String code) {
        @SuppressWarnings("unchecked") List<String> items = (List<String>) list.get("items");
        for (int i = 0; i < items.size(); i++) if (items.get(i).startsWith(code + " ")) return i + 1;
        throw new AssertionError(code + " not in " + items);
    }

    @Test
    void optionsMatchTheOldDropDownsAndPrinterList() {
        ScreenSession old = openCriteria();
        Map<String, Object> st = old.state();
        IcsOptions o = api.options();
        assertThat(codes(widget(st, "VHIC30I", "Drop-down_List1"))).isEqualTo(o.yards().stream().map(c -> c.code() + "|" + c.description()).toList());
        assertThat(codes(widget(st, "VHIC30I", "Drop-down_List3"))).isEqualTo(o.locations().stream().map(c -> c.code() + "|" + c.description()).toList());
        assertThat(codes(widget(st, "VHIC30I", "Drop-down_List2"))).isEqualTo(o.messageCodes().stream().map(c -> c.code() + "|" + c.description()).toList());
        assertThat(widget(st, "VHIC30I", "Container_Details1").get("rows"))
                .isEqualTo(o.printers().stream().map(x -> List.of(x.description(), x.vtam())).toList());
    }

    @Test
    void displayWithTheDefaultsSendsTheSameCriteria() {
        ScreenSession old = openCriteria();
        old.event(windowId(old, "VHIC30I"), List.of(new ScreenSession.Change("Text3", "object", "11-10-2026")), "Push_Button3", "clicked", null);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHIC31A");
        Map<String, Object> oldRows = widget(old.state(), "VHIC31I", "VAGen_Container_Details_DATA_ROW");

        p.clear();
        IcsReport r = api.messages(new IcsCriteria("2026-11-10", null, null, null, null, null));
        assertThat(p.lastSent("VHIC31A").get("VHIC31A_CRIT")).isEqualTo(oldSent.get("VHIC31A_CRIT"));
        @SuppressWarnings("unchecked") List<List<String>> rows = (List<List<String>>) oldRows.get("rows");
        assertThat(r.messages().stream().map(IcsReport.Message::consist).toList()).isEqualTo(rows.stream().map(x -> x.get(3)).toList());
    }

    @Test
    void displayForOneLocationAndCodeInConsistOrderSendsTheSameCriteria() {
        ScreenSession old = openCriteria();
        String w = windowId(old, "VHIC30I");
        Map<String, Object> st = old.state();
        old.event(w, List.of(), "Radio_Button_Set1", "select", 2);                       // consist order
        old.event(w, List.of(), "Radio_Button_Set2", "select", 2);                       // by location
        old.event(w, List.of(), "Drop-down_List3", "select", indexOf(widget(st, "VHIC30I", "Drop-down_List3"), "A07"));
        old.event(w, List.of(), "Drop-down_List2", "select", indexOf(widget(st, "VHIC30I", "Drop-down_List2"), "1F"));
        old.event(w, List.of(new ScreenSession.Change("Text3", "object", "11-10-2026")), "Push_Button3", "clicked", null);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHIC31A");

        p.clear();
        api.messages(new IcsCriteria("2026-11-10", "consist", "location", null, "A07", "1F"));
        assertThat(p.lastSent("VHIC31A").get("VHIC31A_CRIT")).isEqualTo(oldSent.get("VHIC31A_CRIT"));
    }

    @Test
    void hardCopySendsTheSamePrintRequest() {
        ScreenSession old = openCriteria();
        String w = windowId(old, "VHIC30I");
        old.event(w, List.of(), "Container_Details1", "select", 1);                       // printer DUBLIN TMZ
        old.event(w, List.of(new ScreenSession.Change("Text3", "object", "11-10-2026")), "Push_Button2", "clicked", null);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHIC30E");
        assertThat(oldSent).as("the old screen printed").isNotNull();

        p.clear();
        api.print(new IcsCriteria("2026-11-10", null, null, null, null, null), "DUBLNHP1");
        assertThat(p.lastSent("VHIC30E").get("VHIC30E_CRIT")).isEqualTo(oldSent.get("VHIC30E_CRIT"));
    }
}
