package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the vehicle incident screens VHINC08I, VHINC09I and VHINC10I share: their message line (the old *-MOVE-MSG
 * loop), texts built from fixed-width message fields, an incident's narratives (VHINC1D) and the EGL lookups behind them.
 */
public final class IncidentsVehicleSupport {
    static final String PKG = "MRSVehicleIncident";

    private final ScreenBundle bundle;
    private final EglTexts texts;

    IncidentsVehicleSupport(ScreenBundle bundle) {
        this.bundle = bundle;
        this.texts = new EglTexts(bundle);
    }

    EglTexts texts() {
        return texts;
    }

    /** the length of a (possibly nested) field of a working record, e.g. VHINC08IW-TRIGGER SUCCESS-MESSAGE-3 */
    int length(String record, String field) {
        RecordLayout r = bundle.recordType(record, List.of(PKG));
        if (r == null) throw new IllegalStateException("record " + record + " not found");
        FieldLayout f = find(r.fields(), field);
        if (f == null) throw new IllegalStateException(record + " has no field " + field);
        return f.length();
    }

    /** a group field of a program record (decoded as its parts) as the one text the old screen moves, e.g. INCD-NO */
    String group(String record, String json, Object value) {
        if (!(value instanceof Map<?, ?> m)) return value == null ? "" : value.toString().strip();
        RecordLayout r = bundle.recordType(record, List.of(PKG));
        FieldLayout f = r == null ? null : findJson(r.fields(), json);
        if (f == null) throw new IllegalStateException(record + " has no field " + json);
        return parts(f, m).strip();
    }

