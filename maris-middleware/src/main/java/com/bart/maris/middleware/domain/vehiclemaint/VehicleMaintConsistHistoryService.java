package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consist history maintenance (old VHVM19I): the consists in a number range on a revenue date (VHVM19A) and a change
 * of one history record's status and ICS details (VHVM19B). The revenue date starts as today's or yesterday's revenue
 * day (VHYT34A, VHVM19IS-REV-DATE). Dates are shown MM-DD-YYYY and sent YYYY-MM-DD, as XXDATES-CONVERT does.
 */
public final class VehicleMaintConsistHistoryService {
    private static final String SCREEN = "VHVM19I", PKG = "MRSVehicleMaintenance";
    private static final int MAX_ROWS = 150;
    /** the function VHVM19IS-UPDATE puts in XDCONTROL before the call */
    private final String updateFunction;

    private final Programs programs;
    private final String terminal;
    private final List<String> statuses;
    private final List<VehicleMaintConsistTable.Column> columns;
    private final String noData, tooMany, updated;

    public VehicleMaintConsistHistoryService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        statuses = t.items(SCREEN, "Drop-down_List2");
        columns = VehicleMaintConsistTable.columns(t, SCREEN);
        List<String> msgs = t.assignedAll("VHVM19IS-RET-DATA", PKG, "MSG");
        noData = msgs.get(0);
        tooMany = msgs.get(1);
        updated = t.assignedAll("VHVM19IS-MOD-DATA", PKG, "MSG").get(0);
        updateFunction = t.assigned("VHVM19IS-UPDATE", PKG, "UFUNCTION", null);
    }

    public record Options(String revDate, List<String> statuses, List<VehicleMaintConsistTable.Column> columns) {}

    /** VHVM19IS-REV-DATE: the current revenue date (MM-DD-YYYY), and the screen's choices */
    public Options options() {
        Programs.Outcome o = programs.attempt("VHYT34A", Map.of());
        Map<String, Object> d = o.result().record("VHYT34A_DATA");
        String rev = text(d, "currentTime").compareTo(text(d, "timeRevEnd")) > 0 ? text(d, "currentDate") : text(d, "yesterday");
        return new Options(VehicleMaintConsistTable.mdy(rev), statuses, columns);
    }

    public record History(List<Map<String, Object>> rows, String message) {}

    /** VHVM19IS-RET-DATA: nothing without a begin consist; the end consist defaults to the begin consist */
    public History history(String from, String to, String revDate) {
        String beg = s(from), end = s(to);
        if (beg.isEmpty()) return new History(List.of(), "");
        if (end.isEmpty()) end = beg;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNoBeg", beg);
        crit.put("conNoEnd", end);
        crit.put("revDate", VehicleMaintConsistTable.ymd(s(revDate)));
        Programs.Outcome o = programs.attempt("VHVM19A", Map.of("VHVM19A_CRIT", crit));
        ProgramResult r = o.result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (!o.ok() && urc != 100) throw programs.error("VHVM19A", r.control());
        List<Map<String, Object>> rows = o.ok() ? VehicleMaintConsistTable.rows(r, "VHVM19A_DATA") : List.of();
        String message = VehicleMaintConsistTable.full(r, "VHVM19A_DATA", MAX_ROWS) ? tooMany : o.ok() ? "" : noData;
        return new History(rows, message);
    }

    /** a change to a selected history record: its key (from its row) and the fields the screen lets the user edit */
    public record Change(String conNo, long dashNo, String dateInsrv, String timeInsrv, String dateInsrvRev, String status, String dateInsrvIcs,
                         String timeInsrvIcs, String noCarsInd) {}

    /** VHVM19IS-MOD-DATA (UPDATE): VHVM19B; a status is required */
    public String update(Change c) {
        String stat = s(c.status());
        if (stat.isEmpty() || !statuses.contains(stat)) throw new InvalidRequestException("Choose a consist status: " + String.join(", ", statuses) + ".");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", s(c.conNo()));
        crit.put("dashNo", c.dashNo());
        crit.put("dateInsrvRev", VehicleMaintConsistTable.ymd(s(c.dateInsrvRev())));
        crit.put("dateInsrv", VehicleMaintConsistTable.ymd(s(c.dateInsrv())));
        crit.put("timeInsrv", s(c.timeInsrv()));
        crit.put("termLastUpd", terminal);
        crit.put("termMaintUpd", terminal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conStat", stat);
        String ics = s(c.dateInsrvIcs());
        data.put("dateInsrvIcs", ics.isEmpty() ? "" : VehicleMaintConsistTable.ymd(ics));
        data.put("noCarsInd", s(c.noCarsInd()));
        data.put("timeInsrvIcs", s(c.timeInsrvIcs()));
        Programs.Outcome o = programs.attempt("VHVM19B", Map.of("XDCONTROL", Map.of("ufunction", updateFunction), "VHVM19B_CRIT", crit, "VHVM19B_DATA", data));
        if (!o.ok()) throw programs.error("VHVM19B", o.result().control());
        return updated;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
