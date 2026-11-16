package com.bart.maris.middleware.domain.yardtracking;

import java.util.List;
import java.util.Map;

/** One track's cars (old VHYT08I "Yard Detail Inquiry"): 30 car positions a page, and what can be done with them. */
public final class YardTrackingTrack {
    private YardTrackingTrack() {}

    /**
     * one of the 30 positions of a page: the position number, the car as shown (with its orientation marks), the car number,
     * the pseudo train it is in, its status (with "TI" when a train incident is open), previous status and home yard;
     * {@code usable} = the old screen paints the car sky blue (previous status USABLE), {@code alert} = its status is shown red
     */
    public record Position(int slot, int position, String carShown, String car, String pseudoTrain, String status, String previousStatus,
                           String homeYard, boolean usable, boolean alert) {}

    /** a location a car can be moved to (the "Move Car to Location" list, program VHYT08F) */
    public record Location(String code, String description) {}

    /**
     * the track window: its yard and track (with descriptions), the pseudo train column's caption, the page and whether there
     * is a page before / after, the positions, the move-to locations, and which buttons the user group may use
     */
    public record Track(String yard, String yardDescription, String subLocation, String subLocationDescription, String pseudoCaption,
                        int page, boolean previous, boolean next, List<Position> positions, List<Location> locations,
                        boolean userMayChange, boolean canMoveCar, boolean canMoveYard, boolean canFixOrder, String message) {}

    /**
     * a button pressed on the track after clicking positions: {@code slots} = the position buttons clicked in order (1..30 on
     * {@code page}); {@code action} = change-reason, fix-order, move-car or move-yard; {@code toLocation} = the "Move Car to
     * Location" choice
     */
    public record Action(String yard, String subLocation, Integer page, List<Integer> slots, String action, String toLocation) {}

    /** what the button did: the message, the window it opens with the values the old screen passes it, and the track after */
    public record Result(String message, String opens, Map<String, Object> with, Track track) {}
}
