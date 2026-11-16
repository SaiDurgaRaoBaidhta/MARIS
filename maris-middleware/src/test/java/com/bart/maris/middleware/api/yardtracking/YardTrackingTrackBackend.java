package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A scripted backend for the track window (old VHYT08I) and the windows it opens (VHYT02I, VHYT06I, VHYT14I). */
final class YardTrackingTrackBackend {
    private YardTrackingTrackBackend() {}

    static final String YARD = "CY", TRACK = "T-12";

    /** cars on the track: car, consist, status, previous status, home yard, train incident flag; positions 1.. */
    static final String[][] CARS = {
            {"1101", "201", "NO GO", "GO", "CY", "N"}, {"1102", "201", "GO", "USABLE", "CY", "Y"}, {"1103", "", "USABLE", "NO GO", "RY", "N"},
            {"1104", "", "NO GO", "NO GO", "HY", "N"}, {"1105", "305", "GO", "GO", "CY", "N"}};

    static Parity backend() {
        return backend(CARS.length);
    }

    static Parity backend(int count) {
        Parity p = new Parity();
        answers(count).forEach(p::on);
        return p;
    }

    static Map<String, Object> reason(String code, String description) {
        return Map.of("moveReason", Map.of("moveReasonCd", code, "moveReasonDesc", description));
    }

    /** what each program answers (records by parameter name, XDCONTROL included) */
    static Map<String, java.util.function.Function<Map<String, Map<String, Object>>, Map<String, Object>>> answers(int count) {
        Map<String, java.util.function.Function<Map<String, Map<String, Object>>, Map<String, Object>>> p = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] c = CARS[i % CARS.length];
            String car = String.valueOf(1101 + i);
            rows.add(Map.ofEntries(Map.entry("carNo", car), Map.entry("conNo", c[1]), Map.entry("vagenOrientation", i % 2 == 0 ? "<" : ">"),
                    Map.entry("yrdSubLoc", TRACK), Map.entry("posnNo", i + 1), Map.entry("vehStat", c[2]), Map.entry("vehStatPrev", c[3]),
                    Map.entry("moveReasonCd", "M1"), Map.entry("homeYrd", c[4]), Map.entry("trainIncdInd", c[5]), Map.entry("vehConNo", c[1]),
                    Map.entry("vehDashNo", 1), Map.entry("partConNo", "")));
        }
        List<Map<String, Object>> sv = new ArrayList<>();
        for (int i = 0; i < count; i++) sv.add(Map.of("svCarNo", String.valueOf(1101 + i), "svPosnNo", i + 1));
        p.put("VHYT08D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08D_DATA", Map.of("yrdTrkgYrd", "ALL", "pseudoYrdInd", "N")));
        p.put("VHYT08B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08B_DATA", Map.of("yrdDesc", "CONCORD YARD",
                "yrdSubLocDesc", "TRACK TWELVE", "cntCarsMax", 30)));
        p.put("VHYT08F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT08F_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", "T-13", "yrdSubLocDesc", "TRACK THIRTEEN"), Map.of("yrdSubLoc", "SHOP1", "yrdSubLocDesc", "SHOP ONE")))));
        p.put("VHYT08H", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT08H_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", TRACK, "yrdSubLocDesc", "TRACK TWELVE")))));
        p.put("VHYT08C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHYT08C_DATA", Map.of("dataRow", List.of(
                Map.of("lftOrientationCd", "<", "rgtOrientationCd", ">", "carType", "A2"),
                Map.of("lftOrientationCd", "[", "rgtOrientationCd", "]", "carType", "B2"),
                Map.of("lftOrientationCd", "(", "rgtOrientationCd", ")", "carType", "C1")))));
        p.put("VHYT08A", in -> Map.of("XDCONTROL", Map.of("urc", count == 0 ? 100 : 0, "urowcnt", count),
                "VHYT08A_DATA", Map.of("dataRow", rows, "svDataRow", sv)));
        p.put("VH01A02", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VH01A02_DATA", Map.of("userGroupAccess", List.of(
                Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT08", "partName", "pbtMoveCar", "accessAllow", "Y"),
                Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT08", "partName", "pbtMoveYard", "accessAllow", "Y"),
                Map.of("userGrpX", "BRIGHTCO", "guiName", "VHYT08", "partName", "pbtFixOrder", "accessAllow", "Y")))));
        p.put("VHYT08G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT08G_DATA", Map.of("dbChange", "", "trkIsFull", "", "goCarFound", "")));
        // move reason window
        p.put("VHYT06A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHYT06A_DATA", Map.of("dataRow", List.of(
                reason("M1", "MOVED FOR PM"), reason("M2", "WAITING FOR PARTS"), reason("M3", "CLEANING")))));
        p.put("VHYT06B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT06B_DATA", Map.of("carNo", "1103", "moveReasonCd", "M1",
                "vagenOrientation", "<", "vehStat", "USABLE", "posnNo", 3, "moveReasonDesc", "MOVED FOR PM", "yrdTrkgYrd", "SW", "yrdSubLoc", TRACK)));
        p.put("VHYT06C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT06C_DATA", Map.of("exist", "Y")));
        p.put("VHYT06D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.put("VHYT06E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        // fix order window
        List<Map<String, Object>> fix = new ArrayList<>(), fixB = new ArrayList<>();
        for (int i = 0; i < Math.min(count, CARS.length); i++) {
            fix.add(Map.of("carNo", String.valueOf(1101 + i), "posnNo", i + 1, "conNo", CARS[i][1], "partConNo", "", "partConInd", "N"));
            fixB.add(Map.of("carAndOr", Map.of("carNo", String.valueOf(1101 + i)), "posnNo", i + 1));
        }
        p.put("VHYT14A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", fix.size()), "VHYT14A_DATA", Map.of("dataRow", fix, "carFound", "Y")));
        p.put("VHYT14B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", fixB.size()), "VHYT14B_DATA", Map.of("dataRow", fixB, "carFound", "Y")));
        for (String w : List.of("VHYT14C", "VHYT14D", "VHYT14E", "VHYT14F")) p.put(w, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        // print popup
        p.put("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrLocDesc", "DUBLIN", "vtamName", "DUBLNHP1"), Map.of("prtrLocDesc", "LAKESIDE", "vtamName", "LKS11HP1")))));
        p.put("VHYT02B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT02B_CRIT", Map.of("yrd", YARD, "yrdCount", 2),
                "VHYT02B_DATA", Map.of("dataRow", List.of(Map.of("yrd", "SW"), Map.of("yrd", "CY"))),
                "VHYT02B_DATA_1", Map.of("dataRow", List.of(Map.of("yrdSubLoc", "T-12"), Map.of("yrdSubLoc", "T-13")))));
        p.put("VHYT02A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        return p;
    }
}
