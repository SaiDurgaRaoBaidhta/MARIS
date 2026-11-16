package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingMapsService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yard tracking maps: VHYT40I / VHYT41I Dublin-Pleasanton, VHYT50I / VHYT51I Pittsburg-Bay Point, VHYT04I Hayward shop. */
@RestController
@RequestMapping("/api/v1/yard-tracking/maps")
@Tag(name = "Yard tracking maps")
public class YardTrackingMapsController {
    private final YardTrackingMapsService service;

    public YardTrackingMapsController(YardTrackingMapsService service) {
        this.service = service;
    }

    @GetMapping("/{map}")
    @Operation(summary = "The cars on a yard tracking map (dublin / dublin-update VHYT40A, pittsburg / pittsburg-update VHYT50A, hayward-shop VHYT04A)")
    public YardTrackingMap map(@PathVariable String map) {
        return service.map(map);
    }

    @GetMapping("/{map}/maintenance-access")
    @Operation(summary = "May the signed-on user group open the update map's maintenance menu (VHYTM6I)? (access list VH01A02)")
    public Map<String, Boolean> maintenanceAccess(@PathVariable String map, @AuthenticationPrincipal MarisUser user) {
        return Map.of("allowed", service.maintenanceAllowed(map, user == null ? "" : user.groupId()));
    }
}
