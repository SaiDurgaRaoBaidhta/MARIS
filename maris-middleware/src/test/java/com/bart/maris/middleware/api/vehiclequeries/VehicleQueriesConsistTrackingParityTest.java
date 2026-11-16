package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistTrackingService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Consist tracking (VHST05I with VHST02I / VHST04I, VHST01I with VHST03I) against the old screens run by the engine. */
class VehicleQueriesConsistTrackingParityTest {
    // ---- scripted backend ----------------------------------------------------------------------------------

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    static Map<String, Object> con(String no, int dash) {
        return m("conNo", no, "conHyphen", "-", "dashNo", dash);
    }

    /** VHST2A1 rows: 5 consists (two pages), every colour rule and orientation case */
    static List<Map<String, Object>> trainRows() {
        List<Map<String, Object>> out = new ArrayList<>();
        Object[][] spec = {
            // car, con, dash, origin, conStat, vehStat, trainIncd, ja, orient, left, right, scanner, carWn, conWn
            {"1101", "201", 1, "CY", "CON", "GO", "N", "N", "L", "L", "R", "N", "N", "N"},
            {"1102", "201", 1, "CY", "CON", "USABLE", "N", "N", "R", "L", "R", "N", "Y", "N"},
            {"1103", "201", 1, "CY", "CON", "NO GO", "N", "N", "X", "L", "R", "N", "N", "Y"},
            {"1201", "305", 2, "HY", "", "GO", "Y", "N", "L", "L", "R", " ", "N", "N"},
            {"1202", "305", 2, "HY", "", "USABLE", "N", "Y", "L", "L", "R", " ", "N", "N"},
            {"1301", "410", 3, "DY", "", "USABLE", "N", "N", "R", "L", "R", "S", "N", "N"},
            {"1401", "512", 4, "RY", "", "GO", "N", "N", "L", "L", "R", "S", "N", "N"},
            {"1501", "620", 5, "ZZ", "", "LIMIT", "N", "N", "L", "L", "R", "S", "Y", "Y"},
            {"1502", "620", 5, "ZZ", "", "GO", "N", "N", "R", "L", "R", "S", "N", "N"},
        };
        for (Object[] s : spec) {
            out.add(m("carNo", s[0], "consist", con((String) s[1], (Integer) s[2]), "homeYrd", s[3], "carOrder", 1, "originLoc", s[3], "dateInsrvRev", "2026-11-10",
                    "conStat", s[4], "vehStat", s[5], "timeInsrv", "05:10:00", "trainIncdInd", s[6], "vagenOrientation", s[8], "lftOrientationCd", s[9],
                    "rgtOrientationCd", s[10], "msgLoc", "M" + s[2], "orientOfCars", "A", "scannerDir", s[11], "jaIncdInd", s[7], "carWnInd", s[12], "conWnInd", s[13]));
        }
        return out;
    }

    /** VHST04A rows; VHST04B gives each car 0 .. 3 clean dates, so the clean count differs from the car count */
    static List<Map<String, Object>> detailRows() {
        List<Map<String, Object>> out = new ArrayList<>();
        Object[][] spec = {{"1101", "201", 1, "CY"}, {"1102", "201", 1, "CY"}, {"1201", "305", 2, "HY"}, {"1301", "410", 3, "RY"}, {"1302", "410", 3, "RY"},
                {"1401", "512", 4, "DY"}, {"1501", "620", 5, "XX"}};
        for (Object[] s : spec) out.add(m("consist", con((String) s[1], (Integer) s[2]), "carNo", s[0], "carOrder", 1, "originLoc", s[3], "dateInsrvRev", "2026-10-0" + s[2],
                "timeInsrv", "06:00:00"));
        return out;
    }

