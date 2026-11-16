package com.bart.maris.middleware.api.configtracking;

import com.bart.maris.middleware.domain.configtracking.ConfigTrackingCarService;
import com.bart.maris.middleware.domain.configtracking.ConfigTrackingReportService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration tracking: a car's configuration (old VHCF01I) and its report's printer window (VFCF05I). */
@RestController
@RequestMapping("/api/v1/config-tracking")
@Tag(name = "Configuration tracking")
public class ConfigTrackingController {
    private final ConfigTrackingCarService cars;
    private final ConfigTrackingReportService report;

    public ConfigTrackingController(ConfigTrackingCarService cars, ConfigTrackingReportService report) {
        this.cars = cars;
        this.report = report;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    public record ChangeRequest(String action, String truck, Map<String, String> values, List<String> highlight, String employee, String location) {}

    public record PrintRequest(String printer) {}

    @GetMapping("/cars/options")
    @Operation(summary = "The actions, trucks (X / Y) and locations of the car configuration screen (old VHCF01I)")
    public ConfigTrackingCarService.Options options() {
        return cars.options();
    }

    @GetMapping("/cars/{car}")
    @Operation(summary = "A car's trucks and parts; action as the old list (NO ACTION SELECTED, LIST DATA, ... SERIAL OFF, ... SERIAL ON, GEOMETRY DATA), "
            + "truck X or Y (old VHCF01I List / Refresh; VHCF01A, VHCF01B ...)")
    public ConfigTrackingCarService.Car car(@PathVariable String car, @RequestParam(required = false) String action, @RequestParam(required = false) String truck,
                                            @AuthenticationPrincipal MarisUser u) {
        return cars.list(group(u), car, action, truck);
    }

    @PostMapping("/cars/{car}")
    @Operation(summary = "Update: values by the old field name (AXLE-SERIAL-01, X-TRUCK-SERIAL, FLANGE-1 ...), parts to highlight (TRUCK1, AXLE1, DISC1, TM1 ...), "
            + "employee and location (old VHCF01I Update)")
    public ConfigTrackingCarService.Car update(@PathVariable String car, @RequestBody ChangeRequest r, @AuthenticationPrincipal MarisUser u) {
        return cars.update(group(u), new ConfigTrackingCarService.Change(car, r.action(), r.truck(), r.values(), r.highlight(), r.employee(), r.location()));
    }

    @GetMapping("/printers")
    @Operation(summary = "The printers for the car configuration report (old VFCF05I; WS70A01)")
    public ConfigTrackingReportService.Printers printers() {
        return report.printers();
    }

    @PostMapping("/cars/{car}/print")
    @Operation(summary = "Print the car configuration report on a printer (VTAM name) (old VFCF05I Hard Copy; VHCF05A)")
    public ResponseEntity<Void> print(@PathVariable String car, @RequestBody PrintRequest r) {
        report.print(car, r.printer());
        return ResponseEntity.accepted().build();
    }
}
