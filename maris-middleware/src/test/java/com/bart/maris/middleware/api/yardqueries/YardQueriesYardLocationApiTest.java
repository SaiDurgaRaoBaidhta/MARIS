package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The yard location report (old VHYT26I) and the reason out of service report (old VHYT32I) over HTTP, on a fake backend. */
class YardQueriesYardLocationApiTest extends ApiTestBase {

    @Test
    void theYardLocationReportAsksForTheYardAndShowsItsTables() throws Exception {
        List<Map<String, Object>> cars = new ArrayList<>();
        for (Map<String, Object> c : YardQueriesYardLocationParityTest.CARS) cars.add(new HashMap<>(c));
        Map<String, Object> crit = new HashMap<>();
        backend().on("VHYT26A", in -> { crit.putAll(in.get("VHYT26A_CRIT")); return reply(0, cars.size(), Map.of("VHYT26A_DATA", rows("dataRow", cars))); });
        backend().on("VHYT30A", in -> reply(100, 0, Map.of()));
        backend().on("VHYT26C", in -> reply(0, 1, Map.of("VHYT26C_DATA", rows("dataRow", List.of(
                Map.of("outOfService", Map.of("code", "BR", "reason", "REASON BR"), "totalCars", 3, "totalAcars", 2, "totalCcars", 1))))));
        Answer a = get("/api/v1/yard-queries/yard-location", Map.of("yard", "cy"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit.get("yrdTrkgYrd")).isEqualTo("CY");
        assertThat(called()).containsExactly("VHYT26A", "VHYT26C", "VHYT30A");
        assertThat(a.body().get("yardName").asText()).isEqualTo("CONCORD YARD");
        assertThat(a.body().get("totalCars").asText()).isEqualTo(String.valueOf(cars.size()));
        assertThat(a.body().at("/cars/headings/0").asText()).isEqualTo("Sub Loc");
        assertThat(a.body().at("/cars/rows").size()).isEqualTo(cars.size());
        assertThat(a.body().at("/cars/rows/0/2").asText()).isEqualTo("<1101");
        assertThat(a.body().at("/reasons/rows/0/0").asText()).contains("BR").contains("REASON BR");
        assertThat(a.body().at("/homeYards/rows").size()).isEqualTo(4);
        assertThat(a.body().path("testTracksNote").isMissingNode()).as("only Hayward includes the test tracks").isTrue();
    }

    @Test
    void haywardIncludesTheTestTracks() throws Exception {
        backend().on("VHYT26A", in -> reply(100, 0, Map.of()));
        backend().on("VHYT30A", in -> reply(100, 0, Map.of()));
        backend().on("VHYT26C", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/yard-queries/yard-location", Map.of("yard", "HY"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("testTracksNote").asText()).isEqualTo(Parity.bundle().screen("VHYT26I").path("widgets").path("Label5").path("props").path("object").asText().strip());
        assertThat(a.body().at("/cars/rows").size()).isZero();
    }

    @Test
    void theOutOfServiceSummaryShowsTheSortedReasons() throws Exception {
        Map<String, Object> crit = new HashMap<>();
        backend().on("VHYT32A", in -> { crit.putAll(in.get("VHYT32A_CRIT")); return reply(0, 2, Map.of("VHYT32A_DATA", rows("dataRow", List.of(
                YardQueriesOutOfServiceParityTest.change("1201", "S-1", "GO", "BR", "2026-11-03", "10.00"),
                YardQueriesOutOfServiceParityTest.change("1201", "S-1", "BR", "GO", "2026-11-05", "12.00"))))); });
        backend().on("VHYT32B", in -> reply(0, 0, Map.of("VHYT32B_CRIT", in.get("VHYT32B_CRIT"), "VHYT32B_DATA", in.get("VHYT32B_DATA"))));
        backend().on("VHYT32C", in -> reply(0, 1, Map.of("VHYT32C_DATA", rows("dataRow", List.of(Map.of("moveReasonCd", "BR", "moveReasonDsc", "BRAKES"))))));
        backend().on("VHYT32D", in -> reply(0, 1, Map.of("VHYT32D_DATA", rows("dataRow", List.of(
                Map.of("moveReasonCd", "BR", "moveReasonDesc", "BRAKES", "vagenCount", 1, "totalHours", 50))))));
        Answer a = get("/api/v1/yard-queries/out-of-service", Map.of("yard", "RY", "from", "2026-11-01", "to", "2026-11-10", "summary", "true"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(crit).containsEntry("yrdTrkgYrd", "RY").containsEntry("startDate", "2026-11-01").containsEntry("endDate", "2026-11-10");
        assertThat(called()).containsExactly("VHYT32A", "VHYT32B", "VHYT32C", "VHYT32D");
        assertThat(a.body().get("shop").asText()).isEqualTo("RICHMOND SHOP");
        assertThat(a.body().get("heading").asText()).contains("SUMMARY");
        assertThat(a.body().at("/summary/rows/0/0").asText()).isEqualTo("BR");
        assertThat(a.body().at("/detail/rows").size()).isEqualTo(2);
    }

    @Test
    void theDetailReportHasNoSummary() throws Exception {
        backend().on("VHYT32A", in -> reply(100, 0, Map.of()));
        Answer a = get("/api/v1/yard-queries/out-of-service", Map.of("from", "2026-11-01", "to", "2026-11-10"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHYT32A");
        assertThat(a.body().get("heading").asText()).contains("DETAIL");
        assertThat(a.body().has("summary") && !a.body().get("summary").isNull()).isFalse();
    }

    @Test
    void badDatesAreRefusedWithTheOldMessage() throws Exception {
        Answer a = get("/api/v1/yard-queries/out-of-service", Map.of("from", "11-01-2026", "to", "2026-11-10"));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().get("detail").asText()).isEqualTo(Parity.bundle().promptText("VHYT27I", "Message_Prompter1"));
        assertThat(called()).isEmpty();
    }
}
