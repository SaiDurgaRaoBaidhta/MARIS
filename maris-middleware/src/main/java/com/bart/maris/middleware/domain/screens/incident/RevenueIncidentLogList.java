package com.bart.maris.middleware.domain.screens.incident;

import com.bart.maris.middleware.domain.screens.ActionResult;
import com.bart.maris.middleware.domain.screens.ScreenAction;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import com.bart.maris.middleware.security.MarisUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * VHIE01I Revenue Incident Log, button "List" (Push_Button3). Translated from
 * MARIS_RUI/EGLSource/MRSVehicleIncdEvent: VHIE01IConnections.egl (Text1 = FROM-DATE, Text2 = TO-DATE,
 * Text3 = MESSAGE, list box VAGen_Container_Details_DATA_ROW) and RUIFunctions.egl (VHIE01IS-DATE-CK, then
 * VHIE01IS-GET-DATA calling program VHIE01A). Read-only.
 */
@Component
public class RevenueIncidentLogList implements ScreenAction {
    static final String PROGRAM = "VHIE01A";
    static final String TABLE = "VAGen_Container_Details_DATA_ROW";
    /** URC 100: SQL "not found". */
    static final int NOT_FOUND = 100;
    private static final DateTimeFormatter SCREEN_DATE = DateTimeFormatter.ofPattern("MM-dd-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final Clock clock;

    public RevenueIncidentLogList(LegacyProgramService legacy, MessageResolver messages, Clock clock) {
        this.legacy = legacy;
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    public String screen() {
        return "VHIE01I";
    }

    @Override
    public String control() {
        return "Push_Button3";
    }

    @Override
    public ActionResult run(Map<String, String> fields, MarisUser user) {
        LocalDate from = date(fields.get("Text1"));
        LocalDate to = date(fields.get("Text2"));
        if (from == null || to == null) return screenMessage("Enter the FROM and TO dates as MM-DD-YYYY.");

        // VHIE01IS-DATE-CK: the same checks and messages, in the same order (the last one wins)
        LocalDate today = LocalDate.now(clock);
        String problem = null;
        if (to.isAfter(today)) problem = "Future TO Date is Not Allowed";
        if (from.isAfter(today)) problem = "Future FROM Date is Not Allowed";
        if (to.isBefore(from)) problem = "FROM Date Must Be Less Than or Equal to TO Date";
        if (problem != null) return screenMessage(problem);

        // VHIE01IS-GET-DATA
        ProgramResult r = legacy.execute(PROGRAM, Map.of("VHIE01A_CRIT",
                Map.of("fromDate", from.toString(), "toDate", to.toString())), true);
        XdControl c = r.control();
        if (c.outcome() == XdControl.Outcome.ERROR) {
            String text = c.returnCode() == NOT_FOUND ? "No Data Found for Date Request" : messageText(c);
            return new ActionResult(ActionResult.Control.of(c), Map.of("Text3", text), Map.of(TABLE, List.of()), text);
        }
        List<List<String>> rows = rows(r, c);
        String text = "Y".equals(s(r.record("VHIE01A_CRIT").get("exceedFlag"))) ? "Date Request Exceeded Max Number of Rows" : "";
        return new ActionResult(ActionResult.Control.of(c), Map.of("Text3", text), Map.of(TABLE, rows),
                text.isEmpty() ? rows.size() + (rows.size() == 1 ? " incident" : " incidents") + " listed." : text);
    }

    /** Columns as in VHIE01I.egl: Rev Date, Incd No, Car, Incd Stat, Symp, Time, Speed, Dir, Loc, Con No, Narrative. */
    @SuppressWarnings("unchecked")
    private static List<List<String>> rows(ProgramResult r, XdControl c) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) r.record("VHIE01A_DATA").getOrDefault("dataRow", List.of());
        int n = Math.min(c.rowCount(), data.size());
        List<List<String>> out = new ArrayList<>(n);
        for (Map<String, Object> d : data.subList(0, n)) {
            Map<String, Object> time = (Map<String, Object>) d.getOrDefault("timeIncdOccr", Map.of());
            out.add(List.of(s(d.get("dateIncdOccrRev")), s(d.get("incdNo")), s(d.get("carNo")), s(d.get("vehIncdStat")),
                    s(d.get("sympCd")), s(time.get("timeHhmm")), s(d.get("speed")), s(d.get("conDirection")),
                    s(d.get("incdLoc")), consist(d.get("conNo2")), s(d.get("narr"))));
        }
        return out;
    }

    /** CON-NO-2 is CON (3) + DASH (1) + CON-DASH-NO num(2), e.g. "197-03". */
    @SuppressWarnings("unchecked")
    private static String consist(Object group) {
        if (!(group instanceof Map<?, ?> m)) return s(group);
        Map<String, Object> g = (Map<String, Object>) m;
        String con = s(g.get("con"));
        if (con.isEmpty()) return "";
        Object no = g.get("conDashNo");
        String dash = s(g.get("dash"));
        return con + dash + (no instanceof Number num ? String.format("%02d", num.intValue()) : s(no));
    }

    private String messageText(XdControl c) {
        MessageResolver.Message m = messages.resolve(c.messageCode(), c.messageInserts());
        return m != null && m.text() != null ? m.text() : "The MARIS system returned code " + c.returnCode() + ".";
    }

    private static ActionResult screenMessage(String text) {
        return new ActionResult(ActionResult.Control.screenError(), Map.of("Text3", text), null, text);
    }

    private static LocalDate date(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.strip().replace('/', '-'), SCREEN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString().strip();
    }
}
