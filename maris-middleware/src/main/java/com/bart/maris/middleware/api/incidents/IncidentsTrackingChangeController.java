package com.bart.maris.middleware.api.incidents;

import com.bart.maris.middleware.domain.incidents.IncidentsNeverGoService;
import com.bart.maris.middleware.domain.incidents.IncidentsTrackingChangeService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle tracking change incident (old screens VHCI02I and VHCI03I "never go"). */
@RestController
@RequestMapping("/api/v1/incidents/tracking-change")
@Tag(name = "Incidents")
public class IncidentsTrackingChangeController {
    private final IncidentsNeverGoService neverGo;
    private final IncidentsTrackingChangeService change;

    public IncidentsTrackingChangeController(IncidentsNeverGoService neverGo, IncidentsTrackingChangeService change) {
        this.neverGo = neverGo;
        this.change = change;
    }

    @GetMapping("/options")
    @Operation(summary = "VHCI02I: the backend's date and time, the actions and statuses, the status picked for the group, and the location right (VHCI02G, VHCI02B)")
    public IncidentsTrackingChangeService.Options options(@AuthenticationPrincipal MarisUser user) {
        return change.options(group(user));
    }

    @PostMapping("/incidents")
    @Operation(summary = "VHCI02I List Incds: a car's incidents for an action (VHINC6A, VHCI01B, VHCI01D or VHCI01A; DI/UD/ERR/DF also reset a pseudo consist through VHINXA/VHINXB)")
    public IncidentsTrackingChangeService.Listing list(@RequestBody IncidentsTrackingChangeService.ListRequest r, @AuthenticationPrincipal MarisUser user) {
        return change.list(r, group(user));
    }

    @PostMapping("/changes")
    @Operation(summary = "VHCI02I Change: apply the action to the marked incidents (VHCIP..VHCIZ, VHCIW, VHINVB, VHCI01E, VHCI02D, VHINXD ...)")
    public IncidentsTrackingChangeService.Result apply(@RequestBody IncidentsTrackingChangeService.ChangeRequest r, @AuthenticationPrincipal MarisUser user) {
        return change.change(r, group(user));
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    @GetMapping("/never-go/options")
    @Operation(summary = "The backend's date and time, the never-go statuses, and whether the group may change locations (VHCI02G, VHCI03E, VHCI02B)")
    public IncidentsNeverGoService.Options neverGoOptions(@AuthenticationPrincipal MarisUser user) {
        return neverGo.options(group(user));
    }

    @PostMapping("/never-go")
    @Operation(summary = "Change a car to a never-go status and open its incident (VHCI01B, AFCXVLD, VHINVB, VHINVA, VHCIV, VHINXA/B, VHCI01E, VHINXD)")
    public IncidentsNeverGoService.Result neverGo(@RequestBody IncidentsNeverGoService.Request r, @AuthenticationPrincipal MarisUser user) {
        return neverGo.change(r, group(user));
    }
}
