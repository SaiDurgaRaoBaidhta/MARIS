package com.bart.maris.middleware.engine.screen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ViewWrapper: a child screen of this screen (e.g. VHIC31I inside VHIC30I). The child is created on the first
 * openWidget and kept (with its record state) for later opens. Events the parent listens to on the wrapper come from
 * the child's screen (promoted events) and its window (openedWidget, closedWidget ...).
 */
public final class WrapperPart extends Part {
    final String viewClass;
    private final ScreenInstance parent;
    ScreenInstance child;
    private final Set<String> forwarded = new HashSet<>();

    WrapperPart(String name, String viewClass, ScreenInstance parent) {
        super(name, parent.session);
        this.viewClass = viewClass;
        this.parent = parent;
    }

    String childScreen() {
        int dot = viewClass.lastIndexOf('.');
        return dot < 0 ? viewClass : viewClass.substring(dot + 1);
    }

    ScreenInstance child() {
        if (child == null) {
            child = session.create(childScreen(), parent);
            forward();
            parent.align(this);                       // re-run the initial sync of every connection touching the wrapper
        }
        return child;
    }

    private void forward() {
        for (String ev : List.copyOf(events())) {
            if (!forwarded.add(ev)) continue;
            Part src = child.screenPart.listensPromoted(ev) ? child.screenPart : child.shell();
            if (src == null) continue;
            src.on(ev, (v, has) -> { if (has) signal(ev, v); else signal(ev); });
            if (src != child.screenPart) child.screenPart.on(ev, (v, has) -> { if (has) signal(ev, v); else signal(ev); });
        }
    }

    @Override
    public Object get(String attr) {
        if (attr.equals("self")) return child;
        return child == null ? null : child.screenPart.get(attr);
    }

    @Override
    public void set(String attr, Object value) {
        if (child != null) child.screenPart.set(attr, value);
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        switch (action) {
            case "openWidget", "openApplicationModalWidget" -> {
                child().open();
                return null;
            }
            case "closeWidget" -> {
                if (child != null) child.close();
                return null;
            }
            default -> {
                return child().screenPart.perform(action, args, invokedFromEvent);
            }
        }
    }
}
