package com.bart.maris.middleware.api.incidentevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsSymptomHistoryService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The symptom history (old VHIE07I, opened by VHIE03I's Search) against the old screens run by the engine. */
class IncidentEventsSymptomHistoryParityTest {
    static Map<String, Object> event(String car, String con, int dash, String date) {
        return Map.ofEntries(Map.entry("carNo", car), Map.entry("sympCd", "0120"), Map.entry("conNo", con), Map.entry("dashNo", dash),
                Map.entry("dateInsrv", date), Map.entry("timeInsrv", "10:15:00"), Map.entry("incdNo", ""), Map.entry("narr", "DOOR STUCK " + car),
                Map.entry("carNo1", "<" + car + " "), Map.entry("carNo2", " 1102>"));
    }

    static Parity backend(int events, int incidents, boolean dateFile) {
        Parity p = IncidentEventsWorksheetParityTest.backend();
        p.on("VHIE07C", in -> dateFile ? Map.of("XDCONTROL", Map.of("urc", 0), "VHIE07C_DATA", Map.of("currentDate", "2026-11-14", "beginDate", "2026-08-16",
                "numDays", in.get("VHIE07C_CRIT").get("numDays"))) : Map.of("XDCONTROL", Map.of("urc", 100)));
        List<Map<String, Object>> ev = new ArrayList<>(), inc = new ArrayList<>();
        for (int i = 0; i < events; i++) ev.add(event("110" + i, "20" + i, 3 + i, "2026-10-0" + (i + 1)));
        for (int i = 0; i < incidents; i++) {
            var m = new java.util.HashMap<>(event("120" + i, "30" + i, 11 + i, "2026-09-1" + i));
            m.put("incdNo", "2999001" + i);
            inc.add(m);
        }
        p.on("VHIE07A", in -> events == 0 ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", events), "VHIE07A_CRIT", Map.of("sympCd", "0120", "recCount", events), "VHIE07A_DATA", Map.of("dataRow", ev)));
        p.on("VHIE07B", in -> incidents == 0 ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", incidents), "VHIE07B_CRIT", Map.of("sympCd", "0120", "recCounter", incidents),
                "VHIE07B_DATA", Map.of("dataRow", inc)));
        return p;
    }

    static IncidentEventsSymptomHistoryService api(Parity p) {
        return new IncidentEventsSymptomHistoryService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    /** VHIE03I: type the symptom, Search opens VHIE07I; then List with the days */
    static ScreenSession old(Parity p, String symptom, String days) {
        ScreenSession s = IncidentEventsWorksheetParityTest.open(p);
        String w = IncidentEventsWorksheetParityTest.id(s, "VHIE03I");
        s.event(w, List.of(new ScreenSession.Change("Text9", "object", symptom)), "Push_Button9", "clicked", null);
        String w7 = IncidentEventsWorksheetParityTest.id(s, "VHIE07I");
        assertThat(w7).as("VHIE07I opened").isNotNull();
        List<ScreenSession.Change> ch = days == null ? List.of() : List.of(new ScreenSession.Change("Text2", "object", days));
        s.event(w7, ch, "Push_Button2", "clicked", null);
        return s;
    }

    @Test
    void historyMatchesTheOldScreen() {
        for (int[] c : new int[][] {{2, 2}, {0, 3}, {3, 0}, {1, 2}}) {
            for (String days : new String[] {null, "30"}) {
                Parity p = backend(c[0], c[1], true);
                ScreenSession s = old(p, "0120", days);
                assertThat(IncidentEventsWorksheetParityTest.value(s, "VHIE07I", "Text2")).isEqualTo(days == null ? "90" : days);
                var oldCrit = new ArrayList<Object>();
                for (String pr : List.of("VHIE07C", "VHIE07A", "VHIE07B")) oldCrit.add(IncidentEventsWorksheetParityTest.records(p, pr));
                List<List<String>> oldRows = IncidentEventsWorksheetParityTest.rows(s, "VHIE07I", "VAGen_Container_Details_DATA_ROW");
                String oldMsg = IncidentEventsWorksheetParityTest.value(s, "VHIE07I", "Text1");
                p.clear();
                var h = api(p).history("0120", days == null ? null : Integer.valueOf(days));
                for (int k = 0; k < 3; k++) assertThat(IncidentEventsWorksheetParityTest.records(p, List.of("VHIE07C", "VHIE07A", "VHIE07B").get(k))).isEqualTo(oldCrit.get(k));
                List<List<String>> mine = new ArrayList<>();
                for (var r : h.rows()) {
                    List<String> row = new ArrayList<>(List.of(r.type(), r.car(), r.symptom(), r.dateInService(), r.timeInService(), r.consist(), r.incident()));
                    row.addAll(r.cars());
                    row.add(r.narrative());
                    mine.add(row);
                }
                assertThat(mine).as(c[0] + " events, " + c[1] + " incidents").isEqualTo(oldRows);
                assertThat(h.message() == null ? "" : h.message()).isEqualTo(oldMsg);
            }
        }
    }

    @Test
    void dateFileErrorMatchesTheOldScreen() {
        Parity p = backend(0, 0, false);
        ScreenSession s = old(p, "0120", null);
        String oldMsg = IncidentEventsWorksheetParityTest.value(s, "VHIE07I", "Text1");
        p.clear();
        assertThat(api(p).history("0120", null).message()).isEqualTo(oldMsg);
    }
}
