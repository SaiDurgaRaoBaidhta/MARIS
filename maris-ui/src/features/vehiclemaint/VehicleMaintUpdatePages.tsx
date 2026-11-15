/* Vehicle maintenance update pages: car hours after a test (old VHVM08I), consist status (VHVM10I), consist history (VHVM19I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  addTestHours, carHours, consistHistory, consistHistoryOptions, consistStatus, consistStatusOptions, updateConsistHistory, updateConsistStatus,
  type CarHours, type Column, type ConsistHistoryOptions, type ConsistStatusOptions, type Row, type Rows,
} from "./updatesApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 dark:border-line-dark`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const str = (v: string | number | null | undefined) => (v === null || v === undefined ? "" : String(v));

function Alert({ text }: { text: string }) {
  return text ? <p role="alert" className="mt-3 whitespace-pre-line rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
}

function Text({ name, value, set, max, readOnly }: { name: string; value: string; set?: (v: string) => void; max?: number; readOnly?: boolean }) {
  return <label className={label}>{name}<input className={field} value={value} maxLength={max} readOnly={readOnly} onChange={e => set?.(e.target.value)} /></label>;
}

function Choice({ name, value, set, items }: { name: string; value: string; set: (v: string) => void; items: string[] }) {
  return (
    <label className={label}>{name}
      <select className={field} value={value} onChange={e => set(e.target.value)}>
        <option value="" />
        {items.map(i => <option key={i} value={i}>{i}</option>)}
      </select>
    </label>
  );
}

/** old VHVM08I: add test hours to a car's PM hours */
export function CarHoursTestPage() {
  const app = useApp();
  const [car, setCar] = useState("");
  const [test, setTest] = useState("");
  const [found, setFound] = useState<CarHours | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const n = /^\d{0,3}$/.test(test.trim()) ? Number(test.trim() || 0) : 0;
  const search = async () => {
    setBusy(true); setError(""); setFound(null);
    try { setFound(await carHours(car.trim())); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const clear = () => { setCar(""); setTest(""); setFound(null); setError(""); };
  const update = async () => {
    setBusy(true); setError("");
    try { app.toast((await addTestHours(car.trim(), test.trim())).message, "ok"); clear(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Car Hours Test Update" code="VHVM08I" />
      <section aria-label="Car" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Text name="Enter Car Number" value={car} set={setCar} max={4} />
          <button type="button" className={primary} disabled={busy || !car.trim()} onClick={search}>Search</button>
        </div>
        <Alert text={error} />
      </section>
      {found && (
        <section aria-label="Hours" className={panel}>
          <div className="flex flex-wrap items-end gap-4">
            <Text name="Current Location" value={found.location} readOnly />
            <Text name="Where is Car" value={found.whereIsCar} readOnly />
            <Text name="Current Car Hours" value={str(found.cumulativeHours)} readOnly />
            <Text name="Hours to Next PM" value={str(found.hoursToNextPm)} readOnly />
            <Text name="Hours From Last PM" value={str(found.hoursFromLastPm)} readOnly />
          </div>
          <div className="mt-4 flex flex-wrap items-end gap-4">
            <Text name="Enter the Test Hours" value={test} set={setTest} max={3} />
            <Text name="New Current Hours" value={str(found.cumulativeHours)} readOnly />
            <Text name="New Hours to Next PM" value={str(found.hoursToNextPm - n)} readOnly />
            <Text name="New Hours From Last PM" value={str(found.hoursFromLastPm + n)} readOnly />
          </div>
          <div className="mt-4 flex gap-3">
            <button type="button" className={primary} disabled={busy} onClick={update}>Update</button>
            <button type="button" className={secondary} onClick={clear}>Clear</button>
          </div>
        </section>)}
    </>
  );
}

function Table({ columns, rows, selected, select }: { columns: Column[]; rows: Row[]; selected: number; select: (i: number) => void }) {
  return (
    <div className="max-h-[50vh] overflow-auto">
      <table className="w-full text-[13px]">
        <thead><tr>{columns.map((c, i) => <th key={i} className="sticky top-0 bg-slate-100 px-2 py-1 text-left dark:bg-[#0a1624]">{c.heading}</th>)}</tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} aria-selected={i === selected} onClick={() => select(i)}
              className={`cursor-pointer ${i === selected ? "bg-bart-100 dark:bg-bart-900/40" : "hover:bg-slate-50 dark:hover:bg-white/5"}`}>
              {columns.map((c, j) => <td key={j} className="whitespace-nowrap px-2 py-1">{str(r[c.field])}</td>)}
            </tr>))}
        </tbody>
      </table>
    </div>
  );
}

interface Edit { status: string; dateInsrvIcs: string; timeInsrvIcs: string; noCarsInd: string; dateOutsrv: string }
const editOf = (r: Row): Edit => ({ status: str(r.conStat), dateInsrvIcs: str(r.dateInsrvIcs), timeInsrvIcs: str(r.timeInsrvIcs), noCarsInd: str(r.noCarsInd), dateOutsrv: str(r.dateOutsrv) });

