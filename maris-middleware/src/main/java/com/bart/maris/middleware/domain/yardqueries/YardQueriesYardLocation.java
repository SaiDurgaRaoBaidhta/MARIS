package com.bart.maris.middleware.domain.yardqueries;

import java.util.List;

/**
 * The yard location status report (old VHYT26I) as its window showed it. Every table carries the old column headings with
 * its rows; headings and captions are the screen's own.
 */
public record YardQueriesYardLocation(String title, String yardName, String runDate, String runTime, String totalCaption, String totalCars,
                                      String testTracksNote, Table cars, Table reasons, Table homeYards, String exchangeTitle, Table exchange,
                                      String homeYardsButton, String reasonsButton) {

    /** one of the report's tables: the old column headings and the rows' cell texts */
    public record Table(List<String> headings, List<List<String>> rows) {}
}
