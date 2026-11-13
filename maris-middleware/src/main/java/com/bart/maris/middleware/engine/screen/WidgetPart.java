package com.bart.maris.middleware.engine.screen;

import com.bart.maris.middleware.engine.runtime.Conv;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A widget of a screen (VAST emulation): its value, list items, selection, colours, enabled / visible state. */
public final class WidgetPart extends Part {
    final String type;
    final JsonNode props;
    final ScreenInstance screen;

    Object object;
    List<Object> items = new ArrayList<>();
    List<Integer> selection = new ArrayList<>();     // 1-based indices
    Boolean toggled;
    boolean enabled = true, visible = true, focus;
    String fg, bg, color, title, messageString;
    int buttonType = 1, iconType;
    boolean dirty;                                     // changed since the screen was last sent to the UI

    WidgetPart(String name, String type, JsonNode props, ScreenInstance screen) {
        super(name, screen.session);
        this.type = type;
        this.props = props;
        this.screen = screen;
        if (props != null) {
            if (props.has("object")) object = json(props.get("object"));
            if (props.has("items") && props.get("items").isArray()) props.get("items").forEach(i -> items.add(json(i)));
            if (props.has("selectionIndex")) { int s = props.get("selectionIndex").asInt(); if (s > 0) selection.add(s); }
            if (props.has("selection")) toggled = props.get("selection").asBoolean();
            if (props.has("enabled")) enabled = props.get("enabled").asBoolean(true);
            if (props.has("visible")) visible = props.get("visible").asBoolean(true);
            if (props.has("title")) title = props.get("title").asText();
            if (props.has("messageString")) messageString = props.get("messageString").asText();
            if (props.has("buttonType")) buttonType = props.get("buttonType").asInt(1);
            if (props.has("iconType")) iconType = props.get("iconType").asInt();
            if (props.has("foregroundColor")) fg = props.get("foregroundColor").asText();
            if (props.has("backgroundColor")) bg = props.get("backgroundColor").asText();
        }
    }

    private static Object json(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.decimalValue();
        if (n.isBoolean()) return n.booleanValue();
        if (n.isObject() && n.has("id")) return null;
        return n.asText();
    }

    boolean isList() {
        return type.contains("Container") || type.contains("ComboBox") || type.contains("List") || type.equals("RadioButtonSet");
    }

    boolean isTable() {
        return type.contains("ContainerDetails");
    }

    boolean isShell() {
        return type.equals("ShellView");
    }

    // ---- attributes ----------------------------------------------------------------------------------------

    @Override
    public Object get(String attr) {
        return switch (attr) {
            case "object", "string", "labelString", "value", "text" -> object;
            case "items" -> items.isEmpty() && !isList() ? null : new ArrayList<>(items);
            case "selectionIndex" -> BigDecimal.valueOf(selection.isEmpty() ? 0 : selection.get(0));
            case "selectionIndices", "selectedIndices" -> selection.stream().map(i -> (Object) BigDecimal.valueOf(i)).toList();
            case "selectedItem" -> selection.isEmpty() || selection.get(0) > items.size() ? null : items.get(selection.get(0) - 1);
            case "selectedItems" -> selection.stream().filter(i -> i <= items.size()).map(i -> items.get(i - 1)).toList();
            case "selection" -> toggled != null ? toggled : (Object) BigDecimal.valueOf(selection.isEmpty() ? 0 : selection.get(0));
            case "selectionIsValid" -> !selection.isEmpty();
            case "entryObject" -> selection.isEmpty() ? object : get("selectedItem");
            case "enabled" -> enabled;
            case "visible" -> visible;
            case "foregroundColor" -> fg;
            case "backgroundColor" -> bg;
            case "color" -> color;
            case "title" -> title;
            case "messageString" -> messageString;
            case "buttonType" -> BigDecimal.valueOf(buttonType);
            case "iconType" -> BigDecimal.valueOf(iconType);
            case "self" -> this;
            default -> null;
        };
    }

    @Override
    public void set(String attr, Object v) {
        dirty = true;
        switch (attr) {
            case "object", "string", "labelString", "value", "text" -> {
                Object nv = v instanceof ArrayFieldPart.Row r ? r.value() : v;
                if (Objects.equals(norm(object), norm(nv))) return;          // TextView: only a really different value
                object = nv;
                signal("object", object);
            }
            case "items" -> {
                items = new ArrayList<>();
                if (v instanceof List<?> l) items.addAll(l);
                else if (v != null) items.add(v);
                selection = new ArrayList<>(selection.stream().filter(i -> i <= items.size()).toList());
                signal("items", new ArrayList<>(items));
            }
            case "selectionIndex" -> select(v == null ? List.of() : idx(v) > 0 ? List.of(idx(v)) : List.of());
            case "selectionIndices", "selectedIndices" -> {
                List<Integer> l = new ArrayList<>();
                if (v instanceof List<?> list) for (Object o : list) { int i = idx(o); if (i > 0) l.add(i); }
                select(l);
            }
            case "selectedItem" -> {
                int at = indexOf(v);
                select(at > 0 ? List.of(at) : List.of());
            }
            case "selection" -> {
                if (toggled != null || v instanceof Boolean) {
                    boolean b = Conv.truthy(v);
                    if (toggled != null && toggled == b) return;
                    toggled = b;
                    signal("selection", b);
                } else {
                    select(idx(v) > 0 ? List.of(idx(v)) : List.of());
                }
            }
            case "enabled" -> enabled = Conv.truthy(v);
            case "visible" -> visible = Conv.truthy(v);
            case "foregroundColor" -> fg = v == null ? null : Conv.rtrim(Conv.string(v));
            case "backgroundColor" -> bg = v == null ? null : Conv.rtrim(Conv.string(v));
            case "color" -> color = v == null ? null : Conv.rtrim(Conv.string(v));
            case "title" -> title = v == null ? "" : Conv.rtrim(Conv.string(v));
            case "messageString" -> messageString = v == null ? "" : Conv.rtrim(Conv.string(v));
            case "buttonType" -> buttonType = Conv.number(v).intValue();
            case "iconType" -> iconType = Conv.number(v).intValue();
            case "entryObject" -> { object = v; signal("entryObject", v); }
            default -> { }
        }
    }

