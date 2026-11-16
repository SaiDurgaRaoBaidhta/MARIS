package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.Question;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.Step;
import com.bart.maris.middleware.domain.consist.ConsistRuleScreens.View;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The consist maintenance screens, each run with its own rules by {@link ConsistRuleRunner}:
 * <ul>
 * <li>make train (old VHMT01I): the consist checked when it is typed (VHMT01IS-YT-CON), Create (VHMT011..016);</li>
 * <li>combine trains (VHCM01I): the trains listed (VHC01IS-COM-TRAIN, VHCM01IS-LIST), Combine (VHCM011..018);</li>
 * <li>clear train (VHCT01I): Clear Train (VHCT011..019, VHCT01A) with its questions;</li>
 * <li>change consist (VHCC01I): the consist listed (VHCC01IS-TEXT), Update with the new cars (VHCC011..017);</li>
 * <li>train swap (VHTS01I): pairs added to the swap list (VHTS01IS-ADD), Update (VHTS2A1..3, VHTSX);</li>
 * <li>split consist (VHSC01I): the consist listed (VHSC01IS-ORG-CON), the split car chosen, Update (VHSC2A1/2, VHSCX/Y).</li>
 * </ul>
 * Every call opens the screen fresh for the signed-on group (its access check, VH01A02, included) and takes the user's
 * steps; a question box the screen asks comes back as {@code question}, and is answered by sending the call again
 * with {@code answers}.
 */
public final class ConsistMaintenanceService {
    /** one row of a screen's car table, by column */
    public record Result(String message, List<String> notices, Question question, Map<String, String> fields, List<Map<String, String>> cars,
                         List<Map<String, String>> swaps) {}

    /** a screen: its code, message box, single fields and car table columns (10 widgets each) */
    private record Spec(String screen, String message, Map<String, String> fields, Map<String, List<String>> columns) {}

    private static List<String> numbered(String prefix, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(prefix + i);
        return out;
    }

    private static List<String> list(String... w) { return List.of(w); }

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    static final Spec MAKE_TRAIN = new Spec("VHMT01I", "ConMsgTXT1", fields("consist", "ConNotxt1", "date", "DateSrvtxt1", "time", "TimeSrvTXT1"),
            columnsOf("car", numbered("CarsNumTXT", 10), "location", numbered("VehStatusTXT", 10), "orientation", numbered("OrintationTXT", 10),
                    "softwareVersion", numbered("SoftVerTXT", 10), "error", numbered("LocTXT", 10)));
    static final Spec COMBINE = new Spec("VHCM01I", "Text1", fields("combineTrain", "Text11", "train", "Text31", "beforeAfter", "Text2",
            "newTrain", "Text411", "time", "Text511", "location", "Text3"),
            columnsOf("car", list("Text4", "Text5", "Text6", "Text7", "Text8", "Text9", "Text10", "Text111", "Text12", "Text13"),
                    "consist", list("Text41", "Text51", "Text61", "Text71", "Text81", "Text91", "Text101", "Text112", "Text121", "Text131"),
                    "status", list("Text42", "Text52", "Text62", "Text72", "Text82", "Text92", "Text102", "Text113", "Text122", "Text132")));
    static final Spec CLEAR = new Spec("VHCT01I", "Text15", fields("consist", "Text1", "time", "Text2", "yard", "Text13", "madeInError", "Text14"),
            columnsOf("car", list("Text3", "Text4", "Text5", "Text6", "Text7", "Text8", "Text9", "Text10", "Text11", "Text12")));
    static final Spec CHANGE = new Spec("VHCC01I", "Text35", fields("consist", "Text1", "dash", "Text2", "time", "Text3", "date", "Text4"),
            columnsOf("car", list("Text5", "Text6", "Text7", "Text8", "Text9", "Text10", "Text11", "Text12", "Text13", "Text14"),
                    "newCar", list("Text15", "Text16", "Text17", "Text18", "Text19", "Text20", "Text21", "Text22", "Text23", "Text24"),
                    "location", list("Text262", "Text26", "Text27", "Text28", "Text29", "Text30", "Text31", "Text32", "Text33", "Text34"),
                    "orientation", list("Text51", "Text52", "Text53", "Text54", "Text55", "Text56", "Text57", "Text58", "Text59", "Text510"),
                    "error", list("Text251", "Text261", "Text271", "Text281", "Text291", "Text301", "Text311", "Text321", "Text331", "Text341")));
    static final Spec SWAP = new Spec("VHTS01I", "Text3", fields("from", "txtFrom", "to", "txtTo"), Map.of());
    static final Spec SPLIT = new Spec("VHSC01I", "txtFooter", fields("consist", "txtConNo", "dash", "txtDashNo", "time", "txtTimeInService",
            "leftConsist", "txtLeftCon", "rightConsist", "txtRightCon", "splitTime", "txtSplitTime", "splitLocation", "txtSplitLocation"),
            columnsOf("left", numbered("txtLeftCon", 10), "right", numbered("txtRightCon", 10)));

