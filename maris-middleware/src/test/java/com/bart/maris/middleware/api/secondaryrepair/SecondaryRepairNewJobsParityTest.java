package com.bart.maris.middleware.api.secondaryrepair;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairInDoorService;
import com.bart.maris.middleware.domain.secondaryrepair.SecondaryRepairJobSplitService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * New jobs (VHSR01I, in the door) and new sub jobs (VHSR08I, job split), opened from the menu VHMSRI, against the old
 * screens run by the engine: the records written (VHSR01E, VHSR08C) and the messages of the shared checks. The engine
 * leaves these screens' drop-down lists (work type, department) empty, so the old side keeps its starting work type and
 * cannot pick a department: wayside jobs and the work type check are covered by SecondaryRepairJobsApiTest.
 */
class SecondaryRepairNewJobsParityTest {
    static final String TERMINAL = "MARIS-UI";
    static final DateTimeFormatter SCREEN = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    static Parity backend(String jobStat, int threshold) {
        Parity p = new Parity();
        p.on("VHSRWT", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSRWT_DATA", Map.of("dataRow", List.of(
                Map.of("workType", "REPAIR", "workTypeDesc", "REPAIR"), Map.of("workType", "TEST", "workTypeDesc", "TEST ONLY")))));
        p.on("VHSRRA", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSRRA_DATA", Map.of("dataRow", List.of(
                Map.of("reprGroup", Map.of("reprArea", "R1", "reprAreaDesc", "ELECTRONICS")), Map.of("reprGroup", Map.of("reprArea", "R2", "reprAreaDesc", "MECH"))))));
        p.on("VHSRUD", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSRUD_DATA", Map.of("dataRow", List.of(Map.of("deptCd", "A"), Map.of("deptCd", "W")))));
        p.on("VHSR01B", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR01B_DATA", Map.of("dataRow", List.of(Map.of("equipType", "SWITCH", "equipTypeDesc", "SW")))));
        p.on("VHSR01C", in -> "BAD".equals(in.get("VHSR01C_CRIT").get("equipId")) ? Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0))
                : Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHSR01C_DATA", Map.of("equipType", "FAREGATE", "equipLoc", "M16")));
        p.on("VHINC6A", in -> switch ((String) in.get("VHINC6A_CRIT").get("carNo")) {
            case "1999" -> Map.of("XDCONTROL", Map.of("urc", 100, "urowcnt", 0));
            case "1998" -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("carType", "X"));
            default -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHINC6A_DATA", Map.of("carType", "C"));
        });
        p.on("VHSRVS", in -> {
            Map<String, Object> d = new HashMap<>(in.get("VHSRVS_DATA"));
            Object stock = d.get("dataRow") instanceof Map<?, ?> row ? row.get("stockNo") : null;
            d.put("recordFound", "NOSTOCK".equals(stock) ? "N" : "Y");
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHSRVS_DATA", d);
        });
        p.on("VHSR01D", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR01D_CRIT", Map.of("stockNo", in.get("VHSR01D_CRIT").get("stockNo"), "rowCount", 1)));
        p.on("VHSR01A", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHSR01A_DATA", Map.of("sumCntCmpntsIn", 5, "cntThreshold", threshold)));
        p.on("VHSR01E", in -> {
            Map<String, Object> d = new HashMap<>(in.get("VHSR01E_DATA"));
            d.put("jobNo", "B00042");
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHSR01E_DATA", d);
        });
        p.on("VHSR08A", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 2), "VHSR08A_DATA", Map.of("recordFound", "Y", "jobStat", jobStat, "carNo", "1101",
                "dataRow", List.of(Map.of("reprArea", "R1", "subJobDocNo", 1, "narr", "FIRST"), Map.of("reprArea", "R2", "subJobDocNo", 2, "narr", "SECOND")))));
        p.on("VHSR08C", in -> Map.of("XDCONTROL", Map.of("urc", 0)));
        return p;
    }

    static ScreenSession open(Parity p, String button, String screen) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHMSRI", p.user);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHMSRI"), List.of(), button, "clicked", null);
        assertThat(SecondaryRepairCommentsParityTest.window(s, screen)).as(screen).isNotNull();
        return s;
    }

    static String day(int daysBack) {
        return LocalDate.now().minusDays(daysBack).format(SCREEN);
    }

    static String iso(String screen) {
        return screen.substring(6) + "-" + screen.substring(0, 2) + "-" + screen.substring(3, 5);
    }

    // ---- VHSR08I: a new sub job -------------------------------------------------------------------------------

    static SecondaryRepairJobSplitService split(Parity p) {
        return new SecondaryRepairJobSplitService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    static ScreenSession splitScreen(Parity p, String job) {
        ScreenSession s = open(p, "Push_Button30", "VHSR08I");
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR08I"), List.of(new ScreenSession.Change("txtJobNo", "object", job)), "txtJobNo", "losingFocus", null);
        return s;
    }

    static List<ScreenSession.Change> subJobFields(String workType, String date, String area, String stock, String serial, String qty) {
        List<ScreenSession.Change> c = new ArrayList<>();
        c.add(new ScreenSession.Change("ddlWorkType", "selectedItem", workType));
        c.add(new ScreenSession.Change("txtDateIn", "object", date));
        c.add(new ScreenSession.Change("cmbReprArea", "entryObject", area));
        c.add(new ScreenSession.Change("txtStockNo", "object", stock));
        c.add(new ScreenSession.Change("txtSerNo", "object", serial));
        c.add(new ScreenSession.Change("txtBatchQty", "object", qty));
        c.add(new ScreenSession.Change("mleJobNarrative", "object", "SPLIT FOR TEST"));
        return c;
    }

    @ParameterizedTest
    @CsvSource({"0", "6"})
    void aNewSubJobWritesWhatTheOldScreenWrites(int threshold) {
        Parity p = backend("OPN", threshold);
        ScreenSession s = splitScreen(p, "A12345");
        String date = day(3);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR08I"), subJobFields("REPAIR", date, "R2", "123456789", "SN9", "2"), "pbtCreate", "clicked", null);
        var old = p.lastSent("VHSR08C");
        String oldMessage = SecondaryRepairCommentsParityTest.value(s, "VHSR08I", "txtMsg");
        assertThat(old).as("old VHSR08C, message " + oldMessage).isNotNull();
        p.clear();
        var c = split(p).create("A12345", new SecondaryRepairJobSplitService.NewSubJob("REPAIR", iso(date), "R2", "123456789", "SN9", 2, "SPLIT FOR TEST"),
                p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR08C").get("VHSR08C_DATA")).isEqualTo(old.get("VHSR08C_DATA"));
        assertThat(c.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"REPAIR|3|R9|NOSTOCK|1", "REPAIR|-2|R1||1", "REPAIR|600|R1|123|0", "REPAIR|0|R1|123|1"})
    void subJobChecksGiveTheOldMessages(String workType, int daysBack, String area, String stock, String qty) {
        stock = stock == null ? "" : stock;
        Parity p = backend("OPN", 0);
        ScreenSession s = splitScreen(p, "A12345");
        String date = day(daysBack);
        s.event(SecondaryRepairCommentsParityTest.window(s, "VHSR08I"), subJobFields(workType, date, area, stock, "", qty), "pbtCreate", "clicked", null);
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR08I", "txtMsg");
        String msg;
        try {
            msg = split(p).create("A12345", new SecondaryRepairJobSplitService.NewSubJob(workType, iso(date), area, stock, "", Integer.valueOf(qty), "X"), "G", TERMINAL)
                    .message();
        } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @ParameterizedTest
    @CsvSource({"CLO", "DEF"})
    void closedAndDeferredJobsGiveTheOldMessages(String stat) {
        Parity p = backend(stat, 0);
        ScreenSession s = splitScreen(p, "A12345");
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR08I", "txtMsg");
        String msg;
        try { split(p).job("A12345"); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    @Test
    void unknownJobGivesTheOldMessage() {
        Parity p = backend("OPN", 0);
        p.on("VHSR08A", in -> Map.of("XDCONTROL", Map.of("urc", 100), "VHSR08A_DATA", Map.of("recordFound", "N")));
        ScreenSession s = splitScreen(p, "ZZZZZZ");
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR08I", "txtMsg");
        String msg;
        try { split(p).job("ZZZZZZ"); msg = null; } catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }

    // ---- VHSR01I: a new job -----------------------------------------------------------------------------------

    static SecondaryRepairInDoorService inDoor(Parity p) {
        return new SecondaryRepairInDoorService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), Clock.systemDefaultZone());
    }

    @SuppressWarnings("unchecked")
    static List<String> items(ScreenSession s, String screen, String widget) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) {
            if (!screen.equals(w.get("screen"))) continue;
            Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(widget);
            return ((List<Object>) x.get("items")).stream().map(o -> String.valueOf(o).strip()).toList();
        }
        return List.of();
    }

    /** "car|dept|equipType|equipId" plus the shared fields */
    static ScreenSession newJob(Parity p, String car, String dept, String equipType, String equipId, String workType, String date, String area, String stock,
                                String qty) {
        ScreenSession s = open(p, "Push_Button25", "VHSR01I");
        String w = SecondaryRepairCommentsParityTest.window(s, "VHSR01I");
        if (!dept.isEmpty()) {
            s.event(w, List.of(new ScreenSession.Change("ddlDeptCd", "selectionIndex", items(s, "VHSR01I", "ddlDeptCd").indexOf(dept) + 1)), "ddlDeptCd",
                    "selectionIsValid", null);
            if (!equipId.isEmpty()) s.event(w, List.of(new ScreenSession.Change("txtEquipId", "object", equipId)), "txtEquipId", "losingFocus", null);
        }
        List<ScreenSession.Change> c = new ArrayList<>();
        c.add(new ScreenSession.Change("ddlWorkType", "selectedItem", workType));
        c.add(new ScreenSession.Change("txtDateIn", "object", date));
        c.add(new ScreenSession.Change("cmbReprArea", "entryObject", area));
        c.add(new ScreenSession.Change("txtCarNo", "object", car.isEmpty() ? "0" : car));
        if (!equipType.isEmpty()) c.add(new ScreenSession.Change("cmbEquipType", "entryObject", equipType));
        c.add(new ScreenSession.Change("txtIncdNo", "object", "29990001"));
        c.add(new ScreenSession.Change("txtSentFrom", "object", "HAYW"));
        c.add(new ScreenSession.Change("txtStockNo", "object", stock));
        c.add(new ScreenSession.Change("txtSerNo", "object", "SER1"));
        c.add(new ScreenSession.Change("txtBatchQty", "object", qty));
        c.add(new ScreenSession.Change("mleNarrative", "object", "IN THE DOOR"));
        s.event(w, c, "pbtCreate", "clicked", null);
        return s;
    }

    static SecondaryRepairInDoorService.NewJob job(String car, String dept, String equipType, String equipId, String workType, String date, String area,
                                                   String stock, String qty) {
        return new SecondaryRepairInDoorService.NewJob(workType, iso(date), area, car, dept, equipType, equipId, "29990001", "", "HAYW", stock, "SER1",
                Integer.valueOf(qty), "IN THE DOOR");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {"1101|''|''|''|0", "0088|''|''|''|6"})
    void aNewJobWritesWhatTheOldScreenWrites(String car, String dept, String equipType, String equipId, int threshold) {
        Parity p = backend("OPN", threshold);
        String date = day(1);
        ScreenSession s = newJob(p, car, dept, equipType, equipId, "REPAIR", date, "R1", "123456789", "2");
        var old = p.lastSent("VHSR01E");
        String oldMessage = SecondaryRepairCommentsParityTest.value(s, "VHSR01I", "txtStatusmsg");
        assertThat(old).as("old VHSR01E, message " + oldMessage).isNotNull();
        p.clear();
        var c = inDoor(p).create(job(car, dept, equipType, equipId, "REPAIR", date, "R1", "123456789", "2"), p.user.groupId(), TERMINAL);
        assertThat(p.lastSent("VHSR01E").get("VHSR01E_DATA")).isEqualTo(old.get("VHSR01E_DATA"));
        assertThat(c.job()).isEqualTo("B00042");
        assertThat(c.message()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1999|''|''|''|REPAIR|1|R9|NOSTOCK|1", "1998|''|''|''|REPAIR|1|R1|123|0", "''|''|''|''|REPAIR|1|R1|123|1",
            "1101|''|''|''|REPAIR|600|R1|123|1"})
    void newJobChecksGiveTheOldMessages(String car, String dept, String equipType, String equipId, String workType, int back, String area, String stock,
                                        String qty) {
        Parity p = backend("OPN", 0);
        String date = day(back);
        ScreenSession s = newJob(p, car, dept, equipType, equipId, workType, date, area, stock, qty);
        assertThat(p.lastSent("VHSR01E")).isNull();
        String old = SecondaryRepairCommentsParityTest.value(s, "VHSR01I", "txtStatusmsg");
        String msg;
        try { msg = inDoor(p).create(job(car, dept, equipType, equipId, workType, date, area, stock, qty), "G", TERMINAL).message(); }
        catch (InvalidRequestException e) { msg = e.getMessage(); }
        assertThat(msg).isEqualTo(old);
    }
}
