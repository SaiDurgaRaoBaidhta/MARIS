package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Over HTTP, on a fake backend: the Concord / Richmond shop maps (old VHYT60I, VHYT96I), force clear (VHYT54I), pseudo
 * correction (VHYT52I), shop activity (VHYT31I) and its prints (VHYT23I, VHYT22I), make / break overrides (VHYT34I).
 */
class YardTrackingMiscApiTest extends ApiTestBase {

    @Test
    void theShopMapsShowTheirCarsAndPrint() throws Exception {
        backend().on("VHYT60A", in -> reply(0, 1, Map.of("VHYT60A-DATA", rows("dataRow", List.of(Map.of("carNo", "1101", "yrdSubLoc", "RR-01",
                "posnNo", 1, "vagenOrientation", "<", "lftOrientationCd", "<", "homeYrd", "CY", "softver", "V12"))))));
        Answer a = get("/api/v1/yard-tracking/maps/concord-shop", Map.of());
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("screen").asText()).isEqualTo("VHYT60I");
        assertThat(a.body().at("/cars/0/label").asText()).isEqualTo("$<1101");
        assertThat(a.body().at("/cars/0/colour").asText()).isEqualTo("BLUE");
        assertThat(a.body().at("/shopReport/whichReport").asText()).isEqualTo("SHOPSTAT");
        assertThat(a.body().at("/shopReport/yard").asText()).isEqualTo("CY");
        backend().on("VHYT95A", in -> reply(100, 0, Map.of()));
        Answer r = get("/api/v1/yard-tracking/maps/richmond-shop", Map.of());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("cars").size()).isZero();
        assertThat(r.body().at("/shopReport/yard").asText()).isEqualTo("RY");
        // the other maps still answer on the same path pattern
        assertThat(get("/api/v1/yard-tracking/maps/no-such-map", Map.of()).status()).isEqualTo(400);
    }

    @Test
    void forceClearChecksTheTrainThenSendsTheMessage() throws Exception {
        Answer bad = post("/api/v1/yard-tracking/force-clear", Map.of("train", "05", "location", "CY"));
        assertThat(bad.status()).isEqualTo(200);
        assertThat(bad.body().get("message").asText()).contains("05").contains("is not a valid train number");
        assertThat(called()).isEmpty();
        Map<String, Object> sentMessage = new HashMap<>();
        backend().on("VHYT54A", in -> reply(0, 1, Map.of("VHYT54A_DATA", rows("dataRow", List.of(Map.of("conNo", "101", "dashNo", 3))))));
        backend().on("VHYT54B", in -> reply(0, 1, Map.of()));
        backend().on("VHYT54C", in -> { sentMessage.putAll(in.get("VHYT54C_DATA")); return reply(0, 1, Map.of()); });
        Answer ok = post("/api/v1/yard-tracking/force-clear", Map.of("train", "101", "location", "CY"));
        assertThat(ok.body().get("message").asText()).contains("Process started").contains("101").contains("CY");
        assertThat(called()).containsExactly("VHYT54A", "VHYT54B", "VHYT54C");
        assertThat(sentMessage.get("userGrpMsgCmplt")).isEqualTo("BRIGHTCO");
        assertThat(sentMessage.get("msgTranCd")).isEqualTo("3F");
    }

    @Test
    void pseudoCorrectionListsTrainsAndRetrievesOne() throws Exception {
        backend().on("VHYT52A", in -> reply(0, 2, Map.of("VHYT52A_DATA", rows("dataRow", List.of(
                Map.of("carNo", "1101", "consist", Map.of("conNo", "201", "conHyphen", "-", "dashNo", 1), "conStat", "INS", "vehStat", "GO", "noCarsInd", "N",
                        "vagenOrientation", "<", "lftOrientationCd", "<"),
                Map.of("carNo", "1102", "consist", Map.of("conNo", "201", "conHyphen", "-", "dashNo", 1), "conStat", "INS", "vehStat", "GO", "noCarsInd", "N",
                        "vagenOrientation", ">", "rgtOrientationCd", ">"))))));
        Answer a = get("/api/v1/yard-tracking/pseudo-correction", Map.of("yard", "cy"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/trains/0/consist").asText()).isEqualTo("201-01");
        assertThat(a.body().at("/trains/0/cars/0").asText()).isEqualTo("<1101");
        Map<String, Object> req = new HashMap<>();
        backend().on("VHYT52B", in -> { req.putAll(in.get("VHYT52B_DATA")); return reply(0, 1, Map.of("VHYT52B_CRIT", Map.of("yesRequest", "N"))); });
        Answer r = post("/api/v1/yard-tracking/pseudo-correction/retrieve", Map.of("yard", "CY", "row", 1));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("message").asText()).contains("Process started");
        assertThat(req.get("conNo")).isEqualTo("201");
        assertThat(req.get("userGrpReqst")).isEqualTo("BRIGHTCO");
        assertThat(post("/api/v1/yard-tracking/pseudo-correction/retrieve", Map.of("yard", "CY", "row", 9)).status()).isEqualTo(400);
    }

    @Test
    void shopActivityListsADayAndPrints() throws Exception {
        backend().on("VHYT31A", in -> reply(0, 1, Map.of("VHYT31A_DATA", rows("dataRow", List.of(Map.of("yard", Map.of("yrd", "CY", "yrdDesc", "CONCORD")))))));
        backend().on("VHYT31E", in -> reply(0, 1, Map.of("VHYT31E_DATA", Map.of("dateWork", "2026-11-14"))));
        backend().on("VHYT31B", in -> reply(0, 1, Map.of("VHYT31B_DATA", Map.of("rowCnt", 1, "arrivalCnt", 4,
                "dataRow", List.of(Map.of("carInfo", Map.of("lftOr", "<", "carNo", "1101", "rgtOr", " "), "vehStat", "GO"))))));
        Answer o = get("/api/v1/yard-tracking/shop-activity/options", Map.of());
        assertThat(o.body().at("/yards/0/code").asText()).isEqualTo("CY");
        Answer a = post("/api/v1/yard-tracking/shop-activity/list", Map.of("yard", "cy", "date", "11-14-2026"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/cars/0/car").asText()).isEqualTo("<1101");
        assertThat(a.body().at("/totals/arrivals").asInt()).isEqualTo(4);
        assertThat(post("/api/v1/yard-tracking/shop-activity/list", Map.of("yard", "ZZ")).status()).isEqualTo(400);
        backend().on("WS70A01", in -> reply(0, 1, Map.of("WS70A01_DATA", rows("dataRow", List.of(Map.of("vtamName", "PRT01", "prtrName", "SHOP"))))));
        Map<String, Object> crit = new HashMap<>();
        backend().on("VHYT23A", in -> { crit.putAll(in.get("VHYT23A_CRIT")); return reply(0, 1, Map.of()); });
        assertThat(get("/api/v1/yard-tracking/printers", Map.of()).body().at("/0/name").asText()).isEqualTo("PRT01");
        Answer p = post("/api/v1/yard-tracking/shop-activity/print", Map.of("yard", "CY", "date", "11-14-2026", "printer", 1));
        assertThat(p.status()).isEqualTo(200);
        assertThat(crit.get("reptType")).isEqualTo("PRT01");
        assertThat(crit.get("yrdTrkgYrd")).isEqualTo("CY");
        backend().on("VHYT22A", in -> reply(0, 1, Map.of()));
        assertThat(post("/api/v1/yard-tracking/car-history/print", Map.of("car", "1101", "printer", 1)).status()).isEqualTo(200);
        assertThat(post("/api/v1/yard-tracking/car-history/print", Map.of("car", "", "printer", 1)).status()).isEqualTo(400);
    }

    @Test
    void makeBreakOverridesOpenAndSave() throws Exception {
        backend().on("VHYT34A", in -> reply(0, 1, Map.of("VHYT34A_DATA", Map.of("currentDate", "2026-11-15", "currentTime", "10:00:00", "timeRevEnd", "03:00:00"))));
        backend().on("VHYT34B", in -> reply(0, 1, Map.of("VHYT34B_DATA", Map.of("cntDayOfWeek", 2))));
        backend().on("VHYT34D", in -> reply(0, 2, Map.of("VHYT34D_CRIT", Map.of("maCnt", 2), "VHYT34D_DATA", rows("dataRow", List.of(
                Map.of("timeOfArrival", "05:00:00"), Map.of("timeOfArrival", "06:00:00"))))));
        backend().on("VHYT34H", in -> reply(0, 1, Map.of("VHYT34H_DATA", rows("dataRow", List.of(Map.of("conNo", "201"))))));
        for (String p : List.of("VHYT34C", "VHYT34E", "VHYT34N", "VHYT34G")) backend().on(p, in -> reply(0, 0, Map.of()));
        Answer w = post("/api/v1/yard-queries/make-break-overrides/window", Map.of("yard", "CY"));
        assertThat(w.status()).isEqualTo(200);
        assertThat(w.body().at("/times/amMakeFrom").size()).isEqualTo(2);
        assertThat(w.body().at("/trains/0").asText()).isEqualTo("201");
        assertThat(w.body().get("revenueDate").asText()).isEqualTo("11-15-2026");
        Answer s = post("/api/v1/yard-queries/make-break-overrides", Map.of("yard", "CY", "overrides",
                Map.of("times", Map.of("amMakeFrom", "05:00:00", "amMakeTo", "06:00:00"), "boxes", Map.of("amMakeCancel", true))));
        assertThat(s.status()).isEqualTo(200);
        assertThat(called()).contains("VHYT34G");
        assertThat(post("/api/v1/yard-queries/make-break-overrides", Map.of("yard", "CY", "overrides",
                Map.of("times", Map.of("amMakeFrom", "09:99:00")))).status()).isEqualTo(400);
    }
}
