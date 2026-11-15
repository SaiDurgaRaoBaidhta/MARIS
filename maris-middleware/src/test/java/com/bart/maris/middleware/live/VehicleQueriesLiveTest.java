package com.bart.maris.middleware.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.domain.vehiclequeries.VehicleQueriesBatchReportService;
import com.bart.maris.middleware.support.LiveTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vehicle queries, read-only, against the real backend: every batch report's start values equal what its old screen
 * shows when it opens. Hard Copy submits a real report, so it is covered by the API and parity tests only.
 */
class VehicleQueriesLiveTest extends LiveTestBase {
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    @Test
    void batchReportStartValuesMatchTheOldScreens() throws Exception {
        for (VehicleQueriesBatchReportService.Report r : VehicleQueriesBatchReportService.REPORTS) {
            JsonNode clean = send("GET", "/api/v1/vehicle-queries/batch-reports/" + r.key() + "/options", null);
            assertThat(clean.get("_status").asInt()).as(r.key()).isEqualTo(200);
            JsonNode widgets = send("POST", "/api/v1/ui/open", Map.of("screen", r.screen())).at("/windows/0/widgets");
            List<String> shown = new ArrayList<>();
            widgets.forEach(w -> { if (w.has("value")) shown.add(w.get("value").asText().strip()); });
            if (r.fromProgram() != null && !clean.get("fromDate").asText().isEmpty()) {
                assertThat(shown).as(r.key() + " from date").contains(MDY.format(LocalDate.parse(clean.get("fromDate").asText())));
                assertThat(shown).as(r.key() + " to date").contains(MDY.format(LocalDate.parse(clean.get("toDate").asText())));
            }
            if (r.yardProgram() != null) {
                List<String> oldItems = new ArrayList<>(), labels = new ArrayList<>();
                widgets.forEach(w -> { if (w.has("items") && w.get("type").asText().contains("DropDown")) w.get("items").forEach(i -> { if (!i.asText().isBlank()) oldItems.add(i.asText().strip()); }); });
                clean.get("yards").forEach(y -> labels.add(y.get("label").asText()));
                assertThat(labels).as(r.key() + " yards").isEqualTo(oldItems);
            }
        }
    }

