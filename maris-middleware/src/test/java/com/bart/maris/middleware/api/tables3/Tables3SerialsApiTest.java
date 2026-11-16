package com.bart.maris.middleware.api.tables3;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.tables3.Tables3Egl;
import com.bart.maris.middleware.support.ApiTestBase;
import com.bart.maris.middleware.support.Parity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Component serials (old VHCF49I list, VHCF50I detail, VHCF51I hours, VHCF52I geo data): the clean API over HTTP with a
 * fake backend. The old texts are read from the EGL here too.
 */
class Tables3SerialsApiTest extends ApiTestBase {
    private static final List<String> LIST = Tables3Egl.messages(Parity.bundle(), "VHCCS49I_LIST", "MESG1");
    private static final List<String> SAVE = Tables3Egl.messages(Parity.bundle(), "VHCF50IS_SAVE", "MESG1");
    private final AtomicReference<Map<String, Object>> sent = new AtomicReference<>();

    @Test
    void theStockListIsVhcf49b() throws Exception {
        backend().on("VHCF49B", in -> reply(0, 1, Map.of("VHCF49B_DATA", rows("dataRow", List.of(Map.of("stkDesc", Map.of("stockNo", "123456789", "stockShortDesc", "TRUCK ASSY")))))));
        Answer a = get("/api/v1/tables-3/component-serials/options", null);
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/stocks/0/stock").asText()).isEqualTo("123456789");
        assertThat(a.body().at("/stocks/0/text").asText()).isEqualTo("123456789 TRUCK ASSY");
    }

