package com.bart.maris.middleware.domain.yardreports;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
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
import java.util.Map;
import java.util.Set;

/**
 * Yard maps. The rules are the old screens' (MARIS_RUI/EGLSource/MRSYardTrackingReport/RUIFunctions.egl):
 * &lt;screen&gt;IS-INIT (the yard asked for), -LOAD and the per-track functions (which spots show which car, the label),
 * GiveAColor (the condition). Read-only.
 */
public class YardReportsService {
    private static final String PKG = "MRSYardTrackingReport";
    /** the old screens' left orientations (strLib.indexOf("&lt;O({vblm", orientation) &gt; 0) */
    private static final String LEFT = "<O({vblm";

    /**
     * one map: the program it calls and its records, the old INIT that sets the yard, the old LOAD that places the cars,
     * the old colour statement group, and whether its update screen colours cars by condition (GiveAColor)
     */
    private record Spec(String program, String crit, String data, String init, String load, String colours, boolean condition,
                        Dollar dollar) {}

    /** when the old map puts "$" before a car's label (its software version, SOFTVER) */
    private enum Dollar {
        /** never (VHIC01 / VHIC02; Millbrae shows no label rule) */
        NEVER,
        /** getButton: when SOFTVER has a "$" (Daly City and Richmond yards) */
        WHEN_MARKED,
        /** VHYT70IS-ST.. / VHYT71IS-ST..: when SOFTVER is not blank (Concord yard) */
        WHEN_SET
    }

    /** a map ready to use: everything read from the old screens when the service starts */
    private record Def(Spec spec, String yard, Map<String, Set<Integer>> tracks, Map<String, List<String>> colours) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("berryessa", new Spec("VHIC01A", "VHIC01A_CRIT", "VHIC01A_DATA", "VHIC01IS-INIT", "VHIC01IS-LOAD", "VHIC01IS-COLOR", true, Dollar.NEVER));
        SPECS.put("millbrae", new Spec("VHYT80A", "VHYT80A_CRIT", "VHYT80A_DATA", "VHYT80IS-INIT", "VHYT80IS-LOAD", "VHYT80IS-COLOR", false, Dollar.NEVER));
        SPECS.put("daly-city-yard", new Spec("VHYTX5A", "VHYT105A_CRIT", "VHYT105A_DATA", "VHYT105IS-INIT", "VHYT105IS-LOAD", "VHYT105IS-COLOR", true, Dollar.WHEN_MARKED));
        SPECS.put("concord-yard", new Spec("VHYT70A", "VHYT70A_CRIT", "VHYT70A_DATA", "VHYT70IS-INIT", "VHYT70IS-LOAD", "VHYT70IS-COLOR", true, Dollar.WHEN_SET));
        SPECS.put("richmond-yard", new Spec("VHYT90A", "VHYT90A_CRIT", "VHYT90A_DATA", "VHYT90IS-INIT", "VHYT90IS-LOAD", "VHYT90IS-COLOR", true, Dollar.WHEN_MARKED));
    }

    private final Programs programs;
    private final Map<String, Def> maps = new LinkedHashMap<>();
    /* VHSW01IS-INIT / -LOAD: the system-wide map's yard and the places it marks */
    private final String systemWideYard;
    private final List<String> systemWidePlaces;

    public YardReportsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        SPECS.forEach((name, s) -> maps.put(name, new Def(s, screens.assignedText(s.init(), PKG, "YRD-TRKG-YRD"),
                YardReportsLayout.of(screens, s.load(), PKG), YardReportsLayout.colours(screens, s.colours(), PKG))));
        this.systemWideYard = screens.assignedText("VHSW01IS-INIT", PKG, "YRD-TRKG-YRD");
        this.systemWidePlaces = List.copyOf(YardReportsLayout.of(screens, "VHSW01IS-LOAD", PKG).keySet());
    }

    /** VHSW01IS-INIT + -LOAD: the old map marks a place when the program returns a car there */
    public YardReportsSystemWide systemWide() {
        ProgramResult r = programs.run("VHSW01A", Map.of("VHSW01A_CRIT", Map.of("yrdTrkgYrd", systemWideYard)));
        Map<String, List<String>> cars = new LinkedHashMap<>();
        systemWidePlaces.forEach(p -> cars.put(p, new ArrayList<>()));
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHSW01A_DATA", "dataRow")) {
                List<String> at = cars.get(text(row, "yrdSubLoc"));
                if (at != null) at.add(text(row, "carNo"));
            }
        }
        List<YardReportsSystemWide.Location> out = new ArrayList<>();
        cars.forEach((place, list) -> out.add(new YardReportsSystemWide.Location(place, list)));
        return new YardReportsSystemWide(systemWideYard, out);
    }

    public YardReportsMap map(String name) {
        Def d = maps.get(name);
        if (d == null) throw new InvalidRequestException("Unknown yard map: " + name + ". Known maps: " + String.join(", ", maps.keySet()));
        ProgramResult r = programs.run(d.spec().program(), Map.of(d.spec().crit(), Map.of("yrdTrkgYrd", d.yard())));
        // a later row on the same spot replaces the earlier one, as on the old screen
        Map<String, YardReportsMap.Car> spots = new LinkedHashMap<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, d.spec().data(), "dataRow")) {
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                if (!d.tracks().getOrDefault(track, Set.of()).contains(position)) continue;
                spots.put(track + "/" + position, car(row, track, position, d));
            }
        }
        List<YardReportsMap.Track> tracks = new ArrayList<>();
        d.tracks().forEach((t, s) -> tracks.add(new YardReportsMap.Track(t, List.copyOf(s))));
        return new YardReportsMap(name, d.yard(), tracks, new ArrayList<>(spots.values()));
    }

    private static YardReportsMap.Car car(Map<String, Object> row, String track, int position, Def d) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        List<String> colour = d.colours().getOrDefault(text(row, "homeYrd"), d.colours().get(YardReportsLayout.OTHER_YARD));
        boolean left = !orientation.isEmpty() && LEFT.contains(orientation);
        String label = left ? orientation + car : car + orientation, software = text(row, "softver");
        boolean dollar = switch (d.spec().dollar()) {
            case NEVER -> false;
            case WHEN_MARKED -> software.contains("$");
            case WHEN_SET -> !software.isEmpty();
        };
        return new YardReportsMap.Car(track, position, car, text(row, "conNo"), orientation, dollar ? "$" + label : label,
                text(row, "vehStat"),
                d.spec().condition() ? condition(text(row, "vehStat"), text(row, "trainIncdInd"), number(row, "hpmhrs")) : null,
                text(row, "moveReasonCd"), text(row, "homeYrd"), colour.get(0), colour.get(1));
    }

    /** GiveAColor: GO without a train incident (PM due = brown, else black), USABLE without one (blue), anything else red */
    static String condition(String status, String incident, long pmDue) {
        if (incident.equals("Y")) return "not-usable";
        if (status.equals("GO")) return pmDue == 1 ? "go-pm-due" : "go";
        if (status.equals("USABLE")) return "usable";
        return "not-usable";
    }
}
