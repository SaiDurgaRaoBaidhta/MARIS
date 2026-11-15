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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job split (old VHSR08I, from the menu and after a new job in VHSR01I): a job's sub jobs (VHSR08A), one sub job with
 * its comments (VHSR08B), and a new sub job (VHSR08C) after the checks VHSR08IS-CREATE shares with VHSR01I. Its Comments
 * button opens the comments (VHSR02I) of the selected sub job. Rules from VHSR08IS-* in MRSSecondaryRepair.
 */
public class SecondaryRepairJobSplitService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final Clock clock;
    private final SecondaryRepairJobChecks checks;
    private final String closed, deferred, unknown, closedStatus, deferredStatus, docNo, forJob, created, warning;

    public SecondaryRepairJobSplitService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.checks = new SecondaryRepairJobChecks(programs, t, "VHSR08I");
        String rj = "VHSR08IS-RET-JOB", st = "VHSR08IS-DB-STORE";
        closed = t.assigned(rj, PKG, "FINAL-MESSAGE", "Closed");
        deferred = t.assigned(rj, PKG, "FINAL-MESSAGE", "Deferred");
        unknown = t.assigned(rj, PKG, "FINAL-MESSAGE", "does not exist");
        List<String> stats = t.compared(rj, PKG, "JOB-STAT");
        closedStatus = stats.get(0);
        deferredStatus = stats.get(1);
        docNo = t.assignedRaw(st, PKG, "FINAL-MESSAGE-1").get(0);
        forJob = t.assignedRaw(st, PKG, "FINAL-MESSAGE-3").get(0);
        created = t.assignedRaw(st, PKG, "FINAL-MESSAGE-5").get(0);
        warning = t.assignedRaw(st, PKG, "FINAL-MESSAGE-6").get(0);
    }

    public record Options(List<SecondaryRepairJobChecks.Code> workTypes, List<SecondaryRepairJobChecks.Code> repairAreas, String workType) {}

    /** VHSR08IS-INIT: work types (VHSRWT) and repair areas (VHSRRA) */
    public Options options() {
        return new Options(checks.workTypes(), checks.repairAreas(), checks.defaultWorkType);
    }

    public record SubJob(int doc, String repairArea, String comment) {}

    /** @param nextDoc the number the new sub job gets; @param repairArea the old default: the last sub job's area */
    public record Job(String job, String status, String car, String dept, String equipmentType, String equipmentLocation, String equipmentId,
                      String incident, String printControlNo, String sentFrom, int nextDoc, String repairArea, List<SubJob> subJobs) {}

    /** VHSR08IS-RET-JOB: the job must exist and not be closed or deferred (VHSR08A) */
    public Job job(String jobNo) {
        String job = SecondaryRepairForms.upper(jobNo);
        if (job.isEmpty()) throw new InvalidRequestException(unknown);
        ProgramResult r = programs.attempt("VHSR08A", Map.of("VHSR08A_CRIT", Map.of("jobNo", job))).result();
        Map<String, Object> d = r.record("VHSR08A_DATA");
        int urc = r.control() == null ? 0 : r.control().returnCode();
        boolean found = "Y".equals(text(d, "recordFound"));
        if (urc == 100 && !found) throw new InvalidRequestException(unknown);
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error("VHSR08A", r.control());
        if (!found) throw new InvalidRequestException(unknown);
        String stat = text(d, "jobStat");
        if (urc != 100 && stat.equals(closedStatus)) throw new InvalidRequestException(closed);
        if (urc != 100 && stat.equals(deferredStatus)) throw new InvalidRequestException(deferred);
        List<SubJob> subs = new ArrayList<>();
        if (urc != 100) {
            for (Map<String, Object> row : rows(r, "VHSR08A_DATA", "dataRow")) subs.add(new SubJob((int) number(row, "subJobDocNo"), text(row, "reprArea"), text(row, "narr")));
        }
        return new Job(job, stat, text(d, "carNo"), text(d, "deptCd"), text(d, "equipType"), text(d, "equipLoc"), text(d, "equipId"), text(d, "incdNo"),
                text(d, "jobPrtCtlNo"), text(d, "sentFrom"), subs.size() + 1, subs.isEmpty() ? "" : subs.get(subs.size() - 1).repairArea(), subs);
    }

    public record Comment(int number, String text, String date, String time, String userGroup) {}

    public record SubJobDetail(String job, int doc, String status, String workType, String repairArea, String dateIn, String stockNo, String serialNo,
                               long quantity, List<Comment> comments) {}

    /** VHSR08IS-RET-NARR: one sub job and its job comments (VHSR08B); the date in shown as MM-DD-YYYY */
    public SubJobDetail subJob(String jobNo, int doc) {
        String job = SecondaryRepairForms.upper(jobNo);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("subJobDocNo", doc);
        crit.put("jobNo", job);
        ProgramResult r = programs.attempt("VHSR08B", Map.of("VHSR08B_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error("VHSR08B", r.control());
        Map<String, Object> d = r.record("VHSR08B_DATA");
        List<Comment> comments = new ArrayList<>();
        if (urc != 100) {
            for (Map<String, Object> row : rows(r, "VHSR08B_DATA", "dataRow")) {
                comments.add(new Comment((int) number(row, "narrNo"), text(row, "narr"), text(row, "dateNarr"), text(row, "timeNarr"), text(row, "userGrp")));
            }
        }
        String in = SecondaryRepairForms.flat(d == null ? null : d.get("dateIn"));
        String shown = in.length() == 10 ? in.substring(5, 7) + "-" + in.substring(8, 10) + "-" + in.substring(0, 4) : in;
        return new SubJobDetail(job, doc, text(d, "subJobStat"), text(d, "workType"), text(d, "reprArea"), shown, text(d, "stockNo"), text(d, "serNo"),
                number(d, "cntCmpntsIn"), comments);
    }

    /** a new sub job: the date in as ISO yyyy-MM-dd (blank: today) */
    public record NewSubJob(String workType, String dateIn, String repairArea, String stockNo, String serialNo, Integer quantity, String comment) {}

    public record Created(int doc, String message, boolean overThreshold, Job job) {}

    /** VHSR08IS-CREATE + DB-STORE: the shared checks, then the sub job as the next number (VHSR08C) */
    public Created create(String jobNo, NewSubJob n, String userGroup, String terminal) {
        Job job = job(jobNo);
        LocalDate today = LocalDate.now(clock);
        String date = n.dateIn() == null || n.dateIn().isBlank() ? today.format(SecondaryRepairForms.SCREEN_DATE) : SecondaryRepairForms.screenDate(n.dateIn());
        var in = new SecondaryRepairJobChecks.Input(SecondaryRepairForms.upper(n.workType()), date, true, SecondaryRepairForms.upper(n.repairArea()),
                SecondaryRepairForms.upper(n.stockNo()), n.quantity() == null ? 1 : n.quantity());
        List<String> errors = new ArrayList<>();
        checks.first(in, today, errors);
        boolean over = checks.last(in, errors);
        SecondaryRepairForms.fail(errors, checks.width, checks.maxErrors);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobNo", job.job());
        data.put("subJobDocNo", job.nextDoc());
        data.put("workType", in.workType());
        data.put("dateIn", date);
        data.put("reprArea", in.repairArea());
        data.put("cntCmpntsIn", in.quantity());
        data.put("stockNo", in.stockNo());
        data.put("serNo", SecondaryRepairForms.strip(n.serialNo()));
        data.put("userGrp", userGroup == null ? "" : userGroup);
        data.put("termLastUpd", terminal == null ? "" : terminal);
        data.put("narr", n.comment() == null ? "" : n.comment());
        ProgramResult r = programs.attempt("VHSR08C", Map.of("VHSR08C_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHSR08C", r.control());
        String message = (docNo + String.format("%02d", job.nextDoc() % 100) + forJob + String.format("%-6s", job.job()) + created
                + (over ? warning : "")).strip();
        return new Created(job.nextDoc(), message, over, job(job.job()));
    }
}
