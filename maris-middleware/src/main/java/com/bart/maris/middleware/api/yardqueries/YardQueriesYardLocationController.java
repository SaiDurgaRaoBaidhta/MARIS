package com.bart.maris.middleware.api.yardqueries;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfServiceService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocation;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocationService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The yard location status report (old VHYT26I) and the reason out of service report (old VHYT32I), opened from VHYT27I. */
@RestController
@RequestMapping("/api/v1/yard-queries")
@Tag(name = "Yard tracking queries")
public class YardQueriesYardLocationController {
    private final YardQueriesYardLocationService yardLocation;
    private final YardQueriesOutOfServiceService outOfService;

    public YardQueriesYardLocationController(YardQueriesYardLocationService yardLocation, YardQueriesOutOfServiceService outOfService) {
        this.yardLocation = yardLocation;
        this.outOfService = outOfService;
    }

    @GetMapping("/yard-location")
    @Operation(summary = "Yard location status report for a yard (old VHYT26I; VHYT26A, VHYT30A, VHYT26C)")
    public YardQueriesYardLocation yardLocation(@RequestParam(required = false) String yard) {
        return yardLocation.report(yard);
    }

    @GetMapping("/out-of-service")
    @Operation(summary = "Reason out of service report, detail or summary, for a yard and dates yyyy-MM-dd (old VHYT32I; VHYT32A, VHYT32B, VHYT32C, VHYT32D)")
    public YardQueriesOutOfService outOfService(@RequestParam(required = false) String yard, @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to, @RequestParam(defaultValue = "false") boolean summary,
                                                @AuthenticationPrincipal MarisUser user) {
        return outOfService.report(yard, from, to, summary, user == null ? "" : user.groupId());
    }
}
