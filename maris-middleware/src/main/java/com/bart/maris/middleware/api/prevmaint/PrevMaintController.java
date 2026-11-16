package com.bart.maris.middleware.api.prevmaint;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService.PmCriteria;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService.Report;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Preventive maintenance reports (old screens VHHPM1I, VHHPR1I, VHHPM2I, VHPMR1I, VHHPD1I, VHHPD2I). */
@RestController
@RequestMapping("/api/v1/preventive-maintenance")
@Tag(name = "Preventive maintenance")
public class PrevMaintController {
    private final PrevMaintService service;

    public PrevMaintController(PrevMaintService service) {
        this.service = service;
    }

    /** report names in URLs: hours-by-yard, mtbi, by-pm-type, hours-descending, hours-to-next */
    static Report report(String name) {
        try {
            return Report.valueOf(name.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown report " + name + ". Reports: hours-by-yard, mtbi, by-pm-type, hours-descending, hours-to-next.");
        }
    }

    @GetMapping("/options")
    @Operation(summary = "Revenue date, report start date, the reports, yards, locations, PM types and printers (VHHPMA3, VHHPMA2, WS70A01)")
    public PrevMaintService.Options options() {
        return service.options();
    }

    @GetMapping("/reports/{report}")
    @Operation(summary = "One report: hours-by-yard (VHHPRA1), mtbi (VHHPMA1), by-pm-type (VHPMRA1), hours-descending (VHHPDA1), hours-to-next (VHHPD2A)")
    public PrevMaintService.PmReport report(@PathVariable String report, @RequestParam(required = false) String homeYard,
                                            @RequestParam(required = false) String location, @RequestParam(required = false) String pmType,
                                            @RequestParam(required = false) String carFrom, @RequestParam(required = false) String carTo,
                                            @RequestParam(required = false) Integer hoursFrom, @RequestParam(required = false) Integer hoursTo) {
        return service.report(report(report), new PmCriteria(homeYard, location, pmType, carFrom, carTo, hoursFrom, hoursTo));
    }

    public record PrintRequest(String homeYard, String location, String pmType, String carFrom, String carTo, Integer hoursFrom,
                               Integer hoursTo, String printer) {}

    @PostMapping("/reports/{report}/print")
    @Operation(summary = "Print a report on a MARIS printer (program VHHPM01)")
    public Map<String, String> print(@PathVariable String report, @RequestBody PrintRequest r) {
        return Map.of("message", service.print(report(report),
                new PmCriteria(r.homeYard(), r.location(), r.pmType(), r.carFrom(), r.carTo(), r.hoursFrom(), r.hoursTo()), r.printer()));
    }
}
