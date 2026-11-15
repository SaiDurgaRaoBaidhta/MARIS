package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Consist log (VHCL01I/VHCL02I) and daily vehicle status (VHDVS1I .. VHDVS4I) against the real backend: only what reads.
 * Display of the consist log runs VHCL01E, which UPDATEs MRS_VALIDTION_DATE, and the daily status runs VHDVS11, which
 * UPDATEs MRS_SYS_PARM; both, and the prints (VHCL011, VHDVS04), are covered by the API and parity tests only.
 */
class VehicleQueriesConsistLogLiveTest extends LiveTestBase {
    @Test
    void consistLogOptionsGiveTodayAndTheRealPrinters() throws Exception {
        JsonNode o = send("GET", "/api/v1/vehicle-queries/consist-log/options", null);
        assertThat(o.get("_status").asInt()).isEqualTo(200);
        assertThat(o.get("revenueDate").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(o.get("printers").size()).isPositive();
        o.get("printers").forEach(p -> assertThat(p.get("vtamName").asText()).isNotBlank());
    }
}
