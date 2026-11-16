package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Sub job out the door (old VHSR04I) against the real backend, read-only parts only: the drop-downs (VHSRWT, VHSR04A)
 * and the sub job lookup (VHSR02E). The writing program VHSR04B is never called here.
 */
class SecondaryRepairOutTheDoorLiveTest extends LiveTestBase {
    @Test
    void dropDownsMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/secondary-repair/out-the-door/options", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSR04I"));
        List<String> faults = new ArrayList<>(), oldFaults = new ArrayList<>(), types = new ArrayList<>(), oldTypes = new ArrayList<>();
        clean.get("faultCodes").forEach(f -> faults.add(f.asText()));
        clean.get("workTypes").forEach(t -> types.add(t.get("code").asText()));
        old.at("/windows/0/widgets/cmbCmpntFaultCd/items").forEach(i -> oldFaults.add(i.asText().strip()));
        old.at("/windows/0/widgets/cmbDiagnosticWorkType/items").forEach(i -> oldTypes.add(i.asText().strip()));
        // the engine leaves an old drop-down empty when its items come only from the LOAD-* trigger (an engine gap):
        // a list is compared only when the old screen filled it
        assertThat(faults).isNotEmpty();
        assertThat(types).isNotEmpty();
        if (!oldFaults.isEmpty()) assertThat(faults).isEqualTo(oldFaults);
        if (!oldTypes.isEmpty()) assertThat(types).isEqualTo(oldTypes);
    }

    @Test
    void anUnknownSubJobGivesTheOldMessage() throws Exception {
        JsonNode clean = send("GET", "/api/v1/secondary-repair/out-the-door/sub-job?jobNo=ZZZZZZ&doc=1", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSR04I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of("changes", List.of(
                Map.of("widget", "txtJobNo", "attr", "object", "value", "ZZZZZZ"), Map.of("widget", "txtDocNo", "attr", "object", "value", "1")),
                "widget", "txtDocNo", "event", "losingFocus"));
        assertThat(clean.get("_status").asInt()).isEqualTo(400);
        assertThat(clean.get("detail").asText()).isEqualTo(old.at("/windows/0/widgets/txtMsgArea/value").asText().strip());
    }
}
