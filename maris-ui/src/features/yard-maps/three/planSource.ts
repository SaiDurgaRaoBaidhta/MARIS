/* The 2D drawing the 3D stage is built from, drawn off screen exactly as the old viewer's drawMap drew it (cells, track
 * tags, captions, the SVG track lines), with every cell filled from the map model. The 3D module reads its geometry from
 * this DOM (positions, sizes, line paths), so the 3D yard comes out the same as in the old viewer. */
import type { MapCar, MapModel, MapSpec } from "../types";

const SVGNS = "http://www.w3.org/2000/svg";
const BLUE1 = "#3f8ad8";
const HOME_YARDS = ["CY", "DY", "RY", "HY"];
const px = (n: number) => n + "px";

type Style = Partial<CSSStyleDeclaration> | null | undefined;
function el(tag: string, cls?: string | null, style?: Style, text?: string | null): HTMLElement {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (style) Object.assign(e.style, style);
  if (text != null) e.textContent = text;
  return e;
}

/** the old viewer's carClass: home-yard colour on the inquiry maps, GO / USABLE / NO GO on the update maps of DP and PB */
function carClass(c: MapCar, upd: boolean, yard: string): string {
  if (upd || yard === "DP" || yard === "PB") {
    if (c.status === "GO") return "c-GO";
    if (c.status === "USABLE") return "c-USE";
    return "c-NOGO";
  }
  return "c-" + (HOME_YARDS.includes(c.home) ? c.home : "XX");
}

export interface PlanSource {
  /** the frame the 3D module works with (f), holding root > .mvwin > .canvas */
  f: HTMLElement & { mv?: unknown; x3d?: unknown; mvCrop?: unknown };
  root: HTMLElement;
  canvas: HTMLElement;
  /** the car behind a cell of the drawing */
  carOf: (cell: Element) => MapCar | null;
}

type Item = unknown[];
type Opts = { color?: string; w?: number; fill?: string; dash?: string; arrow?: boolean };

