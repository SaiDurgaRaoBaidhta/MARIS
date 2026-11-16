package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Changing a car's orientation (old VHYT13I, opened from the car move window with the yard, track and car): the cars on the
 * track (VHYT13A), the chosen car's two orientation marks (VHYT13B), then Change: VHYT13E checks the car did not change
 * meanwhile and is not an end car of a consist, VHYT13C and VHYT13D write the new mark (writes), VHYT13A lists again. The
 * screen's own statement groups run on its records ({@link YardTrackingMoveScreenLogic}), with the list selections the old
 * drop-downs make in between.
 */
public final class YardTrackingOrientationService {
    static final String SCREEN = "VHYT13I", PKG = "MRSYardTracking";

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;

    public YardTrackingOrientationService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
    }

    /** the window as it opens for a track and car: the cars, the chosen car (the one passed in, else the first) and its marks */
    public YardTrackingMove.Orientation orientation(String yard, String subLocation, String car) {
        return open(yard, subLocation, car).view();
    }

    /** a new mark chosen for the car, then Change */
    public YardTrackingMove.Orientation change(String yard, String subLocation, String car, String orientation) {
        Session s = open(yard, subLocation, car);
        String want = orientation == null ? "" : orientation.strip();
        int idx = -1;
        for (int i = 1; i <= 2; i++) if (s.text("VHYT13B_DATA", "VAGen_ORIENTATION", i).strip().equals(want) && !want.isEmpty()) idx = i;
        if (idx < 0) throw new InvalidRequestException("The orientation is one of " + s.marks() + ".");
        s.chooseOrientation(idx);
        if (s.canChange) {
            s.l.run("VHYT13IS-CHANGE");
            if ("Y".equals(s.text("VHYT13IW-TRIGGER", "UPDATE-SW"))) s.chooseOrientation(idx);      // the reloaded list selects again
        }
        return s.view();
    }

    private Session open(String yard, String subLocation, String car) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT), t = subLocation == null ? "" : subLocation.strip().toUpperCase(Locale.ROOT);
        if (y.isEmpty() || t.isEmpty()) throw new InvalidRequestException("A yard and a track are required.");
        Session s = new Session(new YardTrackingMoveScreenLogic(screens, SCREEN, legacy, clock), y, t);
        s.l.store("VHYT13IW-PASSVALUE", "YRD-TRKG-YRD", y);                              // what the car move window connects in
        s.l.store("VHYT13IW-PASSVALUE", "YRD-SUB-LOC", t);
        s.l.store("VHYT13IW-PASSVALUE", "CAR-NO", car == null ? "" : car.strip());
        s.l.store("VHYT13IW-PASSVALUE", "TERM-LAST-UPD", terminal);
        s.l.run("VHYT13IS-LOAD-DATA");                                                 // aboutToOpenWidget
        if ("Y".equals(s.text("VHYT13A_DATA", "CAR-FOUND"))) {
            s.l.store("VHYT13IW", "CAR-INDEX", s.l.number("VHYT13IW", "INIT-CAR-INDEX"));   // the car list selects INIT-CAR-INDEX
            s.l.run("VHYT13IS-GET-ORIEN");                                              // its selectionIndex event
            s.chooseOrientation((int) s.l.number("VHYT13IW", "ORIEN-INDEX"));           // the mark list selects ORIEN-INDEX
        }
        return s;
    }

    private final class Session {
        final YardTrackingMoveScreenLogic l;
        final String yard, track;
        boolean canChange;

        Session(YardTrackingMoveScreenLogic l, String yard, String track) {
            this.l = l;
            this.yard = yard;
            this.track = track;
        }

        String text(String rec, String field, int... index) {
            return l.text(rec, field, index);
        }

        List<String> marks() {
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= 2; i++) out.add(text("VHYT13B_DATA", "VAGen_ORIENTATION", i).strip());
            return out;
        }

        /** the mark list's selectionIndex event: VHYT13IS-VALID, which enables or disables Change */
        void chooseOrientation(int idx) {
            l.store("VHYT13IW", "ORIEN-INDEX", idx);
            int before = l.switches.size();
            l.run("VHYT13IS-VALID");
            for (int i = l.switches.size() - 1; i >= before; i--) {
                String sw = l.switches.get(i);
                if (sw.endsWith(".ENABLE-CHANGE")) { canChange = true; break; }
                if (sw.endsWith(".DISABLE-CHANGE")) { canChange = false; break; }
            }
        }

        YardTrackingMove.Orientation view() {
            List<String> cars = new ArrayList<>();
            long n = l.number("VHYT13IW-TRIGGER", "ROW-CNT");
            for (int i = 1; i <= n; i++) cars.add(text("VHYT13A_DATA", "CAR-AND-OR", i));
            String message = text("VHYT13IW-MSG", "FINAL-MESSAGE").strip();
            String code = text("XXMESSAGE", "UMSGCODE");
            if (message.isBlank() && !code.isBlank()) {
                List<String> ins = new ArrayList<>();
                for (int i = 1; i <= 3; i++) ins.add(text("XXMESSAGE", "UMSGINS", i));
                message = messages.resolve(code, ins).text();
            }
            String current = text("VHYT13IW", "LFT-OR").isBlank() ? text("VHYT13IW", "RGT-OR").strip() : text("VHYT13IW", "LFT-OR").strip();
            return new YardTrackingMove.Orientation(yard, track, cars, text("VHYT13IW", "CAR-NO").strip(), cars.isEmpty() ? List.of() : marks(),
                    current, canChange, message);
        }
    }
}
