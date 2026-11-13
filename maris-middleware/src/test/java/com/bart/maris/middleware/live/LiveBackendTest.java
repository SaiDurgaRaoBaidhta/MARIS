package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end against the REAL local EGL backend (Tomcat :8080 + Oracle), read-only programs only.
 * Run: MARIS_LIVE_GROUP=... MARIS_LIVE_PASSWORD=... mvn verify -Plive
 *
 * <p>Expected values were read directly from the local Oracle copy (MRS_CON, MRS_PRTR_ID, MRS_YRD_TRKG + MRS_VEH,
 * MRS_VEH_INCD + MRS_VEH_INCD_NARR) on 13-Nov-2026.
 */
@Tag("live")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveBackendTest {
    static final String GROUP = System.getenv("MARIS_LIVE_GROUP");
    static final String PASSWORD = System.getenv("MARIS_LIVE_PASSWORD");

    @LocalServerPort
    int port;
    final ObjectMapper json = new ObjectMapper();
    final CookieManager cookies = new CookieManager();
    final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    @BeforeEach
    void credentials() {
        assumeTrue(GROUP != null && PASSWORD != null, "set MARIS_LIVE_GROUP and MARIS_LIVE_PASSWORD");
    }

    JsonNode send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
        return ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_status", r.statusCode());
    }

    JsonNode signon(String group, String password) throws Exception {
        send("GET", "/api/v1/auth/csrf", null);
        return send("POST", "/api/v1/auth/signon", Map.of("groupId", group, "password", password));
    }

    @Test
    void signonThroughRealMrs02a() throws Exception {
        JsonNode r = signon(GROUP, PASSWORD);
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.get("user").asText()).isEqualTo(GROUP.toUpperCase());
        assertThat(r.get("role").asText()).isNotBlank();
        assertThat(r.get("group").asText()).isNotBlank();
        assertThat(send("GET", "/api/v1/auth/me", null).get("_status").asInt()).isEqualTo(200);
    }

    @Test
    void wrongPasswordIsRefusedByRealMrs02a() throws Exception {
        assertThat(signon(GROUP, "WRONG1").get("_status").asInt()).isEqualTo(401);
        assertThat(signon("NOGROUP", "WRONG1").get("_status").asInt()).isEqualTo(401);
    }

    @Test
    void consistLookupMatchesTheDatabase() throws Exception {
        signon(GROUP, PASSWORD);
        JsonNode r = send("POST", "/api/v1/dev/programs/VHXCONA", Map.of("records", Map.of("VHXCONA_CRIT", Map.of("conNo", "197"))));
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.at("/meta/control/returnCode").asInt()).isZero();
        assertThat(r.at("/data/VHXCONA_DATA/conNo").asText()).isEqualTo("197");
        assertThat(r.at("/data/VHXCONA_DATA/conStat").asText()).isEqualTo("OUT");   // MRS_CON.CON_STAT for 197
    }

    @Test
    void missingConsistReportsNotFound() throws Exception {
        signon(GROUP, PASSWORD);
        JsonNode r = send("POST", "/api/v1/dev/programs/VHXCONA", Map.of("records", Map.of("VHXCONA_CRIT", Map.of("conNo", "ZZZ"))));
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        assertThat(r.at("/meta/control/returnCode").asInt()).isNotZero();
        assertThat(r.at("/meta/outcome").asText()).isEqualTo("ERROR");
    }

    @Test
    void printerListDecodesAllRows() throws Exception {
        signon(GROUP, PASSWORD);
        JsonNode r = send("POST", "/api/v1/dev/programs/WS70A01", Map.of());
        assertThat(r.get("_status").asInt()).isEqualTo(200);
        JsonNode rows = r.at("/data/WS70A01_DATA/dataRow");
        assertThat(r.at("/meta/control/rowCount").asInt()).isEqualTo(rows.size());
        assertThat(rows.size()).isEqualTo(87);   // MRS_PRTR_ID rows with a VTAM name
        assertThat(rows.toString()).contains("DUBLNHP1");
    }

    @Test
    void fleetFromRealVhsw01a() throws Exception {
        signon(GROUP, PASSWORD);
        JsonNode f = send("GET", "/api/v1/fleet", null);
        assertThat(f.get("_status").asInt()).isEqualTo(200);
        // SQL: count(*) from MRS_YRD_TRKG join MRS_VEH per YRD_TRKG_YRD
        assertThat(cars(f.at("/fleet/HY"))).isEqualTo(204);
        assertThat(f.at("/fleet/HY").size()).isEqualTo(38);
        assertThat(cars(f.at("/fleet/CY"))).isEqualTo(223);
        assertThat(cars(f.at("/fleet/DY"))).isEqualTo(113);
        assertThat(cars(f.at("/fleet/BS"))).isEqualTo(34);       // MARIS yard SVB
        // first track of HY in YRD_SUB_LOC order: 1A, position 1 = car 3142, NO GO
        assertThat(f.at("/fleet/HY/0/id").asText()).isEqualTo("1A");
        assertThat(f.at("/fleet/HY/0/cars/0/car").asText()).isEqualTo("3142");
        assertThat(f.at("/fleet/HY/0/cars/0/stat").asText()).isEqualTo("NO GO");
    }

    @Test
    void revenueIncidentLogFromRealVhie01a() throws Exception {
        signon(GROUP, PASSWORD);
        JsonNode a = send("POST", "/api/v1/screens/VHIE01I/actions/Push_Button3", java.util.Map.of(
                "caption", "List", "fields", java.util.Map.of("Text1", "11-01-2026", "Text2", "11-12-2026")));
        assertThat(a.get("_status").asInt()).isEqualTo(200);
        assertThat(a.at("/control/URC").asInt()).isIn(0, 4);
        JsonNode rows = a.at("/rows/VAGen_Container_Details_DATA_ROW");
        // SQL: DELETE_IND='N', INCD_TYPE='REV', occurred 2026-11-01..12, narrative line 1 -> 8 rows
        assertThat(rows.size()).isEqualTo(8);
        assertThat(rows.at("/0/1").asText()).isEqualTo("29990010");
        assertThat(rows.at("/0/2").asText()).isEqualTo("4377");
        assertThat(rows.at("/0/3").asText()).isEqualTo("OPN");
        assertThat(a.get("message").asText()).isEqualTo("8 incidents listed.");
    }

    private static int cars(JsonNode tracks) {
        int n = 0;
        for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (!c.isNull()) n++;
        return n;
    }
}
