package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingHaywardService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Hayward yard update map (old VHYT05I). Read-only: the moves happen in the screens it opens (VHYT11I ...). */
@RestController
@RequestMapping("/api/v1/yard-tracking/hayward-yard")
@Tag(name = "Yard tracking: Hayward yard map (update)")
public class YardTrackingHaywardController {
    private final YardTrackingHaywardService service;

    public YardTrackingHaywardController(YardTrackingHaywardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "VHYT05I: the cars on the Hayward yard's tracks and whether Maint is allowed")
    public YardTrackingHaywardService.HaywardMap map(@AuthenticationPrincipal MarisUser user) {
        return service.map(user == null ? "" : user.groupId());
    }

    public record SelectRequest(List<YardTrackingHaywardService.Click> clicks) {}

    @PostMapping("/selection")
    @Operation(summary = "VHYT05I clicks: the window they open (VHYT11I) and what the map passes to it")
    public YardTrackingHaywardService.Selection select(@RequestBody SelectRequest r) {
        return service.select(r.clicks());
    }
}
