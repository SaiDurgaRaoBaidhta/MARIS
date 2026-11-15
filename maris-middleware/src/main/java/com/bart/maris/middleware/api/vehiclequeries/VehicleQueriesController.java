package com.bart.maris.middleware.api.vehiclequeries;

import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarHoursService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarInquiryService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarStatusService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesFleetReportService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesHomeYardFleetService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesHomeYardStatusService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Criteria;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesRepeaterCarsService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesVehicleStatusService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesYardStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle queries, read-only: the batch print reports (old VHCP01I, VHDP01I, VHFO01I, VHMC01I, VHMR01-03I, VHNG01I,
 * VHOT01I, VHPS01I, VHRC03I, VHPT01I, VHYTOTI), car status history (VHCSH1I with its printer window VHCSH2I) and the
 * home yard fleet summary (VHHF01I with VHHF02I), car hours (VHCHR1I), the fleet report (VHFR01I) and repeater cars
 * (VHRC01I with VHRC02I), home yard status (VHYS01I with VHYS02I .. VHYS05I) and yard status (VHYS06I with VHYS07I .. VHYS10I).
 */
@RestController
@RequestMapping("/api/v1/vehicle-queries")
@Tag(name = "Vehicle queries")
public class VehicleQueriesController {
    private final VehicleQueriesBatchReportService batch;
    private final VehicleQueriesCarStatusService carStatus;
    private final VehicleQueriesHomeYardFleetService fleet;
    private final VehicleQueriesCarHoursService carHours;
    private final VehicleQueriesFleetReportService fleetReport;
    private final VehicleQueriesRepeaterCarsService repeaters;
    private final VehicleQueriesHomeYardStatusService homeYardStatus;
    private final VehicleQueriesYardStatusService yardStatus;
    private final VehicleQueriesVehicleStatusService vehicleStatus;
    private final VehicleQueriesCarInquiryService carInquiry;

    public VehicleQueriesController(VehicleQueriesBatchReportService batch, VehicleQueriesCarStatusService carStatus,
                                    VehicleQueriesHomeYardFleetService fleet, VehicleQueriesCarHoursService carHours,
                                    VehicleQueriesFleetReportService fleetReport, VehicleQueriesRepeaterCarsService repeaters,
                                    VehicleQueriesHomeYardStatusService homeYardStatus, VehicleQueriesYardStatusService yardStatus,
                                    VehicleQueriesVehicleStatusService vehicleStatus, VehicleQueriesCarInquiryService carInquiry) {
        this.carInquiry = carInquiry;
        this.vehicleStatus = vehicleStatus;
        this.yardStatus = yardStatus;
        this.repeaters = repeaters;
        this.homeYardStatus = homeYardStatus;
        this.batch = batch;
        this.carStatus = carStatus;
        this.fleet = fleet;
        this.carHours = carHours;
        this.fleetReport = fleetReport;
    }

    @GetMapping("/home-yard-status/options")
    @Operation(summary = "Yards, statuses, narrative choices, car range and printers of the home yard status report (VHYS01I)")
    public VehicleQueriesStatusCriteria.Options homeYardStatusOptions() {
        return homeYardStatus.options();
    }

    @GetMapping("/home-yard-status/check")
    @Operation(summary = "Check the criteria like VHYS01I Display; option base|status|nogo|summary")
    public VehicleQueriesStatusCriteria.Checked homeYardStatusCheck(Criteria c) {
        return homeYardStatus.check(c);
    }

    @GetMapping("/home-yard-status/cars")
    @Operation(summary = "The cars of the Base / Status report (VHYS02I: VHYS02A)")
    public VehicleQueriesHomeYardStatusService.Cars homeYardStatusCars(Criteria c) {
        return homeYardStatus.cars(c);
    }

