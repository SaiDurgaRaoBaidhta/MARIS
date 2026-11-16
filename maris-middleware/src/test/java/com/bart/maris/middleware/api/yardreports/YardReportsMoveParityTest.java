package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardreports.YardReportsMoveService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Moving a pseudo train: the clean API against the old VHIC03I run by the engine, both against the same scripted backend.
 * VHIC03I is opened by VHYT11I with the cars' yard and positions; here they are set on its pass record, as VHYT11I does.
 * The engine does not fill VHIC03I's yard list (Drop-down_List1 items from DATA_ROW_of_VHYT09A_DATA stay empty), so the
 * chosen yard is set on TO-YRD-TRKG-YRD, which the list's selectedItem is bound to, and the yards are compared by request.
 */
class YardReportsMoveParityTest {
    private Parity p;
    private YardReportsMoveService api;

    @BeforeEach
    void backend() {
        p = new Parity();
        p.on("VHYT09A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3),
                "VHYT09A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "RY"), Map.of("yrd", "HY")))));
        p.on("VHIC03A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        api = new YardReportsMoveService(p.legacy(), p.messages(), Parity.bundle(), "MARIS-UI");
    }

    @SuppressWarnings("unchecked")
    private String windowId(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> w.get("screen").equals("VHIC03I"))
                .map(w -> (String) w.get("id")).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> widget(ScreenSession s, String name) {
        return (Map<String, Object>) ((Map<String, Object>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets")).get(name);
    }

    /** opens VHIC03I with the cars VHYT11I passes, chooses a yard ("" = none) and presses Move */
    private ScreenSession oldMove(String fromYard, String toYard) {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIC03I", p.user);
        String w = windowId(old);
        ScreenInstance s = old.window(w);
        s.recordPart("VHIC03IW-PASSVALUE").set("FROM-YRD-TRKG-YRD data", fromYard);
        s.recordPart("VHIC03IW-PASSVALUE").set("FROM-POSN-NO data", 3);
        s.recordPart("VHIC03IW-PASSVALUE").set("TO-POSN-NO data", 5);
        s.recordPart("VHIC03IW-PASSVALUE").set("TO-YRD-TRKG-YRD data", toYard);
        old.event(w, List.of(), "Push_Button1", "clicked", null);
        return old;
    }

    @Test
    void theYardsAreAskedForAsBefore() {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHIC03I", p.user);
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHYT09A");
        assertThat(oldSent).as("the old screen asked for the yards").isNotNull();
        p.clear();
        assertThat(api.yards()).containsExactly("CY", "RY", "HY");
        assertThat(p.lastSent("VHYT09A").get("VHYT09A_CRIT")).isEqualTo(oldSent.get("VHYT09A_CRIT"));
    }

    @Test
    void movingSendsTheSameRecordInTheSameOrder() {
        oldMove("CY", "RY");
        List<String> oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();
        Map<String, Map<String, Object>> oldSent = p.lastSent("VHIC03A");
        assertThat(oldSent).as("the old screen moved the cars").isNotNull();

        p.clear();
        api.yards();                                                                   // the page loads the yards, as VHIC03IS-INIT
        api.move("CY", 3, 5, "RY", p.user.groupId());
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        assertThat(p.lastSent("VHIC03A").get("VHIC03A_CRIT")).isEqualTo(oldSent.get("VHIC03A_CRIT"));
    }

    @Test
    void theSameYardIsRefusedAsBeforeAndNothingIsWritten() {
        ScreenSession old = oldMove("CY", "CY");
        assertThat(p.lastSent("VHIC03A")).isNull();
        String oldMessage = String.valueOf(widget(old, "Text1").get("value")).strip();

        p.clear();
        // the old MSG field holds 40 characters, so the old screen cut this message short; the API gives the whole text
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(InvalidRequestException.class, () -> api.move("CY", 3, 5, "CY", "BRIGHTCO")))
                .hasMessageStartingWith(oldMessage);
        assertThat(p.lastSent("VHIC03A")).isNull();
    }

    @Test
    void noYardIsRefusedAsBeforeAndNothingIsWritten() {
        ScreenSession old = oldMove("CY", "");
        assertThat(p.lastSent("VHIC03A")).isNull();
        String oldMessage = String.valueOf(widget(old, "Text1").get("value")).strip();

        p.clear();
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(InvalidRequestException.class, () -> api.move("CY", 3, 5, "", "BRIGHTCO")))
                .hasMessage(oldMessage);
    }
}
