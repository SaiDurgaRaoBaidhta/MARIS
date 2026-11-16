package com.bart.maris.middleware.api.consist;

import com.bart.maris.middleware.domain.consist.ConsistRuleScreens;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parity helpers for the consist screens run by {@link ConsistRuleScreens}: the old screen run by the engine with the
 * same steps, its widgets read back, and the records each side sent compared program by program.
 */
final class ConsistRulesOld {
    private ConsistRulesOld() {}

    static ConsistRuleScreens screens(Parity p) {
        return new ConsistRuleScreens(Parity.bundle(), p.legacy(), p.messages(), Clock.systemDefaultZone(), "MARIS-UI");
    }

    /** the old screen opened from the menu for the test user, then the steps; a question box is answered as given */
    static ScreenSession old(Parity p, String screen, List<ConsistRuleScreens.Step> steps, Map<String, Boolean> answers) {
        ScreenSession s = p.oldScreens();
        if (menu != null) {                                     // opened from the vehicle menu, which hands down its tables
            s.openRoot(menu[0], p.user);
            answer(s, answers);
            p.clear();
            s.event(windowId(s, menu[0]), List.of(), menu[1], "clicked", null);
            for (int i = 0; i < p.sent.size(); i++) if (p.sent.get(i).getKey().equals("VH01A02")) { p.sent.remove(i); break; }   // the menu's access check
        } else s.openRoot(screen, p.user);
        String id = windowId(s, screen);
        answer(s, answers);
        for (ConsistRuleScreens.Step st : steps) {
            if (st.event() == null) s.event(id, List.of(new ScreenSession.Change(st.widget(), "object", st.value())), null, null, null);
            else s.event(id, List.of(), st.widget(), st.event(), null);
            answer(s, answers);
        }
        return s;
    }

    /** answers open question boxes from the map (by prompter name); other boxes are closed with OK */
    @SuppressWarnings("unchecked")
    private static void answer(ScreenSession s, Map<String, Boolean> answers) {
        for (int guard = 0; guard < 20; guard++) {
            Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
            if (d == null) return;
            String message = String.valueOf(d.get("message")).strip();
            Boolean yes = null;
            if (answers != null) for (var e : answers.entrySet()) {
                String text;
                try { text = promptText(e.getKey()); } catch (IllegalStateException notOnThisScreen) { continue; }
                if (message.equalsIgnoreCase(text)) yes = e.getValue();
            }
            List<String> buttons = (List<String>) d.get("buttons");
            String button = yes == null ? buttons.get(0) : yes ? buttons.get(0) : buttons.get(buttons.size() - 1);
            s.answer((Integer) d.get("id"), button);
        }
    }

    private static String screenOf = "";
    private static String[] menu;

    /** the next old screen opens from this menu button (e.g. VH001I's Push_Button3 opens VHCT01I), or (null) on its own */
    static void via(String menuScreen, String button) {
        menu = menuScreen == null ? null : new String[]{menuScreen, button};
    }

    private static String promptText(String prompter) {
        return Parity.bundle().promptText(screenOf, prompter).strip();
    }

    static void screen(String screen) {
        screenOf = screen;
    }

    @SuppressWarnings("unchecked")
    static String windowId(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (w.get("screen").toString().equalsIgnoreCase(screen)) return w.get("id").toString();
        }
        throw new AssertionError(screen + " is not open");
    }

    /** a widget's value on the old screen */
    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!w.get("screen").toString().equalsIgnoreCase(screen)) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            Object v = x == null ? null : x.get("value");
            return v == null ? "" : v.toString().strip();
        }
        return "";
    }

    /**
     * a record field of the old screen, as the rules left it. The engine does not refresh a text box when the rules
     * write only the sub-fields of the field it shows (VHCY01I's message parts MSG-2..MSG-12), the old runtime did;
     * so messages built from parts are compared from the record.
     */
    static String field(ScreenSession s, String screen, String var, String field) {
        Object v = s.window(windowId(s, screen)).recordPart(var).get(field + " data");
        return v == null ? "" : com.bart.maris.middleware.engine.runtime.Conv.rtrim(com.bart.maris.middleware.engine.runtime.Conv.string(v)).strip();
    }

    /** the records sent, by program, in call order (XDCONTROL left out: it carries the last call's answer back) */
    static Map<String, List<Map<String, Map<String, Object>>>> byProgram(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent) {
        Map<String, List<Map<String, Map<String, Object>>>> out = new LinkedHashMap<>();
        for (var e : sent) {
            if (e.getKey().equals("VH01A01")) continue;          // the vehicle menu's tables: loaded by the menu (old) or the API (new)
            Map<String, Map<String, Object>> recs = new LinkedHashMap<>(e.getValue());
            recs.remove("XDCONTROL");
            out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(recs);
        }
        return out;
    }
}
