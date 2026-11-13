package com.bart.maris.middleware.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the clean-API tests of every module: the real middleware over HTTP (session cookie + CSRF, like the UI),
 * against one fake EGL backend shared by all test classes (so Spring starts once).
 *
 * <p>Each test says what the backend programs answer ({@link #backend()}.on(...)), calls the API like the UI does,
 * and checks both the JSON the UI gets and the records the middleware sent to the backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestBase {
    private static final FakeLegacyBackend BACKEND;

    static {
        try {
            BACKEND = new FakeLegacyBackend(TestCatalog.get());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(BACKEND::close));
    }

    @DynamicPropertySource
    static void legacyUrl(DynamicPropertyRegistry r) {
        r.add("maris.legacy.base-url", BACKEND::baseUrl);
    }

    @LocalServerPort
    protected int port;
    protected final ObjectMapper json = new ObjectMapper();
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    protected static FakeLegacyBackend backend() {
        return BACKEND;
    }

    @BeforeEach
    void signedOn() throws Exception {
        BACKEND.reset();
        BACKEND.signonProgram("BRIGHTCO", "MARIS1", "MRS01I");
        send("GET", "/api/v1/auth/csrf", null);
        send("POST", "/api/v1/auth/signon", Map.of("groupId", "BRIGHTCO", "password", "MARIS1"));
        BACKEND.reset();                                     // tests see only their own calls
        BACKEND.signonProgram("BRIGHTCO", "MARIS1", "MRS01I");
    }

    /** an HTTP answer: status + parsed body */
    public record Answer(int status, JsonNode body) {}

    protected Answer get(String path, Map<String, ?> query) throws Exception {
        StringBuilder q = new StringBuilder();
        if (query != null) {
            for (Map.Entry<String, ?> e : query.entrySet()) {
                q.append(q.isEmpty() ? "?" : "&").append(e.getKey()).append('=')
                        .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        return send("GET", path + q, null);
    }

    protected Answer post(String path, Object body) throws Exception {
        return send("POST", path, body);
    }

    protected Answer send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Answer(r.statusCode(), r.body().isEmpty() ? json.nullNode() : json.readTree(r.body()));
    }

    /** the backend answers: records by parameter name + XDCONTROL return code / row count */
    protected static FakeLegacyBackend.Reply reply(int urc, int rows, Map<String, Object> records) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        records.forEach((k, v) -> {
            @SuppressWarnings("unchecked") Map<String, Object> rec = (Map<String, Object>) v;
            m.put(k, rec);
        });
        m.put("XDCONTROL", Map.of("urc", urc, "urowcnt", rows));
        return new FakeLegacyBackend.Reply(m, "00000000");
    }

    /** a list of rows as a record with one array field */
    protected static Map<String, Object> rows(String field, List<Map<String, Object>> rows) {
        return Map.of(field, rows);
    }

    /** the programs the middleware called, in order */
    protected static List<String> called() {
        return BACKEND.calls().stream().map(c -> c.service().replaceFirst("Service$", "")).toList();
    }
}
