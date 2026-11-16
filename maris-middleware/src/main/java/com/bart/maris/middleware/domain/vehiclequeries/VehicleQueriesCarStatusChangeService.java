package com.bart.maris.middleware.domain.vehiclequeries;

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
 * Change a car's vehicle status (old VHCHG1I): read the car's status (VHVM1A2), then record the new one (VHVM1A1, an
 * update). The statuses offered, the confirmation question and the messages come from the old screen.
 */
public final class VehicleQueriesCarStatusChangeService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final List<String> statuses;
    private final String enterCar, enterStatus, notRetrieved, changed, question, gui, title;

    public VehicleQueriesCarStatusChangeService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        statuses = t.items("VHCHG1I", "Drop-down_List1");
        enterCar = t.assigned("VHCHG1IS-GET-DATA", PKG, "MSG", null);
        enterStatus = t.assigned("VHCHG1IS-GET-STAT", PKG, "MSG", "Enter");
        notRetrieved = t.assigned("VHCHG1IS-GET-STAT", PKG, "MSG", "could not");
        changed = t.assigned("VHCHG1IS-UPDATE", PKG, "MSG", null);
        question = t.prompt("VHCHG1I", "msgChangeStatYard").strip();
        gui = t.assigned("VHCHG1IS-GET-STAT", PKG, "GUI", null);
        title = t.windowTitle("VHCHG1I");
    }

    /** {@code message}: the first prompt; {@code question}: asked before the change is made */
    public record Options(String title, List<String> statuses, String message, String question) {}

    public Options options() {
        return new Options(title, statuses, enterCar, question);
    }

    public record CarStatus(String car, String status, String previousStatus, String message) {}

    /** the car's status (VHCHG1IS-GET-STAT: VHVM1A2) */
    public CarStatus car(String car) {
        String c = carNo(car);
        Map<String, Object> d = read(c);
        return new CarStatus(c, text(d, "vehStat"), text(d, "vehStatPrev"), enterStatus);
    }

    /** Update after Yes (VHCHG1IS-UPDATE: VHVM1A1); the car's status is read again first, as the old screen showed it */
    public String change(String car, String status) {
        String c = carNo(car);
        String s = status == null ? "" : status.strip();
        if (!statuses.contains(s)) throw new InvalidRequestException(enterStatus);
        Map<String, Object> current = read(c);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("gui", gui);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carNo", c);
        data.put("vehStat", s);
        data.put("vehStatPrev", text(current, "vehStat"));
        // the old screen links the menu's terminal id both ways with VHVM1A2's TERM-LAST-UPD, so it records the car's
        // last terminal as read, not this one
        data.put("termLastUpd", text(current, "termLastUpd"));
        programs.run("VHVM1A1", Map.of("VHVM1A1_CRIT", crit, "VHVM1A1_DATA", data));
        return changed;
    }

    private Map<String, Object> read(String c) {
        ProgramResult r = programs.attempt("VHVM1A2", Map.of("VHVM1A2_CRIT", Map.of("carNo", c))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4 || r.record("VHVM1A2_DATA") == null) throw new InvalidRequestException(notRetrieved);
        return r.record("VHVM1A2_DATA");
    }

    private String carNo(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || c.length() > 4) throw new InvalidRequestException(notRetrieved);
        return c;
    }
}
