package com.bart.maris.middleware.domain.ics;

/**
 * What the ICS report is for (old screen VHIC30I).
 *
 * @param revenueDate yyyy-MM-dd
 * @param order       "time" (date / time message received, the default) or "consist"
 * @param groupBy     "yard" (yard tracking yard, the default) or "location"
 * @param yard        a yard code or ALL (default)
 * @param location    an ICS location or ALL (default)
 * @param messageCode a message train code or ALL (default)
 */
public record IcsCriteria(String revenueDate, String order, String groupBy, String yard, String location, String messageCode) {
    public IcsCriteria {
        order = blank(order) ? "time" : order.strip().toLowerCase(java.util.Locale.ROOT);
        groupBy = blank(groupBy) ? "yard" : groupBy.strip().toLowerCase(java.util.Locale.ROOT);
        yard = blank(yard) ? "ALL" : yard.strip().toUpperCase(java.util.Locale.ROOT);
        location = blank(location) ? "ALL" : location.strip().toUpperCase(java.util.Locale.ROOT);
        messageCode = blank(messageCode) ? "ALL" : messageCode.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
