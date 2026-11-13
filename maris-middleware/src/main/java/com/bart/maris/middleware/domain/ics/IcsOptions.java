package com.bart.maris.middleware.domain.ics;

import java.util.List;

/** Everything the ICS criteria screen offers; each list ends with ALL, as the old drop-downs do. */
public record IcsOptions(String revenueDate, List<Printer> printers, List<Code> yards, List<Code> locations,
                         List<Code> messageCodes, List<String> warnings) {
    /** @param description what the old list shows (PRTR-LOC-DESC); @param vtam the address a print request takes */
    public record Printer(String description, String vtam, String name, String server) {}

    public record Code(String code, String description) {}
}
