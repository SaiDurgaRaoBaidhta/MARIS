package com.bart.maris.middleware.api.yardqueries;

import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.TERMINAL;
import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.openOld;
import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.rows;
import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.value;
import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.widgets;
import static com.bart.maris.middleware.api.yardqueries.YardQueriesYardLocationParityTest.withoutControl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfService;
import com.bart.maris.middleware.domain.yardqueries.YardQueriesOutOfServiceService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The reason out of service report: the clean API against the old VHYT32I run by the engine, reached from VHYT27I's Display
 * for report 6 (detail) and 7 (summary, which the window presses Show Summary for itself). Compared: every record sent,
 * including the writing VHYT32B (the out of service periods it times) and VHYT32D (the reason totals it sorts), the detail
 * and summary tables, the heading and the report totals.
 */
class YardQueriesOutOfServiceParityTest {
    static Map<String, Object> change(String car, String track, String prev, String now, String date, String time) {
        Map<String, Object> m = new HashMap<>(Map.of("yrdSubLoc", track, "carNo", car, "moveReasonCdPrv", prev, "moveReasonCdNew", now,
                "dateUpdReason", date, "timeUpdReason", Map.of("timeInHhmm", time), "prvMoveReasonDsc", "WAS " + prev.strip(),
                "newMoveReasonDsc", "NOW " + now.strip(), "vehStat", "NO GO"));
        m.put("yrdSubLocPrev", track.equals("S-2") ? "S-9" : "");
        return m;
    }

    /** every branch of VHYT32IS-PREPARE: GO -> out (REASON-2), out -> out (REASON-3, same car and first of a car), out -> GO (REASON-4) */
    static final List<Map<String, Object>> CHANGES = List.of(change("1201", "S-1", "GO", "BR", "2026-11-03", "10.00"),
            change("1201", "S-1", "BR", "DR", "2026-11-04", "11.30"), change("1201", "S-1", "DR", "GO", "2026-11-05", "12.00"),
            change("1305", "S-2", "PM", "BR", "2026-11-02", "08.15"), change("1305", "S-2", "BR", "50", "2026-11-06", "09.45"),
            change("1410", "S-3", "DR", "GO", "2026-11-07", "07.00"), change("1522", "S-4", "50", "PM", "2026-11-08", "16.20"));

