package com.bart.maris.middleware.domain.tables3;

import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A component serial's hours (old VHCF51I) and geo data (VHCF52I), both opened from VHCF49I for the selected row or for
 * every serial. Rules: MRSTableMaintenanceApp3/RUIFunctions.egl SERIAL-NO-HRS and SERIAL-GEO-DAT (the ranges),
 * VHCF51IS_INIT (VHCF51A), VHCF51IS_UPDATE (AFCXVLD, VHCF51B), VHCF52IS_INIT (VHCF52C, VHCF52A), VHCF52IS_UPDATE
 * (AFCXVLD, VHCF52B), MRSCommonCode AFCXVLD-VALIDATE.
 *
 * The old screens enable Update only when VHCF49I was opened from MRS14I, which is none of the 215 screens (their
 * menus open it from VHMVSI, VHMSSI or VHMEN), so there the two updates could never run; VHCF52A gets DIVIDE-SW for that
 * case. The update rules are kept as the screens have them.
 */
public class Tables3SerialDataService {
    private static final String HRS_INIT = "VHCF51IS_INIT", HRS_UPD = "VHCF51IS_UPDATE", GEO_INIT = "VHCF52IS_INIT", GEO_UPD = "VHCF52IS_UPDATE";

    private final Programs programs;
    private final String stockFrom, stockTo, serialFrom, serialTo, divide;
    private final String hrsNoData, hrsBadDate, hrsSaved, hrsFailed, geoTooMany, geoNoData, geoBadDate, geoSaved, geoFailed;
    private final int geoLimit;

