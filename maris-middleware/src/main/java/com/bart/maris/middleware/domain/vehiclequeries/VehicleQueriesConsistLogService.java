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
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consist log (old VHCL01I asks for the consist range and revenue date, VHCL02I shows the consists with their cars):
 * the checks of VHCL01IS-VALIDX (the date checked by program VHCL01E), the consists (VHCL02B) and their cars (VHCL02C)
 * put together as VHCL02IS-MOVE-DATA does, the screen's closing message, and the print (VHCL011). The texts, the
 * "ALL" range and the row limits are read from the old screens.
 */
public final class VehicleQueriesConsistLogService {
    private static final String PKG = "MRSVehicleQueries";
    private static final String VALID = "VHCL01IS-VALIDX";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    /** consists per VHCL02I page (VHCL02IW-CON-1 .. CON-4) */
    public static final int PAGE = 4;
    /** cars per consist in VHCL02B_DATA (CAR-NO-1 .. CAR-NO-10) */
    private static final int SLOTS = 10;

    private final Programs programs;
    private final VehicleQueriesRawText raw;
    private final Clock clock;
    private final String fromInvalid, toLess, dateInvalid, all, allLow, allHigh;
    private final String selected, noConsist, exceed, noCars, changed, printed;
    private final int maxConsists, maxCars;

