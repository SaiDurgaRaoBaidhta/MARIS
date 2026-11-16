package com.bart.maris.middleware.domain.incidentevents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.EglTexts;
import java.util.ArrayList;
import java.util.List;

/**
 * The texts and limits of the work sheet's "CreMaint" button (old VHIE03IS-WHICH-ONE, VHIE04IS-CK-DATA / -CK-DATA-F /
 * -CK-DATA-T, VHIE03IS-CK-DATA-Y / -X, VHIE03IS-BLD-MSG, VHIE03IS-MOVE-MSG, the VHIE04IS-STORE* groups), all read from
 * the screen's statements.
 */
final class IncidentEventsIncidentFiling {
    private static final String PKG = IncidentEventsWorksheetService.PKG;

    final String invalidCarNoConsist, invalidCar, carStillOnCon, dashNotInService, trainProblem, fixProblem;
    final List<String> errors;                                     // VHIE03IS-BLD-MSG's ERROR-MESSAGE-OCCR texts, in source order
    final List<String> directions, digits, outStatuses;
    final String all, inConsist, zeroCar, discvDur, marisFunc, trainCar, reportId, reportIvr, reportIvh, typeRev;
    final String ivrPfx, ivrType, ivrName, fixPfx, fixType, fixName;
    final int maxDays;
    final List<String> ivrOk, fixOk, trainOk, consistOk;

    IncidentEventsIncidentFiling(EglTexts t) {
        invalidCarNoConsist = t.assigned("VHIE03IS-WHICH-ONE", PKG, "MESSAGE", "NO Consist");
        invalidCar = t.assignedAll("VHIE03IS-WHICH-ONE", PKG, "MESSAGE").stream().filter(s -> !s.contains("NO Consist")).findFirst().orElseThrow();
        carStillOnCon = t.assigned("VHIE03IS-CK-DATA-Y", PKG, "MESSAGE", null);
        dashNotInService = t.assignedRaw("VHIE03IS-CK-DATA-X", PKG, "MSG1").get(0);
        trainProblem = t.assigned("VHIE04IS-STOREDB-T", PKG, "MESSAGE", "Train");
        fixProblem = t.assigned("VHIE04IS-STOREDB-F", PKG, "MESSAGE", "Problem");
        errors = t.assignedRaw("VHIE03IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR");
        if (errors.size() != 25) throw new IllegalStateException("VHIE03IS-BLD-MSG has " + errors.size() + " messages, expected 25");
        directions = t.compared("VHIE04IS-CK-DATA", PKG, "CON-DIRECTION");
        digits = t.compared("VHIE04IS-CK-DATA", PKG, "TEMP-SPEED-CHK-CHAR");
        all = t.compared("VHIE04IS-CK-DATA", PKG, "SPEED").get(0);
        outStatuses = t.compared("VHIE04IS-CK-DATA", PKG, "CON-STAT");
        inConsist = t.compared("VHIE04IS-CK-DATA", PKG, "WHERE-IS-CAR").get(0);
        zeroCar = t.compared("VHIE04IS-CK-DATA-F", PKG, "CAR-NO").get(0);
        maxDays = t.compared("VHIE04IS-CK-DATA-F", PKG, "DAYS-DIFF").stream().mapToInt(Integer::parseInt).max().orElseThrow();
        discvDur = t.assigned("VHIE04IS-STORE-DB", PKG, "DISCV-DUR", null);
        marisFunc = t.assigned("VHIE04IS-STORE-DB", PKG, "MARIS-FUNC", null);
        ivrPfx = t.assigned("VHIE04IS-STORE-DB", PKG, "INCD-PFX", null);
        ivrType = t.assigned("VHIE04IS-STORE-DB", PKG, "INCD-TYPE", null);
        ivrName = t.assigned("VHIE04IS-STORE-DB", PKG, "INCIDENT-NAME", null);
        typeRev = t.assigned("VHIE04IS-STOREDB-F", PKG, "TYPE-IND", null);
        int rev = t.compared("VHIE04IS-STOREDB-F", PKG, "TYPE-IND").indexOf(typeRev);
        fixPfx = t.assignedAll("VHIE04IS-STOREDB-F", PKG, "INCD-PFX").get(rev);
        fixType = t.assignedAll("VHIE04IS-STOREDB-F", PKG, "INCD-TYPE").get(rev);
        fixName = t.assignedAll("VHIE04IS-STOREDB-F", PKG, "INCIDENT-NAME").get(rev);
        trainCar = t.assigned("VHIE04IS-STOREDB-T", PKG, "CAR-NO", null);
        reportId = t.assigned("VHIE04IS-CA-CICS", PKG, "REPT-ID", null);
        List<String> types = t.assignedAll("VHIE04IS-CA-CICS", PKG, "REPT-TYPE");
        reportIvr = types.get(0);
        reportIvh = types.get(1);
        ivrOk = success(t, "VHIE04IS-STORE-DB");
        fixOk = success(t, "VHIE04IS-STOREDB-F");
        trainOk = success(t, "VHIE04IS-STOREDB-T");
        consistOk = success(t, "VHIE04IS-STOREDB-X");
    }

