package com.bart.maris.middleware.domain.yardreports;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Move a pseudo train's cars to another yard (old screen VHIC03I, opened from VHYT11I with the cars' yard and positions).
 * The rules are MRSYardTrackingReport/RUIFunctions.egl VHIC03IS-INIT (the yards, VHYT09A), VHIC03IS-MOVE (the checks) and
 * VHIC03IS-MOVEX (VHIC03A, which writes). The old screen's texts are read from the screen bundle.
 */
public class YardReportsMoveService {
    private static final String PKG = "MRSYardTrackingReport";

    private final Programs programs;
    private final String terminalId;
    private final String sameYard, noYard, noRoom, movedFrom, movedTo;

    public YardReportsMoveService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, String terminalId) {
        this.programs = new Programs(legacy, messages);
        this.terminalId = terminalId;
        List<String> move = YardReportsLayout.assignedTexts(screens, "VHIC03IS-MOVE", PKG, "MSG");
        if (move.size() != 2) throw new IllegalStateException("VHIC03IS-MOVE no longer has its two messages: " + move);
        this.sameYard = move.get(0).strip();
        this.noYard = move.get(1).strip();
        this.noRoom = YardReportsLayout.assignedTexts(screens, "VHIC03IS-MOVEX", PKG, "MSG").get(0).strip();
        this.movedFrom = YardReportsLayout.assignedTexts(screens, "VHIC03IS-MOVEX", PKG, "MSGY-1").get(0);
        this.movedTo = YardReportsLayout.assignedTexts(screens, "VHIC03IS-MOVEX", PKG, "MSGY-3").get(0);
    }

    /** VHIC03IS-INIT: the yards the cars can move to (VHYT09A, its criteria left empty as the old screen does) */
    public List<String> yards() {
        ProgramResult r = programs.run("VHYT09A", Map.of("VHYT09A_CRIT", Map.of()));
        List<String> out = new ArrayList<>();
        if (r.control().returnCode() != 100) for (Map<String, Object> row : rows(r, "VHYT09A_DATA", "dataRow")) out.add(text(row, "yrd"));
        return out;
    }

    /** VHIC03IS-MOVE + -MOVEX: moves the cars at fromPosition..toPosition of the pseudo train in fromYard to toYard */
    public YardReportsMove move(String fromYard, int fromPosition, int toPosition, String toYard, String userGroup) {
        String from = fromYard == null ? "" : fromYard.strip(), to = toYard == null ? "" : toYard.strip();
        if (from.equals(to)) throw new InvalidRequestException(sameYard);
        if (to.isEmpty()) throw new InvalidRequestException(noYard);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("toYrdTrkgYrd", to);
        crit.put("fromPosnNo", fromPosition);
        crit.put("toPosnNo", toPosition);
        crit.put("fromYrdTrkgYrd", from);
        crit.put("termId", terminalId);
        crit.put("userGroup", userGroup);
        ProgramResult r = programs.run("VHIC03A", Map.of("VHIC03A_CRIT", crit));
        // the old screen treats only URC 0 and 4 as done; 100 shows the program's message too
        if (r.control().returnCode() == 100) throw programs.error("VHIC03A", r.control());
        if (text(r.record("VHIC03A_CRIT"), "errorSw").equals("Y")) return new YardReportsMove(false, noRoom);
        return new YardReportsMove(true, (movedFrom + from + movedTo + to).replaceAll("\\s+", " ").strip());
    }
}
