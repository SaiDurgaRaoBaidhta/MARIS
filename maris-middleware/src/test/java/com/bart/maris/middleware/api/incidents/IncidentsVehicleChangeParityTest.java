package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleMaintainService;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleUpdateService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Changing an incident: VHINC09I (from the VHMVSI menu) and VHINC10I (from VHMMMI) against the old screens run by the
 * engine. List must show the same incident, Update must send the same VHINC1C / VHINCXC records and give the same message.
 */
class IncidentsVehicleChangeParityTest {
    static final String TERMINAL = IncidentsVehicleCreateParityTest.TERMINAL;

    static Map<String, Object> incident(String no, String type, String status, String car) {
        Map<String, Object> d = new HashMap<>();
        d.put("conNo", "201");
        d.put("carNo", car);
        d.put("dashNo", 3);
        d.put("conDirection", "N");
        d.put("dateIncdOccr", "11-10-2026");
        d.put("dateInsrvRev", "2026-11-10");
        d.put("dateInsrv", "2026-11-10");
        d.put("timeInsrv", "05.00.00");
        d.put("discvDur", "2");
        d.put("incdLoc", "CY01");
        d.put("speed", type.equals("REV") ? "035" : "");
        d.put("deferredPriority", "N");
        d.put("vehIncdStat", status);
        d.put("incdType", type);
        d.put("sympCd", "0120");
        d.put("timeIncdOccr", "09.15.00");
        d.put("dataNarrRow", List.of(Map.of("narrNo", 1, "userGrp", "SHOPCY", "dateNarr", "11-10-2026", "timeNarr", "09.20.00", "narr", "DOOR STUCK"),
                Map.of("narrNo", 2, "userGrp", "VEHSUPR", "dateNarr", "11-11-2026", "timeNarr", "10.00.00", "narr", "STILL STUCK")));
        d.put("dateLastUpd", "2026-11-11");
        d.put("timeLastUpd", "10.00.00");
        d.put("termLastUpd", "TERM01");
        d.put("cumHrsMin", Map.of("cumHrsChar", Map.of("cumHrs", 12), "cumFiller", ":", "cumMinChar", Map.of("cumMin", 30)));
        return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINC1D_CRIT", Map.of("incdNo", no, "retCheck", "BOTH"), "VHINC1D_DATA", d);
    }

