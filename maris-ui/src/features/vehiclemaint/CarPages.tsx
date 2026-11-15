/* Vehicle table and status history pages: add a car (old VHVM02I), change a car (VHVM03I), an incident's status history (VHVM06I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  addCar, addEntry, carOptions, changeCar, changeEntry, deleteEntry, entries, findCar, historyOptions, subLocations,
  type Car, type CarFields, type CarOptions, type Entry, type EntryChange, type HistoryOptions,
} from "./carApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const grid = "grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const param = (name: string) => new URLSearchParams(window.location.search).get(name) ?? "";

/** the old screens' field captions, by record field */
const CAPTIONS: Record<string, string> = {
  carNo: "Car No", carType: "Car Type", vehStat: "Veh Stat", vehStatPrev: "Veh Stat Prev", homeYrd: "Home Yrd", warInd: "War Ind",
  atoPresentInd: "ATO", batteryReplInd: "Battery Replaced", dateBatteryRepl: "Battery Date", batteryReplLoc: "Battery Location",
  currSchdPmNo: "Cur Schd PM", nextSchdPmNo: "Next Schd PM", lastSchdPmNo: "Last PM No", hrsToNextPm: "Hrs To Next PM",
  hrsFromLastPm: "Hrs From PM", hrsAtLastPm: "Hrs At Last PM", lastPmLoc: "Last PM Loc", cntTimesWash: "Wash Cnt", cntTimesPaInspc: "PA Cnt",
  whereIsCar: "Where Is Car", currLoc: "Cur Loc", conNo: "Con No", dashNo: "Dash No", vagenOrientation: "Orient", conNoFail: "Con Fail",
  dashNoFail: "Dash Fail", dateEndCarRev: "Date End Car", timeEndCar: "Time End Car", endCarHrs: "EndCar Hrs", endCarMin: "EndCar Min",
  midCarHrs: "MidCar Hrs", midCarMin: "MidCar Min", cumHrs: "Cum Hrs", cumMin: "Cum Min", brkInHrs: "Brk In Hrs", mtbi: "MTBI",
  mtbiHrs: "MTBI Hrs", mtbiIncds: "MTBI Incds", trainIncdInd: "Train Incd Ind", cameraCd: "Camera Cd", configCmpntStat: "Config Cmpnt Stat",
  dateConfigCStat: "Date Cmpnt Stat", timeConfigCStat: "Time Cmpnt Stat", configCmpntType: "Config Cmpnt Type", dateConfigUpd: "Date Config Upd",
  timeConfigUpd: "Time Config Upd", termConfigUpd: "Term Config Upd",
};
const caption = (f: string) => CAPTIONS[f] ?? f;

