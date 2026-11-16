package com.bart.maris.middleware.domain.yardreports;

import java.util.List;

/** One yard map (old screens VHIC01 / VHIC02, ...): the map's tracks and spots (old LOAD order) and the cars on them. */
public record YardReportsMap(String map, String yard, List<Track> tracks, List<Car> cars) {
    public record Track(String track, List<Integer> spots) {}

    /**
     * @param label     what the old map's spot shows: a left orientation before the car number, any other after it
     * @param condition  null when the map's update screen colours no car; else the old update maps' colour rule (GiveAColor): go, go-pm-due, usable or not-usable
     * @param colour     the old inquiry map's text colour for the car's home yard (each map has its own table)
     * @param background the old inquiry map's background for the car's home yard
     */
    public record Car(String track, int position, String car, String consist, String orientation, String label,
                      String status, String condition, String moveReason, String homeYard, String colour, String background) {}
}
