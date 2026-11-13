import { getJson, post } from "@/shared/api/client";
import type { ActionRequest, ActionResult, ScreenDef } from "./types";

export const screenFile = (key: string) => key.replace(/[^A-Za-z0-9_-]/g, "_");
/** a screen's layout: part of the UI, always static (public/screens) */
export const fetchScreen = (key: string) => getJson<ScreenDef>(`/screens/${screenFile(key)}.json`);

/** a push button that calls server programs (EGL: call "VHxxxxx" with XDCONTROL + criteria + data) */
export async function runAction(req: ActionRequest): Promise<ActionResult> {
  return post<ActionResult>(`/screens/${encodeURIComponent(req.screen)}/actions/${encodeURIComponent(req.control)}`, req);
}