    private static Object norm(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        return Conv.rtrim(o.toString());
    }

    private static int idx(Object v) {
        if (v instanceof List<?> l) return l.isEmpty() ? 0 : idx(l.get(0));
        return Conv.number(v).intValue();
    }

    private int indexOf(Object v) {
        if (v == null) return 0;
        for (int i = 0; i < items.size(); i++) {
            Object it = items.get(i);
            if (it == v || Objects.equals(norm(display(it)), norm(display(v)))) return i + 1;
        }
        return 0;
    }

    static Object display(Object o) {
        if (o instanceof ArrayFieldPart.Row r) return r.text();
        return o;
    }

    /** changes the selection; signals only when it changed */
    void select(List<Integer> sel) {
        if (sel.equals(selection)) return;
        selection = new ArrayList<>(sel);
        dirty = true;
        signal("selectedItem", get("selectedItem"));
        signal("selectedItems", get("selectedItems"));
        signal("selectionIndices", get("selectionIndices"));
        signal("selectionIndex", get("selectionIndex"));
        if (type.equals("RadioButtonSet")) signal("selection", get("selection"));
    }

    // ---- actions -------------------------------------------------------------------------------------------

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        dirty = true;
        switch (action) {
            case "enable" -> enabled = true;
            case "disable" -> enabled = false;
            case "show" -> visible = true;
            case "hide" -> visible = false;
            case "setFocus" -> screen.focus(this);
            case "click" -> signal("clicked");
            case "deselectAllItems" -> select(List.of());
            case "selectAll" -> { List<Integer> all = new ArrayList<>(); for (int i = 1; i <= items.size(); i++) all.add(i); select(all); }
            case "selectIndex_" -> { int i = idx(args.isEmpty() ? null : args.get(0)); if (i > 0) { List<Integer> l = new ArrayList<>(selection); if (!l.contains(i)) l.add(i); select(l); } }
            case "openWidget", "openApplicationModalWidget" -> screen.open();
            case "closeWidget" -> screen.close();
            case "prompt", "promptThenDestroy" -> screen.session.prompt(this, messageString, title, buttonType, iconType);
            case "showBusyCursor", "resetCursor", "removeBusyCursor", "beep" -> { }
            case "clear" -> set("object", "");
            default -> {
                if (invokedFromEvent) set(action, args.isEmpty() ? null : args.get(0));
            }
        }
        return null;
    }

    // ---- what the UI shows ---------------------------------------------------------------------------------

    /** the widget's state for the UI */
    Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (object != null) m.put("value", Conv.rtrim(Conv.string(object)));
        if (toggled != null) m.put("checked", toggled);
        if (isTable()) {
            List<String> cols = columns();
            List<List<String>> rows = new ArrayList<>();
            for (Object it : items) {
                List<String> r = new ArrayList<>();
                for (String c : cols) {
                    Object v = it instanceof ArrayFieldPart.Row row ? row.valueOf(c) : c.isEmpty() ? it : null;
                    r.add(v == null ? "" : Conv.rtrim(Conv.string(v)));
                }
                rows.add(r);
            }
            m.put("rows", rows);
            m.put("columns", headings());
        } else if (isList()) {
            List<String> l = new ArrayList<>();
            for (Object it : items) l.add(it == null ? "" : Conv.rtrim(Conv.string(display(it))));
            m.put("items", l);
        }
        if (isList()) m.put("selected", selection);
        if (!enabled) m.put("enabled", false);
        if (!visible) m.put("visible", false);
        if (fg != null) m.put("fg", fg);
        if (bg != null) m.put("bg", bg);
        if (color != null) m.put("color", color);
        if (focus) m.put("focus", true);
        if (title != null && isShell()) m.put("title", title);
        return m;
    }

    List<String> columns() {
        List<String> out = new ArrayList<>();
        if (props == null || !props.has("children")) return out;
        for (JsonNode c : props.get("children")) {
            JsonNode col = screen.widgetDef(c.path("id").asText());
            out.add(col == null ? "" : col.path("props").path("attributeName").asText(""));
        }
        return out;
    }

    List<String> headings() {
        List<String> out = new ArrayList<>();
        if (props == null || !props.has("children")) return out;
        for (JsonNode c : props.get("children")) {
            JsonNode col = screen.widgetDef(c.path("id").asText());
            out.add(col == null ? "" : col.path("props").path("heading").asText(""));
        }
        return out;
    }
}
