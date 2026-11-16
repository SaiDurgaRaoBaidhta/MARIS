/* The map page's actions in one hook: its buttons, what a car box or track tag click does, and the move being picked.
 * YardMapPage (M1) draws the buttons (or <MapButtons>) and calls onCar / onTrack / onPseudo from the drawing. */
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import type { MapCar, YardCode } from "../types";
import {
  NO_YARD_MOVE, carClickTarget, mapButtons, maintenanceAllowed, screenPage, shopMoveClick, trackClickTarget, yardMoveClick,
  yardTrackClick, type MapButton, type Selection, type ShopPick, type YardMove,
} from "./actions";
import { MAP_SCREENS, PSEUDO_TRACK, type MapMode } from "./screens";

export interface MapActionsOptions {
  yard: YardCode;
  shop: boolean;
  mode: MapMode;
  /** reload the map's cars (ReFresh / Refresh Map) */
  refresh(): void;
  /** leave the map (Return / Exit); default: back to the page before */
  exit?(): void;
}

export interface MapActions {
  buttons: MapButton[];
  /** a click on a box: the car in it, or null for an empty box, and the MARIS track and 1-based position it shows */
  onCar(car: MapCar | null, track: string, position: number): void;
  /** a click on a track tag / track button */
  onTrack(track: string): void;
  /** the PSEUDO TRACK button */
  onPseudo(): void;
  /** the move being picked on an update map (for highlighting) */
  selection: Selection;
  /** a note for the map's status line (empty when none) */
  message: string;
}

export function useMapActions({ yard, shop, mode, refresh, exit }: MapActionsOptions): MapActions {
  const app = useApp();
  const legacy = MAP_SCREENS[yard]?.legacy ?? yard;
  const [move, setMove] = useState<YardMove>(NO_YARD_MOVE);
  const [pick, setPick] = useState<ShopPick | null>(null);
  const [maintDenied, setMaintDenied] = useState(false);
  const [message, setMessage] = useState("");
  const moveRef = useRef(move); moveRef.current = move;
  const pickRef = useRef(pick); pickRef.current = pick;

  // another map (or mode): no half-picked move carries over
  useEffect(() => { setMove(NO_YARD_MOVE); setPick(null); setMaintDenied(false); setMessage(""); }, [yard, shop, mode]);

  const open = useCallback((t: { screen: string; params: Record<string, string | number> }) => app.open(screenPage(t)), [app]);
  const leave = useCallback(() => (exit ? exit() : app.back()), [exit, app]);
  const again = useCallback(() => { setMove(NO_YARD_MOVE); setPick(null); setMessage(""); refresh(); }, [refresh]);

  const maint = useCallback(() => {
    maintenanceAllowed(yard)
      .then(ok => { if (ok) open({ screen: "VHYTM6I", params: { yrdTrkgYrd: legacy } }); else setMaintDenied(true); })
      .catch(e => setMessage(e instanceof Error ? e.message : String(e)));
  }, [yard, legacy, open]);

  const onCar = useCallback((car: MapCar | null, track: string, position: number) => {
    if (mode === "update" && !shop) {
      const r = yardMoveClick(moveRef.current, legacy, track, position);
      setMove(r.state); if (r.open) open(r.open);
      return;
    }
    if (mode === "update" && shop) {
      const from = MAP_SCREENS[yard]?.shop?.update ?? "";
      const r = shopMoveClick(pickRef.current, legacy, from, track, position, car?.car ?? "");
      setPick(r.state); if (r.open) open(r.open);
      return;
    }
    const t = carClickTarget(yard, shop, mode, car);
    if (t) open(t);
  }, [mode, shop, yard, legacy, open]);

  const onTrack = useCallback((track: string) => {
    if (mode === "update" && !shop) {
      const r = yardTrackClick(moveRef.current, legacy, track);
      setMove(r.state); if (r.open) open(r.open);
      return;
    }
    if (mode === "inquiry" && !shop) open(trackClickTarget(yard, track));
    // the shop maps' track tags are captions only: the old screens wire nothing to them
  }, [mode, shop, yard, legacy, open]);

  const onPseudo = useCallback(() => onTrack(PSEUDO_TRACK), [onTrack]);

  const buttons = useMemo(() => mapButtons({ yard, shop, mode, open: app.open, refresh: again, exit: leave, maint, maintDenied, pick }),
    [yard, shop, mode, app.open, again, leave, maint, maintDenied, pick]);

  const selection: Selection = pick
    ? { kind: "shop", track: pick.track, position: pick.position, car: pick.car }
    : move.clicked > 0
      ? { kind: "yard", track: move.fromTrack, from: Math.min(move.fromPos, move.toPos), to: Math.max(move.fromPos, move.toPos), clicks: move.clicked }
      : { kind: "none" };

  return { buttons, onCar, onTrack, onPseudo, selection, message };
}
