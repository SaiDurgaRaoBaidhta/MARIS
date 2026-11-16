package com.bart.maris.middleware.domain.tables2;

import java.util.List;
import java.util.Map;

/** Request and response types of the MRS messages received screens (old VHCCS04I, VHCCS05I, VHCCS18I, VHCCS06I, VHCCS07I). */
public final class Tables2Messages {
    private Tables2Messages() {}

    /** a scanner row (VHCCS4H / VHCCSHE): {@code id} is the row text the old drop-down shows and the criteria take */
    public record Scanner(String id, String scannerId, String location, String direction) {}

    /** VHCCS04I_INIT: the criteria lists and the proposed date / times */
    public record Options(List<String> yards, List<String> tranCodes, List<String> locations, List<Scanner> scanners,
                          List<String> processedCodes, String fromDate, String fromTime, String toTime, List<String> warnings) {}

    /** VHCCS04I's criteria, as the old fields hold them (date MM-DD-YYYY, times HH:MM:SS, scanner = a scanner row's id) */
    public record Criteria(String fromDate, String fromTime, String toTime, String tranCode, String conNo, String yard,
                           String location, String scanner, String processed) {}

    /**
     * VHCCS04I_LIST: the messages (VHCCS4A rows, each with its scanner direction), the screen's message line, the
     * time-stamp window the list used (what Msg Rcv Cars / Errs pass on) and the two counters
     */
    public record Listing(List<Map<String, Object>> messages, String message, String compareFrom, String compareTo,
                          long threshold, long toBeProcessed) {}

    /** a save's result: the old screen's message line; {@code ok} when the program wrote */
    public record Saved(boolean ok, String message) {}

    /** VHCCS18I's options: printers and its own lists */
    public record Printer(String name, String vtam) {}

    public record PrintOptions(List<Printer> printers, List<String> tranCodes, List<String> yards, List<String> locations,
                               List<String> scanners, List<String> processedCodes, List<String> sorts, List<String> warnings) {}

    /** VHCCS18IS-SUBMIT: the criteria (scanner = a VHCCSHE row), the printer's VTAM name and the sort (1 date-time, 2 consist) */
    public record Print(String printer, String fromDate, String fromTime, String toTime, String tranCode, String conNo,
                        String yard, String location, String scanner, Integer sort) {}

    /** VHCCS06I: one car of a message */
    public record Car(String timestamp, String carNo, Integer carOrder, String conNo, Integer dashNo, String dateInService,
                      String orientation, String orientationDirection) {}

    public record Cars(List<Car> cars, List<String> orientations) {}

    /** VHCCS07I: one error of a message */
    public record Error(String timestamp, Integer count, String dateLastUpdated, String fieldName, String function,
                        String description, String type, String terminal, String timeLastUpdated) {}
}
