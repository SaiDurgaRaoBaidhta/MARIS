package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Secondary repair, read-only, against the real backend: the status lists and a stock inquiry equal the old screens'. */
class SecondaryRepairLiveTest extends LiveTestBase {
    @Test
    void statusesMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/secondary-repair/stock-inquiry/options", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSR05I"));
        JsonNode items = open.at("/windows/0/widgets/Combo_Box1/items");
        assertThat(clean.get("statuses").size()).isEqualTo(items.size());
        for (int i = 0; i < items.size(); i++) assertThat(items.get(i).asText()).startsWith(clean.at("/statuses/" + i + "/code").asText());
    }

    @Test
    void stockInquiryMatchesTheOldScreen() throws Exception {
        // a stock number the old screen knows: the first job's stock from an open job is not available here, so try a common one
        for (String stock : List.of("011000000", "1", "A")) {
            JsonNode clean = send("GET", "/api/v1/secondary-repair/stock-inquiry?stockNo=" + stock, null);
            JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSR05I"));
            JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                    "changes", List.of(Map.of("widget", "txtstockno", "attr", "object", "value", stock)), "widget", "pbList", "event", "clicked"));
            String oldMessage = old.at("/windows/0/widgets/txtmessage/value").asText();
            if (clean.get("_status").asInt() == 400) {
                assertThat(clean.get("detail").asText()).isEqualTo(oldMessage);
            } else {
                assertThat(clean.get("count").asInt()).isEqualTo(old.at("/windows/0/widgets/Stock_Container/rows").size());
            }
        }
    }

    /** an unknown sub job, through the clean API and through the old screen's List (both only read: VHSR09B / VHSR03F / VHSR02E) */
    private void unknownSubJobMatches(String screen, String jobWidget, String docWidget, String list, String messageWidget, String cleanPath) throws Exception {
        JsonNode clean = send("GET", cleanPath, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", jobWidget, "attr", "object", "value", "ZZZZZZ"), Map.of("widget", docWidget, "attr", "object", "value", "1")),
                "widget", list, "event", "clicked"));
        assertThat(clean.get("_status").asInt()).isEqualTo(400);
        assertThat(clean.get("detail").asText()).isEqualTo(old.at("/windows/0/widgets/" + messageWidget + "/value").asText().strip());
    }

    @Test
    void unknownSubJobsGiveTheOldMessages() throws Exception {
        unknownSubJobMatches("VHSR09I", "txtJobNo", "txtDocNo", "pbtList", "txtMsgArea", "/api/v1/secondary-repair/repair-detail?jobNo=ZZZZZZ&doc=1");
        unknownSubJobMatches("VHSR03I", "txtJobNo", "txtDocNo", "pbtList", "txtMsg", "/api/v1/secondary-repair/labor?jobNo=ZZZZZZ&doc=1");
    }

    @Test
    void newJobListsComeFromTheBackend() throws Exception {
        JsonNode in = send("GET", "/api/v1/secondary-repair/in-door/options", null);
        JsonNode split = send("GET", "/api/v1/secondary-repair/job-split/options", null);
        assertThat(in.get("workTypes").size()).isPositive();
        assertThat(split.get("repairAreas").size()).isEqualTo(in.get("repairAreas").size());
        assertThat(in.get("workType").asText()).isNotBlank();
    }
}
