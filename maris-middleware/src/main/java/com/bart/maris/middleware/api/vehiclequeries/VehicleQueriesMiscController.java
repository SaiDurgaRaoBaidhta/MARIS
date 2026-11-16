package com.bart.maris.middleware.api.vehiclequeries;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarStatusChangeService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistOrientationService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesDncSheetService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesScramService;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle queries: the consist orientation report (old VHCO01I), the D and C sheet reprint (VHDC01I), the SCRAM report
 * (VHSCM1I) and the car status change (VHCHG1I, an update). Prints record this system's terminal id
 * (maris.engine.terminal-id) and the signed-on group, as the old menus passed them down.
 */
@RestController
@RequestMapping("/api/v1/vehicle-queries")
@Tag(name = "Vehicle queries")
public class VehicleQueriesMiscController {
    private final VehicleQueriesConsistOrientationService orientation;
    private final VehicleQueriesDncSheetService dnc;
    private final VehicleQueriesScramService scram;
    private final VehicleQueriesCarStatusChangeService statusChange;
    private final String terminal;

    public VehicleQueriesMiscController(VehicleQueriesConsistOrientationService orientation, VehicleQueriesDncSheetService dnc, VehicleQueriesScramService scram,
                                        VehicleQueriesCarStatusChangeService statusChange, MarisProperties props) {
        this.orientation = orientation;
        this.dnc = dnc;
        this.scram = scram;
        this.statusChange = statusChange;
        this.terminal = props.engine().terminalId();
    }

    // ---- VHCO01I ---------------------------------------------------------------------------------------------

    @GetMapping("/consist-orientation/options")
    @Operation(summary = "Start dates and printers of the consist orientation report (VHCO01I: VHSV011, VHHPMA3, WS70A01); dates yyyy-MM-dd")
    public VehicleQueriesConsistOrientationService.Options consistOrientationOptions() {
        return orientation.options();
    }

    public record OrientationPrint(String from, String to, String fromDate, String toDate, String printer) {}

    @PostMapping("/consist-orientation/print")
    @Operation(summary = "Print the consist orientation report (VHCO01I Print: VHCO011); 'from' may be the word for every consist")
    public Map<String, String> printConsistOrientation(@RequestBody OrientationPrint r) {
        return Map.of("message", orientation.print(r.from(), r.to(), r.fromDate(), r.toDate(), r.printer()));
    }

    // ---- VHDC01I ---------------------------------------------------------------------------------------------

    @GetMapping("/dnc-sheet/options")
    @Operation(summary = "Printers of the D and C sheet reprint (VHDC01I: VHDC011)")
    public VehicleQueriesDncSheetService.Options dncSheetOptions() {
        return dnc.options();
    }

    public record DncPrint(String blankForm, String car, String incident, String printer) {}

    @PostMapping("/dnc-sheet/print")
    @Operation(summary = "Print a blank D and C sheet or a car's / incident's sheet (VHDC01I OK: VHDC013, printDnC / printBlankDnC, VHDC012)")
    public Map<String, String> printDncSheet(@RequestBody DncPrint r, @AuthenticationPrincipal MarisUser user) {
        return Map.of("message", dnc.print(r.blankForm(), r.car(), r.incident(), r.printer(), user == null ? "" : user.groupId(), terminal));
    }

    // ---- VHSCM1I ---------------------------------------------------------------------------------------------

    @GetMapping("/scram/options")
    @Operation(summary = "The SCRAM report's start day and time, codes, home yards and headings (VHSCM1I: VHXREVA, VHSCM1A)")
    public VehicleQueriesScramService.Options scramOptions() {
        return scram.options();
    }

    @GetMapping("/scram")
    @Operation(summary = "Cars each home yard provides for the yards, for a schedule day and time (VHSCM1I List Details, Prv / Nxt: VHSCM1B)")
    public VehicleQueriesScramService.Report scramReport(@RequestParam(required = false) String day, @RequestParam(required = false) String time) {
        return scram.report(day, time);
    }

    // ---- VHCHG1I ---------------------------------------------------------------------------------------------

    @GetMapping("/car-status-change/options")
    @Operation(summary = "Statuses a car can be changed to and the screen's prompts (VHCHG1I)")
    public VehicleQueriesCarStatusChangeService.Options carStatusChangeOptions() {
        return statusChange.options();
    }

    @GetMapping("/car-status-change/cars/{car}")
    @Operation(summary = "A car's current and previous vehicle status (VHCHG1I: VHVM1A2)")
    public VehicleQueriesCarStatusChangeService.CarStatus carStatus(@PathVariable String car) {
        return statusChange.car(car);
    }

    public record StatusChange(String status) {}

    @PostMapping("/car-status-change/cars/{car}")
    @Operation(summary = "Change a car's vehicle status (VHCHG1I Update, Yes: VHVM1A2, VHVM1A1)")
    public Map<String, String> changeCarStatus(@PathVariable String car, @RequestBody StatusChange r) {
        return Map.of("message", statusChange.change(car, r.status()));
    }
}