function Alert({ text, ok }: { text: string; ok?: boolean }) {
  if (!text) return null;
  return <p role={ok ? "status" : "alert"} className={`mt-3 whitespace-pre-line rounded-lg px-3 py-2 ${ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}>{text}</p>;
}

/** the list a car field chooses from, when the old screen had one */
function choices(f: string, o: CarOptions, type: string, adding: boolean): string[] | null {
  switch (f) {
    case "carType": return adding ? o.addCarTypes : o.carTypes;
    case "vehStat": return o.statuses;
    case "vehStatPrev": return o.previousStatuses;
    case "homeYrd": return o.homeYards;
    case "warInd": case "atoPresentInd": case "batteryReplInd": return o.yesNo;
    case "batteryReplLoc": return o.batteryLocations;
    case "lastPmLoc": return o.lastPmLocations;
    case "whereIsCar": return o.whereIsCar;
    case "currLoc": return adding ? null : o.locations;
    case "cameraCd": return o.cameras.map(c => c.code);
    case "vagenOrientation": return o.orientations[type] ?? [];
    case "currSchdPmNo": case "nextSchdPmNo": case "lastSchdPmNo": return o.schedulePms.filter(Boolean);
    case "configCmpntStat": return o.configStatuses;
    case "configCmpntType": return o.configTypes;
    default: return null;
  }
}

function CarField({ f, value, set, options, type, adding, disabled }: {
  f: string; value: string; set: (v: string) => void; options: CarOptions; type: string; adding: boolean; disabled?: boolean;
}) {
  const list = choices(f, options, type, adding);
  const date = f.startsWith("date");
  return (
    <label className={label}>{caption(f)}
      {list ? (
        <select className={field} value={value} disabled={disabled} onChange={e => set(e.target.value)}>
          <option value="" />
          {list.map(i => <option key={i} value={i}>{f === "cameraCd" ? `${i} ${options.cameras.find(c => c.code === i)?.description ?? ""}` : i}</option>)}
        </select>
      ) : <input className={field} type={date ? "date" : "text"} value={value} disabled={disabled} onChange={e => set(e.target.value)} />}
    </label>
  );
}

const str = (v: unknown) => (v === null || v === undefined ? "" : String(v));

/** the add form's fields: the ones the old add screen shows */
const ADD_FIELDS = ["carNo", "carType", "vehStat", "vehStatPrev", "homeYrd", "warInd", "atoPresentInd", "batteryReplInd", "dateBatteryRepl",
  "batteryReplLoc", "cameraCd", "vagenOrientation", "currSchdPmNo", "nextSchdPmNo", "hrsToNextPm", "whereIsCar", "currLoc", "trainIncdInd"];

/** old VHVM02I: add a car to the vehicle table */
export function AddCarPage() {
  const [options, setOptions] = useState<CarOptions | null>(null);
  const [car, setCar] = useState<CarFields>({});
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { carOptions().then(o => { setOptions(o); setCar({ dateBatteryRepl: o.addDefaults.dateBatteryRepl ?? "" }); }).catch(e => setError(message(e))); }, []);
  if (!options) return <><PageHead title="Add Vehicle" code="VHVM02I" />{error ? <Alert text={error} /> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  const set = (f: string) => (v: string) => setCar(c => ({ ...c, [f]: v }));
  const add = async () => {
    setBusy(true); setError(""); setDone("");
    const body: CarFields = {};
    for (const f of ADD_FIELDS) if (str(car[f]) !== "") body[f] = str(car[f]);
    try { setDone((await addCar(body)).message); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Add Vehicle" code="VHVM02I" />
      <section aria-label="Vehicle" className={panel}>
        <div className={grid}>
          {ADD_FIELDS.map(f => <CarField key={f} f={f} value={str(car[f])} set={set(f)} options={options} type={str(car.carType)} adding />)}
        </div>
        <div className="mt-4 flex gap-2">
          <button type="button" className={primary} disabled={busy} onClick={add}>Add</button>
          <button type="button" className={secondary} onClick={() => { setCar({ ...options.addDefaults }); setError(""); setDone(""); }}>Clear</button>
        </div>
        <Alert text={error} />
        <Alert text={done} ok />
      </section>
    </>
  );
}

/** old VHVM03I: change a car of the vehicle table; ordinary groups may change only the fields the old screen leaves open */
export function ChangeCarPage() {
  const [options, setOptions] = useState<CarOptions | null>(null);
  const [number, setNumber] = useState(param("carNo"));
  const [car, setCar] = useState<Car | null>(null);
  const [edit, setEdit] = useState<CarFields>({});
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { carOptions().then(setOptions).catch(e => setError(message(e))); }, []);
  const find = async () => {
    setBusy(true); setError(""); setDone(""); setCar(null);
    try { const c = await findCar(number.trim()); setCar(c); setEdit({ ...c.fields }); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const update = async () => {
    if (!car) return;
    setBusy(true); setError(""); setDone("");
    const changes: CarFields = {};
    for (const f of car.editable) if (str(edit[f]) !== str(car.fields[f])) changes[f] = str(edit[f]);
    try { setDone((await changeCar(number.trim(), changes)).message); setCar({ ...car, fields: { ...edit } }); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Update Vehicle" code="VHVM03I" />
      <section aria-label="Car" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car No<input className={field} value={number} maxLength={4} onChange={e => setNumber(e.target.value)} /></label>
          <button type="button" className={primary} disabled={busy || !number.trim()} onClick={find}>Find Car</button>
        </div>
        <Alert text={error} />
        <Alert text={done} ok />
      </section>
      {car && options && (
        <section aria-label="Vehicle" className={panel}>
          <div className={grid}>
            {Object.keys(car.fields).filter(f => f !== "carNo").map(f => (
              <CarField key={f} f={f} value={str(edit[f])} set={v => setEdit(e => ({ ...e, [f]: v }))} options={options} type={str(edit.carType)}
                adding={false} disabled={!car.editable.includes(f)} />))}
          </div>
          <div className="mt-4 flex gap-2">
            <button type="button" className={primary} disabled={busy} onClick={update}>Update</button>
            <button type="button" className={secondary} onClick={() => { setCar(null); setNumber(""); setDone(""); setError(""); }}>Clear Screen</button>
          </div>
        </section>)}
    </>
  );
}

const EMPTY: EntryChange = { timestamp: "", date: "", time: "", previousStatus: "", newStatus: "", yard: "", subLocation: "", pseudoConsist: "", moveReason: "", homeYard: "" };

/** old VHVM06I: an incident's status history: pick an entry to change or delete it, or add one */
export function StatusHistoryPage() {
  const [options, setOptions] = useState<HistoryOptions | null>(null);
  const [incident, setIncident] = useState(param("incdNo"));
  const [list, setList] = useState<Entry[] | null>(null);
  const [form, setForm] = useState<EntryChange>(EMPTY);
  const [selected, setSelected] = useState("");
  const [subs, setSubs] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { historyOptions().then(setOptions).catch(e => setError(message(e))); }, []);
  useEffect(() => { if (form.yard) subLocations(form.yard).then(setSubs).catch(() => setSubs([])); else setSubs([]); }, [form.yard]);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); setDone(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  const load = () => run(async () => { setList(await entries(incident.trim())); setSelected(""); setForm(EMPTY); });
  const after = (r: { message: string; entries: Entry[] }) => { setDone(r.message); setList(r.entries); setSelected(""); setForm(EMPTY); };
  const pick = (e: Entry) => {
    setSelected(e.timestamp);
    setForm({ timestamp: e.timestamp, date: e.date, time: e.time, previousStatus: e.previousStatus, newStatus: e.newStatus, yard: e.yard,
      subLocation: e.subLocation, pseudoConsist: e.pseudoConsist, moveReason: e.moveReason, homeYard: e.homeYard });
  };
  const set = (k: keyof EntryChange) => (v: string) => setForm(f => ({ ...f, [k]: v }));
  const choose = (name: string, k: keyof EntryChange, items: string[]) => (
    <label className={label}>{name}
      <select className={field} value={form[k]} onChange={e => set(k)(e.target.value)}>
        <option value="" />{items.map(i => <option key={i} value={i}>{i}</option>)}
      </select>
    </label>);
  const text = (name: string, k: keyof EntryChange, type = "text") => (
    <label className={label}>{name}<input className={field} type={type} step={type === "time" ? 1 : undefined} value={form[k]} onChange={e => set(k)(e.target.value)} /></label>);
  return (
    <>
      <PageHead title="Incident Status History" code="VHVM06I" />
      <section aria-label="Incident" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Incd No<input className={field} value={incident} maxLength={8} onChange={e => setIncident(e.target.value)} /></label>
          <button type="button" className={primary} disabled={busy || !incident.trim()} onClick={load}>Show</button>
        </div>
        <Alert text={error} />
        <Alert text={done} ok />
      </section>
      {list && options && (
        <>
          <section aria-label="Status changes" className={panel}>
            {list.length === 0 ? <p className="text-slate-500">No status changes.</p> : (
              <table className="w-full text-left text-[13px]">
                <thead><tr>{["Time Stamp", "Date Stat Chg", "Time Stat Chg", "Prev Stat", "New Stat", "Yrd Trk Yrd", "Sub Loc", "Psuedo Con No", "Mv Reason Cd", "Home Yrd", "User Grp"]
                  .map(h => <th key={h} className="px-2 py-1">{h}</th>)}</tr></thead>
                <tbody>{list.map(e => (
                  <tr key={e.timestamp} aria-selected={selected === e.timestamp} onClick={() => pick(e)}
                    className={`cursor-pointer ${selected === e.timestamp ? "bg-sky-100" : "hover:bg-slate-50"}`}>
                    {[e.timestamp, e.date, e.time, e.previousStatus, e.newStatus, e.yard, e.subLocation, e.pseudoConsist, e.moveReason, e.homeYard, e.userGroup]
                      .map((v, i) => <td key={i} className="px-2 py-1">{v}</td>)}
                  </tr>))}</tbody>
              </table>)}
          </section>
          <section aria-label="Status change" className={panel}>
            <div className={grid}>
              {text("Date Stat Chg", "date", "date")}
              {text("Time Stat Chg", "time", "time")}
              {choose("Prev Stat", "previousStatus", options.statuses)}
              {choose("New Stat", "newStatus", options.statuses)}
              {choose("Yrd Trk Yrd", "yard", options.yards)}
              {choose("Sub Loc", "subLocation", subs)}
              {text("Psuedo Con No", "pseudoConsist")}
              {choose("Mv Reason Cd", "moveReason", options.moveReasons)}
              {choose("Home Yrd", "homeYard", options.yards)}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button type="button" className={primary} disabled={busy || !!selected} onClick={() => run(async () => after(await addEntry(incident.trim(), form)))}>Add</button>
              <button type="button" className={primary} disabled={busy || !selected} onClick={() => run(async () => after(await changeEntry(incident.trim(), form)))}>Update</button>
              <button type="button" className={secondary} disabled={busy || !selected} onClick={() => run(async () => after(await deleteEntry(incident.trim(), selected)))}>Delete</button>
              <button type="button" className={secondary} onClick={() => { setSelected(""); setForm(EMPTY); }}>Part Clear</button>
            </div>
          </section>
        </>)}
    </>
  );
}
