package com.bart.maris.middleware.domain.configtracking;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The car configuration report's printer window (old screen VFCF05I, opened by VHCF01I's Print with the car): the
 * printers (VHYS01IS-INIT-X, WS70A01) and Hard Copy (VHCF05IS-SUBMIT, VHCF05A with the chosen printer's VTAM name and
 * the car). Hard Copy is enabled only once a printer is chosen.
 */
public final class ConfigTrackingReportService {
    public record Printer(String description, String vtamName) {}

    public record Printers(List<Printer> printers, List<String> warnings) {}

    private final Programs programs;

    public ConfigTrackingReportService(LegacyProgramService legacy, MessageResolver messages) {
        this.programs = new Programs(legacy, messages);
    }

    /** VHYS01IS-INIT-X: the printers (a failure is a warning, as the old message box) */
    public Printers printers() {
        Programs.Outcome o = programs.attempt("WS70A01", Map.of());
        List<Printer> out = new ArrayList<>();
        if (o.ok()) for (Map<String, Object> r : rows(o.result(), "WS70A01_DATA", "dataRow")) out.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return new Printers(out, o.ok() ? List.of() : List.of(o.error()));
    }

    /** VHCF05IS-SUBMIT: the report for the car on the printer (URC 0, 4 and 100 succeed) */
    public void print(String car, String vtamName) {
        String printer = vtamName == null ? "" : vtamName.strip();
        if (printer.isEmpty()) throw new InvalidRequestException("Choose a printer.");       // the old Hard Copy stays disabled until one is chosen
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptType", printer);
        crit.put("carNo", car == null ? "" : car.strip());
        programs.run("VHCF05A", Map.of("VHCF05A_CRIT", crit));
    }
}