    /** a group's bytes as text: its parts in order, arrays element by element, each part padded to its length */
    private static String parts(FieldLayout f, Map<?, ?> m) {
        StringBuilder b = new StringBuilder();
        for (FieldLayout c : f.children()) {
            Object v = m.get(c.json());
            List<?> elements = v instanceof List<?> l ? l : java.util.Collections.singletonList(v);
            for (int i = 0; i < c.occurs(); i++) {
                Object e = i < elements.size() ? elements.get(i) : null;
                String s = e instanceof Number n ? String.format("%0" + c.length() + "d", n.longValue()) : e instanceof Map<?, ?> em ? parts(c, em)
                        : e == null ? "" : e.toString();
                int len = c.isGroup() ? c.bytes() : c.length();
                b.append(s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length()));
            }
        }
        return b.toString();
    }

    private static FieldLayout findJson(List<FieldLayout> fields, String json) {
        for (FieldLayout f : fields) {
            if (f.json().equals(json)) return f;
            FieldLayout c = findJson(f.children(), json);
            if (c != null) return c;
        }
        return null;
    }

    private static FieldLayout find(List<FieldLayout> fields, String name) {
        for (FieldLayout f : fields) {
            if (f.name().equalsIgnoreCase(name)) return f;
            FieldLayout c = find(f.children(), name);
            if (c != null) return c;
        }
        return null;
    }

    /** texts moved into consecutive fixed-width fields of a record, read as one text (e.g. SUCCESS-MESSAGE-1..5) */
    String fixed(String record, List<Map.Entry<String, String>> parts) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> p : parts) {
            int n = length(record, p.getKey());
            String v = p.getValue() == null ? "" : p.getValue();
            b.append(v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length()));
        }
        return b.toString().stripTrailing();
    }

    /** the largest number a statement group compares a field with, e.g. ERROR-INDEX <= 40 in *-MOVE-MSG */
    int limit(String function, String field) {
        return texts.compared(function, PKG, field).stream().mapToInt(Integer::parseInt).max().orElseThrow();
    }

    /**
     * the old message line: each message up to its first period (which becomes ", "), at most {@code width} characters
     * of it, then the last separator blanked, exactly like *-MOVE-MSG (FINAL-MSG, TEMP-MSG)
     */
    static String join(List<String> messages, int width, int tempLength, int finalLength, int maxMessages) {
        char[] out = new char[finalLength];
        Arrays.fill(out, ' ');
        int fin = 0;
        for (int m = 0; m < Math.min(maxMessages, messages.size()); m++) {
            String msg = messages.get(m);
            if (msg.isBlank()) break;
            String temp = msg.length() >= tempLength ? msg.substring(0, tempLength) : msg + " ".repeat(tempLength - msg.length());
            for (int i = 1; i <= width; i++) {
                char c = i <= temp.length() ? temp.charAt(i - 1) : ' ';
                if (c != '.') {
                    fin++;
                    if (fin <= finalLength) out[fin - 1] = c;
                } else {
                    fin++;
                    if (fin <= finalLength) out[fin - 1] = ',';
                    fin++;
                    if (fin <= finalLength) out[fin - 1] = ' ';
                    break;
                }
            }
        }
        if (messages.isEmpty()) return "";
        fin--;
        if (fin >= 1 && fin <= finalLength) out[fin - 1] = ' ';
        return new String(out).strip();
    }

    /** the first text tested with "in" against an array in a statement group, e.g. "pbtRevenueFixIncident" in PART-NAME */
    String inText(String function) {
        Ast.Function f = bundle.function(function, PKG);
        if (f == null) throw new IllegalStateException("function " + function + " not found");
        String s = inText(f.body());
        if (s == null) throw new IllegalStateException(function + " tests no text with in");
        return s;
    }

    private static String inText(List<Ast.Stmt> stmts) {
        for (Ast.Stmt s : stmts) {
            String r = switch (s) {
                case Ast.If i -> i.cond() instanceof Ast.InTest t && t.value() instanceof Ast.Str str ? str.value().strip()
                        : first(inText(i.then()), inText(i.otherwise()));
                case Ast.Try t -> inText(t.body());
                default -> null;
            };
            if (r != null) return r;
        }
        return null;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    /** a text per tested value: for each "if (FIELD == value)" of a statement group (nested else-ifs too), what it assigns */
    Map<String, String> byValue(String function, String testField, String assignedField) {
        List<String> values = texts.compared(function, PKG, testField);
        List<String> assigned = texts.assignedUnder(function, PKG, assignedField, testField);
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < values.size() && i < assigned.size(); i++) out.put(values.get(i), assigned.get(i));
        return out;
    }

    public record Narrative(int number, String userGroup, String date, String time, String text) {}

    /** an incident's narratives as VHINC1D answers them (as many as its row count), with the record it read */
    static List<Narrative> narratives(ProgramResult r) {
        List<Narrative> out = new ArrayList<>();
        Map<String, Object> data = r.record("VHINC1D_DATA");
        int n = r.control() == null ? 0 : r.control().rowCount();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = data != null && data.get("dataNarrRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            out.add(new Narrative((int) number(row, "narrNo"), text(row, "userGrp"), text(row, "dateNarr"), colons(time(row.get("timeNarr"))), raw(row, "narr")));
        }
        return out;
    }

    /** a text field as the program gave it, trailing blanks dropped (narratives keep their leading blanks and line layout) */
    static String raw(Map<String, Object> rec, String field) {
        Object v = rec == null ? null : rec.get(field);
        return v == null ? "" : v.toString().stripTrailing();
    }

    /** a time record (HR, FILLER, MIN, FILLER, SEC) or text as the screen shows it */
    static String time(Object v) {
        if (v instanceof Map<?, ?> m) {
            List<String> parts = new ArrayList<>();
            for (Object x : m.values()) parts.add(x instanceof Number n ? String.format("%02d", n.longValue()) : String.valueOf(x));
            return String.join("", parts).strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    public record Update(String date, String time, String terminal) {}

    /** an incident as VHINC09I / VHINC10I show it after List (*-INCD-DET), with the cars of its consist (VHINC9A) */
    public record Details(String incident, String car, String consist, String type, String status, String symptom, String duration, String date, String time,
                          String speed, String direction, String location, String deferredPriority, long cumulativeHours, long cumulativeMinutes,
                          Update lastUpdate, Update maintenanceUpdate, String carsInConsist, List<Narrative> narratives) {}

    /**
     * the details of VHINC1D's answer; {@code workRecord} is the screen's working record (its DASH-NO width), and
     * {@code inService} whether VHINC9A gets the incident's in-service date and time (VHINC09IS-H-INCDS does not pass them)
     */
    Details details(Programs programs, String incident, ProgramResult r, String workRecord, boolean inService) {
        Map<String, Object> d = r.record("VHINC1D_DATA");
        String con = text(d, "conNo");
        long dash = number(d, "dashNo");
        int dashWidth = length(workRecord, "DASH-NO");
        String consist = (String.format("%-3s", con) + (dash != 0 ? "-" : " ") + String.format("%0" + dashWidth + "d", dash)).strip();
        String cars = "";
        if (!con.isEmpty() && dash > 0) {
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("conNo", con);
            crit.put("dashNo", dash);
            if (inService) {
                crit.put("dateInsrvRev", text(d, "dateInsrvRev"));
                crit.put("dateInsrv", text(d, "dateInsrv"));
                crit.put("timeInsrv", text(d, "timeInsrv"));
            }
            Programs.Outcome o = programs.attempt("VHINC9A", Map.of("VHINC9A_CRIT", crit));
            if (o.ok() || o.result().control().returnCode() == 100) {
                Map<String, Object> a = o.result().record("VHINC9A_DATA");
                cars = a == null ? "" : group("VHINC9A_DATA", "dispDataRow", a.get("dispDataRow"));
            }
        }
        String time = group("VHINC1D_DATA", "timeIncdOccr", d.get("timeIncdOccr"));
        Object hm = d.get("cumHrsMin");
        long hrs = hm instanceof Map<?, ?> m && m.get("cumHrsChar") instanceof Map<?, ?> h ? number(cast(h), "cumHrs") : number(d, "cumHrs");
        long min = hm instanceof Map<?, ?> m2 && m2.get("cumMinChar") instanceof Map<?, ?> n ? number(cast(n), "cumMin") : number(d, "cumMin");
        return new Details(incident, text(d, "carNo"), consist, text(d, "incdType"), text(d, "vehIncdStat"), text(d, "sympCd"), text(d, "discvDur"),
                group("VHINC1D_DATA", "dateIncdOccr", d.get("dateIncdOccr")), time.length() >= 5 ? time.substring(0, 2) + ":" + time.substring(3, 5) : time,
                text(d, "speed"), text(d, "conDirection"), text(d, "incdLoc"), text(d, "deferredPriority"), hrs, min,
                new Update(text(d, "dateLastUpd"), colons(text(d, "timeLastUpd")), text(d, "termLastUpd")),
                new Update(text(d, "dateMaintUpd"), colons(text(d, "timeMaintUpd")), text(d, "termMaintUpd")), cars, narratives(r));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /** a time as the screen shows it once it sets its separators (TIME-LAST-FILLER-1/2 = ":"), "" when blank */
    static String colons(String t) {
        if (t == null || t.isBlank()) return "";
        char[] c = String.format("%-8s", t).toCharArray();
        c[2] = ':';
        c[5] = ':';
        return new String(c).strip();
    }

    /** VHINC1D for an incident (RET-CHECK "NARR" or "BOTH") */
    static Programs.Outcome readIncident(Programs programs, String incident, String retCheck) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", incident);
        crit.put("retCheck", retCheck);
        return programs.attempt("VHINC1D", Map.of("VHINC1D_CRIT", crit));
    }
}
