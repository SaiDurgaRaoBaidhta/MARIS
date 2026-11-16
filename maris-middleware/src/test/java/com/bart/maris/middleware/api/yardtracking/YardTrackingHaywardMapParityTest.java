package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingHaywardMapService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMapsLayout;
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
 * The Hayward yard map's clean API against the old map screen (Vhyt07i) run by the engine: same backend, same criteria,
 * the same car label and colours on every spot, the same track each button opens.
 */
class YardTrackingHaywardMapParityTest {
    private static final String PKG = "MRSYardTracking";
    private static final String[] YARDS = {"HY", "RY", "CY", "DY", "MY"};
    private static final String[] ORIENTATION = {"<", ">", "O", "b", "(", " "};
    private static final String[] SOFTWARE = {"", "V2", "$V3", " ", " V"};

    private Parity backend() {
        Parity p = new Parity();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Set<Integer>> layout = new LinkedHashMap<>(YardTrackingMapsLayout.of(Parity.bundle(), "VHYT07IS-LOAD", PKG));
        layout.put("NOT-A-TRK", Set.of(1));
        int n = 0;
        for (var t : layout.entrySet()) {
            int last = t.getValue().stream().max(Integer::compare).orElse(1);
            for (int pos = 1; pos <= last + 2; pos++, n++) {
                Map<String, Object> row = new HashMap<>(Map.of("carNo", String.valueOf(1000 + n), "conNo", "532", "vagenOrientation", ORIENTATION[n % 6],
                        "yrdSubLoc", t.getKey(), "posnNo", pos, "vehStat", "GO", "homeYrd", YARDS[n % 5], "softver", SOFTWARE[n % 5]));
                rows.add(row);
            }
        }
        p.on("VHYT07A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHYT07A_DATA", Map.of("dataRow", rows)));
        return p;
    }

    /** the old spots that show a car: widgets bound to a BUTTON field, as "label colour/background" */
    @SuppressWarnings("unchecked")
    private static List<String> oldSpots(ScreenSession old) {
        Set<String> spots = new HashSet<>();
        for (var c : Parity.bundle().screen("Vhyt07i").path("connections")) {
            var a = c.path("a");
            if (c.path("m").asText().startsWith("connect") && a.path(1).asText().matches("BUTTON(-?\\d+)? data") && a.path(3).asText().equals("object")) {
                spots.add(a.path(2).path("id").asText().replaceFirst("^screen\\.", ""));
            }
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) {
            ((Map<String, Map<String, Object>>) w.get("widgets")).forEach((name, wd) -> {
                if (spots.contains(name) && wd.get("value") != null && !wd.get("value").toString().isBlank()) {
                    out.add(wd.get("value").toString().strip() + " " + (wd.get("fg") != null ? wd.get("fg") : wd.get("color")) + "/" + wd.get("bg"));
                }
            });
        }
        return out;
    }

    @Test
    void carsMatchTheOldMap() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("Vhyt07i", p.user);
        List<String> shown = oldSpots(old);
        var oldCrit = p.lastSent("VHYT07A").get("VHYT07A_CRIT");
        p.clear();
        var m = new YardTrackingHaywardMapService(p.legacy(), p.messages(), Parity.bundle()).map();
        assertThat(p.lastSent("VHYT07A").get("VHYT07A_CRIT")).as("criteria").isEqualTo(oldCrit);
        assertThat(shown).as("the old map shows cars").isNotEmpty();
        // the old screen's groups fill these spots' BUTTON fields, but no widget shows them (the drawing has fewer spots
        // than the track groups): T-22 12-13, T-23 9-21, TF08 4-10, T-23A 3. The API returns those cars; the test leaves them out.
        Set<String> notDrawn = new HashSet<>(List.of("T-22/12", "T-22/13", "TF08/4", "TF08/5", "TF08/6", "TF08/7", "TF08/8", "TF08/9", "TF08/10", "T-23A/3"));
        for (int k = 9; k <= 21; k++) notDrawn.add("T-23/" + k);
        List<String> api = new ArrayList<>();
        int skipped = 0;
        for (YardReportsMap.Car c : m.cars()) {
            if (notDrawn.contains(c.track() + "/" + c.position())) skipped++;
            else api.add(c.label() + " " + c.colour() + "/" + c.background());
        }
        assertThat(skipped).as("every spot the old drawing lacks got a car").isEqualTo(notDrawn.size());
        assertThat(api).containsExactlyInAnyOrderElementsOf(shown);
        assertThat(m.yard()).isEqualTo("HY");
    }

    @Test
    void trackButtonsOpenTheSameTrackDetail() {
        Parity api = backend();
        var m = new YardTrackingHaywardMapService(api.legacy(), api.messages(), Parity.bundle()).map();
        for (var spot : List.of("T10_SPOT", "PSEUDO_SPOT", "TT1_SPOT", "S25_SPOT")) {
            Parity p = backend();
            ScreenSession old = p.oldScreens();
            old.openRoot("Vhyt07i", p.user);
            String id = YardTrackingPseudoTrainParityTest.id(old, "Vhyt07i");
            old.event(id, List.of(), spot, "clicked", null);
            String track = String.valueOf(old.window(id).recordPart("VHYT07IW-PASSVALUE").get("YRD-SUB-LOC data")).strip();
            String yard = String.valueOf(old.window(id).recordPart("VHYT07IW-PASSVALUE").get("YRD-TRKG-YRD data")).strip();
            assertThat(YardTrackingPseudoTrainParityTest.id(old, "Vhyt08i")).as(spot + " opens the track detail").isNotNull();
            String caption = Parity.bundle().screen("Vhyt07i").path("widgets").path(spot).path("props").path("object").asText().strip();
            assertThat(m.spots()).as(spot).contains(new YardTrackingHaywardMapService.Spot(caption, track));
            assertThat(m.yard()).isEqualTo(yard);
        }
    }
}
