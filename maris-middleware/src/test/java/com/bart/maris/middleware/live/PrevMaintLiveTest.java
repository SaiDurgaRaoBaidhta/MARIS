package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Preventive maintenance, read-only, against the real backend: every report lists the same cars as the old screens. */
class PrevMaintLiveTest extends LiveTestBase {
    @ParameterizedTest
    @CsvSource({"1,hours-by-yard,CY,,VHHPR1I", "2,mtbi,ALL,,VHHPM2I", "3,by-pm-type,HY,,VHPMR1I", "4,hours-descending,DY,,VHHPD1I", "5,hours-to-next,,CY,VHHPD2I"})
    void reportMatchesTheOldScreen(int rpt, String report, String yard, String loc, String child) throws Exception {
        yard = yard == null ? "" : yard; loc = loc == null ? "" : loc;
        JsonNode clean = send("GET", "/api/v1/preventive-maintenance/reports/" + report + "?homeYard=" + yard + "&location=" + loc, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHHPM1I"));
        String path = "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events";
        send("POST", path, Map.of("changes", List.of(), "widget", "Radio_Button_Set1", "event", "select", "value", rpt));
        String list = rpt == 5 ? "Drop-down_List11" : "Drop-down_List1", value = rpt == 5 ? loc : yard;
        JsonNode items = open.at("/windows/0/widgets/" + list + "/items");
        int idx = 0;
        for (int i = 0; i < items.size(); i++) if (items.get(i).asText().equals(value)) idx = i + 1;
        send("POST", path, Map.of("changes", List.of(), "widget", list, "event", "select", "value", idx));
        JsonNode old = send("POST", path, Map.of("changes", List.of(), "widget", "pbtDisplay", "event", "clicked"));
        List<String> oldCars = new ArrayList<>();
        for (JsonNode w : old.get("windows")) {
            if (!w.get("screen").asText().equals(child)) continue;
            w.get("widgets").forEach(x -> {
                if (!x.has("rows") || !oldCars.isEmpty()) return;
                int col = 0;                                                       // the column headed "Car..."
                for (int i = 0; i < x.get("columns").size(); i++) if (x.get("columns").get(i).asText().strip().toLowerCase().startsWith("car")) { col = i; break; }
                int c = col;
                x.get("rows").forEach(r -> oldCars.add(r.get(c).asText()));
            });
        }
        List<String> cars = new ArrayList<>();
        clean.get("cars").forEach(c -> cars.add(c.get("car").asText()));
        assertThat(cars).isEqualTo(oldCars);
    }

    /** PM scheduling (VHPMS01I), read-only part only: the PM description lookup. Save (VHPMS) writes and is never run here. */
    @ParameterizedTest
    @CsvSource({"03", "A"})
    void pmDescriptionMatchesTheOldScreen(String pmType) throws Exception {
        JsonNode list = send("GET", "/api/v1/car-bright/cleaning?homeYard=HY&option=last", null);         // any real car
        String car = list.at("/cars/0/car").asText();
        assertThat(car).isNotBlank();
        JsonNode clean = send("GET", "/api/v1/preventive-maintenance/schedule/description?car=" + car + "&pmType=" + pmType, null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);

        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHPMS01I"));
        String path = "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events";
        JsonNode old = send("POST", path, Map.of("changes", List.of(Map.of("widget", "txtCarNo", "attr", "object", "value", car),
                Map.of("widget", "txtPMType", "attr", "object", "value", pmType)), "widget", "txtPMType", "event", "losingFocus"));
        assertThat(clean.get("description").asText()).isEqualTo(old.at("/windows/0/widgets/Text1/value").asText().strip());
        assertThat(clean.get("pmType").asText()).isEqualTo(old.at("/windows/0/widgets/txtPMType/value").asText().strip());
    }
}
