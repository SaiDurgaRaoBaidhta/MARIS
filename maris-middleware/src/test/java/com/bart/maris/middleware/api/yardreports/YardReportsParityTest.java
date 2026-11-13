package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardreports.YardReportsService;
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
 * The yard map clean API against the old map screens run by the engine: same backend, same request, same cars shown.
 * Every track of the map gets a car on every spot and two beyond, plus a track the map does not have; the old screen
 * and the API must show the same cars (compared as a list of what each spot shows, so widget names do not matter).
 */
class YardReportsParityTest {
    private static final String PKG = "MRSYardTrackingReport";
    /** the colours GiveAColor gives the update maps, by the API's condition */
    private static final Map<String, String> OLD_COLOUR = Map.of("go", "BLACK", "go-pm-due", "BROWN", "usable", "BLUE", "not-usable", "RED");
    private static final String[] YARDS = {"HY", "RY", "CY", "DY", "MY"};
    private static final String[] STATUS = {"GO", "USABLE", "NO GO"};
    private static final String[] ORIENTATION = {"<", ">", "O", "b", "("};
    private static final String[] SOFTWARE = {"", "V2", "$V3", " "};

    private Parity p;

    /** rows for every spot of the map, two more per track, and one unknown track; car numbers are unique */
    private static List<Map<String, Object>> rows(String load, boolean conditionFields, boolean software) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Set<Integer>> layout = new java.util.LinkedHashMap<>(YardReportsLayout.of(Parity.bundle(), load, PKG));
        layout.put("T-95", Set.of(1));
        int n = 0;
        for (var t : layout.entrySet()) {
            int last = t.getValue().stream().max(Integer::compare).orElse(0);
            for (int pos = 1; pos <= last + 2; pos++, n++) {
                // PM due (HPMHRS 1) is never sent: the engine compares GiveAColor's SMALLINT parameter wrongly and always
                // shows BLACK where the EGL shows BROWN; YardReportsApiTest covers go-pm-due. Re-add once engine/** is fixed.
                Map<String, Object> row = new java.util.HashMap<>(Map.of("carNo", String.valueOf(1000 + n), "conNo", "532",
                        "vagenOrientation", ORIENTATION[n % 5], "yrdSubLoc", t.getKey(), "posnNo", pos, "vehStat", STATUS[n % 3],
                        "homeYrd", YARDS[n % 5]));
                if (conditionFields) {
                    row.put("trainIncdInd", n % 7 == 0 ? "Y" : "N");
                    row.put("hpmhrs", 0);
                }
                if (software) row.put("softver", SOFTWARE[n % 4]);
                rows.add(row);
            }
        }
        return rows;
    }

    private void backend(String program, String data, String load) {
        p = new Parity();
        List<Map<String, Object>> rows = rows(load, !data.equals("VHYT80A_DATA"), !data.equals("VHYT80A_DATA") && !data.equals("VHIC01A_DATA"));
        p.on(program, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), data, Map.of("dataRow", rows)));
    }

    /** the old screen's car spots (widgets bound to a BUTTON-n or ORIENTATION-n field) that show something */
    @SuppressWarnings("unchecked")
    private List<String> oldSpots(String screen, Function<Map<String, Object>, String> show) {
        Set<String> spots = new HashSet<>();
        for (var c : Parity.bundle().screen(screen).path("connections")) {
            var a = c.path("a");
            if (c.path("m").asText().equals("connectAttributes") && a.path(1).asText().matches("(BUTTON|ORIENTATION)-?\\d+ data")
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
                if (spots.contains(name) && wd.get("value") != null && !wd.get("value").toString().isBlank()) out.add(show.apply(wd));
            });
        }
        return out;
    }

    /**
     * @param oldDefects "TRACK/spot" places the old screen draws wrongly (a widget wired to another spot, a missing colour
     *                   binding); the API reports them correctly, so both sides leave out the cars there
     */
    private void same(String screen, String program, String map, Function<Map<String, Object>, String> old,
                      Function<YardReportsMap.Car, String> clean, Set<String> oldDefects) {
        List<String> shown = oldSpots(screen, old);
        Map<String, Map<String, Object>> oldSent = p.lastSent(program);
        p.clear();
        YardReportsMap m = new YardReportsService(p.legacy(), p.messages(), Parity.bundle()).map(map);
        assertThat(p.lastSent(program).get(program + "_CRIT")).isEqualTo(oldSent.get(program + "_CRIT"));
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

    /** an inquiry spot: label, text colour (bound to foregroundColor, or to color on VHYT90I), background */
    private static String inquiry(Map<String, Object> w) {
        return w.get("value") + " " + (w.get("fg") != null ? w.get("fg") : w.get("color")) + "/" + w.get("bg");
    }

    @Test
    void berryessaInquiry() {
        backend("VHIC01A", "VHIC01A_DATA", "VHIC01IS-LOAD");
        // VHIC01: SSTOR3 spot 9 is wired to widget SSTOR1TXT09, so the old map shows one of the two cars on SSTOR1's spot
        same("VHIC01", "VHIC01A", "berryessa", YardReportsParityTest::inquiry, c -> c.label() + " " + c.colour() + "/" + c.background(),
                Set.of("SSTOR3/9", "SSTOR1/9"));
    }

    @Test
    void berryessaUpdate() {
        backend("VHIC01A", "VHIC01A_DATA", "VHIC02IS-LOAD");
        // VHIC02: SSTOR3 spot 4 has no colour binding (CAR-4-COLOR is set but not shown)
        same("VHIC02", "VHIC01A", "berryessa", w -> w.get("value") + " " + w.get("color"), c -> c.label() + " " + OLD_COLOUR.get(c.condition()),
                Set.of("SSTOR3/4"));
    }

    /** the old Millbrae spots show only the orientation (CAR-NO is not on the screen) */
    @Test
    void millbraeInquiry() {
        backend("VHYT80A", "VHYT80A_DATA", "VHYT80IS-LOAD");
        same("VHYT80I", "VHYT80A", "millbrae", YardReportsParityTest::inquiry, c -> c.orientation() + " " + c.colour() + "/" + c.background(), Set.of());
    }

    @Test
    void millbraeUpdate() {
        backend("VHYT80A", "VHYT80A_DATA", "VHYT81IS-LOAD");
        same("VHYT81I", "VHYT80A", "millbrae", w -> w.get("value").toString(), YardReportsMap.Car::orientation, Set.of());
    }

    private static String update(YardReportsMap.Car c) {
        return c.label() + " " + OLD_COLOUR.get(c.condition());
    }

    private static String inquiryOf(YardReportsMap.Car c) {
        return c.label() + " " + c.colour() + "/" + c.background();
    }

    @Test
    void dalyCityYardInquiry() {
        backend("VHYTX5A", "VHYT105A_DATA", "VHYT105IS-LOAD");
        same("VHYT105I", "VHYTX5A", "daly-city-yard", YardReportsParityTest::inquiry, YardReportsParityTest::inquiryOf, Set.of());
    }

    @Test
    void dalyCityYardUpdate() {
        backend("VHYTX5A", "VHYT105A_DATA", "VHYT106IS-LOAD");
        // VHYT106IS-RR-1A writes spot 2's colour to CAR-1-STAT-COLOR (an EGL copy slip), so spot 1 shows spot 2's colour
        same("VHYT106I", "VHYTX5A", "daly-city-yard", w -> w.get("value") + " " + w.get("color"), YardReportsParityTest::update,
                Set.of("RR-1A/1", "RR-1A/2"));
    }

    @Test
    void concordYardInquiry() {
        backend("VHYT70A", "VHYT70A_DATA", "VHYT70IS-LOAD");
        // VHYT70I: ST17TXT5 does not show VHYT70IW-ST17's CAR-5 colours
        same("VHYT70I", "VHYT70A", "concord-yard", YardReportsParityTest::inquiry, YardReportsParityTest::inquiryOf, Set.of("ST-17/5"));
    }

    @Test
    void concordYardUpdate() {
        backend("VHYT70A", "VHYT70A_DATA", "VHYT71IS-LOAD");
        // VHYT71I: BUTTON-4 of ST13 and BUTTON-1 of ST25 are bound to no widget
        same("VHYT71I", "VHYT70A", "concord-yard", w -> w.get("value") + " " + w.get("color"), YardReportsParityTest::update,
                Set.of("ST-13/4", "ST-25/1"));
    }

    @Test
    void richmondYardInquiry() {
        backend("VHYT90A", "VHYT90A_DATA", "VHYT90IS-LOAD");
        // VHYT90I: BUTTON-1 of TR-1 is bound to no widget
        same("VHYT90I", "VHYT90A", "richmond-yard", YardReportsParityTest::inquiry, YardReportsParityTest::inquiryOf, Set.of("TR-1/1"));
    }

    @Test
    void richmondYardUpdate() {
        backend("VHYT90A", "VHYT90A_DATA", "VHYT91IS-LOAD");
        // VHYT91I: tracks S-1, S-2, S-3 and TR-1 are bound to no widget (the inquiry map shows them); ST-15 spot 17 has no colour
        same("VHYT91I", "VHYT90A", "richmond-yard", w -> w.get("value") + " " + w.get("color"), YardReportsParityTest::update,
                Set.of("S-1/1", "S-1/2", "S-2/1", "S-2/2", "S-3/1", "S-3/2", "TR-1/1", "TR-1/2", "TR-1/3", "TR-1/4", "ST-15/17"));
    }

    /** VHSW01I marks a location with a text when a car is there; the API lists the cars at each location */
    @Test
    @SuppressWarnings("unchecked")
    void systemWide() {
        p = new Parity();
        List<Map<String, Object>> rows = List.of(
                Map.of("carNo", "1001", "yrdSubLoc", "BAYFAIR", "posnNo", 1), Map.of("carNo", "1002", "yrdSubLoc", "BAYFAIR", "posnNo", 2),
                Map.of("carNo", "1003", "yrdSubLoc", "OKS", "posnNo", 1), Map.of("carNo", "1004", "yrdSubLoc", "T-95", "posnNo", 1));
        p.on("VHSW01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHSW01A_DATA", Map.of("dataRow", rows)));
        ScreenSession old = p.oldScreens();
        old.openRoot("VHSW01I", p.user);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHSW01A");
        Map<String, String> marker = Map.of("MACART", "Push_Button414", "OKS", "Push_Button4141", "BAYFAIR", "Push_Button4142", "M90", "Push_Button4143");
        Map<String, Map<String, Object>> widgets = null;
        for (Map<String, Object> w : (List<Map<String, Object>>) old.state().get("windows")) {
            if (w.get("screen").equals("VHSW01I")) widgets = (Map<String, Map<String, Object>>) w.get("widgets");
        }

        p.clear();
        var clean = new YardReportsService(p.legacy(), p.messages(), Parity.bundle()).systemWide();
        assertThat(p.lastSent("VHSW01A").get("VHSW01A_CRIT")).isEqualTo(oldSent.get("VHSW01A_CRIT"));
        for (var loc : clean.locations()) {
            Object shown = widgets.get(marker.get(loc.location())).get("value");
            assertThat(shown != null && !shown.toString().isBlank()).as(loc.location()).isEqualTo(!loc.cars().isEmpty());
        }
    }
}
