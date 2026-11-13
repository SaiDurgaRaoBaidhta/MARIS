import { getJson, post, useMock } from "@/shared/api/client";
import type { ActionRequest, ActionResult, ScreenDef } from "./types";

export const screenFile = (key: string) => key.replace(/[^A-Za-z0-9_-]/g, "_");
/** a screen's layout: part of the UI, always static (public/screens) */
export const fetchScreen = (key: string) => getJson<ScreenDef>(`/screens/${screenFile(key)}.json`);

/* what a button that writes to the database says when it is done (the mock has no database) */
function doneMessage(title: string, caption: string): string {
  const subj = title.replace(/\b(screen|gui|maintenance|maint|update|inquiry|input|add|processing|report|criteria|view)\b/gi, "")
    .replace(/[-–(].*$/, "").replace(/\s+/g, " ").trim() || "Record";
  const c = caption.toLowerCase();
  const verb = /^(add|create|insert|new)/.test(c) ? "added" : /^(update|change|save|apply|modify)/.test(c) ? "updated"
    : /^(delete|remove|purge|scrap)/.test(c) ? "deleted" : /^(close|force)/.test(c) ? "closed"
    : /^(list|display|retrieve|search|find|refresh)/.test(c) ? "" : "saved";
  return verb ? `${subj} ${verb} successfully.` : "Data retrieved.";
}

/** a push button that calls server programs (EGL: call "VHxxxxx" with XDCONTROL + criteria + data) */
export async function runAction(req: ActionRequest): Promise<ActionResult> {
  if (!useMock) return post<ActionResult>(`/screens/${encodeURIComponent(req.screen)}/actions/${encodeURIComponent(req.control)}`, req);
  const s = await fetchScreen("egl:" + req.screen).catch(() => null);
  return { control: { URC: 0, UMSGCODE: "", UMSGINS: "" }, message: doneMessage(s?.title || req.screen, req.caption || req.control) };
}
