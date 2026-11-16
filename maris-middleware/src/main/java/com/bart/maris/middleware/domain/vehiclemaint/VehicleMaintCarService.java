package com.bart.maris.middleware.domain.vehiclemaint;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The vehicle table: add a car (old VHVM02I, program VHVM1A3) and change a car (old VHVM03I, VHVM1A5 reads it and
 * VHVM1A4 saves it). The fields a screen lets the user set, the fields VHVM03I locks for ordinary user groups, the
 * choices, the defaults and the messages are all read from the old screens.
 */
public final class VehicleMaintCarService {
    static final String PKG = "MRSVehicleMaintenance";
    private static final String ADD = "VHVM02I", CHANGE = "VHVM03I";

    private final Programs programs;
    private final Clock clock;
    private final String terminal;
    private final EglTexts t;
    private final RecordLayout addLayout, carLayout;
    /** request field -> widget, per screen; camera code comes through the camera list's row */
    private final Map<String, String> addFields = new LinkedHashMap<>(), changeFields = new LinkedHashMap<>();
    private final Set<String> lockedWidgets;
    private final List<String> privilegedGroups, addCarTypes, addRequired, changeCompared;
    private final String missing, badType, added, addFailed, carNotFound, noChange, updated, updateFailed;

    public VehicleMaintCarService(LegacyProgramService legacy, MessageResolver messages, EglTexts t, ScreenBundle bundle, Clock clock, String terminal) {
        this.programs = new Programs(legacy, messages);
        this.t = t;
        this.clock = clock;
        this.terminal = terminal;
        VehicleMaintScreenFields f = new VehicleMaintScreenFields(bundle, PKG);
        addLayout = bundle.recordType("VHVM1A3_DATA", List.of(PKG));
        carLayout = bundle.recordType("VHVM1A5_DATA", List.of(PKG));
        f.bound(ADD, "VHVM1A3_DATA").forEach((w, j) -> addFields.putIfAbsent(j, w));
        f.bound(CHANGE, "VHVM1A5_DATA").forEach((w, j) -> changeFields.putIfAbsent(j, w));
        addFields.put("cameraCd", cameraWidget(bundle, ADD));
        changeFields.put("cameraCd", cameraWidget(bundle, CHANGE));
        lockedWidgets = f.disabledBy(CHANGE, "DISABLE");
        privilegedGroups = t.compared("VHVM03I-DBMAINT", PKG, "USER-GROUP");
        addCarTypes = t.compared("VHVM02IW-CARTYPE", PKG, "CAR-TYPE");
        addRequired = required(bundle);
        changeCompared = VehicleMaintEgl.comparedFields(bundle, "VHVM03I-PRE-UP", PKG, "VHVM1A5_DATA", "VHVM1A5_CRIT");
        missing = t.triggeredPrompt(ADD, "MISSING-DATA");
        badType = t.triggeredPrompt(ADD, "BAD-TYPE");
        added = t.triggeredPrompt(ADD, "GOOD");
        addFailed = t.triggeredPrompt(ADD, "BAD");
        carNotFound = t.triggeredPrompt(CHANGE, "BAD-CAR");
        noChange = t.triggeredPrompt(CHANGE, "NO-UPDATE");
        updated = t.triggeredPrompt(CHANGE, "DONE");
        updateFailed = t.triggeredPrompt(CHANGE, "BAD");
    }

    /** the camera list's widget: its selected row feeds VHVM02IW DATA_ROW, whose CAMERA-CD is the car's camera code */
    private static String cameraWidget(ScreenBundle bundle, String screen) {
        for (var c : bundle.screen(screen).path("connections")) {
            var a = c.path("a");
            if (a.path(0).path("id").asText().startsWith("screen.") && a.path(2).path("id").asText().equals("VHVM02IW")
                    && a.path(3).asText().equals("DATA_ROW data")) return a.path(0).path("id").asText().substring(7);
        }
        throw new IllegalStateException(screen + " has no camera list");
    }

    /** VHVM02IS-PRE-ADD: the fields that must not be blank, in the order it tests them */
    private List<String> required(ScreenBundle bundle) {
        List<String> out = new ArrayList<>();
        for (String egl : VehicleMaintEgl.testedFields(bundle, "VHVM02IS-PRE-ADD", PKG, "VHVM1A3_DATA")) {
            String j = VehicleMaintScreenFields.json(addLayout, egl);
            if (j != null && !out.contains(j)) out.add(j);
        }
        if (out.isEmpty()) throw new IllegalStateException("VHVM02IS-PRE-ADD tests no field");
        return out;
    }

    public record Camera(String code, String description) {}

    public record Options(List<String> carTypes, List<String> addCarTypes, List<String> statuses, List<String> previousStatuses,
                          List<String> homeYards, List<String> yesNo, List<String> batteryLocations, List<String> lastPmLocations,
                          List<String> whereIsCar, List<String> locations, List<Camera> cameras, Map<String, List<String>> orientations,
                          List<String> schedulePms, List<String> configStatuses, List<String> configTypes, Map<String, Object> addDefaults) {}

