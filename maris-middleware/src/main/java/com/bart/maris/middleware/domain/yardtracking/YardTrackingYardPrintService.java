package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The yard car list hard copy (old VHYT24I, opened from the yard maps with the yard): the printers (WS70A01), then Hard Copy
 * submits the yard's report program, which VHYT24IS-SUB chooses by yard (VHYT24A Hayward, VHYT02E/VHYT02C Dublin and
 * Pittsburg, VHYT24B..E Concord, Richmond, Daly City, Millbrae, VHYT02F/G the others). The screen's own statement groups run
 * on its records ({@link YardTrackingMoveScreenLogic}).
 */
public final class YardTrackingYardPrintService {
    static final String SCREEN = "VHYT24I", PKG = "MRSYardTracking";

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final List<String> yards;

    public YardTrackingYardPrintService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        this.screens = screens;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        yards = YardTrackingMoveEgl.comparedAll(screens, "VHYT24IS-SUB", PKG, "YRD-TRKG-YRD");
        if (yards.isEmpty()) throw new IllegalStateException("VHYT24IS-SUB prints for no yard");
    }

    /** the yards the old Hard Copy prints for */
    public List<String> yards() {
        return yards;
    }

    /** the printers (VHYT01IS-INIT-X, WS70A01) */
    public YardTrackingMove.PrintOptions options(String yard) {
        String y = yard(yard);
        YardTrackingMoveScreenLogic l = new YardTrackingMoveScreenLogic(screens, SCREEN, legacy, clock);
        l.run("VHYT01IS-INIT-X");
        failed(l);
        List<YardTrackingMove.Printer> out = new ArrayList<>();
        for (var row : l.rows("WS70A01_DATA")) {
            String name = String.valueOf(row.getOrDefault("VTAM-NAME", "")).strip();
            if (!name.isEmpty()) out.add(new YardTrackingMove.Printer(String.valueOf(row.getOrDefault("PRTR-LOC-DESC", "")).strip(), name));
        }
        return new YardTrackingMove.PrintOptions(y, out);
    }

    /** Hard Copy on a printer: the yard's report program (VHYT24IS-SUB) */
    public void print(String yard, String printer) {
        String y = yard(yard), p = printer == null ? "" : printer.strip();
        if (p.isEmpty()) throw new InvalidRequestException("Choose a printer.");
        YardTrackingMoveScreenLogic l = new YardTrackingMoveScreenLogic(screens, SCREEN, legacy, clock);
        l.store("VHYT24IW-PASSVALUE", "YRD-TRKG-YRD", y);
        l.store("VHDVS4IW-PASSVALUE", "VTAM-NAME", p);                                  // VHDVS4IS-HARD-COPY, from the printer chosen
        l.run("VHYT24IS-SUB");
        failed(l);
    }

    private String yard(String yard) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);
        if (!yards.contains(y)) throw new InvalidRequestException("The yard is one of " + String.join(", ", yards) + ".");
        return y;
    }

    /** a program's error the old screen shows in its message box (XXMESSAGE) */
    private void failed(YardTrackingMoveScreenLogic l) {
        String code = l.text("XXMESSAGE", "UMSGCODE");
        if (code.isBlank()) return;
        List<String> ins = new ArrayList<>();
        for (int i = 1; i <= 3; i++) ins.add(l.text("XXMESSAGE", "UMSGINS", i));
        String program = l.calls.isEmpty() ? SCREEN : l.calls.get(l.calls.size() - 1).getKey();
        throw new LegacyBusinessException(program, XdControl.from(l.get("XDCONTROL")), messages.resolve(code, ins));
    }
}
