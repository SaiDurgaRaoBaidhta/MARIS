package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moving cars on a track (old VHYT11I "Car Move", opened from the yard maps with a yard, a track and the positions clicked
 * there): the yard and track (VHYT08B), the car types' marks (VHYT08C), the cars (VHYT08A), the "Move to" tracks (VHYT11A),
 * the user group's button access (VH01A02); then position buttons clicked and a button: Move (VHYT11C checks the pseudo
 * trains, VHYT11B moves, writes), Pseudo MT (opens VHYT12I), Reverse (VHYT11F, writes), Fix Order (opens VHYT14I),
 * Orientation (opens VHYT13I), Mve Yrd (opens VHIC03I) and Print (opens VHYT02I). The screen's own statement groups do the
 * work (thousands of lines of per-position branches), run by {@link YardTrackingMoveScreenLogic} in the old window's order.
 */
public final class YardTrackingMoveService {
    static final String SCREEN = "VHYT11I", PKG = "MRSYardTracking";
    private static final int SLOTS = 30;
    private static final List<String> ACTIONS = List.of("move", "pseudo-train", "reverse", "fix-order", "orientation", "move-yard", "print");

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;
    /** position slot -> the statement group its button runs (VHYT11IS-POS-n; the old screen names slot 23 VHYT11IS-POS23) */
    private final Map<Integer, String> slotGroups = new HashMap<>();
    private final String selected;

