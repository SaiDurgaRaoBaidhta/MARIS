package com.bart.maris.middleware.api.carbright;

import com.bart.maris.middleware.domain.carbright.CarBrightService;
import com.bart.maris.middleware.domain.carbright.CarBrightThoroughCleanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Car thorough cleaning (old screens VHCBR1I, VHCBR2I; adding cleanings VHCBP1I). */
@RestController
@RequestMapping("/api/v1/car-bright")
@Tag(name = "Car bright")
public class CarBrightController {
    private final CarBrightService service;
    private final CarBrightThoroughCleanService cleanings;

    public CarBrightController(CarBrightService service, CarBrightThoroughCleanService cleanings) {
        this.service = service;
        this.cleanings = cleanings;
    }

    @GetMapping("/home-yards")
    @Operation(summary = "The home yards the report accepts (the old screen's list)")
    public List<String> homeYards() {
        return service.homeYards();
    }

    @GetMapping("/cleaning")
    @Operation(summary = "Cars by last thorough cleaning or cleaning over due (programs VHCBR1X, VHCBR1A)")
    public CarBrightService.Cleaning cleaning(@RequestParam(required = false) String homeYard, @RequestParam(required = false) String option) {
        return service.list(homeYard, option);
    }

    @GetMapping("/printers")
    @Operation(summary = "Printers (program WS70A01)")
    public List<CarBrightService.Printer> printers() {
        return service.printers();
    }

    public record PrintRequest(String homeYard, String option, String printer) {}

    @PostMapping("/cleaning/print")
    @Operation(summary = "Print the report (program VHCBR02)")
    public Map<String, String> print(@RequestBody PrintRequest r) {
        return Map.of("message", service.print(r.homeYard(), r.option(), r.printer()));
    }

    @GetMapping("/thorough-cleanings/defaults")
    @Operation(summary = "Yards, the default yard, today's revenue date (MM-DD-YYYY) and the time (VHIE01B), for adding cleanings")
    public CarBrightThoroughCleanService.Defaults cleaningDefaults() {
        return cleanings.defaults();
    }

    /** @param date MM-DD-YYYY; @param time HH:MM; @param cars up to 20 car numbers, blanks skipped */
    public record CleaningRequest(String yard, String date, String time, List<String> cars) {}

    @PostMapping("/thorough-cleanings")
    @Operation(summary = "Add a thorough cleaning for up to 20 cars: checks (VHCBR2C per car), then the update (VHCBP1A)")
    public CarBrightThoroughCleanService.Added addCleaning(@RequestBody CleaningRequest r) {
        return cleanings.add(r.yard(), r.date(), r.time(), r.cars());
    }
}
