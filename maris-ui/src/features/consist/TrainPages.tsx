/* Lay up a train (old VHLT01I) and reconfigure a train (old VHTR01I, its confirmation window VHTR02I), on the clean API.
 * The rules and messages are the old screens': the middleware runs their own statement groups. The pages keep the user's
 * steps and send them all on each call, as the old window kept them. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  checkLayUpTrain, fetchLayUpOptions, fetchLayUpTracks, fetchReconfigureOptions, fetchReconfigureTracks, layUpTrain, reconfigureState,
  reconfigureTrain, type LayUpOptions, type LayUpTrain, type ReconfigureOptions, type ReconfigureState, type Steps,
} from "./trainsApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Note({ value, ok }: { value: string; ok?: boolean }) {
  if (!value) return null;
  return <p role={ok ? "status" : "alert"} className={`mt-3 rounded-lg px-3 py-2 ${ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}>{value}</p>;
}

/** yard and its transfer / storage tracks */
function YardTrack({ yards, yard, setYard, tracks, track, setTrack }: {
  yards: string[]; yard: string; setYard: (y: string) => void; tracks: string[]; track: string; setTrack: (t: string) => void;
}) {
  return <>
    <label className={label}>Location
      <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
        <option value="" />
        {yards.map(y => <option key={y} value={y}>{y}</option>)}
      </select>
    </label>
    <label className={label}>Transfer/Storage Track
      <select className={field} value={track} onChange={e => setTrack(e.target.value)}>
        <option value="" />
        {tracks.map(t => <option key={t} value={t}>{t}</option>)}
      </select>
    </label>
  </>;
}

