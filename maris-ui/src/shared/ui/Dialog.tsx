import { useEffect, useRef } from "react";

/** Information (OK) or Yes / No question, centred over a dimmed page */
export function Dialog({ title, msg, ask, close }: { title: string; msg: string; ask: boolean; close: (ok: boolean) => void }) {
  const ref = useRef<HTMLButtonElement>(null);
  useEffect(() => { ref.current?.focus(); }, []);
  return (
    <div className="fixed inset-0 z-[9500] grid place-items-center bg-slate-900/40 p-4 backdrop-blur-[2px]" onClick={e => { if (e.target === e.currentTarget) close(false); }}
      onKeyDown={e => { if (e.key === "Escape") close(false); }}>
      <div role="alertdialog" aria-modal="true" aria-labelledby="dlg-t" className="w-[420px] max-w-full rounded-2xl bg-white p-6 shadow-2xl dark:bg-panel-dark">
        <h2 id="dlg-t" className="mb-2 text-base font-bold text-navy dark:text-ink-dark">{title}</h2>
        <p className="mb-5 text-[13px] leading-relaxed text-slate-700 dark:text-slate-300">{msg}</p>
        <div className="flex justify-end gap-2">
          {ask && <button className="h-9 rounded-lg border border-slate-300 px-4 font-semibold text-slate-700 hover:bg-slate-50 dark:border-line-dark dark:text-slate-200 dark:hover:bg-chip-dark" onClick={() => close(false)}>No</button>}
          <button ref={ref} className="h-9 rounded-lg bg-bart px-5 font-semibold text-white hover:bg-bart-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-bart" onClick={() => close(true)}>{ask ? "Yes" : "OK"}</button>
        </div>
      </div>
    </div>
  );
}
