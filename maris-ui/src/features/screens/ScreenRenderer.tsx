/* Draws one MARIS screen from its captured layout (see types/maris.ts ScreenDef).
 * Every field is a controlled React input keyed by its EGL field name (data-w), so the middleware can fill and read
 * screens by the same names the EGL programs use. Push buttons do what the EGL wiring says (open / close / call programs). */
import { createElement, useMemo, type CSSProperties, type ReactNode } from "react";
import type { SElement, SNode, ScreenDef } from "./types";
import { cssColor, type LiveWidget } from "./live";

/* legacy class -> Tailwind. Order matters only for readability; all of an element's classes are applied. */
const TW: Record<string, string> = {
  "k-grp": "absolute rounded-xl border border-white/85 bg-[linear-gradient(160deg,rgba(255,255,255,.58),rgba(255,255,255,.32))] shadow-[inset_0_1px_0_rgba(255,255,255,.9),0_0_0_1px_rgba(169,219,241,.35),0_10px_26px_-16px_rgba(11,37,69,.3),0_1px_2px_rgba(15,23,42,.04)] dark:border-[#22364b] dark:bg-[linear-gradient(170deg,#10213a,#0d1b2c)] dark:shadow-[0_10px_24px_-18px_rgba(0,0,0,.8)]",
  group: "absolute rounded-xl border border-white/85 bg-white/40 shadow-[0_0_0_1px_rgba(169,219,241,.35)] dark:border-line-dark dark:bg-[#0d1b2c]",
  "k-leg": "absolute -top-[9px] left-1.5 whitespace-nowrap rounded-md bg-gradient-to-b from-white to-[#f0f8fc] px-1.5 text-[13px] leading-4 font-bold text-bart-700 shadow-[inset_0_0_0_1px_#cdeaf7,0_2px_6px_-3px_rgba(0,103,158,.25)] dark:bg-none dark:bg-[#12304a] dark:text-[#9fdcf8] dark:shadow-[inset_0_0_0_1px_#1f4566]",
  legend: "absolute -top-[9px] left-1.5 whitespace-nowrap px-1.5 font-bold text-bart-700 dark:text-[#9fdcf8]",
  "k-leg-plain": "bg-none shadow-none",
  "k-lbl": "absolute whitespace-nowrap leading-[18px] text-slate-600 dark:text-[#c9d6e4]",
  lbl: "absolute whitespace-nowrap leading-[18px] text-slate-600 dark:text-[#c9d6e4]",
  "k-head": "!font-semibold !text-[13px] !text-navy dark:!text-[#cfe3f5]",
  "k-grphead": "!font-semibold !text-[13px] !text-navy !leading-4 border-b border-[#cbd5e1] pb-0.5 dark:!text-[#cfe3f5] dark:border-[#2e4560]",
  "k-colhead": "!bg-none !border-0 !shadow-none !font-semibold !text-navy dark:!text-[#cfe3f5]",
  "k-inp": "absolute rounded-md border border-slate-300 bg-white px-[5px] py-px text-slate-900 shadow-[inset_0_1px_2px_rgba(15,23,42,.04)] outline-none focus:border-bart focus:ring-3 focus:ring-bart/15 dark:border-[#3d5c7c] dark:bg-[#050d17] dark:text-[#f1f6fb]",
  ro: "!border-[#b9c6d5] !bg-[#e6ecf3] !text-slate-600 !shadow-none focus:!ring-0 dark:!border-[#182b40] dark:!bg-[#182b40] dark:!text-[#a9bacd]",
  "k-sel": "sel-arrow absolute rounded-md border border-slate-300 bg-white py-0 pl-[7px] pr-6 text-slate-900 outline-none focus:border-bart focus:ring-3 focus:ring-bart/15 dark:border-[#2a405a] dark:bg-[#0a1624] dark:text-ink-dark",
  "k-btn": "absolute inline-flex items-center justify-center whitespace-nowrap rounded-[7px] border border-slate-300 bg-gradient-to-b from-white to-slate-50 px-2.5 text-[13px] leading-[13px] font-semibold text-slate-800 shadow-[0_1px_2px_rgba(15,23,42,.06),inset_0_-1px_0_rgba(15,23,42,.04)] hover:border-bart hover:text-bart-700 active:translate-y-px disabled:cursor-default disabled:opacity-50 disabled:hover:border-slate-300 disabled:hover:text-slate-800 dark:border-[#2a405a] dark:from-[#17304a] dark:to-[#12263b] dark:text-[#dbe6f1]",
  btn: "absolute inline-flex items-center justify-center",
  sq3d: "absolute rounded-[5px] border border-slate-300 bg-gradient-to-b from-white to-slate-100 shadow-[0_1px_2px_rgba(15,23,42,.08)] hover:outline hover:outline-[#6b9ac9] dark:border-[#2a405a] dark:from-[#12263b] dark:to-[#12263b]",
  "k-tbl": "scroll-thin absolute overflow-auto rounded-lg border border-slate-200 bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,23,42,.04)] dark:border-line-dark dark:bg-[#0c1a2a] dark:text-[#dbe6f1]",
  tbl: "scroll-thin absolute overflow-auto",
  "r111-wide": "!overflow-x-auto",
  "k-chk": "absolute flex cursor-pointer items-center gap-[5px] whitespace-nowrap leading-5 text-slate-600 dark:text-[#c9d6e4] [&_input]:m-0 [&_input]:accent-bart-600",
  statusbar: "absolute flex items-center rounded-lg border border-slate-200 bg-gradient-to-b from-slate-50 to-slate-100 px-3.5 text-[#0d3a66] dark:border-line-dark dark:from-[#10213a] dark:to-[#10213a] dark:text-[#c9d6e4]",
  "k-hr": "absolute h-0.5 border-y border-t-slate-300 border-b-white dark:border-line-dark",
  sep: "absolute border-t border-slate-300 dark:border-line-dark",
  ln: "cursor-pointer text-bart-600 underline hover:text-[#0d3a66] dark:text-[#4fb8ea]",
  hs: "absolute cursor-pointer",
  red: "!text-[#d0261b]",
  "egl-off": "opacity-50 !cursor-default",
  "r41-off": "opacity-50",
  "msg-gone": "!hidden",
  hl: "bg-[#ffa230] px-[3px] py-px",
  strike: "line-through",
  cur: "",
  // menu items: a small square and its caption (large .mi on menus, small .ms on tower / shop menus)
  mi: "absolute flex items-center gap-2 whitespace-nowrap text-[16px] leading-[18px] text-slate-800 dark:text-[#c9d6e4] [&.dis]:text-[#a8b3c4]",
  ms: "absolute flex items-center gap-2 whitespace-nowrap text-[13px] leading-[18px] text-slate-800 dark:text-[#c9d6e4] [&.dis]:text-[#a8b3c4]",
  sq: "flex-none w-[18px] h-4 rounded-[5px] border border-slate-300 bg-gradient-to-b from-white to-slate-100 shadow-[0_1px_2px_rgba(15,23,42,.08)] cursor-pointer hover:bg-none hover:bg-[#e8e4dc] hover:outline hover:outline-[#6b9ac9] [.dis_&]:bg-none [.dis_&]:bg-slate-100 [.dis_&]:border-slate-200 [.dis_&]:shadow-none [.dis_&]:outline-none dark:bg-none dark:bg-[#12263b] dark:border-[#2a405a]",
};

