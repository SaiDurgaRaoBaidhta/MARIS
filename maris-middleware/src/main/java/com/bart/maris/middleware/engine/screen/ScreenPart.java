package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.runtime.Conv;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The "screen" variable of a Connections handler: its promoted attributes, actions and events. */
public final class ScreenPart extends Part {
    private final ScreenInstance screen;
    final Map<String, Object[]> attrs = new HashMap<>();      // name -> {part, attr}
    final Map<String, Object[]> actions = new HashMap<>();    // name -> {part, action}
    final java.util.Set<String> promotedEvents = new java.util.HashSet<>();

    boolean listensPromoted(String e) {
        return promotedEvents.contains(e) || attrs.containsKey(e);
    }

    ScreenPart(ScreenInstance screen) {
        super("screen", screen.session);
        this.screen = screen;
    }

    @Override
    public Object get(String attr) {
        Object[] a = attrs.get(attr);
        return a == null ? null : ((Part) a[0]).get((String) a[1]);
    }

    @Override
    public void set(String attr, Object value) {
        Object[] a = attrs.get(attr);
        if (a != null) ((Part) a[0]).set((String) a[1], value);
    }

    @Override
    public boolean settable(String attr) {
        return attrs.containsKey(attr);
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        Object[] a = actions.get(action);
        if (a != null) return ((Part) a[0]).perform((String) a[1], args, invokedFromEvent);
        switch (action) {
            case "message_title_iconType_buttonType_" -> {
                Args n = args instanceof Args x ? x : new Args();
                if (!(args instanceof Args)) n.addAll(args);
                Object buttons = n.named("buttonType", 3);
                Object icon = n.named("iconType", 2);
                session.prompt(null, Conv.rtrim(Conv.string(n.named("message", 0))), Conv.rtrim(Conv.string(n.named("title", 1))),
                        buttons == null || Conv.number(buttons).intValue() == 0 ? 1 : Conv.number(buttons).intValue(),
                        icon == null ? 0 : Conv.number(icon).intValue());
                return null;
            }
            case "openWidget", "openApplicationModalWidget" -> { screen.open(); return null; }
            case "closeWidget" -> { screen.close(); return null; }
            default -> {
                if (attrs.containsKey(action)) set(action, args.isEmpty() ? null : args.get(0));
                return null;
            }
        }
    }
}
