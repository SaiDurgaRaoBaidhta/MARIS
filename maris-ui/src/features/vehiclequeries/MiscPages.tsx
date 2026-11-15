/* Vehicle queries: consist orientation report (old VHCO01I), D and C sheet reprint (VHDC01I), SCRAM report (VHSCM1I)
 * and car status change (VHCHG1I). Texts, choices and messages come from the middleware (the old screens). */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  carStatus, changeCarStatus, dncOptions, orientationOptions, printDnc, printOrientation, scramOptions, scramReport, statusChangeOptions,
  type CarStatus, type DncOptions, type OrientationOptions, type ScramOptions, type ScramReport, type StatusChangeOptions,
} from "./miscApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 dark:border-line-dark`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

function useRun() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(text(e)); } finally { setBusy(false); }
  };
  return { busy, error, setError, run };
}

const Alert = ({ children }: { children: ReactNode }) =>
  <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{children}</p>;

/** VHCO01I: consist range and revenue dates, printed by VHCO011 */
export function ConsistOrientationPage() {
  const app = useApp();
  const { busy, error, run } = useRun();
  const [o, setO] = useState<OrientationOptions | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [printer, setPrinter] = useState("");
  const load = () => orientationOptions().then(x => { setO(x); setFromDate(x.fromDate); setToDate(x.toDate); }).catch(() => setO(null));
  useEffect(() => { load(); }, []);
  const print = () => run(async () => app.toast((await printOrientation({ from: from.trim(), to: to.trim(), fromDate, toDate, printer })).message, "ok"));
  const clear = () => { setFrom(""); setTo(""); setPrinter(""); load(); };
  return (
    <>
      <PageHead title="Consist Orientation Report" code="VHCO01I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Consist No. From<input className={`${field} w-24`} value={from} maxLength={3} placeholder={o?.all} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>Consist No. To<input className={`${field} w-24`} value={to} maxLength={3} onChange={e => setTo(e.target.value)} /></label>
          <label className={label}>From Date<input className={field} type="date" value={fromDate} onChange={e => setFromDate(e.target.value)} /></label>
          <label className={label}>To Date<input className={field} type="date" value={toDate} onChange={e => setToDate(e.target.value)} /></label>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer</option>
              {o?.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={primary} disabled={busy || !printer} onClick={print}>Print</button>
          <button type="button" className={secondary} onClick={clear}>Clear</button>
        </div>
        {error && <Alert>{error}</Alert>}
      </section>
    </>
  );
}

/** VHDC01I: a blank D and C sheet, or a car's / incident's sheet */
export function DncSheetPage() {
  const app = useApp();
  const { busy, error, run } = useRun();
  const [o, setO] = useState<DncOptions | null>(null);
  const [blank, setBlank] = useState("");
  const [car, setCar] = useState("");
  const [incident, setIncident] = useState("");
  const [printer, setPrinter] = useState("");
  useEffect(() => { dncOptions().then(x => { setO(x); setBlank(x.no); }).catch(() => setO(null)); }, []);
  const clear = () => { setBlank(o?.no ?? ""); setCar(""); setIncident(""); };
  const ok = () => run(async () => { app.toast((await printDnc({ blankForm: blank, car: car.trim(), incident: incident.trim(), printer })).message, "ok"); clear(); });
  return (
    <>
      <PageHead title="Reprint D & C Sheet" code="VHDC01I" />
      <section aria-label="Sheet" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Print Blank Form ({o?.yes}/{o?.no})<input className={`${field} w-16`} value={blank} maxLength={1} onChange={e => setBlank(e.target.value)} /></label>
          <label className={label}>Print Form for Car<input className={`${field} w-24`} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          <label className={label}>Incident<input className={`${field} w-32`} value={incident} maxLength={8} onChange={e => setIncident(e.target.value)} /></label>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Select Printer</option>
              {o?.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.location} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={primary} disabled={busy || !printer} onClick={ok}>OK</button>
          <button type="button" className={secondary} onClick={clear}>Clear</button>
        </div>
        {error && <Alert>{error}</Alert>}
      </section>
    </>
  );
}

/** VHSCM1I: cars each home yard provides for the four yards, one home yard at a time (Prv / Nxt) */
export function ScramPage() {
  const { busy, error, run } = useRun();
  const [o, setO] = useState<ScramOptions | null>(null);
  const [day, setDay] = useState("");
  const [time, setTime] = useState("");
  const [report, setReport] = useState<ScramReport | null>(null);
  const [at, setAt] = useState(0);
  const load = () => scramOptions().then(x => { setO(x); setDay(x.day); setTime(x.time); }).catch(() => setO(null));
  useEffect(() => { load(); }, []);
  const list = () => run(async () => { setReport(null); setAt(0); setReport(await scramReport(day.trim(), time.trim())); });
  const clear = () => { setReport(null); setAt(0); load(); };
  const yard = report?.yards[at];
  return (
    <>
      <PageHead title="SCRAM Report" code="VHSCM1I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Schedule DAY ({o?.days.join(" / ")})<input className={`${field} w-20`} value={day} maxLength={2} onChange={e => setDay(e.target.value)} /></label>
          <label className={label}>Schedule TIME ({o?.times.join(" / ")})<input className={`${field} w-20`} value={time} maxLength={2} onChange={e => setTime(e.target.value)} /></label>
          <button type="button" className={primary} disabled={busy} onClick={list}>List Details</button>
          <button type="button" className={secondary} onClick={clear}>Clear</button>
        </div>
        {error && <Alert>{error}</Alert>}
      </section>
      {yard && o && (
        <section aria-label="SCRAM" className={`${panel} overflow-x-auto`}>
          <div className="mb-2 flex items-center gap-3">
            <button type="button" className={secondary} disabled={at === 0} onClick={() => setAt(at - 1)}>Prv</button>
            <span className="font-semibold">{o.homeYardHeading}: {yard.homeYard}</span>
            <button type="button" className={secondary} disabled={at >= report!.yards.length - 1} onClick={() => setAt(at + 1)}>Nxt</button>
          </div>
          {yard.message && <p className="mb-2 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{yard.message}</p>}
          <table className="text-sm">
            <thead>
              <tr><th />{o.groups.map(g => <th key={g} colSpan={3} className="px-2 text-center">{g}</th>)}</tr>
              <tr><th className="px-2 text-left">{o.homeYardHeading}</th>{o.groups.flatMap(g => o.columns.map(c => <th key={g + c} className="px-2 text-right">{c}</th>))}</tr>
            </thead>
            <tbody>
              {yard.rows.filter(r => r.homeYard || r.cells.some(c => c !== 0)).map((r, i) =>
                <tr key={i}><td className="px-2">{r.homeYard}</td>{r.cells.map((c, k) => <td key={k} className="px-2 text-right">{c}</td>)}</tr>)}
              <tr className="font-semibold"><td className="px-2">Total</td>{yard.totals.flatMap((t, g) => [t.scheduled, t.actual, t.difference].map((v, k) =>
                <td key={g * 3 + k} className="px-2 text-right">{v}</td>))}</tr>
            </tbody>
          </table>
        </section>)}
    </>
  );
}

/** VHCHG1I: read a car's status, then change it after the old question */
export function CarStatusChangePage() {
  const app = useApp();
  const { busy, error, run } = useRun();
  const [o, setO] = useState<StatusChangeOptions | null>(null);
  const [car, setCar] = useState("");
  const [current, setCurrent] = useState<CarStatus | null>(null);
  const [status, setStatus] = useState("");
  const [msg, setMsg] = useState("");
  useEffect(() => { statusChangeOptions().then(x => { setO(x); setMsg(x.message); }).catch(() => setO(null)); }, []);
  const read = () => run(async () => {
    setCurrent(null);
    try { const c = await carStatus(car.trim()); setCurrent(c); setMsg(c.message); } catch (e) { setMsg(text(e)); }
  });
  const update = () => run(async () => {
    if (!o || !(await app.confirm(o.question))) return;
    setMsg((await changeCarStatus(car.trim(), status)).message);
  });
  const clear = () => { setCar(""); setCurrent(null); setStatus(""); setMsg(o?.message ?? ""); };
  return (
    <>
      <PageHead title="Change Car Status" code="VHCHG1I" />
      <section aria-label="Car status" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car Number<input className={`${field} w-24`} value={car} maxLength={4} onChange={e => setCar(e.target.value)} onBlur={() => car.trim() && read()} /></label>
          <div className={label}>Current Vehicle Status<span className="h-9 py-2 font-normal">{current?.status}</span></div>
          <div className={label}>Veh Stat Prev<span className="h-9 py-2 font-normal">{current?.previousStatus}</span></div>
          <label className={label}>Change To Status
            <select className={field} value={status} disabled={!current} onChange={e => setStatus(e.target.value)}>
              <option value="" />
              {o?.statuses.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <button type="button" className={primary} disabled={busy || !current || !status} onClick={update}>Update</button>
          <button type="button" className={secondary} onClick={clear}>Clear</button>
        </div>
        <p aria-label="Messages" className="mt-3 font-semibold text-red-700 dark:text-red-300">{msg}</p>
        {error && <Alert>{error}</Alert>}
      </section>
    </>
  );
}
