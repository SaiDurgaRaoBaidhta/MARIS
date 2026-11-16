/* MRS messages received (old VHCCS04I) on the clean API, with the windows it opened shown on the same page: a message's
 * detail and Save (VHCCS05I), its cars (VHCCS06I) and errors (VHCCS07I), and the hard copy (VHCCS18I). The old screen's
 * Delete of a whole message never had a time stamp to send, so it is not offered. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  deleteCar, deleteError, fetchCars, fetchErrors, fetchOptions, fetchPrintOptions, listMessages, printMessages, saveCar, saveError, saveMessage,
  type Car, type Criteria, type Listing, type Message, type MessageError, type Options, type PrintOptions,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const grid = "grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the detail fields VHCCS05I shows and saves (VHCCS5C_DATA's names) */
const DETAIL: [string, string][] = [
  ["tsMsgRcvd", "Time Stamp"], ["msgTranCd", "Msg Tran Cd"], ["msgVers", "Msg Vers"], ["msgSeqNo", "Msg Seq No"], ["msgLength", "Msg Length"],
  ["msgProcessedCd", "Processed Cd"], ["processedByCntrl", "Processed By"], ["msgRejectCd", "Reject Cd"], ["dateTrans", "Date Trans"],
  ["timeTrans", "Time Trans"], ["dateCreate", "Date Create"], ["timeCreate", "Time Create"], ["dateIcsCreate", "Date ICS Create"],
  ["timeIcsCreate", "Time ICS Create"], ["dateMsgCmplt", "Date Msg Cmplt"], ["timeMsgCmplt", "Time Msg Cmplt"], ["dateInsrvRev", "Date Insrv Rev"],
  ["conNo", "Con No"], ["dashNo", "Dash No"], ["conNoTo", "Con No To"], ["dashNoTo", "Dash No To"], ["conSize", "Con Size"],
  ["conCurrYrd", "Curr Yrd"], ["conCurrLoc", "Curr Loc"], ["conCurrLocIcs", "Curr Loc ICS"], ["conCurrTrkId", "Curr Trk"],
  ["originLoc", "Origin Loc"], ["originLocIcs", "Origin ICS"], ["originTrkId", "Origin Trk"], ["destLoc", "Dest Loc"], ["destLocIcs", "Dest ICS"],
  ["destTrkId", "Dest Trk"], ["inZoneFlag", "In Zone"], ["iTrainDir", "Train Dir"], ["iSize", "I Size"], ["iCurrYrd", "I Curr Yrd"],
  ["iCurrLoc", "I Curr Loc"], ["iConNo", "I Con No"], ["iConTo", "I Con To"], ["iCars", "Cars"], ["funcMsgCmplt", "Func Msg Cmplt"],
  ["termMsgCmplt", "Term Msg Cmplt"], ["userGrpMsgCmplt", "User Grp"], ["currConNo", "Curr Con No"], ["noCarsInd", "No Cars"],
  ["pseudoCmpltCd", "Pseudo Cmplt"], ["dateLastUpd", "Date Last Upd"], ["timeLastUpd", "Time Last Upd"], ["termLastUpd", "Term Last Upd"],
  ["msgData", "Msg Data"],
];

