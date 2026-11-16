package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Hayward yard update map (old VHYT05I): the cars VHYT07A returns on the tracks and spots of VHYT05IS-LOAD-X, each
 * with the label and colour its per-track group gives (a left orientation first, "$" when SOFTVER is set, GiveAColor's
 * condition), the Maint button's access check (VH01A02), and the old three-click move selection (VHYT05IS-CLICK and its
 * VHYT05IS-CLK-.. groups, run as they are) that opens the car move detail VHYT11I.
 *
 * <p>The spots' click codes (TRAK-POS, e.g. "T-1001") and the track buttons' codes (TRACK-NO) come from the screen's own
 * wiring: a spot's group writes VHYT07IW-xx.BUTTON-n, a button shows that field, and its click sends a constant.
 */
public final class YardTrackingHaywardService {
    static final String SCREEN = "VHYT05I";
    private static final String PKG = YardTrackingMapsService.PKG;

    private final Programs programs;
    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String yard, guiName, maintPart, pseudoTrack, left;
    private final Map<String, Set<Integer>> tracks;
    /** "track/position" -> the spot's click code; the track buttons: their label and TRACK-NO */
    private final Map<String, String> spotCodes = new LinkedHashMap<>();
    private final List<TrackButton> trackButtons = new ArrayList<>();

