/* The drawn yard and shop maps: each map as the old viewer showed it (data/maps/<YARD>[-shop].json, captured from the
 * viewer: its track lines, tags, captions, legend and car boxes with their exact places and sizes) and its drawing spec
 * (data/specs, the viewer's MAPS / SHOPS, which the 3D view builds from). Loaded per map, so they stay out of the main bundle. */
import { cellKey, type DrawnCells } from "./model";
import type { MapSpec, YardCode } from "./types";

/** one element of a drawing, as the viewer had it inside its canvas */
export interface DrawEl {
  svg?: string;                          // the drawing's SVG (track lines, curves, the scanned line art)
  tag?: string;
  cls?: string;
  style?: string;                        // inline style as the viewer set it (position, size, fitted font size)
  html?: string;                         // inner HTML of captions, tags, legend, links
  data?: Record<string, string>;        // data-* (open: the track a tag opens; yard / track / idx: a car box)
  cell?: boolean;                        // a car box
  title?: string;
}

export interface Drawing {
  title: string;                         // "Hayward Yard Map"
  code: string;                          // "VHYT07I"
  kind: string;                          // "Inquiry"
  canvasClass: string;                   // "canvas inq yardmap" / "canvas inq shop"
  canvasStyle: string;                   // "width: 1873px; height: 796px;"
  mvwinStyle: string;                    // "transform: translate(27px, 52px);" (the crop to the drawing's content)
  bxStyle: string;                       // "width: 1910px; height: 850px; ..." (the cropped size the map is fitted from)
  acts: string[];
  links: string[];
  exit: string;
  legend: [string, string][];
  legendHead: string;
  els: DrawEl[];
}

const drawings = import.meta.glob<Drawing>("./data/maps/*.json", { import: "default" });
const specs = import.meta.glob<MapSpec>("./data/specs/*.json", { import: "default" });

export const mapKey = (yard: YardCode, shop: boolean) => yard + (shop ? "-shop" : "");
export const hasMap = (yard: YardCode, shop: boolean) => !!drawings[`./data/maps/${mapKey(yard, shop)}.json`];

export async function loadDrawing(yard: YardCode, shop: boolean): Promise<Drawing | null> {
  const f = drawings[`./data/maps/${mapKey(yard, shop)}.json`];
  return f ? f() : null;
}

export async function loadSpec(yard: YardCode, shop: boolean): Promise<MapSpec | null> {
  const f = specs[`./data/specs/${mapKey(yard, shop)}.json`];
  return f ? f() : null;
}

/** the car boxes a drawing has */
export function drawnCells(d: Drawing | null): DrawnCells {
  const s: DrawnCells = new Set();
  for (const e of d?.els ?? []) if (e.cell && e.data?.track != null) s.add(cellKey(e.data.track, +(e.data.idx ?? 0)));
  return s;
}

/** "width: 12px; left: 3px" -> React style object (vendor-free, !important dropped: these are inline styles) */
export function styleOf(css: string | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of (css ?? "").split(";")) {
    const i = part.indexOf(":");
    if (i < 0) continue;
    const k = part.slice(0, i).trim(), v = part.slice(i + 1).replace(/!important/g, "").trim();
    if (!k) continue;
    out[k.startsWith("--") ? k : k.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())] = v;
  }
  return out;
}

/** a px size from an inline style ("width: 1910px" -> 1910) */
export function pxOf(css: string, prop: string): number {
  const m = new RegExp("(?:^|;)\\s*" + prop + "\\s*:\\s*([\\d.]+)px").exec(css);
  return m ? +m[1] : 0;
}
