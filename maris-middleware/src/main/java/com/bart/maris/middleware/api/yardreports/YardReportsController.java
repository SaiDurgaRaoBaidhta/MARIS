package com.bart.maris.middleware.api.yardreports;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardreports.YardReportsMove;
import com.bart.maris.middleware.domain.yardreports.YardReportsMoveService;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudoService;
import com.bart.maris.middleware.domain.yardreports.YardReportsService;
import com.bart.maris.middleware.domain.yardreports.YardReportsSystemWide;
import io.swagger.v3.oas.annotations.Operation;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yard maps, configuring pseudo trains and moving them (old screens VHYT53I, VHIC03I, VHIC01 / VHIC02 Berryessa, VHYT80I / 81I Millbrae, VHYT105I / 106I, 70I / 71I, 90I / 91I, VHSW01I). */
@RestController
@RequestMapping("/api/v1/yard-reports")
@Tag(name = "Yard reports")
public class YardReportsController {
    private final YardReportsService service;
    private final YardReportsMoveService moves;
    private final YardReportsPseudoService pseudo;

    public YardReportsController(YardReportsService service, YardReportsMoveService moves, YardReportsPseudoService pseudo) {
        this.service = service;
        this.moves = moves;
        this.pseudo = pseudo;
    }

    // ---- VHYT53I configure pseudo trains --------------------------------------------------------------------------

    @GetMapping("/pseudo-config/{yard}")
    @Operation(summary = "A yard's tracks, consists and their cars, to build a pseudo consist from (old VHYT53I; VHYT53D, VHYT53B, VHYT53C)")
    public YardReportsPseudo.Config pseudoConfig(@PathVariable String yard) {
        return pseudo.config(yard);
    }

    @GetMapping("/pseudo-config/{yard}/cars")
    @Operation(summary = "The cars on a track (or ALL) to add (VHYT53E) or insert (list=insert, VHYT53M) into a new pseudo consist")
    public List<YardReportsPseudo.Car> pseudoTrackCars(@PathVariable String yard, @RequestParam String track,
                                                       @RequestParam(required = false) String list) {
        return pseudo.trackCars(yard, track, "insert".equalsIgnoreCase(list));
    }

    public record CreateRequest(String consist, boolean partial, List<YardReportsPseudo.NewCar> cars) {}

    @PostMapping("/pseudo-config/{yard}/consists")
    @Operation(summary = "Create a pseudo consist (VHYT53F) or a partial one (VHYT53G) after the old checks (VHYT53L); writes")
    public YardReportsPseudo.Result createPseudo(@PathVariable String yard, @RequestBody CreateRequest r, @AuthenticationPrincipal MarisUser user) {
        return pseudo.create(yard, r.consist(), r.partial(), r.cars(), user.groupId());
    }

    public record CarMoveRequest(String car, String track) {}

    @PostMapping("/pseudo-config/{yard}/car-moves")
    @Operation(summary = "Move a car of the new consist out to a track (VHYT53H); writes")
    public YardReportsMove movePseudoCar(@PathVariable String yard, @RequestBody CarMoveRequest r, @AuthenticationPrincipal MarisUser user) {
        return pseudo.moveCar(yard, r.car(), r.track(), user.groupId());
    }

    public record ResetRequest(String consist, String track, String indicator) {}

    @PostMapping("/pseudo-config/{yard}/resets")
    @Operation(summary = "Reset a consist marked C or D (VHYT53K); writes")
    public YardReportsPseudo.Result resetPseudo(@PathVariable String yard, @RequestBody ResetRequest r, @AuthenticationPrincipal MarisUser user) {
        return pseudo.reset(yard, r.consist(), r.track(), r.indicator(), user.groupId());
    }

    @GetMapping("/pseudo-train-move/yards")
    @Operation(summary = "The yards a pseudo train's cars can move to (old screen VHIC03I, program VHYT09A)")
    public List<String> moveYards() {
        return moves.yards();
    }

    /** the cars at fromPosition..toPosition of the pseudo train in fromYard */
    public record MoveRequest(String fromYard, int fromPosition, int toPosition, String toYard) {}

    @PostMapping("/pseudo-train-move")
    @Operation(summary = "Move a pseudo train's cars to another yard (old screen VHIC03I, program VHIC03A, writes)")
    public YardReportsMove move(@RequestBody MoveRequest r, @AuthenticationPrincipal MarisUser user) {
        return moves.move(r.fromYard(), r.fromPosition(), r.toPosition(), r.toYard(), user.groupId());
    }

    @GetMapping("/system-wide")
    @Operation(summary = "The BART system-wide map: the places it marks and the cars at each (program VHSW01A)")
    public YardReportsSystemWide systemWide() {
        return service.systemWide();
    }

    @GetMapping("/maps/{map}")
    @Operation(summary = "The cars on a yard map's tracks (berryessa VHIC01A, millbrae VHYT80A, daly-city-yard VHYTX5A, concord-yard VHYT70A, richmond-yard VHYT90A, daly-city-shop VHYTX0A, concord-shop VHYT60A)")
    public YardReportsMap map(@PathVariable String map) {
        return service.map(map);
    }
}
