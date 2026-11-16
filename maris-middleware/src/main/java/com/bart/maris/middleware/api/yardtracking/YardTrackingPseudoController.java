package com.bart.maris.middleware.api.yardtracking;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingConsistVerifyService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingHaywardMapService;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingPseudoTrain;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingPseudoTrainService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pseudo trains: the pseudo status train (old VHYT09I) and its printer window (VHYT01I), the Hayward yard map (Vhyt07i),
 * verify / reset a pseudo train (VHYT12I).
 */
@RestController
@RequestMapping("/api/v1/yard-tracking")
@Tag(name = "Yard tracking pseudo trains")
public class YardTrackingPseudoController {
    private final YardTrackingPseudoTrainService trains;
    private final YardTrackingHaywardMapService hayward;
    private final YardTrackingConsistVerifyService verify;

    public YardTrackingPseudoController(YardTrackingPseudoTrainService trains, YardTrackingHaywardMapService hayward, YardTrackingConsistVerifyService verify) {
        this.trains = trains;
        this.hayward = hayward;
        this.verify = verify;
    }

    @GetMapping("/pseudo-trains/options")
    @Operation(summary = "VHYT09I opening: the yards (VHYT09A plus ALL), the yard shown first and the message")
    public YardTrackingPseudoTrain.Options options(@RequestParam(required = false) String yard) {
        return trains.options(yard);
    }

    @GetMapping("/pseudo-trains")
    @Operation(summary = "VHYT09I List: the yard's pseudo trains with their cars (VHYT09B, VHYT09C)")
    public YardTrackingPseudoTrain.Report consists(@RequestParam String yard) {
        return trains.consists(yard);
    }

    @GetMapping("/pseudo-trains/printers")
    @Operation(summary = "VHYT01I: the printers (WS70A01)")
    public List<YardTrackingPseudoTrain.Printer> printers() {
        return trains.printers();
    }

    public record PrintRequest(String yard, String printer) {}

    @PostMapping("/pseudo-trains/print")
    @Operation(summary = "VHYT01I Hard Copy: print the yard's pseudo status trains (VHYT01A)")
    public ResponseEntity<Map<String, Boolean>> print(@RequestBody PrintRequest r) {
        trains.print(r.yard(), r.printer());
        return ResponseEntity.ok(Map.of("submitted", true));
    }

    @GetMapping("/hayward-yard-map")
    @Operation(summary = "Vhyt07i: the cars on the Hayward yard map (VHYT07A) and what its buttons open")
    public YardTrackingHaywardMapService.YardMap haywardMap() {
        return hayward.map();
    }

    /** what the car move window passed (VHYT12IW-PASSVALUE), the train number typed and the answers to the prompts */
    public record VerifyRequest(String yard, String track, Integer fromPosition, Integer toPosition, String consist, Map<String, String> answers) {
        YardTrackingConsistVerifyService.Pass pass(MarisUser user) {
            return new YardTrackingConsistVerifyService.Pass(yard, track, fromPosition == null ? 0 : fromPosition, toPosition == null ? 0 : toPosition,
                    user == null ? "" : user.groupId(), null);
        }
    }

    @PostMapping("/consist-verify/open")
    @Operation(summary = "VHYT12I opening: the cars picked (VHYT12A, VHYT12C) and their checks; answers: carsInConsist")
    public YardTrackingConsistVerifyService.State open(@RequestBody VerifyRequest r, @AuthenticationPrincipal MarisUser user) {
        return verify.open(r.pass(user), r.answers());
    }

    @PostMapping("/consist-verify/verify")
    @Operation(summary = "VHYT12I Verify: check the train number (VHYT12B) and write the train (VHYT12D); answers: carsInConsist, override")
    public YardTrackingConsistVerifyService.State verify(@RequestBody VerifyRequest r, @AuthenticationPrincipal MarisUser user) {
        return verify.verify(r.pass(user), r.consist(), r.answers());
    }

    @PostMapping("/consist-verify/reset")
    @Operation(summary = "VHYT12I Reset: take the cars off their train (VHYT12E); answers: carsInConsist")
    public YardTrackingConsistVerifyService.State reset(@RequestBody VerifyRequest r, @AuthenticationPrincipal MarisUser user) {
        return verify.reset(r.pass(user), r.answers());
    }
}
