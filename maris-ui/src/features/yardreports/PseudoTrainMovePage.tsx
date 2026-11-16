/* Move a pseudo train's cars to another yard (old screen VHIC03I), on the clean API. The old window was opened from
 * VHYT11I with the cars' yard and positions; until VHYT11I is rebuilt they are entered here. The checks (not the same yard,
 * a yard chosen) and the messages are the old screen's, from the middleware. Move writes (VHIC03A). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchMoveYards, movePseudoTrain, type MoveRequest } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

export function PseudoTrainMovePage() {
  const [yards, setYards] = useState<string[] | null>(null);
  const [r, setR] = useState<MoveRequest>({ fromYard: "", fromPosition: 1, toPosition: 1, toYard: "" });
  const [done, setDone] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { fetchMoveYards().then(setYards).catch(e => setError(e instanceof Error ? e.message : String(e))); }, []);
  const set = <K extends keyof MoveRequest>(k: K, v: MoveRequest[K]) => setR(x => ({ ...x, [k]: v }));
  const clear = () => { setDone(""); setError(""); };
  const move = async () => {
    setBusy(true); clear();
    try { const o = await movePseudoTrain(r); if (o.moved) setDone(o.message); else setError(o.message); }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };

  const yardSelect = (name: "fromYard" | "toYard", text: string) => (
    <label className={label}>{text}
      <select className={field} value={r[name]} onChange={e => set(name, e.target.value)}>
        <option value="">Choose a yard</option>
        {yards?.map(y => <option key={y} value={y}>{y}</option>)}
      </select>
    </label>
  );
  return (
    <>
      <PageHead title="Move Pseudo Train to another Yard" code="VHIC03I" />
      <section aria-label="Move cars" className="mx-2 mb-6 max-w-2xl rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        {!yards && !error && <p className="text-slate-500">Loading the yards from MARIS…</p>}
        {yards && <>
        <div className="grid gap-4 sm:grid-cols-2">
          {yardSelect("fromYard", "From yard")}
          {yardSelect("toYard", "To yard")}
          <label className={label}>From position
            <input type="number" min={1} className={field} value={r.fromPosition} onChange={e => set("fromPosition", Number(e.target.value))} />
          </label>
          <label className={label}>To position
            <input type="number" min={1} className={field} value={r.toPosition} onChange={e => set("toPosition", Number(e.target.value))} />
          </label>
        </div>
        <div className="mt-4 flex gap-2">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !yards} onClick={() => void move()}>Move</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        </>}
        {done && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{done}</p>}
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
    </>
  );
}
