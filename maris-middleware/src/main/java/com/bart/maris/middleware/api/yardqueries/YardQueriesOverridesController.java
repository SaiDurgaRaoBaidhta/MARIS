package com.bart.maris.middleware.api.yardqueries;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingOverridesService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Make / break overrides (old VHYT34I). Every call opens the old window, whose opening calls VHYT34B (which writes),
 * so they are all POSTs.
 */
@RestController
@RequestMapping("/api/v1/yard-queries/make-break-overrides")
@Tag(name = "Yard queries: make / break overrides")
public class YardQueriesOverridesController {
    private final YardTrackingOverridesService service;

    public YardQueriesOverridesController(YardTrackingOverridesService service) {
        this.service = service;
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    public record Window(String yard, String date) {}

    @PostMapping("/window")
    @Operation(summary = "The window as it opens: revenue date, schedule, make / break times, trains, single-train overrides")
    public YardTrackingOverridesService.State window(@RequestBody Window r, @AuthenticationPrincipal MarisUser user) {
        return service.window(r.yard(), r.date(), group(user));
    }

    public record Overrides(String yard, String date, YardTrackingOverridesService.Overrides overrides) {}

    @PostMapping
    @Operation(summary = "Make/Break Overrides: cancel or re-activate makes and breaks (VHYT34F)")
    public YardTrackingOverridesService.State overrides(@RequestBody Overrides r, @AuthenticationPrincipal MarisUser user) {
        return service.overrides(r.yard(), r.date(), r.overrides(), group(user));
    }

    public record Train(String yard, String date, String train, String arrival) {}

    @PostMapping("/train")
    @Operation(summary = "A train's arrival times (VHYT34J) and what an arrival is scheduled for (VHYT34K, VHYT34L, VHYT34M)")
    public YardTrackingOverridesService.TrainTimes train(@RequestBody Train r, @AuthenticationPrincipal MarisUser user) {
        return service.train(r.yard(), r.date(), r.train(), r.arrival(), group(user));
    }

    public record Single(String yard, String date, YardTrackingOverridesService.SingleOverride override) {}

    @PostMapping("/single-train")
    @Operation(summary = "Single Train Override for a train's arrival (VHYT34G)")
    public YardTrackingOverridesService.State single(@RequestBody Single r, @AuthenticationPrincipal MarisUser user) {
        return service.singleOverride(r.yard(), r.date(), r.override(), group(user));
    }
}
