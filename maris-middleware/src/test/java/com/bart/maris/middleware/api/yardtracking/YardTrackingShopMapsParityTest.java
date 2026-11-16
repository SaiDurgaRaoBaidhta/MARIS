package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMapsLayout;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopMapsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Concord (VHYT60I) and Richmond (VHYT96I) shop maps' clean API against the old screens run by the engine: same
 * backend, same criteria, same cars shown (label and colour on each spot), the same Print report and yard.
 */
class YardTrackingShopMapsParityTest {
    private static final String PKG = "MRSYardTracking";
    private static final String[] YARDS = {"HY", "RY", "CY", "DY", "MY"};
    private static final String[] ORIENTATION = {"<", ">", "O", "b", "("};
    private static final String[] SOFTWARE = {"", "V2", "$V3", " ", " V", "V"};

    private Parity p;

    /** a car on every spot of every track the LOAD knows, two beyond, and a track it does not know */
    private void backend(String program, String data, String load) {
        p = new Parity();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Set<Integer>> layout = new LinkedHashMap<>(YardTrackingMapsLayout.of(Parity.bundle(), load, PKG));
        layout.put("NOT-A-TRK", Set.of(1));
        int n = 0;
        for (var t : layout.entrySet()) {
            int last = t.getValue().stream().max(Integer::compare).orElse(1);
            for (int pos = 1; pos <= last + 2; pos++, n++) {
                Map<String, Object> row = new HashMap<>(Map.of("carNo", String.valueOf(1000 + n), "conNo", "532",
                        "vagenOrientation", ORIENTATION[n % 5], "yrdSubLoc", t.getKey(), "posnNo", pos, "vehStat", "GO",
                        "homeYrd", YARDS[n % 5], "softver", SOFTWARE[n % SOFTWARE.length], "lftOrientationCd", "<"));
                rows.add(row);
            }
        }
        p.on(program, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), data, Map.of("dataRow", rows)));
    }

    @SuppressWarnings("unchecked")
    private List<String> oldSpots(ScreenSession old, String screen) {
        Set<String> spots = new HashSet<>();
        for (var c : Parity.bundle().screen(screen).path("connections")) {
            var a = c.path("a");
            if (c.path("m").asText().matches("connectAttributes|connectReadOnlyAttributeToAttribute") && a.path(1).asText().matches("(BUTTON|BUTTONTXT)(-?\\d+)? data")
                    && a.path(3).asText().equals("object")) spots.add(a.path(2).path("id").asText().replaceFirst("^screen\\.", ""));
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) {
            if (!screen.equalsIgnoreCase((String) w.get("screen"))) continue;
            ((Map<String, Map<String, Object>>) w.get("widgets")).forEach((name, wd) -> {
                if (spots.contains(name) && wd.get("value") != null && !wd.get("value").toString().isBlank()) {
                    out.add(wd.get("value") + " " + (wd.get("fg") != null ? wd.get("fg") : wd.get("color")));
                }
            });
        }
        return out;
    }

    private static Map<String, Map<String, Object>> criteria(Map<String, Map<String, Object>> sent) {
        Map<String, Map<String, Object>> out = new java.util.TreeMap<>();
        sent.forEach((k, v) -> { if (k.endsWith("CRIT")) out.put(k, v); });
        return out;
    }

    @SuppressWarnings("unchecked")
    /**
     * @param oldDefects "TRACK/spot" places the old screen never draws (no widget bound to them); both sides leave their cars out
     * @param oldExtra   what the old screen draws on a spot that is not its own (a widget bound to another spot's text)
     */
    private void same(String screen, String program, String data, String load, String map, Set<String> oldDefects, Set<String> oldExtra) {
        backend(program, data, load);
        ScreenSession old = p.oldScreens();
        old.openRoot(screen, p.user);
        List<String> shown = oldSpots(old, screen);
        var oldSent = p.lastSent(program);
        // the old Print: the SHOP statement group's report and yard reach VHYT27I
        String id = null;
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) id = (String) w.get("id");
        old.event(id, List.of(), "btn_Print", "clicked", null);
        Map<String, Object> print = null;
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) if (w.get("screen").equals("VHYT27I")) print = w;
        p.clear();
        YardTrackingMap m = new YardTrackingShopMapsService(p.legacy(), p.messages(), Parity.bundle()).map(map);
        assertThat(m.screen()).isEqualTo(screen);
        assertThat(criteria(p.lastSent(program))).as("criteria").isEqualTo(criteria(oldSent));
        assertThat(shown).as("the old screen shows cars").isNotEmpty();
        Set<String> skipped = new HashSet<>();
        List<String> api = new ArrayList<>();
        for (YardReportsMap.Car c : m.cars()) {
            if (oldDefects.contains(c.track() + "/" + c.position())) skipped.add(c.label());
            else api.add(c.label() + " " + c.colour());
        }
        assertThat(skipped).as("every listed defect was exercised").hasSize(oldDefects.size());
        List<String> oldShown = new ArrayList<>(shown);
        for (String x : oldExtra) assertThat(oldShown.remove(x)).as("the old screen draws " + x).isTrue();
        assertThat(api).containsExactlyInAnyOrderElementsOf(oldShown.stream().filter(x -> skipped.stream().noneMatch(s -> x.startsWith(s + " "))).toList());
        assertThat(print).as("Print opens VHYT27I").isNotNull();
        assertThat(m.shopReport()).isNotNull();
        var passed = old.window((String) print.get("id")).recordPart("VHYT27IW-PASSVALUE");
        assertThat(String.valueOf(passed.get("WHICH-REPORT data")).strip()).isEqualTo(m.shopReport().whichReport());
        assertThat(String.valueOf(passed.get("YRD-TRKG-YRD data")).strip()).isEqualTo(m.shopReport().yard());
    }

    @Test
    void concordShop() {
        // VHYT60I: the LOAD places cars on S7-4 and S6-6, but the screen has no widgets for them
        same("VHYT60I", "VHYT60A", "VHYT60A-DATA", "VHYT60IS-LOAD2", "concord-shop", Set.of("S7-4/1", "S6-6/1"), Set.of());
    }

    @Test
    void richmondShop() {
        // VHYT96I: the LOAD places cars on RRP-3D, RT-91A/B, TR-1A and TR1-A..D, but no widget shows those records; S2TXT01 shows
        // S-2's spot 2 text (BUTTONTXT02) in spot 1's colour, so S-2's spot 1 car never shows
        same("VHYT96I", "VHYT95A", "VHYT95A-DATA", "VHYT96IS-LOAD", "richmond-shop",
                Set.of("RRP-3D/1", "RT-91A/1", "RT-91B/1", "TR-1A/1", "TR1-A/1", "TR1-B/1", "TR1-C/1", "TR1-D/1", "S-2/1"), Set.of("$1158b BLUE"));
    }
}
