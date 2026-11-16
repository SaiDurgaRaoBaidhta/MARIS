package com.bart.maris.middleware.domain.yardtracking;

import java.util.List;

/** The pseudo status train window (old VHYT09I) and its printer window (VHYT01I). */
public final class YardTrackingPseudoTrain {
    private YardTrackingPseudoTrain() {}

    /**
     * VHYT09IS-INIT: the yards to choose from (VHYT09A, then the screen's "ALL"), the yard shown first, the opening message
     * and how many consists a page shows (VHYT09IS-PAGE-CNT's divisor)
     */
    public record Options(List<String> yards, String yard, String message, int pageSize) {}

    /** one car of a consist: its position, car (with the orientation marks), home yard and the old colour (RED unless GO/USABLE) */
    public record Car(int position, String car, String homeYard, String colour) {}

    /** one consist (VHYT09B row after VHYT09IS-MOVE-DATA) */
    public record Consist(String consist, String track, String yard, String dateLastUpdated, String timeLastUpdated, List<Car> cars) {}

    /** VHYT09IS-GET-DATA: the consists of the yard (all pages) and the message the old screen shows */
    public record Report(String yard, List<Consist> consists, String message, boolean printable) {}

    public record Printer(String description, String vtam) {}
}
