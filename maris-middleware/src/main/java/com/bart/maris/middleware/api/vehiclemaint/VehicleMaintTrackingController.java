package com.bart.maris.middleware.api.vehiclemaint;

import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Change;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Criteria;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Entry;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Event;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintTrackingService.Pass;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle parts status / mod tracking (old screen VHVM11I). A response with "question" asks the user; resend with "answer". */
@RestController
@RequestMapping("/api/v1/vehicle-maintenance/tracking-events")
@Tag(name = "Vehicle maintenance")
public class VehicleMaintTrackingController {
    private final VehicleMaintTrackingService service;

    public VehicleMaintTrackingController(VehicleMaintTrackingService service) {
        this.service = service;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    @GetMapping("/options")
    @Operation(summary = "Dates, event types, statuses, locations and fixed choices (VHVMDTA, VHVM11E, VHVM11F, VHVM11G)")
    public VehicleMaintTrackingService.Options options(@RequestParam(required = false) String from, @AuthenticationPrincipal MarisUser user) {
        return service.options(group(user), from);
    }

    @PostMapping("/search")
    @Operation(summary = "The events matching the criteria (VHVM11A)")
    public VehicleMaintTrackingService.Listing list(@RequestBody Criteria c) {
        return service.list(c);
    }

    @PostMapping("/linked")
    @Operation(summary = "The events VHCI02I / VHCI03I open the screen on, with the form they fill (VHVM11A)")
    public VehicleMaintTrackingService.Linked linked(@RequestBody Pass p, @RequestParam(required = false) String dateFrom) {
        return service.linked(p, dateFrom);
    }

    @PostMapping("/selection")
    @Operation(summary = "What the screen shows for a selected event (VHVM11H)")
    public VehicleMaintTrackingService.Selection select(@RequestBody Event e) {
        return service.select(e);
    }

    @GetMapping("/cars/{car}/check")
    @Operation(summary = "The car number is known (VHVM1A5)")
    public Map<String, String> checkCar(@PathVariable String car) {
        return Map.of("message", service.checkCar(car));
    }

    @PostMapping
    @Operation(summary = "Add an event (VHVM11B)")
    public VehicleMaintTrackingService.Result add(@RequestBody Entry e) {
        return service.add(e);
    }

    @PostMapping("/update")
    @Operation(summary = "The Update button: update an open event, close a closed one (VHVM11C, battery VHVM11J)")
    public VehicleMaintTrackingService.Result update(@RequestBody Change c) {
        return service.update(c);
    }

    @PostMapping("/close")
    @Operation(summary = "The Close button (VHVM11C, battery VHVM11J)")
    public VehicleMaintTrackingService.Result close(@RequestBody Change c) {
        return service.close(c);
    }

    @PostMapping("/remove")
    @Operation(summary = "Remove an event (VHVM11C)")
    public VehicleMaintTrackingService.Result remove(@RequestBody Change c) {
        return service.remove(c);
    }

    @PostMapping("/work-assign")
    @Operation(summary = "Create a TRK incident for the event and record it on the event (VHVM11K, VHVM11C)")
    public VehicleMaintTrackingService.Result workAssign(@RequestBody Change c, @AuthenticationPrincipal MarisUser user) {
        return service.workAssign(c, group(user));
    }
}
