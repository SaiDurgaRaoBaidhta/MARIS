/* Screen layouts: the final layout of one MARIS screen, captured from the reference UI (tools/capture-screens.mjs). */
import type { Schemas } from "@/shared/api/client";

export type ActionRequest = Schemas["ActionRequest"];
export type ActionResult = Schemas["ActionResult"];

/** where a menu function leads */
export type Target = { kind: "screen"; key: string } | { kind: "map"; yard: string; shop: boolean };

/* ---------- screens ----------
 * A screen is the final layout of one MARIS screen, captured from the reference UI, as a tree of nodes.
 * Positions are absolute pixels in the screen's own canvas (cw x ch). */
export interface SElement {
  t: string;                         // tag
  c?: string;                        // legacy class names (k-grp, k-lbl, k-inp, k-sel, k-btn, k-tbl, sq3d …)
  s?: Record<string, string>;        // inline style that matters (position, size, colour, alignment)
  h?: 1;                             // hidden at start (alternative tables, toggled by Detail / Summary)
  a?: Record<string, string | number>;
  o?: [string, string][];            // <select> options [value, text]
  k?: SNode[];                       // children
  html?: string;                     // inline svg icon
}
export type SNode = string | SElement;

/** What a control does, from the EGL <SCREEN>Connections wiring. */
export interface Wiring {
  k: string;          // btn | hs | list
  cap: string;        // caption
  to: string[];       // screens it opens (more than one: depends on the data)
  x: boolean;         // closes the screen
  p: string[];        // server programs it calls (MARIS_Server: call "VHxxxxx")
  m: string[];        // messages it can show
  off: boolean;       // disabled in the source
  it: string | null;  // screen it opens through a hot spot
}

export interface FieldMeta { k: string; items: string[]; max: number | null; ro: boolean; cap: string }

export interface ScreenDef {
  key: string;        // egl:VHCF01I, mf:VHSR12I, incd:event, web:inv …
  code?: string;      // VHCF01I
  cls: string;        // form | map | webwin …
  title: string;
  ver: string;
  cw: number;
  ch: number;
  body: SNode[] | null;   // null: drawn by a dedicated page (yard / shop maps)
  menu?: { name: string; list: { label: string; off: boolean; to: Target | null }[] }[];   // a menu screen: its functions as tiles
  flow?: boolean;         // a web page laid out by its own (inlined) styles instead of absolute positions
  rootStyle?: Record<string, string>;
  wiring?: Record<string, Wiring>;
  meta?: { module: string; progs: [string, string, unknown[]][]; reads: string[]; writes: string[]; inputs: Record<string, FieldMeta> };
}
