package com.bart.maris.middleware.api.configqueries;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponents;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration tracking queries (old screen VHCF16I config component inquiry). */
@RestController
@RequestMapping("/api/v1/config-queries")
@Tag(name = "Configuration tracking queries")
public class ConfigQueriesController {
    private final ConfigQueriesComponentsService components;

    public ConfigQueriesController(ConfigQueriesComponentsService components) {
        this.components = components;
    }

    @GetMapping("/component-options")
    @Operation(summary = "Equipment types, stock numbers, serial statuses and home yards to search components by (old VHCF16I; VHCF16B, C, D)")
    public ConfigQueriesComponents.Options componentOptions() {
        return components.options();
    }

    @GetMapping("/components")
    @Operation(summary = "Components by stock (12-34-56789), serial, status, home yard, equipment type; sort 1 or 2 (old VHCF16I; VHCF16A)")
    public ConfigQueriesComponents.Result components(@RequestParam(required = false) String stock, @RequestParam(required = false) String serial,
                                                     @RequestParam(required = false) String status, @RequestParam(required = false) String homeYard,
                                                     @RequestParam(required = false) String equipmentType, @RequestParam(required = false) String sort) {
        return components.search(stock, serial, status, homeYard, equipmentType, sort);
    }
}
