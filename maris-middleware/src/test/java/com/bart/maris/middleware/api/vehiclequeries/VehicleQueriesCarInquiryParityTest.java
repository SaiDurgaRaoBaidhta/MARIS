package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarInquiryService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Car inquiry (VHSV07I asks for the car, VHSV06I shows it) against the old screens run by the engine. */
class VehicleQueriesCarInquiryParityTest {
    /** orientations chosen to hit the screen's tests, car 2 with a mark only VHSV06IS-MOVE-CON's odd test sees */
    static final List<Map<String, Object>> CARS = List.of(Map.of("carNo", "1101", "carOrientation", "<"), Map.of("carNo", "1102", "carOrientation", ">"),
            Map.of("carNo", "1103", "carOrientation", "v"), Map.of("carNo", "1104", "carOrientation", "B"), Map.of("carNo", "1105", "carOrientation", " "));

    static Parity backend(List<Map<String, Object>> cars) {
        Parity p = new Parity();
        p.on("VHSV06F", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06F_DATA", Map.of("fromDate", "2026-08-01", "toDate", "2026-11-12")));
        p.on("VHSV06A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06A_CRIT", Map.of("carNo", "1101", "rowCount", 1, "incdDateFrom", "2026-08-01",
                        "incdDateTo", "2026-11-12"),
                "VHSV06A_DATA_01", Map.of("currLoc", "CY01", "dispVehStat", Map.of("vehStat", "NO GO", "vehStatFiller", " T"), "homeYrd", "CY", "mtbi", 12),
                "VHSV06A_DATA_02", Map.of("carnoExt", "1101"),
                "VHSV06A_DATA_03", Map.of("dataRow", List.of(Map.of("incdNo", "29990010", "dateIncdOccr", "2026-10-02", "timeIncdOccr", "10:15:00", "sympCd", "0120",
                        "incdLoc", "CY01", "vehIncdStat", "OPN", "conDirection", "N", "cumHrs", 33, "deferredPriority", "")))));
        p.on("VHSV06B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06B_DATA", Map.of("carNo", "1101", "yrdSubLoc", "CY-T12", "moveReasonCd", "M1",
                "moveReasonDesc", "MOVED FOR PM")));
        p.on("VHSV06N", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06N_DATA", Map.of("dataRow", List.of(Map.of("narr", "DOOR STUCK")),
                "carRow", cars, "conNo", "201", "dashNo", 3)));
        p.on("VHSV06C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06C_CRIT", Map.of("incdNo", "29990010", "reprRowCount", 1),
                "VHSV06C_DATA", Map.of("dataRow", List.of(Map.of("reprDocNo", 2, "vehSystem", "DR", "vehSubSystem", "01", "reprDispCd", "R", "serNoOn", "ON1",
                        "serNoOff", "OFF1", "vehPrtCtlNo", "PCT1")))));
        p.on("VHNARY", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHNARY_DATA", Map.of("dataRow", List.of(Map.of("narr", "ADJUSTED")))));
        p.on("VHSV06D", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSV06D_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntFaultCd", "F1", "cmpntDocNo", 1, "cmpntCd", "DOR", "reprDispCd", "R", "serNoOff", "S1")))));
        return p;
    }

    static VehicleQueriesCarInquiryService api(Parity p) {
        return new VehicleQueriesCarInquiryService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> w = window(s, screen);
        if (w == null) return "(no " + screen + ")";
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
        if (x == null) return "(no " + widget + " in " + ((Map<String, Object>) w.get("widgets")).keySet() + ")";
        Object v = x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static ScreenSession old(Parity p, String car) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSV07I", p.user);
        s.event((String) window(s, "VHSV07I").get("id"), List.of(new ScreenSession.Change("Text1", "object", car)), "Push_Button1", "clicked", null);
        return s;
    }

    static List<Object> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent, String program, String record) {
        List<Object> out = new ArrayList<>();
        for (var e : sent) if (e.getKey().equals(program)) out.add(e.getValue().get(record));
        return out;
    }

    @Test
    void inquiryMatchesTheOldScreens() {
        Parity p = backend(CARS);
        ScreenSession s = old(p, "1101");
        assertThat(window(s, "VHSV06I")).as("VHSV06I opened: " + value(s, "VHSV07I", "Text2")).isNotNull();
        var oldCalls = new ArrayList<>(p.sent);
        p.clear();
        var svc = api(p);
        var inq = svc.inquiry("1101");
        var inc = svc.incident(inq.incidents().get(0).incident());
        var repairs = svc.repairs(inq.incidents().get(0).incident());
        var repair = svc.repair(inq.incidents().get(0).incident(), repairs.get(0).document());
        for (String[] pr : new String[][] {{"VHSV06A", "VHSV06A_CRIT"}, {"VHSV06N", "VHSV06N_CRIT"}, {"VHSV06C", "VHSV06C_CRIT"}, {"VHNARY", "VHNARY_CRIT"},
                {"VHSV06D", "VHSV06D_CRIT"}, {"VHSV06B", "VHSV06B_DATA"}}) {
            assertThat(sent(p.sent, pr[0], pr[1]).stream().distinct().toList()).as(pr[0]).isEqualTo(sent(oldCalls, pr[0], pr[1]).stream().distinct().toList());
        }
        assertThat(inq.car().status()).isEqualTo(value(s, "VHSV06I", "txt_VehStat"));
        assertThat(inq.car().location()).isEqualTo(value(s, "VHSV06I", "txtLoc"));
        assertThat(inq.moveReason().description()).isEqualTo(value(s, "VHSV06I", "txt_MoveReason_Des"));
        assertThat(inq.incidents().get(0).incident()).isEqualTo(value(s, "VHSV06I", "txtIncdNo"));
        assertThat(repairs.get(0).serialOn()).isEqualTo(value(s, "VHSV06I", "txtSernoon"));
        assertThat(repair.narrative()).containsExactly("ADJUSTED");
        for (int k = 0; k < CARS.size(); k++) assertThat(inc.cars().get(k)).as("car " + (k + 1)).isEqualTo(value(s, "VHSV06I", "txt_Car" + (k + 1)));
        assertThat(inc.consist().replace(" ", "")).isEqualTo(value(s, "VHSV06I", "txt_ConAndDash").replace(" ", ""));
        String version = new EglTexts(Parity.bundle()).assignedAll("VHSV06IS-RETDATA", "MRSVehicleQueries", "TITLE-9").get(0).replaceAll("\\s", "");
        assertThat(inq.title().replaceAll("\\s", "")).isEqualTo(String.valueOf(window(s, "VHSV06I").get("title")).replaceAll("\\s", "").replace(version, ""));
    }

    @Test
    void carChecksMatchTheOldScreen() {
        for (String car : List.of("", "11A1")) {
            Parity p = backend(CARS);
            ScreenSession s = old(p, car);
            String oldMessage = value(s, "VHSV07I", "Text2");
            assertThat(oldMessage).as("old message for '" + car + "'").isNotBlank();
            assertThat(window(s, "VHSV06I")).isNull();
            String message;
            try { api(p).check(car); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
            assertThat(message).isEqualTo(oldMessage);
        }
    }
}
