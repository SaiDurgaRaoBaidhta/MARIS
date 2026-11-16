package com.bart.maris.middleware.api.tables2;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.tables2.Tables2MessageDetailsService;
import com.bart.maris.middleware.domain.tables2.Tables2Messages;
import com.bart.maris.middleware.domain.tables2.Tables2MessagesService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MRS messages received (old VHCCS04I and the windows it opens: VHCCS05I detail, VHCCS06I cars, VHCCS07I errors, VHCCS18I
 * hard copy) run by the engine, against the clean API on the same scripted backend: the records each sends to every
 * program, writers included, and the messages.
 */
class Tables2MessagesParityTest {
    static final String TS = "2026-11-14-10.15.00.000000";

    static Map<String, Object> ok(int rows, Map<String, Object> records) {
        Map<String, Object> out = new HashMap<>(records);
        out.put("XDCONTROL", Map.of("urc", 0, "urowcnt", rows));
        return out;
    }

    static Map<String, Object> message(String ts, String scanner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tsMsgRcvd", ts);
        m.put("conCurrLoc", "HY01");
        m.put("conCurrYrd", "HY");
        m.put("conNo", "201");
        m.put("scannerId", Map.of("scannerIdX", scanner, "scnFil1", "", "scanIdDir", ""));
        m.put("iCars", "1101 1102 1103");
        m.put("conSize", 3);
        m.put("dashNo", 2);
        m.put("dateCreate", "2026-11-14");
        m.put("dateTrans", "2026-11-14");
        m.put("dateIcsCreate", "2026-11-13");
        m.put("timeCreate", "10.15.00");
        m.put("timeTrans", "10.15.01");
        m.put("msgData", "PAYLOAD");
        m.put("msgLength", 42);
        m.put("msgProcessedCd", "Y");
        m.put("msgSeqNo", 7);
        m.put("msgTranCd", "05");
        m.put("msgVers", "01");
        m.put("trainDir", "N");
        return m;
    }

