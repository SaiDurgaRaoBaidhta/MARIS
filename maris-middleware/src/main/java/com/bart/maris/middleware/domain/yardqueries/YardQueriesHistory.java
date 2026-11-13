package com.bart.maris.middleware.domain.yardqueries;

import java.util.List;

/** The yard history reports opened from VHYT27I: car location history (VHYT29I) and vehicle status history (VHYT33I). */
public final class YardQueriesHistory {
    private YardQueriesHistory() {}

    /** @param maxRowsReached the program returns at most 325 rows; the old report flags 324 and more */
    public record CarHistory(String yard, String from, String to, String fromCar, String toCar, boolean maxRowsReached, List<Move> rows) {}

    /** @param previousTrack the track the car came from, or the previous reason code when it has none */
    public record Move(String car, String track, int position, String status, String orientation, String dateMoved, String timeMoved,
                       String previousTrack, int previousPosition, String reason, String reasonCode) {}

    public record StatusHistory(String yard, String from, String to, boolean maxRowsReached, List<StatusChange> rows) {}

    public record StatusChange(String car, String dateChanged, String timeChanged, String track, String newStatus, String previousStatus,
                               String reason, String reasonCode) {}
}
