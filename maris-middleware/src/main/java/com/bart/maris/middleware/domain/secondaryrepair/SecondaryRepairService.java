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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Secondary repair, read-only screens: stock inquiry (VHSR05I), job status (VHSR11I), job history (VHSR10I) and
 * reprint of the job form (VHSR15I). Rules from MARIS_RUI/EGLSource/MRSSecondaryRepair/RUIFunctions.egl.
 * The old Detail / Summary print buttons of VHSR05I had nothing connected in the web version, so they have no API.
 */
public class SecondaryRepairService {
    private static final String PKG = "MRSSecondaryRepair";

    private final Programs programs;
    private final List<String> storageChoices;
    private final String noStock, stockUnknown, badStatus, badStorage, noJobs, firstRows,
            badJobNo, closedJob, noSuchJob, reprintNoJob, reprintClosed, reprintNoSuchJob, reprintSubmitted;
    private final int maxStockRows;

    public SecondaryRepairService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        this.storageChoices = t.items("VHSR05I", "Cmblongtermstorage");
        this.noStock = t.assigned("VHSR05IS-VAL-STOCK", PKG, "FINAL-MESSAGE", "Enter");
        this.stockUnknown = t.assigned("VHSR05IS-VAL-STOCK", PKG, "FINAL-MESSAGE", "does not exist");
        this.badStatus = t.assigned("VHSR05IS-VAL-OTHER", PKG, "FINAL-MESSAGE", "STATUS");
        this.badStorage = t.assigned("VHSR05IS-VAL-OTHER", PKG, "FINAL-MESSAGE", "STORAGE");
        this.noJobs = t.assigned("VHSR05IS-LIST", PKG, "FINAL-MESSAGE", "No information");
        this.firstRows = t.assigned("VHSR05IS-LIST", PKG, "FINAL-MESSAGE", "ROWS");
        this.maxStockRows = Integer.parseInt(t.compared("VHSR05IS-LIST", PKG, "UROWCNT").get(0));
        this.badJobNo = t.assigned("VHSR11IS-LIST", PKG, "FINAL-MESSAGE", "Invalid");
        this.closedJob = t.assigned("VHSR11IS-LIST", PKG, "FINAL-MESSAGE", "Closed");
        this.noSuchJob = t.assigned("VHSR11IS-LIST", PKG, "FINAL-MESSAGE", "does not exist");
        this.reprintNoJob = t.assigned("VHSR15IS-VALIDATE", PKG, "FINAL-MESSAGE", "valid Job");
        this.reprintClosed = t.assigned("VHSR15IS-VALIDATE", PKG, "FINAL-MESSAGE", "Closed");
        this.reprintNoSuchJob = t.assigned("VHSR15IS-VALIDATE", PKG, "FINAL-MESSAGE", "does not exist");
        this.reprintSubmitted = t.assigned("VHSR15IS-MESSAGE", PKG, "FINAL-MESSAGE", "SUBMITTED");
    }

    // ---- VHSR05I: stock inquiry ----------------------------------------------------------------------------

    public record Code(String code, String description) {}

    public record StockOptions(List<Code> statuses, List<String> longTermStorage) {}

    /** VHSR05IS-STAT-LOAD: the sub job statuses (program VHSR05C) */
    public StockOptions stockOptions() {
        return new StockOptions(statuses(), storageChoices);
    }

    private List<Code> statuses() {
        List<Code> out = new ArrayList<>();
        ProgramResult r = programs.run("VHSR05C", Map.of());
        if (r.control().returnCode() == 100) return out;
        for (Map<String, Object> row : rows(r, "VHSR05C_DATA", "dataRow")) out.add(new Code(text(row, "subJobStat"), text(row, "subJobStatDesc")));
        return out;
    }

    public record StockJob(String job, long doc, String serialNo, long quantityIn, String from, String workType, String dateIn,
                           String carOrEquipment, String area, String status, String dateOut) {}

    /** @param note the old notice when the maximum number of rows came back; @param message when nothing was found */
    public record StockInquiry(String stockNo, String description, int count, long totalQuantityIn, List<StockJob> jobs, String note, String message) {}

    /** VHSR05IS-LIST: VAL-STOCK (VHSR05B), VAL-OTHER, then the jobs (VHSR05A), with the old messages */
    public StockInquiry stockInquiry(String stockNo, String serialNo, String status, String longTermStorage) {
        String stock = upper(stockNo), ser = upper(serialNo), stat = upper(status), lts = upper(longTermStorage);
        if (stock.isEmpty()) throw new InvalidRequestException(noStock);
        if (stock.length() > 9) throw new InvalidRequestException("The stock number holds at most 9 characters.");   // VHSR05A_CRIT.STOCK-NO char(9)
        if (ser.length() > 12) throw new InvalidRequestException("The serial number holds at most 12 characters.");
        ProgramResult s = programs.run("VHSR05B", Map.of("VHSR05B_CRIT", Map.of("stockNo", stock)));
        if (s.control().returnCode() == 100) throw new InvalidRequestException(stockUnknown);
        String description = text(s.record("VHSR05B_DATA"), "stockDesc");
        if (!stat.isEmpty() && statuses().stream().noneMatch(c -> c.code().equals(stat))) throw new InvalidRequestException(badStatus);
        if (!lts.isEmpty() && !storageChoices.contains(lts)) throw new InvalidRequestException(badStorage);

        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("stockNo", stock);
        crit.put("serNo", ser);
        crit.put("subJobStat", stat);
        crit.put("longTermStorInd", lts);
        ProgramResult r = programs.run("VHSR05A", Map.of("VHSR05A_CRIT", crit));
        if (r.control().returnCode() == 100) return new StockInquiry(stock, description, 0, 0, List.of(), null, noJobs);
        List<StockJob> jobs = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHSR05A_DATA", "dataRow")) {
            jobs.add(new StockJob(text(row, "jobNo"), number(row, "subJobDocNo"), text(row, "serNo"), number(row, "cntCmpntsIn"), text(row, "sentFrom"),
                    text(row, "workType"), text(row, "dateIn"), joined(row.get("carNoOrEq")), text(row, "reprArea"), text(row, "subJobStat"), text(row, "dateOut")));
        }
        long total = number(r.record("VHSR05A_DATA"), "totCntCmpntsIn");
        return new StockInquiry(stock, description, jobs.size(), total, jobs, r.control().rowCount() >= maxStockRows ? firstRows : null, null);
    }

    /** a field the record splits into parts, read back as one text */
    private static String joined(Object v) {
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder();
            for (Object part : m.values()) sb.append(part instanceof Map<?, ?> ? joined(part) : part == null ? "" : part.toString());
            return sb.toString().strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    // ---- VHSR11I / VHSR10I: a job and its sub jobs -----------------------------------------------------------

    public record SubJob(long doc, String stockNo, String serialNo, String faultCode, long quantityIn, long quantityOut, long quantityScrapped,
                         String dateIn, String dateOut, String area, String workType, String diagnosticWorkType, String status, String qaInspector,
                         String dateToVendor, String vendor, String warrantyTime, String longTermStorage) {}

    public record Job(String job, String status, String car, String dept, String equipmentType, String equipmentLocation, String equipmentId,
                      String incident, String printControlNo, long openDocs, int count, List<SubJob> subJobs) {}

    /** VHSR11IS-LIST: an open job; a closed job or an unknown one gives the old message */
    public Job openJob(String jobNo) {
        return job(jobNo, true);
    }

    /** VHSR10IS-RET-SUB: any job, closed ones too (the history the stock inquiry opens) */
    public Job jobHistory(String jobNo) {
        return job(jobNo, false);
    }

    private Job job(String jobNo, boolean openOnly) {
        String j = upper(jobNo);
        if (j.isEmpty()) throw new InvalidRequestException(badJobNo);
        ProgramResult r = programs.run("VHSR11A", Map.of("VHSR11A_CRIT", Map.of("jobNo", j)));
        Map<String, Object> d = r.record("VHSR11A_DATA");
        boolean found = r.control().returnCode() != 100 && "Y".equals(text(d, "recordFound"));
        if (openOnly && !found) throw new InvalidRequestException(noSuchJob);
        if (openOnly && "CLO".equals(text(d, "jobStat"))) throw new InvalidRequestException(closedJob);
        List<SubJob> subs = new ArrayList<>();
        if (r.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r, "VHSR11A_DATA", "dataRow")) {
                subs.add(new SubJob(number(row, "subJobDocNo"), joined(row.get("stockNo")), text(row, "serNo"), text(row, "cmpntFaultCd"),
                        number(row, "cntCmpntsIn"), number(row, "cntCmpntsOut"), number(row, "cntCmpntsScrappd"), text(row, "dateIn"),
                        text(row, "dateOut"), text(row, "reprArea"), text(row, "workType"), text(row, "diagWorkType"), text(row, "subJobStat"),
                        text(row, "qaInspectId"), text(row, "dateToVendor"), text(row, "vendorId"), text(row, "warTimeCtlCd"), text(row, "longTermStorInd")));
            }
        }
        return new Job(j, text(d, "jobStat"), text(d, "carNo"), text(d, "deptCd"), text(d, "equipType"), text(d, "equipLoc"), text(d, "equipId"),
                text(d, "incdNo"), text(d, "jobPrtCtlNo"), number(d, "openDocCount"), subs.size(), subs);
    }

    public record Repair(long doc, String component, String faultCode, long count, String serialNo, String stockNo) {}

    public record Labor(long doc, String date, String employee, String position, BigDecimal hours, String shift, String workElement,
                        String workFunction, String workOrder, String functionCode) {}

    public record SubJobDetails(String job, long doc, List<String> narrative, List<String> repairNarrative, List<Repair> repairs, List<Labor> labor) {}

    /** VHSR10IS-PRV-NXT: one sub job's narrative (VHSRJNX), repair narrative (VHSRJRN), repairs (VHSR09A) and labor (VHSR10A) */
    public SubJobDetails subJob(String jobNo, int doc) {
        String j = upper(jobNo);
        Map<String, Object> key = Map.of("jobNo", j, "subJobDocNo", doc);
        List<String> narr = narrative("VHSRJNX", key), reprNarr = narrative("VHSRJRN", key);
        List<Repair> repairs = new ArrayList<>();
        ProgramResult r9 = programs.run("VHSR09A", Map.of("VHSR09A_CRIT", key));
        if (r9.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r9, "VHSR09A_DATA_01", "dataRow")) {
                repairs.add(new Repair(number(row, "jobReprDocNo"), text(row, "cmpntCd"), text(row, "cmpntFaultCd"), number(row, "cntCmpntsRepr"),
                        text(row, "serNo"), joined(row.get("stockNo"))));
            }
        }
        List<Labor> labor = new ArrayList<>();
        ProgramResult r10 = programs.run("VHSR10A", Map.of("VHSR10A_CRIT", key));
        if (r10.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r10, "VHSR10A_DATA", "dataRow")) {
                Object hrs = row.get("reprLaborHrs");
                labor.add(new Labor(number(row, "jobLaborDocNo"), text(row, "dateWork"), text(row, "emplNo"), text(row, "techPosnCd"),
                        hrs instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(hrs == null ? 0 : hrs)), text(row, "shift"),
                        text(row, "workElementCd"), text(row, "workFunc"), text(row, "workOrderNo"), text(row, "funcCd")));
            }
        }
        return new SubJobDetails(j, doc, narr, reprNarr, repairs, labor);
    }

    private List<String> narrative(String program, Map<String, Object> key) {
        ProgramResult r = programs.run(program, Map.of(program + "_DATA", key));
        List<String> out = new ArrayList<>();
        if (r.control().returnCode() == 100 || r.control().rowCount() == 0) return out;
        for (Map<String, Object> row : rows(r, program + "_DATA", "dataRow")) out.add(text(row, "narr"));
        return out;
    }

    // ---- VHSR15I: reprint the job form -----------------------------------------------------------------------

    public record Printer(String description, String vtam) {}

    /** @param defaultPrinter the user group's default printer (its VTAM name), when it has one */
    public record ReprintOptions(List<Printer> printers, String defaultPrinter) {}

    /** VHSR15IS-INIT: the printers and the user group's default printer (program VHSR15C) */
    public ReprintOptions reprintOptions(String userGroup) {
        ProgramResult r = programs.run("VHSR15C", Map.of("VHSR15C_CRIT", Map.of("userGroup", userGroup == null ? "" : userGroup)));
        List<Printer> out = new ArrayList<>();
        if (r.control().returnCode() == 100) return new ReprintOptions(out, null);
        for (Map<String, Object> row : rows(r, "VHSR15C_DATA", "dataRow")) out.add(new Printer(text(row, "prtrLocDesc"), text(row, "vtamName")));
        int def = (int) number(r.record("VHSR15C_DATA"), "defaultPrtIdx");
        return new ReprintOptions(out, def > 0 && def <= out.size() ? out.get(def - 1).vtam() : null);
    }

    /** VHSR15IS-VALIDATE (VHSR15A) + VHSR15IS-SUBMIT (VHSR15B) */
    public String reprint(String jobNo, Integer doc, String printer) {
        String j = upper(jobNo);
        if (j.isEmpty()) throw new InvalidRequestException(reprintNoJob);
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        int d = doc == null ? 1 : doc;
        ProgramResult r = programs.run("VHSR15A", Map.of("VHSR15A_CRIT", Map.of("jobNo", j, "docNo", d)));
        if (r.control().returnCode() == 100) throw new InvalidRequestException(reprintNoSuchJob);
        Map<String, Object> data = r.record("VHSR15A_DATA");
        if (!"Y".equals(text(data, "recordExist"))) throw new InvalidRequestException(reprintNoSuchJob);
        if ("CLO".equals(text(data, "subJobStat"))) throw new InvalidRequestException(reprintClosed);
        ProgramResult p = programs.attempt("VHSR15B", Map.of("VHSR15B_CRIT", Map.of("jobNo", j, "docNo", d, "reptType", printer.strip()))).result();
        if (p.control().returnCode() != 0 && p.control().returnCode() != 4) throw programs.error("VHSR15B", p.control());
        return reprintSubmitted;
    }

    private static String upper(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }
}
