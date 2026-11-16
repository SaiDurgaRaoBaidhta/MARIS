package com.bart.maris.middleware.api.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.carwash.CarWashConsistWashService;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Consist wash (old VHCWA2I) against the old screen run by the engine: same backend, same records sent, same messages. */
class CarWashConsistWashParityTest {
    static final String TERMINAL = "MARIS-UI";

    static Parity backend(String washInd, boolean found) {
        Parity p = new Parity();
        p.on("VHIE01B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentDate", "2026-11-14",
                "currentTime", "10:15:00", "yesterday", "2026-11-13")));
        p.on("VHCWA5", in -> {
            if (!found) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String car : List.of("1101", "1802", "1103")) rows.add(Map.of("carNo", car, "conNo", in.get("VHCWA5_CRIT").get("conNo"), "dashNo", 2, "washInd", "N"));
            Map<String, Object> crit = new LinkedHashMap<>(in.get("VHCWA5_CRIT"));
            crit.put("dashNo", 2);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHCWA5_CRIT", crit, "VHCWA5_DATA", Map.of("dataRow", rows));
        });
        p.on("VHCWA3", in -> {
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) in.get("VHCWA5_DATA").get("dataRow"));
            Map<String, Object> first = new LinkedHashMap<>(rows.get(0));
            first.put("washInd", washInd);
            rows.set(0, first);
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHCWA5_DATA", Map.of("dataRow", rows));
        });
        p.on("VHCWA6", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static CarWashConsistWashService api(Parity p) {
        return new CarWashConsistWashService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** the old screen: consist and date typed, date left (VHCWA2I-GET-CARS), yard and time typed, then Update */
    static ScreenSession old(Parity p, String consist, String date, String yard, String time, boolean update) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCWA2I", p.user);
        String id = (String) window(s, "VHCWA2I").get("id");
        s.event(id, List.of(new ScreenSession.Change("txtFromdate", "object", consist), new ScreenSession.Change("Text6", "object", date)), "Text6", "losingFocus", null);
        s.event(id, List.of(new ScreenSession.Change("Text4", "object", yard), new ScreenSession.Change("Text1", "object", time)), "Text1", "losingFocus", null);
        if (update) s.event(id, List.of(), "pbtDisplay", "clicked", null);
        return s;
    }

    static List<Map<String, Map<String, Object>>> calls(Parity p, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (var e : p.sent) if (e.getKey().equals(program)) out.add(business(e.getValue()));
        return out;
    }

    /** the records of a call without XDCONTROL (the old screens hand the previous call's control block on) */
    static Map<String, Map<String, Object>> business(Map<String, Map<String, Object>> records) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>(records);
        m.remove("XDCONTROL");
        return m;
    }

    @Test
    void openingMatches() {
        Parity p = backend("N", true);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCWA2I", p.user);
        var start = api(new Parity().on("VHIE01B", backendAnswer(p))).start();
        assertThat(start.washDate().substring(5, 7) + "-" + start.washDate().substring(8) + "-" + start.washDate().substring(0, 4)).isEqualTo(value(s, "VHCWA2I", "Text6"));
        assertThat(start.message()).isEqualTo(value(s, "VHCWA2I", "Text5"));
    }

    private static java.util.function.Function<Map<String, Map<String, Object>>, Map<String, Object>> backendAnswer(Parity unused) {
        return in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentDate", "2026-11-14",
                "currentTime", "10:15:00", "yesterday", "2026-11-13"));
    }

    @Test
    void washSendsWhatTheOldScreenSends() {
        Parity p = backend("N", true);
        ScreenSession s = old(p, "201", "11-14-2026", "CY", "1030", true);
        var oldCalls = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHCWA2I", "Text5");
        p.clear();
        var result = api(p).wash(new CarWashConsistWashService.Wash("201", "2026-11-14", "1030", "CY"));
        for (String program : List.of("VHCWA5", "VHCWA3", "VHCWA6")) {
            List<Map<String, Map<String, Object>>> before = new ArrayList<>();
            for (var e : oldCalls) if (e.getKey().equals(program)) before.add(business(e.getValue()));
            assertThat(calls(p, program)).as(program).isEqualTo(before);
        }
        assertThat(result.message()).isEqualTo(oldMessage);
        assertThat(result.consist().cars()).containsExactly(value(s, "VHCWA2I", "Text7"), value(s, "VHCWA2I", "Text8"), value(s, "VHCWA2I", "Text2"));
        assertThat(String.valueOf(result.consist().dashNo())).isEqualTo(value(s, "VHCWA2I", "txtTodate"));
    }

    @Test
    void lookupMessagesMatch() {
        for (boolean found : List.of(true, false)) {
            for (String washInd : List.of("N", "Y")) {
                Parity p = backend(washInd, found);
                ScreenSession s = old(p, "201", "11-14-2026", "", "", false);
                String afterLookup = value(s, "VHCWA2I", "Text5");
                p.clear();
                ScreenSession s2 = p.oldScreens();
                s2.openRoot("VHCWA2I", p.user);
                String id = (String) window(s2, "VHCWA2I").get("id");
                s2.event(id, List.of(new ScreenSession.Change("txtFromdate", "object", "201"), new ScreenSession.Change("Text6", "object", "11-14-2026")), "Text6",
                        "losingFocus", null);
                String lookupOnly = value(s2, "VHCWA2I", "Text5");
                p.clear();
                var c = api(p).consist("201", "2026-11-14");
                assertThat(c.message()).as("found=" + found + " washInd=" + washInd).isEqualTo(lookupOnly);
                assertThat(afterLookup).isNotBlank();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"CY,2530", "CY,10:75", "CY,ab", "XX,1030", "cy,1030"})
    void checksMatch(String yard, String time) {
        Parity p = backend("N", true);
        ScreenSession s = old(p, "201", "11-14-2026", yard, time, false);
        String oldMessage = value(s, "VHCWA2I", "Text5");
        p.clear();
        String message;
        try {
            api(p).wash(new CarWashConsistWashService.Wash("201", "2026-11-14", time, yard));
            message = api(p).readyMessage();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        if (yard.equals("cy")) assertThat(message).isEqualTo(api(p).readyMessage()); // the API takes the yard in any case; the old screen only in capitals
        else assertThat(message).isEqualTo(oldMessage);
    }

    @Test
    void validTimeAndYardSayPressUpdate() {
        Parity p = backend("N", true);
        ScreenSession s = old(p, "201", "11-14-2026", "CY", "10.30", false);
        assertThat(value(s, "VHCWA2I", "Text5")).isEqualTo(api(p).readyMessage());
        assertThat(value(s, "VHCWA2I", "Text1")).isEqualTo("10:30:00");
    }
}
