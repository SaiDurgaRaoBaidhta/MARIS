/* One track's cars (old VHYT08I) and the windows it opens: the report printer (VHYT02I), a car's move reason (VHYT06I) and
 * fixing the order of the cars (VHYT14I), on the clean API. A window opened from another page gets the old pass values in
 * its key (egl:VHYT06I?yrdTrkgYrd=..&yrdSubLoc=..&carNo=..&posnNo=..). */
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import {
  changeReason, fetchFixOrder, fetchReason, fetchReportOptions, fetchTrack, fixOrder, printTrackReport, trackAction,
  type FixView, type ReasonView, type ReportOptions, type Track, type TrackAction,
} from "./trackApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the pass values in the key of the page in front (egl:CODE?field=value) */
function useLinkParams(): URLSearchParams {
  const { stack } = useApp();
  const top = stack[stack.length - 1];
  const key = top?.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : "");
}

function Note({ text, kind = "info" }: { text: string; kind?: "info" | "error" }) {
  if (!text.trim()) return null;
  return <p role={kind === "error" ? "alert" : "status"} className={`mt-3 rounded-lg px-3 py-2 ${kind === "error" ? "bg-red-50 text-red-800" : "bg-emerald-50 text-emerald-800"}`}>{text}</p>;
}

/** yard and track: from the link, or typed when the page is opened on its own */
function useYardTrack() {
  const p = useLinkParams();
  const [yard, setYard] = useState(p.get("yrdTrkgYrd") ?? "");
  const [track, setTrack] = useState(p.get("yrdSubLoc") ?? "");
  return { yard, setYard, track, setTrack, linked: !!(p.get("yrdTrkgYrd") && p.get("yrdSubLoc")), params: p };
}

function YardTrackForm({ yard, setYard, track, setTrack, onShow }: { yard: string; setYard: (v: string) => void; track: string; setTrack: (v: string) => void; onShow: () => void }) {
  return (
    <section aria-label="Choose a track" className={panel}>
      <div className="flex flex-wrap items-end gap-3">
        <label className={label}>Yard<input className={field} maxLength={4} value={yard} onChange={e => setYard(e.target.value.toUpperCase())} /></label>
        <label className={label}>Track<input className={field} maxLength={10} value={track} onChange={e => setTrack(e.target.value.toUpperCase())} /></label>
        <button type="button" className={primary} disabled={!yard || !track} onClick={onShow}>Display</button>
      </div>
    </section>
  );
}

