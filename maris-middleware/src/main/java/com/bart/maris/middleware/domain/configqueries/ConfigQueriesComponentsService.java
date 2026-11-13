package com.bart.maris.middleware.domain.configqueries;

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
 * Config component inquiry (old screen VHCF16I). Rules: MRSConfigurTrackQuery/RUIFunctions.egl VHCF16IS-INITIAL (lists),
 * VHCF16I-GET-DATA (VHCF16A), CommonFunctions VHCF14IS-STK-DASH (the stock number without its dashes). Read-only.
 * The old screen's equipment type list is bound to nothing, so it always searched every type; the API sends the type
 * when one is given.
 */
public class ConfigQueriesComponentsService {
    private static final String PKG = "MRSConfigurTrackQuery";

    private final Programs programs;
    private final List<String> homeYards;
    private final String limitMsg, noDataMsg, homeYardNeedsStock;

    public ConfigQueriesComponentsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        this.homeYards = ConfigQueriesEgl.listItems(screens, "VHCF16I", "Drop-down_List1");
        List<String> msg = ConfigQueriesEgl.assignedTexts(screens, "VHCF16I-GET-DATA", PKG, "MSG");
        this.limitMsg = msg.get(1).strip();
        this.noDataMsg = msg.get(2).strip();
        this.homeYardNeedsStock = msg.get(3).strip();
    }

    /** VHCF16IS-INITIAL: a list that fails is a warning, the others still load */
    public ConfigQueriesComponents.Options options() {
        List<String> warnings = new ArrayList<>(), types = new ArrayList<>(), stats = new ArrayList<>();
        List<ConfigQueriesComponents.Stock> stocks = new ArrayList<>();
        Programs.Outcome b = programs.attempt("VHCF16B", Map.of());
        if (b.ok()) for (Map<String, Object> r : rows(b.result(), "VHCF16B_DATA", "dataRow")) types.add(text(r, "cmpntEquipType"));
        else if (b.result().control().returnCode() != 100) warnings.add(b.error());
        Programs.Outcome c = programs.attempt("VHCF16C", Map.of());
        if (c.ok()) {
            for (Map<String, Object> r : rows(c.result(), "VHCF16C_DATA", "dataRow")) {
                Map<String, Object> d = sub(r, "stockDisplay");
                stocks.add(new ConfigQueriesComponents.Stock(text(d, "stockNoL"), text(d, "shortStockDesc")));
            }
        } else if (c.result().control().returnCode() != 100) warnings.add(c.error());
        Programs.Outcome d = programs.attempt("VHCF16D", Map.of());
        if (d.ok()) for (Map<String, Object> r : rows(d.result(), "VHCF16D_DATA", "dataRow")) stats.add(text(r, "serialStat"));
        else if (d.result().control().returnCode() != 100) warnings.add(d.error());
        return new ConfigQueriesComponents.Options(types, stocks, stats, homeYards, warnings);
    }

    /** VHCF16I-GET-DATA */
    public ConfigQueriesComponents.Result search(String stock, String serial, String status, String homeYard, String equipmentType, String sort) {
        String yard = nz(homeYard).toUpperCase(Locale.ROOT), stk = nz(stock);
        if (!yard.isEmpty() && stk.isEmpty()) throw new InvalidRequestException(homeYardNeedsStock);
        if (!yard.isEmpty() && !homeYards.contains(yard)) throw new InvalidRequestException("The home yard is one of " + String.join(", ", homeYards) + ".");
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("cmpntEquipType", nz(equipmentType));
        crit.put("dataLimitFlag", "N");
        crit.put("stockNo", stockNo(stk));
        crit.put("cmpntSerialNo", nz(serial));
        crit.put("serialStat", nz(status));
        crit.put("homeYrd", yard);
        String s = nz(sort);
        crit.put("sortIndex", s.equals("1") || s.equals("2") ? s : "");
        ProgramResult r = programs.run("VHCF16A", Map.of("VHCF16A_CRIT", crit));
        if (r.control().returnCode() == 100) return new ConfigQueriesComponents.Result(false, noDataMsg, List.of());
        List<ConfigQueriesComponents.Component> out = new ArrayList<>();
        for (Map<String, Object> row : rows(r, "VHCF16A_DATA", "dataRow")) {
            out.add(new ConfigQueriesComponents.Component(text(row, "carNo"), text(row, "homeYrd"), text(row, "cmpntEquipType"), text(row, "cmpntSerialNo"),
                    text(row, "cmpntTrackingCd"), text(row, "dateBuyRebuilt"), text(row, "emplNo"), text(row, "lastRemovedBy"), text(row, "manufSerialNo"),
                    text(row, "parentEquipType"), text(row, "parentPosnCd"), text(row, "parentSerialNo"), text(row, "parentStockNo"), text(row, "posnCd"),
                    text(row, "poNo"), text(row, "serialConfigdInd"), text(row, "serialStat"), text(row, "shopLoc"), text(row, "stockNo"),
                    number(row, "cmpntCumHrs")));
        }
        boolean limit = text(r.record("VHCF16A_CRIT"), "dataLimitFlag").equals("Y");
        return new ConfigQueriesComponents.Result(limit, limit ? limitMsg : null, out);
    }

    /** VHCF14IS-STK-DASH ("S"): "12-34-56789" -> "123456789"; a stock number without dashes is taken as it is */
    static String stockNo(String full) {
        if (full.length() == 11 && full.charAt(2) == '-' && full.charAt(5) == '-') return full.substring(0, 2) + full.substring(3, 5) + full.substring(6);
        return full;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> row, String group) {
        return row.get(group) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
