import type { ReactNode } from "react";
import { pageLabel, useApp } from "@/shared/lib/app-state";
import { Icon } from "./Icon";

/** Path, title, screen code and the page actions (Back, zoom). */
export function PageHead({ code, ver, title, zoom, children }: { code?: string; ver?: string; title: string; zoom?: { value: number; set: (z: number) => void }; children?: ReactNode }) {
  const app = useApp();
  const btn = "inline-flex h-[38px] items-center gap-2 rounded-xl border border-white/90 bg-white/70 px-3.5 font-semibold text-slate-700 shadow-[0_0_0_1px_rgba(169,219,241,.45),0_8px_18px_-12px_rgba(11,37,69,.35)] backdrop-blur hover:-translate-y-px hover:bg-sky/95 hover:text-bart-700 dark:border-line-dark dark:bg-chip-dark dark:text-slate-200";
  return (
    <div className="no-print flex min-h-[92px] items-center justify-between gap-5 px-2 pt-2.5">
      <div className="min-w-0">
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 overflow-hidden whitespace-nowrap text-slate-500">
          {app.stack.map((p, i) => {
            const last = i === app.stack.length - 1;
            const label = <>{i === 0 && <Icon name="home" size={13} />}{pageLabel(p)}</>;
            return <span key={i} className="inline-flex items-center gap-1.5">
              {i > 0 && <span className="text-sky-2 dark:text-line-dark">/</span>}
              {last ? <b className="inline-flex items-center gap-1.5 font-semibold text-slate-700 dark:text-slate-200">{label}</b>
                : <a className="inline-flex cursor-pointer items-center gap-1.5 font-medium text-bart hover:text-bart-700 hover:underline" onClick={() => app.backTo(i)}>{label}</a>}
            </span>;
          })}
        </nav>
        <div className="mt-2 flex min-w-0 items-center gap-2.5">
          <h1 className="truncate text-[24px] font-bold tracking-tight text-navy dark:text-ink-dark">{title}</h1>
          {code && <span className="flex-none rounded-lg bg-gradient-to-br from-bart/14 to-teal/10 px-2.5 py-1.5 text-[13px] leading-none font-semibold text-bart-700 shadow-[inset_0_0_0_1px_rgba(0,153,216,.22)] dark:text-[#7fd3f3]">{code}</span>}
          {ver && <span className="flex-none rounded-lg bg-white/70 px-2.5 py-1.5 text-[13px] leading-none font-medium text-slate-500 shadow-[inset_0_0_0_1px_#e2e8f0] dark:bg-chip-dark dark:shadow-[inset_0_0_0_1px_#22364b]">{ver}</span>}
        </div>
      </div>
      <div className="flex flex-none items-center gap-2.5">
        {children}
        {app.stack.length > 1 && <button type="button" className={btn} onClick={app.back} title="Back to the previous page"><Icon name="back" />Back</button>}
        {zoom && (
          <div className={`${btn} gap-0.5 px-1 hover:translate-y-0`} title="Zoom">
            <button type="button" aria-label="Zoom out" className="size-[30px] rounded-lg text-[16px] font-bold hover:bg-sky hover:text-bart-700 dark:hover:bg-bart/14" onClick={() => zoom.set(Math.max(.5, zoom.value - .1))}>&minus;</button>
            <button type="button" className="min-w-[46px] text-center tabular-nums" onClick={() => zoom.set(1)} title="Normal size">{Math.round(zoom.value * 100)}%</button>
            <button type="button" aria-label="Zoom in" className="size-[30px] rounded-lg text-[16px] font-bold hover:bg-sky hover:text-bart-700 dark:hover:bg-bart/14" onClick={() => zoom.set(Math.min(2, zoom.value + .1))}>+</button>
          </div>)}
      </div>
    </div>
  );
}
