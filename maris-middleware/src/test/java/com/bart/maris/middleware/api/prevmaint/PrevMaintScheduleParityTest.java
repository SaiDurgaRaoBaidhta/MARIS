package com.bart.maris.middleware.api.prevmaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintRecordText;
import com.bart.maris.middleware.domain.prevmaint.PrevMaintScheduleService;
import com.bart.maris.middleware.engine.screen.ScreenSession;
import com.bart.maris.middleware.support.Parity;
import com.bart.maris.middleware.support.TestCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PM scheduling (old VHPMS01I) against the old screen run by the engine: the records sent to VHPMSA3 / VHPMS, and the messages. */
class PrevMaintScheduleParityTest {
    static final String TERMINAL = "MARIS-UI";

    /** @param current the car's current scheduled PM; @param next its next one; @param pmsUrc what VHPMS answers */
    static Parity backend(String current, String next, String stat, int pmsUrc) {
        Parity p = new Parity();
        p.on("VHPMSA2", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHPMSA2_DATA", Map.of("carNo", "1101", "carType", "A2", "homeYrd", "HY")));
        p.on("VHPMSA1", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHPMSA1_DATA", Map.of("ciDesc", "PM INSPECTION " + in.get("VHPMSA1_CRIT").get("pmType"))));
        p.on("VHPMSA3", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHPMSA3_DATA", Map.of("carNo", "1101", "carType", "A2", "dispVehStat", Map.of("vehStat", stat),
                "currSchdPmNo", current, "nextSchdPmNo", next)));
        p.on("VHXREVA", in -> Map.of("XDCONTROL", Map.of("urc", 0), "VHXREVA_DATA", Map.of("revenueDate", "11-13-2026")));
        p.on("VHPMS", in -> Map.of("XDCONTROL", Map.of("urc", pmsUrc, "umsgcode", pmsUrc == 0 ? "" : "VH0001"),
                "VHPMS_DATA", Map.of("incdNo", "29990099", "noRecFound", pmsUrc == 0 ? "" : "Y")));
        return p;
    }

    static PrevMaintScheduleService api(Parity p) {
        return new PrevMaintScheduleService(p.legacy(), p.messages(), new EglTexts(Parity.bundle()), new PrevMaintRecordText(TestCatalog.get()), p.clock, TERMINAL);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> window(ScreenSession s, String screen) {
        for (Map<String, Object> w : (List<Map<String, Object>>) s.state().get("windows")) if (screen.equalsIgnoreCase((String) w.get("screen"))) return w;
        return null;
    }

    @SuppressWarnings("unchecked")
    static String value(ScreenSession s, String screen, String widget) {
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) window(s, screen).get("widgets")).get(widget);
        Object v = x == null ? null : x.get("value");
        return v == null ? "" : v.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static String dialog(ScreenSession s) {
        Map<String, Object> d = (Map<String, Object>) s.state().get("dialog");
        return d == null ? null : String.valueOf(d.get("message")).strip();
    }

    /** the old screen: car, PM type (leaving the field looks up the description), the option, then Save */
    static String old(ScreenSession s, Parity p, String car, String pmType, int option) {
        s.openRoot("VHPMS01I", p.user);
        String id = (String) window(s, "VHPMS01I").get("id");
        s.window(id).recordPart("VHPMS01IW").set("TERM-LAST-UPD data", TERMINAL);     // the menu passes the terminal id in
        if (option != 1) s.event(id, List.of(), "gpbPMOption", "select", option);
        s.event(id, List.of(new ScreenSession.Change("txtCarNo", "object", car), new ScreenSession.Change("txtPMType", "object", pmType)), "txtPMType", "losingFocus", null);
        s.event(id, List.of(), "pbtSave", "clicked", null);
        return id;
    }

