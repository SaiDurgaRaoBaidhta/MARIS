package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidents.IncidentScreens;
import com.bart.maris.middleware.domain.incidents.IncidentsCarService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Event / shop / miscellaneous incidents of a car (VHINC03I) against the old screen run by the engine: opened with the
 * incident type and terminal id its menus pass, then Clear (which runs its set-up for that type).
 */
class IncidentsCarParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    static Parity backend(String carType, int carUrc) {
        Parity p = IncidentsRevenueParityTest.backend(carType, "CON", carUrc);
        p.on("VHINCXA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHINCXA_DATA", Map.of("discvDurCount", 3, "dataRow", List.of(
                Map.of("discvDur", "1", "discvDurDesc", "REVENUE"), Map.of("discvDur", "2", "discvDurDesc", "YARD"), Map.of("discvDur", "4", "discvDurDesc", "SHOP")))));
        p.on("VHINC3B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINC3B_CRIT", Map.of("sympCd", "0120", "sympCdNarr", "DOORS DO NOT CLOSE")));
        p.on("VHINVB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINCZ", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHINCZ_DATA", Map.of("incdNo", "E0007777")));
        p.on("VHINC3A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static IncidentsCarService api(Parity p) {
        return new IncidentsCarService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), new IncidentScreens(Parity.bundle()), Clock.systemDefaultZone(),
                IncidentsTrainParityTest.TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if ("VHINC03I".equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s) {
        return (String) window(s).get("id");
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s).get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static ScreenSession opened(Parity p, String type) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHINC03I", p.user);
        s.window(id(s)).recordPart("VHINCZ_CRIT").set("TYPE-IND data", type);                     // what VH001I / VHMVSI pass
        s.window(id(s)).recordPart("VHINC03IW").set("TERM-LAST-UPD data", IncidentsTrainParityTest.TERMINAL);
        s.window(id(s)).recordPart("VHINVB_CRIT").set("USER-GRP data", "");   // the engine seeds the root's USER-GRP fields; the old menus leave this one blank
        s.event(id(s), List.of(), "pbtClear", "clicked", null);
        return s;
    }

    static ScreenSession.Change c(String widget, Object v) {
        return new ScreenSession.Change(widget, "object", v);
    }

    static String message(Supplier<Object> call) {
        try { call.get(); return null; } catch (InvalidRequestException e) { return e.getMessage(); }
    }

    static void create(ScreenSession s, String[] k) {
        List<ScreenSession.Change> ch = new ArrayList<>(List.of(c("txtCarno", k[0]), c("txtSympcd", k[1]), c("txtDateincd", k[3]), c("txtTimeincd", k[4]),
                c("txtLocation", k[5]), c("mleNarrative", k[6])));
        if (!k[2].isEmpty()) ch.add(c("txtDiscvdur", k[2]));
        s.event(id(s), ch, "pbtCreate", "clicked", null);
    }

    @Test
    void createSendsTheSameIncidentForEachType() {
        for (String type : List.of("EVT", "SHN", "MSC")) {
            Parity p = backend("C2", 0);
            ScreenSession s = opened(p, type);
            String date = value(s, "txtDateincd");
            String[] k = {"1101", "0120", type.equals("SHN") ? "" : "2", date, IncidentsTrainParityTest.nowHhmm(), "A10", "DOOR"};
            int mark = p.sent.size();
            create(s, k);
            var old = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
            String oldMessage = value(s, "txtStatusmsg");
            p.clear();
            var svc = api(p);
            assertThat(svc.options(type).date()).isEqualTo(date);
            p.clear();
            var r = svc.create(new IncidentsCarService.Create(type, k[0], k[1], k[2], k[3], k[4], k[5], k[6]), p.user.groupId());
            for (String program : List.of("AFCXVLD", "VHINC1A", "VHINC6A", "VHINVB", "VHINCZ", "VHINC3A")) {
                assertThat(IncidentsTrainParityTest.sent(p.sent, program)).as(type + " " + program).isEqualTo(IncidentsTrainParityTest.sent(old, program));
            }
            assertThat(r.message()).as(type).isEqualTo(oldMessage);
        }
    }

    @Test
    void checksMatchTheOldScreen() {
        String today = LocalDateTime.now().format(MDY), old = LocalDateTime.now().minusYears(4).format(MDY), future = LocalDateTime.now().plusDays(2).format(MDY);
        String[][] cases = {
                {"", "", "", "", "", "", ""},
                {"1101", "9999", "7", "13-01-2026", "2599", "A10", "N"},
                {"1101", "0120", "2", old, "10:00", "", "N"},
                {"1101", "0120", "2", future, "1000", "A10", ""},
                {"1101", "0120", "1", today, "AB", "A10", "N"},
        };
        for (String type : List.of("EVT", "MSC")) {
            for (String[] k : cases) {
                for (Object[] car : new Object[][] {{"C2", 0}, {"X1", 0}, {"C2", 100}}) {
                    Parity p = backend((String) car[0], (Integer) car[1]);
                    ScreenSession s = opened(p, type);
                    int mark = p.sent.size();
                    create(s, k);
                    var oldSent = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
                    String oldMessage = value(s, "txtStatusmsg");
                    p.clear();
                    String msg = message(() -> api(p).create(new IncidentsCarService.Create(type, k[0], k[1], k[2], k[3], k[4], k[5], k[6]), p.user.groupId()));
                    String what = type + " " + String.join("|", k) + " car " + car[0] + car[1];
                    assertThat(msg).as(what).isEqualTo(oldMessage);
                    assertThat(IncidentsTrainParityTest.sent(oldSent, "VHINCZ")).isEmpty();
                    for (String program : List.of("AFCXVLD", "VHINC1A", "VHINC6A")) {
                        assertThat(IncidentsTrainParityTest.sent(p.sent, program)).as(program + " " + what).isEqualTo(IncidentsTrainParityTest.sent(oldSent, program));
                    }
                }
            }
        }
    }

    @Test
    void symptomNarrativeIsTheOneTheOldScreenFillsIn() {
        Parity p = backend("C2", 0);
        ScreenSession s = opened(p, "EVT");
        s.event(id(s), List.of(c("txtSympcd", "0120")), "txtSympcd", "losingFocus", null);
        assertThat(api(p).symptomNarrative("0120")).isEqualTo(value(s, "mleNarrative"));
    }
}
