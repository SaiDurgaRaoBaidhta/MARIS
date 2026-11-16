package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A scripted backend for the car move window (old VHYT11I), the orientation window (VHYT13I) and the yard print (VHYT24I). */
final class YardTrackingMoveBackend {
    private YardTrackingMoveBackend() {}

    static final String YARD = "CY", TRACK = "T-12", TO_TRACK = "T-13";

    /** cars on the track: car, consist, status, previous status, home yard; positions 1.. */
    static final String[][] CARS = {
            {"1101", "201", "NO GO", "GO", "CY"}, {"1102", "201", "GO", "USABLE", "CY"}, {"1103", "", "USABLE", "NO GO", "RY"},
            {"1104", "", "NO GO", "NO GO", "HY"}, {"1105", "305", "GO", "GO", "CY"}};

    /** pseudo trains VHYT11C finds in the cars chosen: "NOT" = all of its cars move, anything else = it is split */
    static Parity backend(String... split) {
        Parity p = new Parity();
        answers(split).forEach(p::on);
        return p;
    }

    static Map<String, Function<Map<String, Map<String, Object>>, Map<String, Object>>> answers(String... split) {
        Map<String, Function<Map<String, Map<String, Object>>, Map<String, Object>>> p = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < CARS.length; i++) {
            String[] c = CARS[i];
            rows.add(Map.ofEntries(Map.entry("carNo", c[0]), Map.entry("conNo", c[1]), Map.entry("vagenOrientation", i % 2 == 0 ? "<" : ">"),
                    Map.entry("yrdSubLoc", TRACK), Map.entry("posnNo", i + 1), Map.entry("vehStat", c[2]), Map.entry("vehStatPrev", c[3]),
                    Map.entry("moveReasonCd", "M1"), Map.entry("homeYrd", c[4]), Map.entry("trainIncdInd", "N"), Map.entry("vehConNo", c[1]),
                    Map.entry("vehDashNo", 1), Map.entry("partConNo", "")));
        }
        p.put("VHYT08B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08B_DATA", Map.of("yrdDesc", "CONCORD YARD",
                "yrdSubLocDesc", "TRACK TWELVE", "cntCarsMax", 30)));
        p.put("VHYT08F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08F_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", TO_TRACK, "yrdSubLocDesc", "TRACK THIRTEEN")))));
        p.put("VHYT08H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08H_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", TRACK, "yrdSubLocDesc", "TRACK TWELVE")))));
        p.put("VHYT08C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT08C_DATA", Map.of("dataRow", List.of(
                Map.of("lftOrientationCd", "<", "rgtOrientationCd", ">", "carType", "A2"),
                Map.of("lftOrientationCd", "[", "rgtOrientationCd", "]", "carType", "B2")))));
        p.put("VHYT08A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHYT08A_DATA", Map.of("dataRow", rows)));
        p.put("VHYT11A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT11A_CRIT", Map.of("yrdTrkgYrd", YARD, "yrdSubLoc", TRACK,
                "trackCounter", 2, "transferTrkInd", "N"), "VHYT11A_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", TO_TRACK, "yrdSubLocDesc", "TRACK THIRTEEN"), Map.of("yrdSubLoc", "T-14", "yrdSubLocDesc", "TRACK FOURTEEN")))));
        p.put("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VH01A02_DATA", Map.of("userGroupAccess", List.of(
                Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT11", "partName", "pbtMoveYard", "accessAllow", "Y"),
                Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT11", "partName", "pbtMoveCar", "accessAllow", "Y")))));
        List<Map<String, Object>> con = new ArrayList<>();
        for (int i = 0; i < split.length; i++) con.add(Map.of("conNo", "20" + (i + 1), "splitNot", split[i]));
        p.put("VHYT11C", in -> Map.of("XDCONTROL", Map.of("urc", split.length == 0 ? 100 : 0, "urowcnt", split.length),
                "VHYT11C_DATA", Map.of("dataRow", con)));
        p.put("VHYT11B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT11B_CRIT", Map.of("reValidSw", "N", "positionFull", "N", "maxRows", 30)));
        p.put("VHYT11F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT11F_CRIT", Map.of("posnFrom", 2, "posnTo", 4)));
        // orientation window
        List<Map<String, Object>> oc = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            oc.add(Map.of("carAndOr", Map.of("lftOr", i % 2 == 0 ? "<" : " ", "carNo", CARS[i][0], "rgtOr", i % 2 == 0 ? " " : ">"),
                    "posnNo", i + 1, "lftOrientationCd", "<", "rgtOrientationCd", ">"));
        }
        p.put("VHYT13A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", oc.size()), "VHYT13A_DATA", Map.of("dataRow", oc, "carFound", "Y")));
        p.put("VHYT13B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT13B_DATA", Map.of("lftOrientationCd", "<", "rgtOrientationCd", ">")));
        p.put("VHYT13E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT13E_CRIT", Map.of("dbNotChg", "Y", "carInCon", "N", "endCarInd", "N")));
        p.put("VHYT13C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.put("VHYT13D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        // yard print
        p.put("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrLocDesc", "DUBLIN", "vtamName", "DUBLNHP1"), Map.of("prtrLocDesc", "LAKESIDE", "vtamName", "LKS11HP1")))));
        for (String r : List.of("VHYT24A", "VHYT24B", "VHYT24C", "VHYT24D", "VHYT24E", "VHYT02C", "VHYT02E", "VHYT02F", "VHYT02G")) {
            p.put(r, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        }
        return p;
    }
}
