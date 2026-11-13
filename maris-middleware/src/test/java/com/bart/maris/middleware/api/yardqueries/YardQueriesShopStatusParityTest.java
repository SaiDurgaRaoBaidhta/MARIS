package com.bart.maris.middleware.api.yardqueries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatus;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesShopStatusService;
import com.bart.maris.middleware.engine.screen.ScreenInstance;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shop status report: the clean API against the old VHYT25I run by the engine, reached as users reach it: VHYT27I
 * (opened by the menu for the shop status report, REPORT-INDEX 1), a yard chosen, Display.
 */
class YardQueriesShopStatusParityTest {
    private Parity p;

    private static Map<String, Object> shopCar(int i, String track, int mo, int dd, int hh, int mm) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", i, "carNo", String.valueOf(1000 + i),
                "vagenOrientation", i % 2 == 0 ? "<" : ">", "lftOrientCd", "<", "rgtOrientCd", ">", "vehStat", "NO GO",
                "dateInShop", "11-10-2026", "timeInShop", Map.of("timeInHhmm", "08:15"), "moveReasonDsc", "BRAKES"));
        m.putAll(Map.of("currentTime", "14:30:00", "tsMo", mo, "tsDd", dd, "tsHh", hh, "tsMm", mm, "trainIncdInd", i == 1 ? "Y" : "N",
                "carType", Map.of("carLetter", i == 3 ? "Z" : "A")));
        return m;
    }

    private static Map<String, Object> outCar(int i, String track, boolean yardFirst) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "posnNo", i, "carNo", String.valueOf(2000 + i), "vagenOrientation", ">",
                "lftOrientCd", "<", "rgtOrientCd", ">", "vehStat", "GO", "dateOutShop", "11-11-2026", "timeOutShop", Map.of("timeOutHhmm", "09:00"),
                "dateOutYrd", "11-09-2026"));
        m.put("timeOutYrd", Map.of("yTimeOutHhmm", "07:00"));
        m.putAll(Map.of("moveReasonDsc", "TEST", "tsDt", 20261111L, "tsyDt", yardFirst ? 20261109L : 20261112L,
                "tsDd", i, "tsHh", 2, "tsMm", 3, "tsyDd", 4 + i, "tsyHh", 5, "tsyMm", 6, "carType", Map.of("carLetter", "B")));
        return m;
    }

    private void backend() {
        p = new Parity();
        List<Map<String, Object>> shop = List.of(shopCar(1, "S-1", 0, 2, 5, 40), shopCar(2, "S-2", 1, 0, 23, 45), shopCar(3, "T-24", 0, 9, 9, 9),
                shopCar(4, "S-3", 0, 0, 7, 10));
        List<Map<String, Object>> out = List.of(outCar(1, "SHOP", true), outCar(2, "T-24", false), outCar(3, "SHOP", false));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0)));
        p.on("VHYT27B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHRT04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHRT04A_DATA", Map.of("dataRow", List.of(
                Map.of("yrd", "CY", "yrdDesc", "CONCORD YARD"), Map.of("yrd", "HY", "yrdDesc", "HAYWARD YARD")))));
        p.on("VHYT25A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", shop.size()), "VHYT25A_DATA", Map.of("dataRow", shop)));
        p.on("VHYT30A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", out.size()), "VHYT30A_DATA", Map.of("dataRow", out)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> widgets(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (w.get("screen").toString().equalsIgnoreCase(screen)) return (Map<String, Map<String, Object>>) w.get("widgets");
        }
        throw new AssertionError(screen + " is not open: " + s.state().get("windows"));
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> rows(Map<String, Map<String, Object>> w, String container) {
        List<List<String>> out = new ArrayList<>();
        for (Object r : (List<Object>) w.get(container).getOrDefault("rows", List.of())) {
            out.add(((List<Object>) r).stream().map(x -> x == null ? "" : x.toString().strip()).toList());
        }
        return out;
    }

    /** VHYT27I for the shop status report, yard chosen, Display */
    private ScreenSession openOld(String yard) {
        ScreenSession old = p.oldScreens();
        old.openRoot("VHYT27I", p.user);
        @SuppressWarnings("unchecked") String w = ((List<Map<String, Object>>) old.state().get("windows")).get(0).get("id").toString();
        ScreenInstance s = old.window(w);
        // the menu opens VHYT27I with WHICH-REPORT = SHOPSTAT; VHYT27IS-BEGIN then sets REPORT-INDEX 1 and turns Display on
        s.recordPart("VHYT27IW").set("REPORT-INDEX data", 1);
        s.recordPart("VHYT27IW-TRIGGERS").set("ENABLE-DISPLAY data", "Y");
        @SuppressWarnings("unchecked") List<String> yards = (List<String>) widgets(old, "VHYT27I").get("Drop-down_List1").get("items");
        int index = 0;
        for (int i = 0; i < yards.size(); i++) if (yards.get(i).startsWith(yard + " ")) index = i + 1;
        old.event(w, List.of(), "Drop-down_List1", "select", index);
        // the list's selectedItem reaches YARD-SELECTION through an event-to-attribute connection the engine does not apply
        s.recordPart("VHYT27IW-PASSVALUE").set("YARD-SELECTION data", yards.get(index - 1));
        p.clear();
        old.event(w, List.of(), "Push_Button3", "clicked", null);
        return old;
    }

    @Test
    void theReportAsksForTheSameAndShowsTheSameCarsTimesAndTotals() {
        backend();
        ScreenSession old = openOld("HY");
        Map<String, Map<String, Object>> w = widgets(old, "VHYT25I");
        Map<String, Map<String, Object>> sent25 = p.lastSent("VHYT25A"), sent30 = p.lastSent("VHYT30A");
        assertThat(sent25).as("the old report asked for the shop's cars").isNotNull();

        p.clear();
        YardQueriesShopStatus r = new YardQueriesShopStatusService(p.legacy(), p.messages(), Parity.bundle()).report("HY");
        // The engine does not pass the yard from VHYT27I to VHYT25I: YARD-SELECTION reaches YARD-SELECTION_of_VHYT27IW-PASSVALUE
        // as a plain value, not as the group part whose "YRD data" the child is bound to, so the old VHYT25A_CRIT yard is blank
        // there (VHYT30IS-GET-DATA then falls back to HY). Everything else is compared.
        Map<String, Object> oldCrit = new HashMap<>(sent25.get("VHYT25A_CRIT"));
        assertThat(oldCrit.get("yrdTrkgYrd")).isEqualTo("");
        oldCrit.put("yrdTrkgYrd", "HY");
        assertThat(p.lastSent("VHYT25A").get("VHYT25A_CRIT")).isEqualTo(oldCrit);
        assertThat(p.lastSent("VHYT30A").get("VHYT30A_CRIT")).isEqualTo(sent30.get("VHYT30A_CRIT"));

        List<List<String>> oldShop = rows(w, "VAGen_Container_Details_DATA");
        assertThat(oldShop).hasSize(r.cars().size());
        for (int i = 0; i < r.cars().size(); i++) {
            YardQueriesShopStatus.Car c = r.cars().get(i);
            String row = String.join("|", oldShop.get(i));
            assertThat(row).as("row " + i).contains(c.leftEnd() + c.car() + c.rightEnd());
            if (c.elapsedDays() != null) assertThat(row).as("row " + i).contains(String.format("%03d-Days %02d-Hrs", c.elapsedDays(), c.elapsedHours()));
        }
        // the average from the old record: the engine does not refresh Text5 when AVERAGE_TIME's parts are written
        @SuppressWarnings("unchecked") String report = ((List<Map<String, Object>>) old.state().get("windows")).stream()
                .filter(x -> x.get("screen").toString().equalsIgnoreCase("VHYT25I")).map(x -> x.get("id").toString()).findFirst().orElseThrow();
        assertThat(String.valueOf(old.window(report).recordPart("VHYT25IW").get("AVERAGE_TIME data")).strip())
                .isEqualTo(String.format("%03d-Days %02d-Hrs", r.averageDays(), r.averageHours()));
        assertThat(Integer.parseInt(String.valueOf(w.get("Text4").get("value")).strip())).isEqualTo(r.totalCars());
        assertThat(Integer.parseInt(String.valueOf(w.get("Text7").get("value")).strip())).isEqualTo(r.grandTotal());

        List<List<String>> oldOut = rows(w, "VAGen_Container_Details_DATA1");
        assertThat(oldOut).hasSize(r.outOfYard().cars().size());
        for (int i = 0; i < oldOut.size(); i++) {
            YardQueriesShopStatus.OutCar c = r.outOfYard().cars().get(i);
            String row = String.join("|", oldOut.get(i));
            assertThat(row).as("out row " + i).contains(c.origin()).contains(c.dateIn()).contains(c.leftEnd() + c.car() + c.rightEnd())
                    .contains(String.format("%02d-Day %02d-Hrs", c.elapsedDays(), c.elapsedHours()));
        }
    }
}
