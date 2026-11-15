package com.bart.maris.middleware.domain.vehiclequeries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Checked;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Criteria;
import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesStatusCriteria.Option;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Yard status (old VHYS06I criteria) and its reports: Base / Status (VHYS07I), NOGO (VHYS08I), Summary (VHYS09I) and the
 * summary by car type (VHYS10I). The criteria are checked like the home yard status screen's; the reports follow their
 * own statement groups, which differ from the home yard ones in places (the narrative's yard is the car's location,
 * VHYS07I has its own failure message, VHYS08I its own messages, VHYS09I shows the yard code when it has no name).
 */
public final class VehicleQueriesYardStatusService {
    private static final String PKG = "MRSVehicleQueries";

    private final Programs programs;
    private final VehicleQueriesStatusCriteria criteria;
    private final String noCars, tooMany, noIncidents, nogoNoIncidents, nogoNoCars, summaryFull, summaryNoCars;
    private final int summaryRows;
    private final Map<String, String> summaryHeaders = new LinkedHashMap<>(), typeHeaders = new LinkedHashMap<>();
    private final SortedMap<Integer, String> typeRowNames;
    private final List<EglTexts.Cell> typeFormulas;
    private final int typeRowCount;
    private final List<String> types = new ArrayList<>(), typeHeadings = new ArrayList<>();

