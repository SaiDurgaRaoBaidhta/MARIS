import { useEffect, useRef, useState, type FormEvent } from "react";
import { useApp } from "@/shared/lib/app-state";
import { Icon } from "@/shared/ui/Icon";
import { signOn } from "./api";

/* the lit track: straight from the horizon to the bottom of the panel, sleepers running towards you */
const at = (d: number) => [720, 600 + 400 * d];
const edge = (d: number, k: number) => { const p = at(d), h = (4 + 150 * d) * k; return [p[0] + h, p[1]]; };
const rail = (k: number) => Array.from({ length: 81 }, (_, j) => { const q = edge(j / 80, k * .78); return (j ? "L" : "M") + q[0].toFixed(1) + " " + q[1].toFixed(1); }).join("");
const N = 30;

function TrackScene() {
  const g = useRef<SVGGElement>(null);
  useEffect(() => {
    const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
    let phase = 0, last = 0, raf = 0;
    const draw = (t: number) => {
      phase = reduce ? 0 : (phase + (last ? Math.min(50, t - last) : 0) / 1400) % 1; last = t;
      [...(g.current?.children || [])].forEach((r, i) => {
        const d = Math.pow((i + phase) / N, 2.6), a = edge(d, -1), b = edge(d, 1), th = .5 + 8 * d;
        r.setAttribute("points", [[a[0], a[1] - th], [b[0], b[1] - th], [b[0], b[1] + th], [a[0], a[1] + th]].map(q => q[0].toFixed(1) + "," + q[1].toFixed(1)).join(" "));
        r.setAttribute("opacity", (.06 + .45 * d).toFixed(3));
      });
      if (!reduce) raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, []);
  return (
    <svg className="absolute inset-0 size-full" viewBox="0 0 1000 1000" preserveAspectRatio="xMidYMax slice" aria-hidden="true">
      <defs>
        <radialGradient id="lgGlow" cx="72%" cy="60%" r="60%"><stop offset="0" stopColor="#0099d8" stopOpacity=".55" /><stop offset=".45" stopColor="#0b4f86" stopOpacity=".22" /><stop offset="1" stopColor="#061426" stopOpacity="0" /></radialGradient>
        <linearGradient id="lgRail" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stopColor="#cfe3f5" stopOpacity=".9" /><stop offset="1" stopColor="#7fd3f3" stopOpacity=".05" /></linearGradient>
        <linearGradient id="lgFade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#081b33" /><stop offset=".55" stopColor="#081b33" stopOpacity="0" /><stop offset=".9" stopColor="#081b33" stopOpacity="0" /><stop offset="1" stopColor="#081b33" stopOpacity=".85" /></linearGradient>
      </defs>
      <rect width="1000" height="1000" fill="url(#lgGlow)" />
      <g ref={g}>{Array.from({ length: N }, (_, i) => <polygon key={i} fill="#8fb4d8" />)}</g>
      <path d={rail(-1)} stroke="url(#lgRail)" strokeWidth="4" fill="none" />
      <path d={rail(1)} stroke="url(#lgRail)" strokeWidth="4" fill="none" />
      {/* <circle cx="720" cy="600" r="5" fill="#fff" /> */}
      <rect width="1000" height="1000" fill="url(#lgFade)" />
      <path d="M470 1000 L700 600 M970 1000 L740 600" stroke="url(#lgRail)" strokeWidth="2" fill="none" opacity=".45" />
    </svg>
  );
}

export function LoginPage() {
  const app = useApp();
  // a real MARIS group ID and password, checked by the middleware (MRS02A)
  const [user, setUser] = useState("");
  const [pw, setPw] = useState("");
  const [show, setShow] = useState(false);
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const pwRef = useRef<HTMLInputElement>(null);
  const userRef = useRef<HTMLInputElement>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (busy) return;
    if (!user.trim()) { setMsg("Enter your user ID."); userRef.current?.focus(); return; }
    if (!pw) { setMsg("Enter your password."); pwRef.current?.focus(); return; }
    setBusy(true); setMsg("");
    try { app.signOn(await signOn(user, pw)); }
    catch (err) { setMsg(err instanceof Error ? err.message : String(err)); }
    finally { setBusy(false); }
  };

  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      {/* left: BART navy with the track */}
      <section className="relative hidden flex-col justify-between overflow-hidden bg-[radial-gradient(900px_600px_at_10%_0%,#0d3a66,transparent_60%),linear-gradient(160deg,#0b2545,#081b33)] p-[44px_60px_40px] text-white lg:flex">
        <TrackScene />
        <div className="relative flex items-center gap-4">
          <img src="/bart-logo.png" alt="BART" className="h-10 rounded" />
          <span className="h-10 w-px bg-white/25" />
          <div><b className="block text-[16px] tracking-[4px]">MARIS</b><small className="text-[13px] text-white/80">Maintenance &amp; Reliability Information System</small></div>
        </div>
        <div className="relative max-w-[520px]">
          <div className="flex items-center gap-3 text-[12px] font-semibold uppercase tracking-[.22em] text-[#7fd3f3] before:h-0.5 before:w-7 before:bg-bart">Rolling stock &amp; shops · Fleet operations</div>
          <h1 className="mt-4 text-[24px] leading-tight font-extrabold tracking-tight">Keeping every BART car<br /><span className="text-[#4fc3f7]">ready for service.</span></h1>
          <p className="mt-5 text-[16px] leading-relaxed text-white/80">One place for the fleet's incidents, repairs, consists, yard and shop maps, and maintenance reports.</p>
          <ul className="mt-9 flex flex-wrap gap-3">
            {[["8", "Yards & shops", "tracked"], ["19", "Application areas", "on one sign-on"], ["3D", "Live yard and", "shop maps"]].map(([b, a, c]) => (
              <li key={b} className="flex items-center gap-3 rounded-xl border border-white/15 bg-white/[.06] px-4 py-3 backdrop-blur">
                <b className="text-[22px] font-extrabold">{b}</b><span className="text-[13px] leading-snug text-white/80">{a}<br />{c}</span>
              </li>))}
          </ul>
        </div>
        <div className="relative flex flex-col gap-1 text-[13px] text-white/75">
          <span className="flex items-center gap-2"><Icon name="lock" size={13} /> Authorized BART personnel only. Use of this system is monitored.</span>
          <span>Server plmars01v · DB BRMRSP</span>
        </div>
      </section>

      {/* right: the sign-on card */}
      <section className="relative flex flex-col items-center justify-center bg-[#f1f5f9] px-4 py-16 dark:bg-[#0a1522]">
        <button type="button" onClick={app.toggleTheme}
          className="absolute right-6 top-6 flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-[13px] font-semibold text-navy hover:bg-sky dark:border-line-dark dark:bg-chip-dark dark:text-ink-dark">
          <Icon name={app.theme === "dark" ? "sun" : "moon2"} size={17} />{app.theme === "dark" ? "Light theme" : "Dark theme"}
        </button>
        <form onSubmit={submit} autoComplete="off"
          className="relative w-[420px] max-w-full overflow-hidden rounded-[20px] border border-slate-200 bg-white px-9 pb-7 pt-10 shadow-[0_30px_60px_-30px_rgba(11,37,69,.35)] before:absolute before:inset-x-10 before:top-0 before:h-0.5 before:bg-gradient-to-r before:from-transparent before:via-bart/70 before:to-transparent dark:border-line-dark dark:bg-panel-dark">
          <h2 className="text-[24px] font-bold text-navy dark:text-ink-dark">Sign on</h2>
          <p className="mb-6 mt-1 text-slate-600 dark:text-slate-400">Use your MARIS user ID and password.</p>
          <label htmlFor="lg-user" className="mb-2 block font-semibold text-navy dark:text-ink-dark">User ID</label>
          <div className="mb-5 flex h-[46px] items-center gap-3 rounded-[10px] border border-slate-300 px-4 text-slate-500 focus-within:border-bart focus-within:ring-4 focus-within:ring-bart/15 dark:border-line-dark">
            <Icon name="user" />
            <input id="lg-user" ref={userRef} value={user} onChange={e => setUser(e.target.value.toUpperCase())} maxLength={8} autoFocus
              autoCapitalize="characters" spellCheck={false} className="h-full flex-1 bg-transparent text-[13px] uppercase text-slate-900 outline-none dark:text-ink-dark" />
          </div>
          <label htmlFor="lg-pw" className="mb-2 block font-semibold text-navy dark:text-ink-dark">Password</label>
          <div className="mb-4 flex h-[46px] items-center gap-3 rounded-[10px] border border-slate-300 px-4 text-slate-500 focus-within:border-bart focus-within:ring-4 focus-within:ring-bart/15 dark:border-line-dark">
            <Icon name="lock" />
            <input id="lg-pw" ref={pwRef} type={show ? "text" : "password"} value={pw} maxLength={6} onChange={e => setPw(e.target.value)} className="h-full flex-1 bg-transparent text-[13px] text-slate-900 outline-none dark:text-ink-dark" />
            <button type="button" onClick={() => { setShow(!show); pwRef.current?.focus(); }} className="font-semibold text-bart hover:text-bart-700">{show ? "Hide" : "Show"}</button>
          </div>
          <div role="alert" className="min-h-6 py-1 text-[13px] font-medium text-red-600">{msg}</div>
          <button type="submit" disabled={busy} className="flex h-12 w-full items-center justify-center gap-2 rounded-[10px] bg-bart text-[16px] font-semibold text-white hover:bg-bart-600 active:translate-y-px disabled:cursor-progress">
            {busy ? "Preparing your workspace…" : <>Sign On <Icon name="arrow" /></>}
          </button>
          <button type="button" onClick={() => { setPw(""); setMsg(""); pwRef.current?.focus(); }} className="mt-2.5 h-[42px] w-full rounded-[10px] border border-slate-300 font-semibold text-slate-700 hover:bg-slate-50 dark:border-line-dark dark:text-slate-200 dark:hover:bg-chip-dark">Clear</button>
          <p className="mt-6 text-center leading-relaxed text-slate-600 dark:text-slate-400">Need access or a password reset? Contact the MARIS administrator.</p>
        </form>
        <div className="absolute bottom-4 text-slate-500">MARIS · V.DEC.13.17 · Bay Area Rapid Transit</div>
      </section>
    </div>
  );
}
