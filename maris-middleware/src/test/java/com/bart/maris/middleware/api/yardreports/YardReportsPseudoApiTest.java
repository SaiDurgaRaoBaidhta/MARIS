package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardreports.YardReportsLayout;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Configure pseudo trains (old screen VHYT53I): the clean API. Rules from MRSYardTrackingReport/RUIFunctions.egl
 * VHYT53IS-INIT / -INIT2 / -GET-DATA / -GET-CARS / -MOVE-DATA (reading), -INIT3 / -INIT4 (a track's cars),
 * -CREATE-C / -VALID.. (VHYT53F), -CREATE-P / -VALID2.. (VHYT53G), -MOVE-CAR (VHYT53H), -RESET (VHYT53K).
 */
class YardReportsPseudoApiTest extends ApiTestBase {
    private static final String PKG = "MRSYardTrackingReport";
    private static final String BASE = "/api/v1/yard-reports/pseudo-config/CY";

    private static String text(String function, String field, int i) {
        return YardReportsLayout.assignedTexts(Parity.bundle(), function, PKG, field).get(i);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    private static Map<String, Object> carX(String left, String car, String right) {
        return Map.of("lftOr", left, "carNo", car, "rgtOr", right);
    }

    private void readBackend() {
        backend().on("VHYT53D", in -> reply(0, 2, Map.of("VHYT53D_DATA", rows("dataRow", List.of(
                Map.of("yrdSubLoc", "ST-07", "yrdSubLocDesc", "STORAGE 7"), Map.of("yrdSubLoc", "T-95", "yrdSubLocDesc", "PSEUDO"))))));
        backend().on("VHYT53B", in -> reply(0, 2, Map.of("VHYT53B_DATA", rows("dataRow", List.of(
                Map.of("conNo", "532", "dashNo", 1, "maxPosnNo", 2, "yrdSubLoc", "T-95", "conInd", "P"),
                Map.of("conNo", "446", "dashNo", 0, "maxPosnNo", 1, "yrdSubLoc", "T-95", "conInd", "C"))))));
        backend().on("VHYT53C", in -> reply(0, 3, Map.of("VHYT53C_DATA", rows("dataRow", List.of(
                Map.of("conNo", "532", "carNoX", carX("<", "1234", ""), "vehStat", "GO", "carType", "A1", "atoPresentInd", "Y", "trainIncdInd", "N", "homeYrd", "CY"),
                Map.of("conNo", "532", "carNoX", carX("", "2345", ">"), "vehStat", "NO GO", "carType", "B2", "atoPresentInd", "N", "trainIncdInd", "N", "homeYrd", "CY"),
                Map.of("conNo", "446", "carNoX", carX("<", "3456", ""), "vehStat", "USABLE", "carType", "C1", "atoPresentInd", "Y", "trainIncdInd", "Y", "homeYrd", "HY"))))));
    }

    @Test
    void theYardsTracksConsistsAndTheirCars() throws Exception {
        readBackend();
        Answer a = get(BASE, null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT53D", "VHYT53B", "VHYT53C");
        // the track list ends with ALL, as VHYT53IS-INIT2 adds it
        assertThat(a.body().get("tracks").findValuesAsText("track")).containsExactly("ST-07", "T-95", text("VHYT53IS-INIT2", "YRD-SUB-LOC", 0));
        assertThat(a.body().get("consists").findValuesAsText("display")).containsExactly("532-01", "446-X");
        assertThat(a.body().at("/consists/0/cars").findValuesAsText("car")).containsExactly("1234", "2345");
        assertThat(a.body().at("/consists/0/cars/0/leftEnd").asText()).isEqualTo("<");
        assertThat(a.body().at("/consists/0/cars/1/rightEnd").asText()).isEqualTo(">");
        assertThat(a.body().at("/consists/0/cars/1/serviceable").asBoolean()).isFalse();             // NO GO: red
        assertThat(a.body().at("/consists/1/cars/0/incident").asBoolean()).isTrue();
        assertThat(a.body().at("/consists/1/resettable").asBoolean()).isTrue();                       // CON-IND C or D
        assertThat(a.body().at("/consists/0/resettable").asBoolean()).isFalse();
    }

    @Test
    void consistsAndCarsThatDoNotMatchAreTheOldDatabaseChangedMessage() throws Exception {
        readBackend();
        backend().on("VHYT53C", in -> reply(0, 1, Map.of("VHYT53C_DATA", rows("dataRow", List.of(
                Map.of("conNo", "999", "carNoX", carX("<", "1234", ""), "vehStat", "GO"))))));
        Answer a = get(BASE, null);
        assertThat(a.body().get("message").asText()).isEqualTo(text("VHYT53IS-MOVE-DATA", "FINAL-MESSAGE", 0).replaceAll("\\s+", " ").strip());
        assertThat(a.body().get("consists").size()).isZero();
    }

    @Test
    void aYardWithoutConsistsSaysSo() throws Exception {
        readBackend();
        backend().on("VHYT53B", in -> reply(100, 0, Map.of()));
        Answer a = get(BASE, null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT53D", "VHYT53B");
        assertThat(a.body().get("message").asText()).isEqualTo(("CY " + text("VHYT53IS-GET-DATA", "FINAL-MSG-2", 3)).replaceAll("\\s+", " ").strip());
    }

    @Test
    void aTracksCarsForAddingOrInserting() throws Exception {
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>();
        Map<String, Object> rows = Map.of("VHYT53E_DATA", rows("dataRow", List.of(Map.of("carAndOr", carX("", "4567", ">"), "vehStat", "GO", "carType", "B1", "atoPresentInd", "N", "trainIncdInd", "N"))));
        backend().on("VHYT53E", in -> { crit.set(in.get("VHYT53E_CRIT")); return reply(0, 1, rows); });
        backend().on("VHYT53M", in -> { crit.set(in.get("VHYT53M_CRIT")); return reply(0, 1, Map.of("VHYT53M_DATA", rows.get("VHYT53E_DATA"))); });

        Answer a = get(BASE + "/cars", Map.of("track", "ST-07"));
        assertThat(called()).containsExactly("VHYT53E");
        assertThat(crit.get()).containsEntry("yrdTrkgYrd", "CY").containsEntry("frYrdSubLoc", "ST-07").containsEntry("toYrdSubLoc", "ST-07");
        assertThat(a.body().at("/0/car").asText()).isEqualTo("4567");
        assertThat(a.body().at("/0/rightEnd").asText()).isEqualTo(">");

        get(BASE + "/cars", Map.of("track", "ALL", "list", "insert"));                          // the insert list: VHYT53M
        assertThat(called()).endsWith("VHYT53M");
        assertThat(crit.get()).containsEntry("frYrdSubLoc", "").containsEntry("toYrdSubLoc", "9999999999");
    }

    // ---- create ------------------------------------------------------------------------------------------------

    private static Map<String, Object> car(String left, String car, String right, String type, String ato, String incident, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("leftEnd", left); m.put("car", car); m.put("rightEnd", right); m.put("type", type); m.put("ato", ato.equals("Y"));
        m.put("incident", incident.equals("Y")); m.put("status", status);
        return m;
    }

    private static final List<Map<String, Object>> GOOD = List.of(car("<", "1001", "", "A1", "Y", "N", "GO"),
            car("", "1002", "", "B1", "N", "N", "USABLE"), car("", "1003", ">", "C1", "Y", "N", "GO"));

    private final AtomicReference<Map<String, Map<String, Object>>> created = new AtomicReference<>();

    private void createBackend(int lUrc, String conErr) {
        backend().on("VHYT53L", in -> {
            Map<String, Object> data = new HashMap<>(in.get("VHYT53L_DATA"));
            data.put("conErr", conErr);
            data.put("yrdTrkgYrd", "RY");
            return reply(lUrc, 0, Map.of("VHYT53L_DATA", data));
        });
        backend().on("VHYT53F", in -> { created.set(in); return reply(0, 0, Map.of()); });
        backend().on("VHYT53G", in -> { created.set(in); return reply(0, 0, Map.of()); });
    }

    private Answer create(String consist, boolean partial, List<Map<String, Object>> cars) throws Exception {
        return post(BASE + "/consists", Map.of("consist", consist, "partial", partial, "cars", cars));
    }

    @Test
    void creatingAConsistChecksItThenWritesTheOldRecord() throws Exception {
        createBackend(100, "N");
        Answer a = create("123", false, GOOD);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT53L", "VHYT53F");
        Map<String, Object> crit = created.get().get("VHYT53F_CRIT"), data = created.get().get("VHYT53F_DATA");
        assertThat(crit.get("yrdTrkgYrd")).isEqualTo("CY");
        @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) crit.get("carNoRow");
        @SuppressWarnings("unchecked") List<Map<String, Object>> cars = (List<Map<String, Object>>) row.get("carNoN");
        assertThat(cars.get(0)).containsEntry("lftOr", "<").containsEntry("carNo", "1001");
        assertThat(cars.get(2)).containsEntry("carNo", "1003").containsEntry("rgtOr", ">");
        assertThat(list(row.get("colorN"))).startsWith("BLACK", "BLACK", "BLACK");
        assertThat(list(row.get("carTypeN"))).startsWith("A1", "B1", "C1");
        assertThat(data).containsEntry("newConNo", "123").containsEntry("userGroup", "BRIGHTCO").containsEntry("termLastUpd", "MARIS-UI");
        assertThat(((Number) data.get("numOfCar")).intValue()).isEqualTo(3);
        assertThat(a.body().get("message").asText()).isEqualTo(("123 " + text("VHYT53IS-CREATE-C", "FINAL-MSG-2", 0)).replaceAll("\\s+", " ").strip());
    }

    @Test
    void aPartialConsistIsWrittenWithVhyt53g() throws Exception {
        createBackend(100, "N");
        Answer a = create("124", true, List.of(car("<", "1001", "", "A1", "Y", "N", "NO GO")));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT53L", "VHYT53G");
        @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) created.get().get("VHYT53G_CRIT").get("carNoRow");
        assertThat(list(row.get("colorN"))).startsWith("RED");                                   // not GO / USABLE
    }

    @Test
    void theOldChecksRefuseBadConsistsWithTheOldMessagesAndWriteNothing() throws Exception {
        createBackend(100, "N");
        Map<String, String> refused = Map.of(
                "", text("VHYT53IS-CREATE-C", "FINAL-MESSAGE", 0),
                "12A", "12A -Con No is not numeric",
                "099", "099 -Con No out of range");
        for (var e : refused.entrySet()) {
            Answer a = create(e.getKey(), false, GOOD);
            assertThat(a.status()).as(e.getKey()).isEqualTo(400);
            assertThat(a.body().get("detail").asText()).as(e.getKey()).isEqualTo(e.getValue().replaceAll("\\s+", " ").strip());
        }
        Answer one = create("123", false, List.of(GOOD.get(0)));
        assertThat(one.body().get("detail").asText()).isEqualTo(text("VHYT53IS-VALID", "FINAL-MESSAGE", 1).replaceAll("\\s+", " ").strip());
        assertThat(called()).doesNotContain("VHYT53L", "VHYT53F");
    }

    @Test
    void eachBadCarIsListedAsTheOldScreenDoes() throws Exception {
        createBackend(100, "N");
        List<Map<String, Object>> cars = new ArrayList<>(List.of(
                car("<", "2001", "", "B1", "N", "N", "GO"),          // end car of a middle type
                car("", "2002", "", "A1", "N", "N", "GO"),           // middle car of an end type
                car("", "2003", "", "B1", "N", "Y", "GO"),           // incident
                car("", "2004", "", "B1", "N", "N", "NO GO"),        // not serviceable
                car("", "2005", "", "C1", "N", "N", "GO")));         // C1 end car without ATO
        Answer a = create("123", false, cars);
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(
                "2001-Invalid type for end car, 2002-Invalid car type, 2003-Train Incident, 2004-Not servicable, 2005-Invalid type for end car");
        assertThat(called()).containsExactly("VHYT53L");
    }

    @Test
    void anEndCarNeedsAnOrientation() throws Exception {
        createBackend(100, "N");
        Answer a = create("123", false, List.of(car("", "1001", "", "A1", "Y", "N", "GO"), GOOD.get(2)));
        assertThat(a.body().get("detail").asText()).isEqualTo("1001-Invalid orientation");
    }

    @Test
    void aTrainThatAlreadyExistsIsRefused() throws Exception {
        createBackend(0, "Y");
        Answer a = create("123", false, GOOD);
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo("Train 123 already exists in RY yard. Please enter another train number.");
        assertThat(called()).containsExactly("VHYT53L");
    }

    // ---- move a car out, reset -------------------------------------------------------------------------------

    @Test
    void movingACarToATrackWritesVhyt53h() throws Exception {
        AtomicReference<Map<String, Map<String, Object>>> sent = new AtomicReference<>();
        backend().on("VHYT53H", in -> { sent.set(in); return reply(0, 0, Map.of("VHYT53H_DATA", Map.of("moveSameTrk", "N"))); });
        Answer a = post(BASE + "/car-moves", Map.of("car", "1002", "track", "ST-07"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().get("VHYT53H_CRIT")).containsEntry("yrdSubLoc", "ST-07").containsEntry("yrdTrkgYrd", "CY");
        assertThat(sent.get().get("VHYT53H_DATA")).containsEntry("userGroup", "BRIGHTCO").containsEntry("termLastUpd", "MARIS-UI");
        assertThat(a.body().get("moved").asBoolean()).isTrue();
        assertThat(a.body().get("message").asText()).isEqualTo(("1002" + text("VHYT53IS-MOVE-CAR", "FINAL-MSG-2", 1)).replaceAll("\\s+", " ").strip());
    }

    @Test
    void aFullTrackIsTheOldMessage() throws Exception {
        backend().on("VHYT53H", in -> new FakeLegacyBackend.Reply(Map.of("XDCONTROL", Map.of("urc", 8), "VHYT53H_DATA", Map.of("trkIsFull", "Y")), "00000000"));
        Answer a = post(BASE + "/car-moves", Map.of("car", "1002", "track", "ST-07"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("moved").asBoolean()).isFalse();
        assertThat(a.body().get("message").asText()).isEqualTo((text("VHYT53IS-MOVE-CAR", "TEMP-MSG-1", 0) + "ST-07").replaceAll("\\s+", " ").strip());
    }

    @Test
    void movingNeedsACarAndATrack() throws Exception {
        Answer a = post(BASE + "/car-moves", Map.of("car", "1002", "track", ""));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(text("VHYT53IS-MOVE-CAR", "FINAL-MESSAGE", 1).replaceAll("\\s+", " ").strip());
        assertThat(called()).isEmpty();
    }

    @Test
    void resettingAConsistWritesVhyt53k() throws Exception {
        AtomicReference<Map<String, Map<String, Object>>> sent = new AtomicReference<>();
        backend().on("VHYT53K", in -> { sent.set(in); return reply(0, 0, Map.of()); });
        Answer a = post(BASE + "/resets", Map.of("consist", "446", "track", "T-95", "indicator", "C"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().get("VHYT53K_CRIT")).containsEntry("conNo", "446").containsEntry("yrdSubLoc", "T-95")
                .containsEntry("yrdTrkgYrd", "CY").containsEntry("conInd", "C");
        assertThat(sent.get().get("VHYT53K_DATA")).containsEntry("userGroup", "BRIGHTCO");
    }

    @Test
    void onlyAConsistMarkedCOrDCanBeReset() throws Exception {
        Answer a = post(BASE + "/resets", Map.of("consist", "532", "track", "T-95", "indicator", "P"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void aBackendErrorOnCreateIsTheMarisMessage() throws Exception {
        createBackend(100, "N");
        backend().on("VHYT53F", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("PSEUDO CONSIST", "", ""))), "00000000"));
        Answer a = create("123", false, GOOD);
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("PSEUDO CONSIST Is Not Found");
    }

    @Test
    void aBackendErrorOnMoveThatIsNotAFullTrackIsTheMarisMessage() throws Exception {
        backend().on("VHYT53H", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("CAR", "", ""))), "00000000"));
        Answer a = post(BASE + "/car-moves", Map.of("car", "1002", "track", "ST-07"));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("CAR Is Not Found");
    }
}
