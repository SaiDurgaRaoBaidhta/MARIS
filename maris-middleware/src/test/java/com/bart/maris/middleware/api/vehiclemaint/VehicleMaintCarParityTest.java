package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclemaint.VehicleMaintCarService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.security.MarisUser;
import com.bart.maris.middleware.support.Parity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Add a car (VHVM02I) and change a car (VHVM03I) against the old screens run by the engine. */
class VehicleMaintCarParityTest {
    static final String TERMINAL = "MARIS-UI";                       // the engine's terminal id (maris.engine.terminal-id default)

    static Map<String, Object> car() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("carNo", "1101"); c.put("carType", "C2"); c.put("vehStat", "GO"); c.put("vehStatPrev", "PM"); c.put("homeYrd", "CY");
        c.put("warInd", "N"); c.put("atoPresentInd", "Y"); c.put("batteryReplInd", "Y"); c.put("dateBatteryRepl", "2025-06-01");
        c.put("batteryReplLoc", "CY"); c.put("cameraCd", "H"); c.put("vagenOrientation", "<"); c.put("currSchdPmNo", "2");
        c.put("nextSchdPmNo", "3"); c.put("lastSchdPmNo", "1"); c.put("hrsToNextPm", 120); c.put("hrsFromLastPm", 30); c.put("hrsAtLastPm", 4000);
        c.put("cntTimesWash", 7); c.put("cntTimesPaInspc", 2); c.put("trainIncdInd", "N"); c.put("currLoc", "CY01"); c.put("whereIsCar", "YRD");
        c.put("lastPmLoc", "CY"); c.put("conNo", "201"); c.put("dashNo", 3); c.put("cumHrs", 5000); c.put("mtbi", 12);
        c.put("dateEndCarRev", "2026-10-01"); c.put("configCmpntStat", "ACTIVE"); c.put("configCmpntType", "CBTC");
        return c;
    }

    static Parity backend() {
        Parity p = new Parity();
        p.on("VHVM1A6", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM1A6_DATA", Map.of("dataRow", List.of(
                Map.of("cameraCd", "H", "cameraCdDesc", "HIGH DEF"), Map.of("cameraCd", "N", "cameraCdDesc", "NONE")))));
        p.on("VHVM1A7", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHVM1A7_DATA", Map.of("vagenCount", 2, "dataRow", List.of(
                Map.of("carType", "C2", "actvInd", "Y", "lftOrientationCd", "<", "rgtOrientationCd", ">"),
                Map.of("carType", "B2", "actvInd", "Y", "lftOrientationCd", "B", "rgtOrientationCd", "b")))));
        p.on("VHCY1A1", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCY1A1_DATA", Map.of("dataRow", List.of(Map.of("loc", "CY01"), Map.of("loc", "HY02")))));
        p.on("VHVM1A9", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM1A9_DATA", Map.of("dataRow", List.of(Map.of("configCmpntStat", "ACTIVE"))),
                "VHVM1A9B_DATA", Map.of("dataRow", List.of(Map.of("cmpntEquipType", "CBTC")))));
        p.on("VHVM1A5", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM1A5_CRIT", car(), "VHVM1A5_DATA", car()));
        p.on("VHVM1A4", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM1AA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM1AA_DATA", Map.of("hrsToNextPm", 250, "cntDaysNxtWash", 14,
                "cntDaysNxtPa", 30, "dateNxtWash", "2026-11-28", "dateNxtPa", "2026-12-14")));
        p.on("VHVM1A3", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VH01A01", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VH01A01_DATA", in.get("VH01A01_DATA")));   // sign-on data, as sent
        return p;
    }

    static VehicleMaintCarService api(Parity p) {
        return new VehicleMaintCarService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Parity.bundle(), p.clock, TERMINAL);
    }

    /**
     * the records a program got, without XDCONTROL and without two things the engine does not reproduce: the camera code
     * (the engine shows no rows in the camera list, a list fed by a whole array row, so the old screen sends a blank one)
     * and the terminal id (VHMMMI hands VHVM02I/VHVM03I the whole VH01A01_DATA record, which the engine does not pass on,
     * so TERM-ID stays blank there; VHVM06I gets TERM-ID field by field and is compared in full)
     */
    static Map<String, Map<String, Object>> comparable(Map<String, Map<String, Object>> sent) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        sent.forEach((k, v) -> {
            if (k.equals("XDCONTROL")) return;
            Map<String, Object> m = new LinkedHashMap<>(v);
            m.keySet().removeIf(f -> f.startsWith("term") || f.equals("cameraCd"));
            out.put(k, m);
        });
        return out;
    }

    static MarisUser user(String group) {
        return new MarisUser(group, "TEST", "VH001I", "ALL", "HY", "", "ADMIN", Instant.now());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s) {
        List<Map<String, Object>> ws = (List<Map<String, Object>>) s.state().get("windows");
        return ws.get(ws.size() - 1);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String w) {
        return (Map<String, Object>) ((Map<String, Object>) window(s).get("widgets")).get(w);
    }

    @SuppressWarnings("unchecked")
    static List<String> items(ScreenSession s, String w) {
        return ((List<Object>) widget(s, w).get("items")).stream().map(String::valueOf).map(String::strip).toList();
    }

    static int index(ScreenSession s, String w, String item) {
        return items(s, w).indexOf(item) + 1;
    }

    /** the messages the old screen shows, answering each with OK */
    @SuppressWarnings("unchecked")
    static List<String> dialogs(ScreenSession s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
            if (d == null) break;
            out.add(String.valueOf(d.get("message")).strip());
            s.answer((Integer) d.get("id"), "OK");
        }
        return out;
    }

    /** the old screen opened from its menu VHMMMI, which reads the sign-on data (VH01A01) and passes it down */
    static ScreenSession menu(Parity p, MarisUser u, String screen) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMMMI", u);
        dialogs(s);
        String button = Parity.bundle().screen("VHMMMI").path("connections").findParents("a").stream()
                .filter(c -> c.path("a").path(2).path("id").asText().equals(screen) && c.path("a").path(3).asText().equals("openWidget"))
                .map(c -> c.path("a").path(0).path("id").asText().substring(7)).findFirst().orElseThrow();
        s.event((String) window(s).get("id"), List.of(), button, "clicked", null);
        dialogs(s);
        return s;
    }

    static ScreenSession found(Parity p, String group) {
        ScreenSession s = menu(p, user(group), "VHVM03I");
        String w = (String) window(s).get("id");
        s.event(w, List.of(new ScreenSession.Change("Text1", "object", "1101")), "Push_Button4", "clicked", null);
        dialogs(s);
        return s;
    }

    @Test
    void changingACarSendsWhatTheOldScreenSends() {
        Parity p = backend();
        ScreenSession s = found(p, "DBMAINT");
        String w = (String) window(s).get("id");
        s.event(w, List.of(new ScreenSession.Change("Drop-down_List2", "selectionIndex", index(s, "Drop-down_List2", "DY")),
                new ScreenSession.Change("Drop-down_List9", "selectionIndex", index(s, "Drop-down_List9", "PM")),
                new ScreenSession.Change("Text17", "object", "8")), "Push_Button1", "clicked", null);
        List<String> oldMessages = dialogs(s);
        Map<String, Map<String, Object>> old = p.lastSent("VHVM1A4");
        assertThat(old).as("old screen saved: " + oldMessages).isNotNull();

        p.clear();
        String message = api(p).update("1101", Map.of("homeYrd", "DY", "vehStat", "PM", "cntTimesWash", 8), "DBMAINT");
        assertThat(message).isEqualTo(oldMessages.get(0));
        assertThat(comparable(p.lastSent("VHVM1A4"))).isEqualTo(comparable(old));
        assertThat(p.lastSent("VHVM1A4").get("VHVM1A5_DATA")).containsEntry("termLastUpd", TERMINAL).containsEntry("termMaintUpd", TERMINAL)
                .containsEntry("termHomeYrdUpd", TERMINAL).containsEntry("cameraCd", "H");
    }

    @Test
    void nothingChangedIsRefusedLikeTheOldScreen() {
        Parity p = backend();
        Map<String, Object> noCamera = car();
        noCamera.put("cameraCd", "");                                   // (the engine's camera list is empty, see comparable)
        p.on("VHVM1A5", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM1A5_CRIT", noCamera, "VHVM1A5_DATA", noCamera));
        ScreenSession s = found(p, "DBMAINT");
        s.event((String) window(s).get("id"), List.of(), "Push_Button1", "clicked", null);
        List<String> oldMessages = dialogs(s);
        assertThat(p.lastSent("VHVM1A4")).isNull();
        assertThatThrownBy(() -> api(p).update("1101", Map.of(), "DBMAINT")).isInstanceOf(InvalidRequestException.class)
                .hasMessage(oldMessages.get(0));
    }

    @Test
    void ordinaryGroupsMayChangeOnlyTheFieldsTheOldScreenLeavesOpen() {
        Parity p = backend();
        ScreenSession s = found(p, "BRIGHTCO");
        VehicleMaintCarService.Car car = api(p).car("1101", "BRIGHTCO");
        for (String f : car.editable()) assertThat(car.fields()).containsKey(f);
        assertThat(car.editable()).contains("homeYrd", "vagenOrientation").doesNotContain("vehStat", "carType", "cntTimesWash");
        assertThat(widget(s, "Drop-down_List9").get("enabled")).isEqualTo(false);
        assertThat(widget(s, "Drop-down_List2").get("enabled")).isNotEqualTo(false);
        assertThatThrownBy(() -> api(p).update("1101", Map.of("vehStat", "PM"), "BRIGHTCO")).isInstanceOf(InvalidRequestException.class);
        assertThat(api(p).car("1101", "DBMAINT").editable()).contains("vehStat", "carType", "cntTimesWash");
    }

    @Test
    void choicesMatchTheOldScreens() {
        Parity p = backend();
        ScreenSession s = found(p, "DBMAINT");
        VehicleMaintCarService.Options o = api(p).options();
        assertThat(o.locations()).containsExactly("CY01", "HY02");          // (the engine leaves the old location list empty)
        assertThat(items(s, "Drop-down_List17")).isEqualTo(o.configStatuses());
        assertThat(items(s, "Drop-down_List18")).isEqualTo(o.configTypes());
        assertThat(items(s, "Drop-down_List14").stream().filter(x -> !x.isBlank()).toList()).isEqualTo(o.orientations().get("C2"));
        assertThat(items(s, "Drop-down_List7")).isEqualTo(o.schedulePms());
        assertThat(o.cameras()).extracting(VehicleMaintCarService.Camera::code).containsExactly("H", "N");
    }

    @Test
    void addingACarSendsWhatTheOldScreenSends() {
        Parity p = backend();
        ScreenSession s = menu(p, p.user, "VHVM02I");
        String w = (String) window(s).get("id");
        s.event(w, List.of(), "Push_Button2", "clicked", null);                     // Clear puts the defaults in
        dialogs(s);
        s.event(w, List.of(new ScreenSession.Change("Text1", "object", "1999"), new ScreenSession.Change("Text2", "object", "C2")), "Text2", "losingFocus", null);
        dialogs(s);
        s.event(w, List.of(new ScreenSession.Change("Text5", "object", "CY"),
                new ScreenSession.Change("Drop-down_List2", "selectionIndex", 1),
                new ScreenSession.Change("Drop-down_List1", "selectionIndex", 2),
                new ScreenSession.Change("Drop-down_List1", "selectionIndex", index(s, "Drop-down_List1", "<")),
                new ScreenSession.Change("Drop-down_List16", "selectionIndex", index(s, "Drop-down_List16", "HY")),
                new ScreenSession.Change("Text23", "object", "06-01-2026")), "Push_Button1", "clicked", null);
        List<String> oldMessages = dialogs(s);
        Map<String, Map<String, Object>> old = p.lastSent("VHVM1A3");
        assertThat(old).as("old screen added: " + oldMessages).isNotNull();

        p.clear();
        VehicleMaintCarService svc = api(p);
        Map<String, Object> req = new LinkedHashMap<>(svc.options().addDefaults());
        req.put("carNo", "1999"); req.put("carType", "C2"); req.put("homeYrd", "CY"); req.put("cameraCd", "H");
        req.put("vagenOrientation", "<"); req.put("batteryReplLoc", "HY"); req.put("dateBatteryRepl", "2026-06-01");
        assertThat(svc.add(req)).isEqualTo(oldMessages.get(0));
        assertThat(comparable(p.lastSent("VHVM1A3"))).isEqualTo(comparable(old));
        assertThat(p.lastSent("VHVM1A3").get("VHVM1A3_DATA")).containsEntry("termLastUpd", TERMINAL).containsEntry("cameraCd", "H");
    }

    @Test
    void addChecksMatchTheOldScreen() {
        Parity p = backend();
        ScreenSession s = menu(p, p.user, "VHVM02I");
        String w = (String) window(s).get("id");
        s.event(w, List.of(new ScreenSession.Change("Text2", "object", "Z9")), "Text2", "losingFocus", null);
        List<String> badType = dialogs(s);
        s.event(w, List.of(new ScreenSession.Change("Text2", "object", "")), "Push_Button1", "clicked", null);
        List<String> missing = dialogs(s);
        assertThat(p.lastSent("VHVM1A3")).isNull();
        VehicleMaintCarService svc = api(p);
        assertThatThrownBy(() -> svc.add(Map.of("carNo", "1999", "carType", "Z9"))).hasMessage(badType.get(0));
        assertThatThrownBy(() -> svc.add(Map.of("carNo", "1999"))).hasMessage(missing.get(0));
    }
}
