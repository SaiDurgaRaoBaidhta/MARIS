/* Yard and shop maps: the reference UI's map viewer (2D plan, 3D yard, Show-cars panel, camera views) runs unchanged in an
 * iframe island (public/legacy/maris-map.html, built by tools/build-map-island.mjs) until it is ported to React.
 * One iframe is kept for the whole session, so only the first map waits for it to load. It covers the page, as the
 * reference's map focus mode does; Return / Exit on the map comes back here as "closed". */
import { useEffect, useRef, useState } from "react";
import { fetchFleet } from "@/features/yard-tracking/api";
import { useApp } from "@/shared/lib/app-state";
import { splitTitle } from "@/features/screens/ScreenPage";
import { PageHead } from "@/shared/ui/PageHead";

type Msg = { source?: string; type: string; message?: string; code?: string; on?: boolean; title?: string };

export function MapIsland({ map, navWidth }: { map: { yard: string; shop: boolean } | null; navWidth: number }) {
  const app = useApp();
  const frame = useRef<HTMLIFrameElement>(null);
  const [wanted, setWanted] = useState(false);       // the iframe exists once a map was asked for
  const [ready, setReady] = useState(false);
  const [shown, setShown] = useState(false);
  const [title, setTitle] = useState("");
  const [full, setFull] = useState(true);            // the map in full screen covers the page; otherwise the page shell stays
  const back = useRef(app.back); back.current = app.back;
  const toast = useRef(app.toast); toast.current = app.toast;
  const open = useRef(app.open); open.current = app.open;

  useEffect(() => { if (map) setWanted(true); }, [map]);
  // load it quietly a moment after sign-on, so the first map opens at once
  useEffect(() => { const t = setTimeout(() => setWanted(true), 2500); return () => clearTimeout(t); }, []);
  useEffect(() => {
    const on = (e: MessageEvent<Msg>) => {
      if (e.origin !== location.origin || e.data?.source !== "maris-map") return;
      if (e.data.type === "maris-map:ready") setReady(true);
      if (e.data.type === "maris-map:shown") { setShown(true); setTitle(e.data.title || ""); }
      if (e.data.type === "maris-map:closed") { setShown(false); back.current(); }
      if (e.data.type === "maris-map:screen" && e.data.code) open.current({ kind: "screen", key: "egl:" + e.data.code, label: e.data.code });
      if (e.data.type === "maris-map:full") setFull(!!e.data.on);
      if (e.data.type === "maris-map:error") toast.current("The map could not be opened: " + e.data.message, "err");
    };
    addEventListener("message", on);
    return () => removeEventListener("message", on);
  }, []);
  const post = (m: object) => frame.current?.contentWindow?.postMessage(m, location.origin);
  const key = map ? `${map.yard}:${map.shop}` : "";
  /* the island runs on baked demo data: push the middleware's real fleet (program VHSW01A) into it before every map */
  useEffect(() => {
    if (!ready) return;
    let gone = false;
    fetchFleet(true)
      .then(f => { if (!gone) post({ type: "maris-map:data", fleet: f.fleet }); })
      .catch(e => toast.current("The map data could not be loaded: " + String(e), "err"));
    return () => { gone = true; };
  }, [ready, key]);
  useEffect(() => {
    if (!ready || !map) return;
    setShown(false);
    const s = app.session;
    post({
      type: "maris-map:open", yard: map.yard, shop: map.shop,
      user: s?.user, role: s?.role, group: s?.group, update: s?.update, homeYard: s?.yard,
    });
  }, [ready, key]);   // eslint-disable-line react-hooks/exhaustive-deps -- reopen only when the map changes
  useEffect(() => { if (ready) post({ type: "maris-map:theme", theme: app.theme }); }, [ready, app.theme]);

  if (!wanted) return null;
  const [code, name] = splitTitle(title);
  // out of full screen, as in the reference: the page header (path, title, code) and the map card under it, in the page shell
  const page = !full && !!map;
  return (
    <>
      {page && shown && (
        <div className="fixed right-6 top-16 z-[4400] px-6" style={{ left: navWidth }}>
          <PageHead title={name || map.yard} code={code || undefined} />
        </div>)}
      {/* the viewer sizes its own map card (58px above the window's bottom, as in the reference): the layer reaches the bottom and adds no card of its own */}
      <div className={`fixed ${full ? "inset-0 z-[6000] bg-[#eef3f8] dark:bg-[#08121d]" : "bottom-0 right-4 top-[166px] z-[4400]"} ${map ? "" : "hidden"}`}
        style={full ? undefined : { left: navWidth + 16 }} aria-hidden={!map}>
        {map && !shown && <p role="status" className="absolute inset-0 grid place-items-center text-slate-500">Loading the map…</p>}
        <iframe ref={frame} src="/legacy/maris-map.html" title="Yard map" className={`size-full border-0 bg-transparent ${shown ? "" : "invisible"}`} />
      </div>
    </>
  );
}
