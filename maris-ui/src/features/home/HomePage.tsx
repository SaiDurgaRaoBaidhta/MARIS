import { useEffect, useMemo, useState } from "react";
import { fetchFleet } from "@/features/yard-tracking/api";
import type { Car, Fleet, Yard } from "@/features/yard-tracking/types";
import { useApp } from "@/shared/lib/app-state";
import { Icon } from "@/shared/ui/Icon";

/* status groups: colour and severity order */
const GRP = { go: ["GO", "#8cc4a5"], hi: ["GO & high PM", "#e8c48c"], usable: ["Usable", "#9fb8e3"], nogo: ["NO GO / holds", "#e4a3a3"] } as const;
type G = keyof typeof GRP;
const SEV: Record<G, number> = { nogo: 0, hi: 1, usable: 2, go: 3 };
const grpOf = (c: Car): G => c.stat === "GO" ? "go" : c.stat === "USABLE" ? "usable" : /^(HIGH|HPM)$/.test(c.stat) ? "hi" : "nogo";
const carId = (c: Car) => String(c.car).replace(/[^0-9A-Z]/gi, "");
type Row = { c: Car; id: string; code: string; track: string; g: G };
type YardStat = { code: string; y: Yard; cnt: Record<G, number>; cars: number; slots: number; occ: number };

function summarize(F: Fleet) {
  const yards: YardStat[] = [], all: Record<G, number> = { go: 0, hi: 0, usable: 0, nogo: 0 }, cars: Row[] = []; let slots = 0;
  F.mapYards.forEach(code => {
    const cnt: Record<G, number> = { go: 0, hi: 0, usable: 0, nogo: 0 }; let n = 0, sl = 0;
    (F.fleet[code] || []).forEach(t => t.cars.forEach(c => { sl++; if (!c) return; n++; const g = grpOf(c); cnt[g]++; all[g]++; cars.push({ c, id: carId(c), code, track: t.id, g }); }));
    slots += sl; yards.push({ code, y: F.yards[code] || { code, name: code, city: code, pseudo: code }, cnt, cars: n, slots: sl, occ: sl ? Math.round(100 * n / sl) : 0 });
  });
  const tracks = F.mapYards.reduce((n, code) => n + (F.fleet[code] || []).filter(t => t.cars.some(Boolean)).length, 0);
  return { yards, all, cars, slots, tracks, total: cars.length };
}

const StackBar = ({ cnt, n }: { cnt: Record<G, number>; n: number }) => (
  <div className="flex h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-chip-dark">
    {n ? (Object.keys(GRP) as G[]).map(k => cnt[k] ? <i key={k} style={{ flex: cnt[k], background: GRP[k][1] }} title={`${GRP[k][0]}: ${cnt[k]}`} /> : null) : null}
  </div>);

function Ring({ p, color, tip }: { p: number; color: string; tip: string }) {
  const r = 20, L = 2 * Math.PI * r;
  return (
    <svg viewBox="0 0 48 48" width="48" height="48" role="img" aria-label={tip}><title>{tip}</title>
      <circle cx="24" cy="24" r={r} fill="none" strokeWidth="4" className="stroke-slate-200 dark:stroke-chip-dark" />
      <circle cx="24" cy="24" r={r} fill="none" strokeWidth="4" strokeLinecap="round" stroke={color} strokeDasharray={`${(L * p / 100).toFixed(1)} ${L.toFixed(1)}`} transform="rotate(-90 24 24)" />
      <text x="24" y="28" textAnchor="middle" className="fill-slate-700 text-[11px] font-bold dark:fill-slate-200">{p}%</text>
    </svg>);
}

const card = "rounded-[18px] border border-white/90 bg-gradient-to-br from-white/80 to-white/55 shadow-[0_0_0_1px_rgba(169,219,241,.35),0_16px_30px_-22px_rgba(11,37,69,.4)] dark:border-line-dark dark:from-[#10213a] dark:to-[#0d1b2c] dark:shadow-none";

