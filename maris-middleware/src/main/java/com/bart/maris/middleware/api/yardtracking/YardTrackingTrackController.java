package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrack;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackFixOrderService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackReasonService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackReportService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingTrackService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** One track's cars and the windows it opens (old VHYT08I track, VHYT02I print, VHYT06I move reason, VHYT14I fix order). */
@RestController
@RequestMapping("/api/v1/yard-tracking")
@Tag(name = "Yard tracking: track")
public class YardTrackingTrackController {
    private final YardTrackingTrackService track;
    private final YardTrackingTrackReportService report;
    private final YardTrackingTrackReasonService reasons;
    private final YardTrackingTrackFixOrderService fixOrder;

    public YardTrackingTrackController(YardTrackingTrackService track, YardTrackingTrackReportService report, YardTrackingTrackReasonService reasons,
                                       YardTrackingTrackFixOrderService fixOrder) {
        this.track = track;
        this.report = report;
        this.reasons = reasons;
        this.fixOrder = fixOrder;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    @GetMapping("/track")
    @Operation(summary = "A track's cars, 30 positions a page (old VHYT08I; VHYT08D, VHYT08B, VHYT08F, VHYT08H, VHYT08C, VHYT08A, VH01A02)")
    public YardTrackingTrack.Track track(@RequestParam(required = false) String yard, @RequestParam(required = false) String subLocation,
                                         @RequestParam(required = false) Integer page, @AuthenticationPrincipal MarisUser user) {
        return track.track(yard, subLocation, page, group(user));
    }

    @PostMapping("/track/actions")
    @Operation(summary = "Positions clicked, then Change Reason / Fix Order / Move Car (VHYT08G, writes) / Move Yrd (old VHYT08I)")
    public YardTrackingTrack.Result act(@RequestBody YardTrackingTrack.Action a, @AuthenticationPrincipal MarisUser user) {
        return track.act(a, group(user));
    }

    @GetMapping("/track-report/options")
    @Operation(summary = "The report printer window's printers, yards and tracks (old VHYT02I; WS70A01, VHYT02B)")
    public YardTrackingTrackReportService.Options reportOptions(@RequestParam(required = false) String yard) {
        return report.options(yard);
    }

    @PostMapping("/track-report/print")
    @Operation(summary = "Print the yard tracking report (old VHYT02I Hard Copy; VHYT02A)")
    public Map<String, String> print(@RequestBody YardTrackingTrackReportService.Print p) {
        return Map.of("message", report.print(p));
    }

    @GetMapping("/move-reasons/car")
    @Operation(summary = "The move reasons and a car's current one (old VHYT06I; VHYT06A, VHYT06B)")
    public YardTrackingTrackReasonService.View reason(@RequestParam(required = false) String yard, @RequestParam(required = false) String subLocation,
                                                      @RequestParam(required = false) String car, @RequestParam(required = false) Integer position) {
        return reasons.view(yard, subLocation, car, position);
    }

    @PostMapping("/move-reasons/change")
    @Operation(summary = "Change a car's move reason (old VHYT06I Update; VHYT06C, VHYT06D and VHYT06E write)")
    public YardTrackingTrackReasonService.Changed changeReason(@RequestBody YardTrackingTrackReasonService.Change c, @AuthenticationPrincipal MarisUser user) {
        return reasons.change(c, group(user));
    }

    @GetMapping("/fix-order")
    @Operation(summary = "A track's cars for fixing their order (old VHYT14I; VHYT14A)")
    public YardTrackingTrackFixOrderService.View fixOrder(@RequestParam(required = false) String yard, @RequestParam(required = false) String subLocation,
                                                          @RequestParam(required = false) Integer position) {
        return fixOrder.view(yard, subLocation, position);
    }

    @PostMapping("/fix-order")
    @Operation(summary = "Insert a car before / after another (old VHYT14I Fix Order; VHYT14B, then VHYT14F, VHYT14D, VHYT14C, VHYT14E write)")
    public YardTrackingTrackFixOrderService.Fixed fix(@RequestBody YardTrackingTrackFixOrderService.Fix f) {
        return fixOrder.fix(f);
    }
}
