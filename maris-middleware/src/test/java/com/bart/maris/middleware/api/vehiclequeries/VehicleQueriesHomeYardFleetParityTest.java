package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesHomeYardFleetService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Home yard fleet summary (VHHF01I) and its print (VHHF02I) against the old screens run by the engine. */
class VehicleQueriesHomeYardFleetParityTest {
    static Map<String, Object> car(String type, String stat, String where, String war) {
        return Map.of("carType", Map.of("carType1", type, "carType2", "2"), "vehStat", stat, "whereIsCar", where, "warInd", war, "carNo", "1101");
    }

    /** every branch of VHHF01IS-TYPE-*: held, in revenue, warranty, unscheduled, AWPART, PM, ENGHLD, MODHLD, MISC, GO, other */
    static final List<Map<String, Object>> CARS = List.of(
            car("A", "STOR", "YRD", "N"), car("A", "GO", "CON", "N"), car("A", "NO GO", "YRD", "Y"), car("B", "NO GO", "YRD", "N"),
            car("C", "AWPART", "YRD", "N"), car("D", "PM", "YRD", "N"), car("E", "ENGHLD", "YRD", "N"), car("L", "MODHLD", "YRD", "N"),
            car("M", "MISC", "YRD", "N"), car("K", "GO", "YRD", "N"), car("K", "USABLE", "YRD", "N"), car("C", "INSP", "YRD", "N"),
            car("X", "GO", "YRD", "N"), car("K", "AWPART", "YRD", "N"), car("K", "PM", "YRD", "N"), car("A", "SAFETY", "CON", "N"));

    static Parity backend() {
        Parity p = new Parity();
        p.on("VHHF01A", in -> {
            String yard = String.valueOf(in.get("VHHF01A_CRIT").get("homeYrd")).strip();
            if (yard.equals("HY")) return Map.of("XDCONTROL", Map.of("urc", 100));                     // no cars: the column stays empty
            Map<String, Object> crit = new HashMap<>(in.get("VHHF01A_CRIT"));
            crit.put("awpACount", 2); crit.put("awpKCount", yard.equals("CY") ? 3 : 1);
            crit.put("adataRow", List.of(Map.of("acars", yard.equals("CY") ? "1101" : "1202")));        // returned, then sent again
            List<Map<String, Object>> cars = yard.equals("CY") ? CARS : CARS.subList(0, 9);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars.size()), "VHHF01A_CRIT", crit, "VHHF01A_DATA", Map.of("dataRow", cars));
        });
        p.on("VHHF01B", in -> {
            String yard = String.valueOf(in.get("VHHF01B_CRIT").get("yrd")).strip();
            if (yard.equals("RY")) return Map.of("XDCONTROL", Map.of("urc", 8));                       // fails: the last yard's counts stay
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHHF01B_DATA", Map.of("dataRow", List.of(
                    Map.of("carType", Map.of("carType1", "A"), "cntCarsReqd", yard.equals("CY") ? 10 : 7),
                    Map.of("carType", Map.of("carType1", "K"), "cntCarsReqd", 4), Map.of("carType", Map.of("carType1", "C"), "cntCarsReqd", 2))));
        });
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHHF02A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesHomeYardFleetService api(Parity p) {
        return new VehicleQueriesHomeYardFleetService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    /** each call in order with its criteria record (the input; the output records only carry what came back before) */
    static List<String> criteria(Parity p) {
        List<String> out = new ArrayList<>();
        for (var e : p.sent) {
            if (e.getKey().equals("WS70A01")) continue;
            e.getValue().forEach((rec, v) -> { if (rec.endsWith("_CRIT")) out.add(e.getKey() + " " + v); });
        }
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameTableAndSameRecordsAsTheOldScreen() {
        Parity p = backend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHHF01I", p.user);
        List<List<String>> oldRows = (List<List<String>>) ((Map<String, Object>) ((Map<String, Object>) window(s, "VHHF01I").get("widgets")).get("cntCarsSummary")).get("rows");
        List<String> oldSent = criteria(p);

        p.clear();
        var f = api(p).summary();
        assertThat(criteria(p)).isEqualTo(oldSent);
        assertThat(f.rows()).hasSize(oldRows.size());
        for (int i = 0; i < oldRows.size(); i++) {
            var r = f.rows().get(i);
            List<String> mine = new ArrayList<>();
            mine.add(r.name());
            for (String y : f.yards()) {
                r.yards().get(y).byType().forEach(n -> mine.add(String.valueOf(n)));
                mine.add(String.valueOf(r.yards().get(y).total()));
            }
            assertThat(mine).as("row " + (i + 1)).isEqualTo(oldRows.get(i).stream().map(String::strip).toList());
        }
        assertThat(f.headings()).containsExactly("A", "B", "C", "D", "E", "L", "M", "CB", "Tot");
    }

    @Test
    @SuppressWarnings("unchecked")
    void noCarsInAnyYardLeavesTheTableEmpty() {
        Parity p = new Parity();
        p.on("VHHF01A", in -> Map.of("XDCONTROL", Map.of("urc", 100)));
        ScreenSession s = p.oldScreens();
        s.openRoot("VHHF01I", p.user);
        Object oldRows = ((Map<String, Object>) ((Map<String, Object>) window(s, "VHHF01I").get("widgets")).get("cntCarsSummary")).get("rows");
        assertThat(oldRows == null || ((List<?>) oldRows).isEmpty()).isTrue();
        assertThat(api(p).summary().rows()).isEmpty();
    }

    @Test
    void printSendsTheSameRecord() {
        Parity p = backend();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHHF01I", p.user);
        s.event((String) window(s, "VHHF01I").get("id"), List.of(), "Push_Button1", "clicked", null);
        String printer = (String) window(s, "VHHF02I").get("id");
        s.event(printer, List.of(), "cntPrinterdet", "select", 1);
        s.event(printer, List.of(), "pbtHardcopy", "clicked", null);
        Map<String, Object> sent = p.lastSent("VHHF02A").get("VHHF02A_CRIT");
        Object d = s.state().get("dialog");

        p.clear();
        String message = api(p).print("DUBLNHP1");
        assertThat(p.lastSent("VHHF02A").get("VHHF02A_CRIT")).isEqualTo(sent);
        assertThat(message).isEqualTo(String.valueOf(((Map<?, ?>) d).get("message")).strip());
    }
}
