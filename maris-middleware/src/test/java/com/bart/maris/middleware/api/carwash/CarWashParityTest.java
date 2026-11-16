package com.bart.maris.middleware.api.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.carwash.CarWashService;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Car wash reports against the old VHCWR1I / VHCWR2I run by the engine. */
class CarWashParityTest {
    private Parity backend() {
        Parity p = new Parity();
        p.on("AFCXVLD", in -> Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N")));
        p.on("VHCWR1A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHCWR1A_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "washedDate", "11-02-2026", "washedTime", "07:15", "carNo1", "1102", "washedDate1", "11-03-2026", "washedTime1", "08:00"),
                Map.of("carNo", "1103", "washedDate", "11-04-2026", "washedTime", "09:00")))));
        p.on("VHCWR2A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCWR2A_DATA", Map.of("dataRow", List.of(
                Map.of("carNo", "1101", "dateLastWash", "10-01-2026", "timeLastWash", "06:00", "currLoc", "HY03", "daysOverdue", 12, "totalWashes", 40)))));
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> window(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({"CY,11-01-2026,11-12-2026,2026-11-01,2026-11-12", "RY,11/01/2026,11/05/2026,2026-11-01,2026-11-05"})
    void washesMatchTheOldScreen(String yard, String from, String to, String isoFrom, String isoTo) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCWR1I", p.user);
        old.event((String) window(old).get("id"), List.of(new ScreenSession.Change("txtYard", "object", yard),
                new ScreenSession.Change("txtFromDate", "object", from), new ScreenSession.Change("txtToDate", "object", to)), "pbtList", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHCWR1A").get("VHCWR1A_CRIT");
        List<String> oldCars = new ArrayList<>();
        for (List<String> r : (List<List<String>>) ((Map<String, Object>) ((Map<String, Object>) window(old).get("widgets")).get(
                ((Map<String, Object>) window(old).get("widgets")).keySet().stream().filter(k -> ((Map<String, Object>) ((Map<String, Object>) window(old).get("widgets")).get(k)).containsKey("rows")).findFirst().orElseThrow())).get("rows")) {
            oldCars.add(String.join("|", r.subList(0, 3)));
            if (!r.get(3).isBlank()) oldCars.add(String.join("|", r.subList(3, 6)));
        }

        p.clear();
        var api = new CarWashService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock).washes(yard, isoFrom, isoTo);
        Map<String, Object> newCrit = p.lastSent("VHCWR1A").get("VHCWR1A_CRIT");
        // the old screen sends the dates as typed (MM-DD-YYYY or MM/DD/YYYY); the API always sends MM-DD-YYYY
        assertThat(newCrit.get("yard")).isEqualTo(oldCrit.get("yard"));
        assertThat(newCrit.get("fromDate")).isEqualTo(((String) oldCrit.get("fromDate")).replace('/', '-'));
        assertThat(newCrit.get("toDate")).isEqualTo(((String) oldCrit.get("toDate")).replace('/', '-'));
        assertThat(api.washes().stream().map(w -> String.join("|", w.car(), w.date(), w.time())).toList()).isEqualTo(oldCars);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({"HY", "DY"})
    void overdueMatchesTheOldScreen(String yard) {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHCWR2I", p.user);
        old.event((String) window(old).get("id"), List.of(new ScreenSession.Change("txtYard", "object", yard)), "pbtList", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHCWR2A").get("VHCWR2A_CRIT");

        p.clear();
        var api = new CarWashService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), p.clock).overdue(yard);
        assertThat(p.lastSent("VHCWR2A").get("VHCWR2A_CRIT")).isEqualTo(oldCrit);
        assertThat(api.count()).isEqualTo(1);
    }
}
