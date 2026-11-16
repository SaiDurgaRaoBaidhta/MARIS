package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The yard tracking report printer window (old VHYT02I, Print on the track windows): the printers (WS70A01), the yards and
 * a yard's tracks (VHYT02B, plus "ALL"), and Hard Copy (VHYT02A, a print program: never run by live tests).
 */
public final class YardTrackingTrackReportService {
    private static final String PKG = "MRSYardTracking";

    private final Programs programs;
    private final String noYard, all;

    public YardTrackingTrackReportService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        noYard = YardTrackingTrackEgl.first(screens, "VHYT02IS-SUBMIT", PKG, "MESSAGE");
        all = YardTrackingTrackEgl.first(screens, "VHYT02IS-INIT", PKG, "YRD-SUB-LOC");          // the "all tracks" choice
    }

    public record Printer(String description, String vtam) {}

    public record Options(List<Printer> printers, List<String> yards, String yard, List<String> subLocations) {}

    public record Print(String yard, String subLocation, String printer) {}

    /** VHYT02IS-INIT-X (WS70A01) and VHYT02IS-INIT (VHYT02B for the yard the window is opened on) */
    public Options options(String yard) {
        ProgramResult w = programs.run("WS70A01", Map.of());
        List<Printer> printers = new ArrayList<>();
        for (Map<String, Object> r : Programs.rows(w, "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        String y = up(yard);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yrd", y);
        ProgramResult b = programs.run("VHYT02B", Map.of("VHYT02B_CRIT", crit));
        List<String> yards = new ArrayList<>(), tracks = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> yr = b.record("VHYT02B_DATA") != null && b.record("VHYT02B_DATA").get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        long count = number(b.record("VHYT02B_CRIT"), "yrdCount");
        for (int i = 0; i < Math.min(count, yr.size()); i++) yards.add(text(yr.get(i), "yrd"));
        for (Map<String, Object> r : Programs.rows(b, "VHYT02B_DATA_1", "dataRow")) tracks.add(text(r, "yrdSubLoc"));
        tracks.add(all);
        return new Options(printers, yards, y, tracks);
    }

    /** VHYT02IS-SUBMIT: a yard is required, a blank track means all of them */
    public String print(Print p) {
        String y = up(p.yard()), t = up(p.subLocation());
        if (y.isEmpty()) throw new InvalidRequestException(noYard);
        if (t.isEmpty()) t = all;
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yrdTrkgYrd", y);
        crit.put("yrdSubLoc", t);
        crit.put("reptType", p.printer() == null ? "" : p.printer().strip());
        programs.run("VHYT02A", Map.of("VHYT02A_CRIT", crit));
        return "";
    }

    private static String up(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }
}
