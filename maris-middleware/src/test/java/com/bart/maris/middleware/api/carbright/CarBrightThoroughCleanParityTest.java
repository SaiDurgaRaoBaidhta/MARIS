package com.bart.maris.middleware.api.carbright;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.carbright.CarBrightThoroughCleanService;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Adding car thorough cleanings (old VHCBP1I) against the old screen run by the engine: the messages and what VHCBP1A gets. */
class CarBrightThoroughCleanParityTest {
    static final String TERMINAL = "MARIS-UI";

    /** @param found what VHCBP1A answers in RECORD-FOUND; car 9999 is not in the vehicle table, 8888 makes VHCBR2C fail */
    static Parity backend(String found) {
        Parity p = new Parity();
        p.on("VHIE01B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03.00.00", "currentTime", "10.15.32",
                "currentDate", "2026-11-10", "yesterday", "2026-11-09")));
        p.on("VHCBR2C", in -> {
            String car = String.valueOf(in.get("VHCBR2C_CRIT").get("carNo")).strip();
            if (car.equals("9999")) return Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            if (car.equals("8888")) return Map.of("XDCONTROL", Map.of("urc", 8, "urowcnt", 0, "umsgcode", "VH0001"));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCBR2C_CRIT", Map.of("carNo", car, "carType", "A2"));
        });
        p.on("VHCBP1A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHCBP1A_DATA", Map.of("recordFound", found)));
        return p;
    }

    static CarBrightThoroughCleanService api(Parity p) {
        return new CarBrightThoroughCleanService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String name) {
        return (Map<String, Object>) ((Map<String, Object>) window(s, "VHCBP1I").get("widgets")).get(name);
    }

    static String value(ScreenSession s, String name) {
        Object v = widget(s, name).get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static ScreenSession old(Parity p, String yard, String date, String time, List<String> cars) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCBP1I", p.user);
        String id = (String) window(s, "VHCBP1I").get("id");
        s.window(id).recordPart("VHCBP1IW-PASSVALUE").set("TERM-LAST-UPD data", TERMINAL);    // the menu passes the terminal id in
        int idx = ((List<String>) widget(s, "Drop-down_List1").get("items")).indexOf(yard) + 1;
        if (idx > 0) s.event(id, List.of(), "Drop-down_List1", "select", idx);
        else s.window(id).recordPart("VHCBP1IW").set("BRIT-LOC data", yard);                 // a yard the list does not have
        s.event(id, List.of(new ScreenSession.Change("txtDate", "object", date), new ScreenSession.Change("txtTime", "object", time)), null, null, null);
        for (int i = 0; i < cars.size(); i++) {
            s.event(id, List.of(new ScreenSession.Change("txtCar" + (i + 1), "object", cars.get(i))), "txtCar" + (i + 1), "losingFocus", null);
        }
        s.event(id, List.of(), "pbtAdd", "clicked", null);
        return s;
    }

    static List<String> cars(String list) {
        return list.isEmpty() ? List.of() : new ArrayList<>(Arrays.asList(list.split(";", -1)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "HY|11-09-2026|10:30|1101;1102;;1103|N",          // added (blank boxes between cars are skipped)
            "CY|11-09-2026|10:30|1101|Y",                     // duplicate cleaning
            "DY|11-09-2026|10:30|1101||",                     // VHCBP1A answers neither
            "HY|''|''|''|N",                                  // date, time and first car blank: one message
            "HY|12-31-2099|25:00|1101;1101|N",                // future date, bad time, duplicate cars
            "HY|11-09-2026|0000|1101|N",                      // time 0000
            "HY|11-09-2026|10:30|1101;11A1;9999|N",           // first bad car: not numeric
            "HY|11-09-2026|10:30|1101;9999|N",                // not in the vehicle table
            "XX|11-09-2026|10:30|1101|N"})                    // a yard the screen does not know
    void addMatchesTheOldScreen(String yard, String date, String time, String carList, String found) {
        List<String> cars = cars(carList);
        Parity p = backend(found == null ? "" : found);
        ScreenSession s = old(p, yard, date, time, cars);
        String oldMessage = value(s, "txtMsg");
        var oldAdd = p.lastSent("VHCBP1A") == null ? null : p.lastSent("VHCBP1A").get("VHCBP1A_DATA");          // XDCONTROL carries over between calls
        var oldChecks = p.sent.stream().filter(e -> e.getKey().equals("VHCBR2C")).map(e -> e.getValue().get("VHCBR2C_CRIT")).toList();

        p.clear();
        String message;
        try {
            message = api(p).add(yard, date, time, cars).message();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        var checks = p.sent.stream().filter(e -> e.getKey().equals("VHCBR2C")).map(e -> e.getValue().get("VHCBR2C_CRIT")).toList();
        assertThat(checks).as("VHCBR2C").isEqualTo(oldChecks);
        assertThat(p.lastSent("VHCBP1A") == null ? null : p.lastSent("VHCBP1A").get("VHCBP1A_DATA")).as("VHCBP1A").isEqualTo(oldAdd);
        if (yard.equals("XX")) {
            assertThat(oldMessage).as("the old screen says nothing for a yard it does not know").isEmpty();
            assertThat(message).isEqualTo(new EglTexts(Parity.bundle()).assigned("VHCBP1IS-ADD", "MRSVehicleCarBright", "ERROR-MESSAGE", "Invalid Yard"));
        } else {
            assertThat(message).as("message").isEqualTo(oldMessage);
        }
    }

    @Test
    void aVehicleCheckTheBackendRefusesStopsLikeTheOldScreen() {
        Parity p = backend("N");
        old(p, "HY", "11-09-2026", "10:30", List.of("1101", "8888", "1102"));
        assertThat(p.lastSent("VHCBP1A")).isNull();
        p.clear();
        try {
            api(p).add("HY", "11-09-2026", "10:30", List.of("1101", "8888", "1102"));
            throw new AssertionError("expected the backend error");
        } catch (LegacyBusinessException expected) {
            assertThat(p.lastSent("VHCBP1A")).isNull();
        }
    }

    @Test
    void defaultsAreTheOpeningValues() {
        Parity p = backend("N");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCBP1I", p.user);
        p.clear();
        var d = api(p).defaults();
        assertThat(d.date()).isEqualTo(value(s, "txtDate"));
        // the old time box is fed through TIME-CAR-BRIT-HH-MM, a part of a part, which the engine leaves blank (engine gap):
        // the API answers the time VHIE01B gives, as the real old screen shows it
        assertThat(d.time()).isEqualTo("10.15");
        String id = (String) window(s, "VHCBP1I").get("id");
        assertThat(d.yard()).isEqualTo(String.valueOf(s.window(id).recordPart("VHCBP1IW").get("BRIT-LOC data")).strip());
    }
}
