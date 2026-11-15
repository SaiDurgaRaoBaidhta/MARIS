package com.bart.maris.middleware.api.vehiclemaint;

import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintCarHoursService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintConsistHistoryService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintConsistStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle maintenance updates: car hours after a test (old VHVM08I), consist status (VHVM10I), consist history (VHVM19I). */
@RestController
@RequestMapping("/api/v1/vehicle-maintenance")
@Tag(name = "Vehicle maintenance")
public class VehicleMaintUpdatesController {
    private final VehicleMaintCarHoursService hours;
    private final VehicleMaintConsistStatusService status;
    private final VehicleMaintConsistHistoryService history;

    public VehicleMaintUpdatesController(VehicleMaintCarHoursService hours, VehicleMaintConsistStatusService status, VehicleMaintConsistHistoryService history) {
        this.hours = hours;
        this.status = status;
        this.history = history;
    }

    @GetMapping("/car-hours/{car}")
    @Operation(summary = "A car's hours (program VHVM1A5)")
    public VehicleMaintCarHoursService.CarHours carHours(@PathVariable String car) {
        return hours.car(car);
    }

    public record TestHours(String testHours) {}

    @PostMapping("/car-hours/{car}/test-hours")
    @Operation(summary = "Add test hours to a car's PM hours (programs VHVM1A5, VHVM8A1)")
    public VehicleMaintCarHoursService.Updated addTestHours(@PathVariable String car, @RequestBody TestHours r) {
        return hours.addTestHours(car, r.testHours());
    }

    @GetMapping("/consist-status/options")
    @Operation(summary = "The consist statuses and the table columns")
    public VehicleMaintConsistStatusService.Options consistStatusOptions() {
        return status.options();
    }

    @GetMapping("/consist-status")
    @Operation(summary = "Consists in a number range (program VHVM10A)")
    public VehicleMaintConsistStatusService.Consists consists(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return status.consists(from, to);
    }

    @PostMapping("/consist-status/update")
    @Operation(summary = "Change a consist's status and ICS details (program VHVM10C)")
    public Map<String, String> updateConsist(@RequestBody VehicleMaintConsistStatusService.Change c) {
        return Map.of("message", status.update(c));
    }

    @GetMapping("/consist-history/options")
    @Operation(summary = "The current revenue date (program VHYT34A), the consist statuses and the table columns")
    public VehicleMaintConsistHistoryService.Options consistHistoryOptions() {
        return history.options();
    }

    @GetMapping("/consist-history")
    @Operation(summary = "Consist history in a number range on a revenue date, MM-DD-YYYY (program VHVM19A)")
    public VehicleMaintConsistHistoryService.History consistHistory(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                    @RequestParam(required = false) String revDate) {
        return history.history(from, to, revDate);
    }

    @PostMapping("/consist-history/update")
    @Operation(summary = "Change a consist history record's status and ICS details (program VHVM19B)")
    public Map<String, String> updateHistory(@RequestBody VehicleMaintConsistHistoryService.Change c) {
        return Map.of("message", history.update(c));
    }
}