    public VehicleQueriesYardStatusService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        criteria = new VehicleQueriesStatusCriteria(programs, t, VehicleQueriesStatusCriteria.Family.YARD);
        List<String> carMsgs = t.assignedAll("VHYS07IS-RET-DATA", PKG, "MSG");
        noCars = carMsgs.get(0);
        tooMany = carMsgs.get(1);
        noIncidents = t.assignedAll("VHYS07IS-INCD-DET", PKG, "NARR").get(0);
        List<String> nogo = t.assignedAll("VHYS08IS-RET-DATA", PKG, "FINAL-ERROR-MESSAGE");
        nogoNoIncidents = nogo.get(0);
        nogoNoCars = t.assignedAll("VHYS08IS-SELECT", PKG, "FINAL-ERROR-MESSAGE").get(0);
        summaryFull = t.assignedAll("VHYS09IS-500", PKG, "MSG").get(0);
        summaryNoCars = t.assignedAll("VHYS09IS-NOCARS", PKG, "MSG").get(0);
        summaryRows = Integer.parseInt(t.compared("VHYS09IS-RET-DATA", PKG, "CAR-COUNT").get(0));
        headers(t, "VHYS09IS-RET-DATA", summaryHeaders);
        headers(t, "VHYS10IS-INIT", typeHeaders);
        typeRowNames = new TreeMap<>(t.indexed("VHYS10IS-INIT", PKG, "CAR-TYPE-DATA"));
        typeRowNames.putAll(t.indexed("VHYS10IS-TYPE-CHK", PKG, "CAR-TYPE-DATA"));          // names TYPE-CHK sets again
        typeFormulas = t.cells("VHYS10IS-TYPE-CHK", PKG, "CAR-");
        typeRowCount = t.assignedNumber("VHYS10IS-INIT", PKG, "ROW-COUNT").intValue();
        for (EglTexts.Column col : t.tableColumns("VHYS10I", "cntCarsSummary")) {
            String f = col.field().toUpperCase(Locale.ROOT);
            if (!f.startsWith("CAR-") || !f.endsWith("-DATA") || f.equals("CAR-TYPE-DATA")) continue;
            String type = f.substring(4, f.length() - 5);
            if (!type.equals("TOTAL")) types.add(type);
            typeHeadings.add(col.heading());
        }
    }

    /** the header each yard gets: the yards a statement group compares YARD with, and the HEADER it sets under each */
    private static void headers(EglTexts t, String function, Map<String, String> into) {
        List<String> yards = t.compared(function, PKG, "YARD"), names = t.assignedUnder(function, PKG, "HEADER", "YARD");
        for (int i = 0; i < yards.size(); i++) into.put(yards.get(i), names.get(i));
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

    private Checked option(Criteria c, Option... allowed) {
        Checked k = check(c);
        for (Option o : allowed) if (k.option() == o) return k;
        throw new InvalidRequestException("This list is for the " + List.of(allowed) + " option.");
    }

    private static String shown(Map<String, Object> st, String stat, String filler, String ind) {
        return String.format("%-6s%-1s%-2s", text(st, stat), text(st, filler), text(st, ind)).stripTrailing();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> group(Map<String, Object> row, String key) {
        return row.get(key) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    }

    // ---- VHYS07I: Base / Status ------------------------------------------------------------------------

    /** {@code narrativeYard}: the yard VHYS07B is asked with for this car's narratives (its location) */
    public record Car(String car, String status, String location, String ato, long hours, long minutes, long mtbi, String homeYard,
                      String narrativeYard) {}

    public record Cars(Checked criteria, List<Car> cars, String message) {}

    private Map<String, Object> carsCrit(Checked k, String retInd, String car) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yard", k.yard());
        crit.put("vehStat", k.status());
        crit.put("carNo", car);
        crit.put("retInd", retInd);
        crit.put("reportOption", k.option().number());
        crit.put("carNoFrom", k.carFrom());
        crit.put("carNoTo", k.carTo());
        return crit;
    }

    /** VHYS07IS-RET-DATA (program VHYS07A, RET-IND "CAR") */
    public Cars cars(Criteria c) {
        Checked k = option(c, Option.BASE, Option.STATUS);
        ProgramResult r = programs.attempt("VHYS07A", Map.of("VHYS07A_CRIT", carsCrit(k, "CAR", ""))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 100) return new Cars(k, List.of(), noCars);
        if (urc != 0 && urc != 4) return new Cars(k, List.of(), tooMany);
        List<Car> cars = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS07A_CAR_DATA", "dataRow")) {
            Map<String, Object> st = group(row, "dispVehStat");
            cars.add(new Car(text(row, "carNo"), shown(st, "vehStat", "vehStatFiller", "incdInd"), text(row, "currLoc"), text(row, "atoPresentInd"),
                    number(row, "cumHrs"), number(row, "cumMin"), number(row, "mtbi"), text(row, "homeYrd"), text(row, "currLoc")));
        }
        return new Cars(k, cars, null);
    }

    public record Incident(String incident, String symptom, String location, String date, String time, String status, String direction) {}

    public record Incidents(String car, List<Incident> incidents, String message) {}

    /** VHYS07IS-INCD-DET (program VHYS07A, RET-IND "INCDS") */
    public Incidents incidents(Criteria c, String car) {
        Checked k = option(c, Option.BASE, Option.STATUS);
        String carNo = car == null ? "" : car.strip();
        ProgramResult r = programs.attempt("VHYS07A", Map.of("VHYS07A_CRIT", carsCrit(k, "INCDS", carNo))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode(), n = r.control() == null ? 0 : r.control().rowCount();
        if (n == 0 && urc == 100) return new Incidents(carNo, List.of(), noIncidents);
        if (n == 0 && urc != 0 && urc != 4) throw programs.error("VHYS07A", r.control());
        List<Incident> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS07A_INCD_DATA", "dataRow")) {
            Map<String, Object> st = group(row, "dispVehIncdStat");
            String status = String.format("%-3s%-1s%s", text(st, "vehIncdStat"), text(st, "vehIncdStatFiller"), text(st, "vehIncdStatDesc")).stripTrailing();
            out.add(new Incident(text(row, "incdNo"), text(row, "sympCd"), text(row, "incdLoc"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"),
                    status, text(row, "conDirection")));
        }
        return new Incidents(carNo, out, null);
    }

    /** VHYS07IS-SELECT / VHYS08IS-SELECT (program VHYS07B): an incident's narrative lines */
    public List<String> narrative(String car, String incident, String yard) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("yard", yard == null ? "" : yard.strip());
        crit.put("incdNo", incident == null ? "" : incident.strip());
        crit.put("carNo", car == null ? "" : car.strip());
        ProgramResult r = programs.run("VHYS07B", Map.of("VHYS07B_CRIT", crit));
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS07B_NARR_DATA", "dataRow")) lines.add(text(row, "narr"));
        return lines;
    }

    // ---- VHYS08I: NOGO ---------------------------------------------------------------------------------

    public record NogoIncident(String car, String incidentCar, String status, String homeYard, String incident, String incidentLocation,
                               String date, String time, String direction) {}

    public record Nogo(Checked criteria, int cars, List<NogoIncident> incidents, String message) {}

    /** VHYS08IS-RET-DATA: the NOGO cars (VHYS08A), then each car's incidents (VHYS08B, adding to one data record) */
    public Nogo nogo(Criteria c) {
        Checked k = option(c, Option.NOGO);
        Map<String, Object> critA = new LinkedHashMap<>();
        critA.put("yard", k.yard());
        critA.put("reportOption", k.option().number());
        critA.put("carNoFrom", k.carFrom());
        critA.put("carNoTo", k.carTo());
        critA.put("carNo", "");
        ProgramResult a = programs.attempt("VHYS08A", Map.of("VHYS08A_CRIT", critA)).result();
        int urc = a.control() == null ? 0 : a.control().returnCode();
        // no car read: RET-DATA's own message is replaced by SELECT's, which then finds no car
        if (urc != 0 && urc != 4) return new Nogo(k, 0, List.of(), nogoNoCars);
        int carCount = a.control() == null ? 0 : a.control().rowCount();
        List<Map<String, Object>> cars = rows(a, "VHYS08A_DATA", "carRow");
        Map<String, Object> data = new LinkedHashMap<>();
        int rowCnt = 0, incidents = 0;
        for (int i = 0; i < Math.min(carCount, cars.size()); i++) {
            Map<String, Object> car = cars.get(i);
            Map<String, Object> critB = new LinkedHashMap<>();
            critB.put("carNo", text(car, "carNo"));
            critB.put("incdInd", text(car, "incdInd"));
            critB.put("currLoc", "");
            critB.put("homeYrd", text(car, "homeYrd"));
            critB.put("vehStat", text(car, "vehStat"));
            critB.put("vehStatFiller", text(car, "vehStatFiller"));
            critB.put("rowCnt", rowCnt);
            ProgramResult b = programs.attempt("VHYS08B", Map.of("VHYS08B_CRIT", critB, "VHYS08B_DATA", data)).result();
            Map<String, Object> backCrit = b.record("VHYS08B_CRIT"), backData = b.record("VHYS08B_DATA");
            if (backCrit != null) rowCnt = (int) number(backCrit, "rowCnt");
            if (backData != null) data = new LinkedHashMap<>(backData);
            int u = b.control() == null ? 0 : b.control().returnCode();
            if (u == 0 || u == 4) incidents = rowCnt;
        }
        List<NogoIncident> out = new ArrayList<>();
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = data.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (int i = 0; i < Math.min(incidents, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            out.add(new NogoIncident(text(row, "carNo"), text(row, "storeCarNo"), shown(group(row, "dispVehStat"), "vehStat", "vehStatFiller", "incdInd"),
                    text(row, "homeYrd"), text(row, "incdNo"), text(row, "incdLoc"), text(row, "dateIncdOccr"), text(row, "timeIncdOccr"), text(row, "conDirection")));
        }
        String message = carCount == 0 ? nogoNoCars : incidents == 0 ? nogoNoIncidents : null;
        return new Nogo(k, carCount, out, message);
    }

    // ---- VHYS09I / VHYS10I: Summary --------------------------------------------------------------------

    public record SummaryCar(String car, boolean atoMissing, String status, long mtbi, String homeYard) {}

    public record Summary(Checked criteria, String header, List<SummaryCar> cars, String message) {}

    /** VHYS09IS-RET-DATA (program VHYS09A); the header is the yard's name, or its code when it has none */
    public Summary summary(Criteria c) {
        Checked k = option(c, Option.SUMMARY);
        ProgramResult r = programs.attempt("VHYS09A", Map.of("VHYS09A_CRIT", Map.of("yard", k.yard()))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String header = summaryHeaders.getOrDefault(k.yard(), k.yard());
        if (urc != 0 && urc != 4) return new Summary(k, header, List.of(), summaryNoCars);
        int count = r.control() == null ? 0 : r.control().rowCount();
        List<SummaryCar> cars = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHYS09A_DATA", "dataRow")) {
            for (String n : new String[] {"", "1"}) {
                Map<String, Object> no = group(row, "dispCarNo" + n), st = group(row, "dispVehStat" + n);
                String car = text(no, "carNo" + n);
                if (car.isEmpty()) continue;
                cars.add(new SummaryCar(car, !text(no, "carNo" + n + "Star").isEmpty(), shown(st, "vehStat" + n, "vehStat" + n + "Filler", "incdInd" + n),
                        number(row, "mtbi" + n), text(row, "homeYrd" + n)));
            }
        }
        return new Summary(k, header, cars, count == summaryRows ? summaryFull : null);
    }

    public record TypeRow(int row, String name, List<Integer> byType, int total) {}

    public record TypeSummary(String yard, String header, List<String> types, List<String> headings, List<TypeRow> rows, String message) {}

    /** the Summary button: VHYS10I (program VHYS10A), the yard's cars counted by type with VHYS10I's own cell formulas */
    public TypeSummary typeSummary(String yard) {
        String y = yard == null ? "" : yard.strip();
        ProgramResult r = programs.attempt("VHYS10A", Map.of("VHYS10A_CRIT", Map.of("yard", y))).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String header = typeHeaders.getOrDefault(y, "");
        if (urc == 100) return new TypeSummary(y, header, types, typeHeadings, List.of(), null);
        if (urc != 0 && urc != 4) return new TypeSummary(y, header, types, typeHeadings, List.of(), programs.error("VHYS10A", r.control()).getMessage());
        VehicleQueriesTypeSummary sum = new VehicleQueriesTypeSummary(types, typeFormulas);
        int n = r.control() == null ? 0 : r.control().rowCount();
        List<Map<String, Object>> cars = rows(r, "VHYS10A_DATA", "dataRow");
        for (int i = 0; i < Math.min(n, cars.size()); i++) sum.count(cars.get(i));
        List<TypeRow> out = new ArrayList<>();
        List<VehicleQueriesTypeSummary.Cells> cells = sum.rows(typeRowCount);
        for (int i = 0; i < cells.size(); i++) out.add(new TypeRow(i + 1, typeRowNames.getOrDefault(i + 1, ""), cells.get(i).byType(), cells.get(i).total()));
        return new TypeSummary(y, header, types, typeHeadings, out, null);
    }
}
