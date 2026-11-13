package com.bart.maris.middleware.legacy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.TestCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class LegacyServiceClientTest {
    private final LegacyCatalog catalog = TestCatalog.get();
    private FakeLegacyBackend backend;
    private LegacyProgramService service;

    @BeforeEach
    void start() throws Exception {
        backend = new FakeLegacyBackend(catalog);
        service = serviceFor(backend.baseUrl(), Duration.ofSeconds(2));
    }

    @AfterEach
    void stop() {
        backend.close();
    }

    private LegacyProgramService serviceFor(String baseUrl, Duration readTimeout) {
        MarisProperties.Legacy props = new MarisProperties.Legacy(baseUrl, "/restservices", Duration.ofSeconds(1),
                readTimeout, 50, Duration.ofSeconds(30));
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout()).build());
        f.setReadTimeout(readTimeout);
        RestClient http = RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
        return new LegacyProgramService(catalog, new LegacyServiceClient(http, new ObjectMapper(), props));
    }

    @Test
    void sendsTheSameJsonRpcBodyAsTheBrowser() {
        backend.signonProgram("BRIGHTCO", "MARIS1", "MRS01I");
        service.execute("MRS02A", Map.of("MRS02A_CRIT", Map.of("userGrp", "BRIGHTCO", "userPassword", "MARIS1")), true);

        FakeLegacyBackend.Call call = backend.calls().get(0);
        assertThat(call.service()).isEqualTo("MRS02AService");
        JsonNode body = call.body();
        assertThat(body.get("method").asText()).isEqualTo("MRS02A");
        assertThat(body.get("params")).hasSize(4);
        assertThat(body.get("params").get(0).get("contents").asText())
                .isEqualTo(" ".repeat(95) + "000%00%00" + " ".repeat(22));
        assertThat(body.get("params").get(1).get("contents").asText()).isEqualTo("BRIGHTCOMARIS1");
        assertThat(body.get("params").get(2).get("contents").asText()).isEqualTo(" ".repeat(51));
        assertThat(body.get("params").get(3).toString())
                .isEqualTo("{\"errorCode\":\"\",\"username\":\"\",\"password\":\"\",\"callingGUI\":\"\",\"sessionID\":\"\"}");
    }

    @Test
    void decodesTheNestedReply() {
        backend.signonProgram("BRIGHTCO", "MARIS1", "MRS01I");
        ProgramResult r = service.execute("mrs02a", Map.of("mrs02a_crit", Map.of("userGrp", "BRIGHTCO", "userPassword", "MARIS1")), true);
        assertThat(r.record("MRS02A_DATA")).containsEntry("signonIsValid", "Y").containsEntry("menuGuiName", "MRS01I");
        assertThat(r.control().returnCode()).isZero();
    }

    @Test
    void nonZeroProgramErrorCodeFails() {
        backend.on("VHXCONA", in -> new FakeLegacyBackend.Reply(Map.of(), "00000008"));
        assertThatThrownBy(() -> service.execute("VHXCONA", Map.of(), true))
                .isInstanceOf(LegacyProgramFailedException.class).hasMessageContaining("00000008");
    }

    @Test
    void htmlErrorPageIsAProtocolError() {
        assertThatThrownBy(() -> service.execute("VHXCONA", Map.of(), true))   // no handler -> Tomcat-style HTML 500
                .isInstanceOf(LegacyProtocolException.class).hasMessageContaining("HTTP 500");
    }

    @Test
    void malformedReplyIsAProtocolError() {
        backend.rawReply("VHXCONAService", "{\"result\": [1]}");
        assertThatThrownBy(() -> service.execute("VHXCONA", Map.of(), true)).isInstanceOf(LegacyProtocolException.class);
    }

    @Test
    void unreachableBackendIsUnavailable() {
        backend.close();
        assertThatThrownBy(() -> service.execute("VHXCONA", Map.of(), true)).isInstanceOf(LegacyUnavailableException.class);
    }

    @Test
    void slowBackendTimesOut() {
        LegacyProgramService impatient = serviceFor(backend.baseUrl(), Duration.ofMillis(300));
        backend.signonProgram("A", "B", "M");
        backend.delay(1500);
        assertThatThrownBy(() -> impatient.execute("MRS02A", Map.of(), true)).isInstanceOf(LegacyUnavailableException.class);
    }

    @Test
    void unknownParameterIsRefusedBeforeCallingTheBackend() {
        assertThatThrownBy(() -> service.execute("MRS02A", Map.of("NOPE", Map.of()), true))
                .hasMessageContaining("not a parameter of MRS02A");
        assertThat(backend.calls()).isEmpty();
    }
}
