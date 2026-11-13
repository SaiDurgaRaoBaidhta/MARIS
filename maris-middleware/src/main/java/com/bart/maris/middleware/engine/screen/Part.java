package com.bart.maris.middleware.engine.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anything a connection can point at: a record, a function part, a variable, a widget, a child screen, a connection.
 * Mirrors the gt2 runtime: events fan out synchronously to handlers in registration order (EventMixin._signalEvent),
 * unless a prompt has paused event dispatch, in which case they are queued (Connection.pauseEvents).
 */
public abstract class Part {
    public interface Handler {
        void on(Object value, boolean hasValue);
    }

    public final String name;
    protected final ScreenSession session;
    private final Map<String, List<Handler>> handlers = new LinkedHashMap<>();

    protected Part(String name, ScreenSession session) {
        this.name = name;
        this.session = session;
    }

    public void on(String event, Handler h) {
        handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(h);
    }

    public boolean listens(String event) {
        List<Handler> l = handlers.get(event);
        return l != null && !l.isEmpty();
    }

    public java.util.Set<String> events() {
        return handlers.keySet();
    }

    /** signals an event without a value (payload = [sender]) */
    public void signal(String event) {
        session.dispatch(this, event, null, false);
    }

    /** signals an event with a value (payload = [sender, value]) */
    public void signal(String event, Object value) {
        session.dispatch(this, event, value, true);
    }

    /** called by the session to run the handlers now */
    void fire(String event, Object value, boolean hasValue) {
        List<Handler> l = handlers.get(event);
        if (l == null) return;
        for (Handler h : List.copyOf(l)) h.on(value, hasValue);
    }

    /** an attribute's value (null = no value) */
    public abstract Object get(String attr);

    /** sets an attribute; returns false when the part has no such attribute setter */
    public abstract void set(String attr, Object value);

    /** whether the attribute can be set (public interface has a setter) */
    public boolean settable(String attr) {
        return true;
    }

    /** runs an action; invokedFromEvent: an unknown action name that is an attribute sets it */
    public abstract Object perform(String action, List<Object> args, boolean invokedFromEvent);
}
