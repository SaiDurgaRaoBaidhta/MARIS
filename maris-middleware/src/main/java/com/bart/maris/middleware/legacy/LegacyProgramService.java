package com.bart.maris.middleware.legacy;

import com.bart.maris.middleware.legacy.client.LegacyProgramFailedException;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.codec.CodecException;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.codec.WireEncodingGuard;
import com.bart.maris.middleware.legacy.control.XdControl;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition.ParamKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a legacy program by name with JSON-style inputs and returns decoded JSON-style outputs.
 * This is the single entry point the domain services use to reach the EGL backend.
 */
public class LegacyProgramService {
    private static final Logger log = LoggerFactory.getLogger(LegacyProgramService.class);
    private static final String OK = "00000000";

    private final LegacyCatalog catalog;
    private final LegacyServiceClient client;

    public LegacyProgramService(LegacyCatalog catalog, LegacyServiceClient client) {
        this.catalog = catalog;
        this.client = client;
    }

    public ServiceDefinition definition(String function) {
        return catalog.function(function).orElseThrow(() -> new UnknownProgramException(function));
    }

    /**
     * @param inputs        parameter name (e.g. {@code MRS02A_CRIT}, case-insensitive) -> record values or a primitive.
     *                      Parameters that are not given are sent as empty records, like a fresh screen.
     * @param trimEmptyRows drop unused trailing rows of table fields in the result
     */
    public ProgramResult execute(String function, Map<String, ?> inputs, boolean trimEmptyRows) {
        ServiceDefinition def = definition(function);
        Map<String, Object> byUpperName = new LinkedHashMap<>();
        if (inputs != null) inputs.forEach((k, v) -> byUpperName.put(k.toUpperCase(java.util.Locale.ROOT), v));
        for (String k : byUpperName.keySet()) {
            if (def.params().stream().noneMatch(p -> p.kind() != ParamKind.PROGRAM_RETURN && p.name().equalsIgnoreCase(k))) {
                throw new CodecException(k, "is not a parameter of " + def.function());
            }
        }

        List<Object> args = new ArrayList<>();
        for (ServiceDefinition.Param p : def.params()) {
            Object in = byUpperName.get(p.name().toUpperCase(java.util.Locale.ROOT));
            switch (p.kind()) {
                case RECORD -> {
                    if (in != null && !(in instanceof Map)) throw new CodecException(p.name(), "must be an object");
                    @SuppressWarnings("unchecked") Map<String, ?> values = (Map<String, ?>) in;
                    args.add(RecordCodec.encode(catalog.record(p.record()), values));
                }
                case PRIMITIVE -> args.add(in == null ? "" : in);
                case PROGRAM_RETURN -> args.add(null);
            }
        }

        long start = System.nanoTime();
        LegacyServiceClient.Reply reply;
        try {
            reply = client.call(def, args);
        } catch (RuntimeException e) {
            log.warn("legacy call failed function={} service={} ms={} error={}", def.function(), def.service(),
                    (System.nanoTime() - start) / 1_000_000, e.getMessage());
            throw e;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        Map<String, Object> out = new LinkedHashMap<>();
        XdControl control = null;
        for (int i = 0; i < def.params().size(); i++) {
            ServiceDefinition.Param p = def.params().get(i);
            if (p.kind() == ParamKind.RECORD) {
                RecordLayout layout = catalog.record(p.record());
                byte[] bytes = (byte[]) reply.values().get(i);
                if (WireEncodingGuard.looksEbcdic(layout, bytes)) {
                    log.warn("function={} record={} came back in EBCDIC; converting", def.function(), p.name());
                    bytes = WireEncodingGuard.toAscii(layout, bytes);
                }
                Map<String, Object> decoded = RecordCodec.decode(layout, bytes, trimEmptyRows);
                out.put(p.name(), decoded);
                if (XdControl.RECORD.equals(p.record())) control = XdControl.from(decoded);
            } else if (p.kind() == ParamKind.PRIMITIVE) {
                out.put(p.name(), reply.values().get(i));
            }
        }

        log.info("legacy call function={} service={} ms={} errorCode={} urc={} rows={}", def.function(), def.service(), ms,
                reply.errorCode(), control == null ? "-" : control.returnCode(), control == null ? "-" : control.rowCount());
        if (reply.errorCode() != null && !reply.errorCode().isEmpty() && !OK.equals(reply.errorCode())) {
            throw new LegacyProgramFailedException(def.function(), reply.errorCode());
        }
        return new ProgramResult(def.function(), def.service(), out, control, ms);
    }
}
