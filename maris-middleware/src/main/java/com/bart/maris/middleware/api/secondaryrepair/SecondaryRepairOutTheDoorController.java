package com.bart.maris.middleware.api.secondaryrepair;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.config.RequestIdFilter;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairOutTheDoorService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A secondary repair sub job logged out the door (old VHSR04I). The terminal recorded is this system's
 * (maris.engine.terminal-id); the user group is the signed-on group.
 */
@RestController
@RequestMapping("/api/v1/secondary-repair/out-the-door")
@Tag(name = "Secondary repair")
public class SecondaryRepairOutTheDoorController {
    private final SecondaryRepairOutTheDoorService service;
    private final String terminal;

    public SecondaryRepairOutTheDoorController(SecondaryRepairOutTheDoorService service, MarisProperties props) {
        this.service = service;
        this.terminal = props.engine().terminalId();
    }

    @GetMapping("/options")
    @Operation(summary = "The diagnostic work types (VHSRWT) and fault codes (VHSR04A), and the doc number the screen starts with")
    public SecondaryRepairOutTheDoorService.Options options() {
        return service.options();
    }

    @GetMapping("/sub-job")
    @Operation(summary = "A sub job by job and doc number (VHSR02E); only an open one can go out")
    public SecondaryRepairOutTheDoorService.SubJob subJob(@RequestParam(required = false) String jobNo, @RequestParam(required = false) Integer doc) {
        return service.subJob(jobNo, doc);
    }

    @PostMapping
    @Operation(summary = "Log the sub job out the door (AFCXVLD, then VHSR04B, which writes)")
    public SecondaryRepairOutTheDoorService.Result goOut(@RequestBody SecondaryRepairOutTheDoorService.OutTheDoor request, @AuthenticationPrincipal MarisUser user) {
        return service.goOut(request, user == null ? "" : user.groupId(), terminal);
    }

    /** the old screen joined its messages into one line: a 400 like every input error, with each message too */
    @ExceptionHandler(SecondaryRepairOutTheDoorService.Rejected.class)
    ProblemDetail rejected(SecondaryRepairOutTheDoorService.Rejected e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setProperty("code", "INVALID_INPUT");
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        p.setProperty("errors", e.errors());
        return p;
    }
}