const TAGS = new Set(["div", "span", "label", "u", "b", "small", "i", "s", "mark", "ol", "ul", "li", "fieldset", "legend", "p", "h1", "h2", "h3", "h4", "em", "strong", "pre", "code", "caption",
  "table", "thead", "tbody", "tfoot", "tr", "th", "td", "colgroup", "col", "section", "header", "footer", "nav"]);
/* EGL screens (.win.egl .client …) have their own layer of rules in the reference */
const TW_EGL: Record<string, string> = {
  gc: "absolute inset-0",
  btn: "flex items-center justify-center text-center whitespace-normal leading-[13px] px-[3px] min-h-[26px] [&_span]:leading-[13px]",
  "k-btn": "!font-semibold !text-slate-800 hover:!text-bart-700 disabled:!text-[#a8b3c4] dark:!text-[#dbe6f1]",
  "k-lbl": "overflow-visible",
  "k-chk": "flex items-center gap-1.5 leading-[18px] [&_input]:size-[15px] [&_input]:flex-none [.k-chk+&]:mt-0.5",
  "k-leg-plain": "!-top-5 !left-0.5 !p-0 !bg-none !bg-transparent !shadow-none !rounded-none leading-4",
  "vh-num": "!p-0 !min-w-0 !font-semibold !text-[13px] text-center tracking-[.5px] !rounded",
  hs: "border border-dashed border-[#8a8a8a]",
  sep: "bg-[#d8e0ea] min-h-px max-h-0.5 dark:bg-line-dark",
};
const tw = (c?: string, egl = false) => (c || "").split(/\s+/).map(x => [TW[x], egl ? TW_EGL[x] : ""].filter(Boolean).join(" ")).filter(Boolean).join(" ");
const isLeg = (n: SNode) => typeof n !== "string" && /\bk-leg\b/.test(n.c || "");
const caption = (n: SNode): string => typeof n === "string" ? n : (n.k || []).map(caption).join("");
const styleOf = (n: SElement): CSSProperties => {
  const s: CSSProperties = {};
  for (const [k, v] of Object.entries(n.s || {})) (s as Record<string, string>)[k] = v;
  if (n.h) s.display = "none";
  return s;
};

