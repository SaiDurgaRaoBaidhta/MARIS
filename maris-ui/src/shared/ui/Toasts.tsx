export type Toast = { id: number; msg: string; kind: "info" | "ok" | "err" };

/** message cards at the top right (the screens' message lines show here) */
export function Toasts({ toasts }: { toasts: Toast[] }) {
  return (
    <div className="no-print pointer-events-none fixed right-6 top-20 z-[9000] flex w-[360px] max-w-[calc(100vw-32px)] flex-col gap-2" aria-live="polite">
        {toasts.map(t => (
          <div key={t.id} role="status"
            className={`animate-toast-in pointer-events-auto rounded-xl border bg-white/95 px-4 py-3 text-[13px] text-slate-800 shadow-[0_18px_40px_-20px_rgba(11,37,69,.5)] backdrop-blur dark:bg-panel-dark/95 dark:text-ink-dark
              ${t.kind === "err" ? "border-red-200 dark:border-red-900" : t.kind === "ok" ? "border-emerald-200 dark:border-emerald-900" : "border-sky-2 dark:border-line-dark"}`}>
            <span className={`mr-2 inline-block size-2 rounded-full ${t.kind === "err" ? "bg-red-500" : t.kind === "ok" ? "bg-emerald-500" : "bg-bart"}`} />
            {t.msg}
          </div>
        ))}
      </div>
  );
}
