package com.bart.maris.middleware.api.yardreports;

import com.bart.maris.middleware.domain.yardreports.YardReportsMap;
import com.bart.maris.middleware.domain.yardreports.YardReportsService;
import com.bart.maris.middleware.domain.yardreports.YardReportsSystemWide;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yard maps (old screens VHIC01 / VHIC02 Berryessa, VHYT80I / 81I Millbrae, VHYT105I / 106I, 70I / 71I, 90I / 91I, VHSW01I). */
@RestController
@RequestMapping("/api/v1/yard-reports")
@Tag(name = "Yard reports")
public class YardReportsController {
    private final YardReportsService service;

    public YardReportsController(YardReportsService service) {
        this.service = service;
    }

    @GetMapping("/system-wide")
    @Operation(summary = "The BART system-wide map: the places it marks and the cars at each (program VHSW01A)")
    public YardReportsSystemWide systemWide() {
        return service.systemWide();
    }

    @GetMapping("/maps/{map}")
    @Operation(summary = "The cars on a yard map's tracks (berryessa VHIC01A, millbrae VHYT80A, daly-city-yard VHYTX5A, concord-yard VHYT70A, richmond-yard VHYT90A)")
    public YardReportsMap map(@PathVariable String map) {
        return service.map(map);
    }
}
