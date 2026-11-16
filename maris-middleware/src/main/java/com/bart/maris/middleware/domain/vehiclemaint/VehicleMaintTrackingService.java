package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle parts status / mod tracking (old VHVM11I): the tracking events of cars (awaiting parts, battery, mods ...),
 * listed by criteria (VHVM11A), added (VHVM11B), updated, closed and removed (VHVM11C, a battery close also updates the
 * car through VHVM11J), and a work-assign incident created for an event (VHVM11K). VHCI02I and VHCI03I open it with a
 * car's events preselected. Every text, default and fixed value is the screen's own, read from the EGL.
 */
public final class VehicleMaintTrackingService {
    private static final String PKG = "MRSVehicleMaintenance";
    private static final String SCREEN = "VHVM11I";

    private final Programs programs;
    private final String terminal;
    private final EglTexts t;
    private final String all, battery, awp, mod, open, closed, incidentPrefix, yes, no, defaultDateFrom, modDateFrom, allDsc, linkedStatus;
    private final List<String> batteryCarTypes, fullGroups, fullFrom;
    private final String begAll, endAll, sortDefault, modSort;
    private final String msgCarRequired, msgCarUnknown, msgAddMissing, msgAdded, msgMaxRows, msgNoData, msgLinkedMax, msgLinkedNoData, msgOpenWithDate,
            msgEssential, msgUpdated, msgChangeType, msgRemoved, msgClosed, msgClosedBattery, msgMonth, msgDay, msgYear, msgFuture, msgNeedClosedDate,
            msgNeedLocation, msgIncidentCreated, msgWorkAssignMissing;
    private final String askBatteryUpdate, askBatteryClose, askClose, askRemove;
    private final String removedDate, removedTime, kStatus, kType, kNarr;

