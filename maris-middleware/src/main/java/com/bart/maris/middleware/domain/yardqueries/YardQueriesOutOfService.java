package com.bart.maris.middleware.domain.yardqueries;

import java.util.List;

/**
 * The reason out of service report (old VHYT32I), detail or summary, as its window showed it. Tables carry the old column
 * headings; captions are the screen's own. {@code summary} is null for the detail report (the old window never offered it).
 */
public record YardQueriesOutOfService(String heading, String shop, String from, String to, String runTime, String daysCaption, String totalDays,
                                      String timeCaption, String totalTime, List<String> detailGroups, YardQueriesYardLocation.Table detail,
                                      String summaryNote, YardQueriesYardLocation.Table summary) {}
