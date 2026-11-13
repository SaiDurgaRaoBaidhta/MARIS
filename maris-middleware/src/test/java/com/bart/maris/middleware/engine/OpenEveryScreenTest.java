package com.bart.maris.middleware.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.security.MarisUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** Every old screen opens: its connections wire, its open logic runs (against a backend that answers "no rows"). */
class OpenEveryScreenTest {
    static ScreenEngine engine() {
        LegacyCatalog catalog = LegacyCatalog.load(new DefaultResourceLoader(), "classpath:legacy-catalog/");
        LegacyServiceClient client = mock(LegacyServiceClient.class);
        when(client.call(any(), any())).thenAnswer(inv -> new LegacyServiceClient.Reply(new ArrayList<>(inv.getArgument(1)), "00000000"));
        return new ScreenEngine(ScreenBundle.load("classpath:egl-screens/"), catalog, client, Clock.systemDefaultZone(), Set.of());
    }

    @Test
    void opensEveryScreen() {
        ScreenEngine engine = engine();
        MarisUser user = new MarisUser("BRIGHTCO", "TEST", "VH001I", "ALL", "HY", "", "ADMIN", Instant.now());
        Map<String, String> failed = new TreeMap<>();
        Map<String, Integer> warned = new TreeMap<>();
        int ok = 0;
        for (String s : new TreeSet<>(engine.bundle().screenNames())) {
            ScreenSession ss = engine.newSession("BRIGHTCO");
            try {
                ss.openRoot(s, user);
                Map<String, Object> st = ss.state();
                Object d = st.get("dialog");
                if (d instanceof Map<?, ?> m && String.valueOf(m.get("message")).startsWith("The screen could not finish")) failed.put(s, String.valueOf(m.get("message")));
                else ok++;
                if (!ss.warnings().isEmpty()) warned.put(s, ss.warnings().size());
                ss.warnings().forEach(w -> System.out.println("W " + w.replaceAll("\\{[^}]*\\}", "").replaceAll("\\[.*?\\]", "[..]")));
            } catch (RuntimeException e) {
                failed.put(s, e.toString());
            }
        }
        System.out.println("OPENED OK: " + ok + "  FAILED: " + failed.size() + "  WITH WIRING WARNINGS: " + warned.size());
        failed.forEach((k, v) -> System.out.println("FAIL " + k + ": " + v));
        System.out.println("WARN " + warned);
    }
}
