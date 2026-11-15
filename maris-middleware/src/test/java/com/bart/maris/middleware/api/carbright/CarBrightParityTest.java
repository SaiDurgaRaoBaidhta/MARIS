package com.bart.maris.middleware.api.carbright;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.carbright.CarBrightService;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Car thorough cleaning against the old VHCBR1I / VHCBR2I run by the engine. */
class CarBrightParityTest {
    private Parity backend() {
        Parity p = new Parity();
        p.on("VHCBR1X", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHCBR1X_DATA", Map.of("dateCarBrit", "2026-08-15", "overDueDate", "2026-05-15")));
        p.on("VHCBR1A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCBR1A_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "currLoc", "CY01", "vehStat", "GO", "dateCarBrit", "2026-07-01",
                        "carNo1", "1102", "currLoc1", "CY02", "vehStat1", "NO GO", "dateCarBrit1", "2026-06-01"),
                Map.of("carNo", "1103", "currLoc", "CY03", "vehStat", "GO", "dateCarBrit", "2026-05-01")))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHCBR02", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    private CarBrightService api(Parity p) {
        return new CarBrightService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> windows(ScreenSession s) {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) out.put((String) w.get("screen"), w);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> widget(ScreenSession s, String screen, String name) {
        return (Map<String, Object>) ((Map<String, Object>) windows(s).get(screen).get("widgets")).get(name);
    }

    private static int itemIndex(Map<String, Object> list, String item) {
        return ((List<String>) list.get("items")).indexOf(item) + 1;
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({"CY,1,last", "ALL,2,overdue", "HY,1,last"})
    void listMatchesTheOldScreen(String yard, int option, String apiOption) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCBR1I", p.user);
        String w = (String) windows(old).get("VHCBR1I").get("id");
        old.event(w, List.of(), "Drop-down_List1", "select", itemIndex(widget(old, "VHCBR1I", "Drop-down_List1"), yard));
        old.event(w, List.of(), "Radio_Button_Set1", "select", option);
        old.event(w, List.of(), "pbtList", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHCBR1A").get("VHCBR1A_CRIT");
        List<String> oldCars = new ArrayList<>();
        for (List<String> r : (List<List<String>>) widget(old, "VHCBR1I", "cntCarInfo").get("rows")) {
            oldCars.add(String.join("|", r.subList(0, 4)));
            if (!r.get(4).isBlank()) oldCars.add(String.join("|", r.subList(4, 8)));
        }

        p.clear();
        var r = api(p).list(yard, apiOption);
        assertThat(p.lastSent("VHCBR1A").get("VHCBR1A_CRIT")).isEqualTo(oldCrit);
        assertThat(r.cars().stream().map(c -> String.join("|", c.car(), c.location(), (c.status() + " " + c.trainIncident()).strip(), c.lastThoroughClean())).toList()).isEqualTo(oldCars);
    }

    @ParameterizedTest
    @CsvSource({"DY,1,last", "CY,2,overdue"})
    void printMatchesTheOldScreen(String yard, int option, String apiOption) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCBR1I", p.user);
        String w = (String) windows(old).get("VHCBR1I").get("id");
        old.event(w, List.of(), "Drop-down_List1", "select", itemIndex(widget(old, "VHCBR1I", "Drop-down_List1"), yard));
        old.event(w, List.of(), "Radio_Button_Set1", "select", option);
        old.event(w, List.of(), "pbtPrint", "clicked", null);
        String w2 = (String) windows(old).get("VHCBR2I").get("id");
        old.event(w2, List.of(), "cntPrinterdet", "select", 1);
        old.event(w2, List.of(), "pbtHardcopy", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHCBR02").get("VHCBR02_CRIT");

        p.clear();
        api(p).print(yard, apiOption, "DUBLNHP1");
        assertThat(p.lastSent("VHCBR02").get("VHCBR02_CRIT")).isEqualTo(oldCrit);
    }
}
