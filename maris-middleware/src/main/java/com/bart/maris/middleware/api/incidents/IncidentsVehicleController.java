package com.bart.maris.middleware.api.incidents;

import com.bart.maris.middleware.domain.incidents.IncidentsVehicleCreateService;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleMaintainService;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleSupport;
import com.bart.maris.middleware.domain.incidents.IncidentsVehicleUpdateService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle incidents of one car: filing one (old VHINC08I), changing one (VHINC09I) and maintaining any incident
 * (VHINC10I). Narratives are added in the narrative window (VHNAR01I, /api/v1/vehicle-maintenance/narratives); the
 * pages read the incident's narratives again afterwards.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents")
public class IncidentsVehicleController {
    private final IncidentsVehicleCreateService create;
    private final IncidentsVehicleUpdateService update;
    private final IncidentsVehicleMaintainService maintain;

    public IncidentsVehicleController(IncidentsVehicleCreateService create, IncidentsVehicleUpdateService update, IncidentsVehicleMaintainService maintain) {
        this.create = create;
        this.update = update;
        this.maintain = maintain;
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    @GetMapping("/vehicle-incident/options")
    @Operation(summary = "VHINC08I as it opens for a type (YRD, SHP, REV): title, date (VHCI02G), discovered durations (VHINCXA)")
    public IncidentsVehicleCreateService.Options options(@RequestParam String type, @AuthenticationPrincipal MarisUser user) {
        return create.options(type, group(user));
    }

    @PostMapping("/vehicle-incident")
    @Operation(summary = "File a vehicle incident (VHINC08I Create: checks, VHINXA/VHINXB, VHINVB, VHINCY, VHINXC, VHINC3A)")
    public IncidentsVehicleCreateService.Created file(@RequestBody IncidentsVehicleCreateService.Request r, @AuthenticationPrincipal MarisUser user) {
        return create.create(r, group(user));
    }

    @GetMapping("/vehicle-incident/{incident}/narratives")
    @Operation(summary = "An incident's narratives (VHINC1D NARR), read again after the narrative window closes")
    public List<IncidentsVehicleSupport.Narrative> narratives(@PathVariable String incident) {
        return create.narratives(incident);
    }

    @GetMapping("/vehicle-update/{incident}")
    @Operation(summary = "VHINC09I List: the incident and what may change (VHINC1D BOTH, VHINC9A)")
    public IncidentsVehicleUpdateService.Incident updatable(@PathVariable String incident) {
        return update.incident(incident);
    }

    @PostMapping("/vehicle-update/{incident}")
    @Operation(summary = "VHINC09I Update (VHINC1A, VHINC1C)")
    public Map<String, String> change(@PathVariable String incident, @RequestBody IncidentsVehicleUpdateService.Change c) {
        return update.update(incident, c);
    }

    @GetMapping("/vehicle-maintenance/options")
    @Operation(summary = "VHINC10I as it opens: the incident statuses (VH01A01), discovered durations, revenue date (VHXREVA)")
    public IncidentsVehicleMaintainService.Options maintenanceOptions(@AuthenticationPrincipal MarisUser user) {
        return maintain.options(group(user));
    }

    @GetMapping("/vehicle-maintenance/{incident}")
    @Operation(summary = "VHINC10I List (VHINC1D BOTH, VHINC9A)")
    public IncidentsVehicleSupport.Details maintained(@PathVariable String incident) {
        return maintain.incident(incident);
    }

    @PostMapping("/vehicle-maintenance/{incident}")
    @Operation(summary = "VHINC10I Update (VHINCXC)")
    public Map<String, String> maintain(@PathVariable String incident, @RequestBody IncidentsVehicleMaintainService.Change c) {
        return maintain.update(incident, c);
    }
}
