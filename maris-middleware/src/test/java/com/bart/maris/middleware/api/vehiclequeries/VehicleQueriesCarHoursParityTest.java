package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarHoursService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
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

/** Car hours (VHCHR1I) against the old screen run by the engine. */
class VehicleQueriesCarHoursParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final LocalDate TODAY = LocalDate.now();

    static Parity backend(int carUrc, int listUrc, Set<String> bad) {
        Parity p = new Parity();
        p.on("VHINC6A", in -> Map.of("XDCONTROL", Map.of("urc", carUrc, "urowcnt", carUrc == 0 ? 1 : 0)));
        p.on("AFCXVLD", in -> {
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            c.put("dateError", bad.contains(String.valueOf(c.get("formDate")).strip()) ? "Y" : "N");
            c.put("otherSqlError", "N");
            return Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", c);
        });
        p.on("VHCHR1A", in -> listUrc != 0 ? Map.of("XDCONTROL", Map.of("urc", listUrc, "umsgins", List.of("1101", "", ""))) : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1),
                "VHCHR1A_DATA", Map.of("totalHrs", 1234, "totalMin", 56, "dateFrom", "10-01-2026", "dateTo", "10-31-2026")));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("VHCHR01", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesCarHoursService api(Parity p) {
        return new VehicleQueriesCarHoursService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> win(ScreenSession s, int i) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(i);
    }

    @SuppressWarnings("unchecked")
    static Object value(ScreenSession s, String widget) {
        return ((Map<String, Object>) ((Map<String, Object>) win(s, 0).get("widgets")).get(widget)).get("value");
    }

    static String dialog(ScreenSession s) {
        Object d = s.state().get("dialog");
        return d == null ? null : String.valueOf(((Map<?, ?>) d).get("message")).strip();
    }

    static ScreenSession old(Parity p, String button, String car, String from, String to, boolean printer) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCHR1I", p.user);
        String w = (String) win(s, 0).get("id");
        if (printer) s.event(w, List.of(), "cntPrinter", "select", 1);
        s.event(w, List.of(new ScreenSession.Change("txtCarno", "object", car), new ScreenSession.Change("txtFromdate", "object", from),
                new ScreenSession.Change("txtTodate", "object", to)), button, "clicked", null);
        return s;
    }

    static String mdy(String iso) {
        return iso.isEmpty() ? "" : MDY.format(LocalDate.parse(iso));
    }

    static String message(Supplier<?> call) {
        try {
            Object r = call.get();
            return r instanceof VehicleQueriesCarHoursService.CarHours h ? h.message() : r.toString();
        } catch (InvalidRequestException | LegacyBusinessException e) {
            return e.getMessage();
        }
    }

    @Test
    void listSendsTheSameCriteriaAndShowsTheSameHours() {
        String from = TODAY.minusDays(40).toString(), to = TODAY.minusDays(1).toString();
        Parity p = backend(0, 0, Set.of());
        ScreenSession s = old(p, "pbtDisplay", "1101", mdy(from), mdy(to), false);
        Map<String, Object> sent = p.lastSent("VHCHR1A").get("VHCHR1A_CRIT");
        p.clear();
        var h = api(p).list("1101", from, to);
        assertThat(p.lastSent("VHCHR1A").get("VHCHR1A_CRIT")).isEqualTo(sent);
        assertThat(String.valueOf(h.hours())).isEqualTo(value(s, "txtHours"));
        assertThat(String.valueOf(h.minutes())).isEqualTo(value(s, "txtMin"));
        assertThat(h.actualFrom()).isEqualTo(value(s, "txtFromdate1"));
        assertThat(h.actualTo()).isEqualTo(value(s, "txtTodate1"));
    }

    @Test
    void anEarlyFromDateBecomesTheEarliestOne() {
        Parity p = backend(0, 0, Set.of());
        old(p, "pbtDisplay", "1101", "06-30-1999", mdy(TODAY.minusDays(1).toString()), false);
        Map<String, Object> sent = p.lastSent("VHCHR1A").get("VHCHR1A_CRIT");
        p.clear();
        api(p).list("1101", "1999-06-30", TODAY.minusDays(1).toString());
        assertThat(p.lastSent("VHCHR1A").get("VHCHR1A_CRIT")).isEqualTo(sent);
    }

    @Test
    void nothingFoundShowsTheOldMessage() {
        String from = TODAY.minusDays(40).toString(), to = TODAY.minusDays(1).toString();
        Parity p = backend(0, 100, Set.of());
        ScreenSession s = old(p, "pbtDisplay", "1101", mdy(from), mdy(to), false);
        String oldMessage = dialog(s);
        assertThat(oldMessage).isNotBlank();
        p.clear();
        var h = api(p).list("1101", from, to);
        assertThat(h.message()).isEqualTo(oldMessage);
        assertThat(h.hours()).isZero();
    }

    @Test
    void sameMessagesAsTheOldScreen() {
        String a = TODAY.minusDays(20).toString(), b = TODAY.minusDays(2).toString();
        same(backend(0, 0, Set.of()), "", a, b);                                       // no car
        same(backend(0, 0, Set.of()), "0000", a, b);                                   // car 0
        same(backend(100, 0, Set.of()), "9999", a, b);                                 // car not found
        same(backend(0, 0, Set.of()), "1101", "", b);                                  // no from date
        same(backend(0, 0, Set.of(a)), "1101", a, b);                                  // from incorrect
        same(backend(0, 0, Set.of(b)), "1101", a, b);                                  // to incorrect
        same(backend(0, 0, Set.of()), "1101", a, TODAY.plusDays(3).toString());        // to after today
        same(backend(0, 0, Set.of()), "1101", b, a);                                   // from after to
    }

    private void same(Parity p, String car, String from, String to) {
        ScreenSession s = old(p, "pbtDisplay", car, mdy(from), mdy(to), false);
        String oldMessage = dialog(s);
        assertThat(oldMessage).as("old message for " + car + " " + from + ".." + to).isNotBlank();
        p.clear();
        assertThat(message(() -> api(p).list(car, from, to))).as(car + " " + from + ".." + to).isEqualTo(oldMessage);
    }

    @Test
    void hardCopySendsTheSameRecord() {
        String from = TODAY.minusDays(40).toString(), to = TODAY.minusDays(1).toString();
        Parity p = backend(0, 0, Set.of());
        ScreenSession s = old(p, "pbtHardcopy", "1101", mdy(from), mdy(to), true);
        Map<String, Object> sent = p.lastSent("VHCHR01").get("VHCHR01_CRIT");
        String oldMessage = dialog(s);
        p.clear();
        assertThat(api(p).print("1101", from, to, "DUBLNHP1")).isEqualTo(oldMessage);
        assertThat(p.lastSent("VHCHR01").get("VHCHR01_CRIT")).isEqualTo(sent);
    }
}
