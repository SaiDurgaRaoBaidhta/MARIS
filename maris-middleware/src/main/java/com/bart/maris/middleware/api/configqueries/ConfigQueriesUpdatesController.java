package com.bart.maris.middleware.api.configqueries;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesAxleService;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponentAddService;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesTruckService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration tracking query screens that write: old VHCF21I add a component serial to stock, VHCF37I truck inquiry /
 * VHCF35I truck build, VHCF06I axle and gear box maintenance.
 */
@RestController
@RequestMapping("/api/v1/config-queries")
@Tag(name = "Configuration tracking queries")
public class ConfigQueriesUpdatesController {
    private final ConfigQueriesComponentAddService add;
    private final ConfigQueriesTruckService trucks;
    private final ConfigQueriesAxleService axles;

    public ConfigQueriesUpdatesController(ConfigQueriesComponentAddService add, ConfigQueriesTruckService trucks, ConfigQueriesAxleService axles) {
        this.add = add;
        this.trucks = trucks;
        this.axles = axles;
    }

    static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    public record AddRequest(String componentType, String stock, String serial, String date) {}

    @GetMapping("/serial-add/options")
    @Operation(summary = "Component types and the proposed date (old VHCF21I; VHCF21A)")
    public ConfigQueriesComponentAddService.Options addOptions(@AuthenticationPrincipal MarisUser u) {
        return add.options(group(u));
    }

    @GetMapping("/serial-add/stocks")
    @Operation(summary = "A component type's stock numbers (old VHCF21I; VHCF21B)")
    public ConfigQueriesComponentAddService.Stocks addStocks(@RequestParam String componentType, @AuthenticationPrincipal MarisUser u) {
        return add.stocks(group(u), componentType);
    }

    @PostMapping("/serial-add")
    @Operation(summary = "Add a serial to stock: date MM-DD-YYYY (old VHCF21I Update; VHCF21C, VHCF21E, VHCF21D)")
    public ConfigQueriesComponentAddService.Result addSerial(@RequestBody AddRequest r, @AuthenticationPrincipal MarisUser u) {
        return add.add(group(u), r.componentType(), r.stock(), r.serial(), r.date());
    }

    public record BuildRequest(String stock, String serial, Map<String, String> components, List<String> takeOff, Map<String, String> axleParts,
                               String status, String location, String comments) {}

    public record AxleUpdateRequest(String kind, String serial, Map<String, String> values, Set<String> checks) {}

    @GetMapping("/trucks/options")
    @Operation(summary = "Truck stock numbers, statuses and locations (old VHCF35I / VHCF37I; VHCF35C)")
    public ConfigQueriesTruckService.Options truckOptions(@AuthenticationPrincipal MarisUser u) {
        return trucks.options(group(u));
    }

    @GetMapping("/trucks")
    @Operation(summary = "A truck and its components; forBuild=true as the build screen shows it, Update enabled or not "
            + "(old VHCF37I / VHCF35I Retrieve; VHCF35D, VHCF35B, VHCF37F)")
    public ConfigQueriesTruckService.Truck truck(@RequestParam String stock, @RequestParam String serial, @RequestParam(defaultValue = "false") boolean forBuild,
                                                 @AuthenticationPrincipal MarisUser u) {
        return trucks.retrieve(group(u), forBuild ? ConfigQueriesTruckService.BUILD : ConfigQueriesTruckService.INQUIRY, stock, serial);
    }

    @PostMapping("/trucks/build")
    @Operation(summary = "Truck build Update: new serials per part (axle1, axle2, tractionMotor1/2, brakeDisc1/2) are checked and added, or the "
            + "parts in takeOff come off the truck (old VHCF35I; VHCF35D, VHCF35G, VHCF35H)")
    public ConfigQueriesTruckService.Truck build(@RequestBody BuildRequest r, @AuthenticationPrincipal MarisUser u) {
        return trucks.build(group(u), new ConfigQueriesTruckService.Build(r.stock(), r.serial(), r.components(), r.takeOff(), r.axleParts(), r.status(),
                r.location(), r.comments()));
    }

    @GetMapping("/axles")
    @Operation(summary = "An axle (kind=axle) or gear box (kind=gearbox) and its parts (old VHCF06I List; VHCF06A)")
    public ConfigQueriesAxleService.Axle axle(@RequestParam(defaultValue = "axle") String kind, @RequestParam(required = false) String serial,
                                              @AuthenticationPrincipal MarisUser u) {
        return axles.list(group(u), kind, serial);
    }

    @PostMapping("/axles/update")
    @Operation(summary = "Axle / gear box Update: the values typed and the boxes checked (old VHCF06I Update; VHCF06C..M, VHCF01J, VHCF01P)")
    public ConfigQueriesAxleService.Axle updateAxle(@RequestBody AxleUpdateRequest r, @AuthenticationPrincipal MarisUser u) {
        return axles.update(group(u), r.kind(), r.serial(), r.values(), r.checks());
    }
}