    @GetMapping("/home-yard-status/nogo")
    @Operation(summary = "The NOGO report: NOGO cars and their incidents (VHYS03I: VHYS03A, VHYS03B)")
    public VehicleQueriesHomeYardStatusService.Nogo homeYardStatusNogo(Criteria c) {
        return homeYardStatus.nogo(c);
    }

    @GetMapping("/home-yard-status/summary")
    @Operation(summary = "The Summary report: the yard's cars (VHYS04I: VHYS04A)")
    public VehicleQueriesHomeYardStatusService.Summary homeYardStatusSummary(Criteria c) {
        return homeYardStatus.summary(c);
    }

    @GetMapping("/home-yard-status/type-summary")
    @Operation(summary = "The yard's cars counted by type and state (VHYS05I: VHYS05A)")
    public VehicleQueriesHomeYardStatusService.TypeSummary homeYardStatusTypeSummary(@RequestParam String yard) {
        return homeYardStatus.typeSummary(yard);
    }

    @GetMapping("/home-yard-status/cars/{car}/incidents")
    @Operation(summary = "A car's incidents in the Base / Status report (VHYS02I: VHYS02A)")
    public VehicleQueriesHomeYardStatusService.Incidents homeYardStatusIncidents(Criteria c, @PathVariable String car) {
        return homeYardStatus.incidents(c, car);
    }

    @GetMapping("/home-yard-status/narrative")
    @Operation(summary = "An incident's narrative lines (VHYS02B); homeYard is the car's home yard")
    public List<String> homeYardStatusNarrative(@RequestParam String car, @RequestParam String incident, @RequestParam String homeYard) {
        return homeYardStatus.narrative(car, incident, homeYard);
    }

    public record HomeYardStatusPrint(String option, String yard, String status, String narr, String carFrom, String carTo, String printer) {}

    @PostMapping("/home-yard-status/print")
    @Operation(summary = "Print the home yard status report (VHYS01I Print: VHYS01C)")
    public Map<String, String> printHomeYardStatus(@RequestBody HomeYardStatusPrint r) {
        return Map.of("message", homeYardStatus.print(new Criteria(r.option(), r.yard(), r.status(), r.narr(), r.carFrom(), r.carTo()), r.printer()));
    }

    @GetMapping("/vehicle-status/options")
    @Operation(summary = "Report kinds, deferred items, start dates and printers of the vehicle status report (VHSV01I)")
    public VehicleQueriesVehicleStatusService.Options vehicleStatusOptions() {
        return vehicleStatus.options();
    }

    @GetMapping("/vehicle-status")
    @Operation(summary = "A car's open incidents (VHSV01I Display, VHSV02I: VHINC6A, AFCXVLD, VHSV02A); report 1 repaired, 2 deferred")
    public VehicleQueriesVehicleStatusService.Report vehicleStatus(VehicleQueriesVehicleStatusService.Criteria c) {
        return vehicleStatus.report(c);
    }

    @GetMapping("/vehicle-status/incidents/{incident}/narrative")
    @Operation(summary = "An incident's narrative lines (VHSV02I: VHNARX)")
    public List<String> vehicleStatusNarrative(@PathVariable String incident) {
        return vehicleStatus.narrative(incident);
    }

    @GetMapping("/vehicle-status/consist")
    @Operation(summary = "The consist an incident's car ran in, cars with orientation (VHSV02I: VHSV02C)")
    public VehicleQueriesVehicleStatusService.Consist vehicleStatusConsist(@RequestParam String consist, @RequestParam int dash,
                                                                           @RequestParam(defaultValue = "") String dateInServiceRev,
                                                                           @RequestParam(defaultValue = "") String dateInService,
                                                                           @RequestParam(defaultValue = "") String timeInService) {
        return vehicleStatus.consist(consist, dash, dateInServiceRev, dateInService, timeInService);
    }

    public record VehicleStatusPrint(Integer report, String car, String fromDate, String toDate, String sympFrom, String sympTo, String deferredItems,
                                     String printer) {}

