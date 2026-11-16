/* Consist tracking: status trains (old VHST05I with VHST02I / VHST04I) and the symptom view (old VHST01I with VHST03I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  carDetails, consistOptions, consistSymptoms, printConsists, printConsistSymptoms, statusTrains,
  type ConsistOptions, type Details, type Symptoms, type Trains,
} from "./consistApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const card = "rounded-xl border border-slate-200 p-3 dark:border-line-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
/** the old screens' car colours: red for an incident or a car not fit for service, green for usable */
const carColour = (c: { alert: boolean; usable: boolean }) =>
  c.alert ? "text-red-700 dark:text-red-300" : c.usable ? "text-green-700 dark:text-green-300" : "";

function useRun() {
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); setNote(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  return { error, note, setNote, busy, run };
}

function Feedback({ error, note }: { error: string; note: string }) {
  return <>
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
    {note && <p role="status" className="mt-3 rounded-lg bg-green-50 px-3 py-2 text-green-800 dark:bg-green-950/40 dark:text-green-200">{note}</p>}
  </>;
}

function Printers({ options, printer, setPrinter }: { options: ConsistOptions | null; printer: string; setPrinter: (p: string) => void }) {
  return (
    <label className={label}>Printer
      <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
        <option value="">(choose)</option>
        {options?.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
      </select>
    </label>
  );
}

/** VHST05I: a consist range (or ALL), report 1 summary list or 2 car details */
export function StatusTrainPage() {
  const [options, setOptions] = useState<ConsistOptions | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [report, setReport] = useState(1);
  const [printer, setPrinter] = useState("");
  const [trains, setTrains] = useState<Trains | null>(null);
  const [details, setDetails] = useState<Details | null>(null);
  const { error, note, setNote, busy, run } = useRun();
  useEffect(() => { consistOptions().then(setOptions).catch(() => setOptions({ reports: [], printers: [] })); }, []);
  const display = () => run(async () => {
    setTrains(null); setDetails(null);
    if (report === 1) setTrains(await statusTrains(from, to)); else setDetails(await carDetails(from, to));
  });
  return (
    <>
      <PageHead title="Consist Tracking - Status Train" code="VHST05I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Consist No. From<input className={`${field} w-24`} value={from} maxLength={3} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>Consist No. To<input className={`${field} w-24`} value={to} maxLength={3} onChange={e => setTo(e.target.value)} /></label>
          <fieldset className="flex gap-3 text-[13px]"><legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Report Type</legend>
            {(options?.reports ?? []).map((r, i) => (
              <label key={r} className="flex items-center gap-1"><input type="radio" name="report" checked={report === i + 1} onChange={() => setReport(i + 1)} />{r}</label>
            ))}
          </fieldset>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <Printers options={options} printer={printer} setPrinter={setPrinter} />
          <button type="button" className={`${button} border border-slate-300`} disabled={busy || !printer}
            onClick={() => run(async () => setNote((await printConsists(report, from, to, printer)).message))}>Print</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { setFrom(""); setTo(""); setTrains(null); setDetails(null); }}>Clear</button>
        </div>
        <Feedback error={error} note={note} />
      </section>
      {trains && (
        <section aria-label="Summary list" className={panel}>
          <h2 className="mb-3 font-semibold">Consist Range {trains.from} To {trains.to} · {trains.trains.length} consists</h2>
          <div className="grid gap-3 md:grid-cols-2">
            {trains.trains.map((t, i) => (
              <article key={`${t.consist}-${i}`} aria-label={`Consist ${t.consist}`} className={card}>
                <p className="text-sm">
                  <b className={t.warning ? "bg-yellow-200 px-1 dark:bg-yellow-700" : ""}>{t.consist}</b> {t.mark && <b className="text-red-700">{t.mark}</b>} · {t.origin}
                  · {t.dateInService} {t.timeInService} · Order Loc {t.orderLocation} {t.orientation}
                </p>
                <ul className="mt-2 flex flex-wrap gap-2 font-mono text-[13px]">
                  {t.cars.map(c => (
                    <li key={c.car} className={`rounded border border-slate-200 px-1 dark:border-line-dark ${carColour(c)}`}>
                      {c.leftMark}{c.car}{c.rightMark} <span className={c.warning ? "bg-yellow-200 dark:bg-yellow-700" : ""}>{c.homeYard}</span>
                    </li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </section>
      )}
      {details && (
        <section aria-label="Car details" className={panel}>
          <h2 className="mb-3 font-semibold">Consist Range {details.from} To {details.to}</h2>
          {details.trains.map((t, i) => (
            <article key={`${t.consist}-${i}`} aria-label={`Consist ${t.consist}`} className={`${card} mb-3`}>
              <p className="mb-2 text-sm"><b>{t.consist}</b> · {t.origin} · {t.dateInService} {t.timeInService}</p>
              <table aria-label={`Cars of ${t.consist}`} className="w-full text-[13px]">
                <thead><tr>{["Car", "Home Yrd", "PM Hr/Sch", "ODue Wash", "ODue Cln"].map(h => <th key={h} className="text-left">{h}</th>)}</tr></thead>
                <tbody>{t.cars.map(c => <tr key={c.car}><td>{c.car}</td><td>{c.homeYard}</td><td>{c.pm}</td><td>{c.washDays}</td><td>{c.clean}</td></tr>)}</tbody>
              </table>
            </article>
          ))}
        </section>
      )}
    </>
  );
}

/** VHST01I: one consist and a symptom (opened from incident events) */
export function ConsistSymptomPage() {
  const [options, setOptions] = useState<ConsistOptions | null>(null);
  const [consist, setConsist] = useState("");
  const [symptom, setSymptom] = useState("");
  const [printer, setPrinter] = useState("");
  const [result, setResult] = useState<Symptoms | null>(null);
  const { error, note, setNote, busy, run } = useRun();
  useEffect(() => { consistOptions().then(setOptions).catch(() => setOptions({ reports: [], printers: [] })); }, []);
  return (
    <>
      <PageHead title="Consist Tracking - Status Train Symptom" code="VHST01I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Consist No.<input className={`${field} w-24`} value={consist} maxLength={3} onChange={e => setConsist(e.target.value)} /></label>
          <label className={label}>Symptom Code<input className={`${field} w-24`} value={symptom} maxLength={4} onChange={e => setSymptom(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy}
            onClick={() => run(async () => { setResult(null); setResult(await consistSymptoms(consist, symptom)); })}>Display</button>
          <Printers options={options} printer={printer} setPrinter={setPrinter} />
          <button type="button" className={`${button} border border-slate-300`} disabled={busy || !printer}
            onClick={() => run(async () => setNote((await printConsistSymptoms(consist, symptom, printer)).message))}>Print</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { setConsist(""); setSymptom(""); setResult(null); }}>Clear</button>
        </div>
        <Feedback error={error} note={note} />
      </section>
      {result && (
        <section aria-label="Symptom view" className={panel}>
          <h2 className="mb-3 font-semibold">Consist {result.consist} · Symptom {result.symptom} · {result.dateFrom} to {result.dateTo}</h2>
          {result.trains.length === 0 && <p>No consists found.</p>}
          {result.trains.map((t, i) => (
            <article key={`${t.consist}-${i}`} aria-label={`Consist ${t.consist}`} className={`${card} mb-3`}>
              <p className="mb-2 text-sm"><b>{t.consist}</b> {t.mark && <b className="text-red-700">{t.mark}</b>} · {t.origin} · {t.dateInService} {t.timeInService}</p>
              <table aria-label={`Cars of ${t.consist}`} className="w-full text-[13px]">
                <thead><tr>{["Car", "Symptom count", "Home Yard"].map(h => <th key={h} className="text-left">{h}</th>)}</tr></thead>
                <tbody>{t.cars.map(c => (
                  <tr key={c.car}><td className={`font-mono ${carColour(c)}`}>{c.software ? "$" : ""}{c.leftMark}{c.car}{c.rightMark}</td><td>{c.symptomCount}</td><td>{c.homeYard}</td></tr>
                ))}</tbody>
              </table>
            </article>
          ))}
        </section>
      )}
    </>
  );
}
