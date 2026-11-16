package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import java.util.List;

/**
 * One yard tracking map (old screens VHYT40I / VHYT41I Dublin-Pleasanton, VHYT50I / VHYT51I Pittsburg-Bay Point, VHYT04I
 * Hayward shop): the map's tracks and spots (old LOAD order) and the cars on them, in the same shape as the yard reports
 * maps, plus what the old map passes on when it opens another screen.
 *
 * @param screen      the old screen the map is
 * @param yard        the yard code the old INIT asks for (DPX, PBX; null for the Hayward shop, whose criteria carry none)
 * @param pseudoTrack the track the old map's PSEUDO spot opens (T-95), null when the map has none
 * @param shopReport  the Hayward shop's Print: the report and yard VHYT10IS-SHOP passes to VHYT27I (null on the yard maps)
 */
public record YardTrackingMap(String map, String screen, String yard, String pseudoTrack, ShopReport shopReport,
                              List<YardReportsMap.Track> tracks, List<YardReportsMap.Car> cars) {
    public record ShopReport(String whichReport, String yard) {}
}
