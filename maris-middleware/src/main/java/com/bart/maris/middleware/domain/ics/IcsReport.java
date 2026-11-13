package com.bart.maris.middleware.domain.ics;

import java.util.List;

/** The ICS / MARIS messages received on one revenue date (old screen VHIC31I). */
public record IcsReport(String revenueDate, int count, boolean maxRowsReached, List<Message> messages) {
    /** the most rows program VHIC31A returns */
    public static final int MAX_ROWS = 150;

    public record Message(String received, String type, String consist, String consistTo, long size, String location,
                          String icsLocation, String direction, String inZone, String created, String icsCreated,
                          long sequence, String processed, String function, String completed, String revenueDate,
                          List<String> cars) {}
}
