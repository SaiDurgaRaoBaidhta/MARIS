package com.bart.maris.middleware.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for the read-only tests against the REAL local backend (Tomcat :8080, Oracle). One class per module
 * (live/&lt;Module&gt;LiveTest.java). Run: MARIS_LIVE_GROUP=… MARIS_LIVE_PASSWORD=… mvn test -Plive.
 * Never call a program that writes from a live test.
 */
@Tag("live")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class LiveTestBase {
    protected static final String GROUP = System.getenv("MARIS_LIVE_GROUP");
    protected static final String PASSWORD = System.getenv("MARIS_LIVE_PASSWORD");

    @LocalServerPort
    protected int port;
    protected final ObjectMapper json = new ObjectMapper();
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    @BeforeEach
    void signedOnToTheRealBackend() throws Exception {
        assumeTrue(GROUP != null && PASSWORD != null, "set MARIS_LIVE_GROUP and MARIS_LIVE_PASSWORD");
        send("GET", "/api/v1/auth/csrf", null);
        send("POST", "/api/v1/auth/signon", Map.of("groupId", GROUP, "password", PASSWORD));
    }

    /** the answer body with "_status" added */
    protected JsonNode send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
        if (!node.isObject()) node = json.createObjectNode().set("_body", node);
        return ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_status", r.statusCode());
    }
}
