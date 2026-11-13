package com.bart.maris.middleware.api.yardreports;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardreports.YardReportsMove;
import com.bart.maris.middleware.domain.yardreports.YardReportsMoveService;
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
import org.springframework.web.bind.annotation.RestController;

/** Yard maps and the pseudo train move (old screens VHIC03I, VHIC01 / VHIC02 Berryessa, VHYT80I / 81I Millbrae, VHYT105I / 106I, 70I / 71I, 90I / 91I, VHSW01I). */
@RestController
@RequestMapping("/api/v1/yard-reports")
@Tag(name = "Yard reports")
public class YardReportsController {
    private final YardReportsService service;
    private final YardReportsMoveService moves;

    public YardReportsController(YardReportsService service, YardReportsMoveService moves) {
        this.service = service;
        this.moves = moves;
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
