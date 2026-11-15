package com.bart.maris.middleware.api.carwash;

import com.bart.maris.middleware.config.RequestIdFilter;
import com.bart.maris.middleware.domain.carwash.CarWashCarsWashService;
import com.bart.maris.middleware.domain.carwash.CarWashConsistWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Car wash updates: a whole consist (old VHCWA2I) and single cars (old VHCWA3I). */
@RestController
@RequestMapping("/api/v1/car-wash")
@Tag(name = "Car wash")
public class CarWashUpdateController {
    private final CarWashConsistWashService consist;
    private final CarWashCarsWashService cars;

    public CarWashUpdateController(CarWashConsistWashService consist, CarWashCarsWashService cars) {
        this.consist = consist;
        this.cars = cars;
    }

    @GetMapping("/consist-washes/start")
    @Operation(summary = "What the consist wash screen starts with: the revenue date (VHIE01B), the yards, the first message")
    public CarWashConsistWashService.Start consistStart() {
        return consist.start();
    }

    @GetMapping("/consist-washes/consist")
    @Operation(summary = "The cars of a consist in service on a revenue date, yyyy-MM-dd (VHCWA5, VHCWA3)")
    public CarWashConsistWashService.Consist consist(@RequestParam(required = false) String consist, @RequestParam(required = false) String date) {
        return this.consist.consist(consist, date);
    }

    @PostMapping("/consist-washes")
    @Operation(summary = "Record the wash of every car of a consist (VHCWA6, writes)")
    public CarWashConsistWashService.Result washConsist(@RequestBody CarWashConsistWashService.Wash wash) {
        return consist.wash(wash);
    }

    @GetMapping("/car-washes/start")
    @Operation(summary = "What the single car wash screen starts with: the revenue date (VHIE01B), the dummy consist, the yards")
    public CarWashCarsWashService.Start carsStart() {
        return cars.start();
    }

    @GetMapping("/car-washes/time")
    @Operation(summary = "The old screen's time check: the HH:MM:SS it stores and its message")
    public CarWashCarsWashService.TimeCheck time(@RequestParam(required = false) String time) {
        return cars.time(time);
    }

    @PostMapping("/car-washes")
    @Operation(summary = "Record the wash of up to 10 cars (VHINVB, VHCWA3A, VHCWA3B, then VHCWA3D, which writes)")
    public CarWashCarsWashService.Result washCars(@RequestBody CarWashCarsWashService.Wash wash) {
        return cars.wash(wash);
    }

    /** the old screen marked the invalid cars with "B": a 400 like every input error, with the boxes (1-10) marked */
    @ExceptionHandler(CarWashCarsWashService.InvalidCarsException.class)
    ProblemDetail invalidCars(CarWashCarsWashService.InvalidCarsException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setProperty("code", "INVALID_INPUT");
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        p.setProperty("invalidBoxes", e.boxes());
        return p;
    }
}
