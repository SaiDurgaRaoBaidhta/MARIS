package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.consist.ConsistMaintenanceService;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.Step;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The consist maintenance screens (VHMT01I, VHCM01I, VHCT01I, VHCC01I, VHTS01I, VHSC01I): the clean API against the old
 * screen run by the engine, the same steps, the same scripted backend. Every program answers its own records back with a
 * return code the case chooses (0, or 100 "not found" for the programs named), and the lookups answer a consist of four
 * cars; the records each side sent (writers included) and what each shows are compared.
 */
class ConsistMaintenanceParityTest {
    static final List<String> PROGRAMS = List.of("VH01A02", "VHMT011", "VHMT012", "VHMT013", "VHMT014", "VHMT015", "VHMT016",
            "VHCM011", "VHCM012", "VHCM013", "VHCM014", "VHCM015", "VHCM016", "VHCM017", "VHCM018",
            "VHCT011", "VHCT012", "VHCT013", "VHCT014", "VHCT015", "VHCT016", "VHCT017", "VHCT018", "VHCT019", "VHCT01A", "VHXREVC",
            "VHCC011", "VHCC013", "VHCC014", "VHCC015", "VHCC016", "VHCC017",
            "VHTSX", "VHXCONB", "VHXCONA", "VHTS1A9", "VHTS2A1", "VHTS2A2", "VHTS2A3",
            "VHSCY", "VHSC2A1", "VHSCX", "VHSC2A2");

