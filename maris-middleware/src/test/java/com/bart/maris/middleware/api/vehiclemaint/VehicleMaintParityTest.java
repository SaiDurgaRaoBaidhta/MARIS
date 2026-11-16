package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService.CarEventCriteria;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintReportsService.EventCriteria;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The vehicle event report (VHVM12I) and the car event report (VHVM13i) against the old screens run by the engine. */
class VehicleMaintParityTest {
    static Parity backend() {
        Parity p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHVM13B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM13B_DATA", Map.of("dataRow", List.of(Map.of("eventType", "CE"), Map.of("eventType", "MOD")))));
        p.on("VHVM11E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        p.on("VHVM12A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM13A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    @SuppressWarnings("unchecked")
    static String window(ScreenSession s) {
        return (String) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("id");
    }

    @SuppressWarnings("unchecked")
    static int index(ScreenSession s, String widget, String item) {
        Map<String, Object> w = (Map<String, Object>) ((Map<String, Object>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets")).get(widget);
        return ((List<String>) w.get("items")).indexOf(item) + 1;
    }

    static VehicleMaintReportsService api(Parity p) {
        return new VehicleMaintReportsService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @Test
    void choicesMatchTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHVM13i", p.user);
        VehicleMaintReportsService.ReportOptions o = api(p).options();
        assertThat(items(old, "Drop-down_List2")).isEqualTo(o.eventTypes()).containsExactly("CE", "MOD");
        assertThat(items(old, "Drop-down_List1")).isEqualTo(o.carTypes());
        assertThat(items(old, "Drop-down_List4").stream().filter(i -> !i.isBlank()).toList()).isEqualTo(o.homeYards());
    }

    @SuppressWarnings("unchecked")
    static List<String> items(ScreenSession s, String widget) {
        Map<String, Object> w = (Map<String, Object>) ((Map<String, Object>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets")).get(widget);
        return ((List<Object>) w.get("items")).stream().map(String::valueOf).toList();
    }

    @Test
    void vehicleEventReportMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHVM12I", p.user);
        String w = window(old);
        old.event(w, List.of(), "Container_Details1", "select", 1);
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", "11-01-2026"), new ScreenSession.Change("Text2", "object", "11-12-2026"),
                new ScreenSession.Change("Text6", "object", "1101"), new ScreenSession.Change("Text31", "object", "CE"),
                new ScreenSession.Change("Text4", "object", "OPEN"), new ScreenSession.Change("Text81", "object", "R123"),
                new ScreenSession.Change("Text7", "object", "CY01"), new ScreenSession.Change("Text8", "object", "CY"),
                new ScreenSession.Change("Text3", "object", "A")), "Push_Button2", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHVM12A").get("VHVM12A_CRIT");

        p.clear();
        api(p).printEvents(new EventCriteria("2026-11-01", "2026-11-12", "1101", (String) oldCrit.get("begCarType"), "CY", "CE", "A", "OPEN", "CY01",
                "R123", (String) oldCrit.get("sortOrder")), "DUBLNHP1");
        assertThat(p.lastSent("VHVM12A").get("VHVM12A_CRIT")).isEqualTo(oldCrit);
    }

    @Test
    void carEventReportMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHVM13i", p.user);
        String w = window(old);
        old.event(w, List.of(), "Drop-down_List1", "select", index(old, "Drop-down_List1", "C2"));
        old.event(w, List.of(), "Drop-down_List4", "select", index(old, "Drop-down_List4", "HY"));
        old.event(w, List.of(), "Drop-down_List2", "select", index(old, "Drop-down_List2", "MOD"));
        old.event(w, List.of(), "Container_Details1", "select", 1);
        old.event(w, List.of(new ScreenSession.Change("Text4", "object", "11-10-2026"), new ScreenSession.Change("Text7", "object", "R123"),
                new ScreenSession.Change("Text1", "object", "CHECK BRAKES")), "Push_Button2", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHVM13A").get("VHVM13A_CRIT");
        assertThat(oldCrit).as("the old screen submitted").isNotNull();

        p.clear();
        api(p).printCarEvents(new CarEventCriteria("C2", "2026-11-10", "MOD", "", "R123", "CHECK BRAKES", "HY"), "DUBLNHP1");
        assertThat(p.lastSent("VHVM13A").get("VHVM13A_CRIT")).isEqualTo(oldCrit);
    }
}
