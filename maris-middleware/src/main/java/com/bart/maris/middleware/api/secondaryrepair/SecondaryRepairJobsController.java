package com.bart.maris.middleware.api.secondaryrepair;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairCommentsService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairInDoorService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairJobSplitService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairLaborService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairRepairDetailService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secondary repair updates of jobs and sub jobs: in the door (old VHSR01I), job split (VHSR08I), comments (VHSR02I),
 * repair detail (VHSR09I), labor detail and close (VHSR03I). The terminal recorded on updates is the one the
 * old screens record for this system (maris.engine.terminal-id); the user group is the signed-on group.
 */
@RestController
@RequestMapping("/api/v1/secondary-repair")
@Tag(name = "Secondary repair")
public class SecondaryRepairJobsController {
    private final SecondaryRepairCommentsService comments;
    private final SecondaryRepairLaborService labor;
    private final SecondaryRepairRepairDetailService repairs;
    private final SecondaryRepairJobSplitService split;
    private final SecondaryRepairInDoorService inDoor;
    private final String terminal;

    public SecondaryRepairJobsController(SecondaryRepairCommentsService comments, SecondaryRepairLaborService labor, SecondaryRepairRepairDetailService repairs,
                                         SecondaryRepairJobSplitService split, SecondaryRepairInDoorService inDoor, MarisProperties props) {
        this.comments = comments;
        this.labor = labor;
        this.repairs = repairs;
        this.split = split;
        this.inDoor = inDoor;
        this.terminal = props.engine().terminalId();
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    // ---- VHSR02I: job and repair comments ------------------------------------------------------------------

    @GetMapping("/comments/kinds")
    @Operation(summary = "The comment kinds the old screen selects between (JOB, REPAIR)")
    public List<String> commentKinds() {
        return comments.kinds();
    }

    @GetMapping("/comments")
    @Operation(summary = "A sub job's comments of one kind: how many, and the first (VHSR02E, VHSR02F)")
    public SecondaryRepairCommentsService.Comments listComments(@RequestParam(required = false) String jobNo, @RequestParam(required = false) Integer doc,
                                                                @RequestParam(required = false) String kind) {
        return comments.list(jobNo, doc, kind);
    }

    @GetMapping("/comments/{number}")
    @Operation(summary = "One comment by its number (VHSR02C)")
    public SecondaryRepairCommentsService.Comment comment(@PathVariable int number, @RequestParam String jobNo, @RequestParam int doc, @RequestParam String kind) {
        return comments.comment(jobNo, doc, kind, number);
    }

    public record CommentRequest(String jobNo, Integer doc, String kind, String text) {}

    @PostMapping("/comments")
    @Operation(summary = "Add a comment as the next number (VHSR02A), then read it back (VHSR02D)")
    public SecondaryRepairCommentsService.Added addComment(@RequestBody CommentRequest r, @AuthenticationPrincipal MarisUser user) {
        return comments.add(r.jobNo(), r.doc(), r.kind(), r.text(), group(user), terminal);
    }

    @PutMapping("/comments/{number}")
    @PostMapping("/comments/{number}")
    @Operation(summary = "Change a comment's text (VHSR02B); PUT, or POST for clients that only post")
    public Map<String, String> updateComment(@PathVariable int number, @RequestBody CommentRequest r, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", comments.update(r.jobNo(), r.doc() == null ? 0 : r.doc(), r.kind(), number, r.text(), group(user), terminal));
    }

    // ---- VHSR01I: in the door, a new job -----------------------------------------------------------------------

    @GetMapping("/in-door/options")
    @Operation(summary = "Work types (VHSRWT), repair areas (VHSRRA) and the user group's departments (VHSRUD)")
    public SecondaryRepairInDoorService.Options inDoorOptions(@AuthenticationPrincipal MarisUser user) {
        return inDoor.options(group(user));
    }

    @GetMapping("/departments/{dept}/equipment-types")
    @Operation(summary = "A department's equipment types (VHSR01B)")
    public List<?> equipmentTypes(@PathVariable String dept) {
        return inDoor.equipmentTypes(dept);
    }

    @GetMapping("/departments/{dept}/equipment/{equipId}")
    @Operation(summary = "A piece of equipment's type and location (VHSR01C); 404 when it does not exist")
    public org.springframework.http.ResponseEntity<SecondaryRepairInDoorService.Equipment> equipment(@PathVariable String dept, @PathVariable String equipId) {
        var e = inDoor.equipment(dept, equipId);
        return e == null ? org.springframework.http.ResponseEntity.notFound().build() : org.springframework.http.ResponseEntity.ok(e);
    }

    @PostMapping("/jobs")
    @Operation(summary = "A new job after the old checks (VHSR01E numbers it)")
    public SecondaryRepairInDoorService.Created createJob(@RequestBody SecondaryRepairInDoorService.NewJob n, @AuthenticationPrincipal MarisUser user) {
        return inDoor.create(n, group(user), terminal);
    }

    // ---- VHSR08I: job split ------------------------------------------------------------------------------------

    @GetMapping("/job-split/options")
    @Operation(summary = "Work types (VHSRWT) and repair areas (VHSRRA)")
    public SecondaryRepairJobSplitService.Options splitOptions() {
        return split.options();
    }

    @GetMapping("/job-split/{jobNo}")
    @Operation(summary = "A job open for splitting and its sub jobs (VHSR08A)")
    public SecondaryRepairJobSplitService.Job splitJob(@PathVariable String jobNo) {
        return split.job(jobNo);
    }

    @GetMapping("/job-split/{jobNo}/sub-jobs/{doc}")
    @Operation(summary = "One sub job and its job comments (VHSR08B)")
    public SecondaryRepairJobSplitService.SubJobDetail splitSubJob(@PathVariable String jobNo, @PathVariable int doc) {
        return split.subJob(jobNo, doc);
    }

    @PostMapping("/job-split/{jobNo}/sub-jobs")
    @Operation(summary = "A new sub job as the next number, after the old checks (VHSR08C)")
    public SecondaryRepairJobSplitService.Created createSubJob(@PathVariable String jobNo, @RequestBody SecondaryRepairJobSplitService.NewSubJob n,
                                                               @AuthenticationPrincipal MarisUser user) {
        return split.create(jobNo, n, group(user), terminal);
    }

    // ---- VHSR09I: repair detail ----------------------------------------------------------------------------------

    @GetMapping("/repair-detail")
    @Operation(summary = "A sub job out the door and its repairs (VHSR09B, VHSR09A)")
    public SecondaryRepairRepairDetailService.RepairDetail repairDetail(@RequestParam(required = false) String jobNo, @RequestParam(required = false) Integer doc) {
        return repairs.details(jobNo, doc);
    }

    @GetMapping("/kits/{kitNo}")
    @Operation(summary = "A kit, which must be defined and have details (VHSR09C)")
    public SecondaryRepairRepairDetailService.Kit kit(@PathVariable String kitNo) {
        return repairs.kit(kitNo);
    }

    @GetMapping("/stock/{stockNo}/short-description")
    @Operation(summary = "A stock number's short description (VHSR09H)")
    public Map<String, String> stockDescription(@PathVariable String stockNo) {
        return Map.of("description", repairs.stockDescription(stockNo));
    }

    public record RepairRequest(String jobNo, Integer doc, String kitNo, Boolean noRepair, List<SecondaryRepairRepairDetailService.RepairLine> lines) {}

    @PostMapping("/repair-detail")
    @Operation(summary = "Add repairs, typed and / or from a kit, or the no-repair kit (VHSR09D)")
    public SecondaryRepairRepairDetailService.Added addRepairs(@RequestBody RepairRequest r) {
        return repairs.add(r.jobNo(), r.doc(), r.kitNo(), Boolean.TRUE.equals(r.noRepair()), r.lines(), terminal);
    }

    // ---- VHSR03I: labor detail and job close -----------------------------------------------------------------------

    @GetMapping("/labor")
    @Operation(summary = "A sub job out the door: its labor (VHSR03A), whether it can close (VHSR09A), its repair comments (VHSR03B)")
    public SecondaryRepairLaborService.LaborDetails laborDetails(@RequestParam(required = false) String jobNo, @RequestParam(required = false) Integer doc) {
        return labor.details(jobNo, doc);
    }

    public record LaborRequest(String jobNo, Integer doc, List<SecondaryRepairLaborService.LaborLine> lines, String comment) {}

    @PostMapping("/labor")
    @Operation(summary = "Add labor lines (VHSR03D), with the first repair comment when there is none")
    public SecondaryRepairLaborService.Added addLabor(@RequestBody LaborRequest r, @AuthenticationPrincipal MarisUser user) {
        return labor.add(r.jobNo(), r.doc(), r.lines(), r.comment(), group(user), terminal);
    }

    public record CloseRequest(String jobNo, Integer doc) {}

    @PostMapping("/labor/close")
    @Operation(summary = "Close a sub job that has repair details (VHSR03E)")
    public Map<String, String> closeSubJob(@RequestBody CloseRequest r, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", labor.close(r.jobNo(), r.doc(), group(user), terminal));
    }
}
