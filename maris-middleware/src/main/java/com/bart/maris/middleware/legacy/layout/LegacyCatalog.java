package com.bart.maris.middleware.legacy.layout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * The generated description of the legacy backend: services, record layouts and message texts.
 * Built by tools/egl-catalog-generator from the EGL source; loaded once at start-up.
 */
public class LegacyCatalog {
    private static final Logger log = LoggerFactory.getLogger(LegacyCatalog.class);

    private final Map<String, ServiceDefinition> byFunction = new HashMap<>();
    private final Map<String, RecordLayout> records;
    private final Map<String, MessageDefinition> messages;
    private final List<ServiceDefinition> services;

    public LegacyCatalog(List<ServiceDefinition> services, Map<String, RecordLayout> records,
                         Map<String, MessageDefinition> messages) {
        this.services = List.copyOf(services);
        this.records = Map.copyOf(records);
        this.messages = Map.copyOf(messages);
        for (ServiceDefinition s : services) {
            for (ServiceDefinition.Param p : s.params()) {
                if (p.kind() == ServiceDefinition.ParamKind.RECORD && !records.containsKey(p.record())) {
                    throw new IllegalStateException(s.service() + " uses unknown record " + p.record());
                }
            }
            if (byFunction.put(key(s.function()), s) != null) {
                throw new IllegalStateException("duplicate service function " + s.function());
            }
        }
    }

    public static LegacyCatalog load(ResourceLoader loader, String location) {
        ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String base = location.endsWith("/") ? location : location + "/";
        List<ServiceDefinition> services = read(om, loader.getResource(base + "services.json"), new TypeReference<>() {});
        Map<String, RecordLayout> records = read(om, loader.getResource(base + "records.json"), new TypeReference<>() {});
        Map<String, MessageDefinition> messages = read(om, loader.getResource(base + "messages.json"), new TypeReference<>() {});
        LegacyCatalog c = new LegacyCatalog(services, records, messages);
        log.info("Legacy catalog loaded: {} services, {} record layouts, {} messages", services.size(), records.size(), messages.size());
        return c;
    }

    private static <T> T read(ObjectMapper om, Resource r, TypeReference<T> type) {
        try (InputStream in = r.getInputStream()) {
            return om.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read legacy catalog file " + r.getDescription(), e);
        }
    }

    /** Looks a service up by its JSON-RPC function name (normally the EGL program name), ignoring case. */
    public Optional<ServiceDefinition> function(String name) {
        return Optional.ofNullable(byFunction.get(key(name)));
    }

    public RecordLayout record(String qualifiedName) {
        RecordLayout r = records.get(qualifiedName);
        if (r == null) throw new IllegalArgumentException("Unknown record " + qualifiedName);
        return r;
    }

    public Optional<MessageDefinition> message(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(messages.get(code.strip()));
    }

    public Collection<ServiceDefinition> services() {
        return services;
    }

    private static String key(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
