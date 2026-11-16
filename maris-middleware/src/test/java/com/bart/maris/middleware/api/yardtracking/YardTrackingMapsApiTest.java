package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Yard tracking maps (old screens VHYT40I / VHYT41I Dublin-Pleasanton, VHYT50I / VHYT51I Pittsburg-Bay Point, VHYT04I
 * Hayward shop): the clean API. Rules from MRSYardTracking/RUIFunctions.egl: the maps' INIT / LOAD / per-track functions,
 * their COLOR groups, dy100Color, GiveAColor, VHYT41IS-MAINT / VHYTIS-ACCES-MAINT.
 */
class YardTrackingMapsApiTest extends ApiTestBase {
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    private static Map<String, Object> car(String car, String orientation, String track, int position, String status, String homeYard,
                                           String incident, int pmDue) {
        Map<String, Object> m = new HashMap<>(Map.of("carNo", car, "conNo", "532", "vagenOrientation", orientation, "yrdSubLoc", track,
                "posnNo", position, "vehStat", status, "moveReasonCd", "M01", "homeYrd", homeYard, "trainIncdInd", incident, "hpmhrs", pmDue));
        return m;
    }

    private void program(String program, String crit, String data, List<Map<String, Object>> rows) {
        backend().on(program, in -> {
            sent.set(in.get(crit));
            return reply(0, rows.size(), Map.of(data, rows("dataRow", rows)));
        });
    }

    @Test
    void dublinAsksForTheDpxYardAndColoursByHomeYard() throws Exception {
        program("VHYT40A", "VHYT40A_CRIT", "VHYT40A_DATA", List.of(
                car("1234", "<", "L1", 1, "GO", "HY", "N", 0),
                car("2345", ">", "L1", 2, "USABLE", "CY", "N", 0),
                car("3456", "<", "T-95", 1, "GO", "RY", "N", 0),          // the pseudo track is not drawn on the map
                car("4567", "<", "L1", 11, "GO", "DY", "N", 0)));         // L1 has 10 spots
        Answer a = get("/api/v1/yard-tracking/maps/dublin", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT40A");
        assertThat(sent.get().get("yrdTrkgYrd")).isEqualTo("DPX");
        assertThat(a.body().get("screen").asText()).isEqualTo("VHYT40I");
        assertThat(a.body().get("yard").asText()).isEqualTo("DPX");
        assertThat(a.body().get("pseudoTrack").asText()).isEqualTo("T-95");
        assertThat(a.body().get("cars").size()).isEqualTo(2);
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("<1234");       // a left orientation comes first
        assertThat(a.body().at("/cars/1/label").asText()).isEqualTo("2345>");
        assertThat(a.body().at("/cars/0/colour").asText()).isEqualTo("GREEN");       // VHYT40IS-COLOR: HY green on grey80
        assertThat(a.body().at("/cars/0/background").asText()).isEqualTo("GREY80");
        assertThat(a.body().at("/cars/1/colour").asText()).isEqualTo("BLUE");
        assertThat(a.body().get("tracks").findValuesAsText("track")).contains("L1", "L2", "LTM1", "LTM2", "LB1", "LB2", "LSTOR1", "LSTOR2");
        assertThat(a.body().at("/tracks/0/spots").toString()).isEqualTo("[1,2,3,4,5,6,7,8,9,10]");
    }

    @Test
    void theUpdateMapsColourByCondition() throws Exception {
        program("VHYT50A", "VHYT50A_CRIT", "VHYT50A_DATA", List.of(
                car("1001", "<", "C1", 1, "GO", "HY", "N", 0),
                car("1002", "<", "C1", 2, "GO", "HY", "N", 1),
                car("1003", "<", "C1", 3, "USABLE", "HY", "N", 0),
                car("1004", "<", "C1", 4, "GO", "HY", "Y", 0),
                car("1005", "<", "C1", 5, "NO GO", "HY", "N", 0)));
        Answer a = get("/api/v1/yard-tracking/maps/pittsburg-update", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().get("yrdTrkgYrd")).isEqualTo("PBX");
        assertThat(a.body().get("cars").findValuesAsText("condition")).containsExactly("go", "go-pm-due", "usable", "not-usable", "not-usable");
        assertThat(a.body().at("/cars/0/colour").isNull() || a.body().at("/cars/0/colour").isMissingNode()).isTrue();
    }

    @Test
    void theHaywardShopPutsTheRowsLeftEndFirstMarksSoftwareAndHasOneSpotTracks() throws Exception {
        Map<String, Object> a1 = car("2001", "<", "1A", 1, "GO", "CY", "N", 0);
        a1.put("lftOrientationCd", "<");
        a1.put("softver", "V2");
        Map<String, Object> a2 = car("2002", "<", "2N-1", 7, "GO", "RY", "N", 0);   // 2N-1: one spot, any position
        a2.put("lftOrientationCd", ">");
        a2.put("softver", "V");
        program("VHYT04A", "VHYT04A-CRIT", "VHYT04A-DATA", List.of(a1, a2));
        Answer a = get("/api/v1/yard-tracking/maps/hayward-shop", null);

        assertThat(a.status()).isEqualTo(200);
        assertThat(sent.get().containsKey("yrdTrkgYrd")).isFalse();                  // VHYT04IS-INIT-S sends no yard
        assertThat(a.body().at("/shopReport/whichReport").asText()).isEqualTo("SHOPSTAT");   // VHYT10IS-SHOP, for Print (VHYT27I)
        assertThat(a.body().at("/shopReport/yard").asText()).isEqualTo("HY");
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("$<2001");        // SOFTVER longer than 1: "$" first
        assertThat(a.body().at("/cars/1/label").asText()).isEqualTo("2002<");         // not the row's left end: after the car
        assertThat(a.body().at("/cars/1/position").asInt()).isEqualTo(7);
        assertThat(a.body().at("/cars/0/colour").asText()).isEqualTo("BLUE");         // dy100Color: CY blue
        boolean oneSpot = false;
        for (var t : a.body().get("tracks")) if (t.get("track").asText().equals("2N-1")) oneSpot = t.get("spots").isEmpty();
        assertThat(oneSpot).as("2N-1 is a one-spot track").isTrue();
    }

    @Test
    void maintenanceIsRefusedOnlyWhenTheAccessListSaysNo() throws Exception {
        backend().on("VH01A02", in -> {
            sent.set(in.get("VH01A02_CRIT"));
            return reply(0, 1, Map.of("VH01A02_DATA", Map.of("userGroupAccess", List.of(Map.of("partName", "pbtMaint", "accessAllow", "N")))));
        });
        Answer a = get("/api/v1/yard-tracking/maps/dublin-update/maintenance-access", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("allowed").asBoolean()).isFalse();
        assertThat(sent.get().get("guiName")).isEqualTo("VHYT41");
        assertThat(sent.get().get("userGrp")).isEqualTo("BRIGHTCO");

        backend().reset();
        backend().on("VH01A02", in -> reply(0, 0, Map.of("VH01A02_DATA", Map.of("userGroupAccess", List.of()))));
        assertThat(get("/api/v1/yard-tracking/maps/pittsburg-update/maintenance-access", null).body().get("allowed").asBoolean()).isTrue();
    }

    @Test
    void unknownMapsAndInquiryMapMaintenanceAreBadRequests() throws Exception {
        assertThat(get("/api/v1/yard-tracking/maps/nowhere", null).status()).isEqualTo(400);
        assertThat(get("/api/v1/yard-tracking/maps/dublin/maintenance-access", null).status()).isEqualTo(400);
        assertThat(called()).isEmpty();
    }
}
