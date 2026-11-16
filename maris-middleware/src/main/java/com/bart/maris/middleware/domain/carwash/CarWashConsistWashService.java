package com.bart.maris.middleware.domain.carwash;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wash of a whole consist (old VHCWA2I, MRSVehicleCarWash RUIFunctions): the consist's cars on a revenue date
 * (VHCWA2I-GET-CARS: VHCWA5, then VHCWA3), and the wash record (VHCWA2IS-UPDATE: VHCWA6, which writes). The checks are
 * VHCWA2IW-OK's; when the typed time is wrong the old screen shows that check's message, so this does too.
 */
public final class CarWashConsistWashService {
    private static final String PKG = "MRSVehicleCarWash";
    /** VHCWA5_DATA's DATA_ROW occurrences (a consist has at most 10 cars) */
    private static final int CONSIST_ROWS = 10;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final Programs programs;
    private final String terminal;
    private final String enterData, carsFound, carsNotFound, alreadyWashed, pressUpdate, correctErrors, updated, notUpdated, useDashNo, guiName;
    private final List<String> yards;

    public CarWashConsistWashService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        enterData = t.assigned("VHCWA2IS-BEGIN", PKG, "MSG", null);
        List<String> found = t.assignedAll("VHCWA2I-GET-CARS", PKG, "MSG");
        carsFound = found.get(0);
        carsNotFound = found.get(1);
        alreadyWashed = t.assignedUnder("VHCWA2I-GET-CARS", PKG, "MSG", "WASH-IND").get(0);
        useDashNo = t.assigned("VHCWA2I-GET-CARS", PKG, "USE-DASH-NO", null);
        pressUpdate = t.assigned("VHCWA2IW-OK", PKG, "MSG", "Press");
        correctErrors = t.assigned("VHCWA2IW-OK", PKG, "MSG", "Correct");
        yards = t.compared("VHCWA2IW-OK", PKG, "CAR-WASH-LOC");
        updated = t.assigned("VHCWA2IS-UPDATE", PKG, "MSG", "Updated");
        notUpdated = t.assigned("VHCWA2IS-UPDATE", PKG, "MSG", "problem");
        guiName = t.assigned("VHCWA2IS-UPDATE", PKG, "GUI-NAME", null);
    }

    /** @param washDate the revenue date the old screen starts with (yyyy-MM-dd) */
    public record Start(String washDate, List<String> yards, String message) {}

    /** VHCWA2I opening: VHCWA2I-CAL-REV and VHCWA2IS-BEGIN */
    public Start start() {
        return new Start(CarWashLegacy.revenueDate(programs), yards, enterData);
    }

    /** @param found false when the consist ran nowhere that day; @param alreadyWashed VHCWA5's WASH-IND */
    public record Consist(String consist, String date, int dashNo, List<String> cars, boolean found, boolean alreadyWashed, String message) {}

    private record Lookup(Consist consist, Map<String, Object> carsRecord, int count) {}

    /** VHCWA2I-GET-CARS: the cars of a consist in service on a revenue date (yyyy-MM-dd) */
    public Consist consist(String consist, String date) {
        return lookup(consist, date).consist();
    }

    private Lookup lookup(String consist, String date) {
        String con = consist == null ? "" : consist.strip();
        String d = iso(date);
        if (con.isEmpty()) throw new InvalidRequestException(correctErrors);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNo", con);
        crit.put("dateInsrvRev", d);
        crit.put("useDashNo", useDashNo);
        List<Map<String, Object>> blank = new ArrayList<>();                 // VHCWA5_DATA.WASH-IND = "N": every row of the record
        for (int i = 0; i < CONSIST_ROWS; i++) blank.add(Map.of("washInd", "N"));
        Map<String, Object> cars0 = new LinkedHashMap<>();
        cars0.put("dataRow", blank);
        ProgramResult r = programs.attempt("VHCWA5", Map.of("VHCWA5_CRIT", crit, "VHCWA5_DATA", cars0)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode(), rows = r.control() == null ? 0 : r.control().rowCount();
        if (!((urc == 0 || urc == 4) && rows > 0)) {
            return new Lookup(new Consist(con, d, 0, List.of(), false, false, carsNotFound), r.record("VHCWA5_DATA"), 0);
        }
        Map<String, Object> carsRecord = r.record("VHCWA5_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = carsRecord != null && carsRecord.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<String> cars = new ArrayList<>();
        for (int i = 0; i < Math.min(10, list.size()); i++) cars.add(text(list.get(i), "carNo"));
        while (!cars.isEmpty() && cars.get(cars.size() - 1).isEmpty()) cars.remove(cars.size() - 1);
        int dash = (int) number(r.record("VHCWA5_CRIT"), "dashNo");
        Map<String, Object> crit3 = new LinkedHashMap<>();
        crit3.put("numCars", rows);
        ProgramResult r3 = programs.attempt("VHCWA3", Map.of("VHCWA3_CRIT", crit3, "VHCWA5_DATA", carsRecord)).result();
        if (r3.record("VHCWA5_DATA") != null) carsRecord = r3.record("VHCWA5_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = carsRecord.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        boolean washed = !after.isEmpty() && "Y".equals(text(after.get(0), "washInd"));
        return new Lookup(new Consist(con, d, dash, cars, true, washed, washed ? alreadyWashed : carsFound), carsRecord, rows);
    }

    public record Wash(String consist, String date, String time, String yard) {}

    public record Result(String message, Consist consist) {}

    /** VHCWA2IW-OK then VHCWA2IS-UPDATE: records the wash of every car of the consist (VHCWA6) */
    public Result wash(Wash w) {
        Lookup l = lookup(w.consist(), w.date());
        Consist c = l.consist();
        if (!c.found()) throw new InvalidRequestException(c.message());
        if (c.alreadyWashed()) throw new InvalidRequestException(c.message());
        CarWashTime.Parsed time = CarWashTime.parse(w.time());
        String yard = w.yard() == null ? "" : w.yard().strip().toUpperCase(Locale.ROOT);
        if (!yards.contains(yard) || c.dashNo() <= 0 || time.error() != CarWashTime.Error.NONE) throw new InvalidRequestException(correctErrors);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carWashLoc", yard);
        data.put("conNo", c.consist());
        data.put("dashNo", c.dashNo());
        data.put("dateCarWash", c.date());
        data.put("termLastUpd", terminal);
        data.put("timeCarWash", time.time() == null ? "" : time.time());
        data.put("totalCars", l.count());
        data.put("guiName", guiName);
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("VHCWA6_DATA", data);
        in.put("VHCWA5_DATA", l.carsRecord());
        ProgramResult r = programs.attempt("VHCWA6", in).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw CarWashLegacy.failed(programs, "VHCWA6", r.control(), notUpdated);
        return new Result(updated, c);
    }

    /** the screen's ready-to-update hint (VHCWA2IW-OK), for the page */
    public String readyMessage() {
        return pressUpdate;
    }

    private String iso(String date) {
        try {
            return ISO.format(LocalDate.parse(date == null ? "" : date.strip(), ISO));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(correctErrors);
        }
    }
}
