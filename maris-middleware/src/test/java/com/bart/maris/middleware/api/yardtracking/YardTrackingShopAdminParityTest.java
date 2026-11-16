package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopAdminService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopMoveService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The shop administration maps (old VHYT10I Hayward, VHYT95I Richmond) and the shop car move they open (VHYT03I with its
 * reason check VHYT42I): the clean API against the old screens run by the engine, same scripted backend.
 */
class YardTrackingShopAdminParityTest {
    static final String TERMINAL = "MARIS-UI";

    /** the scripted backend; occupied = the car VHYT03J finds at the new location ("" = none), bidir = VHYT03D's insert flag */
    static Parity backend(String reason, String occupied, String bidir) {
        Parity p = new Parity();
        p.on("VHYT08C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT08C_DATA", Map.of("dataRow", List.of(
                Map.of("lftOrientationCd", "<", "rgtOrientationCd", ">", "carType", "A2"),
                Map.of("lftOrientationCd", "v", "rgtOrientationCd", "^", "carType", "C1")))));
        List<Map<String, Object>> cars = List.of(
                car("1101", "<", "TT", 2, "GO", "  ", "HY"), car("1102", ">", "TT", 3, "USABLE", "$1", "CY"),
                car("1103", "v", "1B", 1, "NO GO", "", "RY"), car("1104", " ", "B-PIT-01", 1, "GO", "", "HY"));
        p.on("VHYT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars.size()), "VHYT04A-DATA", Map.of("dataRow", cars)));
        List<Map<String, Object>> ry = List.of(car("1201", "<", "RR-01", 1, "GO", "", "RY"), car("1202", ">", "RR-03", 1, "GO", "$22", "DY"),
                car("1203", "v", "B-PIT-01", 1, "NO GO", "", "HY"));
        p.on("VHYT95A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", ry.size()), "VHYT95A-DATA", Map.of("dataRow", ry)));
        p.on("VHYT03A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHYT03A_DATA", Map.of("dataRow", List.of(
                Map.of("moveReasonCd", "M1", "filler", " - ", "moveReasonDesc", "WAITING FOR PARTS"),
                Map.of("moveReasonCd", "17", "filler", " - ", "moveReasonDesc", "MUST CHANGE"),
                Map.of("moveReasonCd", "P2", "filler", " - ", "moveReasonDesc", "PM INSPECTION")))));
        p.on("VHYT03B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHYT03B_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", "TT", "filler", " - ", "yrdSubLocDesc", "TRANSFER TABLE"),
                Map.of("yrdSubLoc", "1B", "filler", " - ", "yrdSubLocDesc", "SHOP 1 B"),
                Map.of("yrdSubLoc", "B-PIT-01", "filler", " - ", "yrdSubLocDesc", "BLOWER PIT 1")))));
        p.on("VHYT03F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT03F_DATA", Map.of("yrdDesc", "HAYWARD")));
        p.on("VHYT03D", in -> {
            Map<String, Object> crit = in.get("VHYT03D_CRIT");
            Map<String, Object> d = new HashMap<>();
            d.put("carNo", crit.get("carNo")); d.put("yrdSubLoc", "TT"); d.put("yrdSubLocDesc", "TRANSFER TABLE");
            d.put("moveReasonCdX", Map.of("moveReasonCd", reason, "moveFiller", "-", "moveReasonDesc", "REASON " + reason));
            d.put("posnNo", 2); d.put("biDirInsertInd", bidir); d.put("shiftCarsCd", "SN");
            return Map.of("XDCONTROL", Map.of("urc", "9999".equals(crit.get("carNo")) ? 100 : 0, "urowcnt", 1), "VHYT03D_DATA", d);
        });
        p.on("VHYT03E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT03E_DATA", Map.of("vagenOrientation", "<", "vehStat", "GO", "trainIncdInd", "Y")));
        p.on("VHYT03J", in -> Map.of("XDCONTROL", Map.of("urc", occupied.isEmpty() ? 100 : 0, "urowcnt", occupied.isEmpty() ? 0 : 1),
                "VHYT03J_DATA", Map.of("dataRow", occupied.isEmpty() ? List.of() : List.of(Map.of("carNo", occupied, "posnNo", 1)))));
        p.on("VHYT03G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT03G_CRIT", in.get("VHYT03G_CRIT")));
        p.on("VHYT03H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT03H_CRIT", in.get("VHYT03H_CRIT")));
        return p;
    }

    static Map<String, Object> car(String car, String orientation, String track, int posn, String stat, String softver, String home) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", car); m.put("conNo", "201"); m.put("vagenOrientation", orientation); m.put("yrdSubLoc", track); m.put("posnNo", posn);
        m.put("vehStat", stat); m.put("moveReasonCd", "M1"); m.put("lftOrientationCd", "<"); m.put("rgtOrientationCd", ">"); m.put("carType", "A2");
        m.put("homeYrd", home); m.put("softver", softver); m.put("trainIncdInd", "N"); m.put("hpmhrs", 0);
        return m;
    }

    static YardTrackingShopAdminService maps(Parity p) {
        return new YardTrackingShopAdminService(p.legacy(), p.messages(), Parity.bundle(), p.clock);
    }

    static YardTrackingShopMoveService move(Parity p) {
        return new YardTrackingShopMoveService(p.legacy(), Parity.bundle(), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> screen.equalsIgnoreCase((String) w.get("screen")))
                .map(w -> (String) w.get("id")).reduce((a, b) -> b).orElse(null);
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!screen.equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            Object v = x == null ? null : x.get("value");
            return v == null ? "" : v.toString().strip();
        }
        return "(no window)";
    }

    /** the old map's spot buttons: "track/position" -&gt; the widget whose click runs that spot's group */
    static Map<String, String> buttons(String screen) {
        Map<String, String> byGroup = new HashMap<>();
        for (JsonNode c : Parity.bundle().screen(screen).path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(0).path("id").asText().startsWith("screen.") && a.path(1).asText().equals("clicked") && a.path(3).asText().equals("execute"))
                byGroup.put(a.path(2).path("id").asText().replace("__reference", ""), a.path(0).path("id").asText().substring(7));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : SPOT_GROUPS.get(screen).entrySet()) out.put(e.getKey(), byGroup.get(e.getValue()));
        return out;
    }

    /** the groups the spots used here run (the old screens' own names) */
    static final Map<String, Map<String, String>> SPOT_GROUPS = Map.of(
            "VHYT10I", Map.of("TT/2", "VHYT10IS-CLICK-TT-2", "TT/3", "VHYT10IS-CLICK-TT-3", "1B/1", "VHYT10IS-CLICK-1B",
                    "B-PIT-01/1", "VHYT10IS-CLICK-B-PIT-01", "TT/5", "VHYT10IS-CLICK-TT-5"),
            "VHYT95I", Map.of("RR-01/1", "VHYT95IS-CK-RR01-1", "RR-03/1", "VHYT95IS-CK-RR03-1", "B-PIT-01/1", "VHYT95IS-CK-B-PIT-01"));

    static ScreenSession oldMap(Parity p, String screen) {
        ScreenSession s = p.oldScreens();
        s.openRoot(screen, p.user);
        return s;
    }

    @ParameterizedTest
    @CsvSource({"hayward,VHYT10I", "richmond,VHYT95I"})
    void theCarsShowAsOnTheOldMap(String map, String screen) {
        Parity p = backend("M1", "", "N");
        ScreenSession s = oldMap(p, screen);
        var oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();
        p.clear();
        var m = maps(p).map(map);
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        Map<String, String> buttons = buttons(screen);
        int checked = 0;
        for (var car : m.cars()) {
            String w = buttons.get(car.track() + "/" + car.position());
            if (w == null) continue;
            assertThat(car.label()).as(car.track() + "/" + car.position()).isEqualTo(value(s, screen, w));
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(2);
        assertThat(m.spots()).anyMatch(x -> x.clickable() && x.track().equals(m.cars().get(0).track()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "hayward|VHYT10I|TT/2;1B/1",            // car to an occupied spot
            "hayward|VHYT10I|TT/2;TT/5",            // car to a free spot on the same track
            "hayward|VHYT10I|B-PIT-01/1;TT/5",
            "hayward|VHYT10I|TT/2",                 // not complete
            "hayward|VHYT10I|TT/2;TT/2",            // the same spot twice: reset
            "richmond|VHYT95I|RR-01/1;RR-03/1",
            "richmond|VHYT95I|B-PIT-01/1;RR-01/1"})
    void theClicksOpenTheCarMoveAsBefore(String map, String screen, String list) {
        Parity p = backend("M1", "", "N");
        ScreenSession s = oldMap(p, screen);
        String id = windowId(s, screen);
        Map<String, String> buttons = buttons(screen);
        List<String> clicks = new ArrayList<>(List.of(list.split(";")));
        for (String c : clicks) if (windowId(s, "VHYT03I") == null) s.event(id, List.of(), buttons.get(c), "clicked", null);
        boolean opened = windowId(s, "VHYT03I") != null;
        ScreenInstance w = s.window(id);
        var sel = maps(p).select(map, clicks);
        assertThat(sel.open() != null).as("VHYT03I opened").isEqualTo(opened);
        if (!opened) return;
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("carNo", text(w, "CAR-NO"));
        old.put("yrdTrkgYrd", text(w, "YRD-TRKG-YRD"));
        old.put("yrdSubLoc", text(w, "YRD-SUB-LOC"));
        old.put("newYrdLoc", text(w, "NEW-YRD-LOC"));
        old.put("carNo2", text(w, "CAR-NO-2"));
        old.put("posnNoFrom", num(w, "POSN-NO-FROM"));
        old.put("posnNoTo", num(w, "POSN-NO-TO"));
        old.put("whichGui", text(w, "WHICH-GUI"));
        // the old map's WHICH-GUI is set back to its own value by VHYT03I's INIT; compare it as the map left it before opening
        Map<String, Object> mine = new LinkedHashMap<>(sel.params());
        mine.remove("whichGui");
        old.remove("whichGui");
        assertThat(mine).isEqualTo(old);
    }

    static String text(ScreenInstance w, String field) {
        Object v = w.recordPart("VHYT10IW-PASSVALUE").get(field + " data");
        return v == null ? "" : v.toString().strip();
    }

    static long num(ScreenInstance w, String field) {
        Object v = w.recordPart("VHYT10IW-PASSVALUE").get(field + " data");
        return v == null || v.toString().isBlank() ? 0 : new java.math.BigDecimal(v.toString().strip()).longValue();
    }

    // ---- VHYT03I ------------------------------------------------------------------------------------------

    /** opens VHYT03I in the old screens the way the Hayward map does (its two clicks) */
    static ScreenSession oldMove(Parity p, String from, String to) {
        ScreenSession s = oldMap(p, "VHYT10I");
        String id = windowId(s, "VHYT10I");
        Map<String, String> buttons = buttons("VHYT10I");
        s.event(id, List.of(), buttons.get(from), "clicked", null);
        s.event(id, List.of(), buttons.get(to), "clicked", null);
        assertThat(windowId(s, "VHYT03I")).as("VHYT03I opened").isNotNull();
        return s;
    }

    static YardTrackingShopMoveService.Context context(Parity p, String from, String to) {
        var sel = maps(p).select("hayward", List.of(from, to));
        Map<String, Object> m = sel.params();
        return new YardTrackingShopMoveService.Context((String) m.get("carNo"), (String) m.get("yrdTrkgYrd"), (String) m.get("yrdSubLoc"),
                (String) m.get("newYrdLoc"), (String) m.get("carNo2"), ((Number) m.get("posnNoFrom")).intValue(),
                ((Number) m.get("posnNoTo")).intValue(), (String) m.get("whichGui"));
    }

    @Test
    void theMoveWindowOpensAsBefore() {
        Parity p = backend("M1", "", "N");
        ScreenSession s = oldMove(p, "TT/2", "TT/5");
        var oldCalls = sent(p, "VHYT03");
        p.clear();
        var view = move(p).open(context(p, "TT/2", "TT/5"), p.user.groupId(), null);
        assertThat(sent(p, "VHYT03")).isEqualTo(oldCalls);
        assertThat(view.carNo()).isEqualTo(value(s, "VHYT03I", "Text1"));
        assertThat(view.status()).isEqualTo(value(s, "VHYT03I", "Text7"));
        assertThat(view.location()).isEqualTo(value(s, "VHYT03I", "Text4"));
        assertThat(view.yard()).isEqualTo(value(s, "VHYT03I", "Text3"));
        assertThat(view.currentReason()).isEqualTo(value(s, "VHYT03I", "CrntRsnCD_txt"));
        assertThat(view.message()).isEqualTo(value(s, "VHYT03I", "Text6"));
        assertThat(view.reasons()).hasSize(3);
        assertThat(view.locations()).hasSize(3);
    }

    /** VHYT03I's own programs, in call order (record by record), from the map's clicks on */
    static List<Object> sent(Parity p, String prefix) {
        List<Object> out = new ArrayList<>();
        for (var e : p.sent) {
            if (!e.getKey().startsWith(prefix)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            e.getValue().forEach((k, v) -> { if (!k.equals("XDCONTROL")) m.put(k, v); });
            out.add(Map.of(e.getKey(), m));
        }
        return out;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "M1|''|N|P2|TT|''|false",            // reason only, same location: VHYT03G
            "M1|''|N|P2|1B|''|false",            // reason and location: VHYT03H
            "M1|''|N|M1|TT|''|false",            // same reason, same location: the reason check asks
            "M1|''|N|M1|TT|''|true",             // ... "Reason Is Not Changing": update goes on
            "17|''|N|17|1B|''|false",            // reason 17 must change
            "M1|1301|N|P2|1B|''|false",          // the new location is occupied (VHYT03J)
            "M1|''|Y|P2|1B|''|false",            // inserts at the end of a track: compress direction asked
            "M1|''|Y|P2|1B|NORTH|false"})
    void updateSendsWhatTheOldScreenSends(String reason, String occupied, String bidir, String newReason, String newLoc, String compress,
                                          boolean keep) {
        Parity p = backend(reason, occupied, bidir);
        ScreenSession s = oldMove(p, "TT/2", "TT/5");
        String id = windowId(s, "VHYT03I");
        // choose in the old window: the reason row, the location row, the compress direction, then Update
        int r = index(List.of("M1", "17", "P2"), newReason), l = index(List.of("TT", "1B", "B-PIT-01"), newLoc);
        s.event(id, List.of(), "NRsnCD_Drpwn", "select", r);
        s.event(id, List.of(), "Drop-down_List2", "select", l);
        if (!compress.isEmpty()) s.event(id, List.of(), "Radio_Button_Set1", "select", compress.equals("NORTH") ? 1 : 2);
        int before = p.sent.size();
        s.event(id, List.of(), "Push_Button1", "clicked", null);
        boolean asked = windowId(s, "VHYT42I") != null;
        if (asked && keep) s.event(windowId(s, "VHYT42I"), List.of(), "RsnNotChged_btn", "clicked", null);
        var oldWrites = writes(p, before);
        String oldMessage = value(s, "VHYT03I", "Text6");
        p.clear();
        var ctx = context(p, "TT/2", "TT/5");
        int start = p.sent.size();
        var out = move(p).update(ctx, p.user.groupId(), null, newReason, newLoc, compress, keep);
        assertThat(out.question() != null).as("the reason check asked").isEqualTo(asked && !keep);
        assertThat(writes(p, start)).isEqualTo(oldWrites);
        assertThat(out.message()).isEqualTo(oldMessage);
    }

    static int index(List<String> codes, String code) {
        return codes.indexOf(code) + 1;
    }

    /** the writing programs' records sent after the given call (VHYT03G, VHYT03H) */
    static List<Object> writes(Parity p, int from) {
        List<Object> out = new ArrayList<>();
        for (int i = from; i < p.sent.size(); i++) {
            var e = p.sent.get(i);
            if (e.getKey().equals("VHYT03G")) out.add(Map.of("VHYT03G", e.getValue().get("VHYT03G_CRIT")));
            if (e.getKey().equals("VHYT03H")) out.add(Map.of("VHYT03H", e.getValue().get("VHYT03H_CRIT")));
        }
        return out;
    }
}
