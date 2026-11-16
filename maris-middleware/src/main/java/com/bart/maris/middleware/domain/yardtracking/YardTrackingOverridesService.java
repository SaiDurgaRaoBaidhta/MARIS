package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Make / break overrides of a yard's trains for a revenue date (old VHYT34I, module MrsYardTrackingQueries): the date and
 * schedule (VHYT34A, VHYT34B), the make / break times (VHYT34C/D), the trains (VHYT34H) and their arrival times (VHYT34J),
 * what a train is scheduled for (VHYT34K, VHYT34L, VHYT34M), the single-train overrides (VHYT34N), and the two updates:
 * Make/Break Overrides (VHYT34F) and Single Train Override (VHYT34G). Every request opens the screen like the old
 * window (its opening groups call VHYT34B, which writes), applies the request and runs the screen's own statement groups
 * ({@link YardTrackingTrackScreenLogic}).
 */
public class YardTrackingOverridesService {
    static final String SCREEN = "VHYT34I";
    /** the time lists: request name -> [time field in VHYT34IW, index field, the record its list reads, VHYT34D_CRIT's count of it] */
    private static final Map<String, String[]> TIMES = new LinkedHashMap<>();

    static {
        TIMES.put("amMakeFrom", new String[] {"AM-MAKE-FROM-TIME", "AM-MAKE-FROM-IDX", "VHYT34D_DATA", "MA-CNT"});
        TIMES.put("amMakeTo", new String[] {"AM-MAKE-TO-TIME", "AM-MAKE-TO-IDX", "VHYT34D_DATA", "MA-CNT"});
        TIMES.put("amBreakFrom", new String[] {"AM-BREAK-FROM-TIME", "AM-BREAK-FROM-IDX", "VHYT34D_BA_DATA", "BA-CNT"});
        TIMES.put("amBreakTo", new String[] {"AM-BREAK-TO-TIME", "AM-BREAK-TO-IDX", "VHYT34D_BA_DATA", "BA-CNT"});
        TIMES.put("pmMakeFrom", new String[] {"PM-MAKE-FROM-TIME", "PM-MAKE-FROM-IDX", "VHYT34D_MP_DATA", "MP-CNT"});
        TIMES.put("pmMakeTo", new String[] {"PM-MAKE-TO-TIME", "PM-MAKE-TO-IDX", "VHYT34D_MP_DATA", "MP-CNT"});
        TIMES.put("pmBreakFrom", new String[] {"PM-BREAK-FROM-TIME", "PM-BREAK-FROM-IDX", "VHYT34D_BP_DATA", "BP-CNT"});
        TIMES.put("pmBreakTo", new String[] {"PM-BREAK-TO-TIME", "PM-BREAK-TO-IDX", "VHYT34D_BP_DATA", "BP-CNT"});
    }

    /** the check boxes: request name -> VHYT34IW field ("1" checked, "0" not) */
    private static final Map<String, String> BOXES = new LinkedHashMap<>();

    static {
        BOXES.put("allCancel", "ALL-CANCEL");
        BOXES.put("allReactivate", "ALL-RE-ACTIVATE");
        BOXES.put("amMakeCancel", "AM-MAKE-CANCEL");
        BOXES.put("amMakeReactivate", "AM-MAKE-RE");
        BOXES.put("amBreakCancel", "AM-BREAK-CANCEL");
        BOXES.put("amBreakReactivate", "AM-BREAK-RE");
        BOXES.put("pmMakeCancel", "PM-MAKE-CANCEL");
        BOXES.put("pmMakeReactivate", "PM-MAKE-RE");
        BOXES.put("pmBreakCancel", "PM-BREAK-CANCEL");
        BOXES.put("pmBreakReactivate", "PM-BREAK-RE");
    }

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;

