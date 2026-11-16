package com.bart.maris.middleware.domain.yardqueries;

import java.util.List;

/**
 * The shop status report (old screen VHYT25I) for one yard: the cars in the shop with how long they have been there, and
 * the cars out of the yard (its second list, VHYT30).
 *
 * @param totalCars  every car in the shop list (the ones on T-24 are counted but not timed)
 * @param grandTotal the shop list's cars and the second list's cars on SHOP (VHYT25IS-FINALCT)
 */
public record YardQueriesShopStatus(String yard, String shop, String runTime, List<Car> cars, Integer averageDays, Integer averageHours,
                                    int totalCars, OutOfYard outOfYard, int grandTotal) {
    /** @param elapsedDays null for a car the report does not time (T-24) */
    public record Car(String track, int position, String car, String leftEnd, String rightEnd, String status, boolean incident,
                      String dateIn, String timeIn, String reason, Integer elapsedDays, Integer elapsedHours) {}

    /** @param transferTrack the label the old report gives Hayward's transfer tracks */
    public record OutOfYard(String transferTrack, List<OutCar> cars, Integer averageDays, Integer averageHours, int totalCars) {}

    /** @param origin YARD or SHOP: where its last move out was from (the later of its two time stamps) */
    public record OutCar(String origin, String track, int position, String car, String leftEnd, String rightEnd, String status,
                         boolean incident, String dateIn, String timeIn, String reason, int elapsedDays, int elapsedHours, int elapsedMinutes) {}
}
