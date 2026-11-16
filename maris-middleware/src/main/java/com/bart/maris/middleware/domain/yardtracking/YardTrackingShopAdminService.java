package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shop administration maps (MARIS_RUI/EGLSource/MRSYardTracking): old VHYT10I (Hayward shop, programs VHYT08C and
 * VHYT04A) and VHYT95I (Richmond shop, VHYT95A). Each shows the shop's cars on the tracks and spots of its LOAD (the
 * label its LOAD builds: "$" when SOFTVER is longer than one character, the orientation first when it is the row's own
 * left end; the colour dy100Color gives the home yard), and its spot buttons' two-click move: the first click picks the
 * car, the second the place it goes to, and then the map opens the shop car move VHYT03I. The clicks are replayed on the
 * old screen's own statement groups (its INIT, then each spot's CLICK group), so the pass values are the old ones.
 */
public final class YardTrackingShopAdminService {
    static final String PKG = YardTrackingMapsService.PKG;
    /** the window the second click opens */
    public static final String MOVE_SCREEN = "VHYT03I";

    /** one map: screen, its INIT, the program reading its cars (and the one VHYT10I reads its car types with), its LOAD */
    private record Spec(String screen, String init, String carTypes, String program, String crit, String data, String load) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("hayward", new Spec("VHYT10I", "VHYT10IS-INIT", "VHYT08C", "VHYT04A", "VHYT04A-CRIT", "VHYT04A-DATA", "VHYT10IS-LOAD"));
        SPECS.put("richmond", new Spec("VHYT95I", "VHYT95IS-INIT-S", null, "VHYT95A", "VHYT95A-CRIT", "VHYT95A-DATA", "VHYT95IS-LOAD"));
    }

    private record Def(Spec spec, String yard, Map<String, Set<Integer>> tracks, Map<String, String> clickGroups, Report report) {}

    private final Programs programs;
    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final Map<String, Def> maps = new LinkedHashMap<>();
    private final Map<String, List<String>> colours;

    public YardTrackingShopAdminService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.bundle = screens;
        this.legacy = legacy;
        this.clock = clock;
        // both LOADs colour a car with dy100Color (the shared colour function of the home yard)
        this.colours = YardReportsLayout.colours(screens, "dy100Color", PKG, "HOME_YARD", "theColor", null);
        SPECS.forEach((name, s) -> {
            Map<String, String> clicks = clickGroups(screens, s.screen());
            Map<String, Set<Integer>> tracks = YardTrackingMapsLayout.of(screens, s.load(), PKG);
            String shop = s.screen() + "S-SHOP";
            Report report = new Report(screens.assignedText(shop, PKG, "WHICH-REPORT"), screens.assignedText(shop, PKG, "YRD-TRKG-YRD"));
            maps.put(name, new Def(s, screens.assignedText(s.init(), PKG, "YRD-TRKG-YRD"), tracks, clicks, report));
            if (clicks.isEmpty() || tracks.isEmpty()) throw new IllegalStateException(s.screen() + "'s spots not found");
        });
    }

    /** a car on a spot: what the old button shows (label, colour) */
    public record Car(String track, int position, String car, String consist, String orientation, String label, String status,
                      String moveReason, String homeYard, String colour) {}

    /** a spot of the map; clickable when the old screen has a CLICK group for it */
    public record Spot(String track, int position, boolean clickable) {}

    /** what the old Print passes to the report screen (VHYT27I) */
    public record Report(String whichReport, String yard) {}

    public record ShopMap(String map, String screen, String yard, Report report, List<Spot> spots, List<Car> cars) {}

    /** a spot clicked on the map: "track/position", e.g. "TT/2" */
    public record Selection(String open, String message, Map<String, Object> params) {}

    public List<String> names() {
        return List.copyOf(maps.keySet());
    }

    /** &lt;screen&gt;S-INIT + -LOAD: the cars on the map's spots (a later row on a spot replaces an earlier one) */
    public ShopMap map(String name) {
        Def d = def(name);
        Spec s = d.spec();
        if (s.carTypes() != null) programs.run(s.carTypes(), Map.of());                 // VHYT10IS-INIT: the car types' orientations
        ProgramResult r = programs.run(s.program(), Map.of());
        Map<String, Car> spots = new LinkedHashMap<>();
        if (r.control().returnCode() != 100) {
            int count = r.control().rowCount();
            List<Map<String, Object>> list = rows(r, s.data(), "dataRow");
            for (int i = 0; i < Math.min(count, list.size()); i++) {
                Map<String, Object> row = list.get(i);
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                Set<Integer> known = d.tracks().get(track);
                if (known == null || (!known.isEmpty() && !known.contains(position))) continue;
                spots.put(known.isEmpty() ? track : track + "/" + position, car(row, track, position));
            }
        }
        List<Spot> all = new ArrayList<>();
        d.tracks().forEach((t, set) -> {
            if (set.isEmpty()) all.add(new Spot(t, 1, d.clickGroups().containsKey(t + "/1")));
            for (int p : set) all.add(new Spot(t, p, d.clickGroups().containsKey(t + "/" + p)));
        });
        return new ShopMap(name, s.screen(), d.yard(), d.report(), all, new ArrayList<>(spots.values()));
    }

    private Car car(Map<String, Object> row, String track, int position) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        boolean left = !orientation.isEmpty() && orientation.equals(text(row, "lftOrientationCd"));
        String label = left ? orientation + car : car + orientation;
        // strLib.characterLen(SOFTVER) > 1 (trailing blanks left out)
        if (String.valueOf(row.getOrDefault("softver", "")).stripTrailing().length() > 1) label = "$" + label;
        List<String> colour = colours.getOrDefault(text(row, "homeYrd"), colours.get(YardReportsLayout.OTHER_YARD));
        return new Car(track, position, car, text(row, "conNo"), orientation, label, text(row, "vehStat"), text(row, "moveReasonCd"),
                text(row, "homeYrd"), colour == null ? null : colour.get(0));
    }

    /**
     * The old map's clicks replayed on its own groups: its INIT loads the spots, then each clicked spot's CLICK group runs
     * (first click: the car to move; second: where it goes; a third resets with "TOO MANY CLICKS"). When the second click
     * completes the move the old map opens VHYT03I with these values.
     */
    public Selection select(String name, List<String> clicked) {
        Def d = def(name);
        if (clicked == null || clicked.isEmpty()) throw new InvalidRequestException("Choose a car on the map.");
        YardTrackingTrackScreenLogic s = new YardTrackingTrackScreenLogic(bundle, d.spec().screen(), legacy, clock);
        s.run(d.spec().init());
        for (String spot : clicked) {
            String group = d.clickGroups().get(spot == null ? "" : spot.strip());
            if (group == null) throw new InvalidRequestException("There is no spot " + spot + " on the map.");
            s.run(group);
            if (s.opened.contains(MOVE_SCREEN)) break;
        }
        String message = s.text(d.spec().screen() + "W-MESSAGE", "TOO-MANY-CLICK");
        if (!s.opened.contains(MOVE_SCREEN)) return new Selection(null, message, Map.of());
        // VHYT03I's attributes are bound to VHYT10IW-PASSVALUE (both maps' wiring)
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("carNo", s.text("VHYT10IW-PASSVALUE", "CAR-NO"));
        p.put("yrdTrkgYrd", s.text("VHYT10IW-PASSVALUE", "YRD-TRKG-YRD"));
        p.put("yrdSubLoc", s.text("VHYT10IW-PASSVALUE", "YRD-SUB-LOC"));
        p.put("newYrdLoc", s.text("VHYT10IW-PASSVALUE", "NEW-YRD-LOC"));
        p.put("carNo2", s.text("VHYT10IW-PASSVALUE", "CAR-NO-2"));
        p.put("posnNoFrom", s.number("VHYT10IW-PASSVALUE", "POSN-NO-FROM"));
        p.put("posnNoTo", s.number("VHYT10IW-PASSVALUE", "POSN-NO-TO"));
        p.put("whichGui", s.text("VHYT10IW-PASSVALUE", "WHICH-GUI"));
        return new Selection(MOVE_SCREEN, message, p);
    }

    private Def def(String name) {
        Def d = maps.get(name == null ? "" : name);
        if (d == null) throw new InvalidRequestException("Unknown shop map: " + name + ". Known maps: " + String.join(", ", maps.keySet()));
        return d;
    }

    // ---- reading the old screen ----------------------------------------------------------------------------

    /** spot "track/position" -&gt; the statement group its button's click runs (from the group's own first-click values) */
    private static Map<String, String> clickGroups(ScreenBundle screens, String screen) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode def = screens.screen(screen);
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (!"connectEventToAction".equals(c.path("m").asText()) || !a.path(0).path("id").asText().startsWith("screen.")
                    || !"clicked".equals(a.path(1).asText()) || !"execute".equals(a.path(3).asText())) continue;
            String to = a.path(2).path("id").asText();
            if (!to.endsWith("__reference")) continue;
            String group = to.substring(0, to.length() - 11);
            Ast.Function f = screens.function(group, PKG);
            if (f == null || f.body().isEmpty() || !(f.body().get(0) instanceof Ast.If first)) continue;
            String track = null;
            Integer position = null;
            for (Ast.Stmt st : first.then()) {
                if (!(st instanceof Ast.Assign as)) continue;
                String field = last(as.target());
                if (field.equalsIgnoreCase("YRD-SUB-LOC") && as.value() instanceof Ast.Str v) track = v.value();
                if (field.equalsIgnoreCase("POSN-NO-FROM") && as.value() instanceof Ast.Num n) position = n.value().intValue();
            }
            if (track != null && position != null) out.putIfAbsent(track + "/" + position, group);
        }
        return out;
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }
}
