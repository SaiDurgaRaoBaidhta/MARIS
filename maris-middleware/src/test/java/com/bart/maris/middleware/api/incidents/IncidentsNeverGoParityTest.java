package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.incidents.IncidentsNeverGoService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle tracking change incident "never go" (VHCI03I) against the old screen run by the engine. The engine opens the
 * screen without a parent, so the terminal it records is blank: the API is built with a blank terminal to match.
 */
class IncidentsNeverGoParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final String TODAY = LocalDate.now().format(MDY), YESTERDAY = LocalDate.now().minusDays(1).format(MDY);

    @SuppressWarnings("unchecked")
    static Parity backend(String whereIsCar, String rank) {
        Parity p = new Parity();
        p.on("VHCI02G", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI02G_CRIT", Map.of("currentDate", TODAY, "currentTime", "23:59:00")));
        p.on("VHCI03E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHCI03E_DATA", Map.of("dataRow", List.of(Map.of("vehStat", "ACIDNT"),
                Map.of("vehStat", "AWPART"), Map.of("vehStat", "NVGO")))));
        p.on("VHCI02B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1)));
        p.on("VHCI01B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCI01B_DATA", Map.of("whereIsCar", whereIsCar, "dispVehStat", "GO     GOOD TO GO",
                "carNo", "1101", "carType", "C2", "worstCaseRank", Integer.parseInt(rank), "trainIncdInd", "N")));
        p.on("AFCXVLD", in -> Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N")));
        p.on("VHINVB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINVA", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCIV", in -> {
            Map<String, Object> t = new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) in.get("VHCI01IW-TEMP-MI"));
            t.put("errFound", "N");
            t.put("statusUpd", "Y");
            t.put("newVehStat", "NVGO");
            t.put("newVehStatDesc", "NEVER GO");
            t.put("saveIncdNo", "I1234567");
            t.put("saveIncdType", "AWP");
            t.put("intoShopUpd", "Y");
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHCI01IW-TEMP-MI", t);
        });
        p.on("VHINXA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHINXA_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "conNo", "201", "yrdSubLoc", "HY-T12"), Map.of("carNo", "1102", "conNo", "201")))));
        p.on("VHINXB", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHCI01E", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHINXD", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static IncidentsNeverGoService api(Parity p) {
        return new IncidentsNeverGoService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock, "");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String name) {
        Map<String, Object> w = ((List<Map<String, Object>>) s.state().get("windows")).get(0);
        return (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
    }

    static String value(ScreenSession s, String name) {
        Object v = widget(s, name).get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static String id(ScreenSession s) {
        return (String) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("id");
    }

    static ScreenSession changed(Parity p, String car, int status, String date, String time, String narr) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCI03I", p.user);
        List<ScreenSession.Change> ch = List.of(new ScreenSession.Change("txtCarNo", "object", car), new ScreenSession.Change("mleNarrative", "object", narr),
                new ScreenSession.Change("txtStatChgDate", "object", date), new ScreenSession.Change("txtStatChgTime", "object", time));
        if (status > 0) s.event(id(s), ch, "cmbCarStatus", "select", status);
        else s.event(id(s), ch, null, null, null);
        p.clear();
        s.event(id(s), List.of(), "pbtUpdate", "clicked", null);
        return s;
    }

    /** the records sent, the narrative's clock stamp left out (each side stamps its own time) */
    @SuppressWarnings("unchecked")
    static List<Object> sent(Parity p, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : p.sent) {
            if (!e.getKey().equals(program)) continue;
            Map<String, Object> r = new LinkedHashMap<>(e.getValue().get(record));
            if (r.get("storeNarr") instanceof Map<?, ?> n) {
                Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) n);
                m.remove("storeNarrDate");
                m.remove("storeNarrTime");
                r.put("storeNarr", m);
            }
            out.add(r);
        }
        return out;
    }

    @Test
    void optionsMatchTheOpenedScreen() {
        Parity p = backend("YRD", "10");
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCI03I", p.user);
        var o = api(p).options(p.user.groupId());
        assertThat(o.statuses()).isEqualTo(widget(s, "cmbCarStatus").get("items"));
        assertThat(o.date()).isEqualTo(value(s, "txtStatChgDate"));
        assertThat(o.time()).isEqualTo(value(s, "txtStatChgTime"));
        assertThat(o.canChangeLocation()).isTrue();
    }

    @Test
    void changeSendsTheSameRecords() {
        Parity p = backend("YRD", "10");
        ScreenSession s = changed(p, "1101", 3, YESTERDAY, "0930", "BROKEN DOOR");
        String[][] records = {{"VHCI01B", "VHCI01B_DATA"}, {"AFCXVLD", "AFCXVLD_CRIT"}, {"VHINVB", "VHINVB_CRIT"}, {"VHINVA", "VHINVA_CRIT"},
                {"VHCIV", "VHCI01IW-TEMP-MI"}, {"VHINXA", "VHINXA_CRIT"}, {"VHINXB", "VHINXB_CRIT"}, {"VHINXB", "VHINXB_DATA"}, {"VHCI01E", "VHCI01E_CRIT"},
                {"VHINXD", "VHINXD_CRIT"}, {"VHINXD", "VHINXD_DATA"}};
        Map<String, List<Object>> old = new LinkedHashMap<>();
        for (String[] r : records) old.put(r[0] + "/" + r[1], sent(p, r[0], r[1]));
        List<String> oldOrder = p.sent.stream().map(Map.Entry::getKey).toList();
        String oldMsg = value(s, "txtMsg");
        p.clear();
        var r = api(p).change(new IncidentsNeverGoService.Request("1101", "NVGO", YESTERDAY, "0930", "BROKEN DOOR", null), p.user.groupId());
        assertThat(p.sent.stream().map(Map.Entry::getKey).filter(oldOrder::contains).toList()).isEqualTo(oldOrder);
        for (String[] rec : records) assertThat(sent(p, rec[0], rec[1])).as(rec[0] + " " + rec[1]).isEqualTo(old.get(rec[0] + "/" + rec[1]));
        assertThat(r.message()).isEqualTo(oldMsg);
        assertThat(r.incident()).isEqualTo("I1234567");
    }

    @Test
    void checksGiveTheOldMessages() {
        Object[][] cases = {{"", 3, YESTERDAY, "0930", "N", "YRD", "10"}, {"1101", 0, YESTERDAY, "0930", "N", "YRD", "10"}, {"1101", 3, YESTERDAY, "0930", "N", "CON", "10"},
                {"1101", 1, YESTERDAY, "0930", "N", "YRD", "90"}, {"1101", 3, YESTERDAY, "0930", "", "YRD", "10"}, {"1101", 3, YESTERDAY, "2561", "N", "YRD", "10"},
                {"1101", 3, LocalDate.now().plusDays(3).format(MDY), "0930", "N", "YRD", "10"},
                {"1101", 3, LocalDate.now().minusDays(400).format(MDY), "0930", "N", "YRD", "10"}};
        for (Object[] c : cases) {
            Parity p = backend((String) c[5], (String) c[6]);
            ScreenSession s = changed(p, (String) c[0], (Integer) c[1], (String) c[2], (String) c[3], (String) c[4]);
            String oldMsg = value(s, "txtMsg");
            assertThat(oldMsg).as("old message for " + List.of(c)).isNotBlank();
            List<String> statuses = List.of("ACIDNT", "AWPART", "NVGO");
            String status = (Integer) c[1] == 0 ? "" : statuses.get((Integer) c[1] - 1);
            String msg;
            try {
                api(p).change(new IncidentsNeverGoService.Request((String) c[0], status, (String) c[2], (String) c[3], (String) c[4], null), p.user.groupId());
                msg = null;
            } catch (InvalidRequestException e) {
                msg = e.getMessage();
            }
            assertThat(msg).as(List.of(c).toString()).isEqualTo(oldMsg);
        }
    }
}