    @PostMapping("/vehicle-status/print")
    @Operation(summary = "Print the vehicle status report (VHSV01I Print: VHSV012)")
    public Map<String, String> printVehicleStatus(@RequestBody VehicleStatusPrint r) {
        return Map.of("message", vehicleStatus.print(new VehicleQueriesVehicleStatusService.Criteria(r.report(), r.car(), r.fromDate(), r.toDate(),
                r.sympFrom(), r.sympTo(), r.deferredItems()), r.printer()));
    }

    @GetMapping("/car-inquiry/{car}")
    @Operation(summary = "A car's status / history inquiry (VHSV07I Display, VHSV06I: VHSV06F, VHSV06A, VHSV06B)")
    public VehicleQueriesCarInquiryService.Inquiry carInquiry(@PathVariable String car) {
        return carInquiry.inquiry(car);
    }

    @GetMapping("/car-inquiry/incidents/{incident}")
    @Operation(summary = "An incident's narrative and consist (VHSV06I: VHSV06N)")
    public VehicleQueriesCarInquiryService.IncidentDetails carInquiryIncident(@PathVariable String incident) {
        return carInquiry.incident(incident);
    }

    @GetMapping("/car-inquiry/incidents/{incident}/repairs")
    @Operation(summary = "An incident's repairs (VHSV06I: VHSV06C)")
    public List<VehicleQueriesCarInquiryService.Repair> carInquiryRepairs(@PathVariable String incident) {
        return carInquiry.repairs(incident);
    }

    @GetMapping("/car-inquiry/incidents/{incident}/repairs/{document}")
    @Operation(summary = "A repair's narrative and components (VHSV06I: VHNARY, VHSV06D)")
    public VehicleQueriesCarInquiryService.RepairDetails carInquiryRepair(@PathVariable String incident, @PathVariable long document) {
        return carInquiry.repair(incident, document);
    }

    @GetMapping("/vehicle-history")
    @Operation(summary = "A car's incident history (VHSV04I Display, VHSV05I: VHINC6A, AFCXVLD, VHSV05A)")
    public VehicleQueriesVehicleStatusService.History vehicleHistory(VehicleQueriesVehicleStatusService.Criteria c) {
        return vehicleStatus.history(c);
    }

    @GetMapping("/vehicle-history/incidents/{incident}/narrative")
    @Operation(summary = "An incident's narrative lines (VHSV05I: VHSV05F)")
    public List<String> vehicleHistoryNarrative(@PathVariable String incident) {
        return vehicleStatus.historyNarrative(incident);
    }

    @GetMapping("/vehicle-history/incidents/{incident}/repairs")
    @Operation(summary = "An incident's repairs (VHSV05I: VHSV05C)")
    public List<VehicleQueriesVehicleStatusService.Repair> vehicleHistoryRepairs(@PathVariable String incident) {
        return vehicleStatus.repairs(incident);
    }

    @GetMapping("/vehicle-history/incidents/{incident}/repairs/{document}")
    @Operation(summary = "A repair's narrative and components (VHSV05I: VHNARY, VHSV05D)")
    public VehicleQueriesVehicleStatusService.RepairDetails vehicleHistoryRepair(@PathVariable String incident, @PathVariable long document) {
        return vehicleStatus.repair(incident, document);
    }

    @PostMapping("/vehicle-history/print")
    @Operation(summary = "Print a car's incident history (VHSV04I Print: VHSV012)")
    public Map<String, String> printVehicleHistory(@RequestBody VehicleStatusPrint r) {
        return Map.of("message", vehicleStatus.printHistory(new VehicleQueriesVehicleStatusService.Criteria(0, r.car(), r.fromDate(), r.toDate(),
                r.sympFrom(), r.sympTo(), ""), r.printer()));
    }

