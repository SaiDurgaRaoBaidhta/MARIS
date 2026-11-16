package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.time.Clock;
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
 * A sub job logged out the door (old VHSR04I, MRSSecondaryRepair RUIFunctions): the drop-down lists (VHSR04IS-INIT:
 * VHSRWT, VHSR04A), the sub job looked up by job and doc number (VHSR04IS-RET-DATA: VHSR02E; only an open one can go
 * out), and the update (VHSR04IS-UPDATE: AFCXVLD, then VHSR04B, which writes). The update's checks all run and their
 * messages are joined the way VHSR04IS-MOVE-MSG joins them.
 */
public final class SecondaryRepairOutTheDoorService {
    private static final String PKG = "MRSSecondaryRepair";
    private static final String UPDATE = "VHSR04IS-UPDATE", RET = "VHSR04IS-RET-DATA", STORE = "VHSR04IS-DB-STORE";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    /** how the sub job goes out: the old screen's four toggle buttons (none = repaired) */
    public enum Disposition { LONG_TERM_STORAGE, OUT_TO_VENDOR, DEFERRED, AWAITING_PARTS }

    private final Programs programs;
    private final Clock clock;
    private final String jobRequired, docRequired, bothRequired, notFound, open;
    private final Map<String, String> statusMessages = new LinkedHashMap<>();
    private final String invalidDate, qtyOutNegative, qtyScrappedNegative, qtySum, qtyInLabel, invalidFault, invalidWorkType, vendorRequired, futureDate,
            olderThanIn;
    private final Map<Disposition, String> statusFor = new LinkedHashMap<>();
    private final String repaired;
    private final Map<String, String> suffixes = new LinkedHashMap<>();
    private final List<String> done = new ArrayList<>();
    private final int defaultDoc, defaultQtyOut, errorWidth, finalWidth, qtyInWidth;
    private final List<Integer> doneWidths = new ArrayList<>();

    public SecondaryRepairOutTheDoorService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle bundle, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        List<String> input = t.assignedUnder(RET, PKG, "FINAL-MESSAGE", "JOB-NO");
        jobRequired = input.get(0);
        docRequired = input.get(1);
        bothRequired = input.get(2);
        List<String> all = t.assignedAll(RET, PKG, "FINAL-MESSAGE");
        notFound = all.get(all.size() - 1);
        List<String> statuses = t.compared(RET, PKG, "SUB-JOB-STAT"), texts = t.assignedUnder(RET, PKG, "FINAL-MESSAGE", "SUB-JOB-STAT");
        String openStatus = null;
        for (int i = 0; i < statuses.size(); i++) {
            String m = i < texts.size() ? texts.get(i) : "";
            if (m.isEmpty()) openStatus = statuses.get(i);
            else statusMessages.put(statuses.get(i), m);
        }
        if (openStatus == null) throw new IllegalStateException(RET + ": no status lets the sub job go out");
        open = openStatus;

