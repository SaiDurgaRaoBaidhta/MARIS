package com.bart.maris.middleware.domain.prevmaint;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preventive maintenance reports (old criteria screen VHHPM1I and its five reports VHHPR1I, VHHPM2I, VHPMR1I, VHHPD1I,
 * VHHPD2I). Rules from MARIS_RUI/EGLSource/MRSVehiclePrevMaint/RUIFunctions.egl. Read-only except the print request.
 */
public class PrevMaintService {
    private static final String PKG = "MRSVehiclePrevMaint";

    /** the five reports, in the order of the old radio buttons (RPT 1..5) */
    public enum Report { HOURS_BY_YARD, MTBI, BY_PM_TYPE, HOURS_DESCENDING, HOURS_TO_NEXT }

    private final Programs programs;
    private final List<String> homeYards, locations, pmTypes, reportNames;
    private final String noYardOrLoc, noYard, noLoc, badPmCriteria, submitted;

    public PrevMaintService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        this.homeYards = t.items("VHHPM1I", "Drop-down_List1");
        this.locations = t.items("VHHPM1I", "Drop-down_List11");
        this.pmTypes = t.items("VHHPM1I", "Drop-down_List2");
        this.reportNames = t.items("VHHPM1I", "Radio_Button_Set1");
        this.noYardOrLoc = t.triggeredPrompt("VHHPM1I", "NO-HOME-YRD");
        this.noYard = t.triggeredPrompt("VHHPM1I", "NO-YARD");
        this.noLoc = t.triggeredPrompt("VHHPM1I", "NO-LOC");
        this.badPmCriteria = t.triggeredPrompt("VHHPM1I", "NO-VALID-CRITERIA");
        this.submitted = t.triggeredPrompt("VHHPM1I", "SUBMIT");
    }

    // ---- options -------------------------------------------------------------------------------------------

    public record Printer(String description, String vtam) {}

    public record Options(String revenueDate, String fromDate, List<String> reports, List<String> homeYards, List<String> locations,
                          List<String> pmTypes, List<Printer> printers) {}

    private record Dates(String revenueDate, String fromDate) {}

    /** VHHPM1I-REV-DATE: the revenue date (today, or yesterday before the revenue day starts) and the report start date */
    private Dates dates() {
        Map<String, Object> d = programs.run("VHHPMA3", Map.of()).record("VHHPMA3_DATA");
        String rev = text(d, "currentTime").compareTo(text(d, "timeRevStart")) >= 0 ? text(d, "currentDate") : text(d, "yesterday");
        String from = text(programs.run("VHHPMA2", Map.of()).record("VHHPMA2_DATA"), "fromDate");
        return new Dates(rev, from);
    }

    public Options options() {
        Dates d = dates();
        List<Printer> printers = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("WS70A01", Map.of()), "WS70A01_DATA", "dataRow")) {
            printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        }
        return new Options(d.revenueDate(), d.fromDate(), reportNames, homeYards, locations, pmTypes, printers);
    }

    // ---- the reports ---------------------------------------------------------------------------------------

    /** one car of a report; fields a report does not have are left out */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PmCar(String car, String status, String location, String homeYard, Long hoursSinceLastPm, String nextPm,
                        String trainIncident, Long mtbi, Long mtbiHours, Long mtbiIncidents, Long hoursToNextPm, String lastPm,
                        Long hoursAtLastPm, String lastPmLocation, Long cumulativeHours, Long cumulativeMinutes, String due) {
        PmCar(String car, String status, String location, String homeYard, Long hoursSinceLastPm, String nextPm, String trainIncident, Long mtbi,
              Long mtbiHours, Long mtbiIncidents, Long hoursToNextPm, String lastPm, Long hoursAtLastPm, String lastPmLocation,
              Long cumulativeHours, Long cumulativeMinutes) {
            this(car, status, location, homeYard, hoursSinceLastPm, nextPm, trainIncident, mtbi, mtbiHours, mtbiIncidents, hoursToNextPm, lastPm,
                    hoursAtLastPm, lastPmLocation, cumulativeHours, cumulativeMinutes, null);
        }
    }

    /** MTBI per shop (report 2): hours, incidents, cars, and the average (hours / incidents) */
    public record Mtbi(String shop, long hours, long incidents, long cars, BigDecimal average) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PmReport(Report report, String name, String revenueDate, String fromDate, int count, List<PmCar> cars, List<Mtbi> mtbi) {}

    /** criteria of the PM-type report (3) and of the hard copy */
    public record PmCriteria(String homeYard, String location, String pmType, String carFrom, String carTo, Integer hoursFrom, Integer hoursTo) {}

    public PmReport report(Report report, PmCriteria c) {
        String yard = upper(c.homeYard()), loc = upper(c.location());
        // VHHPM1IS-DISPLAY: a home yard (reports 1-4) or a current location (report 5) is needed
        if (report == Report.HOURS_TO_NEXT ? !locations.contains(loc) : !homeYards.contains(yard)) throw new InvalidRequestException(noYardOrLoc);
        Dates d = dates();
        String name = reportNames.get(report.ordinal());
        return switch (report) {
            case HOURS_BY_YARD -> hoursByYard(yard, d, name);
            case MTBI -> mtbi(yard, d, name);
            case BY_PM_TYPE -> byPmType(yard, c, d, name);
            case HOURS_DESCENDING -> hoursDescending(yard, d, name);
            case HOURS_TO_NEXT -> hoursToNext(loc, d, name);
        };
    }

    private static Map<String, Object> yardRange(String yard, Dates d) {
        Map<String, Object> crit = new LinkedHashMap<>();
        boolean all = yard.equals("ALL");
        crit.put("homeYrdLow", all ? "CY" : yard);
        crit.put("homeYrdHigh", all ? "RY" : yard);
        crit.put("dateFrom", d.fromDate());
        crit.put("dateTo", d.revenueDate());
        return crit;
    }

    /** where the car is: its consist when on a train; blank when it is in the yard asked for (not ALL) */
    private static String location(Map<String, Object> r, String yard) {
        String loc = text(r, "whereIsCar").equals("CON") ? text(r, "conNo") : text(r, "currLoc");
        return !yard.equals("ALL") && loc.equals(yard) ? "" : loc;
    }

    /** VHHPR1IS-HOME-YARD + VHHPR1IS-GET-DATA (program VHHPRA1) */
    private PmReport hoursByYard(String yard, Dates d, String name) {
        ProgramResult r = programs.run("VHHPRA1", Map.of("VHHPRA1_CRIT", yardRange(yard, d)));
        List<PmCar> cars = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHHPRA1_DATA", "dataRow")) {
                cars.add(new PmCar(text(row, "carNo"), text(row, "vehStat"), location(row, yard), text(row, "homeYrd"),
                        number(row, "hrsFromLastPm"), text(row, "nextSchdPmNo"), text(row, "trainIncdInd").equals("Y") ? "TI" : "",
                        null, null, null, null, null, null, null, null, null));
            }
        }
        return new PmReport(Report.HOURS_BY_YARD, name, d.revenueDate(), d.fromDate(), cars.size(), cars, null);
    }

    /** VHHPM2IS-HOME-YARD + VHHPM2IS-GET-DATA + VHHPM2IS-CALCULATE (program VHHPMA1) */
    private PmReport mtbi(String yard, Dates d, String name) {
        Map<String, Object> crit = yardRange(yard, d);
        ProgramResult r = programs.run("VHHPMA1", Map.of("VHHPMA1_CRIT", crit));
        List<PmCar> cars = new ArrayList<>();
        Map<String, long[]> byYard = new LinkedHashMap<>();
        long[] all = new long[3];
        if (r.control().returnCode() != 100) {
            boolean inRange = true;                        // VHHPM2IS-CALCULATE stops at the first car outside the yard range
            for (Map<String, Object> row : rows(r, "VHHPMA1_DATA", "dataRow")) {
                String home = text(row, "homeYrd");
                if (home.compareTo((String) crit.get("homeYrdLow")) < 0 || home.compareTo((String) crit.get("homeYrdHigh")) > 0) inRange = false;
                String loc = text(row, "whereIsCar").equals("CON") ? text(row, "conNo") : text(row, "currLoc");
                if (inRange && loc.equals(home)) loc = "";
                cars.add(new PmCar(text(row, "carNo"), text(row, "vehStat"), loc, home, number(row, "hrsFromLastPm"), text(row, "nextSchdPmNo"),
                        null, number(row, "mtbi"), number(row, "mtbiHrs"), number(row, "mtbiIncds"), null, null, null, null, null, null));
                if (!inRange) continue;
                long[] y = byYard.computeIfAbsent(home, k -> new long[3]);
                y[0] += number(row, "mtbiHrs"); y[1] += number(row, "mtbiIncds"); y[2]++;
                all[0] += number(row, "mtbiHrs"); all[1] += number(row, "mtbiIncds"); all[2]++;
            }
        }
        List<Mtbi> summary = new ArrayList<>();
        byYard.forEach((y, v) -> summary.add(new Mtbi(y, v[0], v[1], v[2], average(v[0], v[1]))));
        if (yard.equals("ALL")) summary.add(new Mtbi("ALL", all[0], all[1], all[2], average(all[0], all[1])));
        return new PmReport(Report.MTBI, name, d.revenueDate(), d.fromDate(), cars.size(), cars, summary);
    }

    private static BigDecimal average(long hours, long incidents) {
        return incidents > 0 ? BigDecimal.valueOf(hours).divide(BigDecimal.valueOf(incidents), 2, RoundingMode.DOWN) : BigDecimal.ZERO;
    }

    /** VHPMRIS-BEGIN + VHPMR1IS-GET-DATA (program VHPMRA1) */
    private PmReport byPmType(String yard, PmCriteria c, Dates d, String name) {
        String type = upper(c.pmType());
        if (type.isEmpty()) type = "ALL";
        String from = blankOr(c.carFrom()), to = blankOr(c.carTo());
        Map<String, Object> crit = new LinkedHashMap<>();
        if (from.equals("ALL")) { from = "0100"; to = "9999"; }
        if (from.isEmpty()) from = "0100";
        if (to.isEmpty()) to = "9999";
        crit.put("wsCarFrom", from);
        crit.put("wsCarTo", to);
        if (type.equals("ALL")) {
            crit.put("wsSchdPmFrom", "1");
            crit.put("wsSchdPmTo", "6");
            crit.put("pmType", type);
        } else {
            crit.put("schdPmType", type);
            crit.put("wsSchdPmFrom", type);
            crit.put("wsSchdPmTo", type);
        }
        crit.put("wsLastPmHrsFrom", c.hoursFrom() == null ? 0 : c.hoursFrom());
        crit.put("wsLastPmHrsTo", c.hoursTo() == null || c.hoursTo() == 0 ? 9999 : c.hoursTo());
        boolean all = yard.equals("ALL");
        crit.put("homeYrdLow", all ? "CY" : yard);
        crit.put("homeYrdHigh", all ? "RY" : yard);
        ProgramResult r = programs.run("VHPMRA1", Map.of("VHPMRA1_CRIT", crit));
        List<PmCar> cars = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHPMRA1_DATA", "dataRow")) {
                String next = text(row, "currSchdPmNo").isEmpty() ? text(row, "nextSchdPmNo") : text(row, "currSchdPmNo");
                cars.add(new PmCar(text(row, "carNo"), text(row, "vehStat"), location(row, yard), text(row, "homeYrd"),
                        number(row, "hrsFromLastPm"), next, text(row, "trainIncdInd"), null, null, null, number(row, "hrsToNextPm"),
                        text(row, "lastSchdPmNo"), number(row, "hrsAtLastPm"), text(row, "lastPmLoc"), number(row, "cumHrs"), number(row, "cumMin")));
            }
        }
        return new PmReport(Report.BY_PM_TYPE, name, d.revenueDate(), d.fromDate(), cars.size(), cars, null);
    }

    /** VHHPD1IS-HOME-YARD + VHHPD1IS-GET-DATA (program VHHPDA1), with the MTBI totals */
    private PmReport hoursDescending(String yard, Dates d, String name) {
        ProgramResult r = programs.run("VHHPDA1", Map.of("VHHPDA1_CRIT", yardRange(yard, d)));
        List<PmCar> cars = new ArrayList<>();
        long hours = 0, incidents = 0;
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHHPDA1_DATA", "dataRow")) {
                hours += number(row, "mtbiHrs");
                incidents += number(row, "mtbiIncds");
                cars.add(new PmCar(text(row, "carNo"), text(row, "vehStat"), location(row, yard), text(row, "homeYrd"),
                        number(row, "hrsFromLastPm"), text(row, "nextSchdPmNo"), null, number(row, "mtbi"), number(row, "mtbiHrs"),
                        number(row, "mtbiIncds"), null, null, null, null, null, null));
            }
        }
        List<Mtbi> total = List.of(new Mtbi(yard, hours, incidents, cars.size(), average(hours, incidents)));
        return new PmReport(Report.HOURS_DESCENDING, name, d.revenueDate(), d.fromDate(), cars.size(), cars, total);
    }

    /** VHHPD2IS-HOME-YARD + VHHPD2IS-GET-DATA (program VHHPD2A): by current location */
    private PmReport hoursToNext(String loc, Dates d, String name) {
        Map<String, Object> crit = new LinkedHashMap<>();
        boolean all = loc.equals("ALL");
        crit.put("currLocLow", all ? "CY" : loc);
        crit.put("currLocHigh", all ? "RY" : loc);
        crit.put("dateFrom", d.fromDate());
        crit.put("dateTo", d.revenueDate());
        ProgramResult r = programs.run("VHHPD2A", Map.of("VHHPD2A_CRIT", crit));
        List<PmCar> cars = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHHPD2A_DATA", "dataRow")) {
                String l = text(row, "whereIsCar").equals("CON") ? text(row, "conNo") : text(row, "currLoc");
                cars.add(new PmCar(text(row, "carNo"), text(row, "vehStat"), l, text(row, "homeYrd"), number(row, "hrsFromLastPm"),
                        text(row, "nextSchdPmNo"), null, number(row, "mtbi"), number(row, "mtbiHrs"), number(row, "mtbiIncds"),
                        number(row, "hrsToNextPm"), null, null, null, null, null, text(row, "ind")));   // ODue / Due
            }
        }
        return new PmReport(Report.HOURS_TO_NEXT, name, d.revenueDate(), d.fromDate(), cars.size(), cars, null);
    }

    // ---- hard copy -----------------------------------------------------------------------------------------

    /** VHHPMIS-SUBMIT (the checks) + VHHPM1IS-SUBMIT-X (program VHHPM01) */
    public String print(Report report, PmCriteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String yard = upper(c.homeYard());
        int rpt = report.ordinal() + 1;
        String pmType = upper(c.pmType()), from = blankOr(c.carFrom()), to = blankOr(c.carTo());
        int hf = c.hoursFrom() == null ? 0 : c.hoursFrom(), ht = c.hoursTo() == null ? 0 : c.hoursTo();
        if (report == Report.BY_PM_TYPE) {
            boolean allCars = from.equals("ALL") && to.isEmpty(), carRange = from.matches("\\d+") && to.compareTo(from) > 0;
            boolean noHours = hf == 0 && ht == 0, hourRange = hf > 0 && ht > hf;
            if (pmType.isEmpty() || !((allCars || carRange) && (noHours || hourRange))) throw new InvalidRequestException(badPmCriteria);
        } else if (report == Report.HOURS_TO_NEXT) {
            String loc = upper(c.location());
            if (loc.isEmpty()) throw new InvalidRequestException(noLoc);
            yard = loc;
        } else if (yard.isEmpty()) {
            throw new InvalidRequestException(noYard);
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", yard);
        crit.put("reptId", String.valueOf(rpt));
        crit.put("pmType", pmType);
        crit.put("carNoFrom", from);
        crit.put("carNoTo", to);
        crit.put("pmHoursFrom", String.format("%04d", hf));          // num(4) moved to char(4): zero-padded
        crit.put("pmHoursTo", String.format("%04d", ht));
        crit.put("reptType", printer.strip());
        programs.run("VHHPM01", Map.of("VHHPM01_CRIT", crit));
        return submitted;
    }

    private static String upper(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }

    private static String blankOr(String s) {
        return upper(s);
    }
}