    @GetMapping("/yard-status/options")
    @Operation(summary = "Yards, statuses, narrative choices, car range and printers of the yard status report (VHYS06I)")
    public VehicleQueriesStatusCriteria.Options yardStatusOptions() {
        return yardStatus.options();
    }

    @GetMapping("/yard-status/check")
    @Operation(summary = "Check the criteria like VHYS06I Display; option base|status|nogo|summary")
    public VehicleQueriesStatusCriteria.Checked yardStatusCheck(Criteria c) {
        return yardStatus.check(c);
    }

    @GetMapping("/yard-status/cars")
    @Operation(summary = "The cars of the Base / Status report (VHYS07I: VHYS07A)")
    public VehicleQueriesYardStatusService.Cars yardStatusCars(Criteria c) {
        return yardStatus.cars(c);
    }

    @GetMapping("/yard-status/cars/{car}/incidents")
    @Operation(summary = "A car's incidents in the Base / Status report (VHYS07I: VHYS07A)")
    public VehicleQueriesYardStatusService.Incidents yardStatusIncidents(Criteria c, @PathVariable String car) {
        return yardStatus.incidents(c, car);
    }

    @GetMapping("/yard-status/narrative")
    @Operation(summary = "An incident's narrative lines (VHYS07B); yard: the car's narrativeYard, or the NOGO criteria yard")
    public List<String> yardStatusNarrative(@RequestParam String car, @RequestParam String incident, @RequestParam String yard) {
        return yardStatus.narrative(car, incident, yard);
    }

    @GetMapping("/yard-status/nogo")
    @Operation(summary = "The NOGO report: NOGO cars and their incidents (VHYS08I: VHYS08A, VHYS08B)")
    public VehicleQueriesYardStatusService.Nogo yardStatusNogo(Criteria c) {
        return yardStatus.nogo(c);
    }

    @GetMapping("/yard-status/summary")
    @Operation(summary = "The Summary report: the yard's cars (VHYS09I: VHYS09A)")
    public VehicleQueriesYardStatusService.Summary yardStatusSummary(Criteria c) {
        return yardStatus.summary(c);
    }

    @GetMapping("/yard-status/type-summary")
    @Operation(summary = "The yard's cars counted by type and state (VHYS10I: VHYS10A)")
    public VehicleQueriesYardStatusService.TypeSummary yardStatusTypeSummary(@RequestParam String yard) {
        return yardStatus.typeSummary(yard);
    }

    @PostMapping("/yard-status/print")
    @Operation(summary = "Print the yard status report (VHYS06I Print: VHYS06C)")
    public Map<String, String> printYardStatus(@RequestBody HomeYardStatusPrint r) {
        return Map.of("message", yardStatus.print(new Criteria(r.option(), r.yard(), r.status(), r.narr(), r.carFrom(), r.carTo()), r.printer()));
    }

    @GetMapping("/repeater-cars/options")
    @Operation(summary = "The home yards and printers of the repeater cars report (VHRC01I: VHYS01A, WS70A01)")
    public VehicleQueriesRepeaterCarsService.Options repeaterOptions() {
        return repeaters.options();
    }

    @GetMapping("/repeater-cars")
    @Operation(summary = "A car's or a home yard's repeated incidents (VHRC01I Display, VHRC02I: VHCI01B, VHRC02A); car blank for all")
    public VehicleQueriesRepeaterCarsService.Incidents repeaterCars(@RequestParam String homeYard, @RequestParam(defaultValue = "") String car) {
        return repeaters.list(homeYard, car);
    }

    public record RepeaterPrint(String homeYard, String car, String printer) {}

    @PostMapping("/repeater-cars/print")
    @Operation(summary = "Print the repeater cars report (VHRC01I Hard Copy: VHRC011)")
    public Map<String, String> printRepeaterCars(@RequestBody RepeaterPrint r) {
        return Map.of("message", repeaters.print(r.homeYard(), r.car(), r.printer()));
    }

