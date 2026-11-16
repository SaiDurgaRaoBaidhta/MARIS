package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingMapsLayout;
import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMapsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The yard tracking maps' clean API against the old map screens run by the engine (VHYT40I / 41I, VHYT50I / 51I, VHYT04I):
 * same backend, same request, same cars shown. Every track gets a car on every spot and two beyond, plus a track the map
 * does not have; both sides must show the same cars (compared as what each spot shows, so widget names do not matter).
 */
class YardTrackingMapsParityTest {
    private static final String PKG = "MRSYardTracking";
    private static final Map<String, String> OLD_COLOUR = Map.of("go", "BLACK", "go-pm-due", "BROWN", "usable", "BLUE", "not-usable", "RED");
    private static final String[] YARDS = {"HY", "RY", "CY", "DY", "MY"};
    private static final String[] STATUS = {"GO", "USABLE", "NO GO"};
    private static final String[] ORIENTATION = {"<", ">", "O", "b", "("};
    private static final String[] SOFTWARE = {"", "V2", "$V3", " ", " V", "V"};

    private Parity p;

    /** rows for every spot of the map, `beyond` more per track, and one unknown track; car numbers are unique */
    private static List<Map<String, Object>> rows(String load, boolean shop, int beyond) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Set<Integer>> layout = new java.util.LinkedHashMap<>(YardTrackingMapsLayout.of(Parity.bundle(), load, PKG));
        layout.put("NOT-A-TRK", Set.of(1));
        int n = 0;
        for (var t : layout.entrySet()) {
            int last = t.getValue().stream().max(Integer::compare).orElse(0);
            for (int pos = 1; pos <= last + beyond; pos++, n++) {
                // PM due (HPMHRS 1) is not sent: the engine compares GiveAColor's SMALLINT parameter wrongly (Sai Teja's
                // note); YardTrackingMapsApiTest covers go-pm-due
                Map<String, Object> row = new java.util.HashMap<>(Map.of("carNo", String.valueOf(1000 + n), "conNo", "532",
                        "vagenOrientation", ORIENTATION[n % 5], "yrdSubLoc", t.getKey(), "posnNo", pos, "vehStat", STATUS[n % 3],
                        "homeYrd", YARDS[n % 5], "trainIncdInd", n % 7 == 0 ? "Y" : "N", "hpmhrs", 0));
                if (shop) {
                    row.put("softver", SOFTWARE[n % SOFTWARE.length]);
                    row.put("lftOrientationCd", "<");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private void backend(String program, String data, String load, boolean shop) {
        p = new Parity();
        List<Map<String, Object>> rows = rows(load, shop, 2);
        p.on(program, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), data, Map.of("dataRow", rows)));
    }

    /** the old screen's spots that show something: widgets bound to a BUTTON / BUTTONTXT field, or named like a spot */
    @SuppressWarnings("unchecked")
    private List<String> oldSpots(String screen, Function<Map<String, Object>, String> show) {
        Set<String> spots = new HashSet<>();
        for (var c : Parity.bundle().screen(screen).path("connections")) {
            var a = c.path("a");
            if (c.path("m").asText().equals("connectAttributes") && a.path(1).asText().matches("(BUTTON|BUTTONTXT)(-?\\d+)? data")
                    && a.path(3).asText().equals("object")) {
                spots.add(a.path(2).path("id").asText().replaceFirst("^screen\\.", ""));
            }
        }
        ScreenSession old = p.oldScreens();
        old.openRoot(screen, p.user);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) {
            if (!w.get("screen").equals(screen)) continue;
            ((Map<String, Map<String, Object>>) w.get("widgets")).forEach((name, wd) -> {
                boolean namedSpot = name.matches("[A-Z0-9]+TXT\\d+") && String.valueOf(wd.get("value")).matches(".*\\d.*");
                if ((spots.contains(name) || namedSpot) && wd.get("value") != null && !wd.get("value").toString().isBlank()) out.add(show.apply(wd));
            });
        }
        return out;
    }

    /** @param oldDefects "TRACK/spot" places the old screen draws wrongly; both sides leave out the cars there */
    private void same(String screen, String program, String map, Function<Map<String, Object>, String> old,
                      Function<YardReportsMap.Car, String> clean, Set<String> oldDefects) {
        List<String> shown = oldSpots(screen, old);
        Map<String, Map<String, Object>> oldSent = p.lastSent(program);
        p.clear();
        YardTrackingMap m = new YardTrackingMapsService(p.legacy(), p.messages(), Parity.bundle()).map(map);
        assertThat(m.screen()).isEqualTo(screen);
        assertThat(criteria(p.lastSent(program))).as("the criteria sent").isEqualTo(criteria(oldSent));
        assertThat(shown).as("the old screen shows cars").isNotEmpty();
        Set<String> skipped = new HashSet<>();
        List<String> api = new ArrayList<>();
        for (YardReportsMap.Car c : m.cars()) {
            if (oldDefects.contains(c.track() + "/" + c.position())) skipped.add(c.car());
            else api.add(clean.apply(c));
        }
        assertThat(skipped).as("every listed defect was exercised").hasSize(oldDefects.size());
        List<String> oldShown = shown.stream().filter(x -> skipped.stream().noneMatch(x::contains)).toList();
        assertThat(api).containsExactlyInAnyOrderElementsOf(oldShown);
    }

    private static Map<String, Map<String, Object>> criteria(Map<String, Map<String, Object>> sent) {
        Map<String, Map<String, Object>> out = new java.util.TreeMap<>();
        sent.forEach((k, v) -> { if (k.endsWith("CRIT")) out.put(k, v); });
        return out;
    }

