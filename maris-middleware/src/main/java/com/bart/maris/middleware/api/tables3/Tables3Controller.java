package com.bart.maris.middleware.api.tables3;

import com.bart.maris.middleware.domain.tables3.Tables3SerialDataService;
import com.bart.maris.middleware.domain.tables3.Tables3SerialService;
import com.bart.maris.middleware.domain.tables3.Tables3Serials;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Table maintenance (app 3): component serials (old VHCF49I list, VHCF50I detail, VHCF51I hours, VHCF52I geo data). */
@RestController
@RequestMapping("/api/v1/tables-3")
@Tag(name = "Table maintenance 3")
public class Tables3Controller {
    private final Tables3SerialService serials;
    private final Tables3SerialDataService data;

    public Tables3Controller(Tables3SerialService serials, Tables3SerialDataService data) {
        this.serials = serials;
        this.data = data;
    }

    @GetMapping("/component-serials/options")
    @Operation(summary = "Stock numbers to list component serials by (old VHCF49I; VHCF49B)")
    public Tables3Serials.Options options() {
        return serials.options();
    }

    @GetMapping("/component-serials")
    @Operation(summary = "Component serials by car, stock (ALL or blank: every) and serial (old VHCF49I List; VHCF49E, VHCF49A)")
    public Tables3Serials.Rows list(@RequestParam(required = false) String car, @RequestParam(required = false) String stock,
                                    @RequestParam(required = false) String serial) {
        return serials.list(car, stock, serial);
    }

    @PostMapping("/component-serials/form")
    @Operation(summary = "One listed serial as the old detail form shows it, its comments and lists (old VHCF50I; VHCF50B..M, VHCF50I)")
    public Tables3Serials.Form form(@RequestBody Map<String, Object> row) {
        return serials.form(row);
    }

    @PostMapping("/component-serials/update")
    @Operation(summary = "Update a component serial (old VHCF50I Update; VHCF50D per changed date, VHCF50A)")
    public ResponseEntity<Object> update(@RequestBody Tables3Serials.Change change) {
        return answer(serials.update(change));
    }

    @GetMapping("/serial-hours")
    @Operation(summary = "A serial's hours; no stock and serial: every serial (old VHCF51I; VHCF51A)")
    public Tables3Serials.Rows hours(@RequestParam(required = false) String stock, @RequestParam(required = false) String serial) {
        return data.hours(stock, serial);
    }

    @PostMapping("/serial-hours/update")
    @Operation(summary = "Update a serial's hours (old VHCF51I Update; AFCXVLD, VHCF51B)")
    public ResponseEntity<Object> updateHours(@RequestBody Tables3Serials.HoursChange change) {
        return answer(data.updateHours(change));
    }

    @GetMapping("/serial-geo-data")
    @Operation(summary = "A serial's geo data and the locations; no stock and serial: every serial (old VHCF52I; VHCF52C, VHCF52A)")
    public Map<String, Object> geoData(@RequestParam(required = false) String stock, @RequestParam(required = false) String serial) {
        return data.geoData(stock, serial);
    }

    @PostMapping("/serial-geo-data/update")
    @Operation(summary = "Update a serial's geo data (old VHCF52I Update; AFCXVLD, VHCF52B)")
    public ResponseEntity<Object> updateGeo(@RequestBody Tables3Serials.GeoChange change) {
        return answer(data.updateGeo(change));
    }

    /**
     * An update the old screen refused or the program failed: 422 with the old screen's message as the problem's detail
     * (the program's own message after it), so the UI shows it as any other refusal.
     */
    private static ResponseEntity<Object> answer(Tables3Serials.Result r) {
        if (r.ok()) return ResponseEntity.ok(r);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", r.message());
        body.put("detail", r.detail() == null ? r.message() : r.message() + " (" + r.detail() + ")");
        body.put("code", "REFUSED");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
