package com.bart.maris.middleware.domain.yardqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The yard location status report (old VHYT26I, opened by VHYT27I's Display for report 2): the yard's cars (VHYT26A), the
 * shop / yard exchange (VHYT30A), the summary by out of service reason (sorted by VHYT26C, which writes MARIS's report work
 * table) and the out of town summary by home yard and car type. About 600 lines of counting and elapsed-time arithmetic in
 * the EGL, so the screen's own statement groups run on its records ({@link YardQueriesReportLogic}) and the report is read
 * back from the tables and fields its window shows.
 */
public class YardQueriesYardLocationService {
    static final String SCREEN = "VHYT26I";

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;

    public YardQueriesYardLocationService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.legacy = legacy;
        this.screens = screens;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        new YardQueriesReportLogic(screens, SCREEN, legacy, clock).table("VAGen_Container_Details_DATA");     // fails at start-up if the screen changed
    }

    /** the report for a yard (VHYT27I passes the chosen yard as it is; blank stays blank for VHYT26A) */
    public YardQueriesYardLocation report(String yard) {
        return report(yard, Set.of());
    }

    /**
     * the report, with the given programs not called (they answer with their own input): only for the read-only live test,
     * which must never call VHYT26C (it writes MARIS's report work table)
     */
    public YardQueriesYardLocation report(String yard, Set<String> notCalled) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);
        YardQueriesReportLogic s = new YardQueriesReportLogic(screens, SCREEN, legacy, clock);
        // what VHYT27I hands the window (VHYT27IConnections): the yard, to both pass records, and its terminal id
        s.store("VHYT26W_PASSVALUE", "YRD-TRKG-YRD", y);
        s.store("VHYT25W_PASSVALUE", "YRD-TRKG-YRD", y);
        s.store("VHYT26W_PASSVALUE", "TERM-ID", terminal);
        s.skip(notCalled);
        s.fire(null, "aboutToOpenWidget");
        s.fire(null, "openedWidget");
        boolean testTracks = s.text("VHYT26IW-TRIGGERS", "INC-TEST-TRACK").equals("Y");
        return new YardQueriesYardLocation(s.caption("Label1"), s.text("VHYT26IW", "SHOP"), s.text("VHYT26IW", "RUN_DATE"), s.text("VHYT26IW", "RUN_TIME"),
                s.caption("Label4"), s.text("VHYT26IW", "GRAND-TOTAL-CARS").strip(), testTracks ? s.caption("Label5") : null,
                table(s, "VAGen_Container_Details_DATA"), table(s, "VAGen_Container_Details_DATA_ROW"), table(s, "VAGen_Container_Details_DATA_ROW1"),
                s.caption("Label6"), table(s, "VAGen_Container_Details_DATA1"), s.caption("Push_Button2"), s.caption("Push_Button3"));
    }

    /** a table as the window shows it, without the blank rows at its end (the old lists stop at the last filled row on screen) */
    static YardQueriesYardLocation.Table table(YardQueriesReportLogic s, String container) {
        YardQueriesReportLogic.Table t = s.table(container);
        List<List<String>> rows = new ArrayList<>(t.rows());
        while (!rows.isEmpty() && rows.get(rows.size() - 1).stream().allMatch(c -> c.isEmpty() || c.equals("0"))) rows.remove(rows.size() - 1);
        return new YardQueriesYardLocation.Table(t.headings(), rows);
    }
}
