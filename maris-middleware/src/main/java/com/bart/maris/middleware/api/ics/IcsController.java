package com.bart.maris.middleware.api.ics;

import com.bart.maris.middleware.domain.ics.IcsCriteria;
import com.bart.maris.middleware.domain.ics.IcsOptions;
import com.bart.maris.middleware.domain.ics.IcsReport;
import com.bart.maris.middleware.domain.ics.IcsReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ICS reports (old screens VHIC30I criteria and VHIC31I report). */
@RestController
@RequestMapping("/api/v1/ics")
@Tag(name = "ICS reports")
public class IcsController {
    private final IcsReportService service;

    public IcsController(IcsReportService service) {
        this.service = service;
    }

    @GetMapping("/options")
    @Operation(summary = "Printers, revenue date and the yard / location / message code lists (programs WS70A01, VHIC30A, VHRT04A, VHIC30B, VHIC30C)")
    public IcsOptions options() {
        return service.options();
    }

    @GetMapping("/messages")
    @Operation(summary = "ICS / MARIS messages received on a revenue date (program VHIC31A, at most 150 rows)")
    public IcsReport messages(@RequestParam(required = false) String revenueDate, @RequestParam(required = false) String order,
                              @RequestParam(required = false) String groupBy, @RequestParam(required = false) String yard,
                              @RequestParam(required = false) String location, @RequestParam(required = false) String messageCode) {
        return service.messages(new IcsCriteria(revenueDate, order, groupBy, yard, location, messageCode));
    }

    public record PrintRequest(String revenueDate, String order, String groupBy, String yard, String location, String messageCode,
                               String printer) {}

    @PostMapping("/messages/print")
    @Operation(summary = "Print the report on a MARIS printer (program VHIC30E)")
    public Map<String, String> print(@RequestBody PrintRequest r) {
        return Map.of("message", service.print(new IcsCriteria(r.revenueDate(), r.order(), r.groupBy(), r.yard(), r.location(), r.messageCode()), r.printer()));
    }
}
