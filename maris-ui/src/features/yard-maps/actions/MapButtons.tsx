/* The map's buttons with their old captions; Alt + the old underlined letter presses one. */
import { useEffect } from "react";
import type { MapButton } from "./actions";

/** the map's buttons as plain buttons with their old captions; Alt + the old underlined letter presses one */
export function MapButtons({ buttons, className, buttonClassName }: { buttons: MapButton[]; className?: string; buttonClassName?: string }) {
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (!e.altKey || e.ctrlKey || e.metaKey) return;
      const b = buttons.find(x => x.key && x.key.toLowerCase() === e.key.toLowerCase() && !x.disabled);
      if (b) { e.preventDefault(); b.run(); }
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [buttons]);
  return (
    <div role="group" aria-label="Map buttons" className={className}>
      {buttons.map(b => (
        <button key={b.id} type="button" className={buttonClassName} disabled={b.disabled} onClick={() => b.run()}
                title={b.key ? `Alt+${b.key.toUpperCase()}` : undefined}>{b.label}</button>
      ))}
    </div>
  );
}
