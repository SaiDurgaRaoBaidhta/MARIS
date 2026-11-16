package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Car wash updates against the real backend, read-only parts only: the revenue date (VHIE01B) and a consist's cars
 * (VHCWA5, VHCWA3). The writing programs (VHCWA6, VHCWA3D, VHINVB) are never called here.
 */
class CarWashUpdatesLiveTest extends LiveTestBase {
    private static String mdy(String iso) {
        return iso.substring(5, 7) + "-" + iso.substring(8, 10) + "-" + iso.substring(0, 4);
    }

    @Test
    void startsOnTheOldScreensRevenueDate() throws Exception {
        JsonNode clean = send("GET", "/api/v1/car-wash/consist-washes/start", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode old = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCWA2I"));
        assertThat(mdy(clean.get("washDate").asText())).isEqualTo(old.at("/windows/0/widgets/Text6/value").asText().strip());
        JsonNode cars = send("GET", "/api/v1/car-wash/car-washes/start", null);
        assertThat(cars.get("washDate").asText()).isEqualTo(clean.get("washDate").asText());
    }

    @Test
    void consistLookupMatchesTheOldScreen() throws Exception {
        String date = send("GET", "/api/v1/car-wash/consist-washes/start", null).get("washDate").asText();
        for (String consist : List.of("201", "305")) {
            JsonNode clean = send("GET", "/api/v1/car-wash/consist-washes/consist?consist=" + consist + "&date=" + date, null);
            assertThat(clean.get("_status").asInt()).isEqualTo(200);
            JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCWA2I"));
            JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of("changes", List.of(
                    Map.of("widget", "txtFromdate", "attr", "object", "value", consist), Map.of("widget", "Text6", "attr", "object", "value", mdy(date))),
                    "widget", "Text6", "event", "losingFocus"));
            assertThat(clean.get("message").asText()).as(consist).isEqualTo(old.at("/windows/0/widgets/Text5/value").asText().strip());
            if (clean.get("found").asBoolean()) {
                assertThat(clean.at("/cars/0").asText()).isEqualTo(old.at("/windows/0/widgets/Text7/value").asText().strip());
            }
        }
    }
}
