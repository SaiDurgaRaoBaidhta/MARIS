package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lay up a train (old VHLT01I): the yards the user group may use (VHLT010) and the default one (VHLT020), a yard's
 * storage tracks (VHLT011), the train check when the consist is typed (VHLT009, VHLT021, VHLT022), and the lay-up itself
 * (VHLT01IS-PROCESS and the groups its triggers run, which write through VHLT012, VHLT017 and VHLT018).
 *
 * <p>The rules are the old screen's own statement groups, run on its records by {@link ConsistScreenLogic}; the messages are
 * the ones those groups put in VHLT01IW-TIME-MSG / VHLT01IW-CON-MSG. Each call replays the old screen from its opening, as
 * the old window kept everything between the train check and the lay-up.
 */
public final class ConsistLayUpService {
    static final String SCREEN = "VHLT01I";

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;

    public ConsistLayUpService(ScreenBundle bundle, LegacyProgramService legacy, MessageResolver messages, Clock clock, String terminal) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
    }

    public record Options(List<String> yards, String yard, String time, String revenueDate) {}

    public record Train(String consist, boolean layUp, String pseudoConsist, String message) {}

    public record Result(String message) {}

    /** VHLT01IS-INIT: the yards, the user group's yard and the current time */
    public Options options(String userGroup) {
        ConsistScreenLogic s = open(userGroup);
        return new Options(column(s, "VHLT010_DATA", "YRD"), s.text("VHLT020_DATA", "YRD-TRKG-YRD"), s.text("VHLT01IW", "TIME-OUTSRV"),
                s.text("VHLT01IW-PASSVALUE", "DATE-REV"));
    }

    /** VHLT01IS-LOC: a yard's transfer / storage tracks */
    public List<String> tracks(String userGroup, String yard) {
        ConsistScreenLogic s = open(userGroup);
        yard(s, yard);
        return column(s, "VHLT011_DATA", "YRD-SUB-LOC");
    }

    /** VHLT01IS-TRAIN (Text1 losing focus): whether the train may be laid up, and the old message when not */
    public Train train(String userGroup, String consist) {
        ConsistScreenLogic s = open(userGroup);
        return train(s, consist);
    }

    /** VHLT01IS-PROCESS (Lay-up Train): the checks, then the lay-up; the old message either way */
    public Result layUp(String userGroup, String yard, String track, String consist, String time) {
        ConsistScreenLogic s = open(userGroup);
        yard(s, yard);
        s.store("VHLT01IW", "YARD-SUB-LOC", track == null ? "" : track.strip());
        Train t = train(s, consist);
        if (!t.layUp()) throw new InvalidRequestException(t.message());          // the old Lay-up button stays disabled
        // Text2 is bound to TIME-OUTSRV and, read-only, to WS15IW-TIME.TIME-IN (which VHLT01IS-CONV-TIME reads)
        if (time != null) s.store("VHLT01IW", "TIME-OUTSRV", time.strip());
        s.store("WS15IW-TIME", "TIME-IN", time != null ? time.strip() : s.text("VHLT01IW", "TIME-OUTSRV"));
        s.store("VHLT01IW", "PSEUDO-CON-NO", t.pseudoConsist());
        s.run("VHLT01IS-PROCESS");
        failure(s);
        return new Result(message(s));
    }

    private ConsistScreenLogic open(String userGroup) {
        ConsistScreenLogic s = new ConsistScreenLogic(bundle, SCREEN, legacy, clock);
        s.store("VH01A01_DATA", "USER-GROUP", userGroup == null ? "" : userGroup);
        s.store("VH01A01_DATA", "TERM-ID", terminal);
        s.run("VHLT01IS-INIT");
        failure(s);
        return s;
    }

    /** Drop-down_List1's selectedItem is bound to YARD-LOC and YRD-TRKG-YRD; choosing it presses Push_Button3 (VHLT01IS-LOC) */
    private void yard(ConsistScreenLogic s, String yard) {
        String y = yard == null ? "" : yard.strip();
        s.store("VHLT01IW", "YARD-LOC", y);
        s.store("VHLT01IW", "YRD-TRKG-YRD", y);
        s.run("VHLT01IS-LOC");
        failure(s);
    }

    private Train train(ConsistScreenLogic s, String consist) {
        String c = consist == null ? "" : consist.strip();
        s.store("VHLT01IW", "CON-NO", c);
        s.store("VHLT01IW", "CON-NO-X", c);                                 // Text1 is also bound to CON-NO-X
        s.run("VHLT01IS-TRAIN");
        failure(s);
        boolean enabled = enabled(s, "VHLT01IW-TRIGGER", "ENABLE-LAY-UP", "DISABLE-LAY-UP", false);   // disabled when the window opens
        return new Train(c, enabled, s.text("VHLT01IW", "PSEUDO-CON-NO"), message(s));
    }

    /** Text3 shows CON-MSG-1 and MSG-1, whichever the old screen wrote last */
    private static String message(ConsistScreenLogic s) {
        return shown(s, "VHLT01IW-TIME-MSG|MSG-1", "VHLT01IW-CON-MSG|CON-MSG-1");
    }

    /** the text of the field written last among several bound to one widget ("RECORD|FIELD") */
    static String shown(ConsistScreenLogic s, String... fields) {
        String best = "";
        long when = -1;
        for (String rf : fields) {
            String[] p = rf.split("\\|");
            long w = s.lastWritten(p[0], p[1]);
            if (w > when) { when = w; best = s.text(p[0], p[1]); }
        }
        return best;
    }

    /** a button the old screen enables and disables through two trigger fields: the one written last wins */
    static boolean enabled(ConsistScreenLogic s, String record, String enable, String disable, boolean initially) {
        long e = s.lastWritten(record, enable), d = s.lastWritten(record, disable);
        return e == 0 && d == 0 ? initially : e > d;
    }

    /** a program that failed: the old screen's message box (XXMESSAGE from XDCONTROL) */
    void failure(ConsistScreenLogic s) {
        fail(s, messages);
    }

    static void fail(ConsistScreenLogic s, MessageResolver messages) {
        String code = s.text("XXMESSAGE", "UMSGCODE");
        if (code.isBlank()) return;
        List<String> ins = new ArrayList<>();
        for (int i = 1; i <= 3; i++) ins.add(s.text("XXMESSAGE", "UMSGINS", i));
        XdControl c = new XdControl(code, ins, 12, 0, false, false, "", "", "");
        throw new LegacyBusinessException(SCREEN, c, messages.resolve(code, ins));
    }

    /** a record's table column, up to its last filled row */
    static List<String> column(ConsistScreenLogic s, String record, String field) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : s.rows(record)) {
            Object v = row.get(field);
            String t = v == null ? "" : v.toString().strip();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
