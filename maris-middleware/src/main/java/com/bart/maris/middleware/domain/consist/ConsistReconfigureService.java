package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconfigure a train (old VHTR01I, with its confirmation window VHTR02I): Build-Up joins an in-service train and a pseudo
 * train, Service Reduction splits cars off an in-service train (the split buttons between its cars). The train check
 * (VHTR011), the pseudo train (VHTR014), the split checks (VHSC2A1, VHTS2A3, VHTR015) and the reconfiguration itself
 * (VHTR01IS-RECONFIG, then VHTR01IS-CONT-REC once VHTR02I answers Yes; it writes through VHTR016, VHLT017 and VHLT018).
 *
 * <p>The rules are the old screen's own statement groups, run on its records by {@link ConsistScreenLogic}. Each call
 * replays the window from its opening with the user's steps (the old window kept them), so the page sends them all.
 */
public final class ConsistReconfigureService {
    static final String SCREEN = "VHTR01I";
    static final String CONFIRM = "VHTR02I";

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;
    /** VHTR02I's question and its Yes answer, read from that window */
    private final String question, yes;
    /** the split buttons: the car position each one sends (pbtSplitButtonN clicked -> CAR-POS), and the one running MOVE-LR */
    private final List<Integer> splits = new ArrayList<>();
    private final int moveLr;

