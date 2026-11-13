package com.bart.maris.middleware.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.security.MarisUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Parity between a clean API and the old screen it replaces: both run against the SAME scripted backend, and the
 * test compares what each sent to MARIS (and what each shows). The old screen runs in the screen engine, which
 * executes its original EGL logic; so a rule the clean API forgot shows up as a difference here.
 */
public final class Parity {
    private static ScreenBundle bundle;
    private final LegacyCatalog catalog = TestCatalog.get();
    private final Map<String, Function<Map<String, Map<String, Object>>, Map<String, Object>>> answers = new HashMap<>();
    /** every call: program -> the records sent (decoded) */
    public final List<Map.Entry<String, Map<String, Map<String, Object>>>> sent = new ArrayList<>();
    public final LegacyServiceClient client = mock(LegacyServiceClient.class);
    public final Clock clock = Clock.systemDefaultZone();
    public final MarisUser user = new MarisUser("BRIGHTCO", "TEST", "VH001I", "ALL", "HY", "", "ADMIN", Instant.now());

    public Parity() {
        when(client.call(any(), any())).thenAnswer(inv -> {
            ServiceDefinition def = inv.getArgument(0);
            List<Object> args = new ArrayList<>(inv.getArgument(1));
            Map<String, Map<String, Object>> in = new LinkedHashMap<>();
            for (int i = 0; i < def.params().size(); i++) {
                ServiceDefinition.Param p = def.params().get(i);
                if (p.kind() == ServiceDefinition.ParamKind.RECORD) in.put(p.name(), RecordCodec.decode(catalog.record(p.record()), (byte[]) args.get(i), true));
            }
            sent.add(Map.entry(def.function(), in));
            Function<Map<String, Map<String, Object>>, Map<String, Object>> f = answers.get(def.function());
            Map<String, Object> out = f == null ? Map.of("XDCONTROL", Map.of("urc", 100)) : f.apply(in);
            for (int i = 0; i < def.params().size(); i++) {
                ServiceDefinition.Param p = def.params().get(i);
                if (p.kind() != ServiceDefinition.ParamKind.RECORD) continue;
                @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) out.get(p.name());
                if (v != null) args.set(i, RecordCodec.encode(catalog.record(p.record()), v));
            }
            return new LegacyServiceClient.Reply(args, "00000000");
        });
    }

    /** what a program answers: records by parameter name (XDCONTROL included) */
    public Parity on(String program, Function<Map<String, Map<String, Object>>, Map<String, Object>> answer) {
        answers.put(program, answer);
        return this;
    }

    /** the old screens, run by the engine against this backend */
    public ScreenSession oldScreens() {
        return new ScreenEngine(bundle(), catalog, client, clock, Set.of()).newSession("BRIGHTCO");
    }

    /** the screen bundle (old screens' logic and texts) */
    public static synchronized ScreenBundle bundle() {
        if (bundle == null) bundle = ScreenBundle.load("classpath:egl-screens/");
        return bundle;
    }

    /** the legacy layer the clean services use, against this backend */
    public LegacyProgramService legacy() {
        return new LegacyProgramService(catalog, client);
    }

    public MessageResolver messages() {
        return new MessageResolver(catalog);
    }

    /** the records sent on the last call of a program */
    public Map<String, Map<String, Object>> lastSent(String program) {
        for (int i = sent.size() - 1; i >= 0; i--) if (sent.get(i).getKey().equals(program)) return sent.get(i).getValue();
        return null;
    }

    public void clear() {
        sent.clear();
    }
}
