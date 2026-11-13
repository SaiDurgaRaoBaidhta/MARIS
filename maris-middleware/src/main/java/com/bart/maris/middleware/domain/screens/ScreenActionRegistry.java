package com.bart.maris.middleware.domain.screens;

import com.bart.maris.middleware.security.MarisUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a screen button. Only buttons with a translated {@link ScreenAction} reach the MARIS backend: the old screens
 * call their programs conditionally and many of them write data, so the middleware never runs a button's program
 * list blindly.
 */
@Service
public class ScreenActionRegistry {
    private static final Logger log = LoggerFactory.getLogger(ScreenActionRegistry.class);
    private final Map<String, ScreenAction> actions;

    public ScreenActionRegistry(List<ScreenAction> actions) {
        this.actions = actions.stream().collect(Collectors.toUnmodifiableMap(a -> key(a.screen(), a.control()), Function.identity()));
        log.info("{} screen actions connected to the MARIS backend: {}", this.actions.size(), connected());
    }

    public ActionResult run(String screen, String control, String caption, Map<String, String> fields, MarisUser user) {
        ScreenAction a = actions.get(key(screen, control));
        if (a == null) throw new ActionNotMigratedException(screen, control, caption);
        return a.run(fields == null ? Map.of() : fields, user);
    }

    /** "SCREEN/control" of every connected button. */
    public Set<String> connected() {
        return actions.values().stream().map(a -> a.screen() + "/" + a.control()).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String key(String screen, String control) {
        return screen.strip().toUpperCase(Locale.ROOT) + "/" + control.strip();
    }
}
