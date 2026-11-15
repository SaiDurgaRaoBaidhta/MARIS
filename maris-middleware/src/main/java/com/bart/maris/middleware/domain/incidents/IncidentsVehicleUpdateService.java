package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
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
 * Changing a vehicle incident (old VHINC09I, "Change Incident - Service"): List reads it (VHINC1D "BOTH"); yard, shop,
 * event, misc and revenue incidents can change their symptom, location (and for revenue incidents speed and
 * direction); an open H incident only takes narratives; a train incident (car 0095) or any other type is refused with
 * the old screen's message. Update checks like VHINC09IS-CHK-DATA and writes with VHINC1C.
 */
public final class IncidentsVehicleUpdateService {
    private final Programs programs;
    private final IncidentsVehicleSupport support;
    private final String terminal;
    private final String trainMessage, notFound, invalidIncident, typeText1, typeText3, both, trainCar, hPrefix, closed, revType, all, deferred;
    private final String ok1, ok3;
    private final List<String> editableTypes, digits, messages;
    private final int width, tempLength, finalLength, maxMessages;

    public IncidentsVehicleUpdateService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle bundle, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.support = new IncidentsVehicleSupport(bundle);
        this.terminal = terminal == null ? "" : terminal;
        EglTexts t = support.texts();
        List<String> finals = t.assignedAll("VHINC09IS-RET-INCD", PKG, "FINAL-MESSAGE");
        if (finals.size() != 3) throw new IllegalStateException("VHINC09IS-RET-INCD has " + finals.size() + " messages, expected 3");
        trainMessage = finals.get(0);
        notFound = finals.get(1);
        invalidIncident = finals.get(2);
        typeText1 = t.assignedRaw("VHINC09IS-RET-INCD", PKG, "INCD-MESSAGE-1").get(0);
        typeText3 = t.assignedRaw("VHINC09IS-RET-INCD", PKG, "INCD-MESSAGE-3").get(0);
        both = t.assigned("VHINC09IS-RET-INCD", PKG, "RET-CHECK", null);
        editableTypes = t.compared("VHINC09IS-RET-INCD", PKG, "INCD-TYPE");
        trainCar = t.compared("VHINC09IS-RET-INCD", PKG, "CAR-NO").get(0);
        hPrefix = t.compared("VHINC09IS-RET-INCD", PKG, "INCD-NO-1").get(0);
        closed = t.compared("VHINC09IS-RET-INCD", PKG, "VEH-INCD-STAT").get(0);
        revType = t.compared("VHINC09IS-CHK-DATA", PKG, "INCD-TYPE").get(0);
        all = t.compared("VHINC09IS-CHK-DATA", PKG, "SPEED").get(0);
        digits = t.compared("VHINC09IS-CHK-DATA", PKG, "TEMP-SPEED-CHK-CHAR");
        deferred = t.compared("VHINC09IS-INCD-DET", PKG, "VEH-INCD-STAT").get(0);
        this.messages = t.assignedAll("VHINC09IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR");
        if (this.messages.size() != 7) throw new IllegalStateException("VHINC09IS-BLD-MSG has " + this.messages.size() + " messages, expected 7");
        ok1 = t.assignedRaw("VHINC09IS-STORE-DB", PKG, "FINAL-MESSAGE-1").get(0);
        ok3 = t.assignedRaw("VHINC09IS-STORE-DB", PKG, "FINAL-MESSAGE-3").get(0);
        width = support.limit("VHINC09IS-MOVE-MSG", "ERROR-INDEX");
        maxMessages = support.limit("VHINC09IS-MOVE-MSG", "ERROR-LOOP-INDEX");
        tempLength = support.length("VHINC09IW", "TEMP-ERROR-MSG");
        finalLength = support.length("VHINC09IW", "FINAL-ERROR-MSG");
    }

    /** what may change: everything the old screen enables after List */
    public record Editing(boolean details, boolean speedAndDirection, boolean deferredPriority, boolean narratives) {}

    public record Incident(IncidentsVehicleSupport.Details incident, Editing editing) {}

    private record Read(IncidentsVehicleSupport.Details details, Editing editing) {}

    /** List: VHINC09IS-RET-INCD */
    public Incident incident(String incident) {
        Read r = read(incident);
        return new Incident(r.details(), r.editing());
    }

    private Read read(String incident) {
        String no = incident == null ? "" : incident.strip();
        if (no.isEmpty()) throw new InvalidRequestException(invalidIncident);
        Programs.Outcome o = IncidentsVehicleSupport.readIncident(programs, no, both);
        if (!o.ok()) throw new InvalidRequestException(notFound);
        ProgramResult res = o.result();
        Map<String, Object> d = res.record("VHINC1D_DATA");
        String type = Programs.text(d, "incdType"), status = Programs.text(d, "vehIncdStat");
        if (editableTypes.contains(type)) {
            if (Programs.text(d, "carNo").equals(trainCar)) throw new InvalidRequestException(trainMessage);
            var details = support.details(programs, no, res, "VHINC09IW", true);
            return new Read(details, new Editing(true, type.equals(revType), status.equals(deferred), true));
        }
        if (no.startsWith(hPrefix) && !status.equals(closed)) {
            var details = support.details(programs, no, res, "VHINC09IW", false);
            return new Read(details, new Editing(false, type.equals(revType), status.equals(deferred), true));
        }
        String m = support.fixed("VHINC09IW-TRIGGER", List.of(Map.entry("INCD-MESSAGE-1", typeText1), Map.entry("INCD-MESSAGE-2", type),
                Map.entry("INCD-MESSAGE-3", typeText3)));
        throw new InvalidRequestException(m);
    }

    /** the changes; {@code narrativeIndex} is the narrative the window shows (0 = the first), its text as edited */
    public record Change(String symptom, String speed, String direction, String location, String narrative, Integer narrativeIndex) {}

    /** Update: VHINC09IS-CHK-DATA, then VHINC09IS-STORE-DB (VHINC1C) */
    public Map<String, String> update(String incident, Change c) {
        Read read = read(incident);
        if (!read.editing().details()) {
            throw new InvalidRequestException(support.fixed("VHINC09IW-TRIGGER", List.of(Map.entry("INCD-MESSAGE-1", typeText1),
                    Map.entry("INCD-MESSAGE-2", read.details().type()), Map.entry("INCD-MESSAGE-3", typeText3))));
        }
        var d = read.details();
        boolean rev = read.editing().speedAndDirection();
        String symptom = trim(c.symptom()), location = trim(c.location());
        String speed = rev ? trim(c.speed()) : d.speed(), direction = rev ? trim(c.direction()) : d.direction();
        List<String> out = new ArrayList<>();
        boolean invSymp = symptom.isEmpty(), invSpeed = false, invSpeedVal = false, invDir = false, invLoc = location.isEmpty(), sympMissing = false;
        if (rev) {
            if (speed.isEmpty()) invSpeed = true;
            else if (!speed.equals(all)) {
                String s3 = String.format("%-3s", speed).substring(0, 3);
                for (char ch : s3.toCharArray()) if (ch != ' ' && !digits.contains(String.valueOf(ch))) { invSpeedVal = true; break; }
            }
            if (direction.isEmpty()) invDir = true;
        }
        if (!invSymp) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("vagenCount", 0);
            crit.put("sympCd", symptom);
            Programs.Outcome so = programs.attempt("VHINC1A", Map.of("VHINC1A_CRIT", crit));
            if (so.ok() && number(so.result().record("VHINC1A_CRIT"), "vagenCount") == 0) sympMissing = true;
        }
        if (invSymp || invSpeed || invSpeedVal || invDir || invLoc || sympMissing) {
            if (invSymp) out.add(messages.get(0));
            else if (sympMissing) out.add(messages.get(1));
            if (rev) {
                if (speed.isEmpty()) { if (invSpeed) out.add(messages.get(2)); }
                else if (invSpeedVal) out.add(messages.get(3));
                if (invDir) out.add(messages.get(4));
            }
            if (out.size() < maxMessages && invLoc) out.add(messages.get(5));
            throw new InvalidRequestException(IncidentsVehicleSupport.join(out, width, tempLength, finalLength, maxMessages));
        }
        var narrs = d.narratives();
        int idx = c.narrativeIndex() == null ? 0 : c.narrativeIndex();
        IncidentsVehicleSupport.Narrative shown = idx >= 0 && idx < narrs.size() ? narrs.get(idx) : null;
        String text = c.narrative() == null ? "" : c.narrative().stripTrailing();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", d.incident());
        crit.put("narrChg", text.equals(shown == null ? "" : shown.text()) ? "N" : "Y");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("speed", speed);
        data.put("sympCd", symptom);
        data.put("conDirection", direction);
        data.put("incdLoc", location);
        data.put("termLastUpd", terminal);
        data.put("userGrp", shown == null ? "" : shown.userGroup());
        ProgramResult r = programs.attempt("VHINC1C", Map.of("VHINC1C_CRIT", crit, "VHINC1C_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHINC1C", r.control());
        String message = support.fixed("VHINC09IW", List.of(Map.entry("FINAL-MESSAGE-1", ok1), Map.entry("FINAL-MESSAGE-2", d.incident()),
                Map.entry("FINAL-MESSAGE-3", ok3)));
        return Map.of("message", message);
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }
}