export function HomePage() {
  const app = useApp();
  const s = app.session!;
  const [F, setF] = useState<Fleet | null>(null);
  const [f, setFilter] = useState("all");          // all | att | go | hi | usable | nogo | s:<STATUS>
  const [yard, setYard] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<{ k: keyof typeof KEY; dir: 1 | -1 }>({ k: "sev", dir: 1 });
  useEffect(() => { fetchFleet().then(setF).catch(e => app.toast(String(e), "err")); }, [app]);
  const D = useMemo(() => F && summarize(F), [F]);

  const h = new Date().getHours(), hello = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
  const d = new Date(s.signedAt), today = `${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}-${d.getFullYear()}`;
  const openMap = (code: string, shop: boolean) => app.go({ kind: "map", yard: code, shop, label: `${F?.yards[code]?.name || code} ${shop ? "Shop Map" : "Map"}` });
  const yName = (c: string) => F?.yards[c]?.name || c;

  if (!D) return <div className="p-10 text-slate-500" role="status">Loading the fleet…</div>;
  const pct = (n: number) => D.total ? Math.round(100 * n / D.total) : 0;
  const att = D.all.nogo + D.all.hi, occ = D.slots ? Math.round(100 * D.total / D.slots) : 0;
  // MARIS does not send track capacity (the middleware says so): rank yards by cars instead of track space
  const capKnown = F?.capacityKnown !== false;
  const busy = [...D.yards].sort((a, b) => capKnown ? b.occ - a.occ : b.cars - a.cars)[0];

  const inYard = D.cars.filter(r => !yard || r.code === yard);
  const byF = (r: Row) => f === "all" || (f === "att" ? r.g === "nogo" || r.g === "hi" : f.startsWith("s:") ? r.c.stat === f.slice(2) : r.g === f);
  const Q = q.trim().toUpperCase();
  const key = KEY[sort.k];
  const rows = inYard.filter(byF).filter(r => !Q || [r.id, r.c.stat, r.code, r.track, r.c.home, r.c.from].join(" ").toUpperCase().includes(Q))
    .sort((a, b) => sort.dir * (key(a).localeCompare(key(b), undefined, { numeric: true }) || a.code.localeCompare(b.code) || a.id.localeCompare(b.id, undefined, { numeric: true })));
  const n = (k: string) => inYard.filter(r => k === "all" || (k === "att" ? r.g === "nogo" || r.g === "hi" : r.g === k)).length;
  const chips: [string, string][] = ([["all", "All"], ["att", "Needs attention"], ["go", "GO"], ["nogo", "NO GO / holds"], ["hi", "GO & high PM"], ["usable", "Usable"]] as [string, string][]).filter(([k]) => k === f || n(k));
  if (f.startsWith("s:")) chips.push([f, f.slice(2)]);
  const fName = f === "all" ? "All cars" : f === "att" ? "Cars needing attention" : f.startsWith("s:") ? "Cars in status " + f.slice(2) : GRP[f as G][0] + " cars";
  const why = new Map<string, { n: number; g: G }>(); inYard.forEach(r => { if (r.g === "go") return; const v = why.get(r.c.stat) || { n: 0, g: r.g }; v.n++; why.set(r.c.stat, v); });
  const wl = [...why].sort((a, b) => b[1].n - a[1].n), max = wl.length ? wl[0][1].n : 1, tot = wl.reduce((t, [, v]) => t + v.n, 0);

  const Kpi = ({ label, value, sub, w, color, onClick }: { label: string; value: string | number; sub: string; w: number; color: string; onClick?: () => void }) => {
    const Tag = onClick ? "button" : "div";
    return (
      <Tag type={onClick ? "button" : undefined} onClick={onClick} className={`${card} p-[18px_18px_14px] text-left ${onClick ? "cursor-pointer transition hover:-translate-y-0.5" : ""}`}>
        <span className="flex items-center gap-2 font-medium text-slate-600 dark:text-slate-400"><i className="size-2.5 rounded-[3px]" style={{ background: color }} />{label}</span>
        <b className="mt-1 block text-[24px] font-bold text-navy dark:text-ink-dark">{value}</b>
        <small className="block text-[12px] text-slate-500">{sub}</small>
        <div className="mt-2.5 h-1 rounded-full bg-slate-200 dark:bg-chip-dark"><i className="block h-full rounded-full" style={{ width: `${w}%`, background: color }} /></div>
      </Tag>);
  };
  const th = (k: keyof typeof KEY, t: string) => (
    <th scope="col" aria-sort={sort.k === k ? (sort.dir > 0 ? "ascending" : "descending") : "none"} className="sticky top-0 bg-slate-50 px-3 py-2 text-left font-semibold text-slate-600 dark:bg-chip-dark dark:text-slate-300">
      <button type="button" className={`inline-flex items-center gap-1 ${sort.k === k ? "text-bart-700 dark:text-[#7fd3f3]" : ""}`} onClick={() => setSort(sort.k === k ? { k, dir: sort.dir === 1 ? -1 : 1 } : { k, dir: 1 })}>
        {t}{sort.k === k && <span aria-hidden>{sort.dir > 0 ? "▲" : "▼"}</span>}</button></th>);

  return (
    <div className="animate-wv-in px-2 pb-6 pt-6">
      <section className="relative flex items-center justify-between gap-6 overflow-hidden rounded-3xl bg-[radial-gradient(600px_300px_at_0%_0%,rgba(0,153,216,.95),transparent_65%),radial-gradient(500px_300px_at_100%_100%,rgba(0,119,179,.9),transparent_62%),radial-gradient(420px_260px_at_85%_0%,rgba(31,111,178,.85),transparent_60%),linear-gradient(135deg,#0b2545,#0d3a66_55%,#0f172a)] px-[38px] py-[34px] text-white shadow-[0_30px_60px_-34px_rgba(11,37,69,.7)]">
        <span className="absolute -top-10 right-[22%] size-[150px] rounded-full border border-white/25 bg-gradient-to-br from-white/25 to-white/0 shadow-[inset_0_1px_0_rgba(255,255,255,.4)]" />
        <span className="absolute bottom-3.5 right-[34%] size-14 rounded-full border border-white/25 bg-gradient-to-br from-white/25 to-white/0" />
        <div className="relative">
          <span className="text-[13px] font-medium text-white/90">{s.group}</span>
          <h2 className="mb-2 mt-3.5 text-[24px] leading-tight font-extrabold tracking-tight">{hello}, {s.user}</h2>
          <p className="text-[13px] text-white/80">Signed on {today} · maps open in <b>{s.update ? "Update" : "Inquiry"}</b> mode · server plmars01v</p>
        </div>
        <img src="/bart-logo.png" alt="BART" className="relative h-[46px] flex-none rounded-lg shadow-[0_10px_24px_-10px_rgba(0,0,0,.5)]" />
      </section>

      <div className="mt-6 grid grid-cols-2 gap-3.5 md:grid-cols-3 xl:grid-cols-5">
        <Kpi label="Cars tracked" value={D.total} sub={`in ${D.yards.filter(y => y.cars).length} yards and shops · show all`} w={100} color="#8fc0dd" onClick={() => { setFilter("all"); setYard(null); }} />
        <Kpi label="Ready (GO)" value={D.all.go} sub={`${pct(D.all.go)}% of the fleet`} w={pct(D.all.go)} color={GRP.go[1]} onClick={() => setFilter("go")} />
        <Kpi label="Needs attention" value={att} sub={`${pct(att)}% · NO GO, holds and high PM`} w={pct(att)} color={GRP.nogo[1]} onClick={() => setFilter("att")} />
        {capKnown
          ? <Kpi label="Track space used" value={`${occ}%`} sub={`${D.total} of ${D.slots} car spaces`} w={occ} color="#8fc0dd" />
          : <Kpi label="Tracks in use" value={D.tracks} sub="track capacity is not sent by MARIS" w={100} color="#8fc0dd" />}
        {capKnown
          ? <Kpi label="Fullest yard" value={`${busy.occ}%`} sub={`${busy.y.name} · show its cars`} w={busy.occ} color="#b3bde6" onClick={() => setYard(yard === busy.code ? null : busy.code)} />
          : <Kpi label="Busiest yard" value={busy.cars} sub={`cars · ${busy.y.name} · show them`} w={pct(busy.cars)} color="#b3bde6" onClick={() => setYard(yard === busy.code ? null : busy.code)} />}
      </div>

      <h3 className="mx-1 mb-3.5 mt-[30px] text-[16px] font-bold text-navy dark:text-ink-dark">Yards and shops <small className="ml-2 text-[13px] font-normal text-slate-500">click a card to list its cars below · the buttons open its maps</small></h3>
      <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
        {D.yards.map(Y => {
          const out = Y.cnt.nogo + Y.cnt.hi, picked = yard === Y.code;
          const b = "inline-flex h-[30px] flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white/80 text-[12px] font-medium text-bart-700 hover:border-bart hover:bg-sky dark:border-line-dark dark:bg-chip-dark dark:text-[#7fd3f3]";
          return (
            <div key={Y.code} onClick={() => setYard(picked ? null : Y.code)}
              className={`${card} cursor-pointer p-3 transition hover:-translate-y-0.5 ${picked ? "ring-2 ring-bart/60" : ""}`}>
              <div className="flex items-start justify-between"><div><button type="button" aria-pressed={picked} title="List this yard's cars below" onClick={e => { e.stopPropagation(); setYard(picked ? null : Y.code); }}
                className="block font-bold text-navy hover:text-bart-700 dark:text-ink-dark">{Y.y.name}</button><em className="mt-1.5 inline-block rounded-md bg-sky px-1.5 py-0.5 text-[12px] font-bold not-italic text-bart-700 dark:bg-bart/18 dark:text-[#7fd3f3]">{Y.code}</em></div>
                {capKnown
                  ? <Ring p={Y.occ} color={Y.occ >= 60 ? "#e2bd8c" : "#8fc0dd"} tip={`Track space used: ${Y.cars} cars on ${Y.slots} car spaces (${Y.occ}%)`} />
                  : <Ring p={pct(Y.cars)} color="#8fc0dd" tip={`${Y.cars} cars, ${pct(Y.cars)}% of the cars tracked`} />}</div>
              <div className="my-2 flex items-center gap-3 text-slate-600 dark:text-slate-400"><span><b className="text-[16px] text-navy dark:text-ink-dark">{Y.cars}</b> cars</span>
                <span className="flex items-center gap-1.5 before:size-1.5 before:rounded-full before:bg-[#8cc4a5]">{Y.cnt.go} GO</span>
                {out > 0 && <span className="flex items-center gap-1.5 before:size-1.5 before:rounded-full before:bg-[#e4a3a3]">{out} out</span>}</div>
              <StackBar cnt={Y.cnt} n={Y.cars} />
              <div className="mt-3 flex gap-1.5" onClick={e => e.stopPropagation()}>
                <button type="button" className={b} onClick={() => openMap(Y.code, false)}><Icon name="map" size={13} />Yard map</button>
                {Y.y.shop && <button type="button" className={b} onClick={() => openMap(Y.code, true)}><Icon name="wrench" size={13} />Shop map</button>}
                <button type="button" className={b} onClick={() => app.go({ kind: "screen", key: "web:inv", label: "Yard Track Inventory", yard: Y.code })}><Icon name="list" size={13} />Inventory</button>
              </div>
            </div>);
        })}
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)]">
        <section className={`${card} flex max-h-[640px] flex-col p-4`}>
          <div className="flex flex-wrap items-center justify-between gap-3 px-1">
            <h3 className="text-[16px] font-bold text-navy dark:text-ink-dark">{fName}{yard ? " – " + yName(yard) : ""} <small className="ml-1 text-[13px] font-normal text-slate-500">{rows.length} {rows.length === 1 ? "car" : "cars"}</small></h3>
            <label className="flex h-9 w-[260px] items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-slate-400 focus-within:border-bart dark:border-line-dark dark:bg-panel-dark">
              <Icon name="search" size={14} /><input value={q} onChange={e => setQ(e.target.value)} placeholder="Filter by car, status, track…" spellCheck={false} aria-label="Filter cars" className="min-w-0 flex-1 bg-transparent text-slate-900 outline-none dark:text-ink-dark" /></label>
          </div>
          <div className="my-3 flex flex-wrap gap-2 px-1">
            {chips.map(([k, t]) => {
              const cnt = k.startsWith("s:") ? inYard.filter(r => r.c.stat === k.slice(2)).length : n(k);
              return <button key={k} type="button" aria-pressed={k === f} onClick={() => setFilter(k)} className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 font-medium ${k === f ? "border-bart bg-sky text-bart-700 dark:bg-bart/18 dark:text-[#7fd3f3]" : "border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-line-dark dark:text-slate-300 dark:hover:bg-chip-dark"}`}>{t}<i className="rounded-full bg-slate-100 px-1.5 text-[12px] not-italic dark:bg-chip-dark">{cnt}</i></button>;
            })}
            {yard && <button type="button" onClick={() => setYard(null)} className="inline-flex items-center gap-1.5 rounded-full border border-bart/40 px-3 py-1 font-medium text-bart-700 dark:text-[#7fd3f3]">{yName(yard)}<Icon name="x" size={11} /></button>}
          </div>
          <div className="scroll-thin min-h-0 flex-1 overflow-auto rounded-xl border border-slate-200 dark:border-line-dark">
            <table className="w-full border-collapse text-[13px]">
              <thead><tr>{th("id", "Car")}{th("sev", "Status")}{th("code", "Yard")}{th("track", "Track")}{th("home", "Home")}{th("from", "From")}<th className="sticky top-0 bg-slate-50 dark:bg-chip-dark"><span className="sr-only">Open</span></th></tr></thead>
              <tbody>{rows.map((r, i) => (
                <tr key={i} onClick={() => openMap(r.code, false)} title={`Open the ${yName(r.code)} map`} className="cursor-pointer border-t border-slate-100 hover:bg-sky/60 dark:border-line-dark dark:hover:bg-bart/10">
                  <td className="px-3 py-1.5 font-bold">{r.id}</td>
                  <td className="px-3 py-1.5"><span className="rounded-full px-2 py-0.5 text-[12px] font-semibold text-slate-800" style={{ background: GRP[r.g][1] + "66" }}>{r.c.stat}</span></td>
                  <td className="px-3 py-1.5">{r.code}</td><td className="px-3 py-1.5">{r.track}</td><td className="px-3 py-1.5">{r.c.home}</td><td className="px-3 py-1.5">{r.c.from}</td>
                  <td className="px-2 text-sky-2"><Icon name="chev" size={13} /></td></tr>))}</tbody>
            </table>
            {!rows.length && <p className="p-6 text-center text-slate-500">No cars match.</p>}
          </div>
        </section>
        <section className={`${card} self-start p-4`}>
          <h3 className="mb-3 px-1 text-[16px] font-bold text-navy dark:text-ink-dark">Why cars are out <small className="ml-1 text-[13px] font-normal text-slate-500">{tot} cars{yard ? " in " + yName(yard) : ""}</small></h3>
          {wl.length ? wl.map(([st, v]) => (
            <button key={st} type="button" aria-pressed={f === "s:" + st} onClick={() => setFilter(f === "s:" + st ? "att" : "s:" + st)}
              className={`grid w-full grid-cols-[90px_1fr_36px] items-center gap-3 rounded-lg px-1 py-2 text-left font-semibold hover:bg-sky/60 dark:hover:bg-bart/10 ${f === "s:" + st ? "bg-sky dark:bg-bart/18" : ""}`}>
              <span>{st}</span><div className="h-2 rounded-full bg-slate-100 dark:bg-chip-dark"><i className="block h-full rounded-full" style={{ width: `${Math.max(4, 100 * v.n / max)}%`, background: GRP[v.g][1] }} /></div><b className="text-right">{v.n}</b>
            </button>)) : <p className="p-4 text-slate-500">Every car here is GO.</p>}
          <h3 className="mb-2 mt-5 px-1 text-[16px] font-bold text-navy dark:text-ink-dark">Status by yard</h3>
          {D.yards.map(Y => (
            <button key={Y.code} type="button" title={Y.y.name} aria-pressed={yard === Y.code} onClick={() => setYard(yard === Y.code ? null : Y.code)}
              className={`grid w-full grid-cols-[36px_1fr_40px] items-center gap-3 rounded-lg px-1 py-1.5 text-left hover:bg-sky/60 dark:hover:bg-bart/10 ${yard === Y.code ? "bg-sky dark:bg-bart/18" : ""}`}>
              <span className="font-semibold">{Y.code}</span><StackBar cnt={Y.cnt} n={Y.cars} /><b className="text-right">{Y.cars}</b>
            </button>))}
        </section>
      </div>
    </div>
  );
}

const KEY = { id: (r: Row) => r.id, sev: (r: Row) => SEV[r.g] + r.c.stat, code: (r: Row) => r.code, track: (r: Row) => String(r.track), home: (r: Row) => r.c.home || "", from: (r: Row) => r.c.from || "" };
