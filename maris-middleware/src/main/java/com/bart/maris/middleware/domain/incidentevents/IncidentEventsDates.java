package com.bart.maris.middleware.domain.incidentevents;

/**
 * MRSCommonCode.XXDATES-CONVERT as the incident event screens use it: "MDY" turns YYYY-MM-DD into MM-DD-YYYY and "YMD"
 * the other way; when the year part is not numeric the text is copied unchanged (blank stays blank).
 */
final class IncidentEventsDates {
    private IncidentEventsDates() {}

    /** YYYY-MM-DD to MM-DD-YYYY */
    static String mdy(String ymd) {
        String s = pad(ymd);
        if (!digits(s.substring(0, 4))) return ymd == null ? "" : ymd.strip();
        return s.substring(5, 7) + "-" + s.substring(8, 10) + "-" + s.substring(0, 4);
    }

    /** MM-DD-YYYY to YYYY-MM-DD */
    static String ymd(String mdy) {
        String s = pad(mdy);
        if (!digits(s.substring(6, 10))) return mdy == null ? "" : mdy.strip();
        return s.substring(6, 10) + "-" + s.substring(0, 2) + "-" + s.substring(3, 5);
    }

    private static String pad(String s) {
        return String.format("%-10s", s == null ? "" : s).substring(0, 10);
    }

    private static boolean digits(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }
}
