package com.bart.maris.middleware.api.tables3;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.tables3.Tables3SerialDataService;
import com.bart.maris.middleware.domain.tables3.Tables3SerialService;
import com.bart.maris.middleware.domain.tables3.Tables3Serials;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Component serials: the clean API against the old VHCF49I (list), VHCF50I (detail, update), VHCF51I (hours) and
 * VHCF52I (geo data) run by the engine, same scripted backend, same inputs. Compares the records sent to every program
 * (writers included) and the messages.
 */
class Tables3SerialsParityTest {
    /** the caller the old VHCF51I / VHCF52I need to enable Update (none of the 215 screens is it) */
    private static final String MRS14I = "MRS14I";
    private Parity p;
    private int listUrc = 0, carUrc = 0, dateUrc = 0, afcxvldError = 0, listCount = -1;

    static Map<String, Object> row(String stock, String serial) {
        Map<String, Object> m = new HashMap<>();
        m.put("stockNo", stock);
        m.put("homeYrd", "CY");
        m.put("carNo", "1101");
        m.put("cmpntEquipType", "TRUCK");
        m.put("cmpntSerialNo", serial);
        m.put("configStat", "ACTIVE");
        m.put("dateAxle", "2020-01-02");
        m.put("dateInstalled", "2021-03-04");
        m.put("dateBuyRebuilt", "2019-07-08");
        m.put("serialStat", "ACT");
        m.put("shopLoc", "HY");
        m.put("posnCd", "A1");
        m.put("emplNo", "12345");
        m.put("parentSerialNo", "PSN1");
        m.put("parentStockNo", "987654321");
        m.put("dateTrkRemove", "2022-05-06");
        m.put("subJobDocNo", 3);
        return m;
    }

    private Map<String, Object> reply(int urc, int rows, Map<String, Object> records) {
        Map<String, Object> out = new HashMap<>(records);
        out.put("XDCONTROL", Map.of("urc", urc, "urowcnt", rows));
        return out;
    }

