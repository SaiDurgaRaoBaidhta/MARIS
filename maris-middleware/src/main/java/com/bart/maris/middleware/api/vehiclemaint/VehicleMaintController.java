package com.bart.maris.middleware.api.vehiclemaint;

import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService.CarEventCriteria;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService.EventCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle maintenance, read-only (old screens VHNAR02I, VHVM12I, VHVM13i). */
@RestController
@RequestMapping("/api/v1/vehicle-maintenance")
@Tag(name = "Vehicle maintenance")
public class VehicleMaintController {
    private final VehicleMaintReportsService service;

    public VehicleMaintController(VehicleMaintReportsService service) {
        this.service = service;
    }

    @GetMapping("/incidents/{incident}/narrative")
    @Operation(summary = "An incident's narrative (program VHNARX)")
    public VehicleMaintReportsService.Narrative narrative(@PathVariable String incident) {
        return service.narrative(incident);
    }

    @GetMapping("/reports/options")
    @Operation(summary = "Printers, car types, home yards and event types for the reports (WS70A01, VHVM13B)")
    public VehicleMaintReportsService.ReportOptions options() {
        return service.options();
    }

    public record EventsPrint(String dateFrom, String dateTo, String car, String carType, String homeYard, String eventType, String eventSubType,
                              String eventStatus, String location, String trackingRefNo, String sortOrder, String printer) {}

    @PostMapping("/reports/events/print")
    @Operation(summary = "Print the vehicle event report (program VHVM12A)")
    public Map<String, String> printEvents(@RequestBody EventsPrint r) {
        return Map.of("message", service.printEvents(new EventCriteria(r.dateFrom(), r.dateTo(), r.car(), r.carType(), r.homeYard(), r.eventType(),
                r.eventSubType(), r.eventStatus(), r.location(), r.trackingRefNo(), r.sortOrder()), r.printer()));
    }

    public record CarEventsPrint(String carType, String date, String eventType, String eventStatus, String trackingRefNo, String comments,
                                 String homeYard, String printer) {}

    @PostMapping("/reports/car-events/print")
    @Operation(summary = "Print the car event report (program VHVM13A)")
    public Map<String, String> printCarEvents(@RequestBody CarEventsPrint r) {
        return Map.of("message", service.printCarEvents(new CarEventCriteria(r.carType(), r.date(), r.eventType(), r.eventStatus(),
                r.trackingRefNo(), r.comments(), r.homeYard()), r.printer()));
    }
}
