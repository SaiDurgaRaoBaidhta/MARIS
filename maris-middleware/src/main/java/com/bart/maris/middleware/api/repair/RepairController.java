package com.bart.maris.middleware.api.repair;

import com.bart.maris.middleware.domain.repair.RepairService;
import com.bart.maris.middleware.domain.repair.RepairService.Screen;
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

/** Vehicle repair (old screens VHRV01I, VHRV02I, VHRV03I, VHRV04I). */
@RestController
@RequestMapping("/api/v1/repair")
@Tag(name = "Repair")
public class RepairController {
    private final RepairService service;

    public RepairController(RepairService service) {
        this.service = service;
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    @GetMapping("/options")
    @Operation(summary = "Repair cause and disposition codes (VHRV01C, VHRV01D), the repair yards and the screens' questions")
    public RepairService.Options options() {
        return service.options();
    }

    @GetMapping("/incidents/{incident}")
    @Operation(summary = "An incident and its repairs (VHINC2D, VHRV01A); screen VHRV01I, VHRV03I (events) or VHRV04I (maintenance) applies its checks")
    public RepairService.IncidentRepairs incident(@PathVariable String incident, @RequestParam(defaultValue = "VHRV01I") Screen screen,
                                                  @AuthenticationPrincipal MarisUser user) {
        return service.incident(screen, incident, group(user));
    }

    @GetMapping("/incidents/{incident}/repairs/{document}")
    @Operation(summary = "One repair and its narratives (VHRV01A, VHRV01F)")
    public RepairService.RepairDetails repair(@PathVariable String incident, @PathVariable int document) {
        return service.repair(incident, document);
    }

    @PostMapping("/incidents/{incident}/repairs")
    @Operation(summary = "Add a repair (VHRV01I Add Repr: VHRV01E, AFCXVLD, VHRV01B)")
    public RepairService.Result addRepair(@PathVariable String incident, @RequestBody RepairService.RepairInput in, @AuthenticationPrincipal MarisUser user) {
        return service.addRepair(incident, in, group(user));
    }

    @PostMapping("/incidents/{incident}/close")
    @Operation(summary = "Close the incident (VHRV01I Close Incds: VHRV01B)")
    public RepairService.Result close(@PathVariable String incident, @AuthenticationPrincipal MarisUser user) {
        return service.closeIncident(incident, group(user));
    }

    @PostMapping("/incidents/{incident}/repairs/{document}")
    @Operation(summary = "Change a repair (VHRV04I: VHRV4A1, and VHRV4A2 when the system changes)")
    public RepairService.Result change(@PathVariable String incident, @PathVariable int document, @RequestBody RepairService.RepairInput in,
                                       @AuthenticationPrincipal MarisUser user) {
        return service.changeRepair(incident, document, in, false, group(user));
    }

    @PostMapping("/incidents/{incident}/repairs/{document}/delete")
    @Operation(summary = "Delete a repair (VHRV04I: VHRV4A1 with the delete mark, then VHRV4A2)")
    public RepairService.Result delete(@PathVariable String incident, @PathVariable int document, @AuthenticationPrincipal MarisUser user) {
        return service.changeRepair(incident, document, null, true, group(user));
    }

    @PostMapping("/events/{incident}/repairs")
    @Operation(summary = "Add a repair to an event (VHRV03I Add Repr: AFCXVLD, VHRV03A)")
    public RepairService.Result addEventRepair(@PathVariable String incident, @RequestBody RepairService.EventRepairInput in,
                                               @AuthenticationPrincipal MarisUser user) {
        return service.addEventRepair(incident, in, group(user));
    }

    @PostMapping("/events/{incident}/close")
    @Operation(summary = "Close the event (VHRV03I Close Incds: VHRV03A)")
    public RepairService.Result closeEvent(@PathVariable String incident, @AuthenticationPrincipal MarisUser user) {
        return service.closeEvent(incident, group(user));
    }

    @GetMapping("/incidents/{incident}/repairs/{document}/components")
    @Operation(summary = "A repair's components (VHRV02A)")
    public RepairService.Components components(@PathVariable String incident, @PathVariable int document,
                                               @RequestParam(defaultValue = "false") boolean maintenance) {
        return service.components(incident, document, maintenance);
    }

    @PostMapping("/incidents/{incident}/repairs/{document}/components")
    @Operation(summary = "Add a component (VHRV02I Add: VHRV02F, VHRV02E, VHRV02B)")
    public RepairService.Components addComponent(@PathVariable String incident, @PathVariable int document, @RequestBody RepairService.ComponentInput in,
                                                 @RequestParam(defaultValue = "false") boolean maintenance) {
        return service.addComponent(incident, document, in, maintenance);
    }

    @PostMapping("/incidents/{incident}/repairs/{document}/components/{component}")
    @Operation(summary = "Update a component (VHRV02I Update: VHRV02F, VHRV02E, VHRV02C)")
    public RepairService.Components updateComponent(@PathVariable String incident, @PathVariable int document, @PathVariable int component,
                                                    @RequestBody RepairService.ComponentInput in, @RequestParam(defaultValue = "false") boolean maintenance) {
        return service.updateComponent(incident, document, component, in, maintenance);
    }

    @PostMapping("/incidents/{incident}/repairs/{document}/components/{component}/delete")
    @Operation(summary = "Delete a component (VHRV02I Delete, opened from VHRV04I: VHRV02D)")
    public RepairService.Components deleteComponent(@PathVariable String incident, @PathVariable int document, @PathVariable int component,
                                                    @RequestParam(defaultValue = "true") boolean maintenance) {
        return service.deleteComponent(incident, document, component, maintenance);
    }
}
