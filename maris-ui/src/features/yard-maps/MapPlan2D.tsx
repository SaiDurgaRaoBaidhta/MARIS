/* The 2D map: the old viewer's drawing (its SVG track lines, tags, captions, legend, rails and car boxes, at their exact
 * places) with every box filled from MARIS. The old viewer's own styles (drawing.css) make it look the same. */
import { memo, useLayoutEffect, useRef, type CSSProperties } from "react";
import { fitText } from "./fitText";
import { carClass } from "./model";
import type { DrawEl, Drawing } from "./drawing";
import type { MapCar, MapModel } from "./types";
import "./drawing.css";

/** an element with the viewer's inline style kept exactly (cssText keeps its !important sizes) */
function Raw({ tag = "div", cls, css, html, data, title, onClick, label }: {
  tag?: string; cls?: string; css?: string; html?: string; data?: Record<string, string>; title?: string; onClick?: () => void; label?: string;
}) {
  const ref = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    const e = ref.current; if (!e) return;
    e.style.cssText = css ?? "";
    if (html != null) e.innerHTML = html;
  }, [css, html]);
  const Tag = tag as "div";
  const dataProps = Object.fromEntries(Object.entries(data ?? {}).map(([k, v]) => ["data-" + k, v]));
  return <Tag ref={ref as React.Ref<HTMLDivElement>} className={cls} title={title} onClick={onClick} {...dataProps}
              {...(onClick ? { role: "button", tabIndex: 0, "aria-label": label, onKeyDown: (ev: React.KeyboardEvent) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); onClick(); } } } : {})} />;
}

/** the drawing's SVG (track lines, the scanned line art): static, set once */
const Svg = memo(function Svg({ svg }: { svg: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const host = ref.current; if (!host) return;
    host.innerHTML = svg;
    const e = host.firstElementChild;
    if (e) host.replaceWith(e);          // the svg sits in the canvas itself, as in the viewer
  }, [svg]);
  return <div ref={ref} />;
});

export interface Plan2DProps {
  drawing: Drawing;
  model: MapModel;
  /** a car box clicked: its car (null when empty) and the MARIS track / 1-based position it shows */
  onCar?(car: MapCar | null, track: string, position: number): void;
  /** a track tag clicked (its data-open), e.g. "T-24"; the PSEUDO TRACK tag opens "T-95" */
  onTrack?(track: string): void;
  /** the Yard Track Inventory / Car Move Request links */
  onLink?(which: "inventory" | "move"): void;
  /** boxes to highlight (a move being picked): drawn "track|idx" */
  selected?: Set<string>;
  /** the scale the map is drawn at (fit to the screen) */
  scale: number;
  style?: CSSProperties;
}

export function MapPlan2D({ drawing, model, onCar, onTrack, onLink, selected, scale, style }: Plan2DProps) {
  const els = drawing.els;
  return (
    <div className="fit" style={{ width: Math.round(bx(drawing).w * scale), height: Math.round(bx(drawing).h * scale), ...style }}>
      <div className="bxroot" style={{ width: bx(drawing).w, height: bx(drawing).h, overflow: "hidden", transform: `scale(${scale})` }}>
        <Canvas drawing={drawing} els={els} model={model} onCar={onCar} onTrack={onTrack} onLink={onLink} selected={selected} scale={scale} />
      </div>
    </div>
  );
}

function bx(d: Drawing) {
  const w = /width:\s*([\d.]+)px/.exec(d.bxStyle), h = /height:\s*([\d.]+)px/.exec(d.bxStyle);
  return { w: w ? +w[1] : 1200, h: h ? +h[1] : 700 };
}

function Canvas({ drawing, els, model, onCar, onTrack, onLink, selected, scale }: Omit<Plan2DProps, "style"> & { els: DrawEl[] }) {
  const win = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => { if (win.current) win.current.style.cssText = drawing.mvwinStyle; }, [drawing.mvwinStyle]);
  const canvas = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => { if (canvas.current) canvas.current.style.cssText = drawing.canvasStyle; }, [drawing.canvasStyle]);
  // size the words for the scale the map is shown at, every time its cars change (the viewer's mapText)
  useLayoutEffect(() => { if (canvas.current) fitText(canvas.current, scale); }, [model, scale, drawing]);
  return (
    <div className="mvwin" ref={win}>
      <div ref={canvas} className={drawing.canvasClass}>
        {els.map((e, i) => {
          if (e.svg) return <Svg key={i} svg={e.svg} />;
          if (e.cell) {
            const track = e.data?.track ?? "", idx = +(e.data?.idx ?? 0);
            const car = model.cells[track]?.[idx] ?? null;
            const cls = "cell" + (car ? " " + carClass(car, model.yard) : " mv-empty") + (selected?.has(track + "|" + idx) ? " ym-sel" : "");
            const text = car ? car.text : (e.data?.dash ? "-" : "");
            return <Cell key={i} cls={cls} css={e.style} text={text} data={e.data}
                         title={car ? `${car.car} · ${car.status}${car.consist ? " · consist " + car.consist : ""}` : undefined}
                         onClick={onCar ? () => onCar(car, car?.track ?? track, car?.position ?? idx + 1) : undefined} />;
          }
          const open = e.data?.open;
          const isLink = (e.cls ?? "").split(" ").includes("ln");
          const click = isLink && onLink ? () => onLink(/inventory/i.test(e.html ?? "") ? "inventory" : "move")
            : open && onTrack ? () => onTrack(open) : undefined;
          return <Raw key={i} tag={e.tag} cls={e.cls} css={e.style} html={e.html} data={e.data} title={e.title} onClick={click}
                      label={open ? `Track ${open}` : undefined} />;
        })}
      </div>
    </div>
  );
}

/** a car box: its text and colour change with the data, its place and size are the drawing's */
const Cell = memo(function Cell({ cls, css, text, data, title, onClick }: {
  cls: string; css?: string; text: string; data?: Record<string, string>; title?: string; onClick?: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => { if (ref.current) ref.current.style.cssText = css ?? ""; }, [css]);
  const dataProps = Object.fromEntries(Object.entries(data ?? {}).map(([k, v]) => ["data-" + k, v]));
  return <div ref={ref} className={cls} title={title} onClick={onClick} {...dataProps}>{text}</div>;
});
