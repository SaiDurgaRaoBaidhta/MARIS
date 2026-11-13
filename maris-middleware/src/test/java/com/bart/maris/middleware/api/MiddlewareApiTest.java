package com.bart.maris.middleware.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.TestCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The running middleware over real HTTP (session cookie + CSRF like a browser), against a fake EGL backend. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "maris.dev-api.enabled=true")
class MiddlewareApiTest {
    static final FakeLegacyBackend BACKEND;

    static {
        try {
            BACKEND = new FakeLegacyBackend(TestCatalog.get());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void legacyUrl(DynamicPropertyRegistry r) {
        r.add("maris.legacy.base-url", BACKEND::baseUrl);
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
    }

    @LocalServerPort
    int port;
    final ObjectMapper json = new ObjectMapper();
    Browser browser;

    @BeforeEach
    void fresh() {
        BACKEND.reset();
        BACKEND.signonProgram("BRIGHTCO", "MARIS1", "MRS01I");
        browser = new Browser();
    }

    /** Minimal browser: keeps cookies and sends the XSRF-TOKEN cookie back as the X-XSRF-TOKEN header. */
    class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> send(String method, String path, Object body) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                    .findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode signon(String group, String password) throws Exception {
            send("GET", "/api/v1/auth/csrf", null);
            HttpResponse<String> r = send("POST", "/api/v1/auth/signon", Map.of("groupId", group, "password", password));
            return ((com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(r.body())).put("_status", r.statusCode());
        }
    }

    @Test
    void signOnSeeWhoIAmAndSignOff() throws Exception {
        JsonNode r = browser.signon("brightco", "maris1");   // upper-cased like the old screen
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        // the UI's Session shape (maris-ui src/api/openapi.yaml)
        assertThat(r.get("user").asText()).isEqualTo("BRIGHTCO");
        assertThat(r.get("group").asText()).isEqualTo("LOCAL TEST GROUP");
        assertThat(r.get("role").asText()).isEqualTo("ADMIN");            // start menu MRS01I
        assertThat(r.get("update").asBoolean()).isTrue();
        assertThat(r.get("signedAt").asText()).isNotBlank();
        assertThat(r.toString()).doesNotContain("MARIS1");

        HttpResponse<String> me = browser.send("GET", "/api/v1/auth/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).get("user").asText()).isEqualTo("BRIGHTCO");

        assertThat(browser.send("POST", "/api/v1/auth/signoff", null).statusCode()).isEqualTo(204);
        assertThat(browser.send("GET", "/api/v1/auth/me", null).statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordAndUnknownGroupGiveTheSame401() throws Exception {
        JsonNode wrong = browser.signon("BRIGHTCO", "NOPE");
        JsonNode unknown = browser.signon("NOBODY", "NOPE");
        assertThat(wrong.get("_status").asInt()).isEqualTo(401);
        assertThat(wrong.get("code").asText()).isEqualTo("SIGNON_FAILED");
        assertThat(wrong.get("requestId").asText()).isNotBlank();
        assertThat(unknown.get("detail")).isEqualTo(wrong.get("detail"));
        assertThat(browser.send("GET", "/api/v1/auth/me", null).statusCode()).isEqualTo(401);
    }

    @Test
    void signonWithoutCsrfTokenIsRefused() throws Exception {
        HttpResponse<String> r = browser.send("POST", "/api/v1/auth/signon", Map.of("groupId", "BRIGHTCO", "password", "MARIS1"));
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(json.readTree(r.body()).get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void validationErrorsNameTheFields() throws Exception {
        JsonNode r = browser.signon("TOOLONGGROUP", "");
        assertThat(r.get("_status").asInt()).isEqualTo(400);
        assertThat(r.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(r.get("fields").has("groupId")).isTrue();
        assertThat(r.get("fields").has("password")).isTrue();
        assertThat(r.get("requestId").asText()).isNotBlank();
    }

    @Test
    void apiNeedsASession() throws Exception {
        HttpResponse<String> r = browser.send("GET", "/api/v1/dev/programs/MRS02A", null);
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(json.readTree(r.body()).get("code").asText()).isEqualTo("NOT_SIGNED_ON");
    }

    @Test
    void devApiRunsAProgramWithJson() throws Exception {
        BACKEND.on("VHXCONA", in -> new FakeLegacyBackend.Reply(Map.of(
                "VHXCONA_DATA", Map.of("conNo", in.get("VHXCONA_CRIT").get("conNo"), "conStat", "OUT", "dateInsrvRev", "2026-10-09"),
                "XDCONTROL", Map.of("urc", 0, "urowcnt", 1)), "00000000"));
        browser.signon("BRIGHTCO", "MARIS1");
        HttpResponse<String> r = browser.send("POST", "/api/v1/dev/programs/VHXCONA",
                Map.of("records", Map.of("VHXCONA_CRIT", Map.of("conNo", "197"))));
        JsonNode body = json.readTree(r.body());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body.at("/data/VHXCONA_DATA/conStat").asText()).isEqualTo("OUT");
        assertThat(body.at("/meta/control/returnCode").asInt()).isZero();
        assertThat(body.at("/meta/outcome").asText()).isEqualTo("OK");

        HttpResponse<String> bad = browser.send("POST", "/api/v1/dev/programs/VHXCONA",
                Map.of("records", Map.of("VHXCONA_CRIT", Map.of("conNo", "12345"))));
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(json.readTree(bad.body()).get("field").asText()).isEqualTo("VHXCONA_CRIT.conNo");

        assertThat(browser.send("POST", "/api/v1/dev/programs/NOSUCH", Map.of()).statusCode()).isEqualTo(404);
    }

    @Test
    void backendErrorsBecomeProblemDetails() throws Exception {
        browser.signon("BRIGHTCO", "MARIS1");
        HttpResponse<String> html = browser.send("POST", "/api/v1/dev/programs/VHXCONA", Map.of());   // fake answers HTML 500
        assertThat(html.statusCode()).isEqualTo(502);
        assertThat(html.body()).doesNotContain("<html").contains("BACKEND_ERROR");
    }

    @Test
    void fleetComesFromVhsw01aPerYard() throws Exception {
        BACKEND.on("VHSW01A", in -> {
            String yard = String.valueOf(in.get("VHSW01A_CRIT").get("yrdTrkgYrd")).strip();
            if (!yard.equals("HY")) return new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000");
            return new FakeLegacyBackend.Reply(Map.of(
                    "VHSW01A_DATA", Map.of("dataRow", java.util.List.of(
                            Map.of("carNo", "1234", "conNo", "197", "yrdSubLoc", "ST-01", "posnNo", 1, "vehStat", "GO", "homeYrd", "HY"),
                            Map.of("carNo", "1500", "yrdSubLoc", "ST-01", "posnNo", 3, "vehStat", "NO GO", "homeYrd", "SVB", "moveReasonCd", "AB"),
                            Map.of("carNo", "3001", "yrdSubLoc", "ST-02", "posnNo", 1, "vehStat", "PM", "homeYrd", "CY"))),
                    "XDCONTROL", Map.of("urc", 0, "urowcnt", 3)), "00000000");
        });
        browser.signon("BRIGHTCO", "MARIS1");
        HttpResponse<String> r = browser.send("GET", "/api/v1/fleet", null);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode f = json.readTree(r.body());
        assertThat(f.get("mapYards").toString()).isEqualTo("[\"HY\",\"DY\",\"BS\",\"RY\",\"CY\",\"DP\",\"PB\",\"MB\"]");
        assertThat(f.at("/yards/BS/pseudo").asText()).isEqualTo("SVB");
        assertThat(f.get("capacityKnown").asBoolean(true)).isFalse();
        JsonNode st1 = f.at("/fleet/HY/0");
        assertThat(st1.get("id").asText()).isEqualTo("ST-01");
        assertThat(st1.get("cap").asInt()).isEqualTo(3);
        assertThat(st1.at("/cars/0/car").asText()).isEqualTo("1234");
        assertThat(st1.at("/cars/0/consist").asText()).isEqualTo("197");
        assertThat(st1.at("/cars/1").isNull()).isTrue();                    // empty place
        assertThat(st1.at("/cars/2/home").asText()).isEqualTo("BS");        // SVB -> the UI's code
        assertThat(st1.at("/cars/2/reason").asText()).isEqualTo("AB");
        assertThat(f.at("/fleet/HY/1/cars/0/stat").asText()).isEqualTo("PM");
        assertThat(f.at("/fleet/DY").size()).isZero();                       // URC 100: no cars
        assertThat(BACKEND.calls().stream().filter(c -> c.service().equals("VHSW01AService")).count()).isEqualTo(8);
    }

    @Test
    void revenueIncidentLogListCallsVhie01a() throws Exception {
        BACKEND.on("VHIE01A", in -> {
            Map<String, Object> crit = in.get("VHIE01A_CRIT");
            assertThat(crit.get("fromDate")).isEqualTo("2026-11-01");
            assertThat(crit.get("toDate")).isEqualTo("2026-11-12");
            return new FakeLegacyBackend.Reply(Map.of(
                    "VHIE01A_DATA", Map.of("dataRow", java.util.List.of(Map.<String, Object>ofEntries(
                            Map.entry("dateIncdOccrRev", "2026-11-12"), Map.entry("incdNo", "R0001234"), Map.entry("carNo", "1234"),
                            Map.entry("vehIncdStat", "OPN"), Map.entry("sympCd", "BRK"), Map.entry("timeIncdOccr", Map.of("timeHhmm", "07:15")),
                            Map.entry("speed", "25"), Map.entry("conDirection", "N"), Map.entry("incdLoc", "M10"),
                            Map.entry("conNo2", Map.of("con", "197", "dash", "-", "conDashNo", 3)), Map.entry("narr", "BRAKES DRAGGING")))),
                    "VHIE01A_CRIT", Map.of("exceedFlag", "N"),
                    "XDCONTROL", Map.of("urc", 0, "urowcnt", 1)), "00000000");
        });
        browser.signon("BRIGHTCO", "MARIS1");
        HttpResponse<String> r = browser.send("POST", "/api/v1/screens/VHIE01I/actions/Push_Button3", Map.of(
                "screen", "VHIE01I", "control", "Push_Button3", "caption", "List", "programs", java.util.List.of("VHIE01A"),
                "fields", Map.of("Text1", "11-01-2026", "Text2", "11/12/2026", "Text3", "")));
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode a = json.readTree(r.body());
        assertThat(a.at("/control/URC").asInt()).isZero();
        assertThat(a.at("/rows/VAGen_Container_Details_DATA_ROW/0").toString())
                .isEqualTo("[\"2026-11-12\",\"R0001234\",\"1234\",\"OPN\",\"BRK\",\"07:15\",\"25\",\"N\",\"M10\",\"197-03\",\"BRAKES DRAGGING\"]");
        assertThat(a.at("/fields/Text3").asText()).isEmpty();
        assertThat(a.get("message").asText()).isEqualTo("1 incident listed.");
    }

    @Test
    void revenueIncidentLogChecksDatesLikeTheOldScreen() throws Exception {
        browser.signon("BRIGHTCO", "MARIS1");
        java.util.function.Function<Map<String, String>, JsonNode> press = f -> {
            try {
                return json.readTree(browser.send("POST", "/api/v1/screens/vhie01i/actions/Push_Button3",
                        Map.of("caption", "List", "fields", f)).body());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        assertThat(press.apply(Map.of("Text1", "12-01-2026", "Text2", "11-01-2026")).get("message").asText())
                .isEqualTo("FROM Date Must Be Less Than or Equal to TO Date");
        assertThat(press.apply(Map.of("Text1", "11-01-2099", "Text2", "11-01-2099")).at("/fields/Text3").asText())
                .isEqualTo("Future FROM Date is Not Allowed");
        assertThat(press.apply(Map.of("Text1", "13-45-2026", "Text2", "")).at("/control/URC").asInt()).isNotZero();
        assertThat(BACKEND.calls().stream().noneMatch(c -> c.service().equals("VHIE01AService"))).isTrue();
    }

    @Test
    void revenueIncidentLogNoRowsUsesTheOldMessage() throws Exception {
        BACKEND.on("VHIE01A", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 100)), "00000000"));
        browser.signon("BRIGHTCO", "MARIS1");
        JsonNode a = json.readTree(browser.send("POST", "/api/v1/screens/VHIE01I/actions/Push_Button3",
                Map.of("fields", Map.of("Text1", "01-01-2020", "Text2", "01-02-2020"))).body());
        assertThat(a.at("/control/URC").asInt()).isEqualTo(100);
        assertThat(a.get("message").asText()).isEqualTo("No Data Found for Date Request");
        assertThat(a.at("/rows/VAGen_Container_Details_DATA_ROW").size()).isZero();
    }

    @Test
    void buttonsNotTranslatedYetAnswer501AndCallNothing() throws Exception {
        browser.signon("BRIGHTCO", "MARIS1");
        HttpResponse<String> r = browser.send("POST", "/api/v1/screens/VHCF01I/actions/Push_Button321", Map.of(
                "caption", "Update", "programs", java.util.List.of("VHCF01P", "VHCF01D"), "fields", Map.of()));
        assertThat(r.statusCode()).isEqualTo(501);
        JsonNode p = json.readTree(r.body());
        assertThat(p.get("code").asText()).isEqualTo("NOT_MIGRATED");
        assertThat(p.get("detail").asText()).isEqualTo("\"Update\" on VHCF01I is not connected to the MARIS system yet.");
        assertThat(BACKEND.calls().stream().noneMatch(c -> c.service().startsWith("VHCF01"))).isTrue();
        assertThat(browser.send("GET", "/api/v1/screens/actions", null).body()).contains("VHIE01I/Push_Button3");
    }

    @Test
    void screensAndFleetNeedASession() throws Exception {
        assertThat(browser.send("GET", "/api/v1/fleet", null).statusCode()).isEqualTo(401);
        browser.send("GET", "/api/v1/auth/csrf", null);
        assertThat(browser.send("POST", "/api/v1/screens/VHIE01I/actions/Push_Button3", Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void healthShowsTheBackend() throws Exception {
        HttpResponse<String> r = browser.send("GET", "/actuator/health", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(json.readTree(r.body()).at("/components/legacyBackend/status").asText()).isEqualTo("UP");
    }

    @Test
    void openApiDescribesTheEndpoints() throws Exception {
        HttpResponse<String> r = browser.send("GET", "/v3/api-docs", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("/api/v1/auth/signon");
    }
}
