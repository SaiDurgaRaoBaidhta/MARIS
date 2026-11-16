package com.bart.maris.middleware.domain.yardtracking;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Hayward yard map (MARIS_RUI/EGLSource/MRSYardTracking, old screen Vhyt07i): VHYT07IS-INIT reads the yard's cars
 * (VHYT07A), VHYT07IS-LOAD and its per-track groups put each car on its spot, labelled like the other maps (a left
 * orientation first, "$" first when the car has software), coloured by home yard (VHYT07IS-COLOR). The track buttons
 * open the track detail (VHYT08I) with the track their statement group sets, Pseudo Status Train opens VHYT09I and Print
 * opens VHYT24I, each with the yard the screen sets. Read-only; tracks, colours, texts and links are read from the EGL.
 */
public class YardTrackingHaywardMapService {
    static final String PKG = "MRSYardTracking";
    static final String SCREEN = "Vhyt07i";
    /** the old screen's left orientations: strLib.indexOf("&lt;O({vblm", orientation) &gt; 0 */
    private static final String LEFT = "<O({vblm";

    /** a track button: its caption and the track (YRD-SUB-LOC) its statement group passes to VHYT08I */
    public record Spot(String caption, String track) {}

    /** the map: yard, tracks and spots (LOAD order), cars, the track buttons, and the yard Pseudo Status Train / Print pass */
    public record YardMap(String screen, String yard, List<YardReportsMap.Track> tracks, List<YardReportsMap.Car> cars, List<Spot> spots,
                       String pseudoTrainYard, String printYard) {}

    private final Programs programs;
    private final String yard, pseudoYard, printYard;
    private final Map<String, Set<Integer>> layout;
    private final Map<String, List<String>> colours;
    private final List<Spot> spots = new ArrayList<>();

    public YardTrackingHaywardMapService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        yard = screens.assignedText("VHYT07IS-INIT", PKG, "YRD-TRKG-YRD");
        pseudoYard = screens.assignedText("VHYT08IS-PSEUDO", PKG, "YRD-TRKG-YRD");
        printYard = screens.assignedText("VHYT07-PRINT", PKG, "YRD-TRKG-YRD");
        layout = YardTrackingMapsLayout.of(screens, "VHYT07IS-LOAD", PKG);
        colours = YardReportsLayout.colours(screens, "VHYT07IS-COLOR", PKG, "HOME-YRD", "CAR-COLOR", "CAR-COLOR-B");
        // the buttons whose click runs a group that sets YRD-SUB-LOC (and so opens the track detail), in screen order
        JsonNode def = screens.screen(SCREEN);
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (!c.path("m").asText().equals("connectEventToAction") || !a.path(1).asText().equals("clicked")) continue;
            String widget = a.path(0).path("id").asText().replaceFirst("^screen\\.", ""), to = a.path(2).path("id").asText();
            if (!to.endsWith("__reference")) continue;
            String fn = to.substring(0, to.length() - "__reference".length());
            String track;
            try { track = screens.assignedText(fn, PKG, "YRD-SUB-LOC"); } catch (IllegalStateException e) { continue; }
            JsonNode p = def.path("widgets").path(widget).path("props");
            String caption = p.path("object").asText(p.path("text").asText(widget)).strip();
            spots.add(new Spot(caption, track));
        }
        if (spots.isEmpty()) throw new IllegalStateException(SCREEN + " has no track buttons");
    }

    /** VHYT07IS-INIT and -LOAD: the cars on their spots (a later row on a spot replaces an earlier one) */
    public YardMap map() {
        ProgramResult r = programs.run("VHYT07A", Map.of("VHYT07A_CRIT", Map.of()));
        Map<String, YardReportsMap.Car> placed = new LinkedHashMap<>();
        if (r.control() == null || r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHYT07A_DATA", "dataRow")) {
                String track = text(row, "yrdSubLoc");
                int position = (int) number(row, "posnNo");
                Set<Integer> known = layout.get(track);
                if (known == null || (!known.isEmpty() && !known.contains(position))) continue;
                placed.put(known.isEmpty() ? track : track + "/" + position, car(row, track, position));
            }
        }
        List<YardReportsMap.Track> tracks = new ArrayList<>();
        layout.forEach((t, set) -> tracks.add(new YardReportsMap.Track(t, List.copyOf(set))));
        return new YardMap(SCREEN, yard, tracks, new ArrayList<>(placed.values()), List.copyOf(spots), pseudoYard, printYard);
    }

    private YardReportsMap.Car car(Map<String, Object> row, String track, int position) {
        String car = text(row, "carNo"), orientation = text(row, "vagenOrientation");
        boolean left = !orientation.isEmpty() && LEFT.contains(orientation);
        String label = left ? orientation + car : car + orientation;
        // "$" first when SOFTVER is not blank; the text is CHAR(6), so the end is cut
        if (!text(row, "softver").isEmpty()) label = "$" + label;
        if (label.length() > 6) label = label.substring(0, 6);
        List<String> colour = colours.getOrDefault(text(row, "homeYrd"), colours.get(YardReportsLayout.OTHER_YARD));
        return new YardReportsMap.Car(track, position, car, text(row, "conNo"), orientation, label.strip(), text(row, "vehStat"), null,
                text(row, "moveReasonCd"), text(row, "homeYrd"), colour.get(0), colour.size() < 2 ? null : colour.get(1));
    }
}
