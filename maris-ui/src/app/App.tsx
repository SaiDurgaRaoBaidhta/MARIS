import { useEffect, useState } from "react";
import { LoginPage } from "@/features/auth/LoginPage";
import { MENUS } from "@/features/menu/api";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { YardMapPage } from "@/features/yard-maps/YardMapPage";
import { resolve, Router } from "./router";
import { Footer } from "./shell/Footer";
import { Header } from "./shell/Header";
import { Sidebar } from "./shell/Sidebar";

function Workspace() {
  const app = useApp();
  const [mini, setMini] = useState(() => { try { return localStorage.getItem("maris.nav") === "mini"; } catch { return false; } });
  useEffect(() => { try { localStorage.setItem("maris.nav", mini ? "mini" : "full"); } catch { /* storage unavailable */ } }, [mini]);
  // dev / tools build only: lets tools open any page (screen sweep in tools/check-screens.mjs)
  useEffect(() => { if (import.meta.env.DEV || import.meta.env.VITE_TOOLS === "1") (window as unknown as { __maris: unknown }).__maris = { go: app.go }; }, [app.go]);
  return (
    <>
      <Header onToggleNav={() => setMini(m => !m)} />
      <Sidebar menus={MENUS} mini={mini} />
      <main className={`min-h-screen px-6 pb-16 pt-16 transition-[margin] duration-200 print:m-0 print:p-0 ${mini ? "ml-[72px]" : "ml-[276px]"}`}><Router /></main>
      <Footer />
      {(p => p.kind === "map" ? <YardMapPage map={{ yard: p.yard, shop: p.shop, mode: p.mode }} navWidth={mini ? 72 : 276} /> : null)(resolve(app.stack[app.stack.length - 1]))}
    </>
  );
}

function Root() {
  const app = useApp();
  return app.session ? <Workspace /> : <LoginPage />;
}

export default function App() {
  return <AppProvider><Root /></AppProvider>;
}
