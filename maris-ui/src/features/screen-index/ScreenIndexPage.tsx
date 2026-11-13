/* MARIS Screen Index – AS-IS document: every screen in the document, searchable. */
import { useState } from "react";
import { KNOWN } from "@/features/screens/registry";
import { useApp, type Page } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import doc from "./screenIndexDoc.json";

type Row = { code: string; title: string; id: string; links: string[]; note: string };
type Sec = { letter: string; name: string; rows: Row[] };
const SECS = doc as Sec[];
const YARDS = ["CY", "DY", "HY", "RY", "DP", "MB", "BS", "PB"];

export function ScreenIndexPage() {
  const app = useApp();
  const [q, setQ] = useState("");
  const ql = q.trim().toLowerCase();
  const target = (r: Row, link: string): Page | null => {
    if (YARDS.includes(link)) return /inventory/i.test(r.title) ? { kind: "screen", key: "web:inv", label: "Yard Track Inventory", yard: link } : { kind: "map", yard: link, shop: /shop/i.test(r.title), label: `${link} map` };
    const id = r.id.replace(/\s.*$/, "");
    if (KNOWN["egl:" + id]) return { kind: "screen", key: "egl:" + id, label: r.title };
    if (/notification/i.test(r.id)) return { kind: "screen", key: "web:nogo", label: "NOGO Notifications" };
    if (/MarisICSWeb/i.test(r.id)) return { kind: "screen", key: "web:print", label: "MARIS Print" };
    if (/index\.cfm/i.test(r.id)) return { kind: "screen", key: "web:cf", label: "ColdFusion Reports" };
    if (/consistQuery/i.test(r.id)) return { kind: "screen", key: "web:train", label: "Status of Train Summary" };
    if (/availab/i.test(r.id)) return { kind: "screen", key: "web:avail", label: "Car Availability Report" };
    return null;
  };
  const go = (r: Row, link: string) => { const p = target(r, link); if (p) app.open(p); else app.info("Sorry, this function is still under development"); };
  const shown = SECS.map(s => ({ ...s, rows: s.rows.filter(r => !ql || `${r.code} ${r.title} ${r.id} ${s.name}`.toLowerCase().includes(ql)) })).filter(s => s.rows.length);
  const total = SECS.reduce((n, s) => n + s.rows.reduce((m, r) => m + Math.max(r.links.length, 0), 0), 0);
  const count = ql ? `${shown.reduce((n, s) => n + s.rows.length, 0)} matching` : `${total} screens`;

  return (
    <>
      <PageHead title="MARIS Screen Index - AS-IS Document" />
      <div className="animate-wv-in mx-2 mb-6">
        <div className="sticky top-16 z-10 flex items-center gap-4 rounded-t-xl border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur dark:border-line-dark dark:bg-panel-dark/95">
          <b className="text-navy dark:text-ink-dark">Screens in the MARIS AS-IS document</b>
          <span className="rounded-full bg-sky px-2 py-0.5 text-[12px] font-semibold text-bart-700 dark:bg-bart/18 dark:text-[#7fd3f3]">{count}</span>
          <input autoFocus value={q} onChange={e => setQ(e.target.value)} aria-label="Search screens" placeholder='Search by name, screen ID or figure (e.g. "Fig 7", VHYT09I, Repair)'
            className="h-8 flex-1 rounded-md border border-slate-300 bg-white px-2 outline-none focus:border-bart focus:ring-3 focus:ring-bart/15 dark:border-line-dark dark:bg-[#050d17]" />
        </div>
        <div className="space-y-3 pt-3">
          {shown.map(s => (
            <section key={s.letter + s.name} className="overflow-hidden rounded-xl border border-white/90 bg-white/70 shadow-[0_0_0_1px_rgba(169,219,241,.35)] dark:border-line-dark dark:bg-[#0d1b2c]">
              <h2 className="flex items-center gap-2 border-b border-slate-100 px-3 py-2 font-semibold text-navy dark:border-line-dark dark:text-ink-dark">
                <span className="grid size-5 place-items-center rounded bg-bart text-[12px] font-bold text-white">{s.letter}</span>{s.name}</h2>
              {s.rows.map((r, i) => (
                <div key={i} className={`grid grid-cols-[80px_1fr_minmax(0,1fr)_auto] items-center gap-3 px-3 py-1 even:bg-slate-50/70 dark:even:bg-white/[.03] ${r.links.length ? "" : "text-slate-400"}`}>
                  <span>{r.code}</span><span>{r.title}</span><span className="truncate text-bart-700 dark:text-[#4fb8ea]">{r.id}</span>
                  <span className="flex gap-2">{r.links.length ? r.links.map(l => <button key={l} type="button" onClick={() => go(r, l)} className="text-bart-600 underline hover:text-navy dark:text-[#4fb8ea]">{l}</button>) : <small>{r.note}</small>}</span>
                </div>))}
            </section>))}
          {!shown.length && <p className="p-6 text-center text-slate-500">No screens match.</p>}
        </div>
      </div>
    </>
  );
}