    public VehicleQueriesConsistLogService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog, EglTexts t, Clock clock) {
        this.programs = new Programs(legacy, messages);
        this.raw = new VehicleQueriesRawText(catalog);
        this.clock = clock;
        fromInvalid = t.assigned(VALID, PKG, "MESSAGE", "FROM CONSIST IS");
        toLess = t.assigned(VALID, PKG, "MESSAGE", "LESS THAN");
        dateInvalid = t.assigned(VALID, PKG, "MESSAGE", "DATE");
        all = t.compared(VALID, PKG, "CON-FROM").get(0);
        allLow = t.assignedAll(VALID, PKG, "CON-FROM").get(0);
        allHigh = t.assignedAll(VALID, PKG, "CON-TO").get(0);
        List<String> msg2 = t.assignedRaw("VHCL02IS-GET-DATA2", PKG, "FINAL-MSG-2");
        selected = pick(msg2, "selected");
        noConsist = pick(msg2, "no consist");
        exceed = pick(msg2, "exceed");
        noCars = t.assignedRaw("VHCL02IS-GET-CARS", PKG, "FINAL-MSG-2").get(0);
        changed = t.assignedRaw("VHCL02IS-MOVE-DATA", PKG, "FINAL-MESSAGE").get(0);
        maxConsists = t.assignedNumber("VHCL02IS-GET-DATA2", PKG, "CON-ROW-CNT").intValue();
        maxCars = t.assignedNumber("VHCL02IS-GET-CARS", PKG, "CAR-ROW-CNT").intValue();
        printed = t.triggeredPrompt("VHCL01I", "SUBMIT").strip();
    }

    private static String pick(List<String> texts, String containing) {
        for (String s : texts) if (s.toLowerCase().contains(containing)) return s;
        throw new IllegalStateException("no text containing " + containing);
    }

    public record Printer(String name, String vtamName) {}

    /** {@code revenueDate}: today (yyyy-MM-dd), as VHCL01IS-INIT fills it */
    public record Options(String revenueDate, List<Printer> printers) {}

    /** VHCL01IS-INIT and VHCL01IS-PRINTER (WS70A01) */
    public Options options() {
        List<Printer> printers = new ArrayList<>();
        Programs.Outcome p = programs.attempt("WS70A01", Map.of());
        if (p.ok()) for (Map<String, Object> r : rows(p.result(), "WS70A01_DATA", "dataRow")) printers.add(new Printer(text(r, "prtrLocDesc"), text(r, "vtamName")));
        return new Options(LocalDate.now(clock).toString(), printers);
    }

    /**
     * the checked criteria: the consist range sent on (ALL = the screen's whole range, an empty "to" = "from"), the revenue
     * date as VHCL02I shows it and as the programs take it (VHCL01E's answer)
     */
    public record Criteria(String from, String to, String revenueDate, String revenueDateDb2) {}

    /** VHCL01IS-VALIDX: the consist checks, then the date (program VHCL01E); the last failing check's message wins */
    public Criteria check(String from, String to, String revenueDate) {
        String f = chars(from), tt = chars(to);
        String message = null;
        if (f.equals(all)) {
            f = allLow;
            tt = allHigh;
        } else if (num(f) <= 0) {
            message = fromInvalid;
        } else if (tt.isBlank()) {
            tt = f;
        } else if (num(tt) < num(f)) {
            message = toLess;
        }
        String typed = typed(revenueDate);
        String work = typed.length() > 4 && typed.charAt(4) == '-' ? typed : rearranged(typed);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateWork", work);
        ProgramResult r = programs.attempt("VHCL01E", Map.of("VHCL01E_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        String db2 = "";
        if (urc == 0 || urc == 4) db2 = text(r.record("VHCL01E_DATA"), "dateWork");
        else message = dateInvalid;
        if (message != null) throw new InvalidRequestException(message);
        return new Criteria(f, tt, typed, db2);
    }

    public record Car(String car, String shown, String homeYard, boolean warning) {}

    public record Consist(String consist, String origin, String outOfServiceLocation, String timeIn, String timeOut, String runTime, String swap,
                          String split, String washed, long carCount, boolean warning, List<Car> cars) {}

    /** {@code message}: VHCL02I's closing line (FinalMsg), exactly as the old screen builds it */
    public record ConsistLog(Criteria criteria, List<Consist> consists, int consistCount, int carCount, int pages, String message) {}

    /** Display: VHCL01IS-VALIDX, then VHCL02IS-INIT (VHCL02B, VHCL02C, VHCL02IS-MOVE-DATA) */
    public ConsistLog log(String from, String to, String revenueDate) {
        Criteria c = check(from, to, revenueDate);
        FinalMessage msg = new FinalMessage();
        msg.empty();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("conNoLow", String.format("%03d", num(c.from()) % 1000));
        crit.put("conNoHigh", String.format("%03d", num(c.to()) % 1000));
        crit.put("dateInsrvRev", c.revenueDateDb2());
        ProgramResult b = programs.attempt("VHCL02B", Map.of("VHCL02B_CRIT", crit)).result();
        int urc = b.control() == null ? 0 : b.control().returnCode();
        if (urc == 100) {
            msg.msg2(noConsist);
            return new ConsistLog(c, List.of(), 0, 0, 0, msg.shown());
        }
        if (urc != 0 && urc != 4) throw programs.error("VHCL02B", b.control());
        int conCount = Math.min(b.control() == null ? 0 : b.control().rowCount(), maxConsists);
        List<Map<String, Object>> consistRows = new ArrayList<>(rows(b, "VHCL02B_DATA", "dataRow"));

        ProgramResult cr = programs.attempt("VHCL02C", Map.of("VHCL02C_CRIT", crit)).result();
        int curc = cr.control() == null ? 0 : cr.control().returnCode();
        int carCount = 0;
        List<Map<String, Object>> carRows = List.of();
        if (curc == 0 || curc == 4) {
            carCount = Math.min(cr.control() == null ? 0 : cr.control().rowCount(), maxCars);
            carRows = rows(cr, "VHCL02C_DATA", "dataRow");
        } else if (curc == 100) {
            msg.msg2(noCars);
        }

        List<Slot[]> slots = new ArrayList<>();                             // VHCL02B_DATA's own car columns, then MOVE-DATA's
        for (int x = 0; x < conCount; x++) slots.add(ownSlots(x < consistRows.size() ? consistRows.get(x) : Map.of()));
        boolean[] conWarn = new boolean[conCount];
        for (int x = 0; x < conCount; x++) conWarn[x] = x < consistRows.size() && text(consistRows.get(x), "conWnInd").equals("Y");
        boolean moved = false;
        int y1 = 0;
        for (int x = 0; x < conCount; x++) {
            Map<String, Object> con = x < consistRows.size() ? consistRows.get(x) : Map.of();
            Map<String, Object> carRow = y1 < carRows.size() ? carRows.get(y1) : Map.of();
            if (!raw.text("VHCL02B_DATA", con, "dataRow", "consist").equals(raw.text("VHCL02C_DATA", carRow, "dataRow", "consist"))) {
                msg.whole(changed);
                break;
            }
            int posn = (int) number(con, "maxPosnNo");
            if (posn >= 1 && posn <= SLOTS) {
                Slot[] s = slots.get(x);
                for (int k = 1; k <= posn; k++) {
                    Map<String, Object> ck = car(carRows, y1 + k - 1);
                    // VHCL02IS-MOVE-DATA's two-car branch copies the first car's software version to the second car too
                    Map<String, Object> soft = posn == 2 && k == 2 ? car(carRows, y1) : ck;
                    s[k - 1] = new Slot(raw.text("VHCL02C_DATA", ck, "dataRow", "carNoX"), rawSoft(soft), text(ck, "homeYrd"), text(ck, "carWnInd").equals("Y"));
                }
                conWarn[x] = text(car(carRows, y1 + posn - 1), "conWnInd").equals("Y");
            }
            moved = true;
            y1 += posn == 0 ? 1 : posn;
        }

        List<Consist> consists = new ArrayList<>();
        int pages = 0;
        if (moved) {
            pages = (conCount + PAGE - 1) / PAGE;
            msg.msg1(conCount);
            msg.msg2(selected);
            msg.msg3(carCount);
            for (int x = 0; x < conCount; x++) consists.add(consist(x < consistRows.size() ? consistRows.get(x) : Map.of(), slots.get(x), conWarn[x]));
        }
        if (text(b.record("VHCL02B_DATA"), "exceedLimit").equals("Y")) msg.msg2(exceed);
        return new ConsistLog(c, consists, conCount, carCount, pages, msg.shown());
    }

    /** Print (VHCL01IS-SUBMIT: VHCL01IS-VALIDX, then program VHCL011) */
    public String print(String from, String to, String revenueDate, String printer) {
        if (printer == null || printer.isBlank()) throw new InvalidRequestException("Select a printer.");
        Criteria c = check(from, to, revenueDate);
        String mdy = c.revenueDate();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("reptId", printer.strip());
        crit.put("reptType", printer.strip());
        crit.put("conNoFrom", c.from());
        crit.put("conNoTo", c.to());
        crit.put("dateInsrvRev", rearranged(mdy));
        programs.run("VHCL011", Map.of("VHCL011_CRIT", crit));
        return printed;
    }

    private record Slot(String car, String softVer, String homeYard, boolean warning) {}

    private Slot[] ownSlots(Map<String, Object> con) {
        Slot[] s = new Slot[SLOTS];
        for (int k = 1; k <= SLOTS; k++) {
            s[k - 1] = new Slot(raw.text("VHCL02B_DATA", con, "dataRow", "carNo" + k), raw.text("VHCL02B_DATA", con, "dataRow", "softVer" + k),
                    text(con, "homeYrd" + k), text(con, "carWn" + k).equals("Y"));
        }
        return s;
    }

    private String rawSoft(Map<String, Object> car) {
        return raw.text("VHCL02C_DATA", car, "dataRow", "softVer");
    }

    private static Map<String, Object> car(List<Map<String, Object>> cars, int i) {
        return i >= 0 && i < cars.size() ? cars.get(i) : Map.of();
    }

    /** VHCL02IS-LOAD-PAGE for one consist */
    private Consist consist(Map<String, Object> con, Slot[] slots, boolean warning) {
        String swap = text(con.get("consistSwap") instanceof Map<?, ?> m ? cast(m) : Map.of(), "conNoSwap").isEmpty() ? ""
                : raw.text("VHCL02B_DATA", con, "dataRow", "consistSwap").strip();
        String split = text(con.get("consistSplit") instanceof Map<?, ?> m ? cast(m) : Map.of(), "conNoSplit").isEmpty() ? ""
                : raw.text("VHCL02B_DATA", con, "dataRow", "consistSplit").strip();
        String run = raw.text("VHCL02B_DATA", con, "dataRow", "runTimeHrs") + ":" + raw.text("VHCL02B_DATA", con, "dataRow", "runTimeMin");
        List<Car> cars = new ArrayList<>();
        int last = 0;
        for (int k = 0; k < SLOTS; k++) if (!slots[k].car().isBlank()) last = k + 1;
        for (int k = 0; k < last; k++) {
            Slot s = slots[k];
            String shown = s.car();
            if (s.softVer().contains("$")) shown = fit("$" + s.car().stripTrailing(), 6);
            cars.add(new Car(s.car().strip(), shown.strip(), s.homeYard(), s.warning()));
        }
        return new Consist(raw.text("VHCL02B_DATA", con, "dataRow", "consist").strip(), text(con, "originLoc"), text(con, "outsrvLoc"), text(con, "timeInsrv"),
                text(con, "timeOutsrv"), run, swap, split, text(con, "washInd"), number(con, "exactRowCnt"), warning, cars);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String fit(String s, int len) {
        return s.length() > len ? s.substring(0, len) : s;
    }

    /** the 3-character consist fields: what the screen keeps of the typed text */
    private static String chars(String s) {
        String v = s == null ? "" : s.strip().toUpperCase();
        return v.length() > 3 ? v.substring(0, 3) : v;
    }

    /** CON-FROM-N / CON-TO-N: the typed text as a number (0 when it is not one) */
    private static int num(String s) {
        String v = s == null ? "" : s.strip();
        return !v.isEmpty() && v.chars().allMatch(Character::isDigit) ? Integer.parseInt(v) : 0;
    }

    /** the date as typed on the old screen (MM/dd/yyyy); the API takes yyyy-MM-dd */
    private static String typed(String revenueDate) {
        String d = revenueDate == null ? "" : revenueDate.strip();
        try {
            return MDY.format(LocalDate.parse(d));
        } catch (DateTimeParseException e) {
            return d;
        }
    }

    /** XXDATEW: MM?dd?yyyy -> yyyy-MM-dd, character by character as VHCL01IS-VALIDX moves them */
    private static String rearranged(String mdy) {
        String s = String.format("%-10s", mdy == null ? "" : mdy);
        return s.substring(6, 10) + "-" + s.substring(0, 2) + "-" + s.substring(3, 5);
    }

    /** VHCL02IW-MSG.FINAL-MESSAGE: NUM(3) count, 53 characters of text, NUM(4) car count, 40 characters */
    private static final class FinalMessage {
        private final char[] buf = new char[100];

        void empty() {
            Arrays.fill(buf, ' ');
            msg1(0);
            msg3(0);
        }

        void whole(String s) { put(0, 100, s); }

        void msg1(int n) { put(0, 3, String.format("%03d", n % 1000)); }

        void msg2(String s) { put(3, 53, s); }

        void msg3(int n) { put(56, 4, String.format("%04d", n % 10000)); }

        private void put(int at, int len, String s) {
            for (int i = 0; i < len; i++) buf[at + i] = i < s.length() ? s.charAt(i) : ' ';
        }

        String shown() { return new String(buf).strip(); }
    }
}
