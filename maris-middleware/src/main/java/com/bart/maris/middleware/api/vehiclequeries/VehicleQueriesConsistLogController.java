package com.bart.maris.middleware.api.vehiclequeries;

import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistLogService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesDailyStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vehicle queries: the consist log (old VHCL01I with VHCL02I) and the daily vehicle status (VHDVS1I with VHDVS2I .. VHDVS4I). */
@RestController
@RequestMapping("/api/v1/vehicle-queries")
@Tag(name = "Vehicle queries")
public class VehicleQueriesConsistLogController {
    private final VehicleQueriesConsistLogService consistLog;
    private final VehicleQueriesDailyStatusService dailyStatus;

    public VehicleQueriesConsistLogController(VehicleQueriesConsistLogService consistLog, VehicleQueriesDailyStatusService dailyStatus) {
        this.consistLog = consistLog;
        this.dailyStatus = dailyStatus;
    }

    @GetMapping("/consist-log/options")
    @Operation(summary = "Today's revenue date and the printers of the consist log (VHCL01I)")
    public VehicleQueriesConsistLogService.Options consistLogOptions() {
        return consistLog.options();
    }

    @GetMapping("/consist-log")
    @Operation(summary = "The consists and their cars (VHCL01I Display, VHCL02I: VHCL01E, VHCL02B, VHCL02C); from may be ALL; revenueDate yyyy-MM-dd")
    public VehicleQueriesConsistLogService.ConsistLog consistLog(@RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to,
                                                                 @RequestParam(defaultValue = "") String revenueDate) {
        return consistLog.log(from, to, revenueDate);
    }

    public record ConsistLogPrint(String from, String to, String revenueDate, String printer) {}

    @PostMapping("/consist-log/print")
    @Operation(summary = "Print the consist log (VHCL01I Print: program VHCL011)")
    public Map<String, String> consistLogPrint(@RequestBody ConsistLogPrint body) {
        return Map.of("message", consistLog.print(body.from(), body.to(), body.revenueDate(), body.printer()));
    }

    @GetMapping("/daily-status")
    @Operation(summary = "The daily vehicle status tables (VHDVS1I, VHDVS2I, VHDVS3I); revenueDate yyyy-MM-dd = Retrieve Data for that day, none = today")
    public VehicleQueriesDailyStatusService.DailyStatus dailyStatus(@RequestParam(required = false) String revenueDate) {
        return dailyStatus.report(revenueDate);
    }

    @GetMapping("/daily-status/printers")
    @Operation(summary = "The printers of the daily vehicle status (VHDVS4I: WS70A01)")
    public List<VehicleQueriesDailyStatusService.Printer> dailyStatusPrinters() {
        return dailyStatus.printers();
    }

    public record DailyStatusPrint(String revenueDate, String printer) {}

    @PostMapping("/daily-status/print")
    @Operation(summary = "Print the daily vehicle status (VHDVS4I Hard Copy: program VHDVS04)")
    public Map<String, String> dailyStatusPrint(@RequestBody DailyStatusPrint body) {
        return Map.of("message", dailyStatus.print(body.revenueDate(), body.printer()));
    }
}