    static final String[][] CLEAN = {
        // next wash, bi-weekly, shampoo, thorough, wash days, pm no
        {"10-01-2026", "11-01-2026", "02-03-2024", "12-31-2025", "0", ""},
        {"", "11-10-2026", "", "01-05-2026", "0", "P1"},
        {"12-01-1998", "", "", "", "400", ""},
        {"01-15-2026", "03-01-2024", "02-29-2024", "07-04-2020", "0", ""},
        {"02-28-2025", "11-13-2026", "11-13-2026", "11-13-2026", "9999", ""},
        {"06-30-2026", "12-31-1999", "01-01-2000", "02-29-2000", "0", "P2"},
        {"", "", "", "", "0", ""},
    };

    static Parity backend() {
        Parity p = new Parity();
        p.on("WS70A01", in -> m("XDCONTROL", m("urc", 0, "urowcnt", 2), "WS70A01_DATA", m("dataRow", List.of(m("vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"),
                m("vtamName", "HAYWHP2", "prtrLocDesc", "HAYWARD")))));
        List<Map<String, Object>> trains = trainRows();
        p.on("VHST2A1", in -> m("XDCONTROL", m("urc", 0, "urowcnt", trains.size()), "VHST2A1_DATA", m("dataRow", trains)));
        p.on("VHST4A2", in -> m("XDCONTROL", m("urc", 0, "urowcnt", 2), "VHST4A2_DATA", m("dataRow", List.of(
                m("carType", "A", "actvInd", "Y", "cntDaysBiwkOd", 400, "cntDaysShampoOd", 30, "cntDaysThoroOd", 9000),
                m("carType", "B", "actvInd", "Y", "cntDaysBiwkOd", 1, "cntDaysShampoOd", 1, "cntDaysThoroOd", 1)))));
        List<Map<String, Object>> cars = detailRows();
        p.on("VHST04A", in -> m("XDCONTROL", m("urc", 0, "urowcnt", cars.size()), "VHST04A_DATA", m("dataRow", cars)));
        p.on("VHST04B", in -> {
            Map<String, Object> crit = new LinkedHashMap<>(in.get("VHST04B_CRIT"));
            int k = ((Number) crit.get("vagenCount")).intValue();
            Map<String, Object> data = new LinkedHashMap<>(in.get("VHST04B_DATA"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) data.getOrDefault("dataRow", List.of()));
            while (rows.size() < k) rows.add(new LinkedHashMap<>());
            String[] c = CLEAN[k - 1];
            rows.set(k - 1, m("carType", "A", "currSchdPmNo", c[5], "hrsFromLastPm", 100 + k, "homeYrd", "Y" + k, "dateNxtCarWash", c[0], "dateCarCleanBwk", c[1],
                    "dateCarCleanShm", c[2], "dateCarCleanThr", c[3], "washDays", Integer.valueOf(c[4]), "clean", ""));
            data.put("dataRow", rows);
            int found = 0;
            for (int i = 1; i <= 3; i++) if (!c[i].isEmpty()) found++;
            crit.put("totClnCount", ((Number) crit.getOrDefault("totClnCount", 0)).intValue() + found);
            crit.put("cleanDataFound", found > 0 ? "Y" : "N");
            return m("XDCONTROL", m("urc", 0, "urowcnt", 1), "VHST04B_CRIT", crit, "VHST04B_DATA", data);
        });
        p.on("VHST3A3", in -> m("XDCONTROL", m("urc", 0, "urowcnt", 1), "VHST3A3_DATA", m("fromDate", "2026-01-01", "toDate", "2026-02-01")));
        p.on("VHST3A4", in -> m("XDCONTROL", m("urc", 0, "urowcnt", 1), "VHST3A4_DATA", m("fromDate", "2026-03-01", "toDate", "2026-11-13")));
        List<Map<String, Object>> symp = new ArrayList<>();
        Object[][] spec = {{"1101", 1, "CON", "GO", "N", "L", "A$"}, {"1102", 1, "", "USABLE", "N", "R", "A"}, {"1103", 1, "", "NO GO", "N", "X", "$"},
                {"1104", 1, "", "GO", "Y", "L", ""}, {"1105", 2, "", "GO", "N", "L", ""}};
        for (Object[] s : spec) symp.add(m("carNo", s[0], "consist", con("201", (Integer) s[1]), "carOrder", 1, "sympCd", "0120", "sympCount", 3, "conStat", s[2],
                "vehStat", s[3], "originLoc", "DY", "dateInsrvRev", "2026-11-10", "timeInsrv", "06:00:00", "homeYrd", "DY", "trainIncdInd", s[4], "vagenOrientation", s[5],
                "lftOrientationCd", "L", "rgtOrientationCd", "R", "softVer", s[6]));
        p.on("VHST3A1", in -> m("XDCONTROL", m("urc", 0, "urowcnt", symp.size()), "VHST3A1_DATA", m("rowCount", 2, "dataRow", symp)));
        p.on("VHST011", in -> m("XDCONTROL", m("urc", 0, "urowcnt", 0)));
        return p;
    }