    @GetMapping("/fleet-report")
    @Operation(summary = "The fleet report: all cars by type and state, and the available GO cars by yard (VHFR01I: VHFR01A)")
    public VehicleQueriesFleetReportService.FleetReport fleetReport() {
        return fleetReport.report();
    }

    @PostMapping("/fleet-report/print")
    @Operation(summary = "Print the fleet report (VHFR01I Hard Copy: VHFR01C)")
    public Map<String, String> printFleetReport(@RequestBody PrinterOnly r) {
        return Map.of("message", fleetReport.print(r.printer()));
    }

    @GetMapping("/car-hours")
    @Operation(summary = "A car's revenue hours between two dates (VHCHR1I List: VHINC6A, AFCXVLD, VHCHR1A); dates yyyy-MM-dd")
    public VehicleQueriesCarHoursService.CarHours carHours(@RequestParam String car, @RequestParam String from, @RequestParam String to) {
        return carHours.list(car, from, to);
    }

    @PostMapping("/car-hours/print")
    @Operation(summary = "Print a car's hours (VHCHR1I Hard Copy: VHCHR01)")
    public Map<String, String> printCarHours(@RequestBody CarStatusPrint r) {
        return Map.of("message", carHours.print(r.car(), r.fromDate(), r.toDate(), r.printer()));
    }

    @GetMapping("/home-yard-fleet")
    @Operation(summary = "The home yard fleet summary: cars by type and state per home yard (VHHF01I: VHHF01A, VHHF01B)")
    public VehicleQueriesHomeYardFleetService.Fleet homeYardFleet() {
        return fleet.summary();
    }

    public record PrinterOnly(String printer) {}

    @PostMapping("/home-yard-fleet/print")
    @Operation(summary = "Print the home yard fleet summary (VHHF02I Hard Copy: VHHF02A)")
    public Map<String, String> printHomeYardFleet(@RequestBody PrinterOnly r) {
        return Map.of("message", fleet.print(r.printer()));
    }

    @GetMapping("/printers")
    @Operation(summary = "The printers of the printer windows (WS70A01)")
    public List<VehicleQueriesBatchReportService.Printer> printers() {
        return batch.printers();
    }

    @GetMapping("/car-status-history")
    @Operation(summary = "A car's status changes in a date range (VHCSH1I List: VHINC6A, AFCXVLD, VHCSH1A); dates yyyy-MM-dd")
    public VehicleQueriesCarStatusService.History carStatusHistory(@RequestParam String car, @RequestParam String from, @RequestParam String to) {
        return carStatus.list(car, from, to);
    }

    public record CarStatusPrint(String car, String fromDate, String toDate, String printer) {}

    @PostMapping("/car-status-history/print")
    @Operation(summary = "Print a car's status history (VHCSH1I Print, VHCSH2I Hard Copy: VHCSH2A)")
    public Map<String, String> printCarStatusHistory(@RequestBody CarStatusPrint r) {
        return Map.of("message", carStatus.print(r.car(), r.fromDate(), r.toDate(), r.printer()));
    }

    @GetMapping("/batch-reports")
    @Operation(summary = "The batch print reports: key and old screen")
    public List<Map<String, String>> batchReports() {
        return VehicleQueriesBatchReportService.REPORTS.stream().map(r -> Map.of("report", r.key(), "screen", r.screen())).toList();
    }

    @GetMapping("/batch-reports/{report}/options")
    @Operation(summary = "A report's start dates, yards, minutes and printers (programs A, B, C and WS70A01)")
    public VehicleQueriesBatchReportService.ReportOptions options(@PathVariable String report) {
        return batch.options(report);
    }

    @PostMapping("/batch-reports/{report}/print")
    @Operation(summary = "Check the criteria like the old Hard Copy and submit the report program")
    public Map<String, String> print(@PathVariable String report, @RequestBody VehicleQueriesBatchReportService.PrintRequest r) {
        return Map.of("message", batch.print(report, r));
    }
}
