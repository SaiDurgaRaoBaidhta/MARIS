package com.bart.maris.middleware.domain.vehiclequeries;

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
import java.util.Locale;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Checked;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Criteria;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Option;
import java.util.Map;

/**
 * Home yard status (old VHYS01I criteria with its four report options) and the Base / Status report it opens
 * (VHYS02I: cars, a car's incidents, an incident's narrative). The checks follow VHYS01IS-CHK-SEL / MOVE-VAL (Display)
 * and VHYS01IS-CHK-SEL-X / SUBMIT (Print); every message, title and start value is read from the old screens.
 */
public final class VehicleQueriesHomeYardStatusService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final VehicleQueriesStatusCriteria criteria;
    private final String noCars, noIncidents;
    /** VHYS03I: the incident rows VHYS03B can hold, and the messages for "full" and "no cars" */
    private final int nogoRows, summaryRows;
    /** VHYS04I / VHYS05I: the header for each home yard, the cut-off footer, the no-cars message, the summary rows */
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final String summaryFooter, summaryNoCars;
    private final java.util.SortedMap<Integer, String> typeRowNames;
    private final List<EglTexts.Cell> typeFormulas;
    private final int typeRowCount;
    private final List<String> types = new ArrayList<>(), typeHeadings = new ArrayList<>();
    private final String nogoFull, nogoNoCars;

    public VehicleQueriesHomeYardStatusService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        criteria = new VehicleQueriesStatusCriteria(programs, t, VehicleQueriesStatusCriteria.Family.HOME_YARD);
        noCars = t.assignedAll("VHYS02IS-RET-DATA", PKG, "FINAL-ERROR-MESSAGE").get(0);
        noIncidents = t.assignedAll("VHYS02IS-INCD-DET", PKG, "NARR").get(0);
        nogoRows = Integer.parseInt(t.compared("VHYS03IS-RET-DATA", PKG, "INCD-CNT").get(0));
        nogoFull = t.assignedAll("VHYS03IS-600-ROWS", PKG, "FINAL-ERROR-MESSAGE").get(0);
        nogoNoCars = t.assignedAll("VHYS03IS-SELECT", PKG, "FINAL-ERROR-MESSAGE").get(0);
        List<String> yardCodes = t.compared("VHYS04IS-RET-DATA", PKG, "HOME-YRD"), names = t.assignedUnder("VHYS04IS-RET-DATA", PKG, "HEADER", "HOME-YRD");
        for (int i = 0; i < yardCodes.size(); i++) headers.put(yardCodes.get(i), names.get(i));
        summaryRows = Integer.parseInt(t.compared("VHYS04IS-RET-DATA", PKG, "CAR-COUNT").get(0));
        summaryFooter = t.label("VHYS04I", "lblFooter");
        summaryNoCars = t.triggeredPrompt("VHYS04I", "NO-CARS-FOUND");
        typeRowNames = new java.util.TreeMap<>(t.indexed("VHYS05IS-INIT", PKG, "CAR-TYPE-DATA"));
        typeRowNames.putAll(t.indexed("VHYS05IS-TYPE-CHK", PKG, "CAR-TYPE-DATA"));          // names TYPE-CHK sets again
        typeFormulas = t.cells("VHYS05IS-TYPE-CHK", PKG, "CAR-");
        typeRowCount = t.assignedNumber("VHYS05IS-INIT", PKG, "ROW-COUNT").intValue();
        for (EglTexts.Column col : t.tableColumns("VHYS05I", "cntCarsSummary")) {
            String f = col.field().toUpperCase(Locale.ROOT);
            if (!f.startsWith("CAR-") || !f.endsWith("-DATA") || f.equals("CAR-TYPE-DATA")) continue;
            String type = f.substring(4, f.length() - 5);
            if (!type.equals("TOTAL")) types.add(type);
            typeHeadings.add(col.heading());
        }
    }

    public VehicleQueriesStatusCriteria.Options options() {
        return criteria.options();
    }

    public Checked check(Criteria c) {
        return criteria.check(c);
    }

    public String print(Criteria c, String printer) {
        return criteria.print(c, printer);
    }

    // ---- VHYS02I: Base / Status ------------------------------------------------------------------------

    public record Car(String car, String status, String vehicleStatus, String incidentIndicator, String location, String ato, long hours,
                      long minutes, long mtbi, String homeYard) {}

    public record Cars(Checked criteria, List<Car> cars, String message) {}

    private Map<String, Object> carsCrit(Checked k, String retInd, String car) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", k.yard());
        crit.put("vehStat", k.status());
        crit.put("carNo", car);
        crit.put("retInd", retInd);
        crit.put("reportOption", k.option().number());
        crit.put("carNoFrom", k.carFrom());
        crit.put("carNoTo", k.carTo());
        return crit;
    }

    private Checked baseOrStatus(Criteria c) {
        Checked k = check(c);
        if (k.option() != Option.BASE && k.option() != Option.STATUS) throw new InvalidRequestException("This list is for the Base and Status options.");
        return k;
    }

    /** VHYS02IS-RET-DATA (program VHYS02A, RET-IND "CAR") */
    public Cars cars(Criteria c) {
        Checked k = baseOrStatus(c);
        ProgramResult r = programs.attempt("VHYS02A", Map.of("VHYS02A_CRIT", carsCrit(k, "CAR", ""))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new Cars(k, List.of(), noCars);
        if (urc != 0 && urc != 4) throw programs.error("VHYS02A", r.control());
        List<Car> cars = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS02A_CAR_DATA", "dataRow")) {
            @SuppressWarnings("unchecked") Map<String, Object> st = row.get("dispVehStat") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
            String shown = String.format("%-6s%-1s%-2s", text(st, "vehStat"), text(st, "vehStatFiller"), text(st, "incdInd")).stripTrailing();
            cars.add(new Car(text(row, "carNo"), shown, text(st, "vehStat"), text(st, "incdInd"), text(row, "currLoc"), text(row, "atoPresentInd"),
                    number(row, "cumHrs"), number(row, "cumMin"), number(row, "mtbi"), text(row, "homeYrd")));
        }
        return new Cars(k, cars, null);
    }

    public record Incident(String incident, String symptom, String location, String date, String time, String status, String direction) {}

    public record Incidents(String car, List<Incident> incidents, String message) {}

    /** VHYS02IS-INCD-DET (program VHYS02A, RET-IND "INCDS") */
    public Incidents incidents(Criteria c, String car) {
        Checked k = baseOrStatus(c);
        String carNo = car == null ? "" : car.strip();
        ProgramResult r = programs.attempt("VHYS02A", Map.of("VHYS02A_CRIT", carsCrit(k, "INCDS", carNo))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode(), n = r.control() == null ? 0 : r.control().rowCount();
        if (n == 0 && urc == 100) return new Incidents(carNo, List.of(), noIncidents);
        if (n == 0 && urc != 0 && urc != 4) throw programs.error("VHYS02A", r.control());
        List<Incident> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS02A_INCD_DATA", "dataRow")) {
            @SuppressWarnings("unchecked") Map<String, Object> st = row.get("dispVehIncdStat") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
            String shown = String.format("%-3s%-1s%s", text(st, "vehIncdStat"), text(st, "vehIncdFiller"), text(st, "vehIncdStatDesc")).stripTrailing();
            out.add(new Incident(text(row, "incdNo"), text(row, "sympCd"), text(row, "incdLoc"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"),
                    shown, text(row, "conDirection")));
        }
        return new Incidents(carNo, out, null);
    }

    /** VHYS02IS-SELECT (program VHYS02B): an incident's narrative lines; homeYard is the car's home yard */
    public List<String> narrative(String car, String incident, String homeYard) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", homeYard == null ? "" : homeYard.strip());
        crit.put("incdNo", incident == null ? "" : incident.strip());
        crit.put("carNo", car == null ? "" : car.strip());
        ProgramResult r = programs.run("VHYS02B", Map.of("VHYS02B_CRIT", crit));
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS02B_NARR_DATA", "dataRow")) lines.add(text(row, "narr"));
        return lines;
    }

    // ---- VHYS03I: NOGO ---------------------------------------------------------------------------------

    public record NogoIncident(String car, String incidentCar, String status, String location, String incident, String incidentLocation,
                               String date, String time, String direction) {}

    public record Nogo(Checked criteria, int cars, List<NogoIncident> incidents, String message) {}

    /**
     * VHYS03IS-RET-DATA: the NOGO cars (VHYS03A), then each car's incidents (VHYS03B, which adds them to one data record
     * at the row count it is given; the record and the count go back with every call, as on the old screen)
     */
    public Nogo nogo(Criteria c) {
        Checked k = check(c);
        if (k.option() != Option.NOGO) throw new InvalidRequestException("This list is for the NOGO option.");
        Map<String, Object> critA = new LinkedHashMap<>();
        critA.put("homeYrd", k.yard());
        critA.put("reportOption", k.option().number());
        critA.put("carNoFrom", k.carFrom());
        critA.put("carNoTo", k.carTo());
        critA.put("carNo", "");
        ProgramResult a = programs.attempt("VHYS03A", Map.of("VHYS03A_CRIT", critA)).result();
        int urc = a.control() == null ? 0 : a.control().returnCode();
        int carCount = (urc == 0 || urc == 4) && a.control() != null ? a.control().rowCount() : 0;
        if (urc != 0 && urc != 4 && urc != 100) throw programs.error("VHYS03A", a.control());
        List<Map<String, Object>> cars = carCount == 0 ? List.of() : rows(a, "VHYS03A_DATA", "carRow");
        Map<String, Object> data = new LinkedHashMap<>();
        int rowCnt = 0, incidents = 0;
        boolean full = false;
        for (int i = 0; i < Math.min(carCount, cars.size()); i++) {
            Map<String, Object> car = cars.get(i);
            Map<String, Object> critB = new LinkedHashMap<>();
            critB.put("carNo", text(car, "carNo"));
            critB.put("incdInd", text(car, "incdInd"));
            critB.put("currLoc", text(car, "currLoc"));
            critB.put("vehStat", text(car, "vehStat"));
            critB.put("vehStatFiller", text(car, "vehStatFiller"));
            critB.put("rowCnt", rowCnt);
            ProgramResult b = programs.attempt("VHYS03B", Map.of("VHYS03B_CRIT", critB, "VHYS03B_DATA", data)).result();
            Map<String, Object> backCrit = b.record("VHYS03B_CRIT"), backData = b.record("VHYS03B_DATA");
            if (backCrit != null) rowCnt = (int) number(backCrit, "rowCnt");
            if (backData != null) data = new LinkedHashMap<>(backData);
            int u = b.control() == null ? 0 : b.control().returnCode();
            if (u == 0 || u == 4) {
                incidents = rowCnt;
                if (incidents == nogoRows) full = true;
            }
        }
        List<NogoIncident> out = new ArrayList<>();
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = data.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (int i = 0; i < Math.min(incidents, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            @SuppressWarnings("unchecked") Map<String, Object> st = row.get("dispVehStat") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
            String shown = String.format("%-6s%-1s%-2s", text(st, "vehStat"), text(st, "vehStatFiller"), text(st, "incdInd")).stripTrailing();
            out.add(new NogoIncident(text(row, "carNo"), text(row, "storeCarNo"), shown, text(row, "currLoc"), text(row, "incdNo"), text(row, "incdLoc"),
                    text(row, "dateIncdOccr"), text(row, "timeIncdOccr"), text(row, "conDirection")));
        }
        String message = carCount == 0 ? nogoNoCars : full ? nogoFull : null;
        return new Nogo(k, carCount, out, message);
    }

    // ---- VHYS04I / VHYS05I: Summary ------------------------------------------------------------------------

    /** {@code atoMissing}: the old "*" after the car number (ATO equipment not installed) */
    public record SummaryCar(String car, boolean atoMissing, String status, String location, String homeYard) {}

    public record Summary(Checked criteria, String header, List<SummaryCar> cars, String footer, String message) {}

    /** VHYS04IS-RET-DATA (program VHYS04A): the yard's cars, two to a row on the old screen */
    public Summary summary(Criteria c) {
        Checked k = check(c);
        if (k.option() != Option.SUMMARY) throw new InvalidRequestException("This list is for the Summary option.");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("homeYrd", k.yard());
        ProgramResult r = programs.attempt("VHYS04A", Map.of("VHYS04A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String header = headers.getOrDefault(k.yard(), "");
        if (urc != 0 && urc != 4) return new Summary(k, header, List.of(), null, summaryNoCars);
        int count = r.control() == null ? 0 : r.control().rowCount();
        List<SummaryCar> cars = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS04A_DATA", "dataRow")) {
            for (String n : new String[] {"", "1"}) {
                @SuppressWarnings("unchecked") Map<String, Object> no = row.get("dispCarNo" + n) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
                @SuppressWarnings("unchecked") Map<String, Object> st = row.get("dispVehStat" + n) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
                String car = text(no, "carNo" + n);
                if (car.isEmpty()) continue;
                String shown = String.format("%-6s%-1s%-2s", text(st, "vehStat" + n), text(st, "vehStat" + n + "Filler"), text(st, "incdInd" + n)).stripTrailing();
                cars.add(new SummaryCar(car, !text(no, "carNo" + n + "Star").isEmpty(), shown, text(row, "currLoc" + n), text(row, "homeYrd" + n)));
            }
        }
        return new Summary(k, header, cars, count == summaryRows ? summaryFooter : null, null);
    }

    public record TypeRow(int row, String name, List<Integer> byType, int total) {}

    public record TypeSummary(String yard, String header, List<String> types, List<String> headings, List<TypeRow> rows, String message) {}

    /** the Summary button: VHYS05I (program VHYS05A), the yard's cars counted by type */
    public TypeSummary typeSummary(String yard) {
        String y = yard == null ? "" : yard.strip();
        ProgramResult r = programs.attempt("VHYS05A", Map.of("VHYS05A_CRIT", Map.of("homeYrd", y))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String header = headers.getOrDefault(y, "");
        if (urc == 100) return new TypeSummary(y, header, types, typeHeadings, List.of(), null);
        if (urc != 0 && urc != 4) return new TypeSummary(y, header, types, typeHeadings, List.of(), programs.error("VHYS05A", r.control()).getMessage());
        VehicleQueriesTypeSummary sum = new VehicleQueriesTypeSummary(types, typeFormulas);
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<Map<String, Object>> cars = rows(r, "VHYS05A_DATA", "dataRow");
        for (int i = 0; i < Math.min(n, cars.size()); i++) sum.count(cars.get(i));
        List<TypeRow> out = new ArrayList<>();
        List<VehicleQueriesTypeSummary.Cells> cells = sum.rows(typeRowCount);
        for (int i = 0; i < cells.size(); i++) out.add(new TypeRow(i + 1, typeRowNames.getOrDefault(i + 1, ""), cells.get(i).byType(), cells.get(i).total()));
        return new TypeSummary(y, header, types, typeHeadings, out, null);
    }

}