    public YardTrackingOverridesService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.terminal = terminal == null ? "" : terminal;
    }

    public record SingleTrain(String train, String arrival, String breakScheduled, String breakCancelled, String makeScheduled, String makeCancelled) {}

    /**
     * @param times    each time list (amMakeFrom ...) as the old drop-downs offer it
     * @param statuses ALL, AM make / break, PM make / break status texts
     */
    public record State(String yard, String revenueDate, String schedule, String message, Map<String, List<String>> times,
                        Map<String, String> statuses, List<String> trains, List<SingleTrain> singleTrains) {}

    /** a single train's arrival times (VHYT34J) and, for an arrival, what it is scheduled for (VHYT34I-SCHED-FOR) */
    public record TrainTimes(String train, List<String> arrivals, String scheduledFor) {}

    /** what a request sets: the times chosen (by request name), the boxes checked, and the single train's choices */
    public record Overrides(Map<String, String> times, Map<String, Boolean> boxes) {}

    public record SingleOverride(String train, String arrival, boolean breakCancel, boolean makeCancel, boolean breakScheduled, boolean makeScheduled) {}

    private YardTrackingTrackScreenLogic open(String yard, String date, String group) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);         // blank as the old window opened without one
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, SCREEN, legacy, clock);
        l.signOn(group == null ? "" : group, terminal);
        l.store("VHYT34I-PASSVALUE", "YRD-TRK-YRD", y);
        l.store("VHYT34I-PASSVALUE", "USER-GROUP", group == null ? "" : group);
        for (String g : List.of("VHYT34I-INITIAL", "VHYT34I-REV-DATE", "VHYT34I-TIMES", "VHYT34I-SCHED-ADJ", "VHYT34I-TRAIN-DROP", "VHYT34I-LISTSINGLE")) l.run(g);
        if (date != null && !date.isBlank() && !date.strip().equals(l.text("VHYT34IW", "REV-DATE-MMDDYYYY"))) {
            l.store("VHYT34IW-DATES", "NEW-REV-DATE-MMDDYYYY", date.strip());   // Text5, then losing focus
            l.run("VHYT34I-DATE-CHGED");
        }
        return l;
    }

    /** a time list as the drop-down loads it (LOAD-TIMES): the record's first COUNT times */
    private static List<String> times(YardTrackingTrackScreenLogic l, String[] f) {
        long n = l.number("VHYT34D_CRIT", f[3]);
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(l.text(f[2], "TIME-OF-ARRIVAL", i).strip());
        return out;
    }

    private static List<String> values(YardTrackingTrackScreenLogic l, String rec, String field) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : l.rows(rec)) {
            String v = String.valueOf(r.getOrDefault(field, "")).strip();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private State state(YardTrackingTrackScreenLogic l) {
        Map<String, List<String>> times = new LinkedHashMap<>();
        TIMES.forEach((k, v) -> times.put(k, times(l, v)));
        Map<String, String> statuses = new LinkedHashMap<>();
        for (String f : List.of("ALL-STAT", "AM-MAKE-STAT", "AM-BREAK-STAT", "PM-MAKE-STAT", "PM-BREAK-STAT")) statuses.put(f, l.text("VHYT34IW", f));
        List<SingleTrain> single = new ArrayList<>();
        long n = l.number("VHYT34IW-PROCESS", "SINGLE-TRAIN-CNT");
        List<Map<String, Object>> rows = l.rows("VHYT34N_DATA");
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            Map<String, Object> r = rows.get(i);
            single.add(new SingleTrain(s(r, "CON-NO-FROM"), s(r, "TIME-OF-ARRIVAL-FR"), s(r, "BREAK-SCHED"), s(r, "BREAK-CANCEL"), s(r, "MAKE-SCHED"), s(r, "MAKE-CANCEL")));
        }
        return new State(l.text("VHYT34I-PASSVALUE", "YRD-TRK-YRD"), l.text("VHYT34IW", "REV-DATE-MMDDYYYY"), l.text("VHYT34IW", "SCHED-CD"),
                l.text("VHYT34I-MESSAGE", "MESSAGE"), times, statuses, values(l, "VHYT34H_DATA", "CON-NO"), single);
    }

    private static String s(Map<String, Object> r, String f) {
        return String.valueOf(r.getOrDefault(f, "")).strip();
    }

    /** the window as it opens (for another revenue date when {@code date} differs from the screen's own) */
    public State window(String yard, String date, String group) {
        return state(open(yard, date, group));
    }

    /** VHYT34I-OVERRIDES with the chosen times and boxes */
    public State overrides(String yard, String date, Overrides o, String group) {
        YardTrackingTrackScreenLogic l = open(yard, date, group);
        Map<String, String> times = o == null || o.times() == null ? Map.of() : o.times();
        for (var e : times.entrySet()) {
            String[] f = TIMES.get(e.getKey());
            if (f == null) throw new InvalidRequestException("Unknown time " + e.getKey() + ". Known: " + String.join(", ", TIMES.keySet()));
            String t = e.getValue() == null ? "" : e.getValue().strip();
            List<String> list = times(l, f);
            int idx = list.indexOf(t) + 1;
            if (!t.isEmpty() && idx == 0) throw new InvalidRequestException("Time " + t + " is not one of the " + e.getKey() + " times.");
            l.store("VHYT34IW", f[0], t);
            l.store("VHYT34I-TIME-INDEX", f[1], idx);
        }
        Map<String, Boolean> boxes = o == null || o.boxes() == null ? Map.of() : o.boxes();
        for (var e : boxes.entrySet()) {
            String f = BOXES.get(e.getKey());
            if (f == null) throw new InvalidRequestException("Unknown choice " + e.getKey() + ". Known: " + String.join(", ", BOXES.keySet()));
            l.store("VHYT34IW", f, Boolean.TRUE.equals(e.getValue()) ? "1" : "0");
        }
        l.run("VHYT34I-OVERRIDES");
        return state(l);
    }

    /** VHYT34I-TRAIN-SEL, and VHYT34I-SCHED-FOR for an arrival time */
    public TrainTimes train(String yard, String date, String train, String arrival, String group) {
        YardTrackingTrackScreenLogic l = open(yard, date, group);
        select(l, train, arrival);
        return new TrainTimes(l.text("VHYT34IW", "CON-NO"), values(l, "VHYT34J_DATA", "TIME-OF-ARRIVAL"), l.text("VHYT34IW", "SCHEDULED-FOR"));
    }

    private static void select(YardTrackingTrackScreenLogic l, String train, String arrival) {
        String t = train == null ? "" : train.strip();
        if (!values(l, "VHYT34H_DATA", "CON-NO").contains(t)) throw new InvalidRequestException("Train " + t + " is not one of the listed trains.");
        l.store("VHYT34IW", "CON-NO", t);
        l.store("VHYT34J_CRIT", "CON-NO", t);
        l.run("VHYT34I-TRAIN-SEL");
        if (arrival == null || arrival.isBlank()) return;
        List<String> arrivals = values(l, "VHYT34J_DATA", "TIME-OF-ARRIVAL");
        int idx = arrivals.indexOf(arrival.strip()) + 1;
        if (idx == 0) throw new InvalidRequestException("Arrival " + arrival.strip() + " is not one of train " + t + "'s arrival times.");
        l.store("VHYT34IW", "TIME-OF-ARRIVAL", arrival.strip());
        l.store("VHYT34I-TIME-INDEX", "TIME-OF-ARRIVAL-IDX", idx);
        l.run("VHYT34I-SCHED-FOR");
    }

    /** VHYT34I-SINGLEOVER for a train, an arrival and its boxes */
    public State singleOverride(String yard, String date, SingleOverride o, String group) {
        if (o == null) throw new InvalidRequestException("A train and an arrival time are required.");
        YardTrackingTrackScreenLogic l = open(yard, date, group);
        select(l, o.train(), o.arrival());
        l.store("VHYT34IW", "ST-BREAK-CANCEL", o.breakCancel() ? "1" : "0");
        l.store("VHYT34IW", "ST-MAKE-CANCEL", o.makeCancel() ? "1" : "0");
        l.store("VHYT34IW", "ST-BREAK-SCHED", o.breakScheduled() ? "1" : "0");
        l.store("VHYT34IW", "ST-MAKE-SCHED", o.makeScheduled() ? "1" : "0");
        l.run("VHYT34I-SINGLEOVER");
        return state(l);
    }
}
