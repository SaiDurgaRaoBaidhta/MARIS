package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-service trains: old VHYT52I (pseudo correction: the in-service trains of a dispatch location, VHYT52A, and the
 * request to retrieve a train's cars into the pseudo track, VHYT52B) and VHYT54I (force clear a train to a location:
 * VHYT54A, VHYT54B, then the message VHYT54C). The rules, checks and messages are the screens' own statement groups, run
 * on the screens' records by {@link YardTrackingTrackScreenLogic}; programs go through the legacy layer.
 */
public class YardTrackingTrainsService {
    static final String PSEUDO = "VHYT52I", FORCE = "VHYT54I";

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;

    public YardTrackingTrainsService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
    }

    /** one listed train: consist (CON-NO-D), its cars with orientation marks (CAR-01..10), status and the no-cars mark */
    public record Train(String consist, List<String> cars, String status, String noCars) {}

    /** @param yardChoosable the dispatch location can be chosen (opened from VH001I); otherwise it is the caller's yard */
    public record Trains(String yard, boolean yardChoosable, List<Train> trains, String message) {}

    public record Result(String message, List<Train> trains) {}

    private YardTrackingTrackScreenLogic pseudo(String yard, String whichGui, String group) {
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, PSEUDO, legacy, clock);
        l.signOn(group == null ? "" : group, terminal);
        l.store("VHYT52A-PASSVALUE", "YRD-TRKG-YRD", yard);
        l.store("VHYT52A-PASSVALUE", "WHICH-GUI", whichGui == null ? "" : whichGui);
        l.store("VHYT52A-PASSVALUE", "USER-GRP", group == null ? "" : group);
        return l;
    }

    private static String yard(String y) {
        return y == null ? "" : y.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * VHYT52A_INIT (opening, the caller's yard) or, with {@code location}, VHYT52A_LIST (List for a chosen dispatch location)
     */
    public Trains trains(String yard, String whichGui, String location, String group) {
        YardTrackingTrackScreenLogic l = pseudo(yard(yard), whichGui, group);
        l.run("VHYT52A_INIT");
        boolean choosable = !l.text("VHYT52A-TRIGGER", "ENABLE-YRD").isBlank();
        if (location != null && !location.isBlank()) {
            l.store("VHYT52A-PASSVALUE", "YRD-TRKG-YRD-L", yard(location));
            l.run("VHYT52A_LIST");
        }
        return new Trains(yard(yard), choosable, trains(l), l.text("VHYT52A_MESSAGE", "MESG1"));
    }

    /** VHYT52IS-SEL-ITEM then VHYT52A-RETRIEVE for the selected row (1-based, as listed by {@link #trains}) */
    public Result retrieve(String yard, String whichGui, String location, int row, String group) {
        YardTrackingTrackScreenLogic l = pseudo(yard(yard), whichGui, group);
        l.run("VHYT52A_INIT");
        if (location != null && !location.isBlank()) {
            l.store("VHYT52A-PASSVALUE", "YRD-TRKG-YRD-L", yard(location));
            l.run("VHYT52A_LIST");
        }
        if (row < 1 || row > trains(l).size()) throw new InvalidRequestException(
                l.text("VHYT52A_MESSAGE", "MESG1").isBlank() ? "Select one of the listed trains." : l.text("VHYT52A_MESSAGE", "MESG1"));
        l.store("VHYT52A-SELECTION_Level77Items", "SELECTION-IDX", row);
        l.run("VHYT52IS-SEL-ITEM");
        l.run("VHYT52A-RETRIEVE");
        return new Result(l.text("VHYT52A_MESSAGE", "MESG1"), trains(l));
    }

    private static List<Train> trains(YardTrackingTrackScreenLogic l) {
        List<Train> out = new ArrayList<>();
        for (Map<String, Object> r : l.rows("VHYT52A-CONSIST")) {
            String consist = String.valueOf(r.getOrDefault("CON-NO-D", "")).strip();
            if (consist.isEmpty()) continue;
            List<String> cars = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String c = String.valueOf(r.getOrDefault(String.format("CAR-%02d", i), "")).strip();
                if (!c.isEmpty()) cars.add(c);
            }
            out.add(new Train(consist, cars, String.valueOf(r.getOrDefault("CON-STAT", "")).strip(), String.valueOf(r.getOrDefault("NO-CARS-IND", "")).strip()));
        }
        return out;
    }

    /** VHYT54-FORCE: checks the train and the location, then sends the force-clear message; returns the screen's message */
    public String forceClear(String train, String location, String group) {
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, FORCE, legacy, clock);
        l.signOn(group == null ? "" : group, terminal);
        l.store("VH01A01_DATA", "USER-GROUP", group == null ? "" : group);
        l.store("VH01A01_DATA", "TERM-ID", terminal);
        l.store("VHYT54I-PASSVALUE", "CON-NO", train == null ? "" : train.strip());
        l.store("VHYT54I-PASSVALUE", "YRD", location == null ? "" : location.strip());   // as typed, like the old text box
        l.run("VHYT54-FORCE");
        return l.text("VHYT54A_MESSAGE", "MESG1");
    }
}
