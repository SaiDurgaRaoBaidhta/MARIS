package com.bart.maris.middleware.api.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Train incidents (VHINC01I) over HTTP. */
class IncidentsTrainApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleIncident";
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private void validBackend() {
        backend().on("VH01A02", in -> reply(0, 0, Map.of()));
        backend().on("VHINC1A", in -> reply(0, 1, Map.of("VHINC1A_CRIT", Map.of("vagenCount", 1))));
        backend().on("VHINC1B", in -> reply(0, 1, Map.of("VHINC1B_DATA", Map.of("conNo", "201", "inUseInd", "Y", "noCarsInd", "N"))));
        backend().on("VHINVC", in -> reply(0, 0, Map.of()));
    }

    private static Map<String, Object> create(String time) {
        Map<String, Object> m = new HashMap<>(Map.of("consistNo", "201", "symptom", "0120", "date", "11-13-2026", "time", time, "speed", "35",
                "direction", "N", "location", "A10", "narrative", "TRAIN STOPPED"));
        return m;
    }

    @Test
    void defaultsGiveTheRevenueDateAndTheTimeNow() throws Exception {
        backend().on("VHINCD", in -> reply(0, 1, Map.of("VHINCD_DATA", Map.of("revenueDate", "2026-11-12"))));
        backend().on("VHCI02G", in -> reply(0, 1, Map.of("VHCI02G_CRIT", Map.of("currentDate", "11-13-2026", "currentTime", "01.30.00"))));
        Answer a = get("/api/v1/incidents/train/defaults", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("date").asText()).isEqualTo("11-12-2026");         // before 3 am: the revenue day is yesterday's
        assertThat(a.body().get("time").asText()).matches("\\d{4}");
    }

    @Test
    void createSendsTheIncidentOnTheTrainCar() throws Exception {
        validBackend();
        backend().on("VHINC1F", in -> { sent.set(in.get("VHINC1F_DATA")); return reply(0, 1, Map.of("VHINC1F_DATA", Map.of("incdNo", "T0001234"))); });
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        Answer a = post("/api/v1/incidents/train", create(now));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("incidentNo").asText()).isEqualTo("T0001234");
        assertThat(sent.get()).containsEntry("carNo", TEXTS.assigned("VHINC01IS-MOVE-RE2", PKG, "CAR-NO", null)).containsEntry("conNo", "201")
                .containsEntry("userGrp", "BRIGHTCO").containsEntry("termLastUpd", "MARIS-UI");
        assertThat(called()).containsSubsequence("VH01A02", "VHINC1A", "VHINC1B", "VHINVC", "VHINC1F");
    }

    @Test
    void createRefusesWithTheOldMessagesAndWritesNothing() throws Exception {
        validBackend();
        Map<String, Object> bad = create("2599");
        bad.put("direction", "X");
        Answer a = post("/api/v1/incidents/train", bad);
        assertThat(a.status()).isEqualTo(400);
        String detail = a.body().get("detail").asText();
        assertThat(detail).contains(TEXTS.assigned("VHINC01IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", "Invalid time.").replace(".", ""));
        assertThat(detail).contains(TEXTS.assigned("VHINC01IS-BLD-MSG", PKG, "ERROR-MESSAGE-OCCR", "Invalid direction").replace(".", ""));
        assertThat(called()).doesNotContain("VHINC1F", "VHINVC");
    }

    @Test
    void aGroupWithoutAccessIsRefused() throws Exception {
        backend().on("VH01A02", in -> reply(0, 1, Map.of("VH01A02_DATA", Map.of("userGroupAccess",
                List.of(Map.of("partName", "pbtTrainIncident", "accessAllow", "N"))))));
        Answer a = post("/api/v1/incidents/train", create("1000"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(TEXTS.assigned("VH001IS-MESSAGEX", PKG, "ERROR-MESSAGEX", null));
        assertThat(called()).containsExactly("VH01A02");
    }

    @Test
    void listAndChangeAnIncident() throws Exception {
        validBackend();
        backend().on("VHINC1D", in -> "40000001".equals(in.get("VHINC1D_CRIT").get("incdNo"))
                ? reply(0, 1, Map.of("VHINC1D_DATA", Map.of("conNo", "201", "dashNo", 3, "sympCd", "0120", "speed", "35", "timeIncdOccr", "10:15:00",
                        "dataNarrRow", List.of(Map.of("narrNo", 1, "userGrp", "VEHSUPR", "narr", "STOPPED")))))
                : reply(100, 0, Map.of()));
        backend().on("VHINC1C", in -> { sent.set(in.get("VHINC1C_DATA")); return reply(0, 0, Map.of()); });
        Answer inc = get("/api/v1/incidents/train/40000001", null);
        assertThat(inc.status()).isEqualTo(200);
        assertThat(inc.body().get("time").asText()).isEqualTo("1015");
        assertThat(inc.body().get("dashNo").asText()).isEqualTo("03");
        assertThat(get("/api/v1/incidents/train/40000009", null).status()).isEqualTo(400);
        Answer ch = post("/api/v1/incidents/train/40000001", Map.of("symptom", "0130", "speed", "40", "direction", "N", "location", "A12", "narrativeIndex", 1));
        assertThat(ch.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("sympCd", "0130").containsEntry("userGrp", "VEHSUPR");
        backend().on("VHINC1C", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "AFCR001")), "00000000"));
        assertThat(post("/api/v1/incidents/train/40000001", Map.of("symptom", "0130", "speed", "40", "direction", "N", "location", "A12")).status()).isEqualTo(422);
    }
}
