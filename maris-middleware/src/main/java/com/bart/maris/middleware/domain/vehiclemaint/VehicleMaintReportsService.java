package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vehicle maintenance, read-only screens: an incident's narrative (old VHNAR02I), the vehicle event report (VHVM12I) and
 * the car event report (VHVM13i). Rules from MARIS_RUI/EGLSource/MRSVehicleMaintenance/RUIFunctions.egl.
 * The two reports are print requests (programs VHVM12A, VHVM13A).
 */
public class VehicleMaintReportsService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final Programs programs;
    private final List<String> carTypes, homeYards;
    private final String eventsSubmitted, carEventsSubmitted, missingCarType, missingDate, missingRef, missingComments;

    public VehicleMaintReportsService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        this.carTypes = t.items("VHVM13i", "Drop-down_List1");
        this.homeYards = t.items("VHVM13i", "Drop-down_List4");
        this.eventsSubmitted = t.prompt("VHVM12I", "Message_Prompter1");
        this.carEventsSubmitted = t.triggeredPrompt("VHVM13i", "VALID");
        this.missingCarType = t.triggeredPrompt("VHVM13i", "MISSING-CAR-TYPE");
        this.missingDate = t.triggeredPrompt("VHVM13i", "MISSING-DATE");
        this.missingRef = t.triggeredPrompt("VHVM13i", "MISSING-REF-NO");
        this.missingComments = t.triggeredPrompt("VHVM13i", "MISSING-COMMENTS");
    }

    // ---- VHNAR02I ------------------------------------------------------------------------------------------

    public record Narrative(String incident, List<String> lines) {}

    /** VHNAR02IS-RETNARR: an incident's narrative lines (program VHNARX) */
    public Narrative narrative(String incident) {
        String i = incident == null ? "" : incident.strip();
        if (i.isEmpty() || i.length() > 8) throw new InvalidRequestException("Enter an incident number of up to 8 characters.");
        ProgramResult r = programs.run("VHNARX", Map.of("VHNARX_DATA", Map.of("critIncdNo", i)));
        List<String> lines = new ArrayList<>();
        if (r.control().rowCount() > 0) for (Map<String, Object> row : rows(r, "VHNARX_DATA", "dataRow")) lines.add(text(row, "narr"));
        return new Narrative(i, lines);
    }

    // ---- printers and choices ------------------------------------------------------------------------------

    public record Printer(String description, String vtam) {}

    public record ReportOptions(List<Printer> printers, List<String> carTypes, List<String> homeYards, List<String> eventTypes) {}

    /**
     * VHVM13I-BEGIN: printers (WS70A01) and the event types its drop-down shows (VHVM13B rows; when VHVM13B fails the
     * old list stays empty). VHVM13IS-INIT's VHVM13C list feeds no widget, so it is not called.
     */
    public ReportOptions options() {
        List<Printer> printers = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("WS70A01", Map.of()), "WS70A01_DATA", "dataRow")) {
            printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        }
        List<String> events = new ArrayList<>();
        Programs.Outcome e = programs.attempt("VHVM13B", Map.of());
        if (e.ok()) for (Map<String, Object> r : rows(e.result(), "VHVM13B_DATA", "dataRow")) events.add(text(r, "eventType"));
        return new ReportOptions(printers, carTypes, homeYards, events);
    }

    // ---- VHVM12I: the vehicle event report -----------------------------------------------------------------

    public record EventCriteria(String dateFrom, String dateTo, String car, String carType, String homeYard, String eventType,
                                String eventSubType, String eventStatus, String location, String trackingRefNo, String sortOrder) {}

    /** VHVM12IS-SUBMIT (program VHVM12A); the dates are yyyy-MM-dd */
    public String printEvents(EventCriteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateFrom", isoOrBlank(c.dateFrom()));
        crit.put("dateTo", isoOrBlank(c.dateTo()));
        crit.put("carNo", up(c.car()));
        crit.put("begCarType", up(c.carType()));
        crit.put("endCarType", up(c.carType()));
        crit.put("homeYrd", up(c.homeYard()));
        crit.put("eventType", up(c.eventType()));
        crit.put("eventSubType", up(c.eventSubType()));
        crit.put("eventStat", up(c.eventStatus()));
        crit.put("currLoc", up(c.location()));
        crit.put("trkgRefNo", up(c.trackingRefNo()));
        crit.put("sortOrder", up(c.sortOrder()));
        crit.put("vtamName", printer.strip());
        programs.run("VHVM12A", Map.of("VHVM12A_CRIT", crit));
        return eventsSubmitted;
    }

    // ---- VHVM13i: the car event report ---------------------------------------------------------------------

    public record CarEventCriteria(String carType, String date, String eventType, String eventStatus, String trackingRefNo, String comments,
                                   String homeYard) {}

    /** VHVM13IS-SUBMIT (program VHVM13A): car type, date, tracking number and comments are needed, as on the old screen */
    public String printCarEvents(CarEventCriteria c, String printer) {
        List<String> missing = new ArrayList<>();
        if (up(c.carType()).isEmpty()) missing.add(missingCarType);
        if (blank(c.date())) missing.add(missingDate);
        if (up(c.trackingRefNo()).isEmpty()) missing.add(missingRef);
        if (blank(c.comments())) missing.add(missingComments);
        if (!missing.isEmpty()) throw new InvalidRequestException(String.join("\n", missing));
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String date = iso(c.date());
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateFrom", date);
        crit.put("carType", up(c.carType()));
        crit.put("eventType", up(c.eventType()));
        crit.put("eventStat", up(c.eventStatus()));
        crit.put("trkgRefNo", up(c.trackingRefNo()));
        crit.put("comments", c.comments().strip());
        crit.put("vtamName", printer.strip());
        crit.put("homeYrd", up(c.homeYard()));
        programs.run("VHVM13A", Map.of("VHVM13A_CRIT", crit));
        return carEventsSubmitted;
    }

    private String iso(String d) {
        try {
            return ISO.format(LocalDate.parse(d.strip(), ISO));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(missingDate);
        }
    }

    private String isoOrBlank(String d) {
        return blank(d) ? "" : iso(d);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String up(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }
}
