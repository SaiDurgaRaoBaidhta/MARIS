package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shop maps that work like the Hayward shop (VHYT04I): old VHYT60I (Concord shop, program VHYT60A, VHYT60IS-LOAD2) and
 * VHYT96I (Richmond shop, VHYT95A, VHYT96IS-LOAD). The rules are the old screens': the criteria go out as they are (no
 * yard), the LOAD's tracks and spots, the label ("$" first for a software version longer than one character, the
 * orientation first when it is the row's own left end), the text colour dy100Color(HOME-YRD), and the Print's report
 * and yard (VHYT60IS-SHOP / VHYT95IS-SHOP, passed to VHYT27I). A car opens VHSV06I. Read-only.
 */
public class YardTrackingShopMapsService {
    static final String PKG = "MRSYardTracking";

    private record Spec(String screen, String program, String crit, String data, String load, String print) {}

    private record Def(Spec spec, Map<String, Set<Integer>> tracks, Map<String, List<String>> colours, YardTrackingMap.ShopReport report) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("concord-shop", new Spec("VHYT60I", "VHYT60A", "VHYT60A-CRIT", "VHYT60A-DATA", "VHYT60IS-LOAD2", "VHYT60IS-SHOP"));
        SPECS.put("richmond-shop", new Spec("VHYT96I", "VHYT95A", "VHYT95A-CRIT", "VHYT95A-DATA", "VHYT96IS-LOAD", "VHYT95IS-SHOP"));
    }

    private final Programs programs;
    private final Map<String, Def> maps = new LinkedHashMap<>();

    public YardTrackingShopMapsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        // the shops' text colour: dy100Color(HOME_YARD) returns theColor
        Map<String, List<String>> colours = YardReportsLayout.colours(screens, "dy100Color", PKG, "HOME_YARD", "theColor", null);
        SPECS.forEach((name, s) -> maps.put(name, new Def(s, YardTrackingMapsLayout.of(screens, s.load(), PKG), colours,
                new YardTrackingMap.ShopReport(screens.assignedText(s.print(), PKG, "WHICH-REPORT"), screens.assignedText(s.print(), PKG, "YRD-TRKG-YRD")))));
    }

    public List<String> names() {
        return List.copyOf(maps.keySet());
    }

    /** INIT-S and LOAD: the cars on the spots the LOAD knows (a later row on a spot replaces an earlier one) */
    public YardTrackingMap map(String name) {
        Def d = maps.get(name);
        if (d == null) throw new InvalidRequestException("Unknown shop map: " + name + ". Known maps: " + String.join(", ", maps.keySet()));
        Spec s = d.spec();
        ProgramResult r = programs.run(s.program(), Map.of(s.crit(), Map.of()));
        Map<String, YardReportsMap.Car> spots = new LinkedHashMap<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, s.data(), "dataRow")) {
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                Set<Integer> known = d.tracks().get(track);
                if (known == null || (!known.isEmpty() && !known.contains(position))) continue;
                spots.put(known.isEmpty() ? track : track + "/" + position, car(row, track, position, d.colours()));
            }
        }
        List<YardReportsMap.Track> tracks = new ArrayList<>();
        d.tracks().forEach((t, set) -> tracks.add(new YardReportsMap.Track(t, List.copyOf(set))));
        return new YardTrackingMap(name, s.screen(), null, null, d.report(), tracks, new ArrayList<>(spots.values()));
    }

    private static YardReportsMap.Car car(Map<String, Object> row, String track, int position, Map<String, List<String>> colours) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        boolean left = !orientation.isEmpty() && orientation.equals(text(row, "lftOrientationCd"));
        String label = left ? orientation + car : car + orientation;
        // strLib.characterLen(SOFTVER) > 1 (trailing blanks left out)
        if (String.valueOf(row.getOrDefault("softver", "")).stripTrailing().length() > 1) label = "$" + label;
        List<String> colour = colours.getOrDefault(text(row, "homeYrd"), colours.get(YardReportsLayout.OTHER_YARD));
        return new YardReportsMap.Car(track, position, car, text(row, "conNo"), orientation, label, text(row, "vehStat"), null,
                text(row, "moveReasonCd"), text(row, "homeYrd"), colour == null ? null : colour.get(0), null);
    }
}
