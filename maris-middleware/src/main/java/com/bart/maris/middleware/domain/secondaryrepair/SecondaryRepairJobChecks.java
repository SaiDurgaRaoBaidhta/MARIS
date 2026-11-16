package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.ProgramResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The checks a new job (VHSR01IS-CREATE) and a new sub job (VHSR08IS-CREATE) share, in the old order: work type
 * (VHSRWT), date in (AFCXVLD, then not in the future and not older than the screen's limit), repair area (VHSRRA), stock
 * number (VHSRVS), batch quantity, and the stock's threshold warning (VHSR01D, VHSR01A). Each screen has its own copy
 * of the texts; {@code function} names the one read.
 */
public final class SecondaryRepairJobChecks {
    private static final String PKG = "MRSSecondaryRepair";

    final String badWorkType, badDate, futureDate, oldDate, badArea, stockRequired, badStock, badQuantity;
    final int maxDays, width, maxErrors;
    /** the work type the screens start with */
    final String defaultWorkType;
    private final Programs programs;

    SecondaryRepairJobChecks(Programs programs, EglTexts t, String screen) {
        this.programs = programs;
        String create = screen + "S-CREATE", chk = screen + "S-DATE-CHK";
        badWorkType = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Work Type");
        badDate = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Invalid Date");
        futureDate = t.assigned(chk, PKG, "ERROR-MESSAGE-OCCR", "Future");
        oldDate = t.assigned(chk, PKG, "ERROR-MESSAGE-OCCR", "too old");
        badArea = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Repr Area");
        stockRequired = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Stock No required");
        badStock = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Invalid Stock");
        badQuantity = t.assigned(create, PKG, "ERROR-MESSAGE-OCCR", "Batch qty");
        maxDays = SecondaryRepairForms.limit(t, chk, "JULIAN-DAYS-DIFF");
        width = SecondaryRepairForms.limit(t, screen + "S-MOVE-MSG", "ERROR-COUNT");
        maxErrors = SecondaryRepairForms.limit(t, screen + "S-MOVE-MSG", "ERROR-LOOP-INDEX");
        defaultWorkType = t.assigned(screen + "S-INIT", PKG, "WORK-TYPE", null);
    }

    public record Code(String code, String description) {}

    /** VHSRWT: the work types */
    List<Code> workTypes() {
        ProgramResult r = programs.attempt("VHSRWT", Map.of()).result();
        List<Code> out = new ArrayList<>();
        if (!SecondaryRepairForms.found(r)) return out;
        for (Map<String, Object> row : rows(r, "VHSRWT_DATA", "dataRow")) out.add(new Code(text(row, "workType"), text(row, "workTypeDesc")));
        return out;
    }

    /** VHSRRA: the repair areas */
    @SuppressWarnings("unchecked")
    List<Code> repairAreas() {
        ProgramResult r = programs.attempt("VHSRRA", Map.of()).result();
        List<Code> out = new ArrayList<>();
        if (!SecondaryRepairForms.found(r)) return out;
        for (Map<String, Object> row : rows(r, "VHSRRA_DATA", "dataRow")) {
            Map<String, Object> g = row.get("reprGroup") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            out.add(new Code(text(g, "reprArea"), text(g, "reprAreaDesc")));
        }
        return out;
    }

    /** the fields the checks read, as the screen holds them */
    record Input(String workType, String screenDate, boolean dateGiven, String repairArea, String stockNo, int quantity) {}

    /** work type, date in, repair area: the checks before a screen's own ones */
    void first(Input in, LocalDate today, List<String> errors) {
        if (in.workType().isEmpty() || workTypes().stream().noneMatch(c -> c.code().equals(in.workType()))) errors.add(badWorkType);
        if (!in.dateGiven() || in.screenDate() == null) {
            errors.add(badDate);
        } else if (SecondaryRepairForms.afcxvld(programs, in.screenDate()) == SecondaryRepairForms.DateCheck.INCORRECT) {
            errors.add(badDate);
        } else {
            LocalDate d = LocalDate.parse(in.screenDate(), SecondaryRepairForms.SCREEN_DATE);
            switch (SecondaryRepairForms.age(today, d, maxDays)) {
                case FUTURE -> errors.add(futureDate);
                case TOO_OLD -> errors.add(oldDate);
                default -> { }
            }
        }
        if (in.repairArea().isEmpty() || repairAreas().stream().noneMatch(c -> c.code().equals(in.repairArea()))) errors.add(badArea);
    }

    /** stock number, batch quantity; true when the stock's threshold is passed (a warning, not an error) */
    boolean last(Input in, List<String> errors) {
        boolean stockOk = true;
        if (in.stockNo().isEmpty()) {
            errors.add(stockRequired);
            stockOk = false;
        } else {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dataRow", Map.of("stockNo", in.stockNo()));            // VHSRVS_DATA.STOCK-NO sits in its one DATA_ROW
            ProgramResult r = programs.attempt("VHSRVS", Map.of("VHSRVS_DATA", data)).result();
            String found = text(r.record("VHSRVS_DATA"), "recordFound");
            if (found.equals("N")) {
                errors.add(badStock);
                stockOk = false;
            } else if (!found.equals("Y")) {
                throw programs.error("VHSRVS", r.control());
            }
        }
        if (!stockOk) return false;
        if (in.quantity() < 1) {
            errors.add(badQuantity);
            return false;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("stockNo", in.stockNo());
        ProgramResult rd = programs.attempt("VHSR01D", Map.of("VHSR01D_CRIT", d)).result();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("stockNo", in.stockNo());
        a.put("retInd", number(rd.record("VHSR01D_CRIT"), "rowCount") >= 1 ? "BOTH" : "");
        ProgramResult ra = programs.attempt("VHSR01A", Map.of("VHSR01A_CRIT", a)).result();
        int urc = ra.control() == null ? 0 : ra.control().returnCode();
        if (urc != 0 && urc != 4) return false;
        Map<String, Object> data = ra.record("VHSR01A_DATA");
        long threshold = number(data, "cntThreshold");
        return threshold > 0 && number(data, "sumCntCmpntsIn") + in.quantity() > threshold;
    }
}
