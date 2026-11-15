package com.bart.maris.middleware.api.carwash;

import static com.bart.maris.middleware.api.carwash.CarWashConsistWashParityTest.business;
import static com.bart.maris.middleware.api.carwash.CarWashConsistWashParityTest.value;
import static com.bart.maris.middleware.api.carwash.CarWashConsistWashParityTest.window;
import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.carwash.CarWashCarsWashService;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Wash of single cars (old VHCWA3I) against the old screen run by the engine. */
class CarWashCarsWashParityTest {
    /** the car boxes CAR-NO1..CAR-NO10 and their "B" marks, in order */
    static final List<String> BOXES = List.of("Text7", "Text8", "Text2", "Text9", "Text10", "Text11", "Text12", "Text13", "Text14", "Text15");
    static final List<String> MARKS = List.of("Text3", "Text16", "Text17", "Text18", "Text19", "Text20", "Text21", "Text22", "Text23", "Text24");

    static Parity backend(Set<String> invalid) {
        Parity p = new Parity();
        p.on("VHIE01B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHIE01B_DATA", Map.of("timeRevStart", "03:00:00", "currentDate", "2026-11-14",
                "currentTime", "02:15:00", "yesterday", "2026-11-13")));
        p.on("VHINVB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCWA3A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHCWA3A_DATA",
                Map.of("carIsValid", invalid.contains(String.valueOf(in.get("VHCWA3A_CRIT").get("carNo"))) ? "N" : "Y")));
        p.on("VHCWA3B", in -> {
            String car = String.valueOf(in.get("VHCWA3B_CRIT").get("carNo"));
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHCWA3B_DATA", Map.of("carNo", car, "dateCarWash", "2026-11-0" + car.charAt(3),
                    "dateNxtCarWash", "2026-12-0" + car.charAt(3), "cntTimesWash", 7));
        });
        p.on("VHCWA3D", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static CarWashCarsWashService api(Parity p) {
        return new CarWashCarsWashService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), CarWashConsistWashParityTest.TERMINAL);
    }

    /** the old screen: yard, date and time typed, time left (VHCWA3IS-A-TIME), cars typed, then Update */
    static ScreenSession old(Parity p, String yard, String date, String time, List<String> cars, boolean update) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCWA3I", p.user);
        String id = (String) window(s, "VHCWA3I").get("id");
        List<ScreenSession.Change> changes = new ArrayList<>();
        changes.add(new ScreenSession.Change("Text4", "object", yard));
        if (date != null) changes.add(new ScreenSession.Change("Text6", "object", date));
        changes.add(new ScreenSession.Change("Text1", "object", time));
        s.event(id, changes, "Text1", "losingFocus", null);
        List<ScreenSession.Change> boxes = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) boxes.add(new ScreenSession.Change(BOXES.get(i), "object", cars.get(i)));
        if (!boxes.isEmpty()) s.event(id, boxes, BOXES.get(0), "losingFocus", null);
        if (update) s.event(id, List.of(), "pbtDisplay", "clicked", null);
        return s;
    }

    static List<Map<String, Map<String, Object>>> calls(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(business(e.getValue()));
        return out;
    }

    @Test
    void openingMatches() {
        Parity p = backend(Set.of());
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCWA3I", p.user);
        p.clear();
        var start = api(p).start();
        assertThat(start.consist()).isEqualTo(value(s, "VHCWA3I", "txtFromdate"));
        assertThat(start.message()).isEqualTo(value(s, "VHCWA3I", "Text5"));
        String d = start.washDate();
        assertThat(d.substring(5, 7) + "-" + d.substring(8) + "-" + d.substring(0, 4)).isEqualTo(value(s, "VHCWA3I", "Text6"));
    }

    @Test
    void washSendsWhatTheOldScreenSends() {
        Parity p = backend(Set.of());
        List<String> cars = List.of("1101", "1102", "1203");
        ScreenSession s = old(p, "HY", null, "0745", cars, true);
        var oldSent = new ArrayList<>(p.sent);
        String oldMessage = value(s, "VHCWA3I", "Text5");
        p.clear();
        var result = api(p).wash(new CarWashCarsWashService.Wash("HY", "2026-11-13", "0745", "088", cars));
        for (String program : List.of("VHINVB", "VHCWA3A", "VHCWA3B", "VHCWA3D")) {
            assertThat(calls(p.sent, program)).as(program).isEqualTo(calls(oldSent, program));
        }
        assertThat(result.message()).isEqualTo(oldMessage);
    }

    @Test
    void invalidCarsAreMarkedAsTheOldScreenMarksThem() {
        Parity p = backend(Set.of("1102", "1204"));
        List<String> cars = List.of("1101", "1102", "1203", "1204");
        ScreenSession s = old(p, "CY", null, "07:45", cars, true);
        String oldMessage = value(s, "VHCWA3I", "Text5");
        List<Integer> oldMarks = new ArrayList<>();
        for (int i = 0; i < MARKS.size(); i++) if (value(s, "VHCWA3I", MARKS.get(i)).equals("B")) oldMarks.add(i + 1);
        p.clear();
        try {
            api(p).wash(new CarWashCarsWashService.Wash("CY", "2026-11-13", "07:45", "088", cars));
            throw new AssertionError("no error");
        } catch (CarWashCarsWashService.InvalidCarsException e) {
            assertThat(e.getMessage()).isEqualTo(oldMessage);
            assertThat(e.boxes()).isEqualTo(oldMarks).containsExactly(2, 4);
        }
        assertThat(calls(p.sent, "VHCWA3D")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"HY|2530|1101", "HY|07:75|1101", "HY|ab|1101", "XX|0745|1101", "HY|0745|"}, delimiter = '|')
    void checksMatchTheOldScreen(String yard, String time, String car) {
        Parity p = backend(Set.of());
        List<String> cars = car == null ? List.of() : List.of(car);
        ScreenSession s = old(p, yard, null, time, cars, true);
        String oldMessage = value(s, "VHCWA3I", "Text5");
        p.clear();
        String message;
        try {
            api(p).wash(new CarWashCarsWashService.Wash(yard, "2026-11-13", time, "088", cars));
            message = null;
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(calls(p.sent, "VHCWA3D")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"0745", "07.45", "7:45", "07:45:30", "1"}, delimiter = '|')
    void timeIsStoredAsTheOldScreenStoresIt(String time) {
        Parity p = backend(Set.of());
        ScreenSession s = old(p, "HY", null, time, List.of(), false);
        var t = api(p).time(time);
        assertThat(t.time()).isEqualTo(value(s, "VHCWA3I", "Text1"));
        assertThat(t.message()).isEqualTo(value(s, "VHCWA3I", "Text5"));
    }

    @Test
    void carsAfterABlankBoxAreLeftOut() {
        Parity p = backend(Set.of());
        List<String> cars = Arrays.asList("1101", "", "1203");
        old(p, "HY", null, "0745", cars, true);
        var oldSent = new ArrayList<>(p.sent);
        p.clear();
        api(p).wash(new CarWashCarsWashService.Wash("HY", "2026-11-13", "0745", "088", cars));
        assertThat(calls(p.sent, "VHCWA3D")).isEqualTo(calls(oldSent, "VHCWA3D"));
    }
}
