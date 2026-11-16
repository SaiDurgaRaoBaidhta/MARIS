/* Moving cars on a track (old VHYT11I), changing a car's orientation (VHYT13I) and the yard car list hard copy (VHYT24I), on
 * the clean API. A window opened from another page gets the old pass values in its key
 * (egl:VHYT11I?yrdTrkgYrd=..&yrdSubLoc=..&toYrdSubLoc=..). */
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import {
  changeOrientation, fetchMoveTrack, fetchOrientation, fetchYardPrint, moveAction, printYard,
  type MoveAction, type MoveTrack, type Orientation, type PrintOptions,
} from "./moveApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** an old screen colour name as CSS: "SKY BLUE" -> skyblue, X11 "GREY80" -> 80 % grey */
function cssColour(name: string): string | undefined {
  if (!name) return undefined;
  const grey = /^GRE[YA](\d{1,3})$/i.exec(name.replace(/\s+/g, ""));
  if (grey) { const v = Math.round(Number(grey[1]) * 2.55); return `rgb(${v}, ${v}, ${v})`; }
  return name.replace(/\s+/g, "").toLowerCase();
}

/** the pass values in the key of the page in front (egl:CODE?field=value) */
function useLinkParams(): URLSearchParams {
  const { stack } = useApp();
  const top = stack[stack.length - 1];
  const key = top?.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : "");
}

function Note({ text, kind = "info" }: { text: string; kind?: "info" | "error" }) {
  if (!text.trim()) return null;
  return <p role={kind === "error" ? "alert" : "status"} className={`mt-3 rounded-lg px-3 py-2 ${kind === "error" ? "bg-red-50 text-red-800" : "bg-emerald-50 text-emerald-800"}`}>{text}</p>;
}

