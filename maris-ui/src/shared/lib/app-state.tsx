import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { currentSession, signOff as endSession } from "@/features/auth/api";
import type { Session } from "@/features/auth/types";
import { onSessionExpired } from "@/shared/api/client";
import { Dialog } from "@/shared/ui/Dialog";
import { Toasts, type Toast } from "@/shared/ui/Toasts";

/* App-wide state the features use: session, the page stack (Home -> menu -> screen …), theme, toasts and dialogs. */

/* ---------- pages ---------- */
export type Page =
  | { kind: "home" }
  | { kind: "menu"; section: string }
  | { kind: "screen"; key: string; label: string; yard?: string }
  | { kind: "map"; yard: string; shop: boolean; label: string };

export const pageLabel = (p: Page) => p.kind === "home" ? "Home" : p.kind === "menu" ? p.section : p.label;

interface AppState {
  session: Session | null;
  signOn(s: Session): void;
  signOff(): void;
  stack: Page[];                       // the path from Home to the page in front
  open(p: Page): void;                 // on top of the current page
  go(p: Page): void;                   // from Home
  back(): void;
  backTo(i: number): void;
  theme: "light" | "dark";
  toggleTheme(): void;
  toast(msg: string, kind?: Toast["kind"]): void;
  info(msg: string, title?: string): Promise<void>;
  confirm(msg: string): Promise<boolean>;
}

const Ctx = createContext<AppState | null>(null);
export const useApp = () => { const c = useContext(Ctx); if (!c) throw new Error("useApp outside <AppProvider>"); return c; };

const HOME: Page[] = [{ kind: "home" }];

export function AppProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [restoring, setRestoring] = useState(true);   // asking the middleware for a session kept across a reload
  const [stack, setStack] = useState<Page[]>(HOME);
  const [theme, setTheme] = useState<"light" | "dark">(() => document.documentElement.dataset.theme === "dark" ? "dark" : "light");
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [dialog, setDialog] = useState<{ title: string; msg: string; ask: boolean; done: (ok: boolean) => void } | null>(null);
  const last = useRef({ msg: "", t: 0 });

  useEffect(() => {
    if (theme === "dark") document.documentElement.dataset.theme = "dark"; else delete document.documentElement.dataset.theme;
    try { localStorage.setItem("maris.theme", theme); } catch { /* storage unavailable */ }
  }, [theme]);

  const toast = useCallback((msg: string, kind: Toast["kind"] = "info") => {
    msg = msg.replace(/\s+/g, " ").trim(); if (!msg) return;
    const now = Date.now(); if (msg === last.current.msg && now - last.current.t < 1500) return;   // same message twice: once
    last.current = { msg, t: now };
    const id = now + Math.random();
    setToasts(t => [...t.slice(-3), { id, msg, kind }]);
    setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 4200);
  }, []);

  useEffect(() => {
    currentSession().then(s => { if (s) setSession(s); }).catch(() => { /* sign on again */ }).finally(() => setRestoring(false));
  }, []);
  const signedOn = useRef(false); signedOn.current = !!session;
  useEffect(() => onSessionExpired(() => {
    if (signedOn.current) toast("Your MARIS session has ended. Please sign on again.", "err");
    setSession(null); setStack(HOME);
  }), [toast]);

  const value = useMemo<AppState>(() => ({
    session,
    signOn: s => { setSession(s); setStack(HOME); },
    signOff: () => { void endSession(); setSession(null); setStack(HOME); },
    stack,
    open: p => { setStack(s => [...s, p]); window.scrollTo(0, 0); },
    go: p => { setStack(p.kind === "home" ? HOME : [...HOME, p]); window.scrollTo(0, 0); },
    back: () => setStack(s => s.length > 1 ? s.slice(0, -1) : s),
    backTo: i => setStack(s => s.slice(0, i + 1)),
    theme,
    toggleTheme: () => setTheme(t => t === "dark" ? "light" : "dark"),
    toast,
    info: (msg, title = "Information") => new Promise<void>(r => setDialog({ title, msg, ask: false, done: () => r() })),
    confirm: msg => new Promise<boolean>(r => setDialog({ title: "Please confirm", msg, ask: true, done: r })),
  }), [session, stack, theme, toast]);

  return (
    <Ctx.Provider value={value}>
      {restoring ? <p className="grid min-h-screen place-items-center text-slate-500" role="status">Loading MARIS…</p> : children}
      <Toasts toasts={toasts} />
      {dialog && <Dialog {...dialog} close={ok => { setDialog(null); dialog.done(ok); }} />}
    </Ctx.Provider>
  );
}
