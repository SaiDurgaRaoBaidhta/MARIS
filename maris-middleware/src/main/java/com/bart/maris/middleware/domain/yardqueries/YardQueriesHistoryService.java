package com.bart.maris.middleware.domain.yardqueries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The history reports VHYT27I opens: car location history (VHYT29I: VHYT29IS-GET-DATA / -REFORMAT / -CHECK, program VHYT29A)
 * and yard vehicle status history (VHYT33I: VHYT33IS-..., VHYT33A). The date range is checked as VHYT27IS-TEST-DATE does,
 * with its message. Read-only.
 */
public class YardQueriesHistoryService {
    private static final String PKG = "MrsYardTrackingQueries";
    /** VHYT29IS-CHECK / VHYT33IS-CHECK */
    private static final int MAX_ROWS = 324;

    private final Programs programs;
    private final String badRange;

    public YardQueriesHistoryService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        this.badRange = screens.promptText("VHYT27I", "Message_Prompter1");
    }

    /** VHYT29IS-GET-DATA: a blank car range is 0300-2999, one car is that car; a blank yard is HY */
    public YardQueriesHistory.CarHistory carHistory(String yard, String from, String to, String fromCar, String toCar) {
        String f = checkRange(from, to), t = to.strip(), y = yard(yard), fc = blank(fromCar), tc = blank(toCar);
        Map<String, Object> crit = criteria(f, t, y);
        crit.put("fromCar", fc.isEmpty() ? "0300" : fc);
        crit.put("toCar", !tc.isEmpty() ? tc : fc.isEmpty() ? "2999" : fc);
        ProgramResult r = programs.run("VHYT29A", Map.of("VHYT29A_CRIT", crit));
        List<YardQueriesHistory.Move> out = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHYT29A_DATA", "dataRow")) {
                String prev = text(row, "yrdSubLocPrev");
                out.add(new YardQueriesHistory.Move(text(row, "carNo"), text(row, "yrdSubLoc"), (int) number(row, "posnNo"), text(row, "vehStat"),
                        text(row, "vagenOrientation"), text(row, "dateUserMoved"), text(row, "timeUserMoved"),
                        prev.isEmpty() ? text(row, "moveReasonCdPrv") : prev, (int) number(row, "posnNoPrev"), text(row, "moveReasonDesc"),
                        text(row, "moveReasonCdNew")));
            }
        }
        return new YardQueriesHistory.CarHistory(y, f, t, (String) crit.get("fromCar"), (String) crit.get("toCar"), out.size() >= MAX_ROWS, out);
    }

    /** VHYT33IS-GET-DATA: a blank yard is HY; no car range */
    public YardQueriesHistory.StatusHistory statusHistory(String yard, String from, String to) {
        String f = checkRange(from, to), t = to.strip(), y = yard(yard);
        ProgramResult r = programs.run("VHYT33A", Map.of("VHYT33A_CRIT", criteria(f, t, y)));
        List<YardQueriesHistory.StatusChange> out = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHYT33A_DATA", "dataRow")) {
                out.add(new YardQueriesHistory.StatusChange(text(row, "carNo"), text(row, "dateStatChg"), text(row, "timeStatChg"),
                        text(row, "yrdSubLoc"), text(row, "vehStatNew"), text(row, "vehStatPrev"), text(row, "moveReasonDesc"), text(row, "moveReasonCd")));
            }
        }
        return new YardQueriesHistory.StatusHistory(y, f, t, out.size() >= MAX_ROWS, out);
    }

    private static Map<String, Object> criteria(String from, String to, String yard) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("startDate", from);
        crit.put("endDate", to);
        crit.put("yrdTrkgYrd", yard);
        return crit;
    }

    /**
     * VHYT27IS-TEST-DATE: month 1-12, day 1-31, year from 2001, and from not after to (the old check, not a calendar's);
     * dates come as yyyy-MM-dd, the form the programs take. Returns from.
     */
    private String checkRange(String from, String to) {
        String f = blank(from), t = blank(to);
        if (!valid(f) || !valid(t) || f.compareTo(t) > 0) throw new InvalidRequestException(badRange);
        return f;
    }

    /** VHYT27IS-TEST-DATE's check, for the reports' hard copy too */
    static boolean validRange(String from, String to) {
        return valid(from) && valid(to) && from.compareTo(to) <= 0;
    }

    private static boolean valid(String iso) {
        if (!iso.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int y = Integer.parseInt(iso.substring(0, 4)), m = Integer.parseInt(iso.substring(5, 7)), d = Integer.parseInt(iso.substring(8));
        return y >= 2001 && m >= 1 && m <= 12 && d >= 1 && d <= 31;
    }

    private static String yard(String yard) {
        String y = blank(yard).toUpperCase(Locale.ROOT);
        if (y.isEmpty()) return "HY";
        if (!y.matches("[A-Z]{1,3}")) throw new InvalidRequestException("A yard is one to three letters, e.g. HY.");
        return y;
    }

    private static String blank(String s) {
        return s == null ? "" : s.strip();
    }
}
