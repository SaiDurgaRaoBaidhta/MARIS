package com.bart.maris.middleware.domain.yardqueries;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shop status report (old screen VHYT25I). Rules: MrsYardTrackingQueries/RUIFunctions.egl VHYT25IS-INITIAL (the shop's
 * name), -GET-DATA and -REFORMAT (VHYT25A: time in the shop), VHYT30IS-GET-DATA and -REFORMAT (VHYT30A: cars out of the
 * yard), VHYT25IS-FINALCT (the grand total). The arithmetic is the old screen's: whole numbers, a month is 30 days, a year
 * 365, and the old fields' widths. Read-only.
 */
public class YardQueriesShopStatusService {
    private static final String PKG = "MrsYardTrackingQueries";
    /** the track VHYT25IS-REFORMAT does not time and VHYT30IS-GET-DATA asks for at Hayward */
    private static final String T24 = "T-24";

    private final Programs programs;
    private final Map<String, String> shops;
    private final String defaultShop, transferTrack;

    public YardQueriesShopStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        this.shops = YardQueriesEgl.pairs(screens, "VHYT25IS-INITIAL", PKG, "YRD-TRKG-YRD", "SHOP");
        this.defaultShop = screens.assignedText("VHYT25IS-INITIAL", PKG, "SHOP");
        this.transferTrack = screens.assignedText("VHYT25IS-REFORMAT", PKG, "TRANSFER-TRACK");
    }

    public YardQueriesShopStatus report(String yard) {
        String y = yard == null ? "" : yard.strip().toUpperCase(Locale.ROOT);
        if (!y.matches("[A-Z]{1,3}")) throw new InvalidRequestException("A yard is one to three letters, e.g. HY.");

        // VHYT25IS-GET-DATA / -REFORMAT
        ProgramResult r25 = programs.run("VHYT25A", Map.of("VHYT25A_CRIT", Map.of("yrdTrkgYrd", y)));
        List<YardQueriesShopStatus.Car> cars = new ArrayList<>();
        String runTime = "";
        long grandHours = 0;
        int timed = 0, total = 0;
        if (r25.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r25, "VHYT25A_DATA", "dataRow")) {
                if (runTime.isEmpty()) runTime = left(text(row, "currentTime"), 5);
                Integer days = null, hours = null;
                if (!text(row, "yrdSubLoc").equals(T24)) {
                    int d = (int) (number(row, "tsDd") + number(row, "tsMo") * 30 + number(row, "tsYr") * 365), h = (int) number(row, "tsHh");
                    if (number(row, "tsMm") > 30) h++;
                    if (h == 24) { h = 0; d++; }
                    d %= 1000;                                   // EDAYS-IN is NUM(3)
                    grandHours += d * 24L + h;
                    days = d; hours = h; timed++;
                }
                total++;
                cars.add(new YardQueriesShopStatus.Car(text(row, "yrdSubLoc"), (int) number(row, "posnNo"), text(row, "carNo"),
                        end(row, "lftOrientCd"), end(row, "rgtOrientCd"), text(row, "vehStat"), text(row, "trainIncdInd").equals("Y"),
                        text(row, "dateInShop"), hhmm(row, "timeInShop", "timeInHhmm"), text(row, "moveReasonDsc"), days, hours));
            }
        }
        Integer avgDays = null, avgHours = null;
        if (timed > 0) {
            long avg = grandHours / timed;
            avgDays = (int) (avg / 24 % 1000);                   // AVG_DAYS is NUM(3)
            avgHours = (int) ((avg - avgDays * 24L) % 100);
        }

        // VHYT30IS-GET-DATA / -REFORMAT
        Map<String, Object> crit30 = new LinkedHashMap<>();
        crit30.put("yrdTrkgYrd", y);
        crit30.put("yrdSubLoc", y.equals("HY") ? T24 : "");
        ProgramResult r30 = programs.run("VHYT30A", Map.of("VHYT30A_CRIT", crit30));
        List<YardQueriesShopStatus.OutCar> out = new ArrayList<>();
        long outHours = 0;
        int onShop = 0;
        if (r30.control().returnCode() != 100) {
            for (Map<String, Object> row : rows(r30, "VHYT30A_DATA", "dataRow")) {
                boolean fromYard = number(row, "tsyDt") < number(row, "tsDt") || number(row, "tsDt") == 0;
                String p = fromYard ? "tsy" : "ts";
                int d = (int) ((number(row, p + "Dd") + number(row, p + "Mo") * 30 + number(row, p + "Yr") * 365) % 1000);
                int h = (int) (number(row, p + "Hh") % 100), m = (int) (number(row, p + "Mm") % 100);
                outHours += d * 24L + h;
                if (left(text(row, "yrdSubLoc"), 6).strip().equals("SHOP")) onShop++;
                out.add(new YardQueriesShopStatus.OutCar(fromYard ? "YARD" : "SHOP", text(row, "yrdSubLoc"), (int) number(row, "posnNo"),
                        text(row, "carNo"), end(row, "lftOrientCd"), end(row, "rgtOrientCd"), text(row, "vehStat"),
                        text(row, "trainIncdInd").equals("Y"), text(row, fromYard ? "dateOutYrd" : "dateOutShop"),
                        fromYard ? hhmm(row, "timeOutYrd", "yTimeOutHhmm") : hhmm(row, "timeOutShop", "timeOutHhmm"),
                        text(row, "moveReasonDsc"), d, h, m));
            }
        }
        Integer outDays = null, outAvgHours = null;
        if (!out.isEmpty()) {
            long avg = outHours / out.size();
            outDays = (int) (avg / 24 % 100);                    // VHYT30IW AVG_DAYS is NUM(2)
            outAvgHours = (int) ((avg - outDays * 24L) % 100);
        }
        YardQueriesShopStatus.OutOfYard outOfYard = new YardQueriesShopStatus.OutOfYard(y.equals("HY") ? transferTrack.strip() : null,
                out, outDays, outAvgHours, out.size());
        return new YardQueriesShopStatus(y, shops.getOrDefault(y, defaultShop), runTime, cars, avgDays, avgHours, total, outOfYard, total + onShop);
    }

    /** the orientation mark when the car stands that way round (not for a Z car) */
    @SuppressWarnings("unchecked")
    private static String end(Map<String, Object> row, String code) {
        Object type = row.get("carType");
        String letter = type instanceof Map<?, ?> t ? text((Map<String, Object>) t, "carLetter") : "";
        if (letter.equals("Z")) return "";
        String o = text(row, "vagenOrientation");
        boolean isLeft = o.equals(text(row, "lftOrientCd"));
        if (code.equals("lftOrientCd")) return isLeft ? o : "";
        return !isLeft && o.equals(text(row, "rgtOrientCd")) ? o : "";
    }

    @SuppressWarnings("unchecked")
    private static String hhmm(Map<String, Object> row, String group, String field) {
        Object g = row.get(group);
        return g instanceof Map<?, ?> m ? text((Map<String, Object>) m, field) : left(text(row, group), 5);
    }

    private static String left(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
