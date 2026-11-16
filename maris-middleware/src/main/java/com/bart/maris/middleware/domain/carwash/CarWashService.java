package com.bart.maris.middleware.domain.carwash;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Car wash reports (old screens VHCWR1I washes in a date range, VHCWR2I wash over due). Rules from
 * MARIS_RUI/EGLSource/MRSVehicleCarWash/RUIFunctions.egl: VHCWR1IS-RET-DATA (program VHCWR1A) and
 * VHCWR2IS-RET-DATA (program VHCWR2A). Read-only. The old Print buttons only checked the input in the web version
 * (nothing was connected to their SUBMIT trigger), so there is no print here.
 */
public class CarWashService {
    private static final String PKG = "MRSVehicleCarWash";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    private final Programs programs;
    private final Clock clock;
    private final List<String> washYards, overdueYards;
    private final int washMax, overdueMax;
    private final String badYard, badFrom, fromFuture, badTo, toFuture, toBeforeFrom, noCars, washNote, overdueBadYard, overdueNoCars, overdueNote;

    public CarWashService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.washYards = t.compared("VHCWR1IS-RET-DATA", PKG, "YARD");
        this.overdueYards = t.compared("VHCWR2IS-RET-DATA", PKG, "HOME-YRD");
        this.washMax = Integer.parseInt(t.compared("VHCWR1IS-RET-DATA", PKG, "CAR-COUNT").get(0));
        this.overdueMax = Integer.parseInt(t.compared("VHCWR2IS-RET-DATA", PKG, "CAR-COUNT").get(0));
        this.badYard = t.triggeredPrompt("VHCWR1I", "INVALID-YARD");
        this.badFrom = t.triggeredPrompt("VHCWR1I", "INVALID-FROM-DATE");
        this.fromFuture = t.triggeredPrompt("VHCWR1I", "FROM-GR-SYS");
        this.badTo = t.triggeredPrompt("VHCWR1I", "INVALID-TO-DATE");
        this.toFuture = t.triggeredPrompt("VHCWR1I", "TO-GR-SYS");
        this.toBeforeFrom = t.triggeredPrompt("VHCWR1I", "FROM-LESS-TO");
        this.noCars = t.triggeredPrompt("VHCWR1I", "NO-CARS-FOUND");
        this.washNote = t.label("VHCWR1I", "lblFooter");
        this.overdueBadYard = t.triggeredPrompt("VHCWR2I", "INVALID-YARD");
        this.overdueNoCars = t.triggeredPrompt("VHCWR2I", "NO-CARS-FOUND");
        this.overdueNote = t.label("VHCWR2I", "lblFooter");
    }

    public record Wash(String car, String date, String time) {}

    /** @param note the old footer when the maximum number of rows came back; @param message when no car was found */
    public record Washes(String yard, String from, String to, int count, boolean maxRowsReached, String note, List<Wash> washes, String message) {}

    public record OverdueCar(String car, String lastWashDate, String lastWashTime, String location, long daysOverdue, long totalWashes) {}

    public record Overdue(String homeYard, int count, boolean maxRowsReached, String note, List<OverdueCar> cars, String message) {}

    /** VHCWR1IS-RET-DATA: cars washed in a yard between two dates (yyyy-MM-dd), checks in the old order */
    public Washes washes(String yard, String from, String to) {
        String y = upper(yard);
        if (!washYards.contains(y)) throw new InvalidRequestException(badYard);
        LocalDate today = LocalDate.now(clock);
        LocalDate f = date(from, badFrom);
        if (f.isAfter(today)) throw new InvalidRequestException(fromFuture);
        LocalDate t = date(to, badTo);
        if (t.isAfter(today)) throw new InvalidRequestException(toFuture);
        if (t.isBefore(f)) throw new InvalidRequestException(toBeforeFrom);

        ProgramResult r = programs.run("VHCWR1A", Map.of("VHCWR1A_CRIT", Map.of("yard", y, "fromDate", MDY.format(f), "toDate", MDY.format(t))));
        List<Wash> out = new ArrayList<>();
        int rowCount = 0;
        if (r.control().returnCode() != 100) {
            List<Map<String, Object>> rows = rows(r, "VHCWR1A_DATA", "dataRow");
            rowCount = r.control().rowCount();
            for (Map<String, Object> row : rows) {                      // two cars per row in the old list
                out.add(new Wash(text(row, "carNo"), text(row, "washedDate"), text(row, "washedTime")));
                if (!text(row, "carNo1").isEmpty()) out.add(new Wash(text(row, "carNo1"), text(row, "washedDate1"), text(row, "washedTime1")));
            }
        }
        boolean max = rowCount == washMax;
        return new Washes(y, ISO.format(f), ISO.format(t), out.size(), max, max ? washNote : null, out,
                r.control().returnCode() == 100 ? noCars : null);
    }

    /** VHCWR2IS-RET-DATA: cars of a home yard whose wash is over due */
    public Overdue overdue(String homeYard) {
        String y = upper(homeYard);
        if (!overdueYards.contains(y)) throw new InvalidRequestException(overdueBadYard);
        ProgramResult r = programs.run("VHCWR2A", Map.of("VHCWR2A_CRIT", Map.of("homeYrd", y)));
        List<OverdueCar> out = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHCWR2A_DATA", "dataRow")) {
                out.add(new OverdueCar(text(row, "carNo"), text(row, "dateLastWash"), text(row, "timeLastWash"), text(row, "currLoc"),
                        number(row, "daysOverdue"), number(row, "totalWashes")));
            }
        }
        boolean max = r.control().returnCode() != 100 && r.control().rowCount() == overdueMax;
        return new Overdue(y, out.size(), max, max ? overdueNote : null, out, r.control().returnCode() == 100 ? overdueNoCars : null);
    }

    private static String upper(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }

    private static LocalDate date(String iso, String message) {
        try {
            return LocalDate.parse(iso == null ? "" : iso.strip(), ISO);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(message);
        }
    }
}
