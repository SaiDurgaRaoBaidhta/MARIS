/* An old map screen is an inquiry or an update map: VHYT07I is the Hayward yard's inquiry map, VHYT05I its update map, ... */
import { MAP_SCREENS, type MapMode } from "./actions/screens";

export function mapMode(code: string): MapMode {
  const c = code.toUpperCase();
  return Object.values(MAP_SCREENS).some(s => s.yard.update.toUpperCase() === c || s.shop?.update.toUpperCase() === c) ? "update" : "inquiry";
}