    private static Map<String, List<String>> columnsOf(Object... pairs) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> w = (List<String>) pairs[i + 1];
            m.put((String) pairs[i], w);
        }
        return m;
    }

    private final ConsistRuleScreens screens;

    public ConsistMaintenanceService(ConsistRuleScreens screens) {
        this.screens = screens;
    }

    // ---- the screens ---------------------------------------------------------------------------------------------

    /** VHMT01I: the consist typed (VHMT01IS-YT-CON): what the screen fills in for it */
    public Result makeTrainCheck(String group, String consist) {
        return run(MAKE_TRAIN, group, List.of(Step.type("ConNotxt1", required(consist)), Step.leave("ConNotxt1")), null);
    }

    /** VHMT01I: consist, in-service date and time and up to 10 cars typed, then Create */
    public Result makeTrain(String group, String consist, String date, String time, List<String> cars, Map<String, Boolean> answers) {
        List<Step> s = new ArrayList<>(List.of(Step.type("ConNotxt1", required(consist)), Step.leave("ConNotxt1"),
                Step.type("DateSrvtxt1", text(date)), Step.type("TimeSrvTXT1", text(time))));
        typeColumn(s, MAKE_TRAIN.columns().get("car"), cars);
        s.add(Step.press("Create_btn1"));
        return run(MAKE_TRAIN, group, s, answers);
    }

    /** VHCM01I: the train to combine into, and the train and its before/after position: the cars of both */
    public Result combineList(String group, String combineTrain, String train, String beforeAfter) {
        return run(COMBINE, group, combineSteps(combineTrain, train, beforeAfter), null);
    }

    private static List<Step> combineSteps(String combineTrain, String train, String beforeAfter) {
        return new ArrayList<>(List.of(Step.type("Text11", required(combineTrain)), Step.leave("Text11"), Step.type("Text31", text(train)),
                Step.type("Text2", text(beforeAfter)), Step.leave("Text2")));
    }

    /** VHCM01I: the trains listed, the new train number, out-of-service time and location typed, then Combine */
    public Result combine(String group, String combineTrain, String train, String beforeAfter, String newTrain, String time, String location,
                          Map<String, Boolean> answers) {
        List<Step> s = combineSteps(combineTrain, train, beforeAfter);
        s.add(Step.type("Text411", text(newTrain)));
        s.add(Step.type("Text511", text(time)));
        s.add(Step.type("Text3", text(location)));
        s.add(Step.press("Push_Button4"));
        return run(COMBINE, group, s, answers);
    }

    /** VHCT01I: the screen as it opens (VHCT01IS-INIT: the proposed out-of-service time) */
    public Result clearStart(String group) {
        return run(CLEAR, group, List.of(), null);
    }

    /** VHCT01I: consist, out-of-service time, yard and made-in-error typed, then Clear Train */
    public Result clear(String group, String consist, String time, String yard, String madeInError, Map<String, Boolean> answers) {
        List<Step> s = new ArrayList<>(List.of(Step.type("Text1", required(consist))));
        if (time != null) s.add(Step.type("Text2", time));
        s.add(Step.type("Text13", text(yard)));
        s.add(Step.type("Text14", text(madeInError)));
        s.add(Step.press("Push_Button3"));
        return run(CLEAR, group, s, answers);
    }

    /** VHCC01I: the consist typed (VHCC01IS-TEXT): its cars */
    public Result changeList(String group, String consist) {
        return run(CHANGE, group, List.of(Step.type("Text1", required(consist)), Step.leave("Text1")), null);
    }

    /** VHCC01I: the consist listed, the new car numbers typed (blank keeps a car), then Update */
    public Result change(String group, String consist, List<String> newCars, Map<String, Boolean> answers) {
        List<Step> s = new ArrayList<>(List.of(Step.type("Text1", required(consist)), Step.leave("Text1")));
        typeColumn(s, CHANGE.columns().get("newCar"), newCars);
        s.add(Step.press("Push_Button1"));
        return run(CHANGE, group, s, answers);
    }

    /** VHTS01I: each from/to pair added to the swap list (Swap Consists), then Update */
    public Result swap(String group, List<List<String>> pairs, boolean update, Map<String, Boolean> answers) {
        if (pairs == null || pairs.isEmpty()) throw new InvalidRequestException("At least one from / to consist pair is required.");
        List<Step> s = new ArrayList<>();
        for (List<String> p : pairs) {
            s.add(Step.type("txtFrom", p.isEmpty() ? "" : text(p.get(0))));
            s.add(Step.type("txtTo", p.size() < 2 ? "" : text(p.get(1))));
            s.add(Step.press("pbtSwap"));
        }
        if (update) s.add(Step.press("pbtUpdate"));
        return run(SWAP, group, s, answers);
    }

    /** VHSC01I: the consist typed (VHSC01IS-ORG-CON): its cars */
    public Result splitList(String group, String consist) {
        return run(SPLIT, group, List.of(Step.type("txtConNo", required(consist)), Step.leave("txtConNo")), null);
    }

    /** VHSC01I: the consist listed, the new consists typed, the split car (3..9) chosen, split time and location typed, then Update */
    public Result split(String group, String consist, int atCar, String leftConsist, String rightConsist, String splitTime, String splitLocation,
                        Map<String, Boolean> answers) {
        if (atCar < 3 || atCar > 9) throw new InvalidRequestException("The split car is car 3 to 9.");
        List<Step> s = new ArrayList<>(List.of(Step.type("txtConNo", required(consist)), Step.leave("txtConNo")));
        s.add(Step.type("txtLeftCon", text(leftConsist)));           // the split car button needs both new consist numbers
        s.add(Step.type("txtRightCon", text(rightConsist)));
        s.add(Step.press("pbtSplitButton" + atCar));
        s.add(Step.type("txtSplitTime", text(splitTime)));
        s.add(Step.type("txtSplitLocation", text(splitLocation)));
        s.add(Step.press("pbtUpdate"));
        return run(SPLIT, group, s, answers);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String required(String consist) {
        String c = consist == null ? "" : consist.strip();
        if (c.isEmpty()) throw new InvalidRequestException("A consist number is required.");
        return c;
    }

    private static String text(String v) {
        return v == null ? "" : v.strip();
    }

    private static void typeColumn(List<Step> s, List<String> widgets, List<String> values) {
        if (values == null) return;
        if (values.size() > widgets.size()) throw new InvalidRequestException("At most " + widgets.size() + " cars.");
        for (int i = 0; i < values.size(); i++) if (values.get(i) != null && !values.get(i).isBlank()) s.add(Step.type(widgets.get(i), values.get(i).strip()));
    }

    private Result run(Spec spec, String group, List<Step> steps, Map<String, Boolean> answers) {
        List<String> show = new ArrayList<>(spec.fields().values());
        show.add(spec.message());
        spec.columns().values().forEach(show::addAll);
        ConsistRuleRunner logic = screens.open(spec.screen(), group, spec == CLEAR);    // Clear Train checks the yard against the menu's tables
        if (answers != null) answers.forEach(logic::answer);
        for (Step st : steps) {
            if (logic.unanswered != null) break;
            if (st.event() == null) logic.type(st.widget(), st.value());
            else logic.fire(st.widget(), st.event());
        }
        View v = screens.view(logic, show);
        Map<String, String> f = new LinkedHashMap<>();
        spec.fields().forEach((k, w) -> f.put(k, v.field(w)));
        List<Map<String, String>> cars = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            boolean any = false;
            for (var c : spec.columns().entrySet()) {
                String val = i < c.getValue().size() ? v.field(c.getValue().get(i)) : "";
                row.put(c.getKey(), val);
                any |= !val.isEmpty();
            }
            if (any) { row.put("position", String.valueOf(i + 1)); cars.add(row); }
        }
        List<Map<String, String>> swaps = spec == SWAP ? logic.rows("VHTS01IW-CON-LIST", "DATA-RECORD") : List.of();
        return new Result(v.field(spec.message()), v.notices(), v.question(), f, cars, swaps);
    }
}
