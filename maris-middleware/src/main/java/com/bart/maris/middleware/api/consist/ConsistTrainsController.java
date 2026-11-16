package com.bart.maris.middleware.api.consist;

import com.bart.maris.middleware.domain.consist.ConsistLayUpService;
import com.bart.maris.middleware.domain.consist.ConsistReconfigureService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lay up a train (old VHLT01I) and reconfigure a train (old VHTR01I with its confirmation VHTR02I). */
@RestController
@RequestMapping("/api/v1/consist")
@Tag(name = "Consist: lay-up and reconfigure")
public class ConsistTrainsController {
    private final ConsistLayUpService layUp;
    private final ConsistReconfigureService reconfigure;

    public ConsistTrainsController(ConsistLayUpService layUp, ConsistReconfigureService reconfigure) {
        this.layUp = layUp;
        this.reconfigure = reconfigure;
    }

    private static String group(MarisUser u) {
        return u == null ? "" : u.groupId();
    }

    @GetMapping("/lay-up/options")
    @Operation(summary = "VHLT01I opening: the yards, the user group's yard and the time")
    public ConsistLayUpService.Options layUpOptions(@AuthenticationPrincipal MarisUser user) {
        return layUp.options(group(user));
    }

    @GetMapping("/lay-up/tracks")
    @Operation(summary = "VHLT01I: a yard's transfer / storage tracks")
    public List<String> layUpTracks(@RequestParam String yard, @AuthenticationPrincipal MarisUser user) {
        return layUp.tracks(group(user), yard);
    }

    @GetMapping("/lay-up/trains/{consist}")
    @Operation(summary = "VHLT01I train check: may the train be laid up")
    public ConsistLayUpService.Train layUpTrain(@PathVariable String consist, @AuthenticationPrincipal MarisUser user) {
        return layUp.train(group(user), consist);
    }

    public record LayUpRequest(String yard, String track, String consist, String time) {}

    @PostMapping("/lay-up")
    @Operation(summary = "VHLT01I Lay-up Train (writes)")
    public ConsistLayUpService.Result layUp(@RequestBody LayUpRequest r, @AuthenticationPrincipal MarisUser user) {
        return layUp.layUp(group(user), r.yard(), r.track(), r.consist(), r.time());
    }

    @GetMapping("/reconfigure/options")
    @Operation(summary = "VHTR01I opening: the yards, the time and the split buttons' car positions")
    public ConsistReconfigureService.Options reconfigureOptions(@AuthenticationPrincipal MarisUser user) {
        return reconfigure.options(group(user));
    }

    @GetMapping("/reconfigure/tracks")
    @Operation(summary = "VHTR01I: a yard's transfer / storage tracks")
    public List<String> reconfigureTracks(@RequestParam String yard, @AuthenticationPrincipal MarisUser user) {
        return reconfigure.tracks(group(user), yard);
    }

    @PostMapping("/reconfigure/state")
    @Operation(summary = "VHTR01I after the user's steps: the trains, the message and the enabled buttons")
    public ConsistReconfigureService.State reconfigureState(@RequestBody ConsistReconfigureService.Steps steps, @AuthenticationPrincipal MarisUser user) {
        return reconfigure.state(group(user), steps);
    }

    public record ReconfigureRequest(ConsistReconfigureService.Steps steps, String yard, String track, String time, boolean confirmed) {}

    @PostMapping("/reconfigure")
    @Operation(summary = "VHTR01I Reconfigure (writes once confirmed; unconfirmed it returns VHTR02I's question)")
    public ConsistReconfigureService.Result reconfigure(@RequestBody ReconfigureRequest r, @AuthenticationPrincipal MarisUser user) {
        return reconfigure.reconfigure(group(user), r.steps(), r.yard(), r.track(), r.time(), r.confirmed());
    }
}
