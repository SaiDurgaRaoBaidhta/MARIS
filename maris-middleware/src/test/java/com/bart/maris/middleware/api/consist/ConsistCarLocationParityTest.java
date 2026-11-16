package com.bart.maris.middleware.api.consist;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.consist.ConsistCarLocationService;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.Step;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Move a car to another location: the clean API against the old VHCY01I run by the engine, same backend, same steps. */
class ConsistCarLocationParityTest {
    static final String SCREEN = "VHCY01I";

    static Parity backend(String whereIsCar, String carType, String sameYard, String maxPos) {
        Parity p = new Parity();
        p.on("VHCY1A1", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 3), "VHCY1A1_DATA", Map.of("vagenIndex", 3, "dataRow", List.of(
                Map.of("loc", "RY", "vagenTable", "YRD"), Map.of("loc", "CY", "vagenTable", "YRD"), Map.of("loc", "HYS", "vagenTable", "SHP")))));
        Map<String, Object> car = new HashMap<>(Map.of("carNo", "1101", "carType", carType, "currLoc", "CY", "whereIsCar", whereIsCar, "yrdSubLoc", "",
                "posnNo", 0, "shopLocInd", "N", "yrdLocInd", "N", "transferTrkInd", "N", "softVer", ""));
        car.put("conNo", "201");
        car.put("dashNo", 3);
        p.on("VHCY1A4", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHCY1A4_DATA", car));
        p.on("VHVM1A2", in -> Map.of("XDCONTROL", Map.of("urc", 0, "urowcnt", 1), "VHVM1A2_DATA", Map.of("carNo", "1101", "carType", carType,
                "whereIsCar", whereIsCar, "vehStat", "GO", "softVer", "")));
        p.on("VHCY1A2", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHCY1A2_CRIT"));
            crit.put("sameYrd", sameYard);
            crit.put("maxPos", maxPos);
            crit.put("maxPosn", 12);
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHCY1A2_CRIT", crit, "VHCY1A2_DATA", Map.of("yrdTrkgYrdNew", "RY"));
        });
        p.on("VHCY1A3", in -> {
            Map<String, Object> crit = new HashMap<>(in.get("VHCY1A3_CRIT"));
            crit.put("yrdSubLocDel", "PS-CY");
            crit.put("yrdSubLocAdd", "PS-RY");
            return Map.of("XDCONTROL", Map.of("urc", 0), "VHCY1A3_CRIT", crit);
        });
        return p;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "YRD|A2|N|N|RY",          // moved
            "YRD|A2|N|N|CY",          // the location it is at already
            "YRD|A2|N|N|SW",          // SW is not a location to move to
            "YRD|A2|N|N|XX",          // not in the locations list
            "YRD|A2|Y|N|RY",          // same yard: yard tracking must move it
            "YRD|A2|N|Y|RY",          // no room on the pseudo track
            "CON|A2|N|N|RY",          // in a consist: cannot be moved
            "YRD|D|N|N|RY",           // car type D is not moved
            "YRD|A2|N|N|''"})         // no new location
    void moveMatchesTheOldScreen(String where, String type, String same, String max, String location) {
        Parity p = backend(where, type, same, max);
        List<Step> steps = List.of(Step.type("CarText", "1101"), Step.leave("CarText"), Step.type("NewLoc", location), Step.leave("NewLoc"),
                Step.press("Update_btn"));
        ScreenSession s = ConsistRulesOld.old(p, SCREEN, steps, Map.of());
        String oldMessage = ConsistRulesOld.field(s, SCREEN, "VHCY01W-MSG", "MSG");
        var oldSent = ConsistRulesOld.byProgram(p.sent);
        p.clear();
        var move = new ConsistCarLocationService(ConsistRulesOld.screens(p)).move(p.user.groupId(), "1101", location);
        assertThat(ConsistRulesOld.byProgram(p.sent)).isEqualTo(oldSent);
        assertThat(move.message().strip()).isEqualTo(oldMessage);
    }

    @ParameterizedTest
    @CsvSource({"YRD", "CON", "SHP"})
    void lookupMatchesTheOldScreen(String where) {
        Parity p = backend(where, "A2", "N", "N");
        ScreenSession s = ConsistRulesOld.old(p, SCREEN, List.of(Step.type("CarText", "1101"), Step.leave("CarText")), Map.of());
        var oldSent = ConsistRulesOld.byProgram(p.sent);
        p.clear();
        var car = new ConsistCarLocationService(ConsistRulesOld.screens(p)).car(p.user.groupId(), "1101");
        assertThat(ConsistRulesOld.byProgram(p.sent)).isEqualTo(oldSent);
        assertThat(car.message()).isEqualTo(ConsistRulesOld.value(s, SCREEN, "txtMsg"));
        assertThat(car.currentLocation()).isEqualTo(ConsistRulesOld.value(s, SCREEN, "CurLoc"));
    }
}
