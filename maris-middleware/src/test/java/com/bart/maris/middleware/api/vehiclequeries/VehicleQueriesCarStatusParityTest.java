package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarStatusService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Car status history (VHCSH1I) and its print (VHCSH2I) against the old screens run by the engine. */
class VehicleQueriesCarStatusParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final LocalDate TODAY = LocalDate.now();

    static Map<String, Object> row(String loc, String date, String time, String stat) {
        Map<String, Object> m = new HashMap<>();
        m.put("carLoc", loc); m.put("dateStatChg", date); m.put("timeStatChg", time); m.put("cumHrs", 1234); m.put("cumMin", 45);
        m.put("vehStatNew", stat); m.put("vehStatPrev", "GO"); m.put("incdNo", "29990010"); m.put("sympCd", "B12");
        m.put("statChgActn", "STATUS CHANGED BY YARD TRACKING"); m.put("dvsImpactInd", "Y"); m.put("dateLastUpd", date);
        m.put("timeLastUpd", time); m.put("termLastUpd", "TERM01"); m.put("dateMaintUpd", date); m.put("timeMaintUpd", time);
        m.put("termMaintUpd", "TERM02"); m.put("marisFunc", "VHYT01");
        return m;
    }

    /** carUrc: VHINC6A's return code (0 = found); listUrc: VHCSH1A's; bad: dates AFCXVLD calls incorrect */
    static Parity backend(int carUrc, int listUrc, Set<String> bad) {
        Parity p = new Parity();
        p.on("VHINC6A", in -> Map.of("XDCONTROL", Map.of("urc", carUrc, "urowcnt", carUrc == 0 ? 1 : 0)));
        p.on("AFCXVLD", in -> {
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            c.put("dateError", bad.contains(String.valueOf(c.get("formDate")).strip()) ? "Y" : "N");
            c.put("otherSqlError", "N");
            return Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", c);
        });
        p.on("VHCSH1A", in -> listUrc != 0 ? Map.of("XDCONTROL", Map.of("urc", listUrc)) : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2),
                "VHCSH1A_DATA", Map.of("homeYrd", "CY", "dataRow", List.of(row("CY01", "2026-10-02", "10:15:00", "NO GO"), row("CY02", "2026-10-03", "11:20:00", "GO")))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHCSH2A", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesCarStatusService api(Parity p) {
        return new VehicleQueriesCarStatusService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
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

    static ScreenSession old(Parity p, String button, String car, String from, String to) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCSH1I", p.user);
        s.event((String) window(s, "VHCSH1I").get("id"), List.of(new ScreenSession.Change("txtCarNo", "object", car),
                new ScreenSession.Change("txtFromDate", "object", from), new ScreenSession.Change("txtToDate", "object", to)), button, "clicked", null);
        return s;
    }

    static String mdy(LocalDate d) {
        return MDY.format(d);
    }

    /** the API's answer as the old screen shows it: its message, or null when it lists */
    static String message(Supplier<?> call) {
        try {
            Object r = call.get();
            return r instanceof VehicleQueriesCarStatusService.History h ? h.message() : r.toString();
        } catch (InvalidRequestException | LegacyBusinessException e) {
            return e.getMessage();
        }
    }

    @Test
    void listSendsTheSameCriteriaAndShowsTheSameRows() {
        Parity p = backend(0, 0, Set.of());
        LocalDate from = TODAY.minusDays(40), to = TODAY.minusDays(1);
        ScreenSession s = old(p, "pbtList", "1101", mdy(from), mdy(to));
        Map<String, Object> sent = p.lastSent("VHCSH1A").get("VHCSH1A_CRIT");
        @SuppressWarnings("unchecked") List<List<String>> oldRows = (List<List<String>>) widget(s, "VHCSH1I", "cntCarStatDetails").get("rows");
        Object oldYard = widget(s, "VHCSH1I", "txtHomeYrd").get("value");

        p.clear();
        var h = api(p).list("1101", from.toString(), to.toString());
        assertThat(p.lastSent("VHCSH1A").get("VHCSH1A_CRIT")).isEqualTo(sent);
        assertThat(h.homeYard()).isEqualTo(oldYard);
        assertThat(h.changes()).hasSize(oldRows.size());
        for (int i = 0; i < oldRows.size(); i++) {
            var c = h.changes().get(i);
            List<String> o = oldRows.get(i);
            assertThat(o.get(0)).isEqualTo(c.location());
            assertThat(o.get(1)).isEqualTo(c.date());
            assertThat(c.time()).startsWith(o.get(2));                 // the old column shows the first 5 characters
            assertThat(o.get(3)).isEqualTo(String.valueOf(c.hours()));
            assertThat(o.get(5)).isEqualTo(c.statusNew());
            assertThat(c.action()).startsWith(o.get(9));
        }
    }

    @Test
    void anEarlyFromDateBecomesTheEarliestOne() {
        Parity p = backend(0, 0, Set.of());
        old(p, "pbtList", "1101", "06-30-1999", mdy(TODAY.minusDays(1)));
        Map<String, Object> sent = p.lastSent("VHCSH1A").get("VHCSH1A_CRIT");
        p.clear();
        api(p).list("1101", "1999-06-30", TODAY.minusDays(1).toString());
        assertThat(p.lastSent("VHCSH1A").get("VHCSH1A_CRIT")).isEqualTo(sent);
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        String a = TODAY.minusDays(20).toString(), b = TODAY.minusDays(2).toString();
        same(backend(0, 0, Set.of()), "", a, b);                                          // no car
        same(backend(0, 0, Set.of()), "11A1", a, b);                                      // car not a number
        same(backend(100, 0, Set.of()), "9999", a, b);                                    // car not found
        same(backend(0, 0, Set.of()), "1101", "", b);                                     // no from date
        same(backend(0, 0, Set.of(a)), "1101", a, b);                                     // from date incorrect
        same(backend(0, 0, Set.of(b)), "1101", a, b);                                     // to date incorrect
        same(backend(0, 0, Set.of()), "1101", TODAY.plusDays(2).toString(), TODAY.plusDays(3).toString());   // from after today
        same(backend(0, 0, Set.of()), "1101", a, TODAY.plusDays(3).toString());           // to after today
        same(backend(0, 0, Set.of()), "1101", b, a);                                      // to before from
        same(backend(0, 100, Set.of()), "1101", a, b);                                    // nothing listed
    }

    private void same(Parity p, String car, String from, String to) {
        ScreenSession s = old(p, "pbtList", car, from.isEmpty() ? "" : mdy(LocalDate.parse(from)), to.isEmpty() ? "" : mdy(LocalDate.parse(to)));
        String oldMessage = dialog(s);
        assertThat(oldMessage).as("the old screen shows a message for " + car + " " + from + ".." + to).isNotBlank();
        p.clear();
        assertThat(message(() -> api(p).list(car, from, to))).as(car + " " + from + ".." + to).isEqualTo(oldMessage);
    }

    @Test
    void printSendsTheSameRecord() {
        Parity p = backend(0, 0, Set.of());
        LocalDate from = TODAY.minusDays(40), to = TODAY.minusDays(1);
        ScreenSession s = old(p, "pbtPrint", "1101", mdy(from), mdy(to));
        String printer = (String) window(s, "VHCSH2I").get("id");
        s.event(printer, List.of(), "cntPrinterdet", "select", 1);
        s.event(printer, List.of(), "pbtHardcopy", "clicked", null);
        Map<String, Object> sent = p.lastSent("VHCSH2A").get("VHCSH2A_CRIT");
        String oldMessage = dialog(s);

        p.clear();
        String message = api(p).print("1101", from.toString(), to.toString(), "DUBLNHP1");
        assertThat(p.lastSent("VHCSH2A").get("VHCSH2A_CRIT")).isEqualTo(sent);
        assertThat(message).isEqualTo(oldMessage);
    }

    @Test
    void printChecksLikeTheOldScreen() {
        String a = TODAY.minusDays(20).toString(), b = TODAY.minusDays(2).toString();
        for (String[] c : new String[][] {{"", a, b}, {"1101", b, a}, {"1101", a, TODAY.plusDays(4).toString()}}) {
            Parity p = backend(0, 0, Set.of());
            ScreenSession s = old(p, "pbtPrint", c[0], mdy(LocalDate.parse(c[1])), mdy(LocalDate.parse(c[2])));
            assertThat(window(s, "VHCSH2I")).as("the old screen opens no printer window").isNull();
            assertThat(dialog(s)).isNotBlank();
            assertThat(message(() -> api(p).print(c[0], c[1], c[2], "DUBLNHP1"))).isEqualTo(dialog(s));
            assertThat(p.lastSent("VHCSH2A")).isNull();
        }
    }
}
