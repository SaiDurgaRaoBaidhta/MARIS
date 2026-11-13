import { useApp } from "@/shared/lib/app-state";
import { QUICK } from "./quick";

export function Footer() {
  const app = useApp();
  const link = "font-medium text-bart-700 underline underline-offset-2 hover:text-bart dark:text-[#4fb8ea]";
  return (
    <footer className="no-print fixed inset-x-0 bottom-0 z-[4600] flex h-10 items-center justify-center gap-8 border-t border-slate-200 bg-white/90 text-[13px] text-slate-800 backdrop-blur dark:border-[#1b2c3f] dark:bg-[#0a1522]/95 dark:text-slate-300">
      <span><b className="font-semibold">Server:</b> plmars01v</span>
      <span><b className="font-semibold">DB:</b> BRMRSP</span>
      <span className="hidden items-center gap-1.5 md:flex"><b className="mr-1 font-semibold">Yard/TMZ:</b>
        {["CY", "DY", "HY", "RY", "L30", "W40", "S50", "C80"].map(y => (
          <button key={y} type="button" className="rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[12px] font-semibold text-bart-700 hover:bg-sky dark:border-line-dark dark:bg-chip-dark dark:text-[#7fd3f3]"
            onClick={() => /^[A-Z]Y$/.test(y) ? app.go({ kind: "map", yard: y, shop: false, label: y + " Yard Map" }) : app.toast(`${y} – TMZ status is not part of this prototype.`)}>{y}</button>))}
      </span>
      <a className={link} onClick={() => app.go(QUICK[1].page)}>Maris Print</a>
      <a className={link} onClick={() => app.go(QUICK[2].page)}>ColdFusion Report</a>
      <a className={link} onClick={() => app.toast("Maris FAQ is not available in this prototype.")}>Maris FAQ</a>
    </footer>
  );
}
