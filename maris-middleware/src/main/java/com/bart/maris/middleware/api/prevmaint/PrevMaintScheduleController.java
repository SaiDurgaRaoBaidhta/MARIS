package com.bart.maris.middleware.api.prevmaint;

import com.bart.maris.middleware.domain.prevmaint.PrevMaintScheduleService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Schedule or reschedule a car's PM (old screen VHPMS01I). */
@RestController
@RequestMapping("/api/v1/preventive-maintenance/schedule")
@Tag(name = "Preventive maintenance")
public class PrevMaintScheduleController {
    private final PrevMaintScheduleService service;

    public PrevMaintScheduleController(PrevMaintScheduleService service) {
        this.service = service;
    }

    @GetMapping("/options")
    @Operation(summary = "The two choices (schedule, reschedule) and the PM types the screen accepts")
    public PrevMaintScheduleService.Options options() {
        return service.options();
    }

    @GetMapping("/description")
    @Operation(summary = "The PM type's description for the car's type and home yard (VHPMSA2, VHPMSA1)")
    public PrevMaintScheduleService.Description description(@RequestParam(required = false) String car, @RequestParam(required = false) String pmType) {
        return service.describe(car, pmType);
    }

    /** @param option 1 schedule, 2 reschedule */
    public record SaveRequest(String car, String pmType, Integer option) {}

    @PostMapping
    @Operation(summary = "Schedule (1) or reschedule (2) the PM: checks (VHPMSA3), revenue date (VHXREVA), update (VHPMS)")
    public PrevMaintScheduleService.Saved save(@RequestBody SaveRequest r, @AuthenticationPrincipal MarisUser user) {
        return service.save(r.car(), r.pmType(), r.option() == null ? 1 : r.option(), user == null ? "" : user.groupId());
    }
}
