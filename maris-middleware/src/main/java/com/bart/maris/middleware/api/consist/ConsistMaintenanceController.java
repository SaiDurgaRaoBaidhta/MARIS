package com.bart.maris.middleware.api.consist;

import com.bart.maris.middleware.domain.consist.ConsistCarLocationService;
import com.bart.maris.middleware.domain.consist.ConsistMaintenanceService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consist maintenance (old VHMT01I make train, VHCM01I combine, VHCT01I clear train, VHCC01I change consist, VHTS01I train
 * swap, VHSC01I split, VHCY01I move a car). A question the old screen asks comes back as {@code question}; send the call
 * again with {@code answers: {<question name>: true|false}}.
 */
@RestController
@RequestMapping("/api/v1/consist")
@Tag(name = "Consist maintenance")
public class ConsistMaintenanceController {
    private final ConsistMaintenanceService consist;
    private final ConsistCarLocationService carLocation;

    public ConsistMaintenanceController(ConsistMaintenanceService consist, ConsistCarLocationService carLocation) {
        this.consist = consist;
        this.carLocation = carLocation;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    public record ConsistRequest(String consist) {}

    public record MakeTrainRequest(String consist, String date, String time, List<String> cars, Map<String, Boolean> answers) {}

    public record CombineRequest(String combineTrain, String train, String beforeAfter, String newTrain, String time, String location,
                                 Map<String, Boolean> answers) {}

    public record ClearRequest(String consist, String time, String yard, String madeInError, Map<String, Boolean> answers) {}

    public record ChangeRequest(String consist, List<String> newCars, Map<String, Boolean> answers) {}

    public record SwapRequest(List<List<String>> pairs, boolean update, Map<String, Boolean> answers) {}

    public record SplitRequest(String consist, int atCar, String leftConsist, String rightConsist, String splitTime, String splitLocation,
                               Map<String, Boolean> answers) {}

    public record MoveRequest(String location) {}

    @PostMapping("/make-train/check")
    @Operation(summary = "Make train: the consist typed (VHMT01IS-YT-CON)")
    public ConsistMaintenanceService.Result makeTrainCheck(@RequestBody ConsistRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.makeTrainCheck(group(u), r.consist());
    }

    @PostMapping("/make-train")
    @Operation(summary = "Make train: Create (old VHMT01I; VHMT011..016)")
    public ConsistMaintenanceService.Result makeTrain(@RequestBody MakeTrainRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.makeTrain(group(u), r.consist(), r.date(), r.time(), r.cars(), r.answers());
    }

    @PostMapping("/combine/list")
    @Operation(summary = "Combine trains: the two trains' cars (VHC01IS-COM-TRAIN, VHCM01IS-LIST)")
    public ConsistMaintenanceService.Result combineList(@RequestBody CombineRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.combineList(group(u), r.combineTrain(), r.train(), r.beforeAfter());
    }

    @PostMapping("/combine")
    @Operation(summary = "Combine trains: Combine (old VHCM01I; VHCM011..018)")
    public ConsistMaintenanceService.Result combine(@RequestBody CombineRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.combine(group(u), r.combineTrain(), r.train(), r.beforeAfter(), r.newTrain(), r.time(), r.location(), r.answers());
    }

    @GetMapping("/clear-train")
    @Operation(summary = "Clear train: the screen as it opens (VHCT01IS-INIT)")
    public ConsistMaintenanceService.Result clearStart(@AuthenticationPrincipal MarisUser u) {
        return consist.clearStart(group(u));
    }

    @PostMapping("/clear-train")
    @Operation(summary = "Clear train: Clear Train (old VHCT01I; VHCT011..019, VHCT01A)")
    public ConsistMaintenanceService.Result clear(@RequestBody ClearRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.clear(group(u), r.consist(), r.time(), r.yard(), r.madeInError(), r.answers());
    }

    @PostMapping("/change/list")
    @Operation(summary = "Change consist: the consist's cars (VHCC01IS-TEXT)")
    public ConsistMaintenanceService.Result changeList(@RequestBody ConsistRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.changeList(group(u), r.consist());
    }

    @PostMapping("/change")
    @Operation(summary = "Change consist: Update with new cars (old VHCC01I; VHCC011..017)")
    public ConsistMaintenanceService.Result change(@RequestBody ChangeRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.change(group(u), r.consist(), r.newCars(), r.answers());
    }

    @PostMapping("/swap")
    @Operation(summary = "Train swap: pairs added to the swap list, then (update=true) Update (old VHTS01I)")
    public ConsistMaintenanceService.Result swap(@RequestBody SwapRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.swap(group(u), r.pairs(), r.update(), r.answers());
    }

    @PostMapping("/split/list")
    @Operation(summary = "Split consist: the consist's cars (VHSC01IS-ORG-CON)")
    public ConsistMaintenanceService.Result splitList(@RequestBody ConsistRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.splitList(group(u), r.consist());
    }

    @PostMapping("/split")
    @Operation(summary = "Split consist: Update at the chosen car (old VHSC01I)")
    public ConsistMaintenanceService.Result split(@RequestBody SplitRequest r, @AuthenticationPrincipal MarisUser u) {
        return consist.split(group(u), r.consist(), r.atCar(), r.leftConsist(), r.rightConsist(), r.splitTime(), r.splitLocation(), r.answers());
    }

    @GetMapping("/car-location/{car}")
    @Operation(summary = "Move a car: where it is (old VHCY01I; VHCY1A4)")
    public ConsistCarLocationService.CarLocation car(@PathVariable String car, @AuthenticationPrincipal MarisUser u) {
        return carLocation.car(group(u), car);
    }

    @PostMapping("/car-location/{car}/move")
    @Operation(summary = "Move a car to a new location (old VHCY01I; VHCY1A1/2, VHVM1A2, VHCY1A3)")
    public ConsistCarLocationService.Move move(@PathVariable String car, @RequestBody MoveRequest r, @AuthenticationPrincipal MarisUser u) {
        return carLocation.move(group(u), car, r.location());
    }
}
