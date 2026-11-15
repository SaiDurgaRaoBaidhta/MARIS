package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The criteria screens of the home yard status (VHYS01I) and yard status (VHYS06I) reports: their choices, the checks
 * of Display (statement groups CHK-SEL then MOVE-VAL) and of Print (CHK-SEL-X then SUBMIT). Both screens have the same
 * checks in the same order; each one's messages, titles and start values are read from its own statement groups.
 */
public final class VehicleQueriesStatusCriteria {
    private static final String PKG = "MRSVehicleQueries";

    /** one of the two criteria screens: its statement group prefix and its programs */
    public record Family(String screen, String prefix, String yardsProgram, String statusProgram, String printProgram, String narrWidget) {
        public static final Family HOME_YARD = new Family("VHYS01I", "VHYS01IS", "VHYS01A", "VHYS01B", "VHYS01C", "ddlNarrBase");
        public static final Family YARD = new Family("VHYS06I", "VHYS06IS", "VHYS06A", "VHYS06B", "VHYS06C", "ddlNarrBase");
    }

    /** the screens' four report options, in their order (REPORT-OPTION / REPT-ID 1..4) */
    public enum Option { BASE, STATUS, NOGO, SUMMARY;
        public int number() { return ordinal() + 1; }
        public static Option of(String s) {
            if (s == null || s.isBlank()) return null;
            try { return valueOf(s.strip().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        }
    }

    /** what the user chose: the option, its yard, status and narrative choice, and the car range */
    public record Criteria(String option, String yard, String status, String narr, String carFrom, String carTo) {
        String y() { return yard == null ? "" : yard.strip(); }
        String s() { return status == null ? "" : status.strip(); }
        String n() { return narr == null ? "" : narr.strip(); }
    }

    /** checked criteria, as the criteria screen passes them to its report window */
    public record Checked(Option option, String yard, String status, String narr, String carFrom, String carTo, String title) {}

    public record Status(String status, String description) {}

    public record Printer(String description, String vtam) {}

    public record Options(List<String> yards, List<Status> statuses, List<String> narrChoices, String narr, String carFrom, String carTo,
                          List<String> titles, List<Printer> printers) {}

    private final Programs programs;
    private final Family family;
    /** CHK-SEL's messages in source order (see {@link #check}); CHK-SEL-X's (see {@link #print}) */
    private final List<String> m, p;
    private final List<String> titles, carMessages, narrChoices;
    private final String carFrom, carTo, narrStart, carsBelow, submitted;
    /** SUBMIT also takes "0000" as no from car (VHYS01I); VHYS06I only a blank one */
    private final boolean printZeroFromIsBlank;

    public VehicleQueriesStatusCriteria(Programs programs, EglTexts t, Family family) {
        this.programs = programs;
        this.family = family;
        String check = family.prefix() + "-CHK-SEL", move = family.prefix() + "-MOVE-VAL", submit = family.prefix() + "-SUBMIT";
        m = t.assignedAll(check, PKG, "FINAL-ERROR-MESSAGE");
        p = t.assignedAll(check + "-X", PKG, "FINAL-ERROR-MESSAGE");
        if (m.size() != 14 || p.size() != 8) throw new IllegalStateException(family.screen() + " checks changed: " + m + " / " + p);
        List<String> s = t.assignedAll(submit, PKG, "FINAL-ERROR-MESSAGE");
        carsBelow = s.get(0);
        submitted = s.get(1);
        titles = t.assignedAll(move, PKG, "PASSVALUE-TITLE");
        carMessages = t.assignedAll(move, PKG, "CAR-ERROR-MESSAGE");
        carFrom = t.assigned(check, PKG, "CAR-NO-FROM", null);
        carTo = t.assigned(check, PKG, "CAR-NO-TO", null);
        narrStart = t.openingValue(family.screen(), family.narrWidget());
        narrChoices = t.items(family.screen(), family.narrWidget());
        boolean zero;
        try { zero = t.compared(submit, PKG, "CAR-NO-FROM").contains("0000"); } catch (IllegalStateException e) { zero = false; }
        printZeroFromIsBlank = zero;
    }

    public Options options() {
        return new Options(yards(), statuses(), narrChoices, narrStart, carFrom, carTo, titles, printers());
    }

    List<String> yards() {
        List<String> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt(family.yardsProgram(), Map.of());
        if (o.ok()) for (Map<String, Object> r : rows(o.result(), family.yardsProgram() + "_DATA", "dataRow")) if (!text(r, "yrd").isEmpty()) out.add(text(r, "yrd"));
        return out;
    }

    List<Status> statuses() {
        List<Status> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt(family.statusProgram(), Map.of());
        if (o.ok()) for (Map<String, Object> r : rows(o.result(), family.statusProgram() + "_DATA", "dataRow")) {
            if (!text(r, "vehStat").isEmpty()) out.add(new Status(text(r, "vehStat"), text(r, "vehStatDesc")));
        }
        return out;
    }

    List<Printer> printers() {
        List<Printer> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt("WS70A01", Map.of());
        if (o.ok()) for (Map<String, Object> r : rows(o.result(), "WS70A01_DATA", "dataRow")) out.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return out;
    }

    /** CHK-SEL, then MOVE-VAL: the message of the last failed check, or the values for the report window */
    public Checked check(Criteria c) {
        Option o = Option.of(c.option());
        String message = null;
        if (o == Option.BASE) {
            if (!yards().contains(c.y())) message = m.get(0);
            if (c.y().isEmpty()) message = m.get(1);
            else if (c.n().isEmpty()) message = m.get(2);
        } else if (o == Option.STATUS) {
            if (!yards().contains(c.y())) message = m.get(3);
            if (statuses().stream().noneMatch(s -> s.status().equals(c.s()))) message = m.get(4);
            if (c.y().isEmpty()) message = m.get(5);
            else if (c.s().isEmpty()) message = m.get(6);
            else if (c.n().isEmpty()) message = m.get(7);
        } else if (o == Option.NOGO) {
            if (!yards().contains(c.y())) message = m.get(8);
            if (c.y().isEmpty()) message = m.get(9);
        } else if (o == Option.SUMMARY) {
            if (!yards().contains(c.y())) message = m.get(10);
            if (c.y().isEmpty()) message = m.get(11);
        } else {
            message = m.get(12);
        }
        String from = blank(c.carFrom()) ? carFrom : c.carFrom().strip(), to = blank(c.carTo()) ? carTo : c.carTo().strip();
        if (to.compareTo(from) < 0) message = m.get(13);
        if (message != null) throw new InvalidRequestException(message);
        if (!carMessages.isEmpty() && !digits(from)) throw new InvalidRequestException(carMessages.get(0));
        if (carMessages.size() > 1 && !digits(to)) throw new InvalidRequestException(carMessages.get(1));
        return new Checked(o, c.y(), o == Option.STATUS ? c.s() : "", o == Option.BASE || o == Option.STATUS ? c.n() : "", from, to,
                titles.get(o.ordinal()));
    }

    /** SUBMIT with CHK-SEL-X (the family's print program) */
    public String print(Criteria c, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Option o = Option.of(c.option());
        if (o == null) throw new InvalidRequestException(p.get(0));
        String message = null;
        switch (o) {
            case BASE -> { if (c.y().isEmpty()) message = p.get(1); else if (c.n().isEmpty()) message = p.get(2); }
            case STATUS -> { if (c.y().isEmpty()) message = p.get(3); else if (c.s().isEmpty()) message = p.get(4); else if (c.n().isEmpty()) message = p.get(5); }
            case NOGO -> { if (c.y().isEmpty()) message = p.get(6); }
            case SUMMARY -> { if (c.y().isEmpty()) message = p.get(7); }
        }
        String from = c.carFrom() == null ? "" : c.carFrom().strip(), to = c.carTo() == null ? "" : c.carTo().strip();
        if (from.isEmpty() || (printZeroFromIsBlank && from.equals("0000"))) from = carFrom;
        if (to.isEmpty() || to.equals("0000")) to = carTo;
        if (to.compareTo(from) < 0) throw new InvalidRequestException(carsBelow);
        if (message != null) throw new InvalidRequestException(message);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNoFrom", from);
        crit.put("carNoTo", to);
        crit.put("reptId", String.valueOf(o.number()));
        crit.put("homeYrd", c.y());
        crit.put("reptMisc", o == Option.BASE || o == Option.STATUS ? c.n() : "");
        crit.put("incdStat", o == Option.STATUS ? c.s() : "");
        crit.put("reptType", printer.strip());
        programs.run(family.printProgram(), Map.of(family.printProgram() + "_CRIT", crit));
        return submitted;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean digits(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }
}
