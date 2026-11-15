package com.bart.maris.middleware.api.repair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.repair.RepairService;
import com.bart.maris.middleware.domain.repair.RepairService.Screen;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Repair (VHRV01I, VHRV02I, VHRV03I, VHRV04I): the old screens, run by the engine, and the clean API against the same
 * scripted backend. For every add, change, close and delete the records sent to the writing program must be the same,
 * and for every refused input the message must be the same.
 */
@SuppressWarnings("unchecked")
class RepairParityTest {
    static final String INCD = "29990010", EVENT = "E2990010";
    static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));

    /** the scripted backend; {@code incident} is what VHINC2D answers (status, type, rank) */
    static Parity backend(Map<String, Object> incident) {
        Parity p = new Parity();
        on(p, "VHRV01C", in -> Map.of("XDCONTROL", ok(3), "VHRV01C_DATA", Map.of("reprCauseCount", 3, "dataRow", List.of(
                Map.of("reprCauseCd", "A", "reprCauseCdDesc", "ADJUSTED"), Map.of("reprCauseCd", "B", "reprCauseCdDesc", "BROKEN"),
                Map.of("reprCauseCd", "W", "reprCauseCdDesc", "WORN")))));
        on(p, "VHRV01D", in -> Map.of("XDCONTROL", ok(2), "VHRV01D_DATA", Map.of("reprDispCount", 2, "dataRow", List.of(
                Map.of("reprDispCd", "R", "reprDispCdDesc", "REPLACED"), Map.of("reprDispCd", "F", "reprDispCdDesc", "FIXED")))));
        on(p, "VHINC2D", in -> Map.of("XDCONTROL", ok(1), "VHINC2D_DATA", incident));
        on(p, "VHRV01A", in -> Map.of("XDCONTROL", ok(2),
                "VHRV01A_CRIT", Map.of("incdNo", String.valueOf(in.get("VHRV01A_CRIT").get("incdNo")), "reprCount", 2, "reprNarrCount", 2),
                "VHRV01A_DATA", Map.of("dataReprRow", List.of(
                        repairRow(1, "DR", "01", "A", "10-02-2026", "10:15:00", "HY", "12345", "P1234561"),
                        repairRow(2, "BR", "02", "B", "10-03-2026", "11:30:00", "CY", "54321", ""))),
                "VHRV01A_NARR_DATA", Map.of("dataNarrRow", List.of(
                        Map.of("reprDocNo", 1, "narrNo", 1, "narr", "DOOR ADJUSTED"), Map.of("reprDocNo", 2, "narrNo", 1, "narr", "BRAKE PADS")))));
        on(p, "VHRV01F", in -> Map.of("XDCONTROL", ok(2), "VHRV01F_DATA", Map.of("dataRow", List.of(
                Map.of("narrNo", 1, "userGrp", "BRIGHTCO", "dateNarr", "10-02-2026", "timeNarr", "10:20:00", "narr", "DOOR ADJUSTED"),
                Map.of("narrNo", 2, "userGrp", "BRIGHTCO", "dateNarr", "10-02-2026", "timeNarr", "10:40:00", "narr", "TESTED OK")))));
        on(p, "VHRV01E", in -> Map.of("XDCONTROL", ok(1), "VHRV01E_CRIT", Map.of("vehSystem", "DR", "vehSubSystem", "01",
                "rowCount", "ZZ".equals(in.get("VHRV01E_CRIT").get("vehSystem")) ? 0 : 1)));
        on(p, "AFCXVLD", in -> Map.of("XDCONTROL", ok(1), "AFCXVLD_CRIT", Map.of("formDate", String.valueOf(in.get("AFCXVLD_CRIT").get("formDate")),
                "dateError", String.valueOf(in.get("AFCXVLD_CRIT").get("formDate")).startsWith("2026-13") ? "Y" : "N", "otherSqlError", "N")));
        on(p, "VHRV01B", in -> Map.of("XDCONTROL", ok(1), "VHRV01B_CRIT", Map.of("incdNo", INCD, "errFound", "N")));
        on(p, "VHRV03A", in -> Map.of("XDCONTROL", ok(1), "VHRV03A_CRIT", Map.of("incdNo", EVENT, "errFound", "N")));
        on(p, "VHRV4A1", in -> Map.of("XDCONTROL", ok(1), "VHRV4A1_DATA", in.get("VHRV4A1_DATA")));
        on(p, "VHRV4A2", in -> Map.of("XDCONTROL", ok(1)));
        on(p, "VHRV02A", in -> Map.of("XDCONTROL", ok(2), "VHRV02A_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntDocNo", 1, "cmpntCd", "DOR", "cmpntType", "01", "cmpntFaultCd", "F1", "reprDispCd", "R", "serNoOn", "ON1", "serNoOff", "OFF1",
                        "dateRepr", "10-02-2026"),
                Map.of("cmpntDocNo", 2, "cmpntCd", "MOT", "cmpntType", "02", "cmpntFaultCd", "", "reprDispCd", "F", "serNoOn", "", "serNoOff", "",
                        "dateRepr", "10-02-2026")))));
        on(p, "VHRV02F", in -> {
            Map<String, Object> d = new HashMap<>(in.get("VHRV02F_DATA"));
            d.put("rowCount", "XXX".equals(d.get("cmpntCd")) ? 0 : 1);
            return Map.of("XDCONTROL", ok(1), "VHRV02F_DATA", d);
        });
        on(p, "VHRV02E", in -> Map.of("XDCONTROL", ok("BAD".equals(in.get("VHRV02E_DATA").get("cmpntFaultCd")) ? 0 : 1)));
        for (String w : List.of("VHRV02B", "VHRV02C", "VHRV02D")) on(p, w, in -> Map.of("XDCONTROL", ok(1)));
        return p;
    }

    static void on(Parity p, String program, Function<Map<String, Map<String, Object>>, Map<String, Object>> answer) {
        p.on(program, in -> echo(in, answer.apply(in)));
    }

    /** MARIS hands XDCONTROL back with what was sent (UFUNCTION stays), only the return code and row count changed */
    @SuppressWarnings("unchecked")
    static Map<String, Object> echo(Map<String, Map<String, Object>> in, Map<String, Object> out) {
        Map<String, Object> o = new LinkedHashMap<>(out);
        Map<String, Object> x = new LinkedHashMap<>(in.getOrDefault("XDCONTROL", Map.of()));
        x.putAll((Map<String, Object>) out.getOrDefault("XDCONTROL", Map.of()));
        o.put("XDCONTROL", x);
        return o;
    }

    static Map<String, Object> ok(int rows) {
        return Map.of("urc", 0, "urowcnt", rows);
    }

    static Map<String, Object> incident(String status, String type, int rank) {
        return Map.of("carNo", "1101", "conNo", "201", "dashNo", 3, "vehIncdStat", status, "incdType", type, "worstCaseRank", rank,
                "dateIncdOccr", "10-01-2026");
    }

    static Map<String, Object> repairRow(int doc, String sys, String sub, String cause, String date, String time, String loc, String empl, String pct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("incdNo", INCD);
        m.put("reprDocNo", doc);
        m.put("reprCauseCd", cause);
        m.put("serNoOn", "SON" + doc);
        m.put("serNoOff", "SOFF" + doc);
        m.put("deleteInd", "");
        m.put("vehPrtCtlNo", pct);
        m.put("vehSystem", sys);
        m.put("vehSubSystem", sub);
        m.put("reprDispCd", "R");
        m.put("warReprInd", "N");
        m.put("reprLaborHrs", 2);
        m.put("emplNo", empl);
        m.put("reprLoc", loc);
        m.put("dateRepr", date);
        m.put("timeRepr", time);
        return m;
    }

    static RepairService api(Parity p) {
        return new RepairService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock, "MARIS-UI");
    }

    // ---- driving the old screens -----------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s, String screen) {
        Map<String, Object> w = window(s, screen);
        assertThat(w).as(screen + " open").isNotNull();
        return (String) w.get("id");
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

    /** the message boxes shown, each answered with {@code button} */
    @SuppressWarnings("unchecked")
    static List<String> dialogs(ScreenSession s, String button) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
            if (d == null) break;
            out.add(String.valueOf(d.get("message")).strip());
            s.answer((Integer) d.get("id"), button);
        }
        return out;
    }

    static void type(ScreenSession s, String screen, Map<String, String> fields, String button) {
        List<ScreenSession.Change> ch = new ArrayList<>();
        fields.forEach((k, v) -> ch.add(new ScreenSession.Change(k, "object", v)));
        s.event(id(s, screen), ch, button, button == null ? null : "clicked", null);
    }

    static void select(ScreenSession s, String screen, String table, int row) {
        s.event(id(s, screen), List.of(new ScreenSession.Change(table, "selectionIndices", List.of(row))), null, null, null);
    }

    /** VHRV01I (or VHRV03I / VHRV04I) with the incident listed */
    static ScreenSession listed(Parity p, String screen, String incd) {
        ScreenSession s = p.oldScreens();
        s.openRoot(screen, p.user);
        dialogs(s, "OK");
        type(s, screen, Map.of("txtIncidentNo", incd), "pbtSearch");
        return s;
    }

    static List<Map<String, Map<String, Object>>> sent(Parity p, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (var e : p.sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Map<String, Object>> m = new LinkedHashMap<>(e.getValue());
            m.remove("XDCONTROL");                                  // what the previous call left there, not part of the request
            out.add(m);
        }
        return out;
    }

    /** the records sent to a program, without the fields that hold the time of day */
    static List<Map<String, Map<String, Object>>> sentNoClock(Parity p, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (Map<String, Map<String, Object>> call : sent(p, program)) {
            Map<String, Map<String, Object>> c = new LinkedHashMap<>();
            call.forEach((rec, fields) -> {
                Map<String, Object> f = new LinkedHashMap<>(fields);
                f.remove("timeNarr");
                if (f.containsKey("timeRepr") && !String.valueOf(f.get("timeRepr")).endsWith(":00")) f.remove("timeRepr");
                c.put(rec, f);
            });
            out.add(c);
        }
        return out;
    }

    static String error(Supplier<?> call) {
        try { call.get(); return null; } catch (InvalidRequestException e) { return e.getMessage(); }
    }

    // ---- VHRV01I: the incident and its repairs ---------------------------------------------------------------------

    @Test
    void listingMatchesTheOldScreen() {
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = listed(p, "VHRV01I", INCD);
        p.clear();
        var r = api(p).incident(Screen.VHRV01I, INCD, "BRIGHTCO");
        assertThat(r.car()).isEqualTo(value(s, "VHRV01I", "txtCarno"));
        assertThat(r.status()).isEqualTo(value(s, "VHRV01I", "txtVehincdstat"));
        assertThat(r.consist()).isEqualTo(value(s, "VHRV01I", "txtCondashno"));
        assertThat(String.valueOf(r.nextDocument())).isEqualTo(value(s, "VHRV01I", "txtReprdocno").replaceFirst("^0+", ""));
        assertThat(r.defaults().location()).isEqualTo(value(s, "VHRV01I", "txtReprloc"));
        assertThat(r.defaults().employee()).isEqualTo(value(s, "VHRV01I", "txtEmpNo"));
        assertThat(r.defaults().date()).isEqualTo(value(s, "VHRV01I", "txtRepDate"));
        assertThat(r.defaults().time()).isEqualTo(value(s, "VHRV01I", "txtRepTime"));
        assertThat(r.repairs()).hasSize(2);
        assertThat(r.repairs().get(1).narrative()).isEqualTo("BRAKE PADS");
    }

    @Test
    void refusedIncidentsMatchTheOldScreen() {
        for (Object[] c : new Object[][] {{incident("OPU", "REG", 10), INCD}, {incident("CLO", "REG", 10), INCD}, {incident("OPN", "PMS", 10), INCD},
                {incident("OPN", "REG", 85), "H2990010"}, {incident("OPN", "REG", 10), ""}}) {
            Parity p = backend((Map<String, Object>) c[0]);
            ScreenSession s = listed(p, "VHRV01I", (String) c[1]);
            String old = value(s, "VHRV01I", "txtErrorMsg");
            assertThat(old).as("old message for " + c[0]).isNotBlank();
            assertThat(error(() -> api(p).incident(Screen.VHRV01I, (String) c[1], "BRIGHTCO"))).isEqualTo(old);
        }
        Parity p = backend(incident("OPN", "REG", 10));
        on(p, "VHINC2D", in -> Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0)));
        ScreenSession s = listed(p, "VHRV01I", INCD);
        assertThat(error(() -> api(p).incident(Screen.VHRV01I, INCD, "BRIGHTCO"))).isEqualTo(value(s, "VHRV01I", "txtErrorMsg"));
    }

    // ---- VHRV01I: add a repair, close the incident -----------------------------------------------------------------

    static Map<String, String> repairFields(String cause, String sys, String sub, String disp, String date, String time, String loc, String empl,
                                            String war, String hours, String pct, String narr) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("txtRepairCauseCode", cause);
        m.put("txtSys", sys);
        m.put("txtSub", sub);
        m.put("txtReprDisposition", disp);
        m.put("txtRepDate", date);
        m.put("txtRepTime", time);
        m.put("txtReprloc", loc);
        m.put("txtEmpNo", empl);
        m.put("txtWarranty", war);
        m.put("txtLaborHours", hours);
        m.put("txtPartCtlNo", pct);
        m.put("txtSerOn", "NEWON");
        m.put("txtSerOff", "NEWOFF");
        m.put("mleRepairNarrative", narr);
        return m;
    }

    static RepairService.RepairInput input(Map<String, String> f) {
        String h = f.get("txtLaborHours");
        return new RepairService.RepairInput(f.get("txtRepairCauseCode"), f.get("txtSys"), f.get("txtSub"), f.get("txtReprDisposition"), f.get("txtRepDate"),
                f.get("txtRepTime"), f.get("txtReprloc"), f.get("txtEmpNo"), f.get("txtWarranty"), h == null || h.isBlank() ? null : Integer.valueOf(h),
                f.get("txtPartCtlNo"), f.get("txtSerOn"), f.get("txtSerOff"), f.get("mleRepairNarrative"));
    }

    @Test
    void addingARepairSendsWhatTheOldScreenSends() {
        for (Map<String, String> f : List.of(
                repairFields("A", "DR", "01", "R", "10-05-2026", "1415", "HY", "12345", "Y", "3", "123456", "REPLACED DOOR MOTOR"),
                repairFields("B", "BR", "02", "F", "", "", "CY", "", "N", "0", "P1234567", "PADS"),
                repairFields("W", "DR", "01", "R", TODAY, "0930", "RY", "00001", "", "0", "", "CHECKED"))) {
            Parity p = backend(incident("OPN", "REG", 10));
            ScreenSession s = listed(p, "VHRV01I", INCD);
            p.clear();
            type(s, "VHRV01I", f, "pbtAddRepr");
            List<String> oldBoxes = dialogs(s, "OK");
            var oldCalls = sentNoClock(p, "VHRV01B");
            assertThat(oldCalls).as("old screen added (" + value(s, "VHRV01I", "txtErrorMsg") + oldBoxes + ")").hasSize(1);
            String oldMessage = value(s, "VHRV01I", "txtErrorMsg");
            p.clear();
            var r = api(p).addRepair(INCD, input(f), "BRIGHTCO");
            assertThat(sentNoClock(p, "VHRV01B")).as(f.toString()).isEqualTo(oldCalls);
            // the old screen shows its message, then lists the incident again, which blanks it; the API keeps it
            assertThat(oldMessage).isEmpty();
            assertThat(r.done()).isTrue();
            assertThat(r.message()).isEqualTo(new EglTexts(Parity.bundle()).assigned("VHRV01IS-ADD-REPR", "MRSVehicleRepair", "FINAL-ERROR-MSG", null));
        }
    }

    @Test
    void refusedRepairsGiveTheOldMessages() {
        for (Map<String, String> f : List.of(
                repairFields("", "", "", "", "", "", "", "", "", "", "", ""),
                repairFields("Q", "ZZ", "01", "X", "13-01-2026", "2460", "XX", "", "", "", "", "N"),
                repairFields("A", "DR", "01", "R", "12-31-2099", "0000", "HY", "12A45", "Q", "", "", "N"),
                repairFields("A", "DR", "01", "R", "", "", "HY", "", "Y", "0", "P12A456X", "N"),
                repairFields("A", "DR", "", "R", "", "ab", "HY", "", "", "", "", ""))) {
            Parity p = backend(incident("OPN", "REG", 10));
            ScreenSession s = listed(p, "VHRV01I", INCD);
            p.clear();
            type(s, "VHRV01I", f, "pbtAddRepr");
            String old = value(s, "VHRV01I", "txtErrorMsg");
            assertThat(sent(p, "VHRV01B")).isEmpty();
            assertThat(old).as(f.toString()).isNotBlank();
            assertThat(error(() -> api(p).addRepair(INCD, input(f), "BRIGHTCO"))).as(f.toString()).isEqualTo(old);
            assertThat(sent(p, "VHRV01B")).isEmpty();
        }
    }

    @Test
    void closingSendsWhatTheOldScreenSends() {
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = listed(p, "VHRV01I", INCD);
        p.clear();
        type(s, "VHRV01I", Map.of(), "pbtCloseIncident");
        var oldCalls = sentNoClock(p, "VHRV01B");
        String old = value(s, "VHRV01I", "txtErrorMsg");
        p.clear();
        var r = api(p).closeIncident(INCD, "BRIGHTCO");
        assertThat(sentNoClock(p, "VHRV01B")).isEqualTo(oldCalls).hasSize(1);
        assertThat(r.message().replaceAll("\\s+", " ")).isEqualTo(old.replaceAll("\\s+", " "));
    }

    // ---- VHRV01I: one repair; VHRV02I: its components --------------------------------------------------------------

    @Test
    void selectingARepairShowsWhatTheOldScreenShows() {
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = listed(p, "VHRV01I", INCD);
        select(s, "VHRV01I", "cntPreviousRepairs", 1);
        var d = api(p).repair(INCD, 1);
        assertThat(d.system()).isEqualTo(value(s, "VHRV01I", "txtSys"));
        assertThat(d.cause()).isEqualTo(value(s, "VHRV01I", "txtRepairCauseCode"));
        assertThat(d.partControl()).isEqualTo(value(s, "VHRV01I", "txtPartCtlNo"));
        assertThat(d.time()).isEqualTo(value(s, "VHRV01I", "txtRepTime"));
        assertThat(d.narratives().get(0).text()).isEqualTo(value(s, "VHRV01I", "mleRepairNarrative"));
    }

    static ScreenSession components(Parity p) {
        ScreenSession s = listed(p, "VHRV01I", INCD);
        select(s, "VHRV01I", "cntPreviousRepairs", 1);
        type(s, "VHRV01I", Map.of(), "pbtAddCmpnt");
        id(s, "VHRV02I");
        return s;
    }

    static Map<String, String> componentFields(String cd, String type, String fault, String disp) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("txtCmpntcd", cd);
        m.put("txtCmpntype", type);
        m.put("txtCmpntfaultcd", fault);
        m.put("txtReprdispcd", disp);
        m.put("txtSernoon", "SN-ON");
        m.put("txtSernooff", "SN-OFF");
        return m;
    }

    static RepairService.ComponentInput component(Map<String, String> f) {
        return new RepairService.ComponentInput(f.get("txtCmpntcd"), f.get("txtCmpntype"), f.get("txtCmpntfaultcd"), f.get("txtReprdispcd"),
                f.get("txtSernoon"), f.get("txtSernooff"));
    }

    @Test
    void componentsMatchTheOldScreen() {
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = components(p);
        var c = api(p).components(INCD, 1, false);
        assertThat(c.components()).hasSize(2);
        assertThat(String.valueOf(window(s, "VHRV02I").get("title"))).startsWith(c.title());
        assertThat(String.valueOf(c.nextDocument())).isEqualTo(value(s, "VHRV02I", "txtCmpntDocNo"));
    }

    @Test
    void addingAndUpdatingComponentsSendWhatTheOldScreenSends() {
        Map<String, String> f = componentFields("DOR", "01", "F1", "R");
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = components(p);
        p.clear();
        type(s, "VHRV02I", f, "pbtAdd");
        var old = sent(p, "VHRV02B");
        var oldChecks = List.of(sent(p, "VHRV02F"), sent(p, "VHRV02E"));
        String oldMsg = value(s, "VHRV02I", "txtMsg");
        p.clear();
        var r = api(p).addComponent(INCD, 1, component(f), false);
        assertThat(sent(p, "VHRV02B")).isEqualTo(old).hasSize(1);
        assertThat(List.of(sent(p, "VHRV02F"), sent(p, "VHRV02E"))).isEqualTo(oldChecks);
        assertThat(oldMsg).isEmpty();                                // blanked by VHRV02IS-CLEAR after the list is read again
        assertThat(r.message()).isEqualTo(new EglTexts(Parity.bundle()).assignedAll("VHRV02IS-RET-DATA", "MRSVehicleRepair", "FINAL-MESSAGE").get(0));

        Parity q = backend(incident("OPN", "REG", 10));
        ScreenSession u = components(q);
        select(u, "VHRV02I", "cntCmpntdet", 2);
        Map<String, String> g = componentFields("MOT", "02", "", "F");
        q.clear();
        type(u, "VHRV02I", g, "pbtUpdate");
        var oldU = sent(q, "VHRV02C");
        String oldUMsg = value(u, "VHRV02I", "txtMsg");
        q.clear();
        var ru = api(q).updateComponent(INCD, 1, 2, component(g), false);
        assertThat(sent(q, "VHRV02C")).isEqualTo(oldU).hasSize(1);
        assertThat(oldUMsg).isEmpty();
        assertThat(ru.message()).isEqualTo(new EglTexts(Parity.bundle()).assignedAll("VHRV02IS-RET-DATA", "MRSVehicleRepair", "FINAL-MESSAGE").get(1));
    }

    @Test
    void refusedComponentsGiveTheOldMessages() {
        for (Map<String, String> f : List.of(componentFields("", "01", "", "R"), componentFields("DOR", "", "BAD", "X"),
                componentFields("XXX", "09", "", ""), componentFields("", "", "", "Q"))) {
            Parity p = backend(incident("OPN", "REG", 10));
            ScreenSession s = components(p);
            p.clear();
            type(s, "VHRV02I", f, "pbtAdd");
            String old = value(s, "VHRV02I", "txtMsg");
            assertThat(sent(p, "VHRV02B")).isEmpty();
            assertThat(old).as(f.toString()).isNotBlank();
            assertThat(error(() -> api(p).addComponent(INCD, 1, component(f), false))).as(f.toString()).isEqualTo(old);
        }
    }

    // ---- VHRV03I: repairs of an event --------------------------------------------------------------------------------

    @Test
    void eventRepairsMatchTheOldScreen() {
        for (String[] f : new String[][] {{"10-05-2026", "1415", "CHECKED THE EVENT"}, {"", "", "NOTHING FOUND"}}) {
            Parity p = backend(incident("OPN", "EVT", 0));
            ScreenSession s = listed(p, "VHRV03I", EVENT);
            var listing = api(p).incident(Screen.VHRV03I, EVENT, "BRIGHTCO");
            assertThat(listing.consist()).isEqualTo(value(s, "VHRV03I", "txtCondashno"));
            p.clear();
            type(s, "VHRV03I", Map.of("txtRepDate", f[0], "txtRepTime", f[1], "mleRepairNarrative", f[2]), "pbtAddRepr");
            var old = sentNoClock(p, "VHRV03A");
            assertThat(old).as(value(s, "VHRV03I", "txtMsg")).hasSize(1);
            p.clear();
            var r = api(p).addEventRepair(EVENT, new RepairService.EventRepairInput(f[0], f[1], f[2]), "BRIGHTCO");
            assertThat(sentNoClock(p, "VHRV03A")).isEqualTo(old);
            assertThat(r.done()).isTrue();
        }
    }

    @Test
    void refusedEventsGiveTheOldMessages() {
        for (Object[] c : new Object[][] {{incident("OPN", "REG", 0), EVENT}, {incident("CLO", "EVT", 0), EVENT}, {incident("OPN", "EVT", 0), INCD},
                {incident("OPN", "EVT", 0), ""}}) {
            Parity p = backend((Map<String, Object>) c[0]);
            ScreenSession s = listed(p, "VHRV03I", (String) c[1]);
            String old = value(s, "VHRV03I", "txtMsg");
            assertThat(old).as(c[0] + " " + c[1]).isNotBlank();
            assertThat(error(() -> api(p).incident(Screen.VHRV03I, (String) c[1], "BRIGHTCO"))).isEqualTo(old);
        }
        for (String[] f : new String[][] {{"13-01-2026", "2500", ""}, {"12-31-2099", "x", "N"}}) {
            Parity p = backend(incident("OPN", "EVT", 0));
            ScreenSession s = listed(p, "VHRV03I", EVENT);
            type(s, "VHRV03I", Map.of("txtRepDate", f[0], "txtRepTime", f[1], "mleRepairNarrative", f[2]), "pbtAddRepr");
            String old = value(s, "VHRV03I", "txtMsg");
            assertThat(old).isNotBlank();
            assertThat(error(() -> api(p).addEventRepair(EVENT, new RepairService.EventRepairInput(f[0], f[1], f[2]), "BRIGHTCO"))).isEqualTo(old);
        }
    }

    @Test
    void closingAnEventSendsWhatTheOldScreenSends() {
        Parity p = backend(incident("OPN", "EVT", 0));
        ScreenSession s = listed(p, "VHRV03I", EVENT);
        p.clear();
        type(s, "VHRV03I", Map.of(), "pbtCloseIncds");
        var old = sentNoClock(p, "VHRV03A");
        String oldMsg = value(s, "VHRV03I", "txtMsg");
        p.clear();
        var r = api(p).closeEvent(EVENT, "BRIGHTCO");
        assertThat(sentNoClock(p, "VHRV03A")).isEqualTo(old).hasSize(1);
        assertThat(r.message().replaceAll("\\s+", " ")).isEqualTo(oldMsg.replaceAll("\\s+", " "));
    }

    // ---- VHRV04I: change or delete a repair --------------------------------------------------------------------------

    @Test
    void changingARepairSendsWhatTheOldScreenSends() {
        for (Map<String, String> f : List.of(
                repairFields("B", "DR", "01", "F", "10-06-2026", "0815", "CY", "22222", "Y", "4", "654321", "x"),
                repairFields("A", "BR", "02", "R", "10-02-2026", "1015", "HY", "12345", "N", "2", "P1234561", "x"))) {
            Parity p = backend(incident("OPN", "REG", 10));
            ScreenSession s = listed(p, "VHRV04I", INCD);
            select(s, "VHRV04I", "cntPreviousRepairs", 1);
            p.clear();
            Map<String, String> g = new LinkedHashMap<>(f);
            g.remove("mleRepairNarrative");
            type(s, "VHRV04I", g, "Push_Button1");
            List<String> boxes = dialogs(s, "OK");
            var old = List.of(sentNoClock(p, "VHRV4A1"), sentNoClock(p, "VHRV4A2"));
            String oldMsg = value(s, "VHRV04I", "txtErrorMsg");
            assertThat(old.get(0)).as(oldMsg + boxes).hasSize(1);
            p.clear();
            var r = api(p).changeRepair(INCD, 1, input(f), false, "BRIGHTCO");
            assertThat(List.of(sentNoClock(p, "VHRV4A1"), sentNoClock(p, "VHRV4A2"))).as(f.toString()).isEqualTo(old);
            assertThat(oldMsg).isEmpty();                            // the old screen lists the incident again, which blanks it
            assertThat(r.message()).isEqualTo(new EglTexts(Parity.bundle()).assigned("VHRV04IS-CHG-REPR", "MRSVehicleRepair", "FINAL-ERROR-MSG", "Updated"));
        }
    }

    @Test
    void deletingARepairSendsWhatTheOldScreenSends() {
        Parity p = backend(incident("OPN", "REG", 10));
        ScreenSession s = listed(p, "VHRV04I", INCD);
        select(s, "VHRV04I", "cntPreviousRepairs", 2);
        p.clear();
        type(s, "VHRV04I", Map.of(), "Push_Button2");
        dialogs(s, "OK");
        var old = List.of(sentNoClock(p, "VHRV4A1"), sentNoClock(p, "VHRV4A2"));
        String oldMsg = value(s, "VHRV04I", "txtErrorMsg");
        assertThat(old.get(0)).hasSize(1);
        p.clear();
        var r = api(p).changeRepair(INCD, 2, null, true, "BRIGHTCO");
        assertThat(List.of(sentNoClock(p, "VHRV4A1"), sentNoClock(p, "VHRV4A2"))).isEqualTo(old);
        assertThat(r.message()).isEqualTo(oldMsg);
    }
}
