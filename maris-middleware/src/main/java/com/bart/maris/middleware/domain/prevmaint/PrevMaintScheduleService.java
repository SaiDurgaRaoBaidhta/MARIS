package com.bart.maris.middleware.domain.prevmaint;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schedule or reschedule a car's PM (old VHPMS01I). Rules from MRSVehiclePrevMaint RUIFunctions: VHPMS01IS-PM (the PM
 * description, programs VHPMSA2 + VHPMSA1) and VHPMS01IS-SAVE (checks, VHPMSA3, VHXREVA, then the update VHPMS). Every
 * text, list and code is read from the screen's own statements.
 */
public final class PrevMaintScheduleService {
    private static final String PKG = "MRSVehiclePrevMaint";
    private static final String SAVE = "VHPMS01IS-SAVE";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy"), TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Programs programs;
    private final PrevMaintRecordText fields;
    private final Clock clock;
    private final String terminal;
    private final List<String> options, pmTypes, carTypes, shortTypes, methods, critOptions, done1, done3, captions;
    private final String invalidCar, inPm, notNextDigits, notNextLetters, badIndex, resch1, resch3, notScheduled, noRecord, inPmStatus;

    public PrevMaintScheduleService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, PrevMaintRecordText fields, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.fields = fields;
        this.clock = clock;
        this.terminal = terminal;
        options = t.items("VHPMS01I", "gpbPMOption").stream().map(String::strip).toList();
        captions = t.assignedAll("VHPMS01IS-OPT", PKG, "BUTTON-CAPTION");
        pmTypes = t.compared(SAVE, PKG, "PM-TYPE");
        carTypes = t.compared(SAVE, PKG, "CAR-TYPE-1");
        inPmStatus = t.compared(SAVE, PKG, "VEH-STAT").get(0);
        shortTypes = t.compared("VHPMS01IS-PM", PKG, "PM-TYPE").stream().filter(s -> s.length() == 2 && s.startsWith("0")).toList();
        methods = t.assignedAll("VHPMS01IS-PM", PKG, "PM-METHOD");
        critOptions = t.assignedAll(SAVE, PKG, "PM-OPTION");
        done1 = t.assignedAll(SAVE, PKG, "DISP-MSG-1");
        done3 = t.assignedAll(SAVE, PKG, "DISP-MSG-3");
        invalidCar = t.assigned(SAVE, PKG, "DISP-ERR-MSG", "Car No");
        inPm = t.assigned(SAVE, PKG, "DISP-ERR-MSG", "already in PM");
        notNextDigits = t.assigned(SAVE, PKG, "DISP-ERR-MSG", "1 - 6");
        notNextLetters = t.assigned(SAVE, PKG, "DISP-ERR-MSG", "A - D");
        badIndex = t.assigned(SAVE, PKG, "DISP-ERR-MSG", "INAVLID");
        resch1 = t.assignedRaw(SAVE, PKG, "RESCH-MESSAGE-1").get(0);
        resch3 = t.assignedRaw(SAVE, PKG, "RESCH-MESSAGE-3").get(0);
        notScheduled = t.triggeredPrompt("VHPMS01I", "SCH-MSG");
        noRecord = t.triggeredPrompt("VHPMS01I", "NO-REC-FOUND");
    }

    /** @param options the two choices (schedule, reschedule) with the Save button's caption for each */
    public record Option(int option, String label, String button) {}

    public record Options(List<Option> options, List<String> pmTypes) {}

    public Options options() {
        java.util.ArrayList<Option> out = new java.util.ArrayList<>();
        for (int i = 0; i < options.size(); i++) out.add(new Option(i + 1, options.get(i), i < captions.size() ? captions.get(i) : ""));
        return new Options(out, pmTypes);
    }

    public record Description(String pmType, String description) {}

    /** VHPMS01IS-PM: "01".."06" become "1".."6"; the description of the car type's PM (VHPMSA2, then VHPMSA1) */
    public Description describe(String car, String pmType) {
        String type = normalized(pmType);
        Map<String, Object> d2 = programs.attempt("VHPMSA2", Map.of("VHPMSA2_DATA", Map.of("carNo", clean(car)))).result().record("VHPMSA2_DATA");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carType", fields.text("VHPMSA2", "VHPMSA2_DATA", d2, "carType"));
        crit.put("pmType", type);
        crit.put("pmMethod", method(type));
        crit.put("homeYrd", fields.text("VHPMSA2", "VHPMSA2_DATA", d2, "homeYrd"));
        Programs.Outcome a1 = programs.attempt("VHPMSA1", Map.of("VHPMSA1_CRIT", crit));
        String desc = a1.ok() ? fields.text("VHPMSA1", "VHPMSA1_DATA", a1.result().record("VHPMSA1_DATA"), "ciDesc") : "";
        return new Description(type, desc);
    }

    /** @param incident the incident VHPMS created or updated */
    public record Saved(String message, String incident) {}

    /** VHPMS01IS-SAVE. @param option 1 schedule, 2 reschedule */
    public Saved save(String car, String pmType, int option, String userGroup) {
        String c = clean(car);
        Description d = describe(c, pmType);
        String type = d.pmType();
        // the old screen's checks set "Invalid Car No" or "Invalid PM Type", but its else branch then tests VHPMSA3's car
        // type, still blank because VHPMSA3 was not called, and shows "Invalid Car No" for both (kept as the old screen)
        boolean badCar = c.isEmpty() || !c.chars().allMatch(Character::isDigit);
        if (badCar || type.isEmpty() || !pmTypes.contains(type)) throw new InvalidRequestException(invalidCar);
        int opt = option == 2 ? 2 : 1;

        Map<String, Object> a3in = new LinkedHashMap<>();
        a3in.put("carNo", c);
        a3in.put("pmDescription", d.description());
        ProgramResult r = programs.attempt("VHPMSA3", Map.of("VHPMSA3_DATA", a3in)).result();
        Map<String, Object> a3 = r.record("VHPMSA3_DATA");
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String error = null;
        if (urc == 0 || urc == 4) {
            String carType = fields.text("VHPMSA3", "VHPMSA3_DATA", a3, "carType");
            String current = fields.text("VHPMSA3", "VHPMSA3_DATA", a3, "currSchdPmNo"), next = fields.text("VHPMSA3", "VHPMSA3_DATA", a3, "nextSchdPmNo");
            if (carType.isEmpty() || !carTypes.contains(carType.substring(0, 1))) error = invalidCar;
            else if (vehicleStatus(a3).equals(inPmStatus)) error = inPm;
            else if (opt == 1 && !current.isEmpty()) {
                throw new InvalidRequestException((resch1 + String.format("%-2s", current) + resch3).strip());
            } else if (opt == 2 && current.isEmpty()) {
                throw new InvalidRequestException(notScheduled);
            } else if (!next.equals(type)) {
                error = !next.isEmpty() && next.chars().allMatch(Character::isDigit) && pmTypes.contains(next) ? notNextDigits : notNextLetters;
            }
        }   // any other answer: the old screen carries on to the update (its error branch belongs to the checks above)
        if ("Y".equals(text(a3, "errorIndx"))) error = badIndex;
        if (error != null) throw new InvalidRequestException(error);

        Map<String, Object> crit = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        if (opt == 1) {
            LocalDateTime now = LocalDateTime.now(clock);
            data.put("dateIncdOccr", now.format(DATE));
            data.put("timeIncdOccr", now.format(TIME));
            Map<String, Object> rev = new LinkedHashMap<>();
            rev.put("selIdx", 1);
            rev.put("systemDate", data.get("dateIncdOccr"));
            rev.put("systemTime", data.get("timeIncdOccr"));
            Programs.Outcome x = programs.attempt("VHXREVA", Map.of("VHXREVA_CRIT", rev));
            if (x.ok()) data.put("dateIncdOccrRev", fields.text("VHXREVA", "VHXREVA_DATA", x.result().record("VHXREVA_DATA"), "revenueDate"));
        }
        crit.put("pmOption", critOptions.get(opt - 1));
        crit.put("carNo", c);
        crit.put("pmType", type);
        data.put("termLastUpd", terminal);
        data.put("userGrp", userGroup == null ? "" : userGroup);
        data.put("nextSchdPmNo", type);
        data.put("pmDescription", d.description());
        ProgramResult s = programs.attempt("VHPMS", Map.of("VHPMS_CRIT", crit, "VHPMS_DATA", data)).result();
        int surc = s.control() == null ? 0 : s.control().returnCode();
        if (surc != 0 && surc != 4) {
            if ("Y".equals(text(s.record("VHPMS_DATA"), "noRecFound"))) throw new InvalidRequestException(noRecord);
            throw programs.error("VHPMS", s.control());
        }
        String incident = fields.text("VHPMS", "VHPMS_DATA", s.record("VHPMS_DATA"), "incdNo");
        String message = (String.format("%-35s%-9s", done1.get(opt - 1), incident) + done3.get(opt - 1)).stripTrailing();
        return new Saved(message, incident);
    }

    /** the PM method of a PM type: letters one, digits the other (VHPMS01IS-PM) */
    private String method(String type) {
        if (type.isEmpty() || !pmTypes.contains(type)) return "";
        return Character.isDigit(type.charAt(0)) ? methods.get(1) : methods.get(0);
    }

    private String normalized(String pmType) {
        String t = pmType == null ? "" : pmType.strip();
        return shortTypes.contains(t) ? t.substring(1) : t;
    }

    /** VEH-STAT, the first part of DISP-VEH-STAT */
    private static String vehicleStatus(Map<String, Object> a3) {
        return a3 != null && a3.get("dispVehStat") instanceof Map<?, ?> m ? String.valueOf(m.get("vehStat") == null ? "" : m.get("vehStat")).strip() : "";
    }


    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