    public ConsistReconfigureService(ScreenBundle bundle, LegacyProgramService legacy, MessageResolver messages, Clock clock, String terminal) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        JsonNode confirm = bundle.screen(CONFIRM);
        if (confirm == null) throw new IllegalStateException(CONFIRM + " not found");
        this.question = (label(confirm, "Label1") + " " + label(confirm, "Label2")).strip();
        String y = null;
        for (JsonNode c : confirm.path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(0).path("id").asText().equals("screen.Push_Button1") && a.path(3).asText().equals("YESNO data")) y = a.path(4).path(0).path("args").path(1).asText();
        }
        if (y == null) throw new IllegalStateException(CONFIRM + " has no Yes answer");
        this.yes = y;
        int lr = -1;
        for (JsonNode c : bundle.screen(SCREEN).path("connections")) {
            JsonNode a = c.path("a");
            String w = a.path(0).path("id").asText();
            if (!w.startsWith("screen.pbtSplitButton") || !a.path(1).asText().equals("clicked")) continue;
            if (a.path(3).asText().equals("CAR-POS data")) splits.add(a.path(4).path(0).path("args").path(1).asInt());
        }
        for (JsonNode c : bundle.screen(SCREEN).path("connections")) {
            JsonNode a = c.path("a");
            String w = a.path(0).path("id").asText();
            if (w.startsWith("screen.pbtSplitButton") && a.path(2).path("id").asText().equals("VHTR01IS-MOVE-LR__reference")) {
                for (JsonNode d : bundle.screen(SCREEN).path("connections")) {
                    JsonNode b = d.path("a");
                    if (b.path(0).path("id").asText().equals(w) && b.path(3).asText().equals("CAR-POS data")) lr = b.path(4).path(0).path("args").path(1).asInt();
                }
            }
        }
        if (splits.isEmpty() || lr < 0) throw new IllegalStateException(SCREEN + " has no split buttons");
        this.moveLr = lr;
    }

    private static String label(JsonNode screen, String widget) {
        return screen.path("widgets").path(widget).path("props").path("object").asText("");
    }

    /**
     * the user's steps on the window, in the order the old screen takes them
     *
     * @param action       1 = Build-Up, 2 = Service Reduction (the radio buttons; the window opens on 2)
     * @param consist      the in-service train (Text1)
     * @param leftConsist  Build-Up: the left consist (txtLeftCon)
     * @param rightConsist Build-Up: the right consist (txtRightCon)
     * @param pseudo       Build-Up: the new pseudo train (txtLeftCon6; null = as the screen fills it)
     * @param splits       Service Reduction: the split buttons pressed, by the car position they send
     */
    public record Steps(int action, String consist, String leftConsist, String rightConsist, String pseudo, List<Integer> splits) {}

    public record Consist(String consist, List<String> cars) {}

    public record State(List<String> cars, Consist left, Consist right, String newPseudo, boolean reconfigure, boolean split,
                        boolean pseudoEnabled, String message) {}

    public record Options(List<String> yards, String time, List<Integer> splitPositions) {}

    /** the answer: done (the old message), or VHTR02I's question when the user has not confirmed yet */
    public record Result(String message, String question) {}

    /** VHTR01IS-INIT: the yards and the current time; the split buttons' car positions */
    public Options options(String userGroup) {
        ConsistScreenLogic s = open(userGroup);
        return new Options(ConsistLayUpService.column(s, "VHLT010_DATA", "YRD"), s.text("VHTR01IW", "TIME-OUTSRV"), List.copyOf(splits));
    }

    /** VHTR01IS-LOC: a yard's transfer / storage tracks */
    public List<String> tracks(String userGroup, String yard) {
        ConsistScreenLogic s = open(userGroup);
        yard(s, yard);
        return ConsistLayUpService.column(s, "VHLT011_DATA", "YRD-SUB-LOC");
    }

    /** the window after the user's steps: the trains, the message and which buttons are enabled */
    public State state(String userGroup, Steps steps) {
        ConsistScreenLogic s = open(userGroup);
        steps(s, steps);
        return state(s);
    }

    /** Reconfigure: VHTR01IS-RECONFIG; when it asks (VHTR02I) and confirmed, VHTR01IS-CONT-REC with VHTR02I's Yes */
    public Result reconfigure(String userGroup, Steps steps, String yard, String track, String time, boolean confirmed) {
        ConsistScreenLogic s = open(userGroup);
        steps(s, steps);
        if (!state(s).reconfigure()) throw new InvalidRequestException(state(s).message());   // the old Reconfigure button is disabled
        yard(s, yard);
        s.store("VHTR01IW", "YARD-SUB-LOC", track == null ? "" : track.strip());
        if (time != null) s.store("VHTR01IW", "TIME-OUTSRV", time.strip());
        s.store("WS15IW-TIME", "TIME-IN", time != null ? time.strip() : s.text("VHTR01IW", "TIME-OUTSRV"));
        s.run("VHTR01IS-RECONFIG");
        ConsistLayUpService.fail(s, messages);
        if (!s.text("VHTR01IW-TRIGGER", "OPEN-POP-UP").equals("Y")) return new Result(message(s), null);
        if (!confirmed) return new Result(message(s), question);
        s.store("VHTR02IW", "YESNO", yes);
        s.run("VHTR01IS-CONT-REC");
        ConsistLayUpService.fail(s, messages);
        return new Result(message(s), null);
    }

    private ConsistScreenLogic open(String userGroup) {
        ConsistScreenLogic s = new ConsistScreenLogic(bundle, SCREEN, legacy, clock);
        s.store("VH01A01_DATA", "USER-GROUP", userGroup == null ? "" : userGroup);
        s.store("VH01A01_DATA", "TERM-ID", terminal);
        s.run("VHTR01IS-INIT");
        ConsistLayUpService.fail(s, messages);
        // aboutToOpenWidget presses Clear (Push_Button2): Service Reduction, no yard or track, VHTR01IS-CLEAR
        action(s, 2);
        s.run("VHTR01IS-CLEAR");
        return s;
    }

    /** Drop-down_List1's selectedItem is bound to YARD-LOC and YRD-TRKG-YRD; choosing it presses Push_Button4 (VHTR01IS-LOC) */
    private void yard(ConsistScreenLogic s, String yard) {
        String y = yard == null ? "" : yard.strip();
        s.store("VHTR01IW", "YARD-LOC", y);
        s.store("VHTR01IW", "YRD-TRKG-YRD", y);
        s.run("VHTR01IS-LOC");
        ConsistLayUpService.fail(s, messages);
    }

    private static void action(ConsistScreenLogic s, int action) {
        s.store("VHTR01IW", "SERVICE-ACTION-IDX", action);
        s.run("VHTR01IS-SRV-ACT");
    }

    private void steps(ConsistScreenLogic s, Steps st) {
        if (st.action() != 2) action(s, st.action());
        String c = st.consist() == null ? "" : st.consist().strip();
        s.store("VHTR01IW", "CON-NO", c);
        s.store("VHTR01IW", "CON-NO-X", c);
        s.run("VHTR01IS-VALID-CON");                                       // Text1 losing focus
        ConsistLayUpService.fail(s, messages);
        if (st.action() == 1) {
            String l = st.leftConsist() == null ? "" : st.leftConsist().strip(), r = st.rightConsist() == null ? "" : st.rightConsist().strip();
            s.store("VHTR01IW", "LEFT-CON-NO", l);
            s.store("VHTR01IW", "LEFT-CON-NO-X", l);
            s.store("VHTR01IW", "RIGHT-CON-NO", r);
            s.store("VHTR01IW", "RIGHT-CON-NO-X", r);
            s.run("VHTR01IS-RIGHT-CON");                                   // txtRightCon losing focus
            String p = st.pseudo() == null ? s.text("VHTR01IW", "NEW-PSEUDO-CON-NO") : st.pseudo().strip();
            s.store("VHTR01IW", "NEW-PSEUDO-CON-NO", p);
            s.store("VHTR01IW", "NEW-PSEUDO-CON-NO-X", p);
            s.run("VHTR01IS-VALD-PSEU");                                   // txtLeftCon6 losing focus
            ConsistLayUpService.fail(s, messages);
        }
        for (int pos : st.splits() == null ? List.<Integer>of() : st.splits()) {
            if (!splits.contains(pos)) throw new InvalidRequestException("There is no split button at car position " + pos + ".");
            s.store("VHTR01W-MSG-SC", "CAR-POS", pos);
            s.run(pos == moveLr ? "VHTR01IS-MOVE-LR" : "VHTR01IS-MV-RIGHT");
            ConsistLayUpService.fail(s, messages);
        }
    }

    private static State state(ConsistScreenLogic s) {
        List<String> cars = cars(s, "VHTR01IW");
        return new State(cars, new Consist(s.text("VHTR01IW-LEFT-CON", "LEFT-CON-NO"), cars(s, "VHTR01IW-LEFT-CON")),
                new Consist(s.text("VHTR01IW-RIGHT-CON", "RIGHT-CON-NO"), cars(s, "VHTR01IW-RIGHT-CON")), s.text("VHTR01IW", "NEW-PSEUDO-CON-NO"),
                ConsistLayUpService.enabled(s, "VHTR01IW-TRIGGER", "ENABLE-RECONFIG", "DISABLE-RECONFIG", false),
                ConsistLayUpService.enabled(s, "VHTR01IW-TRIGGER", "ENABLE-SPLIT", "DISABLE-SPLIT", true),
                ConsistLayUpService.enabled(s, "VHTR01IW-TRIGGER", "ENABLE-PSEUDO", "DISABLE-PSEUDO", true), message(s));
    }

    /** CAR-NO-1 .. CAR-NO-10 of a record (the old window's ten car boxes) */
    private static List<String> cars(ConsistScreenLogic s, String record) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 10; i++) out.add(s.text(record, "CAR-NO-" + i).strip());     // the boxes show the text without its leading blank
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /** Text4 shows CON-MSG-1, MSG-1 and the split message, whichever the old screen wrote last */
    private static String message(ConsistScreenLogic s) {
        return ConsistLayUpService.shown(s, "VHTR01IW-TIME-MSG|MSG-1", "VHTR01IW-CON-MSG|CON-MSG-1", "VHTR01W-MSG-SC|MSG");
    }
}
