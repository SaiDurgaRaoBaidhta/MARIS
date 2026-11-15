package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintStatusHistoryService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintStatusHistoryService.Change;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An incident's status history (VHVM06I) against the old screen run by the engine. */
class VehicleMaintStatusHistoryParityTest {
    static final String TERMINAL = VehicleMaintCarParityTest.TERMINAL, INCIDENT = "29990010";
    static final String DAY = LocalDate.now().minusDays(4).toString();
    static final String DAY_MDY = DAY.substring(5, 7) + "-" + DAY.substring(8) + "-" + DAY.substring(0, 4);

    static Map<String, Object> row(String ts, String prev, String now) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("incdNo", INCIDENT); r.put("tsStatChg", ts); r.put("dateStatChg", DAY); r.put("timeStatChg", "10:15:00");
        r.put("incdStatPrev", prev); r.put("incdStatNew", now); r.put("homeYrd", "CY"); r.put("yrdTrkgYrd", ""); r.put("yrdSubLoc", "");
        r.put("moveReasonCd", ""); r.put("pseudoConNo", ""); r.put("marisFunc", "VHVM06"); r.put("userGrp", "BRIGHTCO");
        return r;
    }

    static Parity backend() {
        Parity p = new Parity();
        p.on("VH01A01", in -> {                                     // the sign-on data the menu reads and passes down
            Map<String, Object> d = new LinkedHashMap<>(in.get("VH01A01_DATA"));
            d.put("numYrd", 2);
            d.put("yardRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY")));
            d.put("numVehIncdStat", 3);
            d.put("vehIncdStatRow", List.of(Map.of("vehIncdStat", "OPN"), Map.of("vehIncdStat", "WIP"), Map.of("vehIncdStat", "CLS")));
            return Map.of("XDCONTROL", Map.of("urc", 0), "VH01A01_DATA", d);
        });
        p.on("VHYT15A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT15A_DATA", Map.of("dataRow", List.of(Map.of("moveReasonCd", "M1")))));
        p.on("VHVM6A1", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM6A1_DATA", Map.of("dataRow", List.of(
                row("2026-11-10-10.15.00.000000", "", "OPN"), row("2026-11-10-11.00.00.000000", "OPN", "WIP")))));
        p.on("VHVM6A2", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM6A3", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM6A4", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM6A6", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM6A6_DATA", Map.of("dataRow", List.of(Map.of("yrdSubLoc", "T12")))));
        return p;
    }

    static VehicleMaintStatusHistoryService api(Parity p) {
        return new VehicleMaintStatusHistoryService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock, TERMINAL);
    }

    static ScreenSession loaded(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMMMI", p.user);                               // from its menu, which passes the sign-on data down
        VehicleMaintCarParityTest.dialogs(s);
        s.event(id(s), List.of(), "Push_Button35", "clicked", null);
        VehicleMaintCarParityTest.dialogs(s);
        String w = (String) VehicleMaintCarParityTest.window(s).get("id");
        s.event(w, List.of(new ScreenSession.Change("Text6", "object", INCIDENT)), "Text6", "losingFocus", null);
        VehicleMaintCarParityTest.dialogs(s);
        return s;
    }

    static String id(ScreenSession s) {
        return (String) VehicleMaintCarParityTest.window(s).get("id");
    }

    /** the records sent, without XDCONTROL (the terminal id and user group come from the menu, so they are compared too) */
    static Map<String, Map<String, Object>> comparable(Map<String, Map<String, Object>> sent) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>(sent);
        out.remove("XDCONTROL");
        return out;
    }

    @Test
    void updatingAnEntrySendsWhatTheOldScreenSends() {
        Parity p = backend();
        ScreenSession s = loaded(p);
        s.event(id(s), List.of(), "VAGen_Container_Details_DATA_ROW1", "select", 2);
        s.event(id(s), List.of(new ScreenSession.Change("Text9", "object", DAY_MDY), new ScreenSession.Change("Text10", "object", "11:30:00"),
                new ScreenSession.Change("Text1", "object", "201")), "pbtUpdate", "clicked", null);
        List<String> oldMessages = VehicleMaintCarParityTest.dialogs(s);
        Map<String, Map<String, Object>> old = p.lastSent("VHVM6A3");
        assertThat(old).as("old screen updated: " + oldMessages).isNotNull();

        p.clear();
        var r = api(p).update(INCIDENT, new Change("2026-11-10-11.00.00.000000", DAY, "11:30:00", "OPN", "WIP", "", "", "201", "", "CY"), "BRIGHTCO");
        assertThat(r.message()).isEqualTo(oldMessages.get(0));
        assertThat(comparable(p.lastSent("VHVM6A3"))).isEqualTo(comparable(old));
        assertThat(p.lastSent("VHVM6A3").get("VHVM6A3_DATA")).containsEntry("termMaintUpd", TERMINAL);
    }

    @Test
    void deletingAnEntrySendsWhatTheOldScreenSends() {
        Parity p = backend();
        ScreenSession s = loaded(p);
        s.event(id(s), List.of(), "VAGen_Container_Details_DATA_ROW1", "select", 1);
        s.event(id(s), List.of(), "Push_Button1", "clicked", null);
        List<String> oldMessages = VehicleMaintCarParityTest.dialogs(s);
        Map<String, Map<String, Object>> old = p.lastSent("VHVM6A4");
        assertThat(old).as("old screen deleted: " + oldMessages).isNotNull();

        p.clear();
        var r = api(p).delete(INCIDENT, "2026-11-10-10.15.00.000000", "BRIGHTCO");
        assertThat(r.message()).isEqualTo(oldMessages.get(0));
        assertThat(comparable(p.lastSent("VHVM6A4"))).isEqualTo(comparable(old));
    }

    @Test
    void addingAnEntrySendsWhatTheOldScreenSends() {
        Parity p = backend();
        ScreenSession s = loaded(p);
        s.event(id(s), List.of(), "Part-Clear", "clicked", null);
        VehicleMaintCarParityTest.dialogs(s);
        s.event(id(s), List.of(new ScreenSession.Change("Text7", "object", INCIDENT), new ScreenSession.Change("Text9", "object", DAY_MDY),
                new ScreenSession.Change("Text10", "object", "12:00:00"), new ScreenSession.Change("Drop-down_List4", "selectionIndex", 2),
                new ScreenSession.Change("Drop-down_List5", "selectionIndex", 3), new ScreenSession.Change("Drop-down_List7", "selectionIndex", 1)), "pbt_Add", "clicked", null);
        List<String> oldMessages = VehicleMaintCarParityTest.dialogs(s);
        Map<String, Map<String, Object>> old = p.lastSent("VHVM6A2");
        assertThat(old).as("old screen added: " + oldMessages).isNotNull();

        p.clear();
        var r = api(p).add(INCIDENT, new Change("", DAY, "12:00:00", "WIP", "CLS", "", "", "", "", "CY"), "BRIGHTCO");
        assertThat(r.message()).isEqualTo(oldMessages.get(0));
        assertThat(comparable(p.lastSent("VHVM6A2"))).isEqualTo(comparable(old));
    }

    @Test
    void checksMatchTheOldScreen() {
        String future = LocalDate.now().plusDays(3).toString();
        String futureMdy = future.substring(5, 7) + "-" + future.substring(8) + "-" + future.substring(0, 4);
        Parity p = backend();
        ScreenSession s = loaded(p);
        s.event(id(s), List.of(), "VAGen_Container_Details_DATA_ROW1", "select", 2);
        s.event(id(s), List.of(new ScreenSession.Change("Text9", "object", futureMdy), new ScreenSession.Change("Text10", "object", "00:00:00"),
                new ScreenSession.Change("Drop-down_List4", "selectionIndex", 0)), "pbtUpdate", "clicked", null);
        List<String> oldMessages = VehicleMaintCarParityTest.dialogs(s);
        assertThat(p.lastSent("VHVM6A3")).isNull();
        assertThat(oldMessages).isNotEmpty();
        assertThatThrownBy(() -> api(p).update(INCIDENT, new Change("2026-11-10-11.00.00.000000", future, "00:00:00", "", "WIP", "", "", "", "", "CY"),
                "BRIGHTCO")).hasMessage(String.join("\n", oldMessages));
    }

    @Test
    void subLocationsAndMoveReasonsMatchTheOldScreen() {
        Parity p = backend();
        ScreenSession s = loaded(p);
        assertThat(VehicleMaintCarParityTest.items(s, "Drop-down_List3")).containsExactlyElementsOf(api(p).options("BRIGHTCO").moveReasons());
        assertThat(api(p).subLocations("CY")).containsExactly("T12");
        assertThat(p.lastSent("VHVM6A6").get("VHVM6A6_CRIT")).containsEntry("yardLocInd", "Y").containsEntry("yrdTrkgYrd", "CY");
        assertThat(api(p).entries(INCIDENT)).hasSize(2);
        assertThat(VehicleMaintCarParityTest.items(s, "Drop-down_List7")).containsExactlyElementsOf(api(p).options("BRIGHTCO").yards());
        assertThat(VehicleMaintCarParityTest.items(s, "Drop-down_List5")).containsExactlyElementsOf(api(p).options("BRIGHTCO").statuses());
    }
}
