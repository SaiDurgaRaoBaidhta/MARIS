package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;
import static com.bart.maris.middleware.domain.incidents.IncidentsVehicleSupport.PKG;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
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
 * Vehicle incident maintenance (old VHINC10I, "Vehicle Incd Maint"): any incident can be listed (VHINC1D "BOTH") and
 * every field rewritten, status, type, discovered duration and cumulative hours included (VHINCXC). The status list is
 * the signed-on group's VH01A01 incident statuses; the only check is that a status is chosen.
 */
public final class IncidentsVehicleMaintainService {
    private static final String SCREEN = "VHINC10I";

    private final Programs programs;
    private final IncidentsVehicleSupport support;
    private final String terminal;
    private final String both, notFound, statusMissing, updated;
    private final List<String> durations;
    private final int revenueIndex;

    public IncidentsVehicleMaintainService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle bundle, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.support = new IncidentsVehicleSupport(bundle);
        this.terminal = terminal == null ? "" : terminal;
        EglTexts t = support.texts();
        both = t.assigned("VHINC10IS-RET-INCD", PKG, "RET-CHECK", null);
        notFound = t.triggeredPrompt(SCREEN, "INCD-NOT-FOUND");
        statusMissing = t.triggeredPrompt(SCREEN, "STATUS-ERR");
        updated = t.triggeredPrompt(SCREEN, "UPDATE-MSG");
        durations = new ArrayList<>(t.indexed("VHINC10IS-CLR-FLDS", PKG, "DISCV-DUR-ARRAY").values());
        revenueIndex = t.assignedNumber("VHINC10IS-ENTRY", PKG, "SEL-IDX").intValue();
    }

    public record Status(String code, String description) {}

    public record Options(List<Status> statuses, List<String> durations, String revenueDate) {}

    /** VHINC10IS-ENTRY with the menu's VH01A01 data: the status choices, discovered durations and the revenue date */
    public Options options(String userGroup) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("userGroup", userGroup == null ? "" : userGroup);
        d.put("termId", terminal);
        ProgramResult a = programs.run("VH01A01", Map.of("VH01A01_DATA", d));
        Map<String, Object> data = a.record("VH01A01_DATA");
        int n = (int) number(data, "numVehIncdStat");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = data != null && data.get("vehIncdStatRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<Status> statuses = new ArrayList<>();
        for (int i = 0; i < Math.min(n, rows.size()); i++) statuses.add(new Status(text(rows.get(i), "vehIncdStat"), text(rows.get(i), "vehIncdStatDesc")));
        Programs.Outcome r = programs.attempt("VHXREVA", Map.of("VHXREVA_CRIT", Map.of("selIdx", revenueIndex)));
        String rev = r.ok() ? support.group("VHXREVA_DATA", "revenueDate", r.result().record("VHXREVA_DATA").get("revenueDate")) : "";
        return new Options(statuses, durations, rev);
    }

    /** List: VHINC10IS-RET-INCD */
    public IncidentsVehicleSupport.Details incident(String incident) {
        String no = incident == null ? "" : incident.strip();
        if (no.isEmpty()) throw new InvalidRequestException(notFound);
        Programs.Outcome o = IncidentsVehicleSupport.readIncident(programs, no, both);
        if (!o.ok()) throw new InvalidRequestException(notFound);
        return support.details(programs, no, o.result(), "VHINC10IW", true);
    }

    public record Change(String status, String type, String symptom, String duration, String speed, String direction, String location,
                         String deferredPriority, Long cumulativeHours, Long cumulativeMinutes, String narrative, Integer narrativeIndex) {}

    /** Update: VHINC10IS-CHK-DATA, then VHINC10IS-MOVE (VHINCXC) */
    public Map<String, String> update(String incident, Change c) {
        if (c.status() == null || c.status().isBlank()) throw new InvalidRequestException(statusMissing);
        var d = incident(incident);
        var narrs = d.narratives();
        int idx = c.narrativeIndex() == null ? 0 : c.narrativeIndex();
        IncidentsVehicleSupport.Narrative shown = idx >= 0 && idx < narrs.size() ? narrs.get(idx) : null;
        String text = c.narrative() == null ? "" : c.narrative().stripTrailing();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", d.incident());
        crit.put("narrChg", text.equals(shown == null ? "" : shown.text()) ? "N" : "Y");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("speed", trim(c.speed()));
        data.put("sympCd", trim(c.symptom()));
        data.put("conDirection", trim(c.direction()));
        data.put("incdLoc", trim(c.location()));
        data.put("termLastUpd", terminal);
        data.put("userGrp", shown == null ? "" : shown.userGroup());
        data.put("vehIncdStat", trim(c.status()));
        data.put("incdType", trim(c.type()));
        data.put("deferredPriority", trim(c.deferredPriority()));
        data.put("discvDur", trim(c.duration()));
        data.put("cumHrs", c.cumulativeHours() == null ? 0 : c.cumulativeHours());
        data.put("cumMin", c.cumulativeMinutes() == null ? 0 : c.cumulativeMinutes());
        ProgramResult r = programs.attempt("VHINCXC", Map.of("VHINCXC_CRIT", crit, "VHINCXC_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHINCXC", r.control());
        return Map.of("message", updated);
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }
}
