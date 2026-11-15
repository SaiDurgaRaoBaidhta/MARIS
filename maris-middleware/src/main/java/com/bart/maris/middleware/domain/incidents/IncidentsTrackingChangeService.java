package com.bart.maris.middleware.domain.incidents;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vehicle tracking change incident (old VHCI02I): list a car's incidents for one of seven actions (car status GO / NO GO
 * / PM / USABLE, defer, delete, error NO GO, undelete, force close, move to another car), mark the incidents to change,
 * and apply the action.
 * <p>
 * The screen's rules are some 2,700 lines of checks and record moves (VHCI01IS-LIST, the VHCI01IS-ACT-* lists,
 * VHCI02IS-VALIDAT2/3 and the nine VHCI01IS-*-STATU2 / DF / DI / ERR / UD / FC / MOVE writers). They are run as the
 * screen's own statement groups on its records ({@link IncidentsScreenLogic}), in the order the old screen runs them:
 * pick the action, pick the status, type the car, List, then Change. A change rebuilds the list first without calling
 * the programs that write (the old list for DI / UD / ERR / DF resets a pseudo consist through VHINXB), so nothing is
 * written twice. Every text comes from the screen itself.
 */
public final class IncidentsTrackingChangeService {
    static final String SCREEN = "VHCI02I", W = "VHCI01IW", TRIGGER = "VHCI01IW-TRIGGER";
    /** VHCI01IW-TRIGGER flags VHCI01IS-LIST raises, in action order 1..7, and the statement group each one runs */
    private static final String[][] ACTIONS = {{"ACTION-CARSTATUS", "VHCI02IS-ACT-STAT"}, {"ACTION-DF", "VHCI01IS-ACT-DF"}, {"ACTION-DI", "VHCI01IS-ACT-DI"},
            {"ACTION-ERR", "VHCI01IS-ACT-ERR"}, {"ACTION-UD", "VHCI01IS-ACT-UD"}, {"ACTION-FC", "VHCI01IS-ACT-FC"}, {"ACTION-MOVE", "VHCI01IS-ACT-MOVE"}};

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;
    private final List<String> actions, statuses;
    private final String maintenanceFrom;
    private final Set<String> writers;

