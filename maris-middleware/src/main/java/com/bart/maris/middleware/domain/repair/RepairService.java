package com.bart.maris.middleware.domain.repair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;
import static com.bart.maris.middleware.domain.repair.RepairMessages.fit;
import static com.bart.maris.middleware.domain.repair.RepairMessages.join;
import static com.bart.maris.middleware.domain.repair.RepairMessages.pad;
import static com.bart.maris.middleware.domain.repair.RepairMessages.zoned;
import static com.bart.maris.middleware.domain.repair.RepairMessages.zonedDigit;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle repair (MARIS_RUI/EGLSource/MRSVehicleRepair): VHRV01I repairs of an incident, VHRV03I repairs of an event
 * (EVT) incident, VHRV04I changing or deleting a repair, VHRV02I a repair's components. The rules are the statement
 * groups VHRV01IS-*, VHRV02IS-*, VHRV03IS-*, VHRV04IS-*; every message is read from them.
 */
public class RepairService {
    private static final String PKG = "MRSVehicleRepair";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** the three screens that list an incident's repairs; each has its own checks */
    public enum Screen { VHRV01I, VHRV03I, VHRV04I }

    private final Programs programs;
    private final Clock clock;
    private final String terminal;
    private final EglTexts t;
    // VHRV01IS-LIST / VHRV03IS-LIST / VHRV04IS-LIST
    private final String enterIncident, invalidRequest, carInService, wrongType, closedNotListed, notFound, needRepair, enterEvent, invalidEvent;
    private final List<String> privilegedGroups, excludedTypes;
    private final String inService, closed, highPriorityPrefix, eventPrefix, eventType;
    private final int highRank, firstNarrative;
    // VHRV01IS-VALIDATE / VHRV03IS-VALIDATE
    private final String badCause, badSystem, badDisposition, badDate, futureDate, badTime, badYard, badEmployee, badWarranty, needHours, badPct,
            badNarrative, badEventNarrative;
    private final List<String> yards, warrantyValues;
    // results
    private final String added, closedPrefix, closedSuffix, notClosed, updated, deleted, changeFailed, deletedNarrative, closedStatus;
    // VHRV02IS-*
    private final List<String> componentChecks, componentResults;

    public RepairService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.terminal = terminal;
        this.t = t;
        String list = "VHRV01IS-LIST", msg = "FINAL-ERROR-MSG";
        enterIncident = t.assigned(list, PKG, msg, "Please enter");
        invalidRequest = t.assigned(list, PKG, msg, "Invalid Request");
        carInService = t.assigned(list, PKG, msg, "in service");
        wrongType = t.assigned(list, PKG, msg, "Incident Type");
        closedNotListed = t.assigned(list, PKG, msg, "Closed");
        notFound = t.assigned(list, PKG, msg, "not found");
        needRepair = t.assigned("VHRV04IS-LIST", PKG, msg, "at Least 1 Repair");
        enterEvent = t.assigned("VHRV03IS-LIST", PKG, msg, "Please enter");
        invalidEvent = t.assigned("VHRV03IS-LIST", PKG, msg, "Invalid incident");
        privilegedGroups = t.compared(list, PKG, "USER-GRP");
        excludedTypes = t.compared(list, PKG, "INCD-TYPE");
        List<String> stats = t.compared(list, PKG, "VEH-INCD-STAT");
        inService = stats.get(0);
        closed = stats.get(1);
        highPriorityPrefix = t.compared(list, PKG, "INCD-NO-ARRAY").get(0);
        highRank = Integer.parseInt(t.compared(list, PKG, "WORST-CASE-RANK").get(0));
        firstNarrative = t.assignedNumber(list, PKG, "NARR-NO").intValue();
        eventPrefix = t.compared("VHRV03IS-LIST", PKG, "INCD-NO-ARRAY").get(0);
        eventType = t.compared("VHRV03IS-LIST", PKG, "INCD-TYPE").get(0);

        String v = "VHRV01IS-VALIDATE", em = "ERROR-MESSAGE";
        badCause = t.assigned(v, PKG, em, "Cause");
        badSystem = t.assigned(v, PKG, em, "System");
        badDisposition = t.assigned(v, PKG, em, "Disp");
        badDate = t.assigned(v, PKG, em, "Invalid Date");
        futureDate = t.assigned(v, PKG, em, "Future");
        badTime = t.assigned(v, PKG, em, "Time");
        badYard = t.assigned(v, PKG, em, "Yard");
        badEmployee = t.assigned(v, PKG, em, "Empl");
        badWarranty = t.assigned(v, PKG, em, "Warranty");
        needHours = t.assigned(v, PKG, em, "Labor");
        badNarrative = t.assigned(v, PKG, em, "Narr");
        badPct = t.assigned("VHRV01IS-MOVE-POSN", PKG, em, "PCT");
        badEventNarrative = t.assigned("VHRV03IS-VALIDATE", PKG, em, "Narr");
        yards = t.compared(v, PKG, "REPR-LOC");
        warrantyValues = t.compared(v, PKG, "WAR-REPR-IND");

