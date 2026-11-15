package com.bart.maris.middleware.domain.common;

import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How the clean services call MARIS programs, with the old screens' common rules:
 * XDCONTROL URC 0 or 4 = done, 100 = no rows, anything else = the program's message (XXM0IT1 text).
 */
public final class Programs {
    private final LegacyProgramService legacy;
    private final MessageResolver messages;

    public Programs(LegacyProgramService legacy, MessageResolver messages) {
        this.legacy = legacy;
        this.messages = messages;
    }

    /** runs a program; URC 0, 4 and 100 return, any other URC throws with the program's message */
    public ProgramResult run(String program, Map<String, ?> inputs) {
        ProgramResult r = legacy.execute(program, inputs, true);
        XdControl c = r.control();
        if (c != null && c.returnCode() != 0 && c.returnCode() != 4 && c.returnCode() != 100) throw error(program, c);
        return r;
    }

    /** the same, but a program error is returned as its message text instead of thrown (screens that carry on) */
    public Outcome attempt(String program, Map<String, ?> inputs) {
        ProgramResult r = legacy.execute(program, inputs, true);
        XdControl c = r.control();
        if (c != null && c.returnCode() != 0 && c.returnCode() != 4) return new Outcome(r, message(program, c));
        return new Outcome(r, null);
    }

    public record Outcome(ProgramResult result, String error) {
        public boolean ok() { return error == null; }
    }

    /** a catalog message with a program's inserts, e.g. VHCHR1I's AFCR001 with VHCHR1A's UMSGINS when it finds nothing */
    public String catalogMessage(String code, XdControl c) {
        return messages.resolve(code, c == null ? List.of() : c.messageInserts()).text();
    }

    public LegacyBusinessException error(String program, XdControl c) {
        return new LegacyBusinessException(program, c, messages.resolve(c.messageCode(), c.messageInserts()));
    }

    private String message(String program, XdControl c) {
        MessageResolver.Message m = messages.resolve(c.messageCode(), c.messageInserts());
        return m != null && m.text() != null ? m.text() : "Program " + program + " returned code " + c.returnCode();
    }

    /** the rows of an array field, as many as XDCONTROL's row count says (the old screens loop to UROWCNT) */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(ProgramResult r, String record, String field) {
        Object v = r.record(record).get(field);
        List<Map<String, Object>> all = v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        int n = r.control() == null ? all.size() : Math.min(all.size(), Math.max(0, r.control().rowCount()));
        return new ArrayList<>(all.subList(0, n));
    }

    /** a text field, trimmed ("" when missing) */
    public static String text(Map<String, Object> rec, String field) {
        Object v = rec == null ? null : rec.get(field);
        return v == null ? "" : v.toString().strip();
    }

    public static long number(Map<String, Object> rec, String field) {
        Object v = rec == null ? null : rec.get(field);
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? 0 : Long.parseLong(v.toString().strip()); } catch (NumberFormatException e) { return 0; }
    }
}
