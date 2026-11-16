package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopActivityService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrainsService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yard tracking: shop activity (old VHYT31I) and its prints (VHYT23I, VHYT22I), pseudo correction of in-service trains
 * (VHYT52I) and force clear of a train (VHYT54I).
 */
@RestController
@RequestMapping("/api/v1/yard-tracking")
@Tag(name = "Yard tracking: shop activity and trains")
public class YardTrackingMiscController {
    private final YardTrackingShopActivityService activity;
    private final YardTrackingTrainsService trains;

    public YardTrackingMiscController(YardTrackingShopActivityService activity, YardTrackingTrainsService trains) {
        this.activity = activity;
        this.trains = trains;
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    @GetMapping("/shop-activity/options")
    @Operation(summary = "Shop activity: the yards (VHYT31A), the yard chosen and its name (VHYT31H), today's date (old VHYT31I opening)")
    public YardTrackingShopActivityService.Options activityOptions(@RequestParam(required = false) String yard) {
        return activity.options(yard);
    }

    public record ActivityRequest(String yard, String date) {}

    @PostMapping("/shop-activity/list")
    @Operation(summary = "Shop activity of a yard for a date: the cars and counts (VHYT31E, VHYT31B; these programs write)")
    public YardTrackingShopActivityService.Activity activityList(@RequestBody ActivityRequest r) {
        return activity.list(r.yard(), r.date());
    }

    @GetMapping("/printers")
    @Operation(summary = "The printers the yard tracking print windows list (WS70A01)")
    public List<YardTrackingShopActivityService.Printer> printers() {
        return activity.printers();
    }

    public record ActivityPrint(String yard, String date, int printer) {}

    @PostMapping("/shop-activity/print")
    @Operation(summary = "Print the shop activity report (old VHYT23I, VHYT23A) on a printer (its WS70A01 row)")
    public Map<String, String> printActivity(@RequestBody ActivityPrint r) {
        return Map.of("printer", activity.printActivity(r.yard(), r.date(), r.printer()));
    }

    public record CarPrint(String car, int printer) {}

    @PostMapping("/car-history/print")
    @Operation(summary = "Print a car's history report (old VHYT22I, VHYT22A) on a printer (its WS70A01 row)")
    public Map<String, String> printCar(@RequestBody CarPrint r) {
        return Map.of("printer", activity.printCarHistory(r.car(), r.printer()));
    }

    @GetMapping("/pseudo-correction")
    @Operation(summary = "In-service trains of a dispatch location (old VHYT52I, VHYT52A)")
    public YardTrackingTrainsService.Trains pseudoTrains(@RequestParam(required = false) String yard, @RequestParam(required = false) String whichGui,
                                                         @RequestParam(required = false) String location, @AuthenticationPrincipal MarisUser user) {
        return trains.trains(yard, whichGui, location, group(user));
    }

    public record Retrieve(String yard, String whichGui, String location, int row) {}

    @PostMapping("/pseudo-correction/retrieve")
    @Operation(summary = "Request a listed train's cars back into the pseudo track (old VHYT52I Retrieve Consist, VHYT52B)")
    public YardTrackingTrainsService.Result retrieve(@RequestBody Retrieve r, @AuthenticationPrincipal MarisUser user) {
        return trains.retrieve(r.yard(), r.whichGui(), r.location(), r.row(), group(user));
    }

    public record ForceClear(String train, String location) {}

    @PostMapping("/force-clear")
    @Operation(summary = "Force clear an in-service train to a location (old VHYT54I: VHYT54A, VHYT54B, VHYT54C)")
    public Map<String, String> forceClear(@RequestBody ForceClear r, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", trains.forceClear(r.train(), r.location(), group(user)));
    }
}
