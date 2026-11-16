package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lay-up (old VHLT01I) and reconfigure (old VHTR01I / VHTR02I) over HTTP against the fake backend. */
class ConsistTrainsApiTest extends ApiTestBase {
    @BeforeEach
    void programs() {
        backend().on("VHLT010", in -> reply(0, 2, Map.of("VHLT010_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"))))));
        backend().on("VHLT020", in -> reply(0, 1, Map.of("VHLT020_DATA", Map.of("yrdTrkgYrd", "HY"))));
        backend().on("VHXREVC", in -> reply(0, 0, Map.of("VHXREVC_DATA", Map.of("revenueDate", "11-14-2026"))));
        backend().on("VHTR012", in -> reply(0, 0, Map.of("VHTR012_DATA", Map.of("currentTime", "23:50:00"))));
        backend().on("VHLT011", in -> reply(0, 2, Map.of("VHLT011_DATA", rows("dataRow", List.of(Map.of("yrdSubLoc", "T-10"), Map.of("yrdSubLoc", "T-11"))))));
        backend().on("VHLT009", in -> reply(0, 1, Map.of("VHLT009_CRIT", Map.of("conNo", "201", "noRecordFound", "N"),
                "VHLT009_DATA", Map.of("conStat", "OUT", "dashNo", 3, "timeInsrv", "05:10:00"))));
        backend().on("VHTR011", in -> reply(0, 1, Map.of("VHTR011_CRIT", Map.of("conNo", "201", "noRecordFound", "Y"))));
    }

    @Test
    void theLayUpOptionsAreTheYardsAndTracks() throws Exception {
        Answer a = get("/api/v1/consist/lay-up/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/yards/1").asText()).isEqualTo("HY");
        assertThat(a.body().get("yard").asText()).isEqualTo("HY");
        Answer t = get("/api/v1/consist/lay-up/tracks", Map.of("yard", "HY"));
        assertThat(t.status()).isEqualTo(200);
        assertThat(t.body().get(0).asText()).isEqualTo("T-10");
    }

    @Test
    void aTrainOutOfServiceCannotBeLaidUp() throws Exception {
        Answer a = get("/api/v1/consist/lay-up/trains/201", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("layUp").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).isNotBlank();
        Answer l = post("/api/v1/consist/lay-up", Map.of("yard", "HY", "track", "T-10", "consist", "201", "time", "2359"));
        assertThat(l.status()).isEqualTo(400);
        assertThat(called()).doesNotContain("VHLT012", "VHLT017", "VHLT018");
    }

    @Test
    void theReconfigureWindowSaysWhenTheTrainIsUnknown() throws Exception {
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("action", 2);
        steps.put("consist", "201");
        steps.put("splits", List.of());
        Answer a = post("/api/v1/consist/reconfigure/state", steps);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("reconfigure").asBoolean()).isFalse();
        // the old CON-MSG writes "CONSIST NOT EXIST", then overwrites it with the consist and "-CONSIST MUST BE > 99"
        assertThat(a.body().get("message").asText()).startsWith("201-CONSIST MUST BE");
        Answer o = get("/api/v1/consist/reconfigure/options", null);
        assertThat(o.status()).isEqualTo(200);
        assertThat(o.body().get("splitPositions").size()).isGreaterThan(0);
    }

    @Test
    void anUnknownSplitButtonIsRefused() throws Exception {
        Answer a = post("/api/v1/consist/reconfigure/state", Map.of("action", 2, "consist", "201", "splits", List.of(99)));
        assertThat(a.status()).isEqualTo(400);
    }
}
