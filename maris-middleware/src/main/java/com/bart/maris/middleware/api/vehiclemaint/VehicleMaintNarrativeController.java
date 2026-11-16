package com.bart.maris.middleware.api.vehiclemaint;

import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintNarrativeService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The narrative window (old VHNAR01I) of an incident or of an incident's repair document. The parent screen passes the
 * narrative it shows (see contracts/VHNAR01I.md); the narratives are written under the signed-on user group.
 */
@RestController
@RequestMapping("/api/v1/vehicle-maintenance/narratives")
@Tag(name = "Vehicle maintenance")
public class VehicleMaintNarrativeController {
    private final VehicleMaintNarrativeService service;

    public VehicleMaintNarrativeController(VehicleMaintNarrativeService service) {
        this.service = service;
    }

    @GetMapping("/opening")
    @Operation(summary = "The narrative the parent passed, as the window opens with it (CI / ACTION part apart; no backend call)")
    public VehicleMaintNarrativeService.Narrative opening(@RequestParam(required = false) String incdNo, @RequestParam(required = false) Integer reprDocNo,
                                                          @RequestParam(required = false) Integer currentRow, @RequestParam(required = false) Integer maxRows,
                                                          @RequestParam(required = false) String userGrp, @RequestParam(required = false) String dateNarr,
                                                          @RequestParam(required = false) String timeNarr, @RequestParam(required = false) String narr) {
        return service.opening(incdNo, reprDocNo, currentRow, maxRows, userGrp, dateNarr, timeNarr, narr);
    }

    @GetMapping("/step")
    @Operation(summary = "The previous or next narrative (VHXNR1A, repair VHXNR5A)")
    public VehicleMaintNarrativeService.Narrative step(@RequestParam(required = false) String incdNo, @RequestParam(required = false) Integer reprDocNo,
                                                       @RequestParam(required = false) Integer currentRow, @RequestParam(required = false) Integer maxRows,
                                                       @RequestParam String direction) {
        return service.step(incdNo, reprDocNo, currentRow, maxRows, direction);
    }

    public record AddRequest(String incdNo, Integer reprDocNo, Integer maxRows, String narrative) {}

    @PostMapping
    @Operation(summary = "Add a narrative after the last one (VHXNR2A + VHXNR6A, repair VHXNR3A + VHXNR4A)")
    public VehicleMaintNarrativeService.Added add(@RequestBody AddRequest r, @AuthenticationPrincipal MarisUser user) {
        return service.add(r.incdNo(), r.reprDocNo(), r.maxRows(), r.narrative(), user == null ? "" : user.groupId());
    }

    public record UpdateRequest(String incdNo, Integer reprDocNo, Integer currentRow, String narrative, String ciPart) {}

    @PostMapping("/update")
    @Operation(summary = "Rewrite the narrative shown, keeping its CI / ACTION part (VHXNR7A, repair VHXNR8A)")
    public Map<String, String> update(@RequestBody UpdateRequest r, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", service.update(r.incdNo(), r.reprDocNo(), r.currentRow(), r.narrative(), r.ciPart(), user == null ? "" : user.groupId()));
    }
}
