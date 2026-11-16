package com.bart.maris.middleware.domain.yardreports;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.Car;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.Config;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.Consist;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.NewCar;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.Result;
import com.bart.maris.middleware.domain.yardreports.YardReportsPseudo.Track;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configure pseudo trains (old screen VHYT53I). The rules are MRSYardTrackingReport/RUIFunctions.egl VHYT53IS-INIT2,
 * -GET-DATA, -GET-CARS, -MOVE-DATA (reading), -INIT3 / -INIT4 (a track's cars), -CREATE-C / -VALID / -VALID-CRS / -VALID-MID
 * / -VALID-END / -MOVE-MSG (VHYT53F), -CREATE-P / -VALID2 / -VALID-CR2 / -VALID-MI2 (VHYT53G), -MOVE-CAR (VHYT53H),
 * -RESET (VHYT53K). Building the new consist (include, add, insert, remove) is the page's; the middleware checks it and
 * writes. Every text and car type list is read from the screen bundle.
 */
public class YardReportsPseudoService {
    private static final String PKG = "MRSYardTrackingReport";
    /** the old screens' "not red": GO or USABLE */
    private static final Set<String> SERVICEABLE = Set.of("GO", "USABLE");

    private final Programs programs;
    private final String terminalId;
    private final String all, dbChanged, yardSelected, noConsist, noConNo, noConNoPartial, created, createdPartial, lessThanTwo,
            noCar, outOfRange, notNumeric, trainIs, alreadyIn, yardDot, enterAnother, badMid, midIncident, midRed, badEnd,
            endIncident, endRed, noOrientation, movedBack, moved, noCarOrTrack, trackFull;
    private final Set<String> midTypes, endTypes, endTypesNeedingAto;

    public YardReportsPseudoService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, String terminalId) {
        this.programs = new Programs(legacy, messages);
        this.terminalId = terminalId;
        all = t(screens, "VHYT53IS-INIT2", "YRD-SUB-LOC", 0).strip();
        dbChanged = t(screens, "VHYT53IS-MOVE-DATA", "FINAL-MESSAGE", 0);
        yardSelected = t(screens, "VHYT53IS-GET-DATA", "FINAL-MSG-2", 2);
        noConsist = t(screens, "VHYT53IS-GET-DATA", "FINAL-MSG-2", 3);
        noConNo = t(screens, "VHYT53IS-CREATE-C", "FINAL-MESSAGE", 0);
        noConNoPartial = t(screens, "VHYT53IS-CREATE-P", "FINAL-MESSAGE", 0);
        created = t(screens, "VHYT53IS-CREATE-C", "FINAL-MSG-2", 0);
        createdPartial = t(screens, "VHYT53IS-CREATE-P", "FINAL-MSG-2", 0);
        lessThanTwo = t(screens, "VHYT53IS-VALID", "FINAL-MESSAGE", 1);
        noCar = t(screens, "VHYT53IS-VALID2", "FINAL-MESSAGE", 1);
        outOfRange = t(screens, "VHYT53IS-VALID", "ER-DESC", 0);
        notNumeric = t(screens, "VHYT53IS-VALID", "ER-DESC", 1);
        trainIs = t(screens, "VHYT53IS-VALID", "PROMPT-MSG-A-1", 0);
        alreadyIn = t(screens, "VHYT53IS-VALID", "PROMPT-MSG-A-3", 0);
        yardDot = t(screens, "VHYT53IS-VALID", "PROMPT-MSG-A-5", 0);
        enterAnother = t(screens, "VHYT53IS-VALID", "PROMPT-MSG-A-6", 0);
        badMid = t(screens, "VHYT53IS-VALID-MID", "ER-DESC", 0);
        midIncident = t(screens, "VHYT53IS-VALID-MID", "ER-DESC", 1);
        midRed = t(screens, "VHYT53IS-VALID-MID", "ER-DESC", 2);
        badEnd = t(screens, "VHYT53IS-VALID-END", "ER-DESC", 0);
        endIncident = t(screens, "VHYT53IS-VALID-END", "ER-DESC", 2);
        endRed = t(screens, "VHYT53IS-VALID-END", "ER-DESC", 3);
        noOrientation = t(screens, "VHYT53IS-VALID-END", "ER-DESC", 4);
        movedBack = t(screens, "VHYT53IS-MOVE-CAR", "FINAL-MSG-2", 0);
        moved = t(screens, "VHYT53IS-MOVE-CAR", "FINAL-MSG-2", 1);
        noCarOrTrack = t(screens, "VHYT53IS-MOVE-CAR", "FINAL-MESSAGE", 1);
        trackFull = t(screens, "VHYT53IS-MOVE-CAR", "TEMP-MSG-1", 0);
        midTypes = Set.copyOf(YardReportsLayout.comparedTexts(screens, "VHYT53IS-VALID-MID", PKG, "CAR-TYPE", "!="));
        endTypes = Set.copyOf(YardReportsLayout.comparedTexts(screens, "VHYT53IS-VALID-END", PKG, "CAR-TYPE", "!="));
        endTypesNeedingAto = Set.copyOf(YardReportsLayout.comparedTexts(screens, "VHYT53IS-VALID-END", PKG, "CAR-TYPE", "=="));
    }

    private static String t(ScreenBundle screens, String function, String field, int i) {
        return YardReportsLayout.assignedTexts(screens, function, PKG, field).get(i);
    }

    // ---- reading: VHYT53IS-INIT2, -GET-DATA, -GET-CARS, -MOVE-DATA ----------------------------------------------------

    public Config config(String yard) {
        String y = yard.strip().toUpperCase(java.util.Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        List<Track> tracks = new ArrayList<>();
        Programs.Outcome d = programs.attempt("VHYT53D", Map.of("VHYT53D_CRIT", Map.of("yrdTrkgYrd", y)));
        if (d.ok()) {
            for (Map<String, Object> r : rows(d.result(), "VHYT53D_DATA", "dataRow")) tracks.add(new Track(text(r, "yrdSubLoc"), text(r, "yrdSubLocDesc")));
            tracks.add(new Track(all, ""));
        } else warnings.add(d.error());

        Map<String, Object> range = Map.of("frYrdTrkgYrd", y, "toYrdTrkgYrd", y);
        ProgramResult b = programs.run("VHYT53B", Map.of("VHYT53B_CRIT", range));
        if (b.control().returnCode() == 100) return new Config(y, tracks, List.of(), message(fixed(y, 3), noConsist), warnings);
        ProgramResult c = programs.run("VHYT53C", Map.of("VHYT53C_CRIT", range));
        List<Map<String, Object>> cars = c.control().returnCode() == 100 ? List.of() : rows(c, "VHYT53C_DATA", "dataRow");

        List<Consist> consists = new ArrayList<>();
        int next = 0;
        for (Map<String, Object> row : rows(b, "VHYT53B_DATA", "dataRow")) {
            String con = text(row, "conNo");
            int count = (int) number(row, "maxPosnNo");
            if (next >= cars.size() || !text(cars.get(next), "conNo").equals(con)) {
                return new Config(y, tracks, List.of(), message(dbChanged), warnings);      // MOVE-DATA: the consists and cars disagree
            }
            List<Car> list = new ArrayList<>();
            for (int i = next; i < Math.min(next + count, cars.size()); i++) list.add(car(cars.get(i), "carNoX"));
            next += count;
            int dash = (int) number(row, "dashNo");
            String ind = text(row, "conInd");
            String display = dash != 0 ? con + "-" + String.format("%02d", dash) : ind.equals("C") ? con + "-X" : con;
            consists.add(new Consist(con, dash, display, text(row, "yrdSubLoc"), ind, ind.equals("C") || ind.equals("D"), list));
        }
        return new Config(y, tracks, consists, message(fixed(y, 4), yardSelected), warnings);
    }

    @SuppressWarnings("unchecked")
    private static Car car(Map<String, Object> row, String idField) {
        Map<String, Object> id = row.get(idField) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String status = text(row, "vehStat");
        return new Car(text(id, "carNo"), text(id, "lftOr"), text(id, "rgtOr"), text(row, "carType"), text(row, "atoPresentInd").equals("Y"),
                text(row, "trainIncdInd").equals("Y"), status, text(row, "homeYrd"), SERVICEABLE.contains(status));
    }

    /** VHYT53IS-INIT3 (the Add list, VHYT53E) / -INIT4 (the Insert list, VHYT53M): a track's cars, or every track's */
    public List<Car> trackCars(String yard, String track, boolean insertList) {
        String program = insertList ? "VHYT53M" : "VHYT53E", t = track == null ? "" : track.strip();
        Map<String, Object> crit = new LinkedHashMap<>();
        boolean every = t.equalsIgnoreCase(all);
        crit.put("frYrdSubLoc", every ? "" : t);
        crit.put("toYrdSubLoc", every ? "9999999999" : t);
        crit.put("yrdTrkgYrd", yard.strip().toUpperCase(java.util.Locale.ROOT));
        ProgramResult r = programs.run(program, Map.of(program + "_CRIT", crit));
        List<Car> out = new ArrayList<>();
        if (r.control().returnCode() != 100) for (Map<String, Object> row : rows(r, program + "_DATA", "dataRow")) out.add(car(row, "carAndOr"));
        return out;
    }

    // ---- creating: VHYT53IS-CREATE-C / -CREATE-P and their checks --------------------------------------------------------

    public Result create(String yard, String consist, boolean partial, List<NewCar> cars, String userGroup) {
        String y = yard.strip().toUpperCase(java.util.Locale.ROOT), con = consist == null ? "" : consist.strip();
        List<NewCar> list = cars == null ? List.of() : cars;
        if (con.isEmpty()) throw new InvalidRequestException(message(partial ? noConNoPartial : noConNo));
        if (list.size() > 10) throw new InvalidRequestException("A pseudo consist has at most 10 cars.");
        check(y, con, partial, list);

        Map<String, Object> row = new LinkedHashMap<>();
        List<Map<String, Object>> ids = new ArrayList<>();
        List<String> colours = new ArrayList<>(), types = new ArrayList<>(), atos = new ArrayList<>(), incidents = new ArrayList<>(), stats = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            NewCar c = i < list.size() ? list.get(i) : null;
            ids.add(c == null ? Map.of("lftOr", "", "carNo", "", "rgtOr", "") : Map.of("lftOr", nz(c.leftEnd()), "carNo", nz(c.car()), "rgtOr", nz(c.rightEnd())));
            colours.add(c == null ? "" : colour(c));
            types.add(c == null ? "" : nz(c.type()));
            atos.add(c == null ? "" : c.ato() ? "Y" : "N");
            incidents.add(c == null ? "" : c.incident() ? "Y" : "N");
            stats.add(c == null ? "" : nz(c.status()));
        }
        row.put("carNoN", ids);
        row.put("colorN", colours);
        if (!partial) {
            row.put("carTypeN", types);
            row.put("atoPresentIndN", atos);
            row.put("trainIncdIndN", incidents);
            row.put("vehStatN", stats);
        }
        String program = partial ? "VHYT53G" : "VHYT53F";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("numOfCar", list.size());
        data.put("newConNo", con);
        data.put("termLastUpd", terminalId);
        data.put("userGroup", userGroup);
        programs.run(program, Map.of(program + "_CRIT", Map.of("yrdTrkgYrd", y, "carNoRow", row), program + "_DATA", data));
        return new Result(true, message(fixed(con, 4), partial ? createdPartial : created));
    }

    /** VHYT53IS-VALID (whole consist) / -VALID2 (partial); throws the old message */
    private void check(String yard, String con, boolean partial, List<NewCar> cars) {
        if (cars.size() < (partial ? 1 : 2)) throw new InvalidRequestException(message(partial ? noCar : lessThanTwo));
        String c3 = fixed(con, 3);
        if (!c3.chars().allMatch(Character::isDigit)) throw new InvalidRequestException(errors(java.util.Collections.singletonList(new String[]{c3, notNumeric})));
        int n = Integer.parseInt(c3);
        if (n <= 99 || n >= 1000) throw new InvalidRequestException(errors(java.util.Collections.singletonList(new String[]{c3, outOfRange})));

        Map<String, Object> crit = new HashMap<>(Map.of("conNo", c3));
        if (partial) crit.put("yrdTrkgYrd", yard);                   // VALID sets only the con no, VALID2 the yard too
        Programs.Outcome l = programs.attempt("VHYT53L", Map.of("VHYT53L_CRIT", crit, "VHYT53L_DATA", Map.of("validInd", partial ? "P" : "C")));
        int urc = l.result().control().returnCode();
        Map<String, Object> ld = l.result().record("VHYT53L_DATA");
        if (urc == 0 || urc == 4) {
            if (text(ld, "conErr").equals("Y")) {
                throw new InvalidRequestException(message(fixed(trainIs, 8) + fixed(c3, 3) + fixed(alreadyIn, 19) + fixed(text(ld, "yrdTrkgYrd"), 3)
                        + fixed(yardDot, 7) + fixed(enterAnother, 40)));
            }
            return;                                                // the old screen checks the cars only when VHYT53L finds nothing
        }
        // the old screen showed the program's message and went on to create; the API stops there
        if (urc != 100) throw programs.error("VHYT53L", l.result().control());
        if (!text(ld, "conErr").equals("N")) return;

        List<String[]> errors = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            NewCar c = cars.get(i);
            boolean end = i == 0 || i == cars.size() - 1;
            String type = nz(c.type()).strip(), no = fixed(nz(c.car()), 4);
            if (partial) {
                if (!end && !midTypes.contains(type)) errors.add(new String[]{no, badMid});
            } else if (!end) {
                if (!midTypes.contains(type)) errors.add(new String[]{no, badMid});
                else if (c.incident()) errors.add(new String[]{no, midIncident});
                else if (!SERVICEABLE.contains(nz(c.status()).strip())) errors.add(new String[]{no, midRed});
            } else {
                String orientation = nz(i == 0 ? c.leftEnd() : c.rightEnd()).strip();
                if (!endTypes.contains(type) || (endTypesNeedingAto.contains(type) && !c.ato())) errors.add(new String[]{no, badEnd});
                else if (c.incident()) errors.add(new String[]{no, endIncident});
                else if (!SERVICEABLE.contains(nz(c.status()).strip())) errors.add(new String[]{no, endRed});
                else if (orientation.isEmpty()) errors.add(new String[]{no, noOrientation});
            }
        }
        if (!errors.isEmpty()) throw new InvalidRequestException(errors(errors));
    }

    /** VHYT53IS-MOVE-MSG: up to 10 entries of ER-CAR-NO (4) + ER-DESC (28), each to its ".", joined by ", " */
    private static String errors(List<String[]> list) {
        StringBuilder out = new StringBuilder();
        for (String[] e : list.subList(0, Math.min(10, list.size()))) {
            String entry = fixed(e[0], 4) + fixed(e[1], 28);
            for (int i = 0; i < 31; i++) {
                if (entry.charAt(i) == '.') { out.append(", "); break; }
                out.append(entry.charAt(i));
            }
        }
        if (out.length() >= 2) out.setCharAt(out.length() - 2, ' ');
        return message(out.toString());
    }

    private static String colour(NewCar c) {
        return SERVICEABLE.contains(nz(c.status()).strip()) ? "BLACK" : "RED";
    }

    // ---- VHYT53IS-MOVE-CAR, -RESET ------------------------------------------------------------------------------------

    /** moves a car of the new consist out to a track (VHYT53H writes at once) */
    public YardReportsMove moveCar(String yard, String car, String track, String userGroup) {
        String c = nz(car).strip(), t = nz(track).strip();
        if (c.isEmpty() || t.isEmpty()) throw new InvalidRequestException(message(noCarOrTrack));
        // VHYT53H_CRIT.CAR-NO = the selected car: CAR-NO is an array in the record, and an assignment without an index sets
        // every element (as the screen engine runs it), so all ten car slots carry the car
        List<Map<String, Object>> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) ids.add(Map.of("lftOr", "", "carNo", c, "rgtOr", ""));
        Programs.Outcome o = programs.attempt("VHYT53H", Map.of(
                "VHYT53H_CRIT", Map.of("yrdSubLoc", t, "yrdTrkgYrd", yard.strip().toUpperCase(java.util.Locale.ROOT), "carNoRow", Map.of("carNoN", ids)),
                "VHYT53H_DATA", Map.of("termLastUpd", terminalId, "userGroup", userGroup)));
        Map<String, Object> data = o.result().record("VHYT53H_DATA");
        if (!o.ok()) {
            if (text(data, "trkIsFull").equals("Y")) return new YardReportsMove(false, message(fixed(trackFull, 69) + t));
            throw programs.error("VHYT53H", o.result().control());
        }
        return new YardReportsMove(true, message(fixed(c, 4) + (text(data, "moveSameTrk").equals("Y") ? movedBack : moved)));
    }

    /** resets a consist marked C or D (VHYT53K writes) */
    public Result reset(String yard, String consist, String track, String indicator, String userGroup) {
        String ind = nz(indicator).strip();
        if (!ind.equals("C") && !ind.equals("D")) throw new InvalidRequestException("Only a consist marked C or D can be reset.");
        programs.run("VHYT53K", Map.of(
                "VHYT53K_CRIT", Map.of("conNo", nz(consist).strip(), "yrdSubLoc", nz(track).strip(), "yrdTrkgYrd", yard.strip().toUpperCase(java.util.Locale.ROOT), "conInd", ind),
                "VHYT53K_DATA", Map.of("termLastUpd", terminalId, "userGroup", userGroup)));
        return new Result(true, null);
    }

    // ---- texts ---------------------------------------------------------------------------------------------------------

    /** a text in a fixed-width field, as the old record holds it: cut or padded with blanks */
    private static String fixed(String s, int width) {
        String v = nz(s);
        return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
    }

    /** the old message as one line: runs of blanks as one */
    private static String message(String... parts) {
        return String.join("", parts).replaceAll("\\s+", " ").strip();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
