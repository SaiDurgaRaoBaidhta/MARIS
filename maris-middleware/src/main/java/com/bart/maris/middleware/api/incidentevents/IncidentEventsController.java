package com.bart.maris.middleware.api.incidentevents;

import com.bart.maris.middleware.domain.incidentevents.IncidentEventsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Event and incident log (old screens VHIE01I, VHIE02I, VHIE06I). */
@RestController
@RequestMapping("/api/v1/incident-events")
@Tag(name = "Incident events")
public class IncidentEventsController {
    private final IncidentEventsService service;

    public IncidentEventsController(IncidentEventsService service) {
        this.service = service;
    }

    @GetMapping("/revenue-date")
    @Operation(summary = "The revenue date the log starts with (program VHIE01B)")
    public IncidentEventsService.RevenueDate revenueDate() {
        return service.revenueDate();
    }

    @GetMapping("/incidents")
    @Operation(summary = "Incidents between two revenue dates, yyyy-MM-dd (program VHIE01A)")
    public IncidentEventsService.IncidentLog incidents(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.incidents(from, to);
    }

    @GetMapping("/printers")
    @Operation(summary = "Printers (program WS70A01)")
    public List<IncidentEventsService.Printer> printers() {
        return service.printers();
    }

    public record PrintRequest(String from, String to, String printer) {}

    @PostMapping("/incidents/print")
    @Operation(summary = "Print the log (program VHIE02A)")
    public Map<String, String> print(@RequestBody PrintRequest r) {
        return Map.of("message", service.print(r.from(), r.to(), r.printer()));
    }

    @GetMapping("/cars/{car}/consist")
    @Operation(summary = "The consist a car is in (program VHIE03H)")
    public IncidentEventsService.ConsistOfCar consistOfCar(@PathVariable String car) {
        return service.consistOfCar(car);
    }

    @GetMapping("/consists/{consist}/cars")
    @Operation(summary = "The cars of a consist, in train order (program VHIE06A)")
    public IncidentEventsService.ConsistCars consistCars(@PathVariable String consist) {
        return service.consistCars(consist);
    }
}
