package com.bart.maris.middleware.domain.tables2;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MRS messages received (old VHCCS04I): its criteria lists and proposed date / times (VHCCS04I_INIT: VHCCS4E, 4F, 4G, 4H,
 * 4I), List (VHCCS04I_LIST: AFCXVLD, VHYTIME for both times, VHCCS4A, each row's scanner direction from VHCCS4H), the
 * message detail Save (VHCCS05I, VHCCS05IS_SAVE in UPDATE mode: VHCCS5N / VHCCS5M checks, VHCCS5C) and the hard copy window
 * (VHCCS18I: WS70A01, VHCCSHC / B / D / E / F, VHCCSHA). Messages are read from the screens' statement groups.
 */
public class Tables2MessagesService {
    /** VHCCS04I_LIST: more rows than this and the screen warns (VHCCS4A_DATA holds 71) */
    static final int MAX_ROWS = 71;
    /** the dates VHCCS05IS-DATE-UPD turns from MM-DD-YYYY into YYYY-MM-DD, in its order */
    private static final List<String> SAVE_DATES = List.of("dateTrans", "dateCreate", "dateMsgCmplt", "dateIcsCreate", "dateInsrvRev");
    /** VHCCS5C_DATA's fields that VHCCS05I's widgets fill (SCANNER-ID and TRAIN-DIR have no widget, so they go blank) */
    private static final List<String> TEXT_FIELDS = List.of("tsMsgRcvd", "conCurrLoc", "conCurrLocIcs", "conCurrTrkId", "conCurrYrd", "conNo",
            "conNoTo", "iCurrLoc", "iCurrYrd", "iConTo", "iConNo", "iSize", "iTrainDir", "dateCreate", "dateIcsCreate", "dateInsrvRev",
            "dateMsgCmplt", "dateTrans", "destLoc", "destLocIcs", "destTrkId", "funcMsgCmplt", "inZoneFlag", "msgData", "msgProcessedCd",
            "msgRejectCd", "msgTranCd", "msgVers", "originLoc", "originLocIcs", "originTrkId", "processedByCntrl", "termMsgCmplt",
            "timeCreate", "timeIcsCreate", "timeMsgCmplt", "timeTrans", "userGrpMsgCmplt", "currConNo", "noCarsInd", "pseudoCmpltCd",
            "dateLastUpd", "timeLastUpd", "termLastUpd");
    private static final List<String> NUMBER_FIELDS = List.of("conSize", "dashNo", "dashNoTo", "msgLength", "msgSeqNo");

    private final Programs programs;
    private final Clock clock;
    private final String invalidDate, invalidTo, invalidFrom, tooMany, noData, timeSubsys, checkSubsys;
    private final String tsRequired, dateTransRequired, tranRequired, lengthRequired, updated, updateFailed, dateTransInvalid, dateCreateInvalid;
    private final List<String> sorts;

    public Tables2MessagesService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        List<String> list = Tables2Egl.messages(screens, "VHCCS04I_LIST", "MESG1", 5);
        invalidDate = list.get(0);
        invalidTo = list.get(1);
        invalidFrom = list.get(2);
        tooMany = list.get(3);
        noData = list.get(4);
        timeSubsys = Tables2Egl.first(screens, "VHCCS04I_LIST", "MARIS-SUBSYS");
        checkSubsys = Tables2Egl.first(screens, "VHCCS05IS_DT_CK", "MARIS-SUBSYS");
        List<String> save = Tables2Egl.messages(screens, "VHCCS05IS_SAVE", "MESG1", 8);
        tsRequired = save.get(0);
        dateTransRequired = save.get(1);
        tranRequired = save.get(2);
        lengthRequired = save.get(3);
        updated = save.get(6);
        updateFailed = save.get(7);
        List<String> check = Tables2Egl.messages(screens, "VHCCS05IS_DT_CK", "MESG1", 2);
        dateTransInvalid = check.get(0);
        dateCreateInvalid = check.get(1);
        List<String> s = new ArrayList<>();
        JsonNode screen = screens.screen("VHCCS18I");
        if (screen != null) for (JsonNode i : screen.path("widgets").path("Radio_Button_Set1").path("props").path("items")) s.add(i.asText());
        if (s.isEmpty()) throw new IllegalStateException("VHCCS18I has no sort choices");
        sorts = List.copyOf(s);
    }

    // ---- VHCCS04I ---------------------------------------------------------------------------------------------------

    /** VHCCS04I_INIT: a list that fails is a warning and the others still load, as on the old screen */
    public Tables2Messages.Options options() {
        List<String> warnings = new ArrayList<>();
        List<String> yards = values("VHCCS4E", "yrd", warnings);
        List<String> tran = values("VHCCS4F", "tranCd", warnings);
        List<String> locs = values("VHCCS4G", "loc", warnings);
        List<Tables2Messages.Scanner> scanners = scanners(warnings);
        List<String> processed = values("VHCCS4I", "msgProcessedCd", warnings);
        LocalDateTime now = LocalDateTime.now(clock);
        int hh = now.getHour();
        // VHCCS04I_INIT: three hours back, its own way (00 and 01 both give 22, 03 gives 24); the date stays today's
        int from = hh == 0 || hh == 1 ? 22 : hh == 2 ? 23 : hh == 3 ? 24 : hh - 3;
        String time = String.format("%02d:%02d", now.getMinute(), now.getSecond());
        return new Tables2Messages.Options(yards, tran, locs, scanners, processed,
                String.format("%02d-%02d-%04d", now.getMonthValue(), now.getDayOfMonth(), 2000 + now.getYear() % 100),
                String.format("%02d:%s", from, time), String.format("%02d:%s", hh, time), warnings);
    }

    private List<String> values(String program, String field, List<String> warnings) {
        List<String> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt(program, Map.of());
        if (!o.ok()) { warnings.add(o.error()); return out; }
        for (Map<String, Object> r : rows(o.result(), program + "_DATA", "dataRow")) out.add(text(r, field));
        return out;
    }

    private List<Tables2Messages.Scanner> scanners(List<String> warnings) {
        List<Tables2Messages.Scanner> out = new ArrayList<>();
        Programs.Outcome o = programs.attempt("VHCCS4H", Map.of());
        if (!o.ok()) { warnings.add(o.error()); return out; }
        for (Map<String, Object> r : rows(o.result(), "VHCCS4H_DATA", "dataRow")) {
            Map<String, Object> s = group(r, "scanId");
            String id = pad(text(s, "scannerId"), 1) + pad(text(s, "scFil"), 1) + pad(text(s, "loc"), 4) + pad(text(s, "scFil2"), 1) + pad(text(s, "scanDesc"), 1);
            out.add(new Tables2Messages.Scanner(id.stripTrailing(), text(s, "scannerId"), text(s, "loc"), text(s, "scanDesc")));
        }
        return out;
    }

    /** VHCCS04I_LIST */
    public Tables2Messages.Listing list(Tables2Messages.Criteria c) {
        String date = nz(c.fromDate());
        if (!date.isEmpty()) {                                                   // MRSCommonCode.AFCXVLD-VALIDATE
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("dateError", "N");
            crit.put("formDate", ymd(date));
            crit.put("otherSqlError", "N");
            Map<String, Object> back = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result().record("AFCXVLD_CRIT");
            String err = text(back, "dateError"), sql = text(back, "otherSqlError");
            if (err.equals("Y") && sql.equals("N") || err.equals("N") && sql.equals("Y")) throw new InvalidRequestException(invalidDate);
        }
        String to = dotted(c.toTime());
        if (!timeAccepted(to)) throw new InvalidRequestException(invalidTo);
        String from = dotted(c.fromTime());
        if (!timeAccepted(from)) throw new InvalidRequestException(invalidFrom);

        Map<String, Object> crit = new LinkedHashMap<>();
        char[] dated = pad(date, 10).toCharArray();                              // VHCCS04I_LIST puts the '-' fillers back, even in a blank date
        dated[2] = '-';
        dated[5] = '-';
        crit.put("fromDate", new String(dated).stripTrailing());
        crit.put("fromTime", from);
        crit.put("toTime", to);
        range(crit, "tranCdFr", "tranCdTo", nz(c.tranCode()), "99");
        range(crit, "conNoFr", "conNoTo", nz(c.conNo()), "999");
        range(crit, "conCurrYrdFr", "conCurrYrdTo", nz(c.yard()), "ZZZ");
        String scan = pad(c.scanner() == null ? "" : c.scanner(), 6);
        String scanner = scan.substring(0, 1).strip(), loc = scan.substring(2, 6).strip();
        if (scanner.isEmpty()) {
            crit.put("scannerIdFr", loc.equals("ALL") ? "A" : "");               // VHCCS04I_LIST: a blank scanner of location ALL starts at A
            crit.put("scannerIdTo", "Z");
        } else {
            crit.put("scannerIdFr", scanner);
            crit.put("scannerIdTo", scanner);
        }
        range(crit, "msgProcessedFr", "msgProcessedTo", nz(c.processed()), "9");
        range(crit, "conCurrLocFr", "conCurrLocTo", nz(c.location()), "999");
        String d = pad(date, 10);                                                // MM-DD-YYYY -> the YYYY-MM-DD of the time stamps
        String day = d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5);
        String compareTo = day + "-" + pad(to, 8) + ".000000", compareFrom = day + "-" + pad(from, 8) + ".000000";
        crit.put("compareDtT", compareTo);
        crit.put("compareDtF", compareFrom);

        ProgramResult r = programs.attempt("VHCCS4A", Map.of("VHCCS4A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) return new Tables2Messages.Listing(List.of(), noData, compareFrom, compareTo, 0, 0);
        List<Tables2Messages.Scanner> scanners = scanners(new ArrayList<>());
        List<Map<String, Object>> out = new ArrayList<>();
        String direction = "";                                                   // VHCCS04I-SCAN-ID-X: the last direction found carries on
        for (Map<String, Object> row : rows(r, "VHCCS4A_DATA", "dataRow")) {
            Map<String, Object> m = new LinkedHashMap<>(row);
            Map<String, Object> s = group(row, "scannerId");
            String x = text(s, "scannerIdX");
            if (!x.isEmpty()) for (Tables2Messages.Scanner sc : scanners) if (sc.scannerId().equals(x)) direction = sc.direction();
            m.remove("scannerId");
            m.put("scanner", x);
            m.put("scanDirection", x.isEmpty() ? text(s, "scanIdDir") : direction);
            out.add(m);
        }
        Map<String, Object> data = r.record("VHCCS4A_DATA");
        int count = r.control() == null ? out.size() : r.control().rowCount();
        return new Tables2Messages.Listing(out, count < MAX_ROWS ? "" : tooMany, compareFrom, compareTo,
                number(data, "cntThresholdRcv"), number(data, "msgtobeprocd"));
    }

    /** VHYTIME: the time is good when it answers no message insert (the screen looks at nothing else) */
    private boolean timeAccepted(String time) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("marisSubsys", timeSubsys);
        data.put("timeMaintUpd", time);
        ProgramResult r = programs.attempt("VHYTIME", Map.of("VHYTIME_DATA", data)).result();
        List<String> ins = r.control() == null ? List.of() : r.control().messageInserts();
        return ins == null || ins.isEmpty() || ins.get(0) == null || ins.get(0).isBlank();
    }

    /** VHCCS04I_LIST: a blank criterion asks for everything (blank .. high), a value for itself */
    private static void range(Map<String, Object> crit, String fr, String to, String value, String high) {
        crit.put(fr, value);
        crit.put(to, value.isEmpty() ? high : value);
    }

    // ---- VHCCS05I ---------------------------------------------------------------------------------------------------

    /**
     * VHCCS05IS_SAVE in UPDATE mode: the required fields, VHCCS05IS_DT_CK (dates YYYY-MM-DD, then VHCCS5N / VHCCS5M; a bad
     * transmission or create date stops the save, the other bad dates and times are blanked), then VHCCS5C
     */
    public Tables2Messages.Saved update(Map<String, Object> fields) {
        Map<String, Object> f = new LinkedHashMap<>(fields == null ? Map.of() : fields);
        String message = "";
        if (str(f, "tsMsgRcvd").isBlank()) message = tsRequired;
        if (str(f, "dateTrans").isBlank()) message = dateTransRequired;
        if (str(f, "msgTranCd").isBlank()) message = tranRequired;
        if (num(f, "msgLength") == 0) message = lengthRequired;
        for (String k : SAVE_DATES) if (!str(f, k).isBlank()) f.put(k, ymd(str(f, k)));
        if (!str(f, "dateTrans").isBlank() && !dateAccepted(str(f, "dateTrans"))) message = dateTransInvalid;
        if (!str(f, "dateCreate").isBlank() && !dateAccepted(str(f, "dateCreate"))) message = dateCreateInvalid;
        for (String k : List.of("dateIcsCreate", "dateMsgCmplt", "dateInsrvRev")) if (!str(f, k).isBlank() && !dateAccepted(str(f, k))) f.put(k, "");
        for (String k : List.of("timeCreate", "timeIcsCreate", "timeTrans")) if (!str(f, k).isBlank() && !timeChecked(str(f, k))) f.put(k, "");
        if (!message.isEmpty()) throw new InvalidRequestException(message);

        Map<String, Object> data = new LinkedHashMap<>();
        for (String k : TEXT_FIELDS) data.put(k, str(f, k));
        for (String k : NUMBER_FIELDS) data.put(k, num(f, k));
        Map<String, Object> cars = new LinkedHashMap<>();
        String all = pad(str(f, "iCars"), 50);
        for (int i = 1; i <= 10; i++) cars.put("oCar" + i, all.substring(5 * (i - 1), 5 * (i - 1) + 4).strip());   // I-FILn go blank
        data.put("iCars", cars);
        Programs.Outcome o = programs.attempt("VHCCS5C", Map.of("VHCCS5C_CRIT", Map.of("tsMsgRcvd", str(f, "tsMsgRcvd")), "VHCCS5C_DATA", data));
        return new Tables2Messages.Saved(o.ok(), o.ok() ? updated : updateFailed);
    }

    private boolean dateAccepted(String date) {
        return programs.attempt("VHCCS5N", Map.of("VHCCS5N_DATA", Map.of("dateMaintUpd", date))).ok();
    }

    private boolean timeChecked(String time) {
        return programs.attempt("VHCCS5M", Map.of("VHCCS5M_CRIT", Map.of("marisSubsys", checkSubsys), "VHCCS5M_DATA", Map.of("timeMaintUpd", time))).ok();
    }

    // ---- VHCCS18I ---------------------------------------------------------------------------------------------------

    /** VHCCS18-INIT (printers) and VHCCS18I_INIT (its lists) */
    public Tables2Messages.PrintOptions printOptions() {
        List<String> warnings = new ArrayList<>();
        List<Tables2Messages.Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Tables2Messages.Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        else warnings.add(p.error());
        List<String> yards = values("VHCCSHC", "yrd", warnings);
        List<String> tran = values("VHCCSHB", "tranCd", warnings);
        List<String> locs = values("VHCCSHD", "loc", warnings);
        List<String> scanners = new ArrayList<>();
        Programs.Outcome s = programs.attempt("VHCCSHE", Map.of());
        if (s.ok()) for (Map<String, Object> r : rows(s.result(), "VHCCSHE_DATA", "dataRow")) {
            Map<String, Object> g = group(r, "scanId");
            scanners.add((pad(text(g, "scannerId"), 1) + pad(text(g, "scFil"), 1) + text(g, "loc")).stripTrailing());
        } else warnings.add(s.error());
        List<String> processed = values("VHCCSHF", "msgProcessedCd", warnings);
        return new Tables2Messages.PrintOptions(printers, tran, yards, locs, scanners, processed, sorts, warnings);
    }

    /** VHCCS18IS-SUBMIT: the criteria go to VHCCSHA as typed (the processed code is not sent) */
    public void print(Tables2Messages.Print p) {
        String printer = nz(p.printer());
        if (printer.isEmpty()) throw new InvalidRequestException("Select a printer for the hard copy.");
        int sort = p.sort() == null ? 1 : p.sort();
        if (sort < 1 || sort > sorts.size()) throw new InvalidRequestException("The sort is 1 to " + sorts.size() + ".");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", printer);
        crit.put("fromDate", nz(p.fromDate()));
        crit.put("fromTime", nz(p.fromTime()));
        crit.put("toTime", nz(p.toTime()));
        crit.put("msgTranCd", nz(p.tranCode()));
        crit.put("conNo", nz(p.conNo()));
        crit.put("conCurrYrd", nz(p.yard()));
        crit.put("conCurrLoc", nz(p.location()));
        crit.put("scannerId", pad(p.scanner() == null ? "" : p.scanner(), 1).substring(0, 1).strip());
        crit.put("sortCode", sort);
        programs.run("VHCCSHA", Map.of("VHCCSHA_CRIT", crit));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** MRSCommonCode.XXDATES-CONVERT "YMD": MM-DD-YYYY to YYYY-MM-DD when the year is numeric, anything else as it is */
    static String ymd(String mdy) {
        String d = pad(mdy, 10);
        return d.substring(6, 10).chars().allMatch(Character::isDigit) ? d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5) : mdy;
    }

    /** VHCCS04I_LIST: the time's ':' separators become '.' (DB2 time stamp style) */
    static String dotted(String time) {
        char[] t = pad(time == null ? "" : time, 8).toCharArray();
        if (t[2] == ':') t[2] = '.';
        if (t[5] == ':') t[5] = '.';
        return new String(t).stripTrailing();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> group(Map<String, Object> row, String field) {
        return row.get(field) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static String pad(String s, int n) {
        String v = s == null ? "" : s;
        return v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length());
    }

    private static String str(Map<String, Object> f, String k) {
        Object v = f.get(k);
        return v == null ? "" : v.toString().strip();
    }

    private static long num(Map<String, Object> f, String k) {
        Object v = f.get(k);
        if (v instanceof Number n) return n.longValue();
        try { return v == null || v.toString().isBlank() ? 0 : Long.parseLong(v.toString().strip()); } catch (NumberFormatException e) { return 0; }
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
