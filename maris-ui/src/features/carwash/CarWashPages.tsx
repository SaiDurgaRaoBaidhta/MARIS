/* Car wash reports (old screens VHCWR1I washes in a date range, VHCWR2I wash over due). */
import { useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { listOverdue, listWashes, type Overdue, type Washes } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);

function Table({ name, head, rows }: { name: string; head: string[]; rows: ReactNode[][] }) {
  return (
    <div className="mx-2 mb-6 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[13px]">
        <thead><tr>{head.map(h => <th key={h} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">{r.map((c, j) => <td key={j} className="px-2 py-1">{c}</td>)}</tr>)}</tbody>
      </table>
    </div>);
}

function Notes({ error, message, note }: { error: string; message?: string | null; note?: string | null }) {
  return <>
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
    {message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{message}</p>}
    {note && <p className="mt-3 text-sm text-slate-600 dark:text-[#c9d6e4]">{note}</p>}
  </>;
}

export function CarWashesPage() {
  const [yard, setYard] = useState(""), [from, setFrom] = useState(""), [to, setTo] = useState("");
  const [result, setResult] = useState<Washes | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const list = async () => {
    setBusy(true); setError("");
    try { setResult(await listWashes(yard, from, to)); } catch (e) { setResult(null); setError(errText(e)); } finally { setBusy(false); }
  };
  const clear = () => { setYard(""); setFrom(""); setTo(""); setResult(null); setError(""); };
  return (
    <>
      <PageHead title="Car Wash Report" code="VHCWR1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); list(); }}>
          <label className={label}>Yard<input className={`${field} w-20 uppercase`} maxLength={3} value={yard} onChange={e => setYard(e.target.value.toUpperCase())} /></label>
          <label className={label}>From<input type="date" className={field} value={from} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>To<input type="date" className={field} value={to} onChange={e => setTo(e.target.value)} /></label>
          <button type="submit" className={`${button} bg-bart-600 text-white`} disabled={busy}>List</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </form>
        <Notes error={error} message={result?.message} note={result?.maxRowsReached ? result.note : null} />
      </section>
      {result && result.count > 0 && <Table name="Washes" head={["Car No", "Washed Date", "Washed Time"]} rows={result.washes.map(w => [w.car, w.date, w.time])} />}
    </>
  );
}

export function CarWashOverduePage() {
  const [yard, setYard] = useState("");
  const [result, setResult] = useState<Overdue | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const list = async () => {
    setBusy(true); setError("");
    try { setResult(await listOverdue(yard)); } catch (e) { setResult(null); setError(errText(e)); } finally { setBusy(false); }
  };
  const clear = () => { setYard(""); setResult(null); setError(""); };
  return (
    <>
      <PageHead title="Car Wash Over Due" code="VHCWR2I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); list(); }}>
          <label className={label}>Home yard<input className={`${field} w-20 uppercase`} maxLength={3} value={yard} onChange={e => setYard(e.target.value.toUpperCase())} /></label>
          <button type="submit" className={`${button} bg-bart-600 text-white`} disabled={busy}>List</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </form>
        <Notes error={error} message={result?.message} note={result?.maxRowsReached ? result.note : null} />
      </section>
      {result && result.count > 0 && <Table name="Cars over due" head={["Car No", "Date Last Wash", "Time Last Wash", "Curr Loc", "Days Overdue", "Total Washes"]}
        rows={result.cars.map(c => [c.car, c.lastWashDate, c.lastWashTime, c.location, c.daysOverdue, c.totalWashes])} />}
    </>
  );
}
