import { useEffect, useState } from "react";

/** content width of an element, kept up to date. Use: const [ref, width] = useElementWidth(); <div ref={ref}> */
export function useElementWidth(initial = 1100) {
  const [el, ref] = useState<HTMLElement | null>(null);
  const [width, setWidth] = useState(initial);
  useEffect(() => {
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(e.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, [el]);
  return [ref, width] as const;
}
