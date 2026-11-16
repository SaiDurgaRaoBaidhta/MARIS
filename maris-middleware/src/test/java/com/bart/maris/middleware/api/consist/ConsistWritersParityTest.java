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
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The consist maintenance writers, each reached on a success path and stopped on a refusal path, old screen (engine)
 * against the clean API with the same scripted backend: make train VHMT012 (new consist) / VHMT013 (existing),
 * combine VHCM015, clear train VHCT012 / VHCT017 / VHCT018, change consist VHCC013 / VHCC017, split VHSCX. The records
 * every program got (writers included) and the screen's message are compared.
 */
class ConsistWritersParityTest {
    /** what one program answers: its records back, with fields set; a different answer per consist number if given */
    record Answer(int urc, int rows, Map<String, Map<String, Object>> set, Map<String, Answer> byConsist) {
        static Answer of(int rows, Map<String, Map<String, Object>> set) { return new Answer(0, rows, set, Map.of()); }
        Answer per(String consist, Answer a) { Map<String, Answer> m = new HashMap<>(byConsist); m.put(consist, a); return new Answer(urc, rows, set, m); }
    }

    static Parity backend(Map<String, Answer> answers) {
        Parity p = new Parity();
        List<String> programs = new ArrayList<>(ConsistMaintenanceParityTest.PROGRAMS);
        programs.add("VH01A01");
        for (String prog : programs) {
            Function<Map<String, Map<String, Object>>, Map<String, Object>> f = in -> {
                Answer a = answers.getOrDefault(prog, Answer.of(1, Map.of()));
                for (var rec : in.values()) {
                    Object cn = rec.get("conNo");
                    if (cn != null && a.byConsist().containsKey(cn.toString().strip())) { a = a.byConsist().get(cn.toString().strip()); break; }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : in.entrySet()) if (!e.getKey().equals("XDCONTROL")) out.put(e.getKey(), new HashMap<>(e.getValue()));
                for (var e : a.set().entrySet()) {
                    @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) out.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                    r.putAll(e.getValue());
                }
                out.put("XDCONTROL", Map.of("urc", a.urc(), "urowcnt", a.rows()));
                return out;
            };
            p.on(prog, f);
        }
        return p;
    }

    // ---- data -----------------------------------------------------------------------------------------------------

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    static Map<String, Map<String, Object>> recs(Object... kv) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) kv[i + 1];
            out.put((String) kv[i], v);
        }
        return out;
    }

    static Map<String, Object> mt014Car(String no, String stat) {
        return m("carNo", no, "carType", "A2", "vehStat", stat, "whereIsCar", "YRD", "currLoc", "CY", "atoPresentInd", "Y", "trainIncdInd", "N",
                "conNo", "", "dashNo", 0, "vagenOrientation", "<", "softVer", "");
    }

    static Map<String, Answer> makeTrain(boolean exists, String car1Status) {
        Map<String, Answer> a = new HashMap<>();
        a.put("VHMT011", Answer.of(1, exists ? recs("VHMT011_CRIT", m("noRecordFound", "N"), "VHMT011_DATA", m("conStat", "OUT", "dashNo", 1))
                : recs("VHMT011_CRIT", m("noRecordFound", "Y"))));
        a.put("VHMT014", Answer.of(2, recs("VHMT014_DATA", m("dataRow", List.of(mt014Car("1101", car1Status), mt014Car("1102", "GO"))))));
        return a;
    }

    static Map<String, Answer> combine(String combineTrainStat) {
        Map<String, Answer> a = new HashMap<>();
        a.put("VHCM011", Answer.of(1, recs("VHCM011_CRIT", m("noRecordFound", "N"), "VHCM011_DATA", m("conStat", "SRV", "dashNo", 1, "conSize", 2,
                "timeInsrv", "08:00:00", "dateInsrv", "11-15-2026", "dateInsrvRev", "2026-11-15"))));
        a.put("VHCM012", new Answer(100, 0, Map.of(), Map.of()));
        a.put("VHCM016", new Answer(100, 0, Map.of(), Map.of()));
        a.put("VHCM017", Answer.of(2, recs("VHCM017_CRIT", m("yrdIdx", 2), "VHCM017_DATA", m("dataRow", List.of(m("originLoc", "CY", "combLoc", "CY"),
                m("originLoc", "HY", "combLoc", "HY"))))));
        a.put("VHCM018", Answer.of(1, combineTrainStat == null ? recs("VHCM018_CRIT", m("noRecordFound", "Y"))
                : recs("VHCM018_CRIT", m("noRecordFound", "N"), "VHCM018_DATA", m("conStat", combineTrainStat))));
        return a;
    }

    static Map<String, Object> ct015Car(String no, int order, String stat) {
        return m("dashNo", 1, "dateInsrv", "11-15-2026", "timeInsrv", "08:00:00", "dateInsrvRev", "2026-11-15", "carNo", no, "conNo", "201", "conSize", 2,
                "carOrder", order, "conStat", "SRV", "carType", "A2", "currLoc", "CY", "vehStat", stat, "vehStatPrev", "GO", "homeYrd", "CY", "cumHrs", 100,
                "hrsToNextPm", 50, "hrsToNextPmMax", 500, "warInd", "N");
    }

    static Map<String, Answer> clear(String conStat) {
        Map<String, Answer> a = new HashMap<>();
        a.put("VH01A01", Answer.of(1, recs("VH01A01_DATA", m("numYrd", 2, "yardRow", List.of(m("yrd", "CY", "yrdLoc", "CY", "homeYrdInd", "Y", "yrdDesc", "CONCORD"),
                m("yrd", "HY", "yrdLoc", "HY", "homeYrdInd", "Y", "yrdDesc", "HAYWARD"))))));
        a.put("VHCT011", Answer.of(1, recs("VHCT011_CRIT", m("noRecordFound", "N"), "VHCT011_DATA", m("conStat", conStat, "timeInsrv", "08:00:00",
                "dateInsrvRev", "2026-11-15"))));
        a.put("VHCT013", Answer.of(0, Map.of()));
        a.put("VHCT015", Answer.of(2, recs("VHCT015_CRIT", m("histCount", 2), "VHCT015_DATA", m("dataRow", List.of(ct015Car("1101", 1, "USABLE"),
                ct015Car("1102", 2, "GO"))))));
        Map<String, Object> incd = m("incdNo", "Y1234567", "vehIncdStat", "OPN", "sympCd", "0100", "incdType", "YRD");
        a.put("VHCT016", Answer.of(1, recs("VHCT016_DATA", incd)));
        Map<String, Object> dvs = new HashMap<>(incd);
        dvs.put("incdCountX", 1);
        a.put("VHCT019", Answer.of(1, recs("VHCT019_DATA", dvs)));
        return a;
    }

    static List<Map<String, Object>> ccCars(boolean withStat) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> r = m("dashNo", 1, "dateInsrv", "11-15-2026", "timeInsrv", "08:00:00", "dateInsrvRev", "2026-11-15", "conSize", 2,
                    "carNo", "110" + i, "carOrder", i, "carType", "A2", "whereIsCar", "CON", "currLoc", "CY");
            if (withStat) { r.put("conStat", "SRV"); r.put("vehStat", "GO"); r.put("vagenOrientation", i == 1 ? "<" : ">"); }
            out.add(r);
        }
        return out;
    }

    static Map<String, Answer> change(boolean unchanged) {
        Map<String, Answer> a = new HashMap<>();
        a.put("VHCC011", Answer.of(2, recs("VHCC011_CRIT", m("noRecordFound", "N"), "VHCC011_DATA", m("dataRow", ccCars(true)))));
        List<Map<String, Object>> c16 = ccCars(false);
        c16.forEach(r -> { r.put("vehStat", "GO"); r.put("vagenOrientation", "<"); });
        a.put("VHCC016", Answer.of(2, recs("VHCC016_CRIT", m("noRecordFound", "N"), "VHCC016_DATA", m("dataRow", c16, "conStat", "SRV"))));
        a.put("VHMT014", Answer.of(2, recs("VHMT014_DATA", m("dataRow", List.of(mt014Car("1201", "GO"), mt014Car("1102", "GO"))))));
        if (unchanged) {
            List<Map<String, Object>> c15 = ccCars(false);
            c15.forEach(r -> r.put("conStat", "SRV"));
            a.put("VHCC015", Answer.of(2, recs("VHCC015_CRIT", m("noRecordFound", "N"), "VHCC015_DATA", m("dataRow", c15))));
        }
        return a;
    }

    static Map<String, Answer> split(String carType) {
        Map<String, Answer> a = new HashMap<>();
        List<Map<String, Object>> cars = new ArrayList<>();
        for (int i = 1; i <= 6; i++) cars.add(m("dateInsrvRev", "2026-11-15", "dateInsrv", "11-15-2026", "conNo", "201", "conStat", "SRV", "dashNo", 1,
                "timeInsrv", "08:00:00", "conSize", 6, "originLoc", "CY", "originLocSwap", "", "carNo", "110" + i, "carOrder", i, "carOrientation", i % 2 == 1 ? "<" : ">"));
        a.put("VHXCONB", Answer.of(6, recs("VHXCONB_DATA", m("dataRow", cars))).per("305", new Answer(100, 0, Map.of(), Map.of())));
        a.put("VHTS1A9", Answer.of(1, recs("VHTS1A9_DATA", m("conNo", "201", "conStat", "SRV", "maxDashNo", 1, "vagenFound", "N")))
                .per("305", Answer.of(0, recs("VHTS1A9_DATA", m("conNo", "", "conStat", "", "maxDashNo", 0, "vagenFound", "N")))));
        a.put("VHSC2A1", Answer.of(2, recs("VHSC2A1_DATA", m("dataRow", List.of(m("carType", carType, "vehStat", "GO", "carNo", "1104"),
                m("carType", carType, "vehStat", "GO", "carNo", "1105"))))));
        return a;
    }

    // ---- cases ----------------------------------------------------------------------------------------------------

    record Case(String screen, String messageWidget, List<Step> steps, Map<String, Boolean> answers, Map<String, Answer> backend,
                Function<ConsistMaintenanceService, ConsistMaintenanceService.Result> api, String writer, boolean written, String[] via) {}

    static final String G = "BRIGHTCO";
    static final Map<String, Boolean> YES = Map.of("vhct01_con_trouble", true, "vhct01_open_car_incd", true, "vhct01_con_incd", true, "msgSplit", true);

    static List<Step> mtSteps() {
        return List.of(Step.type("ConNotxt1", "201"), Step.leave("ConNotxt1"), Step.type("DateSrvtxt1", "11-15-2026"), Step.type("TimeSrvTXT1", "10.30"),
                Step.type("CarsNumTXT1", "1101"), Step.type("CarsNumTXT2", "1102"), Step.press("Create_btn1"));
    }

    static List<Step> cmSteps() {
        return List.of(Step.type("Text11", "201"), Step.leave("Text11"), Step.type("Text31", "305"), Step.type("Text2", "B"), Step.leave("Text2"),
                Step.type("Text411", "201"), Step.type("Text511", "23.59"), Step.type("Text3", "CY"), Step.press("Push_Button4"));
    }

    static List<Step> ctSteps() {
        return List.of(Step.type("Text1", "201"), Step.type("Text2", "23.59"), Step.type("Text13", "CY"), Step.type("Text14", "N"), Step.press("Push_Button3"));
    }

    static List<Step> ccSteps() {
        return List.of(Step.type("Text1", "201"), Step.leave("Text1"), Step.type("Text15", "1201"), Step.type("Text16", "1102"), Step.press("Push_Button1"));
    }

    static List<Step> scSteps() {
        return List.of(Step.type("txtConNo", "201"), Step.leave("txtConNo"), Step.type("txtLeftCon", "201"), Step.type("txtRightCon", "305"),
                Step.press("pbtSplitButton4"), Step.type("txtSplitTime", "2359"), Step.type("txtSplitLocation", "CY"), Step.press("pbtUpdate"));
    }

    static Case of(String name) {
        return switch (name) {
            case "makeTrainNew" -> new Case("VHMT01I", "ConMsgTXT1", mtSteps(), Map.of(), makeTrain(false, "GO"),
                    s -> s.makeTrain(G, "201", "11-15-2026", "10.30", List.of("1101", "1102"), Map.of()), "VHMT012", true, null);
            case "makeTrainExisting" -> new Case("VHMT01I", "ConMsgTXT1", mtSteps(), Map.of(), makeTrain(true, "GO"),
                    s -> s.makeTrain(G, "201", "11-15-2026", "10.30", List.of("1101", "1102"), Map.of()), "VHMT013", true, null);
            case "makeTrainNoGoCar" -> new Case("VHMT01I", "ConMsgTXT1", mtSteps(), Map.of(), makeTrain(false, "NO GO"),
                    s -> s.makeTrain(G, "201", "11-15-2026", "10.30", List.of("1101", "1102"), Map.of()), "VHMT012", false, null);
            case "combine" -> new Case("VHCM01I", "Text1", cmSteps(), Map.of(), combine(null),
                    s -> s.combine(G, "201", "305", "B", "201", "23.59", "CY", Map.of()), "VHCM015", true, null);
            case "combineChangedMeanwhile" -> new Case("VHCM01I", "Text1", cmSteps(), Map.of(), combine("OUT"),
                    s -> s.combine(G, "201", "305", "B", "201", "23.59", "CY", Map.of()), "VHCM015", false, null);
            case "clear" -> new Case("VHCT01I", "Text15", ctSteps(), YES, clear("SRV"),
                    s -> s.clear(G, "201", "23.59", "CY", "N", YES), "VHCT012", true, new String[]{"VH001I", "Push_Button3"});
            case "clearIncidents" -> new Case("VHCT01I", "Text15", ctSteps(), YES, clear("SRV"),
                    s -> s.clear(G, "201", "23.59", "CY", "N", YES), "VHCT017", true, new String[]{"VH001I", "Push_Button3"});
            case "clearDvs" -> new Case("VHCT01I", "Text15", ctSteps(), YES, clear("SRV"),
                    s -> s.clear(G, "201", "23.59", "CY", "N", YES), "VHCT018", true, new String[]{"VH001I", "Push_Button3"});
            case "clearNotInService" -> new Case("VHCT01I", "Text15", ctSteps(), YES, clear("OUT"),
                    s -> s.clear(G, "201", "23.59", "CY", "N", YES), "VHCT012", false, new String[]{"VH001I", "Push_Button3"});
            case "change" -> new Case("VHCC01I", "Text35", ccSteps(), Map.of(), change(true),
                    s -> s.change(G, "201", List.of("1201", "1102"), Map.of()), "VHCC013", true, null);
            case "changeConfigurationChanged" -> new Case("VHCC01I", "Text35", ccSteps(), Map.of(), change(false),
                    s -> s.change(G, "201", List.of("1201", "1102"), Map.of()), "VHCC013", false, null);
            case "changeLogged" -> new Case("VHCC01I", "Text35", ccSteps(), Map.of(), change(false),
                    s -> s.change(G, "201", List.of("1201", "1102"), Map.of()), "VHCC017", true, null);
            case "split" -> new Case("VHSC01I", "txtFooter", scSteps(), YES, split("C1"),
                    s -> s.split(G, "201", 4, "201", "305", "2359", "CY", YES), "VHSCX", true, null);
            case "splitNotAtCCars" -> new Case("VHSC01I", "txtFooter", scSteps(), YES, split("A2"),
                    s -> s.split(G, "201", 4, "201", "305", "2359", "CY", YES), "VHSCX", false, null);
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"makeTrainNew", "makeTrainExisting", "makeTrainNoGoCar", "combine", "combineChangedMeanwhile", "clear", "clearIncidents",
            "clearDvs", "clearNotInService", "change", "changeConfigurationChanged", "changeLogged", "split", "splitNotAtCCars"})
    void writersMatchTheOldScreen(String name) {
        Case c = of(name);
        Parity p = backend(c.backend());
        ConsistRulesOld.screen(c.screen());
        ConsistRulesOld.via(c.via() == null ? null : c.via()[0], c.via() == null ? null : c.via()[1]);
        ScreenSession s;
        try {
            s = ConsistRulesOld.old(p, c.screen(), c.steps(), c.answers());
        } finally {
            ConsistRulesOld.via(null, null);
        }
        String oldMessage = ConsistRulesOld.value(s, c.screen(), c.messageWidget());
        var oldSent = ConsistRulesOld.byProgram(p.sent);
        assertThat(oldSent.containsKey(c.writer())).as(name + ": the old screen " + (c.written() ? "calls " : "does not call ") + c.writer()).isEqualTo(c.written());
        p.clear();
        var r = c.api().apply(new ConsistMaintenanceService(ConsistRulesOld.screens(p)));
        assertThat(ConsistRulesOld.byProgram(p.sent)).as(name + " records sent").isEqualTo(oldSent);
        assertThat(r.message().strip()).as(name + " message").isEqualTo(oldMessage);
    }
}
