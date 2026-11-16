package com.bart.maris.middleware.domain.tables3;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Component serial maintenance: the list (old VHCF49I) and one serial's detail and update (VHCF50I). Rules:
 * MRSTableMaintenanceApp3/RUIFunctions.egl VHCCS49I_INIT (VHCF49B), VHCCS49I_LIST (VHCF49E, VHCF49A), VHCF50IS_INIT
 * (VHCF50B..M), VHCF50IS-COMMENT (VHCF50I), VHCF50IS-DATE (dates shown MM-DD-YYYY), VHCF50IS_SAVE with
 * VHCF50IS-DATE-UPD and VHCF50IS_DT_CHK (VHCF50D per changed date, then VHCF50A), MRSCommonCode XXDATES-CONVERT.
 *
 * Old Delete is not offered: VHCCS49I_DELETE deletes by VHCF49I_PASSVALUE.STOCK-NO, which nothing ever fills, so it
 * always answered "Stock No invalid" and never called VHCF49D.
 */
public class Tables3SerialService {
    private static final String LIST = "VHCCS49I_LIST", SAVE = "VHCF50IS_SAVE";

    private final Programs programs;
    private final String selectRow, carMissing, tooMany, noData, stockRequired, saved, failed, subsystem, all;
    private final String carFrom, carTo, stockFrom, stockTo, serialFrom, serialTo;
    private final int limit;
    private final List<Map.Entry<String, String>> dateChecks;
    private final List<String> savedDates, shownDates;
    private static final List<String> LISTS = List.of("VHCF50B", "VHCF50C", "VHCF50E", "VHCF50F", "VHCF50G", "VHCF50H", "VHCF50J", "VHCF50K", "VHCF50L", "VHCF50M");