export type FieldValues = Record<string, string | boolean>;

export interface RendererProps {
  def: ScreenDef;
  fill: (v: string) => string;          // capture-time tokens ({{user}}, {{date}} …) -> session / clock
  values: FieldValues;
  setValue: (id: string, v: string | boolean) => void;
  /** a push button / square / hot spot / link was pressed */
  onPress: (name: string | undefined, caption: string) => void;
  selected: Record<string, number>;
  onSelectRow: (table: string, row: number) => void;
  /** rows the middleware returned, by list box name (data-w of the table's container); they replace the captured rows */
  rows?: Record<string, string[][]>;
  /** live screens: every widget's state from the middleware's screen engine, by EGL name */
  live?: Record<string, LiveWidget>;
  /** live screens: the user picked a row / list entry / radio option (1-based; 0 = none) */
  onSelect?: (widget: string, index: number) => void;
  /** live screens: a text field lost focus (its edit is committed, like the old widget's onChange) */
  onCommit?: (id: string) => void;
}

/* colours the old screens use for the normal look (grey read-only boxes, black text) are left to the theme */
const PLAIN = new Set(["grey75", "#bfbfbf", "white", "#ffffff", "black", "#000000", "default", "grey", "lightgrey"]);
function liveStyle(lw: LiveWidget | undefined, style: CSSProperties): CSSProperties {
  if (!lw) return style;
  const s = { ...style };
  if (lw.visible === false) s.display = "none";
  else if (s.display === "none") delete s.display;     // captured hidden; the running screen shows it
  const fg = lw.fg || lw.color;
  if (fg && !PLAIN.has(fg.toLowerCase())) s.color = cssColor(fg);
  if (lw.bg && !PLAIN.has(lw.bg.toLowerCase())) s.background = cssColor(lw.bg);
  return s;
}
const textOnly = (n: SElement) => (n.k || []).every(k => typeof k === "string" || (k.t === "span" && (k.k || []).every(x => typeof x === "string")));

/* a captured table with the middleware's rows in place of its (empty) captured rows: heading rows stay, every data row
 * takes the cell styles of the first captured row, and short results are padded to the captured height */
const isHead = (tr: SNode) => typeof tr !== "string" && (tr.k || []).some(c => typeof c !== "string" && c.t === "th");
function withRows(table: SElement, data: string[][]): SElement {
  return { ...table, k: (table.k || []).map(sec => {
    if (typeof sec === "string" || (sec.t !== "tbody" && sec.t !== "thead")) return sec;
    const trs = sec.k || [];
    const tpl = trs.find(tr => typeof tr !== "string" && !isHead(tr)) as SElement | undefined;
    if (!tpl) return sec;                                         // a heading section
    const cells = (tpl.k || []).filter((c): c is SElement => typeof c !== "string");
    const row = (v: string[]): SElement => ({ t: "tr", k: cells.map((c, i) => ({ t: "td", s: c.s, c: c.c, a: c.a?.colspan ? { colspan: c.a.colspan } : undefined, k: v[i] ? [v[i]] : [] })) });
    const pad = Math.max(0, trs.filter(tr => !isHead(tr)).length - data.length);
    return { ...sec, k: [...trs.filter(isHead), ...data.map(row), ...Array.from({ length: pad }, () => row([]))] };
  }) };
}

