package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingHaywardService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Hayward yard update map (old VHYT05I): the clean API against the old screen run by the engine, same scripted backend. */
class YardTrackingHaywardParityTest {
    static Parity backend(String maintAccess) {
        Parity p = new Parity();
        p.on("VHYT07A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 5), "VHYT07A_DATA", Map.of("dataRow", List.of(
                row("1101", "201", "<", "T-10", 1, "GO", " ", "N", 0),
                row("1102", "201", ">", "T-10", 2, "USABLE", "$1", "N", 0),
                row("1103", "", "v", "T-11", 3, "NO GO", " ", "Y", 1),
                row("1104", "", " ", "TF01", 1, "GO", " ", "N", 1),
                row("1105", "", "m", "R91", 2, "GO", " ", "N", 0)))));
        p.on("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VH01A02_DATA",
                Map.of("userGroupAccess", maintAccess.isEmpty() ? List.of() : List.of(Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT05", "partName", "pbtMaint", "accessAllow", maintAccess)))));
        return p;
    }

    static Map<String, Object> row(String car, String con, String orientation, String track, int posn, String stat, String softver, String incident, int pm) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", car); m.put("conNo", con); m.put("vagenOrientation", orientation); m.put("yrdSubLoc", track); m.put("posnNo", posn);
        m.put("vehStat", stat); m.put("softver", softver); m.put("trainIncdInd", incident); m.put("hpmhrs", pm); m.put("homeYrd", "HY");
        return m;
    }

    static YardTrackingHaywardService api(Parity p) {
        return new YardTrackingHaywardService(p.legacy(), p.messages(), Parity.bundle(), p.clock);
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> screen.equalsIgnoreCase((String) w.get("screen")))
                .map(w -> (String) w.get("id")).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!"VHYT05I".equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            Object v = x == null ? null : x.get("value");
            return v == null ? "" : v.toString().strip();
        }
        return "(no window)";
    }

    /** the old screen's spot buttons by the TRAK-POS code their click sends */
    static Map<String, String> widgetsByCode() {
        Map<String, String> out = new HashMap<>();
        for (JsonNode c : Parity.bundle().screen("VHYT05I").path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(3).asText().equals("TRAK-POS data")) out.put(a.path(4).path(0).path("args").path(1).asText(), a.path(0).path("id").asText().substring(7));
        }
        return out;
    }

    @Test
    void theCarsShowAsOnTheOldMap() {
        Parity p = backend("");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT05I", p.user);
        Map<String, Object> oldCrit = p.lastSent("VHYT07A").get("VHYT07A_CRIT");
        p.clear();
        var map = api(p).map(p.user.groupId());
        assertThat(p.lastSent("VHYT07A").get("VHYT07A_CRIT")).isEqualTo(oldCrit);
        Map<String, String> widgets = widgetsByCode();
        assertThat(map.cars()).hasSize(5);
        for (var car : map.cars()) {
            assertThat(car.code()).as("click code of " + car.track() + "/" + car.position()).isNotNull();
            assertThat(car.label()).as(car.track() + "/" + car.position()).isEqualTo(value(s, widgets.get(car.code())));
        }
        assertThat(map.yard()).isEqualTo("HY");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"N|false", "Y|true", "''|true"})
    void theMaintButtonFollowsTheAccessList(String access, boolean allowed) {
        Parity p = backend(access);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT05I", p.user);
        p.clear();
        s.event(windowId(s, "VHYT05I"), List.of(), "Maint_btn6", "clicked", null);
        boolean oldOpened = windowId(s, "VHYTM6I") != null;
        // the Maint button's own check is the first VH01A02 call; VHYTM6I, once open, asks again for itself
        var oldCrit = p.sent.stream().filter(e -> e.getKey().equals("VH01A02")).findFirst().orElseThrow().getValue().get("VH01A02_CRIT");
        p.clear();
        assertThat(api(p).maintenanceAllowed(p.user.groupId())).isEqualTo(oldOpened).isEqualTo(allowed);
        assertThat(p.lastSent("VH01A02").get("VH01A02_CRIT")).isEqualTo(oldCrit);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "spot:T-1001;spot:T-1002;spot:T-1101",       // from car, to position, to track
            "spot:T-1001;spot:T-1101;spot:T-1103",       // second click on another track: no to-position
            "spot:T-1001;spot:T-1002",                   // not complete
            "track:T-10",                                 // a track button
            "pseudo",                                     // the pseudo track
            "spot:TF0101;spot:R-9102;spot:R-9102"})
    void theClicksOpenTheMoveDetailAsBefore(String list) {
        Parity p = backend("");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT05I", p.user);
        String id = windowId(s, "VHYT05I");
        Map<String, String> widgets = widgetsByCode();
        List<YardTrackingHaywardService.Click> clicks = new ArrayList<>();
        for (String c : list.split(";")) {
            if (c.equals("pseudo")) {
                clicks.add(new YardTrackingHaywardService.Click(null, null, true));
                if (windowId(s, "VHYT11I") == null) s.event(id, List.of(), "PSEUDO_SPOT", "clicked", null);
            } else if (c.startsWith("track:")) {
                clicks.add(new YardTrackingHaywardService.Click(null, c.substring(6), false));
                if (windowId(s, "VHYT11I") == null) s.event(id, List.of(), c.substring(6).replace("-", "") + "_SPOT", "clicked", null);
            } else {
                clicks.add(new YardTrackingHaywardService.Click(c.substring(5), null, false));
                if (windowId(s, "VHYT11I") == null) s.event(id, List.of(), widgets.get(c.substring(5)), "clicked", null);
            }
        }
        boolean opened = windowId(s, "VHYT11I") != null;
        ScreenInstance w = s.window(id);
        var sel = api(p).select(clicks);
        assertThat(sel.open() != null).as("VHYT11I opened").isEqualTo(opened);
        if (!opened) return;
        Map<String, Object> old = Map.of(
                "yrdTrkgYrd", text(w, "YRD-TRKG-YRD"), "fromYrdSubLoc", text(w, "FROM-YRD-TRKG-YRD"),
                "positionNoFrom", num(w, "FROM-CAR-POSITION"), "positionNoTo", num(w, "TO-CAR-POSITION"),
                "toYrdSubLoc", text(w, "TO-YRD-TRKG-YRD"), "yrdSubLoc", text(w, "FROM-YRD-TRKG-YRD"));
        assertThat(sel.params()).isEqualTo(old);
    }

    static String text(ScreenInstance w, String field) {
        Object v = w.recordPart("VHYT05IW-PASSVALUE").get(field + " data");
        return v == null ? "" : v.toString().strip();
    }

    static long num(ScreenInstance w, String field) {
        Object v = w.recordPart("VHYT05IW-PASSVALUE").get(field + " data");
        return v == null || v.toString().isBlank() ? 0 : new java.math.BigDecimal(v.toString().strip()).longValue();
    }
}
