// @ts-nocheck
/* The 3D yard and shop map, ported from the old map viewer (public/legacy/maris-map.html, script "maris-mapview-js":
 * the railway planner and the M3D module), close to line for line so it looks and behaves the same. It builds the 3D
 * stage from the 2D drawing's own DOM (cells, track tags, captions and SVG track lines), as the old viewer did.
 * What changed: three.js comes from npm (r128, the same version); the yard data comes from the React map model (env)
 * instead of the viewer's yard store; car statuses are the real ones (no demo statuses); cars are not moved locally. */
import * as THREE from "three";

export function createM3D(env) {
  const esc = s => String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const Z = () => 1;
  const store = { get: k => { try { return localStorage.getItem("maris.mv." + k); } catch (e) { return null; } }, set: (k, v) => { try { localStorage.setItem("maris.mv." + k, v); } catch (e) { /* ignore */ } } };
  const S = {};                        // no recorded screens here: statuses come from the yard data
  const ydTrack = (y, t) => env.track(y, t);
  const moveCarData = () => false;     // cars are moved in MARIS (the car move screens), never in the browser
  const toast = (msg, kind) => env.toast && env.toast(msg, kind);
  const parkSwitch = () => {};
  const MV_HOME = { HY: ["Hayward", "#2f9a3a"], CY: ["Concord", "#3552c8"], DY: ["Daly City", "#e2401d"], RY: ["Richmond", "#c4916a"] };
  const cellText = e => (e.tagName === "INPUT" ? e.value : e.textContent).trim();
  function mvCrop(f, root, s) {
    if (f.mvCrop) return f.mvCrop;
    const win = root.firstElementChild; root.style.transform = ""; win.style.transform = "";
    const base = root.getBoundingClientRect(); let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
    root.querySelectorAll(".cell, .tag, .tlabel, .mtxt, .k-inp, .k-btn, .k-lbl, .k-colhead, .mbox, .mv-rail, .map-btn, .ln, .legend-box, .statusbar, .map-title, .map-svg > g > *").forEach(e => {
      if (e.closest(".mv-x") && !e.closest(".map-btn, .ln, .legend-box")) return; const r = e.getBoundingClientRect(); if (!r.width && !r.height) return;
      x1 = Math.min(x1, r.left); y1 = Math.min(y1, r.top); x2 = Math.max(x2, r.right); y2 = Math.max(y2, r.bottom); });
    if (!isFinite(x1)) { x1 = base.left; y1 = base.top; x2 = base.left + s.blob.w; y2 = base.top + s.blob.h; }
    const P = 16, z = Z(), c = { x: Math.floor((x1 - base.left) / z - P), y: Math.floor((y1 - base.top) / z - P) };
    c.w = Math.ceil((x2 - base.left) / z + P - c.x); c.h = Math.ceil((y2 - base.top) / z + P - c.y);
    win.style.transform = `translate(${-c.x}px, ${-c.y}px)`;
    return f.mvCrop = c;
  }
  /* the Actions menu in a map header */
  function railTopology(lines, opt) {
    opt = Object.assign({ step: .4, join: .42, endTol: .45, turnCos: -.1, clear: 1.2, reverseCost: 3 }, opt || {});
    const N = [], lineNodes = [];
    lines.forEach((ln, li) => {
      const ids = [], p = ln.pts; let s = 0;
      for (let i = 0; i < p.length - 1; i++) {
        const [ax, az] = p[i], [bx, bz] = p[i + 1], L = Math.hypot(bx - ax, bz - az), n = Math.max(1, Math.ceil(L / opt.step));
        for (let k = i ? 1 : 0; k <= n; k++) { const t = k / n; N.push({ x: ax + (bx - ax) * t, z: az + (bz - az) * t, line: li, k: ids.length, s: s + L * t }); ids.push(N.length - 1); }
        s += L;
      }
      lineNodes.push(ids);
    });
    /* tangent at a node, pointing toward the end of its line */
    const tan = n => { const ids = lineNodes[N[n].line], k = N[n].k, a = N[ids[Math.max(0, k - 1)]], b = N[ids[Math.min(ids.length - 1, k + 1)]], L = Math.hypot(b.x - a.x, b.z - a.z) || 1; return [(b.x - a.x) / L, (b.z - a.z) / L]; };
    const isEnd = n => { const ids = lineNodes[N[n].line], L = N[ids[ids.length - 1]].s; return N[n].s <= opt.endTol || L - N[n].s <= opt.endTol; };
    const endSide = n => { const ids = lineNodes[N[n].line], L = N[ids[ids.length - 1]].s; return N[n].s <= opt.endTol ? -1 : L - N[n].s <= opt.endTol ? 1 : 0; };   // -1 start, +1 end
    /* close pairs of nodes on different lines */
    const G = new Map(), key = (x, z) => Math.floor(x / opt.join) + ":" + Math.floor(z / opt.join);
    N.forEach((n, i) => { const k = key(n.x, n.z); (G.get(k) || G.set(k, []).get(k)).push(i); });
    const trans = new Map(), crossings = [], turnouts = [], junctionNodes = new Set();
    const addT = (from, to, kind) => { (trans.get(from) || trans.set(from, []).get(from)).push([to, kind]); };
    const st = (n, d) => n * 2 + (d > 0 ? 1 : 0);                                 // state: node + direction of travel along its line
    const pairSeen = new Set();
    N.forEach((a, i) => {
      const gx = Math.floor(a.x / opt.join), gz = Math.floor(a.z / opt.join);
      for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) (G.get((gx + dx) + ":" + (gz + dz)) || []).forEach(j => {
        if (j <= i || N[j].line === a.line) return; const b = N[j]; if (Math.hypot(b.x - a.x, b.z - a.z) >= opt.join) return;
        const ea = endSide(i), eb = endSide(j);
        if (!ea && !eb) {                                                           // neither track ends here: a crossing (no transfer)
          const ck = Math.min(a.line, b.line) + "x" + Math.max(a.line, b.line) + "@" + Math.round(a.x * 2) + "," + Math.round(a.z * 2);
          if (!pairSeen.has(ck)) { pairSeen.add(ck); crossings.push({ a: a.line, b: b.line, x: (a.x + b.x) / 2, z: (a.z + b.z) / 2 }); }
          return;
        }
        /* a turnout: arriving on one track, leave on the other only carrying on forward */
        let used = false;
        [[i, j, ea, eb], [j, i, eb, ea]].forEach(([u, v, eu, ev]) => {
          const tu = tan(u), tv = tan(v);
          [-1, 1].forEach(du => {
            if (eu && du !== eu) return;                                            // arriving at the end of a track: travelling toward that end
            [-1, 1].forEach(dv => {
              if (ev && dv === ev) return;                                          // leaving from the end of a track: travelling into it
              const cos = (tu[0] * du) * (tv[0] * dv) + (tu[1] * du) * (tv[1] * dv);
              if (cos > opt.turnCos) { addT(st(u, du), st(v, dv), "turnout"); used = true; }
            });
          });
        });
        if (used) { junctionNodes.add(i); junctionNodes.add(j); turnouts.push({ a: a.line, b: b.line, x: (a.x + b.x) / 2, z: (a.z + b.z) / 2 }); }
      });
    });
    /* where a car may reverse: on a track, clear of every turnout on that track */
    const nearJ = new Uint8Array(N.length);
    junctionNodes.forEach(j => { const ids = lineNodes[N[j].line]; ids.forEach(k => { if (Math.abs(N[k].s - N[j].s) < opt.clear) nearJ[k] = 1; }); });
    return { N, lineNodes, lines, trans, crossings, turnouts, nearJ, opt, tan, isEnd };
  }
  function railFrom(T, starts, avoid) {                                     // avoid: Map node -> extra cost (to find a different route)
    const S = T.N.length * 2, dist = new Float64Array(S).fill(Infinity), prev = new Int32Array(S).fill(-1), how = new Uint8Array(S);
    const heap = [], push = (d, s) => { heap.push([d, s]); let i = heap.length - 1; while (i) { const p = (i - 1) >> 1; if (heap[p][0] <= heap[i][0]) break; [heap[p], heap[i]] = [heap[i], heap[p]]; i = p; } };
    const pop = () => { const top = heap[0], last = heap.pop(); if (heap.length) { heap[0] = last; let i = 0; for (;;) { const l = 2 * i + 1, r = l + 1; let m = i; if (l < heap.length && heap[l][0] < heap[m][0]) m = l; if (r < heap.length && heap[r][0] < heap[m][0]) m = r; if (m === i) break; [heap[m], heap[i]] = [heap[i], heap[m]]; i = m; } } return top; };
    starts.forEach(n => [0, 1].forEach(b => { const s = n * 2 + b; dist[s] = 0; push(0, s); }));
    const relax = (u, v, w, kind, d) => { if (d + w < dist[v]) { dist[v] = d + w; prev[v] = u; how[v] = kind; push(d + w, v); } };
    while (heap.length) {
      const [d, u] = pop(); if (d > dist[u]) continue;
      const n = u >> 1, dir = u & 1 ? 1 : -1, ids = T.lineNodes[T.N[n].line], k = T.N[n].k + dir;
      const extra = m => (avoid && avoid.get(m)) || 0;
      if (k >= 0 && k < ids.length) { const m = ids[k]; relax(u, m * 2 + (u & 1), Math.abs(T.N[m].s - T.N[n].s) + extra(m), 1, d); }          // along the track
      (T.trans.get(u) || []).forEach(([v]) => { const m = v >> 1; relax(u, v, Math.hypot(T.N[m].x - T.N[n].x, T.N[m].z - T.N[n].z) + extra(m), 2, d); });   // through a turnout
      if (!T.nearJ[n]) relax(u, u ^ 1, T.opt.reverseCost, 3, d);                                                                       // reverse, clear of switches
    }
    return { dist, prev, how };
  }
  function railPath(T, R, goal) {
    const a = goal * 2, b = a + 1, end = R.dist[a] <= R.dist[b] ? a : b;
    if (!isFinite(R.dist[end])) return null;
    const states = []; for (let u = end; u >= 0; u = R.prev[u]) states.push(u); states.reverse();
    const nodes = [], kinds = []; let reversals = 0, turnouts = 0, maxTurn = 0;
    states.forEach((u, i) => { const n = u >> 1; if (i && R.how[u] === 3) reversals++;
      if (i && R.how[u] === 2) { turnouts++; const p = states[i - 1], tp = T.tan(p >> 1), tv = T.tan(n), dp = p & 1 ? 1 : -1, dv = u & 1 ? 1 : -1;     // the turn taken through this switch
        maxTurn = Math.max(maxTurn, Math.acos(Math.max(-1, Math.min(1, tp[0] * dp * tv[0] * dv + tp[1] * dp * tv[1] * dv))) * 180 / Math.PI); }
      if (nodes[nodes.length - 1] !== n) { nodes.push(n); kinds.push(R.how[u]); } });
    /* crossings passed: a crossing point on a line this route runs along */
    const linesUsed = new Set(nodes.map(n => T.N[n].line)), crossings = T.crossings.filter(c => (linesUsed.has(c.a) || linesUsed.has(c.b)) &&
      nodes.some(n => Math.hypot(T.N[n].x - c.x, T.N[n].z - c.z) < T.opt.join && (T.N[n].line === c.a || T.N[n].line === c.b))).length;
    const names = []; nodes.forEach(n => { const nm = T.lines[T.N[n].line].name || "track"; if (names[names.length - 1] !== nm) names.push(nm); });
    return { nodes, pts: nodes.map(n => [T.N[n].x, T.N[n].z]), cost: R.dist[end], reversals, turnouts, crossings, via: names, maxTurn };
  }
  /* up to k genuinely different routes from start to goal, shortest first (each later one is found by making the
   * earlier routes' nodes expensive; it is kept only if it differs in at least a third of its track) */
  function railRoutes(T, starts, goal, k) {
    const out = [], avoid = new Map(), first = railPath(T, railFrom(T, starts), goal); if (!first) return out;
    out.push(first);
    for (let i = 1; i < (k || 3); i++) {
      out[out.length - 1].nodes.forEach(n => avoid.set(n, (avoid.get(n) || 0) + 6));
      const R = railFrom(T, starts, avoid), p = railPath(T, R, goal); if (!p) break;
      const seen = new Set(out.flatMap(q => q.nodes)), fresh = p.nodes.filter(n => !seen.has(n)).length;
      if (fresh < p.nodes.length / 3) continue;
      /* report the real length, without the penalties */
      p.cost = p.nodes.reduce((L, n, j) => j ? L + Math.hypot(T.N[n].x - T.N[p.nodes[j - 1]].x, T.N[n].z - T.N[p.nodes[j - 1]].z) : 0, 0) + p.reversals * T.opt.reverseCost;
      out.push(p);
    }
    return out;
  }
  /* the node of the track a car at p stands on: the nearest node on a track running the car's way (not a track beside it) */
  function railSnap(T, p, dir) {
    let best = -1, bd = Infinity, any = -1, ad = Infinity;
    T.N.forEach((n, i) => { const d = (n.x - p[0]) ** 2 + (n.z - p[1]) ** 2; if (d < ad) { ad = d; any = i; }
      if (dir && d < .3 * .3) { const t = T.tan(i); if (Math.abs(t[0] * dir[0] + t[1] * dir[1]) > .9 && d < bd) { bd = d; best = i; } } });
    return best >= 0 ? best : any;
  }
  /*</rail-topology>*/
  /*<yard-planner>*/
  /* Yard rearrangement planner. It works on a yard model only, so it can be tested on its own:
   *   M.positions  [{ track, idx }]                     every car position (within the track's capacity)
   *   M.occ        [car number | null]                  who stands where (same order as positions)
   *   M.route(a,b) { blocks: [position ids], cost } | null
   *                the rail route from position a to b (null when no track joins them); a car standing on any
   *                of `blocks` is in the way of a car driving that route
   *   M.slotOrder(track, ids)                           the order free positions on a track are filled in
   * planRearrange(M, { from, track, slot }) finds the fewest moves of other cars that clear a way for the car at
   * `from` to reach position `slot` (a position id on `track`) exactly. The requested position is never swapped for
   * another one: it is reached, or the result says why not (occupied, not on that track, not joined, blocked). Without
   * `slot` the car may take any free position on `track` (the order the track fills in). It is a best-first search over yard states (cost: number of moves, then
   * distance driven), with visited-state detection (no cycles), a move limit and a state limit. Only cars that are
   * in the way - directly, or in the way of a car that is in the way, and so on - are moved, and never onto the
   * destination track. Every step is checked against the yard as it will be at that moment: the target must be
   * free, joined by track, and no car may stand on the route. The search is A*: every car still standing on the best
   * route needs at least one move, which never overestimates, so the first plan found has the fewest moves and a
   * yard that needs more moves than allowed is rejected at once. All plans with the fewest moves are compared; then
   * the displaced cars are returned to their own positions where the yard allows it. */
  function planRearrange(M, req, opt) {
    opt = Object.assign({ maxMoves: 8, maxStates: 20000, timeMs: 2500, targets: 6, depth: 3, compare: 8 }, opt || {});
    const now = () => (typeof performance !== "undefined" ? performance.now() : Date.now()), t0 = now();
    const P = M.positions, N = P.length, start = M.occ.slice(), from = req.from, car = start[from], dest = req.track;
    const res = { car, from, track: dest, explored: 0 };
    if (car == null) return Object.assign(res, { ok: false, reason: "not-found" });
    if (P[from].track === dest) return Object.assign(res, { ok: false, reason: "same-track" });
    const rc = new Map(), route = (a, b) => { const k = a * N + b; if (!rc.has(k)) rc.set(k, a === b ? null : M.route(a, b)); return rc.get(k); };
    /* a route may come with alternatives (r.alts): a move is clear if any of them is free; the blockers that count are
     * those of the alternative with the fewest */
    const altsOf = r => r.alts || [r];
    const blockersOf = (occ, a, b) => { const r = route(a, b); if (!r) return null; let best = null;
      altsOf(r).forEach(q => { const bl = q.blocks.filter(x => x !== a && occ[x] != null); if (!best || bl.length < best.length) best = bl; }); return best; };
    const pickAlt = (occ, a, b) => { const r = route(a, b); if (!r || occ[b] != null) return -1; let bi = -1, bc = Infinity;
      altsOf(r).forEach((q, i) => { if (q.cost < bc && q.blocks.every(x => x === a || occ[x] == null)) { bi = i; bc = q.cost; } }); return bi; };
    const clear = (occ, a, b) => pickAlt(occ, a, b) >= 0;
    const costOf = (occ, a, b) => { const i = pickAlt(occ, a, b); return altsOf(route(a, b))[i].cost; };
    const destIds = P.map((_, i) => i).filter(i => P[i].track === dest);
    const exact = req.slot != null;
    if (exact) {
      res.slot = req.slot;
      if (!(req.slot >= 0 && req.slot < N) || P[req.slot].track !== dest) return Object.assign(res, { ok: false, reason: "invalid-slot" });
      if (start[req.slot] != null) return Object.assign(res, { ok: false, reason: "occupied", occupant: start[req.slot] });
    }
    const order = exact ? [req.slot] : (M.slotOrder ? M.slotOrder(dest, destIds) : destIds);
    const reach = order.filter(s => route(from, s));
    if (!reach.length) return Object.assign(res, { ok: false, reason: "no-connection" });
    if (!order.some(s => start[s] == null)) return Object.assign(res, { ok: false, reason: "full" });
    const isDest = new Set(destIds);
    /* free positions a car can be parked on (not the destination track), nearest first */
    const parking = (occ, x) => { const out = []; for (let s = 0; s < N; s++) if (s !== x && occ[s] == null && !isDest.has(s)) { const r = route(x, s); if (r) out.push([s, r.cost]); } return out.sort((a, b) => a[1] - b[1]); };
    const goal = occ => { for (const s of order) if (clear(occ, from, s)) return s; return -1; };
    /* lower bound on the moves still needed: the fewest cars standing on a route to a free destination position */
    const lower = occ => { let h = Infinity; for (const s of order) if (occ[s] == null) { const b = blockersOf(occ, from, s); if (b && b.length < h) h = b.length; } return h; };
    /* the cars that matter in a yard state: the ones in the way of the requested move, and (a few levels down) the
     * ones in the way of those cars getting to a free position */
    const relevant = occ => {
      const seen = new Set(); let level = [];
      order.forEach(s => { if (occ[s] == null) (blockersOf(occ, from, s) || []).forEach(x => { if (!seen.has(x)) { seen.add(x); level.push(x); } }); });
      for (let d = 0; d < opt.depth && level.length; d++) {
        const next = [];
        level.forEach(x => parking(occ, x).slice(0, opt.targets).forEach(([s]) => (blockersOf(occ, x, s) || []).forEach(y => { if (y !== from && !seen.has(y)) { seen.add(y); next.push(y); } })));
        level = next;
      }
      return [...seen];
    };
    const initialBlock = (() => { const s = order.find(q => start[q] == null && route(from, q)); return s == null ? [] : (blockersOf(start, from, s) || []).map(x => start[x]); })();
    res.blockedBy = initialBlock;
    const h0 = lower(start); res.minMoves = h0;
    /* the track has free positions, but none of them is joined by rail to this car (the positions it can reach are taken) */
    if (h0 === Infinity) return Object.assign(res, { ok: false, reason: "free-unreachable", reachable: reach.map(x => P[x].idx).sort((p, q) => p - q), free: order.filter(x => start[x] == null).map(x => P[x].idx).sort((p, q) => p - q) });
    if (h0 > opt.maxMoves) return Object.assign(res, { ok: false, reason: "too-many", maxMoves: opt.maxMoves });
    const direct = goal(start);
    if (direct >= 0) return Object.assign(res, { ok: true, relocations: 0, returns: 0, unrestored: [], alternatives: 1,
      steps: [{ kind: "main", car, from, to: direct, cost: costOf(start, from, direct), alt: pickAlt(start, from, direct) }] });

    /* best-first search; the heap orders by moves first, then by distance */
    const heap = [], swap = (i, j) => { const t = heap[i]; heap[i] = heap[j]; heap[j] = t; };
    const push = n => { heap.push(n); let i = heap.length - 1; while (i) { const p = (i - 1) >> 1; if (heap[p].f <= heap[i].f) break; swap(p, i); i = p; } };
    const pop = () => { const top = heap[0], last = heap.pop(); if (heap.length) { heap[0] = last; let i = 0; for (;;) { const l = 2 * i + 1, r = l + 1; let m = i; if (l < heap.length && heap[l].f < heap[m].f) m = l; if (r < heap.length && heap[r].f < heap[m].f) m = r; if (m === i) break; swap(m, i); i = m; } } return top; };
    const key = occ => occ.map(x => x == null ? "" : x).join("|"), seen = new Set([key(start)]);
    push({ occ: start, moves: [], dist: 0, f: h0 * 1e6 });
    const found = []; let best = Infinity, limitHit = false;
    const stuck = new Map();                                                       // relevant cars that could not be moved anywhere, and why
    while (heap.length) {
      const n = pop();
      if (n.moves.length + lower(n.occ) > best) break;
      if (++res.explored > opt.maxStates || (res.explored % 64 === 0 && now() - t0 > opt.timeMs)) { limitHit = true; break; }
      const g = goal(n.occ);
      if (g >= 0) { found.push({ n, slot: g }); best = n.moves.length; if (found.length >= opt.compare) break; continue; }
      if (n.moves.length >= opt.maxMoves) { limitHit = true; continue; }
      for (const x of relevant(n.occ)) {
        const opts = parking(n.occ, x);
        if (!opts.length) { stuck.set(n.occ[x], "no free position it can reach"); continue; }
        /* where this car can park right now: on each track the deepest free position it can reach (cars are put away to
         * the far end, so they do not shut in the next car), plus the nearest one; best tracks first */
        const reachNow = opts.filter(([s]) => clear(n.occ, x, s)), deep = new Map();
        reachNow.forEach(o => { const t = P[o[0]].track; if (!deep.has(t) || o[1] > deep.get(t)[1]) deep.set(t, o); });
        const choice = [...deep.values()].sort((a, b) => a[1] - b[1]).slice(0, opt.targets);
        if (reachNow.length && !choice.includes(reachNow[0])) choice.push(reachNow[0]);
        const moved = reachNow.length > 0;
        for (const [s, cost] of choice) {
          const occ = n.occ.slice(); occ[s] = occ[x]; occ[x] = null;
          const k = key(occ); if (seen.has(k)) continue; seen.add(k);
          const moves = n.moves.concat([{ kind: "clear", car: n.occ[x], from: x, to: s, cost, alt: pickAlt(n.occ, x, s) }]), h = lower(occ);
          if (moves.length + h > opt.maxMoves) { limitHit = limitHit || h !== Infinity; continue; }   // cannot finish within the move limit
          push({ occ, moves, dist: n.dist + cost, f: (moves.length + h) * 1e6 + n.dist + cost });
        }
        if (!moved && !stuck.has(n.occ[x])) stuck.set(n.occ[x], "every free position is behind other cars");
      }
    }
    if (!found.length) return Object.assign(res, { ok: false, reason: "blocked", limitHit, maxMoves: opt.maxMoves, ms: Math.round(now() - t0), stuck: [...stuck].map(([c, why]) => ({ car: c, why })) });

    /* for each plan: the requested move, then put the displaced cars back where the yard lets them (repeat while one succeeds) */
    const finish = ({ n, slot }) => {
      const occ = n.occ.slice(), steps = n.moves.slice(), home = new Map();
      n.moves.forEach(m => { if (!home.has(m.car)) home.set(m.car, m.from); });
      const mainAlt = pickAlt(occ, from, slot), mainCost = altsOf(route(from, slot))[mainAlt].cost;
      steps.push({ kind: "main", car, from, to: slot, cost: mainCost, alt: mainAlt }); occ[slot] = car; occ[from] = null;
      let dist = n.dist + mainCost, progress = true; const back = [];
      while (progress) {
        progress = false;
        for (const m of n.moves.slice().reverse()) {
          const c = m.car, at = occ.indexOf(c), h = home.get(c);
          if (at === h || at < 0 || occ[h] != null || !clear(occ, at, h)) continue;
          const ra = pickAlt(occ, at, h), rc = altsOf(route(at, h))[ra].cost;
          occ[h] = c; occ[at] = null; back.push({ kind: "return", car: c, from: at, to: h, cost: rc, alt: ra }); dist += rc; progress = true;
        }
      }
      const unrestored = [...home.keys()].filter(c => occ.indexOf(c) !== home.get(c));
      return { steps: steps.concat(back), relocations: n.moves.length, returns: back.length, unrestored, dist };
    };
    const plans = found.map(finish).sort((a, b) => a.relocations - b.relocations || a.unrestored.length - b.unrestored.length || a.dist - b.dist);
    return Object.assign(res, { ok: true, alternatives: plans.length }, plans[0]);
  }
  /*</yard-planner>*/
  const animMs = L => Math.min(6000, 700 + L * 300);                 // how long a car takes to drive a route of length L
  const ydCar = (y, t, i) => { const tr = ydTrack(y, t); return tr && tr.cars[i] ? tr.cars[i] : null; };
  const M3D = (() => {
    // three.js is imported (npm, r128)
    const RM = matchMedia("(prefers-reduced-motion: reduce)").matches;
    const K = 40, SZ = 1.7;                                   // map pixels per world unit; extra room between tracks
    const V3 = (x, y, z) => new THREE.Vector3(x, y, z);
    const live = new Set();
    function rng(seedText) {
      let h = 1779033703 ^ seedText.length;
      for (let i = 0; i < seedText.length; i++) { h = Math.imul(h ^ seedText.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
      return () => { h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); return ((h ^= h >>> 16) >>> 0) / 4294967296; };
    }
    const STATUS = { cy: { color: "#4148c4", name: "Concord" }, dy: { color: "#da3832", name: "Daly City" }, ry: { color: "#b07d5d", name: "Richmond" }, hy: { color: "#55ae58", name: "Hayward" }, xx: { color: "#7b8aa0", name: "Other" } };
    const SHORT = { cy: "Concord", dy: "Daly City", ry: "Richmond", hy: "Hayward", xx: "Other" };
    const HOLDS = ["NO GO", "AWPART", "PM", "MODHLD", "ENGHLD", "TI"];
    /* the statuses the Update maps carry, so the Inquiry maps of the same yard show them too */
    const BASE = {};
    Object.values(S).forEach(x => { if (x.kind !== "map" || !x.blob || !/c-(NOGO|USE)/.test(x.blob.html)) return;
      const re = /class="cell[^"]*\bc-(NOGO|USE)\b[^"]*"[^>]*data-yard="(\w+)" data-track="([^"]+)" data-idx="(\d+)"/g; let m;
      while ((m = re.exec(x.blob.html))) BASE[m[2] + ":" + m[3] + ":" + m[4]] = m[1] === "USE" ? "USABLE" : "NO GO"; });
    /* Richmond Shop is nearly empty in the original: Final seeds it with the same demo cars, so both show these */
    const SHOP_FILL = {};                                       // the shop maps show the yard's own cars
    function fillCells(f) {
      const cv = f.mv.root.querySelector(".canvas"); if (!cv) return;
      const upd = cv.classList.contains("upd");
      cv.querySelectorAll(".cell[data-yard][data-track]").forEach(el => {
        const tr = ydTrack(el.dataset.yard, el.dataset.track); if (!tr || el.classList.contains("turn")) return;
        const r = tr.cars[+el.dataset.idx] || null;
        el.className = el.className.replace(/\bc-\w+\b/g, "").trim();
        if (!r) { el.textContent = el.dataset.dash ? "-" : ""; delete el.dataset.st; delete el.dataset.pm; return; }
        el.textContent = r[0]; el.dataset.st = r[3]; el.dataset.pm = r[4] ? "1" : "0";
        if (/^(DP|PB)$/.test(el.dataset.yard)) { el.classList.add(r[3] === "USABLE" ? "c-USE" : /^(GO|HIGH|HPM)$/.test(r[3]) ? (r[4] || r[3] !== "GO" ? "c-HPM" : "c-GO") : "c-NOGO"); return; }   // round 39: by status
        el.classList.add("c-" + (/^(HY|CY|DY|RY)$/.test(r[1]) ? r[1] : "XX"));
      });
    }
    function fillShop(f) {
      const cv = f.mv.root.querySelector(".shop"); if (!cv) return;
      const upd = cv.classList.contains("upd");
      cv.querySelectorAll(".cell[data-yard]").forEach(el => {
        const list = SHOP_FILL[el.dataset.yard]; if (!list || cellText(el)) return;
        const r = list.find(q => q[0] === el.dataset.track && q[1] === +el.dataset.idx); if (!r) return;
        el.textContent = r[2]; el.dataset.st = r[3]; el.dataset.pm = r[4];
        el.classList.add("c-" + (/^(HY|CY|DY|RY)$/.test(r[5]) ? r[5] : "XX"));
      });
    }
    /* a car's status: a seeded one, the drawn status, the Update map's status, else the seeded demo status Final gives the same car */
    function carState(el, vid) {
      const text = cellText(el); if (!text || text === "-") return null;
      const d = el.dataset, cls = el.className, hm = /\bc-(HY|CY|DY|RY)\b/.exec(cls);
      let stat = /\bc-NOGO\b/.test(cls) ? "NO GO" : /\bc-USE\b/.test(cls) ? "USABLE" : "GO", pm = false, demo = false;
      if (d.st) { stat = d.st; pm = d.pm === "1"; demo = true; }
      else {
        if (stat === "GO" && d.yard && BASE[d.yard + ":" + d.track + ":" + d.idx]) stat = BASE[d.yard + ":" + d.track + ":" + d.idx];
        if (stat === "GO") {
          const x = rng(d.yard ? "demo-status:" + d.yard + ":" + d.track + ":" + d.idx : "demo-status:" + vid + ":" + d.xid)();
          if (x < .13) { stat = "USABLE"; demo = true; } else if (x < .27) { pm = true; demo = true; }
          else if (x < .43) { stat = HOLDS[Math.min(HOLDS.length - 1, Math.floor((x - .27) / .16 * HOLDS.length))]; demo = true; }
        }
      }
      return { text, stat, pm, demo, home: hm ? MV_HOME[hm[1]][0] : "", g: hm ? hm[1].toLowerCase() : "xx" };
    }
    /* the Update maps show the same statuses in the flat plan */
    function dressPlan(f, vid) {
      fillCells(f);
      return;
      f.mv.root.querySelectorAll(".cell").forEach(el => {
        el.classList.remove("m3-use", "m3-pm", "m3-ng");
        const c = carState(el, vid); if (!c || !c.demo) return;
        el.classList.add(c.g === "use" ? "m3-use" : c.g === "pm" ? "m3-pm" : "m3-ng");
      });
    }

    /* the wheel over a 3D stage zooms that map only (a trackpad pinch arrives as Ctrl+wheel); over the side panel the panel scrolls */
    addEventListener("wheel", e => {
      const L = e.target && e.target.closest && e.target.closest(".x3d");
      if (!L || !L._st) return;
      if (e.target.closest(".x3d-side, .x3d-plan, .x3d-carp")) {                // over a panel or card: it scrolls (natively), the map stays still
        e.stopPropagation(); const sc = e.target.closest(".x3d-plan, .x3d-carp, .x3d-list");
        if (e.ctrlKey || !sc || sc.scrollHeight <= sc.clientHeight) e.preventDefault();      // nothing to scroll: do not zoom the map or scroll the page either
        return; }
      e.preventDefault(); e.stopPropagation(); L._st.wheel(e);
    }, { capture: true, passive: false });

    /* ---------- colour + texture helpers ---------- */
    const cx = document.createElement("canvas").getContext("2d");
    function hex(c) { cx.fillStyle = "#000"; cx.fillStyle = c || "#000"; return cx.fillStyle; }
    function rgb(c) { const h = hex(c); if (h[0] !== "#") { const m = h.match(/[\d.]+/g) || [0, 0, 0]; return m.slice(0, 3).map(Number); } return [1, 3, 5].map(i => parseInt(h.substr(i, 2), 16)); }
    function mixW(c, k) { const [r, g, b] = rgb(c); const m = v => Math.round(v + (255 - v) * k); return "rgb(" + m(r) + "," + m(g) + "," + m(b) + ")"; }
    const shade = (c, k) => { const [r, g, b] = rgb(c); const f = v => Math.round(k < 0 ? v * (1 + k) : v + (255 - v) * k); return "rgb(" + f(r) + "," + f(g) + "," + f(b) + ")"; };
    const MONO = '"JetBrains Mono", "SFMono-Regular", Menlo, Consolas, monospace';
    const texCache = new Map();
    function labelTex(text, o) {
      const key = [text, o.fg, o.bg, o.ar, o.font, o.edge, o.r].join("|");
      if (texCache.has(key)) return texCache.get(key);
      const W = 256, H = Math.max(32, Math.min(256, Math.round(W / o.ar)));
      const cv = document.createElement("canvas"); cv.width = W; cv.height = H;
      const g = cv.getContext("2d");
      if (o.bg) {
        const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, o.bg[0]); gr.addColorStop(1, o.bg[1]); g.fillStyle = gr;
        if (o.r && g.roundRect) { g.beginPath(); g.roundRect(0, 0, W, H, o.r); g.fill(); } else g.fillRect(0, 0, W, H);
      }
      if (o.edge) { g.fillStyle = o.edge; const e = Math.max(10, W * .085); g.fillRect(0, 0, e, H); g.fillRect(W - e, 0, e, H); }
      let fs = Math.round(H * .6); g.font = "700 " + fs + "px " + o.font;
      const room = W * (o.edge ? .74 : .9);
      while (fs > 10 && g.measureText(text).width > room) { fs -= 2; g.font = "700 " + fs + "px " + o.font; }
      g.fillStyle = o.fg; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(text, W / 2, H / 2 + 1);
      const t = new THREE.CanvasTexture(cv); t.anisotropy = 8; t.encoding = THREE.sRGBEncoding;
      texCache.set(key, t); return t;
    }
    /* coach livery: the body in the status colour with a window ribbon, two doors per side, belt line and skirt; plain coach ends */
    function rr(g, x, y, w, h, r) { g.beginPath(); if (g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h); }
    function glass(g, x, y, w, h, r) {
      const gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, "#2f4256"); gr.addColorStop(.55, "#17222f"); gr.addColorStop(1, "#0f1822");
      g.fillStyle = gr; rr(g, x, y, w, h, r); g.fill();
      g.save(); rr(g, x, y, w, h, r); g.clip();
      g.fillStyle = "rgba(190,215,240,.16)"; g.beginPath(); g.moveTo(x + w * .1, y); g.lineTo(x + w * .45, y); g.lineTo(x + w * .25, y + h); g.lineTo(x - w * .1, y + h); g.fill();
      g.restore();
    }
    const liveryCache = new Map();
    function livery(color) {
      if (liveryCache.has(color)) return liveryCache.get(color);
      const Sd = document.createElement("canvas"); Sd.width = 1024; Sd.height = 256;
      let g = Sd.getContext("2d"), gr = g.createLinearGradient(0, 0, 0, 256);
      gr.addColorStop(0, shade(color, .38)); gr.addColorStop(.12, shade(color, .12)); gr.addColorStop(.6, color); gr.addColorStop(1, shade(color, -.16));
      g.fillStyle = gr; g.fillRect(0, 0, 1024, 256);
      g.fillStyle = "rgba(255,255,255,.55)"; g.fillRect(0, 0, 1024, 5);
      glass(g, 18, 58, 988, 74, 10);
      g.fillStyle = shade(color, .05); for (let x = 18 + 988 / 9; x < 1000; x += 988 / 9) g.fillRect(x - 4, 58, 8, 74);
      g.fillStyle = "rgba(255,255,255,.5)"; g.fillRect(0, 146, 1024, 6);
      [.27, .73].forEach(fr => {
        const c0 = 1024 * fr, w = 104, x = c0 - w / 2;
        g.fillStyle = shade(color, -.04); rr(g, x, 44, w, 196, 6); g.fill();
        g.strokeStyle = "rgba(10,20,30,.45)"; g.lineWidth = 3; rr(g, x, 44, w, 196, 6); g.stroke();
        g.beginPath(); g.moveTo(c0, 46); g.lineTo(c0, 238); g.stroke();
        glass(g, x + 9, 56, w / 2 - 15, 84, 5); glass(g, c0 + 6, 56, w / 2 - 15, 84, 5);
      });
      g.fillStyle = shade(color, -.42); g.fillRect(0, 232, 1024, 24);
      g.fillStyle = "rgba(0,0,0,.18)"; g.fillRect(0, 228, 1024, 4);
      const side = new THREE.CanvasTexture(Sd); side.anisotropy = 8; side.encoding = THREE.sRGBEncoding;
      const E = document.createElement("canvas"); E.width = 256; E.height = 256;
      g = E.getContext("2d"); gr = g.createLinearGradient(0, 0, 0, 256);
      gr.addColorStop(0, shade(color, .3)); gr.addColorStop(.5, color); gr.addColorStop(1, shade(color, -.2));
      g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
      g.fillStyle = "rgba(255,255,255,.5)"; g.fillRect(0, 0, 256, 6);
      g.fillStyle = "#2a323c"; rr(g, 70, 18, 116, 210, 12); g.fill();
      g.strokeStyle = "rgba(255,255,255,.12)"; g.lineWidth = 2; for (let k = 26; k < 222; k += 10) { g.beginPath(); g.moveTo(74, k); g.lineTo(182, k); g.stroke(); }
      g.fillStyle = shade(color, -.08); rr(g, 92, 30, 72, 188, 6); g.fill();
      g.strokeStyle = "rgba(10,20,30,.45)"; g.lineWidth = 3; rr(g, 92, 30, 72, 188, 6); g.stroke();
      glass(g, 102, 44, 52, 62, 5);
      g.fillStyle = "#c9d1da"; g.fillRect(148, 128, 8, 26);
      g.fillStyle = "rgba(255,255,255,.5)"; g.fillRect(0, 154, 70, 6); g.fillRect(186, 154, 70, 6);
      g.fillStyle = shade(color, -.45); g.fillRect(0, 222, 256, 34);
      g.fillStyle = "#2a323c"; rr(g, 104, 206, 48, 34, 4); g.fill();
      const end = new THREE.CanvasTexture(E); end.anisotropy = 8; end.encoding = THREE.sRGBEncoding;
      const out = { side, end }; liveryCache.set(color, out); return out;
    }
    /* today's BART cars: a white-silver shell, a wide dark window band, two doors a side, and a broad stripe in the
     * car's status colour under the windows (with a fine line of it under the roof) */
    const modernCache = new Map();
    function liveryModern(color) {
      if (modernCache.has(color)) return modernCache.get(color);
      const C = document.createElement("canvas"); C.width = 1024; C.height = 256; const g = C.getContext("2d");
      let gr = g.createLinearGradient(0, 0, 0, 256); gr.addColorStop(0, "#fbfcfd"); gr.addColorStop(.55, "#eef2f6"); gr.addColorStop(1, "#d6dde5");
      g.fillStyle = gr; g.fillRect(0, 0, 1024, 256);
      g.fillStyle = "rgba(20,40,70,.12)"; g.fillRect(0, 0, 1024, 10);                     // roof edge
      g.fillStyle = color; g.fillRect(0, 18, 1024, 7);                                       // fine status line under the roof
      glass(g, 14, 50, 996, 86, 14);                                                         // continuous window band
      g.fillStyle = "rgba(255,255,255,.55)"; for (let x = 14 + 996 / 8; x < 1000; x += 996 / 8) g.fillRect(x - 2, 50, 4, 86);
      g.fillStyle = color; g.fillRect(0, 146, 1024, 44);                                     // the status stripe
      g.fillStyle = "rgba(255,255,255,.35)"; g.fillRect(0, 146, 1024, 3);
      [.3, .7].forEach(fr => {                                                               // two plug doors a side
        const cx = 1024 * fr, w = 96, x = cx - w / 2;
        g.fillStyle = "#e4e9ef"; rr(g, x, 40, w, 192, 8); g.fill();
        g.strokeStyle = "rgba(20,35,55,.5)"; g.lineWidth = 3; rr(g, x, 40, w, 192, 8); g.stroke();
        g.beginPath(); g.moveTo(cx, 42); g.lineTo(cx, 230); g.stroke();
        glass(g, x + 9, 52, w / 2 - 15, 100, 6); glass(g, cx + 6, 52, w / 2 - 15, 100, 6);
        g.fillStyle = color; g.fillRect(x + 4, 160, w - 8, 16);
      });
      g.fillStyle = "#2b333d"; g.fillRect(0, 236, 1024, 20);                                 // dark underbody skirt
      const t = new THREE.CanvasTexture(C); t.anisotropy = 8; t.encoding = THREE.sRGBEncoding; t.offset.set(.5, 0);
      modernCache.set(color, t); return t;
    }
    /* the car body: a side profile with rounded roof corners and ends, pushed out across the car's width */
    let _carGeo = null;
    function carGeo() {
      if (_carGeo) return _carGeo;
      const s = new THREE.Shape(), rx = .075, ry = .3;
      s.moveTo(-.5, 0); s.lineTo(.5, 0); s.lineTo(.5, 1 - ry); s.quadraticCurveTo(.5, 1, .5 - rx, 1); s.lineTo(-.5 + rx, 1); s.quadraticCurveTo(-.5, 1, -.5, 1 - ry); s.lineTo(-.5, 0);
      _carGeo = new THREE.ExtrudeGeometry(s, { depth: 1, bevelEnabled: false, curveSegments: 8 }); _carGeo.translate(0, 0, -.5);
      return _carGeo;
    }
    /* the engine that pulls the shop map train: navy with an amber band, louvred hood, cab windows, safety chevrons */
    let _eng = null;
    function engineTex() {
      if (_eng) return _eng;
      const NAVY = "#24476f", mk = (w, h, fn) => { const c = document.createElement("canvas"); c.width = w; c.height = h; const g = c.getContext("2d");
        const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, "#3a6a9e"); gr.addColorStop(.15, NAVY); gr.addColorStop(1, "#152a44");
        g.fillStyle = gr; g.fillRect(0, 0, w, h); fn(g, w, h); const t = new THREE.CanvasTexture(c); t.anisotropy = 8; t.encoding = THREE.sRGBEncoding; return t; };
      const band = (g, w, y, h) => { g.fillStyle = "#f5b544"; g.fillRect(0, y, w, h); g.fillStyle = "rgba(255,255,255,.65)"; g.fillRect(0, y - 3, w, 2); g.fillRect(0, y + h + 1, w, 2); };
      const louvres = (g, x, y, w, h) => { g.fillStyle = "rgba(8,16,28,.35)"; rr(g, x, y, w, h, 4); g.fill(); g.fillStyle = "rgba(0,0,0,.45)"; for (let k = y + 6; k < y + h - 3; k += 7) g.fillRect(x + 5, k, w - 10, 3); };
      _eng = {
        hoodSide: mk(512, 128, (g, w) => {
          [24, 150, 276, 384].forEach((x, i) => louvres(g, x, 16, i === 3 ? 100 : 108, 58));
          g.strokeStyle = "rgba(0,0,0,.35)"; g.lineWidth = 2; [138, 264, 372].forEach(x => { g.beginPath(); g.moveTo(x, 8); g.lineTo(x, 84); g.stroke(); });
          band(g, w, 90, 14); g.fillStyle = "#c9d1da"; g.fillRect(0, 116, w, 3);
        }),
        cabSide: mk(256, 192, (g, w) => { glass(g, 30, 22, 92, 62, 8); glass(g, 134, 22, 92, 62, 8); g.strokeStyle = "rgba(0,0,0,.4)"; g.lineWidth = 3; rr(g, 140, 96, 80, 78, 5); g.stroke(); band(g, w, 128, 18); }),
        cabFront: mk(256, 256, (g, w) => {
          glass(g, 22, 30, 100, 82, 10); glass(g, 134, 30, 100, 82, 10);
          g.fillStyle = "#1a232d"; rr(g, 88, 6, 80, 18, 4); g.fill();
          g.fillStyle = "#f5b544"; for (let x = 96; x < 160; x += 8) g.fillRect(x, 12, 5, 6);
          [[46, 150], [210, 150]].forEach(([x, y]) => { const r = g.createRadialGradient(x, y, 2, x, y, 20); r.addColorStop(0, "#fffbe6"); r.addColorStop(.5, "rgba(255,240,190,.9)"); r.addColorStop(1, "rgba(255,240,190,0)"); g.fillStyle = r; g.beginPath(); g.arc(x, y, 20, 0, 7); g.fill(); });
          g.save(); g.beginPath(); g.rect(0, 186, w, 70); g.clip();
          g.fillStyle = "#f5b544"; g.fillRect(0, 186, w, 70); g.fillStyle = "#1b2a3c";
          for (let x = -80; x < w + 80; x += 44) { g.beginPath(); g.moveTo(x, 256); g.lineTo(x + 22, 256); g.lineTo(x + 92, 186); g.lineTo(x + 70, 186); g.fill(); }
          g.restore();
        }),
        hoodEnd: mk(256, 192, (g, w) => { louvres(g, 40, 20, 176, 96); band(g, w, 132, 16); }),
        top: mk(256, 128, g => { g.fillStyle = "rgba(0,0,0,.35)"; [[60, 64], [170, 64]].forEach(([x, y]) => { g.beginPath(); g.arc(x, y, 38, 0, 7); g.fill(); }); g.strokeStyle = "rgba(200,210,222,.6)"; g.lineWidth = 3; [[60, 64], [170, 64]].forEach(([x, y]) => { for (let r = 12; r < 38; r += 9) { g.beginPath(); g.arc(x, y, r, 0, 7); g.stroke(); } }); })
      };
      return _eng;
    }
    let _slot = null;
    function slotTex() {
      if (_slot) return _slot;
      const cv = document.createElement("canvas"); cv.width = 128; cv.height = 64; const g = cv.getContext("2d");
      // an empty position: a white cell with a fine dark edge, as on the 2D map
      const rr = (x, y, w, h, r) => { g.beginPath(); if (g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h); };
      g.fillStyle = "rgba(238,240,243,.98)"; rr(5, 5, 118, 54, 8); g.fill();   // light grey, a touch lighter than the ground so it reads as empty
      g.strokeStyle = "rgba(72,84,100,.7)"; g.lineWidth = 6; rr(5, 5, 118, 54, 8); g.stroke();
      g.strokeStyle = "rgba(255,255,255,.9)"; g.lineWidth = 2; rr(10, 10, 108, 44, 6); g.stroke();
      _slot = new THREE.CanvasTexture(cv); _slot.encoding = THREE.sRGBEncoding; return _slot;
    }
    function Soup() { this.p = []; this.n = []; }
    Soup.prototype.quad = function (a, b, c, d, n) { [a, b, c, a, c, d].forEach(v => { this.p.push(v[0], v[1], v[2]); this.n.push(n[0], n[1], n[2]); }); };
    Soup.prototype.mesh = function (mat) {
      if (!this.p.length) return null;
      const g = new THREE.BufferGeometry();
      g.setAttribute("position", new THREE.Float32BufferAttribute(this.p, 3)); g.setAttribute("normal", new THREE.Float32BufferAttribute(this.n, 3));
      return new THREE.Mesh(g, mat);
    };
    function strip(Sx, pts, hw, y, y0) {
      for (let i = 0; i < pts.length - 1; i++) {
        let [ax, az] = pts[i], [bx, bz] = pts[i + 1];
        const dx = bx - ax, dz = bz - az, L = Math.hypot(dx, dz); if (L < 1e-4) continue;
        const ux = dx / L, uz = dz / L, e = Math.min(hw * .5, .04);
        ax -= ux * e; az -= uz * e; bx += ux * e; bz += uz * e;
        const nx = -uz * hw, nz = ux * hw;
        Sx.quad([ax + nx, y, az + nz], [bx + nx, y, bz + nz], [bx - nx, y, bz - nz], [ax - nx, y, az - nz], [0, 1, 0]);
        if (y0 != null) {
          Sx.quad([ax + nx, y0, az + nz], [bx + nx, y0, bz + nz], [bx + nx, y, bz + nz], [ax + nx, y, az + nz], [-uz, 0, ux]);
          Sx.quad([ax - nx, y, az - nz], [bx - nx, y, bz - nz], [bx - nx, y0, bz - nz], [ax - nx, y0, az - nz], [uz, 0, -ux]);
        }
      }
    }
    /* a track bed as MARIS-Web lays it: the sides slope out to the ground and each end closes in a soft ramp (no square wall, no block) */
    function bedStrip(Sx, pts, hw, y, y0, fl) {
      const n = pts.length; if (n < 2) return;
      const un = (a, b) => { const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1; return [dx / L, dz / L]; };
      for (let i = 0; i < n - 1; i++) {
        let [ax, az] = pts[i], [bx, bz] = pts[i + 1];
        const L = Math.hypot(bx - ax, bz - az); if (L < 1e-4) continue;
        const ux = (bx - ax) / L, uz = (bz - az) / L, e = Math.min(hw * .5, .04);
        if (i) { ax -= ux * e; az -= uz * e; } if (i < n - 2) { bx += ux * e; bz += uz * e; }
        const nx = -uz, nz = ux, h = y - y0, k = Math.hypot(h, fl) || 1;
        Sx.quad([ax + nx * hw, y, az + nz * hw], [bx + nx * hw, y, bz + nz * hw], [bx - nx * hw, y, bz - nz * hw], [ax - nx * hw, y, az - nz * hw], [0, 1, 0]);
        Sx.quad([ax + nx * (hw + fl), y0, az + nz * (hw + fl)], [bx + nx * (hw + fl), y0, bz + nz * (hw + fl)], [bx + nx * hw, y, bz + nz * hw], [ax + nx * hw, y, az + nz * hw], [nx * h / k, fl / k, nz * h / k]);
        Sx.quad([ax - nx * hw, y, az - nz * hw], [bx - nx * hw, y, bz - nz * hw], [bx - nx * (hw + fl), y0, bz - nz * (hw + fl)], [ax - nx * (hw + fl), y0, az - nz * (hw + fl)], [-nx * h / k, fl / k, -nz * h / k]);
      }
      [[pts[0], un(pts[1], pts[0])], [pts[n - 1], un(pts[n - 2], pts[n - 1])]].forEach(([[x, z], [ux, uz]]) => {   // u points out of the track
        const nx = -uz, nz = ux, r = fl * 1.6, h = y - y0, k = Math.hypot(h, r) || 1;
        const t1 = [x + nx * hw, y, z + nz * hw], t2 = [x - nx * hw, y, z - nz * hw];
        const b1 = [x + ux * r + nx * (hw + fl * .6), y0, z + uz * r + nz * (hw + fl * .6)], b2 = [x + ux * r - nx * (hw + fl * .6), y0, z + uz * r - nz * (hw + fl * .6)];
        const s1 = [x + nx * (hw + fl), y0, z + nz * (hw + fl)], s2 = [x - nx * (hw + fl), y0, z - nz * (hw + fl)];
        Sx.quad(t1, t2, b2, b1, [ux * h / k, r / k, uz * h / k]);                           // the ramp
        Sx.quad(t1, b1, s1, s1, [nx, .6, nz]); Sx.quad(t2, s2, b2, b2, [-nx, .6, -nz]);       // the rounded corners
      });
    }
    /* round the corners of a polyline: each bend becomes a short curve of radius up to rmax */
    function filletPts(pts, rmax) {
      if (pts.length < 3) return pts;
      const d = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]), out = [pts[0]];
      for (let i = 1; i < pts.length - 1; i++) {
        const A = out[out.length - 1], B = pts[i], C = pts[i + 1], lab = d(A, B), lbc = d(B, C);
        if (lab < 1e-4 || lbc < 1e-4) continue;
        const u = [(B[0] - A[0]) / lab, (B[1] - A[1]) / lab], v = [(C[0] - B[0]) / lbc, (C[1] - B[1]) / lbc];
        const turn = Math.acos(Math.max(-1, Math.min(1, u[0] * v[0] + u[1] * v[1])));
        if (turn < .2) { out.push(B); continue; }
        const r = Math.min(rmax, lab * .48, lbc * .48);
        const P1 = [B[0] - u[0] * r, B[1] - u[1] * r], P2 = [B[0] + v[0] * r, B[1] + v[1] * r], n = Math.max(4, Math.ceil(turn * 7));
        for (let k = 0; k <= n; k++) { const t = k / n, a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t; out.push([a * P1[0] + b * B[0] + c * P2[0], a * P1[1] + b * B[1] + c * P2[1]]); }
      }
      out.push(pts[pts.length - 1]);
      return out;
    }
    /* join lines whose ends meet (within tol) into longer lines, so their shared corner can be rounded */
    function joinEnds(lines, tol) {
      const d = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
      for (let guard = 0; guard < 600; guard++) {
        let hit = null;
        for (let i = 0; i < lines.length && !hit; i++) for (let j = i + 1; j < lines.length && !hit; j++) {
          const P = lines[i], Q = lines[j], pe = [P[0], P[P.length - 1]], qe = [Q[0], Q[Q.length - 1]];
          for (let a = 0; a < 2 && !hit; a++) for (let b = 0; b < 2 && !hit; b++) if (d(pe[a], qe[b]) < tol) hit = { i, j, a, b };
        }
        if (!hit) break;
        const P = lines[hit.i], Q = lines[hit.j];
        const p = hit.a ? P.slice() : P.slice().reverse(), q = hit.b ? Q.slice().reverse() : Q.slice();
        const m = p.concat(q.slice(1)); Object.keys(P).filter(k => isNaN(k)).forEach(k => { m[k] = P[k]; });
        lines.splice(hit.j, 1); lines.splice(hit.i, 1, m);
      }
      return lines;
    }
    function offset(pts, d) {
      return pts.map((p, i) => { const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)];
        const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1; return [p[0] - dz / L * d, p[1] + dx / L * d]; });
    }
    /* named views (th: around the yard, ph: down from straight above); "whole" opens the map, "hero" is the Angle close-up */
    const VIEWS = { top: { th: 0, ph: .02, label: "Top" }, front: { th: 0, ph: 1.22, label: "Front" }, back: { th: Math.PI, ph: 1.22, label: "Back" },
      left: { th: -Math.PI / 2, ph: 1.22, label: "Left" }, right: { th: Math.PI / 2, ph: 1.22, label: "Right" }, hero: { th: -.4, ph: .98, label: "Angle" }, whole: { th: 0, ph: .86, label: "Whole yard" } };
    const PH_MIN = .02, PH_MAX = 1.45;
    const mk = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
    const GROUPS = [[/^(ST|SS|STOR|CSTOR|LSTOR|C)$/, "Storage tracks"], [/^(TF|DTF)$/, "Transfer tracks"], [/^RT$/, "Run-through"],
      [/^(S|SI)$/, "Shop · bays"], [/^(RR|RRP|RS|RM)$/, "RR tracks"], [/^T$/, "Yard tracks"], [/^(TT|ET)$/, "Test & east tracks"], [/^(EX|PSYT)$/, "Exchange"],
      [/^WT$/, "Wheel truing"], [/^(BPC|APC)$/, "Car builder locations"], [/^(TM|TMZ)$/, "TMZ tracks"], [/^(L|W|Y|MT|TR)$/, "Main tracks"]];
    const kindOf = tid => { const p = (/^[A-Z]+/i.exec(tid) || [""])[0].toUpperCase(); const g = p && GROUPS.find(([re]) => re.test(p)); return g ? g[1] : "Other tracks"; };
    const INFO = "map3d.info";

    /* ---------- one 3D stage per map card ---------- */
    function build(f, s, root, v) {
      live.forEach(o => { if (!o.layer.isConnected || o.layer.closest(".view[hidden]")) o.dispose(); });   // one live WebGL stage per visible map
      const body = f.parentElement, vid = v.dataset.id, acts = s.acts || {};
      const isShop = !!root.querySelector(".shop");
      const authored = !!root.querySelector(".map-svg.authored");         // tracks drawn as exact centerlines: taken as they are
      const c = mvCrop(f, root, s), W = c.w, H = c.h;
      const rr0 = root.getBoundingClientRect(), zr = rr0.width / (root.offsetWidth || 1) || 1;
      const box = e => { const r = e.getBoundingClientRect(); return { x: (r.left - rr0.left) / zr, y: (r.top - rr0.top) / zr, w: r.width / zr, h: r.height / zr }; };
      const inside = b => b.w > 0 && b.x + b.w > 0 && b.x < W && b.y + b.h > 0 && b.y < H;
      const own = e => !e.closest(".mv-x");
      /* which track a position belongs to and its place on it (the classic yard maps carry no data-track) */
      const trackOf = new Map(), idxOf = new Map();
      (f.mv.tracks || []).forEach(t => t.cells.forEach((e, i) => { trackOf.set(e, t.name); idxOf.set(e, i); }));
      const tk = e => e.dataset.track || trackOf.get(e) || "_";
      const ix = e => e.dataset.idx != null ? +e.dataset.idx : idxOf.has(e) ? idxOf.get(e) : 0;
      const opens = d => !!(d.dataset.open || (d.dataset.xid && acts[d.dataset.xid]));

      /* ---- DOM: stage, tooltip, tracks & cars panel, view cube, view buttons, hint ---- */
      const layer = mk("div", "x3d"), canvas = document.createElement("canvas");
      canvas.tabIndex = -1;
      canvas.setAttribute("aria-label", "3D map. Drag to pan, right-drag to rotate, scroll or pinch to zoom, click a car or track tag to open it.");
      const tip = mk("div", "x3d-tip"); tip.hidden = true;
      const side = mk("div", "x3d-side");
      const legend = mk("div", "x3d-card x3d-info",
        '<div class="x3d-ihead"><div><h4>Show cars</h4><small class="x3d-sum"></small></div>' +
        '<button type="button" class="x3d-x" title="Hide this panel" aria-label="Hide the tracks panel">&times;</button></div>' +
        '<div class="x3d-chips">' + Object.keys(STATUS).map(k =>
          `<button type="button" class="x3d-chip" data-k="${k}" aria-pressed="true" title="${STATUS[k].name} - click to show or hide these cars"><i style="background:${STATUS[k].color}"></i>${SHORT[k]}<b data-n="${k}">0</b></button>`).join("") + "</div>" +
        '<div class="x3d-list" role="list"></div>');
      const reopen = mk("button", "x3d-card x3d-open", "Track cars"); reopen.type = "button"; reopen.title = "Show the track cars panel";
      side.append(legend, reopen);
      const nav = mk("div", "x3d-nav",
        '<div class="x3d-cubewrap" title="Drag to turn the view. Click a face to look from that side."><div class="x3d-cube">' +
        ["top", "front", "back", "left", "right"].map(k => `<button type="button" class="f-${k}" data-v="${k}">${VIEWS[k].label}</button>`).join("") +
        '<span class="f-bottom"></span></div></div>');
      const hud = mk("div", "x3d-hud", '<div class="x3d-views" role="group" aria-label="Look from">' +
        ["top", "front", "back", "left", "right", "hero"].map(k => `<button type="button" data-v="${k}">${VIEWS[k].label}</button>`).join("") + "</div>");
      const reset = mk("button", "mv-b x3d-reset", "Reset view"); reset.type = "button";
      /* "Reset view" sits in the map's own button row, after Print, the same size as Refresh Map */
      const actRow = f.mv && f.mv.card && f.mv.card.querySelector(".mv-acts");
      if (actRow) { actRow.querySelectorAll(".x3d-reset").forEach(e => e.remove()); actRow.appendChild(reset); } else hud.appendChild(reset);
      /* shop maps: clicking a car opens its panel (as in MARIS Yard Command); Move car then picks a track */
      const carPanel = mk("div", "x3d-card x3d-carp"); carPanel.hidden = true;
      const moveBar = mk("div", "x3d-movebar"); moveBar.hidden = true;
      const planCard = mk("div", "x3d-card x3d-plan"); planCard.hidden = true; planCard.setAttribute("role", "dialog"); planCard.setAttribute("aria-label", "Movement plan");
      const hint = mk("div", "x3d-hint", "Drag to pan, right-drag to rotate, scroll or pinch to zoom where you point");
      layer.append(canvas, tip, side, nav, hud, hint, carPanel, moveBar, planCard);
      /* the view controls and the hint stay out of the way: they fade when the pointer rests, and return on any movement */
      let idleT = 0; const wake = () => { layer.classList.remove("idle"); clearTimeout(idleT); idleT = setTimeout(() => { if (!layer.contains(document.activeElement) || document.activeElement === canvas) layer.classList.add("idle"); }, 2600); };
      ["pointermove", "pointerdown", "wheel", "focusin", "keydown"].forEach(ev => layer.addEventListener(ev, wake, { passive: true })); layer.addEventListener("pointerleave", () => { clearTimeout(idleT); layer.classList.add("idle"); });
      wake(); setTimeout(() => hint.classList.add("gone"), 7000);
      body.insertBefore(layer, body.firstChild);
      const cubeWrap = nav.querySelector(".x3d-cubewrap"), cube = nav.querySelector(".x3d-cube");

      const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: "high-performance", logarithmicDepthBuffer: true });
      renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      renderer.outputEncoding = THREE.sRGBEncoding; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
      renderer.setClearColor(0x000000, 0);
      const camera = new THREE.PerspectiveCamera(32, 1.6, .25, 900), scene = new THREE.Scene();
      scene.fog = new THREE.Fog(0xe2eaf2, 80, 220);
      const st = { f, layer, renderer, camera, scene, cars: new Map(), hide: new Set(), picks: [], hover: null, raf: 0, last: 0, sw: 0, sh: 0, pr: 0 };
      live.add(st); layer._st = st;

      const mapW = W / K, mapD = H / K * SZ;
      const wx = x => (x - W / 2) / K, wz = y => (y - H / 2) / K * SZ;

      /* lights: cool sky fill, a warm key with soft shadows, a cool rim */
      scene.add(new THREE.HemisphereLight(0xf7fbff, 0xa7b6c6, .9));
      const sun = new THREE.DirectionalLight(0xfff5e8, 1.05);
      sun.position.set(-mapW * .35, 40, mapD * .8 + 14); sun.castShadow = true;
      sun.shadow.mapSize.set(2048, 2048); sun.shadow.bias = -.0005; sun.shadow.normalBias = .02;
      Object.assign(sun.shadow.camera, { left: -mapW * .62, right: mapW * .62, top: mapD * .8, bottom: -mapD * .8, near: 1, far: 160 });
      scene.add(sun, sun.target);
      const rim = new THREE.DirectionalLight(0xc4dbff, .35); rim.position.set(mapW * .4, 16, -mapD - 12); scene.add(rim);
      const ground = new THREE.Mesh(new THREE.PlaneGeometry(600, 600), new THREE.MeshStandardMaterial({ color: 0xe3eaf1, roughness: 1 }));
      ground.rotation.x = -Math.PI / 2; ground.position.y = -.06; ground.receiveShadow = true; scene.add(ground);
      const grid = new THREE.GridHelper(600, 300, 0xc3cfdc, 0xc3cfdc); grid.position.y = -.05;
      grid.material.transparent = true; grid.material.opacity = .55; scene.add(grid);

      const mats = {
        ballast: new THREE.MeshStandardMaterial({ color: 0xa7afb9, roughness: .96, side: THREE.DoubleSide }),
        shoulder: new THREE.MeshStandardMaterial({ color: 0xd2dae3, roughness: 1, side: THREE.DoubleSide }),
        rail: new THREE.MeshStandardMaterial({ color: 0x8e99a6, metalness: .8, roughness: .3, side: THREE.DoubleSide }),
        tie: new THREE.MeshStandardMaterial({ color: 0x96785d, roughness: .95 }),
        ink: new THREE.MeshBasicMaterial({ color: 0x4d5b6c, side: THREE.DoubleSide }),
        pad: new THREE.MeshBasicMaterial({ map: slotTex(), transparent: true, depthWrite: false, toneMapped: false }),
        padHot: new THREE.MeshBasicMaterial({ color: 0xf5b544, transparent: true, opacity: .75, depthWrite: false, toneMapped: false }),
        wash: new THREE.MeshBasicMaterial({ color: 0x4a86d8, transparent: true, opacity: .85, depthWrite: false, toneMapped: false }),
        glass: new THREE.MeshPhysicalMaterial({ color: 0xbcd6f0, transparent: true, opacity: .2, roughness: .08, metalness: .1, depthWrite: false, side: THREE.DoubleSide }),
        glassEdge: new THREE.LineBasicMaterial({ color: 0x6f9cc8, transparent: true, opacity: .8 }),
        buffer: new THREE.MeshStandardMaterial({ color: 0xf0a08f, roughness: .6 }),
        under: new THREE.MeshStandardMaterial({ color: 0x2b333d, roughness: .7, metalness: .3 }),
        bogie: new THREE.MeshStandardMaterial({ color: 0x3a434e, roughness: .55, metalness: .5 }),
        hub: new THREE.MeshStandardMaterial({ color: 0xb6bec8, roughness: .3, metalness: .8 }),
        roofUnit: new THREE.MeshStandardMaterial({ color: 0xc9d1da, roughness: .45, metalness: .45 }),
        pole: new THREE.MeshStandardMaterial({ color: 0x9aa4b0, metalness: .5, roughness: .4 }),
        halo: new THREE.MeshBasicMaterial({ color: 0xe5484d, transparent: true, opacity: .6, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
      };
      /* rail car materials: painted livery on the sides and ends, a brighter version for hover */
      const carMat = {};
      Object.keys(STATUS).forEach(k => {
        const tex = liveryModern(STATUS[k].color), em = new THREE.Color(STATUS[k].color).convertSRGBToLinear(), shellC = new THREE.Color("#e7ecf1").convertSRGBToLinear();
        const m = (map, hot) => { const x = new THREE.MeshStandardMaterial({ map, roughness: .3, metalness: .25 }); if (hot) { x.emissive = em; x.emissiveIntensity = .3; } x._lin = 1; return x; };
        const sh = hot => { const x = new THREE.MeshStandardMaterial({ color: shellC, roughness: .32, metalness: .45 }); if (hot) { x.emissive = em; x.emissiveIntensity = .35; } x._lin = 1; return x; };
        carMat[k] = { side: m(tex), sideHot: m(tex, 1), shell: sh(), shellHot: sh(1) };
      });
      const endGlass = new THREE.MeshStandardMaterial({ color: new THREE.Color("#18222e").convertSRGBToLinear(), roughness: .15, metalness: .4 }); endGlass._lin = 1;
      const unitBox = new THREE.BoxGeometry(1, 1, 1), unitPlane = new THREE.PlaneGeometry(1, 1);
      const wheelGeo = new THREE.CylinderGeometry(1, 1, 1, 14), ringGeo = new THREE.RingGeometry(.86, 1, 48);

      const beds = new Soup(), shoulders = new Soup(), rails = new Soup(), inks = new Soup(), ties = [], buffers = [], signals = [];
      const routes = [], laid = [];                            // every piece of track laid (also the shop train's route)
      st.laid = laid;
      /* every track is laid at one standard width (half width TRACK_HW): bed, rails, sleepers, buffers and signals all follow it */
      const TRACK_HW = .3;
      function track(pts, hw, src) { routes.push(pts); pts.src = src || ""; laid.push([pts, TRACK_HW]); }
      function layTrack(pts, hw, lite) {                      // lite: a drawn connecting line - rails and sleepers on a narrow bed
        if (lite) hw *= .82;
        if (!lite) strip(shoulders, pts, hw * 1.3, .004); bedStrip(beds, pts, hw, lite ? .04 : .05, .004, lite ? .05 : .07);
        strip(rails, offset(pts, hw * .55), Math.max(.022, hw * .09), .1, .05); strip(rails, offset(pts, -hw * .55), Math.max(.022, hw * .09), .1, .05);
        for (let i = 0; i < pts.length - 1; i++) {
          const [ax, az] = pts[i], [bx, bz] = pts[i + 1], L = Math.hypot(bx - ax, bz - az), n = Math.floor(L / .3), ang = Math.atan2(bz - az, bx - ax);
          for (let k = 0; k <= n; k++) { const t = n ? k / n : .5; ties.push([ax + (bx - ax) * t, az + (bz - az) * t, ang, hw]); }
        }
      }

      /* SVG track lines, outlines and arrows (positions taken through each element's own transform) */
      const extras = new THREE.Group(); scene.add(extras);
      const isTrack = col => { const [r, g, b] = rgb(col); return b > r + 40 && b >= g; };
      const svgSegs = [], inkLines = [], drawnRails = [];                       // straight pieces of the drawn track lines, to seat car rows on; plain drawn lines
      /* a box captioned TOWER is the yard tower: a building, not an outline on the ground */
      const towerCaps = [...root.querySelectorAll(".mtxt")].filter(e => own(e) && /^\s*TOWER\s*$/i.test(e.textContent || "")).map(box);
      const isTowerBox = b => towerCaps.some(c => c.x < b.x + b.w + 30 && c.x + c.w > b.x - 30 && c.y < b.y + b.h + 30 && c.y + c.h > b.y - 30);
      function tower(cx, cz, bw, bd) {
        const g = new THREE.Group(), conc = new THREE.MeshStandardMaterial({ color: 0xb9c1ca, roughness: .85 }), dark = new THREE.MeshStandardMaterial({ color: 0x5d6873, roughness: .6, metalness: .3 });
        const glass = new THREE.MeshStandardMaterial({ color: 0x8fc4ee, emissive: 0x2a6fb0, emissiveIntensity: .35, transparent: true, opacity: .8, roughness: .1, metalness: .4 });
        const w = Math.max(bw, .5), d = Math.max(bd, .5), sw = Math.min(w, d) * .5;
        const add = (geo, m, y, sx, sy, sz) => { const o = new THREE.Mesh(geo, m); o.scale.set(sx, sy, sz); o.position.y = y; o.castShadow = true; o.receiveShadow = true; g.add(o); return o; };
        add(unitBox, conc, .06, w, .12, d);                         // plinth (the box the map draws)
        add(unitBox, conc, .12 + .7, sw, 1.4, sw);                  // shaft
        add(unitBox, dark, 1.55, w * .92, .1, d * .92);             // cab floor
        add(unitBox, glass, 1.6 + .25, w * .88, .5, d * .88);       // glass cab
        add(unitBox, dark, 2.14, w * 1.04, .08, d * 1.04);          // roof
        add(new THREE.CylinderGeometry(.015, .015, 1, 6), dark, 2.4, 1, .45, 1);   // aerial
        g.position.set(cx, 0, cz); extras.add(g);
      }
      /* lines ending in an arrow head (and the plain lines joined to them) are drawn arrows, never track */
      const arrowEnds = [];
      root.querySelectorAll(".map-svg line[marker-end], .map-svg polyline[marker-end], .map-svg path[marker-end]").forEach(e => { if (e.tagName === "line") arrowEnds.push([+e.getAttribute("x1"), +e.getAttribute("y1")], [+e.getAttribute("x2"), +e.getAttribute("y2")]); });
      const isArrow = e => { if (e.getAttribute("marker-end") || e.getAttribute("marker-start")) return true; if (e.tagName !== "line") return false;
        const ends = [[+e.getAttribute("x1"), +e.getAttribute("y1")], [+e.getAttribute("x2"), +e.getAttribute("y2")]]; return ends.some(([x, y]) => arrowEnds.some(([u, v]) => Math.hypot(u - x, v - y) < 2.5)); };
      root.querySelectorAll(".map-svg").forEach(svg => {
        if (!own(svg)) return;
        const sb = box(svg);
        // the 2D map draws its tracks as rail pairs (#4ba6ea): when it has them, a track goes down the middle of each pair, and the
        // older single lines (#3f8ad9, hidden in 2D) are not used – so the 3D tracks join exactly where the 2D ones do
        // maps drawn with rail pairs (#4ba6ea); a yard map without them draws some tracks as two close #3f8ad9 lines (one track)
        // a map with only a few traced rail pairs beside many clean single lines (Richmond, Daly City shops) is laid from the clean lines
        const rail0 = [...svg.querySelectorAll('path[stroke="#4ba6ea"]')].filter(e => e.getTotalLength && e.getTotalLength() > 6);
        const railPairs = rail0.length * 2 >= svg.querySelectorAll('[stroke="#3f8ad9"]').length ? rail0 : [];
        // close #3f8ad9 pairs are one track on yard maps only; a shop map's leads run close together and each line is its own track
        const yardPairs = railPairs.length || isShop ? [] : [...svg.querySelectorAll('path[stroke="#3f8ad9"], polyline[stroke="#3f8ad9"], line[stroke="#3f8ad9"]')].filter(e => e.getTotalLength && e.getTotalLength() > 6);
        const pairs = railPairs.length ? railPairs : yardPairs, dMax = railPairs.length ? 15 : 13.5;
        if (pairs.length) {
          const smp = e => { const L = e.getTotalLength(), n = Math.max(6, Math.min(120, Math.ceil(L / 3))); return Array.from({ length: n + 1 }, (_, i) => e.getPointAtLength(L * i / n)); };
          const S = pairs.map(e => ({ e, a: smp(e), used: false }));
          const near = (q, B) => B.reduce((m, b) => { const d = Math.hypot(q.x - b.x, q.y - b.y); return d < m.d ? { d, b } : m; }, { d: 1e9, b: null });
          const mids = [];                                          // centre lines in the map's own units
          S.forEach(A => { if (A.used) return; let best = null, bm = 1e9;
            S.forEach(B => { if (B === A || B.used) return; const ds = A.a.map(q => near(q, B.a).d), m = ds.reduce((x, y) => x + y, 0) / ds.length;
              const ok = ds.filter(d => d > 4 && d < dMax).length / ds.length;   // the rails of a curve can differ in length at the ends
              if (ok > .6 && m < bm) { bm = m; best = B; } });
            A.used = true; if (best) best.used = true; if (!best && A.e.getTotalLength() < 14) return;
            const mid = best ? A.a.map(q => { const r = near(q, best.a); return r.d < dMax ? { x: (q.x + r.b.x) / 2, y: (q.y + r.b.y) / 2 } : null; }).filter(Boolean) : A.a.map(q => ({ x: q.x, y: q.y }));
            if (mid.length >= 2) mids.push({ m: mid, e: A.e }); });
          // where a branch leaves a track the rails spread apart and the centre line stops short: a loose end within 26 map units
          // of another track is joined to it, so every lead stays connected as on the 2D map
          mids.forEach((T, i) => [0, T.m.length - 1].forEach(k => { const q = T.m[k], r = T.m[k === 0 ? Math.min(2, T.m.length - 1) : Math.max(0, T.m.length - 3)];
            const dx = q.x - r.x, dy = q.y - r.y, dl = Math.hypot(dx, dy) || 1; let bp = null, bd = 26;
            mids.forEach((U, j) => { if (j === i) return; U.m.forEach(p => { const d = Math.hypot(p.x - q.x, p.y - q.y);
              const cos = ((p.x - q.x) * dx + (p.y - q.y) * dy) / ((d || 1) * dl);          // only straight on from the loose end
              if (d > .5 && d < bd && cos > .8) { bd = d; bp = p; } }); });
            if (bp) { if (k === 0) T.m.unshift({ x: bp.x, y: bp.y }); else T.m.push({ x: bp.x, y: bp.y }); } }));
          mids.forEach(({ m, e }) => { const M = e.getCTM && e.getCTM(); if (!M) return;
            const P = (x, y) => { const q = new DOMPoint(x, y).matrixTransform(M); return [wx(sb.x + q.x), wz(sb.y + q.y)]; };
            const mid = m.map(q => P(q.x, q.y)); track(mid, .2, "svg"); for (let i = 1; i < mid.length; i++) svgSegs.push([mid[i - 1][0], mid[i - 1][1], mid[i][0], mid[i][1]]); });
        }
        svg.querySelectorAll("line, polyline, path, rect").forEach(e => {
          const sc = (e.getAttribute("stroke") || "").toLowerCase();
          if (e.closest("marker, defs") || e.classList.contains("trk-in") || /^#3f8ad7$|^#4ba6ea$/.test(sc) || (pairs.length && sc === "#3f8ad9")) return;   // lines drawn for the 2D map only
          const M = e.getCTM && e.getCTM(); if (!M) return;
          const P = (x, y) => { const q = new DOMPoint(x, y).matrixTransform(M); return [wx(sb.x + q.x), wz(sb.y + q.y)]; };
          const tag = e.tagName.toLowerCase(), col = e.getAttribute("stroke") || "#222", dash = e.getAttribute("stroke-dasharray");
          if (e.hasAttribute("data-rail") && tag === "path" && e.getTotalLength) {   // one rail of the original screen's drawing
            const Lr = e.getTotalLength(), n = Math.max(1, Math.ceil(Lr / 2)), rp = []; for (let i = 0; i <= n; i++) { const p = e.getPointAtLength(Lr * i / n); rp.push(P(p.x, p.y)); }
            return;   // the 2D drawing's rails are not laid in 3D: the map's own track centre lines are (no stray single-rail lines)
          }
          if (tag === "rect") {
            const x = +e.getAttribute("x") || 0, y = +e.getAttribute("y") || 0, rw = +e.getAttribute("width"), rh = +e.getAttribute("height");
            const [x0, z0] = P(x, y), [x1, z1] = P(x + rw, y + rh), bw = Math.abs(x1 - x0), bd = Math.abs(z1 - z0);
            if (bw < .05 || bd < .05 || col === "none") return;
            const cxw = (x0 + x1) / 2, czw = (z0 + z1) / 2;
            if (isTowerBox(box(e))) { tower(cxw, czw, bw, bd); return; }
            if (dash) {                                         // dashed outline = a building / pit area: glass walls
              const hgt = Math.max(.8, Math.min(2, Math.min(bw, bd) * .45)), geo = new THREE.BoxGeometry(bw, hgt, bd);
              const walls = new THREE.Mesh(geo, mats.glass); walls.position.set(cxw, hgt / 2, czw); walls.renderOrder = 2; extras.add(walls);
              const ed = new THREE.LineSegments(new THREE.EdgesGeometry(geo), mats.glassEdge); ed.position.copy(walls.position); extras.add(ed);
              const slab = new THREE.Mesh(unitPlane, new THREE.MeshStandardMaterial({ color: 0xd3dee9, roughness: .8 }));
              slab.rotation.x = -Math.PI / 2; slab.scale.set(bw, bd, 1); slab.position.set(cxw, -.008, czw); slab.receiveShadow = true; extras.add(slab);
            } else {                                            // solid outline (pits, lifts): a glowing frame on the floor
              const c3 = new THREE.Color(hex(col));
              const fm = new THREE.MeshStandardMaterial({ color: c3, emissive: c3, emissiveIntensity: .6, roughness: .5, side: THREE.DoubleSide });
              const Sp = new Soup(); strip(Sp, [[x0, z0], [x1, z0], [x1, z1], [x0, z1], [x0, z0]], .035, .07, 0); const fr = Sp.mesh(fm); if (fr) extras.add(fr);
              const floor = new THREE.Mesh(unitPlane, new THREE.MeshBasicMaterial({ color: c3, transparent: true, opacity: .14, depthWrite: false }));
              floor.rotation.x = -Math.PI / 2; floor.scale.set(bw, bd, 1); floor.position.set(cxw, .012, czw); extras.add(floor);
            }
            return;
          }
          let pts = [];
          if (tag === "line") pts = [P(+e.getAttribute("x1"), +e.getAttribute("y1")), P(+e.getAttribute("x2"), +e.getAttribute("y2"))];
          else if (tag === "polyline") pts = [...e.points].map(p => P(p.x, p.y));
          else if (tag === "path" && e.getTotalLength) {
            const L = e.getTotalLength(), n = Math.max(2, Math.min(90, Math.ceil(L * Math.abs(M.a) / 10)));
            for (let i = 0; i <= n; i++) { const p = e.getPointAtLength(L * i / n); pts.push(P(p.x, p.y)); }
          }
          if (pts.length < 2) return;
          if (isTrack(col) && !dash && !isArrow(e)) { track(pts, .2, /^#3f8ad6$/i.test(col) ? "xo" : "svg"); for (let i = 1; i < pts.length; i++) svgSegs.push([pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]]); }
          else {
            const sw2 = Math.max(.018, (+e.getAttribute("stroke-width") || 1.4) * .014);
            if (dash) { for (let i = 0; i < pts.length - 1; i++) { const a = pts[i], b = pts[i + 1], L = Math.hypot(b[0] - a[0], b[1] - a[1]), n = Math.max(1, Math.floor(L / .18));
              for (let k = 0; k < n; k += 2) strip(inks, [[a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n], [a[0] + (b[0] - a[0]) * (k + 1) / n, a[1] + (b[1] - a[1]) * (k + 1) / n]], sw2, .02); } }
            else inkLines.push(Object.assign(pts, { sw: sw2 }));
            if (e.getAttribute("marker-end")) {
              const a = pts[pts.length - 2], b = pts[pts.length - 1], ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
              const hd = new THREE.Mesh(new THREE.ConeGeometry(.09, .26, 3), mats.ink);
              hd.rotation.set(0, -ang, -Math.PI / 2); hd.position.set(b[0] - Math.cos(ang) * .13, .03, b[1] - Math.sin(ang) * .13); extras.add(hd);
            }
          }
        });
      });

      joinEnds(inkLines, .15).forEach(L => strip(inks, authored ? L : filletPts(L, .9), L.sw || .02, .02));   // copied maps keep the screen's sharp arrow corners     // drawn connecting lines: no square corners either

      /* car positions: one continuous bed per straight run of positions (rows, columns or diagonal lines), the
       * signal at the end where the track's tag is and the buffer stop at the other end */
      const cells = [...root.querySelectorAll(".cell, .k-inp.ro, .mv-slot")].filter(own);
      const runs = new Map();
      cells.forEach(e => { const b = box(e); e._b = b; e._geo = null; if (!inside(b) || e.classList.contains("turn")) return; const k = tk(e); (runs.get(k) || runs.set(k, []).get(k)).push(e); });
      /* the storage cars ST-A..ST-D stand one behind the other on the one sloping track: lay them as a single run */
      if (isShop && !authored) { const st4 = [...runs.keys()].filter(k => /^ST-[A-D]$/.test(k)); if (st4.length >= 3) { const all = []; st4.forEach(k => { all.push(...runs.get(k)); runs.delete(k); }); runs.set("ST-A", all); } }
      const tags = [...root.querySelectorAll(".tlabel, .tag, .mv-trk")].filter(own);
      const tagName = e => (e.getAttribute("aria-label") || e.textContent || "").replace(/\s+/g, " ").trim();
      const tagOf = tid => tags.find(e => e.dataset.open === tid || tagName(e) === tid);
      const groupBy = (list, fn) => { const m = new Map(); list.forEach(e => { const k = Math.round(fn(e._b) / 4); (m.get(k) || m.set(k, []).get(k)).push(e); }); return [...m.values()]; };
      const biggest = gs => Math.max(0, ...gs.map(g => g.length));
      const geoRow = e => { const b = e._b, cw = (e.offsetWidth || b.w) / K, cd = (e.offsetHeight || b.h) / K; return { x: wx(b.x + b.w / 2), z: wz(b.y + b.h / 2), ang: 0, len: cw * .92, wid: Math.min(Math.max(cd * 1.05, .34), .52) }; };
      const geoCol = e => { const b = e._b, cw = (e.offsetWidth || b.w) / K, cd = (e.offsetHeight || b.h) / K; return { x: wx(b.x + b.w / 2), z: wz(b.y + b.h / 2), ang: Math.PI / 2, len: cd * SZ * .88, wid: Math.min(cw * .6, .46) }; };
      function ends(pts, tagPt, hw0, tid) {
        const hw = TRACK_HW;
        const a = pts[0], b = pts[pts.length - 1];
        const near = tagPt && Math.hypot(tagPt[0] - b[0], tagPt[1] - b[1]) < Math.hypot(tagPt[0] - a[0], tagPt[1] - a[1]);
        const s0 = near ? b : a, e0 = near ? a : b, s1 = near ? pts[pts.length - 2] : pts[1], e1 = near ? pts[1] : pts[pts.length - 2];
        const ang = Math.atan2(e0[1] - e1[1], e0[0] - e1[0]), L = Math.hypot(e0[0] - e1[0], e0[1] - e1[1]) || 1;
        buffers.push([e0[0] + (e0[0] - e1[0]) / L * .08, e0[1] + (e0[1] - e1[1]) / L * .08, hw, ang]);
        const sa = Math.atan2(s0[1] - s1[1], s0[0] - s1[0]);
        signals.push([s0[0] - Math.sin(sa) * hw * 1.3, s0[1] + Math.cos(sa) * hw * 1.3, tid]);
      }
      runs.forEach((list, tid) => {
        const tg = tagOf(tid), tb = tg ? box(tg) : null, tagPt = tb ? [wx(tb.x + tb.w / 2), wz(tb.y + tb.h / 2)] : null;
        const stRun = isShop && tid === "ST-A" && list.length >= 3;
        const ord = list.slice().sort((a, b) => stRun ? a._b.x - b._b.x : ix(a) - ix(b));
        if (authored) {
          ord.forEach(e => { const b = e._b, c = [wx(b.x + b.w / 2), wz(b.y + b.h / 2)], g = geoRow(e); let best = null;
            svgSegs.forEach(q => { const dx = q[2] - q[0], dz = q[3] - q[1], L2 = dx * dx + dz * dz; if (L2 < 1e-4) return;
              const t = Math.max(0, Math.min(1, ((c[0] - q[0]) * dx + (c[1] - q[1]) * dz) / L2)), x = q[0] + dx * t, z = q[1] + dz * t, d = Math.hypot(c[0] - x, c[1] - z);
              const ang = Math.atan2(dz * Math.sign(dx || 1), Math.abs(dx)), sc = d + Math.abs(Math.sin(ang)) * .3;   // a level track wins a near tie: a car on a straight is not put on a turnout curve
              if (!best || sc < best.sc) best = { x, z, d, sc, ang }; });
            e._geo = best && best.d < .8 ? { x: best.x, z: best.z, ang: best.ang, len: g.len, wid: g.wid, diag: Math.abs(best.ang) > .02 } : g; });
          return;
        }
        let diag = 0;
        for (let i = 1; i < ord.length; i++) { const p = ord[i - 1]._b, q = ord[i]._b; if (Math.abs((q.y + q.h / 2) - (p.y + p.h / 2)) > 4 && Math.abs((q.x + q.w / 2) - (p.x + p.w / 2)) > 4) diag++; }
        // a sloping run needs at least three cells; two flat cells on different lines (1N-1 / 1N-2) stay flat, as the 2D map draws them
        if (ord.length >= 3 && diag >= (ord.length - 1) * .4) {
          let C = ord.map(e => [wx(e._b.x + e._b.w / 2), wz(e._b.y + e._b.h / 2)]);
          if (stRun) {                                                  // put the cars on the sloping rail they stand beside
            const sl = svgSegs.filter(q => Math.abs(q[1] - q[3]) > .06 && Math.hypot(q[2] - q[0], q[3] - q[1]) > .4), a0 = C[0], b0 = C[C.length - 1], l0 = Math.hypot(b0[0] - a0[0], b0[1] - a0[1]) || 1;
            const hit = C.map(p => { let best = null; sl.forEach(q => { const dx = q[2] - q[0], dz = q[3] - q[1], L2 = dx * dx + dz * dz, Lq = Math.sqrt(L2);
              if (Math.abs((dx * (b0[0] - a0[0]) + dz * (b0[1] - a0[1])) / (Lq * l0)) < .8) return;
              const t = Math.max(0, Math.min(1, ((p[0] - q[0]) * dx + (p[1] - q[1]) * dz) / L2)), x = q[0] + dx * t, z = q[1] + dz * t, d = Math.hypot(p[0] - x, p[1] - z);
              if (d < 1.2 && (!best || d < best.d)) best = { x, z, d }; }); return best; }).filter(Boolean);
            // all of them on the one straight line through the rail points found (a car beside the rail's bend is not pulled off its track)
            if (hit.length >= 2) { const h0 = hit[0], h1 = hit[hit.length - 1], hl = Math.hypot(h1.x - h0.x, h1.z - h0.z) || 1, hx = (h1.x - h0.x) / hl, hz = (h1.z - h0.z) / hl;
              C = C.map(p => { const t = (p[0] - h0.x) * hx + (p[1] - h0.z) * hz; return [h0.x + hx * t, h0.z + hz * t]; }); }
          }
          const A = C[0], B = C[C.length - 1], L = Math.hypot(B[0] - A[0], B[1] - A[1]) || 1, ux = (B[0] - A[0]) / L, uz = (B[1] - A[1]) / L;
          const dev = Math.max(...C.map(p => Math.abs((p[0] - A[0]) * uz - (p[1] - A[1]) * ux))), straight = dev < .7;
          const P = straight ? C.map(p => { const t = (p[0] - A[0]) * ux + (p[1] - A[1]) * uz; return [A[0] + ux * t, A[1] + uz * t]; }) : C;
          const pitch = L / Math.max(1, C.length - 1), hw = .22;
          ord.forEach((e, i) => { const a = P[Math.max(0, i - 1)], b = P[Math.min(P.length - 1, i + 1)];
            e._geo = { x: P[i][0], z: P[i][1], ang: straight ? Math.atan2(uz, ux) : Math.atan2(b[1] - a[1], b[0] - a[0]), len: pitch * .9, wid: .52, diag: true }; });
          const pad0 = Math.min(pitch * .6, .7);
          const d0 = [P[1][0] - P[0][0], P[1][1] - P[0][1]], n0 = Math.hypot(d0[0], d0[1]) || 1;
          const d1 = [P[P.length - 1][0] - P[P.length - 2][0], P[P.length - 1][1] - P[P.length - 2][1]], n1 = Math.hypot(d1[0], d1[1]) || 1;
          const pts = [[P[0][0] - d0[0] / n0 * pad0, P[0][1] - d0[1] / n0 * pad0], ...P, [P[P.length - 1][0] + d1[0] / n1 * pad0, P[P.length - 1][1] + d1[1] / n1 * pad0]];
          pts.name = tid; track(pts, hw); ends(pts, tagPt, hw, tid);
          return;
        }
        const rows = groupBy(list, b => b.y + b.h / 2), cols = groupBy(list, b => b.x + b.w / 2), vert = biggest(cols) > biggest(rows);
        (vert ? cols : rows).forEach(line => {
          line.sort((a, b) => vert ? a._b.y - b._b.y : a._b.x - b._b.x);
          let seg = [line[0]];
          const flush = () => {
            const a = seg[0]._b, b = seg[seg.length - 1]._b;
            seg.forEach(e => { e._geo = geoRow(e); });
            let pts, hw;
            if (vert) { seg.forEach(e => { const g = e._geo, h = g.len / 2 + .12, p = [[g.x - h, g.z], [g.x + h, g.z]]; p.name = tid; track(p, Math.max(.2, g.wid * .55)); }); return; }
            else {
              /* a row drawn just above / below its track line sits ON that line (one track, not two side by side) */
              let z = wz(a.y + a.h / 2);
              const x0 = wx(a.x - 6), x1 = wx(b.x + b.w + 10), tol = a.h / K * SZ * 1.4, lv = new Map();
              if (isShop || !/^(TT|TF-0\d|R9\d)$/.test(tid)) svgSegs.forEach(q => {
                if (Math.abs(q[1] - q[3]) > .06) return;
                const qz = (q[1] + q[3]) / 2, d = Math.abs(qz - z); if (d >= tol) return;
                const ov = Math.min(x1, Math.max(q[0], q[2])) - Math.max(x0, Math.min(q[0], q[2])); if (ov <= 0) return;
                const k = Math.round(qz / .03), en = lv.get(k) || { z: qz, d, ov: 0 }; en.ov += ov; lv.set(k, en);
              });
              let best = null; lv.forEach(en => { if (en.ov > (x1 - x0) * .5 && (!best || en.d < best.d)) best = en; });
              // a car drawn on a sloping lead (ST-A beside the tail track) stays there – it is not pulled down onto the flat line
              if (best) { const cx = (x0 + x1) / 2; let ds = 1e9;
                svgSegs.forEach(q => { if (Math.abs(q[1] - q[3]) <= .06) return; const dx = q[2] - q[0], dz = q[3] - q[1], L = dx * dx + dz * dz || 1,
                  t = Math.max(0, Math.min(1, ((cx - q[0]) * dx + (z - q[1]) * dz) / L)); ds = Math.min(ds, Math.hypot(cx - q[0] - t * dx, z - q[1] - t * dz)); });
                if (ds < best.d) best = null; }
              let covered = false;
              if (best) {
                z = best.z;
                const iv = svgSegs.filter(q => Math.abs(q[1] - z) < .03 && Math.abs(q[3] - z) < .03).map(q => [Math.min(q[0], q[2]), Math.max(q[0], q[2])]).sort((m, n) => m[0] - n[0]);
                let reach = x0 + .3; iv.forEach(([m, n]) => { if (m <= reach + .05) reach = Math.max(reach, n); });
                covered = reach >= x1 - .3;
              }
              seg.forEach(e => { e._geo.z = z; });
              hw = Math.max(.2, a.h / K * .7); pts = [[x0, z], [x1, z]];
              if (covered) return;
            }
            pts.name = tid; track(pts, hw);
            if (seg.length > 1) ends(pts, tagPt, hw, tid);
          };
          for (let i = 1; i < line.length; i++) {
            const p = line[i - 1]._b, q = line[i]._b, gap = vert ? q.y - (p.y + p.h) : q.x - (p.x + p.w), unit = vert ? p.h : p.w;
            if (gap > unit * 1.6) { flush(); seg = []; }
            seg.push(line[i]);
          }
          flush();
        });
      });

      /* join the tracks: a drawn line that ends right beside another track (a curve into a straight, a lead into a
       * storage track) bends its last stretch onto it; a run of positions that stops just short of a crossing track meets it */
      (function joinTracks() {
        const near = (e, Q) => {
          let best = null;
          for (let m = 1; m < Q.length; m++) {
            const a = Q[m - 1], b = Q[m], dx = b[0] - a[0], dz = b[1] - a[1], L2 = dx * dx + dz * dz; if (!L2) continue;
            const t = Math.max(0, Math.min(1, ((e[0] - a[0]) * dx + (e[1] - a[1]) * dz) / L2)), q = [a[0] + dx * t, a[1] + dz * t];
            const g = Math.hypot(q[0] - e[0], q[1] - e[1]), L = Math.sqrt(L2);
            if (!best || g < best.g) best = { q, g, tx: dx / L, tz: dz / L };
          }
          return best;
        };
        routes.forEach(P => {
          return;                                                    // drawn lines keep their own ends
          [0, 1].forEach(endI => {
            if (endI) P.reverse();
            const e = P[0], o = P[1], dl = Math.hypot(e[0] - o[0], e[1] - o[1]) || 1, d = [(e[0] - o[0]) / dl, (e[1] - o[1]) / dl];
            let best = null;
            routes.forEach(Q => { if (Q === P) return; const n = near(e, Q); if (n && n.g > .03 && (n.g < .22 || (n.g < .4 && Math.abs(n.tx * d[0] + n.tz * d[1]) > .85)) && (!best || n.g < best.g)) best = n; });
            if (best) {
              let total = 0; for (let i = 1; i < P.length; i++) total += Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]);
              const D = Math.min(2.5, total * .5);
              if (dl > D) { const k = D / dl; P.splice(1, 0, [e[0] + (o[0] - e[0]) * k, e[1] + (o[1] - e[1]) * k]); }
              const dx = best.q[0] - e[0], dz = best.q[1] - e[1];
              let a = 0;
              for (let i = 0; i < P.length; i++) {
                if (i) a += Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]);
                if (a >= D) break;
                const u = a / D, w = 1 - u * u * (3 - 2 * u);
                P[i][0] += dx * w; P[i][1] += dz * w;
              }
            }
            if (endI) P.reverse();
          });
        });
        routes.forEach(P => {
          if (P.src === "svg" || P.length !== 2) return;
          [0, 1].forEach(endI => {
            const e = P[endI ? 1 : 0], o = P[endI ? 0 : 1], L = Math.hypot(e[0] - o[0], e[1] - o[1]) || 1, d = [(e[0] - o[0]) / L, (e[1] - o[1]) / L];
            let best = null;
            routes.forEach(Q => {
              if (Q === P) return;
              for (let m = 1; m < Q.length; m++) {
                const a = Q[m - 1], b = Q[m], sx = b[0] - a[0], sz = b[1] - a[1], den = d[0] * sz - d[1] * sx;
                if (Math.abs(den) < .3 * Math.hypot(sx, sz)) continue;
                const t = ((a[0] - e[0]) * sz - (a[1] - e[1]) * sx) / den, u = ((a[0] - e[0]) * d[1] - (a[1] - e[1]) * d[0]) / den;
                if (u < -.001 || u > 1.001 || t < -.35 || t > .35 || t > -.03 && t < .03) continue;
                if (!best || Math.abs(t) < Math.abs(best)) best = t;
              }
            });
            if (best != null && L + best > .5) { e[0] += d[0] * best; e[1] += d[1] * best; }
          });
        });
      })();

      /* no sharp corners: real track cannot turn 90 degrees on the spot. Drawn track pieces whose ends meet are
       * joined into one line, and every corner along a line becomes a curve (cars and trains could run round it) */
      (function roundCorners() {
        const svgR = routes.filter(p => p.src === "svg" && p.length >= 2);
        svgR.forEach(x => { const k = routes.indexOf(x); if (k >= 0) routes.splice(k, 1); const l = laid.findIndex(y => y[0] === x); if (l >= 0) laid.splice(l, 1); });
        /* every drawn line as drawn; only the part that runs along a row of car positions is left out (that row has its track) */
        const runR = routes.filter(p => p.src !== "svg" && p.src !== "xo" && p.length >= 2);
        const onRow = (q, d) => runR.some(R => { for (let m = 1; m < R.length; m++) { const a = R[m - 1], b = R[m], dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1;
          const t = ((q[0] - a[0]) * dx + (q[1] - a[1]) * dz) / (L * L); if (t < 0 || t > 1) continue;
          if (Math.hypot(a[0] + dx * t - q[0], a[1] + dz * t - q[1]) < .26 && Math.abs(d[0] * dx + d[1] * dz) / L > .94) return true; } return false; });
        /* soft curves: a drawn piece that crosses from one track to another (a crossover, a lead) becomes an S-curve that leaves
         * and joins each track along it; every other drawn line gets its corners rounded */
        const allR = runR.concat(svgR);
        const dSeg = (q, P) => { let g = 1e9; for (let m = 1; m < P.length; m++) { const a = P[m - 1], b = P[m], dx = b[0] - a[0], dz = b[1] - a[1], L2 = dx * dx + dz * dz || 1, t = Math.max(0, Math.min(1, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dz) / L2)); g = Math.min(g, Math.hypot(a[0] + dx * t - q[0], a[1] + dz * t - q[1])); } return g; };
        const tanNear = (q, self) => { let best = null; allR.forEach(R => { if (R === self) return; for (let m = 1; m < R.length; m++) { const a = R[m - 1], b = R[m], dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz); if (L < .05) continue;
          const t = Math.max(0, Math.min(1, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dz) / (L * L))), g = Math.hypot(a[0] + dx * t - q[0], a[1] + dz * t - q[1]);
          if (Math.abs(dz) / L < .2 && (!best || g < best.g)) best = { g, d: [dx / L, dz / L] }; } }); return best; };
        const soft = P => { if (P.length < 2) return P; const A = P[0], B = P[P.length - 1], L = Math.hypot(B[0] - A[0], B[1] - A[1]); if (L < .3) return P;
          const u = [(B[0] - A[0]) / L, (B[1] - A[1]) / L], dev = Math.max(...P.map(q => Math.abs((q[0] - A[0]) * u[1] - (q[1] - A[1]) * u[0])));
          if (dev < .25 && Math.abs(u[1]) > .17 && Math.abs(u[0]) > .17) { const ta = tanNear(A, P), tb = tanNear(B, P);
            if (ta && tb && ta.g < .45 && tb.g < .45) { const da = ta.d[0] * u[0] + ta.d[1] * u[1] < 0 ? [-ta.d[0], -ta.d[1]] : ta.d, db = tb.d[0] * u[0] + tb.d[1] * u[1] < 0 ? [-tb.d[0], -tb.d[1]] : tb.d, k = L * .5;
              const c1 = [A[0] + da[0] * k, A[1] + da[1] * k], c2 = [B[0] - db[0] * k, B[1] - db[1] * k], out = [];
              for (let i = 0; i <= 28; i++) { const t = i / 28, a = (1 - t) ** 3, b = 3 * (1 - t) ** 2 * t, c = 3 * (1 - t) * t * t, d = t ** 3; out.push([a * A[0] + b * c1[0] + c * c2[0] + d * B[0], a * A[1] + b * c1[1] + c * c2[1] + d * B[1]]); }
              return out; } }
          return P.length > 2 ? filletPts(P, .55) : P; };
        const bent = P => { const A = P[0], B = P[P.length - 1], L = Math.hypot(B[0] - A[0], B[1] - A[1]) || 1; return P.some(q => Math.abs((q[0] - A[0]) * (B[1] - A[1]) - (q[1] - A[1]) * (B[0] - A[0])) / L > .06); };
        const plen = P => { let L = 0; for (let i = 1; i < P.length; i++) L += Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]); return L; };
        const attached = (q, self) => allR.some(R => R !== self && dSeg(q, R) < .3);
        const pieces = joinEnds(svgR.map(p => p.slice()), .3, .92);          // the halves of a drawn diagonal are one track
        /* crossovers (the client's X and single diagonals): each diagonal is one soft S-curve from track to track */
        const curves = routes.filter(p => p.src === "xo").map(p => { const a = p[0][1] < p[p.length - 1][1] ? p[0] : p[p.length - 1], b = a === p[0] ? p[p.length - 1] : p[0];
          const gap = b[1] - a[1], sg = Math.sign(b[0] - a[0]) || 1, ext = gap * .45, A = [a[0] - sg * ext, a[1]], Bq = [b[0] + sg * ext, b[1]], k = Math.abs(Bq[0] - A[0]) * .5, c1 = [A[0] + sg * k, A[1]], c2 = [Bq[0] - sg * k, Bq[1]], out = [];
          for (let i = 0; i <= 36; i++) { const t = i / 36, w0 = (1 - t) ** 3, w1 = 3 * (1 - t) ** 2 * t, w2 = 3 * (1 - t) * t * t, w3 = t ** 3; out.push([w0 * A[0] + w1 * c1[0] + w2 * c2[0] + w3 * Bq[0], w0 * A[1] + w1 * c1[1] + w2 * c2[1] + w3 * Bq[1]]); }
          return { pts: out, x0: Math.min(A[0], Bq[0]), x1: Math.max(A[0], Bq[0]), z0: a[1], z1: b[1], ax: a[0], bx: b[0] }; });
        routes.filter(p => p.src === "xo").forEach(x => { const k = routes.indexOf(x); routes.splice(k, 1); const l = laid.findIndex(y => y[0] === x); if (l >= 0) laid.splice(l, 1); });
        /* the strokes the 2D map draws inside a crossover are not laid again */
        const inX = P => curves.some(c => P.every(q => q[0] > c.x0 - .15 && q[0] < c.x1 + .15 && q[1] > c.z0 - .45 && q[1] < c.z1 + .45));
        /* where a crossover joins two tracks there is no buffer stop */
        for (let i = buffers.length - 1; i >= 0; i--) { const b = buffers[i]; if (curves.some(c => b[0] > c.x0 - .4 && b[0] < c.x1 + .4 && b[1] > c.z0 - .5 && b[1] < c.z1 + .5)) buffers.splice(i, 1); }
        /* the two through tracks of an X keep running straight across it */
        const thru = new Map(); curves.forEach(c => { const x0 = Math.min(c.ax, c.bx), x1 = Math.max(c.ax, c.bx);
          [c.z0, c.z1].forEach(z => { const key = Math.round(z * 10) + ":" + Math.round(x0 * 2); thru.set(key, (thru.get(key) || []).concat([[x0 - .3, z, x1 + .3]])); }); });
        thru.forEach(list => { if (list.length < 2) return; const [x0, z, x1] = list[0]; const p = [[x0, z], [x1, z]]; p.src = "svg"; routes.push(p); laid.push([p, TRACK_HW, isShop ? 1 : 0]); });
        const plain = pieces;
        curves.map(c => c.pts).concat(plain.filter(P => !inX(P) && (plen(P) >= 1.2 || (attached(P[0], P) && attached(P[P.length - 1], P)))).map(soft)).forEach((P0, iP) => { const P = P0; P.src = "svg";
          const pts = []; for (let i = 1; i < P.length; i++) { const a = P[i - 1], b = P[i], L = Math.hypot(b[0] - a[0], b[1] - a[1]), n = Math.max(1, Math.ceil(L / .12));
            for (let k = i > 1 ? 1 : 0; k <= n; k++) pts.push([a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n]); }
          const dirAt = i => { const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], L = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1; return [(b[0] - a[0]) / L, (b[1] - a[1]) / L]; };
          const on = pts.map((q, i) => onRow(q, dirAt(i)));
          let cur = [];
          const flush = () => { if (cur.length >= 2) { const s = [cur[0]]; cur.forEach(q => { const l = s[s.length - 1]; if (Math.hypot(q[0] - l[0], q[1] - l[1]) > .1) s.push(q); });
            if (s.length >= 2) { s.src = "svg"; routes.push(s); laid.push([s, TRACK_HW, isShop || bent(s) ? 1 : 0]); } } cur = []; };
          pts.forEach((q, i) => { if (!on[i]) { if (!cur.length && i) cur.push(pts[i - 1]); cur.push(q); } else if (cur.length) { cur.push(q); flush(); } });
          flush();
        });
      })();

      /* the shop map train runs on the longest joined-up stretch of track with no parked cars; a sample track when there is none */
      let sampleAt = null;
      const trainPath = isShop && !RM && !authored ? findTrainPath() : null;   // a map copied from the original screen gets nothing the screen does not draw
      function findTrainPath() {
        const segs = routes.filter(p => p.length >= 2).map(p => p.map(q => [q[0], q[1]]));
        const len = p => { let L = 0; for (let i = 1; i < p.length; i++) L += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]); return L; };
        const dirAt = (p, end) => { const a = end ? p[p.length - 2] : p[1], b = end ? p[p.length - 1] : p[0]; const L = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1; return [(b[0] - a[0]) / L, (b[1] - a[1]) / L]; };
        function grow(p, used) {
          for (let k = 0; k < 8; k++) {
            const e = p[p.length - 1], d = dirAt(p, true);
            let best = null;
            segs.forEach((q, j) => {
              if (used.has(j)) return;
              [false, true].forEach(rev => {
                const qq = rev ? q.slice().reverse() : q, s0 = qq[0];
                if (Math.hypot(s0[0] - e[0], s0[1] - e[1]) > .3) return;
                const d2 = [-dirAt(qq, false)[0], -dirAt(qq, false)[1]];
                if (d[0] * d2[0] + d[1] * d2[1] > .8 && (!best || len(qq) > best.L)) best = { j, qq, L: len(qq) };
              });
            });
            if (!best) break;
            used.add(best.j); p = p.concat(best.qq.slice(1));
          }
          return p;
        }
        const carPts = cells.filter(e => { const t = cellText(e); return e._b && inside(e._b) && t && t !== "-" && !e.classList.contains("turn"); })
          .map(e => e._geo ? [e._geo.x, e._geo.z] : [wx(e._b.x + e._b.w / 2), wz(e._b.y + e._b.h / 2)]);
        const free = p => carPts.every(q => {
          for (let i = 1; i < p.length; i++) {
            const a = p[i - 1], b = p[i], dx = b[0] - a[0], dz = b[1] - a[1], L2 = dx * dx + dz * dz || 1;
            const t = Math.max(0, Math.min(1, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dz) / L2));
            if (Math.hypot(a[0] + dx * t - q[0], a[1] + dz * t - q[1]) < .4) return false;
          }
          return true;
        });
        let best = null;
        segs.forEach((p, i) => {
          const used = new Set([i]);
          let r = grow(p, used); r = grow(r.slice().reverse(), used);
          const L = len(r);
          if (L >= 8 && free(r) && (!best || L > best.L)) best = { r, L };
        });
        // every shop map has its sample track along the bottom, where the train runs
        let x0 = Infinity, x1 = -Infinity, z1 = -Infinity;
        routes.forEach(p => p.forEach(q => { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); z1 = Math.max(z1, q[1]); }));
        if (!isFinite(x0)) { x0 = -mapW / 2; x1 = mapW / 2; z1 = mapD / 2 - 2; }
        const zS = z1 + 1.5, a = x0 + .5, b = Math.max(x1 - .5, a + 14), pts = [[a, zS], [b, zS]];
        track(pts, .22, "sample"); sampleAt = [a, zS];
        return pts;
      }
      if (authored && drawnRails.length) {
        // a copied map: the track is the original screen's own rails; a centre line only adds track where the screen draws none (inside a car box)
        const grid = new Map(), key = (x, z) => Math.round(x / .5) + "," + Math.round(z / .5);
        drawnRails.forEach((r, ri) => r.forEach(p => { const k = key(p[0], p[1]); (grid.get(k) || grid.set(k, []).get(k)).push([p[0], p[1], ri]); }));
        const nearRail = (x, z, r, skip) => { let best = null; for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) (grid.get((Math.round(x / .5) + a) + "," + (Math.round(z / .5) + b)) || []).forEach(q => { if (q[2] === skip) return; const d = Math.hypot(q[0] - x, q[1] - z); if (d < r && (!best || d < best.d)) best = { d, q }; }); return best; };
        const GAUGE = .35;                                                     // a rail pair of the drawing, about 12px apart, in world units
        // each rail pair of the drawing becomes one track along its centre line; a rail with no partner beside it is not track
        drawnRails.forEach((r, ri) => {
          if (r.length < 2) return;
          let run = []; const flush = () => { if (run.length > 1) layTrack(run, TRACK_HW, 0); run = []; };
          for (let i = 0; i < r.length; i++) {
            const a = r[Math.max(0, i - 1)], b = r[Math.min(r.length - 1, i + 1)], L = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1, ux = (b[0] - a[0]) / L, uz = (b[1] - a[1]) / L, [x, z] = r[i];
            const m = [nearRail(x - uz * GAUGE, z + ux * GAUGE, GAUGE * .45, ri), nearRail(x + uz * GAUGE, z - ux * GAUGE, GAUGE * .45, ri)].filter(q => q && q.q[2] > ri)[0];   // the pair is laid once, from its lower-numbered rail
            if (m) run.push([(x + m.q[0]) / 2, (z + m.q[1]) / 2]); else flush();
          }
          flush();
        });
        // centre lines where the screen hides the rails (inside a car box) still get track
        laid.forEach(([pts, hw, lite]) => { let run = []; const flush = () => { if (run.length > 1) layTrack(run, hw, lite); run = []; };
          for (let i = 0; i < pts.length; i++) { const p = pts[i]; if (nearRail(p[0], p[1], GAUGE * .9, -1)) { if (run.length) { run.push(p); flush(); } } else run.push(p); }
          flush(); });
      } else laid.forEach(([pts, hw, lite]) => layTrack(pts, hw, lite));
      [[shoulders, mats.shoulder], [beds, mats.ballast], [rails, mats.rail], [inks, mats.ink]].forEach(([Sx, mt]) => { const me = Sx.mesh(mt); if (me) { me.receiveShadow = true; scene.add(me); } });
      const d3 = new THREE.Object3D();
      function instanced(geo, mat, list, setup, shadow) {
        if (!list.length) return;
        const im = new THREE.InstancedMesh(geo, mat, list.length);
        list.forEach((it, i) => { setup(d3, it); d3.updateMatrix(); im.setMatrixAt(i, d3.matrix); });
        im.receiveShadow = true; im.castShadow = !!shadow; scene.add(im);
      }
      instanced(unitBox, mats.tie, ties, (d, [x, z, a, hw]) => { d.position.set(x, .058, z); d.rotation.set(0, -a, 0); d.scale.set(.07, .025, hw * 2.05); });
      /* track ends close softly in the bed's own ramp (bedStrip), as in MARIS-Web: no buffer block */
      const poleGeo = new THREE.CylinderGeometry(.018, .018, .7, 6), lampGeo = new THREE.SphereGeometry(.06, 12, 10);
      instanced(poleGeo, mats.pole, signals, (d, [x, z]) => { d.position.set(x, .35, z); d.rotation.set(0, 0, 0); d.scale.set(1, 1, 1); }, true);
      /* signal tips glow in the status colour of their track (worst car first: holds, high PM, usable, GO) */
      const lampMat = {};
      Object.keys(STATUS).forEach(k => { lampMat[k] = new THREE.MeshBasicMaterial({ color: STATUS[k].color, toneMapped: false }); });
      st.lamps = signals.map(([x, z, tid]) => { const m = new THREE.Mesh(lampGeo, lampMat.hy); m.position.set(x, .74, z); scene.add(m); return { m, tid }; });
      const RANK = { cy: 0, dy: 0, ry: 0, hy: 0, xx: 0 };
      function worst(tid) { let g = "hy"; st.cars.forEach((q, e) => { if (q.g && tk(e) === tid && RANK[q.g] > RANK[g]) g = q.g; }); return g; }

      /* track tags, labels and boxes: raised tags; clickable when the map makes them clickable */
      const marks = [];
      root.querySelectorAll(".tlabel, .tag, .mbox, .mv-trk").forEach(e => {
        if (!own(e)) return; const b = box(e); if (!inside(b)) return;
        const ow = e.offsetWidth || b.w, oh = e.offsetHeight || b.h;
        const text = (e.getAttribute("aria-label") || e.innerText || e.textContent || e.value || "").replace(/\s+/g, " ").trim(); if (!text) return;
        const isTag = e.classList.contains("tag"), isBox = e.classList.contains("mbox");
        const fg = isTag ? "#2a1d06" : isBox ? "#dbeeff" : "#ffffff";
        const bg = isTag ? ["#ffcf73", "#f2b640"] : isBox ? ["#2c5d93", "#244e7d"] : ["#2b5c9e", "#1e3a5f"];
        const top = new THREE.MeshStandardMaterial({ map: labelTex(text, { fg, bg, ar: ow / oh, font: MONO, r: 14 }), roughness: .6, transparent: true });
        const sideM = new THREE.MeshStandardMaterial({ color: isTag ? 0xc98a1f : isBox ? 0x1f4570 : 0x17304f, roughness: .7 });
        const hgt = isTag ? .1 : .08, mesh = new THREE.Mesh(unitBox, [sideM, sideM, top, sideM, sideM, sideM]);
        mesh.scale.set(ow / K * 1.05, hgt, Math.max(oh / K * 1.15, .3));
        mesh.position.set(wx(b.x + b.w / 2), hgt / 2 + .02, wz(b.y + b.h / 2));
        marks.push([mesh.position.x, mesh.position.z]);
        const rot = /rotate\(([-\d.]+)deg\)/.exec(e.style.transform || ""); if (rot) mesh.rotation.y = -rot[1] * Math.PI / 180;
        mesh.castShadow = true; mesh.receiveShadow = true; scene.add(mesh);
        if (opens(e)) {
          mesh.userData = { dom: e, kind: "sign", text, base: mesh.position.y, side: sideM, sideHot: new THREE.MeshStandardMaterial({ color: 0xf2a93b, emissive: 0xf2a93b, emissiveIntensity: .45 }) };
          st.picks.push(mesh);
        }
      });

      /* captions drawn on the floor (yard names, PIT, TAIL TRACK, building names ...) */
      /* a caption's lines from its markup (the drawing is hidden while the 3D view shows, so the browser reports no rendered text) */
      const capLines = e => { const c = e.cloneNode(true); c.querySelectorAll("br").forEach(x => x.replaceWith("\n")); return c.textContent.replace(/\u00a0/g, " ").split("\n").map(q => q.replace(/\s+/g, " ").trim()).filter(Boolean); };
      const texts = [...root.querySelectorAll(".mtxt, .k-lbl")].filter(own);
      if (texts.length) {
        const R = Math.min(2, 4096 / Math.max(W, H * SZ));
        const dc = document.createElement("canvas"); dc.width = Math.round(W * R); dc.height = Math.round(H * SZ * R);
        const g = dc.getContext("2d"); g.scale(R, R); g.textBaseline = "top";
        const occ = [...root.querySelectorAll(".cell, .k-inp.ro, .mv-slot")].filter(own).map(c => { const q = box(c), cy = (q.y + q.h / 2) * SZ; return { x0: q.x - 2, x1: q.x + q.w + 2, y0: cy - 26, y1: cy + 14 }; });
        const placed = [];
        texts.forEach(e => {
          const b = box(e); if (!inside(b)) return;
          const cs = getComputedStyle(e), lines = capLines(e); if (!lines.length) return;
          { const fs = parseFloat(cs.fontSize) || 10, lh0 = parseFloat(cs.lineHeight) || fs * 1.2, hgt = lines.length * lh0, ctr = cs.textAlign === "center";
            g.font = cs.fontWeight + " " + cs.fontSize + " " + cs.fontFamily; const tw = Math.max(...lines.map(q => g.measureText(q).width));
            const lx0 = ctr ? b.x + b.w / 2 - tw / 2 : b.x, lx1 = lx0 + tw, ly = (b.y + b.h / 2) * SZ - b.h / 2;
            const hit = y => occ.concat(placed).some(o => o.x0 < lx1 && o.x1 > lx0 && o.y0 < y + hgt && o.y1 > y);
            let best = 0; if (hit(ly)) { for (let d = 2; d < 60; d += 2) { if (!hit(ly - d)) { best = -d; break; } if (!hit(ly + d)) { best = d; break; } } }
            e._dy = best / SZ; placed.push({ x0: lx0, x1: lx1, y0: ly + best, y1: ly + best + hgt }); }
          // each caption is its own label at its 2D place on the ground, drawn after the cars so none of them hides it
          const fs = parseFloat(cs.fontSize) || 10, lh = parseFloat(cs.lineHeight) || fs * 1.2, center = cs.textAlign === "center";
          const font = cs.fontWeight + " " + cs.fontSize + " " + cs.fontFamily; g.font = font;
          const tw = Math.max(...lines.map(q => g.measureText(q).width)) + 4, th = lines.length * lh + 2, K2 = 4;
          const lc = document.createElement("canvas"); lc.width = Math.ceil(tw * K2); lc.height = Math.ceil(th * K2);
          const lg = lc.getContext("2d"); lg.scale(K2, K2); lg.font = font; lg.textBaseline = "top"; lg.textAlign = "left"; lg.fillStyle = hex(cs.color);
          lines.forEach((q, i) => lg.fillText(q, 2, 1 + i * lh + Math.max(0, (lh - fs) / 2)));
          const lt = new THREE.CanvasTexture(lc); lt.anisotropy = 8; lt.encoding = THREE.sRGBEncoding;
          const lx = center ? b.x + b.w / 2 - tw / 2 : b.x - 2, ly = (b.y + b.h / 2) * SZ - b.h / 2 + (e._dy || 0) * SZ - 1;
          const pw = tw / K, pd = th / K, cx3 = wx(lx + tw / 2), cz3 = ((ly + th / 2) - H * SZ / 2) / K;
          const lbl = new THREE.Mesh(new THREE.PlaneGeometry(pw, pd), new THREE.MeshBasicMaterial({ map: lt, transparent: true, depthWrite: false, toneMapped: false }));
          lbl.material.depthTest = false;                       // drawn over everything, at its own place on the ground: never hidden by a car
          lbl.rotation.x = -Math.PI / 2; lbl.position.set(cx3, .012, cz3); lbl.renderOrder = 20; scene.add(lbl);
        });
      }

      /* every car on a map is the same size: cars on diagonal tracks take the map's usual car length (never more than their spacing allows) */
      (() => { const ls = cells.map(e => e._geo).filter(g => g && !g.diag).map(g => g.len).sort((a, b) => a - b), std = ls.length ? ls[ls.length >> 1] : 1.7;
        cells.forEach(e => { if (e._geo && e._geo.diag) e._geo.len = Math.min(e._geo.len, std); }); })();
      /* cars + empty slots; the pseudo track spot is a tall signal */
      cells.forEach(e => {
        const b = e._b; if (!inside(b)) return;
        const cw = (e.offsetWidth || b.w) / K, cd = (e.offsetHeight || b.h) / K, x = wx(b.x + b.w / 2), z = wz(b.y + b.h / 2);
        if (e.classList.contains("turn")) {                   // the turn circle's spot is an empty car slot, as on the 2D map
          const pad = new THREE.Mesh(unitPlane, mats.pad);
          pad.rotation.x = -Math.PI / 2; pad.renderOrder = 1; pad.position.set(x, .106, z); pad.scale.set(cw * .92, Math.min(Math.max(cd * 1.05, .34), .52), 1);
          pad.userData = { dom: e, kind: "sign", text: e.title || "Pseudo Track", base: .106 }; scene.add(pad); st.picks.push(pad);
          return;
        }
        const G = e._geo || { x, z, ang: 0, len: cw * .92, wid: Math.min(Math.max(cd * 1.05, .34), .52) };
        const pad = new THREE.Mesh(unitPlane, e.classList.contains("wash") ? mats.wash : mats.pad);
        pad.rotation.order = "YXZ"; pad.rotation.set(-Math.PI / 2, -G.ang, 0); pad.renderOrder = 1; pad.position.set(G.x, .106, G.z);
        pad.scale.set(G.len, Math.min(G.wid * 1.05, .5), 1);
        pad.userData = { dom: e, kind: "pad", base: .106 }; scene.add(pad); st.picks.push(pad);
        st.cars.set(e, { pad, x: G.x, z: G.z, G, key: "", grp: null, g: null });
      });

      /* one rail car, length along x: two wheel trucks with hubs on a dark underframe, the painted body, roof units, the number on the roof */
      function gear(grp, len, wid) {
        const under = new THREE.Mesh(unitBox, mats.under); under.scale.set(len * .9, .035, wid * .82); under.position.y = .073; grp.add(under);
        [-1, 1].forEach(e => {
          const bx = e * len * .32, bg = new THREE.Mesh(unitBox, mats.bogie); bg.scale.set(Math.min(.24, len * .2), .055, wid * .9); bg.position.set(bx, .045, 0); grp.add(bg);
          [-1, 1].forEach(sd => { const w = new THREE.Mesh(wheelGeo, mats.hub); w.rotation.x = Math.PI / 2; w.scale.set(.038, wid * .92, .038); w.position.set(bx + sd * Math.min(.065, len * .055), .04, 0); grp.add(w); });
        });
      }
      function buildCar(grp, len, wid, k, roof) {
        const M = carMat[k], Hc = .27, y0 = .085;
        const bodyM = new THREE.Mesh(carGeo(), [M.side, M.shell]);                    // sides: the livery; roof and rounded ends: the shell
        bodyM.scale.set(len, Hc, wid); bodyM.position.y = y0; bodyM.castShadow = true; bodyM.receiveShadow = true; grp.add(bodyM);
        gear(grp, len, wid);
        if (roof && roof.map) {                                                         // the car number on the roof, readable from above
          const lab = new THREE.Mesh(unitPlane, roof); lab.rotation.x = -Math.PI / 2; lab.scale.set(len * .72, wid * .6, 1); lab.position.y = y0 + Hc + .004; grp.add(lab);
        }
        [-1, 1].forEach(e => {
          const w = new THREE.Mesh(unitPlane, endGlass); w.rotation.y = e * Math.PI / 2; w.scale.set(wid * .56, Hc * .34, 1); w.position.set(e * (len / 2 + .003), y0 + Hc * .64, 0); grp.add(w);
          const ru = new THREE.Mesh(unitBox, mats.roofUnit); ru.scale.set(len * .12, .03, wid * .46); ru.position.set(e * len * .3, y0 + Hc + .015, 0); grp.add(ru);
        });
        return bodyM;
      }
      function paint(e) {
        const q = st.cars.get(e); if (!q) return;
        const car = carState(e, vid), wash = e.classList.contains("wash"), grpK = car ? car.g : null;
        const key = (car ? car.text : "") + "|" + grpK + "|" + wash;
        if (key === q.key) return;
        q.key = key; q.g = grpK; q.pad.material = wash ? mats.wash : mats.pad;
        const drop0 = q.grp && q.grp.userData.drop;
        if (q.grp) { scene.remove(q.grp); st.picks = st.picks.filter(p => p.userData.grp !== q.grp); if (st.hover && st.hover.userData.grp === q.grp) st.hover = null; q.grp = null; q.body = null; }
        if (!car) return;
        const grp = new THREE.Group(); grp.position.set(q.x, .1, q.z); grp.rotation.y = -q.G.ang;
        const len = q.G.len, wid = q.G.wid;
        const roof = new THREE.MeshStandardMaterial({ map: labelTex(car.text, { fg: "#10202f", bg: [mixW(STATUS[grpK].color, .18), STATUS[grpK].color], ar: (len * .72) / (wid * .6), font: MONO, r: 18 }), roughness: .45, metalness: .15 });
        const bodyM = buildCar(grp, len, wid, grpK, roof);
        if (false) { const halo = new THREE.Mesh(ringGeo, mats.halo); halo.rotation.x = -Math.PI / 2; halo.scale.set(len * .6, wid * .95, 1); halo.position.y = .012; grp.add(halo); }
        bodyM.userData = { dom: e, kind: "car", grp, text: car.text, base: .1, g: grpK };
        st.picks.push(bodyM); q.grp = grp; q.body = bodyM; grp.visible = !st.hide.has(grpK); scene.add(grp);
        if (st.introOn && !RM) { grp.userData.drop = { i: st.introN++ }; grp.position.y = 6.1; } else if (drop0) { grp.userData.drop = drop0; grp.position.y = 6.1; }
        if (st.arriving && st.arriving.text === car.text && !RM) {
          /* the car runs along the rails from where it stood to its new position */
          const path = st.arriving.path && st.arriving.path.length > 1 ? st.arriving.path : [st.arriving.from, [q.x, q.z]];
          const cum = [0]; for (let i = 1; i < path.length; i++) cum.push(cum[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]));
          const L = cum[cum.length - 1];
          grp.userData.anim = { path, cum, L, t0: performance.now(), dur: animMs(L), heading: null };
          grp.position.set(path[0][0], .1, path[0][1]);
          st.arriving = null; kick();
        }
      }
      const lin = mt => { if (!mt || mt._lin) return; mt._lin = 1; if (mt.color) mt.color.convertSRGBToLinear(); if (mt.emissive) mt.emissive.convertSRGBToLinear(); };
      Object.values(mats).forEach(lin); Object.values(lampMat).forEach(lin);
      scene.fog.color.convertSRGBToLinear();

      /* the tracks & cars panel: status chips (show / hide), every track with its positions, filled or empty */
      const trackList = (() => {
        const m = new Map();
        st.cars.forEach((q, e) => { const t = tk(e); if (!t || t === "_") return; (m.get(t) || m.set(t, []).get(t)).push(e); });
        m.forEach(list => list.sort((a, b) => ix(a) - ix(b)));
        const groups = new Map();
        m.forEach((list, t) => { const k = kindOf(t); (groups.get(k) || groups.set(k, []).get(k)).push([t, list]); });
        return groups;
      })();
      const listEl = legend.querySelector(".x3d-list");
      function listInfo(n) {
        let html = "", tracks = 0, pos = 0;
        trackList.forEach((rows, k) => {
          html += "<h5>" + esc(k) + "</h5>";
          rows.forEach(([t, list]) => {
            tracks++; pos += list.length;
            let on = 0;
            const sq = list.map(e => { const g = (st.cars.get(e) || {}).g; if (g) on++; return "<i" + (g ? ' class="k-' + g + (st.hide.has(g) ? " off" : "") + '"' : "") + "></i>"; }).join("");
            html += `<button type="button" class="x3d-row" role="listitem" data-t="${esc(t)}" title="Track ${esc(t)}: ${on} of ${list.length} positions taken. Click to fly to it."><b>${esc(t)}</b><span class="x3d-bar">${sq}</span><em>${on}/${list.length}</em></button>`;
          });
        });
        listEl.innerHTML = html;
        legend.querySelector(".x3d-sum").textContent = Object.values(n).reduce((a, b) => a + b, 0) + " cars · " + tracks + " tracks · " + pos + " positions";
      }
      function counts() {
        const n = { cy: 0, dy: 0, ry: 0, hy: 0, xx: 0 }; st.cars.forEach(q => { if (q.g) n[q.g]++; });
        legend.querySelectorAll("b[data-n]").forEach(b => { b.textContent = n[b.dataset.n]; });
        st.lamps.forEach(l => { l.m.material = lampMat[worst(l.tid)]; });
        listInfo(n);
      }
      legend.querySelector(".x3d-chips").addEventListener("click", e => {
        const b = e.target.closest(".x3d-chip"); if (!b) return;
        const k = b.dataset.k, hide = !st.hide.has(k);
        if (hide) st.hide.add(k); else st.hide.delete(k);
        b.setAttribute("aria-pressed", String(!hide));
        st.cars.forEach(q => { if (q.grp) q.grp.visible = !st.hide.has(q.g); });
        if (st.hover && st.hover.userData.grp && !st.hover.userData.grp.visible) setHover(null, {});
        counts(); kick();
      });
      let lit = [];
      const light = t => {
        lit.forEach(b => { if (b !== st.hover) hot(b, false); }); lit = [];
        if (t) st.cars.forEach((q, e) => { if (tk(e) === t && q.body && q.grp.visible) { hot(q.body, true); lit.push(q.body); } });
        kick();
      };
      listEl.addEventListener("mouseover", e => { const r = e.target.closest(".x3d-row"); light(r ? r.dataset.t : null); });
      listEl.addEventListener("mouseleave", () => light(null));
      listEl.addEventListener("click", e => {
        const r = e.target.closest(".x3d-row"); if (!r) return;
        const ps = []; st.cars.forEach((q, el) => { if (tk(el) === r.dataset.t) ps.push([q.x, q.z]); });
        if (!ps.length) return;
        const xs = ps.map(p => p[0]), zs = ps.map(p => p[1]);
        const ext = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...zs) - Math.min(...zs));
        rig.g = { t: V3((Math.min(...xs) + Math.max(...xs)) / 2, 0, (Math.min(...zs) + Math.max(...zs)) / 2), r: Math.max(rig.min + 1, Math.min(rig.max, ext * 1.3 + 4)), th: rig.g.th, ph: rig.g.ph };
        kick();
      });
      const showInfo = (on, keep) => { legend.hidden = !on; reopen.hidden = on; if (st.refit) st.refit(); if (!keep) store.set(INFO, on ? "1" : "0"); };
      legend.querySelector(".x3d-x").addEventListener("click", () => { light(null); showInfo(false); });
      reopen.addEventListener("click", () => showInfo(true));
      st.introOn = false; st.introN = 0; st.cars.forEach((_, e) => paint(e)); st.introOn = false;
      scene.traverse(o => [].concat(o.material || []).forEach(lin));
      counts();
      st.sync = () => { st.cars.forEach((_, e) => paint(e)); counts(); kick(); };
      const mo = new MutationObserver(ms => {
        const hit = new Set();
        ms.forEach(r => { const t = r.target.nodeType === 1 ? r.target : r.target.parentElement; const e = t && t.closest(".cell, .k-inp.ro, .mv-slot"); if (e && st.cars.has(e)) hit.add(e); });
        if (hit.size) { hit.forEach(paint); counts(); kick(); }
      });
      mo.observe(root, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ["class", "value"] });

      /* ---- camera rig: target + spherical, eased; fitted to what is drawn (track, cars, tags), not the whole drawing area ---- */
      const bnd = { x0: Infinity, x1: -Infinity, z0: Infinity, z1: -Infinity };
      const grow = (x, z) => { bnd.x0 = Math.min(bnd.x0, x); bnd.x1 = Math.max(bnd.x1, x); bnd.z0 = Math.min(bnd.z0, z); bnd.z1 = Math.max(bnd.z1, z); };
      routes.forEach(p => p.forEach(q => grow(q[0], q[1])));
      st.cars.forEach(q => { grow(q.x - q.G.len / 2, q.z); grow(q.x + q.G.len / 2, q.z); });
      marks.forEach(([x, z]) => grow(x, z));
      { extras.updateMatrixWorld(true); const eb = new THREE.Box3().setFromObject(extras); if (!eb.isEmpty()) { grow(eb.min.x, eb.min.z); grow(eb.max.x, eb.max.z); } }   // buildings stand on the yard pad too
      if (!isFinite(bnd.x0)) { bnd.x0 = -mapW / 2; bnd.x1 = mapW / 2; bnd.z0 = -mapD / 2; bnd.z1 = mapD / 2; }
      const corners = [];
      [[bnd.x0 - .4, bnd.z0 - .4], [bnd.x1 + .4, bnd.z0 - .4], [bnd.x1 + .4, bnd.z1 + .4], [bnd.x0 - .4, bnd.z1 + .4]].forEach(([x, z]) => corners.push(V3(x, 0, z), V3(x, .45, z)));
      /* ---- the yard's surroundings: a gravel yard pad, rocks along the track beds, a fence with light masts. Nothing here is clickable
       * and nothing moves the tracks or cars: it is scenery placed around what the map draws. ---- */
      (function environment() {
        let sd = 0; const name = ((f.mv.card.querySelector(".mv-ttl b") || {}).firstChild || {}).textContent || "MARIS";
        for (let i = 0; i < name.length; i++) sd = Math.imul(sd ^ name.charCodeAt(i), 2654435761);
        const rnd = () => { sd |= 0; sd = sd + 0x6D2B79F5 | 0; let t = Math.imul(sd ^ sd >>> 15, 1 | sd); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
        const env = new THREE.Group(); env.name = "environment"; scene.add(env);
        const mat = o => { const m = new THREE.MeshStandardMaterial(o); lin(m); return m; };
        const noiseTex = (base, dots, rep) => {
          const c = document.createElement("canvas"); c.width = c.height = 256; const g = c.getContext("2d");
          g.fillStyle = base; g.fillRect(0, 0, 256, 256);
          for (let i = 0; i < 5200; i++) { g.fillStyle = dots[(rnd() * dots.length) | 0]; const s = rnd() < .85 ? 1.4 : 2.6; g.fillRect(rnd() * 256, rnd() * 256, s, s); }
          const t = new THREE.CanvasTexture(c); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(rep[0], rep[1]); t.anisotropy = 8; t.encoding = THREE.sRGBEncoding; return t;
        };
        /* ground: muted grass and earth around the yard, the grid kept faint */
        ground.material = mat({ color: 0xffffff, map: noiseTex("#a7b597", ["#98a888", "#b3c0a4", "#8d9d7e", "#a1af91"], [90, 90]), roughness: 1 });
        grid.material.opacity = .18;
        /* the yard pad: packed gravel under every track, a little wider than what is drawn */
        const M = 1.6, px0 = bnd.x0 - M, px1 = bnd.x1 + M, pz0 = bnd.z0 - M, pz1 = bnd.z1 + M, pw = px1 - px0, pd = pz1 - pz0;
        const pad = new THREE.Mesh(new THREE.PlaneGeometry(pw, pd), mat({ color: 0xffffff, map: noiseTex("#aaa59b", ["#99948a", "#bbb6ac", "#8c877d", "#a39e94", "#827d73"], [pw / 3, pd / 3]), roughness: 1 }));
        pad.rotation.x = -Math.PI / 2; pad.position.set((px0 + px1) / 2, -.02, (pz0 + pz1) / 2); pad.receiveShadow = true; env.add(pad);
        pad.material.polygonOffset = true; pad.material.polygonOffsetFactor = -1; pad.material.polygonOffsetUnits = -2;
        const edge = new THREE.Mesh(new THREE.PlaneGeometry(pw + .5, pd + .5), mat({ color: 0x8f8a7f, roughness: 1 }));
        edge.rotation.x = -Math.PI / 2; edge.position.set(pad.position.x, -.04, pad.position.z); edge.receiveShadow = true; env.add(edge);
        grid.visible = false;                                        // the faint grid lay between the ground and the pad and flickered along the pad's edge

        /* rocks: ballast stones scattered along both sides of every track bed */
        const rocks = [];
        laid.forEach(([pts, hw, lite]) => { if (lite) return;
          for (let i = 0; i < pts.length - 1; i++) {
            const [ax, az] = pts[i], [bx, bz] = pts[i + 1], L = Math.hypot(bx - ax, bz - az); if (L < .05) continue;
            const ux = (bx - ax) / L, uz = (bz - az) / L, n = Math.floor(L * 2.2);
            for (let k = 0; k < n; k++) {
              const t = rnd() * L, side = rnd() < .5 ? -1 : 1, off = hw * (1.35 + rnd() * .9) * side;
              rocks.push([ax + ux * t - uz * off, az + uz * t + ux * off, .025 + rnd() * .05, rnd() * 6.28]);
            }
          }
        });
        if (rocks.length) {
          const list = rocks.slice(0, 6000), im = new THREE.InstancedMesh(new THREE.DodecahedronGeometry(1, 0), mat({ color: 0xffffff, roughness: .95, flatShading: true }), list.length);
          const tones = [0x8f8a82, 0xa39d93, 0x7c776f, 0xb3ada2, 0x948b7d].map(c => new THREE.Color(c).convertSRGBToLinear());
          list.forEach(([x, z, s, r], i) => { d3.position.set(x, s * .45, z); d3.rotation.set(r, r * 1.7, r * .5); d3.scale.set(s * 1.3, s * .7, s); d3.updateMatrix(); im.setMatrixAt(i, d3.matrix); im.setColorAt(i, tones[i % tones.length]); });
          im.receiveShadow = true; env.add(im);
        }

        /* the fence all round the yard and light masts inside it */
        const fx0 = px0 - .8, fx1 = px1 + .8, fz0 = pz0 - .8, fz1 = pz1 + .8, posts = [];
        const run = (ax, az, bx, bz) => { const L = Math.hypot(bx - ax, bz - az), n = Math.max(1, Math.round(L / 1.2)); for (let k = 0; k <= n; k++) posts.push([ax + (bx - ax) * k / n, az + (bz - az) * k / n]); return [[ax, az], [bx, bz]]; };
        const legs = [run(fx0, fz0, fx1, fz0), run(fx1, fz0, fx1, fz1), run(fx1, fz1, fx0, fz1), run(fx0, fz1, fx0, fz0)];
        const postM = mat({ color: 0x8a949f, metalness: .5, roughness: .45 });
        const pim = new THREE.InstancedMesh(new THREE.CylinderGeometry(.018, .018, .6, 6), postM, posts.length);
        posts.forEach(([x, z], i) => { d3.position.set(x, .3, z); d3.rotation.set(0, 0, 0); d3.scale.set(1, 1, 1); d3.updateMatrix(); pim.setMatrixAt(i, d3.matrix); });
        const wire = new THREE.LineBasicMaterial({ color: 0x8a949f, transparent: true, opacity: .45 }), wp = [];
        legs.forEach(([[ax, az], [bx, bz]]) => [.2, .4, .58].forEach(y => wp.push(ax, y, az, bx, y, bz)));
        const wg = new THREE.BufferGeometry(); wg.setAttribute("position", new THREE.Float32BufferAttribute(wp, 3)); 
        const masts = [];
        const nx = Math.max(2, Math.round(pw / 14));
        for (let k = 0; k <= nx; k++) { const x = px0 + pw * k / nx; masts.push([x, pz0 - .3], [x, pz1 + .3]); }
        const mastM = mat({ color: 0x9aa4b0, metalness: .55, roughness: .4 }), headM = new THREE.MeshBasicMaterial({ color: 0xfff3c4, toneMapped: false });
        const mim = new THREE.InstancedMesh(new THREE.CylinderGeometry(.04, .06, 3.4, 8), mastM, masts.length), him = new THREE.InstancedMesh(unitBox, headM, masts.length);
        masts.forEach(([x, z], i) => { d3.position.set(x, 1.7, z); d3.rotation.set(0, 0, 0); d3.scale.set(1, 1, 1); d3.updateMatrix(); mim.setMatrixAt(i, d3.matrix);
          d3.position.set(x, 3.42, z); d3.scale.set(.42, .08, .22); d3.updateMatrix(); him.setMatrixAt(i, d3.matrix); });
        /* no light masts round the yard */

      })();
      /* the tracks panel takes room on the right only while it is open (on phones it floats over the map) */
      const panelW = () => legend.hidden || st.sw < 700 ? 16 : side.offsetWidth + 24;
      function place(t, r, th, ph) {
        const sp = Math.sin(ph);
        camera.position.set(t.x + r * sp * Math.sin(th), t.y + r * Math.cos(ph), t.z + r * sp * Math.cos(th));
        camera.lookAt(t); camera.updateMatrixWorld();
      }
      function fit(th, ph) {
        const vh = st.sh || 1, t = V3((bnd.x0 + bnd.x1) / 2, 0, (bnd.z0 + bnd.z1) / 2);
        const Y0 = -1 + 2 * 50 / vh, Y1 = 1 - 2 * 14 / vh, X0 = -.98, X1 = Math.max(.4, 1 - 2 * panelW() / (st.sw || 1));
        let r = 10;
        for (let pass = 0; pass < 4; pass++) {
          let lo = 1, hi = 800;
          for (let i = 0; i < 28; i++) {
            const mid = (lo + hi) / 2; place(t, mid, th, ph);
            const ok = corners.every(q => { const p = q.clone().project(camera); return p.z < 1 && p.x >= X0 && p.x <= X1 && p.y >= Y0 && p.y <= Y1; });
            if (ok) hi = mid; else lo = mid;
          }
          r = hi; place(t, r, th, ph);
          const ps = corners.map(q => q.clone().project(camera));
          const my = (Math.min(...ps.map(p => p.y)) + Math.max(...ps.map(p => p.y))) / 2 - (Y0 + Y1) / 2;
          const mx = (Math.min(...ps.map(p => p.x)) + Math.max(...ps.map(p => p.x))) / 2 - (X0 + X1) / 2;
          const right = V3(Math.cos(th), 0, -Math.sin(th)), up = V3(-Math.sin(th), 0, -Math.cos(th));
          const span = Math.tan(camera.fov * Math.PI / 360) * r;
          t.addScaledVector(right, mx * span * camera.aspect * .9).addScaledVector(up, my * span * .9 / Math.max(.3, Math.cos(ph)));
        }
        /* a little closer than the exact fit so the yard reads bigger: zoom in about the left edge, so the extra
         * size runs under the tracks panel on the right (the panel can be closed) and the left end stays in view */
        const k = legend.hidden || st.sw < 700 ? .94 : .84;
        place(t, r, th, ph); ray.setFromCamera(new THREE.Vector2(X0, (Y0 + Y1) / 2), camera);
        const anchor = V3(0, 0, 0);
        if (ray.ray.intersectPlane(groundPlane, anchor)) t.lerp(anchor, 1 - k);
        return { t, r: r * k };
      }
      let fits = {};
      const fitFor = k => fits[k] || (fits[k] = fit(VIEWS[k].th, VIEWS[k].ph));
      const rig = { t: V3(0, 0, 0), r: 40, th: VIEWS.whole.th, ph: VIEWS.whole.ph, min: 2.5, max: 200 };
      rig.g = { t: rig.t.clone(), r: rig.r, th: rig.th, ph: rig.ph };
      function rigUpdate(dt) {
        const k = RM ? 1 : 1 - Math.pow(.0009, dt), g = rig.g;
        rig.t.lerp(g.t, k); rig.r += (g.r - rig.r) * k; rig.th += (g.th - rig.th) * k; rig.ph += (g.ph - rig.ph) * k;
        place(rig.t, rig.r, rig.th, rig.ph);
        return rig.t.distanceTo(g.t) > .002 || Math.abs(g.r - rig.r) > .002 || Math.abs(g.th - rig.th) > .0005 || Math.abs(g.ph - rig.ph) > .0005;
      }
      /* "Angle": a low three-quarter close-up on the busiest group of cars */
      function heroPose() {
        if (fits.__hero) return fits.__hero;
        const pts = [...st.cars.values()].filter(q => q.grp).map(q => [q.x, q.z]);
        const all = pts.length ? pts : [...st.cars.values()].map(q => [q.x, q.z]);
        let t = fitFor("whole").t.clone();
        if (all.length) {
          const R2 = 30;
          let best = all[0], bc = -1;
          all.forEach(p => { let n = 0; all.forEach(q => { if ((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 < R2) n++; }); if (n > bc) { bc = n; best = p; } });
          const near = all.filter(q => (best[0] - q[0]) ** 2 + (best[1] - q[1]) ** 2 < R2);
          t = V3(near.reduce((a, q) => a + q[0], 0) / near.length, 0, near.reduce((a, q) => a + q[1], 0) / near.length);
        }
        const vf = camera.fov * Math.PI / 360, hf = Math.atan(Math.tan(vf) * camera.aspect);
        const span = Math.max(8, Math.min(14, mapW * .42));
        const r = Math.min(Math.max(span / 2 / Math.tan(hf), span * .62 / 2 / Math.tan(vf)), fitFor("whole").r);
        const cs = [...st.cars.values()], vert = cs.filter(q => Math.abs(Math.sin(q.G.ang)) > .7).length;
        const th = vert > cs.length / 2 ? -Math.PI / 2 + .5 : -.45;
        const ph = VIEWS.hero.ph, X1 = Math.max(.4, 1 - 2 * panelW() / (st.sw || 1)), xm = (-.98 + X1) / 2, ym = -.05;
        const pool = [...st.cars.values()].map(q => V3(q.x, .2, q.z));
        for (let pass = 0; pass < 4; pass++) {
          place(t, r, th, ph);
          const ps = pool.map(q => q.clone().project(camera)).filter(q => q.z < 1 && Math.abs(q.x) < 1.15 && Math.abs(q.y) < 1.15);
          if (ps.length < 3) break;
          const mx = (Math.min(...ps.map(q => q.x)) + Math.max(...ps.map(q => q.x))) / 2 - xm;
          const my = (Math.min(...ps.map(q => q.y)) + Math.max(...ps.map(q => q.y))) / 2 - ym;
          const right = V3(Math.cos(th), 0, -Math.sin(th)), up = V3(-Math.sin(th), 0, -Math.cos(th)), span2 = Math.tan(vf) * r;
          t.addScaledVector(right, mx * span2 * camera.aspect * .6).addScaledVector(up, my * span2 * .6 / Math.max(.3, Math.cos(ph)));
        }
        return (fits.__hero = { t, r, th });
      }
      function view(k, intro) {
        const fv = k === "hero" ? heroPose() : fitFor(k), V = VIEWS[k];
        let th = fv.th != null ? fv.th : V.th; while (th - rig.g.th > Math.PI) th -= 2 * Math.PI; while (rig.g.th - th > Math.PI) th += 2 * Math.PI;
        rig.g = { t: fv.t.clone(), r: fv.r, th, ph: V.ph };
        if (intro && !RM) { const w0 = fitFor("whole"); rig.t.copy(w0.t); rig.r = w0.r * 1.3; rig.th = th - .35; rig.ph = .55; }
        else if (intro) { rig.t.copy(fv.t); rig.r = fv.r; rig.th = th; rig.ph = V.ph; }
        kick();
      }
      const home = intro => view("whole", intro);
      st.home = home;
      st.refit = () => {
        const w0 = fits.whole, g = rig.g;
        const atWhole = w0 && Math.abs(g.r - w0.r) < .01 && g.t.distanceTo(w0.t) < .01 && Math.abs(g.ph - VIEWS.whole.ph) < .01;
        fits = {};
        if (atWhole) view("whole");
      };
      function syncCube() { cube.style.transform = "rotateX(" + (-(90 - rig.ph * 180 / Math.PI)).toFixed(2) + "deg) rotateY(" + (-rig.th * 180 / Math.PI).toFixed(2) + "deg)"; }

      /* ---- shop maps: a short train of GO cars pulls out of the shop and leaves the map, then the run repeats (decorative) ---- */
      if (sampleAt) {
        const t = labelTex("SAMPLE TRACK", { fg: "#2a1d06", bg: ["#ffcf73", "#f2b640"], ar: 4.2, font: MONO, r: 14 });
        const sideM = new THREE.MeshStandardMaterial({ color: 0xc98a1f, roughness: .7 }); lin(sideM);
        const tag = new THREE.Mesh(unitBox, [sideM, sideM, new THREE.MeshStandardMaterial({ map: t, roughness: .6, transparent: true }), sideM, sideM, sideM]);
        tag.scale.set(1.9, .1, .45); tag.position.set(sampleAt[0] + 1.1, .07, sampleAt[1] + .6); tag.castShadow = true; scene.add(tag);
      }
      const train = trainPath ? makeTrain() : null;
      function makeTrain() {
        const dirAt = (p, end) => { const a = end ? p[p.length - 2] : p[1], b = end ? p[p.length - 1] : p[0]; const L = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1; return [(b[0] - a[0]) / L, (b[1] - a[1]) / L]; };
        let path = trainPath;
        const edge = q => Math.max(Math.abs(q[0]) / (mapW / 2), Math.abs(q[1]) / (mapD / 2));
        if (edge(path[0]) > edge(path[path.length - 1])) path = path.slice().reverse();
        const cum = [0]; for (let i = 1; i < path.length; i++) cum.push(cum[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]));
        const PL = cum[cum.length - 1];
        const at = sd => {
          if (sd <= 0) { const d = dirAt(path, false); return [path[0][0] + d[0] * -sd, path[0][1] + d[1] * -sd]; }
          if (sd >= PL) { const d = dirAt(path, true); return [path[path.length - 1][0] + d[0] * (sd - PL), path[path.length - 1][1] + d[1] * (sd - PL)]; }
          let i = 1; while (cum[i] < sd) i++;
          const t = (sd - cum[i - 1]) / ((cum[i] - cum[i - 1]) || 1), a = path[i - 1], b = path[i];
          return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
        };
        const CL = .95, EL = 1.25, GAP = .1, WID = .44;
        const n = Math.max(2, Math.min(4, Math.floor((PL * .45) / (CL + GAP)) - 1));
        const units = [], tl = col => new THREE.Color(col).convertSRGBToLinear();
        const mkm = o => { const m = new THREE.MeshStandardMaterial(Object.assign({ roughness: .5, transparent: true }, o)); m._lin = 1; return m; };
        const bx = (g, mat, sx, sy, sz, x, y, z) => { const b = new THREE.Mesh(unitBox, mat); b.scale.set(sx, sy, sz); b.position.set(x, y, z); b.castShadow = true; g.add(b); return b; };
        const own2 = g => { const mt = [], copies = new Map();
          const cp = m => { if (!copies.has(m)) { const c2 = m.clone(); c2._lin = 1; c2.transparent = true; copies.set(m, c2); mt.push(c2); } return copies.get(m); };
          g.traverse(o => { if (o.isMesh) o.material = Array.isArray(o.material) ? o.material.map(cp) : cp(o.material); }); return mt; };
        {
          const g = new THREE.Group(), T = engineTex(), tm = map => mkm({ map, roughness: .4, metalness: .25 });
          const hoodSide = tm(T.hoodSide), hoodEnd = tm(T.hoodEnd), cabSide = tm(T.cabSide), cabFront = tm(T.cabFront), top = tm(T.top);
          const navy = mkm({ color: tl("#24476f"), metalness: .3, roughness: .4 }), grey = mkm({ color: tl("#c3ccd6"), metalness: .45, roughness: .4 });
          const under = mkm({ color: tl("#2b333d"), roughness: .7, metalness: .3 }), lamp = new THREE.MeshBasicMaterial({ color: 0xfff4c8, transparent: true, toneMapped: false });
          gear(g, EL, WID);
          bx(g, under, EL * .99, .04, WID + .04, 0, .11, 0);
          bx(g, [navy, hoodEnd, top, under, hoodSide, hoodSide], EL * .64, .25, WID * .8, -EL * .16, .255, 0);
          bx(g, [cabFront, navy, grey, under, cabSide, cabSide], EL * .3, .35, WID, EL * .34, .305, 0);
          [-1, 1].forEach(sd => { const l = bx(g, lamp, .02, .04, .06, EL / 2 + .012, .29, sd * WID * .33); l.castShadow = false; });
          const mt = own2(g); g.visible = false; scene.add(g); units.push({ g, mt, L: EL });
        }
        const roofMat = new THREE.MeshStandardMaterial({ color: new THREE.Color(mixW(STATUS.xx.color, .62)).convertSRGBToLinear(), roughness: .45, metalness: .15 }); roofMat._lin = 1;
        for (let i = 0; i < n; i++) { const g = new THREE.Group(); buildCar(g, CL, WID, "xx", roofMat); const mt = own2(g); g.visible = false; scene.add(g); units.push({ g, mt, L: CL }); }
        let off = 0; units.forEach(u => { u.off = off + u.L / 2; off += u.L + GAP; });
        const TL = off - GAP, VMAX = Math.max(1.6, Math.min(3.2, PL / 8)), ACC = .8;
        const s0 = Math.min(TL + .3, PL * .5), s1 = PL + TL + 1.2, tAcc = VMAX / ACC, dAcc = VMAX * tAcc / 2;
        const tRun = s1 - s0 <= dAcc ? Math.sqrt(2 * (s1 - s0) / ACC) : tAcc + (s1 - s0 - dAcc) / VMAX;
        const IN = .8, DWELL = 1.6, PAUSE = 3.5, CYCLE = IN + DWELL + tRun + PAUSE;
        const T = { t0: performance.now() + 900, timer: 0 };
        T.tick = now => {
          let t = (now - T.t0) / 1000;
          if (t < 0) { units.forEach(u => { u.g.visible = false; }); return false; }
          t %= CYCLE;
          let sd, fade = 1;
          if (t < IN + DWELL) { sd = s0; fade = Math.min(1, t / IN); }
          else if (t < IN + DWELL + tRun) { const u = t - IN - DWELL; sd = s0 + (u < tAcc ? ACC * u * u / 2 : dAcc + (u - tAcc) * VMAX); }
          else { units.forEach(u => { u.g.visible = false; }); return false; }
          units.forEach(u => {
            const sc = sd - u.off, a = at(sc + u.L / 2), b = at(sc - u.L / 2), m = at(sc);
            const op = fade * Math.max(0, Math.min(1, 1 - (sc - PL) / 1.1));
            u.g.visible = op > .01; u.g.position.set(m[0], .1, m[1]); u.g.rotation.y = -Math.atan2(a[1] - b[1], a[0] - b[0]);
            u.mt.forEach(mm => { mm.opacity = op; mm.depthWrite = op > .98; });
          });
          return true;
        };
        T.wake = now => { clearTimeout(T.timer); const t = (now - T.t0) / 1000, wait = t < 0 ? -t : CYCLE - (t % CYCLE); T.timer = setTimeout(() => { T.timer = 0; kick(); }, Math.max(50, wait * 1000)); };
        T.dispose = () => clearTimeout(T.timer);
        return T;
      }

      /* ---- render on demand: frames run only while something moves (or the shop train runs) ---- */
      function kick() { if (!st.raf && st.sw && layer.isConnected) st.raf = requestAnimationFrame(frame); }
      st.kick = kick;
      function frame(now) {
        st.raf = 0;
        if (!layer.isConnected) { st.dispose(); return; }
        const dt = Math.min(.2, (now - (st.last || now)) / 1000); st.last = now;
        let moving = rigUpdate(dt);
        if (train) { if (!layer.closest(".view[hidden]") && train.tick(now)) moving = true; else train.wake(now); }
        st.cars.forEach(q => { const d = q.grp && q.grp.userData.drop; if (!d) return;         // the opening: each car drops onto its track in turn
          if (d.t0 == null) d.t0 = now + 350 + d.i * 12;
          const u = Math.max(0, Math.min(1, (now - d.t0) / 550)), e = 1 + 2.2 * Math.pow(u - 1, 3) + 1.2 * Math.pow(u - 1, 2);
          q.grp.position.y = .1 + (1 - e) * 6; if (u >= 1) { q.grp.position.y = .1; q.grp.userData.drop = null; } else moving = true; });
        st.picks.forEach(p => {
          if (p.userData.grp && p.userData.grp.userData.drop) return;
          const u = p.userData, lift = p === st.hover ? (u.kind === "car" ? .1 : u.kind === "sign" ? .04 : 0) : 0, obj = u.grp || p;
          const goal = u.base + lift, cur = obj.position.y;
          if (Math.abs(goal - cur) > .001) { obj.position.y = RM ? goal : cur + (goal - cur) * Math.min(1, dt * 14); moving = true; } else obj.position.y = goal;
        });
        st.cars.forEach(q => { const a = q.grp && q.grp.userData.anim; if (!a) return;          // a car driving along the rails to its new position
          const u = Math.min(1, (now - a.t0) / a.dur), e = u < .5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2, sd = e * a.L;
          let i = 1; while (i < a.cum.length - 1 && a.cum[i] < sd) i++;
          const p0 = a.path[i - 1], p1 = a.path[i], seg = (a.cum[i] - a.cum[i - 1]) || 1, t = Math.max(0, Math.min(1, (sd - a.cum[i - 1]) / seg));
          q.grp.position.set(p0[0] + (p1[0] - p0[0]) * t, .1, p0[1] + (p1[1] - p0[1]) * t);
          let h = -Math.atan2(p1[1] - p0[1], p1[0] - p0[0]);                                     // heading along the track (a car runs either way round)
          if (a.heading != null) { while (h - a.heading > Math.PI / 2) h -= Math.PI; while (a.heading - h > Math.PI / 2) h += Math.PI; }
          a.heading = a.heading == null ? h : a.heading + (h - a.heading) * .35; q.grp.rotation.y = a.heading;
          if (u >= 1) { const to = a.path[a.path.length - 1]; q.grp.position.set(to[0], .1, to[1]); q.grp.rotation.y = -q.G.ang; q.grp.userData.anim = null; } else moving = true; });
        if (st.ghost && st.ghost.visible) moving = moving || false;
        syncCube(); renderer.render(scene, camera);
        st.last = moving ? now : 0;
        if (moving) kick();
      }
      /* stage size: set by the card (the whole visible height of the page, like the Fit plan) */
      let first = true;
      st.size = (w, h) => {
        w = Math.max(200, Math.floor(w)); h = Math.max(240, Math.floor(h));
        layer.style.height = h + "px";
        const pr = Math.min(2.5, (devicePixelRatio || 1) * Z());
        if (w === st.sw && h === st.sh && pr === st.pr) { kick(); return; }
        st.sw = w; st.sh = h; st.pr = pr;
        if (first) { const saved = store.get(INFO); showInfo(saved ? saved === "1" : w >= 700, true); }
        renderer.setPixelRatio(pr); renderer.setSize(w, h, false);
        camera.aspect = w / h; camera.updateProjectionMatrix();
        VIEWS.whole.th = w / h < .85 ? Math.PI / 2 : 0;            // portrait stages: the yard's long side runs down the screen
        fits = {};
        const f0 = fitFor("whole");
        scene.fog.near = f0.r * 1.1; scene.fog.far = f0.r * 3.2; rig.max = f0.r * 2.2;
        home(first); first = false;
      };

      /* ---- pointer: drag pans, right-drag (or shift-drag) rotates, wheel / pinch zooms, click opens ---- */
      const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
      const toNdc = e => { const r = canvas.getBoundingClientRect(); ndc.set((e.clientX - r.left) / r.width * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1); };
      function pick(e) {
        toNdc(e); ray.setFromCamera(ndc, camera);
        const h = ray.intersectObjects(st.picks.filter(p => (p.userData.grp || p).visible !== false), false), car = h.find(x => x.object.userData.kind !== "pad");
        return (car || h[0] || {}).object || null;
      }
      function hot(o, on) {
        const u = o.userData;
        if (u.kind === "car") { const M = carMat[u.g]; o.material = [on ? M.sideHot : M.side, on ? M.shellHot : M.shell]; }
        else if (u.kind === "pad") o.material = on ? mats.padHot : (u.dom.classList.contains("wash") ? mats.wash : mats.pad);
        else if (u.side) { const sm = on ? u.sideHot : u.side; o.material = [sm, sm, o.material[2], sm, sm, sm]; }
      }
      function setHover(o, e) {
        if (st.moving && e && e.clientX != null) { moveHover(o, e); return; }
        if (st.hover !== o) { if (st.hover) hot(st.hover, false); st.hover = o; if (o) hot(o, true); canvas.classList.toggle("hot", !!(o && opens(o.userData.dom))); kick(); }
        if (!o) { tip.hidden = true; return; }
        const u = o.userData, d = u.dom, trk = tk(d) === "_" ? "" : tk(d);
        const pos = ", position " + (ix(d) + 1), hint2 = opens(d) ? "<span>Click to open it</span>" : "";
        if (u.kind === "car") {
          const cs = carState(d, vid) || { stat: "GO", g: "xx" };
          tip.innerHTML = `<b>Car ${esc(u.text)}</b><em><i style="background:${STATUS[cs.g].color}"></i>${esc(STATUS[cs.g].name)}</em>` +
            (trk ? `<span>Track ${esc(trk)}${pos}${cs.home ? ". Home yard " + esc(cs.home) : ""}</span>` : "") + hint2;
        } else if (u.kind === "pad") tip.innerHTML = `<b>Empty position</b>${trk ? `<span>Track ${esc(trk)}${pos}</span>` : ""}${hint2}`;
        else tip.innerHTML = `<b>${esc(u.text)}</b>${hint2}`;
        const r = layer.getBoundingClientRect(), z = r.width / (layer.offsetWidth || 1) || 1;
        let x = (e.clientX - r.left) / z + 14, y = (e.clientY - r.top) / z + 14;
        tip.hidden = false;
        if (x + tip.offsetWidth > st.sw - 8) x -= tip.offsetWidth + 28;
        if (y + tip.offsetHeight > st.sh - 8) y -= tip.offsetHeight + 28;
        tip.style.left = x + "px"; tip.style.top = y + "px";
      }
      function orbit(dx, dy, k) { rig.g.th -= dx * k; rig.g.ph = Math.max(PH_MIN, Math.min(PH_MAX, rig.g.ph - dy * k)); kick(); }
      const groundPlane = new THREE.Plane(V3(0, 1, 0), 0), hitP = V3(0, 0, 0);
      function zoomAt(e, factor) {
        const nr = Math.max(rig.min, Math.min(rig.max, rig.g.r * factor)), fz = nr / rig.g.r;
        toNdc(e); ray.setFromCamera(ndc, camera);
        if (fz !== 1 && ray.ray.intersectPlane(groundPlane, hitP)) {
          rig.g.t.sub(hitP).multiplyScalar(fz).add(hitP);
          rig.g.t.x = Math.max(-mapW / 2 - 2, Math.min(mapW / 2 + 2, rig.g.t.x)); rig.g.t.z = Math.max(-mapD / 2 - 2, Math.min(mapD / 2 + 2, rig.g.t.z));
        }
        rig.g.r = nr; kick();
      }
      st.wheel = e => { const dy = e.deltaY * (e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? 400 : 1); zoomAt(e, Math.exp(Math.max(-60, Math.min(60, dy)) * (e.ctrlKey ? .012 : .0016))); };
      let drag = null, pinch = null; const ptrs = new Map();
      const pinchOf = () => { const [a, b] = [...ptrs.values()]; return { d: Math.hypot(a.x - b.x, a.y - b.y) || 1, clientX: (a.x + b.x) / 2, clientY: (a.y + b.y) / 2 }; };
      canvas.addEventListener("contextmenu", e => e.preventDefault());
      canvas.addEventListener("pointerdown", e => {
        ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
        try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        if (ptrs.size === 2) { pinch = pinchOf(); drag = null; setHover(null, e); return; }
        drag = { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, rot: e.button === 2 || e.shiftKey, moved: false };
      });
      canvas.addEventListener("pointermove", e => {
        if (ptrs.has(e.pointerId)) ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (pinch && ptrs.size === 2) {
          const p = pinchOf(); zoomAt(p, pinch.d / p.d);
          const s2 = 2 * rig.g.r * Math.tan(camera.fov * Math.PI / 360) / (canvas.getBoundingClientRect().height || 1);
          const th = rig.g.th, dx = p.clientX - pinch.clientX, dy = p.clientY - pinch.clientY, k = 1 / Math.max(.35, Math.cos(rig.g.ph));
          rig.g.t.x -= dx * s2 * Math.cos(th) + dy * s2 * Math.sin(th) * k; rig.g.t.z -= -dx * s2 * Math.sin(th) + dy * s2 * Math.cos(th) * k;
          pinch = p; return;
        }
        if (!drag) { if (e.pointerType === "mouse") setHover(pick(e), e); return; }
        const dx = e.clientX - drag.x, dy = e.clientY - drag.y; drag.x = e.clientX; drag.y = e.clientY;
        if (Math.hypot(e.clientX - drag.sx, e.clientY - drag.sy) > 4) { drag.moved = true; canvas.classList.add("drag"); setHover(null, e); }
        if (!drag.moved) return;
        if (drag.rot) orbit(dx, dy, .006);
        else {
          const s2 = 2 * rig.g.r * Math.tan(camera.fov * Math.PI / 360) / (canvas.getBoundingClientRect().height || 1);
          const th = rig.g.th, rx = Math.cos(th), rz = -Math.sin(th), fx = -Math.sin(th), fz = -Math.cos(th), k = 1 / Math.max(.35, Math.cos(rig.g.ph));
          rig.g.t.x = Math.max(-mapW / 2, Math.min(mapW / 2, rig.g.t.x - dx * s2 * rx + dy * s2 * fx * k));
          rig.g.t.z = Math.max(-mapD / 2, Math.min(mapD / 2, rig.g.t.z - dx * s2 * rz + dy * s2 * fz * k));
          kick();
        }
      });
      const end = e => {
        ptrs.delete(e.pointerId); if (ptrs.size < 2) pinch = null;
        try { canvas.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        if (!drag) return;
        const was = drag; drag = null; canvas.classList.remove("drag");
        if (!was.moved && e.type === "pointerup" && e.button === 0) {
          const o = pick(e);
          if (st.moving) { const tg = moveTarget(o); if (tg.t != null && tg.i != null && !tg.why) commitMove(tg.t, tg.i); else if (tg.why) moveSay(tg.why); return; }
          if (o && o.userData.kind === "car" && isShop) { setHover(null, e); openCar(o.userData.dom); return; }
          if (!o && carPanel.hidden === false) { closeCar(); return; }
          if (o && o.userData.dom) { setHover(null, e); o.userData.dom.click(); }
        }
      };
      canvas.addEventListener("pointerup", end); canvas.addEventListener("pointercancel", end);
      canvas.addEventListener("pointerleave", e => { if (!drag) setHover(null, e); });
      let gs = 1;
      layer.addEventListener("gesturestart", e => { e.preventDefault(); gs = e.scale || 1; });
      layer.addEventListener("gesturechange", e => { e.preventDefault(); const q = e.scale || 1; zoomAt(e, gs / q); gs = q; });
      layer.addEventListener("gestureend", e => e.preventDefault());
      reset.addEventListener("click", () => home(false));

      /* the view cube: drag it to turn the yard; click a face (or a button) to look from that side */
      let cdrag = null;
      cubeWrap.addEventListener("pointerdown", e => {
        if (e.button !== 0) return;
        cdrag = { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, moved: false, face: e.target.closest("button[data-v]") };
        cubeWrap.setPointerCapture(e.pointerId); cubeWrap.classList.add("drag"); e.preventDefault();
      });
      cubeWrap.addEventListener("pointermove", e => {
        if (!cdrag) return;
        const dx = e.clientX - cdrag.x, dy = e.clientY - cdrag.y; cdrag.x = e.clientX; cdrag.y = e.clientY;
        if (Math.hypot(e.clientX - cdrag.sx, e.clientY - cdrag.sy) > 3) cdrag.moved = true;
        if (cdrag.moved) orbit(dx, dy, .014);
      });
      const cend = e => {
        if (!cdrag) return;
        const was = cdrag; cdrag = null; cubeWrap.classList.remove("drag");
        try { cubeWrap.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        if (!was.moved && e.type === "pointerup" && was.face) view(was.face.dataset.v);
      };
      cubeWrap.addEventListener("pointerup", cend); cubeWrap.addEventListener("pointercancel", cend);
      cube.querySelectorAll("button").forEach(b => b.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); view(b.dataset.v); } }));
      hud.querySelector(".x3d-views").addEventListener("click", e => { const b = e.target.closest("button[data-v]"); if (b) view(b.dataset.v); });
      canvas.addEventListener("webglcontextlost", e => { e.preventDefault(); st.dispose(); });

      /* ---------- the track network: every laid track as a polyline, joined where tracks meet or cross ---------- */
      const CONN_KEY = "maris.connectors";
      const connStore = () => { try { return JSON.parse(sessionStorage.getItem(CONN_KEY) || "{}"); } catch (e) { return {}; } };
      function layExtra(pts, hw) {                                                   // a new piece of track, laid like the others
        const Sh = new Soup(), Bd = new Soup(), Rl = new Soup(), tl = [];
        strip(Sh, pts, hw * 1.3, .004); strip(Bd, pts, hw, .05, .004);
        strip(Rl, offset(pts, hw * .55), Math.max(.022, hw * .09), .1, .05); strip(Rl, offset(pts, -hw * .55), Math.max(.022, hw * .09), .1, .05);
        for (let i = 0; i < pts.length - 1; i++) { const [ax, az] = pts[i], [bx, bz] = pts[i + 1], L = Math.hypot(bx - ax, bz - az), n = Math.floor(L / .3), ang = Math.atan2(bz - az, bx - ax);
          for (let k = 0; k <= n; k++) { const t = n ? k / n : .5; tl.push([ax + (bx - ax) * t, az + (bz - az) * t, ang]); } }
        const grpT = new THREE.Group(); grpT.name = "connector";
        [[Sh, mats.shoulder], [Bd, mats.ballast], [Rl, mats.rail]].forEach(([Sx, mt]) => { const me = Sx.mesh(mt); if (me) { me.receiveShadow = true; grpT.add(me); } });
        if (tl.length) { const im = new THREE.InstancedMesh(unitBox, mats.tie, tl.length); tl.forEach(([x, z, a], i) => { d3.position.set(x, .058, z); d3.rotation.set(0, -a, 0); d3.scale.set(.07, .025, hw * 2.05); d3.updateMatrix(); im.setMatrixAt(i, d3.matrix); }); im.receiveShadow = true; grpT.add(im); }
        scene.add(grpT); pts.src = "conn"; routes.push(pts); topo = null; searches.clear(); kick();
      }
      try { sessionStorage.removeItem(CONN_KEY); } catch (e) { /* ignore */ }      // the track layout stays as the map draws it: no added tracks
      /* the rail topology of this map, read from its drawn tracks (see railTopology): turnouts, crossings, reversals */
      let topo = null;
      function topoOf() {
        if (topo) return topo;
        const lines = routes.filter(p => p.length >= 2 && p.src !== "sample").map(p => ({ pts: p, name: p.name || (p.src === "svg" ? "connecting track" : "track") }));
        return (topo = railTopology(lines));
      }
      const searches = new Map(), searchFrom = n => { if (!searches.has(n)) searches.set(n, railFrom(topoOf(), [n])); return searches.get(n); };
      const pathLen = P => P.reduce((L, q, k) => k ? L + Math.hypot(q[0] - P[k - 1][0], q[1] - P[k - 1][1]) : 0, 0);
      st.topo = () => topoOf();
      st.railPath = (from, to) => { const T = topoOf(), p = railPath(T, searchFrom(railSnap(T, from)), railSnap(T, to)); return { pts: p ? [from].concat(p.pts, [to]) : null, info: p }; };

      /* ---------- car panel and moving a car (shop maps) ---------- */
      const canMove = () => false;          // cars are moved in MARIS (the car move screens), never only in the browser
      const yardOf = () => ((root.querySelector(".cell[data-yard]") || {}).dataset || {}).yard;
      const trackAt = d => { const t = d.dataset.track || d.dataset.open; return t && ydTrack(yardOf(), t) ? t : null; };
      const posName = i => "position " + String(i + 1).padStart(2, "0");
      let ghost = null;
      function ghostAt(t, i) {
        const cell = root.querySelector(`.cell[data-track="${CSS.escape(t)}"][data-idx="${i}"]`), q = cell && st.cars.get(cell); if (!q) { if (ghost) ghost.visible = false; return; }
        if (!ghost) { ghost = new THREE.Mesh(unitBox, new THREE.MeshBasicMaterial({ color: 0xf5b544, transparent: true, opacity: .55, depthWrite: false, toneMapped: false })); scene.add(ghost); }
        ghost.scale.set(q.G.len, .3, q.G.wid); ghost.rotation.set(0, -q.G.ang, 0); ghost.position.set(q.x, .3, q.z); ghost.visible = true;
        if (q.pad) q.pad.material = mats.padHot;
      }
      function clearGhost() { if (ghost) ghost.visible = false; st.cars.forEach((qq, c) => { qq.pad.material = c.classList.contains("wash") ? mats.wash : mats.pad; }); }
      /* what the pointer is on while choosing a destination: an exact empty position, or why it is not one */
      function moveTarget(o) {
        const d = o && o.userData.dom; if (!d) return {};
        const t = trackAt(d); if (!t) return {};
        const isPos = d.matches(".cell[data-track]") && !d.classList.contains("turn"), m = st.moving;
        if (!isPos) return { t, why: `Point at an empty position on ${t}: the car goes exactly where you click` };
        const i = +d.dataset.idx, tr = ydTrack(yardOf(), t), r = ydCar(yardOf(), t, i);
        if (t === m.track && i === m.idx) return { t, i, why: `${m.text} already stands here` };
        if (t === m.track) return { t, i, why: `${m.text} is already on ${t}` };
        if (!tr || i >= tr.cap) return { t, i, why: `${t} · ${posName(i)} is beyond the track's ${tr ? tr.cap : 0} car positions` };
        if (r) return { t, i, why: `${t} · ${posName(i)} is taken by car ${r[0]}` };
        if (!joined(m.track, t)) return { t, i, why: `No track joins ${m.track} to ${t}` };
        return { t, i };
      }
      function moveSay(text) { moveBar.className = "x3d-movebar warn"; moveBar.innerHTML = `<span class="mb-msg">${esc(text)}. Pick an empty position.</span> <button type="button" class="cp-b sm" data-mv="x">Cancel</button>`; }
      function moveHover(o, e) {
        clearGhost();
        const tg = moveTarget(o);
        if (tg.t == null) { tip.hidden = true; canvas.style.cursor = "crosshair"; kick(); return; }
        const ok = tg.i != null && !tg.why;
        tip.innerHTML = ok ? `<span>Move exactly to</span><b>${esc(tg.t)} · ${posName(tg.i)}</b>` : `<b>${esc(tg.t)}${tg.i != null ? " · " + posName(tg.i) : ""}</b><span>${esc(tg.why)}</span>`;
        if (ok) ghostAt(tg.t, tg.i);
        canvas.style.cursor = ok ? "copy" : "not-allowed";
        const r = layer.getBoundingClientRect(), z = r.width / (layer.offsetWidth || 1) || 1;
        tip.hidden = false; tip.style.left = ((e.clientX - r.left) / z + 14) + "px"; tip.style.top = ((e.clientY - r.top) / z + 14) + "px";
        kick();
      }
      function openCar(cell) {
        const y = yardOf(), t = cell.dataset.track, i = +cell.dataset.idx, r = ydCar(y, t, i); if (!r) return;
        const tr = ydTrack(y, t), g = (/^(HY|CY|DY|RY)$/.test(r[1]) ? r[1] : "XX").toLowerCase();
        const home = MV_HOME[r[1]] ? MV_HOME[r[1]][0] : r[1] || "—";
        st.sel = { track: t, idx: i, text: r[0] };
        carPanel.innerHTML = `<div class="cp-h"><div><small>${/^[$({]|[)}]$/.test(r[0]) ? "C-car · legacy fleet" : "E-car · Fleet of the Future"}</small><b class="cp-no">${esc(r[0])}</b>
            <span class="cp-st" style="--c:${STATUS[g].color}"><i></i>${esc(STATUS[g].name)}</span></div>
            <button type="button" class="x3d-x" data-cp="x" aria-label="Close the car panel">&times;</button></div>
          <dl class="cp-kv"><div><dt>Location</dt><dd>${esc(t)} · ${posName(i)}</dd></div><div><dt>Home yard</dt><dd>${esc(home)}</dd></div>
            <div><dt>Track</dt><dd>${esc(tr.desc || t)}</dd></div><div><dt>Orientation</dt><dd>${esc(r[5] || "—")}</dd></div></dl>
          ${canMove() ? `<div class="cp-acts"><button type="button" class="cp-b pri" data-cp="move">Move car <kbd>M</kbd></button><button type="button" class="cp-b" data-cp="detail">Track detail</button></div>`
            : `<p class="cp-ro">Cars are moved in MARIS: open the track to move a car.</p><div class="cp-acts"><button type="button" class="cp-b" data-cp="detail">Track detail</button></div>`}`;
        carPanel.hidden = false; if (planCard.classList.contains("min")) requestAnimationFrame(placePlan); kick();
      }
      function closeCar() { if (st.moving) cancelMove(); carPanel.hidden = true; st.sel = null; }
      carPanel.addEventListener("click", e => {
        const b = e.target.closest("[data-cp]"); if (!b) return;
        if (b.dataset.cp === "x") closeCar();
        else if (b.dataset.cp === "move") startMove();
        else if (b.dataset.cp === "detail" && st.sel) { const c = root.querySelector(`.cell[data-track="${CSS.escape(st.sel.track)}"][data-idx="${st.sel.idx}"]`); if (c) c.click(); }
      });
      function startMove() {
        if (!st.sel || !canMove() || st.busy) return;
        st.moving = st.sel; setHover(null, {}); clearTimeout(st.mvT);
        moveBar.className = "x3d-movebar";
        moveBar.innerHTML = `Click the empty position to move <b>${esc(st.sel.text)}</b> to <kbd>Esc</kbd> to cancel <button type="button" class="cp-b sm" data-mv="x">Cancel</button>`;
        moveBar.hidden = false; canvas.classList.add("moving"); kick();
      }
      function cancelMove() { st.moving = null; moveBar.hidden = true; canvas.classList.remove("moving"); canvas.style.cursor = ""; clearGhost(); tip.hidden = true; kick(); }
      moveBar.addEventListener("click", e => { const b = e.target.closest("[data-mv]"); if (!b) return; if (b.dataset.mv === "x") cancelMove(); else if (b.dataset.mv === "undo" && st.undo && !st.busy) { const u = st.undo; st.undo = null; runMoves(u, true); } });

      /* ---------- planning a move: the real route along the rails, and the cars standing in the way ---------- */
      const cellOf = (t, i) => root.querySelector(`.cell[data-track="${CSS.escape(t)}"][data-idx="${i}"]`);
      const cellPt = (t, i) => { const c = cellOf(t, i), q = c && st.cars.get(c); return q ? [q.x, q.z] : null; };
      /* a car's body on the ground: points along its length (it is a box along its track, not a dot) */
      const bodyAt = (t, i) => { const c = cellOf(t, i), q = c && st.cars.get(c); if (!q) return null; const ux = Math.cos(q.G.ang), uz = Math.sin(q.G.ang), h = q.G.len / 2;
        return [-1, -.5, 0, .5, 1].map(f => [q.x + ux * h * f, q.z + uz * h * f]); };
      const CLEAR = .5;                                                             // centre line of a moving car to the body of a standing car: their two half widths and a little room
      const dirAt = (t, i) => { const c = cellOf(t, i), q = c && st.cars.get(c); return q ? [Math.cos(q.G.ang), Math.sin(q.G.ang)] : null; };
      const nodeAtPos = (t, i) => { const p = cellPt(t, i); return p ? railSnap(topoOf(), p, dirAt(t, i)) : -1; };
      const joinedCache = new Map();
      function joined(a, b) {                                                      // can a car on track a reach track b at all (turnouts, no crossings)?
        const k = a + ">" + b; if (joinedCache.has(k)) return joinedCache.get(k);
        const first = t => { const c = root.querySelector(`.cell[data-track="${CSS.escape(t)}"]`); return c ? nodeAtPos(t, +c.dataset.idx) : -1; };
        const na = first(a), nb = first(b), R = na >= 0 && nb >= 0 ? searchFrom(na) : null;
        const ok = !!R && (isFinite(R.dist[nb * 2]) || isFinite(R.dist[nb * 2 + 1])); joinedCache.set(k, ok); return ok;
      }
      /* distance from a point to a route, and from a car body to a route */
      const distToPath = (p, P) => { let d = Infinity; for (let k = 1; k < P.length; k++) { const [ax, az] = P[k - 1], [bx, bz] = P[k], dx = bx - ax, dz = bz - az, L2 = dx * dx + dz * dz || 1, u = Math.max(0, Math.min(1, ((p[0] - ax) * dx + (p[1] - az) * dz) / L2)); d = Math.min(d, Math.hypot(ax + dx * u - p[0], az + dz * u - p[1])); } return d; };
      const bodyToPath = (body, P) => Math.min(...body.map(q => distToPath(q, P)));
      /* the moving car's route from just after it starts to just before it stops; any other car's body near it is in the way */
      const trimmed = P => { const L = pathLen(P); if (L < .4) return P; const out = []; let acc = 0; for (let k = 0; k < P.length; k++) { if (k) acc += Math.hypot(P[k][0] - P[k - 1][0], P[k][1] - P[k - 1][1]); if (acc >= .05 && acc <= L - .05) out.push(P[k]); } return out.length > 1 ? out : P; };
      /* the yard as the planner sees it: every drawn position within its track's capacity, who stands there, and the
       * rail route between any two positions with the positions whose car bodies would be in the way */
      function yardModel() {
        topoOf();                                                                   // the track network (built on first use)
        const y = yardOf(), positions = [], idOf = new Map();
        root.querySelectorAll(".cell[data-track]").forEach(c => { const t = c.dataset.track, i = +c.dataset.idx, tr = ydTrack(y, t);
          if (!tr || i >= tr.cap || c.classList.contains("turn") || idOf.has(t + "#" + i)) return; const p = cellPt(t, i); if (!p) return;
          idOf.set(t + "#" + i, positions.length); positions.push({ track: t, idx: i, p, body: bodyAt(t, i), node: nodeAtPos(t, i) }); });
        const occ = positions.map(q => { const r = ydCar(y, q.track, q.idx); return r ? r[0] : null; });
        const model = { mainFrom: -1 };
        /* the rail routes between two positions: for the requested car up to three different routes, else the best one;
         * a car whose body stands on a route is in its way */
        const route = (a, b) => {
          const A = positions[a], B = positions[b], T = topoOf(); if (A.node < 0 || B.node < 0) return null;
          let alts;
          if (a === model.mainFrom) alts = railRoutes(T, [A.node], B.node, 3); else { const p = railPath(T, searchFrom(A.node), B.node); alts = p ? [p] : []; }
          if (!alts.length) return null;
          const mk = r => {
            const P = [A.p].concat(r.pts, [B.p]), T2 = trimmed(P); let x0 = Infinity, x1 = -Infinity, z0 = Infinity, z1 = -Infinity; T2.forEach(([x, z]) => { x0 = Math.min(x0, x); x1 = Math.max(x1, x); z0 = Math.min(z0, z); z1 = Math.max(z1, z); });
            const blocks = []; positions.forEach((q, k) => { if (k === a || k === b) return; const [x, z] = q.p; if (x < x0 - 1.6 || x > x1 + 1.6 || z < z0 - 1.6 || z > z1 + 1.6) return; if (bodyToPath(q.body, T2) < CLEAR) blocks.push(k); });
            return { blocks, cost: r.cost, path: P, info: r };
          };
          const L = alts.map(mk); return Object.assign({ alts: L }, L[0]);
        };
        const slotOrder = (t, ids) => ids.slice().sort((a, b) => /^S/.test(t) ? positions[a].idx - positions[b].idx : positions[b].idx - positions[a].idx);
        return Object.assign(model, { positions, occ, route, slotOrder, idOf });
      }
      /* plan a move with the yard planner; turn its steps into the moves runMoves drives (with their rail routes) */
      function planMove(m, t, idx) {
        const M = yardModel(), from = M.idOf.get(m.track + "#" + m.idx);
        if (from == null) return { err: `${m.text} is not drawn on this map.` };
        let slot;
        if (idx != null) {                                                          // an exact position was requested: it must exist on this map
          const tr = ydTrack(yardOf(), t);
          if (!tr) return { err: `${t} is not a track of this yard.` };
          if (idx < 0 || idx >= tr.cap) return { err: `${t} has ${tr.cap} car positions, so ${posName(idx)} does not exist.` };
          slot = M.idOf.get(t + "#" + idx);
          if (slot == null) return { err: `${t} · ${posName(idx)} is not drawn on this map, so a car cannot be placed there.` };
        }
        M.mainFrom = from;
        const r = planRearrange(M, { from, track: t, slot });
        const name = k => M.positions[k].track + " · " + posName(M.positions[k].idx);
        if (!r.ok) return { err: explainFail(r, m, t, M), result: r };
        const moves = r.steps.map(s => ({ ft: M.positions[s.from].track, fi: M.positions[s.from].idx, tt: M.positions[s.to].track, ti: M.positions[s.to].idx,
          path: M.route(s.from, s.to).alts[s.alt || 0].path, info: M.route(s.from, s.to).alts[s.alt || 0].info, alt: s.alt || 0, text: s.car, kind: s.kind, aside: s.kind === "clear", main: s.kind === "main", back: s.kind === "return", label: name(s.from) + " → " + name(s.to) }));
        return { moves, result: r };
      }
      function explainFail(r, m, t, M) {
        if (r.reason === "no-connection") return `There is no way to move ${m.text} to ${t}: the drawn tracks have no switch path from ${m.track} to ${t} (tracks that only cross each other cannot be used to change tracks, and a car cannot turn back through a switch without room to reverse). Pick a track that is connected.`;
        if (r.reason === "full") return `${t} has no free position for ${m.text}.`;
        if (r.reason === "free-unreachable") { const list = a => a.map(n => "#" + (n + 1)).join(", ");
          return `${m.text} cannot reach a free position on ${t}. From ${m.track} the rail route reaches ${t} only at ${list(r.reachable)} (taken). Its free position${r.free.length > 1 ? "s" : ""} ${list(r.free)} lie${r.free.length > 1 ? "" : "s"} the other way along ${t}: the switch onto ${t} faces away from ${r.free.length > 1 ? "them" : "it"}, and there is not enough track past the switch to reverse (tracks that only cross ${t} cannot be used to get there). No other position was chosen instead. Free ${list(r.reachable)} first, or pick another track.`; }
        if (r.reason === "same-track") return `${m.text} is already on ${t}.`;
        const at = r.slot != null ? `${t} · ${posName(M.positions[r.slot].idx)}` : t;
        if (r.reason === "occupied") return `${at} is taken by car ${r.occupant}. Pick an empty position.`;
        if (r.reason === "invalid-slot") return `That position is not on ${t}.`;
        const by = r.blockedBy.length ? `${at} is blocked by car${r.blockedBy.length > 1 ? "s" : ""} ${andList(r.blockedBy)}. ` : "";
        if (r.reason === "too-many") return by + `The only rail routes to ${at} pass ${r.minMoves} parked car${r.minMoves > 1 ? "s" : ""}${r.blockedBy.length ? ` (${r.blockedBy.join(", ")})` : ""}, so clearing them takes at least ${r.minMoves} moves - more than the ${r.maxMoves} a plan may use. Pick a destination that is easier to reach.`;
        const why = (r.stuck || []).slice(0, 4).map(x => `${x.car}: ${x.why}`).join("; ");
        return by + (r.limitHit ? `I checked ${r.explored.toLocaleString()} arrangements of the cars (plans of up to ${r.maxMoves} moves) and found none that clears the way`
          : `I checked every arrangement of the cars that could help (${r.explored.toLocaleString()}) and none clears the way`) + (why ? ` — ${why}.` : ".") + ` No other position was chosen instead: free a position on a connected track, or pick another destination.`;
      }
      st.planMove = planMove; st.yardModel = yardModel;
      st.openCar = (track, idx) => { const c = cellOf(track, idx); if (c) openCar(c); };   // for automated checks
      st.proposeMove = (track, idx, t, ti) => { const r = ydCar(yardOf(), track, idx); if (!r) return; st.sel = { track, idx, text: r[0] }; startMove(); commitMove(t, ti); };   // for automated checks

      st.tryMove = (track, idx, t, ti) => { const r = ydCar(yardOf(), track, idx); if (!r) return "no car"; st.sel = { track, idx, text: r[0] }; startMove(); commitMove(t, ti); if (!planCard.hidden) { const txt = planCard.innerText; planCard.querySelector('[data-pl="run"]').click(); return txt; } return moveBar.innerText; };   // for automated checks
      /* ---------- running the moves one after another, each car driving along its route ---------- */
      const sleep = ms => new Promise(r => setTimeout(r, ms));
      async function runMoves(moves, isUndo) {
        closeCar();                                                                 // the car card goes away as soon as the car starts moving
        st.busy = true; carPanel.classList.add("busy"); clearTimeout(st.mvT);
        const y = yardOf(), last = moves.find(x => x.main) || moves[moves.length - 1], aside = moves.filter(x => x.aside);
        moveBar.className = "x3d-movebar"; moveBar.hidden = false;
        for (let n = 0; n < moves.length; n++) {
          const mv = moves[n];
          moveBar.innerHTML = isUndo ? `Putting the cars back <span class="mb-step">${n + 1} of ${moves.length}</span>`
            : mv.aside ? `Clearing the way: <b>${esc(mv.text)}</b> to ${esc(mv.tt)} · ${posName(mv.ti)} <span class="mb-step">${n + 1} of ${moves.length}</span>`
            : mv.back ? `Putting back: <b>${esc(mv.text)}</b> to ${esc(mv.tt)} · ${posName(mv.ti)} <span class="mb-step">${n + 1} of ${moves.length}</span>`
            : `Moving <b>${esc(mv.text)}</b> to ${esc(mv.tt)} · ${posName(mv.ti)}${moves.length > 1 ? ` <span class="mb-step">${n + 1} of ${moves.length}</span>` : ""}`;
          st.arriving = { text: mv.text, path: mv.path };
          if (!moveCarData(y, mv.ft, mv.fi, mv.tt, mv.ti)) { st.arriving = null; continue; }
          st.lastPath = { pts: mv.path };
          await sleep(RM ? 60 : animMs(pathLen(mv.path)) + 180);
        }
        st.busy = false; carPanel.classList.remove("busy");
        /* check the requested car really stands on the requested position: the yard data, the 2D map and the 3D scene */
        if (!isUndo) {
          const dc = cellOf(last.tt, last.ti), q3 = dc && st.cars.get(dc), r0 = ydCar(y, last.tt, last.ti);
          const at3 = q3 && q3.grp ? Math.hypot(q3.grp.position.x - q3.x, q3.grp.position.z - q3.z) : Infinity;
          st.lastVerify = { car: last.text, track: last.tt, idx: last.ti, data: !!r0 && r0[0] === last.text, plan: !!dc && dc.textContent.trim() === last.text, scene: at3 < .01 };
          if (!(st.lastVerify.data && st.lastVerify.plan && st.lastVerify.scene)) toast(`Check ${last.tt} · ${posName(last.ti)}: car ${last.text} is not shown where it was placed.`);
        }
        if (isUndo) { moveBar.innerHTML = `Move undone: every car is back where it stood`; st.mvT = setTimeout(() => { moveBar.hidden = true; }, 4000); }
        else {
          st.undo = moves.slice().reverse().map(x => ({ ft: x.tt, fi: x.ti, tt: x.ft, ti: x.fi, path: x.path.slice().reverse(), text: x.text }));
          moveBar.innerHTML = `Car <b>${esc(last.text)}</b> moved to <b>${esc(last.tt)} · ${posName(last.ti)}</b>` +
            (aside.length ? `<span class="mb-sub">${aside.length} car${aside.length > 1 ? "s" : ""} moved out of the way (${aside.map(a => esc(a.text)).join(", ")})${moves.some(x => x.back) ? `; ${moves.filter(x => x.back).length} returned to ${moves.filter(x => x.back).length > 1 ? "their" : "its"} position` : ""}${aside.some(a => !moves.some(x => x.back && x.text === a.text)) ? "; the others stay where they were parked" : ""}</span>` : "") +
            ` <button type="button" class="cp-b sm" data-mv="undo">Undo</button>`;
          st.mvT = setTimeout(() => { moveBar.hidden = true; st.undo = null; }, 12000);
        }
      }
      function commitMove(t, idx) {
        const m = st.moving; if (!m || st.busy) return;
        if (t === m.track && idx == null) { cancelMove(); return; }
        const plan = planMove(m, t, idx);
        if (plan.err) {                                                             // stay in move mode: say why and let them pick another track
          clearGhost(); tip.hidden = true; moveBar.className = "x3d-movebar warn";
          moveBar.innerHTML = `<span class="mb-msg">${esc(plan.err)}</span> <button type="button" class="cp-b sm" data-mv="x">Cancel</button>`;
          return;
        }
        st.moving = null; canvas.classList.remove("moving"); canvas.style.cursor = ""; clearGhost(); tip.hidden = true;
        if (!plan.result.relocations) { runMoves(plan.moves, false); return; }     // a clear way: the move runs as before
        showPlan(plan, t);                                                          // other cars would move: show the plan and wait for approval
      }
      /* the plan for approval: nothing moves until Run plan is pressed */
      /* how a move gets there, in words: the tracks it uses, reversals and crossings */
      const routeWords = r => { const v = r.via.filter((n, i) => n !== r.via[i - 1]); return "via " + v.join(" → ") + (r.reversals ? ` · ${r.reversals} reversal${r.reversals > 1 ? "s" : ""}` : "") + (r.crossings ? ` · crosses ${r.crossings} track${r.crossings > 1 ? "s" : ""}` : ""); };
      const andList = a => a.length < 2 ? a.join("") : a.slice(0, -1).join(", ") + " and " + a[a.length - 1];
      function showPlan(plan, t) {
        const r = plan.result, main = plan.moves.find(x => x.main);
        moveBar.className = "x3d-movebar"; moveBar.innerHTML = `Plan ready for <b>${esc(main.text)}</b>: review it and select Run plan`; moveBar.hidden = false;
        planCard.innerHTML = `<div class="pl-h" title="Drag to move this plan (or use the arrow keys)" tabindex="0" aria-label="Movement plan header: drag or use arrow keys to move it"><span class="pl-grip" aria-hidden="true"></span><div><small>Movement plan</small><b>${esc(main.tt)} · ${esc(posName(main.ti))} is blocked by car${r.blockedBy.length > 1 ? "s" : ""} ${esc(andList(r.blockedBy))}</b></div>
            <span class="pl-hb"><button type="button" class="x3d-x pl-min" data-pl="min" aria-label="Make the plan small" title="Make small"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><path d="M6 12h12"/></svg></button><button type="button" class="x3d-x" data-pl="x" aria-label="Close the plan" title="Close">&times;</button></span></div>
          <div class="pl-mini"><span><b>${plan.moves.length} moves</b> · ${r.relocations} car${r.relocations > 1 ? "s" : ""} moved aside · Feasible</span><button type="button" class="cp-b sm" data-pl="max">Show plan</button><button type="button" class="cp-b sm pri" data-pl="run">Run plan</button></div>
          <p class="pl-sub">A way through was found after checking ${r.explored.toLocaleString()} yard arrangement${r.explored > 1 ? "s" : ""}${r.alternatives > 1 ? ` and comparing ${r.alternatives} workable plans` : ""}. It uses the fewest moves.</p>
          <ol class="pl-steps">${plan.moves.map(x => `<li class="k-${x.kind}"><span class="pl-k">${x.main ? "Requested" : x.back ? "Return" : "Clear"}</span><span><b>${esc(x.text)}</b> ${esc(x.label)}${x.main && x.info ? `<small class="pl-via">${esc(routeWords(x.info))}</small>` : ""}</span></li>`).join("")}</ol>
          <dl class="pl-sum"><div><dt>Repositioning</dt><dd>${r.relocations}</dd></div><div><dt>Requested</dt><dd>1</dd></div><div><dt>Returns</dt><dd>${r.returns}</dd></div><div><dt>Status</dt><dd class="ok">Feasible</dd></div></dl>
          ${r.unrestored.length ? `<p class="pl-note">${r.unrestored.map(esc).join(", ")} cannot get back to ${r.unrestored.length > 1 ? "their" : "its"} position and will stay where it was parked.</p>` : ""}
          <div class="pl-acts"><button type="button" class="cp-b" data-pl="x">Cancel</button><button type="button" class="cp-b pri" data-pl="run">Run plan (${plan.moves.length} moves)</button></div>`;
        planCard.hidden = false; st.pendingPlan = plan; setPlanMin(false); kick();
        planCard.scrollTop = 0; const b = planCard.querySelector('.pl-acts [data-pl="run"]'); if (b) b.focus({ preventScroll: true });
      }
      /* the plan card: drag it by its header anywhere on the map; make it small and it waits under the car card */
      let planAt = null;                                                            // where the user put it (layer px), or null for the default spot
      function placePlan() {
        if (planCard.classList.contains("min")) {
          const L = layer.getBoundingClientRect(), z = L.width / (layer.offsetWidth || 1) || 1, cp = carPanel.hidden ? null : carPanel.getBoundingClientRect();
          Object.assign(planCard.style, { left: "14px", top: (cp ? (cp.bottom - L.top) / z + 10 : 14) + "px", transform: "none" }); return;
        }
        if (!planAt) { planCard.style.left = planCard.style.top = planCard.style.transform = ""; return; }
        const W = layer.offsetWidth, H = layer.offsetHeight, w = planCard.offsetWidth, h = Math.min(planCard.offsetHeight, H);
        planAt.x = Math.max(6, Math.min(W - w - 6, planAt.x)); planAt.y = Math.max(6, Math.min(H - Math.min(h, 80) - 6, planAt.y));
        Object.assign(planCard.style, { left: planAt.x + "px", top: planAt.y + "px", transform: "none" });
      }
      function setPlanMin(on) { planCard.classList.toggle("min", on); placePlan(); }
      let pdrag = null;
      planCard.addEventListener("pointerdown", e => {
        if (e.button !== 0 || e.target.closest("button") || !e.target.closest(".pl-h") || planCard.classList.contains("min")) return;
        const L = layer.getBoundingClientRect(), C = planCard.getBoundingClientRect(), z = L.width / (layer.offsetWidth || 1) || 1;
        pdrag = { dx: (e.clientX - C.left) / z, dy: (e.clientY - C.top) / z, L, z }; planCard.setPointerCapture(e.pointerId); planCard.classList.add("dragging"); e.preventDefault();
      });
      planCard.addEventListener("pointermove", e => { if (!pdrag) return; planAt = { x: (e.clientX - pdrag.L.left) / pdrag.z - pdrag.dx, y: (e.clientY - pdrag.L.top) / pdrag.z - pdrag.dy }; placePlan(); });
      const pend = e => { if (!pdrag) return; pdrag = null; planCard.classList.remove("dragging"); try { planCard.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ } };
      planCard.addEventListener("pointerup", pend); planCard.addEventListener("pointercancel", pend);
      planCard.addEventListener("keydown", e => { if (!e.target.closest(".pl-h") || planCard.classList.contains("min")) return;      // arrow keys move it too
        const d = { ArrowLeft: [-20, 0], ArrowRight: [20, 0], ArrowUp: [0, -20], ArrowDown: [0, 20] }[e.key]; if (!d) return; e.preventDefault();
        const L = layer.getBoundingClientRect(), C = planCard.getBoundingClientRect(), z = L.width / (layer.offsetWidth || 1) || 1;
        planAt = { x: (C.left - L.left) / z + d[0], y: (C.top - L.top) / z + d[1] }; placePlan(); });
      planCard.addEventListener("click", e => { const b0 = e.target.closest("[data-pl]"); if (b0 && (b0.dataset.pl === "min" || b0.dataset.pl === "max")) { e.stopImmediatePropagation(); setPlanMin(b0.dataset.pl === "min"); } }, true);
      planCard.addEventListener("click", e => { const b = e.target.closest("[data-pl]"); if (!b) return;
        const plan = st.pendingPlan; planCard.hidden = true; st.pendingPlan = null;
        if (b.dataset.pl === "run" && plan) runMoves(plan.moves, false); else { moveBar.innerHTML = "Plan cancelled. No car was moved."; clearTimeout(st.mvT); st.mvT = setTimeout(() => { moveBar.hidden = true; }, 3000); } });
      st.keys = e => {
        if (e.key === "Escape" && !planCard.hidden) { planCard.querySelector('[data-pl="x"]').click(); return true; }
        if (e.key === "Escape" && st.moving) { cancelMove(); return true; }
        if (e.key === "Escape" && !carPanel.hidden) { closeCar(); return true; }
        if ((e.key === "m" || e.key === "M") && st.sel && !st.moving && canMove()) { startMove(); return true; }
        return false;
      };
      st.dispose = () => {
        if (st.dead) return; st.dead = true;
        live.delete(st); mo.disconnect(); if (train) train.dispose(); if (st.raf) cancelAnimationFrame(st.raf);
        scene.traverse(o => { if (o.geometry && o.geometry !== unitBox && o.geometry !== unitPlane) o.geometry.dispose(); });
        renderer.dispose(); try { renderer.forceContextLoss(); } catch (e) { /* ignore */ }
        parkSwitch(f.mv.card); layer.remove(); if (f.x3d === st) f.x3d = null;
      };
      return st;
    }
    const fills = () => Object.keys(SHOP_FILL).flatMap(y => SHOP_FILL[y].map(r => { const el = document.createElement("div");
      el.className = "cell c-" + r[5]; Object.assign(el.dataset, { yard: y, track: r[0], idx: r[1], st: r[3], pm: r[4] }); el.textContent = r[2]; return el; }));
    return { want: () => false, save: on => store.set("map3d", on ? "3d" : "2d"), build, dressPlan, state: el => carState(el, ""), fills,
      kit: { STATUS, livery, liveryModern, carGeo, engineTex } };   // the car models and status colours, for the sign-on background
  })();
  return M3D;
}
