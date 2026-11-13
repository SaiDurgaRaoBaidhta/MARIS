import { useEffect, useState } from "react";

/** window.innerHeight, kept up to date */
export function useWindowHeight() {
  const [h, setH] = useState(() => window.innerHeight);
  useEffect(() => { const on = () => setH(window.innerHeight); window.addEventListener("resize", on); return () => window.removeEventListener("resize", on); }, []);
  return h;
}
