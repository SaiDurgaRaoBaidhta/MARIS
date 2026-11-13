package com.bart.maris.middleware.api.yardreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.FakeLegacyBackend;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Yard maps (old screens VHIC01 / VHIC02 Berryessa, ...): the clean API.
 * Rules come from MRSYardTrackingReport/RUIFunctions.egl: VHIC01IS-INIT / -LOAD / -S1.. (placement and label),
 * GiveAColor (the car's condition on the update maps).
 */
class YardReportsApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> sentCrit = new AtomicReference<>();

    private static Map<String, Object> car(String car, String orientation, String track, int position, String status,
                                           String homeYard, String incident, int pmDue) {
        return Map.of("carNo", car, "conNo", "532", "vagenOrientation", orientation, "yrdSubLoc", track, "posnNo", position,
                "vehStat", status, "moveReasonCd", "M01", "homeYrd", homeYard, "trainIncdInd", incident, "hpmhrs", pmDue);
    }

    /** a VHYT80A row: Millbrae's record has no train incident or PM fields */
    private static Map<String, Object> millbraeCar(String car, String orientation, String track, int position, String homeYard) {
        return Map.of("carNo", car, "conNo", "532", "vagenOrientation", orientation, "yrdSubLoc", track, "posnNo", position,
                "vehStat", "GO", "moveReasonCd", "M01", "homeYrd", homeYard);
    }

    private void berryessa(int urc, List<Map<String, Object>> rows) {
        backend().on("VHIC01A", in -> {
            sentCrit.set(in.get("VHIC01A_CRIT"));
            return reply(urc, rows.size(), Map.of("VHIC01A_DATA", rows("dataRow", rows)));
        });
    }

    @Test
    void berryessaMapAsksForTheSvbYardAndShowsTheCarsOnTheirTracks() throws Exception {
        berryessa(0, List.of(
                car("1234", "<", "S-1", 1, "GO", "HY", "N", 0),
                car("2345", ">", "S-1", 2, "USABLE", "CY", "N", 0),
                car("3456", "<", "T-95", 1, "GO", "RY", "N", 0),        // the pseudo track is not on the map
                car("4567", "<", "TM3", 11, "GO", "DY", "N", 0)));      // the map has 10 spots per track
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHIC01A");
        assertThat(sentCrit.get().get("yrdTrkgYrd")).isEqualTo("SVB");
        assertThat(a.body().get("yard").asText()).isEqualTo("SVB");
        assertThat(a.body().get("cars").size()).isEqualTo(2);
        assertThat(a.body().at("/cars/0/track").asText()).isEqualTo("S-1");
        assertThat(a.body().at("/cars/0/position").asInt()).isEqualTo(1);
        assertThat(a.body().at("/cars/0/car").asText()).isEqualTo("1234");
        assertThat(a.body().at("/cars/0/consist").asText()).isEqualTo("532");
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("<1234");       // a left orientation comes first
        assertThat(a.body().at("/cars/1/label").asText()).isEqualTo("2345>");       // any other comes after the car
        assertThat(a.body().at("/cars/0/homeYard").asText()).isEqualTo("HY");
        assertThat(a.body().at("/cars/0/status").asText()).isEqualTo("GO");
        assertThat(a.body().at("/cars/0/moveReason").asText()).isEqualTo("M01");
    }

    @Test
    void theConditionFollowsTheOldColourRule() throws Exception {
        berryessa(0, List.of(
                car("1001", "<", "S-2", 1, "GO", "HY", "N", 0),
                car("1002", "<", "S-2", 2, "GO", "HY", "N", 1),
                car("1003", "<", "S-2", 3, "USABLE", "HY", "N", 0),
                car("1004", "<", "S-2", 4, "GO", "HY", "Y", 0),
                car("1005", "<", "S-2", 5, "NO GO", "HY", "N", 0)));
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);
        assertThat(a.body().findValuesAsText("condition")).containsExactly("go", "go-pm-due", "usable", "not-usable", "not-usable");
    }

    @Test
    void theInquiryColoursFollowTheHomeYard() throws Exception {
        berryessa(0, List.of(
                car("1001", "<", "S-1", 1, "GO", "HY", "N", 0),
                car("1002", "<", "S-1", 2, "GO", "RY", "N", 0),
                car("1003", "<", "S-1", 3, "GO", "CY", "N", 0),
                car("1004", "<", "S-1", 4, "GO", "DY", "N", 0),
                car("1005", "<", "S-1", 5, "GO", "MY", "N", 0)));
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);
        // VHIC01IS-COLOR
        assertThat(a.body().findValuesAsText("colour")).containsExactly("GREEN", "DARK ORANGE", "YELLOW", "RED", "BLACK");
        assertThat(a.body().findValuesAsText("background")).containsExactly("GREY55", "LIGHT YELLOW", "GREY55", "LIGHT YELLOW", "GREY80");
    }

    @Test
    void aLaterCarOnTheSameSpotReplacesTheEarlierOne() throws Exception {
        berryessa(0, List.of(car("1001", "<", "TM4", 3, "GO", "HY", "N", 0), car("1002", "<", "TM4", 3, "GO", "HY", "N", 0)));
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);
        assertThat(a.body().findValuesAsText("car")).containsExactly("1002");
    }

    @Test
    void noRowsIsAnEmptyMap() throws Exception {
        berryessa(100, List.of());
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("cars").size()).isZero();
    }

    @Test
    void aBackendErrorIsTheMarisMessage() throws Exception {
        backend().on("VHIC01A", in -> new FakeLegacyBackend.Reply(
                Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "REC NOT FND", "umsgins", List.of("YARD", "", ""))), "00000000"));
        Answer a = get("/api/v1/yard-reports/maps/berryessa", null);
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("detail").asText()).isEqualTo("YARD Is Not Found");
    }

    @Test
    void millbraeMapAsksForTheMbxYardAndHasLongerTracks() throws Exception {
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>();
        List<Map<String, Object>> rows = List.of(
                millbraeCar("2001", "<", "TM5", 18, "RY"),          // TM5 has 18 spots
                millbraeCar("2002", ">", "TM5", 19, "HY"),
                millbraeCar("2003", "<", "TM6", 18, "HY"),          // TM6 has 17
                millbraeCar("2004", ">", "W-1", 3, "CY"));
        backend().on("VHYT80A", in -> {
            crit.set(in.get("VHYT80A_CRIT"));
            return reply(0, rows.size(), Map.of("VHYT80A_DATA", rows("dataRow", rows)));
        });
        Answer a = get("/api/v1/yard-reports/maps/millbrae", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT80A");
        assertThat(crit.get().get("yrdTrkgYrd")).isEqualTo("MBX");
        assertThat(a.body().findValuesAsText("car")).containsExactly("2001", "2004");
        assertThat(a.body().at("/cars/0/colour").asText()).isEqualTo("DARK ORANGE");   // VHYT80IS-COLOR
        assertThat(a.body().at("/cars/0/background").asText()).isEqualTo("LIGHT YELLOW");
        assertThat(a.body().at("/cars/0").has("condition")).isFalse();                  // VHYT81I colours no car (nulls are left out)
    }

    /** a row of the yard maps that carry the software version (VHYTX5A, VHYT70A, VHYT90A) */
    private static Map<String, Object> softwareCar(String car, String track, int position, String softver) {
        return Map.of("carNo", car, "conNo", "532", "vagenOrientation", "<", "yrdSubLoc", track, "posnNo", position,
                "vehStat", "GO", "homeYrd", "HY", "trainIncdInd", "N", "hpmhrs", 0, "softver", softver);
    }

    /** asks the map with three cars (software version "", "V2", "$V3"); returns the labels shown */
    private List<String> labels(String map, String program, String crit, String data, String yard, String track) throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        List<Map<String, Object>> rows = List.of(softwareCar("3001", track, 1, ""), softwareCar("3002", track, 2, "V2"),
                softwareCar("3003", track, 3, "$V3"));
        backend().on(program, in -> {
            sent.set(in.get(crit));
            return reply(0, rows.size(), Map.of(data, rows("dataRow", rows)));
        });
        Answer a = get("/api/v1/yard-reports/maps/" + map, null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly(program);
        assertThat(sent.get().get("yrdTrkgYrd")).isEqualTo(yard);
        assertThat(a.body().findValuesAsText("condition")).containsOnly("go");
        return a.body().findValuesAsText("label");
    }

    @Test
    void dalyCityYardMarksASoftwareVersionWithADollar() throws Exception {
        // getButton: "$" when SOFTVER has a "$"
        assertThat(labels("daly-city-yard", "VHYTX5A", "VHYT105A_CRIT", "VHYT105A_DATA", "DY", "ST-05"))
                .containsExactly("<3001", "<3002", "$<3003");
    }

    @Test
    void concordYardMarksAnySoftwareVersionWithADollar() throws Exception {
        // VHYT70IS-ST.. / VHYT71IS-ST..: "$" when SOFTVER is not blank
        assertThat(labels("concord-yard", "VHYT70A", "VHYT70A_CRIT", "VHYT70A_DATA", "CY", "ST-10"))
                .containsExactly("<3001", "$<3002", "$<3003");
    }

    @Test
    void richmondYardMarksASoftwareVersionWithADollar() throws Exception {
        assertThat(labels("richmond-yard", "VHYT90A", "VHYT90A_CRIT", "VHYT90A_DATA", "RY", "ST-09"))
                .containsExactly("<3001", "<3002", "$<3003");
    }

    @Test
    void systemWideMapTellsWhichLocationsHaveCars() throws Exception {
        AtomicReference<Map<String, Object>> crit = new AtomicReference<>();
        List<Map<String, Object>> rows = List.of(millbraeCar("4001", "<", "BAYFAIR", 1, "HY"), millbraeCar("4002", ">", "BAYFAIR", 2, "HY"),
                millbraeCar("4003", "<", "OKS", 1, "CY"), millbraeCar("4004", "<", "T-95", 1, "CY"));
        backend().on("VHSW01A", in -> {
            crit.set(in.get("VHSW01A_CRIT"));
            return reply(0, rows.size(), Map.of("VHSW01A_DATA", rows("dataRow", rows)));
        });
        Answer a = get("/api/v1/yard-reports/system-wide", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHSW01A");
        assertThat(crit.get().get("yrdTrkgYrd")).isEqualTo("SW");
        assertThat(a.body().get("yard").asText()).isEqualTo("SW");
        // VHSW01IS-LOAD: the four places the old map marks, in its order; T-95 is not one of them
        assertThat(a.body().findValuesAsText("location")).containsExactly("MACART", "BAYFAIR", "M90", "OKS");
        assertThat(a.body().at("/locations/0/cars").size()).isZero();
        assertThat(a.body().at("/locations/1/cars").toString()).isEqualTo("[\"4001\",\"4002\"]");
        assertThat(a.body().at("/locations/3/cars").toString()).isEqualTo("[\"4003\"]");
    }

    @Test
    void systemWideMapWithNoCarsHasEmptyLocations() throws Exception {
        backend().on("VHSW01A", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/yard-reports/system-wide", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("locations").size()).isEqualTo(4);
        assertThat(a.body().findValues("cars")).allMatch(c -> c.isEmpty());
    }

    @Test
    void anUnknownMapIsRefusedAndCallsNothing() throws Exception {
        Answer a = get("/api/v1/yard-reports/maps/atlantis", null);
        assertThat(a.status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }

    @Test
    void theApiNeedsASignedOnUser() throws Exception {
        send("POST", "/api/v1/auth/signoff", null);
        assertThat(get("/api/v1/yard-reports/maps/berryessa", null).status()).isEqualTo(401);
    }
}
