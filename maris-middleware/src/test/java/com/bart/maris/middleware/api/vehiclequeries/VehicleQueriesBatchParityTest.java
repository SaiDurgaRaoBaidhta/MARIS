package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService.PrintRequest;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService.Report;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every batch print report against its old screen run by the engine: the same scripted backend, the same input, the
 * same record sent to the report program, or the same message. The old screen's widgets are found from its own wiring.
 */
class VehicleQueriesBatchParityTest {
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    static final LocalDate TODAY = LocalDate.now();

    static Stream<Report> reports() {
        return VehicleQueriesBatchReportService.REPORTS.stream();
    }

    /** a backend where AFCXVLD answers per date: "bad" dates are incorrect, "sql" dates fail with an SQL error */
    static Parity backend(Report r, Set<String> bad, Set<String> sql, int submitUrc) {
        Parity p = new Parity();
        if (r.fromProgram() != null) {
            p.on(r.fromProgram(), in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), r.fromProgram() + "_DATA", Map.of("fromDate", TODAY.minusDays(30).toString())));
            p.on(r.toProgram(), in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), r.toProgram() + "_DATA", Map.of("toDate", TODAY.minusDays(1).toString())));
        }
        if (r.yardProgram() != null) p.on(r.yardProgram(), in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), r.yardProgram() + "_DATA", Map.of("dataRow", List.of(
                Map.of("yrdDrop", Map.of("yrd", "CY", "yrdDash", "-", "yrdDesc", "CONCORD YARD")),
                Map.of("yrdDrop", Map.of("yrd", "HY", "yrdDash", "-", "yrdDesc", "HAYWARD YARD"))))));
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ")))));
        p.on("AFCXVLD", in -> {
            String d = String.valueOf(in.get("AFCXVLD_CRIT").get("formDate")).strip();
            Map<String, Object> c = new HashMap<>(in.get("AFCXVLD_CRIT"));
            c.put("dateError", bad.contains(d) ? "Y" : "N");
            c.put("otherSqlError", sql.contains(d) ? "Y" : "N");
            return Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", c);
        });
        p.on(r.submitProgram(), in -> Map.of("XDCONTROL", Map.of("urc", submitUrc)));
        return p;
    }

    static VehicleQueriesBatchReportService api(Parity p) {
        return new VehicleQueriesBatchReportService(p.legacy(), p.messages(), com.bart.maris.middleware.support.TestCatalog.get(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    /** the old screen, opened, with its widgets found from its wiring */
    record Old(ScreenSession s, String window, Map<String, String> widget) {
        @SuppressWarnings("unchecked")
        Map<String, Object> state(String w) {
            return (Map<String, Object>) ((Map<String, Object>) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("widgets")).get(w);
        }

        String message() {
            Object d = s.state().get("dialog");
            if (d != null) return String.valueOf(((Map<?, ?>) d).get("message")).strip();
            Object v = state(widget.get("message")).get("value");
            return v == null ? "" : v.toString().strip();
        }

        @SuppressWarnings("unchecked")
        List<String> yards() {
            return ((List<Object>) state(widget.get("yard")).get("items")).stream().map(String::valueOf).toList();
        }

        void hardCopy(LocalDate from, LocalDate to, Integer yardIndex, String minutes) {
            s.event(window, List.of(), widget.get("printers"), "select", 1);
            if (yardIndex != null) s.event(window, List.of(), widget.get("yard"), "select", yardIndex);
            List<ScreenSession.Change> c = new ArrayList<>();
            if (widget.containsKey("from")) c.add(new ScreenSession.Change(widget.get("from"), "object", from == null ? "" : MDY.format(from)));
            if (widget.containsKey("to")) c.add(new ScreenSession.Change(widget.get("to"), "object", to == null ? "" : MDY.format(to)));
            if (minutes != null) c.add(new ScreenSession.Change(widget.get("minutes"), "object", minutes));
            s.event(window, c, widget.get("hardCopy"), "clicked", null);
        }
    }

    @SuppressWarnings("unchecked")
    static Old open(Parity p, Report r) {
        ScreenSession s = p.oldScreens();
        s.openRoot(r.screen(), p.user);
        String window = (String) ((List<Map<String, Object>>) s.state().get("windows")).get(0).get("id");
        Map<String, String> w = new HashMap<>();
        for (JsonNode c : Parity.bundle().screen(r.screen()).path("connections")) {
            JsonNode a = c.path("a");
            String src = a.path(0).path("id").asText(""), attr = a.path(1).asText(""), tgt = a.path(2).path("id").asText(""), tAttr = a.path(3).asText("");
            if (src.startsWith("screen.")) {
                String name = src.substring(7);
                if (tAttr.equals("FROM-DATE data")) w.put("from", name);
                if (tAttr.equals("TO-DATE data")) w.put("to", name);
                if (tAttr.equals("YRD-DROP data")) w.put("yard", name);
                if (tAttr.equals("MINUTES data")) w.put("minutes", name);
                if (attr.equals("selectionIndices") && tgt.endsWith("HARD-COPY__reference")) w.put("printers", name);
                if (attr.equals("clicked") && tgt.equalsIgnoreCase(r.validateFunction() + "__reference")) w.put("hardCopy", name);
            }
            if (tgt.startsWith("screen.") && c.path("m").asText().startsWith("connectAttributes")) {      // record -> widget
                String name = tgt.substring(7);
                if (attr.equals("MESG1 data")) w.put("message", name);
                if (attr.equals("FROM-DATE data")) w.putIfAbsent("from", name);
                if (attr.equals("TO-DATE data")) w.putIfAbsent("to", name);
                if (attr.equals("MINUTES data")) w.putIfAbsent("minutes", name);
            }
        }
        return new Old(s, window, w);
    }

    static String minutes(Report r) {
        return r.minutesFunction() == null ? null : "15";
    }

    static PrintRequest request(Report r, LocalDate from, LocalDate to, String yard) {
        return new PrintRequest(from == null ? "" : from.toString(), to == null ? "" : to.toString(), yard,
                r.minutesFunction() == null ? null : new BigDecimal(minutes(r)), "DUBLNHP1");
    }

    @ParameterizedTest
    @MethodSource("reports")
    void startValuesMatchTheOldScreen(Report r) {
        Parity p = backend(r, Set.of(), Set.of(), 0);
        Old old = open(p, r);
        var o = api(p).options(r.key());
        assertThat(o.button()).isNotBlank();
        if (r.yardProgram() != null) assertThat(o.yards().stream().map(y -> y.label()).toList()).isEqualTo(old.yards());
        if (r.fromProgram() == null) return;
        assertThat(o.fromDate()).isEqualTo(TODAY.minusDays(30).toString());
        assertThat(MDY.format(LocalDate.parse(o.fromDate()))).isEqualTo(String.valueOf(old.state(old.widget().get("from")).get("value")));
        assertThat(MDY.format(LocalDate.parse(o.toDate()))).isEqualTo(String.valueOf(old.state(old.widget().get("to")).get("value")));
        if (r.minutesFunction() != null) assertThat(o.minutes()).isEqualByComparingTo(String.valueOf(old.state(old.widget().get("minutes")).get("value")));
    }

    @ParameterizedTest
    @MethodSource("reports")
    void hardCopySendsTheSameRecord(Report r) {
        Parity p = backend(r, Set.of(), Set.of(), 0);
        Old old = open(p, r);
        LocalDate from = TODAY.minusDays(20), to = TODAY.minusDays(2);
        old.hardCopy(from, to, r.yardProgram() == null ? null : 2, minutes(r));
        Map<String, Object> sent = p.lastSent(r.submitProgram()).get(r.submitProgram() + "_CRIT");
        String oldMessage = old.message();
        assertThat(sent).as("the old screen submitted").isNotNull();

        p.clear();
        String message = api(p).print(r.key(), request(r, from, to, r.yardProgram() == null ? null : "HY"));
        assertThat(p.lastSent(r.submitProgram()).get(r.submitProgram() + "_CRIT")).isEqualTo(sent);
        assertThat(message).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @MethodSource("reports")
    void sameMessagesForBadDates(Report r) {
        if (r.fromProgram() == null) { same(r, Set.of(), Set.of(), 8, null, null); return; }
        LocalDate a = TODAY.minusDays(20), b = TODAY.minusDays(2);
        same(r, Set.of(), Set.of(), 0, b, a);                                // from after to
        same(r, Set.of(), Set.of(), 0, a, TODAY.plusDays(3));                // to after today
        same(r, Set.of(a.toString()), Set.of(), 0, a, b);                    // AFCXVLD: from incorrect
        same(r, Set.of(b.toString()), Set.of(), 0, a, b);                    // AFCXVLD: to incorrect
        same(r, Set.of(), Set.of(b.toString()), 0, a, b);                    // AFCXVLD: SQL error on the to date
        same(r, Set.of(), Set.of(a.toString()), 0, a, b);                    // AFCXVLD: SQL error on the from date
        same(r, Set.of(), Set.of(), 8, a, b);                                // the report program fails
    }

    private void same(Report r, Set<String> bad, Set<String> sql, int urc, LocalDate from, LocalDate to) {
        Parity p = backend(r, bad, sql, urc);
        Old old = open(p, r);
        old.hardCopy(from, to, r.yardProgram() == null ? null : 1, minutes(r));
        boolean oldSubmitted = p.lastSent(r.submitProgram()) != null;
        String oldMessage = old.message();

        p.clear();
        String message;
        try {
            message = api(p).print(r.key(), request(r, from, to, r.yardProgram() == null ? null : "CY"));
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(p.lastSent(r.submitProgram()) != null).as(r.screen() + " submits for " + from + ".." + to).isEqualTo(oldSubmitted);
        assertThat(message).as(r.screen() + " message for " + from + ".." + to).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @MethodSource("reports")
    void aRequiredYardIsAskedFor(Report r) {
        var o = api(backend(r, Set.of(), Set.of(), 0)).options(r.key());
        if (!o.yardRequired()) return;
        Parity p = backend(r, Set.of(), Set.of(), 0);
        assertThatThrownBy(() -> api(p).print(r.key(), request(r, TODAY.minusDays(9), TODAY.minusDays(2), ""))).isInstanceOf(InvalidRequestException.class);
        assertThat(p.lastSent(r.submitProgram())).isNull();
    }
}
