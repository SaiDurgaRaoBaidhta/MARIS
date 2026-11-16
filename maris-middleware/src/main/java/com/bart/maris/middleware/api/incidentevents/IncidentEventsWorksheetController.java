package com.bart.maris.middleware.api.incidentevents;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsSymptomHistoryService;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsWorksheetService;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsWorksheetService.EventInput;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The vehicle desk work sheet (old VHIE03I, with VHIE04I's event detail) and the symptom history (old VHIE07I). The
 * terminal recorded on updates is the one the old screens record (maris.engine.terminal-id); the user group is the
 * signed-on group.
 */
@RestController
@RequestMapping("/api/v1/incident-events/worksheet")
@Tag(name = "Incident events")
public class IncidentEventsWorksheetController {
    private final IncidentEventsWorksheetService worksheet;
    private final IncidentEventsSymptomHistoryService history;
    private final String terminal;

    public IncidentEventsWorksheetController(IncidentEventsWorksheetService worksheet, IncidentEventsSymptomHistoryService history, MarisProperties props) {
        this.worksheet = worksheet;
        this.history = history;
        this.terminal = props.engine().terminalId();
    }

    private static String group(MarisUser user) {
        return user == null ? "" : user.groupId();
    }

    private static Map<String, String> message(String m) {
        Map<String, String> out = new HashMap<>();
        out.put("message", m);
        return out;
    }

    @GetMapping("/options")
    @Operation(summary = "Revenue date, code lists and create-incident access (programs VHIE01B, VHIE04D, VHIE03B/C/D, VH01A02)")
    public IncidentEventsWorksheetService.Options options(@AuthenticationPrincipal MarisUser user) {
        return worksheet.options(group(user), terminal);
    }

    @GetMapping("/symptoms/{symptom}")
    @Operation(summary = "A symptom code's text (program VHIE04D)")
    public Map<String, String> symptom(@PathVariable String symptom) {
        return message(worksheet.symptomText(symptom));
    }

    @GetMapping("/events")
    @Operation(summary = "The events of a revenue date, yyyy-MM-dd (program VHIE03A)")
    public IncidentEventsWorksheetService.EventLog events(@RequestParam String date) {
        return worksheet.events(date);
    }

    @GetMapping("/events/{date}/{number}/corrective-action")
    @Operation(summary = "An event's narrative and corrective action (program VHIE04C)")
    public IncidentEventsWorksheetService.CorrectiveAction correctiveAction(@PathVariable String date, @PathVariable int number) {
        return worksheet.correctiveAction(date, number);
    }

    @GetMapping("/cars/{car}/consist")
    @Operation(summary = "The consist a car runs in (program VHIE03H)")
    public IncidentEventsWorksheetService.Consist consistOfCar(@PathVariable String car) {
        return worksheet.consistOfCar(car);
    }

    public record ConsistQuery(String consist, String date, IncidentEventsWorksheetService.InService inService) {}

    @PostMapping("/consist-cars")
    @Operation(summary = "The cars of a consist on a date, or as an event recorded it (program VHIE03G)")
    public IncidentEventsWorksheetService.Consist consistCars(@RequestBody ConsistQuery q) {
        return worksheet.consistCars(q.consist(), q.date(), q.inService());
    }

    @PostMapping("/events")
    @Operation(summary = "Add an event to the current revenue date (programs VHINC6A, VHIE03E, VHIE04A)")
    public Map<String, String> add(@RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return message(worksheet.add(in, group(user), terminal));
    }

    @PutMapping("/events/{date}/{number}")
    @Operation(summary = "Save an event's changes (program VHIE04B)")
    public Map<String, String> update(@PathVariable String date, @PathVariable int number, @RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return message(worksheet.update(date, number, in, false, group(user), terminal));
    }

    @DeleteMapping("/events/{date}/{number}")
    @Operation(summary = "Delete an event (program VHIE04B with the delete flag); the body is the event as shown")
    public Map<String, String> delete(@PathVariable String date, @PathVariable int number, @RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return message(worksheet.update(date, number, in, true, group(user), terminal));
    }

    @PostMapping("/events/{date}/{number}")
    @Operation(summary = "Save an event's changes (the same as PUT, for clients that send only GET and POST)")
    public Map<String, String> updateByPost(@PathVariable String date, @PathVariable int number, @RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return update(date, number, in, user);
    }

    @PostMapping("/events/{date}/{number}/delete")
    @Operation(summary = "Delete an event (the same as DELETE, for clients that send only GET and POST)")
    public Map<String, String> deleteByPost(@PathVariable String date, @PathVariable int number, @RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return delete(date, number, in, user);
    }

    @PostMapping("/events/{date}/{number}/incident")
    @Operation(summary = "File an incident for an event (VHEVLX / VHEVLY / VHEVL1F / VHINC1X, then VHIE04B and VHINC3A)")
    public Map<String, String> incident(@PathVariable String date, @PathVariable int number, @RequestBody EventInput in, @AuthenticationPrincipal MarisUser user) {
        return message(worksheet.createIncident(date, number, in, group(user), terminal));
    }

    @GetMapping("/symptom-history/{symptom}")
    @Operation(summary = "Events and incidents of a symptom code over the last N days (programs VHIE07C, VHIE07A, VHIE07B)")
    public IncidentEventsSymptomHistoryService.History symptomHistory(@PathVariable String symptom, @RequestParam(required = false) Integer days) {
        return history.history(symptom, days);
    }
}
