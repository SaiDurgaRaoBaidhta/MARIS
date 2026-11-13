package com.bart.maris.middleware.api.screens;

import com.bart.maris.middleware.domain.screens.ActionResult;
import com.bart.maris.middleware.domain.screens.ScreenActionRegistry;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/screens")
@Tag(name = "Screens")
public class ScreenActionController {
    private final ScreenActionRegistry registry;

    public ScreenActionController(ScreenActionRegistry registry) {
        this.registry = registry;
    }

    /**
     * The UI's ActionRequest. {@code programs} is what the layout says the button may call; the middleware does not
     * trust it and runs only the translated logic.
     */
    public record ActionRequest(String screen, String control, String caption, List<String> programs, Map<String, String> fields) {}

    @PostMapping("/{screen}/actions/{control}")
    @Operation(summary = "Press a push button on a MARIS screen. Buttons not translated yet answer 501 NOT_MIGRATED.")
    public ActionResult run(@PathVariable String screen, @PathVariable String control, @RequestBody ActionRequest body,
                            @AuthenticationPrincipal MarisUser user) {
        return registry.run(screen, control, body.caption(), body.fields(), user);
    }

    @GetMapping("/actions")
    @Operation(summary = "Buttons that are connected to the MARIS backend (SCREEN/control)")
    public Set<String> connected() {
        return registry.connected();
    }
}