/** old VHYT11I: click one car or the two ends of a range, then Move (to the "Move to" track), Reverse, Pseudo MT ... */
export function CarMovePage() {
  const app = useApp();
  const p = useLinkParams();
  const [yard, setYard] = useState(p.get("yrdTrkgYrd") ?? "");
  const [track, setTrack] = useState(p.get("yrdSubLoc") ?? p.get("fromYrdSubLoc") ?? "");
  const linked = !!(p.get("yrdTrkgYrd") && (p.get("yrdSubLoc") || p.get("fromYrdSubLoc")));
  const [t, setT] = useState<MoveTrack | null>(null);
  const [page, setPage] = useState(1);
  const [clicked, setClicked] = useState<number[]>([]);
  const [to, setTo] = useState(p.get("toYrdSubLoc") ?? "");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const fetchPage = useCallback(async (y: string, tr: string, dest: string, n: number) => {
    setBusy(true); setError(""); setNote(""); setClicked([]);
    try { const r = await fetchMoveTrack(y, tr, dest, n); setT(r); setPage(r.page); setTo(r.toSubLocation); setNote(r.message); }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }, []);
  const load = (n: number) => fetchPage(yard, track, to, n);
  const link = p.toString();
  useEffect(() => {                                                    // opened with the old pass values: show that track
    if (!linked) return;
    const y = p.get("yrdTrkgYrd") ?? "", tr = p.get("yrdSubLoc") ?? p.get("fromYrdSubLoc") ?? "", dest = p.get("toYrdSubLoc") ?? "";
    setYard(y); setTrack(tr); setTo(dest);
    void fetchPage(y, tr, dest, 1);
  }, [link]); // eslint-disable-line react-hooks/exhaustive-deps

  const act = async (action: MoveAction, confirm?: boolean) => {
    setBusy(true); setError(""); setNote("");
    try {
      const r = await moveAction(yard, track, to, page, clicked, action, confirm);
      if (r.question) {
        setBusy(false);
        await act(action, await app.confirm(r.question));
        return;
      }
      setT(r.track); setClicked([]); setNote(r.message);
      if (r.opens) {
        const q = new URLSearchParams(Object.entries(r.with).map(([k, v]) => [k, String(v)]));
        app.open({ kind: "screen", key: `egl:${r.opens}?${q}`, label: r.opens });
      }
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const toggle = (slot: number) => setClicked(c => (c.includes(slot) ? c.filter(x => x !== slot) : [...c, slot]));

  return (
    <>
      <PageHead title="Car Move" code="VHYT11I" />
      {!linked && (
        <section aria-label="Choose a track" className={panel}>
          <div className="flex flex-wrap items-end gap-3">
            <label className={label}>Yard<input className={field} maxLength={4} value={yard} onChange={e => setYard(e.target.value.toUpperCase())} /></label>
            <label className={label}>Track<input className={field} maxLength={10} value={track} onChange={e => setTrack(e.target.value.toUpperCase())} /></label>
            <button type="button" className={primary} disabled={!yard || !track} onClick={() => load(1)}>Display</button>
          </div>
        </section>
      )}
      {busy && !t && <p className="mx-2 text-slate-500">Loading the track from MARIS…</p>}
      <Note text={error} kind="error" />
      {t && (
        <section aria-label={`Track ${t.subLocation}`} className={panel}>
          <h2 className="mb-3 font-semibold">{t.yard} – {t.yardDescription} · {t.subLocation} – {t.subLocationDescription}</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-slate-500">
                <th>Position</th><th>Car</th><th>Status</th><th>Home yard</th><th>{t.pseudoCaption}</th>
              </tr></thead>
              <tbody>
                {t.positions.map(x => (
                  <tr key={x.slot}>
                    <td><button type="button" aria-pressed={clicked.includes(x.slot)} disabled={!x.enabled} className={secondary}
                      onClick={() => toggle(x.slot)} aria-label={`Position ${x.position || x.slot}`}>{x.position || x.slot}</button></td>
                    <td className="font-mono" style={{ backgroundColor: cssColour(x.color) }}>{x.carShown}</td>
                    <td style={{ color: cssColour(x.statusColor) }}>{x.status}</td>
                    <td>{x.homeYard}</td>
                    <td>{x.pseudoTrain}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-wrap items-end gap-2">
            <button type="button" className={secondary} disabled={!t.previous || busy} onClick={() => load(page - 1)}>Prv</button>
            <button type="button" className={secondary} disabled={!t.next || busy} onClick={() => load(page + 1)}>Nxt</button>
            <label className={label}>Move to
              <select className={field} value={to} onChange={e => setTo(e.target.value)}>
                <option value="" />
                {t.moveTo.map(l => <option key={l.code} value={l.code}>{l.code} {l.description}</option>)}
              </select>
            </label>
            <button type="button" className={primary} disabled={busy} onClick={() => act("move")}>Move</button>
            <button type="button" className={secondary} disabled={busy} onClick={() => act("reverse")}>Reverse</button>
            <button type="button" className={secondary} disabled={busy || !t.canPseudoTrain} onClick={() => act("pseudo-train")}>Pseudo MT</button>
            <button type="button" className={secondary} disabled={busy} onClick={() => act("fix-order")}>Fix Order</button>
            <button type="button" className={secondary} disabled={busy} onClick={() => act("orientation")}>Orientation</button>
            <button type="button" className={secondary} disabled={busy || !t.canMoveYard} onClick={() => act("move-yard")}>Mve Yrd</button>
            <button type="button" className={secondary} disabled={busy} onClick={() => act("print")}>Print</button>
            <button type="button" className={secondary} disabled={busy} onClick={() => load(page)}>Clear</button>
          </div>
          <Note text={note} />
        </section>
      )}
    </>
  );
}

/** old VHYT13I: a car's two orientation marks; choose the other one and Change */
export function OrientationPage() {
  const p = useLinkParams();
  const yard = p.get("yrdTrkgYrd") ?? "", track = p.get("yrdSubLoc") ?? "";
  const [car, setCar] = useState("");
  const [o, setO] = useState<Orientation | null>(null);
  const [mark, setMark] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async (c: string) => {
    setBusy(true); setError("");
    try { const r = await fetchOrientation(yard, track, c); setO(r); setMark(r.current); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }, [yard, track]);
  useEffect(() => { if (yard && track) { const c = p.get("carNo") ?? ""; setCar(c); void load(c); } }, [yard, track]); // eslint-disable-line react-hooks/exhaustive-deps
  const change = async () => {
    setBusy(true); setError("");
    try { const r = await changeOrientation(yard, track, o?.car ?? car, mark); setO(r); setMark(r.current); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const pick = (shown: string) => { const c = shown.replace(/[^0-9A-Za-z]/g, ""); setCar(c); void load(c); };

  return (
    <>
      <PageHead title="Change Orientation" code="VHYT13I" />
      {!yard && <p className="mx-2 text-slate-500">Open this window from the car move window.</p>}
      <Note text={error} kind="error" />
      {o && (
        <section aria-label="Orientation" className={panel}>
          <div className="flex flex-wrap items-end gap-3">
            <label className={label}>Car
              <select className={field} value={o.cars.find(c => c.includes(o.car)) ?? ""} onChange={e => pick(e.target.value)}>
                {o.cars.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
            <label className={label}>Orientation
              <select className={field} value={mark} onChange={e => setMark(e.target.value)}>
                {o.orientations.map(m => <option key={m} value={m}>{m}</option>)}
              </select>
            </label>
            <button type="button" className={primary} disabled={busy || mark === o.current} onClick={change}>Change</button>
          </div>
          <Note text={o.message} />
        </section>
      )}
    </>
  );
}

/** old VHYT24I: the yard's car list on a MARIS printer */
export function YardPrintPage() {
  const p = useLinkParams();
  const yard = p.get("yrdTrkgYrd") ?? "";
  const [o, setO] = useState<PrintOptions | null>(null);
  const [printer, setPrinter] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!yard) return;
    fetchYardPrint(yard).then(setO).catch(e => setError(message(e)));
  }, [yard]);
  const print = async () => {
    setError(""); setNote("");
    try { await printYard(yard, printer); setNote(`Sent to ${printer}.`); } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Yard Car List" code="VHYT24I" />
      {!yard && <p className="mx-2 text-slate-500">Open this window from a yard map.</p>}
      <Note text={error} kind="error" />
      {o && (
        <section aria-label="Hard copy" className={panel}>
          <p className="mb-3">Yard: <strong>{o.yard}</strong></p>
          <ul className="mb-3">
            {o.printers.map(x => (
              <li key={x.name}><label><input type="radio" name="printer" value={x.name} checked={printer === x.name} onChange={() => setPrinter(x.name)} /> {x.name} {x.location}</label></li>
            ))}
          </ul>
          <button type="button" className={primary} disabled={!printer} onClick={print}>Hard Copy</button>
          <Note text={note} />
        </section>
      )}
    </>
  );
}