    private void backend(int serialRows) {
        p = new Parity();
        p.on("VHCF49B", in -> reply(0, 2, Map.of("VHCF49B_DATA", Map.of("dataRow", List.of(
                Map.of("stkDesc", Map.of("stockNo", "123456789", "stockShortDesc", "TRUCK ASSY")), Map.of("stkDesc", Map.of("stockNo", "987654321", "stockShortDesc", "AXLE")))))));
        p.on("VHCF49E", in -> reply(carUrc, 1, Map.of("VHCF49E_DATA", Map.of("dataRow", List.of(Map.of("carNo", "1101"))))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < serialRows; i++) rows.add(row("123456789", "SN" + (i + 1)));
        p.on("VHCF49A", in -> reply(listUrc, listCount >= 0 ? listCount : rows.size(), Map.of("VHCF49A_DATA", Map.of("dataRow", rows))));
        for (String l : List.of("VHCF50B", "VHCF50C")) p.on(l, in -> reply(0, 2, Map.of(l + "_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"))))));
        p.on("VHCF50E", in -> reply(0, 1, Map.of("VHCF50E_DATA", Map.of("dataRow", List.of(Map.of("configCmpntStat", "ACTIVE"))))));
        p.on("VHCF50F", in -> reply(0, 1, Map.of("VHCF50F_DATA", Map.of("dataRow", List.of(Map.of("serialDesc", Map.of("serialStat", "ACT", "serialStatDesc", "ACTIVE")))))));
        p.on("VHCF50J", in -> reply(0, 1, Map.of("VHCF50J_DATA", Map.of("dataRow", List.of(Map.of("posnCd", "A1", "posnCdDesc", "AXLE 1"))))));
        p.on("VHCF50I", in -> reply(0, 1, Map.of("VHCF50I_DATA", Map.of("comments", "OLD COMMENT"))));
        p.on("VHCF50D", in -> reply(dateUrc, 0, Map.of()));
        p.on("VHCF50A", in -> reply(0, 0, Map.of()));
        p.on("VHCF51A", in -> reply(0, 1, Map.of("VHCF51A_DATA", Map.of("dataRow", List.of(Map.of("stockNo", "123456789", "cmpntSerialNo", "SN1",
                "cmpntCumHrs", 1200, "cmpntCumMin", 30, "dateHrsPosted", "2024-02-03"))))));
        p.on("VHCF51B", in -> reply(0, 0, Map.of()));
        p.on("VHCF52C", in -> reply(0, 2, Map.of("VHCF52C_DATA", Map.of("dataRow", List.of(Map.of("yrd", "CY"), Map.of("yrd", "HY"))))));
        p.on("VHCF52A", in -> reply(0, 1, Map.of("VHCF52A_DATA", Map.of("dataRow", List.of(Map.of("stockNo", "123456789", "cmpntSerialNo", "SN1",
                "geoDataName", "WHEEL DIA", "dateMeasured", "2024-02-03", "loc", "HY", "measurement", new BigDecimal("31.25"), "carNo", "1101"))))));
        p.on("VHCF52B", in -> reply(0, 0, Map.of()));
        p.on("AFCXVLD", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("AFCXVLD_CRIT"));
            crit.put("dateError", afcxvldError == 1 ? "Y" : "N");
            crit.put("otherSqlError", "N");
            return reply(0, 0, Map.of("AFCXVLD_CRIT", crit));
        });
    }

    private Tables3SerialService serials() {
        return new Tables3SerialService(p.legacy(), p.messages(), Parity.bundle());
    }

    private Tables3SerialDataService data() {
        return new Tables3SerialDataService(p.legacy(), p.messages(), Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> windows(ScreenSession s) {
        return (List<Map<String, Object>>) s.state().get("windows");
    }

    private static String id(ScreenSession s, String screen) {
        return windows(s).stream().filter(w -> screen.equals(w.get("screen"))).findFirst().orElseThrow(() -> new AssertionError(screen + " not open")).get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> widgets(ScreenSession s, String screen) {
        return (Map<String, Map<String, Object>>) windows(s).stream().filter(w -> screen.equals(w.get("screen"))).findFirst().orElseThrow().get("widgets");
    }

    private static String value(ScreenSession s, String screen, String widget) {
        Object v = widgets(s, screen).get(widget).get("value");
        return v == null ? "" : v.toString().strip();
    }

    private List<Object> sent(String program, String record) {
        return p.sent.stream().filter(e -> e.getKey().equals(program)).map(e -> (Object) e.getValue().get(record)).toList();
    }

    /** the old VHCF49I listed with the criteria; the stock is the old drop-down's text, written as its binding does */
    private ScreenSession oldList(String car, String stockText, String serial) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF49I", p.user);
        String id = id(s, "VHCF49I");
        if (stockText != null) s.window(id).recordPart("VHCF49IW_CRIT").set("STK-NUM data", stockText);
        s.event(id, List.of(new ScreenSession.Change("Text3", "object", car), new ScreenSession.Change("Text1", "object", serial)), "Push_Button1", "clicked", null);
        return s;
    }

    @Test
    void theStockListIsTheOldOne() {
        backend(2);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCF49I", p.user);
        @SuppressWarnings("unchecked") List<String> items = (List<String>) widgets(s, "VHCF49I").get("Drop-down_List1").get("items");
        Object oldCrit = sent("VHCF49B", "VHCF49B_CRIT");
        p.clear();
        Tables3Serials.Options o = serials().options();
        assertThat(sent("VHCF49B", "VHCF49B_CRIT")).isEqualTo(oldCrit);
        assertThat(o.stocks().stream().map(Tables3Serials.Stock::text).toList()).isEqualTo(items.stream().map(String::strip).toList());
        assertThat(o.stocks().get(0).stock()).isEqualTo("123456789");
    }

    private void sameList(String car, String stock, String stockText, String serial, int rows) {
        backend(rows);
        ScreenSession s = oldList(car, stockText, serial);
        List<Object> oldCar = sent("VHCF49E", "VHCF49E_CRIT"), oldList = sent("VHCF49A", "VHCF49A_CRIT");
        String oldMessage = value(s, "VHCF49I", "Text2");
        @SuppressWarnings("unchecked") List<List<Object>> oldRows = (List<List<Object>>) widgets(s, "VHCF49I").get("VAGen_Container_Details1").get("rows");
        p.clear();
        String message;
        List<Map<String, Object>> apiRows = List.of();
        try {
            Tables3Serials.Rows r = serials().list(car, stock, serial);
            message = r.message() == null ? "" : r.message();
            apiRows = r.rows();
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        }
        assertThat(sent("VHCF49E", "VHCF49E_CRIT")).as("VHCF49E").isEqualTo(oldCar);
        assertThat(sent("VHCF49A", "VHCF49A_CRIT")).as("VHCF49A").isEqualTo(oldList);
        assertThat(message).isEqualTo(oldMessage);
        assertThat(apiRows.stream().map(x -> x.get("cmpntSerialNo")).toList())
                .isEqualTo(oldRows == null ? List.of() : oldRows.stream().map(x -> x.get(1).toString().strip()).filter(x -> !x.isEmpty()).toList());
    }

    @Test
    void listingSendsTheOldCriteria() {
        sameList("1101", "123456789", "123456789 TRUCK ASSY", "SN1", 2);   // car checked, one stock, one serial
        sameList("", "", null, "", 3);                                       // every car, stock and serial
        sameList("", "ALL", "ALL", "", 1);                                   // "ALL" stock
    }

    @Test
    void listingMessages() {
        // "MORE THAN 90 ROWS" cannot happen on the old screen: VHCF49A_DATA holds 85 rows, and a row count of 90 makes its
        // table load fail (getFieldsStartingAt_to_(1, 90)); the API test covers the API's message
        listUrc = 100;
        sameList("", "", null, "", 0);                                       // nothing: no data
        listUrc = 0;
        carUrc = 8;
        sameList("9999", "", null, "", 1);                                   // a car VHCF49E does not know
    }

    /** VHCF49I List, select row 1, Update: VHCF50I opens (its lists and comments) */
    private ScreenSession oldDetail() {
        ScreenSession s = oldList("1101", "123456789 TRUCK ASSY", "");
        String id = id(s, "VHCF49I");
        p.clear();
        s.event(id, List.of(new ScreenSession.Change("VAGen_Container_Details1", "selectionIndices", List.of(1))), "VAGen_Container_Details1", "selectionIndices", null);
        s.event(id, List.of(), "Push_Button31", "clicked", null);
        return s;
    }

    @Test
    void theDetailOpensWithTheOldCallsAndForm() {
        backend(2);
        ScreenSession s = oldDetail();
        List<String> oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();
        Object oldComments = sent("VHCF50I", "VHCF50I_CRIT");
        p.clear();
        Tables3Serials.Form f = serials().form(row("123456789", "SN1"));
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        assertThat(sent("VHCF50I", "VHCF50I_CRIT")).isEqualTo(oldComments);
        assertThat(f.comments()).isEqualTo(value(s, "VHCF50I", "mleNarrative"));
        assertThat(f.form().get("dateAxle")).isEqualTo(value(s, "VHCF50I", "Text8"));          // dates as the old form shows them
        assertThat(f.form().get("dateInstalled")).isEqualTo(value(s, "VHCF50I", "Text9"));
        assertThat(f.form().get("dateBuyRebuilt")).isEqualTo(value(s, "VHCF50I", "Text7"));
        assertThat(f.form().get("dateTrkRemove")).isEqualTo(value(s, "VHCF50I", "Text26"));    // shown as stored
        assertThat(f.form().get("stockNo")).isEqualTo(value(s, "VHCF50I", "Text1"));
        @SuppressWarnings("unchecked") List<String> yards = (List<String>) widgets(s, "VHCF50I").get("Drop-down_List1").get("items");
        assertThat(f.lists().get("VHCF50B").stream().map(Tables3Serials.Choice::text).toList()).isEqualTo(yards);
    }

    /** the old VHCF50I's working record VHCF50A-ADDUPDT, field by field, as the API's form (names in JSON) */
    private static Map<String, Object> oldForm(ScreenSession s, Map<String, Object> row) {
        var rec = s.window(id(s, "VHCF50I")).recordPart("VHCF50A-ADDUPDT");
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : row.keySet()) {
            Object v = rec.get(egl(k) + " data");
            out.put(k, v instanceof String t ? t.stripTrailing() : v);
        }
        // PARENT-STOCK-NO and SERIAL-STAT are sub-fields of the drop-down texts STK-DESC (9 + ...) and SERIAL-DESC (3 + ...)
        out.put("parentStockNo", head(rec.get("STK-DESC data"), 9));
        out.put("serialStat", head(rec.get("SERIAL-DESC data"), 3));
        return out;
    }

    private static String head(Object text, int n) {
        String t = text == null ? "" : text.toString();
        return (t.length() > n ? t.substring(0, n) : t).stripTrailing();
    }

    static String egl(String json) {
        return json.replaceAll("([A-Z])", "-$1").toUpperCase(Locale.ROOT);
    }

    /** the old row as VHCF49A lists it, every field (the API's "original") */
    private static Map<String, Object> listed(Map<String, Object> scripted) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : List.of("stockNo", "homeYrd", "carNo", "carNoPrev", "cmpntEquipType", "cmpntSerialNo", "cmpntTrackingCd", "configStat", "dateAxle",
                "dateBuyRebuilt", "dateInstalled", "dateLastRemoved", "dateLastUpd", "dateMaintUpd", "emplNo", "lastRemovedBy", "parentEquipType", "parentPosnCd",
                "parentSerialNo", "parentStockNo", "posnCd", "serialConfigdInd", "serialStat", "shopLoc", "dateHubCoupRepl", "secCmpntStat", "cmpntFailureCd",
                "jobNo", "subJobDocNo", "armatureNo", "dateCmpntEvaluat", "dateCmpntRepr", "prevTrkSerialNo", "prevTrkPosCd", "prevTrkStockNo", "prevAxlSerialNo",
                "prevAxlePosCd", "dateTrkRemove", "timeTrkRemove", "truckStat", "termLastUpd", "termMaintUpd", "timeLastUpd", "timeMaintUpd")) {
            Object v = scripted.get(k);
            out.put(k, v == null ? (k.equals("subJobDocNo") || k.equals("armatureNo") ? 0 : "") : v);
        }
        return out;
    }

    /** the fields VHCF50A_DATA and VHCF50A-ADDUPDT have at the same place (before STK-DESC): the engine's byte move keeps them */
    private static final List<String> ALIGNED = List.of("stockNo", "homeYrd", "carNo", "carNoPrev", "cmpntEquipType", "cmpntSerialNo", "cmpntTrackingCd",
            "configStat", "dateAxle", "dateBuyRebuilt", "dateInstalled", "dateLastRemoved", "dateLastUpd", "dateMaintUpd", "emplNo", "lastRemovedBy",
            "parentEquipType", "parentPosnCd", "parentSerialNo");

    private void sameUpdate(Map<String, String> typed) {
        backend(2);
        ScreenSession s = oldDetail();
        String id2 = id(s, "VHCF50I");
        List<ScreenSession.Change> changes = new ArrayList<>();
        typed.forEach((w, v) -> changes.add(new ScreenSession.Change(w, "object", v)));
        s.event(id2, changes, null, null, null);
        Map<String, Object> original = listed(row("123456789", "SN1"));
        Map<String, Object> form = oldForm(s, original);
        String comments = value(s, "VHCF50I", "mleNarrative");
        p.clear();
        s.event(id2, List.of(), "Push_Button1", "clicked", null);
        List<Object> oldDates = sent("VHCF50D", "VHCF50D_DATA"), oldCrit = sent("VHCF50A", "VHCF50A_CRIT");
        List<Object> oldData = sent("VHCF50A", "VHCF50A_DATA");
        String oldMessage = value(s, "VHCF50I", "Text34");
        p.clear();
        Tables3Serials.Result r = serials().update(new Tables3Serials.Change(original, form, comments));
        assertThat(sent("VHCF50D", "VHCF50D_DATA")).as("VHCF50D").isEqualTo(oldDates);
        assertThat(sent("VHCF50A", "VHCF50A_CRIT")).as("VHCF50A crit").isEqualTo(oldCrit);
        assertThat(r.message()).isEqualTo(oldMessage);
        List<Object> data = sent("VHCF50A", "VHCF50A_DATA");
        assertThat(data).hasSameSizeAs(oldData);
        if (data.isEmpty()) return;
        @SuppressWarnings("unchecked") Map<String, Object> mine = (Map<String, Object>) data.get(0), old = (Map<String, Object>) oldData.get(0);
        for (String k : ALIGNED) assertThat(mine.get(k)).as(k).isEqualTo(old.get(k));
        assertThat(mine.get("comments")).isEqualTo(old.get("comments"));
        // after STK-DESC the old working record and VHCF50A_DATA differ in layout: the engine copies the bytes, the API
        // copies by name (the old record's sub-fields PARENT-STOCK-NO and SERIAL-STAT are named for that)
        assertThat(mine.get("parentStockNo")).isEqualTo(form.get("parentStockNo"));
        assertThat(mine.get("dateTrkRemove")).isEqualTo(form.get("dateTrkRemove"));
    }

    @Test
    void anUpdateSendsTheOldRecords() {
        sameUpdate(Map.of("Text8", "01-05-2020", "mleNarrative", "NEW COMMENT"));              // one date changed: VHCF50D for it
        sameUpdate(Map.of());                                                                  // nothing changed: no date check
        sameUpdate(Map.of("Text8", "01-05-2020", "Text9", "02-06-2021", "Text7", "bad"));      // several dates, one not a date
    }

    @Test
    void updateRefusals() {
        dateUrc = 8;
        sameUpdate(Map.of("Text8", "01-05-2020", "Text9", "02-06-2021"));                     // VHCF50D refuses: the last date's message
        dateUrc = 0;
        sameUpdate(Map.of("Text1", ""));                                                       // no stock number
    }

    /** VHCF49I List, row 1 selected (or not), Serial No Hrs / Serial Geo Data */
    private ScreenSession oldChild(String button, boolean select, String fromGui) {
        ScreenSession s = oldList("1101", "123456789 TRUCK ASSY", "");
        String id = id(s, "VHCF49I");
        s.window(id).recordPart("VHCF49I-TRIGGER").set("FROM-GUI data", fromGui);
        if (select) s.event(id, List.of(new ScreenSession.Change("VAGen_Container_Details1", "selectionIndices", List.of(1))), "VAGen_Container_Details1", "selectionIndices", null);
        p.clear();
        s.event(id, List.of(), button, "clicked", null);
        return s;
    }

    @Test
    void hoursListAsTheOldScreen() {
        for (boolean select : List.of(true, false)) {
            backend(2);
            ScreenSession s = oldChild("Push_Button51", select, "");
            Object old = sent("VHCF51A", "VHCF51A_CRIT");
            p.clear();
            Tables3Serials.Rows r = select ? data().hours("123456789", "SN1") : data().hours("", "");
            assertThat(sent("VHCF51A", "VHCF51A_CRIT")).isEqualTo(old);
            @SuppressWarnings("unchecked") List<List<Object>> rows = (List<List<Object>>) widgets(s, "VHCF51I").get("VAGen_Container_Details1").get("rows");
            assertThat(r.rows().get(0).get("cmpntSerialNo")).isEqualTo(rows.get(0).get(1).toString().strip());
        }
    }

    private void sameHours(String date, String hours, String minutes) {
        backend(2);
        ScreenSession s = oldChild("Push_Button51", true, MRS14I);
        String id = id(s, "VHCF51I");
        s.event(id, List.of(new ScreenSession.Change("VAGen_Container_Details1", "selectionIndices", List.of(1))), "VAGen_Container_Details1", "selectionIndices", null);
        p.clear();
        s.event(id, List.of(new ScreenSession.Change("Text4", "object", date), new ScreenSession.Change("Text5", "object", hours),
                new ScreenSession.Change("Text6", "object", minutes)), "Push_Button1", "clicked", null);
        List<Object> oldCheck = sent("AFCXVLD", "AFCXVLD_CRIT"), oldCrit = sent("VHCF51B", "VHCF51B_CRIT"), oldData = sent("VHCF51B", "VHCF51B_DATA");
        String oldMessage = value(s, "VHCF51I", "Text1");
        p.clear();
        Tables3Serials.Result r = data().updateHours(new Tables3Serials.HoursChange("123456789", "SN1", Long.parseLong(hours), Long.parseLong(minutes), date));
        assertThat(sent("AFCXVLD", "AFCXVLD_CRIT")).as("AFCXVLD").isEqualTo(oldCheck);
        assertThat(sent("VHCF51B", "VHCF51B_CRIT")).as("VHCF51B crit").isEqualTo(oldCrit);
        assertThat(sent("VHCF51B", "VHCF51B_DATA")).as("VHCF51B data").isEqualTo(oldData);
        sameMessage(r, oldMessage);
    }

    @Test
    void hoursUpdateAsTheOldScreen() {
        sameHours("03-04-2024", "1300", "15");
        afcxvldError = 1;
        sameHours("13-45-2024", "1300", "15");                                                 // AFCXVLD says the date is wrong
        afcxvldError = 0;
    }

    @Test
    void geoDataListAsTheOldScreen() {
        backend(2);
        ScreenSession s = oldChild("Push_Button61", true, "");
        List<String> oldCalls = p.sent.stream().map(Map.Entry::getKey).toList();
        Object old = sent("VHCF52A", "VHCF52A_CRIT"), oldLoc = sent("VHCF52C", "VHCF52C_CRIT");
        p.clear();
        Map<String, Object> r = data().geoData("123456789", "SN1");
        assertThat(p.sent.stream().map(Map.Entry::getKey).toList()).isEqualTo(oldCalls);
        assertThat(sent("VHCF52A", "VHCF52A_CRIT")).isEqualTo(old);
        assertThat(sent("VHCF52C", "VHCF52C_CRIT")).isEqualTo(oldLoc);
        @SuppressWarnings("unchecked") List<String> locations = (List<String>) widgets(s, "VHCF52I").get("Drop-down_List1").get("items");
        assertThat(r.get("locations")).isEqualTo(locations.stream().map(String::strip).toList());
    }

    private void sameGeo(String date, String measurement) {
        backend(2);
        ScreenSession s = oldChild("Push_Button61", true, MRS14I);
        String id = id(s, "VHCF52I");
        s.event(id, List.of(new ScreenSession.Change("VAGen_Container_Details1", "selectionIndices", List.of(1))), "VAGen_Container_Details1", "selectionIndices", null);
        s.event(id, List.of(new ScreenSession.Change("Text5", "object", date), new ScreenSession.Change("Text7", "object", measurement),
                new ScreenSession.Change("Text8", "object", "ABC"), new ScreenSession.Change("Text9", "object", "54321")), null, null, null);
        var pass = s.window(id).recordPart("VHCF52I_PASSVALUE");
        Map<String, String> shown = new HashMap<>();
        for (String f : List.of("GEO-DATA-NAME", "TIME-MEASURED", "LOC", "POSN-CD", "CAR-NO")) shown.put(f, str(pass.get(f + " data")));
        p.clear();
        s.event(id, List.of(), "Push_Button1", "clicked", null);
        List<Object> oldCheck = sent("AFCXVLD", "AFCXVLD_CRIT"), oldCrit = sent("VHCF52B", "VHCF52B_CRIT"), oldData = sent("VHCF52B", "VHCF52B_DATA");
        String oldMessage = value(s, "VHCF52I", "Text1");
        p.clear();
        Tables3Serials.Result r = data().updateGeo(new Tables3Serials.GeoChange("123456789", "SN1", shown.get("GEO-DATA-NAME"), date, shown.get("TIME-MEASURED"),
                measurement, "ABC", "54321", shown.get("LOC"), shown.get("POSN-CD"), shown.get("CAR-NO")));
        assertThat(sent("AFCXVLD", "AFCXVLD_CRIT")).as("AFCXVLD").isEqualTo(oldCheck);
        assertThat(sent("VHCF52B", "VHCF52B_CRIT")).as("VHCF52B crit").isEqualTo(oldCrit);
        assertThat(sent("VHCF52B", "VHCF52B_DATA")).as("VHCF52B data").isEqualTo(oldData);
        // after a good update VHCF52I lists again (VHCF52IS_INIT), which empties the message: the old screen showed
        // nothing; the API says it was saved
        if (r.ok()) assertThat(oldMessage).isEmpty();
        else sameMessage(r, oldMessage);
    }

    /**
     * The update's message is the old screen's, except a refused date: Update returns before setting UP-TRIGGER, then
     * its hasExecuted runs INIT, which empties the message, so the old screen showed nothing. The API says why.
     */
    private static void sameMessage(Tables3Serials.Result r, String oldMessage) {
        if (!r.ok() && r.detail() == null) assertThat(oldMessage).isEmpty();
        else assertThat(r.message()).isEqualTo(oldMessage);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    @Test
    void geoDataUpdateAsTheOldScreen() {
        sameGeo("03-04-2024", "32.5");
        afcxvldError = 1;
        sameGeo("13-45-2024", "32.5");
        afcxvldError = 0;
    }
}
