package com.bart.maris.middleware.api.incidentevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidentevents.IncidentEventsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The incident log, its print and the consist look-up against the old VHIE01I / VHIE02I / VHIE06I. */
class IncidentEventsParityTest {
    static Parity backend() {
        Parity p = new Parity();
        p.on("VHIE01B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentTime", "10:00:00",
                "currentDate", "2026-11-12", "yesterday", "2026-11-11")));
        p.on("VHIE01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHIE01A_DATA", Map.of("dataRow", List.of(Map.of(
                "dateIncdOccrRev", "2026-11-10", "incdNo", "29990010", "carNo", "4377", "vehIncdStat", "OPN",
                "conNo2", Map.of("con", "197", "dash", "-", "conDashNo", 3))))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHIE02A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHIE03H", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE03H_DATA", Map.of("conNo", "532", "carNo", in.get("VHIE03H_DATA").get("carNo"), "dashNo", Map.of("dashNo1", "00", "dashNo2", "01"))));
        p.on("VHIE06A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHIE06A_DATA", Map.of("dataRow", List.of(
                Map.of("carFormat", " 3198}", "dashNo", Map.of("dashNo1", "00", "dashNo2", "01")), Map.of("carFormat", "{4074 ", "dashNo", Map.of("dashNo1", "00", "dashNo2", "01"))))));
        return p;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> windows(ScreenSession s) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) out.put((String) w.get("screen"), w);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Object value(ScreenSession s, String screen, String widget, String attr) {
        return ((Map<String, Object>) ((Map<String, Object>) windows(s).get(screen).get("widgets")).get(widget)).get(attr);
    }

    static IncidentEventsService api(Parity p) {
        return new IncidentEventsService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock);
    }

    @ParameterizedTest
    @CsvSource({"11-01-2026,11-12-2026,2026-11-01,2026-11-12", "11-10-2026,11-10-2026,2026-11-10,2026-11-10"})
    void logMatchesTheOldScreen(String from, String to, String isoFrom, String isoTo) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIE01I", p.user);
        assertThat(value(old, "VHIE01I", "Text1", "value")).isEqualTo("11-12-2026");          // the revenue date it starts with
        old.event((String) windows(old).get("VHIE01I").get("id"), List.of(new ScreenSession.Change("Text1", "object", from),
                new ScreenSession.Change("Text2", "object", to)), "Push_Button3", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHIE01A").get("VHIE01A_CRIT");
        @SuppressWarnings("unchecked") List<List<String>> oldRows = (List<List<String>>) value(old, "VHIE01I", "VAGen_Container_Details_DATA_ROW", "rows");

        p.clear();
        IncidentEventsService api = api(p);
        assertThat(api.revenueDate().revenueDate()).isEqualTo("2026-11-12");
        var log = api.incidents(isoFrom, isoTo);
        assertThat(p.lastSent("VHIE01A").get("VHIE01A_CRIT")).isEqualTo(oldCrit);
        assertThat(log.incidents().get(0).incident()).isEqualTo(oldRows.get(0).get(1));
        assertThat(log.incidents().get(0).consist()).isEqualTo(oldRows.get(0).get(9));
    }

    @Test
    void printMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIE01I", p.user);
        String w = (String) windows(old).get("VHIE01I").get("id");
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", "11-01-2026"), new ScreenSession.Change("Text2", "object", "11-12-2026")),
                "Push_Button3", "clicked", null);                                         // List (sets the report and dates)
        old.event(w, List.of(), "Push_Button2", "clicked", null);                         // Print: the printer window
        String w2 = (String) windows(old).get("VHIE02I").get("id");
        old.event(w2, List.of(), "Container_Details1", "select", 1);
        old.event(w2, List.of(), "Push_Button2", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHIE02A").get("VHIE02A_CRIT");

        p.clear();
        api(p).print("2026-11-01", "2026-11-12", "DUBLNHP1");
        assertThat(p.lastSent("VHIE02A").get("VHIE02A_CRIT")).isEqualTo(oldCrit);
    }

    @ParameterizedTest
    @CsvSource({"123", "4377"})
    void consistLookUpMatchesTheOldScreen(String car) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIE06I", p.user);
        String w = (String) windows(old).get("VHIE06I").get("id");
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", car)), "Push_Button3", "clicked", null);
        Map<String, Object> oldSent = p.lastSent("VHIE03H").get("VHIE03H_DATA");
        Object oldConsist = value(old, "VHIE06I", "Text2", "value");
        old.event(w, List.of(), "Push_Button4", "clicked", null);
        Map<String, Object> oldCars = p.lastSent("VHIE06A").get("VHIE06A_CRIT");
        Object oldCar1 = value(old, "VHIE06I", "Text14", "value");

        p.clear();
        var c = api(p).consistOfCar(car);
        assertThat(p.lastSent("VHIE03H").get("VHIE03H_DATA")).isEqualTo(oldSent);
        assertThat(c.consist()).isEqualTo(oldConsist);
        var cars = api(p).consistCars(c.consist());
        assertThat(p.lastSent("VHIE06A").get("VHIE06A_CRIT")).isEqualTo(oldCars);
        assertThat(cars.cars().get(0)).isEqualTo(oldCar1);
    }
}
