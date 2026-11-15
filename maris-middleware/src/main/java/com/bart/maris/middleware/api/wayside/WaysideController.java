package com.bart.maris.middleware.api.wayside;

import com.bart.maris.middleware.domain.wayside.WaysideNarrativeService;
import com.bart.maris.middleware.domain.wayside.WaysideNarrativeService.Context;
import com.bart.maris.middleware.domain.wayside.WaysideStockService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Wayside maintenance (old screens WS13I, WS17I). */
@RestController
@RequestMapping("/api/v1/wayside")
@Tag(name = "Wayside")
public class WaysideController {
    private final WaysideStockService stock;
    private final WaysideNarrativeService narratives;

    public WaysideController(WaysideStockService stock, WaysideNarrativeService narratives) {
        this.stock = stock;
        this.narratives = narratives;
    }

    @GetMapping("/stock-numbers")
    @Operation(summary = "Search stock numbers by description (up to 10 characters) and department (old WS17I, program WS17A05)")
    public WaysideStockService.StockSearch stockNumbers(@RequestParam(required = false) String description,
                                                        @RequestParam(required = false) String dept) {
        return stock.search(description, dept);
    }

    private static Context context(String incident, Integer repairDoc, String pmDept, String pmEquipment, String pmLocation, String pmCycle, String pmScheduled) {
        return new Context(incident, repairDoc == null ? 0 : repairDoc, pmDept, pmEquipment, pmLocation, pmCycle, pmScheduled);
    }

    @GetMapping("/narratives/latest")
    @Operation(summary = "The latest narrative of a repair (WS13A08), an incident (WSX2A01) or a PM (WSX3A) (old WS13I)")
    public WaysideNarrativeService.Narrative latest(@RequestParam(required = false) String incident, @RequestParam(required = false) Integer repairDoc,
                                                    @RequestParam(required = false) String pmDept, @RequestParam(required = false) String pmEquipment,
                                                    @RequestParam(required = false) String pmLocation, @RequestParam(required = false) String pmCycle,
                                                    @RequestParam(required = false) String pmScheduled) {
        return narratives.latest(context(incident, repairDoc, pmDept, pmEquipment, pmLocation, pmCycle, pmScheduled));
    }

    @GetMapping("/narratives/step")
    @Operation(summary = "Prv / Nxt from the row shown (WS13A06 repair, WS13A02 incident, WS13A01 PM)")
    public WaysideNarrativeService.Narrative step(@RequestParam String direction, @RequestParam int row, @RequestParam int maxRows,
                                                  @RequestParam(required = false) String incident, @RequestParam(required = false) Integer repairDoc,
                                                  @RequestParam(required = false) String pmDept, @RequestParam(required = false) String pmEquipment,
                                                  @RequestParam(required = false) String pmLocation, @RequestParam(required = false) String pmCycle,
                                                  @RequestParam(required = false) String pmScheduled) {
        return narratives.step(context(incident, repairDoc, pmDept, pmEquipment, pmLocation, pmCycle, pmScheduled), direction, row, maxRows);
    }

    public record NarrativeRequest(String incident, Integer repairDoc, String pmDept, String pmEquipment, String pmLocation, String pmCycle,
                                   String pmScheduled, String text) {}

    @PostMapping("/narratives")
    @Operation(summary = "Add the next narrative (WS13A07 repair, WS13A04 incident, WS13A05 PM), answer the latest")
    public WaysideNarrativeService.Narrative add(@RequestBody NarrativeRequest r, @AuthenticationPrincipal MarisUser user) {
        return narratives.add(context(r.incident(), r.repairDoc(), r.pmDept(), r.pmEquipment(), r.pmLocation(), r.pmCycle(), r.pmScheduled()), r.text(),
                user == null ? "" : user.groupId());
    }
}
