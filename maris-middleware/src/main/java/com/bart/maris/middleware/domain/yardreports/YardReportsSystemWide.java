package com.bart.maris.middleware.domain.yardreports;

import java.util.List;

/** The BART system-wide map (old screen VHSW01I): the places it marks and the cars at each (none = not marked). */
public record YardReportsSystemWide(String yard, List<Location> locations) {
    public record Location(String location, List<String> cars) {}
}
