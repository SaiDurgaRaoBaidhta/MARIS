package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRAM report (old VHSCM1I): for a schedule day and time, the cars each home yard provides for the four yards
 * (scheduled, actual, difference), from program VHSCM1B, one home yard at a time as the old Prv / Nxt buttons page.
 * The start day comes from the revenue date (VHXREVA, VHSCM1A) and the time of day; the day and time codes, the home
 * yards, the yard and column headings and the messages come from the old screen.
 */
public final class VehicleQueriesScramService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String LIST = "VHSCM1IS-LIST";

    private final Programs programs;
    private final Clock clock;
    private final List<String> days, times, dayByRevDay, homeYards, groups, columns;
    private final String weekendTime, both, invalidDay, invalidTime, noSchedule, title, homeHeading;
    private final List<String> weekdayTimes;
    private final int rowCount;
    private final long revSelection;

    public VehicleQueriesScramService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        days = t.compared(LIST, PKG, "SCHEDULE-DAY").stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        times = t.compared(LIST, PKG, "SCHEDULE-TIME").stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        dayByRevDay = t.assignedAll("VHSCM1IS-FIND-DAY", PKG, "SCHEDULE-DAY");          // REV-DAY 1 .. 7: SU, MO, .. SA
        weekendTime = t.assignedAll("VHSCM1IS-FIND-DAY", PKG, "SCHEDULE-TIME").get(0);
        weekdayTimes = t.assignedAll("VHSCM1IS-FIND-TIME", PKG, "SCHEDULE-TIME");        // AM, AM, PM, PM, AM by hour
        both = t.assignedAll(LIST, PKG, "RET-IND").get(0);
        homeYards = new ArrayList<>(t.indexed(LIST, PKG, "HOME-YRD").values());
        rowCount = t.assignedNumber(LIST, PKG, "ROW-COUNT").intValue();
        revSelection = t.assignedNumber("VHSCM1IS-CLEAR", PKG, "SEL-IDX").longValue();
        invalidDay = t.triggeredPrompt("VHSCM1I", "INVALID-DAY").strip();
        invalidTime = t.triggeredPrompt("VHSCM1I", "INVALID-TIME").strip();
        noSchedule = t.triggeredPrompt("VHSCM1I", "NO-REC-FOUND").strip();
        title = t.windowTitle("VHSCM1I");
        groups = new ArrayList<>();
        for (String w : List.of("lblConcord", "lblDalyCity", "lblHayward", "lblRichmond")) groups.add(t.label("VHSCM1I", w).replaceAll("^[-\\s]+|[-\\s]+$", ""));
        List<EglTexts.Column> cols = t.tableColumns("VHSCM1I", "cntScram");
        homeHeading = cols.get(0).heading();
        columns = cols.subList(1, 4).stream().map(EglTexts.Column::heading).toList();
    }

    /** {@code day} / {@code time}: what the screen starts with; {@code groups}: the yards provided for; {@code columns}: per yard */
    public record Options(String title, String day, String time, List<String> days, List<String> times, List<String> homeYards, String homeYardHeading,
                          List<String> groups, List<String> columns) {}

    /** VHSCM1IS-CLEAR: the revenue date's day (VHXREVA, VHSCM1A) and the time of day */
    public Options options() {
        String day = "", time = "";
        ProgramResult rev = programs.attempt("VHXREVA", Map.of("VHXREVA_CRIT", Map.of("selIdx", revSelection))).result();
        int urc = rev.control() == null ? 0 : rev.control().returnCode();
        if (urc == 0 || urc == 4) {
            String mdy = rev.record("VHXREVA_DATA") == null ? "" : revenueDate(rev.record("VHXREVA_DATA"));
            String ymd = mdy.length() == 10 ? mdy.substring(6, 10) + "-" + mdy.substring(0, 2) + "-" + mdy.substring(3, 5) : mdy;
            ProgramResult a = programs.attempt("VHSCM1A", Map.of("VHSCM1A_DATA", Map.of("revDate", ymd))).result();
            int aurc = a.control() == null ? 0 : a.control().returnCode();
            if ((aurc == 0 || aurc == 4) && a.record("VHSCM1A_DATA") != null) {
                int n = (int) number(a.record("VHSCM1A_DATA"), "revDay");
                if (n >= 1 && n <= dayByRevDay.size()) {
                    day = dayByRevDay.get(n - 1);
                    time = n == 1 || n == dayByRevDay.size() ? weekendTime : timeOfDay(LocalTime.now(clock));
                }
            }
        }
        return new Options(title, day, time, days, times, homeYards, homeHeading, groups, columns);
    }

    /** VHSCM1IS-FIND-TIME */
    String timeOfDay(LocalTime now) {
        int h = now.getHour(), m = now.getMinute();
        if (h < 7) return weekdayTimes.get(0);
        if (h == 7) return m == 0 ? weekdayTimes.get(1) : weekdayTimes.get(2);
        if (h <= 17) return weekdayTimes.get(3);
        return weekdayTimes.get(4);
    }

    public record Row(String homeYard, List<Long> cells) {}

    public record Total(long scheduled, long actual, long difference) {}

    /** {@code message}: this home yard has no schedule (the old screen stops paging there) */
    public record Yard(String homeYard, List<Row> rows, List<Total> totals, String message) {}

    public record Report(String day, String time, List<Yard> yards) {}

    /** List Details (VHSCM1IS-LIST), then Nxt through every home yard (VHSCM1IS-PRVNXT: VHSCM1B) */
    public Report report(String day, String time) {
        String d = day == null ? "" : day.strip(), tm = time == null ? "" : time.strip();
        Map<String, Object> rec = new HashMap<>();
        rec.put("retInd", "");
        if (!d.isEmpty() || !tm.isEmpty()) {
            if (d.isEmpty() || !days.contains(d)) throw new InvalidRequestException(invalidDay);
            if (tm.isEmpty() || !times.contains(tm)) throw new InvalidRequestException(invalidTime);
            rec.put("retInd", both);
            rec.put("scheduleDay", d);
            rec.put("scheduleTime", tm);
        }
        List<Yard> yards = new ArrayList<>();
        int shown = rowCount;                                                            // VHSCM1IW.ROW-COUNT: Nxt keeps it
        for (String yard : homeYards) {
            rec.put("critHomeYrd", yard);
            ProgramResult r = programs.attempt("VHSCM1B", Map.of("VHSCM1B_DATA", rec)).result();
            int urc = r.control() == null ? 0 : r.control().returnCode();
            Map<String, Object> back = r.record("VHSCM1B_DATA");
            if (urc != 0 && urc != 4) {                                                  // only the home yard's name
                List<Long> zeros = new ArrayList<>();
                for (int k = 0; k < 12; k++) zeros.add(0L);
                yards.add(new Yard(yard, List.of(new Row(yard, zeros)), totals(Map.of()), null));
                shown = 1;
                rec = new HashMap<>();
                rec.put("dataRow", List.of(Map.of("homeYrd", yard)));
                continue;
            }
            String found = back == null ? "" : text(back, "recFound");
            if (found.equals("N")) {
                yards.add(new Yard(yard, List.of(), totals(Map.of()), noSchedule));
                break;                                                                   // Prv and Nxt are off
            }
            List<Row> rows = new ArrayList<>();
            if (found.equals("Y")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = back.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
                for (int k = 0; k < shown; k++) {                                      // the old table shows ROW-COUNT rows, blank ones too
                    Map<String, Object> row = k < list.size() ? list.get(k) : Map.of();
                    List<Long> cells = new ArrayList<>();
                    for (int c = 1; c <= 12; c++) cells.add(number(row, "c" + c));
                    rows.add(new Row(text(row, "homeYrd"), cells));
                }
            } else {
                shown = 0;                                                               // neither found nor not found: the old message only
            }
            yards.add(new Yard(yard, rows, totals(back == null ? Map.of() : back), null));
            rec = new HashMap<>(back == null ? Map.of() : back);                         // the record goes back as the program left it
        }
        return new Report(d, tm, yards);
    }

    private static List<Total> totals(Map<String, Object> b) {
        List<Total> out = new ArrayList<>();
        for (int g = 1; g <= 4; g++) out.add(new Total(number(b, "totSchd" + g), number(b, "totAct" + g), number(b, "totDiff" + g)));
        return out;
    }

    private static String revenueDate(Map<String, Object> d) {
        Object v = d.get("revenueDate");
        if (v instanceof Map<?, ?> m) {                                                   // MM-DD-YYYY as its parts
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) m;
            return String.format("%02d-%02d-%04d", number(nested(p, "revenueDateMon"), "revenueDateMonNum"),
                    number(nested(p, "revenueDateDay"), "revenueDateDayNum"), number(nested(p, "revenueDateYr"), "revenueDateYrNum"));
        }
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> m, String k) {
        return m.get(k) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    }
}
