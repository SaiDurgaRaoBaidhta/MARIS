package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Secondary repair read-only screens against the old VHSR05I / VHSR11I / VHSR15I run by the engine. */
class SecondaryRepairParityTest {
    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    static Parity backend() {
        Parity p = new Parity();
        p.on("VHSR05C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR05C_DATA", Map.of("dataRow", List.of(
                Map.of("subJobStat", "OPN", "subJobStatDesc", "OPEN"), Map.of("subJobStat", "OUT", "subJobStatDesc", "OUT THE DOOR")))));
        p.on("VHSR05B", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR05B_DATA", Map.of("stockDesc", "BRAKE CALIPER")));
        p.on("VHSR05A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR05A_DATA", Map.of("totCntCmpntsIn", 3, "dataRow", List.of(
                Map.of("jobNo", "A12345", "subJobDocNo", 1, "serNo", "S1", "cntCmpntsIn", 2, "subJobStat", "OPN"),
                Map.of("jobNo", "A12346", "subJobDocNo", 2, "serNo", "S2", "cntCmpntsIn", 1, "subJobStat", "OUT")))));
        p.on("VHSR11A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR11A_DATA", m("recordFound", "Y", "jobStat", "OPN", "carNo", "1101",
                "dataRow", List.of(Map.of("subJobDocNo", 1, "serNo", "S1", "subJobStat", "OPN"), Map.of("subJobDocNo", 2, "serNo", "S2", "subJobStat", "OPN")))));
        for (String n : List.of("VHSRJNX", "VHSRJRN")) {
            p.on(n, in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), n + "_DATA", Map.of("dataRow", List.of(Map.of("narr", "CHECKED")))));
        }
        p.on("VHSR09A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR09A_DATA_01", Map.of("dataRow", List.of(Map.of("cmpntCd", "BRK")))));
        p.on("VHSR10A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR10A_DATA", Map.of("dataRow", List.of(Map.of("emplNo", "12345")))));
        p.on("VHSR15C", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR15C_DATA", Map.of("defaultPrtIdx", 1, "dataRow", List.of(
                Map.of("prtrLocDesc", "SHOP", "vtamName", "SHOPHP1")))));
        p.on("VHSR15A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR15A_DATA", Map.of("recordExist", "Y", "subJobStat", "OPN")));
        p.on("VHSR15B", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    @SuppressWarnings("unchecked")
    static String window(ScreenSession s, String screen) {
        return ((List<Map<String, Object>>) s.state().get("windows")).stream().filter(w -> w.get("screen").equals(screen))
                .map(w -> (String) w.get("id")).findFirst().orElseThrow();
    }

    /** the key fields of a narrative request (the old screen also sends back the previous rows, which the program ignores) */
    static Map<String, Object> key(Map<String, Object> rec) {
        Map<String, Object> k = new LinkedHashMap<>();
        for (String f : List.of("jobNo", "subJobDocNo")) if (rec.containsKey(f)) k.put(f, rec.get(f));
        return k.isEmpty() ? rec : k;
    }

    static SecondaryRepairService api(Parity p) {
        return new SecondaryRepairService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()));
    }

    @ParameterizedTest
    @CsvSource({"STK123,,,", "STK123,S1,OUT,Y"})
    void stockInquiryMatchesTheOldScreen(String stock, String serial, String status, String storage) {
        serial = serial == null ? "" : serial; status = status == null ? "" : status; storage = storage == null ? "" : storage;
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHSR05I", p.user);
        String w = window(old, "VHSR05I");
        if (!status.isEmpty()) old.event(w, List.of(), "Combo_Box1", "entryObject", status);
        if (!storage.isEmpty()) old.event(w, List.of(), "Cmblongtermstorage", "entryObject", storage);
        old.event(w, List.of(new ScreenSession.Change("txtstockno", "object", stock), new ScreenSession.Change("txtserno", "object", serial)), "pbList", "clicked", null);
        Map<String, Object> oldB = p.lastSent("VHSR05B").get("VHSR05B_CRIT"), oldA = p.lastSent("VHSR05A").get("VHSR05A_CRIT");

        p.clear();
        var r = api(p).stockInquiry(stock, serial, status, storage);
        assertThat(p.lastSent("VHSR05B").get("VHSR05B_CRIT")).isEqualTo(oldB);
        assertThat(p.lastSent("VHSR05A").get("VHSR05A_CRIT")).isEqualTo(oldA);
        assertThat(r.jobs()).hasSize(2);
    }

    /** VHSR11I (status job): List, then Next; each sub job shows its narrative (VHSRJNX) */
    @Test
    void jobStatusMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHSR11I", p.user);
        String w = window(old, "VHSR11I");
        old.event(w, List.of(new ScreenSession.Change("txtJobNo", "object", "A12345")), "pbtList", "clicked", null);
        Map<String, Object> oldJob = p.lastSent("VHSR11A").get("VHSR11A_CRIT"), oldNarr1 = p.lastSent("VHSRJNX").get("VHSRJNX_DATA");
        old.event(w, List.of(), "pbtNxt", "clicked", null);
        Map<String, Object> oldNarr2 = p.lastSent("VHSRJNX").get("VHSRJNX_DATA");

        p.clear();
        var api = api(p);
        var job = api.openJob("A12345");
        assertThat(job.count()).isEqualTo(2);
        assertThat(p.lastSent("VHSR11A").get("VHSR11A_CRIT")).isEqualTo(oldJob);
        api.subJob("A12345", (int) job.subJobs().get(0).doc());
        assertThat(key(p.lastSent("VHSRJNX").get("VHSRJNX_DATA"))).isEqualTo(key(oldNarr1));
        api.subJob("A12345", (int) job.subJobs().get(1).doc());
        assertThat(key(p.lastSent("VHSRJNX").get("VHSRJNX_DATA"))).isEqualTo(key(oldNarr2));
    }

    /** VHSR10I (history job), opened from a stock inquiry row: the job and the first sub job's four lists */
    @Test
    void jobHistoryMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHSR05I", p.user);
        String w = window(old, "VHSR05I");
        old.event(w, List.of(new ScreenSession.Change("txtstockno", "object", "STK123")), "pbList", "clicked", null);
        old.event(w, List.of(), "Stock_Container", "select", 1);
        old.event(w, List.of(), "Stock_Container", "defaultActionRequested", null);     // a double-click opens the history
        Map<String, Map<String, Object>> oldSent = new LinkedHashMap<>();
        for (String n : List.of("VHSR11A", "VHSRJNX", "VHSRJRN", "VHSR09A", "VHSR10A")) {
            assertThat(p.lastSent(n)).as("the old history screen called " + n).isNotNull();
            oldSent.put(n, p.lastSent(n).values().stream().skip(1).findFirst().orElseThrow());
        }

        p.clear();
        var api = api(p);
        var job = api.jobHistory("A12345");
        api.subJob("A12345", (int) job.subJobs().get(0).doc());
        for (String n : oldSent.keySet()) {
            assertThat(key(p.lastSent(n).values().stream().skip(1).findFirst().orElseThrow())).as(n).isEqualTo(key(oldSent.get(n)));
        }
    }

    @Test
    void reprintMatchesTheOldScreen() {
        Parity p = backend();
        ScreenSession old = p.oldScreens();
        old.openRoot("VHSR15I", p.user);
        String w = window(old, "VHSR15I");
        Map<String, Object> oldC = p.lastSent("VHSR15C").get("VHSR15C_CRIT");
        old.event(w, List.of(), "Container_Details1", "select", 1);
        old.event(w, List.of(new ScreenSession.Change("ReprintJobNo", "object", "A12345")), "ReprintPrintButton", "clicked", null);
        Map<String, Object> oldA = p.lastSent("VHSR15A").get("VHSR15A_CRIT"), oldB = p.lastSent("VHSR15B").get("VHSR15B_CRIT");

        p.clear();
        var api = api(p);
        assertThat(api.reprintOptions("BRIGHTCO").defaultPrinter()).isEqualTo("SHOPHP1");
        assertThat(p.lastSent("VHSR15C").get("VHSR15C_CRIT")).isEqualTo(oldC);
        api.reprint("A12345", 1, "SHOPHP1");
        assertThat(p.lastSent("VHSR15A").get("VHSR15A_CRIT")).isEqualTo(oldA);
        assertThat(p.lastSent("VHSR15B").get("VHSR15B_CRIT")).isEqualTo(oldB);
    }
}
