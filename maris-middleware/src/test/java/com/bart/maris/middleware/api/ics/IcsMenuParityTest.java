package com.bart.maris.middleware.api.ics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * VHIC32I is the ICS reports menu: no program, one button that opens the ICS report criteria VHIC30I. The new UI
 * routes it straight to the ICS report page; this pins down that the old button does nothing else.
 */
class IcsMenuParityTest {
    @SuppressWarnings("unchecked")
    @Test
    void theMenusOnlyButtonOpensTheIcsReportCriteria() {
        Parity p = new Parity();
        ScreenSession s = p.oldScreens();
        s.openRoot("VHIC32I", p.user);
        List<Map<String, Object>> ws = (List<Map<String, Object>>) s.state().get("windows");
        String id = (String) ws.get(0).get("id");
        s.event(id, List.of(), "Push_Button17", "clicked", null);
        List<String> open = ((List<Map<String, Object>>) s.state().get("windows")).stream().map(w -> (String) w.get("screen")).toList();
        assertThat(open).contains("VHIC30I");
    }
}