function Alert({ msg, ok }: { msg: string; ok?: boolean }) {
  if (!msg) return null;
  return <p role={ok ? "status" : "alert"} className={`mt-3 rounded-lg px-3 py-2 ${ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}>{msg}</p>;
}

function Select({ name, value, items, onChange }: { name: string; value: string; items: string[]; onChange: (v: string) => void }) {
  return (
    <label className={label}>{name}
      <select className={field} value={value} onChange={e => onChange(e.target.value)}>
        <option value="" />
        {items.map(i => <option key={i} value={i}>{i}</option>)}
      </select>
    </label>);
}

export function MessagesPage() {
  const [options, setOptions] = useState<Options | null>(null);
  const [c, setC] = useState<Criteria>({ fromDate: "", fromTime: "", toTime: "", tranCode: "", conNo: "", yard: "", location: "", scanner: "", processed: "" });
  const [listing, setListing] = useState<Listing | null>(null);
  const [chosen, setChosen] = useState<Message | null>(null);
  const [panelKind, setPanel] = useState<"" | "cars" | "errors" | "print">("");
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  useEffect(() => {
    fetchOptions().then(o => { setOptions(o); setC(x => ({ ...x, fromDate: o.fromDate, fromTime: o.fromTime, toTime: o.toTime })); }).catch(e => setError(text(e)));
  }, []);
  const set = (k: keyof Criteria) => (v: string) => setC(x => ({ ...x, [k]: v }));
  const list = async () => {
    setError(""); setDone(""); setChosen(null);
    try { setListing(await listMessages(c)); } catch (e) { setError(text(e)); setListing(null); }
  };
  const save = async () => {
    if (!chosen) return;
    setError(""); setDone("");
    try { const r = await saveMessage(chosen); if (r.ok) setDone(r.message); else setError(r.message); } catch (e) { setError(text(e)); }
  };
  const win = { ts: chosen ? String(chosen.tsMsgRcvd) : "", from: listing?.compareFrom, to: listing?.compareTo };

  return (
    <>
      <PageHead title="MRS Messages Received" code="VHCCS04I" />
      <section aria-label="Criteria" className={panel}>
        {options?.warnings.map(w => <p key={w} className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{w}</p>)}
        <div className={grid}>
          <label className={label}>Date<input className={field} value={c.fromDate} onChange={e => set("fromDate")(e.target.value)} /></label>
          <label className={label}>From Time<input className={field} value={c.fromTime} onChange={e => set("fromTime")(e.target.value)} /></label>
          <label className={label}>To Time<input className={field} value={c.toTime} onChange={e => set("toTime")(e.target.value)} /></label>
          <Select name="Msg Type" value={c.tranCode} items={options?.tranCodes ?? []} onChange={set("tranCode")} />
          <label className={label}>Con Number<input className={field} value={c.conNo} maxLength={4} onChange={e => set("conNo")(e.target.value)} /></label>
          <Select name="Yrd Loc" value={c.yard} items={options?.yards ?? []} onChange={set("yard")} />
          <Select name="Curr Loc" value={c.location} items={options?.locations ?? []} onChange={set("location")} />
          <Select name="Scan Id" value={c.scanner} items={(options?.scanners ?? []).map(s => s.id)} onChange={set("scanner")} />
          <Select name="ProcesCd" value={c.processed} items={options?.processedCodes ?? []} onChange={set("processed")} />
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className={primary} onClick={list}>List</button>
          <button type="button" className={secondary} disabled={!chosen} onClick={save}>Update</button>
          <button type="button" className={secondary} disabled={!listing} onClick={() => setPanel("cars")}>Msg Rcv Cars</button>
          <button type="button" className={secondary} disabled={!listing} onClick={() => setPanel("errors")}>Msg Rcv Errs</button>
          <button type="button" className={secondary} onClick={() => setPanel("print")}>Print</button>
        </div>
        <Alert msg={error} />
        <Alert msg={done} ok />
        {listing?.message && <p role="status" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{listing.message}</p>}
      </section>

      {listing && (
        <section aria-label="Messages" className={panel}>
          <p className="mb-2 text-sm text-slate-600">Number of Msgs To Be Proc: {listing.toBeProcessed} · Threshold Parm Value: {listing.threshold}</p>
          <table aria-label="Messages received" className="w-full text-left text-sm">
            <thead><tr>{["Time Stamp", "Msg Type", "Con", "Yrd", "Loc", "Scan", "Dir", "Proc"].map(h => <th key={h} className="px-2 py-1">{h}</th>)}</tr></thead>
            <tbody>
              {listing.messages.map(m => (
                <tr key={String(m.tsMsgRcvd)} aria-selected={chosen?.tsMsgRcvd === m.tsMsgRcvd} onClick={() => { setChosen({ ...m }); setDone(""); }}
                  className={`cursor-pointer ${chosen?.tsMsgRcvd === m.tsMsgRcvd ? "bg-bart-50" : ""}`}>
                  {["tsMsgRcvd", "msgTranCd", "conNo", "conCurrYrd", "conCurrLoc", "scanner", "scanDirection", "msgProcessedCd"].map(k => <td key={k} className="px-2 py-1">{m[k]}</td>)}
                </tr>))}
            </tbody>
          </table>
        </section>)}

      {chosen && (
        <section aria-label="Message detail" className={panel}>
          <div className={grid}>
            {DETAIL.map(([k, name]) => (
              <label key={k} className={label}>{name}
                <input className={field} value={chosen[k] ?? ""} onChange={e => setChosen(x => ({ ...(x ?? {}), [k]: e.target.value }))} />
              </label>))}
          </div>
          <button type="button" className={`${primary} mt-4`} onClick={save}>Save</button>
        </section>)}

      {panelKind === "cars" && <CarsPanel win={win} />}
      {panelKind === "errors" && <ErrorsPanel win={win} />}
      {panelKind === "print" && <PrintPanel c={c} />}
    </>
  );
}

function CarsPanel({ win }: { win: { ts?: string; from?: string; to?: string } }) {
  const [cars, setCars] = useState<Car[]>([]);
  const [orientations, setOrientations] = useState<string[]>([]);
  const [car, setCar] = useState<Car | null>(null);
  const [msg, setMsg] = useState<{ text: string; ok: boolean }>({ text: "", ok: true });
  const load = () => fetchCars(win).then(r => { setCars(r.cars); setOrientations(r.orientations); }).catch(e => setMsg({ text: text(e), ok: false }));
  useEffect(() => { load(); }, [win.ts, win.from, win.to]);   // eslint-disable-line react-hooks/exhaustive-deps
  const run = async (f: () => Promise<{ ok: boolean; message: string }>) => {
    try { const r = await f(); setMsg({ text: r.message, ok: r.ok }); if (r.ok) { setCar(null); load(); } } catch (e) { setMsg({ text: text(e), ok: false }); }
  };
  const set = (k: keyof Car, v: string) => setCar(x => (x ? { ...x, [k]: k === "carOrder" || k === "dashNo" ? Number(v) || 0 : v } : x));
  return (
    <section aria-label="Message cars" className={panel}>
      <table aria-label="Cars" className="w-full text-left text-sm">
        <thead><tr>{["Car", "Order", "Con", "Dash", "In Service", "Orient", "Dir"].map(h => <th key={h} className="px-2 py-1">{h}</th>)}</tr></thead>
        <tbody>{cars.map(x => (
          <tr key={x.timestamp + x.carNo} className="cursor-pointer" onClick={() => setCar({ ...x })}>
            {[x.carNo, x.carOrder, x.conNo, x.dashNo, x.dateInService, x.orientation, x.orientationDirection].map((v, i) => <td key={i} className="px-2 py-1">{v}</td>)}
          </tr>))}</tbody>
      </table>
      {car && (
        <div className={`${grid} mt-3`}>
          <label className={label}>Car No<input className={field} value={car.carNo} onChange={e => set("carNo", e.target.value)} /></label>
          <label className={label}>Car Order<input className={field} value={car.carOrder} onChange={e => set("carOrder", e.target.value)} /></label>
          <label className={label}>Con No<input className={field} value={car.conNo} onChange={e => set("conNo", e.target.value)} /></label>
          <label className={label}>Dash No<input className={field} value={car.dashNo} onChange={e => set("dashNo", e.target.value)} /></label>
          <label className={label}>Date Insrv Rev<input className={field} value={car.dateInService} onChange={e => set("dateInService", e.target.value)} /></label>
          <Select name="Orientation" value={car.orientation} items={orientations} onChange={v => set("orientation", v)} />
          <label className={label}>Orientation Dir<input className={field} value={car.orientationDirection} onChange={e => set("orientationDirection", e.target.value)} /></label>
          <div className="flex items-end gap-2">
            <button type="button" className={primary} onClick={() => run(() => saveCar(car))}>Update</button>
            <button type="button" className={secondary} onClick={() => run(() => deleteCar(car))}>Delete</button>
          </div>
        </div>)}
      <Alert msg={msg.text} ok={msg.ok} />
    </section>);
}

function ErrorsPanel({ win }: { win: { ts?: string; from?: string; to?: string } }) {
  const [errors, setErrors] = useState<MessageError[]>([]);
  const [err, setErr] = useState<MessageError | null>(null);
  const [msg, setMsg] = useState<{ text: string; ok: boolean }>({ text: "", ok: true });
  const load = () => fetchErrors(win).then(setErrors).catch(e => setMsg({ text: text(e), ok: false }));
  useEffect(() => { load(); }, [win.ts, win.from, win.to]);   // eslint-disable-line react-hooks/exhaustive-deps
  const run = async (f: () => Promise<{ ok: boolean; message: string }>) => {
    try { const r = await f(); setMsg({ text: r.message, ok: r.ok }); if (r.ok) { setErr(null); load(); } } catch (e) { setMsg({ text: text(e), ok: false }); }
  };
  const set = (k: keyof MessageError, v: string) => setErr(x => (x ? { ...x, [k]: k === "count" ? Number(v) || 0 : v } : x));
  return (
    <section aria-label="Message errors" className={panel}>
      <table aria-label="Errors" className="w-full text-left text-sm">
        <thead><tr>{["Time Stamp", "Cnt", "Function", "Type", "Field", "Description"].map(h => <th key={h} className="px-2 py-1">{h}</th>)}</tr></thead>
        <tbody>{errors.map(x => (
          <tr key={x.timestamp + x.fieldName} className="cursor-pointer" onClick={() => setErr({ ...x })}>
            {[x.timestamp, x.count, x.function, x.type, x.fieldName, x.description].map((v, i) => <td key={i} className="px-2 py-1">{v}</td>)}
          </tr>))}</tbody>
      </table>
      {err && (
        <div className={`${grid} mt-3`}>
          <label className={label}>Count Errs<input className={field} value={err.count} onChange={e => set("count", e.target.value)} /></label>
          <label className={label}>Maris Func<input className={field} value={err.function} onChange={e => set("function", e.target.value)} /></label>
          <label className={label}>Err Type<input className={field} value={err.type} onChange={e => set("type", e.target.value)} /></label>
          <label className={label}>Field Name<input className={field} value={err.fieldName} onChange={e => set("fieldName", e.target.value)} /></label>
          <label className={label}>Err Desc<input className={field} value={err.description} onChange={e => set("description", e.target.value)} /></label>
          <div className="flex items-end gap-2">
            <button type="button" className={primary} onClick={() => run(() => saveError(err))}>Update</button>
            <button type="button" className={secondary} onClick={() => run(() => deleteError(err))}>Delete</button>
          </div>
        </div>)}
      <Alert msg={msg.text} ok={msg.ok} />
    </section>);
}

function PrintPanel({ c }: { c: Criteria }) {
  const [o, setO] = useState<PrintOptions | null>(null);
  const [printer, setPrinter] = useState("");
  const [sort, setSort] = useState(1);
  const [msg, setMsg] = useState<{ text: string; ok: boolean }>({ text: "", ok: true });
  useEffect(() => { fetchPrintOptions().then(setO).catch(e => setMsg({ text: text(e), ok: false })); }, []);
  const print = async () => {
    try {
      await printMessages({ printer, fromDate: c.fromDate, fromTime: c.fromTime, toTime: c.toTime, tranCode: c.tranCode, conNo: c.conNo, yard: c.yard,
        location: c.location, scanner: c.scanner, sort });
      setMsg({ text: "Hard copy sent to " + printer + ".", ok: true });
    } catch (e) { setMsg({ text: text(e), ok: false }); }
  };
  return (
    <section aria-label="Hard copy" className={panel}>
      <div className={grid}>
        <label className={label}>Printer
          <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
            <option value="" />
            {o?.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.name} ({p.vtam})</option>)}
          </select>
        </label>
        <fieldset className="flex items-end gap-3">
          {o?.sorts.map((s, i) => (
            <label key={s} className="flex items-center gap-1 text-sm"><input type="radio" name="sort" checked={sort === i + 1} onChange={() => setSort(i + 1)} />{s}</label>))}
        </fieldset>
        <div className="flex items-end"><button type="button" className={primary} disabled={!printer} onClick={print}>Hard Copy</button></div>
      </div>
      <Alert msg={msg.text} ok={msg.ok} />
    </section>);
}
