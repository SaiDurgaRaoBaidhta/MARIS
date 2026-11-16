package com.bart.maris.middleware.domain.tables3;

import java.util.List;
import java.util.Map;

/**
 * Request and response types of the component serial maintenance screens (old VHCF49I list, VHCF50I detail, VHCF51I
 * serial hours, VHCF52I serial geo data). A serial row is the old record's fields by their JSON names (VHCF49A_DATA,
 * VHCF51A_DATA, VHCF52A_DATA rows), so the page shows what the program returns.
 */
public final class Tables3Serials {
    private Tables3Serials() {}

    /** a stock number of the old list (VHCF49B): the list text is the number, a blank and the short description */
    public record Stock(String stock, String description, String text) {}

    /** VHCF49I's start: the stock numbers; whether the old screen offered Update (it hides it when opened from VHMEN) */
    public record Options(List<Stock> stocks, String warning) {}

    /** a list answer: the rows and the old screen's message (more than N rows, no data) */
    public record Rows(List<Map<String, Object>> rows, String message) {}

    /** VHCF50I's lists (VHCF50B, C, E, F, G, H, J, K, L, M): code and the text the old list shows */
    public record Choice(String code, String text) {}

    /** VHCF50I opened on a row: its form (the old screen's fields, dates as it shows them), comments and lists */
    public record Form(Map<String, Object> form, String comments, Map<String, List<Choice>> lists, List<String> warnings) {}

    /** a component serial change: the row as listed, the form as edited, the comments */
    public record Change(Map<String, Object> original, Map<String, Object> form, String comments) {}

    /** serial hours change (VHCF51I): the row's keys and the edited values */
    public record HoursChange(String stock, String serial, Long cumulativeHours, Long cumulativeMinutes, String datePosted) {}

    /** serial geo data change (VHCF52I) */
    public record GeoChange(String stock, String serial, String geoDataName, String dateMeasured, String timeMeasured, String measurement,
                            String measuredBy, String inspector, String location, String position, String car) {}

    /** an update's answer: the old screen's message; ok false when it refused or the program failed */
    public record Result(boolean ok, String message, String detail) {}
}
