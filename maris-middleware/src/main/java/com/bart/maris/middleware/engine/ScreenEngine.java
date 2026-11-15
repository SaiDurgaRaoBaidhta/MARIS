package com.bart.maris.middleware.engine;

import com.bart.maris.middleware.engine.runtime.Conv;
import com.bart.maris.middleware.engine.runtime.Interp;
import com.bart.maris.middleware.engine.runtime.Rec;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition.ParamKind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the old screens' logic on the server. A screen's {@code call "PGM"} statements go to the same REST services
 * the old browser UI called, with the same record bytes.
 */
public class ScreenEngine {
    private static final Logger log = LoggerFactory.getLogger(ScreenEngine.class);

    private final ScreenBundle bundle;
    private final LegacyCatalog catalog;
    private final LegacyServiceClient client;
    private final Clock clock;
    private final Set<String> blocked;
    private final Set<String> allowed;
    private String terminalId = "MARIS-UI";

    /** the terminal id the screens record on updates (maris.engine.terminal-id) */
    public ScreenEngine terminalId(String id) {
        this.terminalId = id;
        return this;
    }

    public String terminalId() {
        return terminalId;
    }
    private final Map<String, ServiceDefinition> byProgram = new HashMap<>();

    /**
     * @param blockedPrograms programs never to call (a test guard for programs that write data); empty in normal use
     */
    public ScreenEngine(ScreenBundle bundle, LegacyCatalog catalog, LegacyServiceClient client, Clock clock, Set<String> blockedPrograms) {
        this(bundle, catalog, client, clock, blockedPrograms, Set.of());
    }

    public ScreenEngine(ScreenBundle bundle, LegacyCatalog catalog, LegacyServiceClient client, Clock clock, Set<String> blockedPrograms,
                        Set<String> allowedPrograms) {
        this.allowed = allowedPrograms;
        this.bundle = bundle;
        this.catalog = catalog;
        this.client = client;
        this.clock = clock;
        this.blocked = blockedPrograms;
        for (ServiceDefinition s : catalog.services()) {
            byProgram.putIfAbsent(s.function().toUpperCase(Locale.ROOT), s);
            if (s.program() != null) byProgram.putIfAbsent(s.program().toUpperCase(Locale.ROOT), s);
        }
    }

    public ScreenBundle bundle() {
        return bundle;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public ScreenSession newSession(String user) {
        return new ScreenSession(this, user);
    }

    /** {@code call "PGM"(rec, ...)}: records go by position into the service's record parameters and come back replaced */
    public void callProgram(ScreenInstance screen, String program, List<Rec> args, Interp in) {
        if (program.contains(".")) program = program.substring(program.lastIndexOf('.') + 1);   // call MRSTableMaintenance.VHRT04A
        String up = program.toUpperCase(Locale.ROOT);
        ServiceDefinition def = byProgram.get(up);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("program", program);
        trace.put("screen", screen.name);
        long t0 = System.nanoTime();
        if (def == null) {
            in.errorCode = "NOSERVICE";
            log.warn("screen={} program={}: no REST service for this program", screen.name, program);
            trace.put("errorCode", in.errorCode);
            screenSessionTrace(screen, trace);
            return;
        }
        if (blocked.contains(up) || !allowed.isEmpty() && !allowed.contains(up)) {
            in.errorCode = "BLOCKED";
            log.info("screen={} program={}: blocked by maris.engine.blocked-programs", screen.name, program);
            trace.put("errorCode", in.errorCode);
            screenSessionTrace(screen, trace);
            return;
        }
        List<Object> wire = new ArrayList<>();
        List<Integer> recordArg = new ArrayList<>();
        int next = 0;
        for (ServiceDefinition.Param p : def.params()) {
            switch (p.kind()) {
                case RECORD -> {
                    int len = catalog.record(p.record()).length();
                    byte[] b = new byte[len];
                    Arrays.fill(b, (byte) ' ');
                    if (next < args.size()) {
                        byte[] src = args.get(next).data;
                        System.arraycopy(src, 0, b, 0, Math.min(len, src.length));
                    }
                    wire.add(b);
                    recordArg.add(next++);
                }
                case PRIMITIVE -> {                                             // a string parameter: the argument's text
                    wire.add(next < args.size() ? Conv.rtrim(Conv.text(args.get(next).data, 0, args.get(next).data.length)) : "");
                    next++;
                    recordArg.add(-1);
                }
                case PROGRAM_RETURN -> { wire.add(null); recordArg.add(-1); }
            }
        }
        try {
            LegacyServiceClient.Reply reply = client.call(def, wire);
            for (int i = 0; i < def.params().size(); i++) {
                int a = recordArg.get(i);
                if (a < 0 || a >= args.size() || def.params().get(i).kind() != ParamKind.RECORD) continue;
                byte[] back = (byte[]) reply.values().get(i);
                if (back == null) continue;
                byte[] dst = args.get(a).data;
                System.arraycopy(back, 0, dst, 0, Math.min(back.length, dst.length));
            }
            String ec = reply.errorCode();
            in.errorCode = ec == null || ec.isBlank() ? "00000000" : ec;
        } catch (RuntimeException e) {
            in.errorCode = "SVCFAIL";
            log.warn("screen={} program={} call failed: {}", screen.name, program, e.toString());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("legacy call screen={} program={} service={} ms={} errorCode={}", screen.name, program, def.service(), ms, in.errorCode);
        trace.put("ms", ms);
        trace.put("errorCode", in.errorCode);
        screenSessionTrace(screen, trace);
    }

    private static void screenSessionTrace(ScreenInstance screen, Map<String, Object> trace) {
        screen.trace(trace);
    }
}
