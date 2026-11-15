package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidents.IncidentScreens;
import com.bart.maris.middleware.domain.incidents.IncidentsRevenueService;
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

/** Revenue incidents of a car (VHINC06I) against the old screen run by the engine (menu VHMOTDI), same scripted backend. */
class IncidentsRevenueParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** car type and where the car is; the revenue date is today */
    static Parity backend(String carType, String where, int carUrc) {
        Parity p = IncidentsTrainParityTest.backend(1, "Y");
        String today = LocalDateTime.now().format(MDY);
        p.on("AFCXVLD", in -> Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", Map.of("dateError",
                String.valueOf(in.get("AFCXVLD_CRIT").get("formDate")).startsWith("2026-13") ? "Y" : "N", "otherSqlError", "N",
                "formDate", in.get("AFCXVLD_CRIT").get("formDate"))));
        p.on("VHXREVC", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVC_DATA", Map.of("revenueDate", today)));
        p.on("VHINC6A", in -> Map.of("XDCONTROL", Map.of("urc", carUrc, "urowcnt", carUrc == 0 ? 1 : 0), "VHINC6A_DATA", Map.of("whereIsCar", where, "carType", carType)));
        p.on("VHINCX", in -> {                                     // the program answers its record back with the new incident number
            Map<String, Object> d = new java.util.HashMap<>(in.get("VHINCX_DATA"));
            d.put("incdNo", "G0004321");
            Map<String, Object> c = new java.util.HashMap<>(in.get("VHINCX_CRIT"));
            c.put("errFound", "N");
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHINCX_CRIT", c, "VHINCX_DATA", d);
        });
        p.on("VHINC3C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static IncidentsRevenueService api(Parity p) {
        EglTexts t = new EglTexts(Parity.bundle());
        return new IncidentsRevenueService(p.legacy(), p.messages(), t, new IncidentScreens(Parity.bundle()), IncidentsTrainParityTest.api(p),
                Clock.systemDefaultZone(), IncidentsTrainParityTest.TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s) {
        return (String) window(s, "VHINC06I").get("id");
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s, "VHINC06I").get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static ScreenSession opened(Parity p) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMOTDI", p.user);
        s.event((String) window(s, "VHMOTDI").get("id"), List.of(), "Push_Button9", "clicked", null);
        assertThat(window(s, "VHINC06I")).as("VHINC06I opened").isNotNull();
        s.window(id(s)).recordPart("VH01A01_DATA").set("USER-GROUP data", p.user.groupId());   // as VH001I passes it
        s.window(id(s)).recordPart("VHINC06IW").set("USER-GRP data", p.user.groupId());         // the menus pass the signed-on group
        return s;
    }

    static ScreenSession.Change c(String widget, Object v) {
        return new ScreenSession.Change(widget, "object", v);
    }

    static String message(Supplier<Object> call) {
        try { call.get(); return null; } catch (InvalidRequestException e) { return e.getMessage(); }
    }

    /** create on the old screen: the Create button, then the hidden button its access check presses */
    static void create(ScreenSession s, String[] k) {
        s.event(id(s), List.of(c("txtCarno", k[0]), c("txtSympcd", k[1]), c("txtDateincd", k[2]), c("txtTimeincd", k[3]), c("txtSpeed", k[4]),
                c("txtDirection", k[5]), c("txtLocation", k[6]), c("mleNarrative", k[7])), "pbtCreate", "clicked", null);
        if (window(s, "VHINC06I") != null && value(s, "txtStatusmsg").isEmpty()) s.event(id(s), List.of(), "Push_Button5", "clicked", null);
    }

    @Test
    void createSendsTheSameIncident() {
        Parity p = backend("C2", "CON", 0);
        ScreenSession s = opened(p);
        String date = value(s, "txtDateincd");
        String[] k = {"1101", "0120", date, IncidentsTrainParityTest.nowHhmm(), "35", "N", "A10", "DOOR"};
        int mark = p.sent.size();
        create(s, k);
        var old = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
        String oldMessage = value(s, "txtStatusmsg");
        p.clear();
        var r = api(p).create(new IncidentsRevenueService.Create(k[0], k[1], k[2], k[3], k[4], k[5], k[6], k[7]), p.user.groupId());
        for (String program : List.of("VH01A02", "AFCXVLD", "VHXREVC", "VHINC1A", "VHINC6A", "VHINVC", "VHINCX", "VHINC3C")) {
            // distinct: after filing, the old screen clears itself and reads the revenue date again
            assertThat(IncidentsTrainParityTest.sent(p.sent, program).stream().distinct().toList()).as(program)
                    .isEqualTo(IncidentsTrainParityTest.sent(old, program).stream().distinct().toList());
        }
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @Test
    void checksMatchTheOldScreen() {
        String yesterday = LocalDateTime.now().minusDays(1).format(MDY), old = LocalDateTime.now().minusDays(9).format(MDY),
                future = LocalDateTime.now().plusDays(2).format(MDY);
        String[][] cases = {
                {"", "", "", "", "", "", "", ""},
                {"1101", "9999", "13-01-2026", "2599", "4A", "X", "A10", "N"},
                {"1101", "0120", old, "1000", "35", "N", "", "N"},
                {"1101", "0120", future, "1000", "ALL", "W", "A10", "N"},
                {"1101", "0120", yesterday, "0000", "35", "N", "A10", ""},
        };
        String[][] cars = {{"C2", "CON", "0"}, {"X1", "CON", "0"}, {"C2", "SHP", "0"}, {"C2", "CON", "100"}};
        for (String[] k : cases) {
            for (String[] car : cars) {
                Parity p = backend(car[0], car[1], Integer.parseInt(car[2]));
                ScreenSession s = opened(p);
                int mark = p.sent.size();
                create(s, k);
                var oldSent = new ArrayList<>(p.sent.subList(mark, p.sent.size()));
                String oldMessage = value(s, "txtStatusmsg");
                p.clear();
                String msg = message(() -> api(p).create(new IncidentsRevenueService.Create(k[0], k[1], k[2], k[3], k[4], k[5], k[6], k[7]), p.user.groupId()));
                assertThat(msg).as(String.join("|", k) + " car " + String.join(",", car)).isEqualTo(oldMessage);
                assertThat(IncidentsTrainParityTest.sent(oldSent, "VHINCX")).isEmpty();
                for (String program : List.of("AFCXVLD", "VHXREVC", "VHINC1A", "VHINC6A")) {
                    assertThat(IncidentsTrainParityTest.sent(p.sent, program)).as(program + " " + String.join("|", k)).isEqualTo(IncidentsTrainParityTest.sent(oldSent, program));
                }
            }
        }
    }
}
