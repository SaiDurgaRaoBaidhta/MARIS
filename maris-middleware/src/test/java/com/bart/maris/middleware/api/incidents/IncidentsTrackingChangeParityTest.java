package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeService;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Vehicle tracking change incident (VHCI02I) against the old screen run by the engine: the same choices (action,
 * status, car), List Incds, the same incidents marked in the list, then Change. Both sides must send the same records
 * to every program, writing ones included, and show the same message. The engine has no way to type into a table
 * cell, so the old side's marks are put on its VHCI01D_DATA / VHCI01A_DATA rows directly, as the cell edit would.
 */
class IncidentsTrackingChangeParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final String TODAY = LocalDate.now().format(MDY), YESTERDAY = LocalDate.now().minusDays(1).format(MDY);
    static final Set<String> WRITERS = Set.of("VHCI01F", "VHCI02D", "VHCIP", "VHCIQ", "VHCIR", "VHCIS", "VHCIT", "VHCIU", "VHCIW", "VHCIX", "VHCIY",
            "VHCIZ", "VHINVA", "VHINVB", "VHINXB", "VHINXD");

    static Map<String, Object> row(String incd, String stat, String change) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("incdNo", incd);
        m.put("eventTypeX", Map.of("eventType", "AWP", "eventSubType", "1"));
        m.put("vehIncdStat", stat);
        m.put("incdType", "SHP");
        m.put("sympCd", "0120");
        m.put("change", change);
        m.put("dateIncdOccr", "10-02-2026");
        return m;
    }

    @SuppressWarnings("unchecked")
    static Parity backend(String vehStat, String whereIsCar) {
        Parity p = new Parity();
        p.on("VHCI02G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI02G_CRIT", Map.of("currentDate", TODAY, "currentTime", "23:59:00")));
        p.on("VHCI02B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VHINC6A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("whereIsCar", whereIsCar)));
        p.on("VHCI01B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI01B_DATA", Map.of("whereIsCar", whereIsCar,
                "dispVehStat", Map.of("vehStat", vehStat, "vehStatDesc", "STATUS"), "carNo", "1101", "carType", Map.of("carType1", "C", "carTypeRem", "2"),
                "conNo", "", "currSchdPmNo", 3, "worstCaseRank", 10, "trainIncdInd", "N")));
        p.on("VHCI01D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCI01D_DATA", Map.of("dataRow", List.of(row("S0000101", "OPN", ""),
                row("S0000102", "OPN", "")))));
        p.on("VHCI01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCI01A_DATA", Map.of("dataRow", List.of(row("S0000101", "OPN", ""),
                row("S0000102", "OPN", "")))));
        p.on("VHCI01F", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHCI01F_CRIT", Map.of("carNo", "1101", "validSw", "Y")));
        p.on("AFCXVLD", in -> Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N")));
        p.on("VHINVB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVA_DATA", Map.of("revenueDate", Map.of("revenueDateMon", "10", "revenueDateFiller1", "-",
                "revenueDateDay", "02", "revenueDateFiller2", "-", "revenueDateYr", "2026"))));
        p.on("VHINXA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINXA_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "conNo", "201", "yrdSubLoc", "HY-T12"), Map.of("carNo", "1102", "conNo", "201")))));
        p.on("VHINXB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCI01E", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCI02D", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHCI02D_CRIT", Map.of("updTrk", "N")));
        p.on("VHINXD", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCI01C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        for (String w : List.of("VHCIP", "VHCIQ", "VHCIR", "VHCIS", "VHCIT", "VHCIU", "VHCIW", "VHCIX", "VHCIY", "VHCIZ")) {
            p.on(w, in -> {
                Map<String, Object> t = new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) in.get("VHCI01IW-TEMP-MI"));
                t.put("errFound", "N");
                t.put("statusUpd", "Y");
                t.put("newVehStat", "GO");
                t.put("newVehStatDesc", "GOOD TO GO");
                t.put("finalIncdCount", 1);
                t.put("intoShopUpd", "Y");
                return Map.of("XDCONTROL", Map.of("urc", 0), "VHCI01IW-TEMP-MI", t);
            });
        }
        return p;
    }

    static IncidentsTrackingChangeService api(Parity p) {
        return new IncidentsTrackingChangeService(Parity.bundle(), p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock, "", WRITERS);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String name) {
        Map<String, Object> w = ((List<Map<String, Object>>) s.state().get("windows")).get(0);
        return (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
    }

    static String value(ScreenSession s, String name) {
        Object v = widget(s, name).get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static String id(ScreenSession s) {
        return (String) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("id");
    }

    /** the old screen: action, status, car, List */
    static ScreenSession listed(Parity p, int action, int status, String car) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCI02I", p.user);
        Rec inv = s.window(id(s)).record("VHINVB_CRIT");                  // the engine's sign-on seeding fills every user group field;
        Map<String, Object> v = new LinkedHashMap<>(RecordCodec.decode(inv.layout, inv.data, false));   // the old menus leave this one blank
        v.put("userGrp", "");
        inv.data = RecordCodec.encode(inv.layout, v);
        s.event(id(s), List.of(), "rbtActions", "select", action);
        if (status > 0) s.event(id(s), List.of(), "ddlStatus", "select", status);
        s.event(id(s), List.of(new ScreenSession.Change("txtCarNo", "object", car)), "pbtList", "clicked", null);
        return s;
    }

    /** marks typed into the list's CHANGE cells (the engine cannot edit a table cell, so they go on the record rows) */
    @SuppressWarnings("unchecked")
    static void mark(ScreenSession s, String data, Map<Integer, String> marks) {
        Rec r = s.window(id(s)).record(data);
        Map<String, Object> v = new LinkedHashMap<>(RecordCodec.decode(r.layout, r.data, false));
        List<Object> rows = new ArrayList<>((List<Object>) v.get("dataRow"));
        for (var e : marks.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) rows.get(e.getKey() - 1));
            row.put("change", e.getValue());
            rows.set(e.getKey() - 1, row);
        }
        v.put("dataRow", rows);
        r.data = RecordCodec.encode(r.layout, v);
    }

    static void change(ScreenSession s, String date, String time, String narr, String pmLoc, String pmTeam) {
        s.event(id(s), List.of(new ScreenSession.Change("cntIncdInfo", "selectionIndices", List.of(1)), new ScreenSession.Change("txtStatChgDate", "object", date),
                new ScreenSession.Change("txtStatChgTime", "object", time), new ScreenSession.Change("mleNarrative", "object", narr),
                new ScreenSession.Change("txtPMLoc", "object", pmLoc), new ScreenSession.Change("Text1", "object", pmTeam)), "pbtUpdate", "clicked", null);
    }

    /** every record sent to a program, the narrative's own clock stamp left out */
    @SuppressWarnings("unchecked")
    static Map<String, List<Object>> sent(Parity p) {
        Map<String, List<Object>> out = new LinkedHashMap<>();
        for (var e : p.sent) {
            for (var rec : e.getValue().entrySet()) {
                if (rec.getKey().equals("XDCONTROL")) continue;
                Map<String, Object> r = new LinkedHashMap<>(rec.getValue());
                if (r.get("storeNarr") instanceof Map<?, ?> n) {
                    Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) n);
                    m.remove("storeNarrDate");
                    m.remove("storeNarrTime");
                    r.put("storeNarr", m);
                }
                out.computeIfAbsent(e.getKey() + "/" + rec.getKey(), k -> new ArrayList<>()).add(r);
            }
        }
        return out;
    }

    static List<String> programs(Parity p) {
        return p.sent.stream().map(Map.Entry::getKey).toList();
    }

    @Test
    void optionsMatchTheOpenedScreen() {
        Parity p = backend("NO GO", "YRD");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCI02I", p.user);
        var o = api(p).options(p.user.groupId());
        assertThat(o.actions()).isEqualTo(widget(s, "rbtActions").get("items"));
        assertThat(o.statuses()).isEqualTo(widget(s, "ddlStatus").get("items"));
        assertThat(o.date()).isEqualTo(value(s, "txtStatChgDate"));
        assertThat(o.time()).isEqualTo(value(s, "txtStatChgTime"));
        s.event(id(s), List.of(), "rbtActions", "select", 1);
        Rec w = s.window(id(s)).record("VHCI01IW");
        assertThat(o.defaultStatus()).isEqualTo(String.valueOf(RecordCodec.decode(w.layout, w.data, false).get("status")).strip()).isNotBlank();
    }

    /** action, status index (0 = none), marks on rows, date, time, narrative, PM location, PM team */
    static List<String> same(String vehStat, int action, int status, Map<Integer, String> marks, String date, String time, String narr, String pmLoc, String pmTeam) {
        List<String> statuses = List.of("GO", "NO GO", "PM", "USABLE");
        Parity p = backend(vehStat, "YRD");
        ScreenSession s = listed(p, action, status, "1101");
        String oldListMsg = value(s, "txtMsg");
        mark(s, action == 7 ? "VHCI01A_DATA" : "VHCI01D_DATA", marks);
        Map<String, List<Object>> oldList = sent(p);
        p.clear();
        change(s, date, time, narr, pmLoc, pmTeam);
        Map<String, List<Object>> old = sent(p);
        List<String> oldOrder = programs(p);
        String oldMsg = value(s, "txtMsg");
        String what = "action " + action + " status " + status + " marks " + marks;

        p.clear();
        var svc = api(p);
        String st = status == 0 ? "" : statuses.get(status - 1);
        var l = svc.list(new IncidentsTrackingChangeService.ListRequest(action, st, "1101"), p.user.groupId());
        assertThat(sent(p)).as(what + " list").isEqualTo(oldList);
        assertThat(l.message()).as(what + " list message").isEqualTo(oldListMsg);
        boolean oldEnabled = !oldOrder.isEmpty() || !oldMsg.equals(oldListMsg);   // a disabled Change does nothing
        assertThat(l.changeEnabled()).as(what + " change enabled").isEqualTo(oldEnabled);
        if (!l.changeEnabled()) return List.of();                         // the old screen's Change stays disabled
        Map<String, String> changes = new LinkedHashMap<>();
        for (var e : marks.entrySet()) if (e.getKey() <= l.rows().size()) changes.put(l.rows().get(e.getKey() - 1).incident(), e.getValue());
        p.clear();
        var r = svc.change(new IncidentsTrackingChangeService.ChangeRequest(action, st, "1101", changes, date, time, narr, pmLoc, pmTeam,
                l.rows().isEmpty() ? null : l.rows().get(0).incident()), p.user.groupId());
        Map<String, List<Object>> now = sent(p);
        for (String w : WRITERS) {
            for (String k : old.keySet()) if (k.startsWith(w + "/")) assertThat(now.get(k)).as(what + " " + k).isEqualTo(old.get(k));
        }
        assertThat(programs(p).stream().filter(WRITERS::contains).toList()).as(what + " writers").isEqualTo(oldOrder.stream().filter(WRITERS::contains).toList());
        assertThat(r.message()).as(what + " message").isEqualTo(oldMsg);
        return oldOrder.stream().filter(WRITERS::contains).toList();
    }

    @Test
    void carStatusGoSendsTheSameRecords() {
        assertThat(same("NO GO", 1, 1, Map.of(1, "Y"), YESTERDAY, "0930", "FIXED", "", "")).contains("VHCIY", "VHCI02D", "VHINXD");
    }

    @Test
    void carStatusNoGoAndUsableSendTheSameRecords() {
        assertThat(same("GO", 1, 2, Map.of(1, "Y", 2, "N"), YESTERDAY, "0930", "BROKEN", "", "")).isNotEmpty();
        assertThat(same("NO GO", 1, 4, Map.of(2, "YES"), YESTERDAY, "0930", "OK", "", "")).contains("VHCIW");
    }

    @Test
    void carStatusPmSendsTheSameRecords() {
        assertThat(same("NO GO", 1, 3, Map.of(1, "Y"), YESTERDAY, "0930", "PM DUE", "HY", "")).isNotEmpty();
    }

    @Test
    void theOtherActionsSendTheSameRecords() {
        for (int action = 2; action <= 6; action++) {
            assertThat(same("NO GO", action, 0, Map.of(1, action == 2 ? "A" : "Y"), YESTERDAY, "0930", "NARR", "", "")).as("action " + action).isNotEmpty();
        }
    }

    @Test
    void moveSendsTheSameRecords() {
        assertThat(same("NO GO", 7, 0, Map.of(1, "1102"), YESTERDAY, "0930", "", "", "")).contains("VHCIX");
    }

    @Test
    void checksGiveTheOldMessages() {
        same("NO GO", 1, 1, Map.of(1, "X"), YESTERDAY, "0930", "N", "", "");                       // invalid response
        same("NO GO", 1, 1, Map.of(1, "Y"), "", "0930", "N", "", "");                                // invalid date
        same("NO GO", 1, 1, Map.of(1, "Y"), LocalDate.now().plusDays(2).format(MDY), "0930", "N", "", "");
        same("NO GO", 1, 1, Map.of(1, "Y"), YESTERDAY, "2599", "N", "", "");                         // invalid time
        same("NO GO", 1, 3, Map.of(1, "Y"), YESTERDAY, "0930", "N", "XX", "");                       // invalid PM location
        same("NO GO", 1, 3, Map.of(1, "Y"), YESTERDAY, "0930", "", "HY", "");                        // narrative required
        same("NO GO", 1, 1, Map.of(1, "Y"), YESTERDAY, "0930", "N", "", "7");                        // invalid PM team
        same("GO", 1, 1, Map.of(1, "Y"), YESTERDAY, "0930", "N", "", "");                            // GO on a GO car
    }
}
