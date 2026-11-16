package com.bart.maris.middleware.api.yardtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.yardtracking.YardTrackingPseudoTrain;
import com.bart.maris.middleware.domain.yardtracking.YardTrackingPseudoTrainService;
import com.bart.maris.middleware.engine.screen.RecordPart;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The pseudo status train (old VHYT09I) and its printer window (VHYT01I) against the old screens run by the engine: same
 * backend, same yard chosen, same consists, cars, colours and message on every page, same records sent.
 */
class YardTrackingPseudoTrainParityTest {
    static final String[] STATUS = {"GO", "USABLE", "NO GO", "ERROR"};

    /** five consists of 1..4 cars (11 cars), VHYT09C's cars in VHYT09B's order unless `broken` names a consist to drop */
    static Parity backend(String broken) {
        Parity p = new Parity();
        p.on("VHYT09A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3),
                "VHYT09A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "HY"), Map.of("yrd", "RY"), Map.of("yrd", "CY")))));
        List<Map<String, Object>> cons = new ArrayList<>(), cars = new ArrayList<>();
        int n = 0;
        for (int c = 0; c < 5; c++) {
            int size = 1 + c % 4;
            String con = String.valueOf(201 + c);
            cons.add(Map.of("conNo", con, "maxPosnNo", size, "yrdSubLoc", "T-1" + c, "yrdTrkgYrd", "HY", "dateLastUpd", "2026-11-1" + c,
                    "timeLastUpd", "10.0" + c + ".00"));
            if (con.equals(broken)) continue;
            for (int k = 0; k < size; k++, n++) {
                cars.add(Map.of("conNo", con, "carNoX", Map.of("lftOr", k == 0 ? "<" : " ", "carNo", String.valueOf(1101 + n), "rgtOr", k == size - 1 ? ">" : " "),
                        "homeYrd", n % 2 == 0 ? "HY" : "RY", "vehStat", STATUS[n % 4], "posnNo", k + 1));
            }
        }
        p.on("VHYT09B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cons.size()), "VHYT09B_DATA", Map.of("dataRow", cons)));
        p.on("VHYT09C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", cars.size()), "VHYT09C_DATA", Map.of("dataRow", cars)));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow",
                List.of(Map.of("prtrLocDesc", "SHOP PRINTER", "vtamName", "PRT1"), Map.of("prtrLocDesc", "YARD PRINTER", "vtamName", "PRT2")))));
        p.on("VHYT01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 0), "VHYT01A_DATA", Map.of()));
        return p;
    }

    static YardTrackingPseudoTrainService api(Parity p) {
        return new YardTrackingPseudoTrainService(p.legacy(), p.messages(), Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    static String id(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return (String) w.get("id");
        return null;
    }

    /** what a widget of the old window shows (the message box Text1 shows FINAL-MESSAGE) */
    @SuppressWarnings("unchecked")
    static String shown(ScreenSession s, String screen, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!screen.equalsIgnoreCase((String) w.get("screen"))) continue;
            Object v = ((Map<String, Map<String, Object>>) w.get("widgets")).get(widget).get("value");
            return v == null ? "" : v.toString().strip();
        }
        return null;
    }

    static String text(RecordPart r, String field) {
        Object v = r.get(field + " data");
        return v == null ? "" : v.toString().strip();
    }

    /** the old page's consists as "con track date time | car/home/colour ..." */
    static List<String> oldPage(ScreenSession s, String id) {
        List<String> out = new ArrayList<>();
        for (int c = 1; c <= 4; c++) {
            RecordPart r = s.window(id).recordPart("VHYT09IW-CON-" + c);
            if (text(r, "CON-NO").isEmpty()) continue;
            StringBuilder b = new StringBuilder(text(r, "CON-NO") + " " + text(r, "YRD-SUB-LOC") + " " + text(r, "DATE-LAST-UPD") + " " + text(r, "TIME-LAST-UPD") + " |");
            for (int k = 1; k <= 10; k++) {
                if (!text(r, "CAR-NO-" + k).isEmpty()) b.append(' ').append(k).append(':').append(text(r, "CAR-NO-" + k)).append('/')
                        .append(text(r, "HOME-YRD-" + k)).append('/').append(text(r, "COLOR-" + k));
            }
            out.add(b.toString());
        }
        return out;
    }

    static List<String> page(YardTrackingPseudoTrain.Report r, int page, int size) {
        List<String> out = new ArrayList<>();
        for (int i = (page - 1) * size; i < Math.min(r.consists().size(), page * size); i++) {
            var c = r.consists().get(i);
            StringBuilder b = new StringBuilder(c.consist() + " " + c.track() + " " + c.dateLastUpdated() + " " + c.timeLastUpdated() + " |");
            for (var car : c.cars()) b.append(' ').append(car.position()).append(':').append(car.car()).append('/').append(car.homeYard()).append('/').append(car.colour());
            out.add(b.toString());
        }
        return out;
    }

    static ScreenSession old(Parity p, String yard) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHYT09I", p.user);
        String id = id(s, "VHYT09I");
        if (yard != null) {
            s.window(id).recordPart("VHYT09IW").set("YRD-TRKG-YRD data", yard);   // the drop-down's selectedItem
            s.event(id, List.of(), "pbList", "clicked", null);
        }
        return s;
    }

    @Test
    void optionsMatchTheOldScreen() {
        Parity p = backend(null);
        ScreenSession s = old(p, null);
        String id = id(s, "VHYT09I");
        var o = api(p).options(null);
        assertThat(o.message().strip()).isEqualTo(shown(s, "VHYT09I", "Text1"));
        assertThat(o.yard()).isEqualTo(text(s.window(id).recordPart("VHYT09IW"), "YRD-TRKG-YRD"));
        assertThat(o.yards()).endsWith("ALL").startsWith("HY", "RY", "CY");
        assertThat(api(p).options("RY").yard()).isEqualTo("RY");
    }

    @Test
    void consistsMatchTheOldScreenOnEveryPage() {
        for (String yard : List.of("HY", "ALL")) {
            Parity p = backend(null);
            ScreenSession s = old(p, yard);
            String id = id(s, "VHYT09I");
            var oldB = p.lastSent("VHYT09B").get("VHYT09B_CRIT");
            var oldC = p.lastSent("VHYT09C").get("VHYT09C_CRIT");
            List<List<String>> oldPages = new ArrayList<>();
            oldPages.add(oldPage(s, id));
            s.event(id, List.of(), "Push_Button4", "clicked", null);
            oldPages.add(oldPage(s, id));
            String oldMessage = shown(s, "VHYT09I", "Text1");
            p.clear();
            var svc = api(p);
            var r = svc.consists(yard);
            assertThat(p.lastSent("VHYT09B").get("VHYT09B_CRIT")).as("VHYT09B criteria").isEqualTo(oldB);
            assertThat(p.lastSent("VHYT09C").get("VHYT09C_CRIT")).as("VHYT09C criteria").isEqualTo(oldC);
            assertThat(r.message()).isEqualTo(oldMessage);
            assertThat(page(r, 1, svc.pageSize())).as(yard + " page 1").isEqualTo(oldPages.get(0)).isNotEmpty();
            assertThat(page(r, 2, svc.pageSize())).as(yard + " page 2").isEqualTo(oldPages.get(1)).isNotEmpty();
            assertThat(r.printable()).isTrue();
        }
    }

    @Test
    void aChangedDatabaseAndAnEmptyYardMatchTheOldScreen() {
        for (String broken : List.of("201", "203")) {            // the first consist, then a later one, has no cars in VHYT09C
            Parity p = backend(broken);
            ScreenSession s = old(p, "HY");
            String id = id(s, "VHYT09I");
            List<String> oldShown = oldPage(s, id);
            String oldMessage = shown(s, "VHYT09I", "Text1");
            p.clear();
            var svc = api(p);
            var r = svc.consists("HY");
            assertThat(r.message()).as("broken " + broken).isEqualTo(oldMessage);
            assertThat(page(r, 1, svc.pageSize())).as("broken " + broken).isEqualTo(oldShown);
        }
        Parity p = backend(null);
        p.on("VHYT09B", in -> Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0), "VHYT09B_DATA", Map.of()));
        ScreenSession s = old(p, "ALL");
        String oldMessage = shown(s, "VHYT09I", "Text1");
        var r = api(p).consists("ALL");
        assertThat(r.message()).isEqualTo(oldMessage);
        assertThat(r.consists()).isEmpty();
        assertThat(r.printable()).isFalse();
    }

    @Test
    void printMatchesTheOldPrinterWindow() {
        Parity p = backend(null);
        ScreenSession s = old(p, "RY");
        String id = id(s, "VHYT09I");
        s.event(id, List.of(), "pbPrint", "clicked", null);
        String pid = id(s, "VHYT01I");
        assertThat(pid).as("VHYT01I opened").isNotNull();
        s.event(pid, List.of(), "cntPrinterdet", "select", 2);
        s.event(pid, List.of(), "Push_Button1", "clicked", null);
        Map<String, Object> oldCrit = p.lastSent("VHYT01A") == null ? null : p.lastSent("VHYT01A").get("VHYT01A_CRIT");
        assertThat(oldCrit).as("old VHYT01A sent").isNotNull();
        p.clear();
        var svc = api(p);
        var printers = svc.printers();
        assertThat(printers).extracting(YardTrackingPseudoTrain.Printer::vtam).containsExactly("PRT1", "PRT2");
        svc.print("RY", printers.get(1).vtam());
        assertThat(new HashMap<>(p.lastSent("VHYT01A").get("VHYT01A_CRIT"))).isEqualTo(new HashMap<>(oldCrit));
    }
}
