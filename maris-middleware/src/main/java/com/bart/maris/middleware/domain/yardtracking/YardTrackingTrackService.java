package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One track's cars (old VHYT08I "Yard Detail Inquiry"): the yard and track descriptions (VHYT08B), the move-to locations and
 * the tracks whose order can be fixed (VHYT08F, VHYT08H), the car types' orientation marks (VHYT08C), the cars on the
 * track (VHYT08A), the user group's yard (VHYT08D) and button access (VH01A02); and the buttons pressed after clicking
 * positions: Change Reason (opens VHYT06I), Fix Order (opens VHYT14I), Move Car (VHYT08G, writes) and Move Yrd (opens
 * VHIC03I). The screen's own statement groups do the work (they are thousands of lines of per-position branches), run by
 * {@link YardTrackingTrackScreenLogic} in the order the old window runs them.
 */
public final class YardTrackingTrackService {
    static final String SCREEN = "VHYT08I", PKG = "MRSYardTracking";
    private static final int SLOTS = 30;

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;
    private final String plainColor, plainStatusColor;
    private final Map<String, String> buttons = new LinkedHashMap<>();

    public YardTrackingTrackService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        plainColor = YardTrackingTrackEgl.first(screens, "VHYT08IS-CAR-COLOR", PKG, "CAR-1-COLOR");                // a car not marked
        plainStatusColor = YardTrackingTrackEgl.first(screens, "VHYT08I-LOAD-DATA", PKG, "CAR-1-STAT-COLOR");       // a GO / USABLE status
        buttons.put("change-reason", YardTrackingTrackEgl.caption(screens, SCREEN, "Push_Button1"));
        buttons.put("fix-order", YardTrackingTrackEgl.caption(screens, SCREEN, "Push_Button412"));
        buttons.put("move-car", YardTrackingTrackEgl.caption(screens, SCREEN, "Push_Button42"));
        buttons.put("move-yard", YardTrackingTrackEgl.caption(screens, SCREEN, "Push_Button41"));
    }

    /** the old window as it opens for a yard and track (VHYT08IW-PASSVALUE), then paged forward to {@code page} */
    public YardTrackingTrack.Track track(String yard, String subLocation, Integer page, String group) {
        Session s = open(yard, subLocation, page, group);
        return s.track();
    }

    /** positions clicked on a page, then a button: what the old window does */
    public YardTrackingTrack.Result act(YardTrackingTrack.Action a, String group) {
        String action = a.action() == null ? "" : a.action().strip().toLowerCase(Locale.ROOT);
        if (!buttons.containsKey(action)) throw new InvalidRequestException("action is one of " + String.join(", ", buttons.keySet()));
        Session s = open(a.yard(), a.subLocation(), a.page(), group);
        for (Integer slot : a.slots() == null ? List.<Integer>of() : a.slots()) {
            if (slot == null || slot < 1 || slot > SLOTS) throw new InvalidRequestException("a position button is 1 to " + SLOTS);
            s.l.store("VHYT08IW-TRIGGER", "ENABLE-FIX", "");
            s.l.store("VHYT08IW-TRIGGER", "DISABLE-FIX", "");
            s.step("VHYT08IW-TRIGGER", "CHANGE", () -> s.l.run("VHYT08IS-POS-" + slot));
            s.fixEnabled = s.changed("VHYT08IW-TRIGGER", "FIX", s.fixEnabled);
        }
        boolean enabled = switch (action) {
            case "change-reason" -> s.changeEnabled;
            case "fix-order" -> s.fixEnabled;
            case "move-car" -> s.moveCarEnabled;
            default -> s.moveYardEnabled;
        };
        if (!enabled) throw new InvalidRequestException(buttons.get(action) + " is not available for this selection.");
        s.l.opened.clear();
        s.l.store("VHYT08IW-MESSAGE", "MESSAGE", "");
        switch (action) {
            case "change-reason" -> s.l.run("VHYT08IS-CHANGE");
            case "fix-order" -> { s.l.run("VHYT08IS-MOVE"); s.l.run("VHYTIS-ACCES-FIX"); }
            case "move-car" -> {
                s.l.store("VHYT08IW-PASSVALUE", "YRD-SUB-LOC-TO", a.toLocation() == null ? "" : a.toLocation().strip());
                s.l.run("VHYT08IS-MOVE");
                s.l.run("VHYTIS-ACCES-CAR");
            }
            default -> { s.l.run("VHYT08IS-MOVE"); s.l.run("VHYTIS-ACCES-MOVE"); }
        }
        String opens = s.l.opened.isEmpty() ? null : s.l.opened.get(s.l.opened.size() - 1);
        Map<String, Object> with = new LinkedHashMap<>();
        if ("VHYT06I".equals(opens)) {                                     // the values VHYT08I connects into VHYT06IW-PASSVALUE
            with.put("yrdTrkgYrd", s.text("VHYT08IW-PASSVALUE", "YRD_TRKG_YRD"));
            with.put("yrdSubLoc", s.text("VHYT08IW-PASSVALUE", "YRD-SUB-LOC"));
            with.put("carNo", s.text("VHYT08IW-PASSVALUE", "CAR-NO"));
            with.put("posnNo", s.l.number("VHYT08IW-PASSVALUE", "POSN-NO-X"));
        } else if ("VHYT14I".equals(opens)) {                              // VHYT14IW-PASSVALUE
            with.put("yrdTrkgYrd", s.text("VHYT08IW-PASSVALUE", "YRD_TRKG_YRD"));
            with.put("yrdSubLoc", s.text("VHYT08IW-PASSVALUE", "YRD-SUB-LOC"));
            with.put("posnNo", s.l.number("VHYT08IW-PASSVALUE", "FROM-POSITION"));
        } else if ("VHIC03I".equals(opens)) {                              // VHIC03IW-PASSVALUE
            for (String f : List.of("FROM-YRD-TRKG-YRD", "TO-YRD-TRKG-YRD", "FROM-YRD-SUB-LOC", "TO-YRD-SUB-LOC", "FROM-CON-NO", "TO-CON-NO")) {
                with.put(camel(f), s.text("VHIC03IW-PASSVALUE", f));
            }
            with.put("fromPosnNo", s.l.number("VHIC03IW-PASSVALUE", "FROM-POSN-NO"));
            with.put("toPosnNo", s.l.number("VHIC03IW-PASSVALUE", "TO-POSN-NO"));
        }
        return new YardTrackingTrack.Result(s.text("VHYT08IW-MESSAGE", "MESSAGE"), opens, with, s.track());
    }

    private Session open(String yard, String subLocation, Integer page, String group) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT), t = subLocation == null ? "" : subLocation.strip().toUpperCase(Locale.ROOT);
        if (y.isEmpty() || t.isEmpty()) throw new InvalidRequestException("A yard and a track are required.");
        Session s = new Session(new YardTrackingTrackScreenLogic(screens, SCREEN, legacy, clock));
        s.l.store("VH01A01_DATA", "USER-GROUP", group == null ? "" : group);           // what the opening window connects in
        s.l.store("VH01A01_DATA", "TERM-ID", terminal);
        s.l.store("VHYT08IW-PASSVALUE", "YRD_TRKG_YRD", y);
        s.l.store("VHYT08IW-PASSVALUE", "YRD-SUB-LOC", t);
        s.step("VHYT08IW-TRIGGER", "CHANGE", () -> s.l.run("VHYT08IS-CK-USER"));      // aboutToOpenWidget, and what it chains
        s.pages();
        s.fixEnabled = s.changed("VHYT08IW-TRIGGER", "FIX", s.changed("VHYTIW-BUTTOM", "FIX", false));
        s.moveCarEnabled = s.changed("VHYTIW-BUTTOM", "MOVECAR", false);
        s.moveYardEnabled = s.changed("VHYTIW-BUTTOM", "MOVEYRD", false);
        int want = page == null || page < 1 ? 1 : page;
        while (s.page < want && s.next) {
            s.clearPaging();
            s.l.run("VHYT08IS-NXT");
            s.pages();
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

    /** one run of the old window, with the enable / disable events it raised so far */
    private final class Session {
        final YardTrackingTrackScreenLogic l;
        int page = 1;
        boolean previous, next, changeEnabled, fixEnabled, moveCarEnabled, moveYardEnabled;

        Session(YardTrackingTrackScreenLogic l) {
            this.l = l;
        }

        String text(String rec, String field, int... index) {
            return l.text(rec, field, index);
        }

        /** runs a step with a record's ENABLE-X / DISABLE-X events cleared, then keeps which one it raised (ENABLE wins) */
        void step(String rec, String what, Runnable r) {
            l.store(rec, "ENABLE-" + what, "");
            l.store(rec, "DISABLE-" + what, "");
            r.run();
            changeEnabled = changed(rec, what, changeEnabled);
        }

        /** the button state after the last step: enabled if it raised ENABLE-X, disabled if DISABLE-X, else unchanged */
        boolean changed(String rec, String what, boolean before) {
            if ("Y".equals(text(rec, "ENABLE-" + what))) return true;
            if ("Y".equals(text(rec, "DISABLE-" + what))) return false;
            return before;
        }

        void clearPaging() {
            for (String f : List.of("ENABLE-PRV", "DISABLE-PRV", "ENABLE-NXT", "DISABLE-NXT")) l.store("VHYT08IW-TRIGGER", f, "");
        }

        void pages() {
            previous = changed("VHYT08IW-TRIGGER", "PRV", previous);
            next = changed("VHYT08IW-TRIGGER", "NXT", next);
        }

        YardTrackingTrack.Track track() {
            List<YardTrackingTrack.Position> out = new ArrayList<>();
            for (int n = 1; n <= SLOTS; n++) {
                String color = text("VHYT08IW-COLOR", "CAR-" + n + "-COLOR"), statColor = text("VHYT11IW-STAT-COR", "CAR-" + n + "-STAT-COLOR");
                out.add(new YardTrackingTrack.Position(n, (int) l.number("VHYT08IW-3", "LABEL-" + n + "-CENTER"), text("VHYT08IW-1", "CAR-NO-" + n),
                        text("VHYT08IW-1", "CAR-NO-" + n + "-CENTER").strip(), text("VHYT08IW-1", "CON-" + n).strip(), text("VHYT08IW-1", "VEH-STAT-" + n),
                        text("VHYT08IW-1", "VEH-STAT-PREV-" + n), text("VHYT08IW-1", "HOME-YRD-" + n),
                        !color.isBlank() && !color.equalsIgnoreCase(plainColor), !statColor.isBlank() && !statColor.equalsIgnoreCase(plainStatusColor)));
            }
            List<YardTrackingTrack.Location> locations = new ArrayList<>();
            long shops = l.number("VHYT08IW-CNT", "SHOP-ROW-CNT");
            for (int i = 1; i <= shops; i++) {
                locations.add(new YardTrackingTrack.Location(text("VHYT08F_DATA", "YRD-SUB-LOC", i).strip(), text("VHYT08F_DATA", "YRD-SUB-LOC-DESC", i).strip()));
            }
            String message = text("VHYT08IW-MESSAGE", "MESSAGE");
            String code = "";
            try { code = text("XXMESSAGE", "UMSGCODE"); } catch (IllegalArgumentException none) { /* the screen has no message record */ }
            if (message.isBlank() && !code.isBlank()) {
                List<String> ins = new ArrayList<>();
                for (int i = 1; i <= 3; i++) ins.add(text("XXMESSAGE", "UMSGINS", i));
                message = messages.resolve(code, ins).text();
            }
            return new YardTrackingTrack.Track(text("VHYT08IW-2", "YRD").strip(), text("VHYT08IW-2", "YRD-DESC").strip(), text("VHYT08IW-2", "SUB-LOC").strip(),
                    text("VHYT08IW-2", "YRD-SUB-LOC-DESC").strip(), (text("VHYT08IW-PSEUDO-X", "PSEUDO-LABEL-1").strip() + " "
                    + text("VHYT08IW-PSEUDO-X", "PSEUDO-LABEL-2").strip()).strip(), page, previous, next, out, locations,
                    "Y".equals(text("VHYT08IW-PASSVALUE", "USER-OK")), moveCarEnabled, moveYardEnabled, fixEnabled, message);
        }
    }
}
