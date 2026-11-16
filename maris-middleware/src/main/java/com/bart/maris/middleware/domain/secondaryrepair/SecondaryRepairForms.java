package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.ProgramResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the secondary repair update screens share: their error list joined the way every *-MOVE-MSG does, the screen's
 * MM-DD-YYYY dates, the AFCXVLD date check (MRSCommonCode.AFCXVLD-VALIDATE) and the 548-day check (*-DATE-CHK).
 */
final class SecondaryRepairForms {
    static final DateTimeFormatter SCREEN_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private SecondaryRepairForms() {}

    /**
     * *-MOVE-MSG: each message (padded to its slot width) is copied up to its first "." which becomes ", "; at most
     * {@code max} messages; the last ", " loses its comma.
     */
    static String join(List<String> messages, int width, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, messages.size()); i++) {
            String m = messages.get(i);
            if (m == null || m.isBlank()) break;
            String slot = (m.length() > width ? m.substring(0, width) : m + " ".repeat(width - m.length()));
            int dot = slot.indexOf('.');
            sb.append(dot < 0 ? slot : slot.substring(0, dot) + ", ");
        }
        if (sb.length() >= 2) sb.setCharAt(sb.length() - 2, ' ');
        return sb.toString().strip();
    }

    /** a request's errors, joined like the old screen; nothing when there are none */
    static void fail(List<String> errors, int width, int max) {
        if (!errors.isEmpty()) throw new InvalidRequestException(join(errors, width, max));
    }

    /** an ISO date (yyyy-MM-dd) as the screens hold it, MM-DD-YYYY; null when it is not a date */
    static String screenDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso.strip()).format(SCREEN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    enum DateCheck { OK, INCORRECT }

    /** AFCXVLD-VALIDATE: the database's own check of a MM-DD-YYYY date; its SQL error is a program error */
    static DateCheck afcxvld(Programs programs, String screenDate) {
        String iso = screenDate.substring(6, 10) + "-" + screenDate.substring(0, 2) + "-" + screenDate.substring(3, 5);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", iso);
        crit.put("otherSqlError", "N");
        Programs.Outcome o = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit));
        Map<String, Object> back = o.result().record("AFCXVLD_CRIT");
        String dateError = back == null ? "N" : text(back, "dateError"), sql = back == null ? "N" : text(back, "otherSqlError");
        if (dateError.equals("Y") && sql.equals("N")) return DateCheck.INCORRECT;
        if (dateError.equals("N") && sql.equals("Y")) throw programs.error("AFCXVLD", o.result().control());
        return DateCheck.OK;
    }

    enum Age { OK, FUTURE, TOO_OLD }

    /**
     * *-DATE-CHK on julian dates (year, day of year): today against the date; a later day in the same year is future, more
     * than 548 days back is too old; across one year the old screen adds 365 or 366 by the date's year divided by 4.
     */
    static Age age(LocalDate today, LocalDate date, int maxDays) {
        int y1 = today.getYear(), d1 = today.getDayOfYear(), y2 = date.getYear(), d2 = date.getDayOfYear();
        if (y1 == y2) {
            if (d2 > d1) return Age.FUTURE;
            return d1 - d2 > maxDays ? Age.TOO_OLD : Age.OK;
        }
        if (Math.abs(y1 - y2) > 1) return Age.TOO_OLD;
        if (y1 > y2) {
            int days = d1 + (y2 % 4 > 0 ? 365 : 366) - d2;
            return days > maxDays ? Age.TOO_OLD : Age.OK;
        }
        return Age.FUTURE;
    }

    /** the first row of a list program is enough to know it answered; a 100 means no rows */
    static boolean found(ProgramResult r) {
        return r.control() == null || r.control().returnCode() != 100;
    }

    /** the largest number a statement group compares a field with, e.g. *-MOVE-MSG's slot width (ERROR-COUNT <= 30) */
    static int limit(EglTexts t, String function, String field) {
        return t.compared(function, "MRSSecondaryRepair", field).stream().filter(x -> x.strip().matches("\\d+"))
                .mapToInt(x -> Integer.parseInt(x.strip())).max().orElseThrow(() -> new IllegalStateException(function + " compares no number with " + field));
    }

    /** a field the record splits into parts (YYYY, "-", MM, ...), read back as one text */
    static String flat(Object v) {
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder();
            for (Object part : m.values()) sb.append(flat(part));
            return sb.toString().strip();
        }
        if (v instanceof java.util.List<?> l) {
            StringBuilder sb = new StringBuilder();
            for (Object part : l) sb.append(part == null ? " " : part instanceof String x && x.isEmpty() ? " " : flat(part));
            return sb.toString().strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    static String upper(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }

    static String strip(String s) {
        return s == null ? "" : s.strip();
    }
}