    public IncidentsTrackingChangeService(ScreenBundle bundle, LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock, String terminal,
                                          Set<String> writers) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal;
        this.writers = writers;
        actions = t.items(SCREEN, "rbtActions");
        statuses = t.items(SCREEN, "ddlStatus");
        maintenanceFrom = t.assigned("VHCI02IS-OPN-VM", "MRSVehicleIncident", "PASS-FROM-GUI", null);
        if (actions.size() != ACTIONS.length) throw new IllegalStateException(SCREEN + " has " + actions.size() + " actions");
    }

    /** {@code maintenanceFrom}: what "Maint Actions" passes to VHVM11I as the opening screen (VHCI02IS-OPN-VM) */
    public record Options(String date, String time, List<String> actions, List<String> statuses, String defaultStatus, boolean canChangeLocation,
                          String maintenanceFrom) {}

    public record Row(String incident, String eventType, String eventSubType, String status, String type, String date, String symptom, String change) {}

    /** what the user chose: action 1..7 (as listed), the status for action 1, and the car */
    public record ListRequest(int action, String status, String car) {}

    /** {@code changeEnabled}: whether the old screen leaves Change enabled after this list (VHCI01IW-TRIGGER ENABLE- / DISABLE-CHANGE) */
    public record Listing(String message, String currentStatus, String heading, List<Row> rows, boolean changeEnabled) {}

    /** a change: the list's choices, the CHANGE value typed for each incident, and the fields below the list */
    public record ChangeRequest(int action, String status, String car, Map<String, String> changes, String date, String time, String narrative,
                                String pmLocation, String pmTeam, String selected) {}

    public record Result(String message, String currentStatus, Listing listing) {}

    private IncidentsScreenLogic open(String group) {
        IncidentsScreenLogic s = new IncidentsScreenLogic(bundle, SCREEN, legacy, clock);
        s.store(W, "USER-GRP", group == null ? "" : group);
        s.store(W, "TERM-LAST-UPD", terminal);
        s.run("VHCI01IS-INIT");
        return s;
    }

    /** VHCI01IS-INIT: the backend's date and time (VHCI02G), the choices, the status VHCI01IS-CAR-STAT picks, and VHCI02B's location right */
    public Options options(String group) {
        IncidentsScreenLogic s = open(group);
        String date = s.text(W, "DATE-STAT-CHG"), time = s.text(W, "DISP-TIME-STAT-CHG");
        s.store(W, "ACTION-OPT", 1);
        s.run("VHCI01IS-CAR-STAT");
        return new Options(date, time, actions, statuses, s.text(W, "STATUS"), "Y".equals(s.text("VHCI02I-TRIGGER", "ENABLE-CHANGE-LOCATION")),
                maintenanceFrom);
    }

    /** the old order: action (VHCI01IS-CAR-STAT), status (VHCI01IS-CHK-PM), car, List (VHCI01IS-LIST and the action's list) */
    private void list(IncidentsScreenLogic s, int action, String status, String car) {
        if (action < 1 || action > ACTIONS.length) action = 0;
        s.store(W, "ACTION-OPT", action);
        s.run("VHCI01IS-CAR-STAT");
        if (action == 1) {
            s.store(W, "STATUS", status == null ? "" : status.strip().toUpperCase(Locale.ROOT));
            s.run("VHCI01IS-CHK-PM");
        }
        s.store(W, "CAR-NO-NUM", car == null ? "" : car.strip());
        s.store(TRIGGER, "ENABLE-CHANGE", " ");
        s.store(TRIGGER, "DISABLE-CHANGE", " ");
        s.run("VHCI01IS-LIST");
        for (String[] a : ACTIONS) {
            if (!"Y".equals(s.text(TRIGGER, a[0]))) continue;
            s.store(TRIGGER, a[0], " ");
            s.run(a[1]);
        }
    }

    private static boolean move(int action) {
        return action == 7;
    }

    private Listing listing(IncidentsScreenLogic s, int action) {
        String data = move(action) ? "VHCI01A_DATA" : "VHCI01D_DATA";
        String countField = move(action) ? "MOVE-INCDS-COUNT" : action == 1 ? "CAR-STATUS-COUNT" : "CI-COUNT";
        int count = (int) s.number(W, countField);
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String incident = s.text(data, "INCD-NO", i);
            if (incident.isBlank()) continue;
            rows.add(new Row(incident, s.text(data, "EVENT-TYPE", i), s.text(data, "EVENT-SUB-TYPE", i), s.text(data, "VEH-INCD-STAT", i),
                    s.text(data, "INCD-TYPE", i), s.text(data, "DATE-INCD-OCCR", i), s.text(data, "SYMP-CD", i), s.text(data, "CHANGE", i)));
        }
        boolean enabled = "Y".equals(s.text(TRIGGER, "ENABLE-CHANGE")) && !"Y".equals(s.text(TRIGGER, "DISABLE-CHANGE"));
        return new Listing(message(s), s.text(W, "SCR-DISP-VEH-STAT"), s.text(W, "COL-HEADING"), rows, enabled);
    }

    /** the screen's message, or the catalog message a failed program left in XXMESSAGE */
    private String message(IncidentsScreenLogic s) {
        String m = s.text(W, "FINAL-ERROR-MESSAGE").strip();
        if (!m.isEmpty()) return m;
        String code = s.text("XXMESSAGE", "UMSGCODE").strip();
        if (code.isEmpty()) return "";
        List<String> ins = List.of(s.text("XXMESSAGE", "UMSGINS", 1), s.text("XXMESSAGE", "UMSGINS", 2), s.text("XXMESSAGE", "UMSGINS", 3));
        MessageResolver.Message r = messages.resolve(code, ins);
        return r == null || r.text() == null ? code : r.text();
    }

    /** List Incds */
    public Listing list(ListRequest r, String group) {
        IncidentsScreenLogic s = open(group);
        list(s, r.action(), r.status(), r.car());
        return listing(s, r.action());
    }

    /** Change: the list rebuilt (no writes), the typed CHANGE values put on its rows, then VHCI02IS-VALIDAT2 and the action's writer */
    public Result change(ChangeRequest r, String group) {
        IncidentsScreenLogic s = open(group);
        s.skip(writers);
        list(s, r.action(), r.status(), r.car());
        s.skip(Set.of());
        String before = message(s);
        Listing listed = listing(s, r.action());
        if (!listed.changeEnabled()) throw new InvalidRequestException(before.isEmpty() ? "Change is not available for this car and action." : before);
        String data = move(r.action()) ? "VHCI01A_DATA" : "VHCI01D_DATA";
        int count = (int) s.number(W, move(r.action()) ? "MOVE-INCDS-COUNT" : r.action() == 1 ? "CAR-STATUS-COUNT" : "CI-COUNT");
        int selected = 0;
        for (int i = 1; i <= count; i++) {
            String incident = s.text(data, "INCD-NO", i).strip();
            if (r.changes() != null && r.changes().containsKey(incident)) s.store(data, "CHANGE", r.changes().get(incident).strip().toUpperCase(Locale.ROOT), i);
            if (incident.equals(r.selected() == null ? "" : r.selected().strip())) selected = i;
        }
        s.store(W, "SELECTION-INDEX", selected);
        if (r.date() != null) s.store(W, "DATE-STAT-CHG", r.date().strip());
        if (r.time() != null) s.store(W, "DISP-TIME-STAT-CHG", r.time().strip());
        s.store(W, "NARR", r.narrative() == null ? "" : r.narrative());
        s.store(W, "PM-LOC", r.pmLocation() == null ? "" : r.pmLocation().strip().toUpperCase(Locale.ROOT));
        s.store(W, "PM-TEAM", r.pmTeam() == null ? "" : r.pmTeam().strip().toUpperCase(Locale.ROOT));
        s.store(W, "FINAL-ERROR-MESSAGE", " ");
        s.run("VHCI02IS-VALIDAT2");
        return new Result(message(s), s.text(W, "SCR-DISP-VEH-STAT"), listing(s, r.action()));
    }
}
