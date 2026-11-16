package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Labor detail of a sub job that is out the door, and closing it (old VHSR03I, opened from the repair detail VHSR09I):
 * the sub job's state (VHSR03F), its labor (VHSR03A), whether it has repairs (VHSR09A), its repair comments (VHSR03B);
 * adding labor lines (VHSR03D) and closing the sub job (VHSR03E). Rules from VHSR03IS-* in MRSSecondaryRepair.
 */
public class SecondaryRepairLaborService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final String jobDocRequired, jobRequired, docRequired, msgJob, msgDash, msgClosed, msgNotOut, msgUnknown, repairsRequired;
    private final String line, dataRequired, dateRequired, dateInvalid, dateDbError, dateBeforeIn, dateAfterOut, emplRequired, emplNotNumeric,
            hoursRequired, hoursTooMany, laborAdded, jobClosed;
    private final List<String> outTheDoor;
    private final String closedStatus;
    private final BigDecimal maxHours;
    private final int width, maxErrors;

    public SecondaryRepairLaborService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        String rd = "VHSR03IS-RET-DATA", add = "VHSR03IS-ADDLABOR", val = "VHSR03IS-VALIDATE", chk = "VHSR03IS-DATE-CHK";
        jobDocRequired = t.assigned(rd, PKG, "FINAL-MESSAGE", "Job & Doc");
        jobRequired = t.assignedAll(rd, PKG, "FINAL-MESSAGE").stream().filter(x -> x.startsWith("Job No")).findFirst().orElseThrow();
        docRequired = t.assignedAll(rd, PKG, "FINAL-MESSAGE").stream().filter(x -> x.startsWith("Doc No")).findFirst().orElseThrow();
        msgJob = t.assignedRaw(rd, PKG, "FINAL-MESSAGE-1").get(0);
        msgDash = t.assignedRaw(rd, PKG, "FINAL-MESSAGE-3").get(0);
        msgClosed = t.assignedRaw(rd, PKG, "FINAL-MESSAGE-5").stream().filter(x -> x.contains("Closed")).findFirst().orElseThrow();
        msgNotOut = t.assignedRaw(rd, PKG, "FINAL-MESSAGE-5").stream().filter(x -> x.contains("Not Out")).findFirst().orElseThrow();
        msgUnknown = t.assignedRaw(rd, PKG, "FINAL-MESSAGE-5").stream().filter(x -> x.contains("does not exist")).findFirst().orElseThrow();
        repairsRequired = t.assigned(rd, PKG, "FINAL-MESSAGE", "Repair Details required");
        closedStatus = t.compared(rd, PKG, "SUB-JOB-STAT").get(0);                 // the status RET-DATA tests first
        outTheDoor = t.compared(rd, PKG, "SUB-JOB-STAT").stream().filter(x -> !x.equals(closedStatus)).toList();
        line = t.assignedRaw(add, PKG, "ERROR-MSG-1").get(0);
        dataRequired = t.assignedRaw(add, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Data required")).findFirst().orElseThrow();
        dateRequired = t.assignedRaw(add, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Date Work required")).findFirst().orElseThrow();
        dateInvalid = t.assignedRaw(add, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Invalid Date")).findFirst().orElseThrow();
        dateDbError = t.assignedRaw(add, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("DB Error")).findFirst().orElseThrow();
        dateBeforeIn = t.assignedRaw(chk, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Date In")).findFirst().orElseThrow();
        dateAfterOut = t.assignedRaw(chk, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Date Out")).findFirst().orElseThrow();
        emplRequired = t.assignedRaw(val, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Empl No required")).findFirst().orElseThrow();
        emplNotNumeric = t.assignedRaw(val, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("not numeric")).findFirst().orElseThrow();
        hoursRequired = t.assignedRaw(val, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("Hrs required")).findFirst().orElseThrow();
        hoursTooMany = t.assignedRaw(val, PKG, "ERROR-MSG-3").stream().filter(x -> x.contains("exceed")).findFirst().orElseThrow();
        maxHours = t.compared(val, PKG, "REPR-LABOR-HRS").stream().map(BigDecimal::new).max(BigDecimal::compareTo).orElseThrow();
        laborAdded = t.assigned("VHSR03IS-DB-STORE", PKG, "FINAL-MESSAGE", "added");
        jobClosed = t.assigned("VHSR03IS-DB-CLOSE", PKG, "FINAL-MESSAGE", "Job Closed");
        width = SecondaryRepairForms.limit(t, "VHSR03IS-MOVE-MSG", "ERROR-INDEX");
        maxErrors = SecondaryRepairForms.limit(t, "VHSR03IS-MOVE-MSG", "ERROR-LOOP-INDEX");
    }

    public record Labor(String date, String employee, BigDecimal hours) {}

    public record Comment(int number, String text, String userGroup, String date, String time) {}

    /**
     * @param canClose the sub job has repair details (the old Close Job button); @param note the old notice when it has
     * none
     */
    public record LaborDetails(String job, int doc, String status, String dateIn, String dateOut, List<Labor> labor, boolean canClose, String note,
                               List<Comment> comments) {}

    private record State(String job, int doc, Map<String, Object> f) {}

    /** VHSR03IS-RET-DATA: the checks, the sub job must be out the door (VHSR03F), then its labor, repairs and comments */
    public LaborDetails details(String jobNo, Integer doc) {
        State s = state(jobNo, doc);
        List<Labor> labor = new ArrayList<>();
        ProgramResult a = programs.attempt("VHSR03A", Map.of("VHSR03A_CRIT", key(s.job(), s.doc()))).result();
        int urc = a.control() == null ? 0 : a.control().returnCode();
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error("VHSR03A", a.control());
        boolean canClose = false;
        String note = null;
        if (urc != 100) {
            for (Map<String, Object> row : rows(a, "VHSR03A_DATA", "dataRow")) labor.add(new Labor(text(row, "dateWork"), text(row, "emplNo"), hours(row.get("reprLaborHrs"))));
            ProgramResult r = programs.attempt("VHSR09A", Map.of("VHSR09A_CRIT", key(s.job(), s.doc()))).result();
            int u = r.control() == null ? 0 : r.control().returnCode(), n = r.control() == null ? 0 : r.control().rowCount();
            if ((u == 0 || u == 4) && n > 0) canClose = true;
            else if (u == 100 && n == 0) note = repairsRequired;
            else throw programs.error("VHSR09A", r.control());
        }
        return new LaborDetails(s.job(), s.doc(), text(s.f(), "subJobStat"), SecondaryRepairForms.flat(s.f().get("dateIn")), SecondaryRepairForms.flat(s.f().get("dateOut")), labor, canClose, note,
                comments(s.job(), s.doc()));
    }

    private State state(String jobNo, Integer doc) {
        String job = SecondaryRepairForms.upper(jobNo);
        int d = doc == null ? 0 : doc;
        if (job.isEmpty() && d == 0) throw new InvalidRequestException(jobDocRequired);
        if (job.isEmpty()) throw new InvalidRequestException(jobRequired);
        if (d == 0) throw new InvalidRequestException(docRequired);
        ProgramResult f = programs.attempt("VHSR03F", Map.of("VHSR03F_CRIT", key(job, d))).result();
        Map<String, Object> data = f.record("VHSR03F_DATA");
        String head = msgJob + String.format("%-6s", job) + msgDash + String.format("%02d", d % 100);
        if (number(data, "docCount") != 1) throw new InvalidRequestException((head + msgUnknown).strip());
        String stat = text(data, "subJobStat");
        if (!outTheDoor.contains(stat)) throw new InvalidRequestException((head + (stat.equals(closedStatus) ? msgClosed : msgNotOut)).strip());
        return new State(job, d, data);
    }

    /** VHSR03IS-PRV-NXT with no button: the sub job's repair comments (VHSR03B) */
    private List<Comment> comments(String job, int doc) {
        ProgramResult b = programs.attempt("VHSR03B", Map.of("VHSR03B_CRIT", key(job, doc))).result();
        List<Comment> out = new ArrayList<>();
        int u = b.control() == null ? 0 : b.control().returnCode();
        if (u != 0 && u != 4) return out;
        for (Map<String, Object> row : rows(b, "VHSR03B_DATA", "dataRow")) {
            out.add(new Comment((int) number(row, "narrNo"), text(row, "narr"), text(row, "userGrp"), text(row, "dateNarr"), text(row, "timeNarr")));
        }
        return out;
    }

    /** a new labor line: the day worked (ISO yyyy-MM-dd), the employee, the hours */
    public record LaborLine(String date, String employee, BigDecimal hours) {}

    public record Added(String message, LaborDetails details) {}

    /**
     * VHSR03IS-ADDLABOR: each line is checked as the old screen did (a first blank line, the date by AFCXVLD and against
     * the sub job's dates in and out, the employee, the hours), at most 6 errors, "Line: nn - ..." each; then the lines
     * with a date are written (VHSR03D), with the repair comment when the sub job has none yet.
     */
    public Added add(String jobNo, Integer doc, List<LaborLine> lines, String comment, String userGroup, String terminal) {
        State s = state(jobNo, doc);
        List<LaborLine> in = lines == null ? List.of() : lines;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        int blank = 0;
        for (int i = 0; i < in.size() + 3 && blank <= 2 && errors.size() <= 6; i++) {
            LaborLine l = i < in.size() && in.get(i) != null ? in.get(i) : new LaborLine(null, null, null);
            int set = i + 1;
            String date = SecondaryRepairForms.strip(l.date()), empl = SecondaryRepairForms.strip(l.employee());
            BigDecimal hrs = l.hours() == null ? BigDecimal.ZERO : l.hours();
            boolean noDateHours = date.isEmpty() && hrs.signum() == 0;
            if (noDateHours) blank++;
            if (noDateHours && set == 1) {
                errors.add(lineMessage(set, dataRequired));
                break;
            }
            if (blank > 2 || (date.isEmpty() && empl.isEmpty() && hrs.signum() <= 0)) continue;
            String screen = null;
            if (date.isEmpty()) {
                errors.add(lineMessage(set, dateRequired));
            } else {
                screen = screenDate(date);
                if (screen == null) {
                    errors.add(lineMessage(set, dateInvalid));
                } else {
                    SecondaryRepairForms.DateCheck c;
                    try { c = SecondaryRepairForms.afcxvld(programs, screen); } catch (RuntimeException e) { c = null; }
                    if (c == null) errors.add(lineMessage(set, dateDbError));
                    else if (c == SecondaryRepairForms.DateCheck.INCORRECT) errors.add(lineMessage(set, dateInvalid));
                    else dateCheck(screen, s.f(), set, errors);
                }
            }
            if (errors.size() >= 6) break;
            if (empl.isEmpty()) errors.add(lineMessage(set, emplRequired));
            else if (!empl.chars().allMatch(Character::isDigit)) errors.add(lineMessage(set, emplNotNumeric));
            if (errors.size() >= 6) break;
            if (hrs.signum() <= 0) errors.add(lineMessage(set, hoursRequired));
            else if (hrs.compareTo(maxHours) > 0) errors.add(lineMessage(set, hoursTooMany));
            if (errors.size() >= 6) break;
            if (screen != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dateWork", screen);
                row.put("emplNo", empl);
                row.put("reprLaborHrs", hrs);
                out.add(row);
            }
        }
        SecondaryRepairForms.fail(errors, width, maxErrors);
        if (out.isEmpty()) throw new InvalidRequestException(lineMessage(1, dataRequired));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataRow", out);
        data.put("jobNo", s.job());
        data.put("subJobDocNo", s.doc());
        data.put("termLastUpd", terminal == null ? "" : terminal);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        boolean addComment = comment != null && !comment.isBlank() && comments(s.job(), s.doc()).isEmpty();
        data.put("addNarrInd", addComment ? "Y" : "N");
        data.put("narr", addComment ? comment : "");
        ProgramResult r = programs.attempt("VHSR03D", Map.of("VHSR03D_DATA", data)).result();
        if (r.control() != null && r.control().returnCode() != 0 && r.control().returnCode() != 4) throw programs.error("VHSR03D", r.control());
        return new Added(laborAdded, details(s.job(), s.doc()));
    }

    /** VHSR03IS-CLOSEJOB / DB-CLOSE: a sub job out the door with repair details is closed (VHSR03E) */
    public String close(String jobNo, Integer doc, String userGroup, String terminal) {
        LaborDetails d = details(jobNo, doc);
        if (!d.canClose()) throw new InvalidRequestException(d.note() == null ? repairsRequired : d.note());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("termLastUpd", terminal == null ? "" : terminal);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        ProgramResult r = programs.attempt("VHSR03E", Map.of("VHSR03E_CRIT", key(d.job(), d.doc()), "VHSR03E_DATA", data)).result();
        if (r.control() != null && r.control().returnCode() != 0 && r.control().returnCode() != 4) throw programs.error("VHSR03E", r.control());
        return jobClosed;
    }

    /** VHSR03IS-DATE-CHK: the day worked between the sub job's date in and date out (YYYY-MM-DD there) */
    private void dateCheck(String screen, Map<String, Object> f, int set, List<String> errors) {
        int work = ymd(screen.substring(6, 10), screen.substring(0, 2), screen.substring(3, 5));
        String in = SecondaryRepairForms.flat(f.get("dateIn")), out = SecondaryRepairForms.flat(f.get("dateOut"));
        if (work < ymd(part(in, 0, 4), part(in, 5, 7), part(in, 8, 10))) errors.add(lineMessage(set, dateBeforeIn));
        else if (work > ymd(part(out, 0, 4), part(out, 5, 7), part(out, 8, 10))) errors.add(lineMessage(set, dateAfterOut));
    }

    private static String part(String s, int from, int to) {
        return s.length() >= to ? s.substring(from, to) : "";
    }

    private static int ymd(String y, String m, String d) {
        return num(y) * 10000 + num(m) * 100 + num(d);
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return 0; }
    }

    /** the day worked as the old screen took it (MMDDYYYY) and stores it (MM-DD-YYYY); null when not a date */
    private static String screenDate(String iso) {
        try {
            return LocalDate.parse(iso).format(SecondaryRepairForms.SCREEN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "Line: " + the line number (2 digits) + the message */
    private String lineMessage(int set, String message) {
        return line + String.format("%02d", set % 100) + message;
    }

    private static BigDecimal hours(Object v) {
        if (v instanceof BigDecimal b) return b;
        try { return new BigDecimal(String.valueOf(v == null ? 0 : v)); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static Map<String, Object> key(String job, int doc) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("jobNo", job);
        k.put("subJobDocNo", doc);
        return k;
    }
}