    static Parity backend(int rows) {
        Parity p = new Parity();
        p.on("VHCCS4E", in -> ok(2, Map.of("VHCCS4E_DATA", Map.of("dataRow", List.of(Map.of("yrd", "HY"), Map.of("yrd", "CY"))))));
        p.on("VHCCS4F", in -> ok(2, Map.of("VHCCS4F_DATA", Map.of("dataRow", List.of(Map.of("tranCd", "05"), Map.of("tranCd", "07"))))));
        p.on("VHCCS4G", in -> ok(1, Map.of("VHCCS4G_DATA", Map.of("dataRow", List.of(Map.of("loc", "HY01"))))));
        p.on("VHCCS4H", in -> ok(2, Map.of("VHCCS4H_DATA", Map.of("dataRow", List.of(
                Map.of("scanId", Map.of("scannerId", "A", "scFil", "-", "loc", "HY01", "scFil2", "-", "scanDesc", "N")),
                Map.of("scanId", Map.of("scannerId", "B", "scFil", "-", "loc", "CY02", "scFil2", "-", "scanDesc", "S")))))));
        p.on("VHCCS4I", in -> ok(2, Map.of("VHCCS4I_DATA", Map.of("dataRow", List.of(Map.of("msgProcessedCd", "Y"), Map.of("msgProcessedCd", "N"))))));
        p.on("AFCXVLD", in -> ok(0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N", "formDate", "2026-11-14"))));
        p.on("VHYTIME", in -> ok(0, Map.of()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) list.add(message(i == 0 ? TS : "2026-11-14-10.16." + String.format("%02d", i) + ".000000", i % 3 == 1 ? "" : i % 3 == 2 ? "Q" : "B"));
        p.on("VHCCS4A", in -> rows == 0 ? Map.of("XDCONTROL", Map.of("urc", 100))
                : ok(rows, Map.of("VHCCS4A_DATA", Map.of("dataRow", list, "cntThresholdRcv", 50, "msgtobeprocd", 3))));
        p.on("VHCCS5B", in -> ok(2, Map.of("VHCCS5B_DATA", Map.of("dataRow", List.of(Map.of("yrd", "HY"), Map.of("yrd", "CY"))))));
        p.on("VHCCS5E", in -> ok(1, Map.of("VHCCS5E_DATA", Map.of("dataRow", List.of(Map.of("scannerData", Map.of("scannerId", "B", "scanFil1", "", "scannerIdDesc", "BAY")))))));
        p.on("VHCCS5N", in -> ok(0, Map.of()));
        p.on("VHCCS5M", in -> ok(0, Map.of()));
        p.on("VHCCS5C", in -> ok(0, Map.of()));
        p.on("VHCCS6A", in -> ok(2, Map.of("VHCCS6A_DATA", Map.of("dataRow", List.of(
                Map.of("tsMsgRcvd", TS, "carNo", "1101", "carOrder", 1, "conNo", "201", "dashNo", 2, "dateInsrvRev", "2026-11-14", "vagenOrientation", "<", "orientationDir", "N"),
                Map.of("tsMsgRcvd", TS, "carNo", "1102", "carOrder", 2, "conNo", "201", "dashNo", 2, "dateInsrvRev", "2026-11-14", "vagenOrientation", ">", "orientationDir", "S"))))));
        p.on("VHCCS6E", in -> ok(2, Map.of("VHCCS6E_DATA", Map.of("dataRow", List.of(Map.of("vagenOrientation", "<"), Map.of("vagenOrientation", ">"))))));
        p.on("VHCCS6C", in -> ok(0, Map.of()));
        p.on("VHCCS6D", in -> ok(0, Map.of()));
        p.on("VHCCS7A", in -> ok(1, Map.of("VHCCS7A_DATA", Map.of("dataRow", List.of(Map.of("tsMsgRcvd", TS, "cntErrs", 2, "fldName", "CAR-NO", "marisFunc", "MOVE",
                "msgErrDesc", "BAD CAR", "msgErrType", "E"))))));
        p.on("VHCCS7C", in -> ok(0, Map.of()));
        p.on("VHCCS7D", in -> ok(0, Map.of()));
        p.on("WS70A01", in -> ok(1, Map.of("WS70A01_DATA", Map.of("dataRow", List.of(Map.of("prtrLocDesc", "DUBLIN", "vtamName", "DUBLNHP1"))))));
        p.on("VHCCSHB", in -> ok(1, Map.of("VHCCSHB_DATA", Map.of("dataRow", List.of(Map.of("tranCd", "05"))))));
        p.on("VHCCSHC", in -> ok(1, Map.of("VHCCSHC_DATA", Map.of("dataRow", List.of(Map.of("yrd", "HY"))))));
        p.on("VHCCSHD", in -> ok(1, Map.of("VHCCSHD_DATA", Map.of("dataRow", List.of(Map.of("loc", "HY01"))))));
        p.on("VHCCSHE", in -> ok(1, Map.of("VHCCSHE_DATA", Map.of("dataRow", List.of(Map.of("scanId", Map.of("scannerId", "A", "scFil", "-", "loc", "HY01")))))));
        p.on("VHCCSHF", in -> ok(1, Map.of("VHCCSHF_DATA", Map.of("dataRow", List.of(Map.of("msgProcessedCd", "Y"))))));
        p.on("VHCCSHA", in -> ok(0, Map.of()));
        return p;
    }

    static Tables2MessagesService api(Parity p) {
        return new Tables2MessagesService(p.legacy(), p.messages(), Parity.bundle(), Clock.systemDefaultZone());
    }

    static Tables2MessageDetailsService details(Parity p) {
        return new Tables2MessageDetailsService(p.legacy(), p.messages(), Parity.bundle());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    static String id(ScreenSession s, String screen) {
        Map<String, Object> w = window(s, screen);
        assertThat(w).as(screen + " is open").isNotNull();
        return (String) w.get("id");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> widget(ScreenSession s, String screen, String name) {
        Map<String, Object> w = window(s, screen);
        return w == null ? null : (Map<String, Object>) ((Map<String, Object>) w.get("widgets")).get(name);
    }

    static String value(ScreenSession s, String screen, String name) {
        Map<String, Object> x = widget(s, screen, name);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    static List<Map<String, Map<String, Object>>> sent(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls, String program) {
        List<Map<String, Map<String, Object>>> out = new ArrayList<>();
        for (var e : calls) {                                       // XDCONTROL carries the previous call's row count on the old side
            if (!e.getKey().equals(program)) continue;
            Map<String, Map<String, Object>> m = new LinkedHashMap<>(e.getValue());
            m.remove("XDCONTROL");
            out.add(m);
        }
        return out;
    }

    static List<String> programs(List<Map.Entry<String, Map<String, Map<String, Object>>>> calls) {
        return calls.stream().map(Map.Entry::getKey).toList();
    }

    /** the old VHCCS04I with its criteria typed, then List */
    static ScreenSession listed(Parity p, Tables2Messages.Criteria c) {
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCCS04I", p.user);
        String id = id(s, "VHCCS04I");
        var crit = s.window(id).recordPart("VHCCS4IW_CRIT");
        crit.set("MSG-TRAN-CD data", c.tranCode());
        crit.set("CON-CURR-YRD data", c.yard());
        crit.set("CON-CURR-LOC data", c.location());
        crit.set("SCAN-ID data", c.scanner());
        crit.set("MSG-PROCESSED-CD data", c.processed());
        s.event(id, List.of(new ScreenSession.Change("Text2", "object", c.fromDate()), new ScreenSession.Change("Text3", "object", c.fromTime()),
                new ScreenSession.Change("Text4", "object", c.toTime()), new ScreenSession.Change("Text6", "object", c.conNo())), "Push_Button8", "clicked", null);
        return s;
    }

    @Test
    void theOptionsMatchTheOldScreen() {
        Parity p = backend(1);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHCCS04I", p.user);
        var old = new ArrayList<>(p.sent);
        p.clear();
        Tables2Messages.Options o = api(p).options();
        for (String prog : List.of("VHCCS4E", "VHCCS4F", "VHCCS4G", "VHCCS4H", "VHCCS4I")) assertThat(sent(p.sent, prog)).as(prog).isEqualTo(sent(old, prog));
        assertThat(widget(s, "VHCCS04I", "Drop-down_List1").get("items")).isEqualTo(o.yards());
        assertThat(widget(s, "VHCCS04I", "Drop-down_List2").get("items")).isEqualTo(o.tranCodes());
        assertThat(widget(s, "VHCCS04I", "Drop-down_List3").get("items")).isEqualTo(o.locations());
        assertThat(widget(s, "VHCCS04I", "Drop-down_List4").get("items")).isEqualTo(o.scanners().stream().map(Tables2Messages.Scanner::id).toList());
        // the engine does not show the date VHCCS04I_INIT builds field by field in Text2; its record holds it
        assertThat(String.valueOf(s.window(id(s, "VHCCS04I")).recordPart("VHCCS4IW_CRIT").get("FROM-DATE data")).strip()).isEqualTo(o.fromDate());
        assertThat(value(s, "VHCCS04I", "Text3").substring(0, 2)).isEqualTo(o.fromTime().substring(0, 2));
        assertThat(value(s, "VHCCS04I", "Text4").substring(0, 5)).isEqualTo(o.toTime().substring(0, 5));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "11-14-2026|09:00:00|12:00:00|||||||3",              // everything
            "11-14-2026|09:00:00|12:00:00|05|201|HY|HY01|A-HY01-N|Y|3",
            "''|09:00:00|12:00:00|||||B-CY02-S||3",              // no date
            "11-14-2026|09:00:00|12:00:00|||||||0",              // nothing found
            "11-14-2026|09:00:00|12:00:00|||||||71"})            // the list is full
    void listMatchesTheOldScreen(String date, String from, String to, String tran, String con, String yard, String loc, String scan, String proc, int rows) {
        Tables2Messages.Criteria c = new Tables2Messages.Criteria(date, from, to, nz(tran), nz(con), nz(yard), nz(loc), nz(scan), nz(proc));
        Parity p = backend(rows);
        ScreenSession s = listed(p, c);
        String oldMessage = value(s, "VHCCS04I", "Text1");
        var old = new ArrayList<>(p.sent);
        p.clear();
        Tables2Messages.Listing l = api(p).list(c);
        for (String prog : List.of("AFCXVLD", "VHYTIME", "VHCCS4A")) assertThat(sent(p.sent, prog)).as(prog).isEqualTo(sent(old, prog));
        assertThat(l.message()).isEqualTo(oldMessage);
        // the engine leaves the old table empty (its rows come through a connection it does not run); the row count is in the record
        Object counter = s.window(id(s, "VHCCS04I")).recordPart("VHCCS4A_CRIT").get("COUNTER data");
        assertThat(l.messages()).hasSize(rows == 0 ? 0 : Integer.parseInt(String.valueOf(counter).strip()));
        if (rows > 0) assertThat(value(s, "VHCCS04I", "Text5")).isEqualTo(String.valueOf(l.toBeProcessed()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"AFCXVLD|Y|N", "AFCXVLD|N|Y", "VHYTIME-TO||", "VHYTIME-FROM||"})
    void listChecksMatchTheOldScreen(String which, String dateError, String sqlError) {
        Tables2Messages.Criteria c = new Tables2Messages.Criteria("13-45-2026", "09:00:00", "12:00:00", "", "", "", "", "", "");
        Parity p = backend(3);
        if (which.equals("AFCXVLD")) p.on("AFCXVLD", in -> ok(0, Map.of("AFCXVLD_CRIT", Map.of("dateError", dateError, "otherSqlError", sqlError))));
        else {
            String bad = which.endsWith("TO") ? "12.00.00" : "09.00.00";
            p.on("VHYTIME", in -> bad.equals(in.get("VHYTIME_DATA").get("timeMaintUpd"))
                    ? Map.of("XDCONTROL", Map.of("urc", 0, "umsgins", List.of("TIME", "", ""))) : ok(0, Map.of()));
        }
        ScreenSession s = listed(p, c);
        String oldMessage = value(s, "VHCCS04I", "Text1");
        var old = new ArrayList<>(p.sent);
        p.clear();
        String message;
        try { api(p).list(c); message = null; } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(message).isNotBlank().isEqualTo(oldMessage);
        assertThat(programs(p.sent)).isEqualTo(programs(old).stream().filter(x -> !x.startsWith("VHCCS4") || x.equals("VHCCS4A")).toList());
    }

    static Tables2Messages.Criteria everything() {
        return new Tables2Messages.Criteria("11-14-2026", "09:00:00", "12:00:00", "", "", "", "", "", "");
    }

    /** the old VHCCS04I listed, its first row chosen */
    static ScreenSession chosen(Parity p) {
        ScreenSession s = listed(p, everything());
        s.event(id(s, "VHCCS04I"), List.of(), "VAGen_Container_Details1", "select", 1);
        return s;
    }

    /** what VHCCS05I shows for a message (dates MM-DD-YYYY), by its record's field names */
    static final Map<String, Object> SHOWN = new LinkedHashMap<>();
    static {
        SHOWN.put("TS-MSG-RCVD", TS);
        SHOWN.put("CON-CURR-LOC", "HY01");
        SHOWN.put("CON-CURR-YRD", "HY");
        SHOWN.put("CON-NO", "201");
        SHOWN.put("I-CAR-1", "1101");
        SHOWN.put("I-CAR-2", "1102");
        SHOWN.put("I-CAR-3", "1103");
        SHOWN.put("CON-SIZE", 3);
        SHOWN.put("DASH-NO", 2);
        SHOWN.put("DATE-CREATE", "11-14-2026");
        SHOWN.put("DATE-TRANS", "11-14-2026");
        SHOWN.put("DATE-ICS-CREATE", "11-13-2026");
        SHOWN.put("TIME-CREATE", "10.15.00");
        SHOWN.put("TIME-TRANS", "10.15.01");
        SHOWN.put("MSG-DATA", "PAYLOAD");
        SHOWN.put("MSG-LENGTH", 42);
        SHOWN.put("MSG-PROCESSED-CD", "Y");
        SHOWN.put("MSG-SEQ-NO", 7);
        SHOWN.put("MSG-TRAN-CD", "05");
        SHOWN.put("MSG-VERS", "01");
    }

    /** the same as the API takes it (VHCCS5C_DATA's names; the cars as VHCCS4A's 50-character I-CARS) */
    static Map<String, Object> asApi(Map<String, Object> shown) {
        Map<String, Object> out = new LinkedHashMap<>();
        StringBuilder cars = new StringBuilder();
        for (var e : shown.entrySet()) {
            if (e.getKey().startsWith("I-CAR-")) { cars.append(String.format("%-5s", e.getValue())); continue; }
            String[] parts = e.getKey().toLowerCase().split("-");
            StringBuilder k = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) k.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            out.put(k.toString(), e.getValue());
        }
        out.put("iCars", cars.toString());
        return out;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"ok|0|0", "no length|0|0", "bad trans date|8|0", "bad ics date and time|4|8", "update fails|0|0"})
    void detailSaveMatchesTheOldScreen(String which, int badDate, int badTime) {
        Parity p = backend(3);
        if (which.equals("update fails")) p.on("VHCCS5C", in -> Map.of("XDCONTROL", Map.of("urc", 8)));
        if (badDate > 0) p.on("VHCCS5N", in -> String.valueOf(in.get("VHCCS5N_DATA").get("dateMaintUpd")).equals(badDate == 8 ? "2026-11-14" : "2026-11-13")
                ? Map.of("XDCONTROL", Map.of("urc", 8)) : ok(0, Map.of()));
        if (badTime > 0) p.on("VHCCS5M", in -> String.valueOf(in.get("VHCCS5M_DATA").get("timeMaintUpd")).equals("10.15.01") ? Map.of("XDCONTROL", Map.of("urc", 8)) : ok(0, Map.of()));
        Map<String, Object> shown = new LinkedHashMap<>(SHOWN);
        if (which.equals("no length")) shown.put("MSG-LENGTH", 0);
        ScreenSession s = chosen(p);
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button1", "clicked", null);
        String w = id(s, "VHCCS05I");
        var rec = s.window(w).recordPart("VHCCS05A_ADDUPDT");                     // what the chosen row puts in the fields
        shown.forEach((k, v) -> rec.set(k + " data", v));
        p.clear();
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        String oldMessage = value(s, "VHCCS05I", "Text37");
        var old = new ArrayList<>(p.sent);
        p.clear();
        String message;
        try { message = api(p).update(asApi(shown)).message(); } catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(programs(p.sent)).isEqualTo(programs(old));
        for (String prog : List.of("VHCCS5N", "VHCCS5M", "VHCCS5C")) assertThat(sent(p.sent, prog)).as(prog).isEqualTo(sent(old, prog));
        assertThat(message).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1102|2|201|2|11-14-2026|>|S", "1102|0|201|2|11-14-2026|>|S", "''|2|''|0|''|''|''", "1102|2|201|2|11-14-2026|''|S"})
    void carUpdateMatchesTheOldScreen(String car, int order, String con, int dash, String date, String orientation, String dir) {
        Parity p = backend(3);
        ScreenSession s = chosen(p);
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button6", "clicked", null);
        String w = id(s, "VHCCS06I");
        var opened = new ArrayList<>(p.sent);
        s.event(w, List.of(), "VAGen_Container_Details1", "select", 1);          // choosing a car enables Update
        var rec = s.window(w).recordPart("VHCCS06I_PASSVALUE");                  // what the chosen car puts in the fields
        rec.set("TS-MSG-RCVD data", TS);
        rec.set("CAR-NO data", car);
        rec.set("CAR-ORDER data", order);
        rec.set("CON-NO data", con);
        rec.set("DASH-NO data", dash);
        rec.set("DATE-INSRV-REV data", date);
        rec.set("VAGen_ORIENTATION data", orientation);
        rec.set("ORIENTATION-DIR data", dir);
        p.clear();
        s.event(w, List.of(), "Push_Button4", "clicked", null);
        String oldMessage = value(s, "VHCCS06I", "Text1");
        var old = new ArrayList<>(p.sent);
        p.clear();
        var d = details(p);
        d.cars(TS, null, null);
        assertThat(sent(p.sent, "VHCCS6A")).isEqualTo(sent(opened, "VHCCS6A").subList(0, 1));
        p.clear();
        String message;
        try { message = d.updateCar(new Tables2Messages.Car(TS, car, order, con, dash, date, orientation, dir)).message(); }
        catch (InvalidRequestException e) { message = e.getMessage(); }
        for (String prog : List.of("AFCXVLD", "VHCCS6C")) assertThat(sent(p.sent, prog)).as(prog).isEqualTo(sent(old, prog));
        assertThat(message).isEqualTo(oldMessage);
    }

    @Test
    void carDeleteMatchesTheOldScreen() {
        Parity p = backend(3);
        ScreenSession s = chosen(p);
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button6", "clicked", null);
        String w = id(s, "VHCCS06I");
        s.window(w).recordPart("VHCCS06I_PASSVALUE").set("TS-MSG-RCVD data", TS);
        s.window(w).recordPart("VHCCS06I_PASSVALUE").set("CAR-NO data", "1101");
        p.clear();
        s.event(w, List.of(), "Push_Button5", "clicked", null);
        var old = new ArrayList<>(p.sent);
        p.clear();
        details(p).deleteCar(TS, "1101");
        assertThat(sent(p.sent, "VHCCS6D")).isEqualTo(sent(old, "VHCCS6D"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"2|MOVE|BAD CAR|E", "0|MOVE|BAD CAR|''", "2|''|BAD CAR|''", "2|MOVE|''|E"})
    void errorUpdateMatchesTheOldScreen(int count, String function, String desc, String type) {
        Parity p = backend(3);
        ScreenSession s = listed(p, everything());
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button7", "clicked", null);
        String w = id(s, "VHCCS07I");
        var opened = new ArrayList<>(p.sent);
        s.event(w, List.of(), "VAGen_Container_Details1", "select", 1);          // choosing an error enables Update
        var rec = s.window(w).recordPart("VHCCS07I_PASSVALUE");                  // what the chosen error puts in the fields
        rec.set("TS-MSG-RCVD data", TS);
        rec.set("CNT-ERRS data", count);
        rec.set("FLD-NAME data", "CAR-NO");
        rec.set("MARIS-FUNC data", function);
        rec.set("MSG-ERR-DESC data", desc);
        rec.set("MSG-ERR-TYPE data", type);
        p.clear();
        s.event(w, List.of(), "Push_Button2", "clicked", null);
        String oldMessage = value(s, "VHCCS07I", "Text7");
        var old = new ArrayList<>(p.sent);
        p.clear();
        var d = details(p);
        Tables2Messages.Listing l = api(p).list(everything());
        p.clear();
        d.errors(null, l.compareFrom(), l.compareTo());
        assertThat(sent(p.sent, "VHCCS7A")).isEqualTo(sent(opened, "VHCCS7A").subList(0, 1));
        p.clear();
        String message;
        try { message = d.updateError(new Tables2Messages.Error(TS, count, null, "CAR-NO", function, desc, type, null, null)).message(); }
        catch (InvalidRequestException e) { message = e.getMessage(); }
        assertThat(sent(p.sent, "VHCCS7C")).isEqualTo(sent(old, "VHCCS7C"));
        if (count == 2 && !function.isEmpty() && !desc.isEmpty() && !type.isEmpty()) assertThat(message).isEqualTo(oldMessage);
        else {
            // deliberate difference: Update re-runs VHCCS07IS_INIT, which clears the old screen's check message; the API keeps it
            assertThat(oldMessage).isEmpty();
            assertThat(Parity.bundle().function("VHCCS07IS_UPDATE", "MRSTableMaintenanceApp2").toString()).contains(message);
        }
    }

    @Test
    void errorDeleteMatchesTheOldScreen() {
        Parity p = backend(3);
        ScreenSession s = listed(p, everything());
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button7", "clicked", null);
        String w = id(s, "VHCCS07I");
        s.window(w).recordPart("VHCCS07I_PASSVALUE").set("TS-MSG-RCVD data", TS);
        p.clear();
        s.event(w, List.of(), "Push_Button3", "clicked", null);
        var old = new ArrayList<>(p.sent);
        p.clear();
        details(p).deleteError(TS);
        assertThat(sent(p.sent, "VHCCS7D")).isEqualTo(sent(old, "VHCCS7D"));
    }

    @Test
    void hardCopyMatchesTheOldScreen() {
        Parity p = backend(3);
        ScreenSession s = listed(p, new Tables2Messages.Criteria("11-14-2026", "09:00:00", "12:00:00", "05", "201", "HY", "HY01", "A-HY01", "Y"));
        p.clear();
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button9", "clicked", null);
        String w = id(s, "VHCCS18I");
        var opened = new ArrayList<>(p.sent);
        s.event(w, List.of(), "VAGen_Container_Details1", "select", 1);
        s.event(w, List.of(), "Push_Button1", "clicked", null);
        var old = new ArrayList<>(p.sent);
        p.clear();
        Tables2Messages.PrintOptions o = api(p).printOptions();
        assertThat(programs(p.sent)).isEqualTo(programs(opened));
        for (String prog : programs(opened)) assertThat(sent(p.sent, prog)).as(prog).isEqualTo(sent(opened, prog));
        assertThat(widget(s, "VHCCS18I", "Drop-down_List4").get("items")).isEqualTo(o.scanners());
        p.clear();
        Map<String, Map<String, Object>> crit = sent(old, "VHCCSHA").get(0);
        @SuppressWarnings("unchecked") Map<String, Object> c = crit.get("VHCCSHA_CRIT");
        api(p).print(new Tables2Messages.Print(o.printers().get(0).vtam(), (String) c.get("fromDate"), (String) c.get("fromTime"), (String) c.get("toTime"),
                (String) c.get("msgTranCd"), (String) c.get("conNo"), (String) c.get("conCurrYrd"), (String) c.get("conCurrLoc"), (String) c.get("scannerId"), 1));
        assertThat(sent(p.sent, "VHCCSHA")).isEqualTo(sent(old, "VHCCSHA"));
    }

    @Test
    void theOldListDeleteNeverHasATimeStamp() {
        // VHCCS04I_DELETE sends VHCCS4I_PASSVALUE.TS-MSG-RCVD, which nothing fills: VHCCS4B gets a blank time stamp and the
        // screen says "Time stamp  invalid". The clean API therefore offers no delete of a whole message.
        Parity p = backend(3);
        ScreenSession s = chosen(p);
        p.clear();
        s.event(id(s, "VHCCS04I"), List.of(), "Push_Button3", "clicked", null);
        assertThat(sent(p.sent, "VHCCS4B")).hasSize(1);
        assertThat(sent(p.sent, "VHCCS4B").get(0).get("VHCCS4B_CRIT").get("tsMsgRcvd")).isEqualTo("");
    }

    static String mdy(String iso) {
        return iso.matches("\\d{4}-\\d{2}-\\d{2}") ? iso.substring(5, 7) + "-" + iso.substring(8) + "-" + iso.substring(0, 4) : iso;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
