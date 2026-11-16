/* Pseudo train pages on the clean API:
 * - PseudoTrainPage: old VHYT09I (pseudo status train) with its printer window VHYT01I (Print on the same page). Takes
 *   ?yrdTrkgYrd from the screen that opens it (the maps, VHYT12I).
 * - HaywardYardMapPage: old Vhyt07i, the Hayward yard map; a track opens its detail (VHYT08I), Pseudo Status Train
 *   VHYT09I, Print VHYT24I, each with the yard the old screen passes.
 * - ConsistVerifyPage: old VHYT12I, verify / reset a pseudo train for the cars the car move window picked (?yrdTrkgYrd,
 *   yrdSubLoc, fromPos, toPos). The old prompts are asked here and the answers sent back.
 * Messages, yards, colours and prompts all come from the middleware (the old screens' own texts). */
import { useCallback, useEffect, useMemo, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  haywardYardMap, openVerify, printPseudoTrains, pseudoOptions, pseudoPrinters, pseudoTrains, resetConsist, verifyConsist,
  type HaywardYardMap, type Printer, type PseudoOptions, type PseudoReport, type VerifyRequest, type VerifyState,
} from "./pseudoApi";

const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** an old screen colour name as CSS: "GREY90" -> 90 % grey, "NAVY" -> navy */
function css(name?: string | null): string | undefined {
  if (!name) return undefined;
  const grey = /^GRE[YA](\d{1,3})$/i.exec(name.replace(/\s+/g, ""));
  if (grey) { const v = Math.round(Number(grey[1]) * 2.55); return `rgb(${v}, ${v}, ${v})`; }
  return name.replace(/\s+/g, "").toLowerCase();
}

/** the pass values in the key of the page in front (egl:CODE?field=value) */
function useLinkParams(): URLSearchParams {
  const { stack } = useApp();
  const top = stack[stack.length - 1];
  const key = top?.kind === "screen" ? top.key : "";
  return useMemo(() => new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : ""), [key]);
}

function Note({ text, error }: { text?: string | null; error?: boolean }) {
  if (!text || !text.trim()) return null;
  return <p role={error ? "alert" : "status"} className={`mt-3 rounded-lg px-3 py-2 ${error ? "bg-red-50 text-red-800" : "bg-sky-50 text-slate-800"}`}>{text}</p>;
}

/* ---------- VHYT09I + VHYT01I ---------- */

