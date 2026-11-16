package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shop car move (old VHYT03I, opened by the shop administration maps VHYT10I / VHYT95I after their second click) and
 * its reason check window (old VHYT42I). The car's details and current reason (VHYT03D, VHYT03E), the reason codes
 * (VHYT03A) and the shop's locations (VHYT03B), the "new location occupied" check (VHYT03J), and Update, which writes
 * a reason change only (VHYT03G) or a move (VHYT03H). The rules are the old screen's own statement groups, run on its
 * records: VHYT03IS-INIT (and INIT-S), the drop-downs' selection (VGTT03IS-PRE-VAL), the car box (VHYT03IS-CHECK-CAR),
 * Update (VHYT03IS-PRE-UPD + VH03IS-UPDATE) and the reason check's answer (VHYT42IS, VHYT03IS-VHYT42).
 */
public final class YardTrackingShopMoveService {
    static final String SCREEN = "VHYT03I";
    static final String CHECK_SCREEN = "VHYT42I";

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;
    private final Question question;

    public YardTrackingShopMoveService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.bundle = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        JsonNode w = screens.screen(CHECK_SCREEN).path("widgets");
        this.question = new Question(w.path("Window").path("props").path("title").asText(""),
                w.path("PlsVerify_lbl").path("props").path("object").asText(""),
                w.path("RsnNotChged_btn").path("props").path("object").asText(""),
                w.path("RsnIsChged_btn").path("props").path("object").asText(""), null);
        if (question.text().isBlank()) throw new IllegalStateException(CHECK_SCREEN + "'s question not found");
    }

    /** what the opening map passes (VHYT10IW-PASSVALUE and VHYT03IW-PASSVALUE's positions) */
    public record Context(String carNo, String yrdTrkgYrd, String yrdSubLoc, String newYrdLoc, String carNo2, Integer posnNoFrom,
                          Integer posnNoTo, String whichGui) {}

    public record Choice(String code, String text) {}

    /** the window's content after it opens (or after a car is typed) */
    public record View(String carNo, String orientation, String status, String location, String yard, String currentReason,
                       List<Choice> reasons, String selectedReason, List<Choice> locations, String selectedLocation,
                       boolean compressAsked, List<String> insertLabel, String message) {}

    /** VHYT42I: the old window's title, question and its two buttons; reason = the current reason it shows */
    public record Question(String title, String text, String keepButton, String changeButton, String reason) {}

    /** Update's outcome: the old message, or the reason check's question when the reason did not change */
    public record Outcome(String message, Question question) {}

    /** VHYT03IS-INIT (+ INIT-S) for the car the map passes; carNo (optional) is a car typed in the car box afterwards */
    public View open(Context c, String userGroup, String carNo) {
        YardTrackingTrackScreenLogic s = opened(c, userGroup);
        typeCar(s, carNo);
        return view(s);
    }

    /**
     * Update: the reason chosen (its code), the new location (its code; blank keeps the car where it is), the compress
     * direction (NORTH / SOUTH, asked when the location inserts at the end of a track) and the answer to the reason
     * check (true = "Reason Is Not Changing").
     */
    public Outcome update(Context c, String userGroup, String carNo, String reason, String location, String compress, boolean reasonUnchanged) {
        YardTrackingTrackScreenLogic s = opened(c, userGroup);
        typeCar(s, carNo);
        if (reason != null && !reason.isBlank()) {
            int k = index(choices(s, "VHYT03A_DATA", "VHYT03A_CRIT", "MOVE-REASON-COUNT", 4), reason);
            if (k == 0) throw new InvalidRequestException("Unknown reason code " + reason.strip() + ".");
            if (k != s.number("VHYT03IW-CAR-DA", "MOVE-COUNTER")) s.store("VHYT03IW-CAR-DAX", "MOVE-REASON-X", s.text("VHYT03A_DATA", "DATA_ROW", k));     // NRsnCD_Drpwn.selectedItem
        }
        if (location != null && !location.isBlank()) {
            int k = index(choices(s, "VHYT03B_DATA", "VHYT03B_CRIT", "LOC-COUNTER", 10), location);
            if (k == 0) throw new InvalidRequestException("Unknown shop location " + location.strip() + ".");
            if (k != s.number("VHYT03IW-CAR-DA", "COUNTER")) selectLocation(s, k);
        }
        if (compress != null && !compress.isBlank()) {
            String d = compress.strip().toUpperCase(Locale.ROOT);
            s.store("VHYT03IW-CAR-SHIFT", "CAR-SHIFT-SW", d.startsWith("N") ? 1 : d.startsWith("S") ? 2 : 0);   // Radio_Button_Set1.selectionIndex
        }
        s.opened.clear();
        s.run("VHYT03IS-PRE-UPD");                                                          // its hasExecuted runs VH03IS-UPDATE
        if (s.opened.contains(CHECK_SCREEN)) {
            if (!reasonUnchanged) {
                return new Outcome(message(s), new Question(question.title(), question.text(), question.keepButton(), question.changeButton(),
                        s.text("VHYT03D_DATA", "MOVE-REASON-CD-X")));
            }
            s.run("VHYT42IS");                                                              // "Reason Is Not Changing": CONTINUE-UPDATE = "Y"
            s.run("VHYT03IS-VHYT42");                                                       // the window closed: TRIGGER.UPDATE clicks Update again
            if (s.text("VHYT03IW-TRIGGER", "VAGen_UPDATE").equals("Y")) s.run("VHYT03IS-PRE-UPD");
        }
        return new Outcome(message(s), null);
    }

    // ---- the old window, replayed ------------------------------------------------------------------------

    private YardTrackingTrackScreenLogic opened(Context c, String userGroup) {
        if (c == null) throw new InvalidRequestException("The shop map's move values are missing.");
        YardTrackingTrackScreenLogic s = new YardTrackingTrackScreenLogic(bundle, SCREEN, legacy, clock);
        s.store("VH01A01_DATA", "USER-GROUP", userGroup == null ? "" : userGroup);
        s.store("VH01A01_DATA", "TERM-ID", terminal);
        s.store("VHYT10IW-PASSVALUE", "CAR-NO", nz(c.carNo()));
        s.store("VHYT10IW-PASSVALUE", "YRD-TRKG-YRD", nz(c.yrdTrkgYrd()));
        s.store("VHYT10IW-PASSVALUE", "YRD-SUB-LOC", nz(c.yrdSubLoc()));
        s.store("VHYT10IW-PASSVALUE", "NEW-YRD-LOC", nz(c.newYrdLoc()));
        s.store("VHYT10IW-PASSVALUE", "CAR-NO-2", nz(c.carNo2()));
        s.store("VHYT10IW-PASSVALUE", "WHICH-GUI", nz(c.whichGui()));
        s.store("VHYT10IW-PASSVALUE", "POSN-NO-FROM", c.posnNoFrom() == null ? 0 : c.posnNoFrom());
        s.store("VHYT10IW-PASSVALUE", "POSN-NO-TO", c.posnNoTo() == null ? 0 : c.posnNoTo());
        // the maps bind VHYT03I's POSN-NO-FROM / -TO attributes to their own pass values
        s.store("VHYT03IW-PASSVALUE", "POSN-NO-FROM", c.posnNoFrom() == null ? 0 : c.posnNoFrom());
        s.store("VHYT03IW-PASSVALUE", "POSN-NO-TO", c.posnNoTo() == null ? 0 : c.posnNoTo());
        s.run("VHYT03IS-INIT");                                                             // its hasExecuted runs VHYT03IS-INIT-S
        // the drop-downs show the rows MOVE-COUNTER / COUNTER point at, but that selection writes nothing to CAR-DAX: only
        // a user's change of row does (choosing the row already shown changes nothing, as in the old window)
        return s;
    }

    /** Drop-down_List2: its selectedItem goes to NEW-YARD-LOC-X, and selectionIsValid runs VGTT03IS-PRE-VAL */
    private static void selectLocation(YardTrackingTrackScreenLogic s, int k) {
        s.store("VHYT03IW-CAR-DAX", "NEW-YARD-LOC-X", s.text("VHYT03B_DATA", "DATA_ROW", k));
        s.run("VGTT03IS-PRE-VAL");
    }

    /** the car box: Text1 writes CAR-DAX.CAR-NO and losingFocus runs VHYT03IS-CHECK-CAR */
    private static void typeCar(YardTrackingTrackScreenLogic s, String carNo) {
        if (carNo == null || carNo.isBlank() || carNo.strip().equals(s.text("VHYT03IW-CAR-DAX", "CAR-NO"))) return;
        s.store("VHYT03IW-CAR-DAX", "CAR-NO", carNo.strip());
        s.run("VHYT03IS-CHECK-CAR");
    }

    private View view(YardTrackingTrackScreenLogic s) {
        List<Choice> reasons = choices(s, "VHYT03A_DATA", "VHYT03A_CRIT", "MOVE-REASON-COUNT", 4);
        List<Choice> locations = choices(s, "VHYT03B_DATA", "VHYT03B_CRIT", "LOC-COUNTER", 10);
        int r = (int) s.number("VHYT03IW-CAR-DA", "MOVE-COUNTER"), l = (int) s.number("VHYT03IW-CAR-DA", "COUNTER");
        List<String> label = new ArrayList<>();
        String l1 = s.text("VHYT03IW-LABEL", "LABEL-1"), l2 = s.text("VHYT03IW-LABEL", "LABEL-2");
        if (!l1.isBlank() || !l2.isBlank()) { label.add(l1); label.add(l2); }
        return new View(s.text("VHYT03IW-CAR-DA", "CAR-NO"), s.text("VHYT03IW-CAR-DA", "VAGen_ORIENTATION"), s.text("VHYT03IW-CAR-DA", "VEH-STAT"),
                s.text("VHYT03IW-CAR-DA", "YRD-SUB-LOC-X"), s.text("VHYT03IW-CAR-DA", "YRD-X"), s.text("VHYT03D_DATA", "MOVE-REASON-CD-X"),
                reasons, r > 0 && r <= reasons.size() ? reasons.get(r - 1).code() : null,
                locations, l > 0 && l <= locations.size() ? locations.get(l - 1).code() : null,
                s.text("VHYT03D_DATA", "BI-DIR-INSERT-IND").equals("Y"), label, message(s));
    }

    private static String message(YardTrackingTrackScreenLogic s) {
        return s.text("VHYT03IW-MESSAGE", "ERROR-MESSAGE").strip();
    }

    /** a drop-down's rows (DATA_ROW text, the code being its first codeLength characters), up to the count INIT kept */
    private static List<Choice> choices(YardTrackingTrackScreenLogic s, String data, String crit, String counter, int codeLength) {
        int n = (int) s.number(crit, counter);
        List<Choice> out = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            String row = s.text(data, "DATA_ROW", k);
            out.add(new Choice(row.substring(0, Math.min(codeLength, row.length())).strip(), row.strip()));
        }
        return out;
    }

    private static int index(List<Choice> list, String code) {
        for (int k = 0; k < list.size(); k++) if (list.get(k).code().equalsIgnoreCase(code.strip())) return k + 1;
        return 0;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
