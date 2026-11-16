package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An incident's status history (old VHVM06I): the entries (VHVM6A1), add one (VHVM6A2), change one (VHVM6A3) and
 * delete one (VHVM6A4), with VHVM06IS-VDATE's date and time checks and VHVM06IS-MOD-DATA's required fields. The
 * choices come from the sign-on data (VH01A01: yards and incident statuses), VHYT15A (move reasons) and VHVM6A6 (a
 * yard's sub-locations). Messages are the screen's own message boxes.
 */
public final class VehicleMaintStatusHistoryService {
    private static final String SCREEN = "VHVM06I", PKG = VehicleMaintCarService.PKG, MOD = "VHVM06IS-MOD-DATA";

    private final Programs programs;
    private final Clock clock;
    private final String terminal, function, addAction, duplicateCode;
    private final String badDate, badTime, blankSubLoc, blankPrevStat, dataError, missing, duplicate, added, updated, deleted;

    public VehicleMaintStatusHistoryService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.clock = clock;
        this.terminal = terminal;
        function = t.assigned(MOD, PKG, "MARIS-FUNC", null);
        addAction = t.assigned(MOD, PKG, "STAT-CHG-ACTN", null);
        duplicateCode = t.compared(MOD, PKG, "UMSGINS").get(0);
        badDate = t.triggeredPrompt(SCREEN, "BAD-DATE-MSG");
        badTime = t.triggeredPrompt(SCREEN, "BAD-TIME-MSG");
        blankSubLoc = t.triggeredPrompt(SCREEN, "BLANK-SUB-LOC");
        blankPrevStat = t.triggeredPrompt(SCREEN, "BLANK-PREV-STAT");
        dataError = t.triggeredPrompt(SCREEN, "DATA-ERROR");
        missing = t.triggeredPrompt(SCREEN, "MISSING-VALUES");
        duplicate = t.triggeredPrompt(SCREEN, "DUPLICATE-KEY");
        added = t.triggeredPrompt(SCREEN, "ADDED");
        updated = t.triggeredPrompt(SCREEN, "UPDATED");
        deleted = t.triggeredPrompt(SCREEN, "DELETED");
    }

    public record Options(List<String> yards, List<String> statuses, List<String> moveReasons) {}

    /** the screen's lists: VH01A01 (what the menu passed down: yards, incident statuses) and VHYT15A (move reasons) */
    public Options options(String userGroup) {
        List<String> yards = new ArrayList<>(), statuses = new ArrayList<>(), reasons = new ArrayList<>();
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("userGroup", userGroup == null ? "" : userGroup);
        in.put("termId", terminal);
        Programs.Outcome s = programs.attempt("VH01A01", Map.of("VH01A01_DATA", in));
        Map<String, Object> d = s.result().record("VH01A01_DATA");
        if (s.ok() && d != null) {
            yards.addAll(column(d, "yardRow", "yrd", (int) number(d, "numYrd")));
            statuses.addAll(column(d, "vehIncdStatRow", "vehIncdStat", (int) number(d, "numVehIncdStat")));
        }
        Programs.Outcome r = programs.attempt("VHYT15A", Map.of());
        if (r.ok()) for (var row : rows(r.result(), "VHYT15A_DATA", "dataRow")) reasons.add(text(row, "moveReasonCd"));
        return new Options(yards, statuses, reasons);
    }

    @SuppressWarnings("unchecked")
    private static List<String> column(Map<String, Object> d, String array, String field, int n) {
        List<String> out = new ArrayList<>();
        if (d.get(array) instanceof List<?> l) {
            for (int i = 0; i < Math.min(n, l.size()); i++) {
                String v = text((Map<String, Object>) l.get(i), field);
                if (!v.isEmpty()) out.add(v);
            }
        }
        return out;
    }

    /** VHVM06IS-GETSUBLOC: a yard's sub-locations (VHVM6A6) */
    public List<String> subLocations(String yard) {
        String y = yard == null ? "" : yard.strip().toUpperCase(java.util.Locale.ROOT);
        if (y.isEmpty()) return List.of();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yardLocInd", "Y");
        crit.put("yrdTrkgYrd", y);
        Programs.Outcome o = programs.attempt("VHVM6A6", Map.of("VHVM6A6_CRIT", crit));
        List<String> out = new ArrayList<>();
        if (o.ok()) for (var row : rows(o.result(), "VHVM6A6_DATA", "dataRow")) out.add(text(row, "yrdSubLoc"));
        return out;
    }

    /** one status change of an incident (VHVM6A1 row; the fields the screen edits) */
    public record Entry(String incident, String timestamp, String date, String time, String previousStatus, String newStatus, String yard,
                        String subLocation, String pseudoConsist, String moveReason, String homeYard, String function, String action,
                        String userGroup, String dateLastUpdated, String terminalLastUpdated, String timeLastUpdated) {}

    /** VHVM06IS-RET-DATA */
    public List<Entry> entries(String incident) {
        String i = incident(incident);
        ProgramResult r = programs.run("VHVM6A1", Map.of("VHVM6A1_CRIT", Map.of("incdNo", i)));
        List<Entry> out = new ArrayList<>();
        for (var row : rows(r, "VHVM6A1_DATA", "dataRow")) {
            out.add(new Entry(text(row, "incdNo"), text(row, "tsStatChg"), text(row, "dateStatChg"), text(row, "timeStatChg"), text(row, "incdStatPrev"),
                    text(row, "incdStatNew"), text(row, "yrdTrkgYrd"), text(row, "yrdSubLoc"), text(row, "pseudoConNo"), text(row, "moveReasonCd"),
                    text(row, "homeYrd"), text(row, "marisFunc"), text(row, "statChgActn"), text(row, "userGrp"), text(row, "dateLastUpd"),
                    text(row, "termLastUpd"), text(row, "timeLastUpd")));
        }
        return out;
    }

    /** what the user sets on the screen; the date is yyyy-MM-dd, the time HH:mm:ss */
    public record Change(String timestamp, String date, String time, String previousStatus, String newStatus, String yard, String subLocation,
                         String pseudoConsist, String moveReason, String homeYard) {}

    public record Result(String message, List<Entry> entries) {}

    /** pbt_Add: VHVM06IS-VDATE, then VHVM06IS-MOD-DATA with VHVM6A2 */
    public Result add(String incident, Change c, String userGroup) {
        String i = incident(incident);
        check(c, 0);
        Map<String, Object> data = data(i, c, userGroup);
        required(data);
        data.put("statChgActn", addAction);
        data.put("termLastUpd", addAction);
        Programs.Outcome o = programs.attempt("VHVM6A2", Map.of("VHVM6A3_DATA", data));
        if (!o.ok()) {
            var ctl = o.result().control();
            boolean dup = ctl != null && !ctl.messageInserts().isEmpty() && duplicateCode.equals(ctl.messageInserts().get(0).strip());
            throw failed("VHVM6A2", o, (dup ? duplicate : o.error()) + "\n" + missing);
        }
        return new Result(added, entries(i));
    }

    /** pbtUpdate on a selected entry: VHVM06IS-VDATE, then VHVM06IS-MOD-DATA with VHVM6A3 (keyed by incident and timestamp) */
    public Result update(String incident, Change c, String userGroup) {
        String i = incident(incident);
        String ts = c.timestamp() == null ? "" : c.timestamp().strip();
        int index = indexOf(entries(i), ts);
        if (index == 0) throw new InvalidRequestException(missing);
        check(c, index);
        Map<String, Object> data = data(i, c, userGroup);
        required(data);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("incdNo", i);
        crit.put("tsStatChg", ts);
        Programs.Outcome o = programs.attempt("VHVM6A3", Map.of("VHVM6A3_CRIT", crit, "VHVM6A3_DATA", data));
        if (!o.ok()) throw failed("VHVM6A3", o, o.error() + "\n" + missing);
        return new Result(updated, entries(i));
    }

    /** Delete on a selected entry: VHVM06IS-MOD-DATA with VHVM6A4, the entry as listed */
    public Result delete(String incident, String timestamp, String userGroup) {
        String i = incident(incident);
        String ts = timestamp == null ? "" : timestamp.strip();
        Entry e = entries(i).stream().filter(x -> x.timestamp().equals(ts)).findFirst().orElseThrow(() -> new InvalidRequestException(missing));
        Change c = new Change(e.timestamp(), e.date(), e.time(), e.previousStatus(), e.newStatus(), e.yard(), e.subLocation(), e.pseudoConsist(),
                e.moveReason(), e.homeYard());
        Programs.Outcome o = programs.attempt("VHVM6A4", Map.of("VHVM6A3_DATA", data(i, c, userGroup)));
        if (!o.ok()) throw failed("VHVM6A4", o, o.error() + "\n" + missing);
        return new Result(deleted, entries(i));
    }

    private static int indexOf(List<Entry> list, String ts) {
        for (int k = 0; k < list.size(); k++) if (list.get(k).timestamp().equals(ts)) return k + 1;
        return 0;
    }

    /** VHVM06IS-MOD-DATA: VHVM6A3_DATA from the selected row */
    private Map<String, Object> data(String incident, Change c, String userGroup) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("dateStatChg", s(c.date()));
        d.put("tsStatChg", s(c.timestamp()));
        d.put("homeYrd", up(c.homeYard()));
        d.put("incdNo", incident);
        d.put("incdStatNew", up(c.newStatus()));
        d.put("incdStatPrev", up(c.previousStatus()));
        d.put("marisFunc", function);
        d.put("moveReasonCd", up(c.moveReason()));
        d.put("termMaintUpd", terminal);
        d.put("timeStatChg", s(c.time()));
        d.put("userGrp", userGroup == null ? "" : userGroup);
        d.put("yrdSubLoc", up(c.subLocation()));
        d.put("yrdTrkgYrd", up(c.yard()));
        d.put("pseudoConNo", up(c.pseudoConsist()));
        return d;
    }

    private void required(Map<String, Object> d) {
        for (String f : List.of("incdNo", "dateStatChg", "timeStatChg", "incdStatNew", "homeYrd")) {
            if (d.get(f).toString().isBlank()) throw new InvalidRequestException(dataError + "\n" + missing);
        }
    }

    /** VHVM06IS-VDATE: the date (not in the future, years 1999-2100), the time (not 00:00, not later today), sub-location, previous status */
    private void check(Change c, int selection) {
        List<String> out = new ArrayList<>();
        LocalDate date = null;
        try { date = LocalDate.parse(s(c.date())); } catch (RuntimeException e) { /* bad date */ }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean badD = date == null || date.getYear() < 1999 || date.getYear() > 2100 || date.isAfter(now.toLocalDate());
        String time = s(c.time());
        int hr = part(time, 0), min = part(time, 3);
        boolean badT = !time.matches("\\d\\d:\\d\\d(:\\d\\d)?") || hr > 24 || min > 59 || (hr == 0 && min == 0)
                || (date != null && date.equals(now.toLocalDate()) && pad(time).compareTo(now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))) > 0);
        if (badD) out.add(badDate);
        if (badT) out.add(badTime);
        if (!s(c.yard()).isEmpty() && s(c.subLocation()).isEmpty()) out.add(blankSubLoc);
        if (s(c.previousStatus()).isEmpty() && selection > 1) out.add(blankPrevStat);
        if (!out.isEmpty()) throw new InvalidRequestException(String.join("\n", out));
    }

    private static String pad(String t) {
        return t.length() == 5 ? t + ":00" : t;
    }

    private static int part(String t, int at) {
        try { return Integer.parseInt(t.substring(at, at + 2)); } catch (RuntimeException e) { return 0; }
    }

    private static String incident(String incident) {
        String i = incident == null ? "" : incident.strip();
        if (i.isEmpty() || i.length() > 8) throw new InvalidRequestException("An incident number is up to 8 characters.");
        return i;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }

    private static String up(String v) {
        return s(v).toUpperCase(java.util.Locale.ROOT);
    }

    private static LegacyBusinessException failed(String program, Programs.Outcome o, String text) {
        var c = o.result().control();
        return new LegacyBusinessException(program, c, new MessageResolver.Message(c == null ? "" : c.messageCode(), "E", text));
    }
}
