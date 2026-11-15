package com.bart.maris.middleware.api.vehiclequeries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesCarStatusChangeService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesConsistOrientationService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesDncSheetService;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesScramService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.legacy.layout.ServiceDefinition;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Consist orientation (VHCO01I), D and C sheet reprint (VHDC01I), SCRAM (VHSCM1I) and car status change (VHCHG1I)
 * against the old screens run by the engine, on the same scripted backend.
 */
class VehicleQueriesMiscParityTest {
    static final String TERMINAL = "MARIS-UI";                                        // the engine's terminal id
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    static EglTexts texts() {
        return new EglTexts(Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> win(ScreenSession s) {
        return ((List<Map<String, Object>>) s.state().get("windows")).get(0);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String w) {
        return (Map<String, Object>) ((Map<String, Object>) win(s).get("widgets")).get(w);
    }

    static String value(ScreenSession s, String w) {
        Object v = widget(s, w) == null ? null : widget(s, w).get("value");
        return v == null ? "" : v.toString().strip();
    }

    static String dialog(ScreenSession s) {
        Object d = s.state().get("dialog");
        return d == null ? null : String.valueOf(((Map<?, ?>) d).get("message")).strip();
    }

    static String message(Supplier<?> call) {
        try { return String.valueOf(call.get()); } catch (InvalidRequestException e) { return e.getMessage(); }
    }

    static ScreenSession open(Parity p, String screen) {
        ScreenSession s = p.oldScreens();
        s.openRoot(screen, p.user);
        return s;
    }

    static String id(ScreenSession s) {
        return (String) win(s).get("id");
    }

    /** every service call with its plain (string) arguments, e.g. printDnC */
    static List<List<Object>> serviceCalls(Parity p, String function) {
        List<List<Object>> out = new ArrayList<>();
        for (var inv : mockingDetails(p.client).getInvocations()) {
            if (!inv.getMethod().getName().equals("call")) continue;
            ServiceDefinition def = inv.getArgument(0);
            if (!def.function().equals(function)) continue;
            List<Object> args = new ArrayList<>(inv.getArgument(1));
            args.removeIf(a -> !(a instanceof String));
            out.add(args);
        }
        return out;
    }

    // ---- VHCO01I -----------------------------------------------------------------------------------------------

    static Parity orientationBackend() {
        Parity p = new Parity();
        p.on("WS70A01", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "WS70A01_DATA", Map.of("dataRow", List.of(
                Map.of("prtrName", "P1", "vtamName", "DUBLNHP1", "prtrLocDesc", "DUBLIN TMZ"), Map.of("prtrName", "P2", "vtamName", "HAYWHP2", "prtrLocDesc", "HAYWARD")))));
        p.on("VHHPMA3", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHHPMA3_DATA", Map.of("timeRevStart", "03.00.00", "currentDate", "2026-11-14",
                "currentTime", "02.00.00", "yesterday", "2026-11-13")));
        p.on("VHSV011", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSV011_DATA", Map.of("fromDate", "2026-08-16", "toDate", "2026-11-14")));
        p.on("VHCO011", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesConsistOrientationService orientation(Parity p) {
        return new VehicleQueriesConsistOrientationService(p.legacy(), p.messages(), texts());
    }

    static ScreenSession orientationPrint(Parity p, String from, String to) {
        ScreenSession s = open(p, "VHCO01I");
        s.event(id(s), List.of(), "Container_Details1", "select", 2);
        s.event(id(s), List.of(new ScreenSession.Change("txtFromdate", "object", from), new ScreenSession.Change("txtTodate", "object", to)), "pbtHardcopy", "clicked", null);
        return s;
    }

    @Test
    void orientationStartsWithTheSameDatesAndPrinters() {
        Parity p = orientationBackend();
        ScreenSession s = open(p, "VHCO01I");
        var o = orientation(p).options();
        assertThat(LocalDate.parse(o.fromDate()).format(MDY)).isEqualTo(value(s, "Text2"));
        assertThat(LocalDate.parse(o.toDate()).format(MDY)).isEqualTo(value(s, "Text3"));     // before the revenue day starts: yesterday
        assertThat(o.printers()).extracting(VehicleQueriesConsistOrientationService.Printer::vtam).containsExactly("DUBLNHP1", "HAYWHP2");
    }

    @Test
    void orientationPrintSendsTheSameRecord() {
        for (String[] c : new String[][] {{"201", ""}, {"ALL", ""}, {"200", "300"}, {"5", ""}}) {
            Parity p = orientationBackend();
            ScreenSession s = orientationPrint(p, c[0], c[1]);
            Map<String, Object> sent = p.lastSent("VHCO011").get("VHCO011_CRIT");
            String shown = dialog(s);
            p.clear();
            String msg = orientation(p).print(c[0], c[1], "2026-08-16", "2026-11-13", "HAYWHP2");
            assertThat(p.lastSent("VHCO011").get("VHCO011_CRIT")).as(c[0] + ".." + c[1]).isEqualTo(sent);
            assertThat(msg).isEqualTo(shown);
        }
    }

    @Test
    void orientationChecksGiveTheOldMessages() {
        // the old screen shows the message and still submits the report; the new API stops at the message
        for (String[] c : new String[][] {{"0", "5"}, {"300", "200"}, {"", ""}}) {
            Parity p = orientationBackend();
            ScreenSession s = orientationPrint(p, c[0], c[1]);
            String oldMessage = value(s, "Text1");
            assertThat(oldMessage).as(c[0] + ".." + c[1]).isNotBlank();
            p.clear();
            assertThat(message(() -> orientation(p).print(c[0], c[1], "2026-08-16", "2026-11-13", "HAYWHP2"))).isEqualTo(oldMessage);
            assertThat(p.lastSent("VHCO011")).isNull();
        }
    }

    // ---- VHDC01I -----------------------------------------------------------------------------------------------

    static Parity dncBackend(int carRows) {
        Parity p = new Parity();
        p.on("VHDC011", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHDC011_DATA", Map.of("dataRow", List.of(
                Map.of("prtrLoc", "DUBLIN TMZ", "cicsVtamName", "DUBLNHP1"), Map.of("prtrLoc", "HAYWARD SHOP", "cicsVtamName", "HAYWHP2")))));
        p.on("VHDC012", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHDC013", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", carRows),
                "VHDC013_DATA", carRows > 0 ? Map.of("carNo", "1101", "incdNo", "29990010", "incdType", "ABC") : Map.of()));
        return p;
    }

    static VehicleQueriesDncSheetService dnc(Parity p) {
        return new VehicleQueriesDncSheetService(p.legacy(), p.messages(), texts(), Parity.bundle());
    }

    /** the programs called after the printers, with the records sent (XDCONTROL left out: the old screen reuses the last one) */
    static List<Map.Entry<String, Map<String, Map<String, Object>>>> records(List<Map.Entry<String, Map<String, Map<String, Object>>>> sent) {
        List<Map.Entry<String, Map<String, Map<String, Object>>>> out = new ArrayList<>();
        for (var e : sent) {
            if (e.getKey().equals("VHDC011")) continue;
            Map<String, Map<String, Object>> m = new HashMap<>(e.getValue());
            m.remove("XDCONTROL");
            out.add(Map.entry(e.getKey(), m));
        }
        return out;
    }

    static ScreenSession dncOk(Parity p, String blank, String car, String incident) {
        ScreenSession s = open(p, "VHDC01I");
        s.event(id(s), List.of(), "Container_Details1", "select", 2);
        s.event(id(s), List.of(new ScreenSession.Change("Text1", "object", blank), new ScreenSession.Change("Text2", "object", car),
                new ScreenSession.Change("Text3", "object", incident)), "Push_Button1", "clicked", null);
        return s;
    }

    @Test
    void dncListsTheSamePrinters() {
        Parity p = dncBackend(1);
        open(p, "VHDC01I");
        var o = dnc(p).options();
        assertThat(o.printers()).extracting(VehicleQueriesDncSheetService.Printer::vtam).containsExactly("DUBLNHP1", "HAYWHP2");
        assertThat(o.yes()).isEqualTo("Y");
        assertThat(o.no()).isEqualTo("N");
    }

    @Test
    void dncPrintsSendTheSameRecordsAndServiceCalls() {
        for (String[] c : new String[][] {{"Y", "", ""}, {"N", "1101", "29990010"}, {"N", "1101", ""}, {"N", "", "29990010"}}) {
            Parity p = dncBackend(1);
            ScreenSession s = dncOk(p, c[0], c[1], c[2]);
            String label = String.join("|", c);
            String shown = dialog(s);
            var oldRecords = new ArrayList<>(p.sent);
            var oldBlank = serviceCalls(p, "printBlankDnC");
            var oldCar = serviceCalls(p, "printDnC");
            assertThat(oldBlank.size() + oldCar.size()).as(label + " old printed").isEqualTo(1);
            Parity q = dncBackend(1);
            String msg = dnc(q).print(c[0], c[1], c[2], "HAYWHP2", q.user.groupId(), TERMINAL);
            assertThat(msg).as(label).isEqualTo(shown);
            assertThat(records(q.sent)).as(label).isEqualTo(records(oldRecords));
            assertThat(serviceCalls(q, "printBlankDnC")).as(label).isEqualTo(oldBlank);
            assertThat(serviceCalls(q, "printDnC")).as(label).isEqualTo(oldCar);
        }
    }

    @Test
    void dncChecksGiveTheOldMessages() {
        for (String[] c : new String[][] {{"Y", "1101", "", "1"}, {"N", "", "", "1"}, {"X", "", "", "1"}, {"", "", "", "1"}, {"y", "", "", "1"},
                {"N", "1101", "29990010", "0"}}) {
            Parity p = dncBackend(Integer.parseInt(c[3]));
            ScreenSession s = dncOk(p, c[0], c[1], c[2]);
            String oldMessage = dialog(s);
            Parity q = dncBackend(Integer.parseInt(c[3]));
            assertThat(message(() -> dnc(q).print(c[0], c[1], c[2], "HAYWHP2", "BRIGHTCO", TERMINAL))).as(String.join("|", c)).isEqualTo(oldMessage);
            assertThat(q.lastSent("VHDC012")).isNull();
        }
    }

    // ---- VHSCM1I -----------------------------------------------------------------------------------------------

    static Parity scramBackend(Map<String, String> found) {
        Parity p = new Parity();
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVA_DATA", Map.of("revenueDate", "11-14-2026")));
        p.on("VHSCM1A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSCM1A_DATA", Map.of("revDate", "2026-11-14", "revDay", 1)));
        p.on("VHSCM1B", in -> {
            Map<String, Object> back = new HashMap<>(in.get("VHSCM1B_DATA"));
            String y = String.valueOf(back.get("critHomeYrd")).strip();
            String f = found.getOrDefault(y, "Y");
            if (f.equals("X")) return Map.of("XDCONTROL", Map.of("urc", 8, "umsgcode", "ABC123"));
            back.put("recFound", f);
            int n = y.charAt(0);
            back.put("totSchd1", n % 7);
            back.put("totAct2", n % 5);
            back.put("totDiff4", 3);
            back.put("dataRow", List.of(Map.of("homeYrd", y + " AM", "c1", n % 9, "c2", 2, "c5", 5, "c12", 12), Map.of("homeYrd", y + " PM", "c3", 7)));
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHSCM1B_DATA", back);
        });
        return p;
    }

    static VehicleQueriesScramService scram(Parity p) {
        return new VehicleQueriesScramService(p.legacy(), p.messages(), texts(), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static List<String> tableRows(ScreenSession s) {
        List<String> out = new ArrayList<>();
        Object rows = widget(s, "cntScram").get("rows");
        if (rows instanceof List<?> l) for (Object r : l) out.add(((List<Object>) r).stream().map(x -> String.valueOf(x).strip()).toList().toString());
        return out;
    }

    static List<String> apiRows(VehicleQueriesScramService.Yard y) {
        List<String> out = new ArrayList<>();
        for (var r : y.rows()) {
            List<String> cells = new ArrayList<>();
            cells.add(r.homeYard());
            r.cells().forEach(c -> cells.add(String.valueOf(c)));
            out.add(cells.toString());
        }
        return out;
    }

    static List<String> totals(ScreenSession s) {
        List<String> out = new ArrayList<>();
        for (int g = 1; g <= 4; g++) out.add(value(s, "txtSched" + g) + "/" + value(s, "txtActual" + g) + "/" + value(s, "txtDiff" + g));
        return out;
    }

    static List<String> totals(VehicleQueriesScramService.Yard y) {
        return y.totals().stream().map(t -> t.scheduled() + "/" + t.actual() + "/" + t.difference()).toList();
    }

    @Test
    void scramStartsOnTheSameDayAndTime() {
        Parity p = scramBackend(Map.of());
        ScreenSession s = open(p, "VHSCM1I");
        var o = scram(p).options();
        assertThat(o.day()).isEqualTo(value(s, "txtDay"));                              // REV-DAY 1: SU, all day
        assertThat(o.time()).isEqualTo(value(s, "txtTime"));
        assertThat(o.homeYards()).containsExactly("CY", "DY", "HY", "RY");
    }

    @Test
    void scramPagesThroughTheHomeYardsLikeNxt() {
        for (Map<String, String> found : List.of(Map.<String, String>of(), Map.of("HY", "X"))) {
            Parity p = scramBackend(found);
            ScreenSession s = open(p, "VHSCM1I");
            s.event(id(s), List.of(new ScreenSession.Change("txtDay", "object", "MO"), new ScreenSession.Change("txtTime", "object", "PM")), "pbtList", "clicked", null);
            List<List<String>> oldRows = new ArrayList<>(), oldTotals = new ArrayList<>();
            oldRows.add(tableRows(s));
            oldTotals.add(totals(s));
            for (int k = 0; k < 3; k++) {
                s.event(id(s), List.of(), "pbtNxt", "clicked", null);
                oldRows.add(tableRows(s));
                oldTotals.add(totals(s));
            }
            var oldCalls = p.sent.stream().filter(e -> e.getKey().equals("VHSCM1B")).map(e -> e.getValue().get("VHSCM1B_DATA")).toList();
            p.clear();
            var r = scram(p).report("MO", "PM");
            var newCalls = p.sent.stream().filter(e -> e.getKey().equals("VHSCM1B")).map(e -> e.getValue().get("VHSCM1B_DATA")).toList();
            assertThat(newCalls).as("records sent " + found).isEqualTo(oldCalls);
            assertThat(r.yards()).hasSize(4);
            for (int k = 0; k < 4; k++) {
                assertThat(apiRows(r.yards().get(k))).as(found + " rows of " + r.yards().get(k).homeYard()).isEqualTo(oldRows.get(k));
                if (!found.containsKey(r.yards().get(k).homeYard())) assertThat(totals(r.yards().get(k))).as("totals").isEqualTo(oldTotals.get(k));
            }
        }
    }

    @Test
    void scramStopsWhereNoScheduleIsFound() {
        Parity p = scramBackend(Map.of("CY", "N"));
        ScreenSession s = open(p, "VHSCM1I");
        s.event(id(s), List.of(), "pbtList", "clicked", null);
        String oldMessage = dialog(s);
        var oldSent = p.lastSent("VHSCM1B").get("VHSCM1B_DATA");
        p.clear();
        var r = scram(p).report(value(s, "txtDay"), value(s, "txtTime"));
        assertThat(p.lastSent("VHSCM1B").get("VHSCM1B_DATA")).isEqualTo(oldSent);
        assertThat(r.yards()).hasSize(1);
        assertThat(r.yards().get(0).message()).isEqualTo(oldMessage);
        assertThat(r.yards().get(0).rows()).isEmpty();
    }

    @Test
    void scramChecksGiveTheOldMessages() {
        for (String[] c : new String[][] {{"XX", "AM"}, {"MO", "ZZ"}, {"", "AM"}, {"MO", ""}, {"mo", "AM"}}) {
            Parity p = scramBackend(Map.of());
            ScreenSession s = open(p, "VHSCM1I");
            s.event(id(s), List.of(new ScreenSession.Change("txtDay", "object", c[0]), new ScreenSession.Change("txtTime", "object", c[1])), "pbtList", "clicked", null);
            String oldMessage = dialog(s);
            assertThat(oldMessage).as(c[0] + "|" + c[1]).isNotBlank();
            assertThat(message(() -> scram(p).report(c[0], c[1]))).as(c[0] + "|" + c[1]).isEqualTo(oldMessage);
        }
    }

    @Test
    void scramWithoutDayAndTimeSendsTheSameRecord() {
        Parity p = scramBackend(Map.of());
        ScreenSession s = open(p, "VHSCM1I");
        s.event(id(s), List.of(new ScreenSession.Change("txtDay", "object", ""), new ScreenSession.Change("txtTime", "object", "")), "pbtList", "clicked", null);
        var oldSent = p.lastSent("VHSCM1B").get("VHSCM1B_DATA");
        p.clear();
        scram(p).report("", "");
        assertThat(p.sent.stream().filter(e -> e.getKey().equals("VHSCM1B")).findFirst().orElseThrow().getValue().get("VHSCM1B_DATA")).isEqualTo(oldSent);
    }

    // ---- VHCHG1I -----------------------------------------------------------------------------------------------

    static Parity changeBackend(int urc) {
        Parity p = new Parity();
        p.on("VHCY1A1", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        p.on("VHVM1A2", in -> Map.of("XDCONTROL", Map.of("urc", urc, "urowcnt", 1), "VHVM1A2_DATA", Map.of("carNo", "1101", "vehStat", "NO GO",
                "vehStatPrev", "GO", "homeYrd", "CY", "termLastUpd", "TERMX", "currLoc", "CY01")));
        p.on("VHVM1A1", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static VehicleQueriesCarStatusChangeService change(Parity p) {
        return new VehicleQueriesCarStatusChangeService(p.legacy(), p.messages(), texts());
    }

    @Test
    void statusChangeShowsTheSameCarAndSendsTheSameUpdate() {
        Parity p = changeBackend(0);
        ScreenSession s = open(p, "VHCHG1I");
        var o = change(p).options();
        assertThat(o.message()).isEqualTo(value(s, "txtMsg"));
        assertThat(o.statuses()).isEqualTo(widget(s, "Drop-down_List1").get("items"));
        s.event(id(s), List.of(new ScreenSession.Change("Text1", "object", "1101")), "Text1", "losingFocus", null);
        p.clear();
        var car = change(p).car("1101");
        assertThat(car.status()).isEqualTo(value(s, "Text2"));
        assertThat(car.previousStatus()).isEqualTo(value(s, "Text3"));
        assertThat(car.message()).isEqualTo(value(s, "txtMsg"));
        int pm = o.statuses().indexOf("PM") + 1;
        s.event(id(s), List.of(), "Drop-down_List1", "select", pm);
        s.event(id(s), List.of(), "Push_Button1", "clicked", null);
        assertThat(dialog(s)).isEqualTo(o.question());
        @SuppressWarnings("unchecked") int dialogId = (Integer) ((Map<String, Object>) s.state().get("dialog")).get("id");
        p.clear();
        s.answer(dialogId, "Yes");
        var oldUpdate = p.lastSent("VHVM1A1");
        p.clear();
        String msg = change(p).change("1101", "PM");
        for (String rec : List.of("VHVM1A1_CRIT", "VHVM1A1_DATA")) assertThat(p.lastSent("VHVM1A1").get(rec)).as(rec).isEqualTo(oldUpdate.get(rec));
        assertThat(msg).isEqualTo(value(s, "txtMsg"));
    }

    @Test
    void anUnknownCarGetsTheOldMessage() {
        Parity p = changeBackend(8);
        ScreenSession s = open(p, "VHCHG1I");
        s.event(id(s), List.of(new ScreenSession.Change("Text1", "object", "9999")), "Text1", "losingFocus", null);
        assertThat(message(() -> change(p).car("9999"))).isEqualTo(value(s, "txtMsg"));
        assertThat(message(() -> change(p).change("9999", "PM"))).isEqualTo(value(s, "txtMsg"));
        assertThat(p.lastSent("VHVM1A1")).isNull();
    }
}
