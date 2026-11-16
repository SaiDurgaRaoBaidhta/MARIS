package com.bart.maris.middleware.domain.yardtracking;

import java.util.List;
import java.util.Map;

/**
 * Moving cars on a track (old VHYT11I "Car Move"), changing a car's orientation (VHYT13I) and the yard car list hard copy
 * (VHYT24I).
 */
public final class YardTrackingMove {
    private YardTrackingMove() {}

    /**
     * one of the 30 positions of a page: the position number, the car as shown (with its orientation marks), the pseudo train
     * it is in, its status, previous status and home yard, the car's and the status' colours as the old screen paints them
     * (YELLOW = selected), and whether its button can be pressed
     */
    public record Position(int slot, int position, String carShown, String car, String pseudoTrain, String status, String previousStatus,
                           String homeYard, String color, String statusColor, boolean enabled) {}

    /** a track the cars can be moved to (the "Move to" list, program VHYT11A) */
    public record Location(String code, String description) {}

    /**
     * the car move window: the yard and track (with descriptions), the track chosen in the "Move to" list, the pseudo train
     * column's caption, the page and whether there is a page before / after, the positions, the "Move to" tracks, which
     * buttons are available, and the message
     */
    public record Track(String yard, String yardDescription, String subLocation, String subLocationDescription, String toSubLocation,
                        String pseudoCaption, int page, boolean previous, boolean next, List<Position> positions, List<Location> moveTo,
                        boolean canPseudoTrain, boolean canMoveYard, String message) {}

    /**
     * position buttons clicked in order ({@code slots}, 1..30 on {@code page}) then a button: {@code action} = move, pseudo-train,
     * reverse, fix-order, orientation, move-yard or print; {@code toSubLocation} = the "Move to" track; {@code confirm} = the
     * answer to the old "un-make the pseudo train(s)?" question
     */
    public record Action(String yard, String subLocation, String toSubLocation, Integer page, List<Integer> slots, String action, Boolean confirm) {}

    /**
     * what the button did: the message, the question the old screen asks first (answer with {@code confirm}), the window it
     * opens with the values the old screen passes it, and the window after
     */
    public record Result(String message, String question, String opens, Map<String, Object> with, Track track) {}

    /** VHYT13I: the cars on the track (as shown, with marks), the car chosen, its two orientation marks and its current one */
    public record Orientation(String yard, String subLocation, List<String> cars, String car, List<String> orientations, String current,
                              boolean canChange, String message) {}

    /** VHYT24I: the yard and the printers to choose from */
    public record PrintOptions(String yard, List<Printer> printers) {}

    public record Printer(String location, String name) {}
}
