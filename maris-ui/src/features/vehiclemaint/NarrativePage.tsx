/* old VHNAR01I: one narrative of an incident (or of an incident's repair document), Prv / Nxt, add a narrative, rewrite the one shown.
 * Opened by incident and repair screens with the narrative they show (query params, see narrativeApi.ts NARRATIVE_PARAMS). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { addNarrative, opening, step, updateNarrative, type Narrative } from "./narrativeApi";

const field = "rounded-lg border border-slate-300 bg-white px-2 py-1 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the parent's params: from the screen key of the page in front ("egl:VHNAR01I?incdNo=…") or the address bar */
function useParams(given?: URLSearchParams): URLSearchParams {
  const app = useApp();
  if (given) return given;
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  const q = key.includes("?") ? key.slice(key.indexOf("?") + 1) : window.location.search.replace(/^\?/, "");
  return new URLSearchParams(q);
}

export function NarrativePage({ params }: { params?: URLSearchParams }) {
  const app = useApp();
  const p = useParams(params);
  const paramText = p.toString();
  const [n, setN] = useState<Narrative | null>(null);
  const [current, setCurrent] = useState("");
  const [fresh, setFresh] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const show = (x: Narrative) => { setN(x); setCurrent(x.narr); };
  useEffect(() => { opening(new URLSearchParams(paramText)).then(show).catch(e => setError(message(e))); }, [paramText]);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError(""); setNote("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };

  if (!n) return <><PageHead title="Narrative" code="VHNAR01I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;

  return (
    <>
      <PageHead title="Narrative" code="VHNAR01I" />
      <section className={panel}>
        <div className="flex flex-wrap gap-4 text-[13px]">
          <span>Incd No <b>{n.incdNo}</b></span>
          {n.reprDocNo !== 0 && <span>Repr Doc No <b>{n.reprDocNo}</b></span>}
          <span>Narr No. <b data-testid="narr-no">{n.currentRow}</b></span>
          <span>Id <b>{n.userGrp}</b></span>
          <span>Date <b>{n.dateNarr}</b></span>
          <span>Time <b>{n.timeNarr}</b></span>
        </div>
        {n.ciPart && <p className="mt-2 font-semibold" data-testid="ci-part">{n.ciPart}</p>}
        <label className={label + " mt-3"}>Current narrative
          <textarea className={field} rows={6} value={current} onChange={e => setCurrent(e.target.value)} maxLength={1000} />
        </label>
        <div className="mt-2 flex flex-wrap gap-2">
          <button className={button + " bg-slate-200"} disabled={busy || !n.canPrev} onClick={() => run(async () => show(await step(n, "prev")))}>Prv</button>
          <button className={button + " bg-slate-200"} disabled={busy || !n.canNext} onClick={() => run(async () => show(await step(n, "next")))}>Nxt</button>
          <button className={button + " bg-brand-600 text-white"} disabled={busy} onClick={() => run(async () => setNote((await updateNarrative(n, current)).message))}>Update Narrative</button>
        </div>
        <label className={label + " mt-4"}>New narrative
          <textarea className={field} rows={4} value={fresh} onChange={e => setFresh(e.target.value)} maxLength={1000} />
        </label>
        <div className="mt-2 flex flex-wrap gap-2">
          <button className={button + " bg-brand-600 text-white"} disabled={busy}
            onClick={() => run(async () => { const a = await addNarrative(n, fresh); show(a.narrative); setFresh(""); setNote(a.message); })}>Add Narrative</button>
          <button className={button + " bg-slate-200"} onClick={() => app.back()}>Return</button>
        </div>
        {note && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-800">{note}</p>}
        {error && <p role="alert" className="mt-3 whitespace-pre-line rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
      </section>
    </>
  );
}
