package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairOutTheDoorService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairOutTheDoorService.Disposition;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Sub job out the door (old VHSR04I) against the old screen run by the engine. */
class SecondaryRepairOutTheDoorParityTest {
    static final String TERMINAL = "MARIS-UI";
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    static Parity backend(String status, boolean found) {
        Parity p = new Parity();
        p.on("VHSRWT", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSRWT_DATA", Map.of("dataRow", List.of(
                Map.of("workType", "BENCH", "workTypeDesc", "BENCH TEST", "actvInd", "Y"), Map.of("workType", "REBUILD", "workTypeDesc", "REBUILD", "actvInd", "Y")))));
        p.on("VHSR04A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR04A_DATA", Map.of("dataRow", List.of(
                Map.of("cmpntFaultCd", "F01"), Map.of("cmpntFaultCd", "F02")))));
        p.on("VHSR02E", in -> found
                ? Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR02E_DATA", Map.of("subJobStat", status, "cntCmpntsIn", 3, "dateIn", "11-02-2026",
                        "vendorId", "VEN01", "longTermStorInd", "N", "workType", "BENCH"))
                : Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0)));
        p.on("AFCXVLD", in -> Map.of("XDCONTROL", Map.of("urc", 0), "AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N")));
        p.on("VHSR04B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static SecondaryRepairOutTheDoorService api(Parity p) {
        return new SecondaryRepairOutTheDoorService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Parity.bundle(), p.clock);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if ("VHSR04I".equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s).get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static ScreenSession lookup(Parity p, String job, String doc) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSR04I", p.user);
        String id = (String) window(s).get("id");
        s.event(id, List.of(new ScreenSession.Change("txtJobNo", "object", job), new ScreenSession.Change("txtDocNo", "object", doc)), "txtDocNo", "losingFocus",
                null);
        return s;
    }

    /** the old screen after the lookup: the fields typed, a toggle chosen, then Update */
    static ScreenSession update(Parity p, String date, String out, String scrapped, String fault, String type, String toggle, String vendor) {
        ScreenSession s = lookup(p, "J12345", "2");
        String id = (String) window(s).get("id");
        List<ScreenSession.Change> c = new ArrayList<>(List.of(new ScreenSession.Change("txtDateOut", "object", date),
                new ScreenSession.Change("txtCntCmpntsOut", "object", out), new ScreenSession.Change("txtCntCmpntsScrappd", "object", scrapped),
                new ScreenSession.Change("cmbCmpntFaultCd", "entryObject", fault), new ScreenSession.Change("cmbDiagnosticWorkType", "entryObject", type),
                new ScreenSession.Change("txtQAInspectId", "object", "QA1")));
        s.event(id, c, "txtQAInspectId", "losingFocus", null);
        if (toggle != null) s.event(id, List.of(new ScreenSession.Change(toggle, "selection", true)), null, null, null);
        if (vendor != null) s.event(id, List.of(new ScreenSession.Change("txtVendorId", "object", vendor)), "txtVendorId", "losingFocus", null);
        s.event(id, List.of(), "pbtUpdate", "clicked", null);
        return s;
    }

    static Map<String, Map<String, Object>> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls, String program) {
        for (int i = calls.size() - 1; i >= 0; i--) {
            if (!calls.get(i).getKey().equals(program)) continue;
            Map<String, Map<String, Object>> m = new LinkedHashMap<>(calls.get(i).getValue());
            m.remove("XDCONTROL");
            return m;
        }
        return null;
    }

    @Test
    void optionsMatchTheOldDropDowns() {
        Parity p = backend("OPN", true);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHSR04I", p.user);
        var o = api(p).options();
        assertThat(String.valueOf(o.doc())).isEqualTo(value(s, "txtDocNo"));
        assertThat(o.faultCodes()).containsExactly("F01", "F02");
        assertThat(o.workTypes()).extracting(SecondaryRepairOutTheDoorService.WorkType::code).containsExactly("BENCH", "REBUILD");
    }

    @ParameterizedTest
    @CsvSource(value = {"CLO|true", "REP|true", "DEF|true", "NWP|true", "OTV|true", "OPN|true", "OPN|false"}, delimiter = '|')
    void lookupMatches(String status, boolean found) {
        Parity p = backend(status, found);
        ScreenSession s = lookup(p, "J12345", "2");
        String oldMessage = value(s, "txtMsgArea");
        var oldCrit = sent(p.sent, "VHSR02E");
        p.clear();
        String message;
        SecondaryRepairOutTheDoorService.SubJob sj = null;
        try {
            sj = api(p).subJob("J12345", 2);
            message = sj.message();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(sent(p.sent, "VHSR02E")).isEqualTo(oldCrit);
        if (sj != null && sj.canGoOut()) {
            assertThat(MDY.format(LocalDate.parse(sj.dateOut()))).isEqualTo(value(s, "txtDateOut"));
            assertThat(String.valueOf(sj.qtyOut())).isEqualTo(value(s, "txtCntCmpntsOut"));
            assertThat(sj.status()).isEqualTo(value(s, "txtSubJobStat"));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"|1", "J12345|0", "|0"}, delimiter = '|')
    void inputChecksMatch(String job, String doc) {
        Parity p = backend("OPN", true);
        ScreenSession s = lookup(p, job == null ? "" : job, doc);
        String message;
        try {
            api(p).subJob(job, Integer.valueOf(doc));
            message = null;
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(message).isEqualTo(value(s, "txtMsgArea"));
    }

    @ParameterizedTest
    @CsvSource(value = {"|", "tbtLongTermStorInd|", "tbtOutToVendor|V77", "tbtDeferSubJob|", "tbtSubJobAwaitingPart|"}, delimiter = '|')
    void updateSendsWhatTheOldScreenSends(String toggle, String vendor) {
        Parity p = backend("OPN", true);
        String today = MDY.format(LocalDate.now());
        ScreenSession s = update(p, today, "2", "1", "F02", "REBUILD", toggle, vendor);
        String oldMessage = value(s, "txtMsgArea");
        var oldCrit = sent(p.sent, "VHSR04B");
        p.clear();
        Disposition d = toggle == null ? null : switch (toggle) {
            case "tbtLongTermStorInd" -> Disposition.LONG_TERM_STORAGE;
            case "tbtOutToVendor" -> Disposition.OUT_TO_VENDOR;
            case "tbtDeferSubJob" -> Disposition.DEFERRED;
            default -> Disposition.AWAITING_PARTS;
        };
        var r = api(p).goOut(new SecondaryRepairOutTheDoorService.OutTheDoor("J12345", 2, LocalDate.now().toString(), 2, 1, "F02", "REBUILD", "QA1", d, vendor),
                p.user.groupId(), TERMINAL);
        var newCrit = sent(p.sent, "VHSR04B");
        assertThat(oldCrit).as("old screen wrote: " + oldMessage).isNotNull();
        // the old screen records the terminal its parent menu passed in (none when opened alone); the API records this system's
        assertThat(newCrit.get("VHSR04B_DATA").get("termLastUpd")).isEqualTo(TERMINAL);
        newCrit.get("VHSR04B_DATA").put("termLastUpd", oldCrit.get("VHSR04B_DATA").get("termLastUpd"));
        assertThat(newCrit).isEqualTo(oldCrit);
        assertThat(r.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(value = {"|1|0|F02|REBUILD|", "11-14-2099|1|0|F02|REBUILD|", "10-01-2026|3|0|F02|REBUILD|", "TODAY|-1|0|F02|REBUILD|",
            "TODAY|1|1|F02|REBUILD|", "TODAY|3|0||REBUILD|", "TODAY|3|0|ZZZ|REBUILD|", "TODAY|3|0|F01||", "TODAY|3|0|F01|NOPE|",
            "TODAY|3|0|F01|BENCH|tbtOutToVendor", "|5|0|||tbtOutToVendor"}, delimiter = '|')
    void updateChecksMatch(String date, String out, String scrapped, String fault, String type, String toggle) {
        Parity p = backend("OPN", true);
        String d = date == null ? "" : date.equals("TODAY") ? MDY.format(LocalDate.now()) : date;
        ScreenSession s = update(p, d, out, scrapped, fault == null ? "" : fault, type == null ? "" : type, toggle, null);
        String oldMessage = value(s, "txtMsgArea");
        p.clear();
        String iso = d.isEmpty() ? "" : LocalDate.parse(d, MDY).toString();
        String message;
        try {
            api(p).goOut(new SecondaryRepairOutTheDoorService.OutTheDoor("J12345", 2, iso, Integer.valueOf(out), Integer.valueOf(scrapped), fault, type, "QA1",
                    toggle == null ? null : Disposition.OUT_TO_VENDOR, null), p.user.groupId(), TERMINAL);
            message = null;
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(message).isEqualTo(oldMessage);
        assertThat(sent(p.sent, "VHSR04B")).isNull();
    }
}
