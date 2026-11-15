package com.bart.maris.middleware.api.secondaryrepair;

import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairService;
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

/** Secondary repair, read-only (old screens VHSR05I, VHSR10I, VHSR11I, VHSR15I). */
@RestController
@RequestMapping("/api/v1/secondary-repair")
@Tag(name = "Secondary repair")
public class SecondaryRepairController {
    private final SecondaryRepairService service;

    public SecondaryRepairController(SecondaryRepairService service) {
        this.service = service;
    }

    @GetMapping("/stock-inquiry/options")
    @Operation(summary = "Sub job statuses (VHSR05C) and long term storage choices")
    public SecondaryRepairService.StockOptions stockOptions() {
        return service.stockOptions();
    }

    @GetMapping("/stock-inquiry")
    @Operation(summary = "Jobs of a stock number (VHSR05B, VHSR05A)")
    public SecondaryRepairService.StockInquiry stockInquiry(@RequestParam(required = false) String stockNo, @RequestParam(required = false) String serialNo,
                                                            @RequestParam(required = false) String status, @RequestParam(required = false) String longTermStorage) {
        return service.stockInquiry(stockNo, serialNo, status, longTermStorage);
    }

    @GetMapping("/jobs/{jobNo}")
    @Operation(summary = "An open job and its sub jobs (VHSR11A; closed jobs are not listed)")
    public SecondaryRepairService.Job openJob(@PathVariable String jobNo) {
        return service.openJob(jobNo);
    }

    @GetMapping("/jobs/{jobNo}/history")
    @Operation(summary = "Any job and its sub jobs, closed ones too (VHSR11A)")
    public SecondaryRepairService.Job jobHistory(@PathVariable String jobNo) {
        return service.jobHistory(jobNo);
    }

    @GetMapping("/jobs/{jobNo}/sub-jobs/{doc}")
    @Operation(summary = "A sub job's narrative, repair narrative, repairs and labor (VHSRJNX, VHSRJRN, VHSR09A, VHSR10A)")
    public SecondaryRepairService.SubJobDetails subJob(@PathVariable String jobNo, @PathVariable int doc) {
        return service.subJob(jobNo, doc);
    }

    @GetMapping("/reprint/options")
    @Operation(summary = "Printers and the user group's default printer (VHSR15C)")
    public SecondaryRepairService.ReprintOptions reprintOptions(@AuthenticationPrincipal MarisUser user) {
        return service.reprintOptions(user == null ? "" : user.groupId());
    }

    public record ReprintRequest(Integer doc, String printer) {}

    @PostMapping("/jobs/{jobNo}/reprint")
    @Operation(summary = "Reprint a job form (VHSR15A, VHSR15B)")
    public Map<String, String> reprint(@PathVariable String jobNo, @RequestBody ReprintRequest r) {
        return Map.of("message", service.reprint(jobNo, r.doc(), r.printer()));
    }
}
