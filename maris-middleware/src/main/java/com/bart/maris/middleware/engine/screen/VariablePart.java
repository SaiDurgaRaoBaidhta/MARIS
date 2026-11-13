package com.bart.maris.middleware.engine.screen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VariablePart: holds a value (often a record's array field, or a list of selection indices).
 * Setting it signals every attribute anyone listens to, re-read from the new value, then "value".
 * Actions and unknown attributes go to the held value; events of the held value reach this part's listeners.
 */
public final class VariablePart extends Part {
    private Object value;
    private final Set<Part> forwarded = new HashSet<>();

    VariablePart(String name, ScreenSession session) {
        super(name, session);
    }

    public Object value() {
        return value;
    }

    @Override
    public Object get(String attr) {
        if (attr.equals("self") || attr.equals("value") || attr.equals("valueHolder")) return value;
        if (attr.equals("first")) return value instanceof List<?> l ? (l.isEmpty() ? null : l.get(0)) : null;
        if (attr.equals("last")) return value instanceof List<?> l ? (l.isEmpty() ? null : l.get(l.size() - 1)) : null;
        if (attr.equals("size")) return value instanceof List<?> l ? java.math.BigDecimal.valueOf(l.size()) : null;
        if (attr.equals("isEmpty")) return !(value instanceof List<?> l) || l.isEmpty();
        if (value instanceof Part p) return p.get(attr);
        return null;
    }

    @Override
    public void set(String attr, Object v) {
        if (attr.equals("self") || attr.equals("value") || attr.equals("valueHolder")) {
            if (v == null) return;
            value = v;
            if (v instanceof Part p && forwarded.add(p)) {
                for (String ev : events()) {
                    if (ev.equals("self") || ev.equals("value")) continue;
                    p.on(ev, (val, has) -> { if (value == p) { if (has) signal(ev, val); else signal(ev); } });
                }
            }
            for (String ev : List.copyOf(events())) {
                if (ev.equals("value")) continue;
                signal(ev, get(ev));
            }
            if (listens("value")) signal("value", value);
            return;
        }
        if (value instanceof Part p) p.set(attr, v);
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        if (value instanceof Part p) return p.perform(action, args, invokedFromEvent);
        if (invokedFromEvent && (action.equals("self") || action.equals("value"))) set(action, args.isEmpty() ? null : args.get(0));
        return null;
    }
}
