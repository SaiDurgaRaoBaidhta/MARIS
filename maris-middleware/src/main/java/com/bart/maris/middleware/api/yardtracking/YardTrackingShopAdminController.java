package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopAdminService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingShopMoveService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The shop administration maps (old VHYT10I Hayward, VHYT95I Richmond) and the shop car move (VHYT03I, VHYT42I). */
@RestController
@RequestMapping("/api/v1/yard-tracking")
@Tag(name = "Yard tracking: shop administration maps and car move")
public class YardTrackingShopAdminController {
    private final YardTrackingShopAdminService maps;
    private final YardTrackingShopMoveService move;

    public YardTrackingShopAdminController(YardTrackingShopAdminService maps, YardTrackingShopMoveService move) {
        this.maps = maps;
        this.move = move;
    }

    @GetMapping("/shop-admin/{map}")
    @Operation(summary = "VHYT10I (hayward) / VHYT95I (richmond): the cars on the shop's tracks and spots")
    public YardTrackingShopAdminService.ShopMap map(@PathVariable String map) {
        return maps.map(map);
    }

    public record SelectRequest(List<String> spots) {}

    @PostMapping("/shop-admin/{map}/selection")
    @Operation(summary = "The map's spot clicks (\"track/position\"): whether they open the car move VHYT03I, and with what")
    public YardTrackingShopAdminService.Selection select(@PathVariable String map, @RequestBody SelectRequest r) {
        return maps.select(map, r.spots());
    }

    public record OpenRequest(YardTrackingShopMoveService.Context context, String carNo) {}

    @PostMapping("/shop-move")
    @Operation(summary = "VHYT03I opens: the car, its current reason, the reason codes and the shop's locations")
    public YardTrackingShopMoveService.View open(@AuthenticationPrincipal MarisUser user, @RequestBody OpenRequest r) {
        return move.open(r.context(), group(user), r.carNo());
    }

    public record UpdateRequest(YardTrackingShopMoveService.Context context, String carNo, String reason, String location,
                                String compress, boolean reasonUnchanged) {}

    @PostMapping("/shop-move/update")
    @Operation(summary = "VHYT03I Update (VHYT03G reason only, VHYT03H move); VHYT42I's question when the reason did not change")
    public YardTrackingShopMoveService.Outcome update(@AuthenticationPrincipal MarisUser user, @RequestBody UpdateRequest r) {
        return move.update(r.context(), group(user), r.carNo(), r.reason(), r.location(), r.compress(), r.reasonUnchanged());
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }
}
