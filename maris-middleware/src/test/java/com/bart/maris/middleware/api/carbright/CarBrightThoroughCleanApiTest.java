package com.bart.maris.middleware.api.carbright;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Adding car thorough cleanings (old VHCBP1I) over HTTP: the opening values, the checks with the old messages, what VHCBP1A gets. */
class CarBrightThoroughCleanApiTest extends ApiTestBase {
    private static final EglTexts TEXTS = new EglTexts(Parity.bundle());
    private static final String PKG = "MRSVehicleCarBright";
    private final AtomicReference<Map<String, Object>> added = new AtomicReference<>();

    @BeforeEach
    void backendAnswers() {
        added.set(null);
        backend().on("VHIE01B", in -> reply(0, 1, Map.of("VHIE01B_DATA", Map.of("timeRevStart", "03.00.00", "currentTime", "02.15.32",
                "currentDate", "2026-11-10", "yesterday", "2026-11-09"))));
        backend().on("VHCBR2C", in -> String.valueOf(in.get("VHCBR2C_CRIT").get("carNo")).strip().equals("9999") ? reply(100, 0, Map.of())
                : reply(0, 1, Map.of("VHCBR2C_CRIT", Map.of("carNo", in.get("VHCBR2C_CRIT").get("carNo"), "carType", "A2"))));
        backend().on("VHCBP1A", in -> { added.set(in.get("VHCBP1A_DATA")); return reply(0, 1, Map.of("VHCBP1A_DATA", Map.of("recordFound", "N"))); });
    }

    @Test
    void defaultsAreTheRevenueDateAndTime() throws Exception {
        Answer a = get("/api/v1/car-bright/thorough-cleanings/defaults", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("date").asText()).isEqualTo("11-09-2026");           // before the revenue day starts: yesterday
        assertThat(a.body().get("time").asText()).isEqualTo("02.15");
        assertThat(a.body().get("yard").asText()).isEqualTo("HY");
        assertThat(a.body().get("maxCars").asInt()).isEqualTo(20);
    }

    @Test
    void addSendsTheCarsTheDateAndTheTerminal() throws Exception {
        Answer a = post("/api/v1/car-bright/thorough-cleanings", Map.of("yard", "CY", "date", "11-09-2026", "time", "09:30", "cars", List.of("1101", "", "1102")));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().get("message").asText()).isEqualTo(TEXTS.assigned("VHCBP1IS-ADD", PKG, "FINAL-ERROR-MSG", "added successfully"));
        assertThat(added.get()).containsEntry("britLoc", "CY").containsEntry("dateCarBrit", "2026-11-09").containsEntry("numOfCars", 2L)
                .containsEntry("termLastUpd", "MARIS-UI");
        assertThat(added.get().get("dataRow").toString()).isEqualTo("[{carNo=1101}, {carNo=1102}]");
        assertThat(added.get().get("timeCarBrit").toString()).contains("hh=09").contains("mm=30").contains("seperator=.").contains(".00");
    }

    @Test
    void checksAnswer400WithTheOldMessagesJoined() throws Exception {
        Answer a = post("/api/v1/car-bright/thorough-cleanings", Map.of("yard", "HY", "date", "", "time", "", "cars", List.of()));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().toString()).contains("Date cannot be blank, Time cannot be blank, First car no field cannot be blank");
        Answer missing = post("/api/v1/car-bright/thorough-cleanings", Map.of("yard", "HY", "date", "11-09-2026", "time", "09:30", "cars", List.of("1101", "9999")));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().toString()).contains("Car No 9999 does not exist");
        assertThat(added.get()).isNull();
    }

    @Test
    void aDuplicateCleaningIsRefused() throws Exception {
        backend().on("VHCBP1A", in -> reply(0, 1, Map.of("VHCBP1A_DATA", Map.of("recordFound", "Y"))));
        Answer a = post("/api/v1/car-bright/thorough-cleanings", Map.of("yard", "HY", "date", "11-09-2026", "time", "09:30", "cars", List.of("1101")));
        assertThat(a.status()).isEqualTo(400);
        assertThat(a.body().toString()).contains(TEXTS.assigned("VHCBP1IS-ADD", PKG, "FINAL-ERROR-MSG", "Duplicate"));
    }
}
