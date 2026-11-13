package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.EngineException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The old screens one user has open: their windows, the event queue that a prompt pauses, and the open prompt.
 * Kept in the HTTP session; every method is called with the session's lock held (see ScreenEngine).
 */
public final class ScreenSession {
    private static final Logger log = LoggerFactory.getLogger(ScreenSession.class);

    final ScreenEngine engine;
    public final String user;
    private final Map<String, ScreenInstance> instances = new LinkedHashMap<>();
    private final List<ScreenInstance> windows = new ArrayList<>();
    private int next = 1;
    private boolean paused;
    private final List<Object[]> queue = new ArrayList<>();
    private Dialog dialog;
    private final List<String> warnings = new ArrayList<>();
    private final List<Map<String, Object>> calls = new ArrayList<>();
    private int events;

    public ScreenSession(ScreenEngine engine, String user) {
        this.engine = engine;
        this.user = user;
    }

    /** a prompt waiting for the user: which events its buttons fire */
    public record Dialog(int id, WidgetPart prompter, String message, String title, int buttonType, int iconType, List<String[]> buttons) {}

    // ---- events --------------------------------------------------------------------------------------------

    void dispatch(Part p, String event, Object value, boolean has) {
        if (paused) { queue.add(new Object[]{p, event, value, has}); return; }
        if (++events > 2_000_000) throw new EngineException("the screen logic did not settle (event loop)");
        p.fire(event, value, has);
    }

    void prompt(WidgetPart prompter, String message, String title, int buttonType, int iconType) {
        paused = true;
        List<String[]> b = switch (buttonType) {
            case 2 -> List.of(new String[]{"OK", "okYesRetry"}, new String[]{"Cancel", "cancelIgnore"});
            case 3 -> List.of(new String[]{"Retry", "okYesRetry"}, new String[]{"Cancel", "cancelIgnore"});
            case 4 -> List.of(new String[]{"Abort", "noAbort"}, new String[]{"Retry", "okYesRetry"}, new String[]{"Ignore", "cancelIgnore"});
            case 5 -> List.of(new String[]{"Yes", "okYesRetry"}, new String[]{"No", "noAbort"});
            case 6 -> List.of(new String[]{"Yes", "okYesRetry"}, new String[]{"No", "noAbort"}, new String[]{"Cancel", "cancelIgnore"});
            default -> List.<String[]>of(new String[]{"OK", "okYesRetry"});
        };
        dialog = new Dialog(next++, prompter, message == null ? "" : message, title == null ? "" : title, buttonType, iconType, b);
    }

    void warn(String w) {
        log.debug("screen engine: {}", w);
        if (warnings.size() < 200) warnings.add(w);
    }

    void called(Map<String, Object> c) {
        calls.add(c);
    }

    // ---- windows -------------------------------------------------------------------------------------------

    ScreenInstance create(String screen, ScreenInstance parent) {
        JsonNode def = engine.bundle().screen(screen);
        if (def == null) throw new EngineException("screen " + screen + " is not in the screen bundle");
        ScreenInstance s = new ScreenInstance("w" + next++, def.path("name").asText(screen), def, engine.bundle(), this, parent);
        instances.put(s.id, s);
        s.setup();
        return s;
    }

    void opened(ScreenInstance s) {
        windows.remove(s);
        windows.add(s);
    }

    void closed(ScreenInstance s) {
        windows.remove(s);
    }

    void focusWindow(ScreenInstance s) {
        opened(s);
    }

    // ---- what the API does ---------------------------------------------------------------------------------

    /** opens a screen from the menu: a fresh start, like opening it in the old application */
    public void openRoot(String screen, com.bart.maris.middleware.security.MarisUser user) {
        instances.clear();
        windows.clear();
        queue.clear();
        paused = false;
        dialog = null;
        warnings.clear();
        guarded(() -> {
            ScreenInstance root = create(screen, null);
            if (user != null) root.seedSignon(user.groupId(), engine.terminalId());
            root.open();
        });
    }

    public ScreenInstance window(String id) {
        ScreenInstance s = instances.get(id);
        if (s == null || !s.open) throw new EngineException("window " + id + " is not open");
        return s;
    }

    /** a user action on a window: field changes first (as the widgets commit them), then the event */
    public void event(String windowId, List<Change> changes, String widget, String event, Object value) {
        ScreenInstance s = instances.get(windowId);
        if (s == null || !s.open) return;               // a window that closed meanwhile: the UI gets the current state
        guarded(() -> {
            for (Change c : changes) {
                WidgetPart w = s.widget(c.widget());
                if (w == null) continue;
                switch (c.attr()) {
                    case "selection", "checked" -> w.set("selection", c.value());
                    case "selectionIndex" -> w.set("selectionIndex", c.value());
                    case "selectionIndices" -> w.set("selectionIndices", c.value());
                    default -> w.set("object", c.value());
                }
            }
            if (widget == null || event == null) return;
            if (widget.equals("@window") && event.equals("close")) { s.close(); return; }
            WidgetPart w = s.widget(widget);
            if (w == null) throw new EngineException(widget + " is not a widget of " + s.name);
            switch (event) {
                case "clicked" -> { if (w.enabled) w.signal("clicked"); }
                case "losingFocus", "gettingFocus", "defaultActionRequested", "doubleClicked" -> w.signal(event);
                case "select" -> w.set("selectionIndex", value);
                default -> w.signal(event, value);
            }
        });
    }

    public record Change(String widget, String attr, Object value) {}

    /** the user pressed a button of the open prompt */
    public void answer(int dialogId, String button) {
        if (dialog == null || dialog.id() != dialogId) return;
        Dialog d = dialog;
        String ev = d.buttons().stream().filter(b -> b[0].equalsIgnoreCase(button) || b[1].equals(button)).map(b -> b[1]).findFirst().orElse("okYesRetry");
        guarded(() -> {
            dialog = null;
            paused = false;
            List<Object[]> q = new ArrayList<>(queue);
            queue.clear();
            if (d.prompter() != null) d.prompter().signal(ev);
            for (Object[] e : q) dispatch((Part) e[0], (String) e[1], e[2], (Boolean) e[3]);
        });
    }

    private void guarded(Runnable r) {
        events = 0;
        try {
            r.run();
        } catch (EngineException | IndexOutOfBoundsException | NullPointerException | IllegalArgumentException e) {
            log.warn("screen logic failed user={} windows={}: {}", user, windows.stream().map(w -> w.name).toList(), e.toString(), e);
            if (dialog == null) {
                paused = true;
                dialog = new Dialog(next++, null, "The screen could not finish this step: " + e.getMessage(), "MARIS", 1, 1,
                        List.<String[]>of(new String[]{"OK", "okYesRetry"}));
            }
        }
    }

    /** everything the UI needs: open windows (bottom to top) with their widgets, and the prompt if one is open */
    public Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> ws = new ArrayList<>();
        for (ScreenInstance s : windows) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id", s.id);
            w.put("screen", s.name);
            WidgetPart sh = s.shell();
            if (sh != null && sh.title != null) w.put("title", Conv.rtrim(sh.title));
            w.put("widgets", s.view());
            ws.add(w);
        }
        m.put("windows", ws);
        if (dialog != null) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", dialog.id());
            d.put("title", dialog.title());
            d.put("message", dialog.message());
            d.put("icon", dialog.iconType());
            d.put("buttons", dialog.buttons().stream().map(b -> b[0]).toList());
            m.put("dialog", d);
        }
        if (!calls.isEmpty()) m.put("calls", new ArrayList<>(calls));
        calls.clear();
        return m;
    }

    public List<String> warnings() {
        return warnings;
    }
}