    @Test
    void carStatusHistoryMatchesTheOldScreen() throws Exception {
        LocalDate to = LocalDate.now().minusDays(1), from = to.minusDays(60);
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;                                         // no car in the local data
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/car-status-history?car=" + car + "&from=" + from + "&to=" + to, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCSH1I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "txtCarNo", "attr", "object", "value", car), Map.of("widget", "txtFromDate", "attr", "object", "value", MDY.format(from)),
                        Map.of("widget", "txtToDate", "attr", "object", "value", MDY.format(to))), "widget", "pbtList", "event", "clicked"));
        List<String> oldDates = new ArrayList<>(), dates = new ArrayList<>();
        old.at("/windows/0/widgets/cntCarStatDetails/rows").forEach(r -> oldDates.add(r.get(1).asText() + " " + r.get(5).asText()));
        if (clean.get("_status").asInt() == 200) clean.get("changes").forEach(c -> dates.add(c.get("date").asText() + " " + c.get("statusNew").asText()));
        // the backend orders by date only, so two changes on one day may come back in either order between calls
        assertThat(dates).as("car " + car).containsExactlyInAnyOrderElementsOf(oldDates);
        assertThat(dates.stream().map(d -> d.substring(0, 10)).toList()).isEqualTo(oldDates.stream().map(d -> d.substring(0, 10)).toList());
    }

    @Test
    void homeYardFleetMatchesTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/home-yard-fleet", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode opened = send("POST", "/api/v1/ui/open", Map.of("screen", "VHHF01I"));
        JsonNode old = opened.at("/windows/0/widgets/cntCarsSummary/rows");
        assertThat(clean.get("rows").size()).isEqualTo(old.size());
        for (int i = 0; i < old.size(); i++) {
            List<String> mine = new ArrayList<>();
            JsonNode r = clean.get("rows").get(i);
            mine.add(r.get("name").asText());
            for (JsonNode y : clean.get("yards")) {
                r.get("yards").get(y.asText()).get("byType").forEach(n -> mine.add(n.asText()));
                mine.add(r.get("yards").get(y.asText()).get("total").asText());
            }
            List<String> theirs = new ArrayList<>();
            old.get(i).forEach(c -> theirs.add(c.asText().strip()));
            assertThat(mine).as("row " + (i + 1)).isEqualTo(theirs);
        }
    }

    @Test
    void carHoursMatchTheOldScreen() throws Exception {
        LocalDate to = LocalDate.now().minusDays(1), from = to.minusDays(60);
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/car-hours?car=" + car + "&from=" + from + "&to=" + to, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHCHR1I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "txtCarno", "attr", "object", "value", car), Map.of("widget", "txtFromdate", "attr", "object", "value", MDY.format(from)),
                        Map.of("widget", "txtTodate", "attr", "object", "value", MDY.format(to))), "widget", "pbtDisplay", "event", "clicked"));
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        if (clean.path("message").isMissingNode() || clean.path("message").isNull()) {
            assertThat(clean.get("hours").asText()).isEqualTo(old.at("/windows/0/widgets/txtHours/value").asText());
            assertThat(clean.get("minutes").asText()).isEqualTo(old.at("/windows/0/widgets/txtMin/value").asText());
        } else {
            assertThat(clean.get("message").asText()).isEqualTo(old.at("/dialog/message").asText().strip());
        }
    }

    @Test
    void fleetReportMatchesTheOldScreen() throws Exception {
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/fleet-report", null);
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode widgets = send("POST", "/api/v1/ui/open", Map.of("screen", "VHFR01I")).at("/windows/0/widgets");
        compare(clean.get("rows"), "name", widgets.at("/cntFleetSummary/rows"));
        compare(clean.get("goCarsByYard"), "yard", widgets.at("/cntAvailGoCars/rows"));
    }

    private static void compare(JsonNode mine, String label, JsonNode old) {
        assertThat(mine.size()).isEqualTo(old.isMissingNode() ? 0 : old.size());
        for (int i = 0; i < mine.size(); i++) {
            List<String> a = new ArrayList<>(), b = new ArrayList<>();
            a.add(mine.get(i).get(label).asText());
            mine.get(i).at("/cells/byType").forEach(n -> a.add(n.asText()));
            a.add(mine.get(i).at("/cells/total").asText());
            old.get(i).forEach(c -> b.add(c.asText().strip()));
            assertThat(a).as(label + " row " + (i + 1)).isEqualTo(b);
        }
    }

    @Test
    void repeaterCarsMatchTheOldScreens() throws Exception {
        JsonNode o = send("GET", "/api/v1/vehicle-queries/repeater-cars/options", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHRC01I"));
        List<String> oldYards = new ArrayList<>(), yards = new ArrayList<>();
        open.at("/windows/0/widgets/ddlHomeYard/items").forEach(i -> { if (!i.asText().isBlank()) oldYards.add(i.asText().strip()); });
        o.get("homeYards").forEach(y -> yards.add(y.asText()));
        assertThat(yards).isEqualTo(oldYards);
        if (yards.size() < 2) return;
        String yard = yards.get(0);
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/repeater-cars?homeYard=" + yard, null);
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "ddlHomeYard", "attr", "selectedItem", "value", yard)), "widget", "pbtDisplay", "event", "clicked"));
        JsonNode list = null;
        for (JsonNode w : old.get("windows")) if (w.get("screen").asText().equalsIgnoreCase("VHRC02I")) list = w;
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(list).as("VHRC02I opened").isNotNull();
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        clean.get("incidents").forEach(i -> a.add(i.get("car").asText() + " " + i.get("incident").asText()));
        list.at("/widgets/cntIncdreprdet/rows").forEach(r -> b.add(r.get(0).asText().strip() + " " + r.get(3).asText().strip()));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void homeYardStatusMatchesTheOldScreens() throws Exception {
        JsonNode o = send("GET", "/api/v1/vehicle-queries/home-yard-status/options", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHYS01I"));
        List<String> oldYards = new ArrayList<>(), yards = new ArrayList<>();
        open.at("/windows/0/widgets/cboYard1/items").forEach(i -> { if (!i.asText().isBlank()) oldYards.add(i.asText().strip()); });
        o.get("yards").forEach(y -> yards.add(y.asText()));
        assertThat(yards).isEqualTo(oldYards);
        if (yards.isEmpty()) return;
        String yard = yards.get(0);
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/home-yard-status/cars?option=base&narr=N&yard=" + yard, null);
        String w = open.at("/windows/0/id").asText();
        send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of("changes", List.of(Map.of("widget", "tbnHomeYardBase", "attr", "selection", "value", true))));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of(
                "changes", List.of(Map.of("widget", "cboYard1", "attr", "entryObject", "value", yard)), "widget", "pbtDisplay", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().equalsIgnoreCase("VHYS02I")) report = x;
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(report).as("VHYS02I opened").isNotNull();
        String first = clean.path("cars").path(0).path("car").asText("");
        assertThat(first).isEqualTo(report.at("/widgets/txtCarNo/value").asText("").strip());
    }

    @Test
    void homeYardSummaryMatchesTheOldScreens() throws Exception {
        JsonNode o = send("GET", "/api/v1/vehicle-queries/home-yard-status/options", null);
        if (o.get("yards").isEmpty()) return;
        String yard = o.get("yards").get(0).asText();
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/home-yard-status/summary?option=summary&yard=" + yard, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHYS01I"));
        String w = open.at("/windows/0/id").asText();
        send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of("changes", List.of(Map.of("widget", "tbnHomeYardSummary", "attr", "selection", "value", true))));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of(
                "changes", List.of(Map.of("widget", "cboYard4", "attr", "entryObject", "value", yard)), "widget", "pbtDisplay", "event", "clicked"));
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().equalsIgnoreCase("VHYS04I")) report = x;
        if (report == null) {                                         // no cars: the old screen shows its message instead
            assertThat(clean.path("message").asText()).isEqualTo(old.at("/dialog/message").asText().strip());
            return;
        }
        List<String> theirs = new ArrayList<>(), mine = new ArrayList<>();
        report.at("/widgets/cntIncdDetails/rows").forEach(r -> { for (int k = 0; k < 8; k += 4) if (!r.get(k).asText().isBlank()) theirs.add(r.get(k).asText().replace("*", "").strip()); });
        clean.get("cars").forEach(c -> mine.add(c.get("car").asText()));
        assertThat(mine).isEqualTo(theirs);
    }

    @Test
    void yardStatusMatchesTheOldScreens() throws Exception {
        JsonNode o = send("GET", "/api/v1/vehicle-queries/yard-status/options", null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHYS06I"));
        List<String> oldYards = new ArrayList<>(), yards = new ArrayList<>();
        open.at("/windows/0/widgets/cboYardBase/items").forEach(i -> { if (!i.asText().isBlank()) oldYards.add(i.asText().strip()); });
        o.get("yards").forEach(y -> yards.add(y.asText()));
        assertThat(yards).isEqualTo(oldYards);
        if (yards.isEmpty()) return;
        String yard = yards.get(0);
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/yard-status/summary?option=summary&yard=" + yard, null);
        String w = open.at("/windows/0/id").asText();
        send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of("changes", List.of(Map.of("widget", "tbnOptYardSummary", "attr", "selection", "value", true))));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + w + "/events", Map.of(
                "changes", List.of(Map.of("widget", "cboYardSummary", "attr", "entryObject", "value", yard)), "widget", "pbtDisplay", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().equalsIgnoreCase("VHYS09I")) report = x;
        assertThat(report).as("VHYS09I opened").isNotNull();
        assertThat(clean.get("header").asText()).isEqualTo(report.at("/widgets/lblHeader/value").asText().strip());
        List<String> theirs = new ArrayList<>(), mine = new ArrayList<>();
        report.at("/widgets/cntIncdDetails/rows").forEach(r -> { for (int k = 0; k < 8; k += 4) if (!r.get(k).asText().isBlank()) theirs.add(r.get(k).asText().replace("*", "").strip()); });
        clean.get("cars").forEach(c -> mine.add(c.get("car").asText()));
        assertThat(mine).isEqualTo(theirs);
    }

    @Test
    void vehicleStatusMatchesTheOldScreens() throws Exception {
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;
        JsonNode o = send("GET", "/api/v1/vehicle-queries/vehicle-status/options", null);
        String from = o.get("fromDate").asText(), to = o.get("toDate").asText();
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/vehicle-status?report=1&car=" + car + "&fromDate=" + from + "&toDate=" + to, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSV01I"));
        assertThat(open.at("/windows/0/widgets/txtFromdate/value").asText().strip()).isEqualTo(from.isEmpty() ? "" : MDY.format(LocalDate.parse(from)));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "txtCarno", "attr", "object", "value", car)), "widget", "pbtDisplay", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().equalsIgnoreCase("VHSV02I")) report = x;
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(report).as("VHSV02I opened").isNotNull();
        List<String> theirs = new ArrayList<>(), mine = new ArrayList<>();
        report.at("/widgets/cntIncdreprdet/rows").forEach(r -> theirs.add(r.get(0).asText().strip()));
        clean.get("incidents").forEach(i -> mine.add(i.get("incident").asText()));
        // the old screen shows one incident at a time (Prv / Nxt); it is the first of the list
        if (!theirs.isEmpty()) assertThat(mine.get(0)).isEqualTo(theirs.get(0));
        else assertThat(mine).isEmpty();
    }

    @Test
    void vehicleHistoryMatchesTheOldScreens() throws Exception {
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;
        JsonNode o = send("GET", "/api/v1/vehicle-queries/vehicle-status/options", null);
        String from = o.get("fromDate").asText(), to = o.get("toDate").asText();
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/vehicle-history?car=" + car + "&fromDate=" + from + "&toDate=" + to, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSV04I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                // VHSV04I opens with "0000" in both symptom boxes (CHAR fields over NUM); the new page leaves them empty
                "changes", List.of(Map.of("widget", "txtCarno", "attr", "object", "value", car), Map.of("widget", "txtTodate", "attr", "object", "value", MDY.format(LocalDate.parse(to))),
                        Map.of("widget", "txtSympcdfrom", "attr", "object", "value", ""), Map.of("widget", "txtSympcdto", "attr", "object", "value", "")),
                "widget", "pbtDisplay", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().toUpperCase().startsWith("VHSV05I")) report = x;
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(report).as("VHSV05I opened").isNotNull();
        String first = clean.path("incidents").path(0).path("incident").asText("");
        assertThat(first).isEqualTo(report.at("/widgets/txtIncdNo/value").asText("").strip());
    }

    @Test
    void carInquiryMatchesTheOldScreens() throws Exception {
        String car = null;
        JsonNode fleet = send("GET", "/api/v1/fleet", null);
        for (var tracks : (Iterable<JsonNode>) () -> fleet.get("fleet").elements()) {
            for (JsonNode t : tracks) for (JsonNode c : t.get("cars")) if (c != null && !c.isNull() && car == null) car = c.get("car").asText();
        }
        if (car == null) return;
        JsonNode clean = send("GET", "/api/v1/vehicle-queries/car-inquiry/" + car, null);
        JsonNode open = send("POST", "/api/v1/ui/open", Map.of("screen", "VHSV07I"));
        JsonNode old = send("POST", "/api/v1/ui/windows/" + open.at("/windows/0/id").asText() + "/events", Map.of(
                "changes", List.of(Map.of("widget", "Text1", "attr", "object", "value", car)), "widget", "Push_Button1", "event", "clicked"));
        JsonNode report = null;
        for (JsonNode x : old.get("windows")) if (x.get("screen").asText().equalsIgnoreCase("VHSV06I")) report = x;
        assertThat(clean.get("_status").asInt()).isEqualTo(200);
        assertThat(report).as("VHSV06I opened").isNotNull();
        assertThat(clean.path("incidents").path(0).path("incident").asText("")).isEqualTo(report.at("/widgets/txtIncdNo/value").asText("").strip());
        assertThat(clean.path("car").path("location").asText("")).isEqualTo(report.at("/widgets/txtLoc/value").asText("").strip());
    }
}
