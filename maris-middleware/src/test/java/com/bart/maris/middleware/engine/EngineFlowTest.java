package com.bart.maris.middleware.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.security.MarisUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** The old ICS report screens run by the engine: criteria screen -> Display -> report window with the backend's rows. */
class EngineFlowTest {
    private final LegacyCatalog catalog = LegacyCatalog.load(new DefaultResourceLoader(), "classpath:legacy-catalog/");
    private final AtomicReference<Map<String, Object>> control = new AtomicReference<>(Map.of("urc", 0, "urowcnt", 2));
    private final List<String> called = new ArrayList<>();
    private boolean failAll;
    private final MarisUser user = new MarisUser("BRIGHTCO", "TEST", "VH001I", "ALL", "HY", "", "ADMIN", Instant.now());

    private ScreenEngine engine() {
        LegacyServiceClient client = mock(LegacyServiceClient.class);
        when(client.call(any(), any())).thenAnswer(inv -> {
            ServiceDefinition def = inv.getArgument(0);
            List<Object> args = new ArrayList<>(inv.getArgument(1));
            called.add(def.function());
            if (failAll) {
                args.set(0, RecordCodec.encode(catalog.record("MRSCommonCode.XDCONTROL"), control.get()));
            } else if (def.function().equals("VHIC31A")) {
                args.set(0, RecordCodec.encode(catalog.record("MRSCommonCode.XDCONTROL"), control.get()));
                args.set(2, RecordCodec.encode(catalog.record("MRSVehicleIcsReports.VHIC31A_DATA"), Map.of("dataRow", List.of(
                        Map.of("conCurrLoc", "C55", "conNo", "532-01", "dashNo", 1, "msgTranCd", "1F", "tsDate", "11-10-2026"),
                        Map.of("conCurrLoc", "M97", "conNo", "446-00", "msgTranCd", "1A", "tsDate", "11-10-2026")))));
            }
            return new LegacyServiceClient.Reply(args, "00000000");
        });
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneId.of("America/Los_Angeles"));
        return new ScreenEngine(ScreenBundle.load("classpath:egl-screens/"), catalog, client, clock, Set.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(Map<String, Object> state, String screen) {
        return ((List<Map<String, Object>>) state.get("windows")).stream().filter(w -> w.get("screen").equals(screen)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> widget(Map<String, Object> window, String name) {
        return (Map<String, Object>) ((Map<String, Object>) window.get("widgets")).get(name);
    }

    @Test
    void criteriaThenDisplayOpensTheReportWithTheBackendRows() {
        ScreenSession s = engine().newSession("BRIGHTCO");
        s.openRoot("VHIC30I", user);
        Map<String, Object> st = s.state();
        assertThat(called).contains("WS70A01", "VHIC30A", "VHIC30B", "VHIC30C");   // the screen's own open logic

        String w = (String) window(st, "VHIC30I").get("id");
        s.event(w, List.of(new ScreenSession.Change("Text3", "object", "11-10-2026")), "Push_Button3", "clicked", null);
        st = s.state();
        Map<String, Object> report = window(st, "VHIC31I");
        assertThat(widget(report, "Text1").get("value")).isEqualTo("11-10-2026");
        assertThat(widget(report, "Text4").get("value")).isEqualTo("2");
        @SuppressWarnings("unchecked") List<List<String>> rows = (List<List<String>>) widget(report, "VAGen_Container_Details_DATA_ROW").get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).contains("532-01", "1F", "C55");
        assertThat(rows.get(1).get(3)).isEqualTo("446-");                  // the screen blanks a "00" dash number
        assertThat(widget(report, "Label7").get("visible")).isEqualTo(false); // fewer than 150 rows: no "maximum reached"

        s.event((String) report.get("id"), List.of(), "Push_Button1", "clicked", null);   // Return closes the report
        assertThat((List<?>) s.state().get("windows")).hasSize(1);
    }

    @Test
    void aBackendErrorShowsTheMessageAsAPromptAndContinuesAfterOk() {
        control.set(Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("VEHICLE", "", "")));
        failAll = true;
        ScreenSession s = engine().newSession("BRIGHTCO");
        s.openRoot("VHSV01I", user);                                         // its open logic reads lists; all fail here
        Map<String, Object> st = s.state();
        @SuppressWarnings("unchecked") Map<String, Object> d = (Map<String, Object>) st.get("dialog");
        assertThat(d).isNotNull();
        assertThat((String) d.get("message")).contains("Is Not Found");
        s.answer((Integer) d.get("id"), "OK");
        assertThat(s.state().get("dialog")).isNull();
    }
}
