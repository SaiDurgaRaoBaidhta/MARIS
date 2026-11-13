package com.bart.maris.middleware.api.dev;

import com.bart.maris.middleware.api.common.ApiResponse;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer endpoints: look up any legacy program's records and run it with JSON. Used to build and test the
 * business endpoints and the React screens. Switched on only by {@code maris.dev-api.enabled=true} (local profile);
 * it must stay off in production because it can run any program, including updates.
 */
@RestController
@RequestMapping("/api/v1/dev/programs")
@ConditionalOnProperty(name = "maris.dev-api.enabled", havingValue = "true")
@Tag(name = "Developer: legacy programs")
public class DevProgramController {
    private final LegacyCatalog catalog;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;

    public DevProgramController(LegacyCatalog catalog, LegacyProgramService legacy, MessageResolver messages) {
        this.catalog = catalog;
        this.legacy = legacy;
        this.messages = messages;
    }

    public record ExecuteRequest(Map<String, Object> records, Boolean trimEmptyRows) {}

    @GetMapping
    @Operation(summary = "Find legacy programs by name or package")
    public ApiResponse<List<ServiceDefinition>> list(@RequestParam(defaultValue = "") String q,
                                                     @RequestParam(defaultValue = "50") int limit) {
        String needle = q.toUpperCase(Locale.ROOT);
        List<ServiceDefinition> hits = catalog.services().stream()
                .filter(s -> s.function().toUpperCase(Locale.ROOT).contains(needle)
                        || s.pkg().toUpperCase(Locale.ROOT).contains(needle))
                .limit(Math.max(1, Math.min(limit, 200))).toList();
        return ApiResponse.of(hits, Map.of("count", hits.size()));
    }

    @GetMapping("/{function}")
    @Operation(summary = "A program's parameters and the full layout of each record")
    public ApiResponse<Map<String, Object>> describe(@PathVariable String function) {
        ServiceDefinition def = legacy.definition(function);
        Map<String, RecordLayout> layouts = new LinkedHashMap<>();
        def.params().stream().filter(p -> p.record() != null).forEach(p -> layouts.put(p.name(), catalog.record(p.record())));
        return ApiResponse.of(Map.of("service", def, "records", layouts));
    }

    @PostMapping("/{function}")
    @Operation(summary = "Run a legacy program with JSON records and get its decoded records back")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String function, @RequestBody(required = false) ExecuteRequest body) {
        Map<String, Object> records = body == null || body.records() == null ? Map.of() : body.records();
        boolean trim = body == null || body.trimEmptyRows() == null || body.trimEmptyRows();
        ProgramResult r = legacy.execute(function, records, trim);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("function", r.function());
        meta.put("service", r.service());
        meta.put("durationMs", r.durationMs());
        if (r.control() != null) {
            meta.put("control", r.control());
            meta.put("outcome", r.control().outcome());
            MessageResolver.Message m = messages.resolve(r.control().messageCode(), r.control().messageInserts());
            if (m != null) meta.put("message", m);
        }
        return ApiResponse.of(r.records(), meta);
    }
}
