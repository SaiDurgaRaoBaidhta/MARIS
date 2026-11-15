package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Change;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Criteria;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Entry;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Event;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Vehicle parts status / mod tracking (VHVM11I) against the old screen run by the engine, on the same scripted backend. */
class VehicleMaintTrackingParityTest {
    static Map<String, Object> row(String car, String type, String sub, String stat, String init, String ref, String incd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actvInd", "Y");
        m.put("carNo", car);
        m.put("dateClosed", "");
        m.put("dateInitialized", init);
        m.put("dateLastUpd", "11-01-2026");
        m.put("eventComment", "C " + car);
        m.put("eventStat", stat);
        m.put("eventSubType", sub);
        m.put("eventType", type);
        m.put("incdNo", incd);
        m.put("locCmpltEvent", "HY");
        m.put("timeInitialized", "10.00.00");
        m.put("trkgRefNo", ref);
        m.put("homeYrd", "HY");
        m.put("loc", "HY-T12");
        m.put("vehStat", "NO GO");
        return m;
    }

    static Parity backend(String batterySwitch) {
        Parity p = new Parity();
        p.on("VHVMDTA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVMDTA_CRIT", Map.of("currentDate", "11-14-2026", "currentDate8", "20261114",
                "currentTime", "10:00:00")));
        p.on("VHVM11E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 5), "VHVM11E_DATA", Map.of("dataRow", List.of(Map.of("eventType", "ALL"),
                Map.of("eventType", "AWP", "eventSubTypeDsc", "AWAITING"), Map.of("eventType", "AWP", "eventSubType", "1", "eventSubTypeDsc", "AWAITING PARTS ONE TWO THREE"),
                Map.of("eventType", "BAT", "eventSubTypeDsc", "BATTERY"), Map.of("eventType", "MOD", "eventSubType", "A", "eventSubTypeDsc", "MODIFICATION")))));
        p.on("VHVM11F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHVM11F_CRIT", Map.of("beginDate", "01-01-2025"), "VHVM11F_DATA",
                Map.of("dataRow", List.of(Map.of("eventStat", "ALL"), Map.of("eventStat", "OPEN"), Map.of("eventStat", "CLOSED")))));
        p.on("VHVM11G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM11G_DATA", Map.of("dataRow", List.of(Map.of("yrd", "ALL"), Map.of("yrd", "HY")))));
        p.on("VHVM11A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM11A_DATA", Map.of("dataRow", List.of(
                row("1101", "AWP", "1", "OPEN", "10-01-2026", "123456789", ""), row("1102", "BAT", "", "OPEN", "10-02-2026", "", "I0000001")))));
        p.on("VHVM11H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM11H_DATA", Map.of("eventComment", "FROM H", "carType", "C2",
                "batteryReplSw", batterySwitch)));
        p.on("VHVM11C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM11B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM11J", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM11K", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHVM11K_DATA", Map.of("incdNo", "T0000077")));
        p.on("VHVM1A5", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleMaintTrackingService api(Parity p) {
        return new VehicleMaintTrackingService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Parity.bundle(), "MARIS-UI");
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

    @SuppressWarnings("unchecked")
    static void answer(ScreenSession s, String button) {
        Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
        assertThat(d).as("a question is open").isNotNull();
        s.answer((Integer) d.get("id"), button);
    }

    static ScreenSession opened(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHVM11I", p.user);
        return s;
    }

    /** the old screen listed and one row selected */
    static ScreenSession selected(Parity p, int row) {
        ScreenSession s = opened(p);
        s.event(id(s), List.of(), "pbtList", "clicked", null);
        s.event(id(s), List.of(), "hptdata", "select", row);
        p.clear();
        return s;
    }

    static List<Object> sent(Parity p, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : p.sent) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    static Event first(Parity p, int row) {
        return api(p).list(new Criteria("01-01-2025", "11-14-2026", null, null, null, null, null, null, null, null, null)).events().get(row - 1);
    }

    @Test
    void optionsMatchTheOpenedScreen() {
        Parity p = backend("");
        ScreenSession s = opened(p);
        var o = api(p).options("BRIGHTCO", null);
        assertThat(o.searchEvents().stream().map(VehicleMaintTrackingService.EventChoice::label).toList()).isEqualTo(widget(s, "Drop-down_List1").get("items"));
        assertThat(o.entryEvents().stream().map(VehicleMaintTrackingService.EventChoice::label).toList()).isEqualTo(widget(s, "ddlnewloc").get("items"));
        assertThat(o.statuses()).isEqualTo(widget(s, "Drop-down_List2").get("items"));
        assertThat(o.entryStatuses()).isEqualTo(widget(s, "ddldd").get("items"));
        assertThat(o.locations()).isEqualTo(widget(s, "Drop-down_List5").get("items"));
        assertThat(o.dateFrom()).isEqualTo(value(s, "Text12"));
        assertThat(o.dateTo()).isEqualTo(value(s, "txtvalidtime"));
        assertThat(o.queryOnly()).isEqualTo(Boolean.FALSE.equals(widget(s, "pbtAdd").getOrDefault("visible", true)));
    }

    @Test
    void listSendsTheSameCriteria() {
        Parity p = backend("");
        ScreenSession s = opened(p);
        p.clear();
        s.event(id(s), List.of(sc("Text7", "1101"), sc("Text6", "12-34-56789")), "Drop-down_List3", "select", 7);
        s.event(id(s), List.of(), "Drop-down_List4", "select", 4);
        s.event(id(s), List.of(), "Drop-down_List1", "select", 3);
        s.event(id(s), List.of(), "Drop-down_List2", "select", 2);
        s.event(id(s), List.of(), "Radio_Button_Set1", "select", 2);
        s.event(id(s), List.of(), "pbtList", "clicked", null);
        var oldCrit = sent(p, "VHVM11A", "VHVM11A_CRIT");
        p.clear();
        var l = api(p).list(new Criteria("01-01-2025", "11-14-2026", "1101", "C2", "HY", "AWP", "1", "OPEN", null, "12-34-56789", "2"));
        assertThat(sent(p, "VHVM11A", "VHVM11A_CRIT")).isEqualTo(oldCrit);
        assertThat(l.events()).hasSize(2);
        @SuppressWarnings("unchecked") List<List<Object>> rows = (List<List<Object>>) widget(s, "hptdata").get("rows");
        assertThat(l.events().get(0).trackingRefNo()).isEqualTo(String.valueOf(rows.get(0).get(4)).strip());
        assertThat(l.events().get(1).incident()).isEqualTo(String.valueOf(rows.get(1).get(10)).strip());
    }

    @Test
    void updateSendsTheSameRecord() {
        Parity p = backend("");
        ScreenSession s = selected(p, 1);
        s.event(id(s), List.of(sc("Text10", "NEW COMMENT")), "pbtUpdate", "clicked", null);
        var old = sent(p, "VHVM11C", "VHVM11C_DATA");
        var oldCrit = sent(p, "VHVM11C", "VHVM11C_CRIT");
        String oldMsg = value(s, "Text13");
        Event e = first(p, 1);
        p.clear();
        var r = api(p).update(new Change(e, null, null, null, null, null, null, "NEW COMMENT", null, null, null, null));
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEqualTo(old);
        assertThat(sent(p, "VHVM11C", "VHVM11C_CRIT")).isEqualTo(oldCrit);
        assertThat(r.message()).isEqualTo(oldMsg);
    }

    @Test
    void closeButtonAsksThenSendsTheSameRecord() {
        Parity p = backend("");
        ScreenSession s = selected(p, 1);
        s.event(id(s), List.of(sc("Text3", "110126")), "Push_Button1", "clicked", null);
        @SuppressWarnings("unchecked") String question = (String) ((Map<String, Object>) s.state().get("dialog")).get("message");
        answer(s, "Yes");
        var old = sent(p, "VHVM11C", "VHVM11C_DATA");
        var oldCrit = sent(p, "VHVM11C", "VHVM11C_CRIT");
        String oldMsg = value(s, "Text13");
        Event e = first(p, 1);
        p.clear();
        var svc = api(p);
        Change c = new Change(e, null, null, null, "110126", null, null, null, null, null, null, null);
        assertThat(svc.close(c).question()).isEqualTo(question);
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEmpty();
        var r = svc.close(new Change(e, null, null, null, "110126", null, null, null, null, null, null, true));
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEqualTo(old);
        assertThat(sent(p, "VHVM11C", "VHVM11C_CRIT")).isEqualTo(oldCrit);
        assertThat(r.message()).isEqualTo(oldMsg);
    }

    @Test
    void batteryClosedThroughUpdateAsksAndUpdatesTheCar() {
        Parity p = backend("");
        ScreenSession s = selected(p, 2);
        String status = value(s, "ddldd");
        s.event(id(s), List.of(sc("Text3", "11-02-2026")), "pbtUpdate", "clicked", null);
        @SuppressWarnings("unchecked") String question = (String) ((Map<String, Object>) s.state().get("dialog")).get("message");
        answer(s, "Yes");
        var old = sent(p, "VHVM11C", "VHVM11C_DATA");
        var oldCrit = sent(p, "VHVM11C", "VHVM11C_CRIT");
        var oldJ = sent(p, "VHVM11J", "VHVM11J_DATA");
        String oldMsg = value(s, "Text13");
        Event e = first(p, 2);
        p.clear();
        var svc = api(p);
        var sel = svc.select(e);
        assertThat(status.isEmpty() ? sel.status() : status).isEqualTo(sel.status());
        Change ask = new Change(e, sel.status(), null, null, "11-02-2026", null, null, null, sel.location(), null, null, null);
        assertThat(svc.update(ask).question()).isEqualTo(question);
        var r = svc.update(new Change(e, sel.status(), null, null, "11-02-2026", null, null, null, sel.location(), null, null, true));
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEqualTo(old);
        assertThat(sent(p, "VHVM11C", "VHVM11C_CRIT")).isEqualTo(oldCrit);
        assertThat(sent(p, "VHVM11J", "VHVM11J_DATA")).isEqualTo(oldJ);
        assertThat(r.message()).isEqualTo(oldMsg);
    }

    @Test
    void removeSendsTheSameRecord() {
        Parity p = backend("");
        ScreenSession s = selected(p, 1);
        s.event(id(s), List.of(), "pbtDelete", "clicked", null);
        answer(s, "Yes");
        var old = sent(p, "VHVM11C", "VHVM11C_DATA");
        var oldCrit = sent(p, "VHVM11C", "VHVM11C_CRIT");
        String oldMsg = value(s, "Text13");
        Event e = first(p, 1);
        p.clear();
        var r = api(p).remove(new Change(e, null, null, null, null, null, null, null, null, null, null, true));
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEqualTo(old);
        assertThat(sent(p, "VHVM11C", "VHVM11C_CRIT")).isEqualTo(oldCrit);
        assertThat(r.message()).isEqualTo(oldMsg);
    }

    @Test
    void workAssignCreatesTheSameIncidentAndUpdate() {
        Parity p = backend("");
        ScreenSession s = selected(p, 1);
        s.event(id(s), List.of(), "Push_Button3", "clicked", null);
        var oldK = sent(p, "VHVM11K", "VHVM11K_DATA");
        var old = sent(p, "VHVM11C", "VHVM11C_DATA");
        String oldMsg = value(s, "Text13");
        Event e = first(p, 1);
        p.clear();
        var r = api(p).workAssign(new Change(e, null, null, null, null, null, null, null, null, null, null, null), p.user.groupId());
        assertThat(sent(p, "VHVM11K", "VHVM11K_DATA")).isEqualTo(oldK);
        assertThat(sent(p, "VHVM11C", "VHVM11C_DATA")).isEqualTo(old);
        assertThat(r.message()).isEqualTo(oldMsg);
        assertThat(r.incident()).isEqualTo("T0000077");
    }

    @Test
    void addSendsTheSameRecord() {
        Parity p = backend("");
        ScreenSession s = opened(p);
        s.event(id(s), List.of(), "pbtClear", "clicked", null);
        s.event(id(s), List.of(sc("Text5", "1201"), sc("Text2", "R12"), sc("Text10", "NEW ONE"),
                sc("Text1", "11-10-2026")), "ddlnewloc", "select", 3);
        s.event(id(s), List.of(), "ddldd", "select", 1);
        p.clear();
        s.event(id(s), List.of(), "pbtAdd", "clicked", null);
        var old = sent(p, "VHVM11B", "VHVM11B_DATA");
        String oldMsg = value(s, "Text13");
        p.clear();
        var r = api(p).add(new Entry("1201", "OPEN", "MOD", "A", "R12", "11-10-2026", null, null, "NEW ONE"));
        assertThat(sent(p, "VHVM11B", "VHVM11B_DATA")).isEqualTo(old);
        assertThat(r.message()).isEqualTo(oldMsg);
    }

    @Test
    void checksGiveTheOldMessages() {
        Parity p = backend("");
        ScreenSession s = opened(p);
        s.event(id(s), List.of(), "pbtAdd", "clicked", null);
        String missing = value(s, "Text13");
        assertThat(message(() -> api(p).add(new Entry("", "", "", "", "", "", "", "", "")))).isEqualTo(missing);
        s.event(id(s), List.of(sc("Text5", "")), "Text5", "losingFocus", null);
        String carRequired = value(s, "Text13");
        assertThat(message(() -> api(p).checkCar(""))).isEqualTo(carRequired);
        p.on("VHVM1A5", in -> Map.of("XDCONTROL", Map.of("urc", 100)));
        s.event(id(s), List.of(sc("Text5", "9999")), "Text5", "losingFocus", null);
        assertThat(message(() -> api(p).checkCar("9999"))).isEqualTo(value(s, "Text13"));
        ScreenSession u = selected(p, 1);
        u.event(id(u), List.of(sc("Text3", "11-02-2026")), "pbtUpdate", "clicked", null);
        Event e = first(p, 1);
        assertThat(message(() -> api(p).update(new Change(e, null, null, null, "11-02-2026", null, null, null, null, null, null, null))))
                .isEqualTo(value(u, "Text13"));
        ScreenSession f = selected(p, 1);
        f.event(id(f), List.of(sc("Text3", "13-02-2026")), "Push_Button1", "clicked", null);
        assertThat(message(() -> api(p).close(new Change(e, null, null, null, "13-02-2026", null, null, null, null, null, null, null))))
                .isEqualTo(value(f, "Text13"));
    }

    static ScreenSession.Change sc(String widget, Object value) {
        return new ScreenSession.Change(widget, "object", value);
    }

    static String message(Runnable r) {
        try { r.run(); return null; } catch (InvalidRequestException e) { return e.getMessage(); }
    }
}
