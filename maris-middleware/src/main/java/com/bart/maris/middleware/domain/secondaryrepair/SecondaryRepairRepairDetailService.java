package com.bart.maris.middleware.domain.secondaryrepair;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repair detail of a sub job that is out the door (old VHSR09I, from the menu): its repairs (VHSR09B, VHSR09A), a kit's
 * check (VHSR09C), a stock's short description (VHSR09H), and adding repairs, typed and / or from a kit (VHSR09F,
 * VHSR09G checks, VHSR09E kit parts, VHSR09D writes). "No Repair" adds the old screen's no-repair kit. After adding, the
 * old screen went on to the labor detail (VHSR03I). Rules from VHSR09IS-* in MRSSecondaryRepair.
 */
public class SecondaryRepairRepairDetailService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final String jobDocRequired, jobRequired, docRequired, subJob, ofJob, isClosed, notOut, unknown, closedStatus;
    private final List<String> outTheDoor;
    private final String line, dataRequired, stockRequired, stockNotNumeric, badPart, badFault, badQuantity;
    private final String kitUnknown, kitEmpty, added, fromKit, fromKitEnd, fromKitAnd, noRepairKit;
    private final int width, maxErrors;

    public SecondaryRepairRepairDetailService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        String rd = "VHSR09IS-RET-DATA", add = "VHSR09IS-ADDREPR", st = "VHSR09IS-DB-STORE";
        jobDocRequired = t.assigned(rd, PKG, "FINAL-MESSAGE", "Job & Doc");
        jobRequired = t.assignedAll(rd, PKG, "FINAL-MESSAGE").stream().filter(x -> x.startsWith("Job No")).findFirst().orElseThrow();
        docRequired = t.assignedAll(rd, PKG, "FINAL-MESSAGE").stream().filter(x -> x.startsWith("Doc No")).findFirst().orElseThrow();
        subJob = t.assignedRaw(rd, PKG, "TEMP-FINAL-MSG-1").get(0);
        ofJob = t.assignedRaw(rd, PKG, "TEMP-FINAL-MSG-3").get(0);
        isClosed = t.assignedRaw(rd, PKG, "TEMP-FINAL-MSG-5").get(0);
        notOut = t.assigned(rd, PKG, "FINAL-MESSAGE", "NOT OUT");
        unknown = t.assigned(rd, PKG, "FINAL-MESSAGE", "does not exist");
        List<String> stats = t.compared(rd, PKG, "SUB-JOB-STAT");
        closedStatus = stats.get(0);
        outTheDoor = stats.stream().filter(x -> !x.equals(closedStatus)).toList();
        line = t.assignedRaw(add, PKG, "ERROR-MSG-1").get(0);
        List<String> m3 = t.assignedRaw(add, PKG, "ERROR-MSG-3");
        dataRequired = pick(m3, "Data required");
        stockRequired = pick(m3, "Stock No required");
        stockNotNumeric = pick(m3, "not numeric");
        badPart = pick(m3, "Part Cd");
        badFault = pick(m3, "Fault Cd");
        badQuantity = pick(m3, "Quantity");
        kitUnknown = t.assigned("VHSR09IS-KIT", PKG, "FINAL-MESSAGE", "not defined");
        kitEmpty = t.assigned("VHSR09IS-KIT", PKG, "FINAL-MESSAGE", "no details");
        added = t.assigned(st, PKG, "FINAL-MESSAGE", "Repair Details added");
        fromKit = t.assignedRaw(st, PKG, "FINAL-MESSAGE-1").get(0);
        List<String> m3s = t.assignedRaw(st, PKG, "FINAL-MESSAGE-3");
        fromKitEnd = m3s.get(0);
        fromKitAnd = m3s.get(1);
        noRepairKit = t.assigned("VHSR09IS-NO-REPAIR", PKG, "KIT-NO", null);
        width = SecondaryRepairForms.limit(t, "VHSR09IS-MOVE-MSG", "ERROR-INDEX");
        maxErrors = SecondaryRepairForms.limit(t, "VHSR09IS-MOVE-MSG", "ERROR-LOOP-INDEX");
    }

    private static String pick(List<String> texts, String containing) {
        return texts.stream().filter(x -> x.contains(containing)).findFirst().orElseThrow(() -> new IllegalStateException("no text with " + containing));
    }

    public record Repair(String stockNo, String serialNo, String component, String faultCode, long quantity) {}

    public record RepairDetail(String job, int doc, String status, List<Repair> repairs) {}

    /** VHSR09IS-RET-DATA: the checks, the sub job must be out the door (VHSR09B), then its repairs (VHSR09A) */
    public RepairDetail details(String jobNo, Integer doc) {
        String job = SecondaryRepairForms.upper(jobNo);
        int d = doc == null ? 0 : doc;
        if (job.isEmpty() && d == 0) throw new InvalidRequestException(jobDocRequired);
        if (job.isEmpty()) throw new InvalidRequestException(jobRequired);
        if (d == 0) throw new InvalidRequestException(docRequired);
        ProgramResult b = programs.attempt("VHSR09B", Map.of("VHSR09B_CRIT", key(job, d))).result();
        Map<String, Object> data = b.record("VHSR09B_DATA");
        if (number(data, "docCount") != 1) throw new InvalidRequestException(unknown);
        String stat = text(data, "subJobStat");
        if (stat.equals(closedStatus)) {
            throw new InvalidRequestException((subJob + String.format("%04d", d % 10000) + ofJob + String.format("%-6s", job) + isClosed).strip());
        }
        if (!outTheDoor.contains(stat)) throw new InvalidRequestException(notOut);
        ProgramResult a = programs.attempt("VHSR09A", Map.of("VHSR09A_CRIT", key(job, d))).result();
        int urc = a.control() == null ? 0 : a.control().returnCode();
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error("VHSR09A", a.control());
        List<Repair> repairs = new ArrayList<>();
        if (urc != 100) {
            for (Map<String, Object> row : rows(a, "VHSR09A_DATA_01", "dataRow")) {
                repairs.add(new Repair(text(row, "stockNo"), text(row, "serNo"), text(row, "cmpntCd"), text(row, "cmpntFaultCd"), number(row, "cntCmpntsRepr")));
            }
        }
        return new RepairDetail(job, d, stat, repairs);
    }

    public record Kit(String kitNo, String description) {}

    /** VHSR09IS-KIT: a kit must be defined and have details (VHSR09C) */
    public Kit kit(String kitNo) {
        String k = SecondaryRepairForms.strip(kitNo);
        ProgramResult r = programs.attempt("VHSR09C", Map.of("VHSR09C_CRIT", Map.of("kitNo", k))).result();
        Map<String, Object> d = r.record("VHSR09C_DATA");
        String found = text(d, "kitFound"), details = text(d, "kitDetlFound");
        if (found.equals("Y") && details.equals("Y")) return new Kit(k, text(d, "kitDesc"));
        if (found.equals("N") && details.equals("N")) throw new InvalidRequestException(kitUnknown);
        if (found.equals("Y") && details.equals("N")) throw new InvalidRequestException(kitEmpty);
        throw programs.error("VHSR09C", r.control());
    }

    /** VHSR09IS-STK-DESC: a stock number's short description (VHSR09H), blank when unknown */
    public String stockDescription(String stockNo) {
        ProgramResult r = programs.attempt("VHSR09H", Map.of("VHSR09H_CRIT", Map.of("stockNo", SecondaryRepairForms.strip(stockNo)))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        return urc == 0 || urc == 4 ? text(r.record("VHSR09H_DATA"), "stockShortDesc") : "";
    }

    public record RepairLine(String stockNo, String serialNo, String component, String faultCode, Integer quantity) {}

    public record Added(String message, RepairDetail detail) {}

    /**
     * VHSR09IS-ADDREPR + DB-STORE: the typed lines are checked as the old screen did (a first blank line without a kit,
     * stock number, part code VHSR09F, fault code VHSR09G, quantity), at most 6 errors "Line: nn - ..."; then the kit's
     * parts (VHSR09E) and the typed lines with a stock number are written together (VHSR09D).
     *
     * @param noRepair the old "No Repair" button: the screen's no-repair kit instead of {@code kitNo}
     */
    public Added add(String jobNo, Integer doc, String kitNo, boolean noRepair, List<RepairLine> lines, String terminal) {
        RepairDetail now = details(jobNo, doc);
        String kit = noRepair ? noRepairKit : SecondaryRepairForms.strip(kitNo);
        List<RepairLine> in = lines == null ? List.of() : lines;
        List<String> errors = new ArrayList<>();
        int blank = 0;
        for (int i = 0; i < in.size() + 3 && blank <= 2 && errors.size() <= 6; i++) {
            RepairLine l = i < in.size() && in.get(i) != null ? in.get(i) : new RepairLine(null, null, null, null, null);
            int set = i + 1;
            String stock = SecondaryRepairForms.strip(l.stockNo()), ser = SecondaryRepairForms.strip(l.serialNo()),
                    part = SecondaryRepairForms.strip(l.component()), fault = SecondaryRepairForms.strip(l.faultCode());
            boolean empty = stock.isEmpty() && ser.isEmpty() && part.isEmpty() && fault.isEmpty();
            if (empty) blank++;
            if (kit.isEmpty() && empty && set == 1) {
                errors.add(lineMessage(set, dataRequired));
                break;
            }
            if (blank > 2 || empty) continue;
            if (stock.isEmpty()) errors.add(lineMessage(set, stockRequired));
            else if (!numeric(stock.length() > 9 ? stock.substring(0, 9) : stock)) errors.add(lineMessage(set, stockNotNumeric));
            if (errors.size() == 6) break;
            if (!part.isEmpty()) {
                ProgramResult r = programs.attempt("VHSR09F", Map.of("VHSR09F_CRIT", Map.of("cmpntCd", part))).result();
                if (!ok(r)) errors.add(lineMessage(set, badPart));
            }
            if (errors.size() == 6) break;
            if (!fault.isEmpty()) {
                ProgramResult r = programs.attempt("VHSR09G", Map.of("VHSR09G_CRIT", Map.of("cmpntFaultCd", fault))).result();
                if (!ok(r)) errors.add(lineMessage(set, badFault));
            }
            if (errors.size() == 6) break;
            if (l.quantity() == null || l.quantity() <= 0) errors.add(lineMessage(set, badQuantity));
            if (errors.size() == 6) break;
        }
        SecondaryRepairForms.fail(errors, width, maxErrors);

        List<Map<String, Object>> out = new ArrayList<>();
        String kitDesc = "";
        boolean kitMoved = false, detailsMoved = false;
        if (!kit.isEmpty()) {
            ProgramResult e = programs.attempt("VHSR09E", Map.of("VHSR09E_CRIT", Map.of("kitNo", kit))).result();
            if (e.control() != null && e.control().rowCount() > 0) {
                for (Map<String, Object> p : rows(e, "VHSR09E_DATA", "dataRow")) {
                    out.add(row(text(p, "partStockNo"), text(p, "serDesc"), text(p, "partNo"), text(p, "partFaultCd"), number(p, "cntParts")));
                }
                kitMoved = true;
                if (!noRepair) kitDesc = kit(kit).description();               // the old screen checked a typed kit on leaving its field
            }
        }
        int blanks = 0;
        for (int i = 0; i < in.size() && blanks <= 2; i++) {
            RepairLine l = in.get(i);
            String stock = l == null ? "" : SecondaryRepairForms.strip(l.stockNo());
            if (stock.isEmpty()) { blanks++; continue; }
            out.add(row(stock, SecondaryRepairForms.strip(l.serialNo()), SecondaryRepairForms.strip(l.component()), SecondaryRepairForms.strip(l.faultCode()),
                    l.quantity() == null ? 0 : l.quantity()));
            detailsMoved = true;
        }
        if (out.isEmpty()) return new Added("", now);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("jobNo", now.job());
        head.put("subJobDocNo", now.doc());
        head.put("termLastUpd", terminal == null ? "" : terminal);
        ProgramResult r = programs.attempt("VHSR09D", Map.of("VHSR09D_DATA_01", Map.of("dataRow", out), "VHSR09D_DATA_02", head)).result();
        if (!ok(r)) throw programs.error("VHSR09D", r.control());
        String message;
        if (detailsMoved && !kitMoved) message = added;
        else if (!detailsMoved) message = (String.format("%-32s", fromKit) + String.format("%-12s", kitDesc) + fromKitEnd).strip();
        else message = (String.format("%-32s", fromKit) + String.format("%-12s", kitDesc) + fromKitAnd + added).strip();
        return new Added(message, details(now.job(), now.doc()));
    }

    private static Map<String, Object> row(String stock, String ser, String part, String fault, long count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stockNo", stock);
        m.put("serNo", ser);
        m.put("cmpntCd", part);
        m.put("cmpntFaultCd", fault);
        m.put("cntCmpntsRepr", count);
        return m;
    }

    private static boolean ok(ProgramResult r) {
        int u = r.control() == null ? 0 : r.control().returnCode();
        return u == 0 || u == 4;
    }

    private static boolean numeric(String s) {
        String t = s.stripTrailing();
        return !t.isEmpty() && t.chars().allMatch(Character::isDigit);
    }

    private String lineMessage(int set, String message) {
        return line + String.format("%02d", set % 100) + message;
    }

    private static Map<String, Object> key(String job, int doc) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("jobNo", job);
        k.put("subJobDocNo", doc);
        return k;
    }
}
