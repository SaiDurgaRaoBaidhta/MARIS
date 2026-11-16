package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Car hours after a test run (old VHVM08I): a car's hours (VHVM1A5), and adding test hours to them (VHVM8A1). The
 * test hours count against the hours to the next PM and towards the hours from the last PM; the cumulative hours stay
 * (VHVM08IS-CALC). The messages are the screen's own message boxes.
 */
public final class VehicleMaintCarHoursService {
    private static final String SCREEN = "VHVM08I";

    private final Programs programs;
    private final String terminal;
    private final String notFound, updated;

    public VehicleMaintCarHoursService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        notFound = t.triggeredPrompt(SCREEN, "BAD-CAR");
        updated = t.triggeredPrompt(SCREEN, "UPDATED");
    }

    public record CarHours(String car, String location, long cumulativeHours, long hoursToNextPm, long hoursFromLastPm, String whereIsCar) {}

    public record Hours(long cumulativeHours, long hoursToNextPm, long hoursFromLastPm) {}

    public record Updated(String message, Hours hours) {}

    /** VHVM08IS-GET-CAR: the car's hours (the screen sends the car in both records, and the program reads it from the data record); a car VHVM1A5 does not find gets the screen's "not found" message */
    public CarHours car(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || c.length() > 4) throw new InvalidRequestException(notFound);
        ProgramResult r = programs.attempt("VHVM1A5", Map.of("VHVM1A5_CRIT", Map.of("carNo", c), "VHVM1A5_DATA", Map.of("carNo", c))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw new InvalidRequestException(notFound);
        Map<String, Object> d = r.record("VHVM1A5_DATA");
        return new CarHours(c, text(d, "currLoc"), number(d, "cumHrs"), number(d, "hrsToNextPm"), number(d, "hrsFromLastPm"), text(d, "whereIsCar"));
    }

    /** VHVM08IS-CALC: the hours after the test */
    public static Hours after(CarHours c, int testHours) {
        return new Hours(c.cumulativeHours(), c.hoursToNextPm() - testHours, c.hoursFromLastPm() + testHours);
    }

    /** the test hours field (NUM 3): blank is 0 */
    public static int testHours(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) return 0;
        if (!v.chars().allMatch(Character::isDigit) || v.length() > 3) throw new InvalidRequestException("Test hours are a whole number from 0 to 999.");
        return Integer.parseInt(v);
    }

    /** VHVM08IS-GET-CAR, VHVM08IS-CALC and VHVM08IS-UPDATE: records the hours after the test (VHVM8A1) */
    public Updated addTestHours(String car, String testHours) {
        int test = testHours(testHours);
        CarHours c = car(car);
        Hours h = after(c, test);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cumHrs", h.cumulativeHours());
        data.put("hrsToNextPm", h.hoursToNextPm());
        data.put("hrsFromLastPm", h.hoursFromLastPm());
        data.put("termLastUpd", terminal);
        ProgramResult r = programs.attempt("VHVM8A1", Map.of("VHVM8A1_CRIT", Map.of("carNo", c.car()), "VHVM8A1_DATA", data)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw new InvalidRequestException(notFound);
        return new Updated(updated, h);
    }
}
