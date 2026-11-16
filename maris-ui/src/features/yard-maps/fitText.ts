/* The old map viewer's word sizing for a drawn map (its mapText). */
/** the old viewer's word sizing (mapText): words in a box fit inside it, car boxes and track tags share one size (the size
 * three in four of them can take), captions keep near their drawn size; sized for the scale the map is shown at */
let measure: CanvasRenderingContext2D | null | undefined;
function ctx() {
  if (measure === undefined) {
    // jsdom (the tests) has no canvas: words keep their drawn size there
    try { measure = typeof navigator !== "undefined" && /jsdom/i.test(navigator.userAgent) ? null : document.createElement("canvas").getContext("2d"); } catch { measure = null; }
  }
  return measure;
}
export function fitText(cv: HTMLElement, z: number) {
  const measure = ctx();
  if (!measure) return;
  const els = [...cv.querySelectorAll<HTMLElement>(".cell, .tlabel, .tag, .mbox, .mtxt:not(.gothic)")]
    .filter(e => e.offsetWidth && (e.textContent ?? "").trim() && !e.closest(".legend-box"));
  if (!els.length || !z) return;
  els.forEach(e => {
    const cs = getComputedStyle(e), boxed = !e.matches(".mtxt, .tag");
    if (!e.dataset.f0) e.dataset.f0 = String(parseFloat(cs.fontSize));
    const T = (e.matches(".mtxt") ? 11 : 12) / z;
    measure.font = cs.fontWeight + " 10px " + cs.fontFamily;
    const lines = e.innerHTML.split(/<br\s*\/?>/i).map(t => t.replace(/<[^>]+>/g, "").replace(/&amp;/g, "&").trim());
    const w10 = Math.max(...lines.map(t => measure.measureText(t).width)) || 1;
    let f = T;
    if (boxed) { const room = e.clientWidth - 8; if (room > 0) f = Math.min(f, room / w10 * 10); f = Math.min(f, (e.clientHeight - 2) / (lines.length * 1.15)); }
    else f = Math.min(f, +e.dataset.f0 * (e.matches(".tag") ? 1.25 : 1.6));
    e.style.setProperty("font-size", (boxed ? f : Math.max(7 / z, f)).toFixed(2) + "px", "important");
    if (boxed) { e.style.paddingLeft = e.style.paddingRight = "0"; e.style.justifyContent = "center"; e.style.textAlign = "center"; }
  });
  const boxes = els.filter(e => !e.matches(".mtxt, .tag"));
  if (boxes.length > 3) {
    const bs = boxes.map(e => parseFloat(e.style.fontSize)).sort((a, b) => a - b), q = bs[Math.floor(bs.length / 4)];
    boxes.forEach(e => { const own = parseFloat(e.style.fontSize); e.style.setProperty("font-size", Math.min(q, own).toFixed(2) + "px", "important"); });
  }
}

