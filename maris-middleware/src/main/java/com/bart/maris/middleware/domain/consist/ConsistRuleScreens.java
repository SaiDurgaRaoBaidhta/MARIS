package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs one consist screen's rules for a clean API call: the screen opens for the signed-on group, then the steps the
 * user would take (values typed into its fields, buttons pressed) run in order, and what the screen would show comes
 * back. Each call starts from a freshly opened screen, so the API keeps no state between calls.
 */
public final class ConsistRuleScreens {
    /** a step: type {@code value} into {@code widget}, or (value null) fire {@code event} on {@code widget} */
    public record Step(String widget, String event, Object value) {
        public static Step type(String widget, Object value) { return new Step(widget, null, value == null ? "" : value); }
        public static Step press(String widget) { return new Step(widget, "clicked", null); }
        public static Step leave(String widget) { return new Step(widget, "losingFocus", null); }
        public static Step on(String widget, String event) { return new Step(widget, event, null); }
    }

    /** a question box the screen asks; the caller answers by sending {@code answers: {name: true|false}} */
    public record Question(String name, String text) {}

    /** what the screen shows after the steps: the fields asked for, its message boxes, and an open question */
    public record View(Map<String, String> fields, List<String> notices, Question question, List<String> programs) {
        public String field(String widget) { return fields.getOrDefault(widget, ""); }
    }

    private final ScreenBundle bundle;
    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;
    private final String terminal;

    public ConsistRuleScreens(ScreenBundle bundle, LegacyProgramService legacy, MessageResolver messages, Clock clock, String terminal) {
        this.bundle = bundle;
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
        this.terminal = terminal;
    }

    public ScreenBundle bundle() {
        return bundle;
    }

    /** the screen opened for a user group, before any step */
    public ConsistRuleRunner open(String screen, String group) {
        return open(screen, group, false);
    }

    /** the screen opened for a user group; {@code menuTables}: with the vehicle menu's tables (VH01A01) it was opened with */
    public ConsistRuleRunner open(String screen, String group, boolean menuTables) {
        ConsistRuleRunner logic = new ConsistRuleRunner(bundle, screen, legacy, clock, Set.of());
        logic.signOn(group == null ? "" : group, terminal, menuTables);
        if (menuTables) logic.menuTables();
        logic.open();
        return logic;
    }

    /** opens the screen, takes the steps, and reads the widgets asked for */
    public View run(String screen, String group, List<Step> steps, Map<String, Boolean> answers, List<String> show) {
        ConsistRuleRunner logic = open(screen, group);
        if (answers != null) answers.forEach(logic::answer);
        for (Step s : steps) {
            if (logic.unanswered != null) break;
            if (s.event() == null) logic.type(s.widget(), s.value());
            else logic.fire(s.widget(), s.event());
        }
        return view(logic, show);
    }

    /** what the logic's screen shows now */
    public View view(ConsistRuleRunner logic, List<String> show) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String w : show) fields.put(w, logic.shown(w));
        List<String> notices = new ArrayList<>();
        for (ConsistRuleRunner.Prompt p : logic.prompts) {
            if (p.question()) continue;
            if (p.name().equals("XXM0I")) {
                String code = logic.text("XXMESSAGE", "UMSGCODE");
                if (!code.isBlank()) notices.add(messages.resolve(code, List.of(logic.text("XXMESSAGE", "UMSGINS", 1),
                        logic.text("XXMESSAGE", "UMSGINS", 2), logic.text("XXMESSAGE", "UMSGINS", 3))).text());
            } else notices.add(p.text());
        }
        Question q = logic.unanswered == null ? null : new Question(logic.unanswered.name(), logic.unanswered.text());
        return new View(fields, notices, q, logic.calls.stream().map(Map.Entry::getKey).toList());
    }
}