export function LayUpPage() {
  const [o, setO] = useState<LayUpOptions | null>(null);
  const [yard, setYard] = useState(""), [track, setTrack] = useState(""), [consist, setConsist] = useState(""), [time, setTime] = useState("");
  const [tracks, setTracks] = useState<string[]>([]);
  const [train, setTrain] = useState<LayUpTrain | null>(null);
  const [error, setError] = useState(""), [done, setDone] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { fetchLayUpOptions().then(x => { setO(x); setYard(x.yard); setTime(x.time); }).catch(e => setError(text(e))); }, []);
  useEffect(() => { if (yard) fetchLayUpTracks(yard).then(setTracks).catch(e => setError(text(e))); else setTracks([]); setTrack(""); }, [yard]);
  const check = async () => {
    setError(""); setDone(""); setTrain(null);
    if (!consist.trim()) return;
    try { setTrain(await checkLayUpTrain(consist.trim())); } catch (e) { setError(text(e)); }
  };
  const layUp = async () => {
    setBusy(true); setError(""); setDone("");
    try { setDone((await layUpTrain({ yard, track, consist: consist.trim(), time })).message); } catch (e) { setError(text(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Lay-up Train" code="VHLT01I" />
      <section aria-label="Lay-up" className={panel}>
        {!o && !error && <p className="text-slate-500">Loading from MARIS…</p>}
        {o && <div className="grid gap-4 md:grid-cols-4">
          <label className={label}>Train<input className={field} maxLength={3} value={consist} onChange={e => setConsist(e.target.value)} onBlur={check} /></label>
          <label className={label}>Time (HHMM)<input className={field} maxLength={5} value={time} onChange={e => setTime(e.target.value)} /></label>
          <YardTrack yards={o.yards} yard={yard} setYard={setYard} tracks={tracks} track={track} setTrack={setTrack} />
        </div>}
        {train && !train.layUp && <Note value={train.message} />}
        <Note value={error} />
        <Note value={done} ok />
        <div className="mt-4 flex gap-2">
          <button type="button" className={primary} disabled={busy || !train?.layUp} onClick={layUp}>Lay-up Train</button>
          <button type="button" className={secondary} onClick={() => { setConsist(""); setTrain(null); setDone(""); setError(""); setTrack(""); if (o) setTime(o.time); }}>Clear Screen</button>
        </div>
      </section>
    </>
  );
}

const blank: Steps = { action: 2, consist: "", splits: [] };

function Cars({ title, consist, cars }: { title: string; consist?: string; cars: string[] }) {
  return (
    <div aria-label={title} role="group" className="mb-2">
      <p className="text-[13px] font-semibold text-slate-600">{title} {consist}</p>
      <div className="flex flex-wrap gap-1">{cars.map((c, i) => <span key={i} className="rounded border border-slate-300 px-2 py-1 font-mono">{c || "-"}</span>)}</div>
    </div>
  );
}

export function ReconfigurePage() {
  const [o, setO] = useState<ReconfigureOptions | null>(null);
  const [steps, setSteps] = useState<Steps>(blank);
  const [state, setState] = useState<ReconfigureState | null>(null);
  const [yard, setYard] = useState(""), [track, setTrack] = useState(""), [time, setTime] = useState("");
  const [tracks, setTracks] = useState<string[]>([]);
  const [question, setQuestion] = useState(""), [error, setError] = useState(""), [done, setDone] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { fetchReconfigureOptions().then(x => { setO(x); setTime(x.time); }).catch(e => setError(text(e))); }, []);
  useEffect(() => { if (yard) fetchReconfigureTracks(yard).then(setTracks).catch(e => setError(text(e))); else setTracks([]); setTrack(""); }, [yard]);
  const refresh = async (s: Steps) => {
    setSteps(s); setError(""); setDone("");
    if (!s.consist.trim()) { setState(null); return; }
    try { setState(await reconfigureState(s)); } catch (e) { setError(text(e)); }
  };
  const send = async (confirmed: boolean) => {
    setBusy(true); setError(""); setDone("");
    try {
      const r = await reconfigureTrain({ steps, yard, track, time, confirmed });
      if (r.question) setQuestion(r.question); else { setQuestion(""); setDone(r.message); }
    } catch (e) { setError(text(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Reconfigure Train" code="VHTR01I" />
      <section aria-label="Reconfigure" className={panel}>
        {!o && !error && <p className="text-slate-500">Loading from MARIS…</p>}
        {o && <>
          <fieldset className="mb-3 flex gap-4"><legend className="text-[13px] font-semibold text-slate-600">Service Action</legend>
            <label><input type="radio" name="action" checked={steps.action === 1} onChange={() => refresh({ ...blank, action: 1, consist: steps.consist })} /> Build-Up</label>
            <label><input type="radio" name="action" checked={steps.action === 2} onChange={() => refresh({ ...blank, action: 2, consist: steps.consist })} /> Service Reduction</label>
          </fieldset>
          <div className="grid gap-4 md:grid-cols-4">
            <label className={label}>Train<input className={field} maxLength={3} value={steps.consist} onChange={e => setSteps({ ...steps, consist: e.target.value })}
              onBlur={() => refresh({ ...steps, splits: [] })} /></label>
            <label className={label}>Time (HHMM)<input className={field} maxLength={5} value={time} onChange={e => setTime(e.target.value)} /></label>
            <YardTrack yards={o.yards} yard={yard} setYard={setYard} tracks={tracks} track={track} setTrack={setTrack} />
            {steps.action === 1 && <>
              <label className={label}>Left Consist<input className={field} maxLength={3} value={steps.leftConsist ?? ""} onChange={e => setSteps({ ...steps, leftConsist: e.target.value })} /></label>
              <label className={label}>Right Consist<input className={field} maxLength={3} value={steps.rightConsist ?? ""} onChange={e => setSteps({ ...steps, rightConsist: e.target.value })}
                onBlur={() => refresh({ ...steps, pseudo: null })} /></label>
            </>}
          </div>
          {state && <div className="mt-4">
            <Cars title="Train" consist={steps.consist} cars={state.cars} />
            {steps.action === 2 && state.split && <div className="mb-2 flex flex-wrap gap-1" role="group" aria-label="Split">
              {o.splitPositions.map(p => <button key={p} type="button" className={secondary} onClick={() => refresh({ ...steps, splits: [...steps.splits, p] })}>Split before car {p}</button>)}
            </div>}
            <Cars title="Left Consist:" consist={state.left.consist} cars={state.left.cars} />
            <Cars title="Right Consist:" consist={state.right.consist} cars={state.right.cars} />
            {state.newPseudo && <p className="text-[13px]">New Pseudo Train: {state.newPseudo}</p>}
            {state.message && <Note value={state.message} ok={state.reconfigure} />}
          </div>}
          {question && <div role="dialog" aria-label="Confirm reconfiguration" className="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3">
            <p>{question}</p>
            <div className="mt-2 flex gap-2">
              <button type="button" className={primary} disabled={busy} onClick={() => send(true)}>Yes</button>
              <button type="button" className={secondary} onClick={() => setQuestion("")}>No</button>
            </div>
          </div>}
          <Note value={error} />
          <Note value={done} ok />
          <div className="mt-4 flex gap-2">
            <button type="button" className={primary} disabled={busy || !state?.reconfigure} onClick={() => send(false)}>Reconfigure</button>
            <button type="button" className={secondary} onClick={() => { setSteps(blank); setState(null); setQuestion(""); setDone(""); setError(""); setYard(""); }}>Clear</button>
          </div>
        </>}
      </section>
    </>
  );
}
