package com.bart.maris.middleware.api.prevmaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PM scheduling (old VHPMS01I) over HTTP: the lookups, the checks with the old messages, and what the update sends. */
class PrevMaintScheduleApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehiclePrevMaint";
    private final AtomicReference<Map<String, Map<String, Object>>> update = new AtomicReference<>();

    private void car(String current, String next, String status) {
        backend().on("VHPMSA2", in -> reply(0, 1, Map.of("VHPMSA2_DATA", Map.of("carNo", "1101", "carType", "A2", "homeYrd", "HY"))));
        backend().on("VHPMSA1", in -> reply(0, 1, Map.of("VHPMSA1_DATA", Map.of("ciDesc", "PM INSPECTION"))));
        backend().on("VHPMSA3", in -> reply(0, 1, Map.of("VHPMSA3_DATA", Map.of("carNo", "1101", "carType", "A2", "dispVehStat", Map.of("vehStat", status),
                "currSchdPmNo", current, "nextSchdPmNo", next))));
    }

    @BeforeEach
    void backendAnswers() {
        update.set(null);
        backend().on("VHXREVA", in -> reply(0, 1, Map.of("VHXREVA_DATA", Map.of("revenueDate", "11-13-2026"))));
        backend().on("VHPMS", in -> { update.set(in); return reply(0, 1, Map.of("VHPMS_DATA", Map.of("incdNo", "29990099"))); });
    }

    @Test
    void optionsAreTheScreensChoicesAndPmTypes() throws Exception {
        Answer a = get("/api/v1/preventive-maintenance/schedule/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/options/0/option").asInt()).isEqualTo(1);
        assertThat(a.body().at("/options/1/label").asText()).isEqualTo(TEXTS.items("VHPMS01I", "gpbPMOption").get(1).strip());
        assertThat(a.body().get("pmTypes").toString()).isEqualTo("[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"A\",\"B\",\"C\",\"D\"]");
    }

    @Test
    void descriptionNormalisesTheTypeAndAsksWithTheCarsTypeAndYard() throws Exception {
        car("", "3", "GO");
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>();
        backend().on("VHPMSA1", in -> { crit.set(in.get("VHPMSA1_CRIT")); return reply(0, 1, Map.of("VHPMSA1_DATA", Map.of("ciDesc", "PM 3 INSPECTION"))); });
        Answer a = get("/api/v1/preventive-maintenance/schedule/description", Map.of("car", "1101", "pmType", "03"));
        assertThat(a.body().get("pmType").asText()).isEqualTo("3");
        assertThat(a.body().get("description").asText()).isEqualTo("PM 3 INSPECTION");
        assertThat(crit.get()).containsEntry("carType", "A2").containsEntry("homeYrd", "HY").containsEntry("pmType", "3")
                .containsEntry("pmMethod", TEXTS.assignedAll("VHPMS01IS-PM", PKG, "PM-METHOD").get(1));
    }

    @Test
    void scheduleSendsTheUpdateWithUserGroupTerminalAndRevenueDate() throws Exception {
        car("", "3", "GO");
        Answer a = post("/api/v1/preventive-maintenance/schedule", Map.of("car", "1101", "pmType", "3", "option", 1));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("incident").asText()).isEqualTo("29990099");
        assertThat(a.body().get("message").asText()).startsWith(TEXTS.assignedAll("VHPMS01IS-SAVE", PKG, "DISP-MSG-1").get(0)).contains("29990099");
        assertThat(update.get().get("VHPMS_CRIT")).containsEntry("carNo", "1101").containsEntry("pmType", "3").containsEntry("pmOption", "SCHEDULE");
        assertThat(update.get().get("VHPMS_DATA")).containsEntry("userGrp", "BRIGHTCO").containsEntry("termLastUpd", "MARIS-UI")
                .containsEntry("dateIncdOccrRev", "11-13-2026").containsEntry("nextSchdPmNo", "3").containsEntry("pmDescription", "PM INSPECTION");
    }

    @Test
    void checksAnswer400WithTheOldMessagesAndUpdateNothing() throws Exception {
        car("2", "3", "GO");
        Answer bad = post("/api/v1/preventive-maintenance/schedule", Map.of("car", "11A1", "pmType", "3", "option", 1));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().toString()).contains(TEXTS.assigned("VHPMS01IS-SAVE", PKG, "DISP-ERR-MSG", "Car No"));
        Answer scheduled = post("/api/v1/preventive-maintenance/schedule", Map.of("car", "1101", "pmType", "3", "option", 1));
        assertThat(scheduled.status()).isEqualTo(400);
        assertThat(scheduled.body().toString()).contains("Car already Scheduled with a PM type 2");
        car("", "3", "PM");
        Answer inPm = post("/api/v1/preventive-maintenance/schedule", Map.of("car", "1101", "pmType", "3", "option", 1));
        assertThat(inPm.body().toString()).contains(TEXTS.assigned("VHPMS01IS-SAVE", PKG, "DISP-ERR-MSG", "already in PM"));
        assertThat(update.get()).isNull();
    }

    @Test
    void anUpdateTheBackendRefusesIs422() throws Exception {
        car("A", "C", "GO");
        backend().on("VHPMS", in -> reply(8, 0, Map.of("VHPMS_DATA", Map.of("noRecFound", ""))));
        Answer a = post("/api/v1/preventive-maintenance/schedule", Map.of("car", "1101", "pmType", "C", "option", 2));
        assertThat(a.status()).isEqualTo(422);
    }
}
