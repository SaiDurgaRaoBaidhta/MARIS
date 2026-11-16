package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The shop administration maps (VHYT10I, VHYT95I) and the shop car move (VHYT03I / VHYT42I) over HTTP against the fake backend. */
class YardTrackingShopAdminApiTest extends ApiTestBase {
    static Map<String, Object> car(String car, String track, int posn, String softver) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", car); m.put("vagenOrientation", "<"); m.put("lftOrientationCd", "<"); m.put("yrdSubLoc", track); m.put("posnNo", posn);
        m.put("vehStat", "GO"); m.put("homeYrd", "HY"); m.put("softver", softver);
        return m;
    }

    void hayward() {
        backend().on("VHYT08C", in -> reply(0, 0, Map.of()));
        backend().on("VHYT04A", in -> reply(0, 2, Map.of("VHYT04A-DATA", rows("dataRow", List.of(car("1101", "TT", 2, ""), car("1102", "1B", 1, "$12"))))));
    }

    @Test
    void theHaywardMapShowsTheCarsAndItsSpots() throws Exception {
        hayward();
        Answer a = get("/api/v1/yard-tracking/shop-admin/hayward", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("screen").asText()).isEqualTo("VHYT10I");
        assertThat(a.body().get("yard").asText()).isEqualTo("HY");
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("<1101");
        assertThat(a.body().at("/cars/1/label").asText()).isEqualTo("$<1102");
        assertThat(a.body().get("spots").size()).isGreaterThan(20);
        assertThat(a.body().at("/report/whichReport").asText()).isEqualTo("SHOPSTAT");
    }

    @Test
    void twoClicksOpenTheCarMove() throws Exception {
        hayward();
        Answer a = post("/api/v1/yard-tracking/shop-admin/hayward/selection", Map.of("spots", List.of("TT/2", "TT/5")));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("open").asText()).isEqualTo("VHYT03I");
        assertThat(a.body().at("/params/carNo").asText()).isEqualTo("1101");
        assertThat(a.body().at("/params/newYrdLoc").asText()).isEqualTo("TT");
        assertThat(a.body().at("/params/posnNoTo").asInt()).isEqualTo(5);
        assertThat(a.body().at("/params/yrdTrkgYrd").asText()).isEqualTo("HY");
    }

    @Test
    void unknownMapsAndSpotsAreRefused() throws Exception {
        assertThat(get("/api/v1/yard-tracking/shop-admin/oakland", null).status()).isEqualTo(400);
        hayward();
        assertThat(post("/api/v1/yard-tracking/shop-admin/hayward/selection", Map.of("spots", List.of("ZZ/9"))).status()).isEqualTo(400);
    }

    final AtomicReference<Map<String, Object>> moved = new AtomicReference<>();

    void move(String reason) {
        backend().on("VHYT03A", in -> reply(0, 2, Map.of("VHYT03A_DATA", rows("dataRow", List.of(
                Map.of("moveReasonCd", "M1", "filler", " - ", "moveReasonDesc", "WAITING"), Map.of("moveReasonCd", "P2", "filler", " - ", "moveReasonDesc", "PM"))))));
        backend().on("VHYT03B", in -> reply(0, 2, Map.of("VHYT03B_DATA", rows("dataRow", List.of(
                Map.of("yrdSubLoc", "TT", "filler", " - ", "yrdSubLocDesc", "TRANSFER"), Map.of("yrdSubLoc", "1B", "filler", " - ", "yrdSubLocDesc", "SHOP 1B"))))));
        backend().on("VHYT03F", in -> reply(0, 1, Map.of("VHYT03F_DATA", Map.of("yrdDesc", "HAYWARD"))));
        backend().on("VHYT03D", in -> reply(0, 1, Map.of("VHYT03D_DATA", Map.of("carNo", "1101", "yrdSubLoc", "TT", "yrdSubLocDesc", "TRANSFER",
                "moveReasonCdX", Map.of("moveReasonCd", reason, "moveFiller", "-", "moveReasonDesc", "WAITING"), "posnNo", 2, "biDirInsertInd", "N"))));
        backend().on("VHYT03E", in -> reply(0, 1, Map.of("VHYT03E_DATA", Map.of("vagenOrientation", "<", "vehStat", "GO", "trainIncdInd", "N"))));
        backend().on("VHYT03J", in -> reply(100, 0, Map.of()));
        backend().on("VHYT03H", in -> { moved.set(in.get("VHYT03H_CRIT")); return reply(0, 1, Map.of("VHYT03H_CRIT", in.get("VHYT03H_CRIT"))); });
        backend().on("VHYT03G", in -> reply(0, 1, Map.of("VHYT03G_CRIT", in.get("VHYT03G_CRIT"))));
    }

    static Map<String, Object> context() {
        Map<String, Object> c = new HashMap<>();
        c.put("carNo", "1101"); c.put("yrdTrkgYrd", "HY"); c.put("yrdSubLoc", "TT"); c.put("newYrdLoc", "TT"); c.put("carNo2", "");
        c.put("posnNoFrom", 2); c.put("posnNoTo", 5); c.put("whichGui", "VHYT03I");
        return c;
    }

    @Test
    void theMoveWindowShowsTheCarAndItsChoices() throws Exception {
        move("M1");
        Answer a = post("/api/v1/yard-tracking/shop-move", Map.of("context", context()));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("carNo").asText()).isEqualTo("1101");
        assertThat(a.body().get("reasons").size()).isEqualTo(2);
        assertThat(a.body().at("/locations/1/code").asText()).isEqualTo("1B");
        assertThat(a.body().get("selectedReason").asText()).isEqualTo("M1");
    }

    @Test
    void updateMovesTheCar() throws Exception {
        move("M1");
        Answer a = post("/api/v1/yard-tracking/shop-move/update", Map.of("context", context(), "reason", "P2", "location", "1B"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo("Update successful");
        assertThat(String.valueOf(moved.get().get("newYrdSubLoc")).strip()).isEqualTo("1B");
    }

    @Test
    void anUnknownReasonIsRefused() throws Exception {
        move("M1");
        assertThat(post("/api/v1/yard-tracking/shop-move/update", Map.of("context", context(), "reason", "ZZ")).status()).isEqualTo(400);
    }
}
