package com.bart.maris.middleware.api.configqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponents;
import com.bart.maris.middleware.domain.configqueries.ConfigQueriesComponentsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Config component inquiry: the clean API against the old VHCF16I run by the engine (its lists, and a search). */
class ConfigQueriesComponentsParityTest {
    private Parity p;

    private void backend(String limitFlag) {
        p = new Parity();
        p.on("VHCF16B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF16B_DATA", Map.of("dataRow", List.of(Map.of("cmpntEquipType", "TRUCK"), Map.of("cmpntEquipType", "AXLE")))));
        p.on("VHCF16C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF16C_DATA", Map.of("dataRow", List.of(
                Map.of("stockNo", "123456789", "stockDisplay", Map.of("stockNoL", "12-34-56789", "shortStockDesc", "TRUCK ASSY")),
                Map.of("stockNo", "987654321", "stockDisplay", Map.of("stockNoL", "98-76-54321", "shortStockDesc", "AXLE ASSY"))))));
        p.on("VHCF16D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCF16D_DATA", Map.of("dataRow", List.of(Map.of("serialStat", "ACT"), Map.of("serialStat", "SCR")))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(new HashMap<>(Map.of("carNo", "100" + i, "homeYrd", "CY", "cmpntEquipType", "TRUCK", "cmpntSerialNo", "SN" + i, "serialStat", "ACT",
                    "stockNo", "12-34-56789", "posnCd", "A" + i, "dateBuyRebuilt", "01-02-2020", "cmpntCumHrs", 100 * i)));
        }
        p.on("VHCF16A", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHCF16A_CRIT"));
            crit.put("dataLimitFlag", limitFlag);
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", rows.size()), "VHCF16A_CRIT", crit, "VHCF16A_DATA", Map.of("dataRow", rows));
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> widgets(ScreenSession s) {
        return (Map<String, Map<String, Object>>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets");
    }

    @SuppressWarnings("unchecked")
    private static List<String> items(Map<String, Map<String, Object>> w, String list) {
        return ((List<String>) w.get(list).get("items")).stream().map(String::strip).filter(x -> !x.isEmpty()).toList();
    }

    private static int index(Map<String, Map<String, Object>> w, String list, String startsWith) {
        @SuppressWarnings("unchecked") List<String> items = (List<String>) w.get(list).get("items");
        for (int i = 0; i < items.size(); i++) if (items.get(i).strip().startsWith(startsWith)) return i + 1;
        throw new AssertionError(startsWith + " not in " + list + ": " + items);
    }

    @Test
    void theListsAreTheOldOnes() {
        backend("N");
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCF16I", p.user);
        Map<String, Map<String, Object>> w = widgets(old);
        List<String> oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();

        Map<String, Map<String, Map<String, Object>>> oldSent = new HashMap<>();
        for (String prog : List.of("VHCF16B", "VHCF16C", "VHCF16D")) oldSent.put(prog, p.lastSent(prog));

        p.clear();
        ConfigQueriesComponents.Options o = new ConfigQueriesComponentsService(p.legacy(), p.messages(), Parity.bundle()).options();
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        for (String prog : List.of("VHCF16B", "VHCF16C", "VHCF16D")) {
            assertThat(p.lastSent(prog).get(prog + "_CRIT")).as(prog).isEqualTo(oldSent.get(prog).get(prog + "_CRIT"));
        }
        // The engine does not fill lists bound as DATA_ROW_of_X "FIELD data" -> items (the equipment type, stock and status
        // lists stay empty there); the API's lists are the programs' rows (see ConfigQueriesComponentsApiTest).
        assertThat(o.equipmentTypes()).containsExactly("TRUCK", "AXLE");
        assertThat(items(w, "Drop-down_List1")).isEqualTo(o.homeYards());
    }

    private void same(String stock, String yard, String status, String serial, int sort, String limitFlag) {
        backend(limitFlag);
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCF16I", p.user);
        @SuppressWarnings("unchecked") String id = ((List<Map<String, Object>>) old.state().get("windows")).get(0).get("id").toString();
        Map<String, Map<String, Object>> w = widgets(old);
        // the stock and status lists' selectedItem, as their bindings write it (the engine leaves those lists empty)
        if (!stock.isEmpty()) old.window(id).recordPart("VHCF16IW").set("STOCK-DISPLAY data", stock + " TRUCK ASSY");
        if (!status.isEmpty()) old.window(id).recordPart("VHCF16IW").set("SERIAL-STAT data", status);
        if (!yard.isEmpty()) old.event(id, List.of(), "Drop-down_List1", "select", index(w, "Drop-down_List1", yard));
        old.event(id, List.of(), "Radio_Button_Set1", "select", sort);
        p.clear();
        old.event(id, List.of(new ScreenSession.Change("Text1", "object", serial)), "Push_Button1", "clicked", null);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHCF16A");
        assertThat(oldSent).as("the old screen searched; it says " + widgets(old).get("Text2")).isNotNull();
        Map<String, Map<String, Object>> after = widgets(old);

        p.clear();
        ConfigQueriesComponents.Result r = new ConfigQueriesComponentsService(p.legacy(), p.messages(), Parity.bundle())
                .search(stock, serial, status, yard, "", String.valueOf(sort));
        assertThat(p.lastSent("VHCF16A").get("VHCF16A_CRIT")).isEqualTo(oldSent.get("VHCF16A_CRIT"));
        @SuppressWarnings("unchecked") List<List<Object>> rows = (List<List<Object>>) after.get("VAGen_Container_Details_DATA-ROW").get("rows");
        assertThat(rows.stream().map(x -> x.get(0).toString().strip()).toList()).isEqualTo(r.rows().stream().map(ConfigQueriesComponents.Component::car).toList());
        String oldMessage = String.valueOf(after.get("Text2").get("value")).strip();
        assertThat(r.message() == null ? "" : r.message()).isEqualTo(oldMessage);
    }

    @Test
    void aSearchSendsTheSameCriteriaAndShowsTheSameRows() {
        same("12-34-56789", "CY", "ACT", "SN1", 2, "N");
    }

    @Test
    void aSearchWithNothingChosenAndTheDataLimit() {
        same("", "", "", "", 1, "Y");
    }
}
