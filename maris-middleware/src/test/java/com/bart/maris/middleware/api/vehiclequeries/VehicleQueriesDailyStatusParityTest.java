package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesDailyStatusService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Daily vehicle status (VHDVS1I, its pages VHDVS2I and VHDVS3I, its printer window VHDVS4I) against the old screens. */
class VehicleQueriesDailyStatusParityTest {
    static final String[] YARDS = {"CY", "DY", "HY", "RY"};
    static final String[] TYPES = {"A2", "B2", "C1", "C2", "D1", "E1", "L1", "M1", "K1", "K2", "X9"};
    static final List<String> COUNTS = List.of("cntCarsAvailAm", "cntCarsInFleet", "cntCarsMlFail", "cntCarsOosAwp", "cntCarsOosEngr", "cntCarsOosMod",
            "cntCarsOosPm", "cntCarsOosUm", "cntCarsOosWar", "cntCarsReprMisc", "cntCarsReprMlx", "cntCarsReprMod", "cntCarsReprPm", "cntCarsReprUm",
            "cntCarsReprWar", "cntCarsShopMisc", "cntCarsShopMod", "cntCarsShopPm", "cntCarsShopWar", "cntCarsYrdFail");

    /** every yard and car type, each count a different small number; {@code seed} changes them (another day) */
    static Parity backend(int seed) {
        List<Map<String, Object>> rows = new ArrayList<>(), req = new ArrayList<>();
        int n = seed;
        for (String y : YARDS) {
            for (String t : TYPES) {
                if (rows.size() < 50) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("yrd", y);
                    m.put("carType", t);
                    m.put("dateInsrvRev", "2026-11-13");
                    for (String f : COUNTS) m.put(f, (n++ * 7 + seed) % 11 + 1);
                    rows.add(m);
                }
                req.add(Map.of("yrd", y, "carType", t, "cntCarsReqd", (n++ % 9) + 2, "actvInd", "Y"));
            }
        }
        Parity p = new Parity();
        p.on("VHDVS11", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHDVS11_DATA", Map.of("fromDate", "2026-11-13", "reportDate", in.get("VHDVS11_DATA").get("reportDate"))));
        p.on("VHDVS03", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", req.size()), "VHDVS03_DATA", Map.of("dataRow", req)));
        p.on("VHDVS01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHDVS01_DATA", Map.of("dataRow", rows),
                "VHDVS02_DATA", Map.of("dataRow", rows)));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHDVS04", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesDailyStatusService api(Parity p) {
        return new VehicleQueriesDailyStatusService(Parity.bundle(), p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String widget) {
        return (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
    }

    static String value(ScreenSession s, String screen, String widget) {
        Object v = widget(s, screen, widget).get("value");
        return v == null ? "" : v.toString().strip();
    }

    static String id(ScreenSession s, String screen) {
        return (String) window(s, screen).get("id");
    }

    /** the old windows, all three open */
    static ScreenSession old(Parity p, String retrieveDate) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHDVS1I", p.user);
        if (retrieveDate != null) {
            s.event(id(s, "VHDVS1I"), List.of(new ScreenSession.Change("Text2", "object", retrieveDate)), "Push_Button5", "clicked", null);
        }
        s.event(id(s, "VHDVS1I"), List.of(), "Push_Button4", "clicked", null);
        s.event(id(s, "VHDVS2I"), List.of(), "Push_Button4", "clicked", null);
        return s;
    }

    static final String[][] WIDGETS = {
            {"VHDVS1I", "Container_Details1"}, {"VHDVS1I", "Container_Details3"}, {"VHDVS2I", "Container_Details1"}, {"VHDVS2I", "Container_Details3"},
            {"VHDVS3I", "Container_Details1"}, {"VHDVS3I", "Container_Details3"}, {"VHDVS3I", "Container_Details2"}};

    @SuppressWarnings("unchecked")
    static void sameTables(ScreenSession s, VehicleQueriesDailyStatusService.DailyStatus r) {
        assertThat(r.tables()).hasSize(WIDGETS.length);
        for (int i = 0; i < WIDGETS.length; i++) {
            var t = r.tables().get(i);
            assertThat(t.screen()).isEqualTo(WIDGETS[i][0]);
            List<List<String>> oldRows = (List<List<String>>) widget(s, WIDGETS[i][0], WIDGETS[i][1]).get("rows");
            List<List<String>> rows = new ArrayList<>();
            for (Map<String, Object> row : t.rows()) {
                List<String> l = new ArrayList<>();
                for (var c : t.columns()) {
                    Object v = row.get(c.field());
                    l.add(v == null ? "" : v.toString().strip());
                }
                rows.add(l);
            }
            List<List<String>> trimmed = new ArrayList<>();
            for (List<String> o : oldRows) trimmed.add(o.stream().map(String::strip).toList());
            assertThat(rows).as(WIDGETS[i][0] + "." + WIDGETS[i][1]).isEqualTo(trimmed);
        }
        assertThat(String.valueOf(r.gains().concord())).isEqualTo(value(s, "VHDVS2I", "Text4"));
        assertThat(String.valueOf(r.gains().dalyCity())).isEqualTo(value(s, "VHDVS2I", "Text5"));
        assertThat(String.valueOf(r.gains().richmond())).isEqualTo(value(s, "VHDVS2I", "Text6"));
        assertThat(String.valueOf(r.gains().hayward())).isEqualTo(value(s, "VHDVS2I", "Text7"));
        assertThat(String.valueOf(r.gains().total())).isEqualTo(value(s, "VHDVS2I", "Text3"));
        assertThat(r.revenueDate()).isEqualTo(value(s, "VHDVS1I", "Text2"));
        assertThat(r.validAsOf()).isEqualTo(value(s, "VHDVS1I", "Text1"));
    }

    @Test
    void openingShowsTheSameTablesAndSendsTheSameRecords() {
        Parity p = backend(1);
        ScreenSession s = old(p, null);
        var oldSent = new ArrayList<>(p.sent);
        p.clear();
        var r = api(p).report(null);
        for (String[] pr : new String[][] {{"VHDVS11", "VHDVS11_DATA"}, {"VHDVS03", "VHDVS03_CRIT"}, {"VHDVS01", "VHDVS01_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0]).isEqualTo(sent(oldSent, pr[0], pr[1]));
        }
        sameTables(s, r);
    }

    @Test
    void retrieveDataForAnotherDayMatches() {
        Parity p = backend(3);
        ScreenSession s = old(p, "11-02-2026");
        var oldSent = new ArrayList<>(p.sent);
        p.clear();
        var r = api(p).report("2026-11-02");
        assertThat(last(p.sent, "VHDVS01", "VHDVS01_CRIT")).isEqualTo(last(oldSent, "VHDVS01", "VHDVS01_CRIT"));
        assertThat(last(p.sent, "VHDVS11", "VHDVS11_DATA")).isEqualTo(last(oldSent, "VHDVS11", "VHDVS11_DATA"));
        sameTables(s, r);
    }

    @Test
    void hardCopySendsTheSameRecord() {
        Parity p = backend(2);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHDVS1I", p.user);
        s.event(id(s, "VHDVS1I"), List.of(), "Push_Button1", "clicked", null);
        assertThat(window(s, "VHDVS4I")).as("VHDVS4I opened").isNotNull();
        s.event(id(s, "VHDVS4I"), List.of(), "cntPrinterdet", "select", 1);
        s.event(id(s, "VHDVS4I"), List.of(), "pbtHardcopy", "clicked", null);
        var sent = p.lastSent("VHDVS04").get("VHDVS04_CRIT");
        Object d = s.state().get("dialog");
        String oldMessage = d == null ? null : String.valueOf(((Map<?, ?>) d).get("message")).strip();
        p.clear();
        var svc = api(p);
        assertThat(svc.printers()).extracting(VehicleQueriesDailyStatusService.Printer::vtamName).containsExactly("DUBLNHP1");
        assertThat(svc.print(null, "DUBLNHP1")).isEqualTo(oldMessage);
        assertThat(p.lastSent("VHDVS04").get("VHDVS04_CRIT")).isEqualTo(sent);
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    static Object last(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program, String record) {
        List<Object> l = sent(sent, program, record);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }
}
