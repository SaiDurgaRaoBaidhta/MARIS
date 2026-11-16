/* The 3D yard / shop map in React: the old viewer's 3D stage (three.js r128), built from the map's own drawing as before.
 * The drawing is drawn off screen from the map model (planSource), the 3D module reads its geometry, and a click on a car
 * or a track tag in 3D comes back here as onCarClick / onTrackClick. */
import { useEffect, useRef } from "react";
import type { MapCar, MapModel } from "../types";
import { createM3D, type M3DStage } from "./m3dLegacy";
import { drawPlanSource } from "./planSource";
import "./map3d.css";
import "./planSource.css";

/** a car row as the 3D module reads the yard data: [car text, home, from train, status, high PM, orientation] or 0 */
type Row = [string, string, string, string, number, string] | 0;
type Track3D = { id: string; desc: string; cap: number; cars: Row[] };

const models = new Map<string, MapModel>();        // the open map's model per yard: the 3D module reads cars through it
const asRow = (c: MapCar | null): Row => c ? [c.text, c.home || "", "", c.status || "GO", /^(HIGH|HPM)$/.test(c.status || "") ? 1 : 0,
  (c.text.match(/^[({<$v]|[)}>V]$/) || [""])[0]] : 0;

let m3d: ReturnType<typeof createM3D> | null = null;
let toastFn: ((msg: string) => void) | undefined;
function engine() {
  if (!m3d) m3d = createM3D({
    track: (y: string, t: string): Track3D | null => {
      const m = models.get(y); const cars = m?.cells[t];
      return cars ? { id: t, desc: t, cap: cars.length, cars: cars.map(asRow) } : null;
    },
    toast: (msg: string) => toastFn?.(msg),
  });
  return m3d;
}

export interface Map3DProps {
  model: MapModel;
  /** a car clicked in 3D (or "Track detail" on a shop car's panel) */
  onCarClick?: (car: MapCar) => void;
  /** a track tag / label clicked in 3D: the drawing's own target (data-open), e.g. "T-24" */
  onTrackClick?: (track: string) => void;
  /** a message the 3D stage wants shown (e.g. it could not lay a track) */
  onMessage?: (msg: string) => void;
  /** the update map's colours (GO / USABLE / NO GO) instead of the home yard's */
  update?: boolean;
  className?: string;
}

export function Map3D({ model, onCarClick, onTrackClick, onMessage, update = false, className }: Map3DProps) {
  const host = useRef<HTMLDivElement>(null);
  const cb = useRef({ onCarClick, onTrackClick });
  cb.current = { onCarClick, onTrackClick };
  toastFn = onMessage;

  useEffect(() => {
    const el = host.current; if (!el) return;
    const M3D = engine();
    models.set(model.yard, model);
    // the old viewer's map card around the drawing: .mvx-view > .mv.is3d > .mv-body > .fit (the drawing, hidden)
    const card = document.createElement("div"); card.className = "mv is3d"; card.style.cssText = "position:relative;width:100%;height:100%";
    const ttl = document.createElement("div"); ttl.className = "mv-ttl"; ttl.hidden = true;
    const b = document.createElement("b"); b.textContent = model.spec.id; ttl.appendChild(b); card.appendChild(ttl);
    const body = document.createElement("div"); body.className = "mv-body"; body.style.cssText = "position:relative;width:100%;height:100%"; card.appendChild(body);
    el.appendChild(card);
    const plan = drawPlanSource(model.spec, model, update);
    body.appendChild(plan.f);
    plan.f.mv = { card, root: plan.root, tracks: [], cells: [...plan.root.querySelectorAll(".cell")], trk: 0 };
    const view = document.createElement("div"); view.dataset.id = (model.shop ? "shop:" : "yardmap:") + model.yard;
    // a click the 3D stage hands to the drawing (car, track tag) comes back to the page
    const click = (e: Event) => {
      const t = e.target as Element | null; if (!t) return;
      const cellEl = t.closest(".cell");
      const car = cellEl ? plan.carOf(cellEl) : null;
      if (car) { cb.current.onCarClick?.(car); return; }
      const open = (t.closest("[data-open]") as HTMLElement | null)?.dataset.open || (cellEl as HTMLElement | null)?.dataset.track;
      if (open) cb.current.onTrackClick?.(open);
    };
    plan.root.addEventListener("click", click);
    let st: M3DStage | null = null;
    try {
      st = M3D.build(plan.f, { ...model.spec, acts: {}, blob: { w: plan.canvas.offsetWidth, h: plan.canvas.offsetHeight } }, plan.root, view);
    } catch (err) {
      console.warn("MARIS 3D map:", err);
      onMessage?.("The 3D view could not be built for this map.");
    }
    const size = () => { if (st) st.size(el.clientWidth, el.clientHeight); };
    size();
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(size) : null;
    ro?.observe(el);
    return () => {
      ro?.disconnect();
      plan.root.removeEventListener("click", click);
      try { st?.dispose(); } catch { /* already gone */ }
      card.remove();
      if (models.get(model.yard) === model) models.delete(model.yard);
    };
  }, [model, update]);   // eslint-disable-line react-hooks/exhaustive-deps -- rebuilt when the map or its data changes

  return <div ref={host} className={"mvx-view m3d-host " + (className || "")} style={{ position: "relative", width: "100%", height: "100%", minHeight: 360 }} />;
}

export default Map3D;
