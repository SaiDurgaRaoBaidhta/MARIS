package com.bart.maris.middleware.domain.incidentevents;

import static com.bart.maris.middleware.domain.common.Programs.number;
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
 * Events and incidents of one symptom code over the last N days (old screen VHIE07I, opened by VHIE03I's Search). Rules
 * from VHIE07IS-BEGIN (90 days), VHIE07IS-LOOKUP (VHIE07C the date range, VHIE07A the events, VHIE07B the incidents) and
 * VHIE07IS-MERGE (events first, then incidents). Read-only.
 */
public class IncidentEventsSymptomHistoryService {
    private static final String PKG = "MRSVehicleIncdEvent";

    private final Programs programs;
    private final int defaultDays;
    private final String dateError, noEvents, noIncidents, eventType, incidentType;

    public IncidentEventsSymptomHistoryService(LegacyProgramService legacy, MessageResolver messages, EglTexts t) {
        this.programs = new Programs(legacy, messages);
        this.defaultDays = t.assignedNumber("VHIE07IS-BEGIN", PKG, "DAYS").intValue();
        this.dateError = t.assigned("VHIE07IS-LOOKUP", PKG, "MSG", "Date File");
        this.noEvents = t.assigned("VHIE07IS-LOOKUP", PKG, "MSG", "No Event");
        this.noIncidents = t.assigned("VHIE07IS-LOOKUP", PKG, "MSG", "INCIDENTS");
        List<String> types = t.assignedAll("VHIE07IS-MERGE", PKG, "FILE-TYPE");          // "E" for the events, then "I"
        this.eventType = types.get(0);
        this.incidentType = types.get(1);
    }

    public int defaultDays() {
        return defaultDays;
    }

    /** one row: E = event, I = incident; the date in service as MM-DD-YYYY, the consist as CCC-DD */
    public record Row(String type, String car, String symptom, String dateInService, String timeInService, String consist, String incident,
                      List<String> cars, String narrative) {}

    /** @param message the old screen's message (the last one it set), otherwise null */
    public record History(String symptom, int days, String from, String to, int events, int incidents, List<Row> rows, String message) {}

    public History history(String symptom, Integer days) {
        String s = symptom == null ? "" : symptom.strip();
        if (s.isEmpty() || s.length() > 4) throw new InvalidRequestException("Enter a symptom code of up to 4 characters.");
        int d = days == null ? defaultDays : days;
        if (d < 0 || d > 999) throw new InvalidRequestException("Enter the number of days, 0 to 999.");
        String message = null;
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("numDays", d);
        ProgramResult cr = programs.run("VHIE07C", Map.of("VHIE07C_CRIT", c));
        if (cr.control() != null && cr.control().returnCode() == 100) message = dateError;
        Map<String, Object> range = cr.record("VHIE07C_DATA");
        String end = text(range, "currentDate"), begin = text(range, "beginDate");

        Map<String, Object> ca = new LinkedHashMap<>();
        ca.put("sympCd", s);
        ca.put("endDate", end);
        ca.put("beginDate", begin);
        ProgramResult ar = programs.run("VHIE07A", Map.of("VHIE07A_CRIT", ca));
        int events = 0;
        if (ar.control() != null && ar.control().returnCode() == 100) message = noEvents;
        else events = (int) number(ar.record("VHIE07A_CRIT"), "recCount");

        Map<String, Object> cb = new LinkedHashMap<>(ca);
        ProgramResult br = programs.run("VHIE07B", Map.of("VHIE07B_CRIT", cb));
        int incidents = 0;
        if (br.control() != null && br.control().returnCode() == 100) message = noIncidents;
        else incidents = (int) number(br.record("VHIE07B_CRIT"), "recCounter");

        List<Map<String, Object>> ev = all(ar, "VHIE07A_DATA"), in = all(br, "VHIE07B_DATA");
        List<Row> rows = new ArrayList<>();
        List<String> dash = new ArrayList<>();                                    // DASH-CHAR2 of every merged row so far
        for (int i = 0; i < Math.min(events, ev.size()); i++) {
            Map<String, Object> r = ev.get(i);
            dash.add(dashChar2(r));
            rows.add(row(eventType, r, dash.get(i)));                             // CON-DASH-NO[RCT] = DASH-CHAR2[ICT], ICT = RCT here
        }
        for (int i = 0; i < Math.min(incidents, in.size()); i++) {
            Map<String, Object> r = in.get(i);
            dash.add(dashChar2(r));
            rows.add(row(incidentType, r, dash.get(i)));                          // the old screen reads row ICT (restarted at 1), not RCT
        }
        return new History(s, d, begin, end, events, incidents, rows, message);
    }

    private static Row row(String type, Map<String, Object> r, String dashNo) {
        List<String> cars = new ArrayList<>();
        for (int k = 1; k <= 10; k++) cars.add(text(r, "carNo" + k));
        String con = text(r, "conNo");
        String consist = (String.format("%-3s", con) + "-" + dashNo).strip();
        return new Row(type, text(r, "carNo"), text(r, "sympCd"), IncidentEventsDates.mdy(text(r, "dateInsrv")), text(r, "timeInsrv"), consist,
                text(r, "incdNo"), cars, text(r, "narr"));
    }

    /** DASH-NO is NUM(4) on the screen; DASH-CHAR2 is its last two digits */
    private static String dashChar2(Map<String, Object> r) {
        String four = String.format("%04d", Math.abs(number(r, "dashNo")) % 10000);
        return four.substring(2);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> all(ProgramResult r, String record) {
        Map<String, Object> d = r.record(record);
        return d != null && d.get("dataRow") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
