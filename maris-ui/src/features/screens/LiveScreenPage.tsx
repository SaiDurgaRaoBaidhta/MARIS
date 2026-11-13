/* A MARIS screen running live: the middleware's screen engine runs the old screen's own logic and calls the MARIS
 * backend; this page draws the screen's layout with the widget state it gets back and sends what the user does. */
import { useEffect, useMemo, useRef, useState } from "react";
import { useElementWidth } from "@/shared/hooks/useElementWidth";
import { useWindowHeight } from "@/shared/hooks/useWindowHeight";
import { useApp } from "@/shared/lib/app-state";
import { fillTokens } from "@/shared/lib/tokens";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchScreen } from "./api";
import { answerDialog, openLive, sendEvent, type Change, type LiveState } from "./live";
import { KNOWN, layoutKey } from "./registry";
import { ScreenRenderer, initialValues, type FieldValues } from "./ScreenRenderer";
import { splitTitle } from "./ScreenPage";
import type { ScreenDef } from "./types";

const layoutFor = (screen: string) => {
  const key = Object.keys(KNOWN).find(k => k.toUpperCase() === ("EGL:" + screen).toUpperCase()) || "egl:" + screen;
  return layoutKey(key);
};

export function LiveScreenPage({ screen, label }: { screen: string; label: string }) {
  const app = useApp();
  const [live, setLive] = useState<LiveState | null>(null);
  const [def, setDef] = useState<ScreenDef | null>(null);
  const [defFor, setDefFor] = useState("");
  const [values, setValues] = useState<FieldValues>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [userZoom, setUserZoom] = useState(1);
  const [box, avail] = useElementWidth();
  const winH = useWindowHeight();
  const sent = useRef<FieldValues>({});
  const queue = useRef(Promise.resolve());

  const top = live?.windows.at(-1);

  // open the screen (fresh, like opening it from the old menu)
  useEffect(() => {
    let current = true;                               // a later open (or leaving the page) supersedes this one
    setLive(null); setDef(null); setErr(""); setValues({}); sent.current = {};
    setBusy(true);
    queue.current = queue.current.then(() => openLive(screen)
      .then(r => { if (current) setLive(r); })
      .catch(e => { if (current) setErr(e instanceof Error ? e.message : String(e)); })
      .finally(() => { if (current) setBusy(false); }));
    return () => { current = false; };
  }, [screen]);

  // the layout of the window on top
  useEffect(() => {
    if (!top || defFor === top.screen) return;
    setValues({}); sent.current = {};
    fetchScreen(layoutFor(top.screen)).then(d => { setDef(d); setDefFor(top.screen); })
      .catch(() => setErr(`${top.screen}: no layout for this screen in the UI.`));
  }, [top, defFor]);

  // the screen closed itself (Return / Exit in the old logic): back to where the user came from
  useEffect(() => {
    if (live && !live.windows.length && !live.dialog) app.back();
  }, [live, app]);

  const user = app.session?.user || "";
  const fill = useMemo(() => (v: string) => fillTokens(v, user), [user]);
  const fields = useMemo(() => def ? initialValues(def, fill) : {}, [def, fill]);

  /* field edits the engine has not seen yet, as the widgets would commit them */
  const pending = (): Change[] => {
    const out: Change[] = [];
    for (const [id, v] of Object.entries(values)) {
      const f = fields[id];
      if (!f?.name || sent.current[id] === v) continue;
      out.push({ widget: f.name, attr: typeof v === "boolean" ? "selection" : "object", value: v });
    }
    return out;
  };

  /* one request at a time, in order, like the old single-threaded UI */
  const run = (f: () => Promise<LiveState>) => {
    queue.current = queue.current.then(async () => {
      setBusy(true);
      try {
        const r = await f();
        setLive(r);
        const w = r.windows.at(-1);
        if (w && top && w.id !== top.id) { setValues({}); sent.current = {}; }
        else sent.current = { ...sent.current, ...values };
      } catch (e) {
        app.toast(e instanceof Error ? e.message : String(e), "err");
      } finally {
        setBusy(false);
      }
    });
  };
  const act = (widget?: string, event?: string, value?: unknown) => {
    if (!top) return;
    const ch = pending();
    if (!widget && !ch.length) return;
    run(() => sendEvent(top.id, ch, widget, event, value));
  };

  if (err) return <><PageHead title={label} /><p className="m-2 rounded-xl border border-amber-200 bg-amber-50 p-5 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200" role="alert">{err}</p></>;
  if (!live || !def || !top) return <><PageHead title={label} /><p className="p-8 text-slate-500" role="status">{busy ? "Opening the screen in MARIS…" : "Loading…"}</p></>;

  const [code, name] = splitTitle(top.title || def.title);
  const byWidth = Math.min(1.15, Math.max(.4, (avail - 4) / def.cw));
  const byHeight = (winH - 64 - 92 - 40 - 28) / def.ch;
  const fit = byHeight < byWidth && byHeight >= byWidth * .8 ? Math.max(.4, byHeight) : byWidth;
  const zoom = fit * userZoom;
  const calls = live.calls || [];

  return (
    <>
      <PageHead title={name || label} code={code || top.screen} ver={def.ver.trim()} zoom={{ value: userZoom, set: setUserZoom }} />
      {live.windows.length > 1 && (
        <nav aria-label="Open windows" className="mx-2 mb-2 flex flex-wrap items-center gap-1 text-[13px] text-slate-500">
          {live.windows.map((w, i) => <span key={w.id} className={i === live.windows.length - 1 ? "font-semibold text-navy dark:text-ink-dark" : ""}>{i > 0 && " › "}{w.screen}</span>)}
          <button type="button" className="ml-auto h-8 rounded-lg border border-slate-300 px-3 font-semibold dark:border-line-dark" onClick={() => act("@window", "close")}>Close window</button>
        </nav>)}
      <div ref={box} className="animate-wv-in relative overflow-x-auto px-2 pb-2">
        <div data-screen data-live={top.screen} className="relative mx-auto leading-[normal] tracking-[-0.005em]" style={{ width: def.cw, height: def.ch, zoom }}>
          <ScreenRenderer def={def} fill={fill} values={values} setValue={(id, v) => setValues(s => ({ ...s, [id]: v }))}
            onPress={n => n && act(n, "clicked")} selected={{}} onSelectRow={() => {}}
            live={top.widgets} onSelect={(w, i) => act(w, "select", i)} onCommit={() => act()} />
        </div>
        {busy && <div className="pointer-events-none absolute inset-0 grid animate-wait place-items-center bg-white/30 dark:bg-black/25" role="status" aria-live="polite">
          <span className="rounded-lg bg-white px-4 py-2 text-sm font-semibold text-navy shadow dark:bg-panel-dark dark:text-ink-dark">Waiting for MARIS…</span></div>}
      </div>
      {calls.length > 0 && (
        <p className="mx-2 mb-4 text-[12px] text-slate-500" aria-label="MARIS backend calls">
          MARIS calls: {calls.map((c, i) => <span key={i} className={c.errorCode !== "00000000" ? "text-red-600" : ""}>{i > 0 && ", "}{c.program}{c.ms !== undefined ? ` (${c.ms} ms)` : ""}{c.errorCode !== "00000000" ? ` – ${c.errorCode}` : ""}</span>)}
        </p>)}
      {live.dialog && (
        <div className="fixed inset-0 z-[9500] grid place-items-center bg-slate-900/40 p-4">
          <div role="alertdialog" aria-modal="true" aria-labelledby="live-dlg-t" className="w-[460px] max-w-full rounded-2xl bg-white p-5 shadow-2xl dark:bg-panel-dark">
            <h2 id="live-dlg-t" className="mb-2 font-bold text-navy dark:text-ink-dark">{live.dialog.title || "MARIS"}</h2>
            <p className="mb-4 whitespace-pre-wrap text-slate-700 dark:text-[#c9d6e4]">{live.dialog.message}</p>
            <div className="flex justify-end gap-2">
              {live.dialog.buttons.map((b, i) => (
                <button key={b} type="button" autoFocus={i === 0} className={`h-9 rounded-lg px-4 font-semibold ${i === 0 ? "bg-bart-600 text-white" : "border border-slate-300 dark:border-line-dark"}`}
                  onClick={() => run(() => answerDialog(live.dialog!.id, b))}>{b}</button>))}
            </div>
          </div>
        </div>)}
    </>
  );
}
