package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Car bright, read-only, against the real backend: the cleaning list equals the old VHCBR1I's. */
class CarBrightLiveTest extends LiveTestBase {
    @ParameterizedTest
    @CsvSource({"CY,1,last", "HY,2,overdue"})
    void cleaningListMatchesTheOldScreen(String yard, int option, String apiOption) throws Exception {
        JsonNode clean = send("GET", "/api/v1/car-bright/cleaning?homeYard=" + yard + "&option=" + apiOption, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCBR1I"));
        String win = open.at("/windows/0/id").asText();
        JsonNode items = open.at("/windows/0/widgets/Drop-down_List1/items");
        int idx = 0;
        for (int i = 0; i < items.size(); i++) if (items.get(i).asText().equals(yard)) idx = i + 1;
        String path = "/api/v1/ui/windows/" + win + "/events";
        send("POST", path, Map.of("changes", List.of(), "widget", "Drop-down_List1", "event", "select", "value", idx));
        send("POST", path, Map.of("changes", List.of(), "widget", "Radio_Button_Set1", "event", "select", "value", option));
        JsonNode old = send("POST", path, Map.of("changes", List.of(), "widget", "pbtList", "event", "clicked"));
        List<String> oldCars = new ArrayList<>();
        for (JsonNode r : old.at("/windows/0/widgets/cntCarInfo/rows")) {
            oldCars.add(r.get(0).asText());
            if (!r.get(4).asText().isBlank()) oldCars.add(r.get(4).asText());
        }
        List<String> cars = new ArrayList<>();
        clean.get("cars").forEach(c -> cars.add(c.get("car").asText()));
        assertThat(cars).isEqualTo(oldCars);
    }

    /** adding cleanings (VHCBP1I), read-only part only: the opening revenue date (VHIE01B). Add (VHCBP1A) writes and is never run here. */
    @org.junit.jupiter.api.Test
    void cleaningDefaultsMatchTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/car-bright/thorough-cleanings/defaults", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCBP1I"));
        assertThat(clean.get("date").asText()).isEqualTo(open.at("/windows/0/widgets/txtDate/value").asText().strip());
        assertThat(clean.get("time").asText()).matches("\\d\\d[.:]\\d\\d");
    }
}
