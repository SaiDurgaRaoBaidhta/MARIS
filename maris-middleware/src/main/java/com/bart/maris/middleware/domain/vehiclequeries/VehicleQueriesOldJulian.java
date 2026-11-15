package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.domain.common.EglTexts;
import java.util.Map;
import java.util.SortedMap;

/**
 * VHST04IS-CL-WASH's date arithmetic, step for step as the old screen does it: MRSCommonCode XDATECON-GREG2JUL (a
 * mm-dd-yyyy date to the number CCYYDDD, with the month table XDATECON-LOAD-TBL) and XJULIAN-FIX-S (days between two
 * such numbers across a year end), with the old number fields' rules: unsigned, and cut to their digits. The old screen
 * compares against VHST01IW.REV-DATE-JULIAN, which no screen ever sets, so the "today" of these sums is 0.
 */
final class VehicleQueriesOldJulian {
    private static final String PKG = "MRSVehicleQueries";
    private static final long REV_DATE_JULIAN = 0;                // VHST01IW.REV-DATE-JULIAN: never assigned

    private final int[] monthStart = new int[13];
    private final String earliest, markB, markS, markT;
    private final long yearDays;

    VehicleQueriesOldJulian(EglTexts t) {
        SortedMap<Integer, String> tbl = t.indexed("XDATECON-LOAD-TBL", "MRSCommonCode", "WS-MO-TBL");
        for (Map.Entry<Integer, String> e : tbl.entrySet()) monthStart[e.getKey()] = Integer.parseInt(e.getValue().substring(5, 8));
        earliest = t.compared("VHST04IS-CL-WASH", PKG, "DATE-NXT-CAR-WASH").get(0);
        yearDays = Long.parseLong(t.compared("VHST04IS-CL-WASH", PKG, "WASH-DAYS").get(0));
        markB = t.assignedAll("VHST04IS-CL-WASH", PKG, "CLEAN-B").get(0);
        markS = t.assignedAll("VHST04IS-CL-WASH", PKG, "CLEAN-S").get(0);
        markT = t.assignedAll("VHST04IS-CL-WASH", PKG, "CLEAN-T").get(0);
        if (tbl.size() != 12) throw new IllegalStateException("XDATECON-LOAD-TBL month table not found");
    }

    record Clean(long washDays, String clean) {}

    /** one car's wash days and overdue cleaning marks (B bi-weekly, S shampoo, T thorough) from its VHST04B row */
    Clean clean(Map<String, Object> row, long biweekly, long shampoo, long thorough) {
        String next = chars(str(row, "dateNxtCarWash"), 10);
        if (next.compareTo(chars(earliest, 10)) < 0) next = chars(earliest, 10);
        long julDate = num(greg2jul(next), 7);
        long wash = num(longOf(row.get("washDays")), 7);
        if (julDate < REV_DATE_JULIAN) wash = num(REV_DATE_JULIAN - julDate, 7);
        if (wash > yearDays) wash = num(fix(julDate, REV_DATE_JULIAN), 7);
        char[] clean = cleanText(row.get("clean")).toCharArray();
        if (overdue(str(row, "dateCarCleanBwk"), biweekly)) clean[0] = markB.charAt(0);
        if (overdue(str(row, "dateCarCleanShm"), shampoo)) clean[1] = markS.charAt(0);
        if (overdue(str(row, "dateCarCleanThr"), thorough)) clean[2] = markT.charAt(0);
        return new Clean(wash, new String(clean));
    }

    private boolean overdue(String date, long limit) {
        if (date.isBlank()) return true;
        long c = num(greg2jul(chars(date, 10)), 7);
        long elapsed = num(REV_DATE_JULIAN - c, 6);
        if (elapsed > yearDays) elapsed = num(fix(c, REV_DATE_JULIAN), 6);
        return elapsed > limit;
    }

    /** XDATECON-GREG2JUL with flag "R" (mm-dd-yyyy) */
    long greg2jul(String mmddyyyy) {
        String s = chars(mmddyyyy, 10);
        long mo = Math.abs(zoned(s.substring(0, 2))), dy = Math.abs(zoned(s.substring(3, 5))), cc = Math.abs(zoned(s.substring(6, 8))),
                yr = Math.abs(zoned(s.substring(8, 10)));
        long ddd = num((mo >= 1 && mo <= 12 ? monthStart[(int) mo] : 0) + dy, 3);
        long cy = cc * 100 + yr;
        if (cy % 4 == 0 && mo > 2) ddd = num(ddd + 1, 3);             // WS-DATES-DAYS-3: the decimals of CY / 4
        return cc * 100000 + yr * 1000 + ddd;
    }

    /** XJULIAN-FIX-S */
    static long fix(long low, long high) {
        long lowCy = low / 1000;
        long rm = (lowCy * 100 / 400) % 100;                            // LEAPVALUE-RM: the 2 decimals of CY / 400
        long highDdd = num(high % 1000 + (rm > 0 ? 365 : 366), 3);
        long newHigh = (high / 1000) * 1000 + highDdd;
        long nw = num(newHigh - low - 1000, 7);
        return num(nw % 1000 + ((nw / 1000) % 100) * 365, 4);
    }

    /** an unsigned old number field of {@code digits} digits */
    static long num(long v, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) mod *= 10;
        return Math.abs(v) % mod;
    }

    /** text as a CHAR field of {@code len} holds it: cut, or padded with blanks */
    static String chars(String s, int len) {
        String v = s == null ? "" : s;
        if (v.length() > len) return v.substring(0, len);
        return v + " ".repeat(len - v.length());
    }

    /** a zoned number read from text, as the old screens read a NUM over a CHAR: low half of each byte, sign in the last */
    static long zoned(String s) {
        long v = 0;
        for (int i = 0; i < s.length(); i++) v = v * 10 + (s.charAt(i) & 0xFF) % 16;
        int last = s.isEmpty() ? '0' : s.charAt(s.length() - 1) & 0xFF;
        return last < 48 || last > 63 ? -v : v;
    }

    /** CLEAN: the group CLEAN-B, CLEAN-S, CLEAN-T */
    private static String cleanText(Object v) {
        if (v instanceof Map<?, ?> m) return chars(String.valueOf(m.get("cleanB") == null ? "" : m.get("cleanB")), 1)
                + chars(String.valueOf(m.get("cleanS") == null ? "" : m.get("cleanS")), 1) + chars(String.valueOf(m.get("cleanT") == null ? "" : m.get("cleanT")), 1);
        return chars(v == null ? "" : v.toString(), 3);
    }

    private static String str(Map<String, Object> row, String f) {
        Object v = row.get(f);
        return v == null ? "" : v.toString();
    }

    private static long longOf(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? 0 : Long.parseLong(v.toString().strip()); } catch (NumberFormatException e) { return 0; }
    }
}