    public YardTrackingHaywardService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.bundle = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.yard = screens.assignedText("VHYT05IS-REPORT", PKG, "YRD-TRKG-YRD");
        this.guiName = screens.assignedText("VHYT05IS-MAINT", PKG, "GUI-NAME");
        this.pseudoTrack = screens.assignedText("VHYT05IS-PSEUDO", PKG, "FROM-YRD-TRKG-YRD");
        this.maintPart = firstText(screens, "VHYTIS-ACCES-MAINT");
        this.tracks = YardTrackingMapsLayout.of(screens, "VHYT05IS-LOAD-X", PKG);
        String l = null;
        Map<String, String> placers = placers(screens.function("VHYT05IS-LOAD-X", PKG).body(), new LinkedHashMap<>());
        if (!placers.isEmpty()) l = indexOfText(screens.function(placers.values().iterator().next(), PKG).body());
        if (l == null) throw new IllegalStateException("VHYT05I's track groups test no left orientations");
        this.left = l;
        wiring(screens.screen(SCREEN), placers);
        if (yard == null || guiName == null || spotCodes.isEmpty()) throw new IllegalStateException("VHYT05I's map rules not found");
    }

    public record TrackButton(String label, String code) {}

    /** a car on a spot: what the old button shows and its colour rule, and the code its click sends */
    public record Car(String track, int position, String code, String car, String consist, String orientation, String label,
                      String status, String condition, String moveReason, String homeYard) {}

    public record Track(String track, List<Integer> spots) {}

    public record HaywardMap(String yard, String pseudoTrack, boolean maintenance, List<Track> tracks, List<TrackButton> trackButtons,
                             List<Car> cars) {}

    /** one click on the map: a car spot (its code), a track button (its code), or the pseudo track */
    public record Click(String spot, String track, boolean pseudo) {}

    /** the window the clicks open (null while the selection is not complete) and what the old map passes to it */
    public record Selection(String open, Map<String, Object> params) {}

    /** VHYT05IS-INIT-X + -LOAD-X, and VHYTIS-GRP-ACCES for the Maint button */
    public HaywardMap map(String userGroup) {
        ProgramResult r = programs.run("VHYT07A", Map.of("VHYT07A_CRIT", Map.of()));
        Map<String, Car> spots = new LinkedHashMap<>();
        if (r.control().returnCode() != 100) {
            int count = r.control().rowCount();
            List<Map<String, Object>> list = rows(r, "VHYT07A_DATA", "dataRow");
            for (int i = 0; i < Math.min(count, list.size()); i++) {
                Map<String, Object> row = list.get(i);
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                Set<Integer> known = tracks.get(track);
                if (known == null || (!known.isEmpty() && !known.contains(position))) continue;
                spots.put(track + "/" + position, car(row, track, position));                // a later row on a spot replaces an earlier one
            }
        }
        List<Track> t = new ArrayList<>();
        tracks.forEach((k, v) -> t.add(new Track(k, List.copyOf(v))));
        return new HaywardMap(yard, pseudoTrack, maintenanceAllowed(userGroup), t, List.copyOf(trackButtons), new ArrayList<>(spots.values()));
    }

    private Car car(Map<String, Object> row, String track, int position) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        String label = !orientation.isEmpty() && left.contains(orientation) ? orientation + car : car + orientation;
        if (!text(row, "softver").isEmpty()) label = "$" + label;                         // strLib.characterLen(strLib.clip(SOFTVER)) > 0
        return new Car(track, position, spotCodes.get(track + "/" + position), car, text(row, "conNo"), orientation, label, text(row, "vehStat"),
                YardTrackingMapsService.condition(text(row, "vehStat"), text(row, "trainIncdInd"), number(row, "hpmhrs")),
                text(row, "moveReasonCd"), text(row, "homeYrd"));
    }

    /** VHYT01IS-INIT-Y + VHYTIS-GRP-ACCES: the access list denies Maint only when it lists the button with ACCESS-ALLOW "N" */
    public boolean maintenanceAllowed(String userGroup) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("userGrp", userGroup == null ? "" : userGroup);
        crit.put("guiName", guiName);
        ProgramResult r = programs.run("VH01A02", Map.of("VH01A02_CRIT", crit));
        Object list = r.record("VH01A02_DATA") == null ? null : r.record("VH01A02_DATA").get("userGroupAccess");
        if (list instanceof List<?> rowsList) {
            for (Object o : rowsList) {
                if (o instanceof Map<?, ?> row && maintPart.equals(String.valueOf(row.get("partName") == null ? "" : row.get("partName")).strip())) {
                    return !"N".equals(String.valueOf(row.get("accessAllow") == null ? "" : row.get("accessAllow")).strip());
                }
            }
        }
        return true;
    }

    /**
     * the old map's clicks, replayed on its own groups: a car spot runs VHYT05IS-CLICK (from car, to position, to track; the
     * third click opens VHYT11I), a track button VHYT05IS-TRACK and the pseudo spot VHYT05IS-PSEUDO (each opens VHYT11I)
     */
    public Selection select(List<Click> clicks) {
        if (clicks == null || clicks.isEmpty()) throw new InvalidRequestException("Choose a car or a track on the map.");
        YardTrackingTrackScreenLogic s = new YardTrackingTrackScreenLogic(bundle, SCREEN, legacy, clock);
        String open = null;
        for (Click c : clicks) {
            if (c.pseudo()) {
                s.run("VHYT05IS-PSEUDO");
                open = "VHYT11I";
            } else if (c.track() != null && !c.track().isBlank()) {
                if (trackButtons.stream().noneMatch(b -> b.code().equals(c.track().strip()))) throw new InvalidRequestException("There is no track " + c.track() + " on the map.");
                s.store("VHYT05IW-TRACK", "TRACK-NO", c.track().strip());
                s.run("VHYT05IS-TRACK");
                open = "VHYT11I";
            } else {
                String code = c.spot() == null ? "" : c.spot().strip();
                if (!spotCodes.containsValue(code)) throw new InvalidRequestException("There is no spot " + code + " on the map.");
                s.store("VHYT05IW-PARM", "TRAK-POS", code);
                s.run("VHYT05IS-CLICK");
                if (s.opened.contains("VHYT11I")) open = "VHYT11I";
            }
            if (open != null) break;
        }
        if (open == null) return new Selection(null, Map.of());
        // VHYT11I's attributes are bound to VHYT05IW-PASSVALUE (old wiring); its FROM-YRD-SUB-LOC and YRD-SUB-LOC both to FROM-YRD-TRKG-YRD
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("yrdTrkgYrd", s.text("VHYT05IW-PASSVALUE", "YRD-TRKG-YRD"));
        p.put("fromYrdSubLoc", s.text("VHYT05IW-PASSVALUE", "FROM-YRD-TRKG-YRD"));
        p.put("positionNoFrom", s.number("VHYT05IW-PASSVALUE", "FROM-CAR-POSITION"));
        p.put("positionNoTo", s.number("VHYT05IW-PASSVALUE", "TO-CAR-POSITION"));
        p.put("toYrdSubLoc", s.text("VHYT05IW-PASSVALUE", "TO-YRD-TRKG-YRD"));
        p.put("yrdSubLoc", s.text("VHYT05IW-PASSVALUE", "FROM-YRD-TRKG-YRD"));
        return new Selection(open, p);
    }

    // ---- reading the old screen ----------------------------------------------------------------------------

    /** LOAD's {@code if (DATA.YRD-SUB-LOC[COUNTER] == "T-10") VHYT05IS-T10();}: track -> the group placing its cars */
    private static Map<String, String> placers(List<Ast.Stmt> body, Map<String, String> out) {
        for (Ast.Stmt s : body) {
            switch (s) {
                case Ast.If i -> {
                    if (i.cond() instanceof Ast.Binary b && b.op().equals("==") && b.right() instanceof Ast.Str v && !i.then().isEmpty()
                            && i.then().get(0) instanceof Ast.ExprStmt es && es.expr() instanceof Ast.Call c) out.put(v.value(), c.name());
                    placers(i.then(), out);
                    placers(i.otherwise(), out);
                }
                case Ast.While w -> placers(w.body(), out);
                default -> {}
            }
        }
        return out;
    }

    /** the spot click codes and track buttons: group writes REC.BUTTON-n, a widget shows "REC BUTTON-n data", its click sends a constant */
    private void wiring(JsonNode screen, Map<String, String> placers) {
        Map<String, String> shownBy = new HashMap<>(), clickCode = new HashMap<>(), trackCode = new HashMap<>();
        for (JsonNode c : screen.path("connections")) {
            JsonNode a = c.path("a");
            String m = c.path("m").asText(), from = a.path(0).path("id").asText();
            if (m.equals("connectReadOnlyAttributeToAttribute") && a.path(1).asText().endsWith(" data") && a.path(2).path("id").asText().startsWith("screen.")
                    && a.path(3).asText().equals("object")) {
                shownBy.put((from + "|" + a.path(1).asText().replace(" data", "")).toUpperCase(), a.path(2).path("id").asText().substring(7));
            } else if (m.equals("connectEventToActionWithParameters") && from.startsWith("screen.") && a.path(1).asText().equals("clicked")) {
                String target = a.path(2).path("id").asText() + "|" + a.path(3).asText(), value = a.path(4).path(0).path("args").path(1).asText();
                if (target.equals("VHYT05IW-PARM|TRAK-POS data")) clickCode.put(from.substring(7), value);
                if (target.equals("VHYT05IW-TRACK|TRACK-NO data")) trackCode.put(from.substring(7), value);
            }
        }
        placers.forEach((track, fn) -> {
            Ast.Function f = bundle.function(fn, PKG);
            if (f == null) return;
            spotsOf(f.body(), track, shownBy, clickCode);
        });
        JsonNode widgets = screen.path("widgets");
        trackCode.forEach((w, code) -> trackButtons.add(new TrackButton(widgets.path(w).path("props").path("object").asText(code), code)));
    }

    private void spotsOf(List<Ast.Stmt> body, String track, Map<String, String> shownBy, Map<String, String> clickCode) {
        for (Ast.Stmt s : body) {
            if (!(s instanceof Ast.If i)) continue;
            if (i.cond() instanceof Ast.Binary b && b.op().equals("==") && b.right() instanceof Ast.Num n && b.left() instanceof Ast.Name nm
                    && last(nm).equalsIgnoreCase("POSN-NO")) {
                for (Ast.Stmt t : i.then()) {
                    if (t instanceof Ast.Assign a && last(a.target()).toUpperCase().startsWith("BUTTON-")) {
                        List<Ast.Segment> seg = a.target().segments();
                        String key = (seg.get(0).name() + "|" + last(a.target())).toUpperCase();
                        String widget = shownBy.get(key), code = widget == null ? null : clickCode.get(widget);
                        if (code != null) spotCodes.put(track + "/" + n.value().intValue(), code);
                    }
                }
            }
            spotsOf(i.then(), track, shownBy, clickCode);
            spotsOf(i.otherwise(), track, shownBy, clickCode);
        }
    }

    /** the text of {@code strLib.indexOf("<O({vblm", ...)} in a track group */
    private static String indexOfText(List<Ast.Stmt> body) {
        for (Ast.Stmt s : body) {
            String t = switch (s) {
                case Ast.LocalDecl d -> d.init() == null ? null : indexOf(d.init());
                case Ast.Assign a -> indexOf(a.value());
                default -> null;
            };
            if (t != null) return t;
        }
        return null;
    }

    private static String indexOf(Ast.Expr e) {
        return switch (e) {
            case Ast.Call c when c.name().toLowerCase().endsWith("indexof") && !c.args().isEmpty() && c.args().get(0) instanceof Ast.Str s -> s.value();
            case Ast.Binary b -> { String l = indexOf(b.left()); yield l != null ? l : indexOf(b.right()); }
            default -> null;
        };
    }

    private static String firstText(ScreenBundle screens, String function) {
        Ast.Function f = screens.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        for (Ast.Stmt st : f.body()) if (st instanceof Ast.If i && literal(i.cond()) != null) return literal(i.cond());
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

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }
}
