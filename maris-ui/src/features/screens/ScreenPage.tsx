import { useEffect, useMemo, useState } from "react";
import { MenuTiles } from "@/features/menu/MenuTiles";
import { useElementWidth } from "@/shared/hooks/useElementWidth";
import { useWindowHeight } from "@/shared/hooks/useWindowHeight";
import { useApp } from "@/shared/lib/app-state";
import { fillTokens } from "@/shared/lib/tokens";
import { PageHead } from "@/shared/ui/PageHead";
import { useMock } from "@/shared/api/client";
import { fetchScreen, runAction } from "./api";
import { liveScreens } from "./live";
import { LiveScreenPage } from "./LiveScreenPage";
import { CLEAN_PAGES } from "./cleanPages";
import { KNOWN, layoutKey } from "./registry";
import { ScreenRenderer, initialValues, type FieldValues } from "./ScreenRenderer";
import type { ScreenDef } from "./types";
const norm = (t: string) => t.replace(/\s+/g, " ").replace(/&/g, "").trim().toLowerCase();

/** "VHRV01I - Vehicle Repair Tracking" -> [code, name] */
export function splitTitle(t: string): [string, string] {
  const isCode = (s: string) => /^[A-Z][A-Z0-9.]{2,}[A-Z0-9i]$/.test(s) && (/\d/.test(s) || /[Ii]$/.test(s));
  const m = /^\s*(\S+)\s*-{1,2}\s*(.+)$/.exec(t); if (m && isCode(m[1])) return [m[1], m[2]];
  const n = /^(.+?)\s+-{1,2}\s+(\S+)\s*$/.exec(t); if (n && isCode(n[2])) return [n[2], n[1]];
  return ["", t];
}

/** the EGL screen a key stands for: egl:VHIC30I, or a redesigned page whose title starts with the code (statveh -> VHSV01I) */
export function eglCode(key: string): string {
  if (/^egl:/i.test(key)) return key.slice(4);
  const info = KNOWN[key];
  const c = info?.code || (info ? splitTitle(info.title)[0] : "");
  return /^[A-Za-z]{2,}\d/.test(c) ? c.replace(/\s+/g, "").replace(/[-–]+$/, "") : "";
}
const eglKey = (code: string) => Object.keys(KNOWN).find(k => k.toUpperCase() === ("EGL:" + code).toUpperCase()) || "egl:" + code;

/** a screen: live through the middleware's screen engine when it can run it, otherwise its captured layout */
export function ScreenPage({ screenKey, label }: { screenKey: string; label: string }) {
  const code = eglCode(screenKey);
  const clean = CLEAN_PAGES[code.toUpperCase()];
  const [live, setLive] = useState<string | false | null>(useMock || !code ? false : null);
  useEffect(() => {
    if (useMock || !code) { setLive(false); return; }
    setLive(null);
    // menus stay tiles (the new UI's navigation); every other screen the engine knows, with an EGL layout, runs live
    Promise.all([liveScreens(), fetchScreen(layoutKey(eglKey(code))).catch(() => null)])
      .then(([s, d]) => {
        const name = [...s].find(x => x === code.toUpperCase() || x === code.toUpperCase() + "I");
        setLive(name && d && !d.menu ? name : false);
      });
  }, [code, screenKey]);
  if (clean && !useMock) return <>{clean()}</>;
  if (live === null) return <><PageHead title={label} /><p className="p-8 text-slate-500" role="status">Loading…</p></>;
  return live ? <LiveScreenPage screen={live} label={label} /> : <StaticScreenPage screenKey={screenKey} label={label} />;
}