    static Parity backend(Supplier<Map<String, Object>> incident) {
        Parity p = new Parity();
        p.on("VHINC1D", in -> incident.get());
        p.on("VHINC9A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINC9A_DATA", Map.of("dispDataRow", "1101 1102 1103")));
        p.on("VHINC1A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC1A_CRIT", Map.of("vagenCount",
                "9999".equals(in.get("VHINC1A_CRIT").get("sympCd")) ? 0 : 1)));
        p.on("VHINC1C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VHINCXC", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VH01A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VH01A01_DATA", Map.of("userGroup", "BRIGHTCO", "termId", TERMINAL,
                "numVehIncdStat", 3, "vehIncdStatRow", List.of(Map.of("vehIncdStat", "OPN", "vehIncdStatDesc", "OPEN"), Map.of("vehIncdStat", "DEF", "vehIncdStatDesc", "DEFERRED"),
                        Map.of("vehIncdStat", "CLO", "vehIncdStatDesc", "CLOSED")))));
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHXREVA_DATA", Map.of("revenueDate", "11-13-2026")));
        return p;
    }

    static String value(ScreenSession s, String screen, String widget) {
        return IncidentsVehicleCreateParityTest.value(s, screen, widget);
    }

    static String id(ScreenSession s, String screen) {
        return (String) IncidentsVehicleCreateParityTest.window(s, screen).get("id");
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) { var m = new HashMap<>(e.getValue()); m.remove("XDCONTROL"); out.add(m); }
        return out;
    }

    // ---- VHINC09I ------------------------------------------------------------------------------------------------

    static ScreenSession old09(Parity p, String incident) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMVSI", p.user);
        s.event(id(s, "VHMVSI"), List.of(), "Push_Button7", "clicked", null);
        s.event(id(s, "VHINC09I"), List.of(new ScreenSession.Change("txtIncdno", "object", incident)), "pbtList", "clicked", null);
        return s;
    }

    static IncidentsVehicleUpdateService api09(Parity p) {
        return new IncidentsVehicleUpdateService(p.legacy(), p.messages(), Parity.bundle(), TERMINAL);
    }

    @Test
    void vhinc09ListShowsWhatTheOldScreenShows() {
        Parity p = backend(() -> incident("A0012345", "YRD", "OPN", "1101"));
        ScreenSession s = old09(p, "A0012345");
        var r = api09(p).incident("A0012345");
        var d = r.incident();
        assertThat(d.car()).isEqualTo(value(s, "VHINC09I", "txtCarno"));
        assertThat(d.consist()).isEqualTo(value(s, "VHINC09I", "txtCondashno"));
        assertThat(d.carsInConsist()).isEqualTo(value(s, "VHINC09I", "txtCarsincon"));
        assertThat(d.date()).isEqualTo(value(s, "VHINC09I", "txtDateincd"));
        // txtTimeincd shows TIME-INCD-OCCR_of_DISP-DET's HRMIN, a part of a part the engine leaves blank: the API gives HH:MM
        assertThat(d.time()).isEqualTo("09:15");
        assertThat(d.location()).isEqualTo(value(s, "VHINC09I", "txtLocation"));
        assertThat(d.status()).isEqualTo(value(s, "VHINC09I", "txtIncdstat"));
        assertThat(d.lastUpdate().time()).isEqualTo(value(s, "VHINC09I", "txtTimelast"));
        assertThat(d.narratives().get(0).text()).isEqualTo(value(s, "VHINC09I", "mleNarrative"));
        assertThat(d.narratives().get(0).userGroup()).isEqualTo(value(s, "VHINC09I", "txtUsergrp"));
        assertThat(d.narratives().get(0).time()).isEqualTo(value(s, "VHINC09I", "txtDisptimeincd"));
        assertThat(r.editing().details()).isTrue();
        assertThat(r.editing().speedAndDirection()).isFalse();
    }

    @Test
    void vhinc09ListMessagesMatch() {
        for (Supplier<Map<String, Object>> inc : List.<Supplier<Map<String, Object>>>of(() -> incident("A0012345", "YRD", "OPN", "0095"),
                () -> incident("B0012345", "TRN", "OPN", "1101"), () -> Map.of("XDCONTROL", Map.of("urc", 100)))) {
            Parity p = backend(inc);
            ScreenSession s = old09(p, "B0012345");
            String message;
            try { api09(p).incident("B0012345"); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
            assertThat(message).isEqualTo(value(s, "VHINC09I", "txtMsgArea"));
        }
        Parity p = backend(() -> incident("A0012345", "YRD", "OPN", "1101"));
        ScreenSession s = old09(p, "");
        String message;
        try { api09(p).incident(""); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(value(s, "VHINC09I", "txtMsgArea"));
    }

    @Test
    void anOpenHIncidentOnlyTakesNarratives() {
        Parity p = backend(() -> incident("H0012345", "TRN", "OPN", "1101"));
        ScreenSession s = old09(p, "H0012345");
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        var r = api09(p).incident("H0012345");
        assertThat(sent(p.sent, "VHINC9A")).isEqualTo(sent(oldCalls, "VHINC9A"));
        assertThat(r.editing().details()).isFalse();
        assertThat(r.editing().narratives()).isTrue();
        assertThat(r.incident().car()).isEqualTo(value(s, "VHINC09I", "txtCarno"));
    }

    record Typed(String symptom, String speed, String location, String narrative) {}

    String update09(String type, Typed t) {
        Parity p = backend(() -> incident("A0012345", type, "OPN", "1101"));
        ScreenSession s = old09(p, "A0012345");
        List<ScreenSession.Change> ch = new ArrayList<>(List.of(new ScreenSession.Change("txtSympcd", "object", t.symptom()),
                new ScreenSession.Change("txtLocation", "object", t.location()), new ScreenSession.Change("mleNarrative", "object", t.narrative())));
        if (t.speed() != null) ch.add(new ScreenSession.Change("txtSpeed", "object", t.speed()));
        s.event(id(s, "VHINC09I"), ch, "pbtUpdate", "clicked", null);
        String oldMessage = value(s, "VHINC09I", "txtMsgArea");
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        String message;
        try {
            message = api09(p).update("A0012345", new IncidentsVehicleUpdateService.Change(t.symptom(), t.speed(), "N", t.location(), t.narrative(), 0)).get("message");
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(sent(p.sent, "VHINC1C")).isEqualTo(sent(oldCalls, "VHINC1C"));
        assertThat(sent(p.sent, "VHINC1A")).isEqualTo(sent(oldCalls, "VHINC1A"));
        assertThat(message).isEqualTo(oldMessage);
        return message;
    }

    @Test
    void vhinc09UpdateMatches() {
        assertThat(update09("YRD", new Typed("0130", null, "CY02", "DOOR STUCK"))).contains("A0012345");
        update09("YRD", new Typed("0130", null, "CY02", "DOOR STUCK AGAIN"));
        update09("REV", new Typed("0130", "040", "CY02", "DOOR STUCK"));
        update09("REV", new Typed("0130", "ALL", "CY02", "DOOR STUCK"));
    }

    @Test
    void vhinc09CheckMessagesMatch() {
        update09("YRD", new Typed("", null, "", "DOOR STUCK"));
        update09("YRD", new Typed("9999", null, "CY02", "DOOR STUCK"));
        update09("REV", new Typed("0130", "", "CY02", "DOOR STUCK"));
        update09("REV", new Typed("", "4X", "", "DOOR STUCK"));
    }

    // ---- VHINC10I ------------------------------------------------------------------------------------------------

    static ScreenSession old10(Parity p, String incident) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMMMI", p.user);
        s.event(id(s, "VHMMMI"), List.of(), "Push_Button20", "clicked", null);
        assertThat(IncidentsVehicleCreateParityTest.window(s, "VHINC10I")).isNotNull();
        s.event(id(s, "VHINC10I"), List.of(new ScreenSession.Change("txtIncdno", "object", incident)), "pbtList", "clicked", null);
        return s;
    }

    static IncidentsVehicleMaintainService api10(Parity p) {
        return new IncidentsVehicleMaintainService(p.legacy(), p.messages(), Parity.bundle(), TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static String dialog(ScreenSession s) {
        Object d = s.state().get("dialog");
        return d instanceof Map<?, ?> m ? String.valueOf(((Map<String, Object>) m).get("message")).strip() : null;
    }

    @Test
    void vhinc10OptionsAndListMatch() {
        Parity p = backend(() -> incident("A0012345", "SHP", "DEF", "1101"));
        ScreenSession s = old10(p, "A0012345");
        var o = api10(p).options(p.user.groupId());
        @SuppressWarnings("unchecked")
        Map<String, Object> w = (Map<String, Object>) ((Map<String, Object>) IncidentsVehicleCreateParityTest.window(s, "VHINC10I").get("widgets")).get("ddlIncdstat");
        assertThat(o.statuses()).extracting(IncidentsVehicleMaintainService.Status::code).containsExactlyElementsOf(
                ((List<?>) w.get("items")).stream().map(x -> String.valueOf(x).strip()).filter(x -> !x.isEmpty()).toList());
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        var d = api10(p).incident("A0012345");
        // the engine shows VHINC10I's DISP-DET fields (car, consist, symptom...) blank after List; the records sent and the fields it does show are compared
        assertThat(sent(p.sent, "VHINC9A")).isEqualTo(sent(oldCalls, "VHINC9A"));
        assertThat(d.carsInConsist()).isEqualTo(value(s, "VHINC10I", "txtCarsincon"));
        assertThat(d.type()).isEqualTo(value(s, "VHINC10I", "txtIncdType"));
        assertThat(d.lastUpdate().time()).isEqualTo(value(s, "VHINC10I", "txtTimelast"));
        assertThat(d.lastUpdate().terminal()).isEqualTo(value(s, "VHINC10I", "txtTermlast"));
        assertThat(d.consist()).isEqualTo("201-003");
    }

    @Test
    void vhinc10NotFoundMatches() {
        Parity p = backend(() -> Map.of("XDCONTROL", Map.of("urc", 100)));
        ScreenSession s = old10(p, "A0099999");
        String message;
        try { api10(p).incident("A0099999"); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(dialog(s));
    }

    @Test
    void vhinc10UpdateMatches() {
        for (String status : List.of("OPN", "")) {
            Parity p = backend(() -> incident("A0012345", "SHP", status, "1101"));
            ScreenSession s = old10(p, "A0012345");
            s.event(id(s, "VHINC10I"), List.of(new ScreenSession.Change("txtSympcd", "object", "0130"), new ScreenSession.Change("txtLocation", "object", "CY02"),
                    new ScreenSession.Change("txtIncdType", "object", "YRD"), new ScreenSession.Change("txtCumhrs", "object", "15"),
                    new ScreenSession.Change("Text1", "object", "30"),
                    new ScreenSession.Change("mleNarrative", "object", "DOOR FIXED")), "pbtChange", "clicked", null);
            String oldMessage = dialog(s);
            var oldCalls = new ArrayList<>(p.sent);
            p.clear();
            String message;
            try {
                message = api10(p).update("A0012345", new IncidentsVehicleMaintainService.Change(status, "YRD", "0130", "2", "", "N", "CY02", "N", 15L, 30L,
                        "DOOR FIXED", 0)).get("message");
            } catch (InvalidRequestException e) {
                message = e.getMessage();
            }
            assertThat(sent(p.sent, "VHINCXC")).as(status).isEqualTo(sent(oldCalls, "VHINCXC"));
            assertThat(message).as(status).isEqualTo(oldMessage);
        }
    }
}
