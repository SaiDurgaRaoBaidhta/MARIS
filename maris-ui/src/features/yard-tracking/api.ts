import { get } from "@/shared/api/client";
import type { Fleet } from "./types";

let fleet: Promise<Fleet> | null = null;
/** every yard with its tracks and cars (middleware: MARIS program VHSW01A). Asked once; `fresh` asks again. */
export function fetchFleet(fresh = false): Promise<Fleet> {
  if (!fleet || fresh) fleet = get<Fleet>("/fleet").catch(e => { fleet = null; throw e; });
  return fleet;
}
