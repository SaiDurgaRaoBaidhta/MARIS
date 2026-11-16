package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.legacy.ProgramResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What the consist status (VHVM10I) and consist history (VHVM19I) screens share: their table and their date conversions. */
public final class VehicleMaintConsistTable {
    static final String TABLE = "VAGen_Container_Details_DATA_ROW1";

    private VehicleMaintConsistTable() {}

    /** a table column: the old heading and the row field (JSON name) it shows */
    public record Column(String heading, String field) {}

    /** the screen's table columns in their order */
    static List<Column> columns(EglTexts t, String screen) {
        List<Column> out = new ArrayList<>();
        for (EglTexts.Column c : t.tableColumns(screen, TABLE)) out.add(new Column(c.heading(), json(c.field())));
        return out;
    }

    /** CON-NO -> conNo, the backend records' field naming */
    static String json(String egl) {
        String[] parts = egl.toLowerCase(Locale.ROOT).split("-");
        StringBuilder b = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) if (!parts[i].isEmpty()) b.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return b.toString();
    }

    /** the rows the backend returned (as many as its row count), texts trimmed */
    static List<Map<String, Object>> rows(ProgramResult r, String record) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : com.bart.maris.middleware.domain.common.Programs.rows(r, record, "dataRow")) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.forEach((k, v) -> m.put(k, v instanceof String s ? s.strip() : v));
            out.add(m);
        }
        return out;
    }

    /** whether the record's n-th row (1-based, of all its occurrences) has a consist: the old screens' "too many rows" test */
    @SuppressWarnings("unchecked")
    static boolean full(ProgramResult r, String record, int n) {
        Map<String, Object> rec = r.record(record);
        Object v = rec == null ? null : rec.get("dataRow");
        if (!(v instanceof List<?> l) || l.size() < n) return false;
        Object con = ((Map<String, Object>) l.get(n - 1)).get("conNo");
        return con != null && !con.toString().isBlank();
    }

    /** MRSCommonCode XXDATES-CONVERT "YMD": MM-DD-YYYY -> YYYY-MM-DD when its year is numeric, else the text unchanged */
    static String ymd(String mmddyyyy) {
        String s = pad(mmddyyyy);
        String year = s.substring(6, 10);
        if (!numeric(year)) return s.stripTrailing();
        return year + "-" + s.substring(0, 2) + "-" + s.substring(3, 5);
    }

    /** XXDATES-CONVERT "MDY": YYYY-MM-DD -> MM-DD-YYYY when its year is numeric, else the text unchanged */
    static String mdy(String yyyymmdd) {
        String s = pad(yyyymmdd);
        String year = s.substring(0, 4);
        if (!numeric(year)) return s.stripTrailing();
        return s.substring(5, 7) + "-" + s.substring(8, 10) + "-" + year;
    }

    private static String pad(String v) {
        String s = v == null ? "" : v;
        return s.length() >= 10 ? s.substring(0, 10) : String.format("%-10s", s);
    }

    private static boolean numeric(String s) {
        return s.chars().allMatch(Character::isDigit);
    }
}
