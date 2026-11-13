package com.bart.maris.middleware.domain.fleet;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every configured yard with its tracks and the cars on them, from legacy program VHSW01A
 * (System Wide Map: MRS_YRD_TRKG joined to MRS_VEH, ordered by track and position). Read-only.
 *
 * <p>MARIS has no track capacity, so a track is as long as its last occupied position.
 */
@Service
public class FleetService {
    private static final Logger log = LoggerFactory.getLogger(FleetService.class);
    static final String PROGRAM = "VHSW01A";
    /** VHSW01A_DATA.DATA_ROW occurs 500 times; a full page means cars were left out. */
    static final int MAX_ROWS = 500;
    /** URC 100: no rows (SQL "not found"). */
    static final int NOT_FOUND = 100;

    private final LegacyProgramService legacy;
    private final MessageResolver messages;
    private final List<MarisProperties.Yard> yards;

    public FleetService(LegacyProgramService legacy, MessageResolver messages, MarisProperties props) {
        this.legacy = legacy;
        this.messages = messages;
        this.yards = props.fleet().yards();
    }

    public Fleet fleet() {
        Map<String, String> uiCode = new LinkedHashMap<>();
        yards.forEach(y -> uiCode.put(y.legacyCode().strip(), y.code()));
        Map<String, Fleet.Yard> out = new LinkedHashMap<>();
        Map<String, List<Fleet.Track>> tracks = new LinkedHashMap<>();
        for (MarisProperties.Yard y : yards) {
            out.put(y.code(), new Fleet.Yard(y.code(), y.name(), y.city() == null ? y.name() : y.city(), y.legacyCode(), y.shop()));
            tracks.put(y.code(), tracks(y, uiCode));
        }
        return new Fleet(out, List.copyOf(out.keySet()), tracks, false);
    }

    @SuppressWarnings("unchecked")
    private List<Fleet.Track> tracks(MarisProperties.Yard yard, Map<String, String> uiCode) {
        ProgramResult r = legacy.execute(PROGRAM, Map.of("VHSW01A_CRIT", Map.of("yrdTrkgYrd", yard.legacyCode())), true);
        XdControl c = r.control();
        if (c != null && c.returnCode() == NOT_FOUND) return List.of();
        if (c != null && c.outcome() == XdControl.Outcome.ERROR) {
            throw new LegacyBusinessException(PROGRAM, c, messages.resolve(c.messageCode(), c.messageInserts()));
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.record("VHSW01A_DATA").getOrDefault("dataRow", List.of());
        int count = c == null ? rows.size() : Math.min(c.rowCount(), rows.size());
        if (count >= MAX_ROWS) log.warn("yard {} has more than {} cars; the rest are not shown", yard.legacyCode(), MAX_ROWS);

        Map<String, List<Fleet.Car>> byTrack = new LinkedHashMap<>();
        for (Map<String, Object> row : rows.subList(0, count)) {
            String car = s(row.get("carNo"));
            if (car.isEmpty()) continue;
            int pos = row.get("posnNo") instanceof Number n ? n.intValue() : 0;
            List<Fleet.Car> places = byTrack.computeIfAbsent(s(row.get("yrdSubLoc")), k -> new ArrayList<>());
            int at = Math.max(pos, 1) - 1;
            while (places.size() <= at) places.add(null);
            if (places.get(at) != null) at = places.size();          // two cars on one position: keep both
            if (at == places.size()) places.add(null);
            String home = s(row.get("homeYrd"));
            places.set(at, new Fleet.Car(car, uiCode.getOrDefault(home, home), s(row.get("vehStat")), "",
                    s(row.get("moveReasonCd")), s(row.get("conNo"))));
        }
        List<Fleet.Track> result = new ArrayList<>();
        byTrack.forEach((id, cars) -> result.add(new Fleet.Track(id, cars.size(), cars)));
        return result;
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString().strip();
    }
}
