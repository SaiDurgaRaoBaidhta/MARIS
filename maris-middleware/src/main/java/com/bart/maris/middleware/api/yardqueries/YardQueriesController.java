package com.bart.maris.middleware.api.yardqueries;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatus;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yard tracking queries (old screens VHYT25I, ...). */
@RestController
@RequestMapping("/api/v1/yard-queries")
@Tag(name = "Yard tracking queries")
public class YardQueriesController {
    private final YardQueriesShopStatusService shopStatus;

    public YardQueriesController(YardQueriesShopStatusService shopStatus) {
        this.shopStatus = shopStatus;
    }

    @GetMapping("/shop-status/{yard}")
    @Operation(summary = "A yard's shop status: cars in the shop and their time there, cars out of the yard (old VHYT25I; VHYT25A, VHYT30A)")
    public YardQueriesShopStatus shopStatus(@PathVariable String yard) {
        return shopStatus.report(yard);
    }
}