    public Tables3SerialDataService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        stockFrom = Tables3Egl.assigned(screens, "SERIAL-NO-HRS", "FR-STOCK-NO").get(0);
        stockTo = Tables3Egl.assigned(screens, "SERIAL-NO-HRS", "TO-STOCK-NO").get(0);
        serialFrom = Tables3Egl.assigned(screens, "SERIAL-NO-HRS", "FR-SERIAL-NO").get(0);
        serialTo = Tables3Egl.assigned(screens, "SERIAL-NO-HRS", "TO-SERIAL-NO").get(0);
        List<String> sw = Tables3Egl.assigned(screens, GEO_INIT, "DIVIDE-SW");
        divide = sw.get(sw.size() - 1);                                               // the else branch: not opened from MRS14I
        hrsNoData = Tables3Egl.messages(screens, HRS_INIT, "MESG1").get(0);
        List<String> hu = Tables3Egl.messages(screens, HRS_UPD, "MESG1");
        hrsBadDate = hu.get(0);
        hrsSaved = hu.get(1);
        hrsFailed = hu.get(2);
        List<String> gi = Tables3Egl.messages(screens, GEO_INIT, "MESG1");
        geoTooMany = gi.get(0);
        geoNoData = gi.get(1);
        geoLimit = Integer.parseInt(geoTooMany.replaceAll("\\D+", ""));
        List<String> gu = Tables3Egl.messages(screens, GEO_UPD, "MESG1");
        geoBadDate = gu.get(0);
        geoSaved = gu.get(1);
        geoFailed = gu.get(2);
    }

    /** VHCF51IS_INIT: the serial's hours rows (blank stock and serial: every serial) */
    public Tables3Serials.Rows hours(String stock, String serial) {
        ProgramResult r = programs.attempt("VHCF51A", Map.of("VHCF51A_CRIT", range(stock, serial, null))).result();
        if (!ok(r)) return new Tables3Serials.Rows(List.of(), hrsNoData);
        return new Tables3Serials.Rows(rowsOf(r, "VHCF51A_DATA"), null);
    }

    /** VHCF52IS_INIT: the locations (VHCF52C) and the serial's geo data rows (VHCF52A) */
    public Map<String, Object> geoData(String stock, String serial) {
        // the old location list's selection index is bound to VHCF52C_CRIT.YRD-INDEX, so it goes out as "0"
        Programs.Outcome c = programs.attempt("VHCF52C", Map.of("VHCF52C_CRIT", Map.of("yrdIndex", "0")));
        List<String> locations = new ArrayList<>();
        if (c.ok()) for (Map<String, Object> row : rows(c.result(), "VHCF52C_DATA", "dataRow")) locations.add(text(row, "yrd"));
        ProgramResult r = programs.attempt("VHCF52A", Map.of("VHCF52A_CRIT", range(stock, serial, divide))).result();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("locations", locations);
        if (!ok(r)) {
            out.put("rows", List.of());
            out.put("message", geoNoData);
            return out;
        }
        out.put("rows", rowsOf(r, "VHCF52A_DATA"));
        int count = r.control() == null ? 0 : r.control().rowCount();
        if (count >= geoLimit) out.put("message", geoTooMany);
        return out;
    }

    /** VHCF51IS_UPDATE: the date posted (MM-DD-YYYY) to YYYY-MM-DD and checked by AFCXVLD, then VHCF51B */
    public Tables3Serials.Result updateHours(Tables3Serials.HoursChange c) {
        String date = nz(c.datePosted());
        Map<String, Object> data = new LinkedHashMap<>();
        if (!date.isEmpty()) {
            date = Tables3SerialService.ymd(date);
            data.put("dtConv1", date);
            if (!dateValid(date)) return new Tables3Serials.Result(false, hrsBadDate, null);
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("stockNo", nz(c.stock()));
        crit.put("cmpntSerialNo", nz(c.serial()));
        data.put("stockNo", nz(c.stock()));
        data.put("cmpntSerialNo", nz(c.serial()));
        data.put("cmpntCumHrs", c.cumulativeHours() == null ? 0 : c.cumulativeHours());
        data.put("cmpntCumMin", c.cumulativeMinutes() == null ? 0 : c.cumulativeMinutes());
        data.put("dateHrsPosted", date);
        data.put("termLastUpd", "");                                                  // the old pass value never gets them
        data.put("termMaintUpd", "");
        Programs.Outcome o = programs.attempt("VHCF51B", Map.of("VHCF51B_CRIT", crit, "VHCF51B_DATA", data));
        return o.ok() ? new Tables3Serials.Result(true, hrsSaved, null) : new Tables3Serials.Result(false, hrsFailed, o.error());
    }

    /** VHCF52IS_UPDATE: the date measured (MM-DD-YYYY) to YYYY-MM-DD and checked by AFCXVLD, then VHCF52B */
    public Tables3Serials.Result updateGeo(Tables3Serials.GeoChange c) {
        String date = nz(c.dateMeasured());
        Map<String, Object> data = new LinkedHashMap<>();
        if (!date.isEmpty()) {
            date = Tables3SerialService.ymd(date);
            data.put("dtConv1", date);
            if (!dateValid(date)) return new Tables3Serials.Result(false, geoBadDate, null);
        }
        BigDecimal measurement;
        try {
            measurement = nz(c.measurement()).isEmpty() ? BigDecimal.ZERO : new BigDecimal(nz(c.measurement()));
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("The measurement is a number.");
        }
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("stockNo", nz(c.stock()));
        crit.put("cmpntSerialNo", nz(c.serial()));
        data.put("geoDataName", nz(c.geoDataName()));
        data.put("measuredBy", nz(c.measuredBy()));
        data.put("timeMeasured", nz(c.timeMeasured()));
        data.put("measurement", measurement);
        data.put("inspectorEmplNo", nz(c.inspector()));
        data.put("loc", nz(c.location()));
        data.put("posnCd", nz(c.position()));
        data.put("carNo", nz(c.car()));
        data.put("dateMeasured", date);
        data.put("termLastUpd", "");
        data.put("termMaintUpd", "");
        Programs.Outcome o = programs.attempt("VHCF52B", Map.of("VHCF52B_CRIT", crit, "VHCF52B_DATA", data));
        return o.ok() ? new Tables3Serials.Result(true, geoSaved, null) : new Tables3Serials.Result(false, geoFailed, o.error());
    }

    /** AFCXVLD-VALIDATE: AFCXVLD answers DATE-ERROR "Y" (and no SQL error) for a wrong date */
    private boolean dateValid(String yyyymmdd) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("dateError", "N");
        crit.put("formDate", Tables3SerialService.ymd(yyyymmdd));
        crit.put("otherSqlError", "N");
        Map<String, Object> back = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result().record("AFCXVLD_CRIT");
        return !("Y".equals(text(back, "dateError")) && "N".equals(text(back, "otherSqlError")));
    }

    /** SERIAL-NO-HRS / SERIAL-GEO-DAT: the selected row's stock and serial, or every one */
    private Map<String, Object> range(String stock, String serial, String divideSw) {
        String s = nz(stock), n = nz(serial);
        boolean every = s.isEmpty() && n.isEmpty();
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("frStockNo", every ? stockFrom : s);
        crit.put("toStockNo", every ? stockTo : s);
        crit.put("frSerialNo", every ? serialFrom : n);
        crit.put("toSerialNo", every ? serialTo : n);
        crit.put("counter", 0);
        if (divideSw != null) crit.put("divideSw", divideSw);
        return crit;
    }

    private static boolean ok(ProgramResult r) {
        int urc = r.control() == null ? 0 : r.control().returnCode();
        return urc == 0 || urc == 4;
    }

    private static List<Map<String, Object>> rowsOf(ProgramResult r, String record) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, record, "dataRow")) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.forEach((k, v) -> m.put(k, v instanceof String s ? s.strip() : v));
            out.add(m);
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