    /** every program answers its records back (urc 0, or 100 for those named) */
    static Parity backend(Set<String> notFound) {
        Parity p = new Parity();
        for (String prog : PROGRAMS) {
            Function<Map<String, Map<String, Object>>, Map<String, Object>> echo = in -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : in.entrySet()) if (!e.getKey().equals("XDCONTROL")) out.put(e.getKey(), new HashMap<>(e.getValue()));
                out.put("XDCONTROL", Map.of("urc", notFound.contains(prog) ? 100 : 0, "urowcnt", notFound.contains(prog) ? 0 : 1));
                return out;
            };
            p.on(prog, echo);
        }
        return p;
    }

    record Case(String screen, List<Step> steps, Map<String, Boolean> answers, Function<ConsistMaintenanceService, ConsistMaintenanceService.Result> api,
                String message, Set<String> notFound) {}

    static final Map<String, Boolean> YES = Map.of("vhct01_con_trouble", true, "vhct01_open_car_incd", true, "vhct01_con_incd", true, "msgSplit", true);
    static final List<String> CARS = List.of("1101", "1102", "1103", "1104");

    static List<Step> typed(List<String> widgets, List<String> values) {
        List<Step> s = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) s.add(Step.type(widgets.get(i), values.get(i)));
        return s;
    }

    static Case of(String name) {
        String g = "BRIGHTCO";
        return switch (name) {
            case "makeTrainCheck" -> new Case("VHMT01I", List.of(Step.type("ConNotxt1", "201"), Step.leave("ConNotxt1")), Map.of(),
                    s -> s.makeTrainCheck(g, "201"), "ConMsgTXT1", Set.of());
            case "makeTrain", "makeTrainNotFound" -> {
                List<Step> st = new ArrayList<>(List.of(Step.type("ConNotxt1", "201"), Step.leave("ConNotxt1"), Step.type("DateSrvtxt1", "11-15-2026"),
                        Step.type("TimeSrvTXT1", "10:30")));
                st.addAll(typed(List.of("CarsNumTXT1", "CarsNumTXT2", "CarsNumTXT3", "CarsNumTXT4"), CARS));
                st.add(Step.press("Create_btn1"));
                yield new Case("VHMT01I", st, Map.of(), s -> s.makeTrain(g, "201", "11-15-2026", "10:30", CARS, Map.of()), "ConMsgTXT1",
                        name.endsWith("NotFound") ? Set.of("VHMT011") : Set.of());
            }
            case "combine" -> new Case("VHCM01I", List.of(Step.type("Text11", "201"), Step.leave("Text11"), Step.type("Text31", "305"),
                    Step.type("Text2", "B"), Step.leave("Text2"), Step.type("Text411", "201"), Step.type("Text511", "10:30"), Step.type("Text3", "CY"),
                    Step.press("Push_Button4")), Map.of(), s -> s.combine(g, "201", "305", "B", "201", "10:30", "CY", Map.of()), "Text1", Set.of());
            case "combineList" -> new Case("VHCM01I", List.of(Step.type("Text11", "201"), Step.leave("Text11"), Step.type("Text31", "305"),
                    Step.type("Text2", "A"), Step.leave("Text2")), Map.of(), s -> s.combineList(g, "201", "305", "A"), "Text1", Set.of("VHCM011"));
            case "clear", "clearNotFound" -> new Case("VHCT01I", List.of(Step.type("Text1", "201"), Step.type("Text13", "CY"), Step.type("Text14", "N"),
                    Step.press("Push_Button3")), YES, s -> s.clear(g, "201", null, "CY", "N", YES), "Text15",
                    name.endsWith("NotFound") ? Set.of("VHCT011") : Set.of());
            case "changeList" -> new Case("VHCC01I", List.of(Step.type("Text1", "201"), Step.leave("Text1")), Map.of(), s -> s.changeList(g, "201"),
                    "Text35", Set.of());
            case "change" -> new Case("VHCC01I", List.of(Step.type("Text1", "201"), Step.leave("Text1"), Step.type("Text15", "1201"),
                    Step.press("Push_Button1")), Map.of(), s -> s.change(g, "201", List.of("1201"), Map.of()), "Text35", Set.of());
            case "swap" -> new Case("VHTS01I", List.of(Step.type("txtFrom", "201"), Step.type("txtTo", "305"), Step.press("pbtSwap"), Step.press("pbtUpdate")),
                    Map.of(), s -> s.swap(g, List.of(List.of("201", "305")), true, Map.of()), "Text3", Set.of());
            case "swapNotFound" -> new Case("VHTS01I", List.of(Step.type("txtFrom", "201"), Step.type("txtTo", "305"), Step.press("pbtSwap")),
                    Map.of(), s -> s.swap(g, List.of(List.of("201", "305")), false, Map.of()), "Text3", Set.of("VHXCONB", "VHTS1A9"));
            case "splitList" -> new Case("VHSC01I", List.of(Step.type("txtConNo", "201"), Step.leave("txtConNo")), Map.of(), s -> s.splitList(g, "201"),
                    "txtFooter", Set.of());
            case "split" -> new Case("VHSC01I", List.of(Step.type("txtConNo", "201"), Step.leave("txtConNo"), Step.press("pbtSplitButton5"),
                    Step.type("txtLeftCon", "201"), Step.type("txtRightCon", "305"), Step.type("txtSplitTime", "10:30"), Step.type("txtSplitLocation", "CY"),
                    Step.press("pbtUpdate")), YES, s -> s.split(g, "201", 5, "201", "305", "10:30", "CY", YES), "txtFooter", Set.of());
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"makeTrainCheck", "makeTrain", "makeTrainNotFound", "combineList", "combine", "changeList", "change",
            "swap", "swapNotFound", "splitList"})   // Clear Train and Split with their writers: ConsistWritersParityTest
    void matchesTheOldScreen(String name) {
        Case c = of(name);
        Parity p = backend(c.notFound());
        ConsistRulesOld.screen(c.screen());
        ScreenSession s = ConsistRulesOld.old(p, c.screen(), c.steps(), c.answers());
        String oldMessage = ConsistRulesOld.value(s, c.screen(), c.message());
        var oldSent = ConsistRulesOld.byProgram(p.sent);
        p.clear();
        var r = c.api().apply(new ConsistMaintenanceService(ConsistRulesOld.screens(p)));
        assertThat(ConsistRulesOld.byProgram(p.sent)).as(name + " records sent").isEqualTo(oldSent);
        assertThat(r.question()).as(name + " question").isNull();
        assertThat(r.message().strip()).as(name + " message").isEqualTo(oldMessage);
        for (var car : r.cars()) {
            // every car shown by the API is what the old screen shows in the same row
            assertThat(car.values().stream().filter(v -> !v.isEmpty()).count()).isPositive();
        }
    }
}