    static VehicleQueriesConsistTrackingService api(Parity p) {
        return new VehicleQueriesConsistTrackingService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Parity.bundle());
    }

    // ---- old screens ---------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String name) {
        Map<String, Object> w = window(s, screen);
        Map<String, Object> x = w == null ? null : (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
        return x == null ? Map.of() : x;
    }

    static String value(ScreenSession s, String screen, String name) {
        Object v = widget(s, screen, name).get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** the widget a screen's connection shows a record field in (e.g. VHST02IW-1 "GR-CAR1 data" -> Text7) */
    static String shownIn(String screen, String record, String field, String attr) {
        JsonNode def = Parity.bundle().screen(screen);
        for (JsonNode c : def.path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(0).path("id").asText().equals(record) && a.path(1).asText().equals(field + " data") && a.path(3).asText().equals(attr)) {
                return a.path(2).path("id").asText().replace("screen.", "");
            }
        }
        throw new AssertionError(screen + " shows no " + record + "." + field + " " + attr);
    }

    static String id(ScreenSession s, String screen) {
        return (String) window(s, screen).get("id");
    }

    static ScreenSession criteria(Parity p, int report, String from, String to, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHST05I", p.user);
        String w = id(s, "VHST05I");
        s.event(w, List.of(), "Radio_Button_Set1", "select", report);
        if (button.equals("pbtHardcopy")) s.event(w, List.of(), "Container_Details1", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtFromdate", "object", from), new ScreenSession.Change("txtTodate", "object", to)), button, "clicked", null);
        return s;
    }

    static ScreenSession symptom(Parity p, String consist, String symp, String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHST01I", p.user);
        String w = id(s, "VHST01I");
        if (button.equals("pbtHardcopy")) s.event(w, List.of(), "Container_Details1", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtFromdate", "object", consist), new ScreenSession.Change("Text2", "object", symp)), button, "clicked", null);
        return s;
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    static String nospace(String s) {
        return s.replace(" ", "");
    }

    // ---- VHST02I -------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "summary {0} to {1}")
    @CsvSource({"201,''", "ALL,''", "201,620", "' 20',''", "100,999"})
    void summaryListMatchesTheOldScreen(String from, String to) {
        Parity p = backend();
        ScreenSession s = criteria(p, 1, from, to, "pbtDisplay");
        assertThat(window(s, "VHST02I")).as("VHST02I opened: " + value(s, "VHST05I", "Text1")).isNotNull();
        var old = new ArrayList<>(p.sent);
        List<String> shown = new ArrayList<>();                       // every page of the old list, consist by consist
        List<Map<String, String>> oldTrains = new ArrayList<>();
        for (int page = 1; page <= 20; page++) {
            if (page > 1) {
                if (Boolean.FALSE.equals(widget(s, "VHST02I", "Push_Button4").get("enabled"))) break;
                s.event(id(s, "VHST02I"), List.of(), "Push_Button4", "clicked", null);
            }
            for (int b = 1; b <= 4; b++) {
                String rec = "VHST02IW-" + b;
                String c = value(s, "VHST02I", shownIn("VHST02I", rec, "CONSIST", "object"));
                if (c.isEmpty()) continue;
                Map<String, String> t = new LinkedHashMap<>();
                t.put("consist", c);
                t.put("origin", value(s, "VHST02I", shownIn("VHST02I", rec, "ORIGIN", "object")));
                t.put("date", value(s, "VHST02I", shownIn("VHST02I", rec, "DATE-INSRV", "object")));
                t.put("time", value(s, "VHST02I", shownIn("VHST02I", rec, "TIME-INSRV", "object")));
                t.put("mark", value(s, "VHST02I", shownIn("VHST02I", rec, "TI-" + b, "object")));
                t.put("order", nospace(value(s, "VHST02I", shownIn("VHST02I", rec, b == 1 ? "ORDER-LOC" : "ORDER-LOC-" + b, "object"))));
                t.put("orient", value(s, "VHST02I", shownIn("VHST02I", rec, b == 1 ? "ORIENT-OF-CARS" : "ORIENT-OF-CARS-" + b, "object")));
                t.put("conWarn", String.valueOf(widget(s, "VHST02I", shownIn("VHST02I", "VHST02IW-COLOR", "CON-WN-COLOR-" + b, "backgroundColor")).get("bg")));
                for (int k = 1; k <= 10; k++) {
                    String carField = b == 1 ? "GR-CAR" + k : "GR-CAR-" + b + "-" + k;
                    String carW = shownIn("VHST02I", rec, carField, "object");
                    String car = value(s, "VHST02I", carW);
                    if (car.isEmpty()) continue;
                    t.put("car" + k, car + "|" + value(s, "VHST02I", shownIn("VHST02I", rec, "HY" + k, "object")) + "|"
                            + widget(s, "VHST02I", shownIn("VHST02I", "VHST02IW-COLOR", "CAR" + k + "-COLOR-" + b, "foregroundColor")).get("fg") + "|"
                            + widget(s, "VHST02I", shownIn("VHST02I", "VHST02IW-COLOR", "CAR" + k + "-WN-COLOR-" + b, "backgroundColor")).get("bg"));
                }
                oldTrains.add(t);
                shown.add(c);
            }
        }
        p.clear();
        var r = api(p).statusTrains(from, to);
        assertThat(sent(p.sent, "VHST2A1", "VHST2A1_CRIT")).isEqualTo(sent(old, "VHST2A1", "VHST2A1_CRIT"));
        assertThat(r.from()).isEqualTo(value(s, "VHST02I", "Text1"));
        assertThat(r.to()).isEqualTo(value(s, "VHST02I", "Text2"));
        List<Map<String, String>> apiTrains = new ArrayList<>();
        for (var t : r.trains()) {
            Map<String, String> x = new LinkedHashMap<>();
            x.put("consist", t.consist());
            x.put("origin", t.origin());
            x.put("date", t.dateInService());
            x.put("time", t.timeInService());
            x.put("mark", t.mark());
            x.put("order", nospace(t.orderLocation()));
            x.put("orient", t.orientation());
            x.put("conWarn", t.warning() ? "yellow" : "white");
            int k = 1;
            for (var c : t.cars()) {
                String color = c.alert() ? "RED" : c.usable() ? "Green" : "BLACK";
                x.put("car" + k++, (c.leftMark() + c.car() + c.rightMark()).strip() + "|" + c.homeYard() + "|" + color + "|" + (c.warning() ? "yellow" : "white"));
            }
            apiTrains.add(x);
        }
        assertThat(apiTrains).isEqualTo(oldTrains);
    }

    // ---- VHST04I -------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "details {0} to {1}")
    @CsvSource({"201,620", "ALL,''", "201,''"})
    void carDetailsMatchTheOldScreen(String from, String to) {
        Parity p = backend();
        ScreenSession s = criteria(p, 2, from, to, "pbtDisplay");
        assertThat(window(s, "VHST04I")).as("VHST04I opened: " + value(s, "VHST05I", "Text1")).isNotNull();
        var old = new ArrayList<>(p.sent);
        List<Map<String, String>> oldTrains = new ArrayList<>();
        for (int page = 1; page <= 20; page++) {
            if (page > 1) {
                if (Boolean.FALSE.equals(widget(s, "VHST04I", "Push_Button4").get("enabled"))) break;
                s.event(id(s, "VHST04I"), List.of(), "Push_Button4", "clicked", null);
            }
            for (int b = 1; b <= 2; b++) {
                String rec = "VHST04IW-" + b;
                String c = value(s, "VHST04I", shownIn("VHST04I", rec, "CONSIST", "object"));
                if (c.replace("-", "").replace("0", "").isBlank()) continue;      // the empty consist past the last car (see the service)
                Map<String, String> t = new LinkedHashMap<>();
                t.put("consist", c);
                t.put("origin", value(s, "VHST04I", shownIn("VHST04I", rec, "ORIGIN", "object")));
                t.put("date", value(s, "VHST04I", shownIn("VHST04I", rec, "DATE-INSRV", "object")));
                t.put("time", value(s, "VHST04I", shownIn("VHST04I", rec, "TIME-INSRV", "object")));
                for (int k = 1; k <= 10; k++) {
                    String car = value(s, "VHST04I", shownIn("VHST04I", rec, "CAR" + k, "object"));
                    if (car.isEmpty()) continue;
                    t.put("car" + k, car + "|" + value(s, "VHST04I", shownIn("VHST04I", rec, "HY" + k, "object")) + "|"
                            + value(s, "VHST04I", shownIn("VHST04I", rec, "PM" + k, "object")) + "|" + value(s, "VHST04I", shownIn("VHST04I", rec, "WASH" + k, "object"))
                            + "|" + value(s, "VHST04I", shownIn("VHST04I", rec, "CLEAN" + k, "object")));
                }
                oldTrains.add(t);
            }
        }
        p.clear();
        var r = api(p).carDetails(from, to);
        for (String[] pr : new String[][] {{"VHST4A2", "VHST4A2_CRIT"}, {"VHST04A", "VHST04A_CRIT"}, {"VHST04B", "VHST04B_CRIT"}, {"VHST04B", "VHST04B_DATA"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0] + " " + pr[1]).isEqualTo(sent(old, pr[0], pr[1]));
        }
        List<Map<String, String>> apiTrains = new ArrayList<>();
        for (var t : r.trains()) {
            Map<String, String> x = new LinkedHashMap<>();
            x.put("consist", t.consist());
            x.put("origin", t.origin());
            x.put("date", t.dateInService());
            x.put("time", t.timeInService());
            int k = 1;
            for (var c : t.cars()) x.put("car" + k++, c.car() + "|" + c.homeYard() + "|" + c.pm() + "|" + c.washDays() + "|" + c.clean());
            apiTrains.add(x);
        }
        assertThat(apiTrains).isEqualTo(oldTrains);
    }

    // ---- VHST03I -------------------------------------------------------------------------------------------

    @Test
    void symptomViewMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession s = symptom(p, "201", "0120", "pbtDisplay");
        assertThat(window(s, "VHST03I")).as("VHST03I opened: " + value(s, "VHST01I", "Text1")).isNotNull();
        var old = new ArrayList<>(p.sent);
        p.clear();
        var r = api(p).symptoms("201", "0120");
        for (String[] pr : new String[][] {{"VHST3A3", "VHST3A3_CRIT"}, {"VHST3A4", "VHST3A4_CRIT"}, {"VHST3A1", "VHST3A1_CRIT"}}) {
            assertThat(sent(p.sent, pr[0], pr[1])).as(pr[0]).isEqualTo(sent(old, pr[0], pr[1]));
        }
        var t = r.trains().get(0);                                     // the old screen shows the first consist only
        String rec = "VHST03IW-1";
        assertThat(t.consist()).isEqualTo(value(s, "VHST03I", shownIn("VHST03I", rec, "CONSIST", "object")));
        assertThat(t.origin()).isEqualTo(value(s, "VHST03I", shownIn("VHST03I", rec, "ORIGIN", "object")));
        assertThat(t.dateInService()).isEqualTo(value(s, "VHST03I", shownIn("VHST03I", rec, "DATE-INSRV", "object")));
        assertThat(t.mark()).isEqualTo(value(s, "VHST03I", "MSG"));
        for (int k = 1; k <= 10; k++) {
            String car = value(s, "VHST03I", shownIn("VHST03I", rec, "SV_GR_CAR" + k, "object"));
            String count = value(s, "VHST03I", shownIn("VHST03I", rec, "HY" + k, "object"));
            if (k > t.cars().size()) {
                assertThat(car).as("car " + k).isEmpty();
                continue;
            }
            var c = t.cars().get(k - 1);
            String fg = String.valueOf(widget(s, "VHST03I", shownIn("VHST03I", "VHST03IW-COLOR", "CAR" + k + "-COLOR-1", "foregroundColor")).get("fg"));
            assertThat(((c.software() ? "$" : "") + (c.leftMark().isEmpty() ? " " : c.leftMark()) + c.car() + c.rightMark()).strip()).as("car " + k).isEqualTo(car);
            assertThat(String.valueOf(c.symptomCount())).as("count " + k).isEqualTo(count);
            assertThat(c.homeYard()).isEqualTo(value(s, "VHST03I", "HomeYard" + k));
            assertThat(c.alert() ? "RED" : c.usable() ? "DARK GREEN" : "BLACK").as("colour " + k).isEqualTo(fg);
        }
    }

    // ---- checks --------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "range ''{0}'' to ''{1}''")
    @CsvSource({"'',''", "0,''", "5,''", "abc,''", "all,''", "300,200", "300,20", "1234,''"})
    void rangeChecksMatchTheOldScreen(String from, String to) {
        Parity p = backend();
        ScreenSession s = criteria(p, 1, from, to, "pbtDisplay");
        String oldMessage = value(s, "VHST05I", "Text1");
        String message;
        try { api(p).range(from, to, false); message = ""; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(window(s, "VHST02I") == null).isEqualTo(!message.isEmpty());
    }

    @ParameterizedTest(name = "symptom ''{0}'' ''{1}''")
    @CsvSource({"'',0120", "201,''", "'',''", "ALL,0120"})
    void symptomChecksMatchTheOldScreen(String consist, String symp) {
        Parity p = backend();
        ScreenSession s = symptom(p, consist, symp, "pbtDisplay");
        String oldMessage = value(s, "VHST01I", "Text1");
        String message;
        try { api(p).checkSymptom(consist, symp); message = ""; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(window(s, "VHST03I")).isNull();
    }

    // ---- printing ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "print report {0}: {1} to {2}")
    @CsvSource({"1,201,''", "2,ALL,''", "1,201,620", "2,ALL,300"})
    void printMatchesTheOldScreen(int report, String from, String to) {
        Parity p = backend();
        criteria(p, report, from, to, "pbtHardcopy");
        var old = sent(p.sent, "VHST011", "VHST011_CRIT");
        assertThat(old).hasSize(1);
        p.clear();
        api(p).print(report, from, to, "DUBLNHP1");
        assertThat(sent(p.sent, "VHST011", "VHST011_CRIT")).isEqualTo(old);
    }

    @Test
    void symptomPrintMatchesTheOldScreen() {
        Parity p = backend();
        symptom(p, "201", "0120", "pbtHardcopy");
        var old = sent(p.sent, "VHST011", "VHST011_CRIT");
        assertThat(old).hasSize(1);
        p.clear();
        api(p).printSymptom("201", "0120", "DUBLNHP1");
        assertThat(sent(p.sent, "VHST011", "VHST011_CRIT")).isEqualTo(old);
    }
}
