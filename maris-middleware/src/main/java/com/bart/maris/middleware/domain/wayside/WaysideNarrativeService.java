package com.bart.maris.middleware.domain.wayside;

import static com.bart.maris.middleware.domain.common.Programs.number;
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
 * The narratives of a wayside incident, repair or PM (old WS13I). Rules from MRSWaysideMaintenance RUIFunctions:
 * WS13IS-ADDNARR (adds the next narrative: WS13A07 repair, WS13A04 incident, WS13A05 PM), WS13IS-RETR-REPAIR /
 * -RETR-INCD / -RETR-PM (the latest one: WS13A08, WSX2A01, WSX3A) and WS13IS-GET-NARR (Prv / Nxt: WS13A06, WS13A02,
 * WS13A01). The context decides which: a repair document number, else an incident, else a PM (department, equipment,
 * location, cycle, scheduled date).
 */
public final class WaysideNarrativeService {
    private static final String PKG = "MRSWaysideMaintenance";
    private static final int NARRATIVE = 1000;

    private final Programs programs;
    private final String terminal, latestFunction, prevFunction, nextFunction;

    public WaysideNarrativeService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        latestFunction = t.assignedAll("WS13IS-RETR-INCD", PKG, "VAGen_FUNCTION").get(0);
        List<String> steps = t.compared("WS13IS-GET-NARR", PKG, "VAGen_FUNCTION");
        prevFunction = steps.get(0);
        nextFunction = steps.get(1);
    }

    /** what the narratives belong to; @param repairDoc 0 when not a repair */
    public record Context(String incident, int repairDoc, String pmDept, String pmEquipment, String pmLocation, String pmCycle, String pmScheduled) {
        public Context {
            incident = s(incident);
            pmDept = s(pmDept);
            pmEquipment = s(pmEquipment);
            pmLocation = s(pmLocation);
            pmCycle = s(pmCycle);
            pmScheduled = s(pmScheduled);
        }

        boolean repair() { return repairDoc != 0; }

        boolean incidentOnly() { return !incident.isEmpty() && repairDoc == 0; }

        boolean pm() { return !pmDept.isEmpty() && incident.isEmpty(); }
    }

    /** one narrative; @param row its number, @param maxRows how many there are; Prv / Nxt as the old buttons allow */
    public record Narrative(String text, String date, String time, String userGroup, int row, int maxRows, boolean hasPrev, boolean hasNext) {}

    /** the latest narrative (what WS13I shows after adding) */
    public Narrative latest(Context c) {
        check(c);
        if (c.repair()) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("incdNo", c.incident());
            crit.put("reprDocNo", c.repairDoc());
            crit.put("narrNo", 0);
            return read("WS13A08", Map.of("WS13A08_CRIT", crit), "WS13A08_DATA", "narrNo");
        }
        if (c.incidentOnly()) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("incdNo", c.incident());
            crit.put("narrNo", 0);
            crit.put("vagenFunction", latestFunction);
            return read("WSX2A01", Map.of("WSX2A01_CRIT", crit), "WSX2A01_DATA", "maxRows");
        }
        Map<String, Object> crit = pmKeys(c);
        crit.put("narrNo", 0);
        crit.put("vagenFunction", latestFunction);
        return read("WSX3A", Map.of("WSX3A_CRIT", crit), "WSX3A_DATA", "narrNo");
    }

    /** WS13IS-GET-NARR: the one before (prev, never below 1) or after (next, never past the last) the row shown */
    public Narrative step(Context c, String direction, int row, int maxRows) {
        check(c);
        boolean prev = "prev".equalsIgnoreCase(direction);
        if (!prev && !"next".equalsIgnoreCase(direction)) throw new InvalidRequestException("direction is prev or next.");
        int target = prev ? Math.max(1, row - 1) : Math.min(row + 1, maxRows);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("currentRow", target);
        crit.put("maxRows", maxRows);
        crit.put("narrNo", target);
        Narrative n;
        if (c.repair()) {
            crit.put("incdNo", c.incident());
            crit.put("reprDocNo", c.repairDoc());
            n = read("WS13A06", Map.of("WS13A06_CRIT", crit), "WS13A06_DATA", "narrNo");
        } else if (c.incidentOnly()) {
            crit.put("incdNo", c.incident());
            n = read("WS13A02", Map.of("WS13A02_CRIT", crit), "WS13A02_DATA", "narrNo");
        } else {
            crit.putAll(pmKeys(c));
            crit.put("vagenFunction", prev ? prevFunction : nextFunction);
            n = read("WS13A01", Map.of("WS13A01_CRIT", crit), "WS13A01_DATA", "narrNo");
        }
        return new Narrative(n.text(), n.date(), n.time(), n.userGroup(), n.row(), maxRows, n.row() > 1, n.row() < maxRows);
    }

    /** WS13IS-ADDNARR: adds the next narrative (after the latest one), then answers the latest */
    public Narrative add(Context c, String text, String userGroup) {
        check(c);
        String narr = text == null ? "" : text.stripTrailing();
        if (narr.isBlank()) throw new InvalidRequestException("Enter the narrative to add.");
        if (narr.length() > NARRATIVE) throw new InvalidRequestException("A narrative holds up to " + NARRATIVE + " characters.");
        int next = latest(c).maxRows() + 1;
        Map<String, Object> data = new LinkedHashMap<>();
        String program;
        if (c.repair()) {
            program = "WS13A07";
            data.put("incdNo", c.incident());
            data.put("reprDocNo", c.repairDoc());
        } else if (c.incidentOnly()) {
            program = "WS13A04";
            data.put("incdNo", c.incident());
        } else {
            program = "WS13A05";
            data.putAll(pmKeys(c));
        }
        data.put("narrNo", next);
        data.put("narr", narr);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        data.put("termNarr", terminal);
        programs.run(program, Map.of(program + "_DATA", data));
        return latest(c);
    }

    private Narrative read(String program, Map<String, ?> inputs, String data, String rowField) {
        ProgramResult r = programs.run(program, inputs);
        Map<String, Object> d = r.record(data);
        int row = (int) number(d, rowField);
        int max = rowField.equals("maxRows") ? row : Math.max(row, (int) number(d, "maxRows"));
        return new Narrative(text(d, "narr"), text(d, "dateNarr"), text(d, "timeNarr"), text(d, "userGrp"), row, max, row > 1, row < max);
    }

    private static Map<String, Object> pmKeys(Context c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pmDeptCd", c.pmDept());
        m.put("pmEquipId", c.pmEquipment());
        m.put("pmLoc", c.pmLocation());
        m.put("pmCycleCd", c.pmCycle());
        m.put("datePmSchd", c.pmScheduled());
        return m;
    }

    private static void check(Context c) {
        if (!c.repair() && !c.incidentOnly() && !c.pm()) throw new InvalidRequestException("Give an incident, a repair document or a PM (department and equipment).");
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
