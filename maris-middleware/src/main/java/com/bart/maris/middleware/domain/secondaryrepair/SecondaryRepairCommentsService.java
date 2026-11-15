package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job and repair comments of a sub job (old VHSR02I, opened from the menu, the job split VHSR08I and the labor detail
 * VHSR03I): list the latest comment (VHSR02E, VHSR02F), page through them (VHSR02C), add one (VHSR02A, then VHSR02D
 * reads it back) and change one (VHSR02B). Rules from MRSSecondaryRepair/RUIFunctions.egl, VHSR02IS-*.
 */
public class SecondaryRepairCommentsService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final String jobRequired, docRequired, badSelection, noSuchDoc, invalidComment, addedJob, addedRepair, updatedJob, updatedRepair;
    /** the comment kinds the old radio buttons select: 1 = job, 2 = repair */
    private final List<String> kinds;
    private final int width, maxMessages;

    public SecondaryRepairCommentsService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        jobRequired = t.assigned("VHSR02IS-LIST", PKG, "ERROR-MESSAGE-OCCR", "Job no");
        docRequired = t.assigned("VHSR02IS-LIST", PKG, "ERROR-MESSAGE-OCCR", "Doc no");
        badSelection = t.assigned("VHSR02IS-LIST", PKG, "ERROR-MESSAGE-OCCR", "selection");
        noSuchDoc = t.assigned("VHSR02IS-LIST", PKG, "FINAL-MESSAGE", "does not exist");
        invalidComment = t.assigned("VHSR02IS-ADDNARR", PKG, "FINAL-MESSAGE", "comment");
        addedJob = t.assigned("VHSR02IS-JOB-NARR", PKG, "FINAL-MESSAGE", "Job comment");
        addedRepair = t.assigned("VHSR02IS-JOB-NARR", PKG, "FINAL-MESSAGE", "repr comment");
        updatedJob = t.assigned("VHSR02IS-UPDNARR", PKG, "FINAL-MESSAGE", "Job comment updated successfully");
        updatedRepair = t.assigned("VHSR02IS-UPDNARR", PKG, "FINAL-MESSAGE", "repr comment updated successfully");
        kinds = t.assignedAll("VHSR02IS-LIST", PKG, "JOB-INDICATOR").stream().distinct().toList();
        width = SecondaryRepairForms.limit(t, "VHSR02IS-MOVE-MSG", "ERROR-COUNT");
        maxMessages = SecondaryRepairForms.limit(t, "VHSR02IS-MOVE-MSG", "ERROR-LOOP-INDEX");
    }

    public record Comment(int number, String text, String userGroup, String date, String time) {}

    /** @param count how many comments the sub job has; @param comment the one shown (the old screen's first), none when 0 */
    public record Comments(String job, int doc, String kind, int count, Comment comment) {}

    /** the kinds (JOB, REPAIR) */
    public List<String> kinds() {
        return kinds;
    }

    /** VHSR02IS-LIST: the checks, the sub job must exist (VHSR02E), then its comments of that kind (VHSR02F) */
    public Comments list(String jobNo, Integer doc, String kind) {
        String job = SecondaryRepairForms.upper(jobNo), k = SecondaryRepairForms.upper(kind);
        int d = doc == null ? 0 : doc;
        List<String> errors = new ArrayList<>();
        if (job.isEmpty()) errors.add(jobRequired);
        if (d < 1) errors.add(docRequired);
        if (!kinds.contains(k)) errors.add(badSelection);
        SecondaryRepairForms.fail(errors, width, maxMessages);
        ProgramResult e = programs.attempt("VHSR02E", Map.of("VHSR02E_CRIT", Map.of("jobNo", job, "subJobDocNo", d))).result();
        if (e.control() != null && e.control().rowCount() != 1) {
            if (e.control().returnCode() == 100) throw new InvalidRequestException(noSuchDoc);
            throw programs.error("VHSR02E", e.control());
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("jobNo", job);
        crit.put("subJobDocNo", d);
        crit.put("jobIndicator", k);
        ProgramResult f = programs.attempt("VHSR02F", Map.of("VHSR02F_CRIT", crit)).result();
        if (f.control() != null && f.control().returnCode() != 0 && f.control().returnCode() != 4) throw programs.error("VHSR02F", f.control());
        Map<String, Object> data = f.record("VHSR02F_DATA");
        int max = (int) number(data, "maxRows");
        if (max < 1) return new Comments(job, d, k, 0, null);
        return new Comments(job, d, k, max, new Comment((int) number(data, "narrNo"), text(data, "narr"), text(data, "userGrp"), text(data, "dateNarr"),
                text(data, "timeNarr")));
    }

    /** VHSR02IS-PRV-NXT: one comment by its number (VHSR02C) */
    public Comment comment(String jobNo, int doc, String kind, int number) {
        String job = SecondaryRepairForms.upper(jobNo), k = kind(kind);
        ProgramResult r = programs.attempt("VHSR02C", Map.of("VHSR02C_CRIT", key(job, doc, k, number))).result();
        if (r.control() != null && r.control().returnCode() != 0 && r.control().returnCode() != 4) throw programs.error("VHSR02C", r.control());
        Map<String, Object> data = r.record("VHSR02C_DATA");
        return new Comment(number, text(data, "narr"), text(data, "userGrp"), text(data, "dateNarr"), text(data, "timeNarr"));
    }

    public record Added(String message, Comments comments) {}

    /**
     * VHSR02IS-ADDNARR: a non-blank comment is added as the next number (VHSR02A), then VHSR02IS-JOB-NARR reads the
     * newest back (VHSR02D)
     */
    public Added add(String jobNo, Integer doc, String kind, String text, String userGroup, String terminal) {
        if (text == null || text.isBlank()) throw new InvalidRequestException(invalidComment);
        Comments now = list(jobNo, doc, kind);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobNo", now.job());
        data.put("subJobDocNo", now.doc());
        data.put("narrNo", now.count() + 1);
        data.put("narr", text);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        data.put("termNarr", terminal == null ? "" : terminal);
        data.put("jobIndicator", now.kind());
        ProgramResult a = programs.attempt("VHSR02A", Map.of("VHSR02A_DATA", data)).result();
        if (a.control() != null && a.control().returnCode() != 0 && a.control().returnCode() != 4) throw programs.error("VHSR02A", a.control());

        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("jobNo", now.job());
        crit.put("subJobDocNo", now.doc());
        crit.put("narrNo", 0);
        crit.put("jobIndicator", now.kind());
        ProgramResult r = programs.attempt("VHSR02D", Map.of("VHSR02D_CRIT", crit)).result();
        if (r.control() != null && r.control().returnCode() != 0 && r.control().returnCode() != 4) throw programs.error("VHSR02D", r.control());
        Map<String, Object> back = r.record("VHSR02D_DATA");
        Comment newest = new Comment((int) number(back, "narrNo"), text(back, "narr"), text(back, "userGrp"), text(back, "dateNarr"), text(back, "timeNarr"));
        String message = now.kind().equals(kinds.get(0)) ? addedJob : addedRepair;
        return new Added(message, new Comments(now.job(), now.doc(), now.kind(), (int) number(back, "currentRow"), newest));
    }

    /** VHSR02IS-UPDNARR: a non-blank text replaces comment {@code number} (VHSR02B) */
    public String update(String jobNo, int doc, String kind, int number, String text, String userGroup, String terminal) {
        if (text == null || text.isBlank()) throw new InvalidRequestException(invalidComment);
        String job = SecondaryRepairForms.upper(jobNo), k = kind(kind);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("narr", text);
        data.put("termNarr", terminal == null ? "" : terminal);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        ProgramResult r = programs.attempt("VHSR02B", Map.of("VHSR02B_CRIT", key(job, doc, k, number), "VHSR02B_DATA", data)).result();
        if (r.control() != null && r.control().returnCode() != 0 && r.control().returnCode() != 4) throw programs.error("VHSR02B", r.control());
        return k.equals(kinds.get(0)) ? updatedJob : updatedRepair;
    }

    private String kind(String kind) {
        String k = SecondaryRepairForms.upper(kind);
        if (!kinds.contains(k)) throw new InvalidRequestException(SecondaryRepairForms.join(List.of(badSelection), width, maxMessages));
        return k;
    }

    private static Map<String, Object> key(String job, int doc, String kind, int number) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("jobNo", job);
        crit.put("narrNo", number);
        crit.put("subJobDocNo", doc);
        crit.put("jobIndicator", kind);
        return crit;
    }
}
