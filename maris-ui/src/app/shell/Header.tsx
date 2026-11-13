import { useEffect, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import { Icon } from "@/shared/ui/Icon";
import { QUICK } from "./quick";

const pad = (n: number) => String(n).padStart(2, "0");
const stamp = (d: Date) => `${pad(d.getMonth() + 1)}-${pad(d.getDate())}-${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

const chip = "grid size-[38px] place-items-center rounded-[11px] bg-slate-100/85 text-slate-700 shadow-[inset_0_0_0_1px_#e2e8f0] hover:bg-sky dark:bg-chip-dark dark:text-slate-300 dark:shadow-[inset_0_0_0_1px_#22364b]";

export function Header({ onToggleNav }: { onToggleNav: () => void }) {
  const app = useApp();
  const [now, setNow] = useState(() => new Date());
  useEffect(() => { const t = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(t); }, []);
  const s = app.session!;
  return (
    <header className="no-print fixed inset-x-0 top-0 z-[5000] flex h-16 items-center gap-6 bg-white/70 px-7 text-navy shadow-[0_1px_0_rgba(226,232,240,.9),0_12px_30px_-20px_rgba(11,37,69,.35)] backdrop-blur-xl backdrop-saturate-150 after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-gradient-to-r after:from-bart after:via-teal after:to-bart-700 after:opacity-85 dark:bg-[#0a1522]/85 dark:text-ink-dark dark:shadow-[0_1px_0_#1b2c3f]">
      <button type="button" className={chip} onClick={onToggleNav} aria-label="Collapse or expand the side menu" title="Collapse / expand the menu"><Icon name="menu" size={18} /></button>
      <button type="button" className="flex min-w-0 items-center gap-3 text-left" onClick={() => app.go({ kind: "home" })}>
        <span className="grid size-[38px] place-items-center rounded-xl bg-gradient-to-br from-bart via-[#1f6fb2] to-teal text-[16px] font-bold text-white shadow-[0_6px_14px_-6px_rgba(0,153,216,.7)]">M</span>
        <span className="min-w-0"><b className="block text-[16px] tracking-[3px]">MARIS</b><small className="block truncate text-[12px] text-slate-500 dark:text-slate-400">Maintenance &amp; Reliability Information System</small></span>
      </button>
      <div className="ml-auto flex items-center gap-3.5 text-slate-700 dark:text-slate-300">
        <button type="button" className={chip} onClick={app.toggleTheme} aria-label="Switch between light and dark theme" title="Light / dark theme"><Icon name={app.theme === "dark" ? "sun" : "moon2"} size={17} /></button>
        <button type="button" className={`relative ${chip}`} title="NOGO notifications" aria-label="NOGO notifications, 1 new" onClick={() => app.go(QUICK[3].page)}>
          <span className="text-[16px]">🔔</span><i className="absolute -right-1 -top-1 grid size-[18px] place-items-center rounded-full bg-red-600 text-[11px] font-bold not-italic text-white shadow-[0_0_0_2px_#fff] dark:shadow-[0_0_0_2px_#0a1522]">1</i>
        </button>
        <div className="hidden items-center gap-2.5 rounded-full bg-white/70 py-1 pl-1 pr-3 shadow-[inset_0_0_0_1px_#e2e8f0] md:flex dark:bg-chip-dark dark:shadow-[inset_0_0_0_1px_#22364b]">
          <span className="grid size-8 place-items-center rounded-full bg-gradient-to-br from-bart via-[#1f6fb2] to-teal text-[13px] font-bold text-white shadow-[0_4px_12px_rgba(0,153,216,.35)]">{s.user.slice(0, 2)}</span>
          <span><b className="block text-[13px] leading-tight font-semibold">{s.user}</b><small className="block text-[12px] leading-tight text-slate-500 dark:text-slate-400">{s.group} · <em className="font-semibold not-italic text-bart-700 dark:text-[#7fd3f3]">{s.update ? "Update" : "Inquiry"}</em></small></span>
        </div>
        <span className="hidden text-[13px] text-slate-500 tabular-nums xl:inline dark:text-slate-400">{stamp(now)}</span>
        <button type="button" onClick={app.signOff} className="h-[38px] rounded-[11px] bg-gradient-to-b from-red-500 to-red-700 px-4 font-semibold text-white shadow-[0_6px_14px_-6px_rgba(200,16,46,.7)] hover:brightness-110">Sign Off</button>
      </div>
    </header>
  );
}
