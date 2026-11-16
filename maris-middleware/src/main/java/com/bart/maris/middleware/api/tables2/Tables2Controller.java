package com.bart.maris.middleware.api.tables2;

import com.bart.maris.middleware.domain.tables2.Tables2MessageDetailsService;
import com.bart.maris.middleware.domain.tables2.Tables2Messages;
import com.bart.maris.middleware.domain.tables2.Tables2MessagesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Table maintenance 2: MRS messages received (old VHCCS04I list, VHCCS05I detail, VHCCS06I cars, VHCCS07I errors, VHCCS18I
 * hard copy). Saves are POSTs (the UI client sends GET and POST).
 */
@RestController
@RequestMapping("/api/v1/tables-2")
@Tag(name = "Table maintenance 2")
public class Tables2Controller {
    private final Tables2MessagesService messages;
    private final Tables2MessageDetailsService details;

    public Tables2Controller(Tables2MessagesService messages, Tables2MessageDetailsService details) {
        this.messages = messages;
        this.details = details;
    }

    @GetMapping("/messages/options")
    @Operation(summary = "Criteria lists and proposed date / times (old VHCCS04I; VHCCS4E, 4F, 4G, 4H, 4I)")
    public Tables2Messages.Options options() {
        return messages.options();
    }

    @PostMapping("/messages/list")
    @Operation(summary = "List the messages received for the criteria (old VHCCS04I List; AFCXVLD, VHYTIME, VHCCS4A, VHCCS4H)")
    public Tables2Messages.Listing list(@RequestBody Tables2Messages.Criteria c) {
        return messages.list(c);
    }

    @PostMapping("/messages/update")
    @Operation(summary = "Save a message's detail (old VHCCS05I Save in UPDATE mode; VHCCS5N, VHCCS5M, VHCCS5C)")
    public Tables2Messages.Saved update(@RequestBody Map<String, Object> fields) {
        return messages.update(fields);
    }

    @GetMapping("/messages/print-options")
    @Operation(summary = "The hard copy window's printers and lists (old VHCCS18I; WS70A01, VHCCSHC / B / D / E / F)")
    public Tables2Messages.PrintOptions printOptions() {
        return messages.printOptions();
    }

    @PostMapping("/messages/print")
    @Operation(summary = "Print the messages received on a MARIS printer (old VHCCS18I Hard Copy; VHCCSHA)")
    public Map<String, Boolean> print(@RequestBody Tables2Messages.Print p) {
        messages.print(p);
        return Map.of("submitted", true);
    }

    @GetMapping("/messages/cars")
    @Operation(summary = "A message's cars, or those of the List's time-stamp window (old VHCCS06I; VHCCS6A, VHCCS6E)")
    public Tables2Messages.Cars cars(@RequestParam(required = false) String timestamp, @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return details.cars(timestamp, from, to);
    }

    @PostMapping("/messages/cars/update")
    @Operation(summary = "Update a message's car (old VHCCS06I Update; AFCXVLD, VHCCS6C)")
    public Tables2Messages.Saved updateCar(@RequestBody Tables2Messages.Car c) {
        return details.updateCar(c);
    }

    @PostMapping("/messages/cars/delete")
    @Operation(summary = "Delete a message's car (old VHCCS06I Delete; VHCCS6D)")
    public Tables2Messages.Saved deleteCar(@RequestBody Tables2Messages.Car c) {
        return details.deleteCar(c.timestamp(), c.carNo());
    }

    @GetMapping("/messages/errors")
    @Operation(summary = "A message's errors, or those of the List's time-stamp window (old VHCCS07I; VHCCS7A)")
    public List<Tables2Messages.Error> errors(@RequestParam(required = false) String timestamp, @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return details.errors(timestamp, from, to);
    }

    @PostMapping("/messages/errors/update")
    @Operation(summary = "Update a message's error (old VHCCS07I Update; VHCCS7C)")
    public Tables2Messages.Saved updateError(@RequestBody Tables2Messages.Error e) {
        return details.updateError(e);
    }

    @PostMapping("/messages/errors/delete")
    @Operation(summary = "Delete a message's error (old VHCCS07I Delete; VHCCS7D)")
    public Tables2Messages.Saved deleteError(@RequestBody Tables2Messages.Error e) {
        return details.deleteError(e.timestamp());
    }
}