    /** the choices of both screens (VHVM1A6 cameras, VHVM1A7 orientations, VHCY1A1 locations, VHVM1A9 config lists) */
    public Options options() {
        List<Camera> cameras = new ArrayList<>();
        Programs.Outcome cam = programs.attempt("VHVM1A6", Map.of());
        if (cam.ok()) for (var r : rows(cam.result(), "VHVM1A6_DATA", "dataRow")) cameras.add(new Camera(text(r, "cameraCd"), text(r, "cameraCdDesc")));
        Map<String, List<String>> orient = new LinkedHashMap<>();
        Programs.Outcome ori = programs.attempt("VHVM1A7", Map.of());
        if (ori.ok()) {
            int n = (int) number(ori.result().record("VHVM1A7_DATA"), "vagenCount");
            List<Map<String, Object>> list = rowsAll(ori.result(), "VHVM1A7_DATA");
            for (int i = 0; i < Math.min(n, list.size()); i++) {                 // VHVM02IS-ORIENT: the last row of a type wins
                orient.put(text(list.get(i), "carType"), List.of(text(list.get(i), "lftOrientationCd"), text(list.get(i), "rgtOrientationCd")));
            }
        }
        List<String> locations = new ArrayList<>();
        Programs.Outcome loc = programs.attempt("VHCY1A1", Map.of());
        if (loc.ok()) for (var r : rows(loc.result(), "VHCY1A1_DATA", "dataRow")) if (!text(r, "loc").isEmpty()) locations.add(text(r, "loc"));
        List<String> stats = new ArrayList<>(), types = new ArrayList<>();
        Programs.Outcome cfg = programs.attempt("VHVM1A9", Map.of());
        if (cfg.ok()) {
            for (var r : rows(cfg.result(), "VHVM1A9_DATA", "dataRow")) stats.add(text(r, "configCmpntStat"));
            for (var r : rows(cfg.result(), "VHVM1A9B_DATA", "dataRow")) types.add(text(r, "cmpntEquipType"));
        }
        return new Options(t.items(CHANGE, "Drop-down_List1"), addCarTypes, t.items(CHANGE, "Drop-down_List9"), t.items(CHANGE, "Drop-down_List11"),
                t.items(CHANGE, "Drop-down_List2"), t.items(CHANGE, "Drop-down_List3"), t.items(ADD, "Drop-down_List16"), t.items(CHANGE, "Drop-down_List6"),
                t.items(CHANGE, "Drop-down_List12"), locations, cameras, orient, new ArrayList<>(t.indexed("VHVM03IS-GET-SCHD", PKG, "SCHD-PM").values()),
                stats, types, addDefaults());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsAll(ProgramResult r, String record) {
        Object v = r.record(record) == null ? null : r.record(record).get("dataRow");
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    /** VHVM02IS-CLEAR: the values Clear puts in the add form (the battery date is today's) */
    private Map<String, Object> addDefaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldLayout f : addLayout.fields()) {
            List<String> v = t.assignedAll("VHVM02IS-CLEAR", PKG, f.name());
            if (!v.isEmpty()) out.put(f.json(), v.get(0));
        }
        out.put("dateBatteryRepl", LocalDate.now(clock).toString());
        return out;
    }

    // ---- VHVM02I: add a car ----------------------------------------------------------------------------------------

