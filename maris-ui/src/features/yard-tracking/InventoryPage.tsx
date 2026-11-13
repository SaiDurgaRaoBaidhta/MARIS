/* F. YARD/TMZ: Daily Yard Track Inventory (DailyYardTrackInventory.jsp) */
import { useEffect, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchFleet } from "./api";
import type { Car, Fleet } from "./types";

const typeLetter = (c: Car) => /^\D*3\d{3}/.test(c.car) ? "D" : "E";
const pad = (n: number) => String(n).padStart(2, "0");
const statCls = (s: string) => s === "GO" ? "" : s === "AWPART" || s === "MODHLD" ? "text-amber-700 dark:text-amber-300" : "text-red-600 dark:text-red-400";

export function InventoryPage({ initialYard = "CY" }: { initialYard?: string }) {
  const app = useApp();
  const [F, setF] = useState<Fleet | null>(null);
  const [code, setCode] = useState(initialYard);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => { fetchFleet().then(setF).catch(e => app.toast(String(e), "err")); }, [app]);
  if (!F) return <><PageHead title="Daily Yard Track Inventory" /><p className="p-8 text-slate-500" role="status">Loading…</p></>;

  const y = F.yards[code];
  const tracks = (F.fleet[code] || []).filter(t => t.id !== "T-95" && t.id !== "TURN");
  const go = tracks.flatMap(t => t.cars).filter((c): c is Car => !!c && (c.stat === "GO" || c.stat === "USABLE"));
  const d = go.filter(c => typeLetter(c) === "D").length, e = go.length - d;
  const n = Math.min(14, Math.max(1, ...tracks.map(t => t.cap)));
  const date = `${now.getFullYear()}/${pad(now.getMonth() + 1)}/${pad(now.getDate())}`, time = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  const rows = tracks.flatMap(t => Array.from({ length: Math.ceil(t.cap / n) }, (_, b) => ({ t, base: b * n })));

  return (
    <>
      <PageHead title={`${y.city} Daily Yard Track Inventory`}>
        <label className="flex items-center gap-2 font-semibold text-slate-700 dark:text-slate-300">Yard
          <select value={code} onChange={ev => setCode(ev.target.value)} className="sel-arrow h-[38px] rounded-xl border border-slate-200 bg-white pl-3 pr-8 dark:border-line-dark dark:bg-chip-dark">
            {F.mapYards.map(c => <option key={c} value={c}>{c} – {F.yards[c].name}</option>)}
          </select></label>
      </PageHead>
      <div className="animate-wv-in mx-2 mb-6 rounded-[20px] border border-white/85 bg-white p-3.5 shadow-[0_0_0_1px_rgba(169,219,241,.4),0_30px_60px_-36px_rgba(11,37,69,.35)] dark:border-line-dark dark:bg-[#0c1826]">
        <div className="mb-3 grid grid-cols-[1fr_auto_1fr] items-center gap-4 rounded-xl border border-navy/80 px-3 py-2 dark:border-line-dark">
          <div><img src="/bart-logo.png" alt="BART" className="h-7" /><div className="mt-1 text-[12px]">plmrs03v</div></div>
          <div className="text-center"><div className="text-[16px] font-bold">Daily Yard Track Inventory - <u>{y.city}</u></div>
            <div className="mt-1 text-[12px] font-semibold">Total Go/Usable Revenue Cars<br />D: <u>{d}</u>&nbsp; E: <u>{e}</u></div></div>
          <div className="flex justify-end gap-6 text-[12px]"><span>Shift 1st [ &nbsp;]<br />2nd [ &nbsp;]<br />3rd [ &nbsp;] ____________</span><span className="text-right">Date: {date}<br />Time: {time}<br />DYT-INV-JSP</span></div>
        </div>
        <div className="scroll-thin overflow-auto">
          <table className="w-full border-collapse text-center text-[12px]">
            <thead><tr><th scope="col" className="border border-slate-200 bg-slate-100 px-2 py-1 dark:border-line-dark dark:bg-chip-dark">Track</th>
              {Array.from({ length: n }, (_, i) => <th key={i} scope="col" className="border border-slate-200 bg-slate-100 px-2 py-1 dark:border-line-dark dark:bg-chip-dark">{i + 1}</th>)}</tr></thead>
            <tbody>{rows.map(({ t, base }) => (
              <tr key={t.id + base} className="even:bg-slate-50/70 dark:even:bg-white/[.03]">
                <th scope="row" className="border border-slate-200 px-2 py-1 font-bold dark:border-line-dark">{base ? "" : t.id}</th>
                {Array.from({ length: n }, (_, i) => {
                  const c = t.cars[base + i], na = base + i >= t.cap;
                  return <td key={i} className={`h-9 min-w-[64px] border border-slate-200 px-1 dark:border-line-dark ${na ? "bg-slate-100 dark:bg-chip-dark" : ""}`}>
                    {c && <><a className="cursor-pointer text-bart-700 underline dark:text-[#4fb8ea]">{c.car} <u>{(c.home || "C")[0]}</u></a><br /><span className={statCls(c.stat)}>{typeLetter(c)}-{c.stat.replace(" ", "")}</span></>}
                  </td>;
                })}
              </tr>))}</tbody>
          </table>
        </div>
        <p className="mt-3 text-[12px] text-slate-600 dark:text-slate-400">This report will refresh from DB: BRMRSP every 2 minutes. Click{" "}
          <button type="button" onClick={() => setNow(new Date())} className="rounded border border-slate-300 px-2 py-0.5 font-semibold dark:border-line-dark">Refresh</button> to reload immediately. Send email to <a className="text-bart-700 underline">maxadmin@bart.gov</a> for support.</p>
      </div>
    </>
  );
}
