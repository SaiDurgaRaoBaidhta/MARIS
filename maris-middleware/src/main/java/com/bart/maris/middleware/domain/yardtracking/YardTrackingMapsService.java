package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yard tracking maps (MARIS_RUI/EGLSource/MRSYardTracking): VHYT40I / VHYT41I (Dublin-Pleasanton, program VHYT40A),
 * VHYT50I / VHYT51I (Pittsburg-Bay Point, VHYT50A) and VHYT04I (Hayward shop, VHYT04A). The rules are the old screens':
 * the yard the INIT asks for, the tracks and spots of the LOAD and its per-track functions, the label (a left
 * orientation first), the inquiry maps' home-yard colours (their COLOR group; the shop's dy100Color), the update maps'
 * condition (GiveAColor). Read-only: the update maps' moves happen in the screens they open (VHYT11I ...).
 * The map layout reader is the yard reports module's (same rules, other EGL package).
 */
public class YardTrackingMapsService {
    static final String PKG = "MRSYardTracking";
    /** the old screens' left orientations: strLib.indexOf("&lt;O({vblm", orientation) &gt; 0 */
    private static final String LEFT = "<O({vblm";

    /**
     * one map: program and records, the INIT that sets the yard (null: none), its LOAD, its colours (null: by condition),
     * the statement group whose YRD-SUB-LOC is the pseudo track (null: none), the access check's GUI name source
     */
    private record Spec(String screen, String program, String crit, String data, String init, String load, Colours colours,
                        String pseudo, boolean shop) {}

    /** a colour statement group or function: if (yardField == "HY") textField = "GREEN"; [backgroundField = ...] */
    private record Colours(String function, String yardField, String textField, String background) {}

