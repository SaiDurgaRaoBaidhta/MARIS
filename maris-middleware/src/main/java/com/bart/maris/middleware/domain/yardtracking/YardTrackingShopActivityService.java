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
 * Shop activity for a day (old VHYT31I: yards VHYT31A, yard name VHYT31H, the working date VHYT31E, the cars and counts
 * VHYT31B) and the prints that go with it: VHYT23I (the shop activity report, VHYT23A, opened by VHYT31I's Print) and
 * VHYT22I (a car's history report, VHYT22A, opened by VHSV06I). The screens' own statement groups run on their records
 * ({@link YardTrackingTrackScreenLogic}); printers come from WS70A01. VHYT31B and VHYT31E write, so List is a POST.
 */
public class YardTrackingShopActivityService {
    static final String ACTIVITY = "VHYT31I", REPORT = "VHYT23I", CAR_REPORT = "VHYT22I";

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;

    public YardTrackingShopActivityService(LegacyProgramService legacy, ScreenBundle screens, Clock clock) {
        this.screens = screens;
        this.legacy = legacy;
        this.clock = clock;
    }

    public record Yard(String code, String name) {}

    /** what VHYT31I shows when it opens: the yards, the yard chosen, its name and today's date as the old screen writes it */
    public record Options(List<Yard> yards, String yard, String yardName, String date, String message) {}

    public record Car(String car, String availabilityDate, String availabilityTime, String status, String track, long position,
                      String inShopDate, String inShopTime, String outShopDate, String outShopTime, String moveReason) {}

    public record Totals(long arrivals, long departures, long releases, long remaining, long production, long productionGainLoss,
                         long availabilityGainLoss, long available0400, long remaining3) {}

    public record Activity(String yard, String date, List<Car> cars, Totals totals, String message, boolean printable) {}

    private YardTrackingTrackScreenLogic open(String yard) {
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, ACTIVITY, legacy, clock);
        l.store("VHYT31IW-PASSVALUE", "YRD-TRKG-YRD", yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT));
        l.run("VHYT31IS-INIT");
        return l;
    }

    private static List<Yard> yards(YardTrackingTrackScreenLogic l) {
        List<Yard> out = new ArrayList<>();
        for (Map<String, Object> r : l.rows("VHYT31A_DATA")) {                 // DATA_ROW's one field is the YARD group
            Map<?, ?> yard = r.get("YARD") instanceof Map<?, ?> m ? m : Map.of();
            String y = String.valueOf(yard.get("yrd") == null ? "" : yard.get("yrd")).strip();
            if (!y.isEmpty()) out.add(new Yard(y, String.valueOf(yard.get("yrdDesc") == null ? "" : yard.get("yrdDesc")).strip()));
        }
        return out;
    }

    /** VHYT31IS-INIT, for the yard the opening screen passes (blank: the first yard VHYT31A lists) */
    public Options options(String yard) {
        YardTrackingTrackScreenLogic l = open(yard);
        return new Options(yards(l), l.text("VHYT31IW", "YRD-TRKG-YRD"), l.text("VHYT31IW", "YRD-DESC"), l.text("VHYT31IW", "WS-DATEX"), "");
    }

    /** VHYT31IS-LIST2 for a yard (a VHYT31A yard, or ALL) and a date (MM-DD-YYYY or YYYY-MM-DD, as typed) */
    public Activity list(String yard, String date) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);
        if (y.isEmpty()) throw new InvalidRequestException("Choose a yard.");
        YardTrackingTrackScreenLogic l = open("");
        Yard chosen = yards(l).stream().filter(x -> x.code().equals(y)).findFirst()
                .orElseThrow(() -> new InvalidRequestException("Unknown yard " + y + ". Choose one of the listed yards."));
        // the drop-down's selected item is the whole YARD group (code and name)
        l.store("VHYT31IW", "YRD-TRKG-YRD", chosen.code());
        l.store("VHYT31IW", "YRD-DESC", chosen.name());
        if (date != null && !date.isBlank()) l.store("VHYT31IW", "WS-DATEX", date.strip());
        l.store("VHYT31IW-TRIGGER", "DISABLE-PRINT", "");
        l.store("VHYT31IW-TRIGGER", "ENABLE-PRINT", "");
        l.run("VHYT31IS-LIST2");
        List<Car> cars = new ArrayList<>();
        long n = l.number("VHYT31IW-CNT", "ROW-CNT");
        List<Map<String, Object>> rows = l.rows("VHYT31B_DATA");
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            Map<String, Object> r = rows.get(i);
            // the group fields as the table shows them: the group's whole text (sub-fields at full width)
            cars.add(new Car(l.text("VHYT31B_DATA", "CAR-INFO", i + 1).strip(), s(r, "DATE-AVAILABILITY"), s(r, "TIME-AVAILABILITY"), s(r, "VEH-STAT"), s(r, "YRD-SUB-LOC"),
                    r.get("POSN-NO") instanceof Number num ? num.longValue() : 0, s(r, "DATE-IN-SHOP"), s(r, "TIME-IN-SHOP"),
                    s(r, "DATE-OUT-SHOP"), s(r, "TIME-OUT-SHOP"), l.text("VHYT31B_DATA", "MOVE-REASON", i + 1).strip()));
        }
        Totals t = new Totals(l.number("VHYT31IW-TOT", "ARRIVAL-CNT"), l.number("VHYT31IW-TOT", "DEPART-CNT"), l.number("VHYT31IW-TOT", "RELEASE-CNT"),
                l.number("VHYT31IW-TOT", "REMAIN-CNT"), l.number("VHYT31IW-TOT", "PRODUCT-CNT"), l.number("VHYT31IW-TOT", "PROD-GAIN-LOSS-CNT"),
                l.number("VHYT31IW-TOT", "AVAIL-GAIN-LOSS-CNT"), l.number("VHYT31IW-TOT", "A400-CNT"), l.number("VHYT31IW-TOT", "REMAIN-CNT3"));
        // Print is on unless the list disabled it (no data); ALL disables it and offers the report only (PRINT-REPORT-SW)
        boolean printable = l.text("VHYT31IW-TRIGGER", "DISABLE-PRINT").isBlank() || !l.text("VHYT31IW-TRIGGER", "PRINT-REPORT-SW").isBlank();
        return new Activity(chosen.code(), l.text("VHYT31IW", "WS-DATEX"), cars, t, l.text("VHYT31IW-MSG", "FINAL-MSG"), printable);
    }

    private static String s(Map<String, Object> r, String f) {
        return String.valueOf(r.getOrDefault(f, "")).strip();
    }

    public record Printer(int index, String name, String description) {}

    /** the printers a print window lists (WS70A01) */
    public List<Printer> printers() {
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, REPORT, legacy, clock);
        l.run("VHYT01IS-INIT-X");
        List<Printer> out = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> r : l.rows("WS70A01_DATA")) {
            i++;
            String name = String.valueOf(r.getOrDefault("VTAM-NAME", "")).strip();
            if (!name.isEmpty()) out.add(new Printer(i, name, (String.valueOf(r.getOrDefault("PRTR-NAME", "")).strip() + " " + String.valueOf(r.getOrDefault("PRTR-LOC-DESC", "")).strip()).strip()));
        }
        return out;
    }

    /** VHYT23IS-SUBMIT: the shop activity report for a yard and date on a printer (WS70A01 row, 1-based) */
    public String printActivity(String yard, String date, int printer) {
        return print(REPORT, printer, Map.of("VHYT23IW-PASSVALUE", Map.of("YRD-TRKG-YRD", up(yard), "REPORT-DATE", date == null ? "" : date.strip())),
                "VHYT23IS-SUBMIT", "VHYT23A_CRIT");
    }

    /** VHYT24IS-SUBMIT (VHYT22I's Hard Copy): a car's history report on a printer */
    public String printCarHistory(String car, int printer) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty()) throw new InvalidRequestException("A car number is required.");
        return print(CAR_REPORT, printer, Map.of("VHYT22IW-PASSVALUE", Map.of("CAR-NO", c)), "VHYT24IS-SUBMIT", "VHYT22A_CRIT");
    }

    private static String up(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }

    /** opens the print window (its printers), picks the printer like a click on its row, sets what its opener passes, submits */
    private String print(String screen, int printer, Map<String, Map<String, String>> passed, String submit, String crit) {
        YardTrackingTrackScreenLogic l = new YardTrackingTrackScreenLogic(screens, screen, legacy, clock);
        l.run("VHYT01IS-INIT-X");
        int printers = l.rows("WS70A01_DATA").size();
        if (printer < 1 || printer > printers) throw new InvalidRequestException("Choose one of the listed printers.");
        l.store("VHDVS4IW-PASSVALUE", "PRT-IDX", printer);
        l.run("VHDVS4IS-HARD-COPY");
        passed.forEach((rec, fields) -> fields.forEach((f, v) -> l.store(rec, f, v)));
        l.run(submit);
        Map<String, Object> sent = new LinkedHashMap<>(l.get(crit));
        return String.valueOf(sent.getOrDefault("reptType", "")).strip();
    }
}
