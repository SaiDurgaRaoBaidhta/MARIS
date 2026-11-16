package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Car wash, read-only, against the real backend: both reports equal the old screens'. */
class CarWashLiveTest extends LiveTestBase {
    private List<String> oldCars(String screen, Map<String, String> fields, int carColumn, boolean pairs) throws Exception {
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", screen));
        List<Map<String, Object>> changes = new ArrayList<>();
        fields.forEach((w, v) -> changes.add(Map.of("widget", w, "attr", "object", "value", v)));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events",
                Map.of("changes", changes, "widget", "pbtList", "event", "clicked"));
        List<String> cars = new ArrayList<>();
        old.at("/windows/0/widgets").forEach(w -> {
            if (w.has("rows")) w.get("rows").forEach(r -> {
                cars.add(r.get(carColumn).asText());
                if (pairs && !r.get(3).asText().isBlank()) cars.add(r.get(3).asText());
            });
        });
        return cars;
    }

    @ParameterizedTest
    @ValueSource(strings = {"CY", "HY"})
    void washesMatchTheOldScreen(String yard) throws Exception {
        LocalDate to = LocalDate.now(), from = to.minusDays(14);
        DateTimeFormatter mdy = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        JsonNode clean = send("GET", "/api/v1/car-wash/washes?yard=" + yard + "&from=" + from + "&to=" + to, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        List<String> cars = new ArrayList<>();
        clean.get("washes").forEach(w -> cars.add(w.get("car").asText()));
        assertThat(cars).isEqualTo(oldCars("VHCWR1I", Map.of("txtYard", yard, "txtFromDate", mdy.format(from), "txtToDate", mdy.format(to)), 0, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DY", "RY"})
    void overdueMatchesTheOldScreen(String yard) throws Exception {
        JsonNode clean = send("GET", "/api/v1/car-wash/overdue?homeYard=" + yard, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        List<String> cars = new ArrayList<>();
        clean.get("cars").forEach(w -> cars.add(w.get("car").asText()));
        assertThat(cars).isEqualTo(oldCars("VHCWR2I", Map.of("txtYard", yard), 0, false));
    }
}