    private record Def(Spec spec, String yard, String pseudoTrack, Map<String, Set<Integer>> tracks, Map<String, List<String>> colours) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("dublin", new Spec("VHYT40I", "VHYT40A", "VHYT40A_CRIT", "VHYT40A_DATA", "VHYT40IS-INIT", "VHYT40IS-LOAD",
                new Colours("VHYT40IS-COLOR", "HOME-YRD", "CAR-COLOR", "CAR-COLOR-B"), "VHYT40IS-PSEUDO", false));
        SPECS.put("dublin-update", new Spec("VHYT41I", "VHYT40A", "VHYT40A_CRIT", "VHYT40A_DATA", "VHYT41IS-INIT", "VHYT41IS-LOAD",
                null, "VHYT41IS-PSEUDO", false));
        SPECS.put("pittsburg", new Spec("VHYT50I", "VHYT50A", "VHYT50A_CRIT", "VHYT50A_DATA", "VHYT50IS-INIT", "VHYT50IS-LOAD",
                new Colours("VHYT50IS-COLOR", "HOME-YRD", "CAR-COLOR", "CAR-COLOR-B"), "VHYT50IS-PSEUDO", false));
        SPECS.put("pittsburg-update", new Spec("VHYT51I", "VHYT50A", "VHYT50A_CRIT", "VHYT50A_DATA", "VHYT51IS-INIT", "VHYT51IS-LOAD",
                null, "VHYT51IS-PSEUDO", false));
        // VHYT04IS-INIT-S sends VHYT04A-CRIT as it is (no yard); dy100Color gives a text colour only
        SPECS.put("hayward-shop", new Spec("VHYT04I", "VHYT04A", "VHYT04A-CRIT", "VHYT04A-DATA", null, "VHYT04IS-LOAD",
                new Colours("dy100Color", "HOME_YARD", "theColor", null), null, true));
    }

    private final Programs programs;
    private final Map<String, Def> maps = new LinkedHashMap<>();
    /** VHYTIS-ACCES-MAINT: the part name the access check looks for, and each update map's GUI name (its MAINT group's VH01A02 GUI-NAME) */
    private final String maintPart;
    private final Map<String, String> guiNames = new LinkedHashMap<>();
    /** VHYT10IS-SHOP: what the Hayward shop's Print passes to VHYT27I */
    private final YardTrackingMap.ShopReport shopReport;

    public YardTrackingMapsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        SPECS.forEach((name, s) -> {
            Colours c = s.colours();
            maps.put(name, new Def(s, s.init() == null ? null : screens.assignedText(s.init(), PKG, "YRD-TRKG-YRD"),
                    // the inquiry maps put it in YRD-SUB-LOC, the update maps in FROM-YRD-SUB-LOC (VHYT41IS-PSEUDO)
                    s.pseudo() == null ? null : screens.assignedText(s.pseudo(), PKG, c == null ? "FROM-YRD-SUB-LOC" : "YRD-SUB-LOC"),
                    YardTrackingMapsLayout.of(screens, s.load(), PKG),
                    c == null ? null : YardReportsLayout.colours(screens, c.function(), PKG, c.yardField(), c.textField(), c.background())));
            // the GUI name the Maint button's own statement group asks for (VHYT51IS-MAINT asks for VHYT50, not VHYT51)
            if (c == null) guiNames.put(name, screens.assignedText(s.screen() + "S-MAINT", PKG, "GUI-NAME"));
        });
        this.maintPart = firstText(screens, "VHYTIS-ACCES-MAINT");
        this.shopReport = new YardTrackingMap.ShopReport(screens.assignedText("VHYT10IS-SHOP", PKG, "WHICH-REPORT"),
                screens.assignedText("VHYT10IS-SHOP", PKG, "YRD-TRKG-YRD"));
    }

    /** the maps this service knows */
    public List<String> names() {
        return List.copyOf(maps.keySet());
    }

    /**
     * &lt;screen&gt;IS-INIT and -LOAD: the cars the map shows, on the spots its LOAD knows (a later row on a spot replaces an
     * earlier one). A track with no spots listed is a one-spot track (the Hayward shop's 2N-1, 3N-1 ...).
     */
    public YardTrackingMap map(String name) {
        Def d = def(name);
        Spec s = d.spec();
        ProgramResult r = programs.run(s.program(), Map.of(s.crit(), d.yard() == null ? Map.of() : Map.of("yrdTrkgYrd", d.yard())));
        Map<String, YardReportsMap.Car> spots = new LinkedHashMap<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, s.data(), "dataRow")) {
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                Set<Integer> known = d.tracks().get(track);
                if (known == null || (!known.isEmpty() && !known.contains(position))) continue;
                // a track whose placer tests no position has one spot: its last row shows, whatever its position
                spots.put(known.isEmpty() ? track : track + "/" + position, car(row, track, position, d));
            }
        }
        List<YardReportsMap.Track> tracks = new ArrayList<>();
        d.tracks().forEach((t, set) -> tracks.add(new YardReportsMap.Track(t, List.copyOf(set))));
        return new YardTrackingMap(name, s.screen(), d.yard(), d.pseudoTrack(), s.shop() ? shopReport : null, tracks,
                new ArrayList<>(spots.values()));
    }

    /**
     * VHYT41IS-MAINT / VHYT51IS-MAINT + VHYTIS-ACCES-MAINT: may the user group open the maintenance menu (VHYTM6I)? The
     * access list (VH01A02) denies it only when it lists the Maint button with ACCESS-ALLOW "N".
     */
    public boolean maintenanceAllowed(String name, String userGroup) {
        Def d = def(name);
        String gui = guiNames.get(name);
        if (gui == null) throw new InvalidRequestException("The " + d.spec().screen() + " map has no Maint button.");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("userGrp", userGroup == null ? "" : userGroup);
        crit.put("guiName", gui);
        ProgramResult r = programs.run("VH01A02", Map.of("VH01A02_CRIT", crit));
        Object list = r.record("VH01A02_DATA") == null ? null : r.record("VH01A02_DATA").get("userGroupAccess");
        if (list instanceof List<?> rows) {
            for (Object o : rows) {
                if (o instanceof Map<?, ?> row && maintPart.equals(String.valueOf(row.get("partName") == null ? "" : row.get("partName")).strip())) {
                    return !"N".equals(String.valueOf(row.get("accessAllow") == null ? "" : row.get("accessAllow")).strip());
                }
            }
        }
        return true;
    }

    private Def def(String name) {
        Def d = maps.get(name);
        if (d == null) throw new InvalidRequestException("Unknown yard tracking map: " + name + ". Known maps: " + String.join(", ", maps.keySet()));
        return d;
    }

    private static YardReportsMap.Car car(Map<String, Object> row, String track, int position, Def d) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        boolean shop = d.spec().shop();
        // the shop puts the orientation first when it is the row's own left end (LFT-ORIENTATION-CD); the yards use the old set
        boolean left = !orientation.isEmpty() && (shop ? orientation.equals(text(row, "lftOrientationCd")) : LEFT.contains(orientation));
        String label = left ? orientation + car : car + orientation;
        // VHYT04IS-LOAD: "$" first when SOFTVER is longer than one character (strLib.characterLen leaves out trailing blanks)
        if (shop && String.valueOf(row.getOrDefault("softver", "")).stripTrailing().length() > 1) label = "$" + label;
        List<String> colour = d.colours() == null ? null
                : d.colours().getOrDefault(text(row, "homeYrd"), d.colours().get(YardReportsLayout.OTHER_YARD));
        String condition = d.colours() == null ? condition(text(row, "vehStat"), text(row, "trainIncdInd"), number(row, "hpmhrs")) : null;
        return new YardReportsMap.Car(track, position, car, text(row, "conNo"), orientation, label, text(row, "vehStat"), condition,
                text(row, "moveReasonCd"), text(row, "homeYrd"), colour == null ? null : colour.get(0),
                colour == null || colour.size() < 2 ? null : colour.get(1));
    }

    /** GiveAColor: GO without a train incident (PM due = brown, else black), USABLE without one (blue), anything else red */
    static String condition(String status, String incident, long pmDue) {
        if (incident.equals("Y")) return "not-usable";
        if (status.equals("GO")) return pmDue == 1 ? "go-pm-due" : "go";
        if (status.equals("USABLE")) return "usable";
        return "not-usable";
    }

    /** the first text literal a function's statements test, e.g. "pbtMaint" in VHYTIS-ACCES-MAINT's {@code "pbtMaint" in VH01A02_DATA.PART-NAME} */
    private static String firstText(ScreenBundle screens, String function) {
        Ast.Function f = screens.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        for (Ast.Stmt st : f.body()) {
            if (st instanceof Ast.If i) {
                String t = literal(i.cond());
                if (t != null) return t;
            }
        }
        throw new IllegalStateException(function + " tests no text");
    }

    private static String literal(Ast.Expr e) {
        return switch (e) {
            case Ast.Str s -> s.value();
            case Ast.InTest in -> literal(in.value());
            case Ast.Binary b -> { String l = literal(b.left()); yield l != null ? l : literal(b.right()); }
            case Ast.Unary u -> literal(u.operand());
            default -> null;
        };
    }
}
