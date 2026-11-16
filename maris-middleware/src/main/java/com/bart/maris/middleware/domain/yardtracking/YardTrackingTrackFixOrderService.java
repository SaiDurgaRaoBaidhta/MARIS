package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fix the order of cars on a track (old VHYT14I, opened by Fix Order on the track windows): the track's cars (VHYT14A);
 * Fix Order inserts one car before or after another after checking the track did not change (VHYT14B), asks first when
 * a pseudo train would be un-made (then VHYT14F resets it), renumbers the cars (VHYT14D), parks the moved car (VHYT14C),
 * adds the history row (VHYT14E) and reads the track again. The screen's index arithmetic and pseudo train branches run
 * as written, by {@link YardTrackingTrackScreenLogic}.
 */
public final class YardTrackingTrackFixOrderService {
    static final String SCREEN = "VHYT14I";
    private static final List<String> WARNINGS = List.of("CAR-RE-ORDER-IN-CON", "CAR-LOC-IN-CON", "CAR-IN-CON", "LOC-IN-CON");

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;

    public YardTrackingTrackFixOrderService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
    }

    /** a car on the track: its list index, number, position, pseudo train and whether that train is partial */
    public record Car(int index, String car, int position, String pseudoTrain, boolean partial) {}

    /** the window: the cars, the car first chosen (the one clicked on the track), and the message */
    public record View(List<Car> cars, int selected, String message) {}

    /**
     * Fix Order: move car {@code carIndex} before (or {@code after}) car {@code relativeIndex}; {@code confirm} answers the old
     * screen's "will be un-made. Continue?" question
     */
    public record Fix(String yard, String subLocation, Integer position, Integer carIndex, Integer relativeIndex, Boolean after, Boolean confirm) {}

    /** what Fix Order did: the message, the question when one must be confirmed first, whether cars moved, the track after */
    public record Fixed(String message, String question, boolean moved, View track) {}

    /** VHYT14IS-LOAD-DATA */
    public View view(String yard, String subLocation, Integer position) {
        YardTrackingTrackScreenLogic l = open(yard, subLocation, position);
        return view(l);
    }

    /** VHYT14IS-CHANGE (and VHYT14IS-RESET-CON when the question is answered yes) */
    public Fixed fix(Fix f) {
        YardTrackingTrackScreenLogic l = open(f.yard(), f.subLocation(), f.position());
        long count = l.number("VHYT14IW-TRIGGER", "ROW-CNT");
        int car = f.carIndex() == null ? 0 : f.carIndex(), rel = f.relativeIndex() == null ? 0 : f.relativeIndex();
        if (car < 1 || car > count || rel < 1 || rel > count) throw new InvalidRequestException("Choose two cars of the track (1 to " + count + ").");
        l.store("VHYT14IW", "CAR1-INDEX", car);
        l.store("VHYT14IW", "CAR2-INDEX", rel);
        l.store("VHYT14IW", "BEFORE-AFTER", Boolean.TRUE.equals(f.after()) ? 2 : 1);
        l.store("VHYT14IW-MSG", "FINAL-MESSAGE", "");
        for (String w : WARNINGS) l.store("VHYT14IW-TRIGGER", w, "");
        int calls = l.calls.size();
        l.run("VHYT14IS-CHANGE");
        String raised = WARNINGS.stream().filter(w -> "Y".equals(l.text("VHYT14IW-TRIGGER", w))).findFirst().orElse(null);
        if (raised != null) {
            String question = l.text("VHYT14IW-MSG", switch (raised) {
                case "CAR-RE-ORDER-IN-CON" -> "WARNING-A";
                case "CAR-LOC-IN-CON" -> "WARNING-B";
                default -> "WARNING-C";
            });
            if (!Boolean.TRUE.equals(f.confirm())) return new Fixed("", question, false, view(l));
            l.run("VHYT14IS-RESET-CON");
        }
        boolean moved = l.calls.subList(calls, l.calls.size()).stream().anyMatch(c -> c.getKey().toUpperCase(Locale.ROOT).startsWith("VHYT14D"));
        return new Fixed(l.text("VHYT14IW-MSG", "FINAL-MESSAGE"), null, moved, view(l));
    }

    private YardTrackingTrackScreenLogic open(String yard, String subLocation, Integer position) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT), t = subLocation == null ? "" : subLocation.strip().toUpperCase(Locale.ROOT);
        if (y.isEmpty() || t.isEmpty()) throw new InvalidRequestException("A yard and a track are required.");
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, SCREEN, legacy, clock);
        l.store("VHYT14IW-PASSVALUE", "YRD-TRKG-YRD", y);                     // what the track window connects in
        l.store("VHYT14IW-PASSVALUE", "YRD-SUB-LOC", t);
        l.store("VHYT14IW-PASSVALUE", "POSN-NO", position == null ? 0 : position);
        l.store("VHYT14IW-PASSVALUE", "TERM-LAST-UPD", terminal);
        l.run("VHYT14IS-LOAD-DATA");                                           // aboutToOpenWidget
        return l;
    }

    private static View view(YardTrackingTrackScreenLogic l) {
        long count = l.number("VHYT14IW-TRIGGER", "ROW-CNT");
        List<Car> cars = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            cars.add(new Car(i, l.text("VHYT14A_DATA", "CAR-NO", i).strip(), (int) l.number("VHYT14A_DATA", "POSN-NO", i),
                    l.text("VHYT14A_DATA", "CON-NO", i).strip(), "Y".equals(l.text("VHYT14A_DATA", "PART-CON-IND", i))));
        }
        return new View(cars, (int) l.number("VHYT14IW-X", "INIT-CAR-INDEX"), l.text("VHYT14IW-MSG", "FINAL-MESSAGE"));
    }
}