    public VehicleMaintTrackingService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle bundle, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.terminal = terminal;
        this.t = t;
        all = t.compared("VHVM11I-GET-DATA", PKG, "HOME-YRD").get(0);
        battery = t.compared("VHVM11IW-PRE-CLOSE", PKG, "EVENT-TYPE").get(0);
        List<String> types = t.compared("VHVM11IW-FROM-CI03", PKG, "EVENT-TYPE");             // AWP, BAT, MOD
        awp = types.get(0);
        mod = types.get(types.size() - 1);
        List<String> stats = t.compared("VHVM11I-UP_CLOSE", PKG, "EVENT-STAT");                 // OPEN, CLOSED
        open = stats.get(0);
        closed = stats.get(1);
        incidentPrefix = t.compared("VHVM11IS-GEN-INCD", PKG, "INCD-NO-PREFIX").get(0);
        List<String> flags = t.assignedAll("VHVM11I-REMOVE", PKG, "I-INCIDENT-FLAG");             // Y, N
        yes = flags.get(0);
        no = flags.get(1);
        defaultDateFrom = t.assignedAll("VHVM11I-BEGIN", PKG, "DATE-FROM").get(0);
        modDateFrom = t.assignedAll("VHVM11I-GET-DATA3", PKG, "DATE-MM-DD-YYYY").get(0);
        allDsc = t.assignedRaw("VHVM11I-BEGIN", PKG, "EVENT-SUB-TYPE-DSC").get(0);
        linkedStatus = t.assignedRaw("VHVM11IW-FROM-CI05", PKG, "EVENT-STAT").get(0);
        batteryCarTypes = t.compared("VHIM11IS-BAT", PKG, "CAR-TYPE");
        fullGroups = t.compared("VHVM11I-BEGIN", PKG, "USER-GROUP");
        fullFrom = t.compared("VHVM11I-BEGIN", PKG, "PASS-FROM-GUI");
        begAll = t.assignedAll("VHVM11I-GET-DATA", PKG, "BEG-CAR-TYPE").get(0);
        endAll = t.assignedAll("VHVM11I-GET-DATA", PKG, "END-CAR-TYPE").get(0);
        sortDefault = openingIndex(bundle);
        modSort = t.assignedNumber("VHVM11I-GET-DATA3", PKG, "SORT-ORDER").toPlainString();
        msgCarRequired = msg("VHVM11I-VALID-CAR", "Required");
        msgCarUnknown = msg("VHVM11I-VALID-CAR", "Not Exist");
        msgAddMissing = msg("VHVM11I-ADD", "Missing");
        msgAdded = msg("VHVM11I-ADD", "Added");
        msgMaxRows = msg("VHVM11I-GET-DATA", "MAX ROWS");
        msgNoData = msg("VHVM11I-GET-DATA", "No data");
        msgLinkedMax = msg("VHVM11IW-FROM-CI05", "MAX ROWS");
        msgLinkedNoData = msg("VHVM11IW-FROM-CI05", "NO DATA");
        msgOpenWithDate = msg("VHVM11I-UP_CLOSE", null);
        msgEssential = msg("VHVM11I-UPDATE", "Esential");
        msgUpdated = msg("VHVM11I-UPDATE", "UPDATED");
        msgChangeType = msg("VHVM11I-UPDATE", "Cannot Change");
        msgRemoved = msg("VHVM11I-REMOVE", "REMOVED");
        msgClosed = msg("VHVM11I-CLOSED", "AWP");
        msgClosedBattery = msg("VHVM11I-CLOSED", "Vehicle Record");
        msgMonth = msg("VHVM11I-VALID-DATE", "Month");
        msgDay = msg("VHVM11I-VALID-DATE", "DAY");
        msgYear = msg("VHVM11I-VALID-DATE", "Year");
        msgFuture = msg("VHVM11I-VALID-DATE", "Grater");
        msgNeedClosedDate = msg("VHVM11I-VALID-DATE", "CLOSED DATE");
        msgNeedLocation = msg("VHVM11IW-PRE-CLOSE", "Location");
        msgIncidentCreated = t.assigned("VHVM11IS-GEN-INCD", PKG, "MSG2A", null);
        msgWorkAssignMissing = msg("VHVM11IS-GEN-INCD", "Missing");
        askBatteryUpdate = t.prompt(SCREEN, "Message_Prompter3");
        askBatteryClose = t.prompt(SCREEN, "Message_Prompter31");
        askClose = t.prompt(SCREEN, "Message_Prompter2");
        askRemove = t.prompt(SCREEN, "Message_Prompter1");
        removedDate = t.assigned("VHVM11I-ADD", PKG, "DATE-REMOVED", null);
        removedTime = t.assigned("VHVM11I-ADD", PKG, "TIME-REMOVED", null);
        kStatus = t.assigned("VHVM11IS-GEN-INCD", PKG, "VEH-INCD-STAT", null);
        kType = t.assigned("VHVM11IS-GEN-INCD", PKG, "INCD-TYPE", null);
        kNarr = t.assigned("VHVM11IS-GEN-INCD", PKG, "NARR", null);
    }

    private String msg(String function, String containing) {
        return t.assigned(function, PKG, "MSG", containing);
    }

    /** the sort option the screen selects when it opens (aboutToOpenWidget -> Radio_Button_Set1 selectionIndex, constant) */
    private static String openingIndex(ScreenBundle bundle) {
        for (JsonNode c : bundle.screen(SCREEN).path("connections")) {
            JsonNode a = c.path("a");
            if (a.path(1).asText().equals("aboutToOpenWidget") && a.path(2).path("id").asText().equals("screen.Radio_Button_Set1")) {
                for (JsonNode p : a.path(4)) if (p.path("args").path(0).asText().equals("value")) return p.path("args").path(1).asText();
            }
        }
        throw new IllegalStateException(SCREEN + " selects no sort order when it opens");
    }

    // ---------------------------------------------------------------- options (VHVM11I-BEGIN)

    /** an event type / sub type choice; label is the drop-down text */
    public record EventChoice(String type, String subType, String label) {}

    public record Options(String runDate, String dateFrom, String dateTo, List<EventChoice> searchEvents, List<EventChoice> entryEvents, List<String> statuses,
                          List<String> entryStatuses, List<String> locations, List<String> carTypes, List<String> homeYards, List<String> batteryLocations,
                          List<String> sortOrders, String defaultSortOrder, boolean queryOnly, Map<String, String> questions) {}

    private record Events(List<EventChoice> search, List<EventChoice> entry) {}

    public Options options(String userGroup, String openedFrom) {
        String runDate = today().date();
        Events ev = events();
        ProgramResult f = programs.attempt("VHVM11F", Map.of()).result();
        String dateFrom = defaultDateFrom;
        List<String> statuses = new ArrayList<>(), entryStatuses = new ArrayList<>();
        if (ok(f)) {
            String begin = text(f.record("VHVM11F_CRIT"), "beginDate");
            if (!begin.isBlank()) dateFrom = begin;
            List<Map<String, Object>> rs = rows(f, "VHVM11F_DATA", "dataRow");
            for (int i = 0; i < rs.size(); i++) {
                String s = text(rs.get(i), "eventStat");
                if (!s.isBlank()) statuses.add(s);
                if (i >= 1 && i <= 20 && !s.isBlank()) entryStatuses.add(s);
            }
        }
        List<String> locations = new ArrayList<>();
        ProgramResult g = programs.attempt("VHVM11G", Map.of()).result();
        if (ok(g)) for (Map<String, Object> r : rows(g, "VHVM11G_DATA", "dataRow")) if (!text(r, "yrd").isBlank()) locations.add(text(r, "yrd"));
        boolean full = fullGroups.contains(userGroup == null ? "" : userGroup.strip()) || (openedFrom != null && fullFrom.contains(openedFrom.strip()));
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("batteryUpdate", askBatteryUpdate);
        questions.put("batteryClose", askBatteryClose);
        questions.put("close", askClose);
        questions.put("remove", askRemove);
        return new Options(runDate, dateFrom, runDate, ev.search(), ev.entry(), statuses, entryStatuses, locations, t.items(SCREEN, "Drop-down_List3"),
                t.items(SCREEN, "Drop-down_List4"), t.items(SCREEN, "Drop-down_List6"), t.items(SCREEN, "Radio_Button_Set1"), sortDefault, !full, questions);
    }

    private static boolean ok(ProgramResult r) {
        int urc = r.control() == null ? 0 : r.control().returnCode();
        return urc == 0 || urc == 4;
    }

    private record Today(String date, String date8) {}

    /** VHVMDTA: the backend's date (MM-DD-YYYY and YYYYMMDD) */
    private Today today() {
        ProgramResult d = programs.run("VHVMDTA", Map.of());
        if (d.control() == null || d.control().rowCount() != 1) throw programs.error("VHVMDTA", d.control());
        String date = loc(d.record("VHVMDTA_CRIT").get("currentDate"));
        String p = pad(date, 10);
        return new Today(date, p.substring(6, 10) + p.substring(0, 2) + p.substring(3, 5));
    }

    /** VHVM11E: the search list (rows 1 and 2 as they are, then every row with a type) and the entry list (from the third on) */
    private Events events() {
        ProgramResult e = programs.attempt("VHVM11E", Map.of()).result();
        List<EventChoice> search = new ArrayList<>(), entry = new ArrayList<>();
        if (!ok(e)) return new Events(search, entry);
        List<Map<String, Object>> rs = rows(e, "VHVM11E_DATA", "dataRow");
        int n = rs.size();
        String[] type = new String[31], sub = new String[31], dsc = new String[31];
        if (n >= 1) type[1] = text(rs.get(0), "eventType");
        if (n >= 2) { type[2] = text(rs.get(1), "eventType"); dsc[2] = allDsc; }
        int j = 3;
        for (int i = 3; i <= n && j <= 30; i++) {
            Map<String, Object> r = rs.get(i - 1);
            if (text(r, "eventType").isBlank()) continue;
            type[j] = text(r, "eventType");
            sub[j] = text(r, "eventSubType");
            Object d = r.get("eventSubTypeDsc");
            dsc[j] = d == null ? "" : d.toString();
            j++;
        }
        for (int i = 1; i <= 30; i++) if (type[i] != null && !type[i].isBlank()) search.add(choice(type[i], sub[i], dsc[i]));
        for (int i = 3, k = 1; i <= n && k <= 15; i++, k++) if (i <= 30 && type[i] != null && !type[i].isBlank()) entry.add(choice(type[i], sub[i], dsc[i]));
        return new Events(search, entry);
    }

    /** DATA_ROW: EVENT-TYPE(3) EVENT-DASH(1) EVENT-SUB-TYPE(1) EVENT-BLANK(1) EVENT-SUB-TYPE-DSC(20) */
    private static EventChoice choice(String type, String sub, String dsc) {
        String s = sub == null ? "" : sub;
        String label = pad(type, 3) + (s.isBlank() ? " " : "-") + pad(s, 1) + " " + cut(dsc == null ? "" : dsc, 20);
        return new EventChoice(type, s, label.stripTrailing());
    }

    // ---------------------------------------------------------------- list (VHVM11I-GET-DATA)

    public record Event(String car, String eventType, String eventSubType, String status, String dateInitialized, String timeInitialized,
                        String trackingRefNo, String dateClosed, String timeClosed, String vehicleStatus, String dateLastUpdated, String homeYard,
                        String incident, String comment, String location, String locationCompleted, String active) {}

    public record Criteria(String dateFrom, String dateTo, String car, String carType, String homeYard, String eventType, String eventSubType,
                           String status, String location, String trackingRefNo, String sortOrder) {}

    public record Listing(List<Event> events, String message) {}

    public Listing list(Criteria c) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateFrom", ymd(c.dateFrom()));
        crit.put("dateTo", ymd(c.dateTo()));
        String car = s(c.car());
        crit.put("carNo", car.equals(all) ? "" : car);
        String carType = s(c.carType());
        boolean anyType = carType.equals(all) || carType.isBlank();
        crit.put("begCarType", anyType ? begAll : carType);
        crit.put("endCarType", anyType ? endAll : carType);
        crit.put("homeYrd", s(c.homeYard()).equals(all) ? "" : s(c.homeYard()));
        crit.put("eventType", s(c.eventType()).equals(all) ? "" : s(c.eventType()));
        crit.put("eventStat", s(c.status()).equals(all) ? "" : s(c.status()));
        crit.put("currLoc", s(c.location()).equals(all) ? "" : s(c.location()));
        crit.put("eventSubType", s(c.eventSubType()));
        crit.put("trkgRefNo", stockNo(c.trackingRefNo()));
        crit.put("sortOrder", c.sortOrder() == null || c.sortOrder().isBlank() ? sortDefault : c.sortOrder().strip());
        return listing(crit, msgMaxRows, msgNoData);
    }

    private Listing listing(Map<String, Object> crit, String maxMsg, String noDataMsg) {
        Programs.Outcome o = programs.attempt("VHVM11A", Map.of("VHVM11A_CRIT", crit));
        if (!o.ok()) return new Listing(List.of(), noDataMsg);
        ProgramResult r = o.result();
        List<Event> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHVM11A_DATA", "dataRow")) out.add(event(row));
        String m = yes.equals(text(r.record("VHVM11A_CRIT"), "maxRowsExceeded")) ? maxMsg : "";
        return new Listing(out, m);
    }

    private static Event event(Map<String, Object> r) {
        return new Event(text(r, "carNo"), text(r, "eventType"), text(r, "eventSubType"), text(r, "eventStat"), text(r, "dateInitialized"),
                text(r, "timeInitialized"), text(r, "trkgRefNo"), text(r, "dateClosed"), text(r, "timeClosed"), text(r, "vehStat"), text(r, "dateLastUpd"),
                text(r, "homeYrd"), text(r, "incdNo"), text(r, "eventComment"), loc(r.get("loc")), text(r, "locCmpltEvent"), text(r, "actvInd"));
    }

    /** a field as its text, a group field as its parts put together */
    @SuppressWarnings("unchecked")
    private static String loc(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> x = (Map<String, Object>) m;
            StringBuilder b = new StringBuilder();
            for (Object o : x.values()) b.append(o == null ? "" : o.toString());
            return b.toString().strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    /** the stock / mod number as typed: 99-99-99999 loses its dashes, anything else is its first 9 characters */
    static String stockNo(String in) {
        String p = pad(in, 11);
        if (p.charAt(2) == '-' && p.charAt(5) == '-') return p.substring(0, 2) + p.substring(3, 5) + p.substring(6, 9) + p.substring(9, 11);
        return p.substring(0, 9).stripTrailing();
    }

    // ---------------------------------------------------------------- linked opening (VHCI02I / VHCI03I)

    /** what VHCI02I / VHCI03I pass (VHVM11IW-PASS-DATA) */
    public record Pass(String from, String car, String incident, String status, String eventType, String dateInitialized) {}

    /** the form the screen fills, and whether it opens on an event to update (Update enabled) or a new one to add */
    public record Linked(List<Event> events, String message, Form form, boolean editing) {}

    public record Form(String car, String eventType, String eventSubType, String status, String dateInitialized, String timeInitialized, String trackingRefNo,
                       String dateClosed, String incident, String comment, String location, String homeYard, String dateLastUpdated, String locationCompleted) {}

    public Linked linked(Pass p, String dateFrom) {
        String runDate = today().date();
        String from = dateFrom == null || dateFrom.isBlank() ? beginDate() : dateFrom;
        String type = s(p.eventType());
        if (fullFrom.size() > 1 && s(p.from()).equals(fullFrom.get(0))) {                         // VHCI02: FROM-CI05
            Listing l = listing(linkedCrit(from, runDate, s(p.car()), "", linkedStatus, defaultSort()), msgLinkedMax, msgLinkedNoData);
            return new Linked(l.events(), l.message(), null, false);
        }
        if (type.equals(mod)) {                                                                     // GET-DATA3
            Listing l = listing(linkedCrit(modDateFrom, runDate, s(p.car()), mod, linkedStatus, modSort), msgLinkedMax, msgLinkedNoData);
            for (Event e : l.events()) {
                if (e.incident().isBlank()) {
                    return new Linked(l.events(), l.message(), new Form(e.car(), e.eventType(), e.eventSubType(), e.status(), e.dateInitialized(), e.timeInitialized(),
                            e.trackingRefNo(), e.dateClosed(), s(p.incident()), e.comment(), e.location(), e.homeYard(), e.dateLastUpdated(), e.locationCompleted()), true);
                }
            }
            return new Linked(l.events(), l.message(), newForm(p, runDate, open), false);
        }
        if (type.equals(awp) || type.equals(battery) || type.isBlank()) {                           // GET-DATA2
            Listing l = listing(linkedCrit(from, runDate, s(p.car()), type, linkedStatus, defaultSort()), msgLinkedMax, msgLinkedNoData);
            return new Linked(l.events(), l.message(), newForm(p, runDate, s(p.status())), false);
        }
        return new Linked(List.of(), "", null, false);
    }

    private String defaultSort() {
        return sortDefault;
    }

    private Form newForm(Pass p, String runDate, String status) {
        String init = s(p.dateInitialized()).isBlank() ? runDate : s(p.dateInitialized());
        return new Form(s(p.car()), s(p.eventType()), "", status, init, "", "", "", s(p.incident()), "", "", "", "", "");
    }

    private Map<String, Object> linkedCrit(String from, String to, String car, String type, String status, String sort) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateFrom", ymd(from));
        crit.put("dateTo", ymd(to));
        crit.put("carNo", car);
        crit.put("begCarType", begAll);
        crit.put("endCarType", endAll);
        crit.put("homeYrd", "");
        crit.put("eventType", type);
        crit.put("eventStat", status);
        crit.put("currLoc", "");
        crit.put("trkgRefNo", "");
        crit.put("sortOrder", sort);
        return crit;
    }

    private String beginDate() {
        ProgramResult f = programs.attempt("VHVM11F", Map.of()).result();
        String begin = ok(f) ? text(f.record("VHVM11F_CRIT"), "beginDate") : "";
        return begin.isBlank() ? defaultDateFrom : begin;
    }

    // ---------------------------------------------------------------- a selected event (VHVM11I-SELECT)

    /** what the screen shows for a selected event: VHVM11H's car type and battery switch, and the form defaults */
    public record Selection(String carType, String batteryReplaced, String status, String location, String entryEventType, String entryEventSubType,
                            boolean canWorkAssign, boolean battery) {}

    public Selection select(Event e) {
        Map<String, Object> h = carEvent(e);
        boolean bat = battery.equals(s(e.eventType()));
        EventChoice entry = entryFor(e);
        return new Selection(text(h, "carType"), text(h, "batteryReplSw"), bat ? closed : s(e.status()), bat ? s(e.homeYard()) : s(e.location()),
                entry == null ? "" : entry.type(), entry == null ? "" : entry.subType(), !bat && !s(e.incident()).startsWith(incidentPrefix), bat);
    }

    private Map<String, Object> carEvent(Event e) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateInitialized", ymd(e.dateInitialized()));
        crit.put("timeInitialized", s(e.timeInitialized()));
        crit.put("carNo", s(e.car()));
        Programs.Outcome o = programs.attempt("VHVM11H", Map.of("VHVM11H_CRIT", crit));
        return o.ok() ? o.result().record("VHVM11H_DATA") : Map.of();
    }

    /** the entry drop-down selection: the event's type and sub type among the first 10 entries, else the first entry */
    private EventChoice entryFor(Event e) {
        List<EventChoice> entry = events().entry();
        if (entry.isEmpty()) return null;
        EventChoice found = entry.get(0);
        for (int i = 0; i < Math.min(10, entry.size()); i++) {
            EventChoice c = entry.get(i);
            if (c.type().equals(s(e.eventType())) && c.subType().equals(s(e.eventSubType()))) found = c;
        }
        return found;
    }

    // ---------------------------------------------------------------- car check (VHVM11I-VALID-CAR)

    public String checkCar(String car) {
        String c = s(car);
        if (c.isBlank()) throw new InvalidRequestException(msgCarRequired);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("carNo", c);
        if (!programs.attempt("VHVM1A5", Map.of("VHVM1A5_DATA", d)).ok()) throw new InvalidRequestException(msgCarUnknown);
        return "";
    }

    // ---------------------------------------------------------------- add (VHVM11I-ADD)

    public record Entry(String car, String status, String eventType, String eventSubType, String trackingRefNo, String dateInitialized, String dateClosed,
                        String incident, String comment) {}

    public record Result(String message, String question, String incident) {
        static Result done(String m) { return new Result(m, null, null); }
        static Result ask(String q) { return new Result(null, q, null); }
    }

    public Result add(Entry e) {
        if (s(e.car()).isBlank() || s(e.status()).isBlank() || s(e.eventType()).isBlank() || s(e.trackingRefNo()).isBlank()) {
            throw new InvalidRequestException(msgAddMissing.strip());
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("actvInd", yes);
        d.put("carNo", s(e.car()));
        d.put("dateClosed", s(e.dateClosed()));
        d.put("dateInitialized", s(e.dateInitialized()));
        d.put("dateInitializedDb2", ymd(e.dateInitialized()));
        d.put("dateRemoved", removedDate);
        d.put("eventComment", s(e.comment()));
        d.put("eventStat", s(e.status()));
        Map<String, Object> ts = new LinkedHashMap<>();
        ts.put("eventType", s(e.eventType()));
        ts.put("eventDash", s(e.eventSubType()).isBlank() ? "" : "-");
        ts.put("eventSubType", s(e.eventSubType()));
        d.put("eventTypeSub", ts);
        d.put("incdNo", s(e.incident()));
        d.put("termLastUpd", terminal);
        d.put("timeRemoved", removedTime);
        d.put("trkgRefNo", s(e.trackingRefNo()));
        programs.run("VHVM11B", Map.of("VHVM11B_DATA", d));
        return Result.done(msgAdded);
    }

    // ---------------------------------------------------------------- update / close / remove / work assign

    /** the event selected in the list, the values the user changed in the form (null = unchanged), and an answer to a question asked */
    public record Change(Event event, String status, String dateInitialized, String trackingRefNo, String dateClosed, String car, String incident,
                         String comment, String location, String entryEventType, String entryEventSubType, Boolean answer) {}

    private record Sel(Event row, String car, String type, String subType, String status, String dateInit, String time, String trkg, String dateClosed,
                       String incident, String comment, String location, String locCmplt, String dateLastUpd, String homeYard) {}

    private static Sel sel(Change c) {
        Event e = c.event();
        if (e == null) throw new InvalidRequestException("An event is required.");
        return new Sel(e, or(c.car(), e.car()), s(e.eventType()), s(e.eventSubType()), or(c.status(), e.status()), or(c.dateInitialized(), e.dateInitialized()),
                s(e.timeInitialized()), or(c.trackingRefNo(), e.trackingRefNo()), or(c.dateClosed(), e.dateClosed()), or(c.incident(), e.incident()),
                or(c.comment(), e.comment()), or(c.location(), e.location()), s(e.locationCompleted()), s(e.dateLastUpdated()), s(e.homeYard()));
    }

    /** the Update button (VHVM11I-UP_CLOSE): an open event is updated, a closed one is closed */
    public Result update(Change c) {
        Sel x = sel(c);
        if (x.status().equals(open) && !x.dateClosed().isBlank()) throw new InvalidRequestException(msgOpenWithDate);
        if (x.status().equals(open)) {
            String sw = "";
            if (needsBatteryAnswer(x)) {
                if (c.answer() == null) return Result.ask(askBatteryUpdate);
                sw = c.answer() ? yes : "";
            }
            return doUpdate(c, x, sw, x.incident());
        }
        if (x.status().equals(closed)) {
            String sw = "";
            if (needsBatteryAnswer(x)) {
                if (c.answer() == null) return Result.ask(askBatteryClose);
                sw = c.answer() ? yes : "";
            }
            return preClose(x, sw, true);
        }
        return Result.done("");
    }

    /** the Close button (VHVM11IW-PRE-CLOSE): asks first when the status is not CLOSED */
    public Result close(Change c) {
        Sel x = sel(c);
        if (Boolean.FALSE.equals(c.answer())) return Result.done("");
        return preClose(x, "", c.answer() != null);
    }

    private boolean needsBatteryAnswer(Sel x) {
        if (!x.type().equals(battery)) return false;
        Map<String, Object> h = carEvent(x.row());
        return batteryCarTypes.contains(text(h, "carType")) && text(h, "batteryReplSw").isEmpty();
    }

    private Result doUpdate(Change c, Sel x, String sw, String incident) {
        String entryType = c.entryEventType(), entrySub = c.entryEventSubType();
        if (entryType == null) {
            EventChoice ch = entryFor(x.row());
            entryType = ch == null ? "" : ch.type();
            entrySub = ch == null ? "" : ch.subType();
        }
        if (!x.type().equals(s(entryType))) throw new InvalidRequestException(msgChangeType);
        if (x.car().isBlank() || x.status().isBlank() || x.type().isBlank() || x.trkg().isBlank()) throw new InvalidRequestException(msgEssential);
        Map<String, Object> crit = crit(x, sw);
        crit.put("updateFlag", yes);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("actvInd", yes);
        d.put("carNo", x.car());
        if (!x.dateClosed().isBlank()) d.put("dateClosed", ymd(x.dateClosed()));
        d.put("dateInitialized", ymd(x.dateInit()));
        d.put("dateLastUpd", x.dateLastUpd());
        d.put("eventComment", x.comment());
        d.put("eventStat", x.status());
        boolean isAwp = x.type().equals(awp);
        d.put("eventType", isAwp ? s(entryType) : x.type());
        d.put("eventSubType", isAwp ? s(entrySub) : x.subType());
        d.put("incdNo", incident);
        d.put("locCmpltEvent", x.locCmplt());
        d.put("termLastUpd", terminal);
        d.put("timeInitialized", x.time());
        d.put("trkgRefNo", x.trkg());
        programs.run("VHVM11C", Map.of("VHVM11C_CRIT", crit, "VHVM11C_DATA", d));
        return Result.done(msgUpdated);
    }

    private Map<String, Object> crit(Sel x, String sw) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("carNo", s(x.row().car()));
        crit.put("batteryReplSw", sw);
        return crit;
    }

    /** VHVM11IW-PRE-CLOSE: the closed date's checks, a battery's location, then close (asking when the status is not CLOSED) */
    private Result preClose(Sel x, String sw, boolean confirmed) {
        Today today = today();
        String closedDate = x.dateClosed();
        String error = null;
        if (!closedDate.isBlank()) {
            String[] v = validDate(closedDate, today.date8());
            error = v[1];
            if (error == null) closedDate = v[0];
        } else if (x.type().equals(battery)) {
            error = msgNeedClosedDate;
        }
        if (x.type().equals(battery) && x.location().isBlank()) error = msgNeedLocation;
        if (error != null) throw new InvalidRequestException(error.strip());
        if (!x.status().equals(closed) && !confirmed) return Result.ask(askClose);
        return doClose(x, sw, closedDate, today.date());
    }

    /** VHVM11I-VALID-DATE: MMDDYY, MMDDYYYY or MM-DD-YYYY; the last failing check's message wins; returns {date, error} */
    String[] validDate(String in, String today8) {
        String e = pad(in, 10);
        String mo = e.substring(0, 2), day = e.substring(2, 4), year = e.substring(4, 8), rest = e.substring(8, 10);
        String exit = pad("", 10);
        if (!rest.isBlank()) exit = e;
        if (year.substring(2).isBlank()) {
            year = "20" + year.substring(0, 2);
            exit = mo + "-" + day + "-" + year;
        }
        if (rest.isBlank()) exit = mo + "-" + day + "-" + year;
        String em = exit.substring(0, 2), ed = exit.substring(3, 5), ey = exit.substring(6, 10);
        String error = null;
        int nm = num(em), nd = num(ed), ny = num(ey);
        if (nm < 1 || nm > 12) error = msgMonth;
        if (nd < 1 || nd > 31) error = msgDay;
        if (ny < 2000) error = msgYear;
        if ((ey + em + ed).compareTo(today8) > 0) error = msgFuture;
        return new String[] {exit, error};
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return 0; }
    }

    private Result doClose(Sel x, String sw, String closedDate, String runDate) {
        boolean bat = x.type().equals(battery);
        if (!((!bat && !x.car().isBlank() && !x.trkg().isBlank()) || (bat && !x.car().isBlank()))) throw new InvalidRequestException(msgEssential);
        Map<String, Object> crit = crit(x, sw);
        crit.put("closedFlag", yes);
        crit.put("iIncidentFlag", x.incident().startsWith(incidentPrefix) ? yes : no);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("actvInd", no);
        d.put("carNo", x.car());
        d.put("dateClosed", ymd(closedDate.isBlank() ? runDate : closedDate));
        d.put("dateInitialized", ymd(x.dateInit()));
        d.put("dateLastUpd", x.dateLastUpd());
        d.put("eventComment", x.comment());
        d.put("eventStat", x.status());
        d.put("eventType", x.type());
        d.put("eventSubType", x.subType());
        d.put("incdNo", x.incident());
        d.put("locCmpltEvent", cut(x.location(), 4));
        d.put("termLastUpd", terminal);
        d.put("timeInitialized", x.time());
        d.put("trkgRefNo", x.trkg());
        Programs.Outcome c = programs.attempt("VHVM11C", Map.of("VHVM11C_CRIT", crit, "VHVM11C_DATA", d));
        String message = c.ok() ? msgClosed : null;
        if (bat) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("carNo", x.car());
            j.put("batteryReplLoc", cut(x.location(), 4));
            j.put("dateBatteryRepl", d.get("dateClosed"));
            programs.run("VHVM11J", Map.of("VHVM11J_DATA", j));
            if (c.ok()) message = msgClosedBattery;
        }
        if (!c.ok()) throw programs.error("VHVM11C", c.result().control());
        return Result.done(message);
    }

    /** the Delete button after its question (VHVM11I-REMOVE) */
    public Result remove(Change c) {
        Sel x = sel(c);
        if (x.car().isBlank() || x.status().isBlank() || x.type().isBlank() || x.trkg().isBlank()) throw new InvalidRequestException(msgEssential);
        Map<String, Object> crit = crit(x, "");
        crit.put("removedFlag", yes);
        crit.put("iIncidentFlag", x.incident().startsWith(incidentPrefix) ? yes : no);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("actvInd", no);
        d.put("carNo", x.car());
        if (!x.dateClosed().isBlank()) d.put("dateClosed", ymd(x.dateClosed()));
        d.put("dateInitialized", ymd(x.dateInit()));
        d.put("dateLastUpd", x.dateLastUpd());
        d.put("eventComment", x.comment());
        d.put("eventStat", x.status());
        d.put("eventType", x.type());
        d.put("incdNo", x.incident());
        d.put("locCmpltEvent", x.locCmplt());
        d.put("termLastUpd", terminal);
        d.put("timeInitialized", x.time());
        d.put("trkgRefNo", x.trkg());
        programs.run("VHVM11C", Map.of("VHVM11C_CRIT", crit, "VHVM11C_DATA", d));
        return Result.done(msgRemoved);
    }

    /** the Work Assign button (VHVM11IS-GEN-INCD): a TRK incident for the event (VHVM11K), then the event updated with it */
    public Result workAssign(Change c, String userGroup) {
        Sel x = sel(c);
        if (x.incident().startsWith(incidentPrefix) && !x.incident().isEmpty()) throw new InvalidRequestException(msgWorkAssignMissing.strip());
        String date = ymd(x.dateInit());
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("dateInsrvRev", date);
        k.put("dateInsrv", date);
        k.put("dateIncdOccrRev", date);
        k.put("dateIncdOccr", date);
        k.put("carNo", x.car());
        k.put("vehIncdStat", kStatus);
        k.put("incdType", kType);
        k.put("incdLoc", cut(x.location(), 4));
        k.put("homeYrd", x.homeYard());
        k.put("sympCd", "");
        k.put("userGrp", userGroup == null ? "" : userGroup);
        k.put("termLastUpd", terminal);
        k.put("narr", kNarr);
        ProgramResult r = programs.run("VHVM11K", Map.of("VHVM11K_DATA", k));
        String incident = incident(r.record("VHVM11K_DATA").get("incdNo"));
        String sw = "";
        if (needsBatteryAnswer(x)) {
            if (c.answer() == null) return new Result(msgIncidentCreated + " " + incident, askBatteryUpdate, incident);
            sw = c.answer() ? yes : "";
        }
        Result u = doUpdate(c, x, sw, incident);
        return new Result(u.message(), null, incident);
    }

    @SuppressWarnings("unchecked")
    private static String incident(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> x = (Map<String, Object>) m;
            long base = number(x, "incdNoBase");
            return (text(x, "incdPfx") + String.format("%06d", base) + text(x, "incdSuffix")).strip();
        }
        return v == null ? "" : v.toString().strip();
    }

    // ---------------------------------------------------------------- helpers

    /** XXDATES-CONVERT "YMD": MM-DD-YYYY to YYYY-MM-DD when the year is numeric, otherwise unchanged */
    static String ymd(String mdy) {
        String p = pad(mdy, 10);
        String y = p.substring(6, 10);
        if (y.chars().allMatch(Character::isDigit)) return y + "-" + p.substring(0, 2) + "-" + p.substring(3, 5);
        return mdy == null ? "" : mdy.stripTrailing();
    }

    private static String pad(String v, int n) {
        String s = v == null ? "" : v;
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private static String cut(String v, int n) {
        String s = v == null ? "" : v;
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }

    private static String or(String v, String dflt) {
        return v == null ? s(dflt) : v.strip();
    }
}
