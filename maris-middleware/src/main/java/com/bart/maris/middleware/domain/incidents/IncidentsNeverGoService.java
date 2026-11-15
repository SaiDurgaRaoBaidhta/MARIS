package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;
import static com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeSupport.PKG;
import static com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeSupport.flat;
import static com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeSupport.pad;
import static com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeSupport.s;
import static com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeSupport.ymd;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle tracking change incident "never go" (old VHCI03I): a car's status changed to a never-go status with a
 * narrative. The car is checked (VHCI01B), the date and time are checked, then VHCIV records the change and opens the
 * incident; a car in a pseudo consist has it reset, the CI message goes out and a car into the shop is updated. The
 * answer carries what "Maint. Actions" passes to VHVM11I.
 */
public final class IncidentsNeverGoService {
    private static final String SCREEN = "VHCI03I";

    private final Programs programs;
    private final IncidentsTrackingChangeSupport support;
    private final String terminal;
    private final String invalidCarZero, invalidCar, invalidStatus, carInUse, dummyCar, trainIncd, invalidActionCar, invalidActionCarDot, notFound;
    private final String inConsist, dummyType, trainType, carSpecial, trainNo, narrCi, invGroup, fromGui, dateSep;
    private final List<String> rankStatuses;
    private final int rankLimit;
    private final String msg1, msg3, msg5;