/** id of a field: its EGL name when it has one, otherwise its place in the layout */
export const fieldId = (n: SElement, path: string) => String(n.a?.["data-w"] ?? path);

export function ScreenRenderer(p: RendererProps) {
  // flow pages (web reports) carry their own computed look; the legacy widget classes would position them absolutely
  const egl = /\begl\b/.test(p.def.cls);
  const T = (c?: string) => p.def.flow ? "" : tw(c, egl);
  // a widget is absolutely placed only when the layout gave it a place; options inside a set flow
  const C = (n: SElement) => {
    const t = T(n.c);
    // only an option inside a radio / check set flows; every other widget keeps its CSS placement (k-leg: top -9px …)
    if (n.s?.left || n.s?.top || !/\bk-chk\b/.test(n.c || "")) return t;
    return t.replace(/(^|\s)absolute(?=\s|$)/g, " ").trim() + " !inline-flex mr-6";
  };
  const body = useMemo(() => p.def.body || [], [p.def]);
  const render = (n: SNode, path: string, group = "", afterLeg = false, opt = 0): ReactNode => {
    if (typeof n === "string") return p.fill(n);
    const a = n.a || {};
    const lw = p.live && a["data-w"] ? p.live[String(a["data-w"])] : undefined;
    let cls = n.c || "";
    if (lw && lw.enabled === false && !/\begl-off\b/.test(cls)) cls += " egl-off";
    if (lw && lw.enabled !== false) cls = cls.replace(/\begl-off\b/, "egl-hot");
    if (cls !== (n.c || "")) n = { ...n, c: cls };
    const style = liveStyle(lw, styleOf(n));
    if (egl && afterLeg && /\bgc\b/.test(cls)) style.top = 9;                     // .k-leg + .gc { inset: 9px 0 0 0 }
    if (egl && /\bbtn\b/.test(cls)) { const h = parseFloat(n.s?.height || "0"); if (h && h < 26) style.transform = `translateY(${-Math.round((26 - h) / 2)}px)`; }   // short buttons grow to 26px around their centre
    const id = fieldId(n, path);
    const kids = () => (n.k || []).map((k, i) => render(k, `${path}.${i}`, a["data-w"] ? String(a["data-w"]) : group, i > 0 && isLeg((n as SElement).k![i - 1]), a["data-w"] ? i + 1 : opt));
    const gl = p.live && group ? p.live[group] : undefined;

    if (n.html) return <span key={path} className={T(cls)} style={style} dangerouslySetInnerHTML={{ __html: n.html }} />;
    switch (p.def.flow && n.t === "table" ? "flow-table" : n.t) {
      case "input": {
        const type = String(a.type || "text");
        if (type === "radio" && gl && p.onSelect) {   // live: the set's selection is the engine's
          return <input key={path} type="radio" name={"radio:" + group} className={C(n)} style={style} checked={(gl.selected || [])[0] === opt}
            disabled={gl.enabled === false} aria-label={String(a.title || group || "option")} onChange={() => p.onSelect!(group, opt)} />;
        }
        if (type === "radio") {         // one choice per set: the set's value is the chosen option's path
          const g = "radio:" + (group || path), cur = p.values[g];
          return <input key={path} type="radio" name={g} className={C(n)} style={style} checked={cur === undefined ? !!a.checked : cur === path} disabled={!!a.disabled}
            aria-label={String(a.title || group || "option")} onChange={() => p.setValue(g, path)} />;
        }
        if (type === "checkbox") {
          const v = p.values[id] ?? lw?.checked ?? !!a.checked;
          return <input key={path} type="checkbox" className={C(n)} style={style} checked={!!v} disabled={!!a.disabled}
            aria-label={String(a.title || a["data-w"] || "check")} onChange={e => p.setValue(id, e.target.checked)} />;
        }
        const ro = !!a.readonly || lw?.enabled === false;
        return <input key={path} type="text" name={String(a["data-w"] || "")} className={`${C(n)} ${ro ? T("ro") : ""}`} style={style}
          value={String(p.values[id] ?? lw?.value ?? (p.live ? "" : p.fill(String(a.value ?? ""))))} readOnly={ro} tabIndex={ro ? -1 : undefined}
          maxLength={a.maxlength ? Number(a.maxlength) : undefined} autoComplete="off" spellCheck={false} autoFocus={lw?.focus}
          aria-label={String(a.title || a["data-w"] || "field")} onChange={e => p.setValue(id, e.target.value)}
          onBlur={() => p.onCommit?.(id)} />;
      }
      case "textarea": {
        const ro = !!a.readonly || lw?.enabled === false;
        return <textarea key={path} name={String(a["data-w"] || "")} className={`${T(cls) || T("k-inp")} ${ro ? T("ro") : ""} resize-none`} style={style}
          value={String(p.values[id] ?? lw?.value ?? (p.live ? "" : p.fill(String(a.value ?? ""))))} readOnly={ro} maxLength={a.maxlength ? Number(a.maxlength) : undefined}
          aria-label={String(a["data-w"] || "text")} onChange={e => p.setValue(id, e.target.value)} onBlur={() => p.onCommit?.(id)} />;
      }
      case "select":
        if (lw && p.onSelect) {
          const sel = (lw.selected || [])[0] || 0;
          return <select key={path} name={String(a["data-w"] || "")} className={T(cls) || T("k-sel")} style={style} disabled={lw.enabled === false}
            value={String(sel)} aria-label={String(a["data-w"] || "list")} onChange={e => p.onSelect!(String(a["data-w"]), Number(e.target.value))}>
            {sel === 0 && <option value="0"></option>}
            {(lw.items || []).map((t, i) => <option key={i} value={i + 1}>{t}</option>)}
          </select>;
        }
        return <select key={path} name={String(a["data-w"] || "")} className={T(cls) || T("k-sel")} style={style} disabled={!!a.disabled}
          value={String(p.values[id] ?? a.value ?? "")} aria-label={String(a["data-w"] || "list")} onChange={e => p.setValue(id, e.target.value)}>
          {(n.o || []).map(([v, t], i) => <option key={i} value={v}>{t}</option>)}
        </select>;
      case "button":
        return <button key={path} type="button" className={T(cls) || T("k-btn")} style={style} disabled={lw ? lw.enabled === false : !!a.disabled} title={a.title ? String(a.title) : undefined}
          onClick={() => p.onPress(a["data-w"] as string | undefined, caption(n).trim() || String(a.title || ""))}>{kids()}</button>;
      case "table": {
        const tableId = path;
        const data = gl?.rows ?? (group ? p.rows?.[group] : undefined);
        const tbl = data ? withRows(n, data) : n;
        // live: the selected row is the engine's; rows are counted after the heading rows
        let heads = 0;
        for (const sec of tbl.k || []) if (typeof sec !== "string" && sec.t === "tbody") { heads = (sec.k || []).filter(isHead).length; break; }
        const liveSel = gl && p.onSelect ? ((gl.selected || [])[0] || 0) - 1 + heads : -1;
        return (
          <table key={path} className={`${egl ? "w-auto min-w-full" : "w-full"} table-fixed border-collapse text-[12px]`} style={style}>
            {(tbl.k || []).map((sec, si) => {
              if (typeof sec === "string") return null;
              if (sec.t === "colgroup") return <colgroup key={si}>{(sec.k || []).map((c, ci) => typeof c === "string" ? null : <col key={ci} style={styleOf(c)} />)}</colgroup>;
              const Sec = sec.t === "thead" ? "thead" : "tbody";
              return <Sec key={si}>{(sec.k || []).map((tr, ri) => {
                if (typeof tr === "string") return null;
                const sel = Sec === "tbody" && (liveSel >= 0 || (gl && p.onSelect) ? ri === liveSel : p.selected[tableId] === ri);
                const dataRow = gl && p.onSelect && Sec === "tbody" && ri >= heads && ri - heads < (data?.length ?? 0);
                return <tr key={ri} className={Sec === "tbody" ? `cursor-default ${sel ? "[&>td]:!bg-bart-600 [&>td]:!text-white" : "even:[&>td]:bg-[#f7fbfd] hover:[&>td]:bg-sky dark:even:[&>td]:bg-[#0f2032] dark:hover:[&>td]:bg-[#133250]"}` : ""}
                  onClick={Sec === "tbody" ? () => (gl && p.onSelect ? dataRow && p.onSelect(group, ri - heads + 1) : p.onSelectRow(tableId, ri)) : undefined}>
                  {(tr.k || []).map((c, ci) => {
                    if (typeof c === "string") return null;
                    const C = c.t === "th" ? "th" : "td";
                    return <C key={ci} colSpan={c.a?.colspan ? Number(c.a.colspan) : undefined} rowSpan={c.a?.rowspan ? Number(c.a.rowspan) : undefined} style={styleOf(c)}
                      className={`${C === "th" ? "sticky top-0 z-[1] border-b border-r border-slate-200 bg-gradient-to-b from-slate-50 to-slate-100 px-1 py-px text-left font-semibold text-slate-700 dark:border-line-dark dark:from-[#12263b] dark:to-[#12263b] dark:text-[#9fdcf8]"
                        : "h-[18px] overflow-hidden text-ellipsis whitespace-nowrap border-b border-slate-100 px-1 py-0.5 dark:border-[#18293c]"} ${T(c.c)}`}>
                      {(c.k || []).map((k, ki) => render(k, `${path}.${si}.${ri}.${ci}.${ki}`))}
                    </C>;
                  })}
                </tr>;
              })}</Sec>;
            })}
          </table>);
      }
      case "a":
        return <a key={path} className={T(cls) || T("ln")} style={style} onClick={() => p.onPress(a["data-w"] as string | undefined, caption(n).trim())}>{kids()}</a>;
      case "img":
        return <img key={path} src="/bart-logo.png" alt="BART" className={T(cls)} style={style} />;
      case "br": return <br key={path} />;
      case "label": case "u": case "b": case "small": case "i": case "s": case "mark": case "ol": case "li": case "span": case "fieldset": case "legend":
      default: {
        const clickable = /\b(sq3d|hs|egl-hot|k-btn|btn|mi|ms)\b/.test(cls) && !/\b(egl-off|dis)\b/.test(cls) && n.t !== "span";
        const tag = TAGS.has(n.t) ? n.t : "div";
        // live: a label's or button's text is the widget's value
        const children = lw?.value !== undefined && textOnly(n) && !/\b(k-tbl|tbl|k-grp|group)\b/.test(cls)
          ? (n.k || []).some(k => typeof k !== "string") ? [<span key="v">{lw.value}</span>] : [lw.value] : kids();
        return createElement(tag, {
          key: path, className: `${C(n)} ${clickable ? "cursor-pointer" : ""}`.trim() || undefined, style,
          title: a.title ? String(a.title) : undefined,
          ...(clickable ? { role: "button", tabIndex: 0, onClick: () => p.onPress(a["data-w"] as string | undefined, String(a.title || caption(n)).trim()),
            onKeyDown: (e: React.KeyboardEvent) => { if (e.key === "Enter") p.onPress(a["data-w"] as string | undefined, String(a.title || caption(n)).trim()); } } : {}),
        }, ...children);
      }
    }
  };
  return <>{body.map((n, i) => render(n, String(i)))}</>;
}

/** every field of a screen with its starting value (for Clear and for the action request) */
export function initialValues(def: ScreenDef, fill: (v: string) => string = v => v): Record<string, { name: string; value: string | boolean; ro: boolean }> {
  const out: Record<string, { name: string; value: string | boolean; ro: boolean }> = {};
  const walk = (n: SNode, path: string) => {
    if (typeof n === "string") return;
    if (n.t === "input" || n.t === "textarea" || n.t === "select") {
      const a = n.a || {}, cb = a.type === "checkbox" || a.type === "radio";
      out[fieldId(n, path)] = { name: String(a["data-w"] || ""), value: cb ? !!a.checked : fill(String(a.value ?? "")), ro: !!a.readonly };
    }
    if (n.t === "table") return;     // table cells hold no fields
    (n.k || []).forEach((k, i) => walk(k, `${path}.${i}`));
  };
  (def.body || []).forEach((n, i) => walk(n, String(i)));
  return out;
}