        added = t.assigned("VHRV01IS-ADD-REPR", PKG, msg, null);
        List<String> closing = t.assignedRaw("VHRV01IS-CLOSE-INC", PKG, "TEMP-MESSAGE-1");
        closedPrefix = closing.get(0);
        closedSuffix = t.assigned("VHRV01IS-CLOSE-INC", PKG, "TEMP-MESSAGE-3", null);
        notClosed = t.assigned("VHRV01IS-CLOSE-INC", PKG, msg, null);
        closedStatus = t.assigned("VHRV01IS-MOVE-REC", PKG, "VEH-INCD-STAT", null);
        updated = t.assigned("VHRV04IS-CHG-REPR", PKG, msg, "Updated");
        deleted = t.assigned("VHRV04IS-CHG-REPR", PKG, msg, "Deleted");
        changeFailed = t.assigned("VHRV04IS-CHG-REPR", PKG, msg, "CANNOT COMPLETE");
        deletedNarrative = t.assigned("VHRV04IS-CHG-REPR", PKG, "NARR", null);

        componentChecks = t.assignedAll("VHRV02IS-NULL-CHK", PKG, "ERR-MSG");
        componentResults = t.assignedAll("VHRV02IS-RET-DATA", PKG, "FINAL-MESSAGE");
        if (componentChecks.size() != 5 || componentResults.size() != 3) throw new IllegalStateException("VHRV02I messages changed: " + componentChecks + componentResults);
    }

    // ---- VHRV01IS-INIT: the codes a repair is checked against ---------------------------------------------------

    public record Code(String code, String description) {}

    /** @param systemChangePrompt VHRV04I's question when a change moves the repair to another system */
    public record Options(List<Code> causes, List<Code> dispositions, List<String> yards, String systemChangePrompt, String deletePrompt,
                          String componentDeletePrompt) {}

    public Options options() {
        return new Options(causes(), dispositions(), yards, t.prompt("VHRV04I", "msgSysSubSysChg"), t.prompt("VHRV04I", "msgDeletePrompt"),
                t.prompt("VHRV02I", "msgDeleteconfirm"));
    }

    private List<Code> causes() {
        Programs.Outcome o = programs.attempt("VHRV01C", Map.of());
        return codes(o.result().record("VHRV01C_DATA"), "reprCauseCount", "reprCauseCd", "reprCauseCdDesc");
    }

    private List<Code> dispositions() {
        Programs.Outcome o = programs.attempt("VHRV01D", Map.of());
        return codes(o.result().record("VHRV01D_DATA"), "reprDispCount", "reprDispCd", "reprDispCdDesc");
    }

    @SuppressWarnings("unchecked")
    private static List<Code> codes(Map<String, Object> data, String count, String code, String desc) {
        List<Code> out = new ArrayList<>();
        if (data == null) return out;
        List<Map<String, Object>> all = data.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        int n = (int) Math.min(number(data, count), all.size());
        for (int i = 0; i < n; i++) out.add(new Code(text(all.get(i), code), text(all.get(i), desc)));
        return out;
    }

    // ---- VHRV01IS-LIST / VHRV03IS-LIST / VHRV04IS-LIST: an incident and its repairs ------------------------------

    public record PreviousRepair(int document, String date, String time, String location, String narrative) {}

    /** the values VHRV01IS-PREV-ACT carries into the next repair (VHRV01IW-RETAIN) */
    public record Defaults(String location, String date, String time, String employee) {}

    public record IncidentRepairs(String incident, String car, String status, String type, String consist, int nextDocument,
                                  List<PreviousRepair> repairs, Defaults defaults) {}

    private record Incident(Map<String, Object> data) {
        String car() { return text(data, "carNo"); }
        String status() { return text(data, "vehIncdStat"); }
        String type() { return text(data, "incdType"); }
    }

    private record Repairs(ProgramResult result, int count, int narrativeCount, List<Map<String, Object>> rows, List<Map<String, Object>> narratives) {}

    public IncidentRepairs incident(Screen screen, String incident, String userGroup) {
        String incd = incidentNo(incident, screen == Screen.VHRV03I ? enterEvent : enterIncident);
        if (screen == Screen.VHRV03I && !incd.startsWith(eventPrefix)) throw new InvalidRequestException(invalidEvent);
        Incident inc = lookup(incd);
        checkIncident(screen, incd, inc, userGroup);
        Repairs r = repairs(incd, "BOTH");
        int urc = urc(r.result());
        List<PreviousRepair> prev = new ArrayList<>();
        Defaults defaults = null;
        int next;
        if (r.count() > 0) {
            next = r.count() + 1;
            List<Map<String, Object>> narr = r.narrativeCount() > 0 ? r.narratives() : List.of();
            prev = previous(screen, r, narr);
            if (screen != Screen.VHRV03I) {
                Map<String, Object> first = r.rows().isEmpty() ? Map.of() : r.rows().get(0);
                PreviousRepair p = prev.isEmpty() ? null : prev.get(0);
                String time = p == null ? "" : fit(p.time(), 2) + (p.time().length() > 3 ? fit(p.time().substring(3), 2) : "");
                defaults = new Defaults(p == null ? "" : p.location(), p == null ? "" : p.date(), time.strip(), text(first, "emplNo"));
            }
        } else if (urc == 100) {
            if (screen == Screen.VHRV04I) throw new InvalidRequestException(needRepair);
            next = 1;
        } else {
            throw programs.error("VHRV01A", r.result().control());
        }
        return new IncidentRepairs(incd, inc.car(), inc.status(), inc.type(), consist(inc.data(), screen), next, prev, defaults);
    }

    private static String incidentNo(String incident, String enter) {
        String i = incident == null ? "" : incident.strip();
        if (i.isEmpty()) throw new InvalidRequestException(enter);
        return fit(i, 8);
    }

    private Incident lookup(String incd) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("retCheck", "INCD");
        Programs.Outcome o = programs.attempt("VHINC2D", Map.of("VHINC2D_CRIT", crit));
        int urc = urc(o.result());
        if (urc == 100) throw new InvalidRequestException(notFound);
        if (!o.ok()) throw programs.error("VHINC2D", o.result().control());
        Map<String, Object> d = o.result().record("VHINC2D_DATA");
        return new Incident(d == null ? Map.of() : d);
    }

    private void checkIncident(Screen screen, String incd, Incident inc, String userGroup) {
        switch (screen) {
            case VHRV01I -> {
                if (incd.startsWith(highPriorityPrefix) && number(inc.data(), "worstCaseRank") >= highRank) throw new InvalidRequestException(invalidRequest);
                if (inc.status().equals(inService) && !privilegedGroups.contains(userGroup == null ? "" : userGroup.strip()))
                    throw new InvalidRequestException(carInService);
                if (excludedTypes.contains(inc.type())) throw new InvalidRequestException(wrongType);
                if (inc.status().equals(closed)) throw new InvalidRequestException(closedNotListed);
            }
            case VHRV03I -> {
                if (!inc.type().equals(eventType)) throw new InvalidRequestException(invalidRequest);
                if (inc.status().equals(closed)) throw new InvalidRequestException(closedNotListed);
            }
            case VHRV04I -> { }                        // VHRV04IS-LIST lists closed incidents too ("XXXCLO" never matches)
        }
    }

    /** VHRV01A with RET-CHECK BOTH (repairs and narratives) or REPR; the counts come back in VHRV01A_CRIT */
    @SuppressWarnings("unchecked")
    private Repairs repairs(String incd, String check) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("retCheck", check);
        ProgramResult r = programs.attempt("VHRV01A", Map.of("VHRV01A_CRIT", crit)).result();
        Map<String, Object> c = r.record("VHRV01A_CRIT");
        Map<String, Object> d = r.record("VHRV01A_DATA"), n = r.record("VHRV01A_NARR_DATA");
        List<Map<String, Object>> rows = d != null && d.get("dataReprRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<Map<String, Object>> narr = n != null && n.get("dataNarrRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        return new Repairs(r, (int) number(c, "reprCount"), (int) number(c, "reprNarrCount"), rows, narr);
    }

    /**
     * VHRV01IS-PREV-ACT (VHRV03IS-PREV-ACT): each repair that has a narrative, with its first narrative. VHRV01I and
     * VHRV04I take the repair number from the narrative row numbered like the repair (an old slip kept as it is).
     */
    private List<PreviousRepair> previous(Screen screen, Repairs r, List<Map<String, Object>> narr) {
        List<PreviousRepair> out = new ArrayList<>();
        int nc = Math.min(r.narrativeCount(), narr.size());
        for (int ri = 0; ri < Math.min(r.count(), r.rows().size()); ri++) {
            Map<String, Object> rep = r.rows().get(ri);
            for (int ni = 0; ni < nc; ni++) {
                if (number(rep, "reprDocNo") != number(narr.get(ni), "reprDocNo")) continue;
                long doc = screen == Screen.VHRV03I ? number(rep, "reprDocNo") : ri < narr.size() ? number(narr.get(ri), "reprDocNo") : 0;
                out.add(new PreviousRepair((int) (doc % 100), text(rep, "dateRepr"), text(rep, "timeRepr"), text(rep, "reprLoc"),
                        fit(text(narr.get(ni), "narr"), 500)));
                break;
            }
        }
        return out;
    }

    private static String consist(Map<String, Object> d, Screen screen) {
        String con = text(d, "conNo");
        if (con.isEmpty()) return "";
        return con + "-" + String.format("%02d", Math.abs(number(d, "dashNo")) % 100);
    }

    // ---- VHRV01IS-SELECT: one repair and its narratives ------------------------------------------------------------

    public record Narrative(int number, String userGroup, String date, String time, String text) {}

    public record RepairDetails(int document, String cause, String serialOn, String serialOff, String deleted, String partControl, String system,
                                String subSystem, String disposition, String warranty, long laborHours, String employee, String location,
                                String date, String time, List<Narrative> narratives) {}

    public RepairDetails repair(String incident, int document) {
        String incd = incidentNo(incident, enterIncident);
        Map<String, Object> row = row(incd, document);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reprDocNo", document);
        crit.put("incdNo", incd);
        ProgramResult f = programs.attempt("VHRV01F", Map.of("VHRV01F_CRIT", crit)).result();
        List<Narrative> narr = new ArrayList<>();
        if (urc(f) == 0 || urc(f) == 4) {
            for (Map<String, Object> n : rows(f, "VHRV01F_DATA", "dataRow")) {
                narr.add(new Narrative((int) number(n, "narrNo"), text(n, "userGrp"), text(n, "dateNarr"), text(n, "timeNarr"), text(n, "narr")));
            }
        }
        String time = text(row, "timeRepr");
        return new RepairDetails(document, text(row, "reprCauseCd"), text(row, "serNoOn"), text(row, "serNoOff"), text(row, "deleteInd"),
                text(row, "vehPrtCtlNo"), text(row, "vehSystem"), text(row, "vehSubSystem"), text(row, "reprDispCd"), text(row, "warReprInd"),
                number(row, "reprLaborHrs"), text(row, "emplNo"), fit(text(row, "reprLoc"), 2), text(row, "dateRepr"),
                (fit(time, 2) + (time.length() > 3 ? fit(time.substring(3), 2) : "")).strip(), narr);
    }

    /** a repair's row of VHRV01A (RET-CHECK REPR) */
    private Map<String, Object> row(String incd, int document) {
        Repairs r = repairs(incd, "REPR");
        if (urc(r.result()) != 0 && urc(r.result()) != 4) throw programs.error("VHRV01A", r.result().control());
        for (int i = 0; i < Math.min(r.count(), r.rows().size()); i++) {
            if (number(r.rows().get(i), "reprDocNo") == document) return r.rows().get(i);
        }
        throw new InvalidRequestException("Repair " + document + " is not a repair of incident " + incd + ".");
    }

    // ---- VHRV01IS-ADD-REPR / VHRV01IS-CLOSE-INC ---------------------------------------------------------------------

    /** what the user types for a repair; time is HHMM and the date MM-DD-YYYY, as on the old screen */
    public record RepairInput(String cause, String system, String subSystem, String disposition, String date, String time, String location,
                              String employee, String warranty, Integer laborHours, String partControl, String serialOn, String serialOff,
                              String narrative) {}

    /** @param done false when MARIS refused it without a message (ERR-FOUND) */
    public record Result(boolean done, String message, String partControl) {}

    public Result addRepair(String incident, RepairInput in, String userGroup) {
        IncidentRepairs inc = incident(Screen.VHRV01I, incident, userGroup);
        Incident data = lookupQuiet(inc.incident());
        Checked c = validate(in, inc);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", inc.incident());
        crit.put("closeIncdInd", "N");
        Map<String, Object> d = repairRecord(inc.incident(), data, userGroup);
        d.put("vehIncdStat", data.status());
        d.put("reprDocNo", inc.nextDocument());
        d.put("reprCauseCd", fit(in.cause(), 1));
        d.put("serNoOn", fit(in.serialOn(), 12));
        d.put("serNoOff", fit(in.serialOff(), 12));
        d.put("vehPrtCtlNo", c.partControl());
        d.put("vehSystem", fit(in.system(), 2));
        d.put("vehSubSystem", fit(in.subSystem(), 2));
        d.put("dateRepr", blank(in.date()) ? MDY.format(now) : fit(in.date(), 10));
        d.put("incdType", data.type());
        d.put("timeRepr", repairTime(in.time(), now));
        d.put("reprDispCd", fit(in.disposition(), 1));
        d.put("reprLoc", fit(in.location(), 2));
        d.put("warReprInd", fit(in.warranty(), 1));
        d.put("reprLaborHrs", in.laborHours() == null ? 0 : in.laborHours());
        d.put("emplNo", fit(in.employee(), 5));
        d.put("narrNo", firstNarrative);
        d.put("dateNarr", MDY.format(now));
        d.put("timeNarr", HMS.format(now));
        d.put("narr", fit(in.narrative(), 1000));
        Programs.Outcome o = programs.attempt("VHRV01B", Map.of("VHRV01B_CRIT", crit, "VHRV01B_DATA", d));
        if (!o.ok()) throw programs.error("VHRV01B", o.result().control());
        boolean ok = "N".equals(text(o.result().record("VHRV01B_CRIT"), "errFound"));
        return new Result(ok, ok ? added : null, c.partControl());
    }

    private Map<String, Object> repairRecord(String incd, Incident data, String userGroup) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("carNo", data.car());
        d.put("incdNo", incd);
        d.put("userGrp", fit(userGroup, 8));
        d.put("termLastUpd", fit(terminal, 15));
        return d;
    }

    /** VHRV01IS-CLOSE-INC: closes the incident with the date, time and yard of its first listed repair */
    public Result closeIncident(String incident, String userGroup) {
        IncidentRepairs inc = incident(Screen.VHRV01I, incident, userGroup);
        Incident data = lookupQuiet(inc.incident());
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", inc.incident());
        crit.put("closeIncdInd", "Y");
        Map<String, Object> d = repairRecord(inc.incident(), data, userGroup);
        d.put("vehIncdStat", closedStatus);
        d.put("incdStatNew", closedStatus);
        PreviousRepair p = inc.repairs().isEmpty() ? null : inc.repairs().get(0);
        d.put("dateRepr", p == null ? "" : p.date());
        d.put("timeRepr", p == null ? "" : p.time());
        d.put("reprLoc", p == null ? "" : p.location());
        Programs.Outcome o = programs.attempt("VHRV01B", Map.of("VHRV01B_CRIT", crit, "VHRV01B_DATA", d));
        if (!o.ok()) throw programs.error("VHRV01B", o.result().control());
        if (!"N".equals(text(o.result().record("VHRV01B_CRIT"), "errFound"))) return new Result(false, notClosed, null);
        return new Result(true, closedMessage(inc.incident()), null);
    }

    private String closedMessage(String incd) {
        return (pad(closedPrefix, 9) + pad(incd, 9) + closedSuffix).strip();
    }

    /** VHINC2D again for the record fields (the old screen keeps VHINC2D_DATA from the List) */
    private Incident lookupQuiet(String incd) {
        return lookup(incd);
    }

    // ---- VHRV01IS-VALIDATE ---------------------------------------------------------------------------------------

    private record Checked(String partControl) {}

    private Checked validate(RepairInput in, IncidentRepairs inc) {
        List<String> errors = new ArrayList<>();
        String cause = fit(in.cause(), 1);
        if (blank(cause) || causes().stream().noneMatch(x -> x.code().equals(cause))) errors.add(badCause);
        String sys = fit(in.system(), 2), sub = fit(in.subSystem(), 2);
        if (!blank(sys) && !blank(sub)) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("vehSystem", sys);
            crit.put("vehSubSystem", sub);
            ProgramResult r = programs.attempt("VHRV01E", Map.of("VHRV01E_CRIT", crit)).result();
            long rows = number(r.record("VHRV01E_CRIT"), "rowCount");
            if (rows != 1 && urc(r) == 0 && rows == 0) errors.add(badSystem);
        } else {
            errors.add(badSystem);
        }
        String disp = fit(in.disposition(), 1);
        if (blank(disp) || dispositions().stream().noneMatch(x -> x.code().equals(disp))) errors.add(badDisposition);
        String date = fit(in.date(), 10);
        if (!blank(date)) {
            String dateCheck = checkDate(date);
            if (dateCheck != null) errors.add(dateCheck);
        }
        String time = fit(in.time(), 4);
        if (!blank(time) && timeInvalid(time)) errors.add(badTime);
        String yard = fit(in.location(), 2).strip();
        if (yard.isEmpty() || !yards.contains(yard)) errors.add(badYard);
        if (errors.size() == 6) throw new InvalidRequestException(join(errors));
        String empl = fit(in.employee(), 5);
        if (!blank(empl) && pad(empl, 5).chars().anyMatch(ch -> zonedDigit((char) ch) < 0)) errors.add(badEmployee);
        if (errors.size() == 6) throw new InvalidRequestException(join(errors));
        String war = fit(in.warranty(), 1);
        if (!blank(war) && !warrantyValues.contains(war)) errors.add(badWarranty);
        if (errors.size() == 6) throw new InvalidRequestException(join(errors));
        if (war.equals(warrantyValues.get(0)) && (in.laborHours() == null || in.laborHours() == 0)) errors.add(needHours);
        if (errors.size() == 6) throw new InvalidRequestException(join(errors));
        String pct = partControl(fit(in.partControl(), 8), errors);
        if (errors.size() == 6) throw new InvalidRequestException(join(errors));
        if (blank(in.narrative())) errors.add(badNarrative);
        if (!errors.isEmpty()) throw new InvalidRequestException(join(errors));
        return new Checked(pct);
    }

    /** the date checks of VHRV01IS-VALIDATE / VHRV03IS-VALIDATE (AFCXVLD, then not in the future); null when fine */
    private String checkDate(String date) {
        String d = pad(date, 10);
        String yr = d.substring(6, 10);
        String form = yr.chars().allMatch(Character::isDigit) ? d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5) : d;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", form);
        crit.put("otherSqlError", "N");
        Map<String, Object> back = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result().record("AFCXVLD_CRIT");
        String de = back == null ? "N" : text(back, "dateError"), sql = back == null ? "N" : text(back, "otherSqlError");
        if (de.equals("Y") && sql.equals("N")) return badDate;
        if (de.equals("N") && sql.equals("Y")) return null;
        LocalDate today = LocalDate.now(clock);
        int y = zoned(yr), m = zoned(d.substring(0, 2)), day = zoned(d.substring(3, 5));
        if (y > today.getYear() || y == today.getYear() && m > today.getMonthValue()
                || y == today.getYear() && m == today.getMonthValue() && day > today.getDayOfMonth()) return futureDate;
        return null;                     // "Date too old." compares with VHINC1D_DATA, which these screens never fill
    }

    private static boolean timeInvalid(String time) {
        String tr = time.stripTrailing();
        if (tr.isEmpty() || !tr.chars().allMatch(Character::isDigit)) return true;
        String p = pad(time, 4);
        int hr = zoned(p.substring(0, 2)), min = zoned(p.substring(2, 4));
        return hr > 24 || hr == 24 && min > 0 || hr == 0 && min == 0 || min > 59;
    }

    /** VHRV01IS-MOVE-REC: HHMM becomes HH:MM:00; no time means now */
    private static String repairTime(String time, LocalDateTime now) {
        if (blank(time)) return HMS.format(now);
        String p = pad(time, 4);
        return p.substring(0, 2) + ":" + p.substring(2, 4) + ":00";
    }

    /**
     * VHRV01IS-VALIDATE / -MOVE-POSN / -SUFFIX: a part control number of 6 characters gets a "P" in front; one of 8
     * starting with "P" gets its last character recomputed from the 6 digits (mod 13, B..M; unchanged when it divides).
     */
    private String partControl(String pct, List<String> errors) {
        if (blank(pct)) return pct;
        char[] a = pad(pct, 8).toCharArray();
        boolean compute;
        if (a[6] != ' ' && a[7] != ' ') {
            compute = a[0] == 'P';
        } else if (a[6] == ' ' && a[7] == ' ') {
            System.arraycopy(a, 0, a, 1, 6);
            a[0] = 'P';
            compute = true;
        } else {
            compute = false;
        }
        if (compute) {
            long suffix = 0;
            boolean bad = false;
            for (int i = 1; i <= 6; i++) {
                int v = zonedDigit(a[i]);
                if (v < 0) { bad = true; break; }
                suffix = suffix * 10 + v % 10;
            }
            if (bad) errors.add(badPct);
            else {
                long rem = suffix % 13;
                if (rem > 0) a[7] = (char) ('A' + rem);
            }
        }
        return new String(a).stripTrailing();
    }

    // ---- VHRV03I: repairs of an event ------------------------------------------------------------------------------

    public record EventRepairInput(String date, String time, String narrative) {}

    /** VHRV03IS-ADD-REPR (VHRV03A): the date and time of the repair and its narrative */
    public Result addEventRepair(String incident, EventRepairInput in, String userGroup) {
        IncidentRepairs inc = incident(Screen.VHRV03I, incident, userGroup);
        Incident data = lookup(inc.incident());
        List<String> errors = new ArrayList<>();
        String date = fit(in.date(), 10);
        if (!blank(date)) {
            String dc = checkDate(date);
            if (dc != null) errors.add(dc);
        }
        String time = fit(in.time(), 4);
        if (!blank(time) && timeInvalid(time)) errors.add(badTime);
        if (blank(in.narrative())) errors.add(badEventNarrative);
        if (!errors.isEmpty()) throw new InvalidRequestException(join(errors));
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", inc.incident());
        crit.put("closeIncdInd", "N");
        Map<String, Object> d = repairRecord(inc.incident(), data, userGroup);
        d.put("reprDocNo", inc.nextDocument());
        d.put("dateRepr", blank(date) ? MDY.format(now) : date);
        d.put("timeRepr", repairTime(time, now));
        d.put("narrNo", firstNarrative);
        d.put("dateNarr", MDY.format(now));
        d.put("timeNarr", HMS.format(now));
        d.put("narr", fit(in.narrative(), 1000));
        Programs.Outcome o = programs.attempt("VHRV03A", Map.of("VHRV03A_CRIT", crit, "VHRV03A_DATA", d));
        if (!o.ok()) throw programs.error("VHRV03A", o.result().control());
        return new Result("N".equals(text(o.result().record("VHRV03A_CRIT"), "errFound")), null, null);
    }

    /** VHRV03IS-CLOSE-INC */
    public Result closeEvent(String incident, String userGroup) {
        IncidentRepairs inc = incident(Screen.VHRV03I, incident, userGroup);
        Incident data = lookup(inc.incident());
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", inc.incident());
        crit.put("closeIncdInd", "Y");
        Map<String, Object> d = repairRecord(inc.incident(), data, userGroup);
        d.put("vehIncdStat", t.assigned("VHRV03IS-MOVE-REC", PKG, "VEH-INCD-STAT", null));
        Programs.Outcome o = programs.attempt("VHRV03A", Map.of("VHRV03A_CRIT", crit, "VHRV03A_DATA", d));
        if (!o.ok()) throw programs.error("VHRV03A", o.result().control());
        if (!"N".equals(text(o.result().record("VHRV03A_CRIT"), "errFound"))) return new Result(false, notClosed, null);
        return new Result(true, closedMessage(inc.incident()), null);
    }

    // ---- VHRV04I: change or delete a repair -----------------------------------------------------------------------

    /**
     * VHRV04IS-SYS-CHG + VHRV04IS-CHG-REPR (VHRV4A1; VHRV4A2 when the system changes or the repair is deleted). The
     * old screen asks first when the system changes; the page asks that question (Options.systemChangePrompt).
     */
    public Result changeRepair(String incident, int document, RepairInput in, boolean delete, String userGroup) {
        IncidentRepairs inc = incident(Screen.VHRV04I, incident, userGroup);
        Map<String, Object> current = row(inc.incident(), document);
        String pct;
        if (delete) {
            pct = text(current, "vehPrtCtlNo");
        } else {
            pct = validate(in, inc).partControl();
        }
        boolean systemChanged = !delete && (!fit(in.system(), 2).equals(text(current, "vehSystem")) || !fit(in.subSystem(), 2).equals(text(current, "vehSubSystem")));
        LocalDateTime now = LocalDateTime.now(clock);
        RepairInput v = delete ? fromRow(current) : in;
        Map<String, Object> d = new LinkedHashMap<>();
        if (delete) d.put("deleteInd", "Y");
        d.put("incdNo", inc.incident());
        d.put("termLastUpd", fit(terminal, 15));
        d.put("reprDocNo", document);
        d.put("reprCauseCd", fit(v.cause(), 1));
        d.put("serNoOn", fit(v.serialOn(), 12));
        d.put("serNoOff", fit(v.serialOff(), 12));
        d.put("vehPrtCtlNo", pct);
        d.put("vehSystem", fit(v.system(), 2));
        d.put("vehSubSystem", fit(v.subSystem(), 2));
        d.put("dateRepr", blank(v.date()) ? MDY.format(now) : fit(v.date(), 10));
        d.put("timeRepr", repairTime(fit(v.time(), 4), now));
        d.put("reprDispCd", fit(v.disposition(), 1));
        d.put("reprLoc", fit(v.location(), 2));
        d.put("warReprInd", fit(v.warranty(), 1));
        d.put("reprLaborHrs", v.laborHours() == null ? 0 : v.laborHours());
        d.put("emplNo", fit(v.employee(), 5));
        Programs.Outcome o = programs.attempt("VHRV4A1", Map.of("VHRV4A1_DATA", d));
        if (!o.ok()) throw programs.error("VHRV4A1", o.result().control());
        Map<String, Object> back = o.result().record("VHRV4A1_DATA");
        if (!text(back, "error").isEmpty()) return new Result(false, changeFailed, pct);
        String message = null;
        if (delete || systemChanged) {
            Map<String, Object> d2 = new LinkedHashMap<>();
            d2.put("reprDocNo", number(back, "reprDocNo"));
            d2.put("incdNo", text(back, "incdNo"));
            Programs.Outcome o2 = programs.attempt("VHRV4A2", Map.of("VHRV4A2_DATA", d2));
            if (o2.ok()) message = deleted;
        }
        if (!delete) message = updated;
        return new Result(true, message, pct);
    }

    private static RepairInput fromRow(Map<String, Object> row) {
        String time = text(row, "timeRepr");
        return new RepairInput(text(row, "reprCauseCd"), text(row, "vehSystem"), text(row, "vehSubSystem"), text(row, "reprDispCd"), text(row, "dateRepr"),
                (fit(time, 2) + (time.length() > 3 ? fit(time.substring(3), 2) : "")).strip(), fit(text(row, "reprLoc"), 2), text(row, "emplNo"),
                text(row, "warReprInd"), (int) number(row, "reprLaborHrs"), text(row, "vehPrtCtlNo"), text(row, "serNoOn"), text(row, "serNoOff"), "");
    }

    /** the narrative text VHRV04I shows for a deleted repair */
    public String deletedNarrative() {
        return deletedNarrative;
    }

    // ---- VHRV02I: a repair's components ----------------------------------------------------------------------------

    public record Component(int document, String component, String type, String fault, String disposition, String serialOn, String serialOff,
                            String date) {}

    public record ComponentInput(String component, String type, String fault, String disposition, String serialOn, String serialOff) {}

    /** @param message the old screen's message after an add / update / delete, otherwise null */
    public record Components(String title, List<Component> components, int nextDocument, String message) {}

    /** VHRV02IS-HIDE: the window title says which screen opened it */
    public String componentsTitle(boolean maintenance) {
        List<String> titles = t.assignedAll("VHRV02IS-HIDE", PKG, "WINDOW-TITLE");
        String title = titles.get(maintenance ? 0 : 1);
        int v = title.indexOf("  ");
        return v > 0 ? title.substring(0, v).strip() : title;
    }

    /** VHRV02IS-RET-DATA with RETRIEVE: VHRV02A */
    public Components components(String incident, int document, boolean maintenance) {
        return listComponents(incidentNo(incident, enterIncident), document, maintenance, null);
    }

    private Components listComponents(String incd, int document, boolean maintenance, String message) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("reprDocNo", document);
        ProgramResult r = programs.attempt("VHRV02A", Map.of("VHRV02A_CRIT", crit)).result();
        int urc = urc(r);
        List<Component> out = new ArrayList<>();
        if (urc == 0 || urc == 4) {
            for (Map<String, Object> row : rows(r, "VHRV02A_DATA", "dataRow")) {
                out.add(new Component((int) number(row, "cmpntDocNo"), text(row, "cmpntCd"), text(row, "cmpntType"), text(row, "cmpntFaultCd"),
                        text(row, "reprDispCd"), text(row, "serNoOn"), text(row, "serNoOff"), text(row, "dateRepr")));
            }
        } else if (urc != 100) {
            throw programs.error("VHRV02A", r.control());
        }
        int next = urc == 0 || urc == 4 ? (r.control() == null ? out.size() : r.control().rowCount()) + 1 : 1;
        return new Components(componentsTitle(maintenance), out, next, message);
    }

    /** VHRV02IS-RET-DATA with ADD: VHRV02IS-NULL-CHK, then VHRV02B */
    public Components addComponent(String incident, int document, ComponentInput in, boolean maintenance) {
        String incd = incidentNo(incident, enterIncident);
        Map<String, Object> rep = row(incd, document);
        checkComponent(in, rep);
        Components now = listComponents(incd, document, maintenance, null);
        Map<String, Object> d = componentRecord(incd, document, now.nextDocument(), rep, in);
        Programs.Outcome o = programs.attempt("VHRV02B", Map.of("VHRV02B_DATA", d));
        if (!o.ok()) throw programs.error("VHRV02B", o.result().control());
        return listComponents(incd, document, maintenance, componentResults.get(0));
    }

    /** VHRV02IS-RET-DATA with UPDATE: VHRV02IS-NULL-CHK, then VHRV02C */
    public Components updateComponent(String incident, int document, int component, ComponentInput in, boolean maintenance) {
        String incd = incidentNo(incident, enterIncident);
        Map<String, Object> rep = row(incd, document);
        checkComponent(in, rep);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("reprDocNo", document);
        crit.put("cmpntDocNo", component);
        Map<String, Object> d = componentRecord(incd, document, component, rep, in);
        d.remove("incdNo");
        d.remove("reprDocNo");
        d.remove("cmpntDocNo");
        Programs.Outcome o = programs.attempt("VHRV02C", Map.of("VHRV02C_CRIT", crit, "VHRV02C_DATA", d));
        if (!o.ok()) throw programs.error("VHRV02C", o.result().control());
        return listComponents(incd, document, maintenance, componentResults.get(1));
    }

    /** VHRV02IS-RET-DATA with DELETE: VHRV02D */
    public Components deleteComponent(String incident, int document, int component, boolean maintenance) {
        String incd = incidentNo(incident, enterIncident);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("reprDocNo", document);
        crit.put("cmpntDocNo", component);
        Programs.Outcome o = programs.attempt("VHRV02D", Map.of("VHRV02D_CRIT", crit));
        if (!o.ok()) throw programs.error("VHRV02D", o.result().control());
        return listComponents(incd, document, maintenance, componentResults.get(2));
    }

    private Map<String, Object> componentRecord(String incd, int document, int component, Map<String, Object> rep, ComponentInput in) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("incdNo", incd);
        d.put("reprDocNo", document);
        d.put("cmpntDocNo", component);
        d.put("dateRepr", text(rep, "dateRepr"));
        d.put("cmpntCd", fit(in.component(), 3));
        d.put("cmpntType", fit(in.type(), 2));
        d.put("cmpntFaultCd", fit(in.fault(), 4));
        d.put("reprDispCd", fit(in.disposition(), 1));
        d.put("serNoOff", fit(in.serialOff(), 12));
        d.put("serNoOn", fit(in.serialOn(), 12));
        d.put("termMaintUpd", fit(terminal, 15));
        return d;
    }

    /** VHRV02IS-NULL-CHK: component code and type together (VHRV02F), the fault code (VHRV02E), the disposition */
    private void checkComponent(ComponentInput in, Map<String, Object> rep) {
        String cd = fit(in.component(), 3), type = fit(in.type(), 2);
        boolean badCd = false, badType = false, badFault = false, badDisp;
        if (blank(cd) && !blank(type)) badCd = true;
        else if (!blank(cd) && blank(type)) badType = true;
        else if (!blank(cd) && !blank(type)) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("cmpntCd", cd);
            d.put("cmpntType", type);
            d.put("vehSystem", text(rep, "vehSystem"));
            d.put("vehSubSystem", text(rep, "vehSubSystem"));
            ProgramResult r = programs.attempt("VHRV02F", Map.of("VHRV02F_DATA", d)).result();
            if (number(r.record("VHRV02F_DATA"), "rowCount") != 1) { badCd = true; badType = true; }
        }
        String fault = fit(in.fault(), 4);
        if (!blank(fault)) {
            ProgramResult r = programs.attempt("VHRV02E", Map.of("VHRV02E_DATA", Map.of("cmpntFaultCd", fault))).result();
            if (r.control() == null || r.control().rowCount() != 1) badFault = true;
        }
        String disp = fit(in.disposition(), 1);
        badDisp = blank(disp) || dispositions().stream().noneMatch(x -> x.code().equals(disp));
        List<String> errors = new ArrayList<>();
        if (badCd && !badType) errors.add(componentChecks.get(0));
        else if (!badCd && badType) errors.add(componentChecks.get(1));
        else if (badCd) errors.add(componentChecks.get(2));
        if (badFault) errors.add(componentChecks.get(3));
        if (badDisp) errors.add(componentChecks.get(4));
        if (!errors.isEmpty()) throw new InvalidRequestException(join(errors));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static int urc(ProgramResult r) {
        return r.control() == null ? 0 : r.control().returnCode();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
