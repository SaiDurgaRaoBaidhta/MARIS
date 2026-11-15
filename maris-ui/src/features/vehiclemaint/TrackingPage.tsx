/* Vehicle parts status / mod tracking (old VHVM11I): list a car's tracking events, add, update, close, remove, work assign. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  addEvent, closeEvent, linkedEvents, removeEvent, searchEvents, selectEvent, trackingOptions, updateEvent, workAssign,
  type Change, type Criteria, type Result, type TrackingEvent, type TrackingOptions,
} from "./trackingApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the parent's params: from the screen key of the page in front ("egl:VHVM11I?carNo=...") or the address bar */
function useParams(): URLSearchParams {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : window.location.search.replace(/^\?/, ""));
}

type FormState = { car: string; entry: string; status: string; dateInitialized: string; trackingRefNo: string; dateClosed: string; incident: string; comment: string; location: string };
const EMPTY: FormState = { car: "", entry: "", status: "", dateInitialized: "", trackingRefNo: "", dateClosed: "", incident: "", comment: "", location: "" };

export function TrackingPage() {
  const app = useApp();
  const params = useParams();
  const [o, setO] = useState<TrackingOptions | null>(null);
  const [c, setC] = useState<Criteria>({ dateFrom: "", dateTo: "", car: "", carType: "", homeYard: "", eventType: "", eventSubType: "", status: "", location: "", trackingRefNo: "", sortOrder: "" });
  const [events, setEvents] = useState<TrackingEvent[]>([]);
  const [sel, setSel] = useState<TrackingEvent | null>(null);
  const [canAssign, setCanAssign] = useState(false);
  const [battery, setBattery] = useState(false);
  const [f, setF] = useState<FormState>(EMPTY);
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const from = params.get("passFromGui") ?? "";

  useEffect(() => {
    trackingOptions(from || undefined).then(x => {
      setO(x);
      setC(v => ({ ...v, dateFrom: x.dateFrom, dateTo: x.dateTo, sortOrder: x.defaultSortOrder }));
      setF(v => ({ ...v, dateInitialized: x.runDate }));
      if (from) {
        linkedEvents({ from, car: params.get("carNo") ?? "", incident: params.get("incdNo") ?? "", status: params.get("eventStat") ?? "",
          eventType: params.get("eventType") ?? "", dateInitialized: params.get("dateInitialized") ?? "" }).then(l => {
          setEvents(l.events);
          setMsg(l.message);
          if (l.form) setF(v => ({ ...v, car: l.form!.car, status: l.form!.status, dateInitialized: l.form!.dateInitialized, incident: l.form!.incident,
            trackingRefNo: l.form!.trackingRefNo, comment: l.form!.comment, entry: x.entryEvents.find(e => e.type === l.form!.eventType)?.label ?? "" }));
        }).catch(e => setMsg(text(e)));
      }
    }).catch(e => setMsg(text(e)));
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  const set = (k: keyof FormState, v: string) => setF(x => ({ ...x, [k]: v }));
  const run = async (step: () => Promise<void>) => { setBusy(true); try { await step(); } catch (e) { setMsg(text(e)); } finally { setBusy(false); } };
  const list = () => run(async () => { const l = await searchEvents(c); setEvents(l.events); setMsg(l.message); setSel(null); });
  const choose = (e: TrackingEvent) => run(async () => {
    const s = await selectEvent(e);
    setSel(e);
    setCanAssign(s.canWorkAssign);
    setBattery(s.battery);
    const entry = o?.entryEvents.find(x => x.type === s.entryEventType && x.subType === s.entryEventSubType)?.label ?? "";
    setF({ car: e.car, entry, status: s.status, dateInitialized: e.dateInitialized, trackingRefNo: e.trackingRefNo, dateClosed: e.dateClosed,
      incident: e.incident, comment: e.comment, location: s.location });
    setMsg("");
  });
  const change = (answer?: boolean): Change => {
    const entry = o?.entryEvents.find(x => x.label === f.entry);
    return { event: sel!, status: f.status, dateInitialized: f.dateInitialized, trackingRefNo: f.trackingRefNo, dateClosed: f.dateClosed, car: f.car,
      incident: f.incident, comment: f.comment, location: f.location, entryEventType: entry?.type, entryEventSubType: entry?.subType, answer };
  };
  /** sends; when the backend asks a question, asks the user and sends again with the answer */
  const ask = (send: (c: Change) => Promise<Result>) => run(async () => {
    let r = await send(change());
    if (r.question) r = await send(change(await app.confirm(r.question)));
    if (r.message !== null) { setMsg(r.message); setEvents((await searchEvents(c)).events); setSel(null); }  // the screen lists again
  });
  const add = () => run(async () => {
    const entry = o?.entryEvents.find(x => x.label === f.entry);
    const r = await addEvent({ car: f.car, status: f.status, eventType: entry?.type ?? "", eventSubType: entry?.subType ?? "", trackingRefNo: f.trackingRefNo,
      dateInitialized: f.dateInitialized, dateClosed: f.dateClosed, incident: f.incident, comment: f.comment });
    setMsg(r.message ?? "");
  });
  const remove = () => run(async () => {
    if (!o || !(await app.confirm(o.questions.remove))) return;
    setMsg((await removeEvent(change(true))).message ?? "");
  });
  const clear = () => { setF({ ...EMPTY, dateInitialized: o?.runDate ?? "" }); setSel(null); setMsg(""); };
  const pick = (label: string) => { const e = o?.searchEvents.find(x => x.label === label); setC(v => ({ ...v, eventType: e?.type ?? "", eventSubType: e?.subType ?? "" })); };
  const write = !o?.queryOnly;

  return (
    <>
      <PageHead title="Vehicle Parts Status / Mod Tracking" code="VHVM11I" />
      <section aria-label="Criteria" className={panel}>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          <label className={label}>Event Type<select className={field} onChange={e => pick(e.target.value)}><option value="" />{o?.searchEvents.map(e => <option key={e.label}>{e.label}</option>)}</select></label>
          <label className={label}>Status<select className={field} value={c.status} onChange={e => setC({ ...c, status: e.target.value })}><option value="" />{o?.statuses.map(s => <option key={s}>{s}</option>)}</select></label>
          <label className={label}>Stk#Mod#<input className={field} value={c.trackingRefNo} maxLength={11} onChange={e => setC({ ...c, trackingRefNo: e.target.value })} /></label>
          <label className={label}>Car<input className={field} value={c.car} maxLength={4} onChange={e => setC({ ...c, car: e.target.value })} /></label>
          <label className={label}>Car Type<select className={field} value={c.carType} onChange={e => setC({ ...c, carType: e.target.value })}><option value="" />{o?.carTypes.map(s => <option key={s}>{s}</option>)}</select></label>
          <label className={label}>Home Yard<select className={field} value={c.homeYard} onChange={e => setC({ ...c, homeYard: e.target.value })}><option value="" />{o?.homeYards.map(s => <option key={s}>{s}</option>)}</select></label>
          <label className={label}>Cur Loc<select className={field} value={c.location} onChange={e => setC({ ...c, location: e.target.value })}><option value="" />{o?.locations.map(s => <option key={s}>{s}</option>)}</select></label>
          <label className={label}>From<input className={field} value={c.dateFrom} onChange={e => setC({ ...c, dateFrom: e.target.value })} /></label>
          <label className={label}>To<input className={field} value={c.dateTo} onChange={e => setC({ ...c, dateTo: e.target.value })} /></label>
          <label className={label}>Sort<select className={field} value={c.sortOrder} onChange={e => setC({ ...c, sortOrder: e.target.value })}>{o?.sortOrders.map((s, i) => <option key={s} value={String(i + 1)}>{s}</option>)}</select></label>
        </div>
        <button className={`${primary} mt-3`} disabled={busy} onClick={list}>List</button>
      </section>
      <section aria-label="Events" className={`${panel} overflow-x-auto`}>
        <table className="w-full text-left text-sm">
          <thead><tr>{["Event", "", "Status", "Date Initialized", "Stk#Mod#", "Date Closed", "Car #", "Loc", "Car Stat", "HY", "Incident", "Date Last Upd", "Comments"].map((h, i) => <th key={i} className="px-2 py-1">{h}</th>)}</tr></thead>
          <tbody>{events.map((e, i) => (
            <tr key={i} onClick={() => choose(e)} aria-selected={sel === e} className={`cursor-pointer ${sel === e ? "bg-bart-50" : ""}`}>
              {[e.eventType, e.eventSubType, e.status, e.dateInitialized, e.trackingRefNo, e.dateClosed, e.car, e.location, e.vehicleStatus, e.homeYard, e.incident, e.dateLastUpdated, e.comment].map((v, j) => <td key={j} className="px-2 py-1">{v}</td>)}
            </tr>))}</tbody>
        </table>
      </section>
      <section aria-label="Event" className={panel}>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
          <label className={label}>Event Type/Sub Type<select className={field} value={f.entry} onChange={e => set("entry", e.target.value)}><option value="" />{o?.entryEvents.map(e => <option key={e.label}>{e.label}</option>)}</select></label>
          <label className={label}>Event Status<select className={field} value={f.status} onChange={e => set("status", e.target.value)}><option value="" />{o?.entryStatuses.map(s => <option key={s}>{s}</option>)}</select></label>
          <label className={label}>Date Init<input className={field} value={f.dateInitialized} onChange={e => set("dateInitialized", e.target.value)} /></label>
          <label className={label}>Stk/Mod<input className={field} value={f.trackingRefNo} onChange={e => set("trackingRefNo", e.target.value)} /></label>
          <label className={label}>Date Closed<input className={field} value={f.dateClosed} onChange={e => set("dateClosed", e.target.value)} /></label>
          <label className={label}>Car #<input className={field} value={f.car} maxLength={4} onChange={e => set("car", e.target.value)} /></label>
          <label className={label}>Loc{battery && o
            ? <select className={field} value={f.location} onChange={e => set("location", e.target.value)}>{o.batteryLocations.map(s => <option key={s}>{s}</option>)}</select>
            : <input className={field} value={f.location} onChange={e => set("location", e.target.value)} />}</label>
          <label className={label}>Incd No<input className={field} value={f.incident} onChange={e => set("incident", e.target.value)} /></label>
          <label className={`${label} col-span-2`}>Comments<input className={field} value={f.comment} onChange={e => set("comment", e.target.value)} /></label>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {write && <button className={primary} disabled={busy || !!sel} onClick={add}>Add</button>}
          {write && <button className={primary} disabled={busy || !sel} onClick={() => ask(updateEvent)}>Update</button>}
          {write && <button className={secondary} disabled={busy || !sel} onClick={() => ask(closeEvent)}>Close</button>}
          {write && <button className={secondary} disabled={busy || !sel} onClick={remove}>Delete</button>}
          <button className={secondary} disabled={busy || !sel || !canAssign} onClick={() => ask(c => workAssign(c))}>Work Assign</button>
          <button className={secondary} onClick={clear}>Clear</button>
        </div>
        {msg && <p role="status" className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-slate-800">{msg}</p>}
      </section>
    </>
  );
}
