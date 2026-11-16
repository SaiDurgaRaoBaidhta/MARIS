package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesRepeaterCarsService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.bart.maris.middleware.support.TestCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Repeater cars (VHRC01I criteria, VHRC02I list) against the old screens run by the engine. */
class VehicleQueriesRepeaterCarsParityTest {
    static Map<String, Object> incident(String car, String stat, String ind, String no) {
        Map<String, Object> m = new HashMap<>();
        m.put("carNo", car); m.put("currLoc", "CY01"); m.put("dispVehStat", Map.of("vehStat", stat, "vehStatFiller", " ", "vehIncdInd", ind));
        m.put("incdNo", no); m.put("dateIncdOccr", "2026-10-02"); m.put("timeIncdOccr", "10:15"); m.put("hrsBetIncds", 12); m.put("hrsFromLastIncd", 30);
        m.put("sympCd", "B1");
        return m;
    }

    static Parity backend(int carUrc, int total) {
        Parity p = new Parity();
        p.on("VHYS01A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHYS01A_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY")))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHCI01B", in -> Map.of("XDCONTROL", Map.of("urc", carUrc, "urowcnt", carUrc == 0 ? 1 : 0)));
        p.on("VHRC02A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHRC02A_DATA", Map.of("totalIncds", total, "dataRow", List.of(
                incident("1101", "NO GO", "T", "29990010"), incident("1102", "GO", "", "29990011")))));
        p.on("VHRC011", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesRepeaterCarsService api(Parity p) {
        return new VehicleQueriesRepeaterCarsService(p.legacy(), p.messages(), TestCatalog.get(), new EglTexts(Parity.bundle()));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String widget) {
        return (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
    }

    static String dialog(ScreenSession s) {
        Object d = s.state().get("dialog");
        return d == null ? null : String.valueOf(((Map<?, ?>) d).get("message")).strip();
    }

    @SuppressWarnings("unchecked")
    static ScreenSession old(Parity p, String yard, String car, String button, boolean printer) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHRC01I", p.user);
        String w = (String) window(s, "VHRC01I").get("id");
        List<String> items = (List<String>) widget(s, "VHRC01I", "ddlHomeYard").get("items");
        if (!yard.isEmpty()) s.event(w, List.of(), "ddlHomeYard", "select", items.indexOf(yard) + 1);
        if (printer) s.event(w, List.of(), "cntPrinterdet", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtCarNo", "object", car)), button, "clicked", null);
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void optionsAndListMatchTheOldScreens() {
        Parity p = backend(0, 501);
        ScreenSession s = old(p, "CY", "1101", "pbtDisplay", false);
        List<String> oldYards = ((List<Object>) widget(s, "VHRC01I", "ddlHomeYard").get("items")).stream().map(String::valueOf).filter(x -> !x.isBlank()).toList();
        Map<String, Object> sent = p.lastSent("VHRC02A").get("VHRC02A_CRIT");
        List<List<String>> oldRows = (List<List<String>>) widget(s, "VHRC02I", "cntIncdreprdet").get("rows");
        Object oldTotal = widget(s, "VHRC02I", "txtTotalincd").get("value");

        p.clear();
        assertThat(api(p).options().homeYards()).isEqualTo(oldYards);
        Parity fresh = backend(0, 2);
        ScreenSession opened = fresh.oldScreens();
        opened.openRoot("VHRC01I", fresh.user);
        assertThat(api(fresh).options().homeYard()).isEqualTo(widget(opened, "VHRC01I", "ddlHomeYard").get("value") == null
                ? ((List<String>) widget(opened, "VHRC01I", "ddlHomeYard").get("items")).get(((List<Integer>) widget(opened, "VHRC01I", "ddlHomeYard").get("selected")).get(0) - 1)
                : widget(opened, "VHRC01I", "ddlHomeYard").get("value"));
        var list = api(p).list("CY", "1101");
        assertThat(p.lastSent("VHRC02A").get("VHRC02A_CRIT")).isEqualTo(sent);
        assertThat(String.valueOf(list.total())).isEqualTo(oldTotal);
        assertThat(list.footer()).isNotNull();
        for (int i = 0; i < oldRows.size(); i++) {
            var r = list.incidents().get(i);
            assertThat(List.of(r.car(), r.location(), r.status(), r.incident(), r.date(), r.time(), String.valueOf(r.hoursBetween()), String.valueOf(r.hoursFromLast())))
                    .isEqualTo(oldRows.get(i).subList(0, 8).stream().map(String::stripTrailing).toList());
        }
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        same(backend(0, 2), "ALL", "");                                   // all yards: use Hard Copy
        same(backend(0, 2), "CY", "11A");                                 // car not numeric
        same(backend(100, 2), "CY", "9999");                              // car not found
    }

    private void same(Parity p, String yard, String car) {
        ScreenSession s = old(p, yard, car, "pbtDisplay", false);
        String oldMessage = dialog(s);
        assertThat(oldMessage).as("old message for " + yard + "/" + car).isNotBlank();
        p.clear();
        String message;
        try { api(p).list(yard, car); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(p.lastSent("VHRC02A")).isNull();
    }

    @Test
    void allCarsOfAYardAndHardCopy() {
        Parity p = backend(0, 2);
        ScreenSession s = old(p, "HY", "", "pbtDisplay", false);
        Map<String, Object> sent = p.lastSent("VHRC02A").get("VHRC02A_CRIT");
        assertThat(window(s, "VHRC02I")).isNotNull();
        p.clear();
        api(p).list("HY", "");
        assertThat(p.lastSent("VHRC02A").get("VHRC02A_CRIT")).isEqualTo(sent);

        Parity q = backend(0, 2);
        ScreenSession h = old(q, "ALL", "", "pbtHardcopy", true);
        Map<String, Object> printed = q.lastSent("VHRC011").get("VHRC011_CRIT");
        String oldMessage = dialog(h);
        q.clear();
        assertThat(api(q).print("ALL", "", "DUBLNHP1")).isEqualTo(oldMessage);
        assertThat(q.lastSent("VHRC011").get("VHRC011_CRIT")).isEqualTo(printed);
    }
}
