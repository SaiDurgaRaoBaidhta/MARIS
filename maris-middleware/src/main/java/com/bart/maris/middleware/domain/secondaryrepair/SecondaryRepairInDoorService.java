package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

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
 * In the door (old VHSR01I, from the menu): a new job for a car or for wayside equipment, after the checks of
 * VHSR01IS-CREATE (the ones it shares with the job split, plus the car VHINC6A, the department's equipment VHSR01B /
 * VHSR01C); written by VHSR01E, which numbers the job. Its Job Split button opens the job split (VHSR08I) for that job.
 */
public class SecondaryRepairInDoorService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final Clock clock;
    private final SecondaryRepairJobChecks checks;
    private final String badCar, badCarType, noCar, equipIdRequired, noEquipId, equipTypeRequired, noEquipType, vehicleOrWayside;
    private final String jobNo, created, warning, noCarCheck, noCarNumber;
    private final List<String> carTypes, idDepartments;

    public SecondaryRepairInDoorService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.checks = new SecondaryRepairJobChecks(programs, t, "VHSR01I");
        String c = "VHSR01IS-CREATE", st = "VHSR01IS-DB-STORE";
        badCar = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Invalid car no");
        badCarType = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Car type");
        noCar = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Car no does not exist");
        equipIdRequired = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equip Id required");
        noEquipId = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equip ID does not exist");
        equipTypeRequired = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equip Type required");
        noEquipType = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Equipment does not exist");
        vehicleOrWayside = t.assigned(c, PKG, "ERROR-MESSAGE-OCCR", "Wayside");
        carTypes = t.compared(c, PKG, "CAR-TYPE-OCCR");
        idDepartments = t.compared(c, PKG, "DEPT-CD").stream().filter(x -> !x.isBlank()).toList();
        List<String> cars = t.compared(c, PKG, "CAR-NO");
        noCarNumber = cars.get(0);                                           // "0000": no car
        noCarCheck = cars.stream().filter(x -> !x.isBlank() && !x.equals(noCarNumber)).findFirst().orElseThrow();   // a car not checked
        jobNo = t.assignedRaw(st, PKG, "FINAL-MESSAGE-1").get(0);
        created = t.assignedRaw(st, PKG, "FINAL-MESSAGE-3").get(0);
        warning = t.assignedRaw(st, PKG, "FINAL-MESSAGE-4").stream().filter(x -> x.contains("Warning")).findFirst().orElseThrow();
    }

    public record Options(List<SecondaryRepairJobChecks.Code> workTypes, List<SecondaryRepairJobChecks.Code> repairAreas, List<String> departments,
                          String workType, List<String> equipmentIdDepartments) {}

    /** VHSR01IS-INIT: work types, repair areas, the user group's departments (VHSRUD) */
    public Options options(String userGroup) {
        ProgramResult r = programs.attempt("VHSRUD", Map.of("VHSRUD_CRIT", Map.of("userGrp", userGroup == null ? "" : userGroup))).result();
        List<String> depts = new ArrayList<>();
        if (SecondaryRepairForms.found(r)) for (Map<String, Object> row : rows(r, "VHSRUD_DATA", "dataRow")) depts.add(text(row, "deptCd"));
        return new Options(checks.workTypes(), checks.repairAreas(), depts, checks.defaultWorkType, idDepartments);
    }

    /** VHSR01IS-DEPT-VAL: a department's equipment types (VHSR01B) */
    public List<SecondaryRepairJobChecks.Code> equipmentTypes(String dept) {
        ProgramResult r = programs.attempt("VHSR01B", Map.of("VHSR01B_CRIT", Map.of("deptCd", SecondaryRepairForms.upper(dept)))).result();
        List<SecondaryRepairJobChecks.Code> out = new ArrayList<>();
        if (!SecondaryRepairForms.found(r)) return out;
        for (Map<String, Object> row : rows(r, "VHSR01B_DATA", "dataRow")) out.add(new SecondaryRepairJobChecks.Code(text(row, "equipType"), text(row, "equipTypeDesc")));
        return out;
    }

    public record Equipment(String type, String location) {}

    /** VHSR01IS-EQUIP-ID: a piece of equipment of a department (VHSR01C); null when it does not exist */
    public Equipment equipment(String dept, String equipId) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("equipId", SecondaryRepairForms.upper(equipId));
        crit.put("deptCd", SecondaryRepairForms.upper(dept));
        ProgramResult r = programs.attempt("VHSR01C", Map.of("VHSR01C_CRIT", crit)).result();
        if (r.control() == null || r.control().rowCount() != 1) return null;
        Map<String, Object> d = r.record("VHSR01C_DATA");
        return new Equipment(text(d, "equipType"), text(d, "equipLoc"));
    }

    /** a new job: a car, or a department with its equipment; the date in as ISO yyyy-MM-dd (blank: today) */
    public record NewJob(String workType, String dateIn, String repairArea, String car, String dept, String equipmentType, String equipmentId,
                         String incident, String printControlNo, String sentFrom, String stockNo, String serialNo, Integer quantity, String comment) {}

    public record Created(String job, String message, boolean overThreshold) {}

    /** VHSR01IS-CREATE + DB-STORE */
    public Created create(NewJob n, String userGroup, String terminal) {
        LocalDate today = LocalDate.now(clock);
        String date = n.dateIn() == null || n.dateIn().isBlank() ? today.format(SecondaryRepairForms.SCREEN_DATE) : SecondaryRepairForms.screenDate(n.dateIn());
        var in = new SecondaryRepairJobChecks.Input(SecondaryRepairForms.upper(n.workType()), date, true, SecondaryRepairForms.upper(n.repairArea()),
                SecondaryRepairForms.upper(n.stockNo()), n.quantity() == null ? 1 : n.quantity());
        List<String> errors = new ArrayList<>();
        checks.first(in, today, errors);

        // the car: CAR-NO-NUM (4 digits) moved to CAR-NO; 0000 is no car; a car clears the department (VHSR01IS-CAR-VAL)
        String carIn = SecondaryRepairForms.strip(n.car());
        String car = "";
        if (!carIn.isEmpty()) {
            if (!carIn.chars().allMatch(Character::isDigit) || carIn.length() > 4) {
                errors.add(badCar);
                car = carIn;
            } else {
                car = String.format("%04d", Integer.parseInt(carIn));
                if (car.equals(noCarNumber)) car = "";
            }
        }
        String dept = car.isEmpty() ? SecondaryRepairForms.upper(n.dept()) : "", equipType = "", equipId = "", equipLoc = "";
        if (!car.isEmpty() && errors.stream().noneMatch(badCar::equals) && !car.equals(noCarCheck)) {
            ProgramResult r = programs.attempt("VHINC6A", Map.of("VHINC6A_CRIT", Map.of("carNo", car))).result();
            if (r.control() != null && r.control().rowCount() == 1) {
                String type = SecondaryRepairForms.flat(r.record("VHINC6A_DATA").get("carType"));
                if (type.isEmpty() || !carTypes.contains(type.substring(0, 1))) errors.add(badCarType);
            } else if (r.control() != null && r.control().returnCode() == 100) {
                errors.add(noCar);
            } else {
                throw programs.error("VHINC6A", r.control());
            }
        }
        if (!dept.isEmpty()) {
            if (idDepartments.contains(dept)) {
                equipId = SecondaryRepairForms.upper(n.equipmentId());
                if (equipId.isEmpty()) {
                    errors.add(equipIdRequired);
                } else {
                    Equipment e = equipment(dept, equipId);
                    if (e == null) errors.add(noEquipId);
                    else { equipType = e.type(); equipLoc = e.location(); }
                }
            } else {
                equipType = SecondaryRepairForms.upper(n.equipmentType());
                if (equipType.isEmpty()) errors.add(equipTypeRequired);
                else {
                    String wanted = equipType;
                    if (equipmentTypes(dept).stream().noneMatch(x -> x.code().equals(wanted))) errors.add(noEquipType);
                }
            }
        } else if (car.isEmpty()) {
            errors.add(vehicleOrWayside);
        }
        boolean over = checks.last(in, errors);
        SecondaryRepairForms.fail(errors, checks.width, checks.maxErrors);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", car);
        data.put("deptCd", dept);
        data.put("equipType", equipType);
        data.put("equipId", equipId);
        data.put("equipLoc", equipLoc);
        data.put("incdNo", SecondaryRepairForms.strip(n.incident()));
        data.put("jobPrtCtlNo", SecondaryRepairForms.strip(n.printControlNo()));
        data.put("workType", in.workType());
        data.put("dateIn", date);
        data.put("reprArea", in.repairArea());
        data.put("cntCmpntsIn", in.quantity());
        data.put("stockNo", in.stockNo());
        data.put("serNo", SecondaryRepairForms.strip(n.serialNo()));
        data.put("sentFrom", SecondaryRepairForms.strip(n.sentFrom()));
        data.put("userGrp", userGroup == null ? "" : userGroup);
        data.put("termLastUpd", terminal == null ? "" : terminal);
        data.put("narr", n.comment() == null ? "" : n.comment());
        ProgramResult r = programs.attempt("VHSR01E", Map.of("VHSR01E_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHSR01E", r.control());
        String job = text(r.record("VHSR01E_DATA"), "jobNo");
        String message = (jobNo + String.format("%-6s", job) + String.format("%-19s", created) + (over ? warning : "")).strip();
        return new Created(job, message, over);
    }
}
