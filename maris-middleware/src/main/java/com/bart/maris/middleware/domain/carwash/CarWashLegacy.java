package com.bart.maris.middleware.domain.carwash;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.util.Map;

/** What the car wash update screens share: the revenue date (VHIE01B) and their error answers. */
final class CarWashLegacy {
    private CarWashLegacy() {}

    /**
     * VHCWA2I-CAL-REV / VHCWA3IS-REV-DTE-2: today's date once the revenue day has started (CURRENT-TIME >=
     * TIME-REV-START), otherwise yesterday; yyyy-MM-dd as VHIE01B gives it ("" when it gives nothing)
     */
    static String revenueDate(Programs programs) {
        ProgramResult r = programs.attempt("VHIE01B", Map.of()).result();
        Map<String, Object> d = r.record("VHIE01B_DATA");
        if (d == null) return "";
        String now = raw(d, "currentTime"), start = raw(d, "timeRevStart");
        return now.compareTo(start) >= 0 ? text(d, "currentDate") : text(d, "yesterday");
    }

    private static String raw(Map<String, Object> rec, String field) {
        Object v = rec.get(field);
        return v == null ? "" : v.toString();
    }

    /** XXDATES-CONVERT "MDY": yyyy-MM-dd -> MM-dd-yyyy when the year is numeric, else the text as it is */
    static String mdy(String iso) {
        if (iso == null || iso.length() < 10 || !iso.substring(0, 4).chars().allMatch(Character::isDigit)) return iso == null ? "" : iso;
        return iso.substring(5, 7) + "-" + iso.substring(8, 10) + "-" + iso.substring(0, 4);
    }

    /**
     * a program error the old screen answered with its own text and the program's message box (XXM0I); both are kept,
     * the screen's text first
     */
    static LegacyBusinessException failed(Programs programs, String program, XdControl c, String screenText) {
        String code = c == null || c.messageCode() == null ? "" : c.messageCode().strip();
        String catalog = code.isEmpty() ? null : programs.catalogMessage(code, c);
        boolean hasCatalog = catalog != null && !catalog.isBlank(), hasScreen = screenText != null && !screenText.isBlank();
        if (!hasCatalog && !hasScreen) return new LegacyBusinessException(program, c, null);
        String text = !hasCatalog ? screenText : !hasScreen ? catalog : screenText + " - " + catalog;
        return new LegacyBusinessException(program, c, new MessageResolver.Message(code, "E", text));
    }
}
