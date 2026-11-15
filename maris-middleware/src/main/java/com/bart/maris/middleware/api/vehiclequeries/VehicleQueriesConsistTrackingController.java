package com.bart.maris.middleware.api.vehiclequeries;

import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consist tracking, read-only: the status train criteria (old VHST05I) with its summary list (VHST02I) and car details
 * (VHST04I), and the symptom criteria (VHST01I) with its consist view (VHST03I).
 */
@RestController
@RequestMapping("/api/v1/vehicle-queries/consist-tracking")
@Tag(name = "Vehicle queries")
public class VehicleQueriesConsistTrackingController {
    private final VehicleQueriesConsistTrackingService service;

    public VehicleQueriesConsistTrackingController(VehicleQueriesConsistTrackingService service) {
        this.service = service;
    }

    @GetMapping("/options")
    @Operation(summary = "Report types and printers of the status train criteria (VHST05I)")
    public VehicleQueriesConsistTrackingService.Options options() {
        return service.options();
    }

    @GetMapping("/status-trains")
    @Operation(summary = "Consists in a range with their cars (VHST05I Display, report 1: VHST02I, VHST2A1); from may be ALL")
    public VehicleQueriesConsistTrackingService.Trains statusTrains(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.statusTrains(from, to);
    }

    @GetMapping("/car-details")
    @Operation(summary = "Consists in a range with each car's PM, wash and cleaning (VHST05I Display, report 2: VHST04I, VHST4A2, VHST04A, VHST04B)")
    public VehicleQueriesConsistTrackingService.Details carDetails(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.carDetails(from, to);
    }

    public record PrintRequest(int report, String from, String to, String printer) {}

    @PostMapping("/print")
    @Operation(summary = "Print report 1 or 2 for a consist range (VHST05I Print: VHST011)")
    public Map<String, String> print(@RequestBody PrintRequest r) {
        return Map.of("message", service.print(r.report(), r.from(), r.to(), r.printer()));
    }

    @GetMapping("/symptoms")
    @Operation(summary = "A consist's cars with a symptom's counts (VHST01I Display, VHST03I: VHST3A3, VHST3A4, VHST3A1)")
    public VehicleQueriesConsistTrackingService.Symptoms symptoms(@RequestParam(required = false) String consist, @RequestParam(required = false) String symptom) {
        return service.symptoms(consist, symptom);
    }

    public record SymptomPrintRequest(String consist, String symptom, String printer) {}

    @PostMapping("/symptoms/print")
    @Operation(summary = "Print the symptom report (VHST01I Print: VHST011 report 3)")
    public Map<String, String> printSymptoms(@RequestBody SymptomPrintRequest r) {
        return Map.of("message", service.printSymptom(r.consist(), r.symptom(), r.printer()));
    }
}
