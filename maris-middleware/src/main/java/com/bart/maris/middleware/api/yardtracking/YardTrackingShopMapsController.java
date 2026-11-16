package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingMap;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopMapsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Concord (VHYT60I) and Richmond (VHYT96I) shop maps. Their paths sit beside the other yard tracking maps
 * (/api/v1/yard-tracking/maps/{map}); a literal path wins over that pattern, so the map page reads them the same way.
 */
@RestController
@RequestMapping("/api/v1/yard-tracking/maps")
@Tag(name = "Yard tracking maps")
public class YardTrackingShopMapsController {
    private final YardTrackingShopMapsService service;

    public YardTrackingShopMapsController(YardTrackingShopMapsService service) {
        this.service = service;
    }

    @GetMapping("/concord-shop")
    @Operation(summary = "The cars on the Concord shop map (old VHYT60I, program VHYT60A)")
    public YardTrackingMap concordShop() {
        return service.map("concord-shop");
    }

    @GetMapping("/richmond-shop")
    @Operation(summary = "The cars on the Richmond shop map (old VHYT96I, program VHYT95A)")
    public YardTrackingMap richmondShop() {
        return service.map("richmond-shop");
    }
}
