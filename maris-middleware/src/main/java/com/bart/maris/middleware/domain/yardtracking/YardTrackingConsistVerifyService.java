package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Verify / reset a pseudo train (MARIS_RUI/EGLSource/MRSYardTracking, old VHYT12I, opened from the car move window with
 * the track and positions of the cars picked). The old screen's rules are about 1,400 lines of per-car checks; this
 * service runs the screen's own statement groups on its records (YardTrackingConsistVerifyLogic), in the order the old
 * window runs them: VHYT12IS-INIT and VHYT12IS-LOAD-CARS when it opens (VHYT12A, VHYT12C per car, then the checks);
 * Verify runs VHYT12IS-VALID-CON (VHYT12B, then VHYT12IS-VERIFY: VHYT12A again and, when nothing moved, VHYT12D writes the
 * train); Reset runs VHYT12IS-RESET-CON (VHYT12E). The old message prompters become questions the caller answers and
 * sends back: the request is replayed from the opening with the answers given (the opening only reads).
 */
public class YardTrackingConsistVerifyService {
    static final String SCREEN = "VHYT12I";
    /** the prompters that ask Yes/No: answer key -> prompter; their Yes runs the group the screen connects, No closes the window */
    private static final Map<String, String> QUESTIONS = Map.of("carsInConsist", "CARS-IN-CON", "override", "CON-HAS-CARS");

    /** the values the car move window passes in (VHYT12IW-PASSVALUE) */
    public record Pass(String yard, String track, int fromPosition, int toPosition, String userGroup, String terminal) {}

    /** one car as the window shows it: the car with its orientation marks, colour, the consist it is in, location, home yard */
    public record Car(int position, String car, String colour, String consist, String consistColour, String location, String homeYard) {}

    /** a Yes/No question the old window asked (key to answer with, title and text) */
    public record Question(String key, String title, String text) {}

    /**
     * the window after the request: the cars, the train number typed, the message, the buttons' state, an open question
     * (null: none), a notice the old window showed in a box (null: none), and whether the window closed (No / cars moved)
     */
    public record State(List<Car> cars, String consist, String message, boolean verifyEnabled, boolean resetEnabled, Question question,
                        String notice, boolean closed) {}

    private final ScreenBundle screens;
    private final LegacyProgramService legacy;
    private final Clock clock;
    private final String terminal;
    private final Map<String, String> titles = new HashMap<>(), texts = new HashMap<>(), onYes = new HashMap<>();
    private final Set<String> closers = new java.util.HashSet<>();

