package com.bart.maris.middleware.api.vehiclemaint;

import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintCarService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintStatusHistoryService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle maintenance updates: the vehicle table (old VHVM02I add, VHVM03I change) and an incident's status history (VHVM06I). */
@RestController
@RequestMapping("/api/v1/vehicle-maintenance")
@Tag(name = "Vehicle maintenance")
public class VehicleMaintCarController {
    private final VehicleMaintCarService cars;
    private final VehicleMaintStatusHistoryService history;

    public VehicleMaintCarController(VehicleMaintCarService cars, VehicleMaintStatusHistoryService history) {
        this.cars = cars;
        this.history = history;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    @GetMapping("/cars/options")
    @Operation(summary = "Choices of the add and change car screens (VHVM1A6, VHVM1A7, VHCY1A1, VHVM1A9)")
    public VehicleMaintCarService.Options carOptions() {
        return cars.options();
    }

    @PostMapping("/cars")
    @Operation(summary = "Add a car to the vehicle table (VHVM1AA, VHVM1A3); fields by their record names")
    public Map<String, String> addCar(@RequestBody Map<String, Object> car) {
        return Map.of("message", cars.add(car));
    }

    @GetMapping("/cars/{car}")
    @Operation(summary = "A car of the vehicle table (VHVM1A5) and the fields this user group may change")
    public VehicleMaintCarService.Car car(@PathVariable String car, @AuthenticationPrincipal MarisUser user) {
        return cars.car(car, group(user));
    }

    @PostMapping("/cars/{car}/changes")
    @Operation(summary = "Change a car (VHVM1A4); only the changed fields are sent")
    public Map<String, String> updateCar(@PathVariable String car, @RequestBody Map<String, Object> changes, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", cars.update(car, changes, group(user)));
    }

    @GetMapping("/status-history/options")
    @Operation(summary = "Yards and incident statuses (VH01A01) and move reasons (VHYT15A)")
    public VehicleMaintStatusHistoryService.Options historyOptions(@AuthenticationPrincipal MarisUser user) {
        return history.options(group(user));
    }

    @GetMapping("/status-history/sub-locations")
    @Operation(summary = "A yard's sub-locations (VHVM6A6)")
    public List<String> subLocations(@RequestParam String yard) {
        return history.subLocations(yard);
    }

    @GetMapping("/status-history/{incident}")
    @Operation(summary = "An incident's status changes (VHVM6A1)")
    public List<VehicleMaintStatusHistoryService.Entry> entries(@PathVariable String incident) {
        return history.entries(incident);
    }

    @PostMapping("/status-history/{incident}")
    @Operation(summary = "Add a status change (VHVM6A2)")
    public VehicleMaintStatusHistoryService.Result addEntry(@PathVariable String incident, @RequestBody VehicleMaintStatusHistoryService.Change c,
                                                            @AuthenticationPrincipal MarisUser user) {
        return history.add(incident, c, group(user));
    }

    @PostMapping("/status-history/{incident}/changes")
    @Operation(summary = "Change a status change, found by its timestamp (VHVM6A3)")
    public VehicleMaintStatusHistoryService.Result updateEntry(@PathVariable String incident, @RequestBody VehicleMaintStatusHistoryService.Change c,
                                                               @AuthenticationPrincipal MarisUser user) {
        return history.update(incident, c, group(user));
    }

    public record Delete(String timestamp) {}

    @PostMapping("/status-history/{incident}/delete")
    @Operation(summary = "Delete a status change, found by its timestamp (VHVM6A4)")
    public VehicleMaintStatusHistoryService.Result deleteEntry(@PathVariable String incident, @RequestBody Delete d,
                                                               @AuthenticationPrincipal MarisUser user) {
        return history.delete(incident, d.timestamp(), group(user));
    }
}