        invalidDate = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "DATE-OUT").get(0);
        qtyOutNegative = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "CNT-CMPNTS-OUT").get(0);
        qtyScrappedNegative = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "CNT-CMPNTS-SCRAPPD").get(0);
        qtySum = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "TEMP-CNT").get(0);
        qtyInLabel = t.assignedRaw(UPDATE, PKG, "MSG-X-1").get(0);
        invalidFault = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "CMPNT-FAULT-CD").get(0);
        invalidWorkType = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "DIAG-WORK-TYPE").get(0);
        vendorRequired = t.assignedUnder(UPDATE, PKG, "ERROR-MESSAGE-OCCR", "VENDOR-ID").get(0);
        List<String> dateChecks = t.assignedUnder("VHSR04IS-DATE-CHK", PKG, "ERROR-MESSAGE-OCCR", "DATE-OUT-YYYY");
        futureDate = dateChecks.get(0);
        olderThanIn = dateChecks.get(1);

        statusFor.put(Disposition.LONG_TERM_STORAGE, t.assignedUnder(STORE, PKG, "SUB-JOB-STAT", "TOGGLE-LONGTERM").get(0));
        statusFor.put(Disposition.OUT_TO_VENDOR, t.assignedUnder(STORE, PKG, "SUB-JOB-STAT", "TOGGLE-VENDORID").get(0));
        statusFor.put(Disposition.DEFERRED, t.assignedUnder(STORE, PKG, "SUB-JOB-STAT", "TOGGLE-DEF").get(0));
        statusFor.put(Disposition.AWAITING_PARTS, t.assignedUnder(STORE, PKG, "SUB-JOB-STAT", "TOGGLE-AWP").get(0));
        List<String> stored = t.assignedAll(STORE, PKG, "SUB-JOB-STAT");
        repaired = stored.get(stored.size() - 1);
        List<String> suffixStatus = t.compared(STORE, PKG, "SUB-JOB-STAT"), suffixText = t.assignedRaw(STORE, PKG, "FINAL-MESSAGE-6");
        for (int i = 0; i < Math.min(suffixStatus.size(), suffixText.size()); i++) suffixes.put(suffixStatus.get(i), suffixText.get(i));
        for (int i = 1; i <= 5; i += 2) done.add(t.assignedRaw(STORE, PKG, "FINAL-MESSAGE-" + i).get(0));

        defaultDoc = t.assignedNumber("VHSR04IS-INIT", PKG, "SUB-JOB-DOC-NO").intValue();
        defaultQtyOut = t.assignedNumber("VHSR04IS-MOVE-VAL", PKG, "CNT-CMPNTS-OUT").intValue();
        RecordLayout w = bundle.recordType("VHSR04IW", List.of(PKG));
        errorWidth = width(w.fields(), "ERROR-MESSAGE-OCCR");
        finalWidth = width(w.fields(), "FINAL-MESSAGE");
        for (int i = 1; i <= 6; i++) doneWidths.add(width(w.fields(), "FINAL-MESSAGE-" + i));
        qtyInWidth = width(bundle.recordType("VHSR04IW-X", List.of(PKG)).fields(), "CNT-CMPNTS-IN");
    }

    private static int width(List<FieldLayout> fields, String name) {
        for (FieldLayout f : fields) {
            if (f.name().equalsIgnoreCase(name)) return f.length();
            int w = width(f.children(), name);
            if (w > 0) return w;
        }
        return 0;
    }

    public record WorkType(String code, String description) {}

    public record Options(int doc, List<String> faultCodes, List<WorkType> workTypes) {}

    /** VHSR04IS-INIT: the diagnostic work types (VHSRWT) and fault codes (VHSR04A) the drop-downs offer */
    public Options options() {
        List<WorkType> types = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("VHSRWT", Map.of()), "VHSRWT_DATA", "dataRow")) {
            types.add(new WorkType(text(r, "workType"), text(r, "workTypeDesc")));
        }
        List<String> faults = new ArrayList<>();
        for (Map<String, Object> r : rows(programs.run("VHSR04A", Map.of()), "VHSR04A_DATA", "dataRow")) faults.add(text(r, "cmpntFaultCd"));
        return new Options(defaultDoc, faults, types);
    }

    /**
     * @param canGoOut only an open sub job; @param message the old screen's text for one that cannot
     * @param dateOut, qtyOut, qtyScrapped what the old screen fills in for an open one
     */
    public record SubJob(String jobNo, int doc, String status, boolean canGoOut, String message, String dateIn, int qtyIn, String dateOut, int qtyOut,
                         int qtyScrapped) {}

    private record Found(SubJob subJob, Map<String, Object> data) {}

    /** VHSR04IS-RET-DATA */
    public SubJob subJob(String jobNo, Integer doc) {
        return find(jobNo, doc).subJob();
    }

    private Found find(String jobNo, Integer doc) {
        String job = jobNo == null ? "" : jobNo.strip();
        int d = doc == null ? 0 : doc;
        if (job.isEmpty() && d > 0) throw new InvalidRequestException(jobRequired);
        if (!job.isEmpty() && d == 0) throw new InvalidRequestException(docRequired);
        if (job.isEmpty()) throw new InvalidRequestException(bothRequired);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("jobNo", job);
        crit.put("subJobDocNo", d);
        ProgramResult r = programs.attempt("VHSR02E", Map.of("VHSR02E_CRIT", crit)).result();
        if (r.control() == null || r.control().rowCount() != 1) throw new InvalidRequestException(notFound);
        Map<String, Object> data = r.record("VHSR02E_DATA");
        String status = text(data, "subJobStat");
        boolean canGoOut = status.equals(open);
        String message = statusMessages.getOrDefault(status, "");
        return new Found(new SubJob(job, d, status, canGoOut, message, text(data, "dateIn"), (int) number(data, "cntCmpntsIn"),
                canGoOut ? ISO.format(LocalDate.now(clock)) : null, canGoOut ? defaultQtyOut : 0, 0), data);
    }

    /** @param dateOut yyyy-MM-dd; @param disposition null = repaired */
    public record OutTheDoor(String jobNo, Integer doc, String dateOut, Integer qtyOut, Integer qtyScrapped, String faultCode, String workType,
                             String inspector, Disposition disposition, String vendorId) {}

    /** the checks failed: their messages, and the old screen's one line made of them */
    public static final class Rejected extends InvalidRequestException {
        private final List<String> errors;

        Rejected(String message, List<String> errors) {
            super(message);
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    public record Result(String message, String status) {}

    /** VHSR04IS-UPDATE and VHSR04IS-DB-STORE (VHSR04B) */
    public Result goOut(OutTheDoor o, String group, String terminal) {
        Found f = find(o.jobNo(), o.doc());
        if (!f.subJob().canGoOut()) throw new InvalidRequestException(f.subJob().message());
        Map<String, Object> in = f.data();
        List<String> errors = new ArrayList<>();

        String dateOut = null;                                                     // shown and sent as MM-dd-yyyy
        LocalDate out = null;
        String typed = o.dateOut() == null ? "" : o.dateOut().strip();
        if (typed.isEmpty()) errors.add(invalidDate);
        else {
            try {
                out = LocalDate.parse(typed, ISO);
            } catch (DateTimeParseException e) {
                errors.add(invalidDate);
            }
            if (out != null) {
                dateOut = MDY.format(out);
                Map<String, Object> crit = new LinkedHashMap<>();
                crit.put("dateError", "N");
                crit.put("formDate", ISO.format(out));
                crit.put("otherSqlError", "N");
                ProgramResult v = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result();
                Map<String, Object> back = v.record("AFCXVLD_CRIT");
                String de = back == null ? "N" : text(back, "dateError"), sql = back == null ? "N" : text(back, "otherSqlError");
                if (de.equals("Y") && sql.equals("N")) errors.add(invalidDate);
                else if (de.equals("N") && sql.equals("Y")) throw sqlError(v);
                else {
                    LocalDate today = LocalDate.now(clock);
                    if (out.isAfter(today)) errors.add(futureDate);
                    else if (before(out, in)) errors.add(olderThanIn);
                }
            }
        }
        int qtyOut = o.qtyOut() == null ? 0 : o.qtyOut(), scrapped = o.qtyScrapped() == null ? 0 : o.qtyScrapped();
        int qtyIn = (int) number(in, "cntCmpntsIn");
        if (qtyOut < 0) errors.add(qtyOutNegative);
        else if (scrapped < 0) errors.add(qtyScrappedNegative);
        else if (qtyOut + scrapped != qtyIn) {
            errors.add(qtySum);
            errors.add(qtyInLabel + String.format("%0" + qtyInWidth + "d", qtyIn));
        }
        String fault = o.faultCode() == null ? "" : o.faultCode().strip().toUpperCase(Locale.ROOT);
        String type = o.workType() == null ? "" : o.workType().strip().toUpperCase(Locale.ROOT);
        Options lists = null;
        if (fault.isEmpty()) errors.add(invalidFault);
        else if (!(lists = options()).faultCodes().contains(fault)) errors.add(invalidFault);
        if (type.isEmpty()) errors.add(invalidWorkType);
        else {
            if (lists == null) lists = options();
            if (lists.workTypes().stream().noneMatch(x -> x.code().equals(type))) errors.add(invalidWorkType);
        }
        String vendor = o.vendorId() == null ? "" : o.vendorId().strip();
        if (o.disposition() == Disposition.OUT_TO_VENDOR && vendor.isEmpty()) errors.add(vendorRequired);
        if (!errors.isEmpty()) throw new Rejected(joined(errors), errors);

        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("jobNo", f.subJob().jobNo());
        crit.put("subJobDocNo", f.subJob().doc());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dateOut", dateOut);
        data.put("cntCmpntsOut", qtyOut);
        data.put("cntCmpntsScrappd", scrapped);
        data.put("cmpntFaultCd", fault);
        data.put("diagWorkType", type);
        data.put("qaInspectId", o.inspector() == null ? "" : o.inspector().strip());
        data.put("userGrp", group == null ? "" : group);
        data.put("termLastUpd", terminal == null ? "" : terminal);
        Disposition d = o.disposition();
        String status = d == null ? repaired : statusFor.get(d);
        data.put("longTermStorInd", d == Disposition.LONG_TERM_STORAGE ? "Y" : text(in, "longTermStorInd"));
        data.put("vendorId", d == Disposition.OUT_TO_VENDOR ? vendor : text(in, "vendorId"));
        data.put("subJobStat", status);
        ProgramResult r = programs.attempt("VHSR04B", Map.of("VHSR04B_CRIT", crit, "VHSR04B_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHSR04B", r.control());
        return new Result(doneMessage(f.subJob().jobNo(), f.subJob().doc(), status), status);
    }

    private LegacyBusinessException sqlError(ProgramResult v) {
        return programs.error("AFCXVLD", v.control());
    }

    /** VHSR04IS-DATE-CHK against DATE-IN (MM-DD-YYYY): year, then month, then day */
    @SuppressWarnings("unchecked")
    private static boolean before(LocalDate out, Map<String, Object> subJob) {
        Object v = subJob == null ? null : subJob.get("dateIn");
        int mm, dd, yyyy;
        if (v instanceof Map<?, ?> parts) {                                       // DATE-IN-MM, -DD, -YYYY
            Map<String, Object> m = (Map<String, Object>) parts;
            mm = (int) number(m, "dateInMm");
            dd = (int) number(m, "dateInDd");
            yyyy = (int) number(m, "dateInYyyy");
        } else {
            String s = v == null ? "" : v.toString();
            mm = num(s, 0, 2);
            dd = num(s, 3, 5);
            yyyy = num(s, 6, 10);
        }
        if (out.getYear() != yyyy) return out.getYear() < yyyy;
        if (out.getMonthValue() != mm) return out.getMonthValue() < mm;
        return out.getDayOfMonth() < dd;
    }

    private static int num(String s, int from, int to) {
        if (s == null || s.length() < to) return 0;
        try { return Integer.parseInt(s.substring(from, to).strip()); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * VHSR04IS-MOVE-MSG: each message up to its "." becomes "text, "; one without a "." is taken whole (its full field
     * width); the last separator's comma is blanked
     */
    String joined(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (String e : errors) {
            String m = (e + " ".repeat(errorWidth)).substring(0, errorWidth);
            int dot = m.indexOf('.');
            if (dot >= 0) sb.append(m, 0, dot).append(", ");
            else sb.append(m);
        }
        if (sb.length() >= 2) sb.setCharAt(sb.length() - 2, ' ');
        String s = sb.toString();
        return (s.length() > finalWidth ? s.substring(0, finalWidth) : s).stripTrailing();
    }

    private String doneMessage(String job, int doc, String status) {
        String m = pad(done.get(0), doneWidths.get(0)) + pad(job, doneWidths.get(1)) + pad(done.get(1), doneWidths.get(2))
                + String.format("%0" + doneWidths.get(3) + "d", doc) + pad(done.get(2), doneWidths.get(4)) + pad(suffixes.getOrDefault(status, ""), doneWidths.get(5));
        return (m.length() > finalWidth ? m.substring(0, finalWidth) : m).stripTrailing();
    }

    private static String pad(String s, int w) {
        String v = s == null ? "" : s;
        return v.length() >= w ? v.substring(0, w) : v + " ".repeat(w - v.length());
    }
}
