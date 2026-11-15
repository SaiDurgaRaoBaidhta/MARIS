package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.number;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The narrative window (old VHNAR01I) that incident and repair screens open: it shows one narrative of an incident
 * (or of an incident's repair document), steps to the previous / next one (VHXNR1A, repair VHXNR5A), adds a narrative
 * (VHXNR2A then VHXNR6A, repair VHXNR3A then VHXNR4A) and rewrites the one shown (VHXNR7A, repair VHXNR8A).
 *
 * <p>The window is stateless like the old one: the parent passes the narrative it shows and the row counts, and every
 * call carries them back. A narrative starting with the "CI / ACTION" marker shows that part apart and keeps it on update.
 */
public final class VehicleMaintNarrativeService {
    private static final String PKG = "MRSVehicleMaintenance";
    private static final int CI_LENGTH = 36, MARKER_LENGTH = 11, REMAINDER_LENGTH = 964, NARR_LENGTH = 1000;

    private final Programs programs;
    private final String terminal;
    private final String invalid, incdAdded, reprAdded, incdUpdated, reprUpdated, ciMarker, up, down, retrieveFunction;

    public VehicleMaintNarrativeService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        invalid = t.assigned("VHNARIS-ADDNARR", PKG, "FINAL-ERROR-MESSAGE", "Invalid");
        incdAdded = t.assigned("VHNARIS-RETR-INCD", PKG, "FINAL-ERROR-MESSAGE", null);
        reprAdded = t.assigned("VHNARS-RETR-REPAIR", PKG, "FINAL-ERROR-MESSAGE", null);
        incdUpdated = t.assigned("VHNARIS-UPDNARR", PKG, "FINAL-ERROR-MESSAGE", "Incd Narrative updated");
        reprUpdated = t.assigned("VHNARIS-UPDNARR", PKG, "FINAL-ERROR-MESSAGE", "Repr Narrative updated");
        ciMarker = t.compared("VHNARIS-CHK-NARR", PKG, "NARR-1").get(0);
        List<String> functions = t.compared("VHNARIS-GET-NARR", PKG, "VAGen_FUNCTION");
        up = functions.get(0);
        down = functions.get(1);
        retrieveFunction = t.assigned("VHNARIS-RETR-INCD", PKG, "VAGen_FUNCTION", null);
    }

    /** one narrative as the window shows it: the CI / ACTION part apart, the time as HH:MM */
    public record Narrative(String incdNo, int reprDocNo, int currentRow, int maxRows, String userGrp, String dateNarr, String timeNarr,
                            String narr, String ciPart, boolean canPrev, boolean canNext) {}

    /** VHNARIS-INIT: the narrative the parent passed, as the window opens with it (no backend call) */
    public Narrative opening(String incdNo, Integer reprDocNo, Integer currentRow, Integer maxRows, String userGrp, String dateNarr, String timeNarr,
                             String narr) {
        return view(incdNo, n(reprDocNo), n(currentRow), n(maxRows), userGrp, dateNarr, timeNarr, narr);
    }

    /** VHNARIS-GET-NARR: the previous / next narrative (VHXNR1A, repair VHXNR5A) */
    public Narrative step(String incdNo, Integer reprDocNo, Integer currentRow, Integer maxRows, String direction) {
        String incd = incd(incdNo);
        int repr = n(reprDocNo), cur = n(currentRow), max = n(maxRows);
        boolean prev = "prev".equalsIgnoreCase(direction);
        if (!prev && !"next".equalsIgnoreCase(direction)) throw new InvalidRequestException("direction is prev or next");
        int row = prev ? Math.max(1, cur - 1) : Math.min(max, cur + 1);
        Map<String, Object> d;
        int shown;
        if (repr != 0) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("narrNo", row);
            crit.put("currentRow", row);
            crit.put("maxRows", max);
            crit.put("incdNo", incd);
            crit.put("reprDocNo", repr);
            ProgramResult r = programs.run("VHXNR5A", Map.of("VHXNR5A_CRIT", crit));
            d = r.record("VHXNR5A_DATA");
        } else if (!incd.isEmpty()) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("incdNo", incd);
            crit.put("narrNo", row);
            crit.put("currentRow", row);
            crit.put("maxRows", max);
            crit.put("vagenFunction", prev ? up : down);
            ProgramResult r = programs.run("VHXNR1A", Map.of("VHXNR1A_CRIT", crit));
            d = r.record("VHXNR1A_DATA");
        } else {
            throw new InvalidRequestException(invalid);
        }
        shown = (int) number(d, "narrNo");
        return view(incd, repr, shown, max, raw(d, "userGrp"), raw(d, "dateNarr"), raw(d, "timeNarr"), raw(d, "narr"));
    }

    public record Added(String message, Narrative narrative) {}

    /** VHNARIS-ADDNARR: a new narrative after the last one (VHXNR2A, repair VHXNR3A), then the new last one read back */
    public Added add(String incdNo, Integer reprDocNo, Integer maxRows, String narrative, String userGroup) {
        String incd = incd(incdNo);
        int repr = n(reprDocNo), max = n(maxRows);
        String text = narrative == null ? "" : narrative;
        if (text.isBlank()) throw new InvalidRequestException(invalid);
        if (repr != 0) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("narrNo", max + 1);
            data.put("narr", text);
            data.put("userGrp", userGroup);
            data.put("incdNo", incd);
            data.put("reprDocNo", repr);
            data.put("termNarr", terminal);
            ProgramResult w = programs.run("VHXNR3A", Map.of("VHXNR3A_DATA", data));
            int newMax = (int) number(w.record("VHXNR3A_DATA"), "narrNo");
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("incdNo", incd);
            crit.put("reprDocNo", repr);
            crit.put("narrNo", 0);
            Map<String, Object> d = programs.run("VHXNR4A", Map.of("VHXNR4A_CRIT", crit)).record("VHXNR4A_DATA");
            int row = (int) number(d, "narrNo");
            return new Added(reprAdded, view(incd, repr, row, newMax, raw(d, "userGrp"), raw(d, "dateNarr"), raw(d, "timeNarr"), raw(d, "narr"), true, false));
        }
        if (incd.isEmpty()) throw new InvalidRequestException(invalid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incdNo", incd);
        data.put("narrNo", max + 1);
        data.put("narr", text);
        data.put("userGrp", userGroup);
        data.put("termNarr", terminal);
        ProgramResult w = programs.run("VHXNR2A", Map.of("VHXNR2A_DATA", data));
        int newMax = (int) number(w.record("VHXNR2A_DATA"), "narrNo");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("narrNo", 0);
        crit.put("vagenFunction", retrieveFunction);
        Map<String, Object> d = programs.run("VHXNR6A", Map.of("VHXNR6A_CRIT", crit)).record("VHXNR6A_DATA");
        int row = (int) number(d, "maxRows");
        return new Added(incdAdded, view(incd, 0, row, newMax, raw(d, "userGrp"), raw(d, "dateNarr"), raw(d, "timeNarr"), raw(d, "narr"), true, false));
    }

    /** VHNARIS-UPDNARR: rewrites the narrative shown (VHXNR7A, repair VHXNR8A), keeping its CI / ACTION part */
    public String update(String incdNo, Integer reprDocNo, Integer currentRow, String narrative, String ciPart, String userGroup) {
        String incd = incd(incdNo);
        int repr = n(reprDocNo), row = n(currentRow);
        String text = narrative == null ? "" : narrative;
        if (text.isBlank()) throw new InvalidRequestException(invalid);
        String ci = ciPart == null ? "" : ciPart;
        String full = ci.startsWith(ciMarker) ? pad(ci, CI_LENGTH) + cut(text, REMAINDER_LENGTH) : text;
        if (repr != 0) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("incdNo", incd);
            crit.put("narrNo", row);
            crit.put("reprDocNo", repr);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("narr", full);
            data.put("userGrp", userGroup);
            data.put("termNarr", terminal);
            programs.run("VHXNR8A", Map.of("VHXNR8A_CRIT", crit, "VHXNR8A_DATA", data));
            return reprUpdated;
        }
        if (incd.isEmpty()) throw new InvalidRequestException(invalid);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incd);
        crit.put("narrNo", row);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("narr", full);
        data.put("termNarr", terminal);
        data.put("userGrp", userGroup);
        programs.run("VHXNR7A", Map.of("VHXNR7A_CRIT", crit, "VHXNR7A_DATA", data));
        return incdUpdated;
    }

    /** VHNARIS-INIT / GET-NARR: the Prv / Nxt buttons follow the row against the count */
    private Narrative view(String incd, int repr, int row, int max, String userGrp, String date, String time, String narr) {
        return view(incd, repr, row, max, userGrp, date, time, narr, row > 1, row < max);
    }

    /** VHNARIS-CHK-NARR splits the CI / ACTION part off */
    private Narrative view(String incd, int repr, int row, int max, String userGrp, String date, String time, String narr, boolean canPrev, boolean canNext) {
        String n = narr == null ? "" : narr;
        String ci = "";
        if (pad(n, MARKER_LENGTH).substring(0, MARKER_LENGTH).equals(pad(ciMarker, MARKER_LENGTH))) {
            String full = pad(n, NARR_LENGTH);
            ci = full.substring(0, CI_LENGTH).stripTrailing();
            n = full.substring(CI_LENGTH);
        }
        return new Narrative(incd == null ? "" : incd.strip(), repr, row, max, s(userGrp), s(date), hhmm(time), n.stripTrailing(), ci, canPrev, canNext);
    }

    /** TIME-NARR-HRMIN: the hours and minutes with ":" between, as the window forces its fillers */
    private static String hhmm(String time) {
        String t = time == null ? "" : time;
        if (t.isBlank()) return "";
        String p = pad(t, 8);
        return p.substring(0, 2) + ":" + p.substring(3, 5);
    }

    private static String incd(String incdNo) {
        String i = incdNo == null ? "" : incdNo.strip();
        if (i.length() > 8) throw new InvalidRequestException("An incident number is up to 8 characters.");
        return i;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static String raw(Map<String, Object> rec, String field) {
        Object v = rec == null ? null : rec.get(field);
        return v == null ? "" : v.toString();
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }

    private static String pad(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
