package com.bart.maris.middleware.domain.yardqueries;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The reason out of service report (old VHYT32I, opened by VHYT27I's Display for report 6, detail, or 7, summary): the reason
 * changes in the date range (VHYT32A), their out of service times (VHYT32B, which records the run in MARIS's validation
 * date table) and, for the summary, the totals by reason (VHYT32C) sorted by VHYT32D (MARIS's report work table). The
 * screen's own statement groups run on its records ({@link YardQueriesReportLogic}); for report 7 the window presses its
 * Show Summary button itself (VHYT32IS-PRESS-SUM), and so does the API.
 */
public class YardQueriesOutOfServiceService {
    static final String SCREEN = "VHYT32I";
    /** VHYT27IW.REPORT-INDEX of the two reports, as VHYT27I-DISPLAY opens VHYT32I for them */
    static final int DETAIL = 6, SUMMARY = 7;

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal, badDates;

    public YardQueriesOutOfServiceService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.legacy = legacy;
        this.screens = screens;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
        this.badDates = screens.promptText("VHYT27I", "Message_Prompter1");
        new YardQueriesReportLogic(screens, SCREEN, legacy, clock).table("VAGen_Container_Details_DATA");
    }

    public YardQueriesOutOfService report(String yard, String from, String to, boolean summary, String group) {
        return report(yard, from, to, summary, group, Set.of());
    }

    /** the report; {@code notCalled}: programs not called (only for the read-only live test: VHYT32B and VHYT32D write) */
    public YardQueriesOutOfService report(String yard, String from, String to, boolean summary, String group, Set<String> notCalled) {
        String f = mdy(from), t = mdy(to);
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);
        YardQueriesReportLogic s = new YardQueriesReportLogic(screens, SCREEN, legacy, clock);
        // what VHYT27I hands the window (VHYT27IConnections): yard, dates as typed (MM-DD-YYYY), report number, group, terminal
        s.store("VHYT32W_PASSVALUE", "YRD-TRKG-YRD", y);
        s.store("VHYT32W_PASSVALUE", "START-DATE", f);
        s.store("VHYT32W_PASSVALUE", "END-DATE", t);
        s.store("VHYT32W_PASSVALUE", "GUI-INDEX", summary ? SUMMARY : DETAIL);
        s.store("VHYT32D_CRIT", "USER-GRP", group == null ? "" : group);
        s.store("VHYT32D_CRIT", "TERM-ID", terminal);
        s.skip(notCalled);
        s.fire(null, "aboutToOpenWidget");
        s.fire(null, "openedWidget");
        for (int i = 0; i < s.clicks.size(); i++) s.fire(s.clicks.get(i), "clicked");      // report 7: Show Summary
        // nothing changed in the range: VHYT32IS-REFORMAT never runs, so there are no totals (the work fields keep their start values)
        boolean timed = s.number("VHYT32IW", "NUMCT") > 0;
        List<String> groups = new ArrayList<>();
        for (String l : List.of("Label6", "Label8", "Label9", "Label10")) groups.add(s.caption(l));
        return new YardQueriesOutOfService(s.text("VHYT32IW", "HEADING"), s.text("VHYT32IW", "SHOP"), s.text("VHYT32W_PASSVALUE", "START-DATE"),
                s.text("VHYT32W_PASSVALUE", "END-DATE"), s.text("VHYT32IW", "HEADING-TIME"), s.caption("Label4"), timed ? s.text("VHYT32IWORK", "DAYS").strip() : null,
                s.caption("Label7"), timed ? s.text("VHYT32IWORK", "DISPLAY-TOT-HRS-MINS").strip() : null, groups,
                YardQueriesYardLocationService.table(s, "VAGen_Container_Details_DATA"), summary ? s.caption("Label1") : null,
                summary ? YardQueriesYardLocationService.table(s, "VAGen_Container_Details_DATA_ROW") : null);
    }

    /** yyyy-MM-dd -&gt; the MM-DD-YYYY VHYT27I's date fields hold; anything else is refused with VHYT27I's date message */
    private String mdy(String iso) {
        String d = iso == null ? "" : iso.strip();
        try {
            LocalDate.parse(d);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(badDates);
        }
        return d.substring(5, 7) + "-" + d.substring(8) + "-" + d.substring(0, 4);
    }
}