    public IncidentsNeverGoService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.support = new IncidentsTrackingChangeSupport(programs, t, clock, terminal);
        this.terminal = terminal;
        List<String> v = t.assignedAll("VHCI03IS-VALIDATE2", PKG, "FINAL-ERROR-MESSAGE");
        invalidCarZero = v.get(0);
        invalidCar = v.get(1);
        invalidStatus = v.get(2);
        carInUse = t.assigned("VHCI03IS-ACT-STAT", PKG, "FINAL-ERROR-MESSAGE", "Use");
        dummyCar = t.assigned("VHCI03IS-ACT-STAT", PKG, "FINAL-ERROR-MESSAGE", "Dummy");
        invalidActionCarDot = t.assigned("VHCI03IS-ACT-STAT", PKG, "FINAL-ERROR-MESSAGE", "Action");
        notFound = t.assigned("VHCI03IS-ACT-STAT", PKG, "FINAL-ERROR-MESSAGE", "not found");
        trainIncd = t.assigned("VHCI03IS-ACT-STAT", PKG, "DISP-ERR-MSG", "train");
        invalidActionCar = t.assigned("VHCI03IS-ACT-STAT", PKG, "DISP-ERR-MSG", "for Car");
        inConsist = t.compared("VHCI03IS-ACT-STAT", PKG, "WHERE-IS-CAR").get(0);
        List<String> types = t.compared("VHCI03IS-ACT-STAT", PKG, "CAR-TYPE-1");
        dummyType = types.get(0);
        trainType = types.get(1);
        carSpecial = t.compared("VHCI03IS-ACT-STAT", PKG, "CAR-NO").get(0);
        rankStatuses = t.compared("VHCI03IS-ACT-STAT", PKG, "STATUS");
        rankLimit = Integer.parseInt(t.compared("VHCI03IS-ACT-STAT", PKG, "WORST-CASE-RANK").get(0));
        trainNo = t.compared("VHCI03IS-ACT-STAT", PKG, "TRAIN-INCD-IND").get(0);
        narrCi = t.assignedRaw("VHCI03IS-NG-STATU2", PKG, "STORE-NARR-CI").get(0);
        invGroup = t.assigned("VHCI03IS-NG-STATU2", PKG, "USER-GRP", null);
        msg1 = t.assignedRaw("VHCI03IS-NG-STATU2", PKG, "FINAL-ERROR-MESSAGE-1").get(0);
        msg3 = t.assignedRaw("VHCI03IS-NG-STATU2", PKG, "FINAL-ERROR-MSG-3-1").get(0);
        msg5 = t.assignedRaw("VHCI03IS-NG-STATU2", PKG, "FINAL-ERROR-MSG-3-3").get(0);
        fromGui = t.assigned("VHCI03IS-OPN-VM", PKG, "PASS-FROM-GUI", null);
        dateSep = t.assigned("VHCI03IS-OPN-VM", PKG, "FIL-1", null);
    }

    public record Options(String date, String time, List<String> statuses, boolean canChangeLocation) {}

    /** VHCI03IS-INIT: the backend's date and time, the statuses (VHCI03E), and whether the group may change locations (VHCI02B) */
    public Options options(String userGroup) {
        IncidentsTrackingChangeSupport.Now now = support.now();
        List<String> statuses = new ArrayList<>();
        ProgramResult e = programs.attempt("VHCI03E", Map.of()).result();
        if (e.control() != null && e.control().rowCount() > 0) for (Map<String, Object> r : rows(e, "VHCI03E_DATA", "dataRow")) {
            if (!text(r, "vehStat").isBlank()) statuses.add(text(r, "vehStat"));
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("userGrp", userGroup == null ? "" : userGroup);
        boolean canChange = programs.attempt("VHCI02B", Map.of("VHCI02B_CRIT", crit)).ok();
        String t = pad(now.time(), 8);
        return new Options(now.date(), t.substring(0, 2) + t.substring(3, 5), statuses, canChange);
    }

    public record Request(String car, String status, String date, String time, String narrative, String yard) {}

    /** what "Maint. Actions" passes to VHVM11I (VHVM11IW-PASS-DATA) */
    public record Maintenance(String from, String car, String incident, String status, String eventType, String dateInitialized) {}

    public record Result(String message, String incident, String status, String statusDescription, Maintenance maintenance) {}

    /** the Change button (VHCI03IS-VALIDATE2) */
    public Result change(Request r, String userGroup) {
        String group = userGroup == null ? "" : userGroup;
        String car = IncidentsTrackingChangeSupport.car(r.car());
        if (car.equals("0000")) throw new InvalidRequestException(invalidCarZero);
        if (car.isEmpty()) throw new InvalidRequestException(invalidCar);
        String status = s(r.status());
        if (status.isEmpty()) throw new InvalidRequestException(invalidStatus);
        String current = checkCar(car, status);
        IncidentsTrackingChangeSupport.Now now = support.now();
        support.checkDateTime(r.date(), r.time(), r.narrative(), now);
        return neverGo(car, status, r, current, group, now);
    }

    /** VHCI03IS-ACT-STAT: the car (VHCI01B) may take the status; answers the car's current status */
    private String checkCar(String car, String status) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("carNo", car);
        Programs.Outcome o = programs.attempt("VHCI01B", Map.of("VHCI01B_DATA", d));
        int urc = o.result().control() == null ? 0 : o.result().control().returnCode();
        if (urc == 100) throw new InvalidRequestException(notFound);
        if (!o.ok()) throw programs.error("VHCI01B", o.result().control());
        Map<String, Object> c = o.result().record("VHCI01B_DATA");
        String type1 = pad(flat(c.get("carType")), 1);
        if (text(c, "whereIsCar").equals(inConsist)) throw new InvalidRequestException(carInUse);
        if (type1.equals(dummyType)) throw new InvalidRequestException(dummyCar);
        if (car.equals(carSpecial) && type1.equals(trainType)) throw new InvalidRequestException(invalidActionCarDot);
        if (rankStatuses.contains(status)) {
            long rank = Programs.number(c, "worstCaseRank");
            if (rank >= rankLimit) throw new InvalidRequestException(invalidActionCar);
            if (!text(c, "trainIncdInd").equals(trainNo)) throw new InvalidRequestException(trainIncd);
        }
        Object st = c.get("dispVehStat");
        String disp = st instanceof Map<?, ?> m ? pad(flat(((Map<?, ?>) m).get("vehStat")), 6) : pad(flat(st), 6);
        return disp.strip();
    }

    /** VHCI03IS-NG-STATU2 */
    private Result neverGo(String car, String status, Request r, String current, String group, IncidentsTrackingChangeSupport.Now now) {
        String tm = pad(s(r.time()), 4);
        String time = tm.substring(0, 2) + ":" + tm.substring(2, 4) + ":00";
        LocalDateTime clock = LocalDateTime.now(support.clock);
        Map<String, Object> temp = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("change", car);
        rows.add(row);
        temp.put("dataRow", rows);
        temp.put("rowCount", 1);
        temp.put("errFound", support.no());
        temp.put("dateStatChg", s(r.date()));
        temp.put("timeStatChg", time);
        temp.put("userGrp", group);
        temp.put("termLastUpd", terminal);
        Map<String, Object> narr = new LinkedHashMap<>();
        narr.put("storeNarrCi", narrCi);
        narr.put("storeNarrAction", "");                                         // the screen blanks it
        narr.put("storeNarrDate", clock.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
        narr.put("storeNarrTime", clock.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        narr.put("narr", r.narrative() == null ? "" : r.narrative());
        temp.put("storeNarr", narr);
        temp.put("carStatus", status);
        temp.put("yrdTrkgYrd", s(r.yard()));
        Map<String, Object> vb = new LinkedHashMap<>();
        vb.put("inputDate", ymd(r.date()));
        vb.put("inputTime", time);
        vb.put("userTermId", terminal);
        vb.put("userGrp", group);
        programs.attempt("VHINVB", Map.of("VHINVB_CRIT", vb));
        Map<String, Object> va = new LinkedHashMap<>();
        va.put("userTermId", terminal);
        va.put("userGrp", invGroup);
        programs.attempt("VHINVA", Map.of("VHINVA_CRIT", va));
        ProgramResult v = programs.attempt("VHCIV", Map.of("VHCI01IW-TEMP-MI", temp)).result();
        Map<String, Object> out = v.record("VHCI01IW-TEMP-MI");
        if (!text(out, "errFound").equals(support.no())) throw programs.error("VHCIV", v.control());
        String newStat = text(out, "newVehStat"), desc = text(out, "newVehStatDesc");
        if (text(out, "statusUpd").equals(support.yes())) {
            boolean reset = support.pseudo(car, group);
            support.ciMessage(car, group, current, newStat, reset);
            if (text(out, "intoShopUpd").equals(support.yes())) support.intoShop(car);
        }
        String incident = text(out, "saveIncdNo");
        String message = (pad(msg1, 14) + pad(car, 4) + pad(msg3, 18) + pad(incident, 8) + msg5).stripTrailing();
        Object rev = out.get("dateIncdOccrRev");
        String y = "", m = "", d = "";
        if (rev instanceof Map<?, ?> rm) {
            y = flat(rm.get("dateIncdYyyy"));
            m = flat(rm.get("dateIncdMm"));
            d = flat(rm.get("dateIncdDd"));
        } else {
            String p = pad(flat(rev), 10);
            y = p.substring(0, 4);
            m = p.substring(5, 7);
            d = p.substring(8, 10);
        }
        String init = pad(m, 2) + dateSep + pad(d, 2) + dateSep + pad(y, 4);
        Maintenance pass = new Maintenance(fromGui, car, incident, newStat, text(out, "saveIncdType"), init.isBlank() || init.strip().equals(dateSep + dateSep) ? "" : init);
        return new Result(message, incident, newStat, desc, pass);
    }
}
