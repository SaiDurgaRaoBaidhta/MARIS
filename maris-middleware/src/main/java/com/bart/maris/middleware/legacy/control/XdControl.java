package com.bart.maris.middleware.legacy.control;

import java.util.List;
import java.util.Map;

/**
 * The common control record every MARIS program receives and returns (MRSCommonCode.XDCONTROL).
 *
 * @param returnCode URC: 0 = OK, 4 = warning, anything else = error (usually an SQL code)
 * @param moreUp     UUP = 'Y': earlier rows exist (paging)
 * @param moreDown   UDOWN = 'Y': more rows exist (paging)
 */
public record XdControl(String messageCode, List<String> messageInserts, int returnCode, int rowCount,
                        boolean moreUp, boolean moreDown, String direction, String userId, String function) {

    public static final String RECORD = "MRSCommonCode.XDCONTROL";

    public enum Outcome { OK, WARNING, ERROR }

    /** Outcome as the old screens judge it: they continue on URC 0 or 4. */
    public Outcome outcome() {
        return returnCode == 0 ? Outcome.OK : returnCode == 4 ? Outcome.WARNING : Outcome.ERROR;
    }

    @SuppressWarnings("unchecked")
    public static XdControl from(Map<String, Object> decoded) {
        List<String> inserts = ((List<Object>) decoded.getOrDefault("umsgins", List.of())).stream()
                .map(o -> String.valueOf(o).strip()).toList();
        int last = inserts.size();
        while (last > 0 && inserts.get(last - 1).isEmpty()) last--;
        return new XdControl(
                str(decoded.get("umsgcode")), inserts.subList(0, last),
                num(decoded.get("urc")), num(decoded.get("urowcnt")),
                "Y".equals(str(decoded.get("uup"))), "Y".equals(str(decoded.get("udown"))),
                str(decoded.get("udir")), str(decoded.get("uuserid")), str(decoded.get("ufunction")));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
