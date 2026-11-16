package com.bart.maris.middleware.api.vehiclemaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Vehicle table (VHVM02I, VHVM03I) and incident status history (VHVM06I) over HTTP, signed on as an ordinary group. */
class VehicleMaintCarApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private final AtomicReference<Map<String, Map<String, Object>>> sent = new AtomicReference<>();

    private void carBackend() {
        backend().on("VHVM1A5", in -> reply(0, 1, Map.of("VHVM1A5_CRIT", VehicleMaintCarParityTest.car(), "VHVM1A5_DATA", VehicleMaintCarParityTest.car())));
        backend().on("VHVM1A4", in -> { sent.set(in); return reply(0, 0, Map.of()); });
    }

    @Test
    void optionsComeFromTheProgramsAndTheScreens() throws Exception {
        backend().on("VHVM1A6", in -> reply(0, 1, Map.of("VHVM1A6_DATA", rows("dataRow", List.of(Map.of("cameraCd", "H", "cameraCdDesc", "HIGH DEF"))))));
        backend().on("VHVM1A7", in -> reply(0, 1, Map.of("VHVM1A7_DATA", Map.of("vagenCount", 1, "dataRow", List.of(Map.of("carType", "C2",
                "lftOrientationCd", "<", "rgtOrientationCd", ">"))))));
        backend().on("VHCY1A1", in -> reply(100, 0, Map.of()));
        backend().on("VHVM1A9", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/vehicle-maintenance/cars/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/cameras/0/code").asText()).isEqualTo("H");
        assertThat(a.body().at("/orientations/C2").toString()).isEqualTo("[\"<\",\">\"]");
        assertThat(a.body().get("statuses").toString()).isEqualTo(json.writeValueAsString(TEXTS.items("VHVM03I", "Drop-down_List9")));
        assertThat(a.body().at("/addDefaults/vehStat").asText()).isEqualTo(TEXTS.assignedAll("VHVM02IS-CLEAR", "MRSVehicleMaintenance", "VEH-STAT").get(0));
    }

    @Test
    void addingACarChecksTheTypeAndTheRequiredFields() throws Exception {
        Answer missing = post("/api/v1/vehicle-maintenance/cars", Map.of("carNo", "1999"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM02I", "MISSING-DATA"));
        assertThat(post("/api/v1/vehicle-maintenance/cars", Map.of("carNo", "1999", "carType", "Z9")).body().get("detail").asText())
                .isEqualTo(TEXTS.triggeredPrompt("VHVM02I", "BAD-TYPE"));
        assertThat(post("/api/v1/vehicle-maintenance/cars", Map.of("carNo", "1999", "conNo", "201")).status()).isEqualTo(400);   // not on the add screen
        assertThat(called()).doesNotContain("VHVM1A3");

        backend().on("VHVM1AA", in -> reply(0, 1, Map.of("VHVM1AA_DATA", Map.of("hrsToNextPm", 250, "cntDaysNxtWash", 14, "cntDaysNxtPa", 30))));
        backend().on("VHVM1A3", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Map<String, Object> car = new LinkedHashMap<>(Map.of("carNo", "1999", "carType", "C2", "vehStat", "STOR", "homeYrd", "CY", "cameraCd", "H",
                "vagenOrientation", "<", "atoPresentInd", "N", "dateBatteryRepl", "2026-06-01", "batteryReplLoc", "HY", "batteryReplInd", "N"));
        Answer ok = post("/api/v1/vehicle-maintenance/cars", car);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM02I", "GOOD"));
        assertThat(sent.get().get("VHVM1A3_DATA")).containsEntry("hrsToNextPm", 250L).containsEntry("termLastUpd", "MARIS-UI").containsEntry("carNo", "1999");

        backend().on("VHVM1A3", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8)), "00000000"));
        Answer bad = post("/api/v1/vehicle-maintenance/cars", car);
        assertThat(bad.status()).isEqualTo(422);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM02I", "BAD"));
    }

    @Test
    void anOrdinaryGroupChangesOnlyTheOpenFields() throws Exception {
        carBackend();
        Answer car = get("/api/v1/vehicle-maintenance/cars/1101", null);
        assertThat(car.status()).isEqualTo(200);
        assertThat(car.body().at("/fields/vehStat").asText()).isEqualTo("GO");
        assertThat(car.body().get("editable").toString()).contains("homeYrd").doesNotContain("vehStat");
        assertThat(post("/api/v1/vehicle-maintenance/cars/1101/changes", Map.of("vehStat", "PM")).status()).isEqualTo(400);
        Answer none = post("/api/v1/vehicle-maintenance/cars/1101/changes", Map.of("homeYrd", "CY"));
        assertThat(none.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM03I", "NO-UPDATE"));
        Answer ok = post("/api/v1/vehicle-maintenance/cars/1101/changes", Map.of("homeYrd", "DY"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM03I", "DONE"));
        assertThat(sent.get().get("VHVM1A4_DATA")).containsEntry("homeYardSwitch", "Y");
        assertThat(sent.get().get("VHVM1A5_DATA")).containsEntry("homeYrd", "DY").containsEntry("termHomeYrdUpd", "MARIS-UI");

        backend().on("VHVM1A5", in -> reply(100, 0, Map.of()));
        assertThat(get("/api/v1/vehicle-maintenance/cars/9999", null).body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM03I", "BAD-CAR"));
    }

    @Test
    void statusHistoryAddChangeDelete() throws Exception {
        String day = LocalDate.now().minusDays(2).toString();
        backend().on("VHVM6A1", in -> reply(0, 2, Map.of("VHVM6A1_DATA", rows("dataRow", List.of(
                VehicleMaintStatusHistoryParityTest.row("TS1", "", "OPN"), VehicleMaintStatusHistoryParityTest.row("TS2", "OPN", "WIP"))))));
        Answer list = get("/api/v1/vehicle-maintenance/status-history/29990010", null);
        assertThat(list.body().size()).isEqualTo(2);
        assertThat(list.body().at("/1/newStatus").asText()).isEqualTo("WIP");

        Map<String, Object> change = new LinkedHashMap<>(Map.of("date", LocalDate.now().plusDays(1).toString(), "time", "10:00:00", "newStatus", "CLS",
                "homeYard", "CY", "yard", "CY"));
        Answer bad = post("/api/v1/vehicle-maintenance/status-history/29990010", change);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM06I", "BAD-DATE-MSG") + "\n"
                + TEXTS.triggeredPrompt("VHVM06I", "BLANK-SUB-LOC"));
        assertThat(called()).doesNotContain("VHVM6A2");

        change.put("date", day);
        change.put("subLocation", "T12");
        backend().on("VHVM6A2", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Answer added = post("/api/v1/vehicle-maintenance/status-history/29990010", change);
        assertThat(added.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM06I", "ADDED"));
        assertThat(sent.get().get("VHVM6A3_DATA")).containsEntry("dateStatChg", day).containsEntry("userGrp", "BRIGHTCO")
                .containsEntry("marisFunc", TEXTS.assigned("VHVM06IS-MOD-DATA", "MRSVehicleMaintenance", "MARIS-FUNC", null));

        backend().on("VHVM6A2", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8, "umsgins", List.of("-803"))), "00000000"));
        Answer dup = post("/api/v1/vehicle-maintenance/status-history/29990010", change);
        assertThat(dup.status()).isEqualTo(422);
        assertThat(dup.body().get("detail").asText()).startsWith(TEXTS.triggeredPrompt("VHVM06I", "DUPLICATE-KEY"));

        backend().on("VHVM6A3", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        change.put("timestamp", "TS2");
        change.put("previousStatus", "OPN");
        Answer upd = post("/api/v1/vehicle-maintenance/status-history/29990010/changes", change);
        assertThat(upd.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM06I", "UPDATED"));
        assertThat(sent.get().get("VHVM6A3_CRIT")).containsEntry("incdNo", "29990010").containsEntry("tsStatChg", "TS2");

        backend().on("VHVM6A4", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Answer del = post("/api/v1/vehicle-maintenance/status-history/29990010/delete", Map.of("timestamp", "TS1"));
        assertThat(del.body().get("message").asText()).isEqualTo(TEXTS.triggeredPrompt("VHVM06I", "DELETED"));
        assertThat(sent.get().get("VHVM6A3_DATA")).containsEntry("tsStatChg", "TS1");
    }
}
