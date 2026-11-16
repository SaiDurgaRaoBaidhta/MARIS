package com.bart.maris.middleware.domain.yardreports;

import java.util.List;

/** Configure pseudo trains (old screen VHYT53I): what the yard offers to build a new pseudo consist from. */
public final class YardReportsPseudo {
    private YardReportsPseudo() {}

    /**
     * A car: its number and the orientation marks at its left and right end (the old 6-character LFT-OR + CAR-NO + RGT-OR).
     * serviceable: GO or USABLE (the old screens draw any other car red and refuse it in a new consist).
     */
    public record Car(String car, String leftEnd, String rightEnd, String type, boolean ato, boolean incident, String status,
                      String homeYard, boolean serviceable) {}

    /** @param resettable the old Reset button is on only for a consist marked C or D */
    public record Consist(String consist, int dash, String display, String track, String indicator, boolean resettable, List<Car> cars) {}

    public record Track(String track, String description) {}

    /** @param message the old screen's message after loading (yard selected, no consist, database changed) */
    public record Config(String yard, List<Track> tracks, List<Consist> consists, String message, List<String> warnings) {}

    /** A car of a new consist, as the page sends it */
    public record NewCar(String car, String leftEnd, String rightEnd, String type, boolean ato, boolean incident, String status) {}

    public record Result(boolean done, String message) {}
}
