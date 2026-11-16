package com.bart.maris.middleware.api.yardqueries;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesHistory;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesHistoryService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesReports;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesReportsService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatus;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yard tracking queries (old screens VHYT27I reports criteria, VHYT25I shop status, VHYT29I car history, VHYT33I status history). */
@RestController
@RequestMapping("/api/v1/yard-queries")
@Tag(name = "Yard tracking queries")
public class YardQueriesController {
    private final YardQueriesShopStatusService shopStatus;
    private final YardQueriesHistoryService history;
    private final YardQueriesReportsService reports;

    public YardQueriesController(YardQueriesShopStatusService shopStatus, YardQueriesHistoryService history, YardQueriesReportsService reports) {
        this.shopStatus = shopStatus;
        this.history = history;
        this.reports = reports;
    }

    @GetMapping("/report-options")
    @Operation(summary = "The reports criteria window's printers, yards and proposed dates (old VHYT27I; WS70A01, VHYT27B, VHRT04A)")
    public YardQueriesReports.Options reportOptions() {
        return reports.options();
    }

    @PostMapping("/reports/print")
    @Operation(summary = "Print a yard report on a MARIS printer (old VHYT27I Hard Copy; VHYT27A / F / C / D / E / G)")
    public Map<String, String> printReport(@RequestBody YardQueriesReports.Print r) {
        return Map.of("message", reports.print(r));
    }

    @GetMapping("/car-history")
    @Operation(summary = "Car location history for a yard, dates (yyyy-MM-dd) and car range (old VHYT29I, program VHYT29A)")
    public YardQueriesHistory.CarHistory carHistory(@RequestParam(required = false) String yard, @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to, @RequestParam(required = false) String fromCar,
                                                    @RequestParam(required = false) String toCar) {
        return history.carHistory(yard, from, to, fromCar, toCar);
    }

    @GetMapping("/status-history")
    @Operation(summary = "Yard vehicle status history for a yard and dates (yyyy-MM-dd) (old VHYT33I, program VHYT33A)")
    public YardQueriesHistory.StatusHistory statusHistory(@RequestParam(required = false) String yard, @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to) {
        return history.statusHistory(yard, from, to);
    }

    @GetMapping("/shop-status/{yard}")
    @Operation(summary = "A yard's shop status: cars in the shop and their time there, cars out of the yard (old VHYT25I; VHYT25A, VHYT30A)")
    public YardQueriesShopStatus shopStatus(@PathVariable String yard) {
        return shopStatus.report(yard);
    }
}