export function PseudoTrainPage() {
  const params = useLinkParams();
  const passed = params.get("yrdTrkgYrd") ?? "";
  const [options, setOptions] = useState<PseudoOptions | null>(null);
  const [yard, setYard] = useState("");
  const [report, setReport] = useState<PseudoReport | null>(null);
  const [page, setPage] = useState(1);
  const [printers, setPrinters] = useState<Printer[] | null>(null);
  const [printer, setPrinter] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  const list = useCallback(async (y: string) => {
    setError("");
    try { const r = await pseudoTrains(y); setReport(r); setPage(1); setNote(r.message); }
    catch (e) { setError(message(e)); }
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const o = await pseudoOptions(passed || undefined);
        setOptions(o); setYard(o.yard); setNote(o.message);
        if (passed) await list(passed);                                  // opened with a yard: listed at once (old VHYT09IS-INIT)
      } catch (e) { setError(message(e)); }
    })();
  }, [passed, list]);

  const size = options?.pageSize || 1;
  const pages = report ? Math.max(1, Math.ceil(report.consists.length / size)) : 0;
  const shown = report ? report.consists.slice((page - 1) * size, page * size) : [];

  return (
    <div>
      <PageHead title="Pseudo Status Train" code="VHYT09I" />
      <section className={panel} aria-label="Yard">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm font-semibold">Yard
            <select className="h-9 rounded-lg border border-slate-300 px-2" value={yard} onChange={e => setYard(e.target.value)}>
              {options?.yards.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>
          <button type="button" className={button} disabled={!yard || !!report} onClick={() => void list(yard)}>List</button>
          <button type="button" className={button} disabled={!report} onClick={() => { setReport(null); setPage(1); setNote(options?.message ?? ""); }}>Clear</button>
          <button type="button" className={button} disabled={!report?.printable}
            onClick={async () => { try { setPrinters(await pseudoPrinters()); } catch (e) { setError(message(e)); } }}>Print</button>
        </div>
        <Note text={note} />
        <Note text={error} error />
      </section>
      {printers && (
        <section className={panel} aria-label="Printers">
          <label className="flex flex-col gap-1 text-sm font-semibold">Printer
            <select className="h-9 rounded-lg border border-slate-300 px-2" value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="" />
              {printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <div className="mt-3 flex gap-3">
            <button type="button" className={button} disabled={!printer}
              onClick={async () => { try { await printPseudoTrains(report?.yard ?? yard, printer); setPrinters(null); setPrinter(""); } catch (e) { setError(message(e)); } }}>Hard Copy</button>
            <button type="button" className={button} onClick={() => { setPrinters(null); setPrinter(""); }}>Return</button>
          </div>
        </section>
      )}
      {report && report.consists.length > 0 && (
        <section className={panel} aria-label="Pseudo trains">
          <p className="mb-2 text-sm">Number of Consists: {report.consists.length} · Page {page} of {pages}</p>
          {shown.map(c => (
            <table key={c.consist + c.track} aria-label={`Pseudo train ${c.consist}`} className="mb-3 w-full text-sm">
              <caption className="text-left font-semibold">Pseudo Train {c.consist} · Yard {c.yard} · {c.track} · Last Upd {c.dateLastUpdated} {c.timeLastUpdated}</caption>
              <tbody>
                <tr>{c.cars.map(x => <td key={x.position} style={{ color: css(x.colour) }}>{x.car}</td>)}</tr>
                <tr>{c.cars.map(x => <td key={x.position}>{x.homeYard}</td>)}</tr>
              </tbody>
            </table>
          ))}
          <div className="flex gap-3">
            <button type="button" className={button} disabled={page <= 1} onClick={() => setPage(page - 1)}>Prv Page</button>
            <button type="button" className={button} disabled={page >= pages} onClick={() => setPage(page + 1)}>Nxt Page</button>
          </div>
        </section>
      )}
    </div>
  );
}

/* ---------- Vhyt07i ---------- */

export function HaywardYardMapPage() {
  const app = useApp();
  const [map, setMap] = useState<HaywardYardMap | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setError("");
    try { setMap(await haywardYardMap()); } catch (e) { setError(message(e)); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const open = (code: string, params: Record<string, string>) =>
    app.open({ kind: "screen", key: `egl:${code}?${new URLSearchParams(params)}`, label: code });

  return (
    <div>
      <PageHead title="Hayward Yard Map" code="VHYT07I" />
      <section className={panel} aria-label="Map buttons">
        <div className="flex flex-wrap gap-3">
          <button type="button" className={button} onClick={() => void load()}>Refresh Map</button>
          <button type="button" className={button} disabled={!map} onClick={() => map && open("VHYT09I", { yrdTrkgYrd: map.pseudoTrainYard })}>Pseudo Status Train</button>
          <button type="button" className={button} disabled={!map} onClick={() => map && open("VHYT24I", { yrdTrkgYrd: map.printYard })}>Print</button>
        </div>
        <Note text={error} error />
      </section>
      {map && (
        <section className={panel} aria-label="Tracks">
          {map.tracks.map(t => {
            const spot = map.spots.find(s => s.track === t.track);
            const cars = map.cars.filter(c => c.track === t.track).sort((a, b) => a.position - b.position);
            return (
              <div key={t.track} className="mb-2 flex flex-wrap items-center gap-2">
                <button type="button" className={button} disabled={!spot} onClick={() => open("VHYT08I", { yrdSubLoc: t.track, yrdTrkgYrd: map.yard })}>
                  {spot?.caption ?? t.track}
                </button>
                {cars.map(c => (
                  <span key={c.position} title={`${c.track} spot ${c.position}`} className="rounded px-1.5 py-0.5 font-mono text-sm"
                    style={{ color: css(c.colour), backgroundColor: css(c.background) }}>{c.label}</span>
                ))}
              </div>
            );
          })}
          {map.spots.filter(s => !map.tracks.some(t => t.track === s.track)).map(s => (
            <button key={s.track} type="button" className={`${button} mr-2`} onClick={() => open("VHYT08I", { yrdSubLoc: s.track, yrdTrkgYrd: map.yard })}>{s.caption}</button>
          ))}
        </section>
      )}
    </div>
  );
}

/* ---------- VHYT12I ---------- */

export function ConsistVerifyPage() {
  const app = useApp();
  const params = useLinkParams();
  const base = useMemo<VerifyRequest>(() => ({
    yard: params.get("yrdTrkgYrd") ?? "", track: params.get("yrdSubLoc") ?? "",
    fromPosition: Number(params.get("fromPos") ?? 0), toPosition: Number(params.get("toPos") ?? 0),
  }), [params]);
  const [state, setState] = useState<VerifyState | null>(null);
  const [consist, setConsist] = useState("");
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [error, setError] = useState("");

  /** runs a step; the old prompts are asked and the step sent again with the answer */
  const run = useCallback(async (step: (r: VerifyRequest) => Promise<VerifyState>, extra: Partial<VerifyRequest>, given: Record<string, string>) => {
    setError("");
    try {
      let a = given;
      for (;;) {
        const s = await step({ ...base, ...extra, answers: a });
        if (s.question) {
          const yes = await app.confirm(s.question.text);
          a = { ...a, [s.question.key]: yes ? "yes" : "no" };
          continue;
        }
        if (s.notice) await app.info(s.notice);
        if (s.closed) { app.back(); return; }
        setState(s);
        setAnswers(a);
        return;
      }
    } catch (e) { setError(message(e)); }
  }, [app, base]);

  useEffect(() => { void run(openVerify, {}, {}); }, [run]);

  return (
    <div>
      <PageHead title="Verify Pseudo Train" code="VHYT12I" />
      <section className={panel} aria-label="Consist">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm font-semibold">Consist No
            <input className="h-9 w-24 rounded-lg border border-slate-300 px-2" maxLength={3} value={consist} onChange={e => setConsist(e.target.value)} />
          </label>
          <button type="button" className={button} disabled={!state?.verifyEnabled} onClick={() => void run(verifyConsist, { consist }, answers)}>Verify</button>
          <button type="button" className={button} onClick={() => setConsist("")}>Clear</button>
          <button type="button" className={button} disabled={!state?.resetEnabled} onClick={() => void run(resetConsist, {}, answers)}>Reset</button>
          <button type="button" className={button} onClick={() => app.open({ kind: "screen", key: `egl:VHYT09I?${new URLSearchParams({ yrdTrkgYrd: base.yard })}`, label: "VHYT09I" })}>Pseudo Status Train</button>
        </div>
        <Note text={state?.message} />
        <Note text={error} error />
      </section>
      {state && (
        <section className={panel}>
          <table aria-label="Cars" className="w-full text-sm">
            <thead><tr><th>#</th><th>Car</th><th>Con</th><th>Status</th><th>Home Yard</th></tr></thead>
            <tbody>
              {state.cars.map(c => (
                <tr key={c.position}>
                  <td>{c.position}</td>
                  <td style={{ color: css(c.colour) }}>{c.car}</td>
                  <td style={{ color: css(c.consistColour) }}>{c.consist}</td>
                  <td>{c.location}</td>
                  <td>{c.homeYard}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
