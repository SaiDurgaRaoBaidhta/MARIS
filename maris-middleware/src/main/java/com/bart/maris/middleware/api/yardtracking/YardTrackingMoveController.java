package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingMove;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMoveService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingOrientationService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingYardPrintService;
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

/** Car moves on a track (old VHYT11I), a car's orientation (VHYT13I) and the yard car list hard copy (VHYT24I). */
@RestController
@RequestMapping("/api/v1/yard-tracking")
@Tag(name = "Yard tracking car moves")
public class YardTrackingMoveController {
    private final YardTrackingMoveService moves;
    private final YardTrackingOrientationService orientation;
    private final YardTrackingYardPrintService print;

    public YardTrackingMoveController(YardTrackingMoveService moves, YardTrackingOrientationService orientation, YardTrackingYardPrintService print) {
        this.moves = moves;
        this.orientation = orientation;
        this.print = print;
    }

    @GetMapping("/car-moves")
    @Operation(summary = "A track's cars to move (VHYT11I: VHYT08B, VHYT08C, VHYT08A, VHYT11A, VH01A02), page 1.. of 30 positions")
    public YardTrackingMove.Track track(@RequestParam(required = false) String yard, @RequestParam(required = false) String subLocation,
                                        @RequestParam(required = false) String toSubLocation, @RequestParam(required = false) Integer page,
                                        @AuthenticationPrincipal MarisUser user) {
        return moves.track(yard, subLocation, toSubLocation, page, user == null ? "" : user.groupId());
    }

    @PostMapping("/car-moves/actions")
    @Operation(summary = "Positions clicked then a button: move (VHYT11C, VHYT11B writes), pseudo-train, reverse (VHYT11F writes), fix-order, "
            + "orientation, move-yard, print")
    public YardTrackingMove.Result act(@RequestBody YardTrackingMove.Action a, @AuthenticationPrincipal MarisUser user) {
        return moves.act(a, user == null ? "" : user.groupId());
    }

    @GetMapping("/orientation")
    @Operation(summary = "A track's cars and a car's orientation marks (VHYT13I: VHYT13A, VHYT13B)")
    public YardTrackingMove.Orientation orientation(@RequestParam(required = false) String yard, @RequestParam(required = false) String subLocation,
                                                    @RequestParam(required = false) String carNo) {
        return orientation.orientation(yard, subLocation, carNo);
    }

    public record OrientationChange(String yard, String subLocation, String carNo, String orientation) {}

    @PostMapping("/orientation")
    @Operation(summary = "Change a car's orientation (VHYT13E checks, VHYT13C and VHYT13D write)")
    public YardTrackingMove.Orientation changeOrientation(@RequestBody OrientationChange c) {
        return orientation.change(c.yard(), c.subLocation(), c.carNo(), c.orientation());
    }

    @GetMapping("/yard-print")
    @Operation(summary = "The yard car list hard copy's printers (VHYT24I: WS70A01)")
    public YardTrackingMove.PrintOptions printOptions(@RequestParam(required = false) String yard) {
        return print.options(yard);
    }

    public record YardPrint(String yard, String printer) {}

    @PostMapping("/yard-print")
    @Operation(summary = "Print a yard's car list (VHYT24A, VHYT24B..E, VHYT02C/E/F/G by yard)")
    public Map<String, String> print(@RequestBody YardPrint p) {
        print.print(p.yard(), p.printer());
        return Map.of("yard", p.yard() == null ? "" : p.yard().strip().toUpperCase(java.util.Locale.ROOT));
    }
}