    public YardTrackingConsistVerifyService(ScreenBundle screens, LegacyProgramService legacy, Clock clock, String terminal) {
        this.screens = screens;
        this.legacy = legacy;
        this.clock = clock;
        this.terminal = terminal;
        JsonNode def = screens.screen(SCREEN);
        if (def == null) throw new IllegalStateException("screen " + SCREEN + " not found");
        for (JsonNode v : def.path("vars")) {
            if (!v.path("type").asText().equals("MessagePrompter")) continue;
            String n = v.path("name").asText().toUpperCase(Locale.ROOT);
            titles.put(n, v.path("props").path("title").asText(""));
            texts.put(n, v.path("props").path("messageString").asText(""));
        }
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (!c.path("m").asText().equals("connectEventToAction") || a.size() < 4) continue;
            String from = a.get(0).path("id").asText().toUpperCase(Locale.ROOT), event = a.get(1).asText(), to = a.get(2).path("id").asText();
            if (!titles.containsKey(from)) continue;
            if (to.endsWith("__reference") && event.equals("okYesRetry")) onYes.put(from, to.substring(0, to.length() - 11));
            // Yes or No that clicks Return closes the window (CAR-MOVED_widget's OK, the questions' No)
            if (to.equals("screen.Push_Button2") && a.get(3).asText().equals("click")) closers.add(from + "." + event);
        }
    }

    /** the window as it opens (VHYT12IS-INIT, VHYT12IS-LOAD-CARS) and after the opening questions answered */
    public State open(Pass pass, Map<String, String> answers) {
        Run r = start(pass, answers);
        return r.state();
    }

    /** Verify: VHYT12IS-VALID-CON with the train number typed (and the questions answered) */
    public State verify(Pass pass, String consist, Map<String, String> answers) {
        Run r = start(pass, answers);
        if (r.stopped() || !r.enabled("VERIFY")) return r.state();         // the old Verify button is disabled: nothing happens
        String c = consist == null ? "" : consist.strip();
        r.logic.store("VHYT12IW", "CON-NO", c);
        // Text2 also feeds CON-NO-X (the number the range check reads); text that is not a number leaves it 0
        r.logic.store("VHYT12IW", "CON-NO-X", c.matches("\\d{1,3}") ? Integer.parseInt(c) : 0);
        r.step("VHYT12IS-VALID-CON");
        return r.state();
    }

    /** Reset: VHYT12IS-RESET-CON (VHYT12E takes the cars off their train) */
    public State reset(Pass pass, Map<String, String> answers) {
        Run r = start(pass, answers);
        if (r.stopped() || !r.enabled("RESET")) return r.state();          // the old Reset button is disabled: nothing happens
        r.step("VHYT12IS-RESET-CON");
        return r.state();
    }

    private Run start(Pass pass, Map<String, String> answers) {
        YardTrackingConsistVerifyLogic logic = new YardTrackingConsistVerifyLogic(screens, SCREEN, legacy, clock);
        String group = pass.userGroup() == null ? "" : pass.userGroup();
        String term = pass.terminal() == null || pass.terminal().isBlank() ? terminal : pass.terminal();
        logic.signOn(group, term);
        logic.store("VHYT12IW-PASSVALUE", "YRD-TRKG-YRD", nz(pass.yard()));
        logic.store("VHYT12IW-PASSVALUE", "YRD-SUB-LOC", nz(pass.track()));
        logic.store("VHYT12IW-PASSVALUE", "FROM-POS", pass.fromPosition());
        logic.store("VHYT12IW-PASSVALUE", "TO-POS", pass.toPosition());
        logic.store("VHYT12IW-PASSVALUE", "USER-GROUP", group);
        logic.store("VHYT12IW-PASSVALUE", "TERM-LAST-UPD", term);
        Run r = new Run(logic, answers == null ? Map.of() : answers);
        r.step("VHYT12IS-INIT");
        r.step("VHYT12IS-LOAD-CARS");
        return r;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    /** one replay: runs groups, answers the prompters it opens with the answers given, stops at the first unanswered one */
    private final class Run {
        final YardTrackingConsistVerifyLogic logic;
        final Map<String, String> answers;
        Question question;
        String notice;
        boolean closed;
        int seen;

        Run(YardTrackingConsistVerifyLogic logic, Map<String, String> answers) {
            this.logic = logic;
            this.answers = answers;
        }

        boolean stopped() {
            return question != null || closed;
        }

        void step(String group) {
            if (stopped()) return;
            logic.run(group);
            while (seen < logic.prompted.size() && !stopped()) {
                String box = logic.prompted.get(seen++).toUpperCase(Locale.ROOT);
                String key = QUESTIONS.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(box)).map(Map.Entry::getKey).findFirst().orElse(null);
                if (key == null) {                                             // an OK box: its text, and Return when OK closes
                    notice = text(box);
                    if (closers.contains(box + ".okYesRetry")) closed = true;
                    continue;
                }
                String a = answers.getOrDefault(key, "");
                if (a.equalsIgnoreCase("yes")) {
                    String fn = onYes.get(box);
                    if (fn != null) step(fn);
                } else if (a.equalsIgnoreCase("no")) {
                    closed = closers.contains(box + ".noAbort");
                } else {
                    question = new Question(key, titles.getOrDefault(box, ""), text(box));
                }
            }
        }

        /** a prompter's text: its own messageString, or the field the screen binds to it (PROMPT-MSG) */
        String text(String box) {
            String own = texts.getOrDefault(box, "");
            if (!own.isBlank()) return own;
            for (JsonNode c : screens.screen(SCREEN).path("connections")) {
                JsonNode a = c.path("a");
                if (a.size() >= 4 && a.get(2).path("id").asText().equalsIgnoreCase(box) && a.get(3).asText().equals("messageString")) {
                    String f = a.get(1).asText();
                    return logic.text(a.get(0).path("id").asText(), f.endsWith(" data") ? f.substring(0, f.length() - 5) : f).strip();
                }
            }
            return "";
        }

        State state() {
            List<Car> cars = new ArrayList<>();
            for (int k = 1; k <= 10; k++) {
                String button = logic.text("VHYT12IW", "BUTTON-" + k).strip();
                String con = logic.text("VHYT12IW", String.format("CON-%02d", k)).strip();
                if (button.isEmpty() && logic.text("VHYT12IW", "CAR-NO-" + k).isEmpty()) continue;
                cars.add(new Car(k, button, logic.text("VHYT12IW-COLOR", "CAR-" + k + "-COLOR"), con, logic.text("VHYT12IW-COLOR", "CON-" + k + "-COLOR"),
                        logic.text("VHYT12IW", "CAR-" + k + "-CURR-LOC").strip(), logic.text("VHYT12IW", "HOME-YRD-" + k)));
            }
            return new State(cars, logic.text("VHYT12IW", "CON-NO"), logic.text("VHYT12IW-MSG", "FINAL-MESSAGE").strip(),
                    enabled("VERIFY"), enabled("RESET"), question, notice, closed);
        }

        /** the last of the screen's ENABLE-x / DISABLE-x triggers written (the button starts enabled) */
        boolean enabled(String what) {
            boolean on = true;
            for (String f : logic.fieldsWritten) {
                if (f.endsWith(".ENABLE-" + what)) on = true;
                else if (f.endsWith(".DISABLE-" + what)) on = false;
            }
            return on;
        }
    }

    /** the programs the window calls that write (never called when only opening) */
    public static final List<String> WRITERS = List.of("VHYT12D", "VHYT12E");
}
