package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesFleetReportService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fleet report (VHFR01I) against the old screen run by the engine. */
class VehicleQueriesFleetReportParityTest {
    static Map<String, Object> car(String type, String ato, String stat, String where, String war, String loc) {
        return Map.of("carType", Map.of("carType1", type), "atoPresentInd", ato, "vehStat", stat, "whereIsCar", where, "warInd", war, "currLoc", loc);
    }

    /** every branch of VHFR01IS-TYPE-*, C with and without ATO, every yard of the GO table, and the L / M quirk */
    static final List<Map<String, Object>> CARS = List.of(
            car("A", "N", "STOR", "YRD", "N", "CY"), car("A", "N", "GO", "CON", "N", "CY"), car("A", "N", "NO GO", "YRD", "Y", "CY"),
            car("B", "N", "NO GO", "YRD", "N", "HY"), car("C", "Y", "AWPART", "YRD", "N", "HY"), car("C", "N", "PM", "YRD", "N", "RY"),
            car("D", "N", "ENGHLD", "YRD", "N", "DY"), car("E", "N", "MODHLD", "YRD", "N", "DY"), car("L", "N", "MISC", "YRD", "N", "CY"),
            car("M", "N", "GO", "YRD", "N", "HY"), car("K", "N", "USABLE", "YRD", "N", "RY"), car("C", "Y", "GO", "YRD", "N", "DY"),
            car("C", "N", "GO", "YRD", "N", "OAK"), car("D", "N", "INSP", "YRD", "N", "CY"), car("X", "N", "GO", "YRD", "N", "CY"),
            car("L", "N", "GO", "YRD", "N", "CY"), car("E", "N", "ERROR", "YRD", "N", "SFO"));

    static Parity backend(int urc) {
        Parity p = new Parity();
        p.on("VHFR01A", in -> urc != 0 ? Map.of("XDCONTROL", Map.of("urc", urc))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", CARS.size()), "VHFR01A_DATA", Map.of("dataRow", CARS)));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHFR01C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesFleetReportService api(Parity p) {
        return new VehicleQueriesFleetReportService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widgets(ScreenSession s) {
        return (Map<String, Object>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets");
    }

    @SuppressWarnings("unchecked")
    static List<List<String>> rows(ScreenSession s, String table) {
        Object r = ((Map<String, Object>) widgets(s).get(table)).get("rows");
        return r == null ? List.of() : (List<List<String>>) r;
    }

    static List<String> flat(String name, VehicleQueriesFleetReportService.Cells c) {
        List<String> out = new ArrayList<>();
        out.add(name);
        c.byType().forEach(n -> out.add(String.valueOf(n)));
        out.add(String.valueOf(c.total()));
        return out;
    }

    @Test
    void sameTablesAsTheOldScreen() {
        Parity p = backend(0);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHFR01I", p.user);
        List<List<String>> oldRows = rows(s, "cntFleetSummary"), oldYards = rows(s, "cntAvailGoCars");
        var f = api(p).report();
        assertThat(f.rows()).hasSize(oldRows.size());
        for (int i = 0; i < oldRows.size(); i++) {
            assertThat(flat(f.rows().get(i).name(), f.rows().get(i).cells())).as("row " + (i + 1)).isEqualTo(oldRows.get(i).stream().map(String::strip).toList());
        }
        assertThat(f.goCarsByYard()).hasSize(oldYards.size());
        for (int i = 0; i < oldYards.size(); i++) {
            assertThat(flat(f.goCarsByYard().get(i).yard(), f.goCarsByYard().get(i).cells())).as("yard row " + (i + 1))
                    .isEqualTo(oldYards.get(i).stream().map(String::strip).toList());
        }
    }

    @Test
    void noCarsLeavesBothTablesEmpty() {
        Parity p = backend(100);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHFR01I", p.user);
        assertThat(rows(s, "cntFleetSummary")).isEmpty();
        var f = api(p).report();
        assertThat(f.rows()).isEmpty();
        assertThat(f.goCarsByYard()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hardCopySendsTheSameRecord() {
        Parity p = backend(0);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHFR01I", p.user);
        String w = (String) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("id");
        s.event(w, List.of(), "Container_Details12", "select", 1);
        s.event(w, List.of(), "pbtHardCopy", "clicked", null);
        Map<String, Object> sent = p.lastSent("VHFR01C").get("VHFR01C_CRIT");
        Object d = s.state().get("dialog");
        p.clear();
        String message = api(p).print("DUBLNHP1");
        assertThat(p.lastSent("VHFR01C").get("VHFR01C_CRIT")).isEqualTo(sent);
        assertThat(message).isEqualTo(String.valueOf(((Map<?, ?>) d).get("message")).strip());
    }
}