    static Parity backend(List<Map<String, Object>> changes) {
        Parity p = YardQueriesYardLocationParityTest.backend(List.of(), List.of());
        p.on("VHYT32A", in -> changes.isEmpty() ? Map.of("XDCONTROL", Map.of("urc", 100))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", changes.size()), "VHYT32A_DATA", Map.of("dataRow", changes)));
        // VHYT32B times each period (it stores them in MARIS's validation date table): days / hours / minutes per row
        p.on("VHYT32B", in -> {
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) in.get("VHYT32B_DATA").get("vagenData"));
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> r = new HashMap<>(rows.get(i));
                if (!String.valueOf(r.get("carNo")).isBlank()) r.putAll(Map.of("tsMo", i == 1 ? 2 : 0, "tsDd", i + 1, "tsHh", (i * 5) % 24, "tsMm", (i * 17) % 60));
                out.add(r);
            }
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHYT32B_CRIT", in.get("VHYT32B_CRIT"), "VHYT32B_DATA", Map.of("vagenData", out));
        });
        p.on("VHYT32C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 4), "VHYT32C_DATA", Map.of("dataRow", List.of(
                Map.of("moveReasonCd", "BR", "moveReasonDsc", "BRAKES"), Map.of("moveReasonCd", "DR", "moveReasonDsc", "DOORS"),
                Map.of("moveReasonCd", "PM", "moveReasonDsc", "PREVENTIVE"), Map.of("moveReasonCd", "XX", "moveReasonDsc", "NEVER USED")))));
        // VHYT32D sorts the reason totals by occurrences, high to low (through MARIS's report work table)
        p.on("VHYT32D", in -> {
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) in.get("VHYT32IW-REASONSUM").get("dataRow"));
            int n = ((Number) in.get("VHYT32D_CRIT").get("numOfRows")).intValue();
            List<Map<String, Object>> sorted = new ArrayList<>(rows.subList(0, Math.min(n, rows.size())));
            sorted.sort((a, b) -> Integer.compare(((Number) b.get("vagenCount")).intValue(), ((Number) a.get("vagenCount")).intValue()));
            return Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", sorted.size()), "VHYT32D_DATA", Map.of("dataRow", sorted));
        });
        return p;
    }

    static void same(int report, List<Map<String, Object>> changes) {
        Parity p = backend(changes);
        ScreenSession old = openOld(p, report, "HY", "11-01-2026", "11-10-2026");
        Map<String, Map<String, Object>> w = widgets(old, "VHYT32I");
        var oldSent = new ArrayList<>(p.sent);
        String oldYard = String.valueOf(p.lastSent("VHYT32A").get("VHYT32A_CRIT").get("yrdTrkgYrd"));

        p.clear();
        YardQueriesOutOfService r = new YardQueriesOutOfServiceService(p.legacy(), Parity.bundle(), p.clock, TERMINAL)
                .report(oldYard, "2026-11-01", "2026-11-10", report == 7, p.user.groupId());
        for (String program : List.of("VHYT32A", "VHYT32B", "VHYT32C", "VHYT32D")) {
            List<Object> o = new ArrayList<>(), n = new ArrayList<>();
            for (var e : oldSent) if (e.getKey().equals(program)) o.add(withoutControl(e.getValue()));
            for (var e : p.sent) if (e.getKey().equals(program)) n.add(withoutControl(e.getValue()));
            assertThat(n).as(program + " records sent").isEqualTo(o);
        }
        assertThat(r.heading()).isEqualTo(value(w, "Text2"));
        assertThat(r.shop()).isEqualTo(value(w, "Text4"));
        assertThat(r.from()).isEqualTo(value(w, "Text1"));
        assertThat(r.to()).isEqualTo(value(w, "Text6"));
        assertThat(r.detail().rows()).as("detail").isEqualTo(rows(w, "VAGen_Container_Details_DATA"));
        if (report == 7) assertThat(r.summary().rows()).as("summary").isEqualTo(rows(w, "VAGen_Container_Details_DATA_ROW"));
        else assertThat(r.summary()).isNull();
        // the totals from the old record: the engine does not refresh Text5 / Text7 when only their parts are written
        String id = old.state().get("windows") instanceof List<?> l ? l.stream().map(x -> (Map<?, ?>) x)
                .filter(x -> x.get("screen").toString().equalsIgnoreCase("VHYT32I")).map(x -> x.get("id").toString()).findFirst().orElseThrow() : "";
        var work = old.window(id).recordPart("VHYT32IWORK");
        if (changes.isEmpty()) {                                               // nothing timed: no totals (the old fields keep their start values)
            assertThat(r.totalDays()).isNull();
            assertThat(r.totalTime()).isNull();
        } else {
            assertThat(r.totalDays()).isEqualTo(String.valueOf(work.get("DAYS data")).strip());
            assertThat(r.totalTime()).isEqualTo(String.valueOf(work.get("DISPLAY-TOT-HRS-MINS data")).strip());
        }
    }

    @Test
    void detail() {
        same(6, CHANGES);
    }

    @Test
    void summary() {
        same(7, CHANGES);
    }

    @Test
    void summaryOfNothing() {
        same(7, List.of());
    }

    /** VHYT27I's dates are typed MM-DD-YYYY; the API takes yyyy-MM-dd and refuses anything else with VHYT27I's date message */
    @Test
    void aBadDateIsRefusedWithTheOldMessage() {
        Parity p = backend(CHANGES);
        var svc = new YardQueriesOutOfServiceService(p.legacy(), Parity.bundle(), p.clock, TERMINAL);
        assertThatThrownBy(() -> svc.report("HY", "11/01/2026", "2026-11-10", false, "G"))
                .isInstanceOf(InvalidRequestException.class).hasMessage(Parity.bundle().promptText("VHYT27I", "Message_Prompter1"));
        assertThat(p.sent).isEmpty();
    }
}
