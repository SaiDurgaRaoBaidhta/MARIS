/* Narratives of a wayside incident, repair or PM: read them one by one (Prv / Nxt) and add the next one (old screen WS13I). */
import { useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { addNarrative, latestNarrative, stepNarrative, type Narrative, type NarrativeContext } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const EMPTY: NarrativeContext = { incident: "", repairDoc: "", pmDept: "", pmEquipment: "", pmLocation: "", pmCycle: "", pmScheduled: "" };
const FIELDS: [keyof NarrativeContext, string][] = [["incident", "Incident"], ["repairDoc", "Repair doc"], ["pmDept", "PM dept"], ["pmEquipment", "PM equipment"],
  ["pmLocation", "PM location"], ["pmCycle", "PM cycle"], ["pmScheduled", "PM scheduled"]];

export function NarrativePage({ context }: { context?: Partial<NarrativeContext> }) {
  const [c, setC] = useState<NarrativeContext>({ ...EMPTY, ...context });
  const [n, setN] = useState<Narrative | null>(null);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const run = async (f: () => Promise<Narrative>) => {
    setBusy(true); setError("");
    try { setN(await f()); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };
  const add = () => run(async () => { const r = await addNarrative(c, text); setText(""); return r; });

  return (
    <>
      <PageHead title="Narrative" code="WS13I" />
      <section aria-label="Narrative of" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-3">
          {FIELDS.map(([k, l]) => (
            <label key={k} className={label}>{l}
              <input className={`${field} w-28`} value={c[k]} onChange={e => setC(x => ({ ...x, [k]: e.target.value }))} />
            </label>))}
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={() => run(() => latestNarrative(c))}>Show</button>
        </div>
      </section>
      {error && <p role="alert" className="mx-2 mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      {n && (
        <section aria-label="Current Narrative" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
          <p className="mb-2 text-[13px] text-slate-600 dark:text-[#c9d6e4]">Narr No. {n.row} of {n.maxRows} · ID {n.userGroup} · {n.date} {n.time}</p>
          <p className="whitespace-pre-wrap rounded-lg border border-slate-200 p-3 text-[13px] dark:border-line-dark">{n.text}</p>
          <div className="mt-3 flex gap-2">
            <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !n.hasPrev} onClick={() => run(() => stepNarrative(c, "prev", n.row, n.maxRows))}>Prv</button>
            <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !n.hasNext} onClick={() => run(() => stepNarrative(c, "next", n.row, n.maxRows))}>Nxt</button>
          </div>
        </section>)}
      <section className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <label className={label}>Additional Narrative
          <textarea className="min-h-24 rounded-lg border border-slate-300 p-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark" maxLength={1000}
            value={text} onChange={e => setText(e.target.value)} />
        </label>
        <button type="button" className={`${button} mt-3 bg-bart-600 text-white`} disabled={busy || !text.trim()} onClick={add}>Add Narrative</button>
      </section>
    </>
  );
}
