package com.bart.maris.middleware.domain.yardqueries;

import java.util.List;

/** The reports criteria window (old screen VHYT27I). */
public final class YardQueriesReports {
    private YardQueriesReports() {}

    /** @param from the start date MARIS proposes (yyyy-MM-dd); @param warnings the lists that failed to load */
    public record Options(List<Printer> printers, List<Code> yards, String from, String to, List<String> warnings) {}

    /** @param vtam the address a print request takes */
    public record Printer(String description, String vtam) {}

    public record Code(String code, String description) {}

    /** a hard copy: the report (the menu's WHICH-REPORT code), yard, printer, dates (yyyy-MM-dd) and car range */
    public record Print(String report, String yard, String printer, String from, String to, String fromCar, String toCar) {}
}
