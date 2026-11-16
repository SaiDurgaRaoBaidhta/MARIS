package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesReports;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesReportsService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reports criteria window: the clean API against the old VHYT27I run by the engine (opening it, and Hard Copy per report). */
class YardQueriesReportsParityTest {
    private Parity p;

    private void backend() {
        p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrLocDesc", "DUBLIN", "vtamName", "DUBLNHP1"), Map.of("prtrLocDesc", "LAKESIDE", "vtamName", "LKS11HP1")))));
        p.on("VHYT27B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHYT27B_DATA", Map.of("startDate", "11-01-2026", "endDate", "11-12-2026",
                "startDatedb2", "2026-11-01", "endDatedb2", "2026-11-12")));
        p.on("VHRT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHRT04A_DATA", Map.of("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "RY", "yrdDesc", "RICHMOND YARD")))));
        for (String w : List.of("VHYT27A", "VHYT27C", "VHYT27D", "VHYT27E", "VHYT27F", "VHYT27G")) p.on(w, in -> Map.of("XDCONTROL", Map.of("urc", 0)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> widgets(ScreenSession s) {
        return (Map<String, Map<String, Object>>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets");
    }

    @Test
    void openingAsksForTheSameListsAndShowsTheSame() {
        backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT27I", p.user);
        List<String> oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();
        Map<String, Map<String, Object>> w = widgets(old);

        p.clear();
        YardQueriesReports.Options o = new YardQueriesReportsService(p.legacy(), p.messages(), Parity.bundle()).options();
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        assertThat(w.get("Container_Details1").get("rows")).isEqualTo(o.printers().stream().map(x -> List.of(x.description(), x.vtam())).toList());
        @SuppressWarnings("unchecked") List<String> items = (List<String>) w.get("Drop-down_List1").get("items");
        assertThat(items.stream().map(i -> i.split("\\s+-\\s+")[0].strip()).toList()).isEqualTo(o.yards().stream().map(YardQueriesReports.Code::code).toList());
        assertThat(w.get("Text1").get("value")).isEqualTo("11-01-2026");
        assertThat(o.from()).isEqualTo("2026-11-01");
    }

    /** VHYT27I opened by the menu for a report, a yard and the first printer chosen, dates and cars typed, Hard Copy */
    private Map<String, Map<String, Object>> oldPrint(int index, String code, String yard, String from, String to, String fromCar, String program) {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT27I", p.user);
        String w = ((Map<?, ?>) ((List<?>) old.state().get("windows")).get(0)).get("id").toString();
        ScreenInstance s = old.window(w);
        s.recordPart("VHYT27IW").set("REPORT-INDEX data", index);
        s.recordPart("VHYT27IW-PASSVALUE").set("WHICH-REPORT data", code);
        s.recordPart("VHYT27IW-PASSVALUE").set("YARD-SELECTION data", yard);
        old.event(w, List.of(), "Container_Details1", "select", 1);
        p.clear();
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", from), new ScreenSession.Change("Text2", "object", to),
                new ScreenSession.Change("Text11", "object", fromCar)), "Push_Button2", "clicked", null);
        Map<String, Map<String, Object>> sent = p.lastSent(program);
        assertThat(sent).as(code + ": the old screen printed with " + program + "; it sent " + p.sent.stream().map(Map.Entry::getKey).toList()).isNotNull();
        return sent;
    }

    @Test
    void eachHardCopySendsTheSamePrintRecord() {
        String[][] cases = {
                {"1", "SHOPSTAT", "VHYT27A", "CY", "", ""}, {"2", "YARD-LOC", "VHYT27F", "RY", "", ""},
                {"3", "CAR-HIST", "VHYT27C", "CY", "1200", ""}, {"4", "YARDHIST", "VHYT27D", "RY", "", ""},
                {"5", "0400-RPT", "VHYT27E", "CY", "", ""}, {"8", "04XX-RPT", "VHYT27G", "RY", "", ""}};
        for (String[] c : cases) {
            backend();
            Map<String, Map<String, Object>> oldSent = oldPrint(Integer.parseInt(c[0]), c[1], c[3], "11-01-2026", "11-10-2026", c[4], c[2]);
            p.clear();
            new YardQueriesReportsService(p.legacy(), p.messages(), Parity.bundle())
                    .print(new YardQueriesReports.Print(c[1], c[3], "DUBLNHP1", "2026-11-01", "2026-11-10", c[4], ""));
            assertThat(p.lastSent(c[2]).get(c[2] + "_CRIT")).as(c[1]).isEqualTo(oldSent.get(c[2] + "_CRIT"));
        }
    }
}
