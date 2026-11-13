package com.bart.maris.middleware.legacy.client;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.legacy.codec.WireEscaping;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition.ParamKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Speaks the EGL REST services' protocol exactly as the old browser UI does:
 *
 * <pre>
 * POST {baseUrl}/restservices/{Service}
 * {"method": "MRS02A", "params": [{"contents": "%xx-escaped record bytes"}, ..., {"errorCode": "", ...}]}
 * -> {"result": [{"contents": "{\"contents\":\"...\"}"}, ..., {"errorCode": "00000000", ...}]}
 * </pre>
 *
 * Record bytes in, record bytes out; this class knows nothing about field layouts.
 */
public class LegacyServiceClient {
    private final RestClient http;
    private final ObjectMapper json;
    private final String servicesPath;
    private final CircuitBreaker breaker;

    public LegacyServiceClient(RestClient http, ObjectMapper json, MarisProperties.Legacy props) {
        this.http = http;
        this.json = json;
        this.servicesPath = props.servicesPath().endsWith("/") ? props.servicesPath() : props.servicesPath() + "/";
        this.breaker = CircuitBreaker.of("legacy", CircuitBreakerConfig.custom()
                .failureRateThreshold(props.circuitBreakerFailureRate() > 0 ? props.circuitBreakerFailureRate() : 50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(props.circuitBreakerOpenFor())
                .recordExceptions(LegacyUnavailableException.class)
                .build());
    }

    /** One value per service parameter, in order: byte[] for RECORD, Object for PRIMITIVE, ignored for PROGRAM_RETURN. */
    public record Reply(List<Object> values, String errorCode) {}

    public Reply call(ServiceDefinition service, List<Object> args) {
        ObjectNode body = json.createObjectNode();
        body.put("method", service.function());
        ArrayNode params = body.putArray("params");
        for (int i = 0; i < service.params().size(); i++) {
            ServiceDefinition.Param p = service.params().get(i);
            switch (p.kind()) {
                case RECORD -> params.addObject().put("contents", WireEscaping.escape((byte[]) args.get(i)));
                case PRIMITIVE -> params.add(json.valueToTree(args.get(i)));
                case PROGRAM_RETURN -> {
                    ObjectNode prr = params.addObject();
                    for (String k : List.of("errorCode", "username", "password", "callingGUI", "sessionID")) prr.put(k, "");
                }
            }
        }
        String reply;
        try {
            reply = breaker.executeSupplier(() -> post(service.service(), body));
        } catch (CallNotPermittedException e) {
            throw new LegacyUnavailableException("MARIS backend calls are paused after repeated failures", e);
        }
        return parse(service, reply);
    }

    private String post(String serviceName, ObjectNode body) {
        try {
            return http.post()
                    .uri(servicesPath + serviceName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), (req, res) -> {
                        throw new LegacyProtocolException("MARIS backend returned HTTP " + res.getStatusCode().value()
                                + " for " + serviceName, res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new LegacyUnavailableException("MARIS backend is not reachable: " + e.getMostSpecificCause().getMessage(), e);
        }
    }

    private Reply parse(ServiceDefinition service, String reply) {
        JsonNode result;
        try {
            JsonNode root = json.readTree(reply == null ? "" : reply);
            result = root == null ? null : root.get("result");
        } catch (IOException e) {
            throw new LegacyProtocolException("MARIS backend reply for " + service.service() + " is not JSON", 200);
        }
        if (result == null || !result.isArray() || result.size() != service.params().size()) {
            throw new LegacyProtocolException("MARIS backend reply for " + service.service()
                    + " does not have " + service.params().size() + " results", 200);
        }
        List<Object> values = new ArrayList<>();
        String errorCode = null;
        for (int i = 0; i < service.params().size(); i++) {
            ServiceDefinition.Param p = service.params().get(i);
            JsonNode node = result.get(i);
            if (p.kind() == ParamKind.RECORD) {
                values.add(WireEscaping.unescape(recordContents(node, service)));
            } else if (p.kind() == ParamKind.PRIMITIVE) {
                values.add(json.convertValue(node, Object.class));
            } else {
                errorCode = node.path("errorCode").asText("");
                values.add(null);
            }
        }
        return new Reply(values, errorCode);
    }

    /** The browser does JSON.parse(contents.replace(/\\/g, "\\\\")).contents; the same here. */
    private String recordContents(JsonNode node, ServiceDefinition service) {
        String outer = node.path("contents").asText(null);
        if (outer == null) throw new LegacyProtocolException("record result without contents in " + service.service(), 200);
        if (!outer.startsWith("{")) return outer;
        try {
            return json.readTree(outer.replace("\\", "\\\\")).path("contents").asText("");
        } catch (IOException e) {
            throw new LegacyProtocolException("malformed record contents in " + service.service(), 200);
        }
    }

    public CircuitBreaker.State breakerState() {
        return breaker.getState();
    }
}
