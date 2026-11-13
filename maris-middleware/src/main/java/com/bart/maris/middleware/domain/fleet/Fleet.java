package com.bart.maris.middleware.domain.fleet;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The fleet as the UI expects it (maris-ui src/api/openapi.yaml, schema Fleet).
 *
 * @param capacityKnown always false: no MARIS REST service returns all track capacities (MRS_YRD_SUB_LOC.CNT_CARS_MAX;
 *                      VHYT16A has the query but is not deployed as a service), so {@code Track.cap} is the last
 *                      occupied position
 */
public record Fleet(Map<String, Yard> yards, List<String> mapYards, Map<String, List<Track>> fleet, boolean capacityKnown) {

    public record Yard(String code, String name, String city, String pseudo, boolean shop) {}

    /** @param cars one entry per place on the track, {@code null} for an empty place */
    public record Track(String id, int cap, @JsonInclude(JsonInclude.Include.ALWAYS) List<Car> cars) {}

    /**
     * @param home    home yard, as the UI's yard code when it is one of the UI's yards
     * @param from    not in MARIS's yard tracking data; always empty
     * @param reason  move reason code (MRS_YRD_TRKG.MOVE_REASON_CD)
     * @param consist consist number (MRS_YRD_TRKG.CON_NO)
     */
    public record Car(String car, String home, String stat, String from, String reason, String consist) {}
}
