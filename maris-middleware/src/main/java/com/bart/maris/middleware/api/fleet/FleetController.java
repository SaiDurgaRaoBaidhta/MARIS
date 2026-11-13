package com.bart.maris.middleware.api.fleet;

import com.bart.maris.middleware.domain.fleet.Fleet;
import com.bart.maris.middleware.domain.fleet.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Yard tracking")
public class FleetController {
    private final FleetService fleet;

    public FleetController(FleetService fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/api/v1/fleet")
    @Operation(summary = "Every yard with its tracks and the cars on them (legacy program VHSW01A, read-only)")
    public Fleet fleet() {
        return fleet.fleet();
    }
}
