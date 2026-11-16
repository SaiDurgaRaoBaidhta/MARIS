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
 * A car's move reason on a track (old VHYT06I, opened by Change Reason on the track windows): the reasons (VHYT06A) and the
 * car (VHYT06B) when it opens; Update checks the car still has its reason (VHYT06C), writes the new one (VHYT06D), adds
 * the history row (VHYT06E) and reads the car again (VHYT06B). Messages are the screen's own (VHYT06IS-*).
 */
public final class YardTrackingTrackReasonService {
    private static final String PKG = "MRSYardTracking";

    private final Programs programs;
    private final String terminal;
    private final String noReasons, selectNew, notChanged, dbChanged;
    private final List<String> changed;

    public YardTrackingTrackReasonService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal == null ? "" : terminal;
        noReasons = YardTrackingTrackEgl.first(screens, "VHYT06IS-INIT", PKG, "FINAL-MESSAGE");
        selectNew = YardTrackingTrackEgl.first(screens, "VHYT06IS-VALID", PKG, "FINAL-MESSAGE");
        notChanged = YardTrackingTrackEgl.first(screens, "VHYT06IS-CHANGE", PKG, "FINAL-MESSAGE");
        dbChanged = YardTrackingTrackEgl.assigned(screens, "VHYT06IS-CHANGE", PKG, "FINAL-MESSAGE").get(1);
        changed = List.of(YardTrackingTrackEgl.first(screens, "VHYT06IS-CHANGE", PKG, "FINAL-MSG-1"),
                YardTrackingTrackEgl.first(screens, "VHYT06IS-CHANGE", PKG, "FINAL-MSG-3"), YardTrackingTrackEgl.first(screens, "VHYT06IS-CHANGE", PKG, "FINAL-MSG-5"));
    }

    public record Reason(String code, String description) {}

    public record Car(String car, int position, String orientation, String status, String yard, String subLocation, String reasonCode, String reasonDescription) {}

    public record View(List<Reason> reasons, Car car, String message) {}

    public record Change(String yard, String subLocation, String car, Integer position, String reasonCode) {}

    public record Changed(String message, boolean updated, Car car) {}

    /** VHYT06IS-INIT then VHYT06IS-GET-DATA: the reasons and the car, as the window opens */
    public View view(String yard, String subLocation, String car, Integer position) {
        List<Reason> reasons = reasons();
        if (reasons.isEmpty()) return new View(reasons, null, noReasons);
        return new View(reasons, car(yard, subLocation, car, position == null ? 0 : position), "");
    }

    private List<Reason> reasons() {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("actvInd", "");
        crit.put("usedByPgmInd", "");
        ProgramResult r = programs.run("VHYT06A", Map.of("VHYT06A_CRIT", crit));
        List<Reason> out = new ArrayList<>();
        if (r.control() != null && r.control().returnCode() == 100) return out;
        Object list = r.record("VHYT06A_DATA") == null ? null : r.record("VHYT06A_DATA").get("dataRow");
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<?> rows = list instanceof List<?> l ? l.subList(0, Math.min(n, l.size())) : List.of();
        for (Object row : rows) {                                               // a row is MOVE-REASON: code + description
            @SuppressWarnings("unchecked")
            Map<String, Object> m = row instanceof Map<?, ?> x && x.get("moveReason") instanceof Map<?, ?> y ? (Map<String, Object>) y : Map.of();
            out.add(new Reason(text(m, "moveReasonCd"), text(m, "moveReasonDesc")));
        }
        return out;
    }

    private Car car(String yard, String subLocation, String car, int position) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", up(car));
        crit.put("moveReasonCd", "");
        crit.put("yrdTrkgYrd", up(yard));
        crit.put("yrdSubLoc", up(subLocation));
        crit.put("posnNo", position);
        Map<String, Object> d = programs.run("VHYT06B", Map.of("VHYT06B_CRIT", crit)).record("VHYT06B_DATA");
        return new Car(text(d, "carNo"), (int) number(d, "posnNo"), text(d, "vagenOrientation"), text(d, "vehStat"), text(d, "yrdTrkgYrd"),
                text(d, "yrdSubLoc"), text(d, "moveReasonCd"), text(d, "moveReasonDesc"));
    }

    /** VHYT06IS-VALID and VHYT06IS-CHANGE: the new reason, written with its history row when the car still has its reason */
    public Changed change(Change c, String group) {
        Car now = car(c.yard(), c.subLocation(), c.car(), c.position() == null ? 0 : c.position());
        String code = c.reasonCode() == null ? "" : c.reasonCode().strip().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) throw new InvalidRequestException(selectNew);
        if (code.equals(now.reasonCode())) return new Changed(notChanged, false, now);
        Map<String, Object> chk = new LinkedHashMap<>();                          // VHYT06IS-CHK-DB: from the window's pass values
        chk.put("carNo", up(c.car()));
        chk.put("moveReasonCd", now.reasonCode());
        chk.put("yrdSubLoc", up(c.subLocation()));
        chk.put("yrdTrkgYrd", up(c.yard()));
        ProgramResult r = programs.run("VHYT06C", Map.of("VHYT06C_CRIT", chk));
        if (r.control() != null && r.control().returnCode() == 100) return new Changed(dbChanged, false, now);
        Map<String, Object> crit = new LinkedHashMap<>();                         // VHYT06IS-UPDATE: from the car as read
        crit.put("yrdSubLoc", now.subLocation());
        crit.put("yrdTrkgYrd", now.yard());
        crit.put("carNo", now.car());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("termLastUpd", terminal);
        data.put("moveReasonCd", code);
        data.put("userGrpUpReason", group == null ? "" : group);
        data.put("vehStat", now.status());
        data.put("vagenOrientation", now.orientation());
        data.put("termUpdReason", terminal);
        programs.run("VHYT06D", Map.of("VHYT06D_CRIT", crit, "VHYT06D_DATA", data));
        Map<String, Object> hc = new LinkedHashMap<>();                           // VHYT06IS-ADD-HIS
        hc.put("yrdSubLoc", now.subLocation());
        hc.put("yrdTrkgYrd", now.yard());
        hc.put("posnNo", now.position());
        hc.put("carNo", now.car());
        Map<String, Object> hd = new LinkedHashMap<>();
        hd.put("userGroup", group == null ? "" : group);
        hd.put("moveReasonCdPrv", now.reasonCode());
        hd.put("termId", terminal);
        programs.run("VHYT06E", Map.of("VHYT06E_CRIT", hc, "VHYT06E_DATA", hd));
        String message = pad(changed.get(0), 12) + pad(now.reasonCode(), 4) + pad(changed.get(1), 9) + pad(now.car(), 4) + pad(changed.get(2), 21) + code;
        return new Changed(message.stripTrailing(), true, car(now.yard(), now.subLocation(), now.car(), now.position()));
    }

    private static String pad(String s, int width) {
        String t = s == null ? "" : s;
        return t.length() >= width ? t.substring(0, width) : t + " ".repeat(width - t.length());
    }

    private static String up(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }
}
