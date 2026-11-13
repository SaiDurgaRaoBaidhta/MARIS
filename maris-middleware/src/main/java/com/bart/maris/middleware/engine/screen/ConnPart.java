package com.bart.maris.middleware.engine.screen;

import java.util.List;

/** An event-to-action connection as a part: "result" / "normalResult" hold the action's last return value. */
public final class ConnPart extends Part {
    Object result;

    ConnPart(String name, ScreenSession session) {
        super(name, session);
    }

    void returned(Object r) {
        result = r;
        signal("result", r);
        signal("normalResult", r);
    }

    @Override
    public Object get(String attr) {
        return attr.equals("result") || attr.equals("normalResult") ? result : null;
    }

    @Override
    public void set(String attr, Object value) {
    }

    @Override
    public boolean settable(String attr) {
        return false;
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        return null;
    }
}