function StaticScreenPage({ screenKey, label }: { screenKey: string; label: string }) {
  const app = useApp();
  const [def, setDef] = useState<ScreenDef | null>(null);
  const [err, setErr] = useState("");
  const [values, setValues] = useState<FieldValues>({});
  const [selected, setSelected] = useState<Record<string, number>>({});
  const [rows, setRows] = useState<Record<string, string[][]>>({});
  const [userZoom, setUserZoom] = useState(1);
  const [box, avail] = useElementWidth();
  const winH = useWindowHeight();
  const [pick, setPick] = useState<{ cap: string; to: string[] } | null>(null);

  useEffect(() => {
    setDef(null); setErr(""); setValues({}); setSelected({}); setRows({}); setUserZoom(1);
    fetchScreen(layoutKey(screenKey)).then(setDef).catch(() => setErr(`${screenKey.replace(/^egl:/, "")} – referenced by the code but not in the source (never migrated).`));
  }, [screenKey]);

  const user = app.session?.user || "";
  const fill = useMemo(() => (v: string) => fillTokens(v, user), [user]);
  const fields = useMemo(() => def ? initialValues(def, fill) : {}, [def, fill]);
  if (err) return <><PageHead title={label} /><p className="m-2 rounded-xl border border-amber-200 bg-amber-50 p-5 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200" role="alert">{err}</p></>;
  if (!def) return <><PageHead title={label} /><p className="p-8 text-slate-500" role="status">Loading…</p></>;

  const [code, name] = splitTitle(def.title);
  const isWeb = /\bwebwin\b/.test(def.cls);
  // fit the page width (up to 115%, as the reference); a screen that is only a little too tall for the window
  // shrinks to fit instead of scrolling, a much taller one keeps its size and scrolls
  const byWidth = Math.min(1.15, Math.max(.4, (avail - 4) / def.cw));
  const byHeight = (winH - 64 - 92 - 40 - 28) / def.ch;   // header, page heading, footer, bottom gap
  const fit = byHeight < byWidth && byHeight >= byWidth * .8 ? Math.max(.4, byHeight) : byWidth;
  const zoom = fit * userZoom;

  /* what a press does: the EGL wiring first, then what the caption says */
  const openCode = (c: string) => {
    const key = Object.keys(KNOWN).find(k => k.toUpperCase() === ("egl:" + c).toUpperCase()) || "egl:" + c;
    app.open({ kind: "screen", key, label: KNOWN[key] ? splitTitle(KNOWN[key].title)[1] : c });
  };
  const clear = () => { setValues({}); setSelected({}); setRows({}); };
  const fieldValues = () => Object.fromEntries(Object.entries(fields).filter(([, f]) => f.name).map(([id, f]) => [f.name, String(values[id] ?? f.value)]));
  const call = async (control: string, cap: string, programs: string[]) => {
    try {
      const r = await runAction({ screen: code || def.key, control, caption: cap, programs, fields: fieldValues() });
      if (r.fields) setValues(v => ({ ...v, ...r.fields }));
      if (r.rows) { setRows(t => ({ ...t, ...r.rows })); setSelected({}); }
      if (r.message) app.toast(r.message, r.control.URC && r.control.URC !== 4 ? "err" : "ok");
    } catch (e) { app.toast(e instanceof Error ? e.message : String(e), "err"); }
  };
  const onPress = (name: string | undefined, rawCap: string) => {
    const w = name ? def.wiring?.[name] : undefined;
    const cap = norm(w?.cap || rawCap);
    if (w) {
      const prints = /^(print|hard copy|print report|reprint)$/.test(cap) && w.to.every(t => !KNOWN["egl:" + t]);
      if (prints) return window.print();
      if (cap === "clear" && !w.to.length && !w.x) return clear();
      if (/^refresh( map)?$/.test(cap) && !w.to.length && !w.x) { clear(); return app.toast("Refreshed."); }
      if (w.off) return app.toast(`"${w.cap}" is disabled.`);
      if (w.to.length === 1) return openCode(w.to[0]);
      if (w.to.length > 1) return setPick({ cap: w.cap, to: w.to });
      if (w.x) return app.back();
      if (w.it) return openCode(w.it);
      if (w.m.length && /\?|^\s*(do you|are you|is this)\b/i.test(w.m[0])) { app.confirm(w.m[0]).then(ok => { if (ok) call(name!, w.cap, w.p); }); return; }
      if (w.m.length && !w.p.length && /^\s*sorry\b/i.test(w.m[0])) { app.info(w.m[0]); return; }
      if (w.m.length && !w.p.length) return app.toast(w.m[0]);
      if (w.p.length) { call(name!, w.cap, w.p); return; }
    }
    if (/^(print|hard copy)$/.test(cap)) return window.print();
    if (/^clear( screen)?$/.test(cap)) return clear();
    if (/^(return|exit|cancel|close)$/.test(cap)) return app.back();
    if (cap) call(name || cap, rawCap, w?.p || []);
  };

  if (def.menu) {
    let i = 0;
    const groups = def.menu.map(g => ({ name: g.name, list: g.list.filter(it => !it.off).map(it => ({ label: it.label, i: i++, open: it.to ? () => {
      const to = it.to!;
      app.open(to.kind === "map" ? { kind: "map", yard: to.yard, shop: to.shop, label: it.label } : { kind: "screen", key: to.key, label: it.label });
    } : undefined })) })).filter(g => g.list.length);
    return <><PageHead title={name || label} code={code} ver={def.ver.trim()} /><MenuTiles groups={groups} /></>;
  }

  return (
    <>
      <PageHead title={name || label} code={code} ver={def.ver.trim()} zoom={{ value: userZoom, set: setUserZoom }} />
      <div ref={box} className="animate-wv-in overflow-x-auto px-2 pb-6">
        <div data-screen className={`relative mx-auto leading-[normal] tracking-[-0.005em] ${isWeb || /\bmap\b/.test(def.cls) ? "rounded-[20px] border border-white/85 bg-white shadow-[0_0_0_1px_rgba(169,219,241,.4),0_30px_60px_-36px_rgba(11,37,69,.35)] dark:border-line-dark dark:bg-[#0c1826]" : ""}`}
          style={{ width: def.cw, height: def.ch, zoom, ...(def.flow ? def.rootStyle : {}) }}>
          <ScreenRenderer def={def} fill={fill} values={values} setValue={(id, v) => setValues(s => ({ ...s, [id]: v }))} onPress={onPress}
            selected={selected} onSelectRow={(t, r) => setSelected(s => ({ ...s, [t]: r }))} rows={rows} />
        </div>
      </div>
      {pick && (
        <div className="fixed inset-0 z-[9500] grid place-items-center bg-slate-900/40 p-4" onClick={e => e.target === e.currentTarget && setPick(null)}>
          <div role="dialog" aria-modal="true" aria-label={pick.cap} className="w-[440px] max-w-full rounded-2xl bg-white p-5 shadow-2xl dark:bg-panel-dark">
            <h2 className="mb-3 font-bold text-navy dark:text-ink-dark">"{pick.cap}" – depends on the data:</h2>
            {pick.to.map(t => (
              <button key={t} type="button" className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left hover:bg-sky dark:hover:bg-bart/14" onClick={() => { setPick(null); openCode(t); }}>
                <span>{KNOWN["egl:" + t] ? splitTitle(KNOWN["egl:" + t].title)[1] : t}</span><small className="text-slate-500">{t}</small>
              </button>))}
            <div className="mt-3 text-right"><button type="button" className="h-9 rounded-lg border border-slate-300 px-4 font-semibold dark:border-line-dark" onClick={() => setPick(null)}>Cancel</button></div>
          </div>
        </div>)}
    </>
  );
}
