package com.bart.maris.middleware.domain.vehiclemaint;

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
 * Consist status maintenance (old VHVM10I): the consists in a number range (VHVM10A) and a change of one consist's
 * status and ICS details (VHVM10C). Setting a consist out of service with no out-of-service date takes its revenue date
 * (VHVM10IS-OUT-DATE). The old Add button calls no program (its VHVM10B call is commented out), so it is not offered.
 */
public final class VehicleMaintConsistStatusService {
    private static final String SCREEN = "VHVM10I", PKG = "MRSVehicleMaintenance";
    private static final int MAX_ROWS = 182;
    /** the function VHVM10IS-UPDATE puts in XDCONTROL before the call */
    private final String updateFunction;

    private final Programs programs;
    private final String terminal;
    private final List<String> statuses, outStatuses;
    private final List<VehicleMaintConsistTable.Column> columns;
    private final String maxRows, invalidData, updated;

    public VehicleMaintConsistStatusService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        statuses = t.items(SCREEN, "Drop-down_List2");
        outStatuses = t.compared("VHVM10IS-OUT-DATE", PKG, "CON-STAT");
        columns = VehicleMaintConsistTable.columns(t, SCREEN);
        maxRows = t.label(SCREEN, "Label3");
        invalidData = t.triggeredPrompt(SCREEN, "DATA-ERROR");
        updated = t.triggeredPrompt(SCREEN, "UPDATED");
        updateFunction = t.assigned("VHVM10IS-UPDATE", PKG, "UFUNCTION", null);
    }

    public record Options(List<String> statuses, List<VehicleMaintConsistTable.Column> columns) {}

    public Options options() {
        return new Options(statuses, columns);
    }

    /** the rows, and the screen's note when the list is full */
    public record Consists(List<Map<String, Object>> rows, String message) {}

    /** VHVM10IS-RET-DATA: nothing without a begin consist; the end consist defaults to the begin consist */
    public Consists consists(String from, String to) {
        String beg = from == null ? "" : from.strip(), end = to == null ? "" : to.strip();
        if (beg.isEmpty()) return new Consists(List.of(), "");
        if (end.isEmpty()) end = beg;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNoBeg", beg);
        crit.put("conNoEnd", end);
        Programs.Outcome o = programs.attempt("VHVM10A", Map.of("VHVM10A_CRIT", crit));
        if (!o.ok()) throw programs.error("VHVM10A", o.result().control());
        ProgramResult r = o.result();
        return new Consists(VehicleMaintConsistTable.rows(r, "VHVM10A_DATA"), VehicleMaintConsistTable.full(r, "VHVM10A_DATA", MAX_ROWS) ? maxRows : "");
    }

    /** a change to a selected consist: its number, its row's revenue date, and the fields the screen lets the user edit */
    public record Change(String conNo, String status, String dateInsrvIcs, String timeInsrvIcs, String noCarsInd, String dateOutsrv,
                         String dateInsrvRev) {}

    /** VHVM10IS-OUT-DATE and VHVM10IS-MOD-DATA (UPDATE): VHVM10C; a consist number and status are required */
    public String update(Change c) {
        String con = s(c.conNo()), stat = s(c.status());
        if (con.isEmpty() || stat.isEmpty() || !statuses.contains(stat)) throw new InvalidRequestException(invalidData);
        String out = outStatuses.contains(stat) && s(c.dateOutsrv()).isEmpty() ? s(c.dateInsrvRev()) : s(c.dateOutsrv());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conNo", con);
        data.put("conStat", stat);
        data.put("dateInsrvIcs", s(c.dateInsrvIcs()));
        data.put("dateOutsrv", out);
        data.put("termMaintUpd", terminal);
        data.put("timeInsrvIcs", s(c.timeInsrvIcs()));
        data.put("noCarsInd", s(c.noCarsInd()));
        Programs.Outcome o = programs.attempt("VHVM10C", Map.of("XDCONTROL", Map.of("ufunction", updateFunction), "VHVM10C_DATA", data));
        if (!o.ok()) throw programs.error("VHVM10C", o.result().control());
        return updated;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