    static Map<String, Object> withoutTime(Map<String, Object> rec) {
        if (rec == null) return null;
        Map<String, Object> m = new LinkedHashMap<>(rec);
        m.remove("timeIncdOccr");                                   // both sides read the clock, seconds apart
        m.remove("systemTime");
        return m;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1101|03|1|''|3 |GO|0",          // schedule
            "1101|C|2|A |C |GO|0",            // reschedule
            "1101|3|1|2 |3 |GO|0",            // already scheduled: reschedule instead
            "1101|3|2|''|3 |GO|0",            // reschedule with nothing scheduled
            "1101|3|1|''|4 |GO|0",            // not the next PM (1 - 6)
            "1101|A|1|''|B |GO|0",            // not the next PM (A - D)
            "1101|3|1|''|3 |GO|8",            // VHPMS finds no record
            "1101|3|1|''|3 |PM|0",            // car already in PM
            "''|3|1|''|3 |GO|0",              // no car
            "11A1|3|1|''|3 |GO|0",            // car not numeric
            "1101|''|1|''|3 |GO|0",           // no PM type
            "1101|Z|1|''|3 |GO|0"})           // PM type not in the list
    void saveMatchesTheOldScreen(String car, String pmType, int option, String current, String next, String stat, int pmsUrc) {
        Parity p = backend(current, next, stat, pmsUrc);
        ScreenSession s = p.oldScreens();
        old(s, p, car, pmType, option);
        String oldMessage = dialog(s);
        var oldA3 = p.lastSent("VHPMSA3");
        var oldPms = p.lastSent("VHPMS");
        var oldRevenue = withoutTime(p.lastSent("VHXREVA") == null ? null : p.lastSent("VHXREVA").get("VHXREVA_CRIT"));
        String footer = value(s, "VHPMS01I", "txtFooter");

        p.clear();
        String message;
        try {
            var r = api(p).save(car, pmType, option, p.user.groupId());
            message = r.message();
            assertThat(r.message()).isEqualTo(footer);
        } catch (InvalidRequestException e) {
            message = e.getMessage();
        } catch (LegacyBusinessException e) {
            message = "(backend error)";
        }
        if (pmsUrc != 0 && oldMessage == null) oldMessage = "(backend error)";
        assertThat(message).as("message").isEqualTo(oldMessage);
        assertThat(p.lastSent("VHPMSA3")).as("VHPMSA3").isEqualTo(oldA3);
        if (oldPms == null) assertThat(p.lastSent("VHPMS")).isNull();
        else {
            assertThat(p.lastSent("VHPMS").get("VHPMS_CRIT")).isEqualTo(oldPms.get("VHPMS_CRIT"));
            assertThat(withoutTime(p.lastSent("VHPMS").get("VHPMS_DATA"))).isEqualTo(withoutTime(oldPms.get("VHPMS_DATA")));
            var oldRev = oldPms == null ? null : oldRevenue;
            assertThat(withoutTime(p.lastSent("VHXREVA") == null ? null : p.lastSent("VHXREVA").get("VHXREVA_CRIT"))).as("VHXREVA").isEqualTo(oldRev);
        }
    }

    @ParameterizedTest
    @CsvSource({"1101,03", "1101,B", "1101,4"})
    void descriptionMatchesTheOldScreen(String car, String pmType) {
        Parity p = backend("", "3 ", "GO", 0);
        ScreenSession s = p.oldScreens();
        s.openRoot("VHPMS01I", p.user);
        String id = (String) window(s, "VHPMS01I").get("id");
        s.event(id, List.of(new ScreenSession.Change("txtCarNo", "object", car), new ScreenSession.Change("txtPMType", "object", pmType)), "txtPMType", "losingFocus", null);
        var oldA1 = p.lastSent("VHPMSA1");
        p.clear();
        var d = api(p).describe(car, pmType);
        assertThat(p.lastSent("VHPMSA1")).isEqualTo(oldA1);
        assertThat(d.description()).isEqualTo(value(s, "VHPMS01I", "Text1"));
        assertThat(d.pmType()).isEqualTo(value(s, "VHPMS01I", "txtPMType"));
    }
}