    /** SUCCESS-MESSAGE-1, -3 and -5 (spaces kept); -2 is the incident, -4 the car or consist */
    private static List<String> success(EglTexts t, String fn) {
        List<String> out = new ArrayList<>();
        for (int k : new int[] {1, 3, 5}) out.add(t.assignedRaw(fn, PKG, "SUCCESS-MESSAGE-" + k).get(0));
        return out;
    }

    /** VHIE04IW2.SUCCESS-MESSAGE: CHAR 8 + 8 + 9 + 4 + 211 */
    static String successMessage(List<String> parts, String incident, String carOrConsist) {
        return (fit(parts.get(0), 8) + fit(incident, 8) + fit(parts.get(1), 9) + fit(carOrConsist, 4) + fit(parts.get(2), 211)).strip();
    }

    static String fit(String s, int n) {
        return String.format("%-" + n + "s", s == null ? "" : s).substring(0, n);
    }

    /** VHIE03IS-MOVE-MSG: each message up to its first "." (40 characters at most), joined by ", " */
    static String joined(List<String> msgs) {
        StringBuilder b = new StringBuilder();
        for (String m : msgs.subList(0, Math.min(6, msgs.size()))) {
            if (m.isBlank()) break;
            String t40 = fit(m, 40);
            int dot = t40.indexOf('.');
            b.append(dot < 0 ? t40 : t40.substring(0, dot) + ", ");
        }
        if (b.length() >= 2) b.setCharAt(b.length() - 2, ' ');
        return b.toString().strip();
    }

    /** HH and MM of the time typed (DISP-TIME-INCD-OCCR-HR / -MIN, NUM(2)) */
    static int[] hourMinute(String time) {
        String t = fit(time, 8);
        return new int[] {num(t.substring(0, 2)), num(t.substring(3, 5))};
    }

    private static int num(String s) {
        String x = s.strip();
        return x.matches("\\d+") ? Integer.parseInt(x) : 0;
    }

    /** TIME-WORKINGSTORAGE: HH:MM:00 */
    static String workingTime(String time) {
        int[] hm = hourMinute(time);
        return String.format("%02d:%02d:00", hm[0] % 100, hm[1] % 100);
    }

    static boolean badTime(String time) {
        int[] hm = hourMinute(time);
        return hm[0] > 24 || (hm[0] == 24 && hm[1] > 0) || (hm[0] == 0 && hm[1] == 0) || hm[1] > 59;
    }

    boolean badSpeed(String speed) {
        if (speed.isBlank() || speed.equals(all)) return false;
        for (char c : fit(speed, 3).toCharArray()) if (c != ' ' && !digits.contains(String.valueOf(c))) return true;
        return false;
    }

    static long n(java.util.Map<String, Object> m, String f) {
        return number(m, f);
    }

    static String tx(java.util.Map<String, Object> m, String f) {
        return text(m, f);
    }
}
