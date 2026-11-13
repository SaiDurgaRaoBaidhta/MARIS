package com.bart.maris.middleware.domain.configqueries;

import java.util.List;

/** Config component inquiry (old screen VHCF16I). */
public final class ConfigQueriesComponents {
    private ConfigQueriesComponents() {}

    /** @param stocks the stock numbers with their dashes (the form the search takes) and descriptions */
    public record Options(List<String> equipmentTypes, List<Stock> stocks, List<String> serialStatuses, List<String> homeYards, List<String> warnings) {}

    public record Stock(String stock, String description) {}

    /** @param limitReached the program stops at 213 rows; @param message the old screen's message (limit, no data) */
    public record Result(boolean limitReached, String message, List<Component> rows) {}

    public record Component(String car, String homeYard, String equipmentType, String serial, String trackingCode, String dateRebuilt,
                            String employee, String lastRemovedBy, String manufacturerSerial, String parentEquipmentType, String parentPosition,
                            String parentSerial, String parentStock, String position, String purchaseOrder, String configured, String status,
                            String shopLocation, String stock, long hours) {}
}
