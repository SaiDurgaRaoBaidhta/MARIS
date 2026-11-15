package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.ProgramResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules VHCI02I and VHCI03I (vehicle tracking change incident) share: the backend's date and time (VHCI02G), the
 * status-change date / time / narrative checks (AFCXVLD and VHCI01IS-BLD-MSG's texts), the pseudo-consist reset
 * (VHINXA, VHINXB), the CI message (VHCI01E) and the into-shop update (VHINXD). Texts and fixed values are the
 * screens' own, read from the EGL.
 */
final class IncidentsTrackingChangeSupport {
    static final String PKG = "MRSVehicleIncident";

    final Programs programs;
    final Clock clock;
    final String terminal;
    private final List<String> buildMessages;          // Invalid Date, Future Date, old Date, Invalid Time, Future Time, Location, Narrative
    private final String yes, no, ciReport;

    IncidentsTrackingChangeSupport(Programs programs, EglTexts t, Clock clock, String terminal) {
        this.programs = programs;
        this.clock = clock;
        this.terminal = terminal;
        buildMessages = t.assignedAll("VHCI01IS-BLD-MSG", PKG, "FINAL-ERROR-MESSAGE");
        if (buildMessages.size() < 7) throw new IllegalStateException("VHCI01IS-BLD-MSG has " + buildMessages.size() + " messages");
        yes = t.assigned("VHCI03IS-PSEUDO", PKG, "CAR-IN-CON-X", "Y");
        no = t.assigned("VHCI03IS-PSEUDO", PKG, "CAR-IN-CON-X", "N");
        ciReport = t.assigned("VHCI01IS-CICS-MSG", PKG, "REPT-ID", null);
    }

    String yes() { return yes; }

    String no() { return no; }

    record Now(String date, String time) {}

    /** VHCI02G: the backend's current date (MM-DD-YYYY) and time (HH:MM:SS) */
    Now now() {
        ProgramResult r = programs.run("VHCI02G", Map.of());
        if (r.control() == null || r.control().rowCount() != 1) throw programs.error("VHCI02G", r.control());
        Map<String, Object> c = r.record("VHCI02G_CRIT");
        return new Now(flat(c.get("currentDate")), flat(c.get("currentTime")));
    }

    /** the car number field (NUM 4): digits up to 4, shown with leading zeros; "" when not a number */
    static String car(String in) {
        String c = in == null ? "" : in.strip();
        if (c.isEmpty()) return "0000";
        if (!c.chars().allMatch(Character::isDigit) || c.length() > 4) return "";
        return String.format("%04d", Integer.parseInt(c));
    }

    /**
     * the date, time and narrative checks of VHCI03IS-VALIDATE2 / VHCI02IS-VALIDAT3, answered with
     * VHCI01IS-BLD-MSG's message (the first that applies); a date check the backend cannot make is its catalog error
     */
    void checkDateTime(String date, String time, String narrative, Now now) {
        String d = s(date);
        int error = -1;
        if (d.isEmpty()) {
            error = 0;
        } else {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("dateError", no);
            crit.put("formDate", ymd(d));
            crit.put("otherSqlError", no);
            ProgramResult v = programs.run("AFCXVLD", Map.of("AFCXVLD_CRIT", crit));
            Map<String, Object> vc = v.record("AFCXVLD_CRIT");
            String de = text(vc, "dateError"), oe = text(vc, "otherSqlError");
            if (de.equals(yes) && oe.equals(no)) {
                error = 0;
            } else if (de.equals(no) && oe.equals(yes)) {
                throw programs.error("AFCXVLD", v.control());
            } else {
                String p = pad(d, 10), sys = pad(now.date(), 10);
                int y = num(p.substring(6, 10)), m = num(p.substring(0, 2)), dd = num(p.substring(3, 5));
                int sy = num(sys.substring(6, 10)), sm = num(sys.substring(0, 2)), sd = num(sys.substring(3, 5));
                if (y > sy || (y == sy && m > sm) || (y == sy && m == sm && dd > sd)) error = 1;
                else if (p.compareTo(sys) > 0) error = 1;                              // compared as MM-DD-YYYY text, as the screen does
                else if (julianNow() - julian(p) > 365) error = 2;
            }
        }
        if (error < 0) {
            String tm = s(time);
            if (tm.isEmpty() || !pad(tm, 4).chars().allMatch(Character::isDigit)) {
                error = 3;
            } else {
                String p = pad(tm, 4);
                int hr = num(p.substring(0, 2)), mi = num(p.substring(2, 4));
                if (hr > 24 || (hr == 24 && mi > 0) || (hr == 0 && mi == 0) || mi > 59) error = 3;
                else if (pad(d, 10).equals(pad(now.date(), 10))) {
                    String st = pad(now.time(), 8);
                    int shr = num(st.substring(0, 2)), smi = num(st.substring(3, 5));
                    if (hr > shr || (hr == shr && mi > smi)) error = 4;
                }
            }
        }
        if (error < 0 && s(narrative).isEmpty()) error = 6;
        if (error >= 0) throw new InvalidRequestException(buildMessages.get(error));
    }

    /** VGVar.currentJulianDate: YYYYDDD */
    private long julianNow() {
        return Long.parseLong(LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyDDD")));
    }

    /** XDATECON-GREG2JUL of an MM-DD-YYYY date: CCYYDDD from the month table, a day more after February in a year divisible by 4 */
    private static long julian(String mdy) {
        int m = num(mdy.substring(0, 2)), d = num(mdy.substring(3, 5)), y = num(mdy.substring(6, 10));
        if (m < 1 || m > 12) return 0;
        int ddd = LocalDate.of(2001, m, 1).getDayOfYear() - 1 + d;
        if (y % 4 == 0 && m > 2) ddd++;
        return y * 1000L + ddd;
    }

    /** VHCI03IS-PSEUDO / VHCI02IS-PSEUDO: a car in a pseudo consist (VHINXA) has the consist reset (VHINXB); true when it was */
    boolean pseudo(String car, String group) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        Programs.Outcome a = programs.attempt("VHINXA", Map.of("VHINXA_CRIT", crit));
        if (!a.ok()) return false;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = a.result().record("VHINXA_DATA").get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        Map<String, Object> bc = new LinkedHashMap<>();
        bc.put("conNo", rows.isEmpty() ? "" : text(rows.get(0), "conNo"));
        bc.put("yrdSubLoc", rows.isEmpty() ? "" : text(rows.get(0), "yrdSubLoc"));
        Map<String, Object> bd = new LinkedHashMap<>();
        bd.put("termLastUpd", terminal);
        bd.put("userGroup", group);
        List<Map<String, Object>> cars = new java.util.ArrayList<>();
        List<String> cons = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("carNum", i < rows.size() ? text(rows.get(i), "carNo") : "");
            cars.add(r);
            cons.add(i < rows.size() ? text(rows.get(i), "conNo") : "");
        }
        bd.put("conData", Map.of("conNum", cons));
        bd.put("dataRow", cars);
        Programs.Outcome b = programs.attempt("VHINXB", Map.of("VHINXB_CRIT", bc, "VHINXB_DATA", bd));
        if (!b.ok()) throw programs.error("VHINXB", b.result().control());
        return true;
    }

    /** VHCI01IS-CICS-MSG: the CI message (VHCI01E) */
    void ciMessage(String car, String group, String oldStatus, String newStatus, boolean reset) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conCar1", car);
        crit.put("reptId", ciReport);
        crit.put("userGroup", group);
        crit.put("termId", terminal);
        crit.put("reptType", oldStatus);
        crit.put("reptMisc", newStatus);
        crit.put("sendResetMsg", reset ? yes : no);
        programs.attempt("VHCI01E", Map.of("VHCI01E_CRIT", crit));
    }

    /** VHCI01IS-SHOP-STAT: the car goes into the shop (VHINXD) */
    void intoShop(String car) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", car);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("termLastUpd", terminal);
        programs.run("VHINXD", Map.of("VHINXD_CRIT", crit, "VHINXD_DATA", d));
    }

    // ---------------------------------------------------------------- helpers

    /** XXDATES-CONVERT "YMD": MM-DD-YYYY to YYYY-MM-DD when the year is numeric, otherwise unchanged */
    static String ymd(String mdy) {
        String p = pad(mdy, 10);
        String y = p.substring(6, 10);
        if (y.chars().allMatch(Character::isDigit)) return y + "-" + p.substring(0, 2) + "-" + p.substring(3, 5);
        return mdy == null ? "" : mdy.stripTrailing();
    }

    /** a field as its text, a group field as its parts put together */
    @SuppressWarnings("unchecked")
    static String flat(Object v) {
        if (v instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder();
            for (Object o : ((Map<String, Object>) m).values()) b.append(o instanceof Map<?, ?> ? flat(o) : o == null ? "" : o.toString());
            return b.toString().strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    static String pad(String v, int n) {
        String s = v == null ? "" : v;
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    static int num(String s) {
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return 0; }
    }

    static String s(String v) {
        return v == null ? "" : v.strip();
    }
}
