package com.bart.maris.middleware.api.incidents;

import com.bart.maris.middleware.domain.incidents.IncidentsCarService;
import com.bart.maris.middleware.domain.incidents.IncidentsRevenueService;
import com.bart.maris.middleware.domain.incidents.IncidentsTrainService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Incidents: train incidents (old VHINC01I), revenue incidents of a car (VHINC06I), event / shop / miscellaneous incidents of a car (VHINC03I). */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents")
public class IncidentsController {
    private final IncidentsTrainService train;
    private final IncidentsRevenueService revenue;
    private final IncidentsCarService car;

    public IncidentsController(IncidentsTrainService train, IncidentsRevenueService revenue, IncidentsCarService car) {
        this.train = train;
        this.revenue = revenue;
        this.car = car;
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    @GetMapping("/train/defaults")
    @Operation(summary = "A new train incident's date (revenue date: VHINCD / VHCI02G) and time (now)")
    public IncidentsTrainService.Defaults trainDefaults() {
        return train.defaults();
    }

    @PostMapping("/train")
    @Operation(summary = "Create a train incident (VH01A02 access, VHINC1A, VHINC1B checks, VHINVC, VHINC1F)")
    public IncidentsTrainService.Result createTrain(@RequestBody IncidentsTrainService.Create c, @AuthenticationPrincipal MarisUser user) {
        return train.create(c, group(user));
    }

    @GetMapping("/train/{incidentNo}")
    @Operation(summary = "A train incident and its narratives (VHINC1D)")
    public IncidentsTrainService.Incident trainIncident(@PathVariable String incidentNo) {
        return train.incident(incidentNo);
    }

    @PostMapping("/train/{incidentNo}")
    @Operation(summary = "Change a train incident's symptom, speed, direction and location (VH01A02 access, VHINC1A, VHINC1C)")
    public IncidentsTrainService.Result changeTrain(@PathVariable String incidentNo, @RequestBody IncidentsTrainService.Change c,
                                                    @AuthenticationPrincipal MarisUser user) {
        return train.change(incidentNo, c, group(user));
    }

    @GetMapping("/revenue/defaults")
    @Operation(summary = "A new revenue incident's date (the revenue date)")
    public IncidentsTrainService.Defaults revenueDefaults() {
        return revenue.defaults();
    }

    @PostMapping("/revenue")
    @Operation(summary = "Create a revenue incident of a car (VH01A02 access; VHINC6A, VHINC1A, AFCXVLD, VHXREVC checks; VHINVC, VHINCX, form VHINC3C)")
    public IncidentsRevenueService.Result createRevenue(@RequestBody IncidentsRevenueService.Create c, @AuthenticationPrincipal MarisUser user) {
        return revenue.create(c, group(user));
    }

    @GetMapping("/car-incident/options")
    @Operation(summary = "A new event (EVT), shop (SHN) or miscellaneous (MSC) incident: title, today's date (VHCI02G), discovered durations (VHINCXA)")
    public IncidentsCarService.Options carOptions(@RequestParam String type) {
        return car.options(type);
    }

    @GetMapping("/car-incident/symptoms/{symptom}/narrative")
    @Operation(summary = "A symptom's standard narrative (VHINC3B)")
    public java.util.Map<String, String> symptomNarrative(@PathVariable String symptom) {
        return java.util.Map.of("narrative", car.symptomNarrative(symptom));
    }

    @PostMapping("/car-incident")
    @Operation(summary = "Create an event / shop / miscellaneous incident of a car (VHINC6A, VHINC1A, VHINCXA, AFCXVLD checks; VHINVB, VHINCZ, form VHINC3A)")
    public IncidentsCarService.Result createCar(@RequestBody IncidentsCarService.Create c, @AuthenticationPrincipal MarisUser user) {
        return car.create(c, group(user));
    }
}
