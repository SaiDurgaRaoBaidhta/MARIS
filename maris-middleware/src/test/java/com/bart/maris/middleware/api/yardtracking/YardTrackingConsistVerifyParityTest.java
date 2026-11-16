package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingConsistVerifyService;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verify / reset a pseudo train (old VHYT12I) against the old screen run by the engine: same backend, same train number
 * typed, same answers to the prompts; both must show the same cars, message and prompts, and send the same records to
 * every program, the writers VHYT12D / VHYT12E included.
 */
class YardTrackingConsistVerifyParityTest {
    /** a car row of VHYT12A: car, type, status, ATO, train incident, orientation (left code "<") */
    static Map<String, Object> car(String no, String type, String stat, String ato, String ti, String orient, int pos) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", no, "carType", type, "vehStat", stat, "atoPresentInd", ato, "trainIncdInd", ti,
                "vagenOrientation", orient, "lftOrientationCd", "<", "rgtOrientationCd", ">", "posnNo", pos, "homeYrd", pos % 2 == 0 ? "HY" : "RY"));
        m.put("conNo", "");
        m.put("yrdSubLoc", "T-10");
        return m;
    }

    static final List<Map<String, Object>> GOOD = List.of(car("1101", "A2", "GO", "Y", "N", "<", 1), car("1102", "B2", "USABLE", "N", "N", "<", 2),
            car("1103", "C2", "GO", "Y", "N", ">", 3), car("1104", "A2", "GO", "Y", "N", ">", 4));
    static final List<Map<String, Object>> BAD = List.of(car("1101", "A2", "NO GO", "Y", "N", "<", 1), car("1102", "Q9", "GO", "N", "N", "<", 2),
            car("1103", "A2", "GO", "N", "Y", ">", 3));

    /** @param inConsist VHYT12C finds every car on train 777; @param trainCars VHYT12B's car count for the typed train (0: urc 100) */
    static Parity backend(List<Map<String, Object>> cars, boolean inConsist, int trainCars, String trainYard) {
        Parity p = new Parity();
        Function<Map<String, Map<String, Object>>, Map<String, Object>> a = in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars.size()),
                "VHYT12A_DATA", Map.of("dataRow", cars));
        p.on("VHYT12A", a);
        p.on("VHYT12C", in -> inConsist
                ? Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT12C_DATA", Map.of("conNo", "777", "partInd", "P1"))
                : Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0), "VHYT12C_DATA", Map.of()));
        p.on("VHYT12B", in -> trainCars == 0 ? Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0), "VHYT12B_DATA", Map.of())
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHYT12B_DATA", Map.of("carInConCnt", trainCars, "yrdTrkgYrd", trainYard)));
        p.on("VHYT12D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT12D_DATA", in.get("VHYT12D_DATA")));
        p.on("VHYT12E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT12E_DATA", in.get("VHYT12E_DATA")));
        return p;
    }

    static String text(RecordPart r, String f) {
        Object v = r.get(f + " data");
        return v == null ? "" : v.toString().strip();
    }

    /** the old window as the API describes it: cars "k:button colour con conColour loc home", message, prompt */
    @SuppressWarnings("unchecked")
    static List<String> oldCars(ScreenSession s, String id) {
        RecordPart w = s.window(id).recordPart("VHYT12IW"), c = s.window(id).recordPart("VHYT12IW-COLOR");
        List<String> out = new ArrayList<>();
        for (int k = 1; k <= 10; k++) {
            if (text(w, "BUTTON-" + k).isEmpty() && text(w, "CAR-NO-" + k).isEmpty()) continue;
            out.add(k + ":" + text(w, "BUTTON-" + k) + " " + text(c, "CAR-" + k + "-COLOR") + " " + text(w, String.format("CON-%02d", k)) + " "
                    + text(c, "CON-" + k + "-COLOR") + " " + text(w, "CAR-" + k + "-CURR-LOC") + " " + text(w, "HOME-YRD-" + k));
        }
        return out;
    }

    static List<String> cars(YardTrackingConsistVerifyService.State st) {
        List<String> out = new ArrayList<>();
        for (var c : st.cars()) out.add(c.position() + ":" + c.car() + " " + c.colour() + " " + c.consist() + " " + c.consistColour() + " " + c.location() + " " + c.homeYard());
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> dialog(ScreenSession s) {
        return (Map<String, Object>) s.state().get("dialog");
    }

    static ScreenSession old(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT12I", p.user);
        return s;
    }

    static YardTrackingConsistVerifyService.Pass pass(ScreenSession s, String id) {
        RecordPart pv = s.window(id).recordPart("VHYT12IW-PASSVALUE");
        String from = text(pv, "FROM-POS"), to = text(pv, "TO-POS");
        return new YardTrackingConsistVerifyService.Pass(text(pv, "YRD-TRKG-YRD"), text(pv, "YRD-SUB-LOC"), from.isEmpty() ? 0 : Integer.parseInt(from),
                to.isEmpty() ? 0 : Integer.parseInt(to), text(pv, "USER-GROUP"), text(pv, "TERM-LAST-UPD"));
    }

    static YardTrackingConsistVerifyService api(Parity p) {
        return new YardTrackingConsistVerifyService(Parity.bundle(), p.legacy(), Clock.systemDefaultZone(), "");
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls, String program) {
        List<Object> out = new ArrayList<>();
        for (var e : calls) if (e.getKey().equals(program)) out.add(new HashMap<>(e.getValue()));
        return out;
    }

    void same(Parity p, ScreenSession s, String id, YardTrackingConsistVerifyService.State st, List<Map.Entry<String, Map<String, Map<String, Object>>>> oldCalls) {
        assertThat(cars(st)).isEqualTo(oldCars(s, id)).isNotEmpty();
        assertThat(st.message()).isEqualTo(text(s.window(id).recordPart("VHYT12IW-MSG"), "FINAL-MESSAGE"));
        for (String program : List.of("VHYT12A", "VHYT12B", "VHYT12C", "VHYT12D", "VHYT12E")) {
            assertThat(sent(p.sent, program)).as(program).isEqualTo(sent(oldCalls, program));
        }
    }

    @Test
    void openingMatchesTheOldScreen() {
        for (var cars : List.of(GOOD, BAD)) {
            Parity p = backend(cars, false, 0, "");
            ScreenSession s = old(p);
            String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
            var calls = new ArrayList<>(p.sent);
            p.clear();
            var st = api(p).open(pass(s, id), Map.of());
            same(p, s, id, st, calls);
            assertThat(st.question()).isNull();
            assertThat(dialog(s)).isNull();
        }
    }

    @Test
    void verifyWritesTheSameTrain() {
        Parity p = backend(GOOD, false, 0, "");
        ScreenSession s = old(p);
        String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
        var pass = pass(s, id);
        s.event(id, List.of(new ScreenSession.Change("Text2", "object", "345")), "Push_Button3", "clicked", null);
        var calls = new ArrayList<>(p.sent);
        assertThat(sent(calls, "VHYT12D")).as("old VHYT12D").hasSize(1);
        p.clear();
        var st = api(p).verify(pass, "345", Map.of());
        same(p, s, id, st, calls);
        assertThat(st.verifyEnabled()).isFalse();
        assertThat(st.resetEnabled()).isTrue();
    }

    @Test
    void aTrainThatHasCarsAsksBeforeOverriding() {
        Parity p = backend(GOOD, false, 3, "");
        ScreenSession s = old(p);
        String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
        var pass = pass(s, id);
        // the train's yard is the passed yard (blank here), so the screen asks whether to override it
        s.event(id, List.of(new ScreenSession.Change("Text2", "object", "345")), "Push_Button3", "clicked", null);
        Map<String, Object> d = dialog(s);
        assertThat(d).as("old prompt").isNotNull();
        Parity p2 = backend(GOOD, false, 3, "");
        var q = api(p2).verify(pass, "345", Map.of()).question();
        assertThat(q).isNotNull();
        assertThat(q.key()).isEqualTo("override");
        assertThat(q.text()).isEqualTo(String.valueOf(d.get("message")).strip());
        assertThat(q.title()).isEqualTo(d.get("title"));
        assertThat(sent(p2.sent, "VHYT12D")).as("nothing written before Yes").isEmpty();
        Parity p3 = backend(GOOD, false, 3, "");
        ScreenSession s3 = old(p3);
        String id3 = YardTrackingPseudoTrainParityTest.id(s3, "VHYT12I");
        s3.event(id3, List.of(new ScreenSession.Change("Text2", "object", "345")), "Push_Button3", "clicked", null);
        s3.answer(((Number) dialog(s3).get("id")).intValue(), "Yes");
        var calls = new ArrayList<>(p3.sent);
        assertThat(sent(calls, "VHYT12D")).as("old VHYT12D after Yes").hasSize(1);
        p3.clear();
        var st = api(p3).verify(pass, "345", Map.of("override", "yes"));
        same(p3, s3, id3, st, calls);
    }

    @Test
    void carsOnOtherTrainsAndBadCarsMatchTheOldScreen() {
        Parity p = backend(GOOD, true, 0, "");
        ScreenSession s = old(p);
        String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
        Map<String, Object> d = dialog(s);
        assertThat(d).as("old asks about cars on other trains").isNotNull();
        var q = api(backend(GOOD, true, 0, "")).open(pass(s, id), Map.of()).question();
        assertThat(q.key()).isEqualTo("carsInConsist");
        assertThat(q.text()).isEqualTo(String.valueOf(d.get("message")).strip());
        s.answer(((Number) d.get("id")).intValue(), "Yes");
        var calls = new ArrayList<>(p.sent);
        p.clear();
        var st = api(p).open(pass(s, id), Map.of("carsInConsist", "yes"));
        same(p, s, id, st, calls);

        Parity b = backend(BAD, false, 0, "");
        ScreenSession sb = old(b);
        String idb = YardTrackingPseudoTrainParityTest.id(sb, "VHYT12I");
        var passb = pass(sb, idb);
        sb.event(idb, List.of(new ScreenSession.Change("Text2", "object", "345")), "Push_Button3", "clicked", null);
        Map<String, Object> db = dialog(sb);
        var callsb = new ArrayList<>(b.sent);
        b.clear();
        var stb = api(b).verify(passb, "345", Map.of());
        same(b, sb, idb, stb, callsb);
        assertThat(db).as("Verify is disabled after bad cars: the old screen does nothing").isNull();
        assertThat(stb.notice()).isNull();
        assertThat(stb.verifyEnabled()).isFalse();
    }

    @Test
    void resetWritesTheSameCars() {
        Parity p = backend(GOOD, true, 0, "");                             // the cars are on a train: Reset is enabled
        ScreenSession s = old(p);
        String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
        var pass = pass(s, id);
        s.answer(((Number) dialog(s).get("id")).intValue(), "Yes");
        s.event(id, List.of(), "pbReset", "clicked", null);
        var calls = new ArrayList<>(p.sent);
        assertThat(sent(calls, "VHYT12E")).as("old VHYT12E").hasSize(1);
        p.clear();
        var st = api(p).reset(pass, Map.of("carsInConsist", "yes"));
        same(p, s, id, st, calls);
    }

    @Test
    void checksMatchTheOldScreen() {
        for (String con : List.of("", "12", "AB1", "1000")) {
            Parity p = backend(GOOD, false, 0, "");
            ScreenSession s = old(p);
            String id = YardTrackingPseudoTrainParityTest.id(s, "VHYT12I");
            var pass = pass(s, id);
            s.event(id, List.of(new ScreenSession.Change("Text2", "object", con)), "Push_Button3", "clicked", null);
            var calls = new ArrayList<>(p.sent);
            p.clear();
            var st = api(p).verify(pass, con, Map.of());
            assertThat(st.message()).as("'" + con + "'").isEqualTo(text(s.window(id).recordPart("VHYT12IW-MSG"), "FINAL-MESSAGE")).isNotEmpty();
            same(p, s, id, st, calls);
        }
    }
}
