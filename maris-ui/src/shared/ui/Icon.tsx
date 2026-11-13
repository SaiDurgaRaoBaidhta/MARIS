import { ICON } from "./icons";

/** 24px line icon from the MARIS icon set (static, trusted SVG path data). */
export function Icon({ name, size = 16, className }: { name: string; size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={2}
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true"
      dangerouslySetInnerHTML={{ __html: ICON[name] || ICON.file }} />
  );
}
