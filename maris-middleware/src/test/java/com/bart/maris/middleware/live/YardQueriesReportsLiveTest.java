package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfServiceService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocation;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesYardLocationService;
import com.bart.maris.middleware.support.LiveTestBase;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The yard location (old VHYT26I) and reason out of service (old VHYT32I) reports on the real backend, read-only. Both
 * reports call programs that write (VHYT26C and VHYT32D fill MARIS's report work table, VHYT32B updates its validation date
 * table), so the endpoints are never called here: the services run with those programs not called, which checks the read
 * part (VHYT26A, VHYT30A, VHYT32A, VHYT32C) and the screens' own arithmetic on real data.
 */
class YardQueriesReportsLiveTest extends LiveTestBase {
    private static final Set<String> WRITERS = Set.of("VHYT26C", "VHYT32B", "VHYT32D");

    @Autowired
    YardQueriesYardLocationService yardLocation;
    @Autowired
    YardQueriesOutOfServiceService outOfService;

    @Test
    void yardLocationOnRealCars() {
        for (String yard : new String[]{"HY", "CY", "RY", "DY"}) {
            YardQueriesYardLocation r = yardLocation.report(yard, WRITERS);
            assertThat(r.yardName()).as(yard).isNotBlank();
            assertThat(r.cars().headings()).as(yard).isNotEmpty().doesNotContain("");
            assertThat(r.cars().rows().size()).as(yard + " cars shown, at most the cars counted").isLessThanOrEqualTo(Integer.parseInt(r.totalCars()));
            assertThat(r.homeYards().rows().size()).as(yard).isLessThanOrEqualTo(4);
            assertThat(r.testTracksNote() != null).as(yard + " test tracks").isEqualTo(yard.equals("HY"));
            for (var row : r.cars().rows()) assertThat(row).as(yard).hasSize(r.cars().headings().size());
        }
    }

    @Test
    void outOfServiceOnTheLastTenDays() {
        String to = LocalDate.now().minusDays(1).toString(), from = LocalDate.now().minusDays(11).toString();
        YardQueriesOutOfService detail = outOfService.report("HY", from, to, false, GROUP, WRITERS);
        assertThat(detail.summary()).isNull();
        assertThat(detail.detail().headings()).isNotEmpty();
        for (var row : detail.detail().rows()) assertThat(row).hasSize(detail.detail().headings().size());
        YardQueriesOutOfService summary = outOfService.report("HY", from, to, true, GROUP, WRITERS);
        assertThat(summary.summary()).isNotNull();
        assertThat(summary.detail().rows()).isEqualTo(detail.detail().rows());
    }

    /** a refused date never reaches MARIS */
    @Test
    void aBadDateOverHttp() throws Exception {
        assertThat(send("GET", "/api/v1/yard-queries/out-of-service?from=11-01-2026&to=2026-11-10", null).get("_status").asInt()).isEqualTo(400);
    }
}
