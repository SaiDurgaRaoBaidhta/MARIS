package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudoService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Configure pseudo trains: the clean API against the old VHYT53I run by the engine, both against the same scripted backend.
 * The old screen is driven as a user would: the caller's yard on its pass record, Refresh, select a consist, Include,
 * type the new consist number, Create; every program it calls and every record it sends must be the API's.
 */
class YardReportsPseudoParityTest {
    private Parity p;
    private YardReportsPseudoService api;

    private static Map<String, Object> carX(String left, String car, String right) {
        return Map.of("lftOr", left, "carNo", car, "rgtOr", right);
    }

    /** a yard with consist 532 (cars of the given types / statuses) and consist 446 (one car, marked C) */
    private void backend(String[][] cars, int lUrc) {
        p = new Parity();
        p.on("VHYT53D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT53D_DATA", Map.of("dataRow", List.of(
                Map.of("yrdSubLoc", "ST-07", "yrdSubLocDesc", "STORAGE 7"), Map.of("yrdSubLoc", "T-95", "yrdSubLocDesc", "PSEUDO")))));
        p.on("VHYT53B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYT53B_DATA", Map.of("dataRow", List.of(
                Map.of("conNo", "532", "dashNo", 1, "maxPosnNo", cars.length, "yrdSubLoc", "T-95", "conInd", "P"),
                Map.of("conNo", "446", "dashNo", 0, "maxPosnNo", 1, "yrdSubLoc", "T-95", "conInd", "C")))));
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < cars.length; i++) {
            rows.add(Map.of("conNo", "532", "carNoX", carX(i == 0 ? "<" : "", cars[i][0], i == cars.length - 1 ? ">" : ""), "vehStat", cars[i][2],
                    "carType", cars[i][1], "atoPresentInd", cars[i][3], "trainIncdInd", cars[i][4], "homeYrd", "CY", "posnNo", i + 1));
        }
        rows.add(Map.of("conNo", "446", "carNoX", carX("", "3456", ">"), "vehStat", "GO", "carType", "A1", "atoPresentInd", "Y", "trainIncdInd", "N", "homeYrd", "HY", "posnNo", 1));
        p.on("VHYT53C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHYT53C_DATA", Map.of("dataRow", rows)));
        p.on("VHYT53L", in -> {
            Map<String, Object> data = new HashMap<>(in.get("VHYT53L_DATA"));
            data.put("conErr", "N");
            return Map.of("XDCONTROL", Map.of("urc", lUrc), "VHYT53L_DATA", data);
        });
        for (String w : List.of("VHYT53F", "VHYT53G", "VHYT53K")) p.on(w, in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHYT53H", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHYT53H_DATA", Map.of("moveSameTrk", "N")));
        api = new YardReportsPseudoService(p.legacy(), p.messages(), Parity.bundle(), "MARIS-UI");
    }

    private static final String[][] GOOD = {{"1001", "A1", "GO", "Y", "N"}, {"1002", "B1", "USABLE", "N", "N"}, {"1003", "C1", "GO", "Y", "N"}};

    @SuppressWarnings("unchecked")
    private static String windowId(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> w.get("screen").equals("VHYT53I"))
                .map(w -> (String) w.get("id")).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static String shown(ScreenSession s, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (w.get("screen").equals("VHYT53I")) {
                Object v = ((Map<String, Map<String, Object>>) w.get("widgets")).get(widget).get("value");
                return v == null ? "" : v.toString().replaceAll("\\s+", " ").strip();
            }
        }
        throw new AssertionError("VHYT53I is not open");
    }

    /** VHYT53I opened by its caller for yard CY (the pass record), then Refresh */
    private ScreenSession openOld() {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT53I", p.user);
        String w = windowId(old);
        ScreenInstance s = old.window(w);
        s.recordPart("VHYT53IW-PASSVALUE").set("YRD-TRKG-YRD data", "CY");
        s.recordPart("VHYT53IW-PASSVALUE").set("TERM-LAST-UPD data", "MARIS-UI");
        s.recordPart("VHYT53IW-PASSVALUE").set("USER-GROUP data", p.user.groupId());
        p.clear();
        old.event(w, List.of(), "Push_Button62", "clicked", null);
        return old;
    }

    /** select consist 532 (row 1), Include all its cars, type the new consist number */
    private void includeFirstConsist(ScreenSession old, String newConsist) {
        String w = windowId(old);
        old.event(w, List.of(), "VAGen_Container_Details_DATA_ROW", "select", 1);
        old.event(w, List.of(), "Push_Button1", "clicked", null);
        old.event(w, List.of(new ScreenSession.Change("Text2", "object", newConsist)), "Text2", "changed", null);
    }

    private static List<YardReportsPseudo.NewCar> newCars(YardReportsPseudo.Consist c) {
        return c.cars().stream().map(x -> new YardReportsPseudo.NewCar(x.car(), x.leftEnd(), x.rightEnd(), x.type(), x.ato(), x.incident(), x.status())).toList();
    }

    /** the records a call sent, without XDCONTROL (the old screen reuses its XDCONTROL, so it carries the last row count) */
    private static Map<String, Map<String, Object>> records(Map<String, Map<String, Object>> sent) {
        Map<String, Map<String, Object>> out = new java.util.TreeMap<>(sent);
        out.remove("XDCONTROL");
        return out;
    }

    private List<String> calls() {
        return p.sent.stream().map(Map.Entry::getKey).toList();
    }

    @Test
    void loadingAsksForTheSameAndShowsTheSameMessage() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        List<String> oldCalls = calls();
        Map<String, Map<String, Map<String, Object>>> oldSent = new HashMap<>();
        for (String prog : List.of("VHYT53D", "VHYT53B", "VHYT53C")) oldSent.put(prog, p.lastSent(prog));
        String oldMessage = shown(old, "Multi-line_Edit1");

        p.clear();
        YardReportsPseudo.Config c = api.config("CY");
        assertThat(calls()).isEqualTo(oldCalls);
        for (String prog : List.of("VHYT53D", "VHYT53B", "VHYT53C")) {
            assertThat(p.lastSent(prog).get(prog + "_CRIT")).as(prog).isEqualTo(oldSent.get(prog).get(prog + "_CRIT"));
        }
        assertThat(c.message()).isEqualTo(oldMessage);
    }

    @Test
    void creatingAConsistSendsTheSameRecordsInTheSameOrder() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        includeFirstConsist(old, "123");
        p.clear();
        old.event(windowId(old), List.of(), "Push_Button6", "clicked", null);
        List<String> oldCalls = calls();
        Map<String, Map<String, Object>> oldL = p.lastSent("VHYT53L"), oldF = p.lastSent("VHYT53F");
        assertThat(oldF).as("the old screen created the consist; it says: " + shown(old, "Multi-line_Edit1")).isNotNull();

        YardReportsPseudo.Config config = api.config("CY");
        p.clear();
        api.create("CY", "123", false, newCars(config.consists().get(0)), p.user.groupId());
        assertThat(calls().subList(0, 2)).isEqualTo(oldCalls.subList(0, 2));                     // VHYT53L, VHYT53F (the old screen then refreshes)
        assertThat(records(p.lastSent("VHYT53L"))).isEqualTo(records(oldL));
        assertThat(p.lastSent("VHYT53F").get("VHYT53F_CRIT")).isEqualTo(oldF.get("VHYT53F_CRIT"));
        assertThat(p.lastSent("VHYT53F").get("VHYT53F_DATA")).isEqualTo(oldF.get("VHYT53F_DATA"));
    }

    @Test
    void creatingAPartialConsistSendsTheSameRecords() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        includeFirstConsist(old, "124");
        p.clear();
        old.event(windowId(old), List.of(), "Push_Button61", "clicked", null);
        Map<String, Map<String, Object>> oldL = p.lastSent("VHYT53L"), oldG = p.lastSent("VHYT53G");
        assertThat(oldG).as("the old screen created the partial consist; it says: " + shown(old, "Multi-line_Edit1")).isNotNull();

        YardReportsPseudo.Config config = api.config("CY");
        p.clear();
        api.create("CY", "124", true, newCars(config.consists().get(0)), p.user.groupId());
        assertThat(records(p.lastSent("VHYT53L"))).isEqualTo(records(oldL));
        assertThat(records(p.lastSent("VHYT53G"))).isEqualTo(records(oldG));
    }

    @Test
    void badCarsAreRefusedWithTheSameMessageAndNothingIsWritten() {
        String[][] bad = {{"2001", "B1", "GO", "N", "N"}, {"2002", "A1", "GO", "N", "N"}, {"2003", "B1", "NO GO", "N", "N"}, {"2004", "C1", "GO", "N", "N"}};
        backend(bad, 100);
        ScreenSession old = openOld();
        includeFirstConsist(old, "123");
        old.event(windowId(old), List.of(), "Push_Button6", "clicked", null);
        assertThat(p.lastSent("VHYT53F")).isNull();
        String oldMessage = shown(old, "Multi-line_Edit1");

        YardReportsPseudo.Config config = api.config("CY");
        assertThat(catchThrowableOfType(InvalidRequestException.class, () -> api.create("CY", "123", false, newCars(config.consists().get(0)), "BRIGHTCO")))
                .hasMessage(oldMessage);
    }

    @Test
    void aConsistNumberOutOfRangeIsRefusedWithTheSameMessage() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        includeFirstConsist(old, "099");
        old.event(windowId(old), List.of(), "Push_Button6", "clicked", null);
        String oldMessage = shown(old, "Multi-line_Edit1");
        assertThat(p.lastSent("VHYT53L")).isNull();

        YardReportsPseudo.Config config = api.config("CY");
        assertThat(catchThrowableOfType(InvalidRequestException.class, () -> api.create("CY", "099", false, newCars(config.consists().get(0)), "BRIGHTCO")))
                .hasMessage(oldMessage);
    }

    @Test
    void resettingAConsistSendsTheSameRecord() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        String w = windowId(old);
        old.event(w, List.of(), "VAGen_Container_Details_DATA_ROW", "select", 2);                // 446, marked C
        p.clear();
        old.event(w, List.of(), "Push_Button8", "clicked", null);
        Map<String, Map<String, Object>> oldK = p.lastSent("VHYT53K");
        assertThat(oldK).as("the old screen reset the consist").isNotNull();

        p.clear();
        api.reset("CY", "446", "T-95", "C", p.user.groupId());
        assertThat(records(p.lastSent("VHYT53K"))).isEqualTo(records(oldK));
    }

    @Test
    void movingACarOutSendsTheSameRecord() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        includeFirstConsist(old, "123");
        String w = windowId(old);
        old.event(w, List.of(), "Push_Button71", "clicked", null);                               // the second car of the new consist
        old.window(w).recordPart("VHYT53IW-MOVE").set("YRD-SUB-LOC data", "ST-07");             // the track list's selectedItem
        p.clear();
        old.event(w, List.of(), "Push_Button3", "clicked", null);
        Map<String, Map<String, Object>> oldH = p.lastSent("VHYT53H");
        assertThat(oldH).as("the old screen moved the car; it says: " + shown(old, "Multi-line_Edit1")).isNotNull();
        String oldMessage = shown(old, "Multi-line_Edit1");

        p.clear();
        var r = api.moveCar("CY", "1002", "ST-07", p.user.groupId());
        assertThat(records(p.lastSent("VHYT53H"))).isEqualTo(records(oldH));
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    /**
     * a one-car consist (446) included after part of 532. Old slip: VHYT53IS-MOVE-DATA does not copy VEH-STAT-1 for a one-car
     * consist, so the old screen sends that car's status blank; the API sends the car's status. Everything else is the same.
     */
    @Test
    @SuppressWarnings("unchecked")
    void includingAOneCarConsistSendsTheSameRecordsButTheCarsStatus() {
        backend(GOOD, 100);
        ScreenSession old = openOld();
        String w = windowId(old);
        old.event(w, List.of(), "VAGen_Container_Details_DATA_ROW", "select", 1);
        old.event(w, List.of(new ScreenSession.Change("Text4", "object", 2)), "Text4", "changed", null);
        old.event(w, List.of(), "Push_Button1", "clicked", null);
        old.event(w, List.of(), "VAGen_Container_Details_DATA_ROW", "select", 2);
        old.event(w, List.of(), "Push_Button1", "clicked", null);
        old.event(w, List.of(new ScreenSession.Change("Text2", "object", "125")), "Text2", "changed", null);
        p.clear();
        old.event(w, List.of(), "Push_Button6", "clicked", null);
        Map<String, Map<String, Object>> oldF = p.lastSent("VHYT53F");
        assertThat(oldF).as("the old screen created the consist; it says: " + shown(old, "Multi-line_Edit1")).isNotNull();

        YardReportsPseudo.Config config = api.config("CY");
        List<YardReportsPseudo.NewCar> cars = new java.util.ArrayList<>(newCars(config.consists().get(0)).subList(0, 2));
        cars.addAll(newCars(config.consists().get(1)));
        p.clear();
        api.create("CY", "125", false, cars, p.user.groupId());
        Map<String, Map<String, Object>> sent = records(p.lastSent("VHYT53F"));
        List<Object> apiStats = (List<Object>) ((Map<String, Object>) sent.get("VHYT53F_CRIT").get("carNoRow")).get("vehStatN");
        List<Object> oldStats = (List<Object>) ((Map<String, Object>) oldF.get("VHYT53F_CRIT").get("carNoRow")).get("vehStatN");
        assertThat(apiStats).containsExactly("GO", "USABLE", "GO");
        assertThat(oldStats).containsExactly("GO", "USABLE");                                     // the third (one-car consist) left blank
        ((Map<String, Object>) sent.get("VHYT53F_CRIT").get("carNoRow")).put("vehStatN", oldStats);
        assertThat(sent).isEqualTo(records(oldF));
    }
}
