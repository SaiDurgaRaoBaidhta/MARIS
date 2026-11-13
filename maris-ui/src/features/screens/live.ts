/* Live screens: the middleware runs each old screen's own logic (its EGL statement groups and connections) and calls
 * the MARIS backend exactly when the old screen did. The UI sends what the user did and draws what comes back. */
import { get, post } from "@/shared/api/client";

/** one widget's state, by its EGL name (the layout's data-w) */
export interface LiveWidget {
  type: string;
  value?: string;            // text fields, labels, button captions
  checked?: boolean;         // toggle buttons
  items?: string[];          // drop-down lists, radio sets
  rows?: string[][];         // tables (container details): one string per column
  columns?: string[];
  selected?: number[];       // 1-based
  enabled?: false;
  visible?: false;
  fg?: string; bg?: string; color?: string;
  focus?: true;
  title?: string;
}

export interface LiveWindow { id: string; screen: string; title?: string; widgets: Record<string, LiveWidget> }
export interface LiveDialog { id: number; title: string; message: string; icon: number; buttons: string[] }
export interface LiveCall { program: string; screen: string; ms?: number; errorCode: string }
export interface LiveState { windows: LiveWindow[]; dialog?: LiveDialog; calls?: LiveCall[] }

export interface Change { widget: string; attr: "object" | "selection" | "selectionIndex"; value: unknown }

let screens: Promise<Set<string>> | null = null;
/** the screens the middleware can run (upper case) */
export const liveScreens = () => screens ??= get<string[]>("/ui/screens").then(l => new Set(l.map(s => s.toUpperCase()))).catch(() => new Set<string>());

export const openLive = (screen: string) => post<LiveState>("/ui/open", { screen });
export const sendEvent = (window: string, changes: Change[], widget?: string, event?: string, value?: unknown) =>
  post<LiveState>(`/ui/windows/${encodeURIComponent(window)}/events`, { changes, widget, event, value });
export const answerDialog = (dialog: number, button: string) => post<LiveState>(`/ui/dialogs/${dialog}`, { button });

/* VAST colour names the old screens use -> CSS */
const VAST: Record<string, string> = { grey75: "#bfbfbf", grey50: "#7f7f7f", grey25: "#404040", darkgreen: "#006400", darkred: "#8b0000", darkblue: "#00008b",
  darkcyan: "#008b8b", darkmagenta: "#8b008b", darkyellow: "#8b8b00", lightgrey: "#d3d3d3", lightgray: "#d3d3d3" };
export const cssColor = (c?: string) => {
  if (!c) return undefined;
  const k = c.trim().toLowerCase().replace(/\s+/g, "");
  return VAST[k] || k;
};
