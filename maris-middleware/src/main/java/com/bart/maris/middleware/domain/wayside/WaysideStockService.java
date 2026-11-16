package com.bart.maris.middleware.domain.wayside;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wayside stock number search (old screen WS17I, a look-up other wayside screens open). Rules from
 * MARIS_RUI/EGLSource/MRSWaysideMaintenance/RUIFunctions.egl: WS17IS-REFORMAT, WS17IS-SEARCH (program WS17A05).
 * Read-only.
 */
public class WaysideStockService {
    /** the old screen's description field (WS17IW-PASSVALUE.STOCK-DESCRIPTION, char(10)) and dept code (char(1)) */
    static final int DESCRIPTION_LENGTH = 10, DEPT_LENGTH = 1;

    private final Programs programs;

    public WaysideStockService(LegacyProgramService legacy, MessageResolver messages) {
        this.programs = new Programs(legacy, messages);
    }

    public record StockNumber(String dept, String stockNo, String description) {}

    /** @param message MARIS's message when it found nothing (URC 100), otherwise null */
    public record StockSearch(int count, List<StockNumber> stockNumbers, String message) {}

    public StockSearch search(String description, String dept) {
        String d = description == null ? "" : description;
        String dp = dept == null ? "" : dept.strip().toUpperCase(java.util.Locale.ROOT);
        if (d.length() > DESCRIPTION_LENGTH) throw new InvalidRequestException("The description holds at most " + DESCRIPTION_LENGTH + " characters.");
        if (dp.length() > DEPT_LENGTH) throw new InvalidRequestException("The department code is one character.");

        Programs.Outcome o = programs.attempt("WS17A05", Map.of("WS17A05_CRIT", Map.of("stockDesc", pattern(d), "deptCd", dp)));
        if (!o.ok() && o.result().control().returnCode() != 100) throw programs.error("WS17A05", o.result().control());
        List<StockNumber> out = new ArrayList<>();
        if (o.ok()) {
            for (Map<String, Object> r : rows(o.result(), "WS17A05_DATA", "dataRow")) {
                out.add(new StockNumber(text(r, "deptCd"), text(r, "stockNo"), text(r, "stockDesc")));
            }
        }
        return new StockSearch(out.size(), out, o.ok() ? null : o.error());
    }

    /**
     * WS17IS-REFORMAT: STOCK-DESC = "%" followed by the 10 description characters, where the trailing blank ones
     * (from the end back to the last non-blank) become "%". Inner blanks stay.
     */
    static String pattern(String description) {
        char[] c = String.format("%-" + DESCRIPTION_LENGTH + "s", description).toCharArray();
        for (int i = c.length - 1; i >= 0 && c[i] == ' '; i--) c[i] = '%';
        return "%" + new String(c);
    }
}