export function drawPlanSource(spec: MapSpec, model: MapModel, update = false): PlanSource {
  const s = spec.s ?? 1, ox = spec.x1, oy = spec.y1, code = model.yard, kind = model.shop ? "shop" : "yard";
  const X = (v: number) => Math.round((v - ox) * s), Y = (v: number) => Math.round((v - oy) * s), S = (v: number) => Math.round(v * s);
  const f = el("div", "fit m3d-src") as PlanSource["f"];
  const root = el("div", "mv-root");
  const win = el("div", "mvwin");
  const cv = el("div", "canvas " + (update ? "upd" : "inq"), { position: "relative", width: px(Math.round((spec.x2 - spec.x1) * s)), height: px(Math.round((spec.y2 - spec.y1) * s)) });
  win.appendChild(cv); root.appendChild(win); f.appendChild(root);
  cv.classList.add(kind === "shop" || spec.font === "shop" ? "shop" : "yardmap");
  if (spec.small) cv.classList.add("small");
  if (spec.tiny) cv.classList.add("tiny");

  const svg = document.createElementNS(SVGNS, "svg");
  svg.setAttribute("width", cv.style.width);
  svg.setAttribute("height", cv.style.height);
  svg.setAttribute("class", "map-svg" + (spec.authored ? " authored" : "") + (spec.bmpArt ? " bmp-art" : ""));
  const mid = "ah-m3d-" + code + (model.shop ? "s" : "y");
  svg.innerHTML = '<defs><marker id="' + mid + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">' +
    '<path d="M0,0 L10,5 L0,10 z" fill="#333"/></marker></defs>';
  const g = document.createElementNS(SVGNS, "g");
  g.setAttribute("transform", "scale(" + s + ") translate(" + (-ox) + " " + (-oy) + ")");
  svg.appendChild(g);
  cv.appendChild(svg);
  const railed = spec.rails || (kind === "shop" && spec.rails !== false);
  const gR = railed ? document.createElementNS(SVGNS, "g") : null, gRi = railed ? document.createElementNS(SVGNS, "g") : null;
  if (gR && gRi) { g.appendChild(gR); g.appendChild(gRi); }

  function shape(tag: string, attrs: Record<string, string | number>, o?: Opts) {
    o = o || {};
    if (gR && gRi && !o.arrow && !o.dash && o.color === BLUE1) {
      const gap = (spec.ch ?? 0) * .72, lw = (o.w || 1.4) / s;
      ([[gR, gap + lw, o.color, "trk"], [gRi, Math.max(.5, gap - lw), null, "trk-in"]] as const).forEach(([grp, wd, col, cls]) => {
        const e = document.createElementNS(SVGNS, tag);
        Object.keys(attrs).forEach(k => e.setAttribute(k, String(attrs[k])));
        e.setAttribute("fill", "none"); e.setAttribute("stroke-width", String(wd)); e.setAttribute("class", cls);
        if (col) e.setAttribute("stroke", col);
        grp.appendChild(e);
      });
      return;
    }
    const e = document.createElementNS(SVGNS, tag);
    Object.keys(attrs).forEach(k => e.setAttribute(k, String(attrs[k])));
    e.setAttribute("stroke", o.color || "#222");
    e.setAttribute("stroke-width", String(o.w || 1.4));
    e.setAttribute("vector-effect", "non-scaling-stroke");
    e.setAttribute("fill", o.fill || "none");
    if (o.dash) e.setAttribute("stroke-dasharray", o.dash);
    if (o.arrow) e.setAttribute("marker-end", "url(#" + mid + ")");
    g.appendChild(e);
  }
  function put(e: HTMLElement, x: number, y: number) { e.style.position = "absolute"; e.style.left = px(X(x)); e.style.top = px(Y(y)); cv.appendChild(e); return e; }

  const cars = new WeakMap<Element, MapCar>();
  function cell(track: string, idx: number, x: number, y: number, cw?: number) {
    const c = el("div", "cell", { width: px(S(cw || spec.cw || 0)), height: px(S(spec.ch || 0)) });
    if (spec.dash) c.dataset.dash = "1";
    put(c, x, y);
    const car = model.cells[track]?.[idx] ?? null;
    c.textContent = car ? car.text : (c.dataset.dash ? "-" : "");
    if (car) {
      c.classList.add(carClass(car, update, code));
      c.dataset.st = car.status;                // the real status (the old viewer seeded demo statuses here)
      c.dataset.pm = "0";
      cars.set(c, car);
    }
    c.dataset.yard = code; c.dataset.track = track; c.dataset.idx = String(idx);
    return c;
  }

  const step = (spec.step as number) || 62.4;
  (spec.items || []).forEach(raw => {
    const it = raw as Item, t = it[0] as string;
    const n = (i: number) => it[i] as number, str = (i: number) => it[i] as string;
    if (t === "row" || t === "col") {
      const [, track, x, y, cnt, st, cw, from] = it as [string, string, number, number, number, number?, number?, number?];
      const d = st || step;
      for (let i = 0; i < cnt; i++) cell(track, (from || 0) + i, t === "row" ? x + i * d : x, t === "col" ? y + i * d : y, cw || (st ? Math.min(spec.cw || 0, d - 1.5) : spec.cw));
    } else if (t === "cell") {
      cell(str(1), n(2), n(3), n(4), it[5] as number | undefined);
    } else if (t === "cells") {
      (it[2] as [number, number][]).forEach(([x, y], i) => cell(str(1), ((it[3] as number) || 0) + i, x, y, it[4] as number | undefined));
    } else if (t === "lbl") {
      const e = el("div", "tlabel", { width: px(S(n(4))), height: px(S(spec.lblH || spec.ch || 0)) }, str(1));
      if (it[5]) e.dataset.open = str(5); else e.classList.add("plain");
      put(e, n(2), n(3));
    } else if (t === "box") {
      put(el("div", "mbox", { width: px(S(n(4))), height: px(S(spec.lblH || spec.ch || 0)) }, str(1)), n(2), n(3));
    } else if (t === "tag") {
      const e = el("div", "tag", null, str(1));
      if (it[4]) e.dataset.open = str(4);
      if (/LEAD/.test(str(1))) e.style.transform = "rotate(32deg)";
      put(e, n(2), n(3));
    } else if (t === "text") {
      const st = it[4];
      const e = el("div", typeof st === "string" ? "mtxt " + st : "mtxt", typeof st === "object" ? st as Style : null);
      e.innerHTML = str(1);
      if (st && typeof st === "object" && (st as { textAlign?: string }).textAlign === "center") e.style.transform = "translateX(-50%)";
      put(e, n(2), n(3));
    } else if (t === "title") {
      const e = el("div", "map-title", { fontSize: px(n(4) || 21), transform: "translateX(-50%)", textAlign: "center" });
      if (it[5]) e.style.fontFamily = str(5);
      e.innerHTML = str(1);
      put(e, n(2), n(3));
    } else if (t === "link") {
      const e = el("div", "ln"); e.innerHTML = str(1); put(e, n(2), n(3));
    } else if (t === "legend") {
      const e = el("div", "legend-box legend-card", { lineHeight: px(n(3)), fontSize: px(n(4)) });
      const sz = it[5] as { w: number; h: number } | undefined;
      if (sz) Object.assign(e.style, { width: px(S(sz.w)), height: "auto", minHeight: px(S(sz.h)), padding: "8px 12px" });
      e.innerHTML = code === "DP" || code === "PB"
        ? '<div class="cc">Car Color:</div><div class="go">GO</div><div class="pm">GO &amp; High PM Hours</div><div class="us">USABLE</div><div class="ng">NOGO, TI, PM, etc.</div>'
        : '<div class="hd">Car Home Yard</div><div class="cy">Concord</div><div class="dy">Daly City</div><div class="ry">Richmond</div><div class="hy">Hayward</div>';
      put(e, n(1), n(2));
    } else if (t === "turn") {
      const e = el("div", "cell turn", { width: px(S(n(3))), height: px(S(spec.ch || 0)) });
      e.dataset.open = "T-95"; e.title = "Pseudo Track";
      put(e, n(1) - n(3) / 2, n(2) - (spec.ch || 0) / 2);
    } else if (t === "line") {
      shape("line", { x1: n(1), y1: n(2), x2: n(3), y2: n(4) }, it[5] as Opts);
    } else if (t === "poly") {
      shape("polyline", { points: (it[1] as number[][]).map(p => p.join(",")).join(" ") }, it[2] as Opts);
    } else if (t === "curve") {
      const c = it[1] as number[];
      shape("path", { d: "M" + c[0] + "," + c[1] + " C" + c[2] + "," + c[3] + " " + c[4] + "," + c[5] + " " + c[6] + "," + c[7] }, it[2] as Opts);
    } else if (t === "path") {
      const o = it[2] as Opts | undefined;
      const attrs: Record<string, string> = { d: str(1) };
      if (o && o.color === "#3f8ad5") attrs["data-rail"] = "1";   // one rail of the original screen's drawing (3D only)
      shape("path", attrs, o);
    } else if (t === "rect") {
      shape("rect", { x: n(1), y: n(2), width: n(3), height: n(4) }, it[5] as Opts);
    } else if (t === "image") {
      const im = document.createElementNS(SVGNS, "image"); im.setAttribute("href", str(1)); im.setAttribute("x", String(n(2))); im.setAttribute("y", String(n(3)));
      im.setAttribute("width", String(n(4))); im.setAttribute("height", String(n(5))); im.setAttribute("class", "map-lines"); im.setAttribute("preserveAspectRatio", "none"); g.insertBefore(im, g.firstChild);
    } else if (t === "wash") {
      const c = cv.querySelector('[data-track="' + str(1) + '"][data-idx="' + n(2) + '"]');
      if (c) c.classList.add("wash");
    }
  });
  return { f, root, canvas: cv, carOf: e => cars.get(e) ?? null };
}