    private static String inquiry(Map<String, Object> w) {
        return w.get("value") + " " + (w.get("fg") != null ? w.get("fg") : w.get("color")) + "/" + w.get("bg");
    }

    private static String inquiryOf(YardReportsMap.Car c) {
        return c.label() + " " + c.colour() + "/" + c.background();
    }

    private static String update(YardReportsMap.Car c) {
        return c.label() + " " + OLD_COLOUR.get(c.condition());
    }

    @Test
    void dublinInquiry() {
        backend("VHYT40A", "VHYT40A_DATA", "VHYT40IS-LOAD", false);
        same("VHYT40I", "VHYT40A", "dublin", YardTrackingMapsParityTest::inquiry, YardTrackingMapsParityTest::inquiryOf, Set.of());
    }

    @Test
    void dublinUpdate() {
        backend("VHYT40A", "VHYT40A_DATA", "VHYT41IS-LOAD", false);
        same("VHYT41I", "VHYT40A", "dublin-update", w -> w.get("value") + " " + w.get("color"), YardTrackingMapsParityTest::update, Set.of());
    }

    @Test
    void pittsburgInquiry() {
        backend("VHYT50A", "VHYT50A_DATA", "VHYT50IS-LOAD", false);
        // VHYT50I: TMZ1TXT5 has no binding for CTM1's CAR-5-COLOR, TMZ2TXT2 none for CTM2's CAR-2-COLOR-B (the designer's colours show)
        same("VHYT50I", "VHYT50A", "pittsburg", YardTrackingMapsParityTest::inquiry, YardTrackingMapsParityTest::inquiryOf, Set.of("CTM1/5", "CTM2/2"));
    }

    @Test
    void pittsburgUpdate() {
        backend("VHYT50A", "VHYT50A_DATA", "VHYT51IS-LOAD", false);
        // VHYT51I: C1TXT9 has no binding for C1's CAR-9-STAT-COLOR (the condition colour is not shown)
        same("VHYT51I", "VHYT50A", "pittsburg-update", w -> w.get("value") + " " + w.get("color"), YardTrackingMapsParityTest::update, Set.of("C1/9"));
    }

    @Test
    void haywardShop() {
        backend("VHYT04A", "VHYT04A-DATA", "VHYT04IS-LOAD", true);
        same("VHYT04I", "VHYT04A", "hayward-shop", w -> w.get("value") + " " + (w.get("fg") != null ? w.get("fg") : w.get("color")),
                // VHYT04I: T24TXT03 has no binding for T24's COLOR-3
                c -> c.label() + " " + c.colour(), Set.of("T-24/3"));
    }

    /**
     * Maint on the update maps: VHYT41IS-MAINT / VHYT51IS-MAINT ask VH01A02 for the user group's access, VHYTIS-ACCES-MAINT
     * opens VHYTM6I unless the list has the Maint button with access "N". The API answers the same, with the same criteria.
     */
    @Test
    @SuppressWarnings("unchecked")
    void maintenanceAccessMatchesTheOldMaintButton() {
        String part = "pbtMaint";
        for (String screen : List.of("VHYT41I", "VHYT51I")) {
            for (List<Map<String, Object>> access : List.of(
                    List.<Map<String, Object>>of(),                                                                  // not listed: allowed
                    List.<Map<String, Object>>of(Map.of("partName", "pbtOther", "accessAllow", "N"), Map.of("partName", part, "accessAllow", "Y")),
                    List.<Map<String, Object>>of(Map.of("partName", "pbtOther", "accessAllow", "Y"), Map.of("partName", part, "accessAllow", "N")))) {
                p = new Parity();
                p.on(screen.equals("VHYT41I") ? "VHYT40A" : "VHYT50A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
                p.on("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", access.size()),
                        "VH01A02_DATA", Map.of("userGroupAccess", access)));
                ScreenSession old = p.oldScreens();
                old.openRoot(screen, p.user);
                String id = null;
                for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) if (w.get("screen").equals(screen)) id = (String) w.get("id");
                p.clear();
                old.event(id, List.of(), maintButton(screen), "clicked", null);
                boolean oldOpened = ((List<Map<String, Object>>) old.state().get("windows")).stream().anyMatch(w -> w.get("screen").equals("VHYTM6I"));
                // the Maint click's own call (VHYTM6I, once open, asks again for itself)
                var oldCrit = p.sent.stream().filter(e -> e.getKey().equals("VH01A02")).findFirst().orElseThrow().getValue().get("VH01A02_CRIT");
                p.clear();
                boolean allowed = new YardTrackingMapsService(p.legacy(), p.messages(), Parity.bundle())
                        .maintenanceAllowed(screen.equals("VHYT41I") ? "dublin-update" : "pittsburg-update", p.user.groupId());
                assertThat(p.lastSent("VH01A02").get("VH01A02_CRIT")).as(screen + " criteria").isEqualTo(oldCrit);
                assertThat(allowed).as(screen + " " + access).isEqualTo(oldOpened);
            }
        }
    }

    /** the update map's Maint button: the push button whose click runs the MAINT statement group */
    private static String maintButton(String screen) {
        for (var c : Parity.bundle().screen(screen).path("connections")) {
            var a = c.path("a");
            if (a.path(1).asText().equals("clicked") && a.path(2).path("id").asText().endsWith("IS-MAINT__reference")) {
                return a.path(0).path("id").asText().replaceFirst("^screen\\.", "");
            }
        }
        throw new IllegalStateException(screen + " has no Maint button");
    }
}