    /** VHVM02IW-CARTYPE, VHVM02IS-PRE-ADD and VHVM02I-ADD-CAR */
    public String add(Map<String, Object> car) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : car.entrySet()) {
            if (!addFields.containsKey(e.getKey())) throw new InvalidRequestException("The add screen has no field " + e.getKey() + ".");
            data.put(e.getKey(), value(addLayout, e.getKey(), e.getValue()));
        }
        String type = str(data.get("carType"));
        if (!type.isEmpty()) {
            if (!addCarTypes.contains(type)) throw new InvalidRequestException(badType);
            Programs.Outcome o = programs.attempt("VHVM1AA", Map.of("VHVM1AA_CRIT", Map.of("carType", type)));
            Map<String, Object> d = o.result().record("VHVM1AA_DATA");
            int urc = o.result().control() == null ? 0 : o.result().control().returnCode();
            if (!o.ok() || urc == 100 || d == null) throw new InvalidRequestException(badType);
            data.put("cntDaysNxtPa", number(d, "cntDaysNxtPa"));
            data.put("cntDaysNxtWash", number(d, "cntDaysNxtWash"));
            if (!car.containsKey("hrsToNextPm")) data.put("hrsToNextPm", number(d, "hrsToNextPm"));
            data.put("dateNxtPaInspc", text(d, "dateNxtPa"));
            data.put("dateNxtCarWash", text(d, "dateNxtWash"));
        }
        for (String r : addRequired) if (str(data.get(r)).isEmpty()) throw new InvalidRequestException(missing);
        data.put("termMaintUpd", terminal);
        data.put("termLastUpd", terminal);
        Programs.Outcome o = programs.attempt("VHVM1A3", Map.of("VHVM1A3_DATA", data));
        if (!o.ok()) throw failed("VHVM1A3", o, addFailed);
        return added;
    }

    // ---- VHVM03I: change a car -------------------------------------------------------------------------------------

    public record Car(Map<String, Object> fields, List<String> editable) {}

    /** VHVM03IS-GET-CAR: the car as VHVM1A5 returns it, and the fields this user group may change */
    public Car car(String car, String userGroup) {
        Map<String, Object> d = read(car).record("VHVM1A5_DATA");
        Map<String, Object> out = new LinkedHashMap<>();
        for (String f : changeFields.keySet()) out.put(f, d.get(f) instanceof String s ? s.strip() : d.get(f));
        out.put("carNo", text(d, "carNo"));
        return new Car(out, editable(userGroup));
    }

    private List<String> editable(String group) {
        boolean all = privilegedGroups.contains(group == null ? "" : group.strip());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : changeFields.entrySet()) {
            if (e.getKey().equals("carNo")) continue;
            if (all || !lockedWidgets.contains(e.getValue())) out.add(e.getKey());
        }
        return out;
    }

    /** VHVM03I-PRE-UP and VHVM03I-UPDATE-CAR: the changes go on the car as read; nothing changed is refused */
    public String update(String car, Map<String, Object> changes, String userGroup) {
        ProgramResult r = read(car);
        Map<String, Object> crit = r.record("VHVM1A5_CRIT"), data = new LinkedHashMap<>(r.record("VHVM1A5_DATA"));
        List<String> editable = editable(userGroup);
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            if (!editable.contains(e.getKey())) throw new InvalidRequestException("The field " + e.getKey() + " can't be changed on this screen.");
            data.put(e.getKey(), value(carLayout, e.getKey(), e.getValue()));
        }
        boolean changed = false;
        for (String egl : changeCompared) {
            String j = VehicleMaintScreenFields.json(carLayout, egl);
            if (j != null && !same(data.get(j), crit == null ? null : crit.get(j))) { changed = true; break; }
        }
        if (!changed) throw new InvalidRequestException(noChange);
        Map<String, Object> switches = new LinkedHashMap<>();
        switches.put("configUpdSwitch", !str(data.get("dateConfigUpd")).isEmpty() && !str(data.get("timeConfigUpd")).isEmpty() ? "Y" : "N");
        switches.put("homeYardSwitch", "N");
        if (!same(data.get("homeYrd"), crit == null ? null : crit.get("homeYrd"))) {
            data.put("dateHomeYrdUpd", LocalDate.now(clock).toString());
            data.put("termHomeYrdUpd", terminal);
            switches.put("homeYardSwitch", "Y");
        }
        data.put("termLastUpd", terminal);
        data.put("termMaintUpd", terminal);
        Programs.Outcome o = programs.attempt("VHVM1A4", Map.of("VHVM1A4_DATA", switches, "VHVM1A5_DATA", data));
        if (!o.ok()) throw failed("VHVM1A4", o, updateFailed);
        return updated;
    }

    private ProgramResult read(String car) {
        String c = car == null ? "" : car.strip();
        if (c.isEmpty() || c.length() > 4) throw new InvalidRequestException(carNotFound);
        Programs.Outcome o = programs.attempt("VHVM1A5", Map.of("VHVM1A5_CRIT", Map.of("carNo", c)));
        int urc = o.result().control() == null ? 0 : o.result().control().returnCode();
        if (!o.ok() || urc == 100 || o.result().record("VHVM1A5_DATA") == null || text(o.result().record("VHVM1A5_DATA"), "carNo").isEmpty()) {
            throw new InvalidRequestException(carNotFound);                     // (the backend answers an unknown car with URC 0 and no data)
        }
        return o.result();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.longValue() == y.longValue();
        if (a instanceof Number || b instanceof Number) return str(a).equals(str(b)) || (str(a).isEmpty() ? "0" : str(a)).equals(str(b).isEmpty() ? "0" : str(b));
        return str(a).equals(str(b));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    /** a request value as the record field takes it: numbers for number fields, text (upper case) for the rest */
    private static Object value(RecordLayout layout, String json, Object v) {
        FieldLayout f = VehicleMaintScreenFields.flat(layout.fields()).stream().filter(x -> x.json().equals(json)).findFirst().orElse(null);
        if (f == null) throw new InvalidRequestException("Unknown field " + json + ".");
        String s = str(v);
        return switch (f.type()) {
            case SMALLINT, INT, BIGINT, NUM, NUMC, BIN, DECIMAL, PACF -> {
                if (s.isEmpty()) yield 0;
                try { yield Long.parseLong(s); } catch (NumberFormatException e) { throw new InvalidRequestException("The field " + json + " takes a number."); }
            }
            default -> {
                if (s.length() > f.length()) throw new InvalidRequestException("The field " + json + " takes at most " + f.length() + " characters.");
                yield s.toUpperCase(java.util.Locale.ROOT);
            }
        };
    }

    static LegacyBusinessException failed(String program, Programs.Outcome o, String text) {
        var c = o.result().control();
        return new LegacyBusinessException(program, c, new MessageResolver.Message(c == null ? "" : c.messageCode(), "E", text));
    }

    Set<String> addFieldNames() {
        return new LinkedHashSet<>(addFields.keySet());
    }
}