/** the shared layout of VHVM10I and VHVM19I: a consist range, the table, and the selected consist's editable fields */
function ConsistPage({ title, code, options, load, save, revDate, setRevDate, outDate }: {
  title: string; code: string; options: ConsistStatusOptions | null; load: (from: string, to: string) => Promise<Rows>;
  save: (row: Row, e: Edit) => Promise<{ message: string }>; revDate?: string; setRevDate?: (v: string) => void; outDate: boolean;
}) {
  const app = useApp();
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [rows, setRows] = useState<Rows | null>(null);
  const [selected, setSelected] = useState(-1);
  const [edit, setEdit] = useState<Edit | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const retrieve = async () => {
    setBusy(true); setError(""); setSelected(-1); setEdit(null);
    try { setRows(await load(from.trim(), to.trim())); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const select = (i: number) => { setSelected(i); setEdit(rows ? editOf(rows.rows[i]) : null); };
  const clear = () => { setSelected(-1); setEdit(null); setError(""); };
  const update = async () => {
    if (!rows || !edit || selected < 0) return;
    setBusy(true); setError("");
    try {
      app.toast((await save(rows.rows[selected], edit)).message, "ok");
      setSelected(-1); setEdit(null);
      setRows(await load(from.trim(), to.trim()));
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const set = (k: keyof Edit) => (v: string) => setEdit(x => (x ? { ...x, [k]: v } : x));
  return (
    <>
      <PageHead title={title} code={code} />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Text name="Begin Consist" value={from} set={setFrom} max={3} />
          <Text name="End Consist" value={to} set={setTo} max={3} />
          {setRevDate && <Text name="Rev Date" value={revDate ?? ""} set={setRevDate} max={10} />}
          <button type="button" className={primary} disabled={busy || !from.trim() || !options} onClick={retrieve}>Retrieve</button>
        </div>
        <Alert text={error} />
      </section>
      {rows && options && (
        <section aria-label="Consists" className={panel}>
          {rows.message && <p role="status" className="mb-2 font-semibold text-amber-700 dark:text-amber-300">{rows.message}</p>}
          <Table columns={options.columns} rows={rows.rows} selected={selected} select={select} />
        </section>)}
      {edit && options && rows && (
        <section aria-label="Selected consist" className={panel}>
          <div className="flex flex-wrap items-end gap-4">
            <Text name="Consist" value={str(rows.rows[selected].conNo)} readOnly />
            <Choice name="Con Stat" value={edit.status} set={set("status")} items={options.statuses} />
            <Text name="Date ICS" value={edit.dateInsrvIcs} set={set("dateInsrvIcs")} max={10} />
            <Text name="Time ICS" value={edit.timeInsrvIcs} set={set("timeInsrvIcs")} max={8} />
            <Text name="No Cars" value={edit.noCarsInd} set={set("noCarsInd")} max={1} />
            {outDate && <Text name="Date Outsrv" value={edit.dateOutsrv} set={set("dateOutsrv")} max={10} />}
          </div>
          <div className="mt-4 flex gap-3">
            <button type="button" className={primary} disabled={busy} onClick={update}>Update</button>
            <button type="button" className={secondary} onClick={clear}>Clear</button>
          </div>
        </section>)}
    </>
  );
}

function useLoaded<T>(f: () => Promise<T>) {
  const [v, setV] = useState<T | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { f().then(setV).catch(e => setError(message(e))); }, [f]);
  return { v, error };
}

/** old VHVM10I: consist status maintenance */
export function ConsistStatusPage() {
  const { v: options, error } = useLoaded(consistStatusOptions);
  return (
    <>
      {error && <Alert text={error} />}
      <ConsistPage title="Consist Status Maintenance" code="VHVM10I" options={options} outDate load={consistStatus}
        save={(r, e) => updateConsistStatus({ conNo: str(r.conNo), dateInsrvRev: str(r.dateInsrvRev), ...e })} />
    </>
  );
}

/** old VHVM19I: consist history maintenance */
export function ConsistHistoryPage() {
  const { v: options, error } = useLoaded<ConsistHistoryOptions>(consistHistoryOptions);
  const [revDate, setRevDate] = useState<string | null>(null);
  const rev = revDate ?? options?.revDate ?? "";
  return (
    <>
      {error && <Alert text={error} />}
      <ConsistPage title="Consist History Maintenance" code="VHVM19I" options={options} outDate={false} revDate={rev} setRevDate={setRevDate}
        load={(from, to) => consistHistory(from, to, rev)}
        save={(r, e) => updateConsistHistory({
          conNo: str(r.conNo), dashNo: Number(r.dashNo ?? 0), dateInsrv: str(r.dateInsrv), timeInsrv: str(r.timeInsrv), dateInsrvRev: str(r.dateInsrvRev),
          status: e.status, dateInsrvIcs: e.dateInsrvIcs, timeInsrvIcs: e.timeInsrvIcs, noCarsInd: e.noCarsInd,
        })} />
    </>
  );
}
