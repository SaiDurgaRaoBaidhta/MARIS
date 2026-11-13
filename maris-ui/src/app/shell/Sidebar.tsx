import { useMemo, useState } from "react";
import type { MenuData, Section } from "@/features/menu/types";
import { useApp, type Page } from "@/shared/lib/app-state";
import { Icon } from "@/shared/ui/Icon";
import { SECTION_ICON } from "@/shared/ui/icons";
import { QUICK } from "./quick";

export function Sidebar({ menus, mini }: { menus: MenuData; mini: boolean }) {
  const app = useApp();
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const sections = useMemo(() => {
    const allowed = menus.access[app.session!.role] || [];
    return menus.sections.filter(s => allowed.includes(s.cap) && (s.kind === "menu" || s.kind === "screen"));
  }, [menus, app.session]);
  const top = app.stack[app.stack.length - 1];
  const curSection = app.stack.find(p => p.kind === "menu") as Extract<Page, { kind: "menu" }> | undefined;
  const ql = q.trim().toLowerCase();
  const openItem = (s: Section, label: string, key?: string) => {
    if (!key) return app.toast(`${label} – this screen is not in the migrated source code.`);
    app.go({ kind: "menu", section: s.cap }); app.open({ kind: "screen", key, label });   // the router turns map keys into maps
  };
  const row = "flex items-center gap-2.5 rounded-[11px] px-2.5 py-2 font-medium text-slate-700 hover:bg-bart/8 hover:text-bart-700 dark:text-slate-300 dark:hover:bg-bart/14 dark:hover:text-[#7fd3f3] cursor-pointer select-none";
  const on = "text-bart-700 bg-gradient-to-br from-bart/15 to-teal/10 shadow-[inset_0_0_0_1px_rgba(0,153,216,.2)] dark:text-[#7fd3f3] dark:bg-bart/18";
  return (
    <aside className={`no-print fixed bottom-10 left-0 top-16 z-[4500] flex flex-col border-r border-slate-200/90 bg-white/60 shadow-[10px_0_30px_-24px_rgba(11,37,69,.35)] backdrop-blur-xl transition-[width] duration-200 dark:border-[#1b2c3f] dark:bg-[#0a1522]/90 ${mini ? "w-[72px]" : "w-[276px]"}`}>
      {!mini && (
        <label className="mx-4 mb-1.5 mt-4 flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white/85 px-3 text-slate-400 focus-within:border-bart focus-within:text-bart focus-within:ring-4 focus-within:ring-bart/14 dark:border-line-dark dark:bg-panel-dark">
          <Icon name="search" size={15} />
          <input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder="Search functions…" aria-label="Search functions" className="min-w-0 flex-1 bg-transparent text-slate-900 outline-none dark:text-ink-dark" />
        </label>)}
      <nav className="scroll-thin flex-1 overflow-y-auto px-3 pb-5 pt-1.5">
        {!ql && <a className={`${row} mt-1.5 ${top.kind === "home" ? on : ""}`} onClick={() => app.go({ kind: "home" })} title="Home"><Icon name="home" className="flex-none text-slate-400" /><span className={mini ? "sr-only" : ""}>Home</span></a>}
        {!ql && !mini && <div className="mx-2.5 mb-1.5 mt-[18px] text-[12px] font-semibold uppercase tracking-[.09em] text-slate-400">Applications</div>}
        {sections.map(s => {
          const capHit = s.cap.toLowerCase().includes(ql);
          const groups = s.groups.map(g => ({ ...g, list: g.list.filter(it => !it.dis && (!ql || capHit || it.label.toLowerCase().includes(ql))) })).filter(g => g.list.length);
          if (ql && !capHit && !groups.length) return null;
          const isCur = curSection?.section === s.cap, isOpen = !mini && (ql ? groups.length > 0 : open[s.cap]);
          return (
            <div key={s.cap} title={s.cap}>
              <div className={`${row} ${isCur ? on : ""}`} onClick={() => app.go({ kind: "menu", section: s.cap })}>
                <span className={`grid size-[30px] flex-none place-items-center rounded-[9px] ${isCur ? "bg-gradient-to-br from-bart via-[#1f6fb2] to-teal text-white shadow-[0_6px_14px_-6px_rgba(0,153,216,.7)]" : "bg-gradient-to-br from-bart/14 to-teal/10 text-bart shadow-[inset_0_0_0_1px_rgba(0,153,216,.16)] dark:text-[#4fc3f7]"}`}><Icon name={SECTION_ICON[s.cap] || "file"} size={15} /></span>
                {!mini && <span className="min-w-0 flex-1 truncate">{s.cap}</span>}
                {!mini && s.kind === "menu" && (
                  <button type="button" aria-label={`${isOpen ? "Collapse" : "Expand"} ${s.cap}`} aria-expanded={isOpen}
                    onClick={e => { e.stopPropagation(); setOpen(o => ({ ...o, [s.cap]: !o[s.cap] })); }}
                    className={`grid flex-none text-slate-400 transition-transform ${isOpen ? "rotate-90 text-bart" : ""}`}><Icon name="chev" size={14} /></button>)}
              </div>
              {isOpen && (
                <div className="mb-2 ml-6 mt-0.5 border-l border-sky-2 pb-1 pl-3 pt-0.5 dark:border-line-dark">
                  {groups.map(g => (
                    <div key={g.name}>
                      <div className="mx-2 mb-0.5 mt-2.5 text-[12px] font-semibold uppercase tracking-[.07em] text-slate-400">{g.name}</div>
                      {g.list.map(it => {
                        const active = top.kind === "screen" && isCur && top.label === it.label;
                        return <a key={it.i} title={it.label} onClick={() => openItem(s, it.label, it.key)}
                          className={`block cursor-pointer truncate rounded-lg px-2.5 py-1.5 ${active ? "bg-gradient-to-br from-bart to-[#1f6fb2] text-white" : "text-slate-600 hover:bg-sky hover:text-bart-700 dark:text-slate-400 dark:hover:bg-bart/14 dark:hover:text-[#7fd3f3]"}`}>{it.label}</a>;
                      })}
                    </div>))}
                </div>)}
            </div>);
        })}
        {!ql && !mini && <div className="mx-2.5 mb-1.5 mt-[18px] text-[12px] font-semibold uppercase tracking-[.09em] text-slate-400">Quick access</div>}
        {!ql && QUICK.map(x => (
          <a key={x.label} title={x.label} onClick={() => app.go(x.page)}
            className={`${row} ${top.kind === "screen" && app.stack.length === 2 && top.label === x.label ? on : ""}`}>
            <Icon name={x.icon} size={15} className="flex-none text-slate-400" /><span className={mini ? "sr-only" : ""}>{x.label}</span>
          </a>))}
      </nav>
    </aside>
  );
}
