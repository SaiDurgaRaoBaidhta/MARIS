/** fills the capture-time tokens of a screen ({{user}}, {{date}} …) */
export function fillTokens(v: string, user: string): string {
  if (!v.includes("{{")) return v;
  const d = new Date(), p = (n: number) => String(n).padStart(2, "0");
  const T: Record<string, string> = { user, date: `${p(d.getMonth() + 1)}-${p(d.getDate())}-${d.getFullYear()}`, ymd: `${d.getFullYear()}/${p(d.getMonth() + 1)}/${p(d.getDate())}`,
    isodate: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`, mdy: `${p(d.getMonth() + 1)}/${p(d.getDate())}/${d.getFullYear()}`,
    hm: `${p(d.getHours())}:${p(d.getMinutes())}`, hms: `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}` };
  return v.replace(/\{\{(\w+)\}\}/g, (all, k) => T[k] ?? all);
}
