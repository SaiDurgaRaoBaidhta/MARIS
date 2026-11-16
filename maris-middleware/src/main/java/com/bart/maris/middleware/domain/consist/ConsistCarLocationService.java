package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.Step;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Move a car that is not in a consist to another location (old VHCY01I): the car is looked up when it is typed
 * (VHCY01IS-GET-CAR: VHCY1A4), and Update checks the new location against the locations list (VHCY1A1), the pseudo
 * track room (VHCY1A2) and the car (VHVM1A2) before VHCY1A3 moves it. The rules are the screen's own, run by
 * {@link ConsistRuleRunner}; the messages are the ones the screen shows in its message line.
 */
public final class ConsistCarLocationService {
    static final String SCREEN = "VHCY01I";
    private static final List<String> SHOWN = List.of("txtMsg", "CurLoc", "TrackNo", "PositionNo", "CarText", "NewLoc");

    private final ConsistRuleScreens screens;

    public ConsistCarLocationService(ConsistRuleScreens screens) {
        this.screens = screens;
    }

    /** a car and where it is now; {@code movable} when the screen lets the user enter a new location */
    public record CarLocation(String car, String currentLocation, String track, String position, boolean movable, String message) {}

    /** the result of a move: the screen's message, and its message boxes */
    public record Move(String message, List<String> notices, boolean moved) {}

    private static String car(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty()) throw new InvalidRequestException("A car number is required.");
        return c;
    }

    private List<Step> lookup(String car) {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.type("CarText", car));
        steps.add(Step.leave("CarText"));
        return steps;
    }

    /** VHCY01IS-GET-CAR: where the car is and whether it may be moved */
    public CarLocation car(String group, String car) {
        ConsistRuleRunner logic = screens.open(SCREEN, group);
        logic.type("CarText", car(car));
        logic.fire("CarText", "losingFocus");
        View v = screens.view(logic, SHOWN);
        return new CarLocation(v.field("CarText"), v.field("CurLoc"), v.field("TrackNo"), v.field("PositionNo"), logic.enabled("Update_btn"),
                v.field("txtMsg"));
    }

    /** the car typed, the new location typed (VHCY01IS-SW), then Update (VHCY01IS-SW-UPDATE and VHCY01IS-UPDATE-X) */
    public Move move(String group, String car, String location) {
        List<Step> steps = lookup(car(car));
        steps.add(Step.type("NewLoc", location == null ? "" : location.strip()));
        steps.add(Step.leave("NewLoc"));
        steps.add(Step.press("Update_btn"));
        View v = screens.run(SCREEN, group, steps, null, SHOWN);
        return new Move(v.field("txtMsg"), v.notices(), v.programs().contains("VHCY1A3"));
    }
}
