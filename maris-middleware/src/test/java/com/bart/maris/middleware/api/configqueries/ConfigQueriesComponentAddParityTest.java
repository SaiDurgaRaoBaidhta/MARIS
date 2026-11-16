package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponentAddService;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Add a component serial (VHCF21I): the clean API against the old screen run by the engine, same scripted backend.
 * Compares every record sent (VHCF21A, VHCF21B, VHCF21C, VHCF21E and the insert VHCF21D) and the message shown.
 */
class ConfigQueriesComponentAddParityTest {
    static final String TERMINAL = "TERM01";
    private Parity p;

    private void backend(boolean exists, String trackingCd, int insertUrc) {
        p = new Parity();
        p.on("VHCF21A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF21A_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntEquipType", "AXLE"), Map.of("cmpntEquipType", "BEARING")))));
        p.on("VHCF21B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF21B_DATA", Map.of("dataRow", List.of(
                Map.of("stockNo", "123456789", "stockNoL", "12-34-56789", "cmpntEquipType", "AXLE"),
                Map.of("stockNo", "987654321", "stockNoL", "98-76-54321", "cmpntEquipType", "AXLE")))));
        p.on("VHCF21C", in -> Map.of("XDCONTROL", Map.of("urc", exists ? 0 : 100, "urowcnt", exists ? 1 : 0)));
        p.on("VHCF21E", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCF21E_DATA", Map.of("cmpntTrackingCd", trackingCd)));
        p.on("VHCF21D", in -> Map.of("XDCONTROL", Map.of("urc", insertUrc, "urowcnt", 1, "umsgcode", insertUrc == 0 ? "" : "VHM0001E")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static String shown(ScreenSession s, String widget) {
        Object v = ((Map<String, Map<String, Object>>) window(s).get("widgets")).get(widget).get("value");
        return v == null ? "" : v.toString().strip();
    }

    /** the old screen: opened by its menu with the terminal id, then the user's steps */
    private ScreenSession old(String type, String stock, String serial, String date) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF21I", p.user);
        String id = (String) window(s).get("id");
        s.window(id).recordPart("VHCF21I-PASS-VALUE").set("TERM-ID data", TERMINAL);
        choose(s, id, "Drop-down_List1", type);
        choose(s, id, "Drop-down_List2", stock);
        s.event(id, List.of(new ScreenSession.Change("Text3", "object", serial), new ScreenSession.Change("Text2", "object", date)), "Push_Button1", "clicked", null);
        return s;
    }

    /**
     * the engine leaves lists fed from DATA_ROW_of_X empty, so the item is put in the list (what that binding would show)
     * and selected; the list's selectedItem connections then run as on the old screen (VHCF21I-GET-STOCK for the type)
     */
    private static void choose(ScreenSession s, String id, String list, String item) {
        if (item.isEmpty()) return;
        s.window(id).widget(list).set("items", List.of(item));
        s.event(id, List.of(), list, "select", 1);
    }

    private ConfigQueriesComponentAddService api() {
        return new ConfigQueriesComponentAddService(new ConfigQueriesRuleScreens(Parity.bundle(), p.legacy(), p.messages(), p.clock, TERMINAL));
    }

    private Map<String, List<Map<String, Map<String, Object>>>> byProgram() {
        Map<String, List<Map<String, Map<String, Object>>>> m = new HashMap<>();
        for (var e : p.sent) m.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>()).add(stripControl(e.getValue()));
        return m;
    }

    private static Map<String, Map<String, Object>> stripControl(Map<String, Map<String, Object>> in) {
        Map<String, Map<String, Object>> out = new HashMap<>(in);
        out.remove("XDCONTROL");                                              // the control block carries over between calls
        return out;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "AXLE|12-34-56789|SN100|11-01-2025|N|T|0",          // added, tracking T -> TIM
            "AXLE|12-34-56789|SN100|11-01-2025|N|W|0",          // tracking W -> WAR
            "BEARING|98-76-54321|SN7|01-15-2024|N|X|0",         // bearing: the year check is always passed
            "AXLE|12-34-56789|SN100|11-01-2025|Y|T|0",          // serial already there (VHCF21C finds it)
            "AXLE|12-34-56789|SN100|11-01-2025|N|T|8",          // the insert fails
            "AXLE|12-34-56789|SN100|12-31-2099|N|T|0",          // future date
            "AXLE|12-34-56789|SN100|05-05-1985|N|T|0",          // before 1990
            "AXLE|12-34-56789|SN100|13-40-2020|N|T|0",          // bad month / day
            "AXLE|12-34-56789|''|11-01-2025|N|T|0",             // no serial
            "AXLE|''|SN100|11-01-2025|N|T|0",                   // no stock
            "''|''|''|11-01-2025|N|T|0"})                       // nothing chosen: the last check's message
    void updateMatchesTheOldScreen(String type, String stock, String serial, String date, String exists, String tracking, int insertUrc) {
        backend(exists.equals("Y"), tracking, insertUrc);
        ScreenSession s = old(type, stock, serial, date);
        String oldMessage = shown(s, "Text1");
        var oldSent = byProgram();
        assertThat(oldSent).as("the old screen opened (VHCF21A)").containsKey("VHCF21A");

        p.clear();
        ConfigQueriesComponentAddService.Result r = api().add("BRIGHTCO", type, stock, serial, date);
        assertThat(byProgram()).isEqualTo(oldSent);
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource({"AXLE"})
    void theListsAreTheOldPrograms(String type) {
        backend(false, "T", 0);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF21I", p.user);
        String id = (String) window(s).get("id");
        choose(s, id, "Drop-down_List1", type);
        String oldDate = shown(s, "Text2");
        var oldSent = byProgram();

        p.clear();
        var o = api().options("BRIGHTCO");
        var st = api().stocks("BRIGHTCO", type);
        assertThat(byProgram().get("VHCF21A").get(0)).isEqualTo(oldSent.get("VHCF21A").get(0));
        assertThat(byProgram().get("VHCF21B")).isEqualTo(oldSent.get("VHCF21B"));
        assertThat(o.componentTypes()).containsExactly("AXLE", "BEARING");
        assertThat(o.actions()).containsExactly("ADD");
        assertThat(o.date()).isEqualTo(oldDate);
        assertThat(st.stocks()).containsExactly("12-34-56789", "98-76-54321");
    }
}
