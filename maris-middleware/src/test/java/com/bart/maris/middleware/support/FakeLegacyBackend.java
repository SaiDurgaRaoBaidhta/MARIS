package com.bart.maris.middleware.support;

import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.codec.WireEscaping;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A stand-in for MARIS_RUI_Services_Web that speaks the same JSON-RPC protocol, for tests that must not depend on
 * the real backend. Handlers receive the decoded request records and return reply records.
 */
public final class FakeLegacyBackend implements AutoCloseable {
    public record Call(String service, String rawBody, JsonNode body) {}

    /** Reply records keyed by parameter name (decoded maps), plus the PROGRAM_RETURN_REC errorCode. */
    public record Reply(Map<String, Map<String, Object>> records, String errorCode) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final LegacyCatalog catalog;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<String, Function<Map<String, Map<String, Object>>, Reply>> handlers = new ConcurrentHashMap<>();
    private final Map<String, String> rawReplies = new ConcurrentHashMap<>();
    private volatile int delayMs;

    public FakeLegacyBackend(LegacyCatalog catalog) throws IOException {
        this.catalog = catalog;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/MARIS_RUI_Services_Web/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                String service = path.substring(path.lastIndexOf('/') + 1);
                if (!path.contains("/restservices/")) {
                    respond(ex, 404, "<html>not found</html>");
                    return;
                }
                if (delayMs > 0) Thread.sleep(delayMs);
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = JSON.readTree(body);
                calls.add(new Call(service, body, json));
                if (rawReplies.containsKey(service)) {
                    String raw = rawReplies.get(service);
                    int status = raw.startsWith("<") ? 500 : 200;
                    respond(ex, status, raw);
                    return;
                }
                ServiceDefinition def = catalog.function(json.get("method").asText()).orElse(null);
                var handler = def == null ? null : handlers.get(def.function());
                if (handler == null) {
                    respond(ex, 500, "<!doctype html><html><body>HTTP Status 500</body></html>");
                    return;
                }
                respond(ex, 200, reply(def, json, handler));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                respond(ex, 500, "<html>" + e + "</html>");
            }
        });
        server.start();
    }

    private String reply(ServiceDefinition def, JsonNode req, Function<Map<String, Map<String, Object>>, Reply> handler) {
        Map<String, Map<String, Object>> in = new java.util.LinkedHashMap<>();
        for (int i = 0; i < def.params().size(); i++) {
            ServiceDefinition.Param p = def.params().get(i);
            if (p.kind() == ServiceDefinition.ParamKind.RECORD) {
                byte[] b = WireEscaping.unescape(req.get("params").get(i).get("contents").asText());
                in.put(p.name(), RecordCodec.decode(catalog.record(p.record()), b, true));
            }
        }
        Reply r = handler.apply(in);
        ObjectNode root = JSON.createObjectNode();
        ArrayNode result = root.putArray("result");
        for (int i = 0; i < def.params().size(); i++) {
            ServiceDefinition.Param p = def.params().get(i);
            if (p.kind() == ServiceDefinition.ParamKind.RECORD) {
                Map<String, Object> values = r.records().getOrDefault(p.name(), in.get(p.name()));
                byte[] bytes = RecordCodec.encode(catalog.record(p.record()), values);
                // like the real server: contents holds a JSON string that itself holds {"contents": "..."}
                result.addObject().put("contents", "{\"contents\":\"" + WireEscaping.escape(bytes) + "\"}");
            } else if (p.kind() == ServiceDefinition.ParamKind.PROGRAM_RETURN) {
                ObjectNode prr = result.addObject();
                prr.put("errorCode", r.errorCode());
                for (String k : List.of("username", "password", "callingGUI", "sessionID")) prr.put(k, "");
            } else {
                result.add(req.get("params").get(i));
            }
        }
        return root.toString();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", body.startsWith("<") ? "text/html" : "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    public FakeLegacyBackend on(String function, Function<Map<String, Map<String, Object>>, Reply> handler) {
        handlers.put(function, handler);
        return this;
    }

    public FakeLegacyBackend rawReply(String service, String body) {
        rawReplies.put(service, body);
        return this;
    }

    public void delay(int ms) {
        this.delayMs = ms;
    }

    public void reset() {
        calls.clear();
        handlers.clear();
        rawReplies.clear();
        delayMs = 0;
    }

    public List<Call> calls() {
        return calls;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/MARIS_RUI_Services_Web";
    }

    /** Behaves like program MRS02A for one known group and password. */
    public FakeLegacyBackend signonProgram(String group, String password, String menu) {
        return on("MRS02A", in -> {
            Map<String, Object> crit = in.get("MRS02A_CRIT");
            boolean exists = group.equals(crit.get("userGrp"));
            boolean valid = exists && password.equals(crit.get("userPassword"));
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("userGrpExists", exists ? "Y" : "N");
            data.put("signonIsValid", valid ? "Y" : "N");
            if (valid) {
                data.put("userGrpDesc", "LOCAL TEST GROUP");
                data.put("menuGuiName", menu);
            }
            return new Reply(Map.of("MRS02A_DATA", data, "XDCONTROL", Map.of("urc", 0)), "00000000");
        });
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
