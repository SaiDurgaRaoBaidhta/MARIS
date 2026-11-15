package com.bart.maris.middleware.api.carwash;

import com.bart.maris.middleware.domain.carwash.CarWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Car wash reports (old screens VHCWR1I, VHCWR2I). */
@RestController
@RequestMapping("/api/v1/car-wash")
@Tag(name = "Car wash")
public class CarWashController {
    private final CarWashService service;

    public CarWashController(CarWashService service) {
        this.service = service;
    }

    @GetMapping("/washes")
    @Operation(summary = "Cars washed in a yard between two dates, yyyy-MM-dd (program VHCWR1A)")
    public CarWashService.Washes washes(@RequestParam(required = false) String yard, @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        return service.washes(yard, from, to);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Cars of a home yard whose wash is over due (program VHCWR2A)")
    public CarWashService.Overdue overdue(@RequestParam(required = false) String homeYard) {
        return service.overdue(homeYard);
    }
}