    @Test
    void listingAndItsMessages() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 85; i++) rows.add(Map.of("stockNo", "123456789", "cmpntSerialNo", "SN" + i, "carNo", "1101"));
        backend().on("VHCF49A", in -> { sent.set(in.get("VHCF49A_CRIT")); return reply(0, 90, Map.of("VHCF49A_DATA", rows("dataRow", rows))); });
        Answer a = get("/api/v1/tables-3/component-serials", Map.of("stock", "ALL"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCF49A");                                      // no car: no VHCF49E
        assertThat(sent.get()).containsEntry("toStockNo", "999999999").containsEntry("carNoTo", "9999").containsEntry("toSerialNo", "999999999999");
        assertThat(a.body().get("rows")).hasSize(85);
        assertThat(a.body().get("message").asText()).isEqualTo(LIST.get(1));                  // the old "more than 90 rows"

        backend().on("VHCF49A", in -> reply(100, 0, Map.of()));
        assertThat(get("/api/v1/tables-3/component-serials", Map.of("serial", "X")).body().get("message").asText()).isEqualTo(LIST.get(2));

        backend().on("VHCF49E", in -> reply(8, 0, Map.of()));
        Answer bad = get("/api/v1/tables-3/component-serials", Map.of("car", "9999"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("detail").asText()).isEqualTo(LIST.get(0));
    }

    private static Map<String, Object> original() {
        Map<String, Object> m = new HashMap<>();
        m.put("stockNo", "123456789");
        m.put("cmpntSerialNo", "SN1");
        m.put("dateAxle", "2020-01-02");
        m.put("dateInstalled", "2021-03-04");
        m.put("parentStockNo", "987654321");
        m.put("subJobDocNo", 3);
        return m;
    }

    @Test
    void theFormShowsDatesAsTheOldScreen() throws Exception {
        backend().on("VHCF50I", in -> { sent.set(in.get("VHCF50I_CRIT")); return reply(0, 1, Map.of("VHCF50I_DATA", Map.of("comments", "OLD"))); });
        for (String l : List.of("VHCF50C", "VHCF50E", "VHCF50F", "VHCF50G", "VHCF50H", "VHCF50J", "VHCF50K", "VHCF50L", "VHCF50M")) backend().on(l, in -> reply(100, 0, Map.of()));
        backend().on("VHCF50B", in -> reply(0, 1, Map.of("VHCF50B_DATA", rows("dataRow", List.of(Map.of("yrd", "CY"))))));
        Answer a = post("/api/v1/tables-3/component-serials/form", original());
        assertThat(a.status()).isEqualTo(200);
        assertThat(called()).containsExactly("VHCF50B", "VHCF50C", "VHCF50E", "VHCF50F", "VHCF50G", "VHCF50H", "VHCF50J", "VHCF50K", "VHCF50L", "VHCF50M", "VHCF50I");
        assertThat(sent.get()).containsEntry("stockNo", "123456789").containsEntry("cmpntSerialNo", "SN1");
        assertThat(a.body().at("/form/dateAxle").asText()).isEqualTo("01-02-2020");
        assertThat(a.body().get("comments").asText()).isEqualTo("OLD");
        assertThat(a.body().at("/lists/VHCF50B/0/code").asText()).isEqualTo("CY");
    }

    @Test
    void anUpdateChecksChangedDatesThenSaves() throws Exception {
        List<String> dates = new ArrayList<>();
        backend().on("VHCF50D", in -> { dates.add(String.valueOf(in.get("VHCF50D_DATA").get("dateMaintUpd"))); return reply(0, 0, Map.of()); });
        backend().on("VHCF50A", in -> { sent.set(in.get("VHCF50A_DATA")); return reply(0, 0, Map.of()); });
        Map<String, Object> form = new HashMap<>(original());
        form.put("dateAxle", "01-05-2020");                                                   // changed
        form.put("dateInstalled", "03-04-2021");                                              // the same date as shown
        Answer a = post("/api/v1/tables-3/component-serials/update", Map.of("original", original(), "form", form, "comments", "NEW"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(dates).containsExactly("2020-01-05");
        assertThat(sent.get()).containsEntry("dateAxle", "2020-01-05").containsEntry("parentStockNo", "987654321").containsEntry("comments", "NEW");
        assertThat(a.body().get("message").asText()).isEqualTo(SAVE.get(1));
    }

    @Test
    void anUpdateWithoutStockOrFailingIsRefused() throws Exception {
        Map<String, Object> form = new HashMap<>(original());
        form.put("stockNo", "");
        Answer a = post("/api/v1/tables-3/component-serials/update", Map.of("original", original(), "form", form, "comments", ""));
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body().get("message").asText()).isEqualTo(SAVE.get(0));
        assertThat(called()).isEmpty();

        backend().on("VHCF50A", in -> reply(8, 0, Map.of()));
        Answer f = post("/api/v1/tables-3/component-serials/update", Map.of("original", original(), "form", original(), "comments", ""));
        assertThat(f.status()).isEqualTo(422);
        assertThat(f.body().get("message").asText()).isEqualTo(SAVE.get(2));
    }

    @Test
    void hoursAndGeoData() throws Exception {
        backend().on("VHCF51A", in -> { sent.set(in.get("VHCF51A_CRIT")); return reply(0, 1, Map.of("VHCF51A_DATA", rows("dataRow", List.of(Map.of("cmpntSerialNo", "SN1", "cmpntCumHrs", 12))))); });
        Answer h = get("/api/v1/tables-3/serial-hours", Map.of("stock", "123456789", "serial", "SN1"));
        assertThat(sent.get()).containsEntry("frStockNo", "123456789").containsEntry("toSerialNo", "SN1");
        assertThat(h.body().at("/rows/0/cmpntCumHrs").asInt()).isEqualTo(12);
        get("/api/v1/tables-3/serial-hours", null);
        assertThat(sent.get()).containsEntry("frStockNo", "AAAAAAAAA").containsEntry("toStockNo", "999999999");   // every serial

        backend().on("VHCF52C", in -> reply(0, 1, Map.of("VHCF52C_DATA", rows("dataRow", List.of(Map.of("yrd", "HY"))))));
        backend().on("VHCF52A", in -> { sent.set(in.get("VHCF52A_CRIT")); return reply(0, 1, Map.of("VHCF52A_DATA", rows("dataRow", List.of(Map.of("geoDataName", "WHEEL"))))); });
        Answer g = get("/api/v1/tables-3/serial-geo-data", Map.of("stock", "123456789", "serial", "SN1"));
        assertThat(sent.get()).containsEntry("divideSw", "1");
        assertThat(g.body().at("/locations/0").asText()).isEqualTo("HY");
        assertThat(g.body().at("/rows/0/geoDataName").asText()).isEqualTo("WHEEL");

        backend().on("AFCXVLD", in -> reply(0, 0, Map.of("AFCXVLD_CRIT", Map.of("dateError", "N", "otherSqlError", "N"))));
        backend().on("VHCF51B", in -> { sent.set(in.get("VHCF51B_DATA")); return reply(0, 0, Map.of()); });
        Answer u = post("/api/v1/tables-3/serial-hours/update", Map.of("stock", "123456789", "serial", "SN1", "cumulativeHours", 13, "cumulativeMinutes", 5,
                "datePosted", "03-04-2024"));
        assertThat(u.status()).isEqualTo(200);
        assertThat(sent.get()).containsEntry("dateHrsPosted", "2024-03-04").containsEntry("cmpntCumHrs", 13L);
    }
}