    public YardTrackingMoveService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        JsonNode def = screens.screen(SCREEN);
        Pattern caption = Pattern.compile("^-(\\d+)-$");
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (!"connectEventToAction".equals(c.path("m").asText()) || a.size() < 4 || !"clicked".equals(a.get(1).asText())) continue;
            String from = a.get(0).path("id").asText(), to = a.get(2).path("id").asText();
            if (!from.startsWith("screen.") || !to.endsWith("__reference")) continue;
            String text = def.path("widgets").path(from.substring(7)).path("props").path("object").asText("").strip();
            Matcher m = caption.matcher(text);
            if (m.matches()) slotGroups.put(Integer.parseInt(m.group(1)), to.substring(0, to.length() - 11));
        }
        if (slotGroups.size() != SLOTS) throw new IllegalStateException(SCREEN + " has " + slotGroups.size() + " position buttons");
        selected = YardTrackingMoveEgl.compared(screens, "VHYT11IS-POS-1", PKG, "CAR-1-COLOR");      // if (CAR-1-COLOR == "YELLOW")
    }

    /** the colour the old screen paints a selected car */
    public String selectedColor() {
        return selected;
    }

    /** the old window as it opens on a yard and track, then paged forward to {@code page} */
    public YardTrackingMove.Track track(String yard, String subLocation, String toSubLocation, Integer page, String group) {
        return open(yard, subLocation, toSubLocation, page, group).track();
    }

    /** positions clicked on a page, then a button: what the old window does */
    public YardTrackingMove.Result act(YardTrackingMove.Action a, String group) {
        String action = a.action() == null ? "" : a.action().strip().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) throw new InvalidRequestException("action is one of " + String.join(", ", ACTIONS));
        Session s = open(a.yard(), a.subLocation(), a.toSubLocation(), a.page(), group);
        for (Integer slot : a.slots() == null ? List.<Integer>of() : a.slots()) {
            if (slot == null || slot < 1 || slot > SLOTS) throw new InvalidRequestException("a position button is 1 to " + SLOTS);
            if (!s.enabled(slot)) throw new InvalidRequestException("position " + slot + " cannot be selected on this track.");
            s.l.run(slotGroups.get(slot));
        }
        if (a.toSubLocation() != null && !a.toSubLocation().isBlank()) s.chooseTrack(a.toSubLocation().strip().toUpperCase(Locale.ROOT));
        s.l.opened.clear();
        String opens = null, question = null;
        switch (action) {
            case "move" -> {
                s.l.run("VHYT11IS-MOVE");
                if ("Y".equals(s.text("VHYT11IW-TRIGGER", "MESSAGE-SPLI"))) {            // the old "un-make the pseudo train(s)?" prompt
                    if (Boolean.TRUE.equals(a.confirm())) s.l.run("VHYT11IS-MOVE-X");
                    else if (a.confirm() == null) question = s.text("VHYT11IW-MSG", "TEST-MSG").strip();
                    else s.l.run("VHYT11IS-CLEAR");
                }
            }
            case "pseudo-train" -> s.l.run("VHYT11IS-PSEUDO-MT");
            case "reverse" -> s.l.run("VHYT11IS-REVERSE");
            case "fix-order" -> { s.l.run("VHYT11IS-FIX"); opens = "VHYT14I"; }           // the window opens after the group, unconditionally
            case "orientation" -> { s.l.run("VHYT11IS-ORIENT"); opens = "VHYT13I"; }
            case "move-yard" -> { s.l.run("VHYT11IS-MOVEX"); s.l.run("VHYTIS-ACCES-MOVE"); }
            default -> opens = "VHYT02I";
        }
        if (!s.l.opened.isEmpty()) opens = s.l.opened.get(s.l.opened.size() - 1);
        Map<String, Object> with = new LinkedHashMap<>();
        if (opens != null) {
            String yard = s.text("VHYT08IW-PASSVALUE", "YRD_TRKG_YRD"), track = s.text("VHYT08IW-PASSVALUE", "YRD-SUB-LOC");
            switch (opens) {
                case "VHYT12I" -> {                                                   // the values VHYT11I connects into VHYT12IW-PASSVALUE
                    with.put("yrdTrkgYrd", yard);
                    with.put("yrdSubLoc", track);
                    with.put("fromPos", s.l.number("VHYT11IW-PASSVALUE", "FROM-POSITION"));
                    with.put("toPos", s.l.number("VHYT11IW-PASSVALUE", "TO-POSITION"));
                }
                case "VHYT13I" -> {
                    with.put("yrdTrkgYrd", yard);
                    with.put("yrdSubLoc", track);
                    with.put("carNo", s.text("VHYT11IW-PASSVALUE", "FROM-CAR").strip());
                    with.put("posnNo", s.l.number("VHYT11IW-PASSVALUE", "FROM-POSITION"));
                }
                case "VHYT14I" -> {
                    with.put("yrdTrkgYrd", yard);
                    with.put("yrdSubLoc", track);
                    with.put("posnNo", s.l.number("VHYT11IW-PASSVALUE", "FROM-POSITION"));
                }
                case "VHIC03I" -> {
                    for (String f : List.of("FROM-YRD-TRKG-YRD", "TO-YRD-TRKG-YRD", "FROM-YRD-SUB-LOC", "TO-YRD-SUB-LOC", "FROM-CON-NO", "TO-CON-NO")) {
                        with.put(camel(f), s.text("VHIC03IW-PASSVALUE", f));
                    }
                    with.put("fromPosnNo", s.l.number("VHYT11IW-PASSVALUE", "FROM-POSITION"));
                    with.put("toPosnNo", s.l.number("VHYT11IW-PASSVALUE", "TO-POSITION"));
                }
                default -> {
                    with.put("yrdTrkgYrd", yard);
                    with.put("yrdSubLoc", track);
                }
            }
        }
        YardTrackingMove.Track t = s.track();
        return new YardTrackingMove.Result(question != null ? question : t.message(), question, question != null ? null : opens,
                question != null ? Map.of() : with, t);
    }

    private Session open(String yard, String subLocation, String toSubLocation, Integer page, String group) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT), t = subLocation == null ? "" : subLocation.strip().toUpperCase(Locale.ROOT);
        if (y.isEmpty() || t.isEmpty()) throw new InvalidRequestException("A yard and a track are required.");
        Session s = new Session(new YardTrackingMoveScreenLogic(screens, SCREEN, legacy, clock));
        s.l.store("VH01A01_DATA", "USER-GROUP", group == null ? "" : group);              // what the opening map connects in
        s.l.store("VH01A01_DATA", "TERM-ID", terminal);
        s.l.store("VHYT08IW-PASSVALUE", "YRD_TRKG_YRD", y);
        s.l.store("VHYT08IW-PASSVALUE", "YRD-SUB-LOC", t);
        s.l.store("VHYT11IW-PASSVALUE", "FROM-YRD-SUB-LOC", t);
        s.l.store("VHYT11IW-PASSVALUE", "TO-YRD-SUB-LOC", toSubLocation == null ? "" : toSubLocation.strip().toUpperCase(Locale.ROOT));
        s.l.run("VHYT11IS-CLEAR-X");                                                    // aboutToOpenWidget, and what it chains
        s.selectTrack();
        int want = page == null || page < 1 ? 1 : page;
        while (s.page < want && s.state("VHYT08IW-TRIGGER", "NXT")) {
            s.l.run("VHYT11IS-NXT");
            s.page++;
        }
        return s;
    }

    private static String camel(String f) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : f.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '-' || c == '_') up = true;
            else { b.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return b.toString();
    }

    /** one run of the old window */
    private final class Session {
        final YardTrackingMoveScreenLogic l;
        int page = 1;

        Session(YardTrackingMoveScreenLogic l) {
            this.l = l;
        }

        String text(String rec, String field, int... index) {
            return l.text(rec, field, index);
        }

        /** a button's state: the last ENABLE-x / DISABLE-x the groups raised (enabled when neither was raised and {@code dflt}) */
        boolean state(String rec, String what, boolean dflt) {
            String en = (rec + ".ENABLE-" + what).toUpperCase(Locale.ROOT), dis = (rec + ".DISABLE-" + what).toUpperCase(Locale.ROOT);
            for (int i = l.switches.size() - 1; i >= 0; i--) {
                if (l.switches.get(i).equals(en)) return true;
                if (l.switches.get(i).equals(dis)) return false;
            }
            return dflt;
        }

        boolean state(String rec, String what) {
            return state(rec, what, false);
        }

        boolean enabled(int slot) {
            String en = ("VHYT11IW-ABLE.POS-" + slot + "-ENABLE"), dis = ("VHYT11IW-ABLE.POS-" + slot + "-DISABLE");
            for (int i = l.switches.size() - 1; i >= 0; i--) {
                if (l.switches.get(i).equals(en)) return true;
                if (l.switches.get(i).equals(dis)) return false;
            }
            return true;
        }

        /** the "Move to" list selects row TRACK-COUNTER (the track passed in), whose row becomes VHYT11IW-TRACK.TRACK-ROW */
        void selectTrack() {
            int n = (int) l.number("VHYT11IW-TRACK", "TRACK-COUNTER");
            if (n < 1) return;
            l.store("VHYT11IW-TRACK", "YRD-SUB-LOC", text("VHYT11A_DATA", "YRD-SUB-LOC", n));
            l.store("VHYT11IW-TRACK", "YRD-SUB-LOC-DESC", text("VHYT11A_DATA", "YRD-SUB-LOC-DESC", n));
        }

        /** a track chosen in the "Move to" list */
        void chooseTrack(String code) {
            int rows = (int) l.number("VHYT11A_CRIT", "TRACK-COUNTER");
            for (int i = 1; i <= rows; i++) {
                if (text("VHYT11A_DATA", "YRD-SUB-LOC", i).strip().equalsIgnoreCase(code)) {
                    l.store("VHYT11IW-TRACK", "TRACK-COUNTER", i);
                    l.store("VHYT11IW-TRACK", "YRD-SUB-LOC", text("VHYT11A_DATA", "YRD-SUB-LOC", i));
                    l.store("VHYT11IW-TRACK", "YRD-SUB-LOC-DESC", text("VHYT11A_DATA", "YRD-SUB-LOC-DESC", i));
                    return;
                }
            }
            throw new InvalidRequestException(code + " is not in the Move to list of this track.");
        }

        YardTrackingMove.Track track() {
            List<YardTrackingMove.Position> out = new ArrayList<>();
            for (int n = 1; n <= SLOTS; n++) {
                out.add(new YardTrackingMove.Position(n, (int) l.number("VHYT08IW-3", "LABEL-" + n + "-CENTER"), text("VHYT08IW-1", "CAR-NO-" + n),
                        text("VHYT08IW-1", "CAR-NO-" + n + "-CENTER").strip(), text("VHYT08IW-1", "CON-" + n).strip(), text("VHYT08IW-1", "VEH-STAT-" + n),
                        text("VHYT08IW-1", "VEH-STAT-PREV-" + n), text("VHYT08IW-1", "HOME-YRD-" + n), text("VHYT11IW-COLOR", "CAR-" + n + "-COLOR").strip(),
                        text("VHYT11IW-STAT-COR", "CAR-" + n + "-STAT-COLOR").strip(), enabled(n)));
            }
            List<YardTrackingMove.Location> moveTo = new ArrayList<>();
            int rows = (int) l.number("VHYT11A_CRIT", "TRACK-COUNTER");
            for (int i = 1; i <= rows; i++) {
                moveTo.add(new YardTrackingMove.Location(text("VHYT11A_DATA", "YRD-SUB-LOC", i).strip(), text("VHYT11A_DATA", "YRD-SUB-LOC-DESC", i).strip()));
            }
            String message = text("VHYT11IW-MESSAGE", "MESSAGE").strip();
            String code = text("XXMESSAGE", "UMSGCODE");
            if (message.isBlank() && !code.isBlank()) {
                List<String> ins = new ArrayList<>();
                for (int i = 1; i <= 3; i++) ins.add(text("XXMESSAGE", "UMSGINS", i));
                message = messages.resolve(code, ins).text();
            }
            return new YardTrackingMove.Track(text("VHYT08IW-2", "YRD").strip(), text("VHYT08IW-2", "YRD-DESC").strip(), text("VHYT08IW-2", "SUB-LOC").strip(),
                    text("VHYT08IW-2", "YRD-SUB-LOC-DESC").strip(), text("VHYT11IW-TRACK", "YRD-SUB-LOC").strip(),
                    (text("VHYT11IW-PSEUDO-L", "PSEUDO-LABEL-1").strip() + " " + text("VHYT11IW-PSEUDO-L", "PSEUDO-LABEL-2").strip()).strip(), page,
                    state("VHYT08IW-TRIGGER", "PRV"), state("VHYT08IW-TRIGGER", "NXT"), out, moveTo, state("VHYT11IW-TRIGGER", "PSEUDO", true),
                    state("VHYTIW-BUTTOM", "MOVEYRD"), message);
        }
    }
}
