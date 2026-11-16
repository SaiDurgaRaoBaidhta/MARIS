package com.bart.maris.middleware.api.prevmaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService.PmCriteria;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintService.Report;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The five PM reports and the hard copy against the old VHHPM1I and its child reports, run by the engine. */
class PrevMaintParityTest {
    static final Map<Report, String> PROGRAM = Map.of(Report.HOURS_BY_YARD, "VHHPRA1", Report.MTBI, "VHHPMA1", Report.BY_PM_TYPE, "VHPMRA1",
            Report.HOURS_DESCENDING, "VHHPDA1", Report.HOURS_TO_NEXT, "VHHPD2A");

    static Parity backend() {
        Parity p = new Parity();
        p.on("VHHPMA3", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHHPMA3_DATA", Map.of("timeRevStart", "03:00:00", "currentTime", "10:15:00",
                "currentDate", "2026-11-13", "yesterday", "2026-11-12")));
        p.on("VHHPMA2", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHHPMA2_DATA", Map.of("fromDate", "2026-01-01", "toDate", "2026-11-13")));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        List<Map<String, Object>> rows = List.of(
                m("carNo", "1101", "vehStat", "GO", "currLoc", "CY", "hrsFromLastPm", 120, "nextSchdPmNo", "2", "homeYrd", "CY",
                        "whereIsCar", "YRD", "trainIncdInd", "Y", "mtbi", 300, "mtbiHrs", 900, "mtbiIncds", 3),
                m("carNo", "1102", "vehStat", "NO GO", "currLoc", "M10", "hrsFromLastPm", 80, "nextSchdPmNo", "3", "homeYrd", "DY",
                        "whereIsCar", "CON", "conNo", "532", "mtbi", 100, "mtbiHrs", 400, "mtbiIncds", 4));
        for (String prog : PROGRAM.values()) {
            List<Map<String, Object>> own = rows.stream().map(r -> only(prog + "_DATA", r)).toList();
            p.on(prog, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), prog + "_DATA", Map.of("dataRow", own)));
        }
        p.on("VHHPM01", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    /** the fields of a row that the program's DATA_ROW has (each program returns its own subset) */
    static Map<String, Object> only(String record, Map<String, Object> row) {
        var layout = com.bart.maris.middleware.support.TestCatalog.get().services().stream()
                .flatMap(s -> s.params().stream()).filter(x -> record.equals(x.name()) && x.record() != null).findFirst().orElseThrow();
        var fields = com.bart.maris.middleware.support.TestCatalog.get().record(layout.record()).fields().get(0).children();
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> { if (fields.stream().anyMatch(f -> k.equals(f.json()))) out.put(k, v); });
        return out;
    }

    /** a map of any size from key, value pairs */
    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> windows(ScreenSession s) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) out.put((String) w.get("screen"), w);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String name) {
        return (Map<String, Object>) ((Map<String, Object>) windows(s).get(screen).get("widgets")).get(name);
    }

    @SuppressWarnings("unchecked")
    static int index(ScreenSession s, String list, String item) {
        return ((List<String>) widget(s, "VHHPM1I", list).get("items")).indexOf(item) + 1;
    }

    /** drives the old criteria screen: report, yard / location / PM criteria, then the given button */
    static ScreenSession old(Parity p, int rpt, String yard, String loc, String pmType, String carFrom, String carTo, String hFrom, String hTo,
                             String button) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHHPM1I", p.user);
        String w = (String) windows(s).get("VHHPM1I").get("id");
        s.event(w, List.of(), "Radio_Button_Set1", "select", rpt);
        if (!yard.isEmpty()) s.event(w, List.of(), "Drop-down_List1", "select", index(s, "Drop-down_List1", yard));
        if (!loc.isEmpty()) s.event(w, List.of(), "Drop-down_List11", "select", index(s, "Drop-down_List11", loc));
        if (!pmType.isEmpty()) s.event(w, List.of(), "Drop-down_List2", "select", index(s, "Drop-down_List2", pmType));
        List<ScreenSession.Change> ch = new ArrayList<>();
        ch.add(new ScreenSession.Change("Text1", "object", carFrom));
        ch.add(new ScreenSession.Change("Text2", "object", carTo));
        ch.add(new ScreenSession.Change("Text4", "object", hFrom));
        ch.add(new ScreenSession.Change("Text5", "object", hTo));
        if (button.equals("pbtHardcopy")) {
            s.event(w, List.of(), "Container_Details1", "select", 1);           // printer
        }
        s.event(w, ch, button, "clicked", null);
        return s;
    }

    static PrevMaintService api(Parity p) {
        return new PrevMaintService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    static Integer num(String s) {
        return s.isEmpty() ? null : Integer.valueOf(s);
    }

    @ParameterizedTest(name = "report {0} yard {1} loc {2}")
    @CsvSource({"1,CY,,,,,,", "1,ALL,,,,,,", "2,ALL,,,,,,", "2,DY,,,,,,", "3,ALL,,ALL,ALL,,,", "3,HY,,2,0100,0500,,", "4,RY,,,,,,", "5,,CY,,,,,", "5,,ALL,,,,,"})
    void displayMatchesTheOldReport(int rpt, String yard, String loc, String pmType, String carFrom, String carTo, String hFrom, String hTo) {
        yard = yard == null ? "" : yard; loc = loc == null ? "" : loc; pmType = pmType == null ? "" : pmType;
        carFrom = carFrom == null ? "" : carFrom; carTo = carTo == null ? "" : carTo; hFrom = hFrom == null ? "" : hFrom; hTo = hTo == null ? "" : hTo;
        Report report = Report.values()[rpt - 1];
        Parity p = backend();
        old(p, rpt, yard, loc, pmType, carFrom, carTo, hFrom, hTo, "pbtDisplay");
        Map<String, Map<String, Object>> oldSent = p.lastSent(PROGRAM.get(report));
        assertThat(oldSent).as("the old report called " + PROGRAM.get(report)).isNotNull();

        p.clear();
        var r = api(p).report(report, new PmCriteria(yard, loc, pmType, carFrom, carTo, num(hFrom), num(hTo)));
        assertThat(p.lastSent(PROGRAM.get(report)).get(PROGRAM.get(report) + "_CRIT")).isEqualTo(oldSent.get(PROGRAM.get(report) + "_CRIT"));
        assertThat(r.count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "print report {0}")
    @CsvSource({"1,CY,,,,,,", "4,ALL,,,,,,", "3,ALL,,ALL,ALL,,,", "3,HY,,2,0100,0500,10,200", "5,,DY,,,,,"})
    void hardCopyMatchesTheOldScreen(int rpt, String yard, String loc, String pmType, String carFrom, String carTo, String hFrom, String hTo) {
        yard = yard == null ? "" : yard; loc = loc == null ? "" : loc; pmType = pmType == null ? "" : pmType;
        carFrom = carFrom == null ? "" : carFrom; carTo = carTo == null ? "" : carTo; hFrom = hFrom == null ? "" : hFrom; hTo = hTo == null ? "" : hTo;
        Parity p = backend();
        old(p, rpt, yard, loc, pmType, carFrom, carTo, hFrom, hTo, "pbtHardcopy");
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHHPM01");
        assertThat(oldSent).as("the old screen printed").isNotNull();

        p.clear();
        api(p).print(Report.values()[rpt - 1], new PmCriteria(yard, loc, pmType, carFrom, carTo, num(hFrom), num(hTo)), "DUBLNHP1");
        assertThat(p.lastSent("VHHPM01").get("VHHPM01_CRIT")).isEqualTo(oldSent.get("VHHPM01_CRIT"));
    }
}
