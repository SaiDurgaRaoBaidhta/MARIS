package com.bart.maris.middleware.api.ui;

import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The old MARIS screens, live: the middleware runs each screen's own logic (its EGL statement groups and connections)
 * and calls the existing backend services exactly when the old screen did. The UI sends what the user did and draws
 * the widgets' state it gets back.
 */
@RestController
@RequestMapping("/api/v1/ui")
@Tag(name = "Live screens")
public class LiveScreenController {
    private static final String KEY = "maris.screenSession";
    private final ScreenEngine engine;

    public LiveScreenController(ScreenEngine engine) {
        this.engine = engine;
    }

    public record OpenRequest(String screen) {}

    public record EventRequest(List<ScreenSession.Change> changes, String widget, String event, Object value) {}

    public record AnswerRequest(String button) {}

    private ScreenSession session(HttpSession http, MarisUser user) {
        Object s = http.getAttribute(KEY);
        if (s instanceof ScreenSession ss) return ss;
        ScreenSession ss = engine.newSession(user == null ? "" : user.groupId());
        http.setAttribute(KEY, ss);
        return ss;
    }

    @PostMapping("/open")
    @Operation(summary = "Open a screen (fresh, as from the menu). Runs its open logic, including backend calls.")
    public Map<String, Object> open(@RequestBody OpenRequest req, HttpSession http, @AuthenticationPrincipal MarisUser user) {
        ScreenSession s = session(http, user);
        synchronized (s) {
            s.openRoot(req.screen(), user);
            return s.state();
        }
    }

    @PostMapping("/windows/{window}/events")
    @Operation(summary = "What the user did on a window: field changes, then one event (clicked, select, losingFocus ...)")
    public Map<String, Object> event(@PathVariable String window, @RequestBody EventRequest req, HttpSession http,
                                     @AuthenticationPrincipal MarisUser user) {
        ScreenSession s = session(http, user);
        synchronized (s) {
            s.event(window, req.changes() == null ? List.of() : req.changes(), req.widget(), req.event(), req.value());
            return s.state();
        }
    }

    @PostMapping("/dialogs/{dialog}")
    @Operation(summary = "Answer the open prompt (OK, Yes, No, Cancel ...)")
    public Map<String, Object> answer(@PathVariable int dialog, @RequestBody AnswerRequest req, HttpSession http,
                                      @AuthenticationPrincipal MarisUser user) {
        ScreenSession s = session(http, user);
        synchronized (s) {
            s.answer(dialog, req.button());
            return s.state();
        }
    }

    @GetMapping("/state")
    @Operation(summary = "The open windows and prompt")
    public Map<String, Object> state(HttpSession http, @AuthenticationPrincipal MarisUser user) {
        ScreenSession s = session(http, user);
        synchronized (s) {
            return s.state();
        }
    }

    @GetMapping("/screens")
    @Operation(summary = "The screens the engine can run")
    public Set<String> screens() {
        return new TreeSet<>(engine.bundle().screenNames());
    }
}
