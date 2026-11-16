package com.bart.maris.middleware.api.wayside;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.wayside.WaysideStockService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The stock number search against the old WS17I run by the engine: same backend, same request, same rows. */
class WaysideParityTest {
    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(strings = {"BRAKE", "", "AB C", "1234567890"})
    void searchMatchesTheOldScreen(String description) {
        Parity p = new Parity();
        p.on("WS17A05", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS17A05_DATA", Map.of("dataRow", List.of(
                Map.of("deptCd", "E", "stockNo", "123456789", "stockDesc", "BRAKE SHOE"),
                Map.of("deptCd", "M", "stockNo", "223456789", "stockDesc", "BRAKE PAD")))));
        ScreenSession old = p.oldScreens();
        old.openRoot("WS17I", p.user);
        String w = (String) ((List<Map<String, Object>>) old.state().get("windows")).get(0).get("id");
        old.event(w, List.of(new ScreenSession.Change("Text1", "object", description)), "Search", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("WS17A05").get("WS17A05_CRIT");
        List<List<String>> oldRows = (List<List<String>>) ((Map<String, Object>) ((Map<String, Object>)
                ((List<Map<String, Object>>) old.state().get("windows")).get(0).get("widgets")).get("Hpt_Container_Details_View1")).get("rows");

        p.clear();
        var api = new WaysideStockService(p.legacy(), p.messages()).search(description, "");
        assertThat(p.lastSent("WS17A05").get("WS17A05_CRIT")).isEqualTo(oldCrit);
        assertThat(api.stockNumbers().stream().map(s -> List.of(s.dept(), s.stockNo(), s.description())).toList()).isEqualTo(oldRows);
    }
}