    public Tables3SerialService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        selectRow = Tables3Egl.messages(screens, "VHCCS49I_UPDATE", "MESG1").get(0);         // "PLEASE SELECT AN ROW"
        List<String> list = Tables3Egl.messages(screens, LIST, "MESG1");
        carMissing = list.get(0);
        tooMany = list.get(1);
        noData = list.get(2);
        limit = Integer.parseInt(tooMany.replaceAll("\\D+", ""));                 // "MORE THAN 90 ROWS ..."
        all = Tables3Egl.compared(screens, LIST, "STOCK-NO").get(0);
        carFrom = blank(Tables3Egl.assigned(screens, LIST, "CAR-NO-FR").get(0));
        carTo = blank(Tables3Egl.assigned(screens, LIST, "CAR-NO-TO").get(0));
        stockFrom = blank(Tables3Egl.assigned(screens, LIST, "FR-STOCK-NO").get(0));
        stockTo = blank(Tables3Egl.assigned(screens, LIST, "TO-STOCK-NO").get(0));
        serialFrom = blank(Tables3Egl.assigned(screens, LIST, "FR-SERIAL-NO").get(0));
        serialTo = blank(Tables3Egl.assigned(screens, LIST, "TO-SERIAL-NO").get(0));
        List<String> save = Tables3Egl.messages(screens, SAVE, "MESG1");
        stockRequired = save.get(0);
        saved = save.get(1);
        failed = save.get(2);
        subsystem = Tables3Egl.assigned(screens, "VHCF50IS_DT_CHK", "MARIS-SUBSYS").get(0);
        dateChecks = Tables3Egl.fieldChecks(screens, "VHCF50IS_DT_CHK", "MESG1").stream().map(e -> Map.entry(json(e.getKey()), e.getValue())).toList();
        savedDates = converted(screens, "VHCF50IS-DATE-UPD", "DATE-MM-DD-YYYY");
        Set<String> bound = boundDates(screens);
        shownDates = converted(screens, "VHCF50IS-DATE", "DATE-YYYY-MM-DD").stream().filter(bound::contains).toList();
    }

    /** VHCCS49I_INIT: the stock list (VHCF49B); its text is what the old drop-down shows */
    public Tables3Serials.Options options() {
        Programs.Outcome b = programs.attempt("VHCF49B", Map.of());
        List<Tables3Serials.Stock> out = new ArrayList<>();
        if (b.ok()) {
            for (Map<String, Object> r : rows(b.result(), "VHCF49B_DATA", "dataRow")) {
                Map<String, Object> d = sub(r, "stkDesc");
                String stock = text(d, "stockNo"), desc = text(d, "stockShortDesc");
                out.add(new Tables3Serials.Stock(stock, desc, (stock + " " + desc).strip()));
            }
        }
        return new Tables3Serials.Options(out, b.ok() ? null : b.error());
    }

    /** VHCCS49I_LIST: car (checked with VHCF49E), stock ("ALL" or blank: every), serial (blank: every) */
    public Tables3Serials.Rows list(String car, String stock, String serial) {
        String c = nz(car), s = nz(stock).toUpperCase(Locale.ROOT), n = nz(serial);
        int counter = 0;
        if (!c.isEmpty()) {
            ProgramResult e = programs.attempt("VHCF49E", Map.of("VHCF49E_CRIT", Map.of("carNo", c))).result();
            int urc = e.control() == null ? 0 : e.control().returnCode();
            if (urc != 0 && urc != 4) throw new InvalidRequestException(carMissing);
            counter = e.control() == null ? 0 : e.control().rowCount();
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("frStockNo", s.isEmpty() || s.equals(all) ? stockFrom : s);
        crit.put("toStockNo", s.isEmpty() || s.equals(all) ? stockTo : s);
        crit.put("frSerialNo", n.isEmpty() ? serialFrom : n);
        crit.put("toSerialNo", n.isEmpty() ? serialTo : n);
        crit.put("carNoFr", c.isEmpty() ? carFrom : c);
        crit.put("carNoTo", c.isEmpty() ? carTo : c);
        crit.put("counter", counter);
        ProgramResult r = programs.attempt("VHCF49A", Map.of("VHCF49A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc != 0 && urc != 4) return new Tables3Serials.Rows(List.of(), noData);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHCF49A_DATA", "dataRow")) out.add(clean(row));
        int count = r.control() == null ? out.size() : r.control().rowCount();
        return new Tables3Serials.Rows(out, count < limit ? null : tooMany);
    }

    /** VHCF50IS_INIT + VHCF50IS_SELECT: the lists, the row as the old form shows it (dates MM-DD-YYYY), the comments */
    public Tables3Serials.Form form(Map<String, Object> row) {
        if (row == null || row.isEmpty()) throw new InvalidRequestException(selectRow);
        Map<String, List<Tables3Serials.Choice>> lists = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String p : LISTS) {
            Programs.Outcome o = programs.attempt(p, Map.of());
            if (!o.ok()) { warnings.add(o.error()); lists.put(p, List.of()); continue; }
            List<Tables3Serials.Choice> items = new ArrayList<>();
            for (Map<String, Object> r : rows(o.result(), p + "_DATA", "dataRow")) items.add(choice(r));
            lists.put(p, items);
        }
        Map<String, Object> form = new LinkedHashMap<>(row);
        for (String f : shownDates) form.put(f, mdy(str(row.get(f))));
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("stockNo", str(row.get("stockNo")));
        crit.put("cmpntSerialNo", str(row.get("cmpntSerialNo")));
        Programs.Outcome c = programs.attempt("VHCF50I", Map.of("VHCF50I_CRIT", crit));
        String comments = c.ok() && c.result().record("VHCF50I_DATA") != null ? text(c.result().record("VHCF50I_DATA"), "comments") : "";
        return new Tables3Serials.Form(form, comments, lists, warnings);
    }

    /** VHCF50IS_SAVE: stock required; dates to YYYY-MM-DD; each changed date checked by VHCF50D; then VHCF50A */
    public Tables3Serials.Result update(Tables3Serials.Change change) {
        Map<String, Object> original = change.original() == null ? Map.of() : change.original();
        Map<String, Object> form = new LinkedHashMap<>(change.form() == null ? Map.of() : change.form());
        String message = "";
        if (nz(str(form.get("stockNo"))).isEmpty()) message = stockRequired;
        for (String f : savedDates) form.put(f, ymd(str(form.get(f))));                // VHCF50IS-DATE-UPD
        for (Map.Entry<String, String> check : dateChecks) {                           // VHCF50IS_DT_CHK
            String d = rtrim(str(form.get(check.getKey())));
            if (d.isBlank() || d.equals(rtrim(str(original.get(check.getKey()))))) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dateMaintUpd", d);
            data.put("marisSubsys", subsystem);
            if (!programs.attempt("VHCF50D", Map.of("VHCF50D_DATA", data)).ok()) message = check.getValue();
        }
        if (!message.isEmpty()) return new Tables3Serials.Result(false, message, null);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("stockNo", str(form.get("stockNo")));
        crit.put("cmpntSerialNo", str(form.get("cmpntSerialNo")));
        Map<String, Object> data = new LinkedHashMap<>();
        for (String k : original.keySet()) if (form.containsKey(k)) data.put(k, form.get(k));   // the old record's fields, by name
        data.put("comments", change.comments() == null ? "" : change.comments());
        Programs.Outcome a = programs.attempt("VHCF50A", Map.of("VHCF50A_CRIT", crit, "VHCF50A_DATA", data));
        return a.ok() ? new Tables3Serials.Result(true, saved, null) : new Tables3Serials.Result(false, failed, a.error());
    }

    // ---- the old date conversion (MRSCommonCode XXDATES-CONVERT) -------------------------------------------------

    /** "YMD": MM-DD-YYYY to YYYY-MM-DD when the year is numeric, otherwise the text as it is */
    static String ymd(String mmddyyyy) {
        String s = pad(mmddyyyy);
        String year = s.substring(6, 10);
        return digits(year) ? year + "-" + s.substring(0, 2) + "-" + s.substring(3, 5) : rtrim(s);
    }

    /** "MDY": YYYY-MM-DD to MM-DD-YYYY when the year is numeric, otherwise the text as it is */
    static String mdy(String yyyymmdd) {
        String s = pad(yyyymmdd);
        String year = s.substring(0, 4);
        return digits(year) ? s.substring(5, 7) + "-" + s.substring(8, 10) + "-" + year : rtrim(s);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** the record fields a statement group converts, in order: "XXDATEW.<from> = REC.<FIELD>" */
    private static List<String> converted(ScreenBundle screens, String function, String into) {
        Ast.Function f = screens.function(function, Tables3Egl.PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        Set<String> out = new LinkedHashSet<>();
        for (Ast.Stmt s : f.body()) {
            if (s instanceof Ast.Assign a && last(a.target()).equals(into) && a.value() instanceof Ast.Name n) out.add(json(last(n)));
        }
        if (out.isEmpty()) throw new IllegalStateException(function + " converts no dates");
        return List.copyOf(out);
    }

    /** the VHCF50IW-DATE fields the old VHCF50I shows in a widget (DATE-TRK-REMOVE is shown as stored) */
    private static Set<String> boundDates(ScreenBundle screens) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode c : screens.screen("VHCF50I").path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(0).path("id").asText().equals("VHCF50IW-DATE") && a.path(2).path("id").asText().startsWith("screen.")) {
                out.add(json(a.path(1).asText().replace(" data", "")));
            }
        }
        return out;
    }

    private static String last(Ast.Name n) {
        return n.segments().get(n.segments().size() - 1).name();
    }

    /** "DATE-HUB-COUP-REPL" -> "dateHubCoupRepl" */
    static String json(String egl) {
        StringBuilder b = new StringBuilder();
        for (String part : egl.toLowerCase(Locale.ROOT).split("-")) b.append(b.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        return b.toString();
    }

    private static Tables3Serials.Choice choice(Map<String, Object> r) {
        List<String> values = new ArrayList<>();
        flat(r, values);
        String code = values.isEmpty() ? "" : values.get(0).strip();
        return new Tables3Serials.Choice(code, String.join(" ", values.stream().map(String::strip).filter(x -> !x.isEmpty()).toList()));
    }

    @SuppressWarnings("unchecked")
    private static void flat(Map<String, Object> r, List<String> out) {
        for (Object v : r.values()) {
            if (v instanceof Map<?, ?> m) flat((Map<String, Object>) m, out);
            else if (v != null) out.add(v.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> row, String group) {
        return row.get(group) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> clean(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            out.put(e.getKey(), v instanceof String s ? s.strip() : v instanceof Map<?, ?> m ? clean((Map<String, Object>) m) : v);
        }
        return out;
    }

    /** an all-blank old text is sent as empty (a CHAR field holds blanks either way) */
    private static String blank(String s) {
        return s.isBlank() ? "" : s;
    }

    private static boolean digits(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private static String pad(String s) {
        String v = s == null ? "" : s;
        return v.length() >= 10 ? v.substring(0, 10) : v + " ".repeat(10 - v.length());
    }

    private static String rtrim(String s) {
        return s == null ? "" : s.stripTrailing();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