/** old VHYT08I: 30 positions a page; click positions, then Change Reason, Fix Order, Move Car or Move Yrd */
export function TrackPage() {
  const app = useApp();
  const { yard, setYard, track, setTrack, linked } = useYardTrack();
  const [t, setT] = useState<Track | null>(null);
  const [page, setPage] = useState(1);
  const [clicked, setClicked] = useState<number[]>([]);
  const [to, setTo] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async (p: number) => {
    setBusy(true); setError(""); setNote(""); setClicked([]);
    try { const r = await fetchTrack(yard, track, p); setT(r); setPage(r.page); setNote(r.message); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }, [yard, track]);
  useEffect(() => { if (linked) void load(1); }, [linked]); // eslint-disable-line react-hooks/exhaustive-deps

  const act = async (action: TrackAction) => {
    setBusy(true); setError(""); setNote("");
    try {
      const r = await trackAction(yard, track, page, clicked, action, to || undefined);
      setT(r.track); setClicked([]); setNote(r.message);
      if (r.opens) {
        const q = new URLSearchParams(Object.entries(r.with).map(([k, v]) => [k, String(v)]));
        app.open({ kind: "screen", key: `egl:${r.opens}?${q}`, label: r.opens });
      }
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const toggle = (slot: number) => setClicked(c => (c.includes(slot) ? c.filter(x => x !== slot) : [...c, slot]));

  return (
    <>
      <PageHead title="Yard Detail Inquiry" code="VHYT08I" />
      {!linked && <YardTrackForm yard={yard} setYard={setYard} track={track} setTrack={setTrack} onShow={() => load(1)} />}
      {busy && !t && <p className="mx-2 text-slate-500">Loading the track from MARIS…</p>}
      <Note text={error} kind="error" />
      {t && (
        <section aria-label={`Track ${t.subLocation}`} className={panel}>
          <h2 className="mb-3 font-semibold">{t.yard} – {t.yardDescription} · {t.subLocation} – {t.subLocationDescription}</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-slate-500">
                <th>Position</th><th>Car</th><th>Status</th><th>Home yard</th><th>{t.pseudoCaption}</th>
              </tr></thead>
              <tbody>
                {t.positions.map(x => (
                  <tr key={x.slot} className={x.usable ? "bg-sky-50" : ""}>
                    <td><button type="button" aria-pressed={clicked.includes(x.slot)} className={`${secondary} ${clicked.includes(x.slot) ? "bg-yellow-200" : ""}`}
                      onClick={() => toggle(x.slot)} aria-label={`Position ${x.position}`}>{x.position}</button></td>
                    <td className="font-mono">{x.carShown}</td>
                    <td className={x.alert ? "text-red-700" : ""}>{x.status}</td>
                    <td>{x.homeYard}</td>
                    <td>{x.pseudoTrain}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-wrap items-end gap-2">
            <button type="button" className={secondary} disabled={!t.previous || busy} onClick={() => load(page - 1)}>Prv</button>
            <button type="button" className={secondary} disabled={!t.next || busy} onClick={() => load(page + 1)}>Nxt</button>
            <button type="button" className={primary} disabled={busy} onClick={() => act("change-reason")}>Change Reason</button>
            <button type="button" className={primary} disabled={busy || !t.canFixOrder} onClick={() => act("fix-order")}>Fix Order</button>
            <label className={label}>Move Car to Location
              <select className={field} value={to} onChange={e => setTo(e.target.value)}>
                <option value="" />
                {t.locations.map(l => <option key={l.code} value={l.code}>{l.code} {l.description}</option>)}
              </select>
            </label>
            <button type="button" className={primary} disabled={busy || !t.canMoveCar} onClick={() => act("move-car")}>Move Car</button>
            <button type="button" className={primary} disabled={busy || !t.canMoveYard} onClick={() => act("move-yard")}>Move Yrd</button>
            <button type="button" className={secondary}
              onClick={() => app.open({ kind: "screen", key: `egl:VHYT02I?${new URLSearchParams({ yrdTrkgYrd: t.yard, yrdSubLoc: t.subLocation })}`, label: "VHYT02I" })}>Print</button>
          </div>
          <Note text={note} />
        </section>
      )}
    </>
  );
}

/** old VHYT02I: the yard tracking report on a MARIS printer */
export function TrackReportPage() {
  const p = useLinkParams();
  const [o, setO] = useState<ReportOptions | null>(null);
  const [yard, setYard] = useState(p.get("yrdTrkgYrd") ?? "");
  const [track, setTrack] = useState(p.get("yrdSubLoc") ?? "");
  const [printer, setPrinter] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  useEffect(() => { fetchReportOptions(yard).then(setO).catch(e => setError(message(e))); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const hardCopy = async () => {
    setError(""); setNote("");
    try { const r = await printTrackReport(yard, track, printer); setNote(r.message || "Sent to the printer."); } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Yard Tracking Report" code="VHYT02I" />
      <section aria-label="Report" className={panel}>
        {o && <div className="grid gap-3 md:grid-cols-3">
          <label className={label}>Yard Tracking Yard
            <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
              <option value="" />{o.yards.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>
          <label className={label}>Yard Sub Location
            <select className={field} value={track} onChange={e => setTrack(e.target.value)}>
              <option value="" />{o.subLocations.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="" />{o.printers.map(x => <option key={x.vtam} value={x.vtam}>{x.description} ({x.vtam})</option>)}
            </select>
          </label>
        </div>}
        <button type="button" className={`${primary} mt-4`} disabled={!printer} onClick={hardCopy}>Hard Copy</button>
        <Note text={error} kind="error" />
        <Note text={note} />
      </section>
    </>
  );
}

/** old VHYT06I: a car's move reason, and Update to a new one */
export function MoveReasonPage() {
  const p = useLinkParams();
  const yard = p.get("yrdTrkgYrd") ?? "", track = p.get("yrdSubLoc") ?? "", car = p.get("carNo") ?? "", position = Number(p.get("posnNo") ?? 0);
  const [v, setV] = useState<ReasonView | null>(null);
  const [code, setCode] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    fetchReason(yard, track, car, position).then(r => { setV(r); setNote(r.message); }).catch(e => setError(message(e)));
  }, [yard, track, car, position]);
  const update = async () => {
    setError(""); setNote("");
    try {
      const r = await changeReason(yard, track, car, position, code);
      setNote(r.message);
      setV(x => (x ? { ...x, car: r.car } : x));
    } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Change Move Reason" code="VHYT06I" />
      <section aria-label="Car" className={panel}>
        {v?.car && <dl className="grid grid-cols-2 gap-2 md:grid-cols-4">
          <div><dt className="text-slate-500">Car No.</dt><dd>{v.car.car}</dd></div>
          <div><dt className="text-slate-500">Yard</dt><dd>{v.car.yard}</dd></div>
          <div><dt className="text-slate-500">Yard Location</dt><dd>{v.car.subLocation}</dd></div>
          <div><dt className="text-slate-500">Position Number</dt><dd>{v.car.position}</dd></div>
          <div><dt className="text-slate-500">Orientation</dt><dd>{v.car.orientation}</dd></div>
          <div><dt className="text-slate-500">Status</dt><dd>{v.car.status}</dd></div>
          <div className="col-span-2"><dt className="text-slate-500">Reason Code</dt><dd>{v.car.reasonCode} {v.car.reasonDescription}</dd></div>
        </dl>}
        {v && <label className={`${label} mt-4`}>New Reason Code
          <select className={field} value={code} onChange={e => setCode(e.target.value)}>
            <option value="" />{v.reasons.map(r => <option key={r.code} value={r.code}>{r.code} {r.description}</option>)}
          </select>
        </label>}
        <button type="button" className={`${primary} mt-4`} disabled={!v?.car} onClick={update}>Update</button>
        <Note text={error} kind="error" />
        <Note text={note} />
      </section>
    </>
  );
}

/** old VHYT14I: insert a car before or after another car of the track */
export function FixOrderPage() {
  const p = useLinkParams();
  const yard = p.get("yrdTrkgYrd") ?? "", track = p.get("yrdSubLoc") ?? "", position = Number(p.get("posnNo") ?? 0);
  const [v, setV] = useState<FixView | null>(null);
  const [car, setCar] = useState(0);
  const [relative, setRelative] = useState(0);
  const [after, setAfter] = useState(false);
  const [question, setQuestion] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    fetchFixOrder(yard, track, position).then(r => { setV(r); setCar(r.selected); setRelative(r.selected); setNote(r.message); }).catch(e => setError(message(e)));
  }, [yard, track, position]);
  const fix = async (confirm: boolean) => {
    setError(""); setNote(""); setQuestion("");
    try {
      const r = await fixOrder({ yard, subLocation: track, position, carIndex: car, relativeIndex: relative, after, confirm });
      if (r.question) { setQuestion(r.question); return; }
      setNote(r.message); setV(r.track); setCar(r.track.selected || 1); setRelative(r.track.selected || 1);
    } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Fix Car Order" code="VHYT14I" />
      <section aria-label="Fix order" className={panel}>
        {v && <div className="flex flex-wrap items-end gap-3">
          <label className={label}>Insert Car
            <select className={field} value={car} onChange={e => setCar(Number(e.target.value))}>
              {v.cars.map(c => <option key={c.index} value={c.index}>{c.car}</option>)}
            </select>
          </label>
          <fieldset className="flex gap-3" aria-label="Where">
            <label><input type="radio" name="where" checked={!after} onChange={() => setAfter(false)} /> before</label>
            <label><input type="radio" name="where" checked={after} onChange={() => setAfter(true)} /> after</label>
          </fieldset>
          <label className={label}>Car
            <select className={field} value={relative} onChange={e => setRelative(Number(e.target.value))}>
              {v.cars.map(c => <option key={c.index} value={c.index}>{c.car}</option>)}
            </select>
          </label>
          <button type="button" className={primary} onClick={() => fix(false)}>Fix Order</button>
        </div>}
        {question && <div role="alertdialog" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">
          <p>{question}</p>
          <div className="mt-2 flex gap-2">
            <button type="button" className={primary} onClick={() => fix(true)}>Yes</button>
            <button type="button" className={secondary} onClick={() => setQuestion("")}>No</button>
          </div>
        </div>}
        <Note text={error} kind="error" />
        <Note text={note} />
      </section>
    </>
  );
}
