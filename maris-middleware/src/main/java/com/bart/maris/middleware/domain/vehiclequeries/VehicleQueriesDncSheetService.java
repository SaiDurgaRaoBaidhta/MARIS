package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

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
 * Reprint of the discrepancy and correction (D and C) sheet (old VHDC01I): a blank form, or the sheet of a car and/or
 * incident. Printers come from VHDC011. A blank form is VHDC012 (report BK) and the printBlankDnC service; a car's sheet
 * is first checked by VHDC013, printed by the printDnC service, and recorded by VHDC012 (report REPRINT). The choices,
 * report ids and messages come from the old screen.
 */
public final class VehicleQueriesDncSheetService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final LegacyProgramService legacy;
    private final String yes, no, blankId, reprintId, carProgramName, onlyOne, badCar, badChoice, printed, title;

    public VehicleQueriesDncSheetService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle bundle) {
        this.programs = new Programs(legacy, messages);
        this.legacy = legacy;
        List<String> choices = t.compared("VHDC01I-VALIDATE", PKG, "BLANK-FORM");      // == "Y" ... == "N"
        yes = choices.get(0);
        no = choices.get(1);
        blankId = t.assigned("VHDC01IS-PRINT-BK", PKG, "REPT-ID", null);
        reprintId = t.assigned("VHDC01IS-PRINT-NBK", PKG, "REPT-ID", null);
        carProgramName = VehicleQueriesProgramArguments.text(bundle, "VHDC01IS-VALID-CAR", PKG, "printDnC");
        onlyOne = t.triggeredPrompt("VHDC01I", "NO-BLANK-ERROR").strip();
        badCar = t.triggeredPrompt("VHDC01I", "ERROR-CAR").strip();
        badChoice = t.triggeredPrompt("VHDC01I", "ERROR-BLANK").strip();
        printed = t.triggeredPrompt("VHDC01I", "OK").strip();
        title = t.windowTitle("VHDC01I");
    }

    public record Printer(String location, String vtam) {}

    /** {@code yes} / {@code no}: the answers to "print a blank form" */
    public record Options(String title, String yes, String no, List<Printer> printers) {}

    /** VHDC01IS-INIT: the printers (VHDC011) */
    public Options options() {
        List<Printer> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt("VHDC011", Map.of());
        if (o.ok()) for (Map<String, Object> row : rows(o.result(), "VHDC011_DATA", "dataRow")) out.add(new Printer(text(row, "prtrLoc"), text(row, "cicsVtamName")));
        return new Options(title, yes, no, out);
    }

    /** OK (VHDC01I-VALIDATE); {@code group} and {@code terminal} are what the old menu passed down (VH01A01_DATA) */
    public String print(String blankForm, String car, String incident, String printer, String group, String terminal) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        String b = blankForm == null ? "" : blankForm.strip(), c = car == null ? "" : car.strip(), i = incident == null ? "" : incident.strip();
        String p = printer.strip();
        if (b.equals(yes)) {
            if (!c.isEmpty() || !i.isEmpty()) throw new InvalidRequestException(onlyOne);
            ProgramResult r = record(blankId, p, "", "", group, terminal);
            service("printBlankDnC", inputs("printerID", p, "programName", blankId));      // prints before the check
            checked("VHDC012", r);
            return printed;
        }
        if (!b.equals(no)) throw new InvalidRequestException(badChoice);
        if (c.isEmpty() && i.isEmpty()) throw new InvalidRequestException(onlyOne);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", i);
        crit.put("carNo", c);
        ProgramResult r = programs.attempt("VHDC013", Map.of("VHDC013_CRIT", crit)).result();
        Map<String, Object> found = r.record("VHDC013_DATA");
        if (found != null && !text(found, "carNo").isEmpty()) {                         // the sheet prints before the check
            service("printDnC", inputs("printerID", p, "programName", carProgramName, "carNO", c, "cirNO", i));
        }
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) throw programs.error("VHDC013", r.control());
        if (r.control() == null || r.control().rowCount() <= 0) throw new InvalidRequestException(badCar);
        checked("VHDC012", record(reprintId, p, c, i, group, terminal));
        return printed;
    }

    /** VHDC012: the print request */
    private ProgramResult record(String reportId, String printer, String car, String incident, String group, String terminal) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", reportId);
        crit.put("reptType", printer);
        crit.put("conCar1", car);
        crit.put("incdNo", incident);
        crit.put("termId", terminal == null ? "" : terminal);
        crit.put("userGrp", group == null ? "" : group);
        return programs.attempt("VHDC012", Map.of("VHDC012_CRIT", crit)).result();
    }

    private void checked(String program, ProgramResult r) {
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error(program, r.control());
    }

    /** a print service the old screen calls inside "try": its failure does not stop the screen */
    private void service(String name, Map<String, Object> in) {
        try {
            legacy.execute(name, in, true);
        } catch (RuntimeException e) {
            // like the old try block: nothing to show
        }
    }

    private static Map<String, Object> inputs(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k < kv.length; k += 2) m.put((String) kv[k], kv[k + 1]);
        return m;
    }
}
